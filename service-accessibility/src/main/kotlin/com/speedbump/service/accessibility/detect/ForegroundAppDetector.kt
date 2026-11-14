package com.speedbump.service.accessibility.detect

/** A TYPE_WINDOW_STATE_CHANGED event, reduced to the fields that matter. */
data class WindowEvent(
    val packageName: String?,
    val className: String?,
    /** Monotonic time. Must not be wall clock - it is used for a duration. */
    val elapsedMs: Long,
)

sealed interface Detection {
    data class Intercept(val packageName: String) : Detection
    data class Ignore(val reason: IgnoreReason) : Detection
}

enum class IgnoreReason {
    NO_PACKAGE,
    OWN_PACKAGE,
    NOT_ARMED,
    NOT_AN_ACTIVITY,
    DEBOUNCED,
    SESSION_GRANTED,
    RESOLUTION_COOLDOWN,
}

/**
 * Decides whether a window change deserves a speedbump.
 *
 * Deliberately pure and stateful-but-injectable: every rule here is a decision
 * a user will feel, and getting one wrong means either a missed block or an
 * overlay appearing on top of itself, so all of it is unit tested rather than
 * discovered on a device.
 *
 * The debounce exists because a single app launch commonly emits several
 * WINDOW_STATE_CHANGED events in quick succession - splash activity, then the
 * real one, then a dialog - and each would otherwise raise its own overlay.
 */
class ForegroundAppDetector(
    private val ownPackage: String,
    private val debounceWindowMs: Long = DEFAULT_DEBOUNCE_MS,
    private val resolutionCooldownMs: Long = DEFAULT_RESOLUTION_COOLDOWN_MS,
) {

    private var lastPackage: String? = null
    private var lastEventAtMs: Long = Long.MIN_VALUE
    private var cooldownPackage: String? = null
    private var cooldownUntilMs: Long = Long.MIN_VALUE

    fun evaluate(
        event: WindowEvent,
        armedPackages: Set<String>,
        isSessionGranted: (String) -> Boolean,
        isActivityClass: (packageName: String, className: String) -> Boolean,
    ): Detection {
        val pkg = event.packageName?.takeIf { it.isNotBlank() }
            ?: return Detection.Ignore(IgnoreReason.NO_PACKAGE)

        // Our own overlay raises window events. Reacting to them would loop.
        if (pkg == ownPackage) return Detection.Ignore(IgnoreReason.OWN_PACKAGE)

        if (pkg !in armedPackages) {
            // Still worth remembering: it is how we know the user left.
            recordSeen(pkg, event.elapsedMs)
            return Detection.Ignore(IgnoreReason.NOT_ARMED)
        }

        // Toasts, dialogs, popup windows and IMEs all fire window state changes
        // carrying the host app's package. Only a real Activity means "the user
        // just opened this app".
        val className = event.className
        if (className.isNullOrBlank() || !isActivityClass(pkg, className)) {
            return Detection.Ignore(IgnoreReason.NOT_AN_ACTIVITY)
        }

        // Ejecting the user home does not stop the app they left from emitting
        // a last window event as it is torn down. Without this guard that tail
        // event opens a brand new interception against an app the user has
        // already walked away from, which double-counts the abandonment and
        // pushes the escalation ladder up a rung for free. The headline metric
        // has to be trustworthy above all else, so a short cooldown wins over
        // catching a genuine relaunch inside one second.
        if (pkg == cooldownPackage && event.elapsedMs < cooldownUntilMs) {
            return Detection.Ignore(IgnoreReason.RESOLUTION_COOLDOWN)
        }

        if (pkg == lastPackage && event.elapsedMs - lastEventAtMs < debounceWindowMs) {
            lastEventAtMs = event.elapsedMs
            return Detection.Ignore(IgnoreReason.DEBOUNCED)
        }

        // The user already paid the toll for this app and is inside a granted
        // session. Re-intercepting on internal navigation would be maddening.
        if (isSessionGranted(pkg)) {
            recordSeen(pkg, event.elapsedMs)
            return Detection.Ignore(IgnoreReason.SESSION_GRANTED)
        }

        recordSeen(pkg, event.elapsedMs)
        return Detection.Intercept(pkg)
    }

    /**
     * Called when an interception ends. Clears the debounce so a genuine later
     * return is judged fresh, and opens a short cooldown on the app that was
     * just resolved to absorb its teardown events.
     */
    fun onInterceptionResolved(packageName: String, elapsedMs: Long) {
        lastPackage = null
        lastEventAtMs = Long.MIN_VALUE
        cooldownPackage = packageName
        cooldownUntilMs = elapsedMs + resolutionCooldownMs
    }

    fun reset() {
        lastPackage = null
        lastEventAtMs = Long.MIN_VALUE
        cooldownPackage = null
        cooldownUntilMs = Long.MIN_VALUE
    }

    private fun recordSeen(pkg: String, elapsedMs: Long) {
        lastPackage = pkg
        lastEventAtMs = elapsedMs
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 800L

        /**
         * Long enough to cover an app teardown plus the home transition
         * (measured at ~570ms on a test phone), short enough that a user who
         * genuinely means to go straight back in still gets a speedbump.
         */
        const val DEFAULT_RESOLUTION_COOLDOWN_MS = 1_200L
    }
}
