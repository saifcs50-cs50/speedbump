package com.speedbump.core.data.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Today" is the user's local day, not a UTC day and not a rolling 24 hours.
 * Someone who scrolls until 1am has had a bad night, not a fresh morning -
 * but the day boundary still has to be midnight, because that is what every
 * other screen-time number they have ever seen uses.
 */
object DayBoundaries {

    fun startOfToday(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDay(LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone), zone)

    fun startOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfToday(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfToday(nowMs, zone) + DAY_MS

    fun startOfDaysAgo(nowMs: Long, days: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDay(
            LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).minusDays(days),
            zone,
        )

    const val DAY_MS = 24 * 60 * 60 * 1000L
}
