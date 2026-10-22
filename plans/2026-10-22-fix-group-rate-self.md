# Fix: the group connection rate could never reach 100%

Date: 2026-10-22
Base version: 7.0.4
TODO item: `## bug fix:` → `群成员连接率似乎忘记排除自己了，导致永远无法达到百分之一百`

## Problem

The member list title of a group showed a rate such as `12/13 (92%)` and never `13/13 (100%)`,
even when every listed member had a ready connection.

## Cause

`groupMembersConnection` counted

```kotlin
(members.count { it.ready } + if (currentMembership.ready) 1 else 0) to (members.size + 1)
```

`GroupMember.ready` is `activeConn?.connStatus == ConnStatus.Ready`, but
`GroupInfo.membership` (the current user's own membership) normally carries no `activeConn` — the
connections are only present on the entries of the member list, which doesn't contain the current
user. So the `+1` term of the numerator was always `0` while the `+1` of the denominator was always
counted, and the rate was permanently short by one member.

## Fix

```kotlin
internal fun groupMembersConnection(members: List<GroupMember>, currentMembership: GroupMember): Pair<Int, Int> {
  val otherMembers = members.filter { it.groupMemberId != currentMembership.groupMemberId }
  val currentConnected = if (currentMembership.activeConn != null) currentMembership.ready else currentMembership.memberActive
  return (otherMembers.count { it.ready } + if (currentConnected) 1 else 0) to (otherMembers.size + 1)
}
```

- The own membership is filtered out of the list first, so it can never be counted twice when a
  member list does include it.
- It still counts towards the total (the title must keep matching the `N members` figure).
- It counts as connected when its membership is active (`memberActive`), because the client can only
  read the member list while it is connected to the group; when it does have an `activeConn` the
  stricter `ready` check is used.

## Files

- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/chat/group/GroupChatInfoView.kt`
- `apps/multiplatform/common/src/commonTest/kotlin/chat/simplex/app/GroupMembersConnectionRateTest.kt`
  (13 tests; distinct member ids, no-double-count and inactive-membership cases, and
  `testRateReachesOneHundredPercent`)

## Docs

- `apps/multiplatform/product/rules.md` (RULE-21 invariant 2)
- `apps/multiplatform/product/views/group-info.md` (Connection rate row)
- `apps/multiplatform/spec/client/chat-view.md` (§8 member list)

## Adversarial review

- *Could the own membership be counted twice?* No: it is filtered out by `groupMemberId` before the
  count, and both real lists (which lack it) and hypothetical lists (which include it) are covered by
  tests.
- *Could a member list entry share the own `groupMemberId` by accident?* No: `groupMemberId` is
  unique per group in the core, and the current membership is a member of the same group.
- *Does an inactive own membership still show a wrong rate?* It is counted in the total but not as
  connected (`testInactiveCurrentMembershipIsNotCountedAsConnected`), which is the intended reading.
- *Any effect on the chat list dot?* Both call sites use the same helper, so they stay consistent by
  construction (RULE-24 invariant 1).
