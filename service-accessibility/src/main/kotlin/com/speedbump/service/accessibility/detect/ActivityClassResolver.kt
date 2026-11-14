package com.speedbump.service.accessibility.detect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "is this class name an Activity in this package?".
 *
 * The only reliable way to know is to ask PackageManager, which is a binder
 * call, and window events arrive on the main thread inside a latency budget of
 * a few hundred milliseconds. So results are cached - the answer for a given
 * component never changes without the package being reinstalled, at which
 * point our process is killed anyway.
 */
@Singleton
class ActivityClassResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val cache = LruCache<String, Boolean>(CACHE_SIZE)

    fun isActivity(packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        cache.get(key)?.let { return it }

        val result = runCatching {
            context.packageManager.getActivityInfo(ComponentName(packageName, className), 0)
            true
        }.getOrElse { error ->
            // NameNotFoundException is the expected "not an activity" answer.
            // Anything else is also a no, but worth not crashing over.
            if (error !is PackageManager.NameNotFoundException) {
                // Unknown failure: fail open so a blocked app is still blocked.
                return true
            }
            false
        }

        cache.put(key, result)
        return result
    }

    private companion object {
        const val CACHE_SIZE = 256
    }
}
