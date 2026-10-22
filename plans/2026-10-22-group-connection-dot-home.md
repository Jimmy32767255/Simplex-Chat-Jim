# Group connection rate dot in the chat list

TODO item 13: 在主页聊天列表的群名称左侧显示群成员连接率圆点（把「群成员连接率和百分比显示」里的那个会变色点也放到主页群名左边，样式同条目 10 的网络状态图标）。

## Problem

Item 7 added the connection-rate dot (green = all connected, yellow = partially connected, grey =
nobody connected) to the member-list title of the group info page. The same signal would be useful
directly in the chat list, next to the group name, so a glance at the home page shows which groups
have unreachable members.

The chat list, however, has no member data: `GroupInfo` only carries the user's own `membership` and
`groupSummary.currentMembers`, and member lists are loaded with `apiListMembers` only when a group
(or its info page) is opened. The user explicitly chose to preload the member lists in the
background for every group.

## Solution

- `ChatModel.groupMembersForRate: SnapshotStateMap<Long, List<GroupMember>>` — session-local cache of
  the member list per group id. Never persisted, not part of `AppSettings`.
- `ChatModel.groupRateRequested: MutableSet<Long>` — guards against requesting the same group twice
  while a prefetch is in flight.
- `ChatListView` gets a `LaunchedEffect` keyed on the group chat ids: it takes the groups still
  missing from the cache (`groupChatsForConnectionRate`) and loads each list sequentially in the
  background (`apiListMembers` → cache).
- `CR.GroupMemberUpdated` also updates the cached list (`ChatModel.updateGroupMembersForRate`), so the
  dot follows connection changes after the initial prefetch.
- `GroupChatInfoView` exposes `GroupMembersConnectionRateDot(connected, total, size)` (the icon part of
  `GroupMembersConnectionStatus`, which now uses it) so the same colour mapping is used in both places.
- `ChatPreviewView.chatPreviewTitle()` renders that dot left of the group name when the rate is known
  and the member section would be visible for the group (`groupMembersSectionVisible`).

## Design decisions

- **Background prefetch instead of lazy per-open data** (user's choice): every ordinary group gets a
  dot on the first home-page visit of the session, not only groups that were opened before.
- **Channels/relay groups and inactive memberships are skipped**: they have no member-list section in
  the group info page (`groupMembersSectionVisible`), so a rate dot would be meaningless there.
- **Sequential, cancellable prefetch**: one `apiListMembers` call at a time inside `withBGApi`, so the
  UI never blocks and a long list of groups only delays the later dots.
- **Session-local cache**: connection state is ephemeral, so nothing is persisted and nothing is added
  to the cross-device backup format (same as items 7, 8 and 10).
- **No new strings**: the dot reuses the same icons/colours as the info page; it has no label.

## Steps

1. `ChatModel`: cache map, request guard, `updateGroupMembersForRate`, and the pure helper
   `groupChatsForConnectionRate(chats, requested)`.
2. `SimpleXAPI.kt`: feed the cache from `CR.GroupMemberUpdated`.
3. `ChatListView`: prefetch effect.
4. `GroupChatInfoView`: extract `GroupMembersConnectionRateDot`.
5. `ChatPreviewView`: render the dot for group rows.
6. Tests for `groupChatsForConnectionRate` (channels, inactive membership, already requested) and for
   `updateGroupMembersForRate`-like pure logic.
7. Docs + TODO note, `./gradlew :common:desktopTest`, then patch `diff/0010-*.patch` and commit.

## Adversarial review

- *Does the prefetch change data the user sees elsewhere?* No — it only fills a display cache; the
  group info page keeps using `chatModel.groupMembers`.
- *Could it spam the core?* One command per group per session, guarded by `groupRateRequested`; the
  rate map is keyed by group id, so a re-composition never re-requests a loaded group.
- *What if the member list changes size?* `CR.GroupMemberUpdated` replaces/appends/removes the single
  member; the next session prefetch re-reads the authoritative list.
- *Channels and left groups:* excluded from both the prefetch and the dot.
- *Two dots in one row?* Group rows never show the network status dot (item 10 is direct chats only),
  so there is no ambiguity.
