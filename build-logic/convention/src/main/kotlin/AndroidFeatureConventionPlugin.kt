import com.speedbump.buildlogic.lib
import com.speedbump.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Every `feature-*` module is an Android library with Compose + Hilt and a
 * dependency on the shared data and UI layers.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("speedbump.android.library")
        pluginManager.apply("speedbump.android.compose")
        pluginManager.apply("speedbump.android.hilt")

        dependencies {
            add("implementation", project(":core-data"))
            add("implementation", project(":core-ui"))

            add("implementation", libs.lib("androidx-core-ktx"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-ktx"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("hilt-navigation-compose"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))

            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
