package chat.simplex.app

import chat.simplex.common.model.ServerMeta
import chat.simplex.common.views.usersettings.networkAndServers.serverGroup
import chat.simplex.common.views.usersettings.networkAndServers.updatedServerMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// use this command for testing:
// ./gradlew desktopTest
class ServerMetadataTest {
  private val smp1 = "smp://a@one.example"
  private val smp2 = "smp://b@two.example"

  @Test
  fun testAddEntry() {
    val result = updatedServerMetadata(emptyMap(), smp1, smp1, ServerMeta(group = "Fast", note = "primary"))
    assertEquals(mapOf(smp1 to ServerMeta(group = "Fast", note = "primary")), result)
  }

  @Test
  fun testUpdateEntry() {
    val current = mapOf(smp1 to ServerMeta(group = "Fast", note = null))
    val result = updatedServerMetadata(current, smp1, smp1, ServerMeta(group = "Slow", note = "backup"))
    assertEquals(mapOf(smp1 to ServerMeta(group = "Slow", note = "backup")), result)
  }

  @Test
  fun testBlankMetaRemovesEntry() {
    val current = mapOf(smp1 to ServerMeta(group = "Fast", note = "primary"))
    assertEquals(emptyMap(), updatedServerMetadata(current, smp1, smp1, ServerMeta.empty))
    assertEquals(emptyMap(), updatedServerMetadata(current, smp1, smp1, ServerMeta(group = "  ", note = "\t")))
  }

  @Test
  fun testBlankMetaOnMissingEntryKeepsMap() {
    assertEquals(emptyMap(), updatedServerMetadata(emptyMap(), smp1, smp1, ServerMeta.empty))
  }

  @Test
  fun testAddressChangeMovesEntry() {
    val current = mapOf(smp1 to ServerMeta(group = "Fast", note = "primary"))
    val result = updatedServerMetadata(current, smp1, smp2, ServerMeta(group = "Fast", note = "primary"))
    assertEquals(mapOf(smp2 to ServerMeta(group = "Fast", note = "primary")), result)
  }

  @Test
  fun testAddressChangeToBlankRemovesOldEntry() {
    val current = mapOf(smp1 to ServerMeta(group = "Fast", note = "primary"))
    assertEquals(emptyMap(), updatedServerMetadata(current, smp1, smp2, ServerMeta.empty))
  }

  @Test
  fun testOtherEntriesAreKept() {
    val current = mapOf(smp1 to ServerMeta(group = "Fast", note = "primary"))
    val result = updatedServerMetadata(current, smp2, smp2, ServerMeta(group = "Slow", note = null))
    assertEquals(mapOf(smp1 to current.getValue(smp1), smp2 to ServerMeta(group = "Slow", note = null)), result)
  }

  @Test
  fun testServerGroup() {
    val metadata = mapOf(smp1 to ServerMeta(group = "Fast", note = null), smp2 to ServerMeta(group = "  ", note = "x"))
    assertEquals("Fast", serverGroup(metadata, smp1))
    assertNull(serverGroup(metadata, smp2))
    assertNull(serverGroup(metadata, "smp://unknown@example"))
  }
}
