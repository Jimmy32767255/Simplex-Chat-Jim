# Settings

> **Related spec:** [spec/client/navigation.md](../../spec/client/navigation.md) | [spec/services/theme.md](../../spec/services/theme.md) | [spec/services/notifications.md](../../spec/services/notifications.md)

## Purpose

Configure all aspects of app behavior including notifications, network/servers, privacy, appearance, database management, call settings, and developer tools. Accessed from the UserPicker or directly from the chat list toolbar.

## Route / Navigation

- **Entry point**: Tap user avatar in `ChatListView` toolbar -> `UserPicker` -> Settings option; or directly via `NavigationButtonMenu` when no users exist
- **Presented by**: `SettingsView` composable via `ModalManager.start.showModalCloseable`
- **Navigation title**: "Your settings" (`AppBarTitle`)
- **Sub-navigation**: Each settings row opens a dedicated view via `showSettingsModal` or `showCustomModal`

## Platform Differences

| Aspect | Android | Desktop |
|---|---|---|
| App section | Device settings, app version | App updates (`AppUpdater`), device settings, app version |
| Notifications | Full notification mode selection (instant/periodic/off) | Notification settings |
| Message sending keys | Not available (IME decides) | "Message sending" section with send / newline Enter key dropdowns |
| Use from desktop/mobile | "Use from desktop" option in UserPicker | "Link a mobile" / "Linked mobiles" option in UserPicker |
| Database migration | "Migrate to another device" with auth | Same |

## Page Sections

### Settings Section

| Row | Icon | Destination | Description |
|---|---|---|---|
| Notifications | `ic_bolt` / `ic_bolt_off` | `NotificationsSettingsView` | Push notification mode and preview settings |
| Network & servers | `ic_wifi_tethering` | `NetworkAndServersView` | SMP/XFTP servers, proxy, .onion hosts, advanced network |
| Audio & video calls | `ic_videocam` | `CallSettingsView` | WebRTC relay policy, ICE servers |
| Privacy & security | `ic_lock` | `PrivacySettingsView` | SimpleX Lock, delivery receipts, link previews, auto-accept |
| Appearance | `ic_light_mode` | `AppearanceView` | Theme, language, profile images, chat bubbles |

All rows disabled when `chatModel.chatRunning != true` (except Appearance).

#### Notifications (`NotificationsSettingsView`)

| Setting | Options |
|---|---|
| Notification mode | Instant (background service) / Periodic (every 10 min) / Off |
| Notification preview | Configuration for notification content visibility |

#### Network & Servers (`NetworkAndServersView`)

| Setting | Description |
|---|---|
| SMP servers | Messaging relay servers; per-operator configuration |
| XFTP servers | File transfer servers; per-operator configuration |
| Server operators | `OperatorView` for each configured operator |
| Advanced network | `AdvancedNetworkSettings` -- timeouts, TCP keep-alive, reconnect intervals |
| Proxy configuration | SOCKS proxy, .onion host settings |

Sub-files: `NetworkAndServers.kt`, `ProtocolServersView.kt`, `ProtocolServerView.kt`, `NewServerView.kt`, `ScanProtocolServer.kt`, `AdvancedNetworkSettings.kt`, `OperatorView.kt`, `ServersBackup.kt`

##### Adding Servers Manually (`NewServerView`)

Reached from "Your servers" → "Add server" → "Enter manually" ("Add server by QR code" and the
chat-relay option sit next to it). Used for both adding a server and editing an existing one.

| Element | Behaviour |
|---|---|
| Server address field | Multi-line. When adding, it accepts one address per line, so several servers can be added in one go; blank lines are ignored and duplicates are added once. When editing an existing server the field holds a single address. |
| Hint footer | Shown only in the add form: "You can add several servers at once: enter one address per line. The settings below are applied to all of them." |
| Test server | Tests one address. Disabled while more than one address is entered, because the result would be ambiguous. |
| Use for new connections | One toggle for the whole form; the same setting is applied to every server of the batch. |
| Roles section | Same as before; applied to every server of the batch. |
| QR code | Shown only while exactly one valid address is entered. |
| Closing the dialog | Saves (the modal's close action *is* the save) and returns to the server list. Valid addresses are added even when some lines are invalid; the invalid ones are reported in an alert. |
| Validation | Each line is validated independently; the warning icon appears while any line is invalid. The add button itself is never blocked by invalid lines. |

Editing an existing server keeps the previous single-address behaviour (`allowMultipleAddresses`
defaults to `false`).

##### Grouping and Notes

Manually added servers can be organised into named groups and given a free-form note, so the list
does not read as a bare list of addresses.

| Element | Behaviour |
|---|---|
| Group field | Optional group name, in the add form and in the edit modal (`ServerGroupAndNoteSection`). Servers sharing a group name are shown together under a small caption header. |
| Note field | Optional free-form note, shown as a second line under the server address in the list. |
| Footer | "Group and note are stored only on this device and are never sent to the servers." |
| Grouped list | `ServersSection` renders servers without a group first (original order), then each group in the order it first appears, keeping the original list indices for navigation. |
| Renaming an address | The metadata entry follows the address: it is moved to the new key and the old key is dropped. |

Group and note live in the client-only `serverMetadata` map preference, keyed by the server address
string. They are deliberately *not* fields of `UserServer`: that structure is serialized into the
`/_servers` core command and read back from the core, so client-only fields would be lost on the
next round-trip (see `spec/api.md` §2.10).

##### Import and Export (`ServersImportExportSection`)

Two rows of the "Network & servers" page (`NetworkAndServers.kt`, between the proxy settings and
the "Save servers" row) that move the custom server list between devices or profiles through a JSON
file (`ServersBackup.kt`).

| Element | Behaviour |
|---|---|
| Export servers | Confirmation dialog with the leak warning ("the exported file contains the addresses and verification keys of your custom servers"), then a save-file chooser writing `simplex-servers.json`. |
| Import servers | Confirmation dialog with the trust warning ("only import servers you trust"), then an open-file chooser; the file is parsed and validated before anything is written. |
| Exported content | Only custom servers: preset and deleted servers are skipped. Every entry keeps the protocol list (SMP/XFTP), `enabled`, `tested`, the role overrides, the operator tag and the client-side group/note. |
| Import preview | The confirmation names how many servers will be added, how many are already present (skipped as duplicates) and how many addresses are invalid (skipped). |
| Import merge | Addresses are validated with the core parser and must match the protocol they were exported from; valid new servers are appended to the operator group with the same operator tag (or to the group without an operator), the existing list is never replaced, and the merged list is saved through the same `saveServers` path as manual edits. |
| Footer | "Only your custom (non-preset) servers are exported, including their groups and notes. Preset servers are not included." |

The JSON format is client-only and versioned (`ServersBackup.version = 1`); it is not part of
`AppSettings` and does not change the `/_servers` core command.

#### Audio & Video Calls (`CallSettingsView`)

| Setting | Description |
|---|---|
| WebRTC relay policy | Always relay / relay when needed / never relay |
| ICE servers | Custom STUN/TURN server configuration |

#### Privacy & Security (`PrivacySettingsView`)

Organized in sections:

**Device Section** (`PrivacyDeviceSection`):

| Setting | Description |
|---|---|
| SimpleX Lock | `SimplexLockView` -- app lock with system auth or passcode (`LAMode.SYSTEM` / `LAMode.PASSCODE`) |

**Chats Section**:

| Setting | Preference Key | Description |
|---|---|---|
| Send link previews | `privacyLinkPreviews` | Auto-generate link preview cards |
| Sanitize links | `privacySanitizeLinks` | Strip tracking parameters from URLs |
| Show last messages | `privacyShowChatPreviews` | Show message previews in chat list |
| Message draft | `privacySaveLastDraft` | Save unsent message draft for each chat |

**Files Section**:

| Setting | Preference Key | Description |
|---|---|---|
| Encrypt local files | `privacyEncryptLocalFiles` | Encrypt files stored on device |
| Auto-accept images | `privacyAcceptImages` | Automatically download received images |
| Blur media radius | `privacyMediaBlurRadius` | Blur radius for media previews |
| Protect IP address | `privacyAskToApproveRelays` | Prompt before connecting to unknown file relays to protect IP address |

#### Appearance (`AppearanceView`)

Platform-specific composable (`expect fun AppearanceView`):

| Setting | Description |
|---|---|
| Profile images | `ProfileImageSection` -- slider for profile image corner radius |
| Theme selection | Color scheme / theme picker |
| Language | App language selection |
| Chat wallpaper | Background image settings |
| Chat bubbles | Message bubble appearance configuration |
| Toolbar opacity | App bar transparency settings (`inAppBarsAlpha`) |
| Color picker | `ClassicColorPicker` for custom theme colors |
| Rainbow mode | `rainbowThemeColors` -- cycles the hue of every interface color while the app is open; display-only, the saved colors are not changed (RULE-29). The toggle sits in *Customize theme > Interface colors*, and the color swatches there animate with the interface |
| Rainbow speed | `rainbowSpeed` -- `Slow` / `Normal` / `Fast`, one full hue cycle in 32 / 16 / 8 seconds. Only shown while rainbow mode is on |
| Rainbow chat colors | `rainbowChatColors` (on by default) -- the chat bubbles cycle with the rest of the interface; turning it off leaves them in the saved colors |
| Rainbow message text | `rainbowMessageText` -- draws the message text itself in rainbow colors. Mutually exclusive with *Rainbow chat colors*: enabling one clears the other, so the text always sits on a plain bubble (RULE-29) |
| Low performance mode | `rainbowLowPerformance` -- updates the hue four times per second instead of twenty: visible steps, but far less recomposition, which keeps the home screen smooth on slower phones |

### Chat Database Section

| Row | Icon | Destination | Description |
|---|---|---|---|
| Database passphrase & export | `ic_database` | `DatabaseView` | Manage encryption, export/import database |
| Migrate to another device | `ic_ios_share` | `MigrateFromDeviceView` | Device migration (requires auth) |

Database icon shows warning color (`WarningOrange`) when database is not encrypted or passphrase is not saved.

### Help Section

| Row | Icon | Destination | Description |
|---|---|---|---|
| How to use SimpleX Chat | `ic_help` | `HelpView` | Usage guide |
| What's new | `ic_add` | `WhatsNewView` | Version changelog |
| About SimpleX Chat | `ic_info` | `SimpleXInfo` (non-onboarding mode) | App information |
| Chat with the founder | `ic_tag` | Opens SimpleX link | Direct chat with SimpleX team |
| Send us an email | `ic_mail` | Opens mailto: | Email support |

### Support Section

| Row | Icon | Description |
|---|---|---|
| Contribute | `ic_keyboard` | Opens GitHub contribution page (hidden for Android Bundle) |
| Rate the app | `ic_star` | Opens Google Play / app store listing |
| Star on GitHub | `ic_github` | Opens GitHub repository |

### App Section (`SettingsSectionApp`)

Platform-specific section (expect/actual composable):

| Row | Description |
|---|---|
| App updates (Desktop) | App update checker and installer |
| Developer tools | Toggle developer mode |
| Storage (developer options) | Opens `StorageView`: size of each top-level entry in the app's data, config and temp folders |
| Chat console | Opens `ChatConsoleView` terminal |
| Terminal always visible (Desktop) | Keep terminal window open |
| Install terminal app | Link to CLI app on GitHub |
| Reset all hints | Reset dismissed hint/card preferences |
| App version | Version string with build info; taps open `VersionInfoView` |

### Chat List Section

Rendered on all platforms, between the main settings section and "Advanced settings".

| Row | Preference Key | Default | Description |
|---|---|---|---|
| Treat favorites as pinned | `favoritesAsPinned` | Off | Favorite contacts and groups are always shown at the top of the home chat list, above the other chats |

The footer explains that favorited contacts and groups will always be shown at the top of the chat list.
The switch only changes the displayed order (`favoritesPinnedFirst` in `ChatListView`); the chat order
stored in the model, the core and the database is untouched, and switching it off restores the previous
order (see `product/rules.md` RULE-25).

### Time Format Section

Rendered on all platforms, right after the "Chat list" section: a single dropdown row (`ic_schedule`
icon) with `SYSTEM`, `HOUR_12` and `HOUR_24`, defaulting to `SYSTEM`.

| Row | Preference Key | Values | Description |
|---|---|---|---|
| Time format | `timeFormat` | System default / 12-hour / 24-hour | 12/24-hour convention used by every time of day |

The footer explains that the setting controls the times shown in chats and in message details. The
value is resolved by `uses24HourFormat` (`SYSTEM` defers to `systemUses24HourFormat()`), and it takes
effect immediately because the resolver reads the preference state; see `product/rules.md` RULE-27.
It is a local display preference — not sent to the core and not part of the `AppSettings` backup.

### Message Sending Section (desktop only)

Rendered only when `appPlatform.isDesktop`. Both rows offer the same four combinations: `Enter`, `Shift + Enter`, `Ctrl + Enter`, `Alt + Enter`.

| Row | Preference Key | Default | Description |
|---|---|---|---|
| Send message with | `messageSendKey` | `Enter` | Enter combination that sends the composed message |
| Insert newline with | `messageNewlineKey` | `Shift + Enter` | Enter combination that inserts a newline at the caret |

The two combinations are always kept distinct: choosing the combination already used by the other row swaps the two values. Any other Enter combination, including `Cmd`/`Win` + `Enter`, does nothing; a plain `Enter` (no modifier) always inserts a newline when it is not the send key, so a newline is never unreachable.

### Chat Translation Section (`TranslationSettingsView`)

A translation sub-page reached from the main settings list (title string `translation_settings`, shown as
「翻译消息 / Translate messages」, with summary `translation_settings_summary`). It configures the in-app
translation service (see `product/rules.md` RULE-31). Every value is stored locally in `AppPreferences`
(JSON string `translationConfig`, plus `translationAutoTranslate`) and is **not** part of the
`AppSettings` backup; secrets are encrypted with `Cryptor` and stored in `translationSecrets` +
`translationSecretsIv`.

| Row | Preference Key | Default | Description |
|---|---|---|---|
| Provider | `provider` (`TranslationProvider`) | `GOOGLE` | `GOOGLE` (free), `BING` (free), `BAIDU` (needs an API Key) or `AI` (OpenAI Responses protocol, DeepSeek default) |
| Default target language | `targetLang` | `English` | Language used by a single tap on the compose-bar translate button |
| Auto-translate | `translationAutoTranslate` | Off | Translate the messages currently visible in the viewport; a message's in-flight request is cancelled as soon as it scrolls out of view |
| Request interval (ms) | `requestIntervalMs` | `600` | Minimum spacing between translation requests |
| Max concurrent | `maxConcurrent` | `2` | Maximum number of translation requests in flight |
| Batch limit | `batchLimit` | `20` | Maximum number of visible messages handled per batch |

Provider-specific fields appear only for the selected provider:

| Field | Provider | Description |
|---|---|---|
| API Key | Baidu / AI | Encrypted with `Cryptor` before it is written to `translationSecrets` (Google and Bing use keyless public endpoints) |
| Baidu AppID + secret | Baidu | Baidu translate credentials |
| Base URL + model + API Key | AI | Defaults to base URL `https://api.deepseek.com/v1` and model `deepseek-chat` (OpenAI Responses protocol) |

## Source Files

| File | Path |
|---|---|
| `SettingsView.kt` | `views/usersettings/SettingsView.kt` |
| `TranslationSettingsView.kt` | `views/usersettings/TranslationSettingsView.kt` (chat-translation settings page) |
| `Appearance.kt` | `views/usersettings/Appearance.kt` |
| `PrivacySettings.kt` | `views/usersettings/PrivacySettings.kt` |
| `NetworkAndServers.kt` | `views/usersettings/networkAndServers/NetworkAndServers.kt` |
| `AdvancedNetworkSettings.kt` | `views/usersettings/networkAndServers/AdvancedNetworkSettings.kt` |
| `OperatorView.kt` | `views/usersettings/networkAndServers/OperatorView.kt` |
| `ProtocolServersView.kt` | `views/usersettings/networkAndServers/ProtocolServersView.kt` |
| `NewServerView.kt` | `views/usersettings/networkAndServers/NewServerView.kt` |
| `ServersBackup.kt` | `views/usersettings/networkAndServers/ServersBackup.kt` (custom-server JSON backup and merge logic) |
| `PlatformTextField.desktop.kt` | `platform/PlatformTextField.desktop.kt` (Enter key handling for send / newline) |
