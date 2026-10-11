# Desktop: choose the Enter key combinations for sending and for a newline

## Problem

On desktop the compose field has a hardcoded keyboard behaviour: `Enter`
sends the message and `Shift+Enter` inserts a newline. This is the
behaviour of `PlatformTextField.desktop.kt`, where the only handling of
`Key.Enter` / `Key.NumPadEnter` decides between sending and inserting by
testing `isShiftPressed`.

Users migrating from other messengers (where `Ctrl+Enter` or `Alt+Enter`
sends, or where plain `Enter` must insert a newline) cannot change this,
and it was requested in TODO.md item 1 as the first change on top of
7.0.4.

## Solution summary

Add two user preferences, `messageSendKey` and `messageNewlineKey`, each
holding a `ChatEnterKeyCombination`, and expose them in a new desktop-only
"Message sending" section of the settings page as two dropdowns. The
compose field reads both preferences and interprets every Enter press from
them.

The change stays inside existing layers:

- `ChatEnterKeyCombination` lives in `SimpleXAPI.kt` next to the other
  preference enums, and follows the existing `mkSafeEnumPreference` +
  `val text` pattern so it survives reuse by future versions.
- The settings UI is added to the common `SettingsLayout`, guarded by
  `appPlatform.isDesktop`, so no new screen or navigation entry is
  introduced and Android/iOS are untouched.
- `PlatformTextField.desktop.kt` keeps its existing structure; only the
  body of the Enter branch changes.

The two combinations are always kept distinct: selecting the combination
already used by the other row swaps the two.

## Detailed technical design

### Data model

```kotlin
enum class ChatEnterKeyCombination(val shift: Boolean, val ctrl: Boolean, val alt: Boolean) {
  ENTER(false, false, false),
  SHIFT_ENTER(true, false, false),
  CTRL_ENTER(false, true, false),
  ALT_ENTER(false, false, true);

  val text: String get() = ...  // localised label, e.g. "Shift + Enter"

  fun matches(shift: Boolean, ctrl: Boolean, alt: Boolean, meta: Boolean): Boolean =
    this.shift == shift && this.ctrl == ctrl && this.alt == alt && !meta

  companion object {
    val defaultSend = ENTER
    val defaultNewline = SHIFT_ENTER
  }
}
```

`meta` (Cmd on macOS, Win on Windows) is deliberately never part of a
combination and always excludes a match. The defaults reproduce the 7.0.4
behaviour exactly.

Stored under new private keys `DesktopMessageSendKey` and
`DesktopMessageNewlineKey` via `mkSafeEnumPreference`, which already
degrades unknown stored values to the default — so a value written by a
future version that added more combinations cannot crash an older build.

These preferences are intentionally **not** added to `AppSettings`
(the cross-device export/import struct). They are device- and
input-method-specific, like `closeBehavior`: carrying "Shift+Enter" to a
touch device would be meaningless, and adding them would change an
already-shipped backup format for no benefit.

### Key interpretation

In the `onPreviewKeyEvent` Enter branch, compute the four modifier
booleans once and resolve:

1. `messageSendKey` matches -> `onDone()` when sending is enabled.
2. `messageNewlineKey` matches -> insert a newline at the caret.
3. otherwise, a plain `Enter` with no modifier -> insert a newline.

Rule 3 is the safety net: an unassigned combination (for example
`Ctrl+Enter` while the send key is `Enter`) does nothing rather than
sending, but the user can never end up unable to insert a newline with
the plain key. `Shift`/`Ctrl`/`Alt` key-down events themselves are not
handled, so no other keyboard behaviour changes.

### Settings UI

A new `SectionView` titled `settings_message_sending`, rendered only when
`appPlatform.isDesktop`, with two `ExposedDropDownSettingRow`s titled
`send_message_with` and `insert_newline_with`. Each row lists all four
combinations labelled by their localised `text`. The selection handler
`setMessageEnterKey(send, selected)` swaps the other preference when the
chosen combination is already taken, then writes the new value, which
updates the `SharedPreference.state` both rows observe.

### Affected documentation

- `product/views/settings.md` — platform differences row and the new
  section.
- `product/concepts.md` — new concept row PC32.
- `spec/state.md` — `AppPreferences` location and the new preference
  category.
- `spec/impact.md` — PC32 added to the affected files.
- `TODO.md` — the implementation note for item 1.

## Detailed implementation steps

1. Add strings (`settings_message_sending`, `send_message_with`,
   `insert_newline_with`, `enter|shift_enter|ctrl_enter|alt_enter_key_combination`)
   to `MR/base/strings.xml` and `MR/zh-rCN/strings.xml`.
2. Add `ChatEnterKeyCombination` and the two preferences (plus their keys)
   to `common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`.
3. Add the desktop-only section and `setMessageEnterKey` to
   `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/SettingsView.kt`.
4. Rework the Enter branch of `PlatformTextField.desktop.kt` to use the
   preferences, extracting the newline insertion into a local function so
   both paths share it.
5. Add `common/src/commonTest/kotlin/chat/simplex/app/ChatEnterKeyCombinationTest.kt`
   covering `matches` (exact modifier matching, `meta` rejection) and the
   default pair being distinct.
6. Run `./gradlew :common:desktopTest` and confirm a green run.
7. Update the documents listed above, store the diff under `diff/`, and
   commit.

## Adversarial self-review

- Could the two rows ever hold the same combination? No: the handler
  swaps before writing, and the stored values are only written through
  that handler. A hand-edited settings file with equal values degrades
  safely — the send rule wins and the newline row simply never fires,
  while plain `Enter` still inserts a newline.
- Could an unassigned combination send by accident? No: sending requires
  an exact match on the send combination, `meta` always disqualifies.
- Does this affect Android or iOS? No: the section is desktop-guarded and
  the only key handling that changed is the desktop `actual`.
- Does it change the 7.0.4 default behaviour? No: `Enter` sends,
  `Shift+Enter` inserts — exactly as before.
