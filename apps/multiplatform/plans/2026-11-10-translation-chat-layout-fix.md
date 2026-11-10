# Fix the translated-message line in the classic and flat chat layouts

Date: 2026-11-10 · Baseline: SimpleX Chat 7.1.2 · Scope: `apps/multiplatform` (client only)

## 1. Problem statement

After the fork was rebased onto official 7.1.2, a translated message drew its bilingual line on top of
the message instead of below it, and the time/status meta landed on the translation. The report
(m00135) confirms the classic bubble layout is affected too, not only the new flat (`Terminal`) layout:
at larger font scales the translated glyph lines also overlapped each other.

Three independent causes:

1. **The 7.1.2 `ItemWithMeta` refactor moved the translation out of the message column.** Upstream
   replaced the ad-hoc meta placement with `ItemWithMeta(meta = …, content = …)`, and the rebase put
   `TranslationUnderBubble` after the content `Column` but still inside
   `Box(contentAlignment = Alignment.BottomEnd)` in `FramedItemView.kt`. A bottom-end sibling is aligned
   with the content's last line, so in the classic bubble the translation covered the last text line and
   `bubbleMeta()` put the time on the translation's baseline.
2. **The translation kept the pre-7.1.2 fixed line height.** `TranslationUnderBubble` used
   `body1.copy(color = secondary, lineHeight = 22.sp)`; 7.1.2 draws message text (and bubbles) with
   `chatTextStyle`, whose line height scales as `fontSize × 22/16 × f(contrast)`. At raised font sizes
   the fixed 22sp was too small, so wrapped translated lines overlapped each other.
3. **The flat layout was never adapted.** In terminal rows the same sibling was measured as part of the
   content by `TerminalMetaLayout`, so the time/reactions attached to the translation, and the fixed
   bubble-era 12dp insets did not line up with the flat text edge.

The translation service, preferences, `ChatItem` data and the core are not involved.

## 2. Solution summary

- Render `TranslationUnderBubble` after `ItemWithMeta` in an outer `Column` in both layouts, so the
  translation is always below the whole item (message + time/reactions). The classic bubble background
  grows to contain it; in terminal rows `TerminalMetaLayout` measures only the message, keeping the
  time/reactions on the message's own last line.
- Keep the message tail inset on the translation wrapper, so a sent bubble's tail still shows beside it.
- Give the translation the 7.1.2 typography (`chatTextStyle(body1.fontSize, secondary)`) and the text
  edge padding (`CHAT_ITEM_TEXT_PADDING`, 12dp), with a 2dp top gap in terminal rows.
- No new state, no `AppSettings` change, no core/protocol change; still display-only and
  non-destructive (RULE-31).

## 3. Detailed technical design

### 3.1 Placement in `FramedItemView`

`FramedItemView` hoists `meta`, `overlayMeta` and `tailRendered` and extracts the old
`Box(contentAlignment = Alignment.BottomEnd)` body into a local `Content(inlineMeta)` composable. The
outer shape becomes:

```kotlin
Column {
  ItemWithMeta(meta, overlayMeta, metaReserved, endButton, gapBelowCard = terminalReactionsGap(ci).toDp()) { Content(it) }
  Box(Modifier.padding(start = if (!sent && tailRendered) msgTailWidthDp else 0.dp,
                       end = if (sent && tailRendered) msgTailWidthDp else 0.dp)) {
    TranslationUnderBubble(ci, linkMode)
  }
}
```

`:L483-L495` in `FramedItemView.kt`. `Content` keeps the message column and the `bubbleMeta()` time box
unchanged (`:L276-L478`), which is why the time still sits on the message's last baseline in both
layouts.

### 3.2 Why below `ItemWithMeta`, not inside the content

A probe confirmed that custom `VerticalAlignmentLine`s (`LastLineRight`, `CardTop`, `CardBottom`,
`LastBaseline`) propagate through `Box`, `Column`, `PriorityLayout` and modifier chains. If the
translation stays inside the measured content, `TerminalMetaLayout` sees the translation's baseline as
the content's last baseline and moves the time onto it; in the classic layout `bubbleMeta()` does the
same through `Box(BottomEnd)`. Measuring the translation outside `ItemWithMeta` removes it from the
meta placement entirely.

### 3.3 Typography and insets

`TranslationUnderBubble` (`:L548-L603`) now builds

```kotlin
val translationStyle = body1.copy(color = secondary, lineHeight = 22.sp)
  .merge(chatTextStyle(body1.fontSize, secondary))
val retryStyle = translationStyle.copy(color = MaterialTheme.colors.primary)
```

and pads with `CHAT_ITEM_TEXT_PADDING` on both sides, `top = if (terminal) 2.dp else 0.dp`,
`bottom = 6.dp`. Loading, done and error/retry texts all use these styles. This fixes the overlapping
glyph lines and aligns the flat layout with the message text edge.

## 4. Implementation steps

1. Hoist `meta`, `overlayMeta`, `tailRendered`; extract `Content(inlineMeta)` in `FramedItemView`.
2. Move `TranslationUnderBubble` out of `Content` into the outer `Column` after `ItemWithMeta`, with the
   tail-width padding box.
3. Apply `chatTextStyle` + `CHAT_ITEM_TEXT_PADDING` in `TranslationUnderBubble`.
4. Compile (`:common:compileKotlinDesktop`), run the layout test classes, and visually check classic and
   terminal previews with a temporary seeded translation.
5. Update `spec/client/chat-view.md`, `spec/client/translation.md`, `spec/impact.md`,
   `product/views/chat.md`, `product/README.md`, `product/concepts.md`, `product/rules.md` (RULE-31) and
   this plan; add the TODO entry and the patch under `diff/`.

## 5. Verification

- `./gradlew :common:compileKotlinDesktop` — passed.
- `./gradlew :common:desktopTest --tests BubbleLayoutTest --tests PriorityLayoutTest --tests
  TerminalMetaLayoutTest --tests TerminalMarkdownGapsTest --tests TerminalMetaCopyTest --tests
  TerminalChatItemRowTest` — passed.
- Temporary render probe (deleted afterwards): with a `LOADING` translation seeded in
  `TranslationService`, the classic preview shows the time on the original line and the translation on
  the next line inside the bubble; the terminal preview shows the time line and the translation below
  it, left-aligned at 12dp. A `DONE` translation cannot be rendered in desktop tests because the test
  environment has no Linux core library (`chatParseMarkdown` `UnsatisfiedLinkError`).
