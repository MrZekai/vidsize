package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.vidsize.compressor.media.CompressionJobState

/**
 * Loads App Open ads opportunistically and never delays app content.
 *
 * Ads are only loaded after UMP says ads may be requested and the Mobile Ads
 * SDK has finished initialising. A loaded App Open ad is treated as stale after
 * four hours, matching Google's documented lifetime guidance.
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

    fun suppressNextForeground() {
        suppressNextForeground = true
    }

    fun preload() {
        loadIfNeeded()
    }

    fun onAppForeground(activity: Activity?) {
        if (activity == null) return
        if (!ConsentManager.adsAllowed) {
            discardLoadedAd()
            return
        }

        if (suppressNextForeground) {
            suppressNextForeground = false
            loadIfNeeded()
            return
        }

        // Never cover an active/finished compression workflow with a full-screen ad.
        if (CompressionJobState.status !is CompressionJobState.Status.Idle) {
            loadIfNeeded()
            return
        }

        if (policy.shouldShow() && isAdAvailable()) {
            show(activity)
        } else {
            loadIfNeeded()
        }
    }

    private fun loadIfNeeded() {
        if (!ConsentManager.adsAllowed) {
            discardLoadedAd()
            return
        }
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        AppOpenAd.load(
            appContext,
            AppOpenAdPolicy.TEST_APP_OPEN_UNIT,
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
