package chat.simplex.app

import chat.simplex.common.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

// use this command for testing:
// ./gradlew desktopTest
class FavoritesPinnedFirstTest {
  private fun settings(favorite: Boolean): ChatSettings =
    ChatSettings(enableNtfs = MsgFilter.All, sendRcpts = null, favorite = favorite)

  private fun directChat(contactId: Long, favorite: Boolean): Chat =
    Chat(
      remoteHostId = null,
      chatInfo = ChatInfo.Direct(Contact.sampleData.copy(contactId = contactId, chatSettings = settings(favorite))),
      chatItems = arrayListOf()
    )

  private fun groupChat(groupId: Long, favorite: Boolean): Chat =
    Chat(
      remoteHostId = null,
      chatInfo = ChatInfo.Group(GroupInfo.sampleData.copy(groupId = groupId, chatSettings = settings(favorite)), groupChatScope = null),
      chatItems = arrayListOf()
    )

  @Test
  fun testOrderIsUnchangedWhenThePreferenceIsOff() {
    val chats = listOf(directChat(1, favorite = false), groupChat(2, favorite = true), directChat(3, favorite = false))
    assertEquals(chats, favoritesPinnedFirst(chats, favoritesAsPinned = false))
  }

  @Test
  fun testFavoriteChatsAreMovedToTheTop() {
    val first = directChat(1, favorite = false)
    val favorite = groupChat(2, favorite = true)
    val last = directChat(3, favorite = false)
    assertEquals(listOf(favorite, first, last), favoritesPinnedFirst(listOf(first, favorite, last), favoritesAsPinned = true))
  }

  @Test
  fun testOrderInsideBothGroupsIsPreserved() {
    val c1 = directChat(1, favorite = false)
    val c2 = directChat(2, favorite = true)
    val g3 = groupChat(3, favorite = false)
    val g4 = groupChat(4, favorite = true)
    val c5 = directChat(5, favorite = true)
    val g6 = groupChat(6, favorite = false)
    assertEquals(listOf(c2, g4, c5, c1, g3, g6), favoritesPinnedFirst(listOf(c1, c2, g3, g4, c5, g6), favoritesAsPinned = true))
  }

  @Test
  fun testNoFavoritesLeavesTheListAsIs() {
    val chats = listOf(directChat(1, favorite = false), groupChat(2, favorite = false))
    assertEquals(chats, favoritesPinnedFirst(chats, favoritesAsPinned = true))
  }

  @Test
  fun testAllFavoritesLeavesTheListAsIs() {
    val chats = listOf(directChat(1, favorite = true), groupChat(2, favorite = true))
    assertEquals(chats, favoritesPinnedFirst(chats, favoritesAsPinned = true))
  }

  @Test
  fun testEmptyListStaysEmpty() {
    assertEquals(emptyList(), favoritesPinnedFirst(emptyList(), favoritesAsPinned = true))
  }

  @Test
  fun testChatWithoutSettingsIsNotFavorite() {
    val chatWithoutSettings = Chat(
      remoteHostId = null,
      chatInfo = ChatInfo.ContactRequest.sampleData,
      chatItems = arrayListOf()
    )
    assertEquals(false, isFavoriteChat(chatWithoutSettings))
    val favorite = groupChat(2, favorite = true)
    assertEquals(listOf(favorite, chatWithoutSettings), favoritesPinnedFirst(listOf(chatWithoutSettings, favorite), favoritesAsPinned = true))
  }

  @Test
  fun testGroupsAndContactsAreBothRecognised() {
    assertEquals(true, isFavoriteChat(directChat(1, favorite = true)))
    assertEquals(true, isFavoriteChat(groupChat(2, favorite = true)))
    assertEquals(false, isFavoriteChat(directChat(3, favorite = false)))
  }
}
