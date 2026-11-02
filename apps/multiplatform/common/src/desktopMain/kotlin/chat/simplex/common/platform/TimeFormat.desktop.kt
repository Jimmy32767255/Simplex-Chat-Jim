package chat.simplex.common.platform

import java.text.DateFormat
import java.text.SimpleDateFormat

// On desktop the JVM exposes the 12/24-hour convention through the default locale's short time
// pattern: 24-hour patterns use `H` (or `k` for 1-based hours), 12-hour ones use `h`.
actual fun systemUses24HourFormat(): Boolean {
  val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()
  return pattern == null || pattern.contains('H') || pattern.contains('k')
}
