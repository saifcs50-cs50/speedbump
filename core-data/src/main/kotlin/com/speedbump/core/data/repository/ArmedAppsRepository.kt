package com.speedbump.core.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The set of packages the accessibility service should currently be watching.
 *
 * "Armed" is narrower than "blocked": an app is armed only if a rule covers it
 * AND that rule's schedule/budget says it applies right now. The service pushes
 * this set straight into AccessibilityServiceInfo.packageNames, so anything not
 * in here costs literally zero battery - the system never wakes us for it.
 */
interface ArmedAppsRepository {
    val armedPackages: StateFlow<Set<String>>

    suspend fun refresh()
}
