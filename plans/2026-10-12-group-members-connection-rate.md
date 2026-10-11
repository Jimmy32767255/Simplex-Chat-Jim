# Group members connection rate (TODO item 7)

## Problem

The group member list in the group info page (`GroupChatInfoView`) shows only the number of
members in its section title:

```
SectionView(title = String.format(generalGetString(MR.strings.group_info_section_title_num_members), activeSortedMembers.count() + 1), ...)
```

(`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt:771`)

The main chat list header, in contrast, shows how well the app is connected: the home title row
renders `SubscriptionStatusIndicator` (`views/chatlist/ChatListView.kt:601`), which is a percentage
text plus a colour-changing status icon (`views/chatlist/ServersSummaryView.kt:120`).

The request (TODO.md item 7) is to show the members connection rate and percentage in the group
member list title, in the same visual style as the home title.

## Solution

In the non-relay, non-"next connect prepared" group member section title, render a connection
indicator next to the member count: the `connected/total (pct%)` text plus a status dot whose
colour changes with the rate.

- Connected members: `GroupMember.ready` (`activeConn?.connStatus == ConnStatus.Ready`,
  `model/ChatModel.kt:2561`), counted over the displayed members plus the current user's own
  membership (the title count is `activeSortedMembers.count() + 1` because the member list excludes
  the current user).
- Dot: reuse the same icon set as the "Network status" dot in the chat info page
  (`views/chat/ChatInfoView.kt:1147` `ServerImage`): `ic_circle_filled` (all connected, green),
  `ic_pending_filled` (partially connected, `WarningYellow`), `ic_error_filled` (none connected,
  secondary colour).
- The title row is rendered with the existing `SectionViewWithButton(title, titleButton)` helper,
  which already right-aligns its `titleButton` slot with a weight spacer. It gains a
  `cardShape: Shape = SectionCardShape` parameter so the member card keeps its top-rounded shape,
  and its title row gets `verticalAlignment = Alignment.CenterVertically` so the dot lines up with
  the title text. Both changes are default-preserving for the existing call sites.

## Design decisions

- The rate is computed from `GroupMember.ready`, not from `activeConn != null`: a member with a
  prepared or stale connection is not actually reachable, and `ready` is the same predicate the
  rest of the client uses to decide that messages can be received.
- The current user is part of both the numerator and the denominator, matching the member count in
  the same title, so a fully connected group shows `N/N (100%)`.
- The count/percentage text is built inline (`"$connected/$total ($percentage%)"`), the same way the
  home indicator builds its percentage (`views/chatlist/ServersSummaryView.kt:124`) - it contains no
  translatable words.
- No new preference and no `AppSettings` change: this is a passive display.
- The predicate and the rate mapping are `internal` top-level functions so they can be unit tested
  from the test source set without a `ChatController` (same approach as
  `updatedServerMetadata` in `ProtocolServersView.kt`).

## Steps

1. `GroupChatInfoView.kt`: add `GroupMembersConnectionRate` enum,
   `internal fun groupMembersConnectionRate(connected: Int, total: Int)` and a private
   `@Composable GroupMembersConnectionStatus(connected, total)`; replace the `SectionView` title at
   line 771 with `SectionViewWithButton` + `titleButton`.
2. `views/helpers/Section.kt`: add `cardShape: Shape = SectionCardShape` to `SectionViewWithButton`
   and centre its title row vertically.
3. Test `common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt`.
4. Docs: `product/views/group-info.md`, `product/README.md`, `spec/client/chat-view.md`,
   `spec/impact.md`, `spec/state.md` if needed, `TODO.md`.
5. `./gradlew :common:desktopTest`, then commit and export `diff/0006-group-members-connection-rate.patch`.

## Adversarial review

- _Relay groups / "next connect prepared" groups_: the whole member section is already guarded by
  `if (!groupInfo.nextConnectPrepared && !groupInfo.useRelays)`, so the indicator is not shown where
  the concept of per-member connections does not apply.
- _Search filter_: the rate uses `activeSortedMembers` (the full list), not `filteredMembers`, so
  typing in the member search box does not change the group's connection rate.
- _Zero members_: `total` is `activeSortedMembers.size + 1`, so it can never be 0; the division guard
  is still there and a zero count renders `0/1 (0%)` with the error dot.
- _`SectionViewWithButton` change_: the new `cardShape` parameter has a default equal to what the
  other four call sites already used, and only the member list passes a value, so no other screen
  changes.
- _Percentage rounding_: `floor`, same as the home indicator, so "100%" is only shown when all
  members are really connected.
