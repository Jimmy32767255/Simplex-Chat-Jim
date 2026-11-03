package chat.simplex.common.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.RainbowSpeed
import kotlinx.coroutines.delay

// Rainbow mode: every color of the active theme is rotated around the HSV hue circle by one shared
// phase, so the whole interface stays consistent while it cycles. The theme itself never changes --
// only what is displayed does. Spec: spec/services/theme.md#rainbow-mode

/** Length of one full hue rotation at the normal speed. */
internal const val RAINBOW_CYCLE_MILLIS: Long = 16_000

/** Time between two hue updates at the normal speed: 20 per second, enough for a slow cycle. */
internal const val RAINBOW_UPDATE_MILLIS: Long = 50

/**
 * Time between two hue updates in low performance mode. Four updates per second look stepped, but the
 * app recomposes 5x less often, which is what keeps the home screen smooth on slower phones.
 */
internal const val RAINBOW_UPDATE_MILLIS_LOW_PERFORMANCE: Long = 250

/** Length of one full hue rotation for the selected speed. */
internal fun rainbowCycleMillis(speed: RainbowSpeed): Long = when (speed) {
  RainbowSpeed.SLOW -> 32_000
  RainbowSpeed.NORMAL -> RAINBOW_CYCLE_MILLIS
  RainbowSpeed.FAST -> 8_000
}

internal fun rainbowUpdateMillis(lowPerformance: Boolean): Long =
  if (lowPerformance) RAINBOW_UPDATE_MILLIS_LOW_PERFORMANCE else RAINBOW_UPDATE_MILLIS

/** Degrees the phase advances between two updates. */
internal fun rainbowHueStep(
  cycleMillis: Long = RAINBOW_CYCLE_MILLIS,
  updateMillis: Long = RAINBOW_UPDATE_MILLIS
): Float = 360f * updateMillis / cycleMillis

internal fun nextRainbowPhase(phase: Float, step: Float = rainbowHueStep()): Float {
  val next = phase + step
  return if (next >= 360f) next - 360f else next
}

internal fun rgbToHsv(red: Float, green: Float, blue: Float): Triple<Float, Float, Float> {
  val max = maxOf(red, green, blue)
  val min = minOf(red, green, blue)
  val delta = max - min
  val hue = when {
    delta == 0f -> 0f
    max == red -> 60f * (((green - blue) / delta) % 6f)
    max == green -> 60f * (((blue - red) / delta) + 2f)
    else -> 60f * (((red - green) / delta) + 4f)
  }
  val saturation = if (max == 0f) 0f else delta / max
  return Triple(if (hue < 0f) hue + 360f else hue, saturation, max)
}

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
 * Rotates the hue of [color] by [degrees]. Achromatic colors (greys, black, white) and transparent
 * colors are returned unchanged: they have no hue to rotate, and shifting them would only make the
 * interface unreadable.
 */
internal fun shiftHue(color: Color, degrees: Float): Color {
  if (degrees == 0f || color.alpha == 0f) return color
  val (hue, saturation, value) = rgbToHsv(color.red, color.green, color.blue)
  if (saturation == 0f) return color
  val (red, green, blue) = hsvToRgb(hue + degrees, saturation, value)
  return Color(red, green, blue, color.alpha)
}

/** The Material palette with every color rotated by [phase]. */
internal fun rainbowColors(colors: Colors, phase: Float): Colors = colors.copy(
  primary = shiftHue(colors.primary, phase),
  primaryVariant = shiftHue(colors.primaryVariant, phase),
  secondary = shiftHue(colors.secondary, phase),
  secondaryVariant = shiftHue(colors.secondaryVariant, phase),
  background = shiftHue(colors.background, phase),
  surface = shiftHue(colors.surface, phase),
  error = shiftHue(colors.error, phase),
  onPrimary = shiftHue(colors.onPrimary, phase),
  onSecondary = shiftHue(colors.onSecondary, phase),
  onBackground = shiftHue(colors.onBackground, phase),
  onSurface = shiftHue(colors.onSurface, phase),
  onError = shiftHue(colors.onError, phase),
)

/**
 * The app palette with the colors rotated by [phase]. The chat bubble colors only take part when
 * [chatColors] is set: they are the largest colored areas on the screen, so they can be turned off
 * (and are turned off while the message text is rainbow, see [rainbowChatColorsActive]).
 */
internal fun rainbowAppColors(appColors: AppColors, phase: Float, chatColors: Boolean = true): AppColors =
  appColors.copy(
    title = shiftHue(appColors.title, phase),
    primaryVariant2 = shiftHue(appColors.primaryVariant2, phase),
    sentMessage = if (chatColors) shiftHue(appColors.sentMessage, phase) else appColors.sentMessage,
    sentQuote = if (chatColors) shiftHue(appColors.sentQuote, phase) else appColors.sentQuote,
    receivedMessage = if (chatColors) shiftHue(appColors.receivedMessage, phase) else appColors.receivedMessage,
    receivedQuote = if (chatColors) shiftHue(appColors.receivedQuote, phase) else appColors.receivedQuote,
  )

/**
 * The color of chat message text in rainbow mode. The hue comes straight from [phase] because text
 * colors have no hue of their own to rotate; saturation and brightness are fixed per theme so the text
 * stays readable on top of the bubble.
 */
internal fun rainbowTextColor(phase: Float, dark: Boolean): Color {
  val (red, green, blue) = hsvToRgb(
    phase,
    saturation = if (dark) 0.55f else 0.85f,
    value = if (dark) 0.95f else 0.45f
  )
  return Color(red, green, blue)
}

/**
 * Chat colors and message text are never rainbow at the same time: two fully saturated hues together
 * make the text unreadable, so the text wins when both are enabled.
 */
internal fun rainbowChatColorsActive(enabled: Boolean, chatColors: Boolean, messageText: Boolean): Boolean =
  enabled && chatColors && !messageText

internal fun rainbowMessageTextActive(enabled: Boolean, messageText: Boolean): Boolean =
  enabled && messageText

/**
 * The one hue phase of the whole interface. `SimpleXTheme` runs the only timer and provides the value
 * here; every other reader (the settings swatches, the message text, `SimpleXThemeOverride`) just reads
 * it, so the whole interface stays in step and no extra timer is started per message or per screen.
 * It is a `compositionLocalOf` rather than a `staticCompositionLocalOf` because it changes every few
 * milliseconds — only the composables that actually read it should recompose.
 */
internal val LocalRainbowPhase = compositionLocalOf { 0f }

/**
 * Starts and advances the rainbow phase while rainbow mode is on, and returns `0f` while it is off.
 * Must be called once, by the theme provider (`SimpleXTheme`); the actual value is published through
 * [LocalRainbowPhase] so the timer is not duplicated.
 */
@Composable
internal fun rememberRainbowPhaseTimer(): Float {
  val enabled = remember { appPrefs.rainbowThemeColors.state }.value
  if (!enabled) return 0f
  val speed = remember { appPrefs.rainbowSpeed.state }.value
  val lowPerformance = remember { appPrefs.rainbowLowPerformance.state }.value
  val updateMillis = rainbowUpdateMillis(lowPerformance)
  val step = rainbowHueStep(rainbowCycleMillis(speed), updateMillis)
  val phase = remember { mutableStateOf(0f) }
  LaunchedEffect(step, updateMillis) {
    while (true) {
      delay(updateMillis)
      phase.value = nextRainbowPhase(phase.value, step)
    }
  }
  return phase.value
}

/**
 * The current hue phase, or `0f` when rainbow mode is off. Reads the phase that the theme provider
 * publishes, so this starts no timer of its own.
 */
@Composable
fun rememberRainbowPhase(): Float = LocalRainbowPhase.current

/** Whether the chat bubble colors take part in rainbow mode. */
@Composable
fun rememberRainbowChatColors(): Boolean {
  val enabled = remember { appPrefs.rainbowThemeColors.state }.value
  val chatColors = remember { appPrefs.rainbowChatColors.state }.value
  val messageText = remember { appPrefs.rainbowMessageText.state }.value
  return rainbowChatColorsActive(enabled, chatColors, messageText)
}

/** The color of chat message text in rainbow mode, or `null` when the text is not rainbow. */
@Composable
fun rememberRainbowMessageTextColor(): Color? {
  val enabled = remember { appPrefs.rainbowThemeColors.state }.value
  val messageText = remember { appPrefs.rainbowMessageText.state }.value
  if (!rainbowMessageTextActive(enabled, messageText)) return null
  val phase = rememberRainbowPhase()
  return rainbowTextColor(phase, dark = !MaterialTheme.colors.isLight)
}
