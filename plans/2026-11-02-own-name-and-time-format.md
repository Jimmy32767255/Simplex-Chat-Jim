# Own Name in Copied Messages + 12/24-Hour Setting

## Problem

1. The batch copy of selected messages (`chatItemsToText`) leaves out the sender name for messages the
   user sent, so an exported conversation loses who said what as soon as both sides are present.
2. Times of day are formatted with `DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)`, i.e. with the
   locale's convention. On Android the 12/24-hour switch is an OS setting that is independent from the
   locale, so the app can show 12-hour times while the system is set to 24-hour (and the reverse); there is
   also no way to override the convention manually.

## Solution

1. `ciSenderName` gains an `ownName` parameter and returns a name for sent messages too:
   - `GroupSnd` → the user's member name in that group (`chatInfo.groupInfo.membership.displayName`,
     which can differ from the main profile), falling back to `ownName`;
   - `DirectSnd`, `LocalSnd`, `LocalRcv` → `ownName` (messages in a chat with oneself are the user's);
   - `ChannelRcv` keeps no name (a channel post has no individual sender).
   `chatItemToText` / `chatItemsToText` take `ownName`, and `ChatView` passes
   `chatModel.currentUser.value?.profile?.displayName`.
2. A `TimeFormat` preference (`SYSTEM`, `HOUR_12`, `HOUR_24`, default `SYSTEM`) plus a dropdown in
   Settings. `uses24HourFormat(format, systemUses24Hour)` resolves it, `systemUses24HourFormat()` is an
   expect/actual pair (Android: `DateFormat.is24HourFormat`, desktop: the locale's short time pattern),
   and `getTimestampText` / `localTimestamp` build their time part from `timeOfDayPattern(use24Hour)`.

## Design decisions

- The own name comes from the chat, not from a global value, so a group transcript shows the same name the
  group members see.
- No new parameter on `getTimestampText`: it reads `ChatController.appPrefs.timeFormat.state` itself, which
  also makes a change recompose every visible timestamp without a restart.
- The date part keeps the existing localized formatting; only the hour convention changes, so the diff is
  limited to the time pattern.
- `localTimestamp` keeps seconds, as before; only the 12/24-hour convention changes.
- `SYSTEM` is the default and reproduces the current behaviour on both platforms, so existing users see no
  change until they touch the dropdown.
- The preference is local device state: not sent to the core, not in the database, not in the
  `AppSettings` cross-device backup (same stance as the other display preferences).

## Steps

1. Strings: `settings_time_format`, `time_format_system`, `time_format_12h`, `time_format_24h`,
   `time_format_footer` (base + zh-rCN).
2. `TimeFormat` enum + `AppPreferences.timeFormat` + `SHARED_PREFS_TIME_FORMAT`.
3. `platform/TimeFormat.kt` expect + android/desktop actuals.
4. `ChatModel.kt`: `uses24HourFormat`, `timeOfDayPattern`, `timeOfDayPatternWithSeconds`,
   `preferredTimeFormatUses24Hour`, wire into `getTimestampText` and `localTimestamp`; `ciSenderName`
   with `ownName`; `chatItemToText` / `chatItemsToText` signatures.
5. `SettingsView`: untitled section with the dropdown + footer, after the "Chat list" section.
6. `ChatView.copyItems`: pass the own name.
7. Tests: `SelectedMessagesCopyTest` (own name for direct/group/notes, group member name wins) and new
   `TimeFormatTest` (resolver, patterns, default).

## Adversarial review

- `TimeFormat.entries` needs Kotlin 1.9+ enum entries — the project is on 2.1.20, fine.
- `mkSafeEnumPreference` stores the constant name, so renaming a value would silently fall back to
  `SYSTEM` — acceptable, the values are stable.
- Reading the preference state inside `getTimestampText` makes it a Compose read; calling it from a
  background/copy context still works (a `MutableState` can be read anywhere).
- Desktop `systemUses24HourFormat` returns `true` when the pattern cannot be inspected, to keep the
  24-hour behaviour of most non-US locales rather than silently switching to AM/PM.
- `ChatInfo.Group` needs the `groupChatScope` argument in tests (no default), and
  `ChatInfo.Local.sampleData` is used for the notes case.
