package com.speedbump.core.data.repository

import com.speedbump.core.data.db.dao.InterceptionEventDao
import com.speedbump.core.data.db.entity.InterceptionEventEntity
import com.speedbump.core.data.model.InterceptionOutcome
import com.speedbump.core.data.policy.EscalationPolicy
import com.speedbump.core.data.time.SpeedbumpClock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the event log that the whole stats story is built on.
 *
 * Every interception is recorded the moment the overlay appears, with a null
 * outcome, and resolved when the user does something. An interception that is
 * never resolved (process death, reboot mid-overlay) stays null rather than
 * being guessed at - an inflated "abandoned" count would make the one number
 * the user trusts a lie.
 */
@Singleton
class InterceptionRepository @Inject constructor(
    private val dao: InterceptionEventDao,
    private val clock: SpeedbumpClock,
    private val escalationPolicy: EscalationPolicy,
) {

    /** Records the start of an interception and returns its row id. */
    suspend fun begin(
        packageName: String,
        baseSeconds: Int,
        escalationWindowMs: Long = EscalationPolicy.DEFAULT_WINDOW_MS,
    ): StartedInterception {
        val now = clock.nowMs()
        val priorLaunches = dao.countForPackageBetween(
            packageName = packageName,
            fromMs = now - escalationWindowMs,
            toMs = now,
        )
        val delaySeconds = escalationPolicy.delaySecondsFor(baseSeconds, priorLaunches)
        val id = dao.insert(
            InterceptionEventEntity(
                packageName = packageName,
                startedAtMs = now,
                delaySeconds = delaySeconds,
                escalationIndex = priorLaunches,
            )
        )
        return StartedInterception(
            id = id,
            packageName = packageName,
            delaySeconds = delaySeconds,
            escalationIndex = priorLaunches,
            unskippableSeconds = escalationPolicy.unskippableSecondsFor(delaySeconds),
        )
    }

    suspend fun resolve(
        id: Long,
        outcome: InterceptionOutcome,
        intention: String? = null,
    ) {
        dao.resolve(
            id = id,
            outcome = outcome,
            resolvedAtMs = clock.nowMs(),
            intention = intention?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun recentIntentions(packageName: String, limit: Int = 7): List<String> =
        dao.recentIntentions(packageName, limit)

    fun observeAbandonedCount(fromMs: Long, toMs: Long): Flow<Int> =
        dao.observeAbandonedCount(fromMs, toMs)

    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<InterceptionEventEntity>> =
        dao.observeBetween(fromMs, toMs)
}

data class StartedInterception(
    val id: Long,
    val packageName: String,
    val delaySeconds: Int,
    val escalationIndex: Int,
    val unskippableSeconds: Int,
)
