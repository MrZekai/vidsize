// Production Native Ad unit id. Supplied by gradle.properties / local.properties
// or a CI secret; never committed. Debug builds ignore it entirely and always
// use Google's test unit (see ads/NativeAdLoader.kt).
val nativeResultAdUnitId: String =
    (providers.gradleProperty("VIDSIZE_NATIVE_RESULT_AD_UNIT_ID").orNull
        ?: System.getenv("VIDSIZE_NATIVE_RESULT_AD_UNIT_ID")
        ?: "")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vidsize.compressor"
    compileSdk = 36

    defaultConfig {
        // applicationId is permanent after the first Play upload.
        applicationId = "com.vidsize.compressor"
        // API 29+ lets Vidsize publish to MediaStore with scoped storage and
        // avoids broad legacy storage permissions on Android 7–9.
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "String",
            "NATIVE_RESULT_AD_UNIT_ID",
            "\"$nativeResultAdUnitId\"",
        )
    }

    // Stable QA/debug signing key.
    //
    // IMPORTANT: This key is intentionally public and is used ONLY for debug APKs
    // produced for device QA. It must never be used for Play release/upload signing.
    // Keeping one fixed debug key lets every GitHub Actions APK update the previous
    // QA APK in-place instead of Android rejecting it for a signature mismatch.
    signingConfigs {
        create("qaDebug") {
            storeFile = rootProject.file("keystores/vidsize-qa-debug.jks")
            storePassword = "vidsize-qa-debug-2026"
            keyAlias = "vidsizeqa"
            keyPassword = "vidsize-qa-debug-2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("qaDebug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.animation:animation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")

    implementation("androidx.media3:media3-transformer:1.11.0")
    implementation("androidx.media3:media3-effect:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")

    implementation("com.google.android.gms:play-services-ads:25.4.0")

    // GDPR / ePrivacy consent. Required before serving ads in the EEA and UK.
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}
