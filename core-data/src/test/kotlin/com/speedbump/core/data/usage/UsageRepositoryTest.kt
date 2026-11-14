package com.speedbump.core.data.usage

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.speedbump.core.data.db.entity.DailyAppUsageEntity
import com.speedbump.core.data.db.entity.DailyDeviceUsageEntity
import com.speedbump.core.data.db.entity.HourlyUsageEntity
import com.speedbump.core.data.time.DayBoundaries
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The aggregator gets the arithmetic right; this is about the shape of what
 * reaches the screen. Zero-filled days, per-app day alignment and the
 * reclaimed-time estimate are where an off-by-one produces a dashboard that is
 * confidently wrong rather than obviously broken.
 *
 * Times are built from [ZoneId.systemDefault] rather than a fixed zone, because
 * that is the zone the repository itself uses - pinning one here would make the
 * test pass or fail depending on the machine running it.
 */
class UsageRepositoryTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 3, 14)
    private val todayEpochDay = today.toEpochDay()
    private val nowMs = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private val usageDao = FakeUsageDao()
    private val source = FakeUsageStatsSource()

    private fun repository(
        abandonedIn: (Long, Long) -> Int = { _, _ -> 0 },
    ) = UsageRepository(
        usageDao = usageDao,
        interceptionDao = FakeInterceptionEventDao(abandonedIn),
        source = source,
        aggregator = UsageAggregator(),
        clock = FixedClock(nowMs),
    )

    // --- stats assembly ----------------------------------------------------

    @Test
    fun `the trend always has one entry per day in the range, oldest first`() = runTest {
        usageDao.seedDeviceUsage(deviceDay(todayEpochDay, pickups = 40, ms = 3.hours))

        repository().observeStats(days = 7).test {
            val trend = awaitItem().dailyTrend

            assertThat(trend).hasSize(7)
            assertThat(trend.map { it.epochDay })
                .isEqualTo((todayEpochDay - 6..todayEpochDay).toList())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day with no usage is zero-filled rather than missing from the trend`() = runTest {
        usageDao.seedDeviceUsage(deviceDay(todayEpochDay, pickups = 40, ms = 3.hours))

        repository().observeStats(days = 7).test {
            val trend = awaitItem().dailyTrend

            assertThat(trend.dropLast(1).map { it.foregroundMs }).containsExactlyElementsIn(
                List(6) { 0L }
            )
            assertThat(trend.last().foregroundMs).isEqualTo(3.hours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `per-app daily buckets line up with the trend's days`() = runTest {
        // Usage on exactly two days: five days ago, and today.
        usageDao.seedAppUsage(
            appDay(todayEpochDay - 5, INSTAGRAM, ms = 20.minutes, opens = 4),
            appDay(todayEpochDay, INSTAGRAM, ms = 50.minutes, opens = 6),
        )

        repository().observeStats(days = 7).test {
            val app = awaitItem().topApps.single()

            assertThat(app.perDayMs).hasSize(7)
            // Index 0 is six days ago, so five days ago is index 1 and today is 6.
            assertThat(app.perDayMs[1]).isEqualTo(20.minutes)
            assertThat(app.perDayMs[6]).isEqualTo(50.minutes)
            assertThat(app.perDayMs.filterIndexed { i, _ -> i !in setOf(1, 6) })
                .containsExactlyElementsIn(List(5) { 0L })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `apps are ranked by total time across the whole range, not by today`() = runTest {
        usageDao.seedAppUsage(
            appDay(todayEpochDay, INSTAGRAM, ms = 10.minutes, opens = 1),
            appDay(todayEpochDay - 3, YOUTUBE, ms = 90.minutes, opens = 1),
            appDay(todayEpochDay, YOUTUBE, ms = 5.minutes, opens = 1),
        )

        repository().observeStats(days = 7).test {
            val apps = awaitItem().topApps

            assertThat(apps.map { it.packageName }).containsExactly(YOUTUBE, INSTAGRAM).inOrder()
            assertThat(apps.first().foregroundMs).isEqualTo(95.minutes)
            assertThat(apps.first().opens).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today's tiles come from today's row, not from the range`() = runTest {
        usageDao.seedDeviceUsage(
            deviceDay(todayEpochDay - 1, pickups = 90, ms = 5.hours, firstPickup = 5 * 60),
            deviceDay(todayEpochDay, pickups = 41, ms = 2.hours, firstPickup = 7 * 60 + 12),
        )

        repository().observeStats(days = 7).test {
            val state = awaitItem()

            assertThat(state.pickupsToday).isEqualTo(41)
            assertThat(state.screenTimeTodayMs).isEqualTo(2.hours)
            assertThat(state.firstPickupMinute).isEqualTo(7 * 60 + 12)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the hourly chart is always 24 buckets indexed by hour`() = runTest {
        usageDao.seedHourly(
            HourlyUsageEntity(todayEpochDay, hour = 23, foregroundMs = 40.minutes),
            HourlyUsageEntity(todayEpochDay, hour = 0, foregroundMs = 5.minutes),
        )

        repository().observeStats(days = 7).test {
            val hourly = awaitItem().hourlyToday

            assertThat(hourly).hasSize(24)
            assertThat(hourly[23]).isEqualTo(40.minutes)
            assertThat(hourly[0]).isEqualTo(5.minutes)
            assertThat(hourly[12]).isEqualTo(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `time reclaimed credits each abandoned launch with the average session`() = runTest {
        // 100 minutes over 10 opens = a 10 minute average session.
        usageDao.seedAppUsage(appDay(todayEpochDay, INSTAGRAM, ms = 100.minutes, opens = 10))

        repository(abandonedIn = { from, _ ->
            if (from == DayBoundaries.startOfToday(nowMs)) 3 else 5
        }).observeStats(days = 7).test {
            val state = awaitItem()

            assertThat(state.abandonedToday).isEqualTo(3)
            assertThat(state.abandonedInRange).isEqualTo(5)
            assertThat(state.timeReclaimedTodayMs).isEqualTo(30.minutes)
            assertThat(state.timeReclaimedRangeMs).isEqualTo(50.minutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reclaimed time is zero rather than a crash when nothing has been opened`() = runTest {
        repository(abandonedIn = { _, _ -> 4 }).observeStats(days = 7).test {
            val state = awaitItem()

            assertThat(state.abandonedInRange).isEqualTo(4)
            assertThat(state.timeReclaimedRangeMs).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty database reports no usage so the screen can say so`() = runTest {
        repository().observeStats(days = 7).test {
            assertThat(awaitItem().hasAnyUsage).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `any usage at all flips the empty state`() = runTest {
        usageDao.seedAppUsage(appDay(todayEpochDay, INSTAGRAM, ms = 1.minutes, opens = 1))

        repository().observeStats(days = 7).test {
            assertThat(awaitItem().hasAnyUsage).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `revoked usage access is reported so the screen can offer to fix it`() = runTest {
        source.access = false

        repository().observeStats(days = 7).test {
            assertThat(awaitItem().hasUsageAccess).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- ingest ------------------------------------------------------------

    @Test
    fun `ingest without usage access writes nothing and says why`() = runTest {
        source.access = false
        source.events = listOf(RawUsageEvent(INSTAGRAM, nowMs - 60_000, UsageEventType.RESUMED))

        val result = repository().ingest()

        assertThat(result).isEqualTo(IngestResult.NoAccess)
        assertThat(usageDao.appUsageRows).isEmpty()
        assertThat(source.lastRequestedRange).isNull()
    }

    @Test
    fun `the first ingest backfills a week`() = runTest {
        repository().ingest()

        val (from, to) = source.lastRequestedRange!!
        assertThat(from).isEqualTo(DayBoundaries.startOfDaysAgo(nowMs, 7))
        assertThat(to).isEqualTo(nowMs)
    }

    // Re-reading the previous run's last day is the point: that run almost
    // certainly cut a session in half, and upserting the whole day repairs it
    // rather than leaving a permanent dent in the history.
    @Test
    fun `a later ingest re-reads the last ingested day to repair the cut session`() = runTest {
        usageDao.seedDeviceUsage(deviceDay(todayEpochDay, pickups = 1, ms = 1.minutes))

        repository().ingest()

        val expectedFrom = LocalDate.ofEpochDay(todayEpochDay - 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        assertThat(source.lastRequestedRange!!.first).isEqualTo(expectedFrom)
    }

    @Test
    fun `ingest rolls events into all three tables`() = runTest {
        val start = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        source.events = listOf(
            RawUsageEvent(null, start - 60_000, UsageEventType.UNLOCKED),
            RawUsageEvent(INSTAGRAM, start, UsageEventType.RESUMED),
            RawUsageEvent(INSTAGRAM, start + 30.minutes, UsageEventType.PAUSED),
        )

        val result = repository().ingest()

        assertThat(result).isInstanceOf(IngestResult.Ingested::class.java)
        assertThat(usageDao.appUsageRows.single().foregroundMs).isEqualTo(30.minutes)
        assertThat(usageDao.appUsageRows.single().opens).isEqualTo(1)
        assertThat(usageDao.deviceUsageRows.single().pickups).isEqualTo(1)
        assertThat(usageDao.hourlyRows.map { it.hour }).containsExactly(10)
    }

    @Test
    fun `re-ingesting a day replaces it instead of doubling it`() = runTest {
        val start = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        source.events = listOf(
            RawUsageEvent(INSTAGRAM, start, UsageEventType.RESUMED),
            RawUsageEvent(INSTAGRAM, start + 30.minutes, UsageEventType.PAUSED),
        )

        val repository = repository()
        repository.ingest()
        repository.ingest()

        assertThat(usageDao.appUsageRows).hasSize(1)
        assertThat(usageDao.appUsageRows.single().foregroundMs).isEqualTo(30.minutes)
    }

    // --- helpers -----------------------------------------------------------

    private fun appDay(day: Long, pkg: String, ms: Long, opens: Int) =
        DailyAppUsageEntity(dayEpochDay = day, packageName = pkg, foregroundMs = ms, opens = opens)

    private fun deviceDay(day: Long, pickups: Int, ms: Long, firstPickup: Int? = null) =
        DailyDeviceUsageEntity(
            dayEpochDay = day,
            pickups = pickups,
            firstPickupMinute = firstPickup,
            totalForegroundMs = ms,
        )

    private val Int.minutes: Long get() = this * 60_000L
    private val Int.hours: Long get() = this * 60 * 60_000L

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
    }
}
