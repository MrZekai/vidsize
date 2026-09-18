package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.Calendar

/**
 * The interstitial, and the deferred-show mechanism that is the whole point of
 * it.
 *
 * ## The mistake this is built to avoid
 *
 * When a Vidsize compression finishes, the result screen offers Share, Show in
 * Gallery, Open Video and Compress Another. The obvious wiring is: "Compress
 * another" shows an ad, and the external actions cancel it,
 * because an ad would interrupt the user on their way to the file.
 *
 * That wiring earns almost nothing. Practically everyone who just produced a
 * smaller video wants to *do something with it* - that is why they compressed
 * it. So the cancelling paths are the common ones, the ad path is the rare
 * one, and most of the format's revenue disappears into a design that looks
 * considerate on paper.
 *
 * The correct move is not to cancel but to **defer**:
 *
 *  - "Compress another" - a plain transition back to Home - shows the ad now.
 *  - Share / Show in Gallery - the user is leaving for content they
 *    asked for - [markPending]; the ad appears when they come back, not between
 *    them and their video.
 *  - Open Video stays inside Vidsize and deliberately remains ad-free.
 *
 * Both halves respect the same three-minute promise in [AdPacing], so a deferred
 * ad that lands right after an app-open ad is skipped rather than stacked.
 * A finished output can also pay for at most one interstitial. Sharing, then
 * opening the same file in Gallery, must not turn one compression into two
 * full-screen impressions.
 *
 * ## Preloading is free here, unlike in most apps
 *
 * The usual problem with interstitials is that requesting one at the moment of
 * display loses the impression on a slow connection. Vidsize does not have that
 * problem: a compression runs for two to five minutes, and the request goes out
 * when the job *starts*. By the time a result screen exists, the creative has
 * had minutes to arrive. There is no load timeout here because there is nothing
 * to time out against - the user is never waiting on this request.
 *
 * ## What is NOT here
 *
 * No session quota, no daily quota, no "first N compressions are free", no
 * minimum-usage threshold. Those are tempo settings and they live in the AdMob
 * panel (3 per hour per user), where they can be retuned without a release.
 * Every one of them that lived in code in v0.8.7 declined silently, and the
 * silence was what cost the weeks. The counters below exist to be *displayed*
 * in the diagnostics sheet, never to gate anything.
 */
object InterstitialAds {

    private var prefs: SharedPreferences? = null
    private var ad: InterstitialAd? = null
    private var loading = false

    /** True when a creative is in hand. Compose state so diagnostics is live. */
    var isLoaded: Boolean by mutableStateOf(false)
        private set

    /**
     * The deferred flag: "the user left for content they asked for; show an ad
     * when they return".
     *
     * Process-level and deliberately not persisted. If the process died while
     * the user was in another app, the pending ad dies with it - resurrecting it
     * on the next cold start would show an ad to someone who has lost all
     * context for why it appeared, and a cold start already has the app-open ad.
     */
    var pending: Boolean by mutableStateOf(false)
        private set

    /** Output identity carried across an external Share/Gallery round trip. */
    private var pendingOutputToken: String? = null

    /** Process-local placement guard: one interstitial per finished output. */
    private var lastShownOutputToken: String? = null

    var shownThisSession: Int by mutableIntStateOf(0)
        private set

    /** Last verdict from [AdGate], kept purely so diagnostics can show it. */
    var lastVerdict: AdGate.Verdict by mutableStateOf(AdGate.Verdict.NOT_LOADED)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(AdPacing.FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Request a creative if one is not already in hand.
     *
     * Called when a compression starts, when the compression screen opens, and
     * after every dismissal. Cheap to call repeatedly - it no-ops when a
     * creative is loaded or a request is already in flight.
     */
    fun preload(context: Context) {
        if (!AdSlots.requestable) {
            // A consent refusal should not leave a stale
            // creative sitting in memory waiting for the window to lapse.
            discard()
            return
        }
        if (loading || ad != null) return

        // Do not request an impression the day has no room for.
        //
        // A capped request still costs a network round trip and then sits in
        // memory as a creative that will never be served. The panel would refuse
        // it anyway; knowing the answer locally means not asking. The counter
        // rolls over with the calendar day, and preload() is called often enough
        // (every compression screen) that the next day's first request needs no
        // special handling.
        if (!DailyImpressionPolicy.canShow(shownToday())) return

        val unitId = AdIds.interstitial ?: return

        loading = true
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
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
     * Mark that an ad is owed on the user's return.
     *
     * Always paired with [Context.suppressAppOpenOnReturn] at the call site -
     * the same external exits already suppress the app-open ad, because they are
     * where the user is deliberately stepping out. Without
     * that pairing the user would meet an app-open ad on the way back in and the
     * interstitial would then be refused by the shared interval, which reads to a
     * tester as "the deferred ad is broken".
     */
    fun markPending(context: Context, outputToken: String) {
        if (!AdSlots.requestable) return
        if (outputToken == lastShownOutputToken) {
            pending = false
            pendingOutputToken = null
            return
        }
        pending = true
        pendingOutputToken = outputToken
        context.suppressAppOpenOnReturn()
        preload(context)
    }

    fun clearPending() {
        pending = false
        pendingOutputToken = null
    }

    /**
     * Show immediately, for a plain in-app transition ("Compress another").
     *
     * @return true if a creative was actually presented.
     */
    fun showNow(activity: Activity, outputToken: String): Boolean {
        if (outputToken == lastShownOutputToken) return false
        val verdict = AdGate.evaluate(loaded = ad != null, shownToday = shownToday())
        lastVerdict = verdict
        if (verdict != AdGate.Verdict.ALLOWED) {
            preload(activity)
            return false
        }
        return present(activity, outputToken)
    }

    /**
     * Consume the deferred flag, if any, and show.
     *
     * Called from `MainActivity.onResume`, which is the one place that reliably
     * fires for every way back into the app: the share sheet returning, the
     * gallery or a video player being dismissed, the task switcher. The flag is
     * consumed whether or not the ad actually appears, so a blocked return does
     * not leave an ad owed indefinitely and fire it at some unrelated later
     * moment.
     */
    fun showPendingIfAny(activity: Activity): Boolean {
        if (!pending) return false
        pending = false
        val outputToken = pendingOutputToken
        pendingOutputToken = null
        if (outputToken == null || outputToken == lastShownOutputToken) return false

        val verdict = AdGate.evaluate(loaded = ad != null, shownToday = shownToday())
        lastVerdict = verdict
        if (verdict != AdGate.Verdict.ALLOWED) {
            preload(activity)
            return false
        }
        return present(activity, outputToken)
    }

    private fun present(activity: Activity, outputToken: String): Boolean {
        val creative = ad ?: return false

        creative.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Marked here, not at the decision, so an ad that failed to
                // present never consumes the next pacing window.
                AdPacing.markFullScreenShown()
                lastShownOutputToken = outputToken
                shownThisSession += 1
                recordShownToday()
            }

            override fun onAdDismissedFullScreenContent() {
                ad = null
                isLoaded = false
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                isLoaded = false
                preload(activity)
            }
        }

        ad = null
        isLoaded = false
        creative.show(activity)
        return true
    }

    private fun discard() {
        ad = null
        isLoaded = false
    }

    /* --------------------------------------------------------------------- */
    /* Display-only counters                                                  */
    /* --------------------------------------------------------------------- */

    /**
     * How many interstitials this user has seen today.
     *
     * This used to be read by the diagnostics sheet and by nothing else, and its
     * own comment said "the daily ceiling is the AdMob panel's job". That is no
     * longer true: the panel caps interstitials at three per user per day, and
     * since v0.9.16 the app enforces the same number through
     * [DailyImpressionPolicy] - both before requesting a creative and at the
     * moment of showing one.
     *
     * The day is a calendar day in the device's own time zone, which is the same
     * day the user experiences. It is deliberately NOT derived from
     * [AdPacing.now], whose monotonic clock is correct for measuring an interval
     * and meaningless for naming a date.
     */
    fun shownToday(): Int {
        val store = prefs ?: return 0
        if (store.getInt(KEY_DAY, -1) != today()) return 0
        return store.getInt(KEY_SHOWN_TODAY, 0)
    }

    private fun recordShownToday() {
        val store = prefs ?: return
        val day = today()
        val current = if (store.getInt(KEY_DAY, -1) == day) {
            store.getInt(KEY_SHOWN_TODAY, 0)
        } else {
            0
        }
        store.edit()
            .putInt(KEY_DAY, day)
            .putInt(KEY_SHOWN_TODAY, current + 1)
            .apply()
    }

    private fun today(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private const val KEY_DAY = "interstitial_day"
    private const val KEY_SHOWN_TODAY = "interstitial_shown_today"
}
