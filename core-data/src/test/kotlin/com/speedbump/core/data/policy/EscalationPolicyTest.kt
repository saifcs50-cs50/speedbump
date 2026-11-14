package com.speedbump.core.data.policy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EscalationPolicyTest {

    private val policy = EscalationPolicy()

    @Test
    fun `default base produces the specified ten twenty forty-five ninety ladder`() {
        val ladder = (0..3).map { policy.delaySecondsFor(EscalationPolicy.DEFAULT_BASE_SECONDS, it) }

        assertThat(ladder).containsExactly(10, 20, 45, 90).inOrder()
    }

    @Test
    fun `ladder scales with a user-chosen base`() {
        assertThat((0..3).map { policy.delaySecondsFor(5, it) })
            .containsExactly(5, 10, 23, 45).inOrder()
        assertThat((0..3).map { policy.delaySecondsFor(30, it) })
            .containsExactly(30, 60, 135, 270).inOrder()
    }

    @Test
    fun `escalation past the top rung stays at the top rung`() {
        val top = policy.delaySecondsFor(10, 3)

        assertThat(policy.delaySecondsFor(10, 4)).isEqualTo(top)
        assertThat(policy.delaySecondsFor(10, 99)).isEqualTo(top)
    }

    @Test
    fun `delay is capped so a bad rule cannot trap someone for an hour`() {
        val delay = policy.delaySecondsFor(EscalationPolicy.MAX_BASE_SECONDS, 3)

        assertThat(delay).isAtMost(EscalationPolicy.MAX_DELAY_SECONDS)
    }

    @Test
    fun `base outside the supported range is clamped rather than rejected`() {
        assertThat(policy.delaySecondsFor(1, 0)).isEqualTo(EscalationPolicy.MIN_BASE_SECONDS)
        assertThat(policy.delaySecondsFor(9_000, 0)).isEqualTo(EscalationPolicy.MAX_BASE_SECONDS)
    }

    @Test
    fun `a non-positive base is a programming error, not a clamp`() {
        runCatching { policy.delaySecondsFor(0, 0) }
            .also { assertThat(it.isFailure).isTrue() }
    }

    @Test
    fun `the skip control is always hidden for a meaningful stretch`() {
        assertThat(policy.unskippableSecondsFor(10)).isAtLeast(EscalationPolicy.MIN_UNSKIPPABLE_SECONDS)
        assertThat(policy.unskippableSecondsFor(1)).isAtLeast(EscalationPolicy.MIN_UNSKIPPABLE_SECONDS)
    }

    @Test
    fun `longer delays hide the skip control for longer`() {
        assertThat(policy.unskippableSecondsFor(90))
            .isGreaterThan(policy.unskippableSecondsFor(10))
    }
}
