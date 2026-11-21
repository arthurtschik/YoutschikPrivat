plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val code = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val updateToken = (findProperty("updateToken") as String?) ?: ""

android {
    namespace = "de.youtschik.privat"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.youtschik.privat"
        minSdk = 29
        targetSdk = 34
        versionCode = code
        versionName = "1.$code"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("String", "UPDATE_TOKEN", "\"$updateToken\"")
        buildConfigField("String", "UPDATE_REPO", "\"arthurtschik/YoutschikPrivat\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "youtschik"
            keyAlias = "release"
            keyPassword = "youtschik"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
