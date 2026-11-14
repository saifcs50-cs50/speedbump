package com.speedbump.core.data.policy

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns "how many times have you opened this recently" into "how long you wait".
 *
 * The product spec asks for two things at once: a base delay the user picks
 * (5-60s) and a 10 -> 20 -> 45 -> 90 second ramp. Those are the same statement
 * expressed as multipliers of a 10 second base, so the ladder is stored as
 * multipliers and applied to whatever base the user chose. A user on a 5s base
 * gets 5/10/23/45; a user on 30s gets 30/60/135/270.
 *
 * The ramp is superlinear on purpose. A linear ramp is easy to wait out; the
 * fourth reopen inside half an hour should feel materially worse than the first.
 */
@Singleton
class EscalationPolicy @Inject constructor() {

    fun delaySecondsFor(baseSeconds: Int, escalationIndex: Int): Int {
        require(baseSeconds > 0) { "baseSeconds must be positive, was $baseSeconds" }
        val clampedBase = baseSeconds.coerceIn(MIN_BASE_SECONDS, MAX_BASE_SECONDS)
        val multiplier = MULTIPLIERS[escalationIndex.coerceIn(0, MULTIPLIERS.lastIndex)]
        return min((clampedBase * multiplier).roundToInt(), MAX_DELAY_SECONDS)
    }

    /**
     * How long the skip control stays hidden. Always at least the whole delay
     * for the first launch, and never less than [MIN_UNSKIPPABLE_SECONDS] -
     * a skip button that appears immediately is not a speedbump.
     */
    fun unskippableSecondsFor(delaySeconds: Int): Int =
        maxOf(MIN_UNSKIPPABLE_SECONDS, (delaySeconds * UNSKIPPABLE_FRACTION).roundToInt())

    companion object {
        const val MIN_BASE_SECONDS = 5
        const val MAX_BASE_SECONDS = 60
        const val DEFAULT_BASE_SECONDS = 10
        const val MAX_DELAY_SECONDS = 300
        const val MIN_UNSKIPPABLE_SECONDS = 3

        /** Escalation window: reopens inside this span keep ramping. */
        const val DEFAULT_WINDOW_MS = 30 * 60 * 1000L

        private const val UNSKIPPABLE_FRACTION = 0.6

        /** 1x, 2x, 4.5x, 9x of base -> 10/20/45/90 at the default base. */
        val MULTIPLIERS = listOf(1.0, 2.0, 4.5, 9.0)
    }
}
