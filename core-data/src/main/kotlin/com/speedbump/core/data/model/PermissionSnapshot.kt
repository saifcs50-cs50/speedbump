package com.speedbump.core.data.model

/**
 * The three special-access grants, as a plain value.
 *
 * Lives here rather than beside the class that reads it so that feature modules
 * can render permission state without depending on :service-accessibility,
 * which depends on them.
 */
data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayGranted: Boolean,
    val usageStatsGranted: Boolean,
) {
    /** Interception cannot happen without both of these. */
    val canIntercept: Boolean get() = accessibilityEnabled && overlayGranted

    val allGranted: Boolean
        get() = accessibilityEnabled && overlayGranted && usageStatsGranted
}
