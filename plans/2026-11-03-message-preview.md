# Message preview next to the send button (feature 4 of the 2026-11-02 request)

## Problem

The composer formats text as markdown, but the user cannot see the result until the message is sent.
For markdown-heavy messages (`**bold**`, `` `code` ``, lists, long paragraphs) this means sending and
then editing. The request was a preview button next to the send button that opens a screen with the
message rendered as it will look.

## Solution

- `views/chat/MessagePreviewView.kt` (new): `messagePreviewAvailable(text, inProgress)`,
  `MessagePreviewView(text)` (a `Surface` bubble with `MarkdownText` + `parseToMarkdown`, then the
  `preview_message_footer` note), `showMessagePreview(text)` (opens a fullscreen modal, no-op for blank
  text) and `MessagePreviewButton(onClick)` (36 dp `ic_visibility` icon in secondary color).
- `views/chat/SendMsgView.kt`: the action area now wraps the send button in a
  `Row(verticalAlignment = Alignment.CenterVertically)` and puts `MessagePreviewButton` before it, shown
  when `messagePreviewAvailable(cs.message.text, cs.inProgress)` and the composition is not a
  `ComposePreview.VoicePreview`.
- Strings `preview_message` ("Preview message" / 预览消息) and `preview_message_footer` (base + zh-rCN).

## Design decisions

- **Reuse the chat renderer.** `MarkdownText(text, parseToMarkdown(text), …)` is exactly what a text chat
  item uses, so the preview cannot drift from the real rendering (RULE-30 invariant 1). It also gets
  selection, link handling and secret toggling for free.
- **Read-only snapshot.** Opening the preview reads `composeState.value.message.text` and shows it; no
  send, no edit, no writes to `ComposeState`. The draft stays in the composer, so closing the preview
  returns the user exactly where they were.
- **Next to the send button, not in the toolbar.** The request asked for the send button's neighbour, and
  that is where the eye icon is most discoverable. A `Row` keeps the send button's own dropdown menu and
  the `BottomEnd` alignment untouched.
- **Pure availability predicate.** `messagePreviewAvailable(text, inProgress)` is a top-level `internal`
  function so it can be unit-tested without Compose, and so the SendMsgView condition stays one line.
- **Voice compositions have no preview.** A voice message has no text to render; the check lives in
  `SendMsgView` (the caller), keeping the predicate independent of preview types.
- **Honest about approximations.** Mentions, links and emoji need chat context; the preview renders them
  as plain markdown and the footer says so, rather than pretending to be exact.
- **No preference, no core, no `AppSettings`.** The feature is always available and purely local.

## Rejected alternatives

- Rendering a synthetic `ChatItem` through `ChatItemView`: it would give a pixel-perfect bubble, but it
  needs a fake `ChatItem` with `CIMeta`, chat direction and member data, and it pulls the whole item
  pipeline (sizing, timestamps, reactions) into a screen that only needs text formatting.
- A side-by-side editor/preview split in the composer: too much layout change in a small compose bar, and
  the request explicitly asked for a separate screen.
- Previewing attachments as well: out of scope, and images/files already show their own preview.

## Steps

1. `MessagePreviewView.kt`.
2. `SendMsgView` action row.
3. Strings (base + zh-rCN).
4. `MessagePreviewTest.kt` (4 cases), `./gradlew :common:desktopTest`.
5. `./gradlew :common:compileDebugKotlinAndroid`.
6. Docs: `spec/client/compose.md` (§8 Message Preview), `product/views/chat.md`, `product/rules.md`
   (RULE-30), `product/concepts.md` (PC45), `spec/impact.md`, `product/README.md`, `TODO.md`.
7. Write `diff/0020-message-preview.patch` and commit.

## Verification

- `./gradlew :common:desktopTest` → `BUILD SUCCESSFUL`; `MessagePreviewTest` 4/4; 24 suites, no failures.
- `./gradlew :common:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL`.
