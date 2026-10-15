package chat.simplex.app

import chat.simplex.common.model.*
import chat.simplex.common.views.usersettings.networkAndServers.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class ServersBackupTest {
  private val smpAddress = "smp://key@example.com"
  private val xftpAddress = "xftp://key@files.example.com"
  private val anotherSmpAddress = "smp://another@second.example"

  // The core's chatParseServer is a JNI call that is not available in unit tests, so the merge
  // logic receives this stub parser instead of the real one.
  private fun stubParse(address: String): ServerAddress? = when (address) {
    smpAddress -> ServerAddress(ServerProtocol.SMP, listOf("example.com"), "", "key")
    anotherSmpAddress -> ServerAddress(ServerProtocol.SMP, listOf("second.example"), "", "another")
    xftpAddress -> ServerAddress(ServerProtocol.XFTP, listOf("files.example.com"), "", "key")
    // duplicated hostname -> ServerAddress.valid == false
    "smp://key@bad.example" -> ServerAddress(ServerProtocol.SMP, listOf("bad.example", "bad.example"), "", "key")
    else -> null
  }

  private fun smpServer(
    address: String,
    preset: Boolean = false,
    deleted: Boolean = false,
    enabled: Boolean = true,
    tested: Boolean? = null,
  ) = UserServer(
    remoteHostId = null,
    serverId = null,
    server = address,
    preset = preset,
    tested = tested,
    enabled = enabled,
    deleted = deleted,
  )

  private fun operator(tag: OperatorTag, domains: List<String> = emptyList()) = ServerOperator(
    operatorId = 1,
    operatorTag = tag,
    tradeName = tag.name,
    legalName = null,
    serverDomains = domains,
    conditionsAcceptance = ConditionsAcceptance.Accepted(null, autoAccepted = false),
    enabled = true,
    smpRoles = ServerRoles.noOperatorDefault,
    xftpRoles = ServerRoles.noOperatorDefault,
  )

  private fun noOperatorGroup(
    smp: List<UserServer> = emptyList(),
    xftp: List<UserServer> = emptyList(),
  ) = UserOperatorServers(operator = null, smpServers = smp, xftpServers = xftp)

  @Test
  fun testExportSkipsPresetDeletedAndBlankServers() {
    val servers = listOf(
      noOperatorGroup(smp = listOf(smpServer(smpAddress), smpServer("smp://preset@example.com", preset = true), smpServer("smp://deleted@example.com", deleted = true), smpServer("")))
    )
    val backup = serversBackupOf(servers)
    assertEquals(listOf(smpAddress), backup.smpServers.map { it.server })
    assertTrue(backup.xftpServers.isEmpty())
    assertEquals(1, backup.version)
  }

  @Test
  fun testExportKeepsSettingsOperatorAndMetadata() {
    val servers = listOf(
      UserOperatorServers(operator(tag = OperatorTag.SimpleX), smpServers = listOf(smpServer(anotherSmpAddress, enabled = false, tested = true)), xftpServers = emptyList()),
      noOperatorGroup(smp = listOf(smpServer(smpAddress)))
    )
    val metadata = mapOf(
      smpAddress to ServerMeta(group = "Fast", note = "primary"),
      anotherSmpAddress to ServerMeta(group = "  ", note = null),
    )
    val backup = serversBackupOf(servers, metadata)
    assertEquals(listOf(anotherSmpAddress, smpAddress), backup.smpServers.map { it.server })
    val first = backup.smpServers[0]
    assertEquals(OperatorTag.SimpleX, first.operator)
    assertEquals(false, first.enabled)
    assertEquals(true, first.tested)
    assertNull(first.group)
    val second = backup.smpServers[1]
    assertNull(second.operator)
    assertEquals("Fast", second.group)
    assertEquals("primary", second.note)
  }

  @Test
  fun testExportIncludesXftpServers() {
    val backup = serversBackupOf(listOf(noOperatorGroup(xftp = listOf(smpServer(xftpAddress)))))
    assertEquals(listOf(xftpAddress), backup.xftpServers.map { it.server })
  }

  @Test
  fun testJsonRoundTrip() {
    val backup = ServersBackup(
      smpServers = listOf(ServersBackupItem(server = smpAddress, enabled = false, tested = true, roles = ServerRolesOverride(storage = false), operator = OperatorTag.Flux, group = "Fast", note = "n")),
      xftpServers = listOf(ServersBackupItem(server = xftpAddress)),
    )
    val json = exportServersBackup(backup)
    assertTrue(json.startsWith("{"))
    assertEquals(backup, parseServersBackup(json))
  }

  @Test
  fun testParseServersBackupReturnsNullOnGarbage() {
    assertNull(parseServersBackup("not json"))
    assertNull(parseServersBackup(""))
    assertNull(parseServersBackup("[1, 2, 3]"))
  }

  @Test
  fun testParseServersBackupIgnoresUnknownKeysAndUsesDefaults() {
    val backup = parseServersBackup("""{"version":1,"smpServers":[{"server":"$smpAddress","futureField":42}]}""")
    assertEquals(ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress))), backup)
  }

  @Test
  fun testMergeAddsToMatchingOperatorGroup() {
    val current = listOf(
      UserOperatorServers(operator(tag = OperatorTag.SimpleX, domains = listOf("simplex.chat")), smpServers = emptyList(), xftpServers = emptyList()),
      noOperatorGroup()
    )
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress, operator = OperatorTag.SimpleX)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(1, result.added.size)
    assertTrue(result.duplicates.isEmpty())
    assertTrue(result.invalid.isEmpty())
    assertEquals(listOf(smpAddress), result.servers[0].smpServers.map { it.server })
    assertTrue(result.servers[1].smpServers.isEmpty())
    val added = result.servers[0].smpServers[0]
    assertEquals(false, added.preset)
    assertEquals(false, added.deleted)
    assertNull(added.remoteHostId)
    assertNull(added.serverId)
  }

  @Test
  fun testMergeFallsBackToNoOperatorGroupWhenTagIsMissing() {
    val current = listOf(
      UserOperatorServers(operator(tag = OperatorTag.SimpleX), smpServers = emptyList(), xftpServers = emptyList()),
      noOperatorGroup()
    )
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress, operator = OperatorTag.Flux)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(listOf(smpAddress), result.servers[1].smpServers.map { it.server })
    assertTrue(result.servers[0].smpServers.isEmpty())
  }

  @Test
  fun testMergeFallsBackToNoOperatorGroupWhenItemHasNoOperator() {
    val current = listOf(
      UserOperatorServers(operator(tag = OperatorTag.SimpleX), smpServers = emptyList(), xftpServers = emptyList()),
      noOperatorGroup()
    )
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(listOf(smpAddress), result.servers[1].smpServers.map { it.server })
  }

  @Test
  fun testMergeCreatesNoOperatorGroup() {
    val current = listOf(UserOperatorServers(operator(tag = OperatorTag.SimpleX), smpServers = emptyList(), xftpServers = emptyList()))
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(2, result.servers.size)
    assertNull(result.servers[1].operator)
    assertEquals(listOf(smpAddress), result.servers[1].smpServers.map { it.server })
  }

  @Test
  fun testMergeSkipsDuplicates() {
    val current = listOf(noOperatorGroup(smp = listOf(smpServer(smpAddress)), xftp = listOf(smpServer(xftpAddress))))
    val backup = ServersBackup(
      smpServers = listOf(ServersBackupItem(server = smpAddress), ServersBackupItem(server = " $smpAddress ")),
      xftpServers = listOf(ServersBackupItem(server = xftpAddress))
    )
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertTrue(result.added.isEmpty())
    assertEquals(3, result.duplicates.size)
    assertEquals(1, result.servers[0].smpServers.size)
  }

  @Test
  fun testMergeTreatsDeletedServerAsNotPresent() {
    val current = listOf(noOperatorGroup(smp = listOf(smpServer(smpAddress, deleted = true))))
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(1, result.added.size)
    assertTrue(result.duplicates.isEmpty())
    assertEquals(2, result.servers[0].smpServers.size)
  }

  @Test
  fun testMergeReportsInvalidAndWrongProtocolAddresses() {
    val current = listOf(noOperatorGroup())
    val backup = ServersBackup(
      smpServers = listOf(
        ServersBackupItem(server = ""),
        ServersBackupItem(server = "not an address"),
        ServersBackupItem(server = "smp://key@bad.example"),
        ServersBackupItem(server = xftpAddress)
      ),
      xftpServers = listOf(ServersBackupItem(server = smpAddress))
    )
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertTrue(result.added.isEmpty())
    assertEquals(listOf("", "not an address", "smp://key@bad.example", xftpAddress, smpAddress), result.invalid)
    assertTrue(result.servers[0].smpServers.isEmpty())
    assertTrue(result.servers[0].xftpServers.isEmpty())
  }

  @Test
  fun testMergeKeepsImportedSettings() {
    val current = listOf(noOperatorGroup())
    val roles = ServerRolesOverride(storage = false, proxy = true)
    val backup = ServersBackup(smpServers = listOf(ServersBackupItem(server = smpAddress, enabled = false, tested = true, roles = roles)))
    val result = mergeServersBackup(current, backup, ::stubParse)
    val added = result.servers[0].smpServers[0]
    assertEquals(false, added.enabled)
    assertEquals(true, added.tested)
    assertEquals(roles, added.roles)
  }

  @Test
  fun testMergeAddsSmpAndXftpToTheSameGroup() {
    val current = listOf(noOperatorGroup())
    val backup = ServersBackup(
      smpServers = listOf(ServersBackupItem(server = smpAddress)),
      xftpServers = listOf(ServersBackupItem(server = xftpAddress))
    )
    val result = mergeServersBackup(current, backup, ::stubParse)
    assertEquals(2, result.added.size)
    assertEquals(listOf(smpAddress), result.servers[0].smpServers.map { it.server })
    assertEquals(listOf(xftpAddress), result.servers[0].xftpServers.map { it.server })
    assertEquals(listOf(ServerProtocol.SMP, ServerProtocol.XFTP), result.added.map { it.protocol })
  }

  @Test
  fun testImportedServerMetadata() {
    val added = listOf(
      ImportedServer(ServerProtocol.SMP, ServersBackupItem(server = smpAddress, group = "Fast", note = "primary")),
      ImportedServer(ServerProtocol.XFTP, ServersBackupItem(server = xftpAddress))
    )
    val current = mapOf(xftpAddress to ServerMeta(group = "Old", note = "old"))
    val result = importedServerMetadata(current, added)
    assertEquals(mapOf(smpAddress to ServerMeta(group = "Fast", note = "primary")), result)
  }

  @Test
  fun testImportedServerMetadataDropsStaleEntryWithoutMetadata() {
    val added = listOf(ImportedServer(ServerProtocol.SMP, ServersBackupItem(server = smpAddress)))
    val current = mapOf(smpAddress to ServerMeta(group = "Fast", note = null), anotherSmpAddress to ServerMeta(group = "Slow", note = null))
    assertEquals(mapOf(anotherSmpAddress to ServerMeta(group = "Slow", note = null)), importedServerMetadata(current, added))
  }
}
