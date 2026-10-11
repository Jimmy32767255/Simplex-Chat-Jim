package chat.simplex.app

import chat.simplex.common.model.*
import chat.simplex.common.views.chat.group.GroupMembersConnectionRate
import chat.simplex.common.views.chat.group.groupMembersConnection
import chat.simplex.common.views.chat.group.groupMembersConnectionRate
import kotlin.test.Test
import kotlin.test.assertEquals

// use this command for testing:
// ./gradlew desktopTest
class GroupMembersConnectionRateTest {
  private val readyMember = GroupMember.sampleData

  private fun memberWithStatus(status: ConnStatus?): GroupMember =
    readyMember.copy(activeConn = if (status == null) null else Connection.sampleData.copy(connStatus = status))

  @Test
  fun testAllMembersConnected() {
    val members = listOf(memberWithStatus(ConnStatus.Ready), memberWithStatus(ConnStatus.Ready))
    assertEquals(3 to 3, groupMembersConnection(members, memberWithStatus(ConnStatus.Ready)))
  }

  @Test
  fun testNoMembersConnected() {
    val members = listOf(memberWithStatus(null), memberWithStatus(ConnStatus.Prepared))
    assertEquals(0 to 3, groupMembersConnection(members, memberWithStatus(null)))
  }

  @Test
  fun testPartiallyConnected() {
    val members = listOf(memberWithStatus(ConnStatus.Ready), memberWithStatus(null), memberWithStatus(ConnStatus.Failed("error")))
    assertEquals(1 to 4, groupMembersConnection(members, memberWithStatus(null)))
  }

  @Test
  fun testSendReadyIsNotCountedAsConnected() {
    assertEquals(0 to 2, groupMembersConnection(listOf(memberWithStatus(ConnStatus.SndReady)), memberWithStatus(ConnStatus.SndReady)))
  }

  @Test
  fun testEmptyGroupCountsOnlyCurrentMembership() {
    assertEquals(1 to 1, groupMembersConnection(emptyList(), memberWithStatus(ConnStatus.Ready)))
    assertEquals(0 to 1, groupMembersConnection(emptyList(), memberWithStatus(null)))
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
}
