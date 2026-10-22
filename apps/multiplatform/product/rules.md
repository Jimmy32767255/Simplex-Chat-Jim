# Business Rules -- SimpleX Chat (Android & Desktop, Kotlin Multiplatform)

This document specifies invariants enforced by the Android and Desktop (Kotlin/Compose Multiplatform) clients.

---

## Table of Contents

1. [Security (RULE-01 through RULE-05)](#1-security)
2. [Message Integrity (RULE-06 through RULE-09)](#2-message-integrity)
3. [Group Integrity (RULE-10 through RULE-13)](#3-group-integrity)
4. [File Transfer (RULE-14 through RULE-15)](#4-file-transfer)
5. [Notification Delivery (RULE-16 through RULE-17)](#5-notification-delivery)
6. [Call Integrity (RULE-18)](#6-call-integrity)
7. [Network Configuration (RULE-19)](#7-network-configuration)
8. [Profile Images (RULE-20)](#8-profile-images)
9. [Connection Status Display (RULE-21)](#9-connection-status-display)
10. [Network Status Dot (RULE-22)](#10-network-status-dot)
11. [Servers Import and Export (RULE-23)](#11-servers-import-and-export)
12. [Group Connection Rate in the Chat List (RULE-24)](#12-group-connection-rate-in-the-chat-list)

---

## 1. Security

### RULE-01: End-to-End Encryption is Mandatory

**Invariant:** Every message, file chunk, and call signaling payload MUST be encrypted end-to-end before transmission. The app MUST NOT transmit plaintext content to any relay server.

**Enforcement:** The Haskell core library handles all encryption. The Kotlin layer never constructs raw SMP messages. All communication flows through `ChatController.sendCmd()` which delegates to the FFI, ensuring the encryption layer cannot be bypassed.

**Location:** `common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt` -- `ChatController.sendCmd()`, `chatSendCmd()` FFI call

---

### RULE-02: Database Encryption at Rest

**Invariant:** The local SQLite database MUST be encrypted. A passphrase (either user-chosen or randomly generated) MUST be set before the database is operational.

**Enforcement:** On first launch, a random passphrase is generated and stored encrypted via the platform keystore (`CryptorInterface.encryptText`). The `initialRandomDBPassphrase` preference tracks whether the user has set a custom passphrase. Database encryption state is tracked in `ChatModel.chatDbEncrypted`. Encryption/re-encryption is performed via `CC.ApiStorageEncryption(config: DBEncryptionConfig)`.

**Caveat:** The user is not forced to set a custom passphrase -- the random passphrase is stored in app-accessible encrypted preferences. See GAP: "Database passphrase not enforced."

**Location:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/helpers/DatabaseUtils.kt`
- `common/src/commonMain/kotlin/chat/simplex/common/platform/Cryptor.kt` -- `CryptorInterface`
- Android: `common/src/androidMain/kotlin/chat/simplex/common/platform/Cryptor.android.kt` -- Android Keystore
- Desktop: `common/src/desktopMain/kotlin/chat/simplex/common/platform/Cryptor.desktop.kt` -- **placeholder, not implemented**

---

### RULE-03: Local Authentication Gating

**Invariant:** When local authentication is enabled (`AppPreferences.performLA == true`), the app MUST require biometric/PIN authentication before displaying any chat content. The lock engages after `laLockDelay` seconds of inactivity.

**Enforcement:** `AppLock.setPerformLA` controls the lock state. The lock delay is configurable via `AppPreferences.laLockDelay` (default 30 seconds). Authentication mode is set via `AppPreferences.laMode` (system biometric or passcode).

**Location:**
- `common/src/commonMain/kotlin/chat/simplex/common/AppLock.kt`
- `SimpleXAPI.kt` -- `AppPreferences.performLA`, `AppPreferences.laMode`, `AppPreferences.laLockDelay`

---

### RULE-04: Self-Destruct Profile

**Invariant:** When self-destruct is enabled (`AppPreferences.selfDestruct == true`), entering the self-destruct passphrase instead of the real passphrase MUST wipe the database and present a clean profile with `selfDestructDisplayName`.

**Enforcement:** The self-destruct passphrase is stored separately (`encryptedSelfDestructPassphrase` / `initializationVectorSelfDestructPassphrase`). On Android, `SimplexService` checks for self-destruct on initialization. The comparison happens during the local authentication flow.

**Location:**
- `SimpleXAPI.kt` -- `AppPreferences.selfDestruct`, `AppPreferences.selfDestructDisplayName`
- `android/src/main/java/chat/simplex/app/SimplexService.kt` -- initialization check

---

### RULE-05: Screen Protection

**Invariant:** When `AppPreferences.privacyProtectScreen == true` (default), the app MUST prevent screenshots and screen recording. On Android this uses `FLAG_SECURE`; on Desktop this is advisory only.

**Enforcement:** The preference defaults to `true`. The Android activity applies `FLAG_SECURE` to its window based on this preference. The Desktop app cannot enforce this at the OS level.

**Location:** `SimpleXAPI.kt` -- `AppPreferences.privacyProtectScreen`

---

## 2. Message Integrity

### RULE-06: Message Ordering Verification

**Invariant:** The app MUST detect and surface message integrity violations (gaps, duplicates, out-of-order delivery) to the user.

**Enforcement:** The Haskell core tracks message sequence numbers per connection. When a gap or integrity error is detected, a `CIContent.RcvIntegrityError(msgError: MsgErrorType)` chat item is inserted into the conversation. The UI renders these as system messages indicating the integrity issue.

**Location:** `ChatModel.kt:3565` -- `CIContent.RcvIntegrityError`

---

### RULE-07: Decryption Error Surfacing

**Invariant:** When a message cannot be decrypted, the app MUST display a `RcvDecryptionError` item showing the error type and count of affected messages. The app MUST NOT silently drop undecryptable messages.

**Enforcement:** The Haskell core emits `CIContent.RcvDecryptionError(msgDecryptError, msgCount)` which the UI renders with an explanation and count. Ratchet re-synchronization can be triggered via `APISyncContactRatchet` / `APISyncGroupMemberRatchet`.

**Location:** `ChatModel.kt:3566` -- `CIContent.RcvDecryptionError`

---

### RULE-08: Delivery Receipt Consistency

**Invariant:** Delivery receipt settings MUST be consistent: when a user enables/disables receipts globally, the change MUST propagate to all contacts/groups (optionally clearing per-chat overrides via `clearOverrides`).

**Enforcement:** Global receipt toggle triggers `CC.SetAllContactReceipts(enable)`. Per-type settings use `CC.ApiSetUserContactReceipts` / `CC.ApiSetUserGroupReceipts` with `UserMsgReceiptSettings(enable, clearOverrides)`. The `privacyDeliveryReceiptsSet` preference gates the initial setup prompt shown during onboarding.

**Location:**
- `SimpleXAPI.kt` -- `CC.SetAllContactReceipts`, `CC.ApiSetUserContactReceipts`, `CC.ApiSetUserGroupReceipts`
- `SimpleXAPI.kt` -- `ChatController.startChat()` -- triggers `setDeliveryReceipts` prompt

---

### RULE-09: Chat Item TTL Enforcement

**Invariant:** When a chat item TTL (time-to-live) is set globally or per-chat, expired messages MUST be deleted by the core. The app MUST NOT display expired items.

**Enforcement:** Global TTL set via `CC.APISetChatItemTTL(userId, seconds)`. Per-chat TTL set via `CC.APISetChatTTL(userId, chatType, id, seconds)`. The Haskell core performs periodic cleanup. The current global TTL is stored in `ChatModel.chatItemTTL`.

**Location:** `SimpleXAPI.kt` -- `CC.APISetChatItemTTL`, `CC.APISetChatTTL`

---

## 3. Group Integrity

### RULE-10: Role-Based Access Control

**Invariant:** Group operations MUST respect the member's role. Only members with sufficient role level can perform privileged operations:
- **Owner:** can delete group, change any member's role, transfer ownership
- **Admin:** can add/remove members, change roles (up to Admin), create/delete group links
- **Moderator:** can delete other members' messages, block members
- **Member / Author / Observer:** cannot perform administrative actions

**Enforcement:** The Haskell core validates role permissions server-side. The Kotlin UI layer uses `GroupMemberRole` comparisons (the enum is ordered: Observer < Author < Member < Moderator < Admin < Owner) to show/hide action buttons.

**Location:** `ChatModel.kt:2369` -- `enum class GroupMemberRole`; various group management views

---

### RULE-11: Group Member Removal Atomicity

**Invariant:** When removing members from a group, the removal command MUST specify all member IDs atomically. Partial removal MUST NOT leave the group in an inconsistent state.

**Enforcement:** `CC.ApiRemoveMembers(groupId, memberIds: List<Long>, withMessages: Boolean)` sends all member IDs in a single command. The `withMessages` flag controls whether the removed members' messages are also deleted.

**Location:** `SimpleXAPI.kt` -- `CC.ApiRemoveMembers`

---

### RULE-12: Group Link Role Default

**Invariant:** When creating a group link, the default member role for joiners MUST be explicitly specified. The role can be updated after creation without regenerating the link.

**Enforcement:** `CC.APICreateGroupLink(groupId, memberRole)` requires a role. `CC.APIGroupLinkMemberRole(groupId, memberRole)` updates it. The link itself remains stable.

**Location:** `SimpleXAPI.kt` -- `CC.APICreateGroupLink`, `CC.APIGroupLinkMemberRole`

---

### RULE-13: Member Blocking Scope

**Invariant:** Blocking a member (`ApiBlockMembersForAll`) MUST apply the block for all group members (not just the requester). The `blocked` flag is visible to all members. Only roles >= Moderator can block.

**Enforcement:** `CC.ApiBlockMembersForAll(groupId, memberIds, blocked)` sends the block/unblock to the core, which propagates it to all group members.

**Location:** `SimpleXAPI.kt` -- `CC.ApiBlockMembersForAll`; `ChatModel.kt` -- `GroupMember.blockedByAdmin`

---

## 4. File Transfer

### RULE-14: File Encryption in Transit and at Rest

**Invariant:** Files sent via XFTP MUST be encrypted before upload. Files received MUST be decrypted only after download. When `privacyEncryptLocalFiles` is enabled (default `true`), files stored locally MUST be encrypted with per-file keys (`CryptoFile.cryptoArgs`).

**Enforcement:** The Haskell core handles XFTP encryption. Local file encryption is toggled via `CC.ApiSetEncryptLocalFiles(enable)`. The `CryptoFile` type carries optional `CryptoFileArgs` (key + nonce) for local decryption. Files are decrypted on-demand for display via `decryptCryptoFile()`.

**Location:**
- `SimpleXAPI.kt` -- `CC.ApiSetEncryptLocalFiles`, `AppPreferences.privacyEncryptLocalFiles`
- `ChatModel.kt` -- `CryptoFile`, `CryptoFileArgs`
- `RecAndPlay.desktop.kt` -- `decryptCryptoFile()` usage in audio playback

---

### RULE-15: Relay Approval for File Transfer

**Invariant:** When `privacyAskToApproveRelays` is enabled (default `true`), the app MUST prompt the user before using XFTP relay servers suggested by contacts (as opposed to the user's own configured servers). The `userApprovedRelays` flag on `CC.ReceiveFile` records the user's consent.

**Enforcement:** `CC.ReceiveFile(fileId, userApprovedRelays, encrypt, inline)` passes the approval flag. The UI prompts the user when the file is from an unapproved relay.

**Location:** `SimpleXAPI.kt` -- `CC.ReceiveFile`, `AppPreferences.privacyAskToApproveRelays`

---

## 5. Notification Delivery

### RULE-16: Background Message Delivery (Android)

**Invariant:** On Android, when `NotificationsMode.SERVICE` is selected (default), the app MUST maintain a foreground service (`SimplexService`) to ensure continuous message delivery. The service MUST survive app backgrounding and device sleep. When `NotificationsMode.PERIODIC` is selected, `MessagesFetcherWorker` MUST periodically wake and fetch messages. When `NotificationsMode.OFF`, no background delivery occurs.

**Enforcement:**
- `SimplexService` runs as a foreground service with `START_STICKY` and a `WakeLock`. It displays a persistent notification on the `SIMPLEX_SERVICE_NOTIFICATION` channel.
- `MessagesFetcherWorker` is a `PeriodicWorkRequest` scheduled via `WorkManager`.
- The mode is stored in `AppPreferences.notificationsMode` and checked at app startup.

**Location:**
- `android/src/main/java/chat/simplex/app/SimplexService.kt`
- `android/src/main/java/chat/simplex/app/MessagesFetcherWorker.kt`
- `SimpleXAPI.kt:7739` -- `enum class NotificationsMode`

---

### RULE-17: Notification Preview Privacy

**Invariant:** Notification content MUST respect `notificationPreviewMode`:
- `HIDDEN` -- notification shows no sender or message content
- `CONTACT` -- notification shows sender name only
- `MESSAGE` -- notification shows sender name and message preview

**Enforcement:** `NtfManager` (Android) reads the preview mode from `AppPreferences.notificationPreviewMode` and constructs notifications accordingly. The `CallService` also respects this mode for call notifications (showing or hiding caller identity).

**Location:**
- `android/src/main/java/chat/simplex/app/model/NtfManager.android.kt` -- `displayNotification()`, `notifyCallInvitation()`
- `android/src/main/java/chat/simplex/app/CallService.kt` -- `updateNotification()`
- `SimpleXAPI.kt` -- `AppPreferences.notificationPreviewMode`

---

## 6. Call Integrity

### RULE-18: Call Lifecycle Management

**Invariant:** An active call MUST be properly managed across the full lifecycle:
1. **Incoming calls** MUST be reported via `CallManager.reportNewIncomingCall()` which triggers a notification (and on Android, a full-screen intent for lock-screen display).
2. **Only one call** can be active at a time. Accepting a new call MUST end any existing call first (`CallManager.acceptIncomingCall` checks `activeCall` and calls `endCall` if needed, guarded by `switchingCall` flag).
3. **Call state** MUST progress through defined states: `WaitCapabilities` -> `InvitationSent`/`InvitationAccepted` -> `OfferSent`/`OfferReceived` -> `Negotiated` -> `Connected` -> `Ended`.
4. **Call end** MUST clean up all resources: send `WCallCommand.End`, call `apiEndCall`, clear `activeCall`, cancel call notifications, and release platform resources.

**Android enforcement:**
- `CallService` (foreground service) keeps the call alive in background with a `WakeLock` and ongoing notification on `CALL_SERVICE_NOTIFICATION` channel.
- `CallActivity` hosts the WebRTC WebView.
- Lock-screen behavior controlled by `AppPreferences.callOnLockScreen` (DISABLE / SHOW / ACCEPT).

**Desktop enforcement:**
- Calls run in the system browser via the NanoWSD WebSocket server on `localhost:50395`.
- The `WebRTCController` composable manages the WebSocket lifecycle.
- On dispose, `WCallCommand.End` is sent and the server is stopped.

**Location:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/call/CallManager.kt`
- `common/src/commonMain/kotlin/chat/simplex/common/views/call/WebRTC.kt`
- Android: `android/src/main/java/chat/simplex/app/CallService.kt`, `android/src/main/java/chat/simplex/app/views/call/CallActivity.kt`
- Desktop: `common/src/desktopMain/kotlin/chat/simplex/common/views/call/CallView.desktop.kt`

---

## 7. Network Configuration

### RULE-19: Client-Only Server Display Metadata

Organisational data about the user's servers -- the group a server belongs to and the free-form note
attached to it -- is display metadata owned by the client.

1. **`UserServer` MUST NOT gain client-only fields.** The user's server list is serialized verbatim
   into the core command `/_servers <userId> <json>` (`ApiSetUserServers`) and parsed back out of the
   core's reply, so any Kotlin-only field would be silently dropped on the next round-trip. Group
   and note are therefore stored in the preference map `AppPreferences.serverMetadata`
   (`Map<String, ServerMeta>`, `SharedPreferences` key `ServerMetadata`), keyed by the exact
   `UserServer.server` address string.
2. **Metadata MUST never be sent to the core or to a server.** `serverMetadata` is read and written
   only through `AppPreferences`; it is not part of any `CC` command, and the UI states this to the
   user ("Group and note are stored only on this device and are never sent to the servers.").
3. **Metadata MUST follow the address.** When the user edits a server's address, the entry is moved
   from the previous key to the new one and the old key is removed
   (`updatedServerMetadata`), so an address edit never silently loses the group/note.
4. **Blank metadata MUST be dropped.** Saving an empty group and note for an address removes the
   entry rather than leaving an empty `ServerMeta` behind.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/ProtocolServersView.kt`
  (`updatedServerMetadata`, `saveServerMetadata`, `serverGroup`, `ServersSection`)
- `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/ProtocolServerView.kt`
  (`ServerGroupAndNoteSection`, saved on modal close)
- `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/NewServerView.kt`
  (batch add applies the same metadata to every added address)
- Test: `common/src/commonTest/kotlin/chat/simplex/app/ServerMetadataTest.kt`

---

## 8. Profile Images

### RULE-20: Opt-In Avatar Interaction and Clipboard Images

1. **Avatar clicks MUST be opt-in.** `ProfileImage` / `ChatInfoImage` / `MemberProfileImage` take an
   `onClick` that defaults to `null`; with `null` the avatar renders exactly as before and consumes
   no clicks. Only the four details/member-list avatars pass a callback, so rows that already have
   their own click action (chat list, user picker, welcome, chat items) are unaffected, and a nested
   avatar click wins over the row click where both exist.
2. **The viewer MUST handle the icon-only case.** `showProfileImageModal` returns without opening
   anything when the chat has no custom image, because there is nothing to view or copy.
3. **Copying MUST degrade gracefully.** `copyImageToClipboard` is an `expect`/`actual` that returns
   `false` instead of throwing when the platform refuses; the UI then reports
   `MR.strings.image_copy_failed`.
4. **Android MUST put a readable URI on the clipboard.** The image is written to
   `clipboard_image.png` in the app files dir and exposed through the existing `FileProvider`
   authority, so the clipboard never points at a deleted file.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/helpers/ChatInfoImage.kt`
  (`ProfileImage`, `ChatInfoImage`, `ProfileImageViewer`, `showProfileImageModal`)
- `common/src/commonMain/kotlin/chat/simplex/common/platform/Images.kt` (`expect copyImageToClipboard`)
  and `common/src/desktopMain/.../platform/Images.desktop.kt` / `common/src/androidMain/.../platform/Images.android.kt`
- `common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatInfoView.kt`,
  `views/chat/group/GroupChatInfoView.kt`, `views/chat/group/GroupMemberInfoView.kt` (wired call sites)
- Test: `common/src/desktopTest/kotlin/chat/simplex/app/ImageClipboardTest.kt`

---

## 9. Connection Status Display

### RULE-21: Ready-Only Group Connection Rate

1. **Only ready connections count as connected.** The group member connection rate MUST count a
   member as connected only when `GroupMember.ready` is true
   (`activeConn?.connStatus == ConnStatus.Ready`). Connections that are merely prepared, sending-only
   (`SndReady`) or failed MUST NOT be counted as connected.
2. **The current user is part of the rate, and counts as connected when active.**
   `groupMembersConnection` adds the current user's own membership to both the connected and the
   total count, so the rate stays consistent with the `N members` figure in the same section title,
   and a fully connected group reads `N/N (100%)`. The own membership MUST be counted only once (the
   helper filters it out of the member list first, because some lists do contain it), and it MUST be
   treated as connected when it has no active connection but its `memberStatus` is active
   (`memberActive`), because `GroupInfo.membership` normally carries no `activeConn` and the rate
   would otherwise never reach 100%.
3. **The rate is a display only.** It MUST NOT change any state, MUST NOT be persisted, and MUST NOT
   be added to the cross-device backup format; the member search filter MUST NOT affect it (the rate
   is computed from all members, not from the filtered list).
4. **Collapsing the member list is ephemeral.** The collapse toggle MUST be per group and session
   local (`rememberSaveable(groupInfo.id)`), MUST NOT be persisted or added to `AppSettings`, MUST
   NOT clear the search text or any member selection, and MUST NOT hide anything outside the member
   list section, so that expanding it again restores exactly the previous state.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`
  (`groupMembersConnection`, `groupMembersConnectionRate`, `GroupMembersConnectionStatus`,
  `groupMembersSectionVisible`, `groupMembersRowsVisible`, `membersCollapsed`)
- `common/src/commonMain/kotlin/chat/simplex/common/views/helpers/Section.kt`
  (`SectionViewWithButton` title button and card shape)
- Test: `common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt`

---

## 10. Network Status Dot

### RULE-22: The Connection Status Dot Reports One Connection Only

1. **One dot, one connection.** A dot MUST describe exactly the connection it is attached to,
   resolved by agent connection id (`connectionSubStatus`), never by chat or contact identity. A
   status that belongs to a different connection MUST NOT be displayed on it.
1b. **One status source for the whole app.** Every place that displays a connection status - the
   dots of the chat list, the chat title, the member list, and the contact / member info pages - MUST
   read it from `ChatModel.connectionStatus(conn)` (the pure `connectionStatus` helper), which
   resolves the live event status first, then the last fetched connection stats, and last the status
   of the open chat for its own connection. Reading the status from anywhere else (for example
   `chatModel.chatSubStatus` directly, as the contact info page did) makes the dot and the info page
   disagree.
2. **The dot is a display only.** It MUST NOT change any state, MUST NOT be persisted, and MUST NOT
   be added to `AppSettings` or to the cross-device backup format; the per-connection status map
   (`ChatModel.connectionSubStatuses`) is session-local client state.
3. **No status, no dot.** When no status is known for the connection (`null`), the dot MUST render
   nothing rather than assuming a connected or a failed state, and it MUST NOT change the row layout
   height.
4. **Direct chats and members only.** The dot MUST only be shown for direct chats/contacts and for
   group members that have an active connection. Group and channel rows MUST NOT show it, because
   they have no single connection to report.
5. **Statuses are requested once, in the background.** Because the core only reports a connection
   status on subscription events, the chat list MUST request the connection stats of the direct chats
   whose status is still unknown once per session in the background
   (`chatsForConnectionStatus` + `ChatModel.connectionStatusRequested`), so the dots are shown without
   opening each chat first. Groups that are already known, connections that are not ready and active,
   and connections requested earlier in the session MUST be skipped, and failures MUST be caught and
   swallowed so the dot simply stays hidden.
6. **The chat title only reports the open chat.** The direct chat title MUST NOT display a status of
   any connection other than the chat's own active connection - in particular it MUST NOT fall back
   to `chatSubStatus` unless that connection is the one currently opened
   (`chatModel.chatAgentConnId`), because `chatSubStatus` is not cleared when a chat is closed.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/helpers/SubscriptionStatusDot.kt`
  (`SubscriptionStatusDot`)
- `common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`
  (`connectionSubStatuses`, `connectionSubStatus`, `ChatModel.connectionStatus`)
- `common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`
  (`CR.SubscriptionStatusEvt` handler)
- `common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatListView.kt`
  (background request of the connection stats, `ChatModel.connectionStatusRequested`)
- `common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatView.kt`
  (`ChatInfoToolbarTitle`), `views/chatlist/ChatPreviewView.kt` (`chatPreviewTitle`),
  `views/chat/group/GroupChatInfoView.kt` (`MemberRow`), `views/chat/ChatInfoView.kt`
  (`SubStatusRow`, the info-page dot this component was extracted from, and the contact info page's
  network status row), `views/chat/group/GroupMemberInfoView.kt` (member info page's network status
  row)
- Test: `common/src/commonTest/kotlin/chat/simplex/app/ConnectionSubStatusTest.kt`

---

## 11. Servers Import and Export

### RULE-23: Import Merges Validated Custom Servers Only

1. **Only custom servers are exported.** The export MUST skip preset servers (they come from the
   client and operator configuration) and deleted servers, and it MUST keep the operator tag and the
   client-side group/note of each server so that an import can restore them.
2. **The export warning is mandatory.** The export MUST be preceded by a confirmation that states
   that the file contains the server addresses and verification keys (the address string carries the
   key hash and the basic-auth password) and that it must be kept private.
3. **The import warning is mandatory.** The import MUST be preceded by a confirmation that only
   trusted servers should be imported, because an imported server can see the user's connection
   metadata.
4. **Import validates and merges, it never replaces.** Every imported address MUST be validated with
   the core parser (`parseServerAddress`), MUST match the protocol of the list it was exported from,
   and MUST be rejected as invalid otherwise. Addresses already present in the same protocol MUST be
   skipped as duplicates, the existing servers MUST NOT be modified, and the merged list MUST be
   persisted through `setUserServers` like any other server change.
5. **Imported servers are ordinary custom servers.** An imported server MUST be stored with
   `preset = false`, `deleted = false` and no `remoteHostId`/`serverId`, and it MUST be added to the
   operator group with the same `OperatorTag`, falling back to the group without an operator.
6. **The backup format is client-only and separate.** The JSON file MUST NOT become part of
   `AppSettings` or of the cross-device backup format, and the core server list (`/_servers`) MUST
   NOT change its shape.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/ServersBackup.kt`
  (`serversBackupOf`, `exportServersBackup`, `parseServersBackup`, `mergeServersBackup`,
  `importedServerMetadata`)
- `common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/networkAndServers/NetworkAndServers.kt`
  (`ServersImportExportSection`, reuses `saveServers`)
- Test: `common/src/commonTest/kotlin/chat/simplex/app/ServersBackupTest.kt`

---

## 12. Group Connection Rate in the Chat List

### RULE-24: The Chat List Rate Uses Background-Loaded Member Lists Only

1. **Same computation as the info page.** The chat-list dot MUST be derived from the same helpers as
   the group info page (`groupMembersConnection` + `groupMembersConnectionRate`), so the two never
   disagree for the same member list.
2. **Only ordinary groups, only when the list is known.** The dot MUST NOT be shown for relay groups
   (channels) or for groups in `nextConnectPrepared`, mirroring `groupMembersSectionVisible` on the
   info page, and it MUST NOT be shown before a member list is cached - no optimistic or "unknown"
   state such as a grey dot.
3. **The prefetch is bounded and silent.** Member lists MUST be requested at most once per group per
   session (`groupRateRequested`), MUST be requested in the background off the UI thread, MUST skip
   groups whose own membership is inactive, and MUST NOT show progress or error UI in the chat list
   when a request fails. Failures, including an exception thrown by the request (for example when the
   group was deleted meanwhile), MUST be caught and swallowed so that only the dot stays hidden.
4. **The cache is session-local.** `ChatModel.groupMembersForRate` MUST NOT be persisted, MUST NOT be
   part of `AppSettings` or the cross-device backup, and MUST NOT be confused with
   `ChatModel.groupMembers`, which is the member list of the currently opened group.
5. **Member updates keep the cache fresh.** A `CR.GroupMemberUpdated` event MUST update the cached
   list when that group is cached: an active member replaces its entry or is appended, an inactive
   one is removed, and updates for groups that were never cached MUST be ignored.

**Enforcement:**
- `common/src/commonMain/kotlin/chat/simplex/common/model/ChatModel.kt`
  (`groupMembersForRate`, `groupRateRequested`, `updateGroupMembersForRate`,
  `groupChatsForConnectionRate`)
- `common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatListView.kt`
  (background prefetch effect)
- `common/src/commonMain/kotlin/chat/simplex/common/views/chatlist/ChatPreviewView.kt`
  (`chatPreviewTitle` group branch)
- `common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`
  (`GroupMembersConnectionRateDot`, shared with the member list title)
- Test: `common/src/commonTest/kotlin/chat/simplex/app/GroupConnectionRateCacheTest.kt`

