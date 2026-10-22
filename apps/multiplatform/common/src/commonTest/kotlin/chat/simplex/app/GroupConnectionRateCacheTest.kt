package chat.simplex.app

import chat.simplex.common.model.*
import kotlin.test.*

// use this command for testing:
// ./gradlew desktopTest
class GroupConnectionRateCacheTest {
  private fun member(id: Long, status: GroupMemberStatus = GroupMemberStatus.MemComplete, groupId: Long = id): GroupMember =
    GroupMember.sampleData.copy(groupMemberId = id, groupId = groupId, memberStatus = status)

  private fun groupChat(
    groupId: Long,
    useRelays: Boolean = false,
    membershipStatus: GroupMemberStatus = GroupMemberStatus.MemComplete
  ): Chat {
    val groupInfo = GroupInfo.sampleData.copy(
      groupId = groupId,
      useRelays = useRelays,
      membership = member(groupId, membershipStatus, groupId)
    )
    return Chat(remoteHostId = null, chatInfo = ChatInfo.Group(groupInfo, groupChatScope = null), chatItems = arrayListOf())
  }

  @Test
  fun testOrdinaryGroupChatsAreRequested() {
    val chats = listOf(groupChat(1), Chat.sampleData, groupChat(2))
    assertEquals(listOf(1L, 2L), groupChatsForConnectionRate(chats, emptySet()).map { it.chatInfo.apiId })
  }

  @Test
  fun testChannelsAreSkipped() {
    val chats = listOf(groupChat(1, useRelays = true), groupChat(2))
    assertEquals(listOf(2L), groupChatsForConnectionRate(chats, emptySet()).map { it.chatInfo.apiId })
  }

  @Test
  fun testGroupsWithoutActiveMembershipAreSkipped() {
    val chats = listOf(groupChat(1, membershipStatus = GroupMemberStatus.MemLeft), groupChat(2, membershipStatus = GroupMemberStatus.MemRemoved))
    assertTrue(groupChatsForConnectionRate(chats, emptySet()).isEmpty())
  }

  @Test
  fun testAlreadyRequestedGroupsAreSkipped() {
    val chats = listOf(groupChat(1), groupChat(2))
    assertEquals(listOf(2L), groupChatsForConnectionRate(chats, setOf(1L)).map { it.chatInfo.apiId })
  }

  @Test
  fun testNonGroupChatsAreSkipped() {
    assertTrue(groupChatsForConnectionRate(listOf(Chat.sampleData), emptySet()).isEmpty())
  }

  @Test
  fun testMemberUpdateReplacesCachedMember() {
    ChatModel.groupMembersForRate.clear()
    ChatModel.groupMembersForRate[1L] = listOf(member(1), member(2))
    val changed = member(2).copy(localDisplayName = "bob")
    ChatModel.updateGroupMembersForRate(GroupInfo.sampleData.copy(groupId = 1), changed)
    assertEquals(listOf(member(1), changed), ChatModel.groupMembersForRate[1L])
  }

  @Test
  fun testMemberUpdateAppendsNewActiveMember() {
    ChatModel.groupMembersForRate.clear()
    ChatModel.groupMembersForRate[1L] = listOf(member(1))
    val added = member(3)
    ChatModel.updateGroupMembersForRate(GroupInfo.sampleData.copy(groupId = 1), added)
    assertEquals(listOf(member(1), added), ChatModel.groupMembersForRate[1L])
  }

  @Test
  fun testMemberUpdateRemovesInactiveMember() {
    ChatModel.groupMembersForRate.clear()
    ChatModel.groupMembersForRate[1L] = listOf(member(1), member(2))
    ChatModel.updateGroupMembersForRate(GroupInfo.sampleData.copy(groupId = 1), member(2).copy(memberStatus = GroupMemberStatus.MemLeft))
    assertEquals(listOf(member(1)), ChatModel.groupMembersForRate[1L])
  }

  @Test
  fun testMemberUpdateIgnoresUncachedGroup() {
    ChatModel.groupMembersForRate.clear()
    ChatModel.updateGroupMembersForRate(GroupInfo.sampleData.copy(groupId = 7), member(1))
    assertNull(ChatModel.groupMembersForRate[7L])
  }

  @Test
  fun testInactiveNewMemberIsNotAdded() {
    ChatModel.groupMembersForRate.clear()
    ChatModel.groupMembersForRate[1L] = listOf(member(1))
    ChatModel.updateGroupMembersForRate(GroupInfo.sampleData.copy(groupId = 1), member(2, GroupMemberStatus.MemInvited))
    assertEquals(listOf(member(1)), ChatModel.groupMembersForRate[1L])
  }

  @AfterTest
  fun clearCache() {
    ChatModel.groupMembersForRate.clear()
  }
}
