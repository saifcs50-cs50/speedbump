package com.speedbump.core.data.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injected so that delay, escalation and budget logic can be unit tested
 * without waiting in real time.
 *
 * Two clocks on purpose: wall time is what stats are reported against, but it
 * jumps when the user changes timezone or the network corrects the clock, so
 * anything measuring a *duration* must use monotonic time.
 */
interface SpeedbumpClock {
    /** Wall-clock epoch millis. For stamping events. */
    fun nowMs(): Long

    /** Monotonic millis since boot. For measuring elapsed durations. */
    fun elapsedRealtimeMs(): Long
}

@Singleton
class SystemSpeedbumpClock @Inject constructor() : SpeedbumpClock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
