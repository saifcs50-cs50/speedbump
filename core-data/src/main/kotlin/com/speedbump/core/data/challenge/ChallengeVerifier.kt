package com.speedbump.core.data.challenge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether an attempt clears the challenge.
 *
 * Every comparison here is deliberately generous about form and strict about
 * substance. Someone who typed the phrase correctly but capitalised the first
 * word has done the work; failing them teaches that the challenge is arbitrary,
 * and a challenge that feels arbitrary gets switched off.
 */
@Singleton
class ChallengeVerifier @Inject constructor() {

    fun verify(challenge: Challenge, attempt: String): Boolean = when (challenge) {
        is Challenge.None -> true
        is Challenge.Typing -> normalise(attempt) == normalise(challenge.phrase)
        is Challenge.Arithmetic -> attempt.trim().toIntOrNull() == challenge.answer
        // Both are completed by an event, not by text: the UI reports the hold
        // finishing, and a scan arrives as a payload through [verifyTag].
        is Challenge.Hold -> false
        is Challenge.PhysicalTag -> false
    }

    /**
     * A scanned tag or QR payload. A challenge with no registered payload
     * accepts any scan, which is the state before the user has chosen a tag.
     */
    fun verifyTag(challenge: Challenge.PhysicalTag, payload: String): Boolean {
        val expected = challenge.expectedPayload ?: return payload.isNotBlank()
        return expected.trim() == payload.trim()
    }

    private fun normalise(value: String): String =
        value.trim().lowercase().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
