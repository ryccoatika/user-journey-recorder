plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.changelog)
}

// Firebase is mandatory (google-services + crashlytics + analytics).
// app/google-services.json is gitignored; produce it with
// ENCRYPT_KEY=<passphrase> ./release/decrypt-secrets.sh before building.
apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")

// versionCode is CI-driven so Play testing tracks always get a monotonically
// increasing code. CI passes -PappVersionCode=<run-based>; default 1 locally.
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
// Version name lives in gradle.properties (single source shared with CI) — used
// by the manifest and the gradle-changelog-plugin (so getChangelog returns
// this version's section).
val appVersionName = project.findProperty("appVersionName") as String

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
        versionCode = appVersionCode
        versionName = appVersionName

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
            // R8 full mode (pinned in gradle.properties): shrink + optimize +
            // obfuscate, and strip unused resources. mapping.txt is uploaded to
            // Play by the deploy workflow so crash stacks stay readable.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // Optimizing defaults — never proguard-android.txt (forces -dontoptimize).
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            // Warnings are errors: deprecations and compiler nags get fixed, not shipped.
            allWarningsAsErrors.set(true)
            // Opt into the future (2.4+) default: constructor-param annotations also
            // target the property — silences KT-73255 migration warnings coherently.
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
    buildFeatures {
        compose = true
    }
}

changelog {
    version.set(appVersionName)
    // CHANGELOG.md lives at the repo root, not inside the module.
    path.set(rootProject.file("CHANGELOG.md").canonicalPath)
    // Our versionName is "1.0", not strict SemVer — accept 2+ number segments.
    headerParserRegex.set("""\d+(\.\d+)+""".toRegex())
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
