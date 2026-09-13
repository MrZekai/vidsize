package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * The rewarded ad: the user watches a short video, and Vidsize goes quiet for
 * ten minutes ([AdFreeWindow]).
 *
 * ## Why this unit matters more than its impression count suggests
 *
 * It has the highest eCPM in the portfolio by a wide margin, because the user
 * chose to watch it and the advertiser knows that. A handful of rewarded views a
 * day can outweigh hundreds of banner impressions. It is also the only thing an
 * app with no paid tier can offer someone who does not want ads - and Vidsize
 * has no paid tier by product decision.
 *
 * ## The policy shape, which is not optional
 *
 * AdMob's rewarded policy is unusually specific, and three of its clauses decide
 * how this class and the strip that drives it are written:
 *
 *  - **The user must start it.** Nothing here auto-plays. [show] is only ever
 *    reached from a tap on the strip.
 *  - **The action and the reward must both be stated beforehand.** The strip
 *    says "a short ad video" and "10 minutes with no ads" before the tap - not
 *    "watch a video", which does not disclose that it is an ad.
 *  - **No feature may depend on watching.** Nothing in Vidsize is gated behind
 *    this. Every compression level, the full quality range, history, sharing -
 *    all of it works identically whether or not the user ever touches the strip.
 *    The reward removes ads; it does not unlock the app.
 *
 * The reward is also non-monetary and non-transferable, which the ten-minute
 * window satisfies by construction.
 *
 * ## The offer is unconditional
 *
 * There is no "let the user see one ad first" threshold and no minimum usage
 * before the strip appears. The banner is on screen from the first second, so
 * the offer is meaningful from the first second too, and a precondition here
 * would only make the highest-earning unit harder to reach. The single condition
 * is that a creative is actually loaded - offering a button that does nothing is
 * worse than showing no offer at all.
 */
object RewardedAds {

    private var ad: RewardedAd? = null
    private var loading = false

    /** True when a creative is in hand, so the strip can offer it honestly. */
    var isLoaded: Boolean by mutableStateOf(false)
        private set

    /** True while the ad is on screen, so the strip can show a busy state. */
    var isShowing: Boolean by mutableStateOf(false)
        private set

    /**
     * Requests a creative if none is in hand.
     *
     * Called when Home appears and after every dismissal. Note that this uses
     * [AdSlots.rewardedRequestable], not `requestable`: the rewarded unit is the
     * one format that keeps loading *during* an ad-free window, so the strip can
     * offer an extension the instant the window lapses.
     */
    fun preload(context: Context) {
        if (!AdSlots.rewardedRequestable) {
            ad = null
            isLoaded = false
            return
        }
        if (loading || ad != null) return
        val unitId = AdIds.rewarded ?: return

        loading = true
        RewardedAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    isLoaded = true
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    isLoaded = false
                    loading = false
                }
            },
        )
    }

    /**
     * Presents the ad. The window is granted only from the SDK's own reward
     * callback - never optimistically, and never on dismissal.
     *
     * A user who closes the ad early gets no window and no penalty of any kind:
     * the strip simply returns to its offer state and they may try again.
     */
    fun show(activity: Activity, onRewardGranted: () -> Unit) {
        val creative = ad ?: run {
            preload(activity)
            return
        }
        if (isShowing) return

        isShowing = true
        creative.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // A rewarded ad is still a full-screen ad. Marking the shared
                // clock keeps the 60-second promise honest for the case where
                // the user dismisses early and earns no window - otherwise an
                // interstitial could land immediately behind it.
                AdPacing.markFullScreenShown()
            }

            override fun onAdDismissedFullScreenContent() {
                ad = null
                isLoaded = false
                isShowing = false
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                isLoaded = false
                isShowing = false
                preload(activity)
            }
        }

        creative.show(activity) {
            // Earned. This is the only path that opens the window.
            AdFreeWindow.grant()
            // Anything already in hand for a suppressed format is now stale.
            InterstitialAds.clearPending()
            onRewardGranted()
        }
    }
}
