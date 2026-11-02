package chat.simplex.app

import chat.simplex.common.model.*
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// use this command for testing:
// ./gradlew desktopTest
class CopiedQuoteTest {
  private val ts = Instant.parse("2026-10-28T02:30:00Z")
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

  private fun quote(text: String, dir: CIDirection?): CIQuote = CIQuote.getSample(itemId = 1, sentAt = ts, text = text, chatDir = dir)

  private fun withQuote(text: String, quoted: CIQuote, dir: CIDirection = CIDirection.DirectRcv()): ChatItem =
    ChatItem.getSampleData(id = 2, dir = dir, ts = ts, text = text, quotedItem = quoted)

  // the marker itself follows the interface language, so the expectation is built from the same resource
  private fun quoteLine(msg: String, author: String?): String =
    if (author != null) {
      String.format(generalGetString(MR.strings.copy_quote_with_author), msg, author)
    } else {
      String.format(generalGetString(MR.strings.copy_quote), msg)
    }

  @Test
  fun testQuotedMessageIsPrependedToCopiedText() {
    val ci = withQuote("hi", quote("earlier", CIDirection.DirectRcv()))
    assertEquals("${quoteLine("earlier", "Alice")}\nhi", ciTextWithQuote(ci, directInfo(), "Me"))
  }

  @Test
  fun testQuoteLineComesAfterTheNameAndTimeOfTheMessage() {
    val ci = withQuote("hi", quote("earlier", CIDirection.DirectRcv()))
    assertEquals("Alice, $renderedTs\n${quoteLine("earlier", "Alice")}\nhi", chatItemToText(ci, directInfo(), "Me", timeText))
  }

  @Test
  fun testOwnQuotedMessageUsesTheOwnName() {
    val ci = withQuote("hi", quote("earlier", CIDirection.DirectSnd()), CIDirection.DirectSnd())
    assertEquals("${quoteLine("earlier", "Me")}\nhi", ciTextWithQuote(ci, directInfo(), "Me"))
  }

  @Test
  fun testQuotedGroupMemberUsesTheMemberNameOfThatGroup() {
    val quoted = quote("earlier", CIDirection.GroupRcv(member("Bob", alias = "B")))
    val ci = withQuote("hi", quoted, CIDirection.GroupRcv(member("Alice")))
    assertEquals("${quoteLine("earlier", "B")}\nhi", ciTextWithQuote(ci, groupInfo(), "Me"))
  }

  @Test
  fun testQuotedTextIsFoldedOntoOneLine() {
    val ci = withQuote("hi", quote("first\nsecond   third", CIDirection.DirectRcv()))
    assertEquals("${quoteLine("first second third", "Alice")}\nhi", ciTextWithQuote(ci, directInfo(), "Me"))
  }

  @Test
  fun testMessageWithoutQuoteIsCopiedAsBefore() {
    val ci = ChatItem.getSampleData(id = 1, dir = CIDirection.DirectRcv(), ts = ts, text = "hi")
    assertNull(ciQuoteLine(ci, directInfo(), "Me"))
    assertEquals("hi", ciTextWithQuote(ci, directInfo(), "Me"))
  }

  @Test
  fun testQuoteWithoutTextIsSkipped() {
    val ci = withQuote("hi", quote("   ", CIDirection.DirectRcv()))
    assertNull(ciQuoteLine(ci, directInfo(), "Me"))
    assertEquals("hi", ciTextWithQuote(ci, directInfo(), "Me"))
  }

  @Test
  fun testChannelQuoteHasNoAuthorName() {
    val ci = withQuote("hi", quote("earlier", CIDirection.ChannelRcv()))
    assertEquals(quoteLine("earlier", null), ciQuoteLine(ci, groupInfo(), "Me"))
  }

  @Test
  fun testQuoteLineIsUsedInTheMultiMessageCopy() {
    val plain = ChatItem.getSampleData(id = 1, dir = CIDirection.DirectRcv(), ts = ts, text = "first")
    val reply = withQuote("second", quote("first", CIDirection.DirectRcv()))
    val copied = chatItemsToText(listOf(plain, reply), directInfo(), "Me", timeText)
    assertEquals("Alice, $renderedTs\nfirst\n\nAlice, $renderedTs\n${quoteLine("first", "Alice")}\nsecond", copied)
  }

  @Test
  fun testQuoteLineContainsTheMarkerFromTheStringResource() {
    val line = quoteLine("earlier", "Alice")
    assertEquals(true, line.startsWith("["))
    assertEquals(true, line.endsWith("Alice]"))
    assertEquals(true, line.contains("earlier"))
  }
}
