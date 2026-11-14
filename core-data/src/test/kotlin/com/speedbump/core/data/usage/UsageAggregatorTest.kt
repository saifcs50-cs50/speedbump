package com.speedbump.core.data.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every number on the stats screen is produced here, so this is where the
 * stats screen is actually verified. The cases below are the ones that were
 * wrong in an early hand-check against Digital Wellbeing: sessions still open
 * when the range ends, sessions crossing midnight, and the screen going off
 * without a matching PAUSED.
 */
class UsageAggregatorTest {

    private val aggregator = UsageAggregator()
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val today: LocalDate = LocalDate.of(2026, 3, 14)
    private val yesterday: LocalDate = today.minusDays(1)

    @Test
    fun `a resumed then paused session lands on the right app and day`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(INSTAGRAM, today.at(10, 12)),
            ),
            rangeEndMs = today.at(23, 0),
            zone = zone,
        )

        val totals = result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM))
        assertThat(totals.foregroundMs).isEqualTo(12.minutes)
        assertThat(totals.opens).isEqualTo(1)
    }

    @Test
    fun `a session still open at the range end is closed there, not dropped`() {
        val result = aggregator.aggregate(
            events = listOf(resumed(INSTAGRAM, today.at(10, 0))),
            rangeEndMs = today.at(10, 30),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(30.minutes)
    }

    @Test
    fun `a session crossing midnight is split across both days`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, yesterday.at(23, 50)),
                paused(INSTAGRAM, today.at(0, 20)),
            ),
            rangeEndMs = today.at(1, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(yesterday.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(10.minutes)
        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(20.minutes)
    }

    @Test
    fun `the open is credited to the day the app was opened, not the day it ended`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, yesterday.at(23, 50)),
                paused(INSTAGRAM, today.at(0, 20)),
            ),
            rangeEndMs = today.at(1, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(yesterday.toEpochDay(), INSTAGRAM)).opens)
            .isEqualTo(1)
        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).opens)
            .isEqualTo(0)
    }

    @Test
    fun `the screen going off ends the session, so a pocketed phone accrues nothing`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                RawUsageEvent(null, today.at(10, 5), UsageEventType.SCREEN_OFF),
            ),
            rangeEndMs = today.at(18, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(5.minutes)
    }

    @Test
    fun `a stale paused for an app that is no longer in front does not end the session`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(LAUNCHER, today.at(10, 1)),
                paused(INSTAGRAM, today.at(10, 9)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(9.minutes)
    }

    @Test
    fun `resuming a second app closes the first one`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                resumed(YOUTUBE, today.at(10, 4)),
                paused(YOUTUBE, today.at(10, 10)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(4.minutes)
        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), YOUTUBE)).foregroundMs)
            .isEqualTo(6.minutes)
    }

    // Moving between two Activities inside one app emits RESUMED for each of
    // them. Counting those as separate opens would inflate the open count,
    // which deflates the average session length, which is what "time
    // reclaimed" is calculated from - so a navigation-heavy app would quietly
    // shrink the headline number.
    @Test
    fun `moving between activities in one app is a single open`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(INSTAGRAM, today.at(10, 2)),
                resumed(INSTAGRAM, today.at(10, 2)),
                paused(INSTAGRAM, today.at(10, 6)),
                resumed(INSTAGRAM, today.at(10, 6)),
                paused(INSTAGRAM, today.at(10, 9)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        val totals = result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM))
        assertThat(totals.opens).isEqualTo(1)
        assertThat(totals.foregroundMs).isEqualTo(9.minutes)
    }

    @Test
    fun `going out to the launcher and back is a second open`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                resumed(LAUNCHER, today.at(10, 5)),
                resumed(INSTAGRAM, today.at(10, 6)),
                paused(INSTAGRAM, today.at(10, 8)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).opens)
            .isEqualTo(2)
    }

    @Test
    fun `picking the phone back up and returning to the same app is a new open`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                RawUsageEvent(null, today.at(10, 5), UsageEventType.SCREEN_OFF),
                RawUsageEvent(null, today.at(11, 0), UsageEventType.UNLOCKED),
                resumed(INSTAGRAM, today.at(11, 0)),
                paused(INSTAGRAM, today.at(11, 3)),
            ),
            rangeEndMs = today.at(12, 0),
            zone = zone,
        )

        val totals = result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM))
        assertThat(totals.opens).isEqualTo(2)
        assertThat(totals.foregroundMs).isEqualTo(8.minutes)
    }

    @Test
    fun `unlocks are pickups and the earliest one sets the first-pickup minute`() {
        val result = aggregator.aggregate(
            events = listOf(
                RawUsageEvent(null, today.at(9, 30), UsageEventType.UNLOCKED),
                RawUsageEvent(null, today.at(7, 12), UsageEventType.UNLOCKED),
                RawUsageEvent(null, today.at(14, 0), UsageEventType.UNLOCKED),
            ),
            rangeEndMs = today.at(23, 0),
            zone = zone,
        )

        val device = result.deviceDays.getValue(today.toEpochDay())
        assertThat(device.pickups).isEqualTo(3)
        assertThat(device.firstPickupMinute).isEqualTo(7 * 60 + 12)
    }

    @Test
    fun `a day with usage but no unlock reports no first pickup rather than midnight`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(INSTAGRAM, today.at(10, 5)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        val device = result.deviceDays.getValue(today.toEpochDay())
        assertThat(device.pickups).isEqualTo(0)
        assertThat(device.firstPickupMinute).isNull()
    }

    @Test
    fun `device foreground time is the sum of that day's apps`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                resumed(YOUTUBE, today.at(10, 5)),
                paused(YOUTUBE, today.at(10, 20)),
                resumed(INSTAGRAM, yesterday.at(9, 0)),
                paused(INSTAGRAM, yesterday.at(9, 30)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.deviceDays.getValue(today.toEpochDay()).totalForegroundMs)
            .isEqualTo(20.minutes)
        assertThat(result.deviceDays.getValue(yesterday.toEpochDay()).totalForegroundMs)
            .isEqualTo(30.minutes)
    }

    @Test
    fun `hourly buckets split a session across the hours it actually spans`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 45)),
                paused(INSTAGRAM, today.at(12, 15)),
            ),
            rangeEndMs = today.at(13, 0),
            zone = zone,
        )

        val day = today.toEpochDay()
        assertThat(result.hourly[day to 10]).isEqualTo(15.minutes)
        assertThat(result.hourly[day to 11]).isEqualTo(60.minutes)
        assertThat(result.hourly[day to 12]).isEqualTo(15.minutes)
        assertThat(result.hourly.keys.filter { it.first == day }).hasSize(3)
    }

    @Test
    fun `hourly buckets accumulate across separate sessions in the same hour`() {
        val result = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(INSTAGRAM, today.at(10, 10)),
                resumed(YOUTUBE, today.at(10, 30)),
                paused(YOUTUBE, today.at(10, 45)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.hourly[today.toEpochDay() to 10]).isEqualTo(25.minutes)
    }

    @Test
    fun `events arriving out of order are sorted before aggregation`() {
        val ordered = aggregator.aggregate(
            events = listOf(
                resumed(INSTAGRAM, today.at(10, 0)),
                paused(INSTAGRAM, today.at(10, 10)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )
        val shuffled = aggregator.aggregate(
            events = listOf(
                paused(INSTAGRAM, today.at(10, 10)),
                resumed(INSTAGRAM, today.at(10, 0)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(shuffled).isEqualTo(ordered)
    }

    @Test
    fun `a resumed event with no package name is ignored, not crashed on`() {
        val result = aggregator.aggregate(
            events = listOf(
                RawUsageEvent(null, today.at(10, 0), UsageEventType.RESUMED),
                resumed(INSTAGRAM, today.at(10, 1)),
                paused(INSTAGRAM, today.at(10, 6)),
            ),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.appDays.keys.map { it.packageName }).containsExactly(INSTAGRAM)
        assertThat(result.appDays.getValue(AppDayKey(today.toEpochDay(), INSTAGRAM)).foregroundMs)
            .isEqualTo(5.minutes)
    }

    @Test
    fun `no events produces an empty aggregate rather than a day of zeroes`() {
        val result = aggregator.aggregate(
            events = emptyList(),
            rangeEndMs = today.at(11, 0),
            zone = zone,
        )

        assertThat(result.appDays).isEmpty()
        assertThat(result.deviceDays).isEmpty()
        assertThat(result.hourly).isEmpty()
    }

    // A device whose clock jumped, or a UsageStats record the platform never
    // closed, can present a session years long. Slicing that by the hour would
    // hang the ingest worker, so the walk is bounded.
    @Test
    fun `an absurdly long session is bounded instead of hanging the walk`() {
        val result = aggregator.aggregate(
            events = listOf(resumed(INSTAGRAM, today.minusYears(3).at(10, 0))),
            rangeEndMs = today.at(10, 0),
            zone = zone,
        )

        assertThat(result.hourly.size).isAtMost(24 * 31)
    }

    // --- helpers -----------------------------------------------------------

    private fun resumed(pkg: String, atMs: Long) =
        RawUsageEvent(pkg, atMs, UsageEventType.RESUMED)

    private fun paused(pkg: String, atMs: Long) =
        RawUsageEvent(pkg, atMs, UsageEventType.PAUSED)

    private fun LocalDate.at(hour: Int, minute: Int): Long =
        LocalDateTime.of(this, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private val Int.minutes: Long get() = this * 60_000L

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val LAUNCHER = "com.sec.android.app.launcher"
    }
}
