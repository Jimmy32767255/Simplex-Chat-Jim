# Network status dot in the chat list, chat title and group member list

TODO item 10: 成员头像旁边的网络状态图标（其实就是把用户页面→服务器→网络状态 右边的那个会变色的点，也复制到私聊标题、主页用户名左侧以及群用户列表的用户名左边）。

## Problem

The network (subscription) status of a connection is only visible on the contact / group member info
pages ("Servers" section, "Network status" row, the coloured dot drawn by the private `ServerImage`
composable in `ChatInfoView.kt`). While chatting or browsing the chat list there is no way to see at a
glance whether a connection is currently connected, connecting or failed.

The direct chat title had a partial indicator (`SubStatusView`), but it deliberately rendered nothing
for `SubscriptionStatus.Active`, showed a spinner for `Pending` and an error icon otherwise, and it
only ever reflected the currently open chat.

## Solution

1. Extract the dot into a reusable composable `SubscriptionStatusDot(subStatus: SubscriptionStatus?, size: Dp = 18.dp)`
   in `views/helpers/SubscriptionStatusDot.kt`. `null` renders nothing. The icons, colours and content
   descriptions are exactly those of the info page dot, so nothing new is introduced visually.
   `SubStatusRow` on the contact info page now uses it and the private duplicate was removed.
2. Make the status of *every* connection available on the client: `ChatModel.connectionSubStatuses`
   (a `mutableStateMapOf<String, SubscriptionStatus>`, key = agent connection id) is updated from
   `CR.SubscriptionStatusEvt` for every connection in the event (before 7.0.4 only the open chat's
   status was kept, in `ChatModel.chatSubStatus`).
3. Resolve a connection's status with the pure helper
   `connectionSubStatus(conn, liveStatuses) = liveStatuses[conn.agentConnId] ?: conn.connectionStats?.subStatus`
   (live agent event first, then the last fetched connection stats).
4. Show the dot in three places, left of the name:
   - direct chat title (`ChatInfoToolbarTitle`),
   - home page / chat list row (`ChatPreviewView.chatPreviewTitle()`),
   - group member list row (`MemberRow` in `GroupChatInfoView.kt`).

## Design decisions

- **Reuse instead of a second implementation.** The info page dot is the source of truth quoted by the
  TODO, so its implementation moved to `views/helpers/` and the info page delegates to it. The old
  `SubStatusView` / `SubProgressView` (spinner for `Pending`, error icon otherwise, nothing for
  `Active`) became redundant and were removed: the title now shows the same dot as the other places,
  including green when the connection is active.
- **Per-connection status map instead of reading `ConnectionStats`.** `connectionStats` is only
  populated on demand (contact / member info page, ratchet sync, decryption-error views), so a dot
  based on it alone would usually be missing in the chat list and member list. The agent already
  emits the status of every connection (`CEvtSubscriptionStatus` with the affected agent connection
  ids, `src/Simplex/Chat/Controller.hs:976`), the client just threw it away for all connections but
  the open chat.
- **`connectionStats.subStatus` stays as a fallback** so a dot can also be shown before any status
  event arrived, e.g. right after opening a chat whose stats were fetched earlier.
- **The direct chat title only falls back to `chatSubStatus` when the connection is the open chat's**
  (`conn.agentConnId == chatModel.chatAgentConnId`), because `chatSubStatus` is not reset when a chat
  is closed - without that check a previously opened chat could display a stale green dot.
- **Placement of "主页用户名左侧".** The home page header itself shows no user name (only the profile
  avatar, "Chats" and the server status indicator), so this is the chat rows of the home page: the dot
  goes left of the contact name, i.e. next to the avatar.
- **Direct chats only.** A group/channel row has no single connection to report; group members are
  covered by the member list dot instead. Relay (channel) member lists keep `ChannelRelaysView`.
- No new preference, no new strings (the existing `icon_descr_server_status_*` descriptions are
  reused), no `AppSettings` change and no core change.

## Steps

1. New `views/helpers/SubscriptionStatusDot.kt`; `ChatInfoView.SubStatusRow` uses it; remove the
   private `ServerImage` duplicate.
2. `ChatModel`: add `connectionSubStatuses` plus the pure `connectionSubStatus` resolver.
3. `SimpleXAPI.kt`: update `connectionSubStatuses` for all connections of `CR.SubscriptionStatusEvt`.
4. `ChatView.ChatInfoToolbarTitle`: dot left of the name for ready and active direct chats; remove
   `SubStatusView` / `SubProgressView`.
5. `ChatPreviewView.chatPreviewTitle()`: dot left of the contact name.
6. `GroupChatInfoView.MemberRow`: dot left of the member name when the member has a connection.
7. Unit tests for the only new pure logic, `connectionSubStatus`
   (`commonTest/.../ConnectionSubStatusTest.kt`).

## Adversarial review

- The dot is only rendered when a status is actually known (`SubscriptionStatusDot(null)` draws
  nothing), so no empty space is left in the rows.
- Contact guards mirror the info page (`contact.ready && contact.active`), so a deleted or not yet
  connected contact never shows a stale dot; member rows additionally require `activeConn != null`.
- `connectionSubStatuses` is not pruned when a connection disappears. Entries are a string and a small
  object, bounded by the number of connections of the current profile, and a stale entry can only be
  read through the same agent connection id, which the core does not reuse. Accepted tradeoff,
  documented here instead of adding a cleanup path that would have to guess when a connection is gone.
- The map lives in `ChatModel` (client-only state), not in the core's `Connection`, so the
  `/_servers` / API serialisation and the cross-device backup format are untouched.
- `SubscriptionStatusDot` is a single icon box with the same size, tint and content descriptions as
  before on the info pages, so no existing screen changes appearance.
- The removed `SubStatusView` was only referenced by `ChatInfoToolbarTitle` (verified by grep); with
  `Pending` the title now shows the pending dot instead of a `CircularProgressIndicator`.
