plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase is mandatory (google-services + crashlytics + analytics). The json
// lives in the gitignored release/ folder next to the keystores; run
// ENCRYPT_KEY=<passphrase> ./release/decrypt-secrets.sh to produce it. The
// google-services plugin only scans app/, so bridge the file into place first,
// then always apply the plugins.
rootProject.file("release/google-services.json")
    .copyTo(file("google-services.json"), overwrite = true)
apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")

android {
    namespace = "com.ryccoatika.journeyrecorder"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ryccoatika.journeyrecorder"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Shared debug keystore so every machine/CI produces the same debug
        // signature (stable SHA-1 for API-console registrations). These are the
        // well-known public Android debug credentials — not secrets.
        getByName("debug") {
            storeFile = rootProject.file("release/app-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Real release key. Guarded by existence so the project still builds on
        // machines/CI without the keystore. Passwords come from Gradle properties
        // (~/.gradle/gradle.properties or -P/env) — never hardcoded here.
        if (rootProject.file("release/app-release.jks").exists()) {
            create("release") {
                storeFile = rootProject.file("release/app-release.jks")
                storePassword = properties["JOURNEYRECORDER_RELEASE_KEYSTORE_PWD"]?.toString().orEmpty()
                keyAlias = "journeyrecorder"
                keyPassword = properties["JOURNEYRECORDER_RELEASE_KEY_PWD"]?.toString().orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            // Picks up the reconfigured debug signingConfig above.
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Signed only when release/app-release.jks is present; null → unsigned.
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}