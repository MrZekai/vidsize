// Production AdMob identifiers are supplied by Gradle properties or CI/local
// environment variables. They are never committed to source control.
val admobAppId: String =
    (providers.gradleProperty("VIDSIZE_ADMOB_APP_ID").orNull
        ?: System.getenv("VIDSIZE_ADMOB_APP_ID")
        ?: "")
val nativeResultAdUnitId: String =
    (providers.gradleProperty("VIDSIZE_NATIVE_RESULT_AD_UNIT_ID").orNull
        ?: System.getenv("VIDSIZE_NATIVE_RESULT_AD_UNIT_ID")
        ?: "")
val homeBannerAdUnitId: String =
    (providers.gradleProperty("VIDSIZE_HOME_BANNER_AD_UNIT_ID").orNull
        ?: System.getenv("VIDSIZE_HOME_BANNER_AD_UNIT_ID")
        ?: "")
val compressionBannerAdUnitId: String =
    (providers.gradleProperty("VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID").orNull
        ?: System.getenv("VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID")
        ?: "")
val appOpenAdUnitId: String =
    (providers.gradleProperty("VIDSIZE_APP_OPEN_AD_UNIT_ID").orNull
        ?: System.getenv("VIDSIZE_APP_OPEN_AD_UNIT_ID")
        ?: "")

val googleTestAdMobAppId = "ca-app-pub-3940256099942544~3347511713"

// Play Upload Key material is reconstructed only inside the signed GitHub
// Actions workflow. No private signing material is committed to the repository.
// The ordinary QA/audit workflow intentionally leaves closedTest unsigned.
val uploadKeystorePath: String =
    (providers.gradleProperty("VIDSIZE_UPLOAD_KEYSTORE_PATH").orNull
        ?: System.getenv("VIDSIZE_UPLOAD_KEYSTORE_PATH")
        ?: "")
val uploadStorePassword: String =
    (providers.gradleProperty("VIDSIZE_UPLOAD_STORE_PASSWORD").orNull
        ?: System.getenv("VIDSIZE_UPLOAD_STORE_PASSWORD")
        ?: "")
val uploadKeyAlias: String =
    (providers.gradleProperty("VIDSIZE_UPLOAD_KEY_ALIAS").orNull
        ?: System.getenv("VIDSIZE_UPLOAD_KEY_ALIAS")
        ?: "")
val uploadKeyPassword: String =
    (providers.gradleProperty("VIDSIZE_UPLOAD_KEY_PASSWORD").orNull
        ?: System.getenv("VIDSIZE_UPLOAD_KEY_PASSWORD")
        ?: "")
val uploadKeystoreType: String =
    (providers.gradleProperty("VIDSIZE_UPLOAD_KEYSTORE_TYPE").orNull
        ?: System.getenv("VIDSIZE_UPLOAD_KEYSTORE_TYPE")
        ?: "PKCS12")

val uploadSigningConfigured =
    uploadKeystorePath.isNotBlank() &&
        uploadStorePassword.isNotBlank() &&
        uploadKeyAlias.isNotBlank() &&
        uploadKeyPassword.isNotBlank()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vidsize.compressor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vidsize.compressor"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "0.8.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("boolean", "ENABLE_ADS", "true")

        buildConfigField("String", "NATIVE_RESULT_AD_UNIT_ID", "\"$nativeResultAdUnitId\"")
        buildConfigField("String", "HOME_BANNER_AD_UNIT_ID", "\"$homeBannerAdUnitId\"")
        buildConfigField("String", "COMPRESSION_BANNER_AD_UNIT_ID", "\"$compressionBannerAdUnitId\"")
        buildConfigField("String", "APP_OPEN_AD_UNIT_ID", "\"$appOpenAdUnitId\"")
    }

    signingConfigs {
        create("qaDebug") {
            storeFile = rootProject.file("keystores/vidsize-qa-debug.jks")
            storePassword = "vidsize-qa-debug-2026"
            keyAlias = "vidsizeqa"
            keyPassword = "vidsize-qa-debug-2026"
        }

        // Only exists when all required secret-backed values are available.
        // This keeps the normal audit AAB explicitly unsigned.
        if (uploadSigningConfigured) {
            create("playUpload") {
                storeFile = file(uploadKeystorePath)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                storeType = uploadKeystoreType
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("qaDebug")
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            manifestPlaceholders["ADMOB_APP_ID"] = googleTestAdMobAppId
        }

        release {
            buildConfigField("boolean", "USE_TEST_ADS", "false")
            manifestPlaceholders["ADMOB_APP_ID"] =
                admobAppId.ifBlank { "MISSING_PRODUCTION_ADMOB_APP_ID" }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("closedTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("boolean", "ENABLE_ADS", "false")
            isMinifyEnabled = false
            isShrinkResources = false
            manifestPlaceholders["ADMOB_APP_ID"] = googleTestAdMobAppId

            // The dedicated signed workflow supplies these values.
            // Without them, this variant remains unsigned for the audit gate.
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
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
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
}

// Dedicated signed-closed-test workflow calls this before bundleClosedTest.
// It is NOT wired into the ordinary audit bundle task because that audit AAB
// is intentionally unsigned and must continue to build without repository secrets.
tasks.register("verifyClosedTestSigningConfig") {
    group = "verification"
    description = "Fail unless the Play upload signing material is present and readable."
    doLast {
        check(uploadSigningConfigured) {
            "Closed-test upload signing is not fully configured."
        }
        check(file(uploadKeystorePath).isFile) {
            "Closed-test upload keystore does not exist: $uploadKeystorePath"
        }
        check(uploadKeystoreType.equals("PKCS12", ignoreCase = true) ||
            uploadKeystoreType.equals("JKS", ignoreCase = true)) {
            "Unsupported upload keystore type: $uploadKeystoreType"
        }
    }
}

val verifyProductionAdConfig = tasks.register("verifyProductionAdConfig") {
    group = "verification"
    description = "Fail production packaging when real AdMob identifiers are missing."
    doLast {
        val missing = linkedMapOf(
            "VIDSIZE_ADMOB_APP_ID" to admobAppId,
            "VIDSIZE_HOME_BANNER_AD_UNIT_ID" to homeBannerAdUnitId,
            "VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID" to compressionBannerAdUnitId,
            "VIDSIZE_NATIVE_RESULT_AD_UNIT_ID" to nativeResultAdUnitId,
            "VIDSIZE_APP_OPEN_AD_UNIT_ID" to appOpenAdUnitId,
        ).filterValues { it.isBlank() }.keys

        check(missing.isEmpty()) {
            "Production AdMob configuration is incomplete: ${missing.joinToString()}"
        }
    }
}

tasks.matching {
    it.name == "bundleRelease" || it.name == "assembleRelease"
}.configureEach {
    dependsOn(verifyProductionAdConfig)
}
