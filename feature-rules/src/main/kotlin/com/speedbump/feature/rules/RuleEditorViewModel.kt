package com.speedbump.feature.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.core.data.apps.InstalledAppsProvider
import com.speedbump.core.data.model.AppRule
import com.speedbump.core.data.model.ChallengeType
import com.speedbump.core.data.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleEditorUiState(
    val packageName: String = "",
    val appLabel: String = "",
    val rule: AppRule = AppRule(packageName = ""),
    val isLoading: Boolean = true,
)

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rulesRepository: RulesRepository,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle[ARG_PACKAGE]) {
        "RuleEditor requires a $ARG_PACKAGE argument"
    }

    private val _uiState = MutableStateFlow(RuleEditorUiState(packageName = packageName))
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = rulesRepository.find(packageName)
                ?: AppRule(packageName = packageName)
            _uiState.value = RuleEditorUiState(
                packageName = packageName,
                appLabel = installedAppsProvider.labelFor(packageName),
                rule = existing,
                isLoading = false,
            )
        }
    }

    /**
     * Edits are persisted as they are made rather than behind a Save button.
     * There is no draft state worth losing here, and a user who backs out of
     * this screen expecting their change to have stuck is right.
     */
    private fun edit(transform: (AppRule) -> AppRule) {
        _uiState.update { it.copy(rule = transform(it.rule)) }
        viewModelScope.launch { rulesRepository.upsert(_uiState.value.rule) }
    }

    fun onBaseDelayChange(seconds: Int) = edit { it.copy(baseDelaySeconds = seconds) }

    fun onChallengeChange(challenge: ChallengeType) = edit { it.copy(challenge = challenge) }

    fun onAskIntentionChange(enabled: Boolean) = edit { it.copy(askIntention = enabled) }

    fun onBudgetChange(minutes: Int?) = edit { it.copy(dailyBudgetMinutes = minutes) }

    fun onQuotaChange(opens: Int?) = edit { it.copy(dailyLaunchQuota = opens) }

    fun onSessionGrantChange(minutes: Int) = edit { it.copy(sessionGrantMinutes = minutes) }

    fun onRemove() {
        viewModelScope.launch { rulesRepository.remove(packageName) }
    }

    companion object {
        const val ARG_PACKAGE = "packageName"
    }
}
