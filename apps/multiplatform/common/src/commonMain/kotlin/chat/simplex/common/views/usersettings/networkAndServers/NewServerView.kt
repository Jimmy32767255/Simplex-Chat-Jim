package chat.simplex.common.views.usersettings.networkAndServers

import SectionBottomSpacer
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.model.*
import chat.simplex.common.model.ServerAddress.Companion.parseServerAddress
import chat.simplex.common.views.helpers.*
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import kotlinx.coroutines.*

@Composable
fun ModalData.NewServerView(
  userServers: MutableState<List<UserOperatorServers>>,
  serverErrors: MutableState<List<UserServersError>>,
  serverWarnings: MutableState<List<UserServersWarning>>,
  rhId: Long?,
  close: () -> Unit
) {
  val testing = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val newServer = remember { mutableStateOf(UserServer.empty) }
  val newServerMeta = remember { mutableStateOf(ServerMeta.empty) }

  ModalView(close = {
    addServer(
      scope,
      newServer.value,
      userServers,
      serverErrors,
      serverWarnings,
      rhId,
      meta = newServerMeta.value,
      close = close
    )
  }) {
    Box {
      NewServerLayout(
        newServer,
        newServerMeta,
        testing.value,
        testServer = {
          testing.value = true
          withLongRunningApi {
            val res = testServerConnection(newServer.value, chatModel)
            if (isActive) {
              newServer.value = res.first
              testing.value = false
              val failure = res.second
              if (failure != null) {
                AlertManager.shared.showAlertMsg(
                  title = generalGetString(MR.strings.smp_servers_test_failed),
                  text = failure.localizedDescription
                )
              }
            }
          }
        },
      )

      if (testing.value) {
        DefaultProgressView(null)
      }
    }
  }
}

@Composable
private fun NewServerLayout(
  server: MutableState<UserServer>,
  meta: MutableState<ServerMeta>,
  testing: Boolean,
  testServer: () -> Unit,
) {
  ColumnWithScrollBar {
    AppBarTitle(stringResource(MR.strings.smp_servers_new_server))
    CustomServer(server, testing, testServer, onDelete = null, allowMultipleAddresses = true)
    ServerGroupAndNoteSection(meta)
    SectionBottomSpacer()
  }
}

/**
 * Splits the server address text field into the list of addresses to add.
 *
 * A user can add several servers at once by entering one address per line.
 * Blanks and duplicate addresses are ignored so that pasting a list with an
 * accidental empty line (or a repeated address) doesn't create junk entries.
 */
fun serverAddresses(server: String): List<String> =
  server.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun serverProtocolAndOperator(
  server: UserServer,
  userServers: List<UserOperatorServers>
): Pair<ServerProtocol, ServerOperator?>? {
  val serverAddress = parseServerAddress(server.server)
  return if (serverAddress != null) {
    val serverProtocol = serverAddress.serverProtocol
    val hostnames = serverAddress.hostnames
    val matchingOperator = userServers.mapNotNull { it.operator }.firstOrNull { op ->
      op.serverDomains.any { domain ->
        hostnames.any { hostname ->
          hostname.endsWith(domain)
        }
      }
    }
    Pair(serverProtocol, matchingOperator)
  } else {
    null
  }
}

fun addServer(
  scope: CoroutineScope,
  server: UserServer,
  userServers: MutableState<List<UserOperatorServers>>,
  serverErrors: MutableState<List<UserServersError>>,
  serverWarnings: MutableState<List<UserServersWarning>>? = null,
  rhId: Long?,
  meta: ServerMeta = ServerMeta.empty,
  close: () -> Unit
) {
  // The address field may contain several addresses, one per line. Every server
  // added in this batch shares the settings (enabled/preset/roles) of `server`.
  val addresses = serverAddresses(server.server)
  close()
  if (addresses.isEmpty()) return

  val multiple = addresses.size > 1
  val invalidAddresses = mutableListOf<String>()
  val addedAddresses = mutableListOf<String>()
  val addedOperators = mutableListOf<ServerOperator>()
  var operatorNotFound = false
  for (address in addresses) {
    // A single address keeps the tested flag obtained in the form, a batch doesn't
    // claim to have tested every address.
    val one = server.copy(server = address, tested = if (multiple) null else server.tested)
    val result = serverProtocolAndOperator(one, userServers.value)
    if (result == null) {
      invalidAddresses.add(address)
      continue
    }
    val (serverProtocol, matchingOperator) = result
    val operatorIndex = userServers.value.indexOfFirst { it.operator?.operatorId == matchingOperator?.operatorId }
    if (operatorIndex == -1) { // Shouldn't happen
      operatorNotFound = true
      continue
    }
    // Create a mutable copy of the userServers list
    val updatedUserServers = userServers.value.toMutableList()
    val operatorServers = updatedUserServers[operatorIndex]
    // Create a mutable copy of the smpServers or xftpServers and add the server
    when (serverProtocol) {
      ServerProtocol.SMP -> {
        val updatedSMPServers = operatorServers.smpServers.toMutableList()
        updatedSMPServers.add(one)
        updatedUserServers[operatorIndex] = operatorServers.copy(smpServers = updatedSMPServers)
      }

      ServerProtocol.XFTP -> {
        val updatedXFTPServers = operatorServers.xftpServers.toMutableList()
        updatedXFTPServers.add(one)
        updatedUserServers[operatorIndex] = operatorServers.copy(xftpServers = updatedXFTPServers)
      }
    }

    userServers.value = updatedUserServers
    addedAddresses.add(one.server)
    matchingOperator?.let { op -> if (addedOperators.none { it.operatorId == op.operatorId }) addedOperators.add(op) }
  }

  // Group/note is client-side metadata keyed by the server address, so it is not
  // part of the server list that is sent to the core.
  if (addedAddresses.isNotEmpty() && !meta.isBlank) {
    val current = ChatController.appPrefs.serverMetadata.get().toMutableMap()
    for (address in addedAddresses) {
      current[address] = meta
    }
    ChatController.appPrefs.serverMetadata.set(current)
  }

  // Only one alert is shown at a time, so invalid addresses (if any) take precedence
  if (invalidAddresses.isNotEmpty()) {
    AlertManager.shared.showAlertMsg(
      title = generalGetString(MR.strings.smp_servers_invalid_address),
      text = generalGetString(MR.strings.smp_servers_check_address) + "\n" + invalidAddresses.joinToString("\n")
    )
  } else if (addedOperators.isNotEmpty()) {
    AlertManager.shared.showAlertMsg(
      title = generalGetString(MR.strings.operator_server_alert_title),
      text = String.format(
        generalGetString(MR.strings.server_added_to_operator__name),
        addedOperators.joinToString(", ") { it.tradeName }
      )
    )
  } else if (operatorNotFound) { // Shouldn't happen
    AlertManager.shared.showAlertMsg(title = generalGetString(MR.strings.error_adding_server))
  }
}
