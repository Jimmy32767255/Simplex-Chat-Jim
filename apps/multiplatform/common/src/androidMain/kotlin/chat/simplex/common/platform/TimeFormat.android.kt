package chat.simplex.common.platform

import android.text.format.DateFormat

// The Android setting is a user preference of its own, it does not always follow the locale.
actual fun systemUses24HourFormat(): Boolean =
  DateFormat.is24HourFormat(androidAppContext)
