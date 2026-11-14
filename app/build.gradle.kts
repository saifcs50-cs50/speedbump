plugins {
    id("speedbump.android.application")
    id("speedbump.android.compose")
    id("speedbump.android.hilt")
}

android {
    namespace = "com.speedbump.app"

    defaultConfig {
        applicationId = "com.speedbump.app"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug signing so `assembleRelease` is runnable before a real
            // upload key exists. Replaced by a real signingConfig at launch.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.coreData)
    implementation(projects.coreUi)
    implementation(projects.serviceAccessibility)
    implementation(projects.featureOnboarding)
    implementation(projects.featureRules)
    implementation(projects.featureStats)
    implementation(projects.featureFocus)
    implementation(projects.integrationShizuku)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // SpeedbumpApplication is the WorkManager Configuration.Provider, so the
    // app module needs these directly rather than through :core-data.
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
