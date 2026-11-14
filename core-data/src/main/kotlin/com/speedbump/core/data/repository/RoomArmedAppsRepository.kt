package com.speedbump.core.data.repository

import com.speedbump.core.data.apps.SafetyListProvider
import com.speedbump.core.data.di.ApplicationScope
import com.speedbump.core.data.model.AppRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives the armed set from the rules table.
 *
 * Right now "armed" means "the rule is enabled". Phase 6 adds the schedule,
 * budget and quota gates, and they all belong here: the accessibility service
 * must never learn *why* an app is armed, only that it is, so that arming
 * logic can grow without touching the hot path that runs on every window
 * change.
 */
@Singleton
class RoomArmedAppsRepository @Inject constructor(
    private val rulesRepository: RulesRepository,
    private val safetyListProvider: SafetyListProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ArmedAppsRepository {

    override val armedPackages: StateFlow<Set<String>> = rulesRepository.enabledRules
        .map { rules -> computeArmed(rules) }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** The Room flow is already reactive, so there is nothing to pull. */
    override suspend fun refresh() = Unit

    private fun computeArmed(rules: List<AppRule>): Set<String> {
        val safe = safetyListProvider.systemCriticalPackages()
        return rules.asSequence()
            .filter { it.enabled }
            .map { it.packageName }
            .filterNot { it in safe }
            .toSet()
    }
}
