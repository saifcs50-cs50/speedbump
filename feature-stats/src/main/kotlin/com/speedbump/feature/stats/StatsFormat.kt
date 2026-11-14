package com.speedbump.feature.stats

import java.util.Locale

/**
 * Durations are rounded to the unit the user can actually feel. Showing
 * "1h 12m 38s" of screen time implies a precision the underlying rollup does
 * not have, and invites someone to argue with the seconds instead of reading
 * the hours.
 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    if (totalMinutes < 1) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

/** Compact form for chart labels, where "1h 12m" is too wide to fit. */
internal fun formatDurationShort(ms: Long): String {
    if (ms <= 0) return "0"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60.0
    return if (totalMinutes < 60) "${totalMinutes}m" else String.format(Locale.US, "%.1fh", hours)
}

/** Minutes past local midnight to a wall-clock time. */
internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour24 = (minuteOfDay / 60).coerceIn(0, 23)
    val minute = (minuteOfDay % 60).coerceIn(0, 59)
    val hour12 = when (hour24 % 12) {
        0 -> 12
        else -> hour24 % 12
    }
    val suffix = if (hour24 < 12) "am" else "pm"
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
}

/** Single-letter weekday for the 7-day chart, so seven fit without wrapping. */
internal fun weekdayInitial(epochDay: Long): String =
    when (java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek.value) {
        1 -> "M"
        2 -> "T"
        3 -> "W"
        4 -> "T"
        5 -> "F"
        6 -> "S"
        else -> "S"
    }

internal fun plural(count: Int, singular: String, pluralForm: String = singular + "s"): String =
    if (count == 1) singular else pluralForm
