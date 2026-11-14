package com.speedbump.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.core.data.apps.InstalledAppsProvider
import com.speedbump.core.data.model.AppRule
import com.speedbump.core.data.model.InstalledApp
import com.speedbump.core.data.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRow(
    val packageName: String,
    val label: String,
    val armed: Boolean,
    val isProtected: Boolean,
    val isSystemApp: Boolean,
    val summary: String,
)

data class RulesUiState(
    val query: String = "",
    val rows: List<AppRow> = emptyList(),
    val armedCount: Int = 0,
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val rulesRepository: RulesRepository,
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val showSystemApps = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<RulesUiState> = combine(
        installedApps,
        rulesRepository.allRules,
        query,
        showSystemApps,
        loading,
    ) { apps, rules, search, showSystem, isLoading ->
        val rulesByPackage = rules.associateBy { it.packageName }
        val needle = search.trim().lowercase()

        val rows = apps.asSequence()
            .filter { showSystem || !it.isSystemApp || it.packageName in rulesByPackage }
            .filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
            .map { app ->
                val rule = rulesByPackage[app.packageName]
                AppRow(
                    packageName = app.packageName,
                    label = app.label,
                    armed = rule?.enabled == true,
                    isProtected = rulesRepository.isProtected(app.packageName),
                    isSystemApp = app.isSystemApp,
                    summary = rule?.let(::summarise).orEmpty(),
                )
            }
            // Armed apps float to the top: they are the ones a user came here
            // to adjust, and scrolling past 200 apps to reach them is hostile.
            .sortedWith(compareByDescending<AppRow> { it.armed }.thenBy { it.label.lowercase() })
            .toList()

        RulesUiState(
            query = search,
            rows = rows,
            armedCount = rules.count { it.enabled },
            showSystemApps = showSystem,
            isLoading = isLoading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    init {
        viewModelScope.launch {
            installedApps.value = installedAppsProvider.launchableApps()
            loading.value = false
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onToggleSystemApps() {
        showSystemApps.value = !showSystemApps.value
    }

    fun onToggleArmed(packageName: String, armed: Boolean) {
        viewModelScope.launch {
            rulesRepository.setEnabled(packageName, armed)
        }
    }

    private fun summarise(rule: AppRule): String = buildList {
        add("${rule.baseDelaySeconds}s delay")
        rule.dailyBudgetMinutes?.let { add("$it min/day") }
        rule.dailyLaunchQuota?.let { add("$it opens/day") }
        if (rule.challenge != com.speedbump.core.data.model.ChallengeType.NONE) {
            add(rule.challenge.name.lowercase().replace('_', ' '))
        }
    }.joinToString("  ·  ")
}
