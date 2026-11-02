package chat.simplex.common.platform

/**
 * Whether the operating system is configured to display times of day in the 24-hour format.
 *
 * Used by the `SYSTEM` value of `TimeFormat`; a user preference can override it. Kept as an
 * expect/actual pair because only the platform can read the OS setting (it is independent from the
 * locale on Android and from the locale alone on desktop).
 */
expect fun systemUses24HourFormat(): Boolean
