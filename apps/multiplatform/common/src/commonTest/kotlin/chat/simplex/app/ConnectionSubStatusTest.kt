package chat.simplex.app

import chat.simplex.common.model.*
import kotlin.test.*

class ConnectionSubStatusTest {
  private fun conn(agentConnId: String = "abc", subStatus: SubscriptionStatus? = null): Connection =
    Connection.sampleData.copy(
      agentConnId = agentConnId,
      connectionStats = subStatus?.let {
        ConnectionStats(
          connAgentVersion = 1,
          rcvQueuesInfo = emptyList(),
          sndQueuesInfo = emptyList(),
          ratchetSyncState = RatchetSyncState.Ok,
          ratchetSyncSupported = true,
          subStatus = it
        )
      }
    )

  @Test
  fun testNoConnection() {
    assertNull(connectionSubStatus(null, mapOf("abc" to SubscriptionStatus.Active)))
  }

  @Test
  fun testNoStatusKnown() {
    assertNull(connectionSubStatus(conn(), emptyMap()))
  }

  @Test
  fun testLiveStatusIsUsed() {
    val c = conn(agentConnId = "abc", subStatus = SubscriptionStatus.Pending)
    assertEquals(SubscriptionStatus.Active, connectionSubStatus(c, mapOf("abc" to SubscriptionStatus.Active)))
  }

  @Test
  fun testFallsBackToConnectionStats() {
    val c = conn(agentConnId = "abc", subStatus = SubscriptionStatus.Pending)
    assertEquals(SubscriptionStatus.Pending, connectionSubStatus(c, mapOf("other" to SubscriptionStatus.Active)))
  }

  @Test
  fun testStatusOfAnotherConnectionIsIgnored() {
    val c = conn(agentConnId = "abc")
    assertNull(connectionSubStatus(c, mapOf("other" to SubscriptionStatus.Active)))
  }

  @Test
  fun testRemovedStatus() {
    val c = conn(agentConnId = "abc")
    val removed = SubscriptionStatus.Removed("subscription error")
    assertEquals(removed, connectionSubStatus(c, mapOf("abc" to removed)))
  }

  @Test
  fun testNoSubStatus() {
    val c = conn(agentConnId = "abc", subStatus = SubscriptionStatus.NoSub)
    assertEquals(SubscriptionStatus.NoSub, connectionSubStatus(c, emptyMap()))
  }
}
