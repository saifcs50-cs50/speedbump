package com.speedbump.core.data.challenge

import com.speedbump.core.data.model.ChallengeType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Builds the challenge shown for one interception.
 *
 * [Random] is a parameter rather than a field so a test can pin a seed and
 * assert on the exact question produced. Every challenge here has to be
 * solvable by someone who is mildly annoyed and standing up: the point is to
 * cost attention, not to be a puzzle the user fails and resents.
 */
@Singleton
class ChallengeGenerator @Inject constructor() {

    fun generate(
        type: ChallengeType,
        escalationIndex: Int = 0,
        expectedTagPayload: String? = null,
        random: Random = Random.Default,
    ): Challenge = when (type) {
        ChallengeType.NONE -> Challenge.None
        ChallengeType.TYPING -> Challenge.Typing(phrase(random))
        ChallengeType.MATH -> arithmetic(escalationIndex, random)
        ChallengeType.HOLD -> Challenge.Hold(HOLD_SECONDS)
        ChallengeType.NFC_TAG, ChallengeType.QR_CODE ->
            Challenge.PhysicalTag(type, expectedTagPayload)
    }

    /**
     * Words are drawn in an adjective-noun-verb cycle so the result reads like
     * a sentence rather than a captcha. It is still nonsense, which is the
     * point - a phrase that can be predicted can be typed without reading it.
     */
    private fun phrase(random: Random): String {
        val words = mutableListOf<String>()
        var length = 0
        var slot = 0

        while (length < MIN_PHRASE_CHARS) {
            val pool = WORD_POOLS[slot % WORD_POOLS.size]
            var word = pool[random.nextInt(pool.size)]
            // A repeated word invites the user to stop reading and start
            // pattern-matching, which is exactly what this defeats.
            while (word == words.lastOrNull()) word = pool[random.nextInt(pool.size)]
            words += word
            // The joined phrase has one fewer space than it has words, so the
            // separator is counted per gap rather than per word.
            length += if (words.size == 1) word.length else word.length + 1
            slot++
        }
        return words.joinToString(" ")
    }

    private fun arithmetic(escalationIndex: Int, random: Random): Challenge.Arithmetic {
        return when (escalationIndex.coerceAtLeast(0)) {
            0 -> {
                val a = random.nextInt(11, 50)
                val b = random.nextInt(11, 50)
                Challenge.Arithmetic("$a + $b", a + b)
            }

            1 -> {
                // Ordered so the answer is never negative: a subtraction with a
                // negative answer is a different, harder question than intended.
                val larger = random.nextInt(40, 100)
                val smaller = random.nextInt(11, larger - 10)
                Challenge.Arithmetic("$larger − $smaller", larger - smaller)
            }

            2 -> {
                val a = random.nextInt(6, 13)
                val b = random.nextInt(6, 13)
                Challenge.Arithmetic("$a × $b", a * b)
            }

            else -> {
                val a = random.nextInt(12, 40)
                val b = random.nextInt(4, 10)
                Challenge.Arithmetic("$a × $b", a * b)
            }
        }
    }

    companion object {
        /** The spec's figure. The user opted into this challenge explicitly. */
        const val HOLD_SECONDS = 30

        /** The spec asks for ~40 characters. */
        const val MIN_PHRASE_CHARS = 40

        // Deliberately plain, lowercase and unambiguous: no punctuation, no
        // capitals, nothing that needs a second keyboard layer. The friction
        // should come from having to read, not from hunting for a symbol.
        private val ADJECTIVES = listOf(
            "quiet", "slow", "bright", "hollow", "narrow", "patient", "distant",
            "gentle", "steady", "rough", "empty", "warm", "sharp", "plain",
            "modest", "sudden", "careful", "wide", "damp", "clever",
        )
        private val NOUNS = listOf(
            "river", "harbour", "window", "letter", "garden", "morning", "signal",
            "ladder", "island", "kettle", "meadow", "corner", "bridge", "pocket",
            "lantern", "market", "orchard", "compass", "cabin", "thicket",
        )
        private val VERBS = listOf(
            "waits", "turns", "settles", "drifts", "holds", "opens", "listens",
            "gathers", "rests", "leans", "carries", "follows", "widens", "cools",
            "lingers", "returns", "circles", "steadies", "fades", "answers",
        )

        private val WORD_POOLS = listOf(ADJECTIVES, NOUNS, VERBS)
    }
}
