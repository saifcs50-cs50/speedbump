package com.speedbump.core.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps Speedbump refuses to stand in front of, ever.
 *
 * These are resolved from the system's *actual current defaults* rather than a
 * hardcoded package list, because "the dialer" is a different package on a
 * Samsung, a Pixel and a Xiaomi, and a user who swapped their SMS app should
 * not silently lose the protection.
 *
 * A blocker that can stop someone reaching a phone or a map is a safety
 * problem, not a feature. This set is unioned with the user's own pins and
 * subtracted from the armed list on every recompute.
 */
@Singleton
class SafetyListProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun systemCriticalPackages(): Set<String> = buildSet {
        add(context.packageName)

        // Phone / emergency calling.
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(::add)
        resolveHandler(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))?.let(::add)

        // Messaging.
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)

        // Maps / navigation.
        resolveHandler(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))?.let(::add)

        // Camera.
        resolveHandler(Intent(MediaStore.ACTION_IMAGE_CAPTURE))?.let(::add)

        // System settings, so a user can always reach the off switch.
        add("com.android.settings")
        add("com.android.systemui")
        add("com.android.emergency")
    }.filter { it.isNotBlank() }.toSet()

    private fun resolveHandler(intent: Intent): String? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        }
        info?.activityInfo?.packageName?.takeIf { it != "android" }
    }.getOrNull()
}
