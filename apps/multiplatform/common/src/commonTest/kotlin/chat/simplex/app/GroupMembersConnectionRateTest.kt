package chat.simplex.app

import chat.simplex.common.model.*
import chat.simplex.common.views.chat.group.GroupMembersConnectionRate
import chat.simplex.common.views.chat.group.groupMembersConnection
import chat.simplex.common.views.chat.group.groupMembersConnectionRate
import chat.simplex.common.views.chat.group.groupMembersRowsVisible
import chat.simplex.common.views.chat.group.groupMembersSectionVisible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class GroupMembersConnectionRateTest {
  private val readyMember = GroupMember.sampleData

  // the current user's own membership never has the id of a listed member
  private val currentUserId = 100L

  private fun member(id: Long, status: ConnStatus?, memberStatus: GroupMemberStatus = GroupMemberStatus.MemComplete): GroupMember =
    readyMember.copy(
      groupMemberId = id,
      memberStatus = memberStatus,
      activeConn = if (status == null) null else Connection.sampleData.copy(connStatus = status)
    )

  private fun currentUser(status: ConnStatus?, memberStatus: GroupMemberStatus = GroupMemberStatus.MemComplete): GroupMember =
    member(currentUserId, status, memberStatus)

  @Test
  fun testAllMembersConnected() {
    val members = listOf(member(2, ConnStatus.Ready), member(3, ConnStatus.Ready))
    // the current membership has no active connection to check, it is connected when it is active
    assertEquals(3 to 3, groupMembersConnection(members, currentUser(status = null)))
  }

  @Test
  fun testNoMembersConnected() {
    val members = listOf(member(2, null), member(3, ConnStatus.Prepared))
    assertEquals(1 to 3, groupMembersConnection(members, currentUser(status = null)))
  }

  @Test
  fun testPartiallyConnected() {
    val members = listOf(member(2, ConnStatus.Ready), member(3, null), member(4, ConnStatus.Failed("error")))
    assertEquals(2 to 4, groupMembersConnection(members, currentUser(status = null)))
  }

  @Test
  fun testSendReadyIsNotCountedAsConnected() {
    val members = listOf(member(2, ConnStatus.SndReady))
    assertEquals(0 to 2, groupMembersConnection(members, currentUser(status = ConnStatus.SndReady)))
  }

  @Test
  fun testEmptyGroupCountsOnlyCurrentMembership() {
    assertEquals(1 to 1, groupMembersConnection(emptyList(), currentUser(status = null)))
    assertEquals(1 to 1, groupMembersConnection(emptyList(), currentUser(status = ConnStatus.Ready)))
  }

  @Test
  fun testRateReachesOneHundredPercent() {
    val members = listOf(member(2, ConnStatus.Ready), member(3, ConnStatus.Ready))
    val (connected, total) = groupMembersConnection(members, currentUser(status = null))
    assertEquals(GroupMembersConnectionRate.ALL_CONNECTED, groupMembersConnectionRate(connected, total))
  }

  @Test
  fun testCurrentUserIsCountedOnlyOnce() {
    // some member lists do contain the current user's own membership
    val members = listOf(member(currentUserId, ConnStatus.Ready), member(2, ConnStatus.Ready))
    assertEquals(2 to 2, groupMembersConnection(members, currentUser(status = null)))
  }

  @Test
  fun testInactiveCurrentMembershipIsNotCountedAsConnected() {
    val members = listOf(member(2, ConnStatus.Ready), member(3, ConnStatus.Ready))
    assertEquals(2 to 3, groupMembersConnection(members, currentUser(status = null, memberStatus = GroupMemberStatus.MemLeft)))
  }

  @Test
  fun testRateAllConnected() {
    assertEquals(GroupMembersConnectionRate.ALL_CONNECTED, groupMembersConnectionRate(13, 13))
  }

  @Test
  fun testRatePartiallyConnected() {
    assertEquals(GroupMembersConnectionRate.PARTIALLY_CONNECTED, groupMembersConnectionRate(6, 13))
  }

  @Test
  fun testRateNoneConnected() {
    assertEquals(GroupMembersConnectionRate.NONE_CONNECTED, groupMembersConnectionRate(0, 13))
    assertEquals(GroupMembersConnectionRate.NONE_CONNECTED, groupMembersConnectionRate(0, 0))
    // never over-reports a full connection
    assertEquals(GroupMembersConnectionRate.ALL_CONNECTED, groupMembersConnectionRate(15, 13))
  }

  @Test
  fun testSectionVisibleOnlyForPlainGroups() {
    assertTrue(groupMembersSectionVisible(nextConnectPrepared = false, useRelays = false))
    assertFalse(groupMembersSectionVisible(nextConnectPrepared = true, useRelays = false))
    assertFalse(groupMembersSectionVisible(nextConnectPrepared = false, useRelays = true))
    assertFalse(groupMembersSectionVisible(nextConnectPrepared = true, useRelays = true))
  }

  @Test
  fun testRowsHiddenWhenCollapsed() {
    assertTrue(groupMembersRowsVisible(nextConnectPrepared = false, useRelays = false, collapsed = false))
    assertFalse(groupMembersRowsVisible(nextConnectPrepared = false, useRelays = false, collapsed = true))
    // collapsing can't reveal a list that is not shown anyway
    assertFalse(groupMembersRowsVisible(nextConnectPrepared = false, useRelays = true, collapsed = false))
    assertFalse(groupMembersRowsVisible(nextConnectPrepared = true, useRelays = false, collapsed = false))
  }
}
