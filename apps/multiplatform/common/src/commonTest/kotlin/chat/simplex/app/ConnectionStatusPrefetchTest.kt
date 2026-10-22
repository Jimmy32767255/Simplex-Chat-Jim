package chat.simplex.app

import chat.simplex.common.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class ConnectionStatusPrefetchTest {
  private fun contact(
    contactId: Long = 1,
    connStatus: ConnStatus? = ConnStatus.Ready,
    agentConnId: String = "abc",
    contactStatus: ContactStatus = ContactStatus.Active,
    subStatus: SubscriptionStatus? = null
  ): Contact =
    Contact.sampleData.copy(
      contactId = contactId,
      contactStatus = contactStatus,
      activeConn = if (connStatus == null) null else Connection.sampleData.copy(
        agentConnId = agentConnId,
        connStatus = connStatus,
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
    )

  private fun directChat(c: Contact): Chat =
    Chat(remoteHostId = null, chatInfo = ChatInfo.Direct(c), chatItems = arrayListOf())

  private val statusUnknown: (Connection) -> Boolean = { false }

  private fun pending(chats: List<Chat>, requested: Set<String> = emptySet(), statusKnown: (Connection) -> Boolean = statusUnknown): List<Chat> =
    chatsForConnectionStatus(chats, requested, statusKnown)

  @Test
  fun testDirectChatWithUnknownStatusIsRequested() {
    val chat = directChat(contact())
    assertEquals(listOf(chat), pending(listOf(chat)))
  }

  @Test
  fun testKnownStatusIsNotRequested() {
    val chat = directChat(contact(subStatus = SubscriptionStatus.Active))
    assertEquals(emptyList(), pending(listOf(chat), statusKnown = { it.connectionStats?.subStatus != null }))
  }

  @Test
  fun testAlreadyRequestedConnectionIsNotRequestedAgain() {
    val chat = directChat(contact(agentConnId = "abc"))
    assertEquals(emptyList(), pending(listOf(chat), requested = setOf("abc")))
  }

  @Test
  fun testChatsWithoutAConnectionAreSkipped() {
    assertEquals(emptyList(), pending(listOf(directChat(contact(connStatus = null)))))
  }

  @Test
  fun testChatsThatAreNotReadyAreSkipped() {
    val prepared = directChat(contact(connStatus = ConnStatus.Prepared))
    val failed = directChat(contact(contactId = 2, connStatus = ConnStatus.Failed("error")))
    assertEquals(emptyList(), pending(listOf(prepared, failed)))
  }

  @Test
  fun testChatsThatAreNotActiveAreSkipped() {
    val deleted = directChat(contact(contactStatus = ContactStatus.Deleted))
    assertEquals(emptyList(), pending(listOf(deleted)))
  }

  @Test
  fun testGroupChatsAreSkipped() {
    val groupChat = Chat(
      remoteHostId = null,
      chatInfo = ChatInfo.Group(GroupInfo.sampleData, groupChatScope = null),
      chatItems = arrayListOf()
    )
    assertEquals(emptyList(), pending(listOf(groupChat)))
  }

  @Test
  fun testOnlyThePendingChatsAreReturned() {
    val pendingChat = directChat(contact(contactId = 1, agentConnId = "pending"))
    val knownChat = directChat(contact(contactId = 2, agentConnId = "known", subStatus = SubscriptionStatus.Active))
    val requestedChat = directChat(contact(contactId = 3, agentConnId = "requested"))
    val result = pending(
      listOf(pendingChat, knownChat, requestedChat),
      requested = setOf("requested"),
      statusKnown = { it.agentConnId == "known" }
    )
    assertEquals(listOf(pendingChat), result)
  }

  @Test
  fun testStatusKnownIsConsultedForEveryConnection() {
    val seen = mutableListOf<String>()
    pending(listOf(directChat(contact(agentConnId = "one")), directChat(contact(contactId = 2, agentConnId = "two")))) {
      seen.add(it.agentConnId)
      false
    }
    assertTrue(seen.containsAll(listOf("one", "two")))
  }
}
