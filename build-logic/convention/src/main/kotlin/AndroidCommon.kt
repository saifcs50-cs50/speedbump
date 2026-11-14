import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.speedbump.buildlogic.SpeedbumpSdk
import com.speedbump.buildlogic.lib
import com.speedbump.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Shared Kotlin/Java settings. Applied by both the application and library
 * convention plugins so every module compiles identically.
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Speedbump treats warnings as signal, not noise, but does not fail
            // the build on them - OEM-specific deprecations are unavoidable.
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
    dependencies {
        add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs"))
    }
}

internal fun ApplicationExtension.applyCommonAndroid() {
    compileSdk = SpeedbumpSdk.COMPILE
    defaultConfig {
        minSdk = SpeedbumpSdk.MIN
        targetSdk = SpeedbumpSdk.TARGET
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

internal fun LibraryExtension.applyCommonAndroid() {
    compileSdk = SpeedbumpSdk.COMPILE
    defaultConfig {
        minSdk = SpeedbumpSdk.MIN
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
