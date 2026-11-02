# "Select to here" in multi-select mode (feature 1 of the 2026-11-02 request)

## Problem

Selecting a long stretch of messages requires one tap per message. QQ and WeChat solve this with a
"select to here" (`选择到此处`) action: select the first message by hand, scroll, then extend the
selection to the edge of the screen in one press. SimpleX Chat 7.0.4 has multi-select
(`selectedChatItems`) but no range action.

## Solution

Two floating pill buttons rendered by `BoxScope.ChatItemsList` in
`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatView.kt`, one at
the top edge of the list (under `FloatingDate`) and one at the bottom edge (above the compose view).
Each press unions a range into the current selection, growing from the anchor message to the visible
row at that edge.

## Design decisions

- **Anchor = last selected message.** A per-chat `rememberSaveable` `selectionAnchor: Long?` is
  written by both selection paths (the item's own select action and the checkbox overlay). Deriving
  the anchor from `selectedChatItems` instead would be ambiguous for a `Set<Long>` (no order, and
  range unions make the set grow), and storing it explicitly keeps repeated range presses stable.
- **Ranges work on merged rows, not raw items.** The list renders `MergedItems`, where a group of
  consecutive messages from one sender is a single row that can be collapsed. Taking whole rows means
  the range selects exactly what the user sees, and a collapsed group is selected entirely — matching
  what a tap on the group already does (`selectUnselectChatItem` adds all ids of an unrevealed group).
  Row indexes come from `MergedItems.indexInParentItems`, the same map used for scrolling.
- **Edge rows come from the layout, not from the data.** `listState.layoutInfo.visibleItemsInfo`
  gives the rows actually on screen; with `reverseLayout = true` the top edge is the maximum index and
  the bottom edge the minimum.
- **Pure helpers, unit-tested.** `views/chat/ChatSelectionRange.kt` holds `mergedItemChatItemIds`,
  `selectionRangeRows`, `selectionToHereIds`, `selectionToHereAddsItems` and `visibleEdgeRows` as
  `internal` top-level functions, so the range logic is tested without Compose (9 tests in
  `ChatSelectionRangeTest.kt`). The composable only reads state and applies the result.
- **Additive only.** The action never deselects; a mistaken press is undone by tapping the same item
  again (the existing toggle behavior).
- **Hidden when useless.** No selection, no anchor, or an empty delta hides both pills, so the UI does
  not offer a no-op.
- **Client-side only.** No core command, no database write and no `AppSettings` field: the selection
  never leaves the device until the user runs delete/forward/copy.

## Rejected alternatives

- A context-menu entry in the item long-press menu: the menu only exists for a specific item, while
  the feature needs the screen edge, and the item may be scrolled off screen.
- Selecting everything between the anchor and the *tapped item* instead of the screen edge: that is
  just a normal tap, no new affordance.
- Reusing `selectedChatItems` order to find the anchor: a `Set` has no insertion order, and `remember`
  on a derived value would reset on every recomposition.

## Steps

1. Add the string `select_to_here` (`Select to here` / `选择到此处`).
2. Add `views/chat/ChatSelectionRange.kt` with the helpers.
3. Add `selectionAnchor`, wire both selection call sites, add the two `SelectToHereButton` overlays.
4. Add `ChatSelectionRangeTest.kt` (9 cases), run `./gradlew :common:desktopTest`.
5. Update `product/views/chat.md`, `spec/client/chat-view.md`, `product/rules.md` (RULE-28),
   `product/concepts.md` (PC43), `spec/impact.md`, `TODO.md`.
6. Write `diff/0018-select-to-here.patch` and commit.

## Verification

`./gradlew :common:desktopTest` → `BUILD SUCCESSFUL`, `ChatSelectionRangeTest` 9/9, 22 suites with no
failures. Android compile verified separately with `:common:compileDebugKotlinAndroid` (no androidMain
change, so the shared Kotlin compile is the relevant check).
