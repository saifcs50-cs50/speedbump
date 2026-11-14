package com.speedbump.core.data.baseline

import com.speedbump.core.data.model.AppRule
import com.speedbump.core.data.policy.EscalationPolicy
import com.speedbump.core.data.usage.AppStat
import javax.inject.Inject
import javax.inject.Singleton

/** Why an app was proposed, so the UI can show the user the evidence. */
enum class ProposalReason {
    /** Enough hours a day that the user will recognise the number. */
    TIME,

    /** The compulsive-check pattern: opened constantly, briefly each time. */
    FREQUENCY,
}

data class RuleProposal(
    val packageName: String,
    val averageDailyMs: Long,
    val averageDailyOpens: Int,
    val suggestedDelaySeconds: Int,
    val suggestedAskIntention: Boolean,
    val reason: ProposalReason,
) {
    fun toRule(): AppRule = AppRule(
        packageName = packageName,
        enabled = true,
        baseDelaySeconds = suggestedDelaySeconds,
        askIntention = suggestedAskIntention,
    )
}

/**
 * Turns a measured baseline into a list of apps worth blocking.
 *
 * The point of the baseline week is that the user does not have to guess. Asked
 * cold which apps to block, people name the ones they feel guilty about; the
 * measurement names the ones they actually lose the evening to, and those are
 * frequently not the same app.
 *
 * Pure so the thresholds can be argued with in a test rather than on a device.
 */
@Singleton
class BaselinePlanner @Inject constructor() {

    fun proposals(
        apps: List<AppStat>,
        daysMeasured: Int,
        protectedPackages: Set<String> = emptySet(),
        limit: Int = MAX_PROPOSALS,
    ): List<RuleProposal> {
        // A zero-day baseline would divide by zero; a baseline shorter than a
        // day is still worth something, so treat it as one day rather than
        // refusing to propose anything.
        val days = daysMeasured.coerceAtLeast(1)

        return apps.asSequence()
            .filter { it.packageName !in protectedPackages }
            .map { it.toProposal(days) }
            .filterNotNull()
            // Ranked by time, which is the number the dashboard shows and the
            // one the user can check. Frequency qualifies an app but does not
            // reorder it, because "you spent 2 hours here" is an argument a
            // user accepts and a weighted score is one they have to trust.
            .sortedByDescending { it.averageDailyMs }
            .take(limit)
            .toList()
    }

    private fun AppStat.toProposal(days: Int): RuleProposal? {
        val dailyMs = foregroundMs / days
        val dailyOpens = opens / days

        val reason = when {
            dailyMs >= MIN_DAILY_MS -> ProposalReason.TIME
            dailyOpens >= MIN_DAILY_OPENS -> ProposalReason.FREQUENCY
            else -> return null
        }

        val dailyMinutes = dailyMs / 60_000
        return RuleProposal(
            packageName = packageName,
            averageDailyMs = dailyMs,
            averageDailyOpens = dailyOpens,
            suggestedDelaySeconds = suggestedDelayFor(dailyMinutes),
            // A long delay on an app opened all day is a tax; asking what the
            // user came for is the intervention that fits a check-loop.
            suggestedAskIntention = dailyMinutes >= INTENTION_PROMPT_MINUTES ||
                reason == ProposalReason.FREQUENCY,
            reason = reason,
        )
    }

    /**
     * Heavier use earns a longer pause, but the top of the scale is well below
     * what the user could choose by hand. A proposal the user finds punitive is
     * one they reject along with everything else on the screen.
     */
    private fun suggestedDelayFor(dailyMinutes: Long): Int = when {
        dailyMinutes < 30 -> EscalationPolicy.DEFAULT_BASE_SECONDS
        dailyMinutes < 60 -> 15
        dailyMinutes < 120 -> 20
        else -> 30
    }

    companion object {
        /** Below this, a speedbump costs more attention than the app does. */
        const val MIN_DAILY_MS = 10 * 60_000L

        /** Opening an app this often a day is a habit regardless of the total. */
        const val MIN_DAILY_OPENS = 15

        const val INTENTION_PROMPT_MINUTES = 60L

        /**
         * A screen of proposals is a decision, and a decision with twenty
         * options is one the user postpones.
         */
        const val MAX_PROPOSALS = 5
    }
}
