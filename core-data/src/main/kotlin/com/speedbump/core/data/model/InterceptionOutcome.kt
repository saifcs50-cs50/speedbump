package com.speedbump.core.data.model

/**
 * What the user did once the speedbump was in front of them.
 *
 * [ABANDONED] is the product. Everything else in this app exists to move
 * events out of the other buckets and into that one.
 */
enum class InterceptionOutcome {
    /** Left before the delay finished. The headline metric. */
    ABANDONED,

    /** Waited out the delay (or passed the challenge) and went in. */
    PROCEEDED,

    /** Spent one of the weekly emergency bypasses. */
    BYPASSED,

    /** Session budget or launch quota hit zero and Speedbump sent them home. */
    EJECTED,

    /** The overlay was torn down for a reason that says nothing about intent
     *  (service disabled, device locked, rules changed mid-interception). */
    VOIDED,
    ;

    val isAbandonment: Boolean get() = this == ABANDONED
}
