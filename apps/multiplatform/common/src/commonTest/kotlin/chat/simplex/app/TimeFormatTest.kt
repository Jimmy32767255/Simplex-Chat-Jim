package chat.simplex.app

import chat.simplex.common.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// use this command for testing:
// ./gradlew desktopTest
class TimeFormatTest {
  @Test
  fun testSystemFollowsThePlatform() {
    assertTrue(uses24HourFormat(TimeFormat.SYSTEM, systemUses24Hour = true))
    assertFalse(uses24HourFormat(TimeFormat.SYSTEM, systemUses24Hour = false))
  }

  @Test
  fun testExplicitFormatsOverrideThePlatform() {
    assertFalse(uses24HourFormat(TimeFormat.HOUR_12, systemUses24Hour = true))
    assertFalse(uses24HourFormat(TimeFormat.HOUR_12, systemUses24Hour = false))
    assertTrue(uses24HourFormat(TimeFormat.HOUR_24, systemUses24Hour = true))
    assertTrue(uses24HourFormat(TimeFormat.HOUR_24, systemUses24Hour = false))
  }

  @Test
  fun testTimePatterns() {
    assertEquals("HH:mm", timeOfDayPattern(use24Hour = true))
    assertEquals("h:mm a", timeOfDayPattern(use24Hour = false))
    assertEquals("HH:mm:ss", timeOfDayPatternWithSeconds(use24Hour = true))
    assertEquals("h:mm:ss a", timeOfDayPatternWithSeconds(use24Hour = false))
  }

  @Test
  fun testDefaultPreferenceIsSystem() {
    assertEquals(TimeFormat.SYSTEM, TimeFormat.default)
  }
}
