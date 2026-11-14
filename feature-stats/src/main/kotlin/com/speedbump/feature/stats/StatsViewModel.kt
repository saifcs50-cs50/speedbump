package com.speedbump.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.core.data.apps.InstalledAppsProvider
import com.speedbump.core.data.usage.AppStat
import com.speedbump.core.data.usage.StatsSnapshot
import com.speedbump.core.data.usage.UsageIngestScheduler
import com.speedbump.core.data.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val opens: Int,
    val perDayMs: List<Long>,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val hasAnyUsage: Boolean = false,
    val rangeDays: Int = UsageRepository.DEFAULT_RANGE_DAYS,
    val abandonedToday: Int = 0,
    val abandonedInRange: Int = 0,
    val timeReclaimedTodayMs: Long = 0,
    val timeReclaimedRangeMs: Long = 0,
    val pickupsToday: Int = 0,
    val firstPickupMinute: Int? = null,
    val screenTimeTodayMs: Long = 0,
    val hourlyToday: List<Long> = List(24) { 0L },
    val dailyTrend: List<com.speedbump.core.data.usage.DayStat> = emptyList(),
    val topApps: List<AppUsageRow> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    usageRepository: UsageRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val ingestScheduler: UsageIngestScheduler,
) : ViewModel() {

    private val permissionRefresh = MutableStateFlow(0)

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeStats().map { it.toUiState() },
        permissionRefresh,
    ) { state, _ -> state }
        // Resolving an app label is a PackageManager binder call per row, and
        // there are up to a few dozen rows. Off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        // Opening the dashboard is the one moment the numbers are definitely
        // about to be read, so it is the right moment to refresh them.
        ingestScheduler.ingestNow()
    }

    /** Usage access can only be granted in Settings, so re-read on resume. */
    fun refresh() {
        ingestScheduler.ingestNow()
        permissionRefresh.value += 1
    }

    private fun StatsSnapshot.toUiState() = StatsUiState(
        isLoading = false,
        hasUsageAccess = hasUsageAccess,
        hasAnyUsage = hasAnyUsage,
        rangeDays = rangeDays,
        abandonedToday = abandonedToday,
        abandonedInRange = abandonedInRange,
        timeReclaimedTodayMs = timeReclaimedTodayMs,
        timeReclaimedRangeMs = timeReclaimedRangeMs,
        pickupsToday = pickupsToday,
        firstPickupMinute = firstPickupMinute,
        screenTimeTodayMs = screenTimeTodayMs,
        hourlyToday = hourlyToday,
        dailyTrend = dailyTrend,
        topApps = topApps.take(MAX_APP_ROWS).map { it.toRow() },
    )

    private fun AppStat.toRow() = AppUsageRow(
        packageName = packageName,
        label = installedAppsProvider.labelFor(packageName),
        foregroundMs = foregroundMs,
        opens = opens,
        perDayMs = perDayMs,
    )

    private companion object {
        /** Past a dozen apps the list stops being a summary and becomes a log. */
        const val MAX_APP_ROWS = 12
    }
}
