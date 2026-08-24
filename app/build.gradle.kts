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
        versionCode = 5
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
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
