package com.speedbump.core.data.baseline

import com.speedbump.core.data.apps.SafetyListProvider
import com.speedbump.core.data.repository.RulesRepository
import com.speedbump.core.data.settings.SettingsRepository
import com.speedbump.core.data.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BaselineState {

    /** Nothing can be measured without the usage grant. */
    data object NeedsUsageAccess : BaselineState

    /** Not enough days of real data yet to propose anything honest. */
    data class Measuring(val daysMeasured: Int, val daysRequired: Int) : BaselineState

    data class Ready(
        val daysMeasured: Int,
        val proposals: List<RuleProposal>,
    ) : BaselineState

    /** The user has acted on the proposals, or chose to pick apps by hand. */
    data object Reviewed : BaselineState
}

/**
 * The baseline week.
 *
 * The spec asks for three days of measurement before anything is proposed, so
 * that rules come from real data rather than from what the user feels guilty
 * about. What it assumes - that the measurement has to be waited out - is not
 * true on Android: `UsageStatsManager` already holds about a week of history,
 * and Speedbump backfills it on first ingest. So "three days of data" is
 * satisfied the moment usage access is granted, for anyone who has owned the
 * phone a week.
 *
 * The requirement is therefore read as days of *data*, not days of *waiting*.
 * A new phone, or a user who grants usage access late, still waits - which is
 * the case the rule was written for. Everyone else gets proposals immediately,
 * instead of an app that does nothing for three days and is uninstalled on the
 * second.
 */
@Singleton
class BaselineRepository @Inject constructor(
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val rulesRepository: RulesRepository,
    private val safetyListProvider: SafetyListProvider,
    private val planner: BaselinePlanner,
) {

    val state: Flow<BaselineState> = combine(
        settingsRepository.settings,
        usageRepository.observeStats(BASELINE_WINDOW_DAYS),
    ) { settings, stats ->
        when {
            settings.baselineReviewed -> BaselineState.Reviewed
            !stats.hasUsageAccess -> BaselineState.NeedsUsageAccess
            else -> {
                val daysMeasured = stats.dailyTrend.count { it.foregroundMs > 0 }
                if (daysMeasured >= REQUIRED_DAYS) {
                    BaselineState.Ready(
                        daysMeasured = daysMeasured,
                        proposals = planner.proposals(
                            apps = stats.topApps,
                            daysMeasured = daysMeasured,
                            protectedPackages = safetyListProvider.systemCriticalPackages(),
                        ),
                    )
                } else {
                    BaselineState.Measuring(daysMeasured, REQUIRED_DAYS)
                }
            }
        }
    }.flowOn(Dispatchers.Default) // systemCriticalPackages() is PackageManager work.

    /** Arms the chosen proposals and closes the baseline. */
    suspend fun accept(proposals: List<RuleProposal>) {
        proposals.forEach { rulesRepository.upsert(it.toRule()) }
        settingsRepository.markBaselineReviewed()
    }

    suspend fun skip() = settingsRepository.skipBaseline()

    companion object {
        /** Matches the 7-day stats window and the ingest backfill. */
        const val BASELINE_WINDOW_DAYS = 7

        const val REQUIRED_DAYS = 3
    }
}
