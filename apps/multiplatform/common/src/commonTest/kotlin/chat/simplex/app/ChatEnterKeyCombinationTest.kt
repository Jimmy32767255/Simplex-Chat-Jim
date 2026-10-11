package chat.simplex.app

import chat.simplex.common.model.ChatEnterKeyCombination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class ChatEnterKeyCombinationTest {
  @Test
  fun testMatches() {
    assertTrue(ChatEnterKeyCombination.ENTER.matches(shift = false, ctrl = false, alt = false, meta = false))
    assertFalse(ChatEnterKeyCombination.ENTER.matches(shift = true, ctrl = false, alt = false, meta = false))
    assertFalse(ChatEnterKeyCombination.ENTER.matches(shift = false, ctrl = false, alt = false, meta = true))
    assertTrue(ChatEnterKeyCombination.SHIFT_ENTER.matches(shift = true, ctrl = false, alt = false, meta = false))
    assertFalse(ChatEnterKeyCombination.SHIFT_ENTER.matches(shift = true, ctrl = true, alt = false, meta = false))
    assertTrue(ChatEnterKeyCombination.CTRL_ENTER.matches(shift = false, ctrl = true, alt = false, meta = false))
    assertTrue(ChatEnterKeyCombination.ALT_ENTER.matches(shift = false, ctrl = false, alt = true, meta = false))
    assertFalse(ChatEnterKeyCombination.ALT_ENTER.matches(shift = false, ctrl = false, alt = true, meta = true))
  }

  @Test
  fun testDefaultsAreDistinct() {
    assertTrue(ChatEnterKeyCombination.defaultSend.matches(shift = false, ctrl = false, alt = false, meta = false))
    assertTrue(ChatEnterKeyCombination.defaultNewline.matches(shift = true, ctrl = false, alt = false, meta = false))
    assertFalse(ChatEnterKeyCombination.defaultSend == ChatEnterKeyCombination.defaultNewline)
  }
}
