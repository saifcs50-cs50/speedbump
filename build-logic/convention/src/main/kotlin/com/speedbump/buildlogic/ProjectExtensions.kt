package com.speedbump.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Speedbump-wide SDK targets. Single source of truth for every module. */
object SpeedbumpSdk {
    /**
     * Compiled against 37 because the Compose BOM requires it. This is
     * independent of [TARGET]: compiling against a newer SDK only makes newer
     * APIs visible, it does not opt the app into new runtime behaviour.
     */
    const val COMPILE = 37

    /** Runtime behaviour contract. Bumped deliberately, never incidentally. */
    const val TARGET = 36

    /** Android 8.0. Below this there is no adaptive icon and no java.time. */
    const val MIN = 26
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.bundleVersion(alias: String) = findVersion(alias).get().requiredVersion
