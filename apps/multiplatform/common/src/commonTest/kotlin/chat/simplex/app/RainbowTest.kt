package chat.simplex.app

import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color
import chat.simplex.common.model.AppPreferences
import chat.simplex.common.ui.theme.*
import kotlin.math.abs
import kotlin.test.*

class RainbowTest {
  // Color stores its components in 8-bit precision, so a round trip through HSV is only accurate
  // to about 1/255 per channel.
  private fun assertColorEquals(expected: Color, actual: Color, tolerance: Float = 0.01f, message: String = "") {
    assertTrue(abs(expected.alpha - actual.alpha) <= tolerance, "$message alpha: ${expected.alpha} != ${actual.alpha}")
    assertTrue(abs(expected.red - actual.red) <= tolerance, "$message red: ${expected.red} != ${actual.red}")
    assertTrue(abs(expected.green - actual.green) <= tolerance, "$message green: ${expected.green} != ${actual.green}")
    assertTrue(abs(expected.blue - actual.blue) <= tolerance, "$message blue: ${expected.blue} != ${actual.blue}")
  }

  private fun assertFloatEquals(expected: Float, actual: Float, tolerance: Float = 0.01f, message: String = "") {
    assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected, got $actual")
  }

  @Test
  fun testRgbToHsvForPrimaries() {
    val (redHue, redSat, redValue) = rgbToHsv(1f, 0f, 0f)
    assertFloatEquals(0f, redHue, 0.01f)
    assertFloatEquals(1f, redSat, 0.01f)
    assertFloatEquals(1f, redValue, 0.01f)

    val (greenHue, _, _) = rgbToHsv(0f, 1f, 0f)
    assertFloatEquals(120f, greenHue, 0.01f)

    val (blueHue, _, _) = rgbToHsv(0f, 0f, 1f)
    assertFloatEquals(240f, blueHue, 0.01f)
  }

  @Test
  fun testRgbToHsvForGrey() {
    val (hue, saturation, value) = rgbToHsv(0.5f, 0.5f, 0.5f)
    assertFloatEquals(0f, hue, 0.01f)
    assertFloatEquals(0f, saturation, 0.01f)
    assertFloatEquals(0.5f, value, 0.01f)
  }

  @Test
  fun testHsvToRgbRoundTrip() {
    for (hue in listOf(0f, 40f, 120f, 200f, 300f, 359f)) {
      val (red, green, blue) = hsvToRgb(hue, 0.7f, 0.9f)
      val (backHue, backSat, backValue) = rgbToHsv(red, green, blue)
      assertFloatEquals(hue, backHue, 0.5f, "hue $hue")
      assertFloatEquals(0.7f, backSat, 0.01f)
      assertFloatEquals(0.9f, backValue, 0.01f)
    }
  }

  @Test
  fun testShiftHueKeepsSaturationValueAndAlpha() {
    val color = Color(0.9f, 0.4f, 0.2f, 0.6f)
    val shifted = shiftHue(color, 90f)
    val (_, sat, value) = rgbToHsv(color.red, color.green, color.blue)
    val (_, shiftedSat, shiftedValue) = rgbToHsv(shifted.red, shifted.green, shifted.blue)
    assertFloatEquals(sat, shiftedSat, 0.01f)
    assertFloatEquals(value, shiftedValue, 0.01f)
    assertFloatEquals(0.6f, shifted.alpha, 0.001f)
  }

  @Test
  fun testShiftHueMovesHueByDegrees() {
    val color = Color(1f, 0f, 0f)
    val (hue, _, _) = rgbToHsv(shiftHue(color, 120f).red, shiftHue(color, 120f).green, shiftHue(color, 120f).blue)
    assertFloatEquals(120f, hue, 0.5f)
  }

  @Test
  fun testShiftHueIsAPeriodicRotation() {
    val color = Color(0.2f, 0.6f, 0.9f)
    assertColorEquals(color, shiftHue(color, 360f))
    assertColorEquals(color, shiftHue(color, 0f))
    // Shifting backwards then forwards by the same amount returns to the start
    assertColorEquals(color, shiftHue(shiftHue(color, 90f), -90f))
  }

  @Test
  fun testShiftHueLeavesAchromaticColorsAlone() {
    for (color in listOf(Color.Black, Color.White, Color.Gray, Color.Transparent)) {
      assertColorEquals(color, shiftHue(color, 123f))
    }
  }

  @Test
  fun testPhaseStepsThroughAFullCycle() {
    var phase = 0f
    var steps = 0
    val expectedSteps = (RAINBOW_CYCLE_MILLIS / RAINBOW_UPDATE_MILLIS).toInt()
    while (steps < expectedSteps) {
      phase = nextRainbowPhase(phase)
      steps++
      assertTrue(phase >= 0f && phase < 360f, "phase $phase stays in range")
    }
    assertTrue(abs(phase) < 1f || abs(phase - 360f) < 1f, "a full cycle returns to the start, got $phase")
  }

  @Test
  fun testHueStepCoversTheCycle() {
    assertFloatEquals(360f / (RAINBOW_CYCLE_MILLIS / RAINBOW_UPDATE_MILLIS), rainbowHueStep(), 0.001f)
  }

  @Test
  fun testRainbowColorsAtZeroPhaseKeepsThePalette() {
    val colors = darkColors(
      primary = Color(0.1f, 0.4f, 0.9f),
      secondary = Color(0.9f, 0.3f, 0.1f),
      background = Color(0.1f, 0.1f, 0.1f),
    )
    val rainbow = rainbowColors(colors, 0f)
    assertColorEquals(colors.primary, rainbow.primary)
    assertColorEquals(colors.background, rainbow.background)
    assertEquals(colors.isLight, rainbow.isLight)
  }

  @Test
  fun testRainbowColorsShiftsChromaticColorsAndKeepsNeutralOnes() {
    val colors = darkColors(
      primary = Color(1f, 0f, 0f),
      secondary = Color(0f, 1f, 0f),
      background = Color(0.1f, 0.1f, 0.1f),
      surface = Color(0.2f, 0.2f, 0.2f),
    )
    val rainbow = rainbowColors(colors, 180f)
    assertNotEquals(colors.primary, rainbow.primary)
    assertNotEquals(colors.secondary, rainbow.secondary)
    // Greys have no hue, so backgrounds stay readable
    assertColorEquals(colors.background, rainbow.background)
    assertColorEquals(colors.surface, rainbow.surface)
    assertEquals(colors.isLight, rainbow.isLight)
  }

  @Test
  fun testRainbowAppColorsShiftsEveryCustomColor() {
    val appColors = AppColors(
      title = Color(1f, 0f, 0f),
      primaryVariant2 = Color(0f, 1f, 0f),
      sentMessage = Color(0f, 0f, 1f),
      sentQuote = Color(1f, 1f, 0f),
      receivedMessage = Color(1f, 0f, 1f),
      receivedQuote = Color(0f, 1f, 1f),
    )
    val rainbow = rainbowAppColors(appColors, 90f)
    assertNotEquals(appColors.title, rainbow.title)
    assertNotEquals(appColors.primaryVariant2, rainbow.primaryVariant2)
    assertNotEquals(appColors.sentMessage, rainbow.sentMessage)
    assertNotEquals(appColors.sentQuote, rainbow.sentQuote)
    assertNotEquals(appColors.receivedMessage, rainbow.receivedMessage)
    assertNotEquals(appColors.receivedQuote, rainbow.receivedQuote)
    // The saved palette is never mutated
    assertEquals(Color(1f, 0f, 0f), appColors.title)
  }

  @Test
  fun testRainbowModeIsOffByDefault() {
    assertFalse(AppPreferences().rainbowThemeColors.get())
  }
}
