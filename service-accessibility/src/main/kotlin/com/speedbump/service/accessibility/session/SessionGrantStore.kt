package com.speedbump.service.accessibility.session

import com.speedbump.core.data.time.SpeedbumpClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which apps the user has already paid the toll for, and until when.
 *
 * Without this, every Activity transition inside a blocked app would raise a
 * fresh speedbump and the app would be unusable rather than merely inconvenient.
 * Friction that never ends is not friction, it is a block - and this product is
 * explicitly not a blocker.
 *
 * Grants are intentionally NOT persisted. A reboot or a force-stop should hand
 * the user a fresh speedbump, not a free pass they no longer remember earning.
 */
@Singleton
class SessionGrantStore @Inject constructor(
    private val clock: SpeedbumpClock,
) {
    private val expiryByPackage = ConcurrentHashMap<String, Long>()

    fun grant(packageName: String, durationMs: Long = DEFAULT_GRANT_MS) {
        expiryByPackage[packageName] = clock.elapsedRealtimeMs() + durationMs
    }

    fun isGranted(packageName: String): Boolean {
        val expiry = expiryByPackage[packageName] ?: return false
        if (clock.elapsedRealtimeMs() >= expiry) {
            expiryByPackage.remove(packageName)
            return false
        }
        return true
    }

    fun remainingMs(packageName: String): Long {
        val expiry = expiryByPackage[packageName] ?: return 0L
        return (expiry - clock.elapsedRealtimeMs()).coerceAtLeast(0L)
    }

    fun revoke(packageName: String) {
        expiryByPackage.remove(packageName)
    }

    fun revokeAll() {
        expiryByPackage.clear()
    }

    companion object {
        /** How long "continue" buys you before the next speedbump. */
        const val DEFAULT_GRANT_MS = 5 * 60 * 1000L
    }
}
