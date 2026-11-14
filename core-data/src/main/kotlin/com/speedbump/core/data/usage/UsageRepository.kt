package com.speedbump.core.data.usage

import com.speedbump.core.data.db.dao.InterceptionEventDao
import com.speedbump.core.data.db.dao.UsageDao
import com.speedbump.core.data.db.entity.DailyAppUsageEntity
import com.speedbump.core.data.db.entity.DailyDeviceUsageEntity
import com.speedbump.core.data.db.entity.HourlyUsageEntity
import com.speedbump.core.data.time.DayBoundaries
import com.speedbump.core.data.time.SpeedbumpClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class AppStat(
    val packageName: String,
    val foregroundMs: Long,
    val opens: Int,
    /** Foreground millis for each day of the range, oldest first. */
    val perDayMs: List<Long>,
)

data class DayStat(
    val epochDay: Long,
    val foregroundMs: Long,
    val pickups: Int,
)

data class StatsSnapshot(
    val rangeDays: Int = UsageRepository.DEFAULT_RANGE_DAYS,
    val todayEpochDay: Long = 0L,
    /** The hero metric. */
    val abandonedToday: Int = 0,
    val abandonedInRange: Int = 0,
    val timeReclaimedTodayMs: Long = 0,
    val timeReclaimedRangeMs: Long = 0,
    val pickupsToday: Int = 0,
    val firstPickupMinute: Int? = null,
    val screenTimeTodayMs: Long = 0,
    /** 24 buckets of foreground millis for today, index = local hour. */
    val hourlyToday: List<Long> = List(24) { 0L },
    val topApps: List<AppStat> = emptyList(),
    /** Exactly [rangeDays] entries, oldest first, zero-filled for quiet days. */
    val dailyTrend: List<DayStat> = emptyList(),
    val hasUsageAccess: Boolean = false,
) {
    val hasAnyUsage: Boolean
        get() = topApps.isNotEmpty() || dailyTrend.any { it.foregroundMs > 0 || it.pickups > 0 }
}

@Singleton
class UsageRepository @Inject constructor(
    private val usageDao: UsageDao,
    private val interceptionDao: InterceptionEventDao,
    private val source: UsageStatsSource,
    private val aggregator: UsageAggregator,
    private val clock: SpeedbumpClock,
) {

    fun hasUsageAccess(): Boolean = source.hasAccess()

    /**
     * Pulls everything since the last ingest and rolls it up.
     *
     * Always re-ingests the last [OVERLAP_DAYS] days rather than starting
     * exactly where it stopped: the previous run almost certainly cut a session
     * in half, and re-reading the overlap and upserting fixes the truncated day
     * instead of leaving a permanent dent in the history.
     */
    suspend fun ingest(): IngestResult {
        if (!source.hasAccess()) return IngestResult.NoAccess

        val now = clock.nowMs()
        val zone = ZoneId.systemDefault()
        val lastDay = usageDao.lastIngestedDay()
        val fromMs = if (lastDay == null) {
            DayBoundaries.startOfDaysAgo(now, INITIAL_BACKFILL_DAYS)
        } else {
            LocalDate.ofEpochDay(lastDay - OVERLAP_DAYS)
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }

        val events = source.readEvents(fromMs, now)
        if (events.isEmpty()) return IngestResult.Ingested(appDays = 0, events = 0)

        val aggregate = aggregator.aggregate(events, rangeEndMs = now, zone = zone)

        usageDao.upsertAppUsage(
            aggregate.appDays.map { (key, totals) ->
                DailyAppUsageEntity(
                    dayEpochDay = key.epochDay,
                    packageName = key.packageName,
                    foregroundMs = totals.foregroundMs,
                    opens = totals.opens,
                )
            }
        )
        aggregate.deviceDays.forEach { (day, totals) ->
            usageDao.upsertDeviceUsage(
                DailyDeviceUsageEntity(
                    dayEpochDay = day,
                    pickups = totals.pickups,
                    firstPickupMinute = totals.firstPickupMinute,
                    totalForegroundMs = totals.totalForegroundMs,
                )
            )
        }
        usageDao.upsertHourly(
            aggregate.hourly.map { (key, ms) ->
                HourlyUsageEntity(dayEpochDay = key.first, hour = key.second, foregroundMs = ms)
            }
        )
        return IngestResult.Ingested(appDays = aggregate.appDays.size, events = events.size)
    }

    /**
     * Wrapped in [flow] so the clock is read when someone actually collects,
     * not when the flow is built. A stats screen left open overnight would
     * otherwise keep reporting yesterday as "today".
     */
    fun observeStats(days: Int = DEFAULT_RANGE_DAYS): Flow<StatsSnapshot> = flow {
        emitAll(statsAsOf(days, clock.nowMs()))
    }

    private fun statsAsOf(days: Int, nowMs: Long): Flow<StatsSnapshot> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).toEpochDay()
        val fromDay = today - (days - 1)
        val rangeDays = (fromDay..today).toList()

        return combine(
            usageDao.observeAppUsage(fromDay, today),
            usageDao.observeDeviceUsage(fromDay, today),
            usageDao.observeHourly(today, today),
            interceptionDao.observeAbandonedCount(
                fromMs = DayBoundaries.startOfToday(nowMs),
                toMs = DayBoundaries.endOfToday(nowMs),
            ),
            interceptionDao.observeAbandonedCount(
                fromMs = DayBoundaries.startOfDaysAgo(nowMs, (days - 1).toLong()),
                toMs = DayBoundaries.endOfToday(nowMs),
            ),
        ) { appUsage, deviceUsage, hourly, abandonedToday, abandonedInRange ->

            val todayDevice = deviceUsage.firstOrNull { it.dayEpochDay == today }

            val hourlyBuckets = MutableList(24) { 0L }
            hourly.forEach { row ->
                if (row.hour in 0..23) hourlyBuckets[row.hour] = row.foregroundMs
            }

            val topApps = appUsage
                .groupBy { it.packageName }
                .map { (pkg, rows) ->
                    val byDay = rows.associate { it.dayEpochDay to it.foregroundMs }
                    AppStat(
                        packageName = pkg,
                        foregroundMs = rows.sumOf { it.foregroundMs },
                        opens = rows.sumOf { it.opens },
                        perDayMs = rangeDays.map { byDay[it] ?: 0L },
                    )
                }
                .sortedByDescending { it.foregroundMs }

            // Zero-filled so the chart keeps a stable number of columns and a
            // quiet Sunday reads as a short bar rather than vanishing.
            val byDay = deviceUsage.associateBy { it.dayEpochDay }
            val trend = rangeDays.map { day ->
                val row = byDay[day]
                DayStat(
                    epochDay = day,
                    foregroundMs = row?.totalForegroundMs ?: 0L,
                    pickups = row?.pickups ?: 0,
                )
            }

            val averageSessionMs = averageSessionMs(topApps)

            StatsSnapshot(
                rangeDays = days,
                todayEpochDay = today,
                abandonedToday = abandonedToday,
                abandonedInRange = abandonedInRange,
                timeReclaimedTodayMs = abandonedToday * averageSessionMs,
                timeReclaimedRangeMs = abandonedInRange * averageSessionMs,
                pickupsToday = todayDevice?.pickups ?: 0,
                firstPickupMinute = todayDevice?.firstPickupMinute,
                screenTimeTodayMs = todayDevice?.totalForegroundMs ?: 0,
                hourlyToday = hourlyBuckets,
                topApps = topApps,
                dailyTrend = trend,
                hasUsageAccess = source.hasAccess(),
            )
        }
    }

    /**
     * Time reclaimed is an estimate and is presented as one.
     *
     * Each abandoned launch is credited with the average session the user
     * actually has, because that is the session they would most likely have
     * had. Using a fixed number instead would be a prettier lie, and this
     * metric only works if the user believes it.
     */
    private fun averageSessionMs(apps: List<AppStat>): Long {
        val totalOpens = apps.sumOf { it.opens }
        if (totalOpens == 0) return 0
        return apps.sumOf { it.foregroundMs } / totalOpens
    }

    companion object {
        const val DEFAULT_RANGE_DAYS = 7
        private const val INITIAL_BACKFILL_DAYS = 7L
        private const val OVERLAP_DAYS = 1L
    }
}

sealed interface IngestResult {
    /** Usage access was revoked, or never granted. Nothing was written. */
    data object NoAccess : IngestResult

    data class Ingested(val appDays: Int, val events: Int) : IngestResult
}
