plugins {
    id("speedbump.android.library")
    id("speedbump.android.compose")
}

android {
    namespace = "com.speedbump.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
