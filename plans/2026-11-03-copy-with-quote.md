# Copy a reply together with its quote

## Problem

Copying a message that replies to another message loses the context: the clipboard gets only the reply
text, so a pasted conversation no longer shows what was being answered. The user asked for the quoted
message to be put in front of the body as `[引用：“$MSG”——$USER_NAME]`.

## Solution

- `model/ChatModel.kt`:
  - `dirSenderName(dir: CIDirection?, chatInfo, ownName)` — the existing name resolution extracted from
    `ciSenderName`, so it can also answer "who wrote the quoted message" (a `CIQuote` carries its own
    `chatDir`).
  - `ciQuoteLine(ci, chatInfo, ownName): String?` — builds `[引用：“text”——author]` from
    `ci.quotedItem`, folding the quoted text onto one line; returns null when there is no quote or the
    quoted text is blank, and omits the author for a channel post.
  - `ciTextWithQuote(ci, chatInfo, ownName)` — quote line + `ci.text`.
  - `chatItemToText` now uses `ciTextWithQuote` for the body, so a multi-message copy has the header
    (`name, time`) first and then the quote line.
- `views/chat/item/ChatItemView.kt`: the single-message **Copy** action passes
  `ciTextWithQuote(cItem, cInfo, ownName)` to `copyItemToClipboard`, whose signature gained a `text`
  parameter (expect + both actuals in `androidMain` / `desktopMain`), so the platform copy only has to put
  the composed string on the clipboard (the desktop file-path branch is unchanged).
- Strings `copy_quote_with_author` (`[Quote: “%1$s”——%2$s]` / `[引用：“%1$s”——%2$s]`) and `copy_quote`
  (`[Quote: “%1$s”]` / `[引用：“%1$s”]`) next to `copy_verb`.

## Design decisions

- **The marker follows the interface language** (user decision): the quotes and brackets come from a
  string resource instead of being hard-coded Chinese, so an English UI says `[Quote: “…”——Alice]`. The
  separator `——` is kept in both languages, as requested.
- **Own quotes are named like own messages** (user decision): the author comes from `dirSenderName` on
  the quote's direction, so a quote of your own message shows your group member name (or profile name)
  rather than the "you" pronoun.
- **One line, not the original layout.** The quote sits inside brackets, so newlines and runs of spaces
  are folded into single spaces; the text itself is never truncated, so nothing is silently lost.
- **Degrade instead of empty placeholders.** A quote whose text is blank produces no line at all; a
  channel quote (no sender) produces the bracket without the author.
- **Both copy paths.** The multi-message copy (which has a header) and the single-message menu copy (which
  does not) share `ciQuoteLine`, so they cannot disagree. The per-version copy inside the message-info
  view is deliberately left alone: it copies one historical version of the text, not the message as it
  was sent.
- **No new state, no core call.** The quote is already in the `ChatItem` (`quotedItem`), so copying stays
  a pure client-side operation and nothing is added to `AppSettings`.

## Steps

1. Extract `dirSenderName`, add `ciQuoteLine` / `ciTextWithQuote`, use them in `chatItemToText`.
2. Thread the composed text through `copyItemToClipboard` (expect + android/desktop actuals + call site).
3. Strings (base + zh-rCN).
4. `CopiedQuoteTest.kt` (10 cases), `./gradlew :common:desktopTest`,
   `./gradlew :common:compileDebugKotlinAndroid`.
5. Docs: `product/rules.md` (RULE-26 invariant 6), `spec/client/chat-view.md`, `product/views/chat.md`,
   `spec/impact.md`, `TODO.md`.
6. Write `diff/0021-copy-with-quote.patch` and commit.

## Verification

- `./gradlew :common:desktopTest` → `BUILD SUCCESSFUL`; `CopiedQuoteTest` 10/10, 25 suites, no failures.
- `./gradlew :common:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL` (the changed android actual compiles).
