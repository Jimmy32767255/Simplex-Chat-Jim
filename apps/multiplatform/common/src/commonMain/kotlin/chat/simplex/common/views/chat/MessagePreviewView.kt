package chat.simplex.common.views.chat

import SectionBottomSpacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.painterResource
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.chat.item.MarkdownText
import chat.simplex.common.views.helpers.*
import chat.simplex.res.*

// Spec: spec/client/compose-view.md#message-preview

/**
 * Whether the compose view should offer the preview button: there has to be text to render, and the
 * compose state has to be a plain text message (not an in-progress or voice composition).
 */
internal fun messagePreviewAvailable(text: String, inProgress: Boolean): Boolean =
  !inProgress && text.isNotBlank()

/** Renders the message as it will look once sent, using the same markdown renderer as the chat. */
@Composable
fun MessagePreviewView(text: String) {
  ColumnWithScrollBar(horizontalAlignment = Alignment.CenterHorizontally) {
    AppBarTitle(generalGetString(MR.strings.preview_message))
    val bubbleShape = RoundedCornerShape(DEFAULT_PADDING * 1.5f)
    Surface(
      shape = bubbleShape,
      color = MaterialTheme.colors.surface,
      modifier = Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING)
    ) {
      Column(Modifier.padding(DEFAULT_PADDING)) {
        MarkdownText(
          text,
          parseToMarkdown(text),
          toggleSecrets = false,
          linkMode = chatModel.simplexLinkMode.value,
          style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface, lineHeight = 22.sp),
        )
      }
    }
    SectionBottomSpacer()
    Text(
      generalGetString(MR.strings.preview_message_footer),
      Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING),
      color = MaterialTheme.colors.secondary,
      textAlign = TextAlign.Center,
      fontSize = 14.sp
    )
    SectionBottomSpacer()
  }
}

fun showMessagePreview(text: String) {
  if (text.isBlank()) return
  ModalManager.fullscreen.showModal { MessagePreviewView(text) }
}

/** Round icon next to the send button that opens the message preview. */
@Composable
fun MessagePreviewButton(onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier
      .size(36.dp)
      .clip(CircleShape)
      .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      painterResource(MR.images.ic_visibility),
      generalGetString(MR.strings.preview_message),
      tint = MaterialTheme.colors.secondary,
      modifier = Modifier.size(22.dp)
    )
  }
}
