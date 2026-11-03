# Fix: rainbow mode did not cycle inside a conversation

## Problem

The settings page showed the color swatches cycling, but opening a chat showed static bubbles and
chrome. Reported as "气泡颜色只有在自定义主题页面里面预览的时候是生效的，实际打开聊天页面看是不生效的".

Root cause: a conversation is wrapped in `SimpleXThemeOverride` (`views/chat/ChatView.kt`:

```kotlin
SimpleXThemeOverride(overrides ?: CurrentColors.collectAsState().value) { ... }
```

), and `SimpleXThemeOverride` handed the untouched `theme.colors` / `theme.appColors` to `MaterialTheme`
and `LocalAppColors`. Rainbow mode was only applied in `SimpleXTheme`, so every screen inside an override
— that is, every open chat — was excluded. The settings preview worked because the swatches call
`shiftHue` directly and the color-editor preview is a separate path.

A second defect found while reading the code: `rememberRainbowPhase()` started its own timer at every
call site. `MarkdownText`'s default text color calls it, so a chat with N messages ran N coroutines,
each with its own drifting phase.

## Solution

- One timer for the whole interface. `SimpleXTheme` now calls `rememberRainbowPhaseTimer()` (internal)
and publishes the value through a new `LocalRainbowPhase` (`compositionLocalOf { 0f }`).
- `rememberRainbowPhase()` becomes a pure reader of that CompositionLocal, so the swatches, the message
text and any other reader share exactly one phase and start nothing.
- `SimpleXThemeOverride` reads the phase too and computes `displayedColors` / `displayedAppColors` with
`rainbowColors` / `rainbowAppColors` before providing them, so a conversation cycles like every other
screen.

## Design decisions

- **`compositionLocalOf`, not `staticCompositionLocalOf`.** The phase changes every 50 ms (250 ms in low
performance mode); a static local would invalidate the whole subtree, which is precisely the jank the
low-performance option exists to avoid. Only composables that read the phase recompose.
- **The override keeps changing only what is displayed.** `SimpleXThemeOverride` receives the per-chat
theme (`overrides`) or `CurrentColors`; the phase is applied on top and never written back, so a
per-chat theme and the saved `ThemeOverrides` stay untouched (RULE-29 invariant 1).
- **The timer stays in one place.** Putting the timer in the theme provider rather than in a
CompositionLocal-based helper means the phase cannot drift between screens and cannot multiply per
message — the second bug above.
- **No behaviour change when rainbow mode is off.** `rememberRainbowPhaseTimer()` returns `0f`, both
`rainbowColors` and `rainbowAppColors` are no-ops at phase 0, and the override behaves exactly as before.

## Rejected alternatives

- Applying the phase inside `SimpleXThemeOverride` by starting its own timer: two independent timers
would drift, and every navigated screen would restart the cycle from zero.
- Replacing `SimpleXThemeOverride` with `SimpleXTheme` inside `ChatView`: the override exists to apply a
per-chat theme; removing it would break per-chat colors and wallpapers.
- Passing the phase as a parameter to `MarkdownText`: it would leak rainbow concerns into a rendering
primitive and still require every call site to be updated.

## Verification

`./gradlew :common:desktopTest` → 25 suites, 179 tests, no failures (the pure hue-rotation helpers in
`RainbowTest` are unchanged by this fix, which only rewires who runs the timer and who applies the
phase). `./gradlew :common:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL`.

Manual check: with rainbow mode on, the chat bubble colors and the surrounding chrome now cycle while a
conversation is open, and the settings swatches show the same hue at the same moment.
