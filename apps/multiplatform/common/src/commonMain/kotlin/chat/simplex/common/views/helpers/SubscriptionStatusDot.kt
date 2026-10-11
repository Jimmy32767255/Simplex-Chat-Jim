package chat.simplex.common.views.helpers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.SubscriptionStatus
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

/**
 * The coloured network status dot of a connection, as shown on the "Network status" row of the
 * contact and group member info pages. It is reused next to the names in the chat list, in the
 * direct chat title and in the group member list.
 *
 * Pass `null` when the status of the connection isn't known - nothing is rendered in that case.
 * Use [chat.simplex.common.model.connectionSubStatus] to resolve the status of a connection.
 */
@Composable
fun SubscriptionStatusDot(subStatus: SubscriptionStatus?, size: Dp = 18.dp) {
  if (subStatus == null) return
  Box(Modifier.size(size)) {
    when (subStatus) {
      is SubscriptionStatus.Active ->
        Icon(painterResource(MR.images.ic_circle_filled), stringResource(MR.strings.icon_descr_server_status_connected), tint = Color.Green)
      is SubscriptionStatus.Pending ->
        Icon(painterResource(MR.images.ic_pending_filled), stringResource(MR.strings.icon_descr_server_status_disconnected), tint = MaterialTheme.colors.secondary)
      is SubscriptionStatus.Removed, SubscriptionStatus.NoSub ->
        Icon(painterResource(MR.images.ic_error_filled), stringResource(MR.strings.icon_descr_server_status_error), tint = MaterialTheme.colors.secondary)
    }
  }
}
