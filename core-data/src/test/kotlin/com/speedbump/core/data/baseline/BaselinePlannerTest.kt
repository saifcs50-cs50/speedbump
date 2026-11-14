package com.speedbump.core.data.baseline

import com.google.common.truth.Truth.assertThat
import com.speedbump.core.data.usage.AppStat
import org.junit.Test

/**
 * These thresholds decide what the app proposes to block on the one screen
 * where the user is deciding whether to trust it, so they are worth arguing
 * with here rather than discovering on a device.
 */
class BaselinePlannerTest {

    private val planner = BaselinePlanner()

    @Test
    fun `averages are per day, not totals over the baseline`() {
        val proposals = planner.proposals(
            apps = listOf(app(INSTAGRAM, totalMs = 3.hours, totalOpens = 30)),
            daysMeasured = 3,
        )

        val proposal = proposals.single()
        assertThat(proposal.averageDailyMs).isEqualTo(1.hours)
        assertThat(proposal.averageDailyOpens).isEqualTo(10)
    }

    @Test
    fun `an app below both thresholds is not worth a speedbump`() {
        val proposals = planner.proposals(
            apps = listOf(app(CALCULATOR, totalMs = 9.minutes, totalOpens = 3)),
            daysMeasured = 1,
        )

        assertThat(proposals).isEmpty()
    }

    // The check-loop is the pattern the product exists for: thirty seconds a
    // time, forty times a day. Ranking on time alone would never surface it.
    @Test
    fun `a briefly-but-constantly opened app qualifies on frequency alone`() {
        val proposals = planner.proposals(
            apps = listOf(app(TWITTER, totalMs = 8.minutes, totalOpens = 40)),
            daysMeasured = 1,
        )

        val proposal = proposals.single()
        assertThat(proposal.reason).isEqualTo(ProposalReason.FREQUENCY)
        assertThat(proposal.suggestedAskIntention).isTrue()
    }

    @Test
    fun `an app qualifying on time is labelled as such`() {
        val proposals = planner.proposals(
            apps = listOf(app(INSTAGRAM, totalMs = 90.minutes, totalOpens = 5)),
            daysMeasured = 1,
        )

        assertThat(proposals.single().reason).isEqualTo(ProposalReason.TIME)
    }

    @Test
    fun `protected apps are never proposed however heavily they are used`() {
        val proposals = planner.proposals(
            apps = listOf(
                app(DIALER, totalMs = 5.hours, totalOpens = 60),
                app(INSTAGRAM, totalMs = 1.hours, totalOpens = 10),
            ),
            daysMeasured = 1,
            protectedPackages = setOf(DIALER),
        )

        assertThat(proposals.map { it.packageName }).containsExactly(INSTAGRAM)
    }

    @Test
    fun `proposals are ranked by daily time`() {
        val proposals = planner.proposals(
            apps = listOf(
                app(TWITTER, totalMs = 20.minutes, totalOpens = 40),
                app(INSTAGRAM, totalMs = 2.hours, totalOpens = 12),
                app(YOUTUBE, totalMs = 45.minutes, totalOpens = 6),
            ),
            daysMeasured = 1,
        )

        assertThat(proposals.map { it.packageName })
            .containsExactly(INSTAGRAM, YOUTUBE, TWITTER).inOrder()
    }

    @Test
    fun `the list is capped so the screen stays a decision rather than a survey`() {
        val proposals = planner.proposals(
            apps = (1..12).map { app("com.app$it", totalMs = it.hours, totalOpens = 10) },
            daysMeasured = 1,
        )

        assertThat(proposals).hasSize(BaselinePlanner.MAX_PROPOSALS)
    }

    @Test
    fun `heavier use earns a longer pause, within a range the user would accept`() {
        fun delayFor(minutes: Int) = planner.proposals(
            apps = listOf(app(INSTAGRAM, totalMs = minutes.minutes, totalOpens = 5)),
            daysMeasured = 1,
        ).single().suggestedDelaySeconds

        assertThat(delayFor(15)).isEqualTo(10)
        assertThat(delayFor(45)).isEqualTo(15)
        assertThat(delayFor(90)).isEqualTo(20)
        assertThat(delayFor(300)).isEqualTo(30)
    }

    @Test
    fun `a zero-day baseline is treated as one day rather than dividing by zero`() {
        val proposals = planner.proposals(
            apps = listOf(app(INSTAGRAM, totalMs = 1.hours, totalOpens = 10)),
            daysMeasured = 0,
        )

        assertThat(proposals.single().averageDailyMs).isEqualTo(1.hours)
    }

    @Test
    fun `a proposal converts to an armed rule carrying its suggestions`() {
        val proposal = planner.proposals(
            apps = listOf(app(INSTAGRAM, totalMs = 3.hours, totalOpens = 20)),
            daysMeasured = 1,
        ).single()

        val rule = proposal.toRule()
        assertThat(rule.packageName).isEqualTo(INSTAGRAM)
        assertThat(rule.enabled).isTrue()
        assertThat(rule.baseDelaySeconds).isEqualTo(proposal.suggestedDelaySeconds)
        assertThat(rule.askIntention).isEqualTo(proposal.suggestedAskIntention)
    }

    @Test
    fun `nothing measured proposes nothing`() {
        assertThat(planner.proposals(apps = emptyList(), daysMeasured = 3)).isEmpty()
    }

    private fun app(pkg: String, totalMs: Long, totalOpens: Int) = AppStat(
        packageName = pkg,
        foregroundMs = totalMs,
        opens = totalOpens,
        perDayMs = emptyList(),
    )

    private val Int.minutes: Long get() = this * 60_000L
    private val Int.hours: Long get() = this * 60 * 60_000L

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val TWITTER = "com.twitter.android"
        const val CALCULATOR = "com.sec.android.app.popupcalculator"
        const val DIALER = "com.samsung.android.dialer"
    }
}
