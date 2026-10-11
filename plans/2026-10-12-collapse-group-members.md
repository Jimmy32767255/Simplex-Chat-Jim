# Collapse the group member list

TODO item: `群成员连接率和百分比显示` is done; this plan covers item 8,
`收起群成员列表（不然在大群里拉到最下面要很长时间）` (TODO.md).

## Problem

The group info page renders every member of a group as its own card row in one
`LazyColumn`. In a large group the member list pushes the group preferences,
"clear chat", "delete group" and "leave group" actions far below the fold, so
reaching them means scrolling through hundreds of rows.

## Solution

The member list section header (`N members` + connection rate) gets a chevron
toggle on its right end. Tapping it collapses the section down to just that
header row, so the rest of the page is immediately reachable; tapping it again
expands the list back.

Collapsed means:

- the current user's own row, the "Add members" button and the member search row
  (all rendered inside the section's card) are hidden, and
- the `itemsIndexed(filteredMembers)` card rows below the header are skipped.

Only the title row stays, so the user still sees how many members the group has
and how many of them are connected.

## Design decisions

- **Toggle lives in the existing header, using `SectionViewWithButton`.** The
  header already uses the `titleButton` slot for the connection rate added by
  item 7, so the chevron is appended to the same slot — no new section type and
  no new card is introduced, and the layout of other `SectionViewWithButton`
  call sites is untouched.
- **Collapsed = empty content.** When collapsed the section's content lambda
  renders nothing. Both `CardColumn` branches (`LocalCardScreen` true/false)
  measure to zero height for empty content, so the card disappears by itself
  rather than leaving an empty rounded strip; `cardShape` is reset to the fully
  rounded `SectionCardShape` for the collapsed case so that, if a future layout
  ever gives the empty container a height, it still has the right corners.
- **State is per group and session-local.** `rememberSaveable(groupInfo.id)` in
  `GroupChatInfoLayout` — the same pattern `deletingItems` already uses. It
  survives navigation to a member's info page and back while the modal is open,
  resets when the modal is closed, and is not written to preferences or to the
  cross-device backup format (`AppSettings`), consistent with the other client
  side displays added in items 1/5/6/7.
- **The chevron mirrors the existing expandable-list convention.**
  `ic_chevron_up` while expanded (tap to collapse), `ic_chevron_down` while
  collapsed (tap to expand), matching `views/newchat/AddChannelView.kt`
  (`relayListExpanded`).
- **No effect on group selection mode.** The selection toolbar and the selection
  state stay as they are; collapsing only hides rows visually. Members selected
  before collapsing remain selected, and expanding shows them again.

## Steps

1. Add `collapse_verb` to `MR/base/strings.xml` and `MR/zh-rCN/strings.xml`
   (`expand_verb` already exists and is reused for the other state).
2. In `GroupChatInfoView.kt` add `val membersCollapsed = rememberSaveable(groupInfo.id) { mutableStateOf(false) }`
   to `GroupChatInfoLayout`.
3. Extend the member-list `SectionViewWithButton`:
   - `titleButton` becomes a clickable `Row` with `GroupMembersConnectionStatus`
     and the chevron icon,
   - `cardShape` becomes `SectionCardShape` while collapsed,
   - the add-members button / search row / current-user row are wrapped in
     `if (!membersCollapsed.value)`.
4. Guard the member `itemsIndexed` block with `&& !membersCollapsed.value`.
5. Add unit tests for the only new pure logic: the visibility predicates
   `groupMembersSectionVisible(nextConnectPrepared, useRelays)` and
   `groupMembersRowsVisible(nextConnectPrepared, useRelays, collapsed)`, which the layout now uses
   for both guards (`testSectionVisibleOnlyForPlainGroups`, `testRowsHiddenWhenCollapsed`).
6. Run `./gradlew :common:desktopTest`, then update the product/spec docs and
   the TODO entry and commit with a diff in `diff/`.

## Adversarial review

- Empty content really is zero-height: `CardColumn` (non-card branch) is
  `Column(Modifier.padding(contentPadding).fillMaxWidth())` and the card branch
  `layout(constraints.maxWidth, y.toInt())` with `y == 0`; `contentPadding`
  defaults to `PaddingValues()`, so nothing is drawn. The row's
  `horizontal = CARD_PADDING` padding is on the zero-size node.
- The `LazyColumn` item keys do not change when collapsing: the member rows
  simply disappear, and `selectedItems` (state) is untouched, so no stale
  selection or key duplication.
- `scrollToItemId`/`appBarHandler.listState` are not reset by collapsing; the
  list keeps its scroll position, which is the desired behaviour when expanding
  again.
- Relay/channel groups (`groupInfo.useRelays`) and `nextConnectPrepared` groups
  never render this section, so the toggle cannot appear where the member list
  is not a plain member list.
- `localized` text: the toggle only uses the new `collapse_verb` plus the
  existing `expand_verb`, no interpolated text.
