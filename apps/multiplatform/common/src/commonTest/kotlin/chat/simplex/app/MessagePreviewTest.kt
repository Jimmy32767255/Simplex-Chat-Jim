package chat.simplex.app

import chat.simplex.common.views.chat.messagePreviewAvailable
import kotlin.test.*

class MessagePreviewTest {
  @Test
  fun testAvailableForRealText() {
    assertTrue(messagePreviewAvailable("hello", inProgress = false))
  }

  @Test
  fun testNotAvailableForBlankText() {
    assertFalse(messagePreviewAvailable("", inProgress = false))
    assertFalse(messagePreviewAvailable("   \n\t ", inProgress = false))
  }

  @Test
  fun testNotAvailableWhileComposing() {
    // In-progress compositions (attachments being prepared, voice) have nothing to preview yet
    assertFalse(messagePreviewAvailable("hello", inProgress = true))
  }

  @Test
  fun testMarkdownIsPreviewable() {
    assertTrue(messagePreviewAvailable("**bold** and `code`", inProgress = false))
  }
}
