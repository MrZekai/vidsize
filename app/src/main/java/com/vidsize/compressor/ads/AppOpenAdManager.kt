package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback

/**
 * Loads App Open ads opportunistically and never delays app content.
 *
 * Ads are only loaded after UMP says ads may be requested and the Mobile Ads
 * SDK has finished initialising.
 *
 * ## The four-hour number is not a pacing rule
 *
 * [APP_OPEN_EXPIRY_MILLIS] is Google's documented lifetime for a *loaded*
 * app-open creative: after four hours the creative is stale and must be
 * discarded and re-requested. It has nothing to do with how often a user should
 * meet an ad, and reading it as though it did is a well-worn way to silence the
 * format for an entire afternoon while every other counter looks healthy. The
 * interval between two full-screen ads is [AdPacing.FULL_SCREEN_GAP_MILLIS] -
 * sixty seconds, shared with the interstitial - and the per-day ceiling is set
 * in the AdMob panel, not here.
 */
class AppOpenAdManager(
    private val appContext: Context,
    private val policy: AppOpenAdPolicy,
) {
    private var appOpenAd: AppOpenAd? = null
    private var loadTimeMillis: Long = 0L
    private var isLoadingAd = false
    var isShowingAd: Boolean = false
        private set

    private var suppressNextForeground = false

    /** Diagnostics only: whether a fresh creative is currently in hand. */
    val hasLoadedAd: Boolean get() = isAdAvailable()

    /** Diagnostics only: whether the next foreground is being skipped. */
    val isSuppressingNextForeground: Boolean get() = suppressNextForeground

    fun suppressNextForeground() {
        suppressNextForeground = true
    }

    fun preload() {
        loadIfNeeded()
    }

    fun onAppForeground(activity: Activity?) {
        if (activity == null) return
        if (!AdSlots.requestable) {
            discardLoadedAd()
            return
        }

        if (suppressNextForeground) {
            suppressNextForeground = false
            loadIfNeeded()
            return
        }

        // The job-state rule (never cover a running or freshly finished
        // compression) now lives in AdGate, which policy.shouldShow() consults,
        // so every full-screen format reads the same conditions in the same
        // order and the diagnostics sheet can name which one declined.
        if (policy.shouldShow() && isAdAvailable()) {
            show(activity)
        } else {
            loadIfNeeded()
        }
    }

    private fun loadIfNeeded() {
        if (!AdSlots.requestable) {
            discardLoadedAd()
            return
        }
        if (isLoadingAd || isAdAvailable()) return
        val unitId = AdIds.appOpen ?: return
        isLoadingAd = true
        AppOpenAd.load(
            appContext,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTimeMillis = System.currentTimeMillis()
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    loadTimeMillis = 0L
                    isLoadingAd = false
                }
            },
        )
    }

    private fun show(activity: Activity) {
        if (isShowingAd) return
        val ad = appOpenAd ?: run {
            loadIfNeeded()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                policy.markFullScreenShown()
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                loadTimeMillis = 0L
                isShowingAd = false
                loadIfNeeded()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                loadTimeMillis = 0L
                isShowingAd = false
                loadIfNeeded()
            }
        }
        ad.show(activity)
    }

    private fun discardLoadedAd() {
        appOpenAd = null
        loadTimeMillis = 0L
    }

    private fun isAdAvailable(nowMillis: Long = System.currentTimeMillis()): Boolean =
        appOpenAd != null && nowMillis - loadTimeMillis < APP_OPEN_EXPIRY_MILLIS

    private companion object {
        const val APP_OPEN_EXPIRY_MILLIS = 4L * 60L * 60L * 1000L
    }
}
