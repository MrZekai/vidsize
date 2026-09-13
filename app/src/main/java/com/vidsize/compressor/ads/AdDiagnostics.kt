package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * The numbers behind the hidden diagnostics screen.
 *
 * ## Why a diagnostics screen is mandatory rather than nice to have
 *
 * Ad logic is a stack of conditions that decline silently, and every decline
 * looks identical from the outside: no ad appeared. Ads switched off at build
 * time, consent refused, an ad-free window open, the 60-second rule not yet
 * satisfied, a compression still running, no fill from the network - six
 * different causes, six different fixes, one observable symptom.
 *
 * v0.8.7 lost weeks to exactly that ambiguity. The rule this version adopts is
 * that the app must be able to answer "why did no ad appear?" on the device, in
 * words, without a cable and without a log reader. Everything here exists to be
 * read by a person; nothing here gates anything.
 *
 * The last line of the sheet is the important one. Do not make a tester
 * interpret a column of counters - state the conclusion.
 */
object AdDiagnostics {

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(AdPacing.FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Counts a finished compression.
     *
     * "How many jobs has this user completed" is the closest Vidsize equivalent
     * of the usage counter the model calls for, and it is the number a tester
     * needs when asking why the interstitial has not appeared yet.
     */
    fun recordCompression() {
        val store = prefs ?: return
        store.edit().putInt(KEY_COMPRESSIONS, compressionCount() + 1).apply()
    }

    fun compressionCount(): Int = prefs?.getInt(KEY_COMPRESSIONS, 0) ?: 0

    /** Everything the sheet renders, sampled at one instant. */
    data class Snapshot(
        val adsEnabledInBuild: Boolean,
        val usingTestUnits: Boolean,
        val sampleUnitLeaked: Boolean,
        val consentResolved: Boolean,
        val canRequestAds: Boolean,
        val sdkReady: Boolean,
        val compressions: Int,
        val sessions: Int,
        val interstitialsThisSession: Int,
        val interstitialsToday: Int,
        val secondsSinceLastFullScreen: Long,
        val pacingSatisfied: Boolean,
        val secondsUntilPacingAllows: Long,
        val adFreeActive: Boolean,
        val adFreeRemaining: String,
        val interstitialLoaded: Boolean,
        val interstitialPending: Boolean,
        val rewardedLoaded: Boolean,
        val appOpenLoaded: Boolean,
        val appOpenSuppressed: Boolean,
        val verdict: AdGate.Verdict,
    )

    fun snapshot(policy: AppOpenAdPolicy, appOpen: AppOpenAdManager): Snapshot {
        val now = System.currentTimeMillis()
        val since = AdPacing.millisSinceLastFullScreen(now)

        val units = listOfNotNull(
            AdIds.homeBanner,
            AdIds.compressionBanner,
            AdIds.nativeResult,
            AdIds.appOpen,
            AdIds.interstitial,
            AdIds.rewarded,
        )

        return Snapshot(
            adsEnabledInBuild = AdSlots.enabled,
            usingTestUnits = com.vidsize.compressor.BuildConfig.USE_TEST_ADS,
            sampleUnitLeaked = units.any { AdIds.isGoogleSample(it) },
            consentResolved = ConsentManager.consentResolved,
            canRequestAds = ConsentManager.canRequestAds,
            sdkReady = ConsentManager.adsSdkReady,
            compressions = compressionCount(),
            sessions = policy.sessionCount(),
            interstitialsThisSession = InterstitialAds.shownThisSession,
            interstitialsToday = InterstitialAds.shownToday(),
            secondsSinceLastFullScreen = if (since == Long.MAX_VALUE) -1L else since / 1000L,
            pacingSatisfied = AdPacing.canShowFullScreen(now),
            secondsUntilPacingAllows = AdPacing.secondsUntilAllowed(now),
            adFreeActive = AdFreeWindow.isActiveNow(now),
            adFreeRemaining = AdFreeWindow.formatRemaining(AdFreeWindow.remainingMillisNow(now)),
            interstitialLoaded = InterstitialAds.isLoaded,
            interstitialPending = InterstitialAds.pending,
            rewardedLoaded = RewardedAds.isLoaded,
            appOpenLoaded = appOpen.hasLoadedAd,
            appOpenSuppressed = appOpen.isSuppressingNextForeground,
            verdict = AdGate.evaluate(loaded = InterstitialAds.isLoaded),
        )
    }

    private const val KEY_COMPRESSIONS = "completed_compressions"
}
