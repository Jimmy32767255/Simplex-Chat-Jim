package chat.simplex.common.views.usersettings.networkAndServers

import chat.simplex.common.model.*
import chat.simplex.common.model.ServerAddress.Companion.parseServerAddress
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A portable JSON backup of the user's custom (non-preset) servers.
 *
 * Every server carries its own address, settings and the operator it was added to, so that the
 * file can be imported into another profile or device that has the same server list structure
 * returned by the core's `/_servers` command.
 */
@Serializable
data class ServersBackup(
  val version: Int = 1,
  val smpServers: List<ServersBackupItem> = emptyList(),
  val xftpServers: List<ServersBackupItem> = emptyList(),
)

@Serializable
data class ServersBackupItem(
  val server: String,
  val enabled: Boolean = true,
  val tested: Boolean? = null,
  val roles: ServerRolesOverride = ServerRolesOverride(),
  val operator: OperatorTag? = null,
  val group: String? = null,
  val note: String? = null,
)

private val serversBackupJson = Json {
  prettyPrint = true
  encodeDefaults = true
  explicitNulls = false
  ignoreUnknownKeys = true
}

/**
 * The custom servers of the current profile, in the order they appear in [userServers].
 *
 * Preset servers are skipped: they come from the client and operator configuration and would only
 * duplicate the ones already present after an import. Deleted servers are skipped as well, and the
 * client-side group/note of every server is copied from [metadata] when it is not blank.
 */
fun serversBackupOf(
  userServers: List<UserOperatorServers>,
  metadata: Map<String, ServerMeta> = emptyMap()
): ServersBackup {
  fun items(servers: List<UserServer>, operator: OperatorTag?): List<ServersBackupItem> =
    servers.filter { !it.preset && !it.deleted && it.server.isNotBlank() }
      .map { s ->
        ServersBackupItem(
          server = s.server,
          enabled = s.enabled,
          tested = s.tested,
          roles = s.roles,
          operator = operator,
          group = metadata[s.server]?.group?.takeIf { it.isNotBlank() },
          note = metadata[s.server]?.note?.takeIf { it.isNotBlank() },
        )
      }
  return ServersBackup(
    smpServers = userServers.flatMap { items(it.smpServers, it.operator?.operatorTag) },
    xftpServers = userServers.flatMap { items(it.xftpServers, it.operator?.operatorTag) },
  )
}

fun exportServersBackup(backup: ServersBackup): String =
  serversBackupJson.encodeToString(ServersBackup.serializer(), backup)

fun parseServersBackup(json: String): ServersBackup? =
  runCatching { serversBackupJson.decodeFromString(ServersBackup.serializer(), json) }.getOrNull()

/** A server that an import added to the list, together with the protocol it was added under. */
data class ImportedServer(val protocol: ServerProtocol, val item: ServersBackupItem)

data class ServersImportResult(
  val servers: List<UserOperatorServers>,
  val added: List<ImportedServer> = emptyList(),
  val duplicates: List<String> = emptyList(),
  val invalid: List<String> = emptyList(),
)

/**
 * Merges [backup] into [current] without touching the servers that are already configured.
 *
 * Every address is validated with [parseAddress] (the core's parser in the app, a stub in tests)
 * and must match the protocol of the list it was exported from. Addresses that are already present
 * in the same protocol are reported as duplicates instead of being added again, and the ones that
 * cannot be parsed or are invalid are reported in [ServersImportResult.invalid].
 *
 * An imported server is added to the operator group with the same [OperatorTag]. If that group does
 * not exist (or the file has no operator), it is added to the group without an operator, creating
 * it when the profile has none.
 */
fun mergeServersBackup(
  current: List<UserOperatorServers>,
  backup: ServersBackup,
  parseAddress: (String) -> ServerAddress? = { parseServerAddress(it) },
): ServersImportResult {
  val servers = current.toMutableList()
  val added = mutableListOf<ImportedServer>()
  val duplicates = mutableListOf<String>()
  val invalid = mutableListOf<String>()

  fun add(protocol: ServerProtocol, item: ServersBackupItem) {
    val address = item.server.trim()
    val parsed = parseAddress(address)
    if (address.isEmpty() || parsed == null || parsed.serverProtocol != protocol || !parsed.valid) {
      invalid.add(address)
      return
    }
    if (servers.any { group -> group.serversOf(protocol).any { !it.deleted && it.server == address } }) {
      duplicates.add(address)
      return
    }
    val operatorIndex = if (item.operator != null) {
      servers.indexOfFirst { it.operator?.operatorTag == item.operator }
    } else {
      -1
    }
    val existingIndex = if (operatorIndex != -1) operatorIndex else servers.indexOfFirst { it.operator == null }
    if (existingIndex == -1) {
      servers.add(UserOperatorServers(operator = null, smpServers = emptyList(), xftpServers = emptyList()))
    }
    val index = if (existingIndex != -1) existingIndex else servers.lastIndex
    val group = servers[index]
    val server = UserServer(
      remoteHostId = null,
      serverId = null,
      server = address,
      preset = false,
      tested = item.tested,
      enabled = item.enabled,
      deleted = false,
      roles = item.roles,
    )
    servers[index] = group.copy(
      smpServers = if (protocol == ServerProtocol.SMP) group.smpServers + server else group.smpServers,
      xftpServers = if (protocol == ServerProtocol.XFTP) group.xftpServers + server else group.xftpServers,
    )
    added.add(ImportedServer(protocol, item.copy(server = address)))
  }

  backup.smpServers.forEach { add(ServerProtocol.SMP, it) }
  backup.xftpServers.forEach { add(ServerProtocol.XFTP, it) }
  return ServersImportResult(servers = servers, added = added, duplicates = duplicates, invalid = invalid)
}

private fun UserOperatorServers.serversOf(protocol: ServerProtocol): List<UserServer> =
  if (protocol == ServerProtocol.SMP) smpServers else xftpServers

/**
 * The new client-side metadata map after importing [added]: servers with a group or a note get the
 * imported one, and a server imported without metadata drops any stale entry for its address, so
 * that the file is the only source of the group/note of the servers it adds.
 */
fun importedServerMetadata(
  current: Map<String, ServerMeta>,
  added: List<ImportedServer>
): Map<String, ServerMeta> {
  val metadata = current.toMutableMap()
  for (a in added) {
    val meta = ServerMeta(group = a.item.group, note = a.item.note)
    if (meta.isBlank) metadata.remove(a.item.server) else metadata[a.item.server] = meta
  }
  return metadata
}
