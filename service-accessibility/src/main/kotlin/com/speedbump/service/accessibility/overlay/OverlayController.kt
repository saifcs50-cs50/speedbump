package com.speedbump.service.accessibility.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.speedbump.service.accessibility.ui.InterceptionScreen
import com.speedbump.service.accessibility.ui.InterceptionUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the interception window.
 *
 * WHY AN OVERLAY AND NOT AN ACTIVITY
 * Since Android 10, an app in the background may not start an Activity except
 * under a short list of exemptions, and the failure mode is silent - the launch
 * is simply dropped. An accessibility service reacting to another app coming to
 * the foreground is squarely in the background case, so an Activity-based
 * blocker misses interceptions on exactly the devices it matters most on.
 * A SYSTEM_ALERT_WINDOW overlay has no such restriction, appears in single
 * -digit-millisecond time, and does not disturb the task stack - so when the
 * user chooses to continue, the app underneath is still exactly where it was.
 */
@Singleton
class OverlayController @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {

    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = root != null

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    @MainThread
    @SuppressLint("InflateParams")
    fun show(
        state: StateFlow<InterceptionUiState>,
        onAbandon: () -> Unit,
        onProceed: () -> Unit,
    ): Boolean {
        if (isShowing) return true
        if (!canShow()) {
            Log.w(TAG, "Overlay permission not granted; cannot intercept.")
            return false
        }

        val windowContext = createWindowContext()
        val wm = windowContext.getSystemService(WindowManager::class.java) ?: return false

        val owner = OverlayLifecycleOwner().apply { onCreate() }

        val composeView = ComposeView(windowContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val ui by state.collectAsState()
                InterceptionScreen(
                    state = ui,
                    onAbandon = onAbandon,
                    onProceed = onProceed,
                )
            }
        }

        val container = BackConsumingFrameLayout(windowContext, onBack = onAbandon).apply {
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }

        return try {
            wm.addView(container, buildLayoutParams())
            container.requestFocus()
            owner.onResume()
            windowManager = wm
            root = container
            lifecycleOwner = owner
            true
        } catch (error: WindowManager.BadTokenException) {
            // Happens when the overlay permission was revoked between the check
            // and the add, or on OEMs that gate overlays behind their own toggle.
            Log.e(TAG, "Overlay rejected by WindowManager", error)
            owner.onDestroy()
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Overlay already added", error)
            owner.onDestroy()
            false
        }
    }

    @MainThread
    fun dismiss() {
        val view = root
        val wm = windowManager
        root = null
        windowManager = null

        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { Log.w(TAG, "Overlay already detached", it) }
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun createWindowContext(): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = appContext.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
            val displayContext =
                if (display != null) appContext.createDisplayContext(display) else appContext
            displayContext.createWindowContext(OVERLAY_TYPE, null)
        } else {
            appContext
        }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OVERLAY_TYPE,
            // FLAG_NOT_FOCUSABLE is deliberately absent: a focusable window is
            // the only kind that receives KEYCODE_BACK, and swallowing back is
            // the difference between a speedbump and a suggestion.
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
                // ADJUST_RESIZE is deprecated for Activities but remains the
            // only way to make an overlay window shrink for the IME, which
            // the typing challenge depends on.
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private companion object {
        const val TAG = "SpeedbumpOverlay"
        const val OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
}

/**
 * Consumes the hardware/gesture Back so it cannot be used to dismiss the
 * speedbump. Back is routed to the same handler as "Not now", so backing out
 * is still allowed - it just counts, correctly, as an abandoned launch.
 */
private class BackConsumingFrameLayout(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
