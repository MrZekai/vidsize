/**
 * Ad identifiers are read from, in order: a Gradle property, an environment
 * variable, then an optional untracked `ads.properties` at the repository root.
 *
 * ## Why they stay out of the repository
 *
 * Not because they are secret - an AdMob unit ID ships inside the APK and anyone
 * can read it out of a decompiled build. The reason is the audit guarantee: a
 * clone with no identifiers packages with ads switched off and still builds, so
 * the unsigned audit AAB in CI can be reviewed by someone who cannot turn the
 * ads SDK on at all. Committing the identifiers would quietly delete that
 * property.
 *
 * `ads.properties` exists so a developer pays that cost once rather than
 * retyping seven `-P` flags on every local build. It is gitignored.
 */
val adsPropertiesFile = rootProject.file("ads.properties")

// ads.properties, java.util.Properties ile DEGIL elle ayristirilir: Gradle
// Kotlin DSL'inde `java` bir eklenti erisimcisi tarafindan golgelenir ve
// `java.util` "Unresolved reference 'util'" verir. Asagisi tamamen Kotlin
// stdlib - golgelenecek nitelikli paket adi ve kaybolacak import yok.
val adsProperties: Map<String, String> = if (!adsPropertiesFile.isFile) emptyMap() else adsPropertiesFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }

fun adId(name: String): String =
    (providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: adsProperties[name]
        ?: "").trim()

val admobAppId: String = adId("VIDSIZE_ADMOB_APP_ID")
val nativeResultAdUnitId: String = adId("VIDSIZE_NATIVE_RESULT_AD_UNIT_ID")
val homeBannerAdUnitId: String = adId("VIDSIZE_HOME_BANNER_AD_UNIT_ID")
val compressionBannerAdUnitId: String = adId("VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID")
val appOpenAdUnitId: String = adId("VIDSIZE_APP_OPEN_AD_UNIT_ID")
val interstitialAdUnitId: String = adId("VIDSIZE_INTERSTITIAL_AD_UNIT_ID")
val rewardedAdUnitId: String = adId("VIDSIZE_REWARDED_AD_UNIT_ID")

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
/**
 * Identifiers that MUST be present for ads to be enabled at all.
 *
 * ## Required versus optional, and why the split exists
 *
 * `adsConfigured` is all-or-nothing: one blank entry here switches off EVERY
 * format, banners included. That is the right default for a unit the app cannot
 * work without - shipping half a configuration is how Google's sample units
 * reached real users in v0.8.7 - but applied to every slot it becomes a silent
 * total shutdown, which is the failure mode this release spent its effort
 * removing everywhere else.
 *
 * So the list is split. A format the product depends on is required. A format
 * that is genuinely optional degrades on its own, loudly in the diagnostics
 * sheet and silently nowhere else:
 *
 *  - **App Open** has no unit in this AdMob account. Rather than block the whole
 *    build on a format the product does not currently use, an absent identifier
 *    disables that one format and leaves the other four working. Creating the
 *    unit later is a one-line property change with no code edit - and
 *    `verifyProductionAdConfig` will then require the privacy policy to declare
 *    it, so the two cannot drift apart.
 *  - **Compression banner** falls back to the home banner unit. One AdMob banner
 *    unit serving two placements is permitted; separate units only buy finer
 *    reporting. Requiring a second unit that adds no revenue would be a build
 *    failure in exchange for a column in a dashboard.
 */
val requiredAdIds = linkedMapOf(
    "VIDSIZE_ADMOB_APP_ID" to admobAppId,
    "VIDSIZE_HOME_BANNER_AD_UNIT_ID" to homeBannerAdUnitId,
    "VIDSIZE_NATIVE_RESULT_AD_UNIT_ID" to nativeResultAdUnitId,
    "VIDSIZE_INTERSTITIAL_AD_UNIT_ID" to interstitialAdUnitId,
    "VIDSIZE_REWARDED_AD_UNIT_ID" to rewardedAdUnitId,
)

/** Present or absent; absent disables exactly one format and nothing else. */
val optionalAdIds = linkedMapOf(
    "VIDSIZE_APP_OPEN_AD_UNIT_ID" to appOpenAdUnitId,
    "VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID" to compressionBannerAdUnitId,
)

val adsConfigured = requiredAdIds.values.none { it.isBlank() } &&
    (requiredAdIds.values + optionalAdIds.values).none { it.contains(googleSamplePublisher) }

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
        versionCode = 19
        versionName = "0.9.1"

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
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$interstitialAdUnitId\"")
        buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$rewardedAdUnitId\"")
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

        /**
         * The variant that goes to the Play closed track.
         *
         * ## Why the hard `ENABLE_ADS = false` is gone
         *
         * v0.8.9 pinned this variant to false as a reaction to v0.8.7, where
         * Google's sample units reached real testers. It worked - and it also
         * made the entire ad model untestable, because the only build a tester
         * ever installs could not show a single ad. Device tests T1 to T7 were
         * literally impossible to run against the artefact being shipped.
         *
         * The protection that actually mattered was never this flag. It was
         * `adsConfigured` (no real identifiers, no ads) plus the runtime
         * backstop in AdIds, which refuses a sample unit in any non-debuggable
         * build. Both survive. So this variant now follows the same rule as
         * release: real identifiers present means real ads, absent means no ads
         * and a build that still succeeds - which is what keeps the unsigned
         * audit AAB buildable with no secrets at all.
         *
         * USE_TEST_ADS also flips to false. Leaving it true here would have
         * requested sample units that AdIds then refuses in a non-debuggable
         * build - ads configured, ads enabled, and nothing on screen, with no
         * indication why. Test creatives belong in `adsQa` below, which is
         * debuggable and can never be uploaded.
         */
        create("closedTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "USE_TEST_ADS", "false")
            buildConfigField("boolean", "ENABLE_ADS", "$adsConfigured")
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

        /**
         * The variant the device test matrix (T1-T7) actually runs against.
         *
         * Release-shaped - same layout, same insets, same unminified code paths
         * as `closedTest` - but debuggable and signed with the PUBLIC QA
         * keystore, so it always shows Google's test creatives and can never be
         * uploaded to Play under Vidsize's identity.
         *
         * This exists because ad behaviour cannot be validated on a build that
         * has no ads, and must not be validated on a build carrying live units:
         * clicking your own production ads is an invalid-traffic problem that
         * gets AdMob accounts suspended, and a tester running through T1-T7 will
         * tap every format several times by design.
         *
         * `isDebuggable = true` is load-bearing rather than a convenience. The
         * runtime backstop in AdIds refuses any Google sample identifier unless
         * BuildConfig.DEBUG is set, so this flag is the single thing that lets
         * test creatives render here and nowhere else.
         */
        create("adsQa") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".adsqa"
            versionNameSuffix = "-adsqa"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("qaDebug")
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("boolean", "ENABLE_ADS", "true")
            manifestPlaceholders["ADMOB_APP_ID"] = googleTestAdMobAppId
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
    // Play In-App Review. Rating volume is the biggest Play ranking lever after
    // the store listing itself, and a compressor's best moment to ask is the
    // one where the user is looking at "15% smaller" - see ReviewPrompt.
    implementation("com.google.android.play:review-ktx:2.0.2")
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
    val privacyPolicy = rootProject.file("docs/privacy.html")
    doLast {
        val missing = requiredAdIds.filterValues { it.isBlank() }.keys
        check(missing.isEmpty()) {
            "Production AdMob configuration is incomplete: ${missing.joinToString()}"
        }

        // QA v0.8.7 BUG-01: the shipped build carried Google's sample publisher,
        // so every user saw "Test Ad" and the developer earned nothing. A
        // release can never again be packaged with one of those identifiers.
        val sample = (requiredAdIds + optionalAdIds)
            .filterValues { it.isNotBlank() && it.contains(googleSamplePublisher) }.keys
        check(sample.isEmpty()) {
            "Google sample AdMob identifiers cannot be shipped: ${sample.joinToString()}"
        }

        // An enabled format must be declared in the published privacy policy.
        //
        // The App Open unit is optional, so this project can ship with the
        // format off and the policy silent about it. The moment an identifier
        // appears, the policy has to say so - otherwise adding one property
        // silently starts showing a format the user was never told about, which
        // is the kind of drift that is invisible in code review and expensive at
        // Play review.
        if (appOpenAdUnitId.isNotBlank()) {
            check(privacyPolicy.isFile && privacyPolicy.readText().contains("App-open ad")) {
                "VIDSIZE_APP_OPEN_AD_UNIT_ID is set but docs/privacy.html does not " +
                    "declare an \"App-open ad\". Declare the format before enabling it."
            }
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

/**
 * Regression gate: the required-identifier list must cover every slot the app
 * actually reads.
 *
 * `adsConfigured` is all-or-nothing, so a slot added to AdIds.kt without a
 * matching entry in `productionAdIds` produces the worst possible outcome - the
 * new format silently never loads, and because nothing is missing from the
 * required list the build reports success. The inverse is worse still: an entry
 * required here that no slot reads would switch off EVERY format, banners
 * included, over an identifier nothing wanted.
 *
 * Rather than trusting the two lists to be kept in step by hand, this reads the
 * BuildConfig fields AdIds.kt references and compares the sets.
 */
val verifyAdUnitCoverage = tasks.register("verifyAdUnitCoverage") {
    group = "verification"
    description = "Fail if AdIds slots and the declared Gradle properties disagree."
    val adIdsFile = file("src/main/java/com/vidsize/compressor/ads/AdIds.kt")
    doLast {
        check(adIdsFile.isFile) { "AdIds.kt not found at ${adIdsFile.path}" }

        val slots = Regex("BuildConfig\\.([A-Z_]+_AD_UNIT_ID)")
            .findAll(adIdsFile.readText())
            .map { it.groupValues[1] }
            .toSortedSet()

        // Every slot AdIds reads must be declared somewhere - required or
        // optional. A slot with no property behind it can never load and nothing
        // would say so; a property with no slot would sit in the required list
        // switching off every format over an identifier nothing wanted.
        val declared = (requiredAdIds.keys + optionalAdIds.keys)
            .map { it.removePrefix("VIDSIZE_") }
            .filter { it.endsWith("_AD_UNIT_ID") }
            .toSortedSet()

        check(slots == declared) {
            "AdIds slots and the declared ad-unit properties have drifted apart.\n" +
                "  read by AdIds : $slots\n" +
                "  declared here : $declared\n" +
                "A slot with no property never loads; a property with no slot can " +
                "switch off every format."
        }
    }
}

/**
 * Regression gate: the rewarded reward length must be stated in exactly one
 * place.
 *
 * The app promises "N minutes with no ads" in eight languages. A translation
 * that hardcodes a number can drift from the constant that enforces it, and an
 * ad-free window shorter than the app claims is a live AdMob policy problem, not
 * a typo. The strings therefore interpolate the duration, and this asserts that
 * every locale still does.
 */
val verifyRewardCopyParity = tasks.register("verifyRewardCopyParity") {
    group = "verification"
    description = "Fail if any locale states the reward duration instead of interpolating it."
    val resDir = file("src/main/res")
    doLast {
        val offenders = mutableListOf<String>()
        resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.forEach { dir ->
                val strings = dir.resolve("strings.xml")
                if (!strings.isFile) return@forEach
                val text = strings.readText()
                listOf("ad_free_title", "ad_free_body", "settings_ads_body").forEach { name ->
                    val value = Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                        .find(text)?.groupValues?.get(1)
                    if (value == null) {
                        offenders += "${dir.name}/$name is missing"
                    } else if (!value.contains("%1" + '$' + "d")) {
                        offenders += "${dir.name}/$name does not interpolate the duration"
                    }
                }
            }
        check(offenders.isEmpty()) {
            "Reward duration copy is not parameterised:\n" + offenders.joinToString("\n")
        }
    }
}

tasks.matching {
    it.name == "bundleRelease" || it.name == "assembleRelease"
}.configureEach {
    dependsOn(verifyProductionAdConfig, verifyAdUnitCoverage, verifyRewardCopyParity)
}

tasks.matching {
    it.name == "bundleClosedTest" || it.name == "assembleClosedTest"
}.configureEach {
    // Deliberately NOT verifyProductionAdConfig: the unsigned audit AAB must
    // keep building with no repository secrets at all. Without identifiers this
    // variant packages with ads off, which is honest; with them it ships the
    // real ones. What must hold either way is that a placeholder App ID never
    // travels with ads enabled, and that the two lists have not drifted.
    dependsOn(verifyAdsOffWithoutRealIds, verifyAdUnitCoverage, verifyRewardCopyParity)
}
