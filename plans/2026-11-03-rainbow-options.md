# Rainbow mode options: speed, chat colors, message text, low performance

## Problem

Rainbow mode (`d50916901`) rotated the hue of every interface color, including the chat bubbles, at a
fixed 16-second cycle and 20 updates per second. Three complaints followed:

1. the cycle speed cannot be changed;
2. the bubbles are always rainbow, but the user may want colorful chrome with plain chat (and the
   request also asked for the *text* to be rainbow, which is a different thing);
3. on phones the home screen became janky with the mode on.

## Solution

Four preferences, all in the *Customize theme > Interface colors* section, all only visible while
rainbow mode is on:

| Preference | Default | Effect |
|---|---|---|
| `rainbowSpeed` (`RainbowSpeed`) | `NORMAL` | cycle length 32 s / 16 s / 8 s |
| `rainbowChatColors` | `true` | bubble colors take part in the rotation |
| `rainbowMessageText` | `false` | the message text itself is rainbow |
| `rainbowLowPerformance` | `false` | hue updates every 250 ms instead of 50 ms |

## Design decisions

- **The timer stays the only timer.** `rememberRainbowPhase()` reads the speed and the low-performance
  flag, computes the step once, and restarts its `LaunchedEffect` only when those change. No new
  composable starts a clock.
- **Low performance trades smoothness for work.** 250 ms is four updates per second: visibly stepped,
  but 5x fewer recompositions of the whole app, which is what the home-screen jank needed. The cycle
  length is unchanged, so the colors still complete a rotation in the same time.
- **Bubbles are on by default.** They were already cycling, so keeping `rainbowChatColors = true` makes
  this change purely additive for existing users; the switch exists to turn them off.
- **Text and bubbles are mutually exclusive, and text wins in code.** `rainbowChatColorsActive(enabled,
  chatColors, messageText)` returns false whenever the text is rainbow, so even a stale preference
  combination cannot produce an unreadable bubble; the settings rows additionally clear the other
  toggle when one is switched on, so the UI never shows two active options.
- **Text rainbow is a color, not a hue shift.** `onSurface` is achromatic in the shipped themes, so
  `shiftHue` would return it unchanged. `rainbowTextColor(phase, dark)` builds the color from the phase
  with a fixed saturation/brightness per theme (bright on dark, dark on light), which keeps contrast on
  the bubble.
- **One place for the text color.** `MarkdownText`'s default `style` resolves the color through
  `rememberRainbowMessageTextColor()`, so text bubbles, quotes and the message-details modal all follow
  the option without touching their call sites. Callers that pass an explicit style keep control.
- **Still display-only and local.** All four keys are client-side `AppPreferences`; nothing is written
  to the theme, the database, or `AppSettings` (RULE-29 invariant 5 is unchanged).

## Rejected alternatives

- A per-color "rainbow" checkbox next to each of the eight swatches: the request is about modes, and
  eight independent switches would be unreadable anyway.
- Letting both rainbows coexist with a warning: the user explicitly asked for exclusion because the text
  becomes unreadable.
- Rebuilding `SimpleXTheme` per animation frame for maximum smoothness: that is exactly the cost the
  low-performance mode exists to avoid.
- Hue-shifting `onSurface` for the text: it is grey in the shipped themes, so nothing would change.

## Verification

`./gradlew :common:desktopTest` → 25 suites, 179 tests, no failures (`RainbowTest` now 19 cases,
covering cycle length per speed, update intervals, the mutual exclusion, bubble opt-out, the text color
range for both themes, and the speed preference round trip).
`./gradlew :common:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL`.

Note: the desktop tests share the app's real `java.util.prefs` store, so a test that asserts a stored
default is environment-dependent (it failed once because the developer had rainbow mode switched on).
The speed test sets and restores the value instead of asserting it.
