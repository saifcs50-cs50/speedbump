package com.speedbump.core.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.speedbump.core.data.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates every app the user could plausibly want to block, WITHOUT the
 * restricted QUERY_ALL_PACKAGES permission.
 *
 * The manifest declares a <queries> element for ACTION_MAIN/CATEGORY_LAUNCHER,
 * which makes exactly the launchable apps visible to us. That set is a superset
 * of what a user would ever pick and a subset of what QUERY_ALL_PACKAGES would
 * return - so we get the feature without the Play Console declaration, the
 * policy review, or the privacy exposure.
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            // Multiple launcher activities in one package would otherwise
            // show up as duplicate rows in the picker.
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    isSystemApp = appInfo.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Resolves a single label, falling back to the package name. */
    fun labelFor(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
