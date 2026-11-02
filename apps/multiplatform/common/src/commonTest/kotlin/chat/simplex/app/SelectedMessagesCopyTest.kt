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
    assertEquals("Ali, $renderedTs\nhello", chatItemToText(ci, groupInfo(), "Me", timeText))
  }

  @Test
  fun testGroupMessageWithoutAliasUsesProfileName() {
    val ci = groupRcv("hello", member("Alice"))
    assertEquals("Alice, $renderedTs\nhello", chatItemToText(ci, groupInfo(), "Me", timeText))
  }

  @Test
  fun testDirectMessageUsesContactAlias() {
    val ci = directRcv("hi")
    assertEquals("Ali, $renderedTs\nhi", chatItemToText(ci, directInfo(alias = "Ali"), "Me", timeText))
  }

  @Test
  fun testDirectMessageWithoutAliasUsesContactName() {
    val ci = directRcv("hi")
    assertEquals("Alice, $renderedTs\nhi", chatItemToText(ci, directInfo(), "Me", timeText))
  }

  @Test
  fun testOwnDirectMessageUsesOwnName() {
    assertEquals("Me, $renderedTs\nhi", chatItemToText(snd("hi"), directInfo(), "Me", timeText))
  }

  @Test
  fun testOwnGroupMessageUsesOwnMemberNameOfThatGroup() {
    // the per-group profile name wins over the main profile name, because that is what the chat shows
    assertEquals("alice, $renderedTs\nhi", chatItemToText(sndFor(1, "hi"), groupInfo(), "Me", timeText))
  }

  @Test
  fun testOwnGroupMessageFallsBackToOwnNameWithoutMembership() {
    val membershipWithoutName = GroupMember.sampleData.copy(
      memberProfile = GroupMember.sampleData.memberProfile.copy(displayName = "")
    )
    val groupWithoutName = ChatInfo.Group(GroupInfo.sampleData.copy(membership = membershipWithoutName), null)
    assertEquals("Me, $renderedTs\nhi", chatItemToText(sndFor(1, "hi"), groupWithoutName, "Me", timeText))
  }

  @Test
  fun testNoteMessageUsesOwnName() {
    val note = ChatItem.getSampleData(id = 1, dir = CIDirection.LocalSnd(), ts = ts, text = "note")
    assertEquals("Me, $renderedTs\nnote", chatItemToText(note, ChatInfo.Local.sampleData, "Me", timeText))
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
    assertTrue(chatItemToText(ci, groupInfo(), "Me", timeText).contains(renderedTs))
    assertFalse(chatItemToText(ci, groupInfo(), "Me", timeText).contains("T(1798761600000)"))
  }

  @Test
  fun testSeveralMessagesAreSeparatedByABlankLineInConversationOrder() {
    val first = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "one")
    val second = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Bob")), ts = ts, text = "two")
    val third = sndFor(3, "three")
    val text = chatItemsToText(listOf(first, second, third), groupInfo(), "Me", timeText)
    assertEquals("Alice, $renderedTs\none\n\nBob, $renderedTs\ntwo\n\nalice, $renderedTs\nthree", text)
  }

  private fun sndFor(id: Long, text: String): ChatItem =
    ChatItem.getSampleData(id = id, dir = CIDirection.GroupSnd(), ts = ts, text = text)

  @Test
  fun testDeletedMessageIsSkipped() {
    val alive = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "alive")
    val deleted = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "dead", itemDeleted = CIDeleted.Deleted(null))
    assertEquals("Alice, $renderedTs\nalive", chatItemsToText(listOf(alive, deleted), groupInfo(), "Me", timeText))
  }

  @Test
  fun testFileMessageIsCopiedAsItsName() {
    val ci = ChatItem.getFileMsgContentSample(id = 1, fileName = "report.pdf")
    assertEquals("Alice, ${getTimestampTextOf(ci)}\nreport.pdf", chatItemToText(ci, directInfo(), "Me", { getTimestampTextOf(ci) }))
  }

  private fun getTimestampTextOf(ci: ChatItem): String = "T(${ci.meta.itemTs.toEpochMilliseconds()})"

  @Test
  fun testEmptySelectionIsEmptyText() {
    assertEquals("", chatItemsToText(emptyList(), groupInfo(), "Me", timeText))
  }

  @Test
  fun testCanCopyChatItem() {
    assertTrue(canCopyChatItem(ChatItem.getSampleData(dir = CIDirection.DirectRcv())))
    assertFalse(canCopyChatItem(ChatItem.getSampleData(dir = CIDirection.DirectRcv(), itemDeleted = CIDeleted.Deleted(null))))
  }

  @Test
  fun testSelectionKeepsTheOldestFirstOrderOfChatItems() {
    // ChatsContext.chatItems is stored oldest first, the selection must not reorder it
    val first = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "one")
    val second = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Bob")), ts = ts, text = "two")
    val third = ChatItem.getSampleData(id = 3, dir = CIDirection.GroupRcv(member("Carol")), ts = ts, text = "three")
    val selected = setOf(1L, 2L, 3L)
    val ordered = selectedChatItemsInOrder(listOf(first, second, third), selected)
    assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id })
    assertEquals("Alice, $renderedTs\none\n\nBob, $renderedTs\ntwo\n\nCarol, $renderedTs\nthree", chatItemsToText(ordered, groupInfo(), "Me", timeText))
  }

  @Test
  fun testSelectionSkipsUnselectedItemsAndKeepsOrder() {
    val first = ChatItem.getSampleData(id = 1, dir = CIDirection.GroupRcv(member("Alice")), ts = ts, text = "one")
    val second = ChatItem.getSampleData(id = 2, dir = CIDirection.GroupRcv(member("Bob")), ts = ts, text = "two")
    val third = ChatItem.getSampleData(id = 3, dir = CIDirection.GroupRcv(member("Carol")), ts = ts, text = "three")
    // a Set has no order of its own, the result follows the list, so the oldest selected message comes first
    assertEquals(listOf(1L, 3L), selectedChatItemsInOrder(listOf(first, second, third), setOf(3L, 1L)).map { it.id })
    assertEquals(emptyList(), selectedChatItemsInOrder(listOf(first, second, third), emptySet()))
  }

  @Test
  fun testSenderNameForOwnAndReceivedItems() {
    val chatInfo = directInfo(alias = "Ali")
    assertEquals("Me", ciSenderName(snd("hi"), chatInfo, "Me"))
    assertEquals("Me", ciSenderName(ChatItem.getSampleData(dir = CIDirection.LocalSnd(), text = "note"), chatInfo, "Me"))
    assertEquals("Me", ciSenderName(ChatItem.getSampleData(dir = CIDirection.LocalRcv(), text = "note"), chatInfo, "Me"))
    assertEquals("Ali", ciSenderName(directRcv("hi"), chatInfo, "Me"))
    assertEquals("alice", ciSenderName(sndFor(1, "hi"), groupInfo(), "Me"))
    // channel posts and other system-like directions have no sender name
    assertNull(ciSenderName(ChatItem.getSampleData(dir = CIDirection.ChannelRcv(), text = "post"), chatInfo, "Me"))
  }

  @Test
  fun testTimeFormatPreference() {
    assertTrue(uses24HourFormat(TimeFormat.SYSTEM, true))
    assertFalse(uses24HourFormat(TimeFormat.SYSTEM, false))
    assertFalse(uses24HourFormat(TimeFormat.HOUR_12, true))
    assertTrue(uses24HourFormat(TimeFormat.HOUR_24, false))
    assertEquals("HH:mm", timeOfDayPattern(true))
    assertEquals("h:mm a", timeOfDayPattern(false))
    assertEquals("HH:mm:ss", timeOfDayPatternWithSeconds(true))
    assertEquals("h:mm:ss a", timeOfDayPatternWithSeconds(false))
  }
}
