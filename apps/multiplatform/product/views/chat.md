# Chat View (Conversation)

> **Related spec:** [spec/client/chat-view.md](../../spec/client/chat-view.md)

## Purpose

Full conversation view for displaying and interacting with messages in a direct contact chat, group chat, or note-to-self. Supports text messaging with markdown, media attachments, voice messages, E2E encrypted calls, message reactions, replies, forwarding, reporting, and content search/filtering.

## Route / Navigation

- **Entry point**: Tap a chat row in `ChatListView` (routed by `ChatListNavLinkView`)
- **Presented by**: `ChatView` composable bound to `chatModel.chatId`; on Desktop, shown in the center column
- **Back navigation**: Sets `chatModel.chatId = null`, stops `AudioPlayer`, clears group members, returns to chat list
- **Sub-navigation**:
  - Info button opens `ChatInfoView` (contact) or `GroupChatInfoView` (group) via `ModalManager.end`
  - Member avatars in group chats navigate to `GroupMemberInfoView`
  - Reports button opens `GroupReportsView` for groups with moderation reports
  - Support chats button opens `MemberSupportView` (moderators) or member support chat (regular members)

## Page Sections

### Navigation Bar (`ChatLayout`)

Custom toolbar with themed background:

| Element | Description |
|---|---|
| Back button | Returns to chat list; stops audio/video playback |
| Contact/Group avatar | Small profile image in toolbar |
| Chat name | Display name; tappable to open info view. In direct chats that are ready and active the name is preceded by the connection network status dot (`SubscriptionStatusDot`: filled green circle = connected, pending icon = connecting, error icon = subscription removed or missing), resolved per connection by `ChatModel.connectionStatus` (live status event, then the last fetched connection stats) and updated from `CR.SubscriptionStatusEvt` |
| Verified shield | Shows verified contact checkmark (direct chats with verified contacts only) |
| More menu button | Opens overflow menu containing search and audio/video call buttons (call buttons shown in direct chats only) |
| Info button | Opens `ChatInfoView` (direct) or `GroupChatInfoView` (group) |
| Reports count | Badge for group reports count; taps open reports view |
| Support chats | Badge for member support; taps open support chat view |

### Message List

Rendered by `LazyColumnWithScrollBar` with pagination:

| Feature | Description |
|---|---|
| Scroll direction | Bottom-to-top (newest messages at bottom) |
| Pagination | `apiLoadMessages` called on scroll to load more; supports `.before`, `.after`, `.around`, `.initial` |
| Merged items | Adjacent messages grouped with `ItemSeparation` (timestamp, large gap, date separators) |
| Floating buttons | Scroll-to-bottom button with unread count |
| Date separators | Date headers between messages from different days |
| Wallpaper | Per-chat themed background via `perChatTheme` from contact/group `uiThemes`; terminal layout: background colour without the image |
| Content filter | Filter messages by type via `ContentFilter` (images, files, links, etc.) |

### Message Types

Each type has a dedicated composable in `views/chat/item/`:

| Type | Composable | Description |
|---|---|---|
| Text | `FramedItemView` | Rendered with markdown (bold, italic, code, links, `@mentions`) via `CIMarkdownText` |
| Image | `CIImageView` | Thumbnail with tap-to-fullscreen via `ImageFullScreenView`; animated GIFs play inline and full screen |
| Video | `CIVideoView` | Video thumbnail with play button; inline playback via `VideoPlayerHolder` |
| Voice | `CIVoiceView` | Waveform visualization with playback controls and duration |
| File | `CIFileView` | File icon, name, size; download/open actions with progress indicator |
| Link preview | `ChatItemLinkView` | URL preview card with title, description, image (defined in `LinkPreviews.kt`) |
| Emoji-only | `EmojiItemView` | Large emoji rendering without message bubble |
| Call event | `CICallItemView` | Call status (missed, ended, duration) |
| Group event | `CIEventView` | Member joined/left, role changes, group updates |
| E2EE info | `CIChatFeatureView` | Encryption status and feature change notifications |
| Group invitation | `CIGroupInvitationView` | Inline group join invitation card |
| Deleted | `DeletedItemView` / `MarkedDeletedItemView` | Placeholder for deleted messages |
| Decryption error | `CIRcvDecryptionError` | Error with ratchet sync suggestion |
| Invalid JSON | `CIInvalidJSONView` | Developer fallback for malformed items |
| Integrity error | `IntegrityErrorItemView` | Message integrity/gap warnings |

### Message Interactions

Long-press context menu on any message:

| Action | Description |
|---|---|
| Reply | Sets compose bar to reply mode with quoted message (`ComposeContextItem.QuotedItem`) |
| Forward | Opens destination picker; uses `apiPlanForwardChatItems` with confirmation for partial forwards |
| Copy | Copies message text to clipboard, with a `[引用：“text”——author]` line in front when the message is a reply |
| Edit | Enters edit mode (`ComposeContextItem.EditingItem`); own messages within edit window |
| Delete | Delete for self or delete for everyone (with confirmation via `deleteMessagesAlertDialog`) |
| Moderate | Group moderators can delete messages for all members (`moderateMessagesAlertDialog`) |
| React | Emoji reaction picker |
| Report | Report message to group moderators (`ComposeContextItem.ReportedItem` with `ReportReason`) |
| Translate | Translate the message with the configured provider and show the result as a bilingual bubble (原文 + 译文) below the message, without touching the original (RULE-31) |
| Select multiple | Enters multi-select mode (`selectedChatItems`) with bulk delete/forward/archive/copy toolbar |
| Archive | Archive selected reports (moderators) |

### Compose Bar (`ComposeView` + `SendMsgView`)

Bottom input area for composing messages:

| Element | Description |
|---|---|
| Text field | `PlatformTextField` with markdown support, `@mention` autocomplete, file paste support |
| Attachment button | Opens `ModalBottomSheetLayout` with options: camera, gallery (image/video), file |
| Emoji button | Desktop only (Android keyboards have their own emoji panel). Opens a popup with search and categories; a picked emoji is inserted at the cursor and the picker stays open. With text selected the emoji goes after it, as the text field drops its selection when the picker takes focus. Picked emoji are not remembered, for privacy. Esc, a click outside or the button closes it |
| Preview button | Eye icon before the send button, shown while there is text to render and the composition is not a voice message. Opens a read-only modal with the message rendered by the same markdown renderer as the chat, so formatting can be checked before sending (RULE-30) |
| Translate button | 36 dp circular `ic_translate` icon left of the send button. Tap translates the composed text into the default target language; long-press opens a language menu and translates into the chosen language for this message only (the default is unchanged). Tapping again after a translation restores the original; if the text was edited after translating, undo is unavailable and the tap re-translates instead (`ComposeState.translateOriginal`/`translateResult`, `canUndoTranslation`, RULE-31) |
| Send button | Sends message; changes to checkmark for reports; animated size/alpha |
| Voice record button | Shown when text is empty and voice allowed; hold to record, release to preview |
| Live message button | Start/update live typing message (if `liveMessageAlertShown`) |
| Context preview | Shows quoted message, editing indicator, or forwarding source above text field |
| Media preview | Thumbnail row of selected images/videos before sending |
| Link preview | Auto-generated link preview card (`ComposePreview.CLinkPreview`) |
| Connecting status | "Connecting..." text shown when contact is not yet ready |
| Commands menu | Developer commands (`showCommandsMenu`) |

Compose states (`ComposeState`):
- `NoContextItem` -- normal new message
- `QuotedItem` -- replying to a message
- `EditingItem` -- editing own message
- `ForwardingItems` -- forwarding from another chat
- `ReportedItem` -- reporting a message with reason

### Multi-Select Toolbar (`SelectedItemsButtonsToolbar`)

Shown when `selectedChatItems != null`:

| Button | Description |
|---|---|
| Delete / Archive | Delete selected messages (for self, or for everyone if allowed by `fullDeleteAllowed`); shown as Archive for report items (group moderators only) |
| Forward | Forward selected messages to another chat |
| Moderate | Delete selected messages for all members (group moderators only) |
| Select to here | Floating pill at the top edge and at the bottom edge of the list, shown only while messages are selected: extends the selection from the anchor message (the last message the user selected) to that edge row of the visible list, taking every message of the merged rows in between. Hidden when the range would add nothing new |
| Copy | Copies the whole selection as one text block: per message the sender name (the local alias when set; own messages carry the user's own name in that chat), the send time (`meta.itemTs`, not the receive time) and the text of the latest version, ordered from the oldest to the newest as in the conversation and separated by an empty line. Disabled when nothing copyable is selected; capped at 200 items like delete. A reply carries its quote as a `[引用：“text”——author]` line before the body |

Selecting a message also sets the selection anchor (`selectionAnchor`, remembered per chat), so a range can be extended repeatedly: select the first message by hand, scroll, then use the edge button instead of tapping every row. A merged group of messages counts as one row and is taken whole, the same way the collapsed group is taken by a tap.

### Timed/Disappearing Messages

When `timedMessageAllowed` is true, compose bar includes a timer icon for setting message disappear time via `customDisappearingMessageTimePref`.

### Terminal Layout

On Android and desktop, a chat with the terminal layout shows messages as full-width rows without bubbles, starting at one edge for sent and received messages, with the sender's name above each group of messages, again after a pause of 7.5 minutes, and the time at the end of the last line. The wallpaper is drawn as its colour without the image.

A chat uses the user's choice for that chat, otherwise the layout set by the group owner or by the address the contact came from, otherwise the user's choice for the profile and then the app setting, otherwise classic.

## Source Files

| File | Path |
|---|---|
| `ChatView.kt` | `views/chat/ChatView.kt` |
| `ComposeView.kt` | `views/chat/ComposeView.kt` |
| `SendMsgView.kt` | `views/chat/SendMsgView.kt` |
| `ComposeEmojiPicker.kt` | `views/chat/ComposeEmojiPicker.kt` (+ `.android.kt`, `.desktop.kt`) |
| Chat item views | `views/chat/item/*.kt` |
