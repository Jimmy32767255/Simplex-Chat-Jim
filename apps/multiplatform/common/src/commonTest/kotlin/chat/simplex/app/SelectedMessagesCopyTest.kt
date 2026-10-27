package chat.simplex.app

import chat.simplex.common.model.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class SelectedMessagesCopyTest {
  private val ts = Instant.parse("2026-10-28T02:30:00Z")

  // a deterministic timestamp renderer, the real one is localized and time-zone dependent
  private val timeText: (Instant) -> String = { "T(${it.toEpochMilliseconds()})" }

  private val renderedTs = "T(${ts.toEpochMilliseconds()})"

  private fun member(displayName: String, alias: String = ""): GroupMember =
    GroupMember.sampleData.copy(
      memberProfile = GroupMember.sampleData.memberProfile.copy(displayName = displayName, localAlias = alias)
    )

  private fun directInfo(alias: String = "", displayName: String = "Alice"): ChatInfo.Direct =
    ChatInfo.Direct(
      Contact.sampleData.copy(
        profile = Contact.sampleData.profile.copy(displayName = displayName, localAlias = alias)
      )
    )

  private fun groupInfo(): ChatInfo.Group = ChatInfo.Group(GroupInfo.sampleData, groupChatScope = null)

  private fun groupRcv(text: String, sender: GroupMember, ts: Instant = this.ts): ChatItem =
    ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(sender), ts = ts, text = text)

  private fun directRcv(text: String, ts: Instant = this.ts): ChatItem =
    ChatItem.getSampleData(id = 1, dir = CIDirection.DirectRcv(), ts = ts, text = text)

  private fun snd(text: String, ts: Instant = this.ts): ChatItem =
    ChatItem.getSampleData(id = 1, dir = CIDirection.DirectSnd(), ts = ts, text = text)

  @Test
  fun testGroupMessageUsesLocalAlias() {
    val ci = groupRcv("hello", member("Alice", alias = "Ali"))
    assertEquals("Ali, $renderedTs\nhello", chatItemToText(ci, groupInfo(), timeText))
  }

  @Test
  fun testGroupMessageWithoutAliasUsesProfileName() {
    val ci = groupRcv("hello", member("Alice"))
    assertEquals("Alice, $renderedTs\nhello", chatItemToText(ci, groupInfo(), timeText))
  }

  @Test
  fun testDirectMessageUsesContactAlias() {
    val ci = directRcv("hi")
    assertEquals("Ali, $renderedTs\nhi", chatItemToText(ci, directInfo(alias = "Ali"), timeText))
  }

  @Test
  fun testDirectMessageWithoutAliasUsesContactName() {
    val ci = directRcv("hi")
    assertEquals("Alice, $renderedTs\nhi", chatItemToText(ci, directInfo(), timeText))
  }

  @Test
  fun testOwnMessageHasNoSenderName() {
    assertEquals("$renderedTs\nhi", chatItemToText(snd("hi"), directInfo(), timeText))
  }

  @Test
  fun testSendTimeIsUsedNotReceiveTime() {
    // createdAt is the receive time, itemTs is the send time
    val ci = groupRcv("hello", member("Alice", alias = "Ali")).copy(
      meta = groupRcv("hello", member("Alice")).meta.copy(
        itemTs = ts,
        createdAt = Instant.parse("2027-01-01T00:00:00Z")
      )
    )
    assertTrue(chatItemToText(ci, groupInfo(), timeText).contains(renderedTs))
    assertFalse(chatItemToText(ci, groupInfo(), timeText).contains("T(1798761600000)"))
  }

  @Test
  fun testSeveralMessagesAreSeparatedByABlankLineInConversationOrder() {
    val first = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "one")
    val second = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Bob")), ts = ts, text = "two")
    val third = sndFor(3, "three")
    val text = chatItemsToText(listOf(first, second, third), groupInfo(), timeText)
    assertEquals("Alice, $renderedTs\none\n\nBob, $renderedTs\ntwo\n\n$renderedTs\nthree", text)
  }

  private fun sndFor(id: Long, text: String): ChatItem =
    ChatItem.getSampleData(id = id, dir = CIDirection.GroupSnd(), ts = ts, text = text)

  @Test
  fun testDeletedMessageIsSkipped() {
    val alive = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "alive")
    val deleted = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "dead", itemDeleted = CIDeleted.Deleted(null))
    assertEquals("Alice, $renderedTs\nalive", chatItemsToText(listOf(alive, deleted), groupInfo(), timeText))
  }

  @Test
  fun testFileMessageIsCopiedAsItsName() {
    val ci = ChatItem.getFileMsgContentSample(id = 1, fileName = "report.pdf")
    assertEquals("Alice, ${getTimestampTextOf(ci)}\nreport.pdf", chatItemToText(ci, directInfo(), { getTimestampTextOf(ci) }))
  }

  private fun getTimestampTextOf(ci: ChatItem): String = "T(${ci.meta.itemTs.toEpochMilliseconds()})"

  @Test
  fun testEmptySelectionIsEmptyText() {
    assertEquals("", chatItemsToText(emptyList(), groupInfo(), timeText))
  }

  @Test
  fun testCanCopyChatItem() {
    assertTrue(canCopyChatItem(ChatItem.getSampleData(dir = CIDirection.DirectRcv())))
    assertFalse(canCopyChatItem(ChatItem.getSampleData(dir = CIDirection.DirectRcv(), itemDeleted = CIDeleted.Deleted(null))))
  }

  @Test
  fun testSenderNameIsNullForOwnAndNotedItems() {
    val chatInfo = directInfo(alias = "Ali")
    assertNull(ciSenderName(snd("hi"), chatInfo))
    assertNull(ciSenderName(ChatItem.getSampleData(dir = CIDirection.LocalSnd(), text = "note"), chatInfo))
    assertEquals("Ali", ciSenderName(directRcv("hi"), chatInfo))
  }
}
