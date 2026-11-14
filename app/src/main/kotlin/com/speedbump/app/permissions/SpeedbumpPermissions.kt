package com.speedbump.app.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import com.speedbump.core.data.model.PermissionSnapshot
import com.speedbump.service.accessibility.SpeedbumpService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speedbump needs three special-access grants, none of which can be requested
 * with a normal runtime permission dialog. Each one has to be explained and
 * then handed off to a Settings screen.
 */
@Singleton
class SpeedbumpPermissions @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Asked of AccessibilityManager rather than parsed out of Settings.Secure,
     * because the secure setting's format has drifted between OEMs and the
     * manager is the authority the framework itself uses.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val target = ComponentName(context, SpeedbumpService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val id = info.id ?: return@any false
                id.equals(target.flattenToString(), ignoreCase = true) ||
                    id.equals(target.flattenToShortString(), ignoreCase = true)
            }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // AppOps is the only way to read a special-access grant. Every variant of
    // this call is marked deprecated and none has a replacement, because the
    // platform would rather apps did not ask - but usage access genuinely
    // cannot be checked any other way.
    @Suppress("DEPRECATION")
    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        accessibilityEnabled = isAccessibilityServiceEnabled(),
        overlayGranted = canDrawOverlays(),
        usageStatsGranted = hasUsageStatsAccess(),
    )

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlaySettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

