package com.speedbump.core.data.challenge

import com.speedbump.core.data.model.ChallengeType

/**
 * A challenge instance, generated fresh for one interception.
 *
 * Instances are values rather than stateful objects so the whole thing can be
 * generated on one side of a process boundary and verified on the other, and
 * so a test can assert on the exact question a seed produces.
 */
sealed interface Challenge {

    val type: ChallengeType

    data object None : Challenge {
        override val type = ChallengeType.NONE
    }

    /** Retype [phrase]. Randomly generated so it cannot become muscle memory. */
    data class Typing(val phrase: String) : Challenge {
        override val type = ChallengeType.TYPING
    }

    data class Arithmetic(val question: String, val answer: Int) : Challenge {
        override val type = ChallengeType.MATH
    }

    data class Hold(val seconds: Int) : Challenge {
        override val type = ChallengeType.HOLD
    }

    /**
     * Scan a tag the user physically placed somewhere inconvenient.
     *
     * [expectedPayload] null means "any tag will do", which is the state before
     * the user has registered one. Registering a specific tag is what stops the
     * user simply tapping any card in their wallet.
     */
    data class PhysicalTag(
        override val type: ChallengeType,
        val expectedPayload: String?,
    ) : Challenge
}
