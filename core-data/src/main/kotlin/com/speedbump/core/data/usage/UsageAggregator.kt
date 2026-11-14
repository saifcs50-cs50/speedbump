package com.speedbump.core.data.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class AppDayKey(val epochDay: Long, val packageName: String)

data class AppDayTotals(val foregroundMs: Long, val opens: Int)

data class DeviceDayTotals(
    val pickups: Int,
    val firstPickupMinute: Int?,
    val totalForegroundMs: Long,
)

data class UsageAggregate(
    val appDays: Map<AppDayKey, AppDayTotals>,
    val deviceDays: Map<Long, DeviceDayTotals>,
    /** (epochDay, hour) -> foreground millis */
    val hourly: Map<Pair<Long, Int>, Long>,
)

/**
 * Turns a stream of raw usage events into the daily and hourly rollups the
 * stats screen reads.
 *
 * Pure and injectable so it can be tested against handcrafted event streams.
 * The fiddly parts are all edge cases that only show up on real devices and
 * would otherwise be found by a user with a wrong number on their dashboard:
 *
 *  - A session open when the range ends is closed at the range end, not
 *    dropped, or every "current" app silently contributes zero.
 *  - A session crossing midnight is split, so a 23:50-00:20 scroll is 10
 *    minutes yesterday and 20 today rather than 30 on whichever day wins.
 *  - The screen going off ends the session. Without this, an app left in the
 *    foreground when the phone is pocketed accrues hours of "usage".
 *  - Moving between Activities inside one app is one open, not several. The
 *    platform emits RESUMED per Activity, so counting them raw inflates opens,
 *    which deflates the average session length, which is the number "time
 *    reclaimed" is derived from.
 */
@Singleton
class UsageAggregator @Inject constructor() {

    fun aggregate(
        events: List<RawUsageEvent>,
        rangeEndMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): UsageAggregate {
        val appTotals = mutableMapOf<AppDayKey, MutableAppTotals>()
        val hourly = mutableMapOf<Pair<Long, Int>, Long>()
        val pickupsByDay = mutableMapOf<Long, MutableList<Long>>()

        var openPackage: String? = null
        var openSince = 0L

        // Survives closeSession, unlike openPackage: A -> B -> A inside one app
        // closes and reopens the session three times but is a single open. Only
        // the screen going off clears it, because coming back to the same app
        // after a lock genuinely is a new visit.
        var lastForegroundPackage: String? = null

        fun closeSession(atMs: Long) {
            val pkg = openPackage ?: return
            if (atMs > openSince) {
                splitByHour(openSince, atMs, zone) { epochDay, hour, ms ->
                    hourly[epochDay to hour] = (hourly[epochDay to hour] ?: 0L) + ms
                    appTotals.getOrPut(AppDayKey(epochDay, pkg)) { MutableAppTotals() }
                        .foregroundMs += ms
                }
            }
            openPackage = null
        }

        for (event in events.sortedBy { it.timestampMs }) {
            when (event.type) {
                UsageEventType.RESUMED -> {
                    val pkg = event.packageName ?: continue
                    val isNewVisit = pkg != lastForegroundPackage
                    closeSession(event.timestampMs)
                    openPackage = pkg
                    openSince = event.timestampMs
                    lastForegroundPackage = pkg
                    if (isNewVisit) {
                        val day = event.timestampMs.toEpochDay(zone)
                        appTotals.getOrPut(AppDayKey(day, pkg)) { MutableAppTotals() }.opens += 1
                    }
                }

                UsageEventType.PAUSED -> {
                    if (openPackage == event.packageName) closeSession(event.timestampMs)
                }

                UsageEventType.SCREEN_OFF -> {
                    closeSession(event.timestampMs)
                    lastForegroundPackage = null
                }

                UsageEventType.UNLOCKED -> {
                    val day = event.timestampMs.toEpochDay(zone)
                    pickupsByDay.getOrPut(day) { mutableListOf() }.add(event.timestampMs)
                }
            }
        }
        closeSession(rangeEndMs)

        val appDays = appTotals.mapValues { (_, totals) ->
            AppDayTotals(foregroundMs = totals.foregroundMs, opens = totals.opens)
        }

        val allDays = appDays.keys.map { it.epochDay }.toMutableSet()
        allDays += pickupsByDay.keys

        val deviceDays = allDays.associateWith { day ->
            val pickups = pickupsByDay[day].orEmpty()
            DeviceDayTotals(
                pickups = pickups.size,
                firstPickupMinute = pickups.minOrNull()?.toMinuteOfDay(zone),
                totalForegroundMs = appDays
                    .filterKeys { it.epochDay == day }
                    .values.sumOf { it.foregroundMs },
            )
        }

        return UsageAggregate(appDays = appDays, deviceDays = deviceDays, hourly = hourly)
    }

    /** Walks [startMs, endMs) emitting the slice that falls in each local hour. */
    private inline fun splitByHour(
        startMs: Long,
        endMs: Long,
        zone: ZoneId,
        emit: (epochDay: Long, hour: Int, ms: Long) -> Unit,
    ) {
        var cursor = startMs
        var guard = 0
        while (cursor < endMs && guard < MAX_HOUR_SLICES) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
            val hourEnd = zoned.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                .toInstant().toEpochMilli()
            val sliceEnd = minOf(hourEnd, endMs)
            emit(zoned.toLocalDate().toEpochDay(), zoned.hour, sliceEnd - cursor)
            cursor = sliceEnd
            guard++
        }
    }

    private class MutableAppTotals {
        var foregroundMs: Long = 0
        var opens: Int = 0
    }

    private companion object {
        /**
         * A session longer than a month is corrupt data, not usage. Bounds the
         * loop so a bad timestamp cannot hang the ingest worker.
         */
        const val MAX_HOUR_SLICES = 24 * 31
    }
}

private fun Long.toEpochDay(zone: ZoneId): Long =
    LocalDate.ofInstant(Instant.ofEpochMilli(this), zone).toEpochDay()

private fun Long.toMinuteOfDay(zone: ZoneId): Int =
    Instant.ofEpochMilli(this).atZone(zone).let { it.hour * 60 + it.minute }
