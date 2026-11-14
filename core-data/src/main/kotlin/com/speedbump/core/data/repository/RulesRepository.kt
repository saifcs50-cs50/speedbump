package com.speedbump.core.data.repository

import com.speedbump.core.data.apps.SafetyListProvider
import com.speedbump.core.data.db.dao.AppRuleDao
import com.speedbump.core.data.model.AppRule
import com.speedbump.core.data.model.toDomain
import com.speedbump.core.data.model.toEntity
import com.speedbump.core.data.time.SpeedbumpClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's opinions about their apps.
 *
 * Every write goes through the safety list. A blocker that can be pointed at
 * the dialer is a safety problem, and the check belongs here rather than in
 * the UI so that import, restore and any future automation inherit it too.
 */
@Singleton
class RulesRepository @Inject constructor(
    private val dao: AppRuleDao,
    private val safetyListProvider: SafetyListProvider,
    private val clock: SpeedbumpClock,
) {

    val allRules: Flow<List<AppRule>> =
        dao.observeAll().map { rules -> rules.map { it.toDomain() } }

    val enabledRules: Flow<List<AppRule>> =
        dao.observeEnabled().map { rules -> rules.map { it.toDomain() } }

    val enabledCount: Flow<Int> = dao.observeEnabledCount()

    fun observe(packageName: String): Flow<AppRule?> =
        dao.observe(packageName).map { it?.toDomain() }

    suspend fun find(packageName: String): AppRule? = dao.find(packageName)?.toDomain()

    /** Returns false if the package is protected and cannot be blocked. */
    suspend fun upsert(rule: AppRule): Boolean {
        if (isProtected(rule.packageName)) return false
        dao.upsert(rule.toEntity(createdAtMs = clock.nowMs()))
        return true
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean): Boolean {
        if (enabled && isProtected(packageName)) return false
        val existing = dao.find(packageName)
        if (existing == null) {
            if (!enabled) return true
            dao.upsert(
                AppRule(packageName = packageName, enabled = true)
                    .toEntity(createdAtMs = clock.nowMs())
            )
        } else {
            dao.setEnabled(packageName, enabled)
        }
        return true
    }

    suspend fun remove(packageName: String) = dao.deleteByPackage(packageName)

    fun isProtected(packageName: String): Boolean =
        packageName in safetyListProvider.systemCriticalPackages()
}
