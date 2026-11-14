package com.speedbump.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.core.data.apps.InstalledAppsProvider
import com.speedbump.core.data.baseline.BaselineRepository
import com.speedbump.core.data.baseline.BaselineState
import com.speedbump.core.data.baseline.ProposalReason
import com.speedbump.core.data.baseline.RuleProposal
import com.speedbump.core.data.settings.SettingsRepository
import com.speedbump.core.data.usage.UsageIngestScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProposalRow(
    val packageName: String,
    val label: String,
    val averageDailyMs: Long,
    val averageDailyOpens: Int,
    val reason: ProposalReason,
    val selected: Boolean,
)

data class BaselineUiState(
    val isLoading: Boolean = true,
    val needsUsageAccess: Boolean = false,
    val isMeasuring: Boolean = false,
    val daysMeasured: Int = 0,
    val daysRequired: Int = BaselineRepository.REQUIRED_DAYS,
    val proposals: List<ProposalRow> = emptyList(),
    val isReviewed: Boolean = false,
) {
    val selectedCount: Int get() = proposals.count { it.selected }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val baselineRepository: BaselineRepository,
    private val settingsRepository: SettingsRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val ingestScheduler: UsageIngestScheduler,
) : ViewModel() {

    /** Packages the user has explicitly unchecked. Proposals default to on. */
    private val deselected = MutableStateFlow<Set<String>>(emptySet())

    private var latestProposals: List<RuleProposal> = emptyList()

    val uiState: StateFlow<BaselineUiState> = combine(
        baselineRepository.state,
        deselected,
    ) { state, unchecked ->
        when (state) {
            BaselineState.NeedsUsageAccess ->
                BaselineUiState(isLoading = false, needsUsageAccess = true)

            is BaselineState.Measuring -> BaselineUiState(
                isLoading = false,
                isMeasuring = true,
                daysMeasured = state.daysMeasured,
                daysRequired = state.daysRequired,
            )

            is BaselineState.Ready -> {
                latestProposals = state.proposals
                BaselineUiState(
                    isLoading = false,
                    daysMeasured = state.daysMeasured,
                    proposals = state.proposals.map { it.toRow(it.packageName !in unchecked) },
                )
            }

            BaselineState.Reviewed -> BaselineUiState(isLoading = false, isReviewed = true)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BaselineUiState())

    init {
        // The proposals are only as good as the ingest behind them, and this is
        // the first screen that needs it.
        ingestScheduler.ingestNow()
    }

    fun refresh() = ingestScheduler.ingestNow()

    fun onToggleProposal(packageName: String) {
        deselected.value = if (packageName in deselected.value) {
            deselected.value - packageName
        } else {
            deselected.value + packageName
        }
    }

    /** Arms the checked proposals. An empty selection is a valid choice. */
    fun onAcceptProposals() {
        val unchecked = deselected.value
        val chosen = latestProposals.filter { it.packageName !in unchecked }
        viewModelScope.launch { baselineRepository.accept(chosen) }
    }

    fun onSkipBaseline() {
        viewModelScope.launch { baselineRepository.skip() }
    }

    fun onFinishOnboarding() {
        viewModelScope.launch { settingsRepository.completeOnboarding() }
    }

    private fun RuleProposal.toRow(selected: Boolean) = ProposalRow(
        packageName = packageName,
        label = installedAppsProvider.labelFor(packageName),
        averageDailyMs = averageDailyMs,
        averageDailyOpens = averageDailyOpens,
        reason = reason,
        selected = selected,
    )
}
