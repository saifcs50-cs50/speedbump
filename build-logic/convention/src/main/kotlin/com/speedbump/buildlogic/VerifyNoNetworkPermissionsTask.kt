package com.speedbump.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if a forbidden permission reaches the merged manifest.
 *
 * "This app has no internet permission" is a claim on the store listing, not
 * an implementation detail, and it is exactly the kind of claim that breaks
 * silently: no Speedbump source file has to change for it to become false,
 * because a transitive AndroidX dependency can merge a permission in on its
 * own. WorkManager already did precisely that with ACCESS_NETWORK_STATE.
 *
 * So the promise is enforced by the build rather than by memory.
 */
@CacheableTask
abstract class VerifyNoNetworkPermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbiddenPermissions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val declared = PERMISSION_REGEX.findAll(manifest)
            .map { it.groupValues[1] }
            .toSortedSet()

        val violations = forbiddenPermissions.get().filter { it in declared }

        val reportFile = report.get().asFile
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Speedbump permission audit")
                appendLine("=".repeat(40))
                appendLine("Declared permissions (${declared.size}):")
                declared.forEach { appendLine("  $it") }
                appendLine()
                appendLine(if (violations.isEmpty()) "PASS" else "FAIL: ${violations.joinToString()}")
            }
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Speedbump declares permissions it promises never to have:")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("These are almost always merged in by a dependency, not written")
                    appendLine("by hand. Find the source with:")
                    appendLine("  ./gradlew :app:processDebugMainManifest --info")
                    appendLine("then remove it in app/src/main/AndroidManifest.xml with:")
                    appendLine("""  <uses-permission android:name="THE_PERMISSION" tools:node="remove" />""")
                    appendLine()
                    appendLine("Full audit: ${reportFile.absolutePath}")
                }
            )
        }
    }

    private companion object {
        val PERMISSION_REGEX =
            Regex("""<uses-permission[^>]*android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    }
}
