plugins {
    id("speedbump.android.library")
    id("speedbump.android.compose")
    id("speedbump.android.hilt")
}

android {
    namespace = "com.speedbump.service.accessibility"
}

dependencies {
    implementation(projects.coreData)
    implementation(projects.coreUi)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
