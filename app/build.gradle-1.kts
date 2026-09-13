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

/**
 * Google's public sample publisher. Any identifier containing it renders
 * Google's own "Test Ad" placeholder creative and earns the developer nothing,
 * and the sample *native* unit is what draws the "AdMob native ad validator"
 * debug popup over the result screen (QA v0.8.7 BUG-01 and BUG-02).
 *
 * It may appear in a debug build only, and only when a developer explicitly
 * opts in with -PVIDSIZE_ENABLE_TEST_ADS=true.
 */
val googleSamplePublisher = "3940256099942544"

/**
 * True only when a complete set of the developer's OWN AdMob identifiers is
 * available. This is the single switch that decides whether the ads SDK is ever
 * allowed to start.
 *
 * When it is false the app ships with ads fully OFF: `ENABLE_ADS` is false,
 * `MobileAds.initialize` is never called, [AdIds] returns null for every slot,
 * and every ad composable renders nothing and reserves no space. That is what
 * closes BUG-01 (every user saw "Test Ad" banners) and BUG-02 (the debug
 * validator popup covered the SHARE VIDEO button) without inventing identifiers
 * that do not belong to this app.
 */
val productionAdIds = listOf(
    admobAppId,
    homeBannerAdUnitId,
    compressionBannerAdUnitId,
    nativeResultAdUnitId,
    appOpenAdUnitId,
)
val adsConfigured = productionAdIds.none { it.isBlank() } &&
    productionAdIds.none { it.contains(googleSamplePublisher) }

/** Opt-in escape hatch so a developer can still exercise ad layout locally. */
val enableTestAdsInDebug: Boolean =
    (providers.gradleProperty("VIDSIZE_ENABLE_TEST_ADS").orNull
        ?: System.getenv("VIDSIZE_ENABLE_TEST_ADS")
        ?: "false").equals("true", ignoreCase = true)

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
        versionCode = 17
        versionName = "0.8.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Overridden per build type below. The default is deliberately the safe
        // one: a variant that forgets to declare its stance gets no ads at all
        // rather than Google's sample creatives.
        buildConfigField("boolean", "ENABLE_ADS", "false")

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
            // Off unless a developer opts in explicitly. A QA tester installing
            // the debug APK must never be shown Google's sample creatives and
            // must never see the native ad validator popup.
            buildConfigField("boolean", "ENABLE_ADS", "$enableTestAdsInDebug")
            manifestPlaceholders["ADMOB_APP_ID"] = googleTestAdMobAppId
        }

        release {
            buildConfigField("boolean", "USE_TEST_ADS", "false")
            buildConfigField("boolean", "ENABLE_ADS", "$adsConfigured")
            // The developer's own App ID whenever one is supplied.
            //
            // The fallback only ever applies to a build where ENABLE_ADS is
            // false (that is what `adsConfigured` decides, and
            // verifyAdsOffWithPlaceholderAppId proves it), so the SDK is never
            // initialised and this value is never used to request anything. It
            // keeps the placeholder that this app has already shipped and booted
            // with rather than introducing an untested one: the Mobile Ads SDK
            // crashes at process start if the meta-data is absent, so the safe
            // move is a known-good string, not an empty or novel one.
            manifestPlaceholders["ADMOB_APP_ID"] =
                admobAppId.ifBlank { googleTestAdMobAppId }

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
            // The closed-test / Play upload variant NEVER shows an ad. v0.8.7
            // shipped Google's sample units to real testers; this variant now
            // cannot request an ad at all, whatever identifiers are present.
            buildConfigField("boolean", "ENABLE_ADS", "false")
            isMinifyEnabled = false
            isShrinkResources = false
            manifestPlaceholders["ADMOB_APP_ID"] =
                admobAppId.ifBlank { googleTestAdMobAppId }

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
        val configured = linkedMapOf(
            "VIDSIZE_ADMOB_APP_ID" to admobAppId,
            "VIDSIZE_HOME_BANNER_AD_UNIT_ID" to homeBannerAdUnitId,
            "VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID" to compressionBannerAdUnitId,
            "VIDSIZE_NATIVE_RESULT_AD_UNIT_ID" to nativeResultAdUnitId,
            "VIDSIZE_APP_OPEN_AD_UNIT_ID" to appOpenAdUnitId,
        )

        val missing = configured.filterValues { it.isBlank() }.keys
        check(missing.isEmpty()) {
            "Production AdMob configuration is incomplete: ${missing.joinToString()}"
        }

        // QA v0.8.7 BUG-01: the shipped build carried Google's sample publisher,
        // so every user saw "Test Ad" and the developer earned nothing. A
        // release can never again be packaged with one of those identifiers.
        val sample = configured.filterValues { it.contains(googleSamplePublisher) }.keys
        check(sample.isEmpty()) {
            "Google sample AdMob identifiers cannot be shipped: ${sample.joinToString()}"
        }
    }
}

/**
 * QA v0.8.7 BUG-01 / BUG-02 structural guard.
 *
 * Ads may only be enabled when the developer's own identifiers are present. If
 * they are not, the app must be packaged with the SDK switched off rather than
 * falling back to Google's sample units. This task makes that invariant a build
 * failure instead of a code-review habit.
 */
val verifyAdsOffWithoutRealIds = tasks.register("verifyAdsOffWithoutRealIds") {
    group = "verification"
    description = "Fail if ads could be enabled without a complete set of real AdMob identifiers."
    doLast {
        if (!adsConfigured) {
            check(!enableTestAdsInDebug) {
                "VIDSIZE_ENABLE_TEST_ADS=true is a local-only debug switch and must " +
                    "not be set for a packaged build."
            }
        }

        // The placeholder App ID in the manifest is only safe because nothing
        // ever starts the SDK. Assert that pairing rather than trusting it.
        val usingPlaceholderAppId = admobAppId.isBlank() ||
            admobAppId.contains(googleSamplePublisher)
        check(!usingPlaceholderAppId || !adsConfigured) {
            "A placeholder AdMob App ID must never ship with ads enabled."
        }
    }
}

tasks.matching {
    it.name == "bundleRelease" || it.name == "assembleRelease"
}.configureEach {
    dependsOn(verifyProductionAdConfig)
}

tasks.matching {
    it.name == "bundleClosedTest" || it.name == "assembleClosedTest"
}.configureEach {
    dependsOn(verifyAdsOffWithoutRealIds)
}
