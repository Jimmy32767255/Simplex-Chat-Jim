package chat.simplex.app

import chat.simplex.common.views.usersettings.networkAndServers.serverAddresses
import kotlin.test.Test
import kotlin.test.assertEquals

// use this command for testing:
// ./gradlew desktopTest
class BatchServersTest {
  @Test
  fun testSingleAddress() {
    assertEquals(listOf("smp://abc@example.com"), serverAddresses("smp://abc@example.com"))
  }

  @Test
  fun testMultipleAddressesOnePerLine() {
    assertEquals(
      listOf("smp://a@one.example", "smp://b@two.example", "xftp://c@three.example"),
      serverAddresses("smp://a@one.example\nsmp://b@two.example\nxftp://c@three.example")
    )
  }

  @Test
  fun testWindowsAndOldMacLineEndings() {
    assertEquals(
      listOf("smp://a@one.example", "smp://b@two.example"),
      serverAddresses("smp://a@one.example\r\nsmp://b@two.example")
    )
    assertEquals(
      listOf("smp://a@one.example", "smp://b@two.example"),
      serverAddresses("smp://a@one.example\rsmp://b@two.example")
    )
  }

  @Test
  fun testBlankLinesAndSurroundingSpacesAreIgnored() {
    assertEquals(
      listOf("smp://a@one.example", "smp://b@two.example"),
      serverAddresses("\n  smp://a@one.example  \n\n\tsmp://b@two.example\t\n   \n")
    )
  }

  @Test
  fun testDuplicateAddressesAreRemovedInOrder() {
    assertEquals(
      listOf("smp://a@one.example", "smp://b@two.example"),
      serverAddresses("smp://a@one.example\nsmp://b@two.example\nsmp://a@one.example")
    )
  }

  @Test
  fun testEmptyInput() {
    assertEquals(emptyList(), serverAddresses(""))
    assertEquals(emptyList(), serverAddresses("   \n \t \n"))
  }
}
