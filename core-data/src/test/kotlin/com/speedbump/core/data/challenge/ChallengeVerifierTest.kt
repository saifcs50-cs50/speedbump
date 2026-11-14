package com.speedbump.core.data.challenge

import com.google.common.truth.Truth.assertThat
import com.speedbump.core.data.model.ChallengeType
import org.junit.Test

class ChallengeVerifierTest {

    private val verifier = ChallengeVerifier()

    @Test
    fun `no challenge always passes`() {
        assertThat(verifier.verify(Challenge.None, "")).isTrue()
    }

    @Test
    fun `the exact phrase passes`() {
        val challenge = Challenge.Typing("quiet river waits gentle harbour turns")

        assertThat(verifier.verify(challenge, "quiet river waits gentle harbour turns")).isTrue()
    }

    // Someone who typed every word correctly has done the work. Failing them on
    // a capital or a stray space teaches that the challenge is arbitrary, and an
    // arbitrary challenge is one the user switches off.
    @Test
    fun `capitalisation, stray spaces and trailing whitespace are forgiven`() {
        val challenge = Challenge.Typing("quiet river waits")

        assertThat(verifier.verify(challenge, "Quiet River Waits")).isTrue()
        assertThat(verifier.verify(challenge, "  quiet   river  waits  ")).isTrue()
    }

    @Test
    fun `a wrong or missing word fails`() {
        val challenge = Challenge.Typing("quiet river waits")

        assertThat(verifier.verify(challenge, "quiet river turns")).isFalse()
        assertThat(verifier.verify(challenge, "quiet river")).isFalse()
        assertThat(verifier.verify(challenge, "")).isFalse()
    }

    @Test
    fun `the right number passes, in whatever spacing`() {
        val challenge = Challenge.Arithmetic("12 + 30", 42)

        assertThat(verifier.verify(challenge, "42")).isTrue()
        assertThat(verifier.verify(challenge, " 42 ")).isTrue()
    }

    @Test
    fun `a wrong number, or something that is not a number, fails`() {
        val challenge = Challenge.Arithmetic("12 + 30", 42)

        assertThat(verifier.verify(challenge, "41")).isFalse()
        assertThat(verifier.verify(challenge, "forty two")).isFalse()
        assertThat(verifier.verify(challenge, "")).isFalse()
        assertThat(verifier.verify(challenge, "42.0")).isFalse()
    }

    // Both are completed by an event rather than by typing, so no string should
    // ever clear them - including an empty one.
    @Test
    fun `hold and tag challenges are never cleared by text`() {
        assertThat(verifier.verify(Challenge.Hold(30), "")).isFalse()
        assertThat(verifier.verify(Challenge.Hold(30), "done")).isFalse()

        val tag = Challenge.PhysicalTag(ChallengeType.NFC_TAG, expectedPayload = "kitchen")
        assertThat(verifier.verify(tag, "kitchen")).isFalse()
    }

    @Test
    fun `a registered tag accepts only itself`() {
        val tag = Challenge.PhysicalTag(ChallengeType.NFC_TAG, expectedPayload = "kitchen-tag")

        assertThat(verifier.verifyTag(tag, "kitchen-tag")).isTrue()
        assertThat(verifier.verifyTag(tag, " kitchen-tag ")).isTrue()
        assertThat(verifier.verifyTag(tag, "wallet-card")).isFalse()
    }

    // Before the user has chosen a tag, any scan clears it - otherwise the
    // challenge would be impossible on the first use.
    @Test
    fun `an unregistered tag challenge accepts any real scan but not an empty one`() {
        val tag = Challenge.PhysicalTag(ChallengeType.QR_CODE, expectedPayload = null)

        assertThat(verifier.verifyTag(tag, "anything")).isTrue()
        assertThat(verifier.verifyTag(tag, "   ")).isFalse()
    }
}
