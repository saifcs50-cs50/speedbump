package com.speedbump.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedbump.app.permissions.SpeedbumpPermissions
import com.speedbump.core.data.model.PermissionSnapshot
import com.speedbump.core.data.repository.InterceptionRepository
import com.speedbump.core.data.repository.RulesRepository
import com.speedbump.core.data.time.DayBoundaries
import com.speedbump.core.data.time.SpeedbumpClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val permissions: PermissionSnapshot = PermissionSnapshot(false, false, false),
    val armedCount: Int = 0,
    val abandonedToday: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissions: SpeedbumpPermissions,
    rulesRepository: RulesRepository,
    interceptionRepository: InterceptionRepository,
    clock: SpeedbumpClock,
) : ViewModel() {

    private val permissionState = MutableStateFlow(permissions.snapshot())

    private val abandonedToday = MutableStateFlow(clock.nowMs())
        .flatMapLatest { now ->
            interceptionRepository.observeAbandonedCount(
                fromMs = DayBoundaries.startOfToday(now),
                toMs = DayBoundaries.endOfToday(now),
            )
        }

    val uiState: StateFlow<HomeUiState> = combine(
        permissionState,
        rulesRepository.enabledCount,
        abandonedToday,
    ) { perms, armed, abandoned ->
        HomeUiState(
            permissions = perms,
            armedCount = armed,
            abandonedToday = abandoned,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Special-access grants can only change in Settings, so re-read on resume. */
    fun refreshPermissions() {
        permissionState.value = permissions.snapshot()
    }
}
