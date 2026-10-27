# Batch copy of selected messages

## Problem

The multi-select toolbar (`SelectedItemsButtonsToolbar`) can delete, moderate, archive and forward
selected messages, but not copy them. Copying one message at a time is only possible from the
single message menu, and it copies the bare text without saying who sent it or when.

A user who wants to keep a part of a conversation (for a support case, a quote in another app, a
backup of a discussion) has to copy messages one by one and re-add the authors and timestamps by
hand.

## Solution

Add a copy button to the multi-select toolbar that puts all selected messages on the clipboard as a
single piece of text, each message rendered as:

```
<name of the sender>, <time the message was sent>
<the message text>
```

with messages separated by a blank line.

Requirements from the request:

* the sender name, using the local alias ("备注") when one is set;
* the send time, not the receive time;
* the current (latest) version of the text, i.e. the text after edits.

## Design decisions

* **Client-side only.** The text is composed from the `ChatItem`s already in the model, so no API
  call, no core change and no new stored state are needed.
* **The three required pieces already exist on `ChatItem`:**
  * `meta.itemTs` is the send timestamp - `meta.createdAt`, which is the receive timestamp, is
    deliberately not used. `CIMeta.timestampText` renders `itemTs` too, so the copy matches what the
    chat shows.
  * `content.text` is the text of the current version: `ChatItem.meta.itemText` and
    `ChatItem.content` are replaced by the core on every edit, while older versions live only in
    `ChatItemInfo.itemVersions`, which is fetched on demand.
  * the sender name comes from `ciSenderProfile(ci, chatInfo)` - the group member's profile for a
    received group message, the contact's profile for a received direct message. `LocalProfile`
    resolves the name with `localAlias.ifEmpty { displayName }` (`NamedChat.chatViewName`), so an
    alias wins over the profile name.
* **Own messages have no sender name.** The item is shown in the chat with its timestamp alone, so it
  is copied the same way: no name, just the time. This also keeps the copy of a chat with yourself
  readable.
* **The time format is the existing one.** `getTimestampText(itemTs)` is reused, so the copied line
  matches the timestamp in the chat and stays localized without new strings (only the separator and
  the toast need a string).
* **Files and voice messages** are copied as their description rather than as a path: the single item
  copy action on desktop saves/loads the file and copies the path, which cannot be done for a whole
  selection without long API calls per item. `ChatItem.text` already yields the file name or the
  voice message duration description.
* **Which items can be copied:** any selected item that has text - the same condition the forward
  action uses minus the states that cannot be rendered (`itemDeleted`, `isLiveDummy`, reports).
  The button is disabled when nothing copyable is selected, and the copy is capped at 200 items, the
  same limit as delete, so that a huge selection cannot freeze the UI or blow the clipboard.

## Steps

1. `model/ChatModel.kt`: a pure formatter
   `internal fun chatItemsToText(items: List<ChatItem>, chatInfo: ChatInfo, timeText: (Instant) -> String): String`
   with helpers `internal fun chatItemSenderName(ci: ChatItem, chatInfo: ChatInfo): String?` and
   `internal fun chatItemToText(ci: ChatItem, chatInfo: ChatInfo, timeText: (Instant) -> String): String`.
   Keep them pure (the time formatter is injected) so they can be unit tested without a UI.
2. `views/chat/SelectableChatItemToolbars.kt`: a copy `IconButton` (`MR.images.ic_content_copy`,
   primary tint) in `SelectedItemsButtonsToolbar`, next to delete/moderate/forward; enablement computed
   in `recheckItems` like the other buttons.
3. `ChatView.kt`: pass the `copyItems` lambda to both toolbar call sites (the report-content one is
   `{}` like `moderateItems` there, since reports are not copyable).
4. Strings: `copy_selected_messages` (content description) in `MR/base/strings.xml` and
   `MR/zh-rCN/strings.xml`.
5. Tests in `common/src/commonTest/kotlin/chat/simplex/app/SelectedMessagesCopyTest.kt` covering the
   formatter: group message with alias, group message without alias, direct message, own message,
   several messages separated by a blank line, order, file message, deleted item skipped.
6. Docs: `product/`, `spec/`, `product/concepts.md`, `spec/impact.md`, this plan, `TODO.md` note.

## Adversarial review

* A selection can contain items the user cannot see (merged, hidden, deleted): `recheckItems` filters
  them the same way for every other action, and the formatter skips empty renderings, so no empty
  lines are produced.
* The selection order in `selectedChatItems` is a `Set`, so the formatter must sort by the chat items
  list, not by the set, to keep the conversation order.
* Copying a very large selection must not block: the cap is 200 items, the same as delete.
* `content.text` for a deleted message is a placeholder line; nothing should be copied for it, but it
  is kept if the user selected it explicitly (it is what the chat shows).
