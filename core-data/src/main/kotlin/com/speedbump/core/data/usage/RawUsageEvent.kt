package com.speedbump.core.data.usage

/** A UsageStatsManager event reduced to what aggregation actually needs. */
data class RawUsageEvent(
    val packageName: String?,
    val timestampMs: Long,
    val type: UsageEventType,
)

enum class UsageEventType {
    /** An Activity came to the foreground. */
    RESUMED,

    /** An Activity left the foreground. */
    PAUSED,

    /** Screen went off, or the keyguard came back. Ends any open session. */
    SCREEN_OFF,

    /** Device unlocked. This is what a "pickup" means. */
    UNLOCKED,
}
