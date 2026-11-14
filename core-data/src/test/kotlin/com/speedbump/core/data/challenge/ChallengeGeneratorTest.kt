package com.speedbump.core.data.challenge

import com.google.common.truth.Truth.assertThat
import com.speedbump.core.data.model.ChallengeType
import org.junit.Test
import kotlin.random.Random

class ChallengeGeneratorTest {

    private val generator = ChallengeGenerator()

    @Test
    fun `every challenge type generates its own shape`() {
        assertThat(generator.generate(ChallengeType.NONE)).isEqualTo(Challenge.None)
        assertThat(generator.generate(ChallengeType.TYPING)).isInstanceOf(Challenge.Typing::class.java)
        assertThat(generator.generate(ChallengeType.MATH)).isInstanceOf(Challenge.Arithmetic::class.java)
        assertThat(generator.generate(ChallengeType.HOLD)).isInstanceOf(Challenge.Hold::class.java)
        assertThat(generator.generate(ChallengeType.NFC_TAG)).isInstanceOf(Challenge.PhysicalTag::class.java)
        assertThat(generator.generate(ChallengeType.QR_CODE)).isInstanceOf(Challenge.PhysicalTag::class.java)
    }

    @Test
    fun `a generated challenge reports the type it was asked for`() {
        ChallengeType.entries.forEach { type ->
            assertThat(generator.generate(type).type).isEqualTo(type)
        }
    }

    // --- typing ------------------------------------------------------------

    @Test
    fun `the phrase is at least the length the spec asks for`() {
        repeat(200) { seed ->
            val phrase = typing(seed).phrase
            assertThat(phrase.length).isAtLeast(ChallengeGenerator.MIN_PHRASE_CHARS)
        }
    }

    @Test
    fun `the phrase is plain lowercase words a phone keyboard types without switching`() {
        repeat(200) { seed ->
            assertThat(typing(seed).phrase).matches("[a-z]+( [a-z]+)*")
        }
    }

    // A phrase that repeats a word invites pattern-matching instead of reading,
    // which is the muscle memory this challenge exists to defeat.
    @Test
    fun `the phrase never repeats a word back to back`() {
        repeat(200) { seed ->
            val words = typing(seed).phrase.split(" ")
            words.zipWithNext().forEach { (first, second) ->
                assertThat(first).isNotEqualTo(second)
            }
        }
    }

    @Test
    fun `different interceptions get different phrases`() {
        val phrases = (0 until 50).map { typing(it).phrase }.toSet()

        assertThat(phrases.size).isAtLeast(40)
    }

    @Test
    fun `the same seed produces the same phrase`() {
        assertThat(typing(7).phrase).isEqualTo(typing(7).phrase)
    }

    // --- arithmetic --------------------------------------------------------

    @Test
    fun `the stated answer is always the correct one`() {
        repeat(300) { seed ->
            (0..4).forEach { escalation ->
                val problem = arithmetic(escalation, seed)
                assertThat(evaluate(problem.question)).isEqualTo(problem.answer)
            }
        }
    }

    // A subtraction that goes negative is a harder, different question than the
    // one intended, and the answer field would need a minus sign to enter.
    @Test
    fun `answers are never negative`() {
        repeat(300) { seed ->
            (0..4).forEach { escalation ->
                assertThat(arithmetic(escalation, seed).answer).isAtLeast(0)
            }
        }
    }

    @Test
    fun `the first escalation rung is addition and the later ones are not`() {
        repeat(50) { seed ->
            assertThat(arithmetic(0, seed).question).contains("+")
            assertThat(arithmetic(1, seed).question).contains("−")
            assertThat(arithmetic(2, seed).question).contains("×")
            assertThat(arithmetic(3, seed).question).contains("×")
        }
    }

    @Test
    fun `escalating past the last rung stays on the last rung rather than crashing`() {
        val far = arithmetic(99, seed = 1)

        assertThat(evaluate(far.question)).isEqualTo(far.answer)
    }

    @Test
    fun `a negative escalation index is treated as the first rung`() {
        val problem = arithmetic(-3, seed = 1)

        assertThat(problem.question).contains("+")
    }

    // --- other types -------------------------------------------------------

    @Test
    fun `the hold challenge uses the duration the spec names`() {
        val hold = generator.generate(ChallengeType.HOLD) as Challenge.Hold

        assertThat(hold.seconds).isEqualTo(30)
    }

    @Test
    fun `a tag challenge carries the registered payload, or none if unregistered`() {
        val registered = generator.generate(
            ChallengeType.NFC_TAG,
            expectedTagPayload = "kitchen-tag",
        ) as Challenge.PhysicalTag
        val unregistered = generator.generate(ChallengeType.QR_CODE) as Challenge.PhysicalTag

        assertThat(registered.expectedPayload).isEqualTo("kitchen-tag")
        assertThat(unregistered.expectedPayload).isNull()
    }

    // --- helpers -----------------------------------------------------------

    private fun typing(seed: Int) =
        generator.generate(ChallengeType.TYPING, random = Random(seed)) as Challenge.Typing

    private fun arithmetic(escalation: Int, seed: Int) = generator.generate(
        ChallengeType.MATH,
        escalationIndex = escalation,
        random = Random(seed),
    ) as Challenge.Arithmetic

    /** Independently evaluates the printed question, rather than trusting it. */
    private fun evaluate(question: String): Int {
        val parts = question.split(" ")
        val left = parts[0].toInt()
        val right = parts[2].toInt()
        return when (parts[1]) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            else -> error("Unknown operator in \"$question\"")
        }
    }
}
