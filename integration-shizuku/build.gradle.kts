plugins {
    id("speedbump.android.library")
    id("speedbump.android.hilt")
}

android {
    namespace = "com.speedbump.integration.shizuku"
}

dependencies {
    implementation(projects.coreData)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
