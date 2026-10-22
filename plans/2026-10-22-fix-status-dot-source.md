# Fix: the status dot and the info page could show different connection statuses

Date: 2026-10-22
Base version: 7.0.4
TODO item: `## bug fix:` → `点的显示有时候和联系人页面里显示的连接状态不一致`

## Problem

The same connection could show one status on its dot (chat list row, chat title, member list) and a
different one in the contact / member info page, most visibly "connected" in the info page while the
dot was missing entirely.

## Cause

Two different sources were read:

- the dots used `connectionSubStatus(conn, chatModel.connectionSubStatuses)` — the live event map,
  falling back to the last fetched `ConnectionStats.subStatus`;
- the contact info page (`ChatInfoView.kt`, **Servers → Network status** row) used
  `chatModel.chatSubStatus.value` directly. That state is only filled when a chat is opened or when a
  subscription event arrives *for the open chat's own connection*, and it is reset when the chat
  closes, so it is unrelated to the connection shown on the page. The member info page used its own
  freshly fetched `ConnectionStats.subStatus`.

The two sources therefore disagreed whenever one of them had a value the other lacked (for example
`CR.ContactConnected` sets `chatSubStatus` but not the per-connection map).

## Fix

One resolver for the whole app:

```kotlin
fun connectionStatus(conn: Connection?): SubscriptionStatus? =
  connectionStatus(conn, connectionSubStatuses, chatAgentConnId.value, chatSubStatus.value)

internal fun connectionStatus(
  conn: Connection?, liveStatuses: Map<String, SubscriptionStatus>,
  openChatAgentConnId: String?, openChatStatus: SubscriptionStatus?
): SubscriptionStatus? =
  connectionSubStatus(conn, liveStatuses)
    ?: openChatStatus?.takeIf { conn != null && conn.agentConnId == openChatAgentConnId }
```

Resolution order: live event status → last fetched connection stats → the open chat's status, and the
last one only for the connection the open chat actually uses (so a status can never leak from one
chat to another). All display sites now call it:

- `ChatView.kt` `ChatInfoToolbarTitle` (the fallback chain moved into the resolver),
- `ChatPreviewView.kt` `chatPreviewTitle`,
- `GroupChatInfoView.kt` `MemberRow`,
- `ChatInfoView.kt` contact info page network status row (`?: cStats?.subStatus` as a last resort
  while the page's own fetch is in flight),
- `GroupMemberInfoView.kt` member info page network status row (`?: cStats.subStatus` likewise).

## Files

- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatView.kt`
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatInfoView.kt`
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupMemberInfoView.kt`
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatPreviewView.kt`
- `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ConnectionSubStatusTest.kt` (12 tests)

## Docs

- `product/rules.md` (RULE-22 invariant 2 and Enforcement list)
- `product/views/contact-info.md`, `product/views/chat.md`, `product/concepts.md` (PC37)
- `spec/state.md` (`connectionSubStatuses` row), `spec/client/chat-view.md`, `spec/client/chat-list.md`,
  `spec/impact.md`

## Adversarial review

- *Can a status of another chat leak into a dot?* No: the open chat's status is only used when
  `conn.agentConnId == chatAgentConnId`, and `chatAgentConnId` is reset when a chat closes.
- *Is `chatSubStatus` still needed at all?* Yes, as the only source right after opening a chat
  (before any subscription event arrives) — it is now consumed only through the resolver.
- *Does the info page lose freshness?* No: it prefers the resolver and falls back to its own fetch
  when the resolver knows nothing, and its fetch also writes the stats into the model, which the
  resolver reads.
- *Behaviour change for other chats' dots?* Only that the dot and the info page now agree; nothing is
  persisted and no preference or `AppSettings` field was added.
