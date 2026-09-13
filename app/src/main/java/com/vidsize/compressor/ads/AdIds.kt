package com.vidsize.compressor.ads

import com.vidsize.compressor.BuildConfig

/**
 * The single source of truth for which ad unit, if any, a slot may request.
 *
 * ## QA v0.8.7 BUG-01 / BUG-02
 *
 * v0.8.7 shipped to real closed-test users with Google's sample publisher
 * (`3940256099942544`) in every slot. Consequences: every banner, the
 * interstitial and the result-screen native ad rendered Google's own "Test Ad"
 * placeholder, the developer earned nothing, and the sample *native* unit drew
 * AdMob's "native ad validator" debug popup directly over the result screen's
 * only call to action.
 *
 * Two independent gates now stand between a build and a test ad:
 *
 *  1. [BuildConfig.ENABLE_ADS] - false for the closed-test and Play variants
 *     unless the developer's own identifiers were supplied at build time. When
 *     it is false nothing here returns an id, `MobileAds` is never initialised,
 *     and every ad composable renders nothing.
 *  2. [isGoogleSample] - a runtime backstop. Even if a build somehow reached a
 *     user with a sample id compiled in, it is filtered out here rather than
 *     requested.
 *
 * A null return means "this slot has no ad" and every caller already treats it
 * that way.
 */
object AdIds {

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    /** Google's public sample publisher. Serves "Test Ad" creatives only. */
    private const val GOOGLE_SAMPLE_PUBLISHER = "3940256099942544"

    /** All-zeros AdMob placeholder; never a servable unit. */
    private const val PLACEHOLDER_PUBLISHER = "0000000000000000"

    val homeBanner: String?
        get() = resolve(TEST_BANNER, BuildConfig.HOME_BANNER_AD_UNIT_ID)

    /**
     * The compression screen's banner, falling back to the home banner unit.
     *
     * One AdMob banner unit may serve two placements; separate units only buy
     * finer reporting. Requiring a second unit would mean a build failure in
     * exchange for a column in a dashboard, so an absent
     * `COMPRESSION_BANNER_AD_UNIT_ID` quietly reuses the home one. Supplying it
     * later splits the reporting with no code change.
     */
    val compressionBanner: String?
        get() = resolve(TEST_BANNER, BuildConfig.COMPRESSION_BANNER_AD_UNIT_ID)
            ?: resolve(TEST_BANNER, BuildConfig.HOME_BANNER_AD_UNIT_ID)

    val nativeResult: String?
        get() = resolve(TEST_NATIVE, BuildConfig.NATIVE_RESULT_AD_UNIT_ID)

    /**
     * The App Open unit, which this AdMob account does not currently have.
     *
     * Returning null is the format's off switch and every caller already treats
     * it that way: [AppOpenAdManager] never requests, never shows, and the
     * diagnostics sheet reports it as not loaded. No other format is affected -
     * that is the whole point of keeping this identifier optional rather than in
     * the required set, where a blank value would take the banner, the native,
     * the interstitial and the rewarded ad down with it.
     *
     * Creating the unit in AdMob and supplying the property turns the format on
     * with no code change. The privacy policy must declare it first;
     * `verifyProductionAdConfig` enforces that pairing.
     */
    val appOpen: String?
        get() = resolve(TEST_APP_OPEN, BuildConfig.APP_OPEN_AD_UNIT_ID)

    val interstitial: String?
        get() = resolve(TEST_INTERSTITIAL, BuildConfig.INTERSTITIAL_AD_UNIT_ID)

    val rewarded: String?
        get() = resolve(TEST_REWARDED, BuildConfig.REWARDED_AD_UNIT_ID)

    /**
     * True when a sample or placeholder identifier would be requested. Kept
     * public so a build variant can be inspected from a test.
     */
    fun isGoogleSample(unitId: String): Boolean =
        unitId.contains(GOOGLE_SAMPLE_PUBLISHER) || unitId.contains(PLACEHOLDER_PUBLISHER)

    private fun resolve(testId: String, productionId: String): String? {
        // Gate 1: the variant is not allowed to show ads at all.
        if (!BuildConfig.ENABLE_ADS) return null

        val candidate = if (BuildConfig.USE_TEST_ADS) testId else productionId.trim()
        if (candidate.isEmpty()) return null

        // Gate 2: a sample or placeholder unit may only ever be requested from a
        // debug build that explicitly opted in (BuildConfig.ENABLE_ADS is false
        // for every packaged variant that has no real identifiers).
        if (isGoogleSample(candidate) && !BuildConfig.DEBUG) return null

        return candidate
    }
}
