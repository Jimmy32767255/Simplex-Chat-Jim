package chat.simplex.common.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.material.Colors
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import chat.simplex.common.model.ChatController.appPrefs
import kotlinx.coroutines.delay

// Spec: spec/services/theme.md#rainbow-mode

/**
 * Rainbow mode rotates the hue of every theme color over time without touching the saved theme.
 *
 * The hue offset is a single value shared by the whole palette: the interface moves through the
 * spectrum as one piece, and a color swatch in the settings always shows the same hue as the color it
 * stands for.
 */
internal const val RAINBOW_CYCLE_MILLIS: Long = 16_000

/**
 * How often the hue offset is recomputed. The cycle is slow enough that 20 updates per second still
 * look continuous, and a lower rate keeps the cost of recomposing the interface with new colors
 * reasonable compared to animating on every frame.
 */
internal const val RAINBOW_UPDATE_MILLIS: Long = 50

/** Hue offset added per update, so a full cycle takes [RAINBOW_CYCLE_MILLIS] regardless of the update rate. */
internal fun rainbowHueStep(): Float = 360f * RAINBOW_UPDATE_MILLIS / RAINBOW_CYCLE_MILLIS

internal fun nextRainbowPhase(phase: Float): Float {
  val next = phase + rainbowHueStep()
  return if (next >= 360f) next - 360f else next
}

/** Returns `(hue, saturation, value)` with hue in `0..360` and the other components in `0..1`. */
internal fun rgbToHsv(red: Float, green: Float, blue: Float): Triple<Float, Float, Float> {
  val maxC = maxOf(red, green, blue)
  val minC = minOf(red, green, blue)
  val delta = maxC - minC
  val hue = when {
    delta == 0f -> 0f
    maxC == red -> 60f * (((green - blue) / delta) % 6f)
    maxC == green -> 60f * (((blue - red) / delta) + 2f)
    else -> 60f * (((red - green) / delta) + 4f)
  }
  val saturation = if (maxC == 0f) 0f else delta / maxC
  return Triple(if (hue < 0f) hue + 360f else hue, saturation, maxC)
}

/** Returns `(red, green, blue)` in `0..1` for the given hue (`0..360`), saturation and value (`0..1`). */
internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Triple<Float, Float, Float> {
  val h = ((hue % 360f) + 360f) % 360f
  val c = value * saturation
  val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
  val m = value - c
  val (r, g, b) = when {
    h < 60f -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  return Triple(r + m, g + m, b + m)
}

/**
 * Rotates the hue of [color] by [degrees], keeping its saturation, brightness and alpha. Colors without
 * a hue (greys, black, white) come back unchanged, which is what keeps backgrounds and text readable in
 * rainbow mode.
 */
internal fun shiftHue(color: Color, degrees: Float): Color {
  if (degrees == 0f || color.alpha == 0f) return color
  val (hue, saturation, value) = rgbToHsv(color.red, color.green, color.blue)
  if (saturation == 0f) return color
  val (red, green, blue) = hsvToRgb(hue + degrees, saturation, value)
  return Color(red, green, blue, color.alpha)
}

internal fun rainbowColors(colors: Colors, phase: Float): Colors {
  if (phase == 0f) return colors
  fun shift(color: Color): Color = shiftHue(color, phase)
  return colors.copy(
    primary = shift(colors.primary),
    primaryVariant = shift(colors.primaryVariant),
    secondary = shift(colors.secondary),
    secondaryVariant = shift(colors.secondaryVariant),
    background = shift(colors.background),
    surface = shift(colors.surface),
    error = shift(colors.error),
    onPrimary = shift(colors.onPrimary),
    onSecondary = shift(colors.onSecondary),
    onBackground = shift(colors.onBackground),
    onSurface = shift(colors.onSurface),
    onError = shift(colors.onError),
  )
}

internal fun rainbowAppColors(appColors: AppColors, phase: Float): AppColors {
  if (phase == 0f) return appColors
  fun shift(color: Color): Color = shiftHue(color, phase)
  return appColors.copy(
    title = shift(appColors.title),
    primaryVariant2 = shift(appColors.primaryVariant2),
    sentMessage = shift(appColors.sentMessage),
    sentQuote = shift(appColors.sentQuote),
    receivedMessage = shift(appColors.receivedMessage),
    receivedQuote = shift(appColors.receivedQuote),
  )
}

/**
 * The current hue offset of rainbow mode, or `0` when the mode is off.
 *
 * Reading the returned value from a composable subscribes it to the animation; callers are expected to
 * pass the result to [rainbowColors] / [rainbowAppColors] rather than to store it.
 */
@Composable
fun rememberRainbowPhase(): Float {
  val enabled = remember { appPrefs.rainbowThemeColors.state }.value
  if (!enabled) return 0f
  val phase = remember { mutableStateOf(0f) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(RAINBOW_UPDATE_MILLIS)
      phase.value = nextRainbowPhase(phase.value)
    }
  }
  return phase.value
}
