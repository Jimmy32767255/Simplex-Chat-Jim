# Fix: the network status dot was missing until the chat had been opened once

Date: 2026-10-22
Base version: 7.0.4
TODO item: `## bug fix:` → `程序刚启动的时候连线连接状态的那个点不会显示，需要手动点进去一次才行`

## Problem

Right after the app started, direct chats (and their info pages) showed no network status dot; the dot
appeared only after the chat had been opened once.

## Cause

A connection status reaches the client in two ways only:

- `CR.SubscriptionStatusEvt`, which the core emits from the subscription service when a server status
  changes (`src/Simplex/Chat/Library/Subscriber.hs`, `CEvtSubscriptionStatus`). Connections that are
  already up when the client starts produce no event, because nothing changed;
- `ConnectionStats.subStatus` from `apiContactInfo` / `apiGroupMemberInfo`, which the client only
  requested when a chat was opened (`ChatView.kt`) or an info page was shown.

So at startup `ChatModel.connectionSubStatuses` was empty and no connection had fetched stats, the
resolver returned `null` for every connection, and RULE-22 ("no status, no dot") correctly hid every
dot — until opening a chat filled one in.

## Fix

Request the missing statuses once per session in the background:

- `chatsForConnectionStatus(chats, requested, statusKnown)` (in `ChatModel.kt`) selects the direct chats
  whose contact is `ready && active`, whose connection status is still unknown and whose
  `agentConnId` was not requested yet.
- `ChatListView` runs a `LaunchedEffect` over the direct chat ids: it marks the selected connections in
  `ChatModel.connectionStatusRequested`, then inside `withBGApi` calls `apiContactInfo` — the same call
  the chat view uses on open, so the numbers come from the same source — and stores the result with
  `ChatsContext.updateContactConnectionStats`, which the resolver reads as
  `Connection.connectionStats.subStatus`. Exceptions are caught and logged, so a contact deleted in the
  meantime only leaves the dot hidden.

Nothing is persisted, no preference and no `AppSettings` field is added, and live events keep updating
the same state as before.

## Files

- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`
  (`connectionStatusRequested`, `chatsForConnectionStatus`)
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatListView.kt`
- `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/ConnectionStatusPrefetchTest.kt`
  (9 tests)

## Docs

- `product/rules.md` (RULE-22 invariant 5 + enforcement list)
- `product/views/chat-list.md`, `product/concepts.md` (PC37)
- `spec/client/chat-list.md` (Connection Status Dot), `spec/state.md`, `spec/impact.md`

## Adversarial review

- *Does this flood the core with requests?* No: one call per contact per session, only for direct chats
  that are ready and active and only while their status is unknown; the set is marked before the loop
  so a re-entered effect cannot duplicate requests.
- *Does it slow down the UI?* It runs inside `withBGApi` (single background dispatcher) like the
  group member prefetch, and results are applied on the main thread one by one.
- *Can it show a wrong dot?* It stores exactly what the info page and the chat title would have
  fetched on open, and later subscription events overwrite it.
- *Group members?* Their dots still appear from subscription events or when their info page is opened;
  requesting stats per member would be one request per member for every group, which is why it is out
  of scope here (and recorded in the TODO note).
