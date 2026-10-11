# Group: change every member's receiving address in one action

## Problem

Changing the receiving address of a connection already exists, but only one
connection at a time:

- a contact: `ChatInfoView.kt` → `apiSwitchContact`,
- a single group member: `GroupMemberInfoView.kt` → `apiSwitchGroupMember`.

A group always keeps one connection with every other member, so "move this
group to different servers" means repeating that action for every member.
TODO.md item 2 asks for a single action that does it for the whole group
(`一键更改群中所有人的接收地址`, i.e. rebuilding all of the user's member
connections at once).

## Solution summary

Add a group-level row, *Change all members' receiving addresses*, to
`GroupChatInfoView.kt` below the member list, visible only for non-relay
groups that have at least one member connection. After a confirmation alert
it runs the existing per-member `apiSwitchGroupMember` for every member with
an active connection and folds each result into the model with
`updateGroupMemberConnectionStats`.

The change reuses the existing layers untouched:

- No core or API change: `APISwitchGroupMember` already switches exactly one
  connection, and the client simply issues it per member.
- No new preference or persisted state.
- Per-member and per-contact switching keeps working exactly as before; the
  new row only batches the same operation.

The scope follows TODO.md item 3, which describes adding a manual server
picker *inside* the existing "change receiving address" flow — confirming
that item 2 is about batching that same flow, not about inventing a new one.

## Detailed technical design

### Which members are switched

`GroupChatInfoView` already builds `activeSortedMembers` (members that are
neither left nor removed, sorted by role). Only members whose `activeConn`
is non-null are switched:

```kotlin
for (member in activeSortedMembers.filter { it.activeConn != null }) { ... }
```

A member without an active connection has no connection to move, and the core
rejects the command with `CEGroupMemberNotActive` for such a member, which
would only surface an error alert. The user's own membership is not part of
`activeSortedMembers` (it is rendered separately from `groupInfo.membership`),
so it is naturally excluded.

Relay/channel groups (`groupInfo.useRelays`) are excluded by the same guard
that hides the member list; their per-relay controls live in
`ChannelRelaysView`.

### The batch action

`GroupChatInfoLayout` gains a `switchAllMembersAddress: () -> Unit` parameter.
`ModalData.GroupChatInfoView` implements it as:

```kotlin
switchAllMembersAddress = {
  showSwitchAllMembersAddressAlert {
    withBGApi {
      for (member in activeSortedMembers.filter { it.activeConn != null }) {
        val r = chatModel.controller.apiSwitchGroupMember(rhId, groupInfo.apiId, member.groupMemberId)
        if (r != null) {
          withContext(Dispatchers.Main) {
            chatModel.chatsContext.updateGroupMemberConnectionStats(rhId, groupInfo, r.first, r.second)
          }
          withContext(Dispatchers.Main) {
            chatModel.secondaryChatsContext.value?.updateGroupMemberConnectionStats(rhId, groupInfo, r.first, r.second)
          }
        }
      }
    }
  }
}
```

This is the same stats-update sequence as the per-member path in
`GroupMemberInfoView.kt`, only without `close.invoke()` (there is no member
modal to close). `apiSwitchGroupMember` returns `null` and shows the existing
`error_changing_address` alert on failure, so a failing member does not abort
the remaining members.

The loop runs on the background API dispatcher (`withBGApi`), so the UI stays
responsive; only the model updates hop to `Dispatchers.Main`.

### UI

A new `item { }` block between the member rows and the destructive actions:

```kotlin
if (!groupInfo.nextConnectPrepared && !groupInfo.useRelays && activeSortedMembers.any { it.activeConn != null }) {
  item {
    SectionDividerSpaced()
    SectionView {
      SwitchAllMembersAddressButton(disabled = false, switchAllMembersAddress = switchAllMembersAddress)
    }
    SectionTextFooter(stringResource(MR.strings.switch_all_members_address_footer))
  }
}
```

`SwitchAllMembersAddressButton` and `showSwitchAllMembersAddressAlert` are
added next to their single-connection counterparts in `ChatInfoView.kt`, reusing
`SectionItemView` and `AlertManager.showAlertDialog` so the new row looks and
behaves exactly like the existing "Change receiving address" row.

### New strings

`switch_all_members_address`, `switch_all_members_address_question`,
`switch_all_members_address_desc`, `switch_all_members_address_footer`
(English in `MR/base/strings.xml`, Chinese in `MR/zh-rCN/strings.xml`).

### Affected documentation

- `product/views/group-info.md` — the new section.
- `product/concepts.md` — new concept row PC33.
- `spec/impact.md` — PC33 on `ChatInfoView.kt`, `GroupChatInfoView.kt`,
  `GroupMemberInfoView.kt`.
- `spec/client/chat-view.md` — the GroupChatInfoView section.
- `spec/api.md` — `apiSwitchGroupMember` / `apiAbortSwitchGroupMember` rows and
  corrected line references for the switch/verification block.
- `TODO.md` — the implementation note for item 2.

## Detailed implementation steps

1. Add the four strings to `MR/base/strings.xml` and `MR/zh-rCN/strings.xml`.
2. Add `SwitchAllMembersAddressButton` and `showSwitchAllMembersAddressAlert`
   to `common/src/commonMain/kotlin/chat/simplex/common/views/chat/ChatInfoView.kt`.
3. Add the `switchAllMembersAddress` callback and its implementation to
   `GroupChatInfoView.kt`, add the parameter to `GroupChatInfoLayout`, and add
   the new section item (plus the preview call site).
4. Run `./gradlew :common:desktopTest` and confirm a green run.
5. Update the documents listed above, store the diff under `diff/`, and commit.

### Testing notes

The feature is UI plus an FFI call sequence, so there is no pure function to
unit-test (the only predicate, `activeConn != null`, is a null check).
Testing is the full `:common:desktopTest` run, which compiles every platform
source set that matters here (commonMain commonMain/desktopMain) and runs the
existing suite. A behavioural test would need a `ChatController` mock, which
the project's test sources do not provide.

## Adversarial self-review

- Does the loop touch members the core cannot switch? No: only members with a
  non-null `activeConn`, which is what makes `memberConnId` `Just` on the core
  side. All others are skipped before the call.
- Can the action be triggered for a group with no peers? No: the row is hidden
  unless some member has an active connection.
- Does a single failure abort the batch? No: `apiSwitchGroupMember` returns
  `null` after showing its own alert, and the loop continues.
- Does this change the single-member or contact flows? No: only a callback was
  added; `GroupMemberInfoView` and the contact path are untouched.
- Does it change relay/channel behaviour? No: the row is hidden for
  `groupInfo.useRelays`.
- Is the 7.0.4 default behaviour preserved? Yes — nothing changes unless the
  user presses the new row.
- Does the model end up consistent? Yes: every successful switch writes
  through `updateGroupMemberConnectionStats` on both the primary and secondary
  chats contexts, the same way the per-member path does.
