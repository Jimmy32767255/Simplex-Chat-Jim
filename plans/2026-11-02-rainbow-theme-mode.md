# Rainbow mode for the interface colors (feature 3 of the 2026-11-02 request)

## Problem

The custom theme editor lets the user pick every interface color by hand, but nothing animates them.
The request was a "rainbow mode" (`彩虹模式`) switch inside 设置 → 外观 → 自定义主题 → 颜色调整 that makes
the chosen colors cycle through the spectrum ("RGB" effect), with the color swatches in that list
cycling as well.

## Solution

A display-time hue rotation applied by `SimpleXTheme`, driven by one shared phase and turned on by a new
client-side preference `appPreferences.rainbowThemeColors`.

- `ui/theme/Rainbow.kt` (new): `rgbToHsv` / `hsvToRgb` (pure Kotlin, no `java.awt`, so it works on
  Android), `shiftHue(color, degrees)`, `rainbowColors(colors, phase)`, `rainbowAppColors(appColors, phase)`
  and `@Composable rememberRainbowPhase()`.
- `ui/theme/Theme.kt` `SimpleXTheme`: computes `displayedColors` / `displayedAppColors` with
  `remember(theme, rainbowPhase)` and passes them to `MaterialTheme` and `rememberedAppColors`.
- `views/usersettings/Appearance.kt` `CustomizeThemeColorsSection`: `SettingsPreferenceItem` with
  `appPrefs.rainbowThemeColors` at the top of the section, a footer explaining the mode, and each swatch
  tinted through `shiftHue(color, rainbowPhase)`.

## Design decisions

- **Display-only.** The palette comes from `CurrentColors` and the stored `ThemeOverrides`. The phase is
  applied when the colors are handed to Compose; nothing writes back. Turning the mode off restores the
  exact colors the user picked, and no theme is re-saved (RULE-29).
- **One phase for everything.** All colors drift by the same offset, so the interface looks like one
  rainbow rather than a random patchwork, and a swatch always matches the color it stands for.
- **Achromatic colors stay put.** `shiftHue` returns greys / black / white unchanged (saturation 0), which
  is what keeps backgrounds, surfaces and text readable.
- **20 updates per second, not 60.** A 16-second cycle advanced every 50 ms still reads as continuous
  motion, and it re-colors the whole app 20 times a second instead of 60. Colour is not layout, so the
  cost is a recomposition of the theme providers, not a relayout.
- **Off means no timer and no copies.** With the preference off, `rememberRainbowPhase()` returns `0`,
  the `LaunchedEffect` is not started, and `rainbowColors` / `rainbowAppColors` return their input object
  unchanged.
- **`SimpleXThemeOverride` is left alone.** It renders a specific theme for per-chat previews and for the
  color editor, where the user must see the colors being edited.
- **Not in `AppSettings`.** Like the other new client-side preferences, the mode is not part of the
  exported settings: the core would drop an unknown field anyway, and a device-local effect should not
  travel.

## Rejected alternatives

- Animating in `ThemeManager` / `CurrentColors`: that would make the flowing colors the "current theme",
  so every read of the theme (including saving and previews) would see them.
- Per-color independent phases: prettier in theory, but the settings swatches could then disagree with
  the real colors, and the palette stops looking like one coherent rainbow.
- Animating on every frame with `infiniteRepeatable`: smoother, but 3× the recompositions for a slow
  cycle where the difference is invisible.

## Steps

1. Add `rainbowThemeColors` (`"RainbowThemeColors"`) to `AppPreferences`.
2. Add `ui/theme/Rainbow.kt`.
3. Wire `SimpleXTheme`.
4. Add the toggle, footer and animated swatches to `CustomizeThemeColorsSection`.
5. Strings `rainbow_mode`, `rainbow_mode_footer` (base + zh-rCN).
6. `RainbowTest.kt` (13 cases), `./gradlew :common:desktopTest`.
7. Docs: `spec/services/theme.md` (§ Rainbow mode + source file), `spec/state.md`, `product/rules.md`
   (RULE-29), `product/concepts.md` (PC44), `spec/impact.md`, `product/views/settings.md`, `TODO.md`.
8. Write `diff/0019-rainbow-theme-mode.patch` and commit.

## Verification

- `./gradlew :common:desktopTest` → `BUILD SUCCESSFUL`, `RainbowTest` 13/13, 23 suites with no failures
  (159 tests).
- `./gradlew :common:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL` (the hue math is plain Kotlin, so it
  compiles for Android unchanged).
