package com.speedbump.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.speedbump.core.data.apps.InstalledAppsProvider
import com.speedbump.core.data.model.AppRule
import com.speedbump.core.data.model.InterceptionOutcome
import com.speedbump.core.data.repository.ArmedAppsRepository
import com.speedbump.core.data.repository.InterceptionRepository
import com.speedbump.core.data.repository.RulesRepository
import com.speedbump.core.data.time.SpeedbumpClock
import com.speedbump.service.accessibility.detect.ActivityClassResolver
import com.speedbump.service.accessibility.detect.Detection
import com.speedbump.service.accessibility.detect.ForegroundAppDetector
import com.speedbump.service.accessibility.detect.WindowEvent
import com.speedbump.service.accessibility.overlay.OverlayController
import com.speedbump.service.accessibility.session.SessionGrantStore
import com.speedbump.service.accessibility.ui.InterceptionUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

/**
 * The blocking mechanism.
 *
 * BATTERY: the single most important line in this class is the call to
 * [syncServiceInfo], which narrows AccessibilityServiceInfo.packageNames to
 * exactly the currently-armed apps. The system then filters events inside
 * system_server and never wakes this process for anything else. Competing
 * blockers subscribe to every window change on the device and pay for it in
 * the battery screen, which is how they end up uninstalled.
 *
 * The one exception is while an interception is on screen, when the filter is
 * widened to everything for a few seconds - see [syncServiceInfo] for why that
 * is both necessary and cheap.
 */
@AndroidEntryPoint
class SpeedbumpService : AccessibilityService() {

    @Inject lateinit var armedAppsRepository: ArmedAppsRepository
    @Inject lateinit var interceptionRepository: InterceptionRepository
    @Inject lateinit var rulesRepository: RulesRepository
    @Inject lateinit var overlayController: OverlayController
    @Inject lateinit var activityClassResolver: ActivityClassResolver
    @Inject lateinit var sessionGrantStore: SessionGrantStore
    @Inject lateinit var installedAppsProvider: InstalledAppsProvider
    @Inject lateinit var clock: SpeedbumpClock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var detector: ForegroundAppDetector

    private var armedPackages: Set<String> = emptySet()
    private var active: ActiveInterception? = null
    private var countdownJob: Job? = null

    private val uiState = MutableStateFlow(EMPTY_UI_STATE)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")

        detector = ForegroundAppDetector(ownPackage = packageName)

        serviceScope.launch {
            armedAppsRepository.armedPackages.collect { packages ->
                armedPackages = packages
                Log.i(TAG, "Armed set now ${packages.size}: $packages")
                syncServiceInfo()
            }
        }
        serviceScope.launch { armedAppsRepository.refresh() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        if (active != null) {
            handleEventDuringInterception(packageName, className)
            return
        }

        val decision = detector.evaluate(
            event = WindowEvent(packageName, className, SystemClock.elapsedRealtime()),
            armedPackages = armedPackages,
            isSessionGranted = sessionGrantStore::isGranted,
            isActivityClass = activityClassResolver::isActivity,
        )

        when (decision) {
            is Detection.Intercept -> beginInterception(decision.packageName)
            is Detection.Ignore -> Log.v(TAG, "Ignored $packageName: ${decision.reason}")
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Interrupted")
        finishInterception(InterceptionOutcome.VOIDED)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "Service unbound - user turned Speedbump off")
        finishInterception(InterceptionOutcome.VOIDED)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        finishInterception(InterceptionOutcome.VOIDED)
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- intercept

    private fun beginInterception(targetPackage: String) {
        val startedAtElapsed = SystemClock.elapsedRealtime()

        serviceScope.launch {
            // A rule should always exist for an armed app, but the armed set is
            // pushed into the system asynchronously, so a window event can
            // arrive in the gap after a rule is deleted. Defaults keep that
            // race from crashing the service.
            val rule = rulesRepository.find(targetPackage)
                ?: AppRule(packageName = targetPackage)

            val started = interceptionRepository.begin(
                packageName = targetPackage,
                baseSeconds = rule.baseDelaySeconds,
            )
            val label = installedAppsProvider.labelFor(targetPackage)

            uiState.value = InterceptionUiState(
                packageName = targetPackage,
                appLabel = label,
                totalSeconds = started.delaySeconds,
                remainingSeconds = started.delaySeconds,
                escalationIndex = started.escalationIndex,
            )

            val shown = overlayController.show(
                state = uiState.asStateFlow(),
                onAbandon = { finishInterception(InterceptionOutcome.ABANDONED) },
                onProceed = { finishInterception(InterceptionOutcome.PROCEEDED) },
            )

            if (!shown) {
                // No overlay permission, or an OEM refused the window. Do not
                // leave a phantom row implying the user was ever asked.
                interceptionRepository.resolve(started.id, InterceptionOutcome.VOIDED)
                Log.w(TAG, "Could not raise overlay for $targetPackage")
                return@launch
            }

            active = ActiveInterception(
                eventId = started.id,
                packageName = targetPackage,
                delaySeconds = started.delaySeconds,
                sessionGrantMs = rule.sessionGrantMs,
            )
            // Widen the event filter so we can see the user walk away.
            syncServiceInfo()

            Log.i(
                TAG,
                "INTERCEPTED $targetPackage in " +
                    "${SystemClock.elapsedRealtime() - startedAtElapsed}ms " +
                    "delay=${started.delaySeconds}s escalation=${started.escalationIndex}",
            )

            startCountdown(started.delaySeconds)
        }
    }

    private fun startCountdown(delaySeconds: Int) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            val deadline = SystemClock.elapsedRealtime() + delaySeconds * 1_000L
            while (true) {
                val remainingMs = deadline - SystemClock.elapsedRealtime()
                val remainingSeconds = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
                uiState.update { it.copy(remainingSeconds = remainingSeconds) }
                if (remainingMs <= 0) break
                delay(TICK_MS)
            }
        }
    }

    /**
     * While the overlay is up the filter is wide open, so most events here are
     * noise. The one that matters is a *different app's Activity* coming
     * forward, which means the user left rather than answering the prompt.
     */
    private fun handleEventDuringInterception(packageName: String?, className: String?) {
        val activeInterception = active ?: return
        if (packageName == null || className == null) return
        if (packageName == this.packageName) return
        if (packageName == activeInterception.packageName) return

        // Filters out the notification shade, IMEs, toasts and popups, which
        // are not the user leaving.
        if (!activityClassResolver.isActivity(packageName, className)) return

        Log.i(TAG, "User left to $packageName during interception")
        finishInterception(InterceptionOutcome.ABANDONED, ejectHome = false)
    }

    private fun finishInterception(
        outcome: InterceptionOutcome,
        ejectHome: Boolean = outcome == InterceptionOutcome.ABANDONED,
    ) {
        val finished = active ?: return
        active = null
        countdownJob?.cancel()
        countdownJob = null

        overlayController.dismiss()
        detector.onInterceptionResolved(finished.packageName, SystemClock.elapsedRealtime())

        if (outcome == InterceptionOutcome.PROCEEDED) {
            sessionGrantStore.grant(finished.packageName, finished.sessionGrantMs)
        }

        // Narrow the filter straight back down. Every millisecond spent wide
        // open is battery we promised not to spend.
        syncServiceInfo()

        if (ejectHome) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        serviceScope.launch {
            interceptionRepository.resolve(finished.eventId, outcome)
        }
        Log.i(TAG, "RESOLVED ${finished.packageName} as $outcome")
    }

    // ------------------------------------------------------------ service info

    /**
     * Pushes the current watch list into the system.
     *
     * Three states:
     *  - interception on screen -> null, meaning "all packages". This is the
     *    only way to observe the user pressing Home and walking away, which is
     *    the abandonment we exist to count. It lasts seconds, not hours.
     *  - nothing armed -> a sentinel package that cannot exist. An empty array
     *    is ambiguous across OEM implementations and some read it as "all",
     *    which would silently undo the entire battery design.
     *  - normal -> exactly the armed packages.
     */
    private fun syncServiceInfo() {
        val info = serviceInfo ?: return
        info.packageNames = when {
            active != null -> null
            armedPackages.isEmpty() -> arrayOf(SENTINEL_PACKAGE)
            else -> armedPackages.toTypedArray()
        }
        serviceInfo = info
        Log.v(TAG, "serviceInfo.packageNames = ${info.packageNames?.joinToString() ?: "ALL"}")
    }

    private data class ActiveInterception(
        val eventId: Long,
        val packageName: String,
        val delaySeconds: Int,
        val sessionGrantMs: Long,
    )

    companion object {
        const val TAG = "SpeedbumpService"
        private const val TICK_MS = 200L

        /**
         * A package name that can never be installed, used to mean "watch
         * nothing" unambiguously.
         */
        private const val SENTINEL_PACKAGE = "com.speedbump.app.__watch_nothing__"

        private val EMPTY_UI_STATE = InterceptionUiState(
            packageName = "",
            appLabel = "",
            totalSeconds = 0,
            remainingSeconds = 0,
        )
    }
}
