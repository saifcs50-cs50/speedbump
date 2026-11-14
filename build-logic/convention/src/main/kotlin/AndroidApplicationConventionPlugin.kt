import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.speedbump.buildlogic.VerifyNoNetworkPermissionsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            applyCommonAndroid()
        }
        configureKotlinJvm()
        registerPermissionAudit()
    }
}

/**
 * Wires the permission audit into every variant's assemble, so the privacy
 * claim is checked on every build rather than whenever someone remembers.
 */
private fun Project.registerPermissionAudit() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            val capitalized = variant.name.replaceFirstChar { it.uppercase() }
            val audit = tasks.register<VerifyNoNetworkPermissionsTask>(
                "verify${capitalized}NoNetworkPermissions"
            ) {
                group = "verification"
                description = "Fails if Speedbump declares a network or package-query permission."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                forbiddenPermissions.set(
                    setOf(
                        "android.permission.INTERNET",
                        "android.permission.ACCESS_NETWORK_STATE",
                        "android.permission.ACCESS_WIFI_STATE",
                        "android.permission.QUERY_ALL_PACKAGES",
                    )
                )
                report.set(
                    layout.buildDirectory.file("reports/speedbump/${variant.name}-permissions.txt")
                )
            }
            tasks.matching { it.name == "assemble$capitalized" }
                .configureEach { dependsOn(audit) }
        }
    }
}
