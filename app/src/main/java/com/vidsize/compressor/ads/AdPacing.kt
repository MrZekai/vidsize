package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/**
 * The one and only pacing rule that lives in Vidsize's code: two full-screen ads
 * never arrive within sixty seconds of each other.
 *
 * ## What this replaces
 *
 * Up to v0.8.9 the app-open ad had to satisfy four conditions at once - a
 * three-day install grace period, a minimum of three sessions, a six-hour
 * cooldown, and a four-hour creative expiry. Each of them failed silently, and
 * together they meant a closed-test user essentially never saw an app-open ad.
 * Worse, when one did not fire there was no way to find out which condition had
 * declined; the failure looked identical to no fill.
 *
 * Three of those four are gone. The rule for keeping one is simple:
 *
 *  - A condition that is a PROMISE to the user stays in code. There are exactly
 *    two: this 60-second gap (do not stack full-screen ads), and the rewarded
 *    ad-free window ([AdFreeWindow]).
 *  - A condition that is only a TEMPO preference moves to the AdMob panel, where
 *    it can be retuned without shipping a release. Interstitial: 3 per hour per
 *    user. App open: 4 per day per user. See `docs/ADS.md`.
 *
 * The fourth condition, the four-hour app-open creative expiry in
 * [AppOpenAdManager], is not a pacing rule and was never one - it is Google's
 * documented staleness limit for a loaded creative. It stays, because confusing
 * creative freshness with the interval between ads is exactly what silently
 * kills an app-open ad for hours at a time.
 *
 * ## Shared across formats on purpose
 *
 * App open and interstitial write to and read from the SAME timestamp. An
 * interstitial shown on return from the share sheet must suppress the app-open
 * ad that a cold start ninety seconds later would otherwise fire, and vice
 * versa. Two separate counters would let the user meet two full-screen ads back
 * to back while each format believed it was behaving.
 */
object AdPacing {

    /** The promise: no two full-screen ads inside this window. */
    const val FULL_SCREEN_GAP_MILLIS: Long = 60L * 1000L

    internal const val FILE_NAME = "vidsize_ads"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Called from `onAdShowedFullScreenContent` for every full-screen format.
     *
     * Deliberately not called at request time or at "we decided to show" time:
     * an ad that failed to present must not consume the next minute of
     * eligibility.
     */
    /** Monotonic, user-proof, and the only clock this file reads. */
    fun now(): Long = SystemClock.elapsedRealtime()

    fun markFullScreenShown(nowMillis: Long = now()) {
        prefs?.edit()?.putLong(KEY_LAST_FULL_SCREEN, nowMillis)?.apply()
    }

    fun lastFullScreenMillis(): Long = prefs?.getLong(KEY_LAST_FULL_SCREEN, 0L) ?: 0L

    fun millisSinceLastFullScreen(nowMillis: Long = now()): Long {
        val last = lastFullScreenMillis()
        if (last <= 0L) return Long.MAX_VALUE
        // A stored value ahead of the current uptime is from a previous boot.
        // Treat it as "nothing shown this boot" rather than as a future event.
        if (last > nowMillis) return Long.MAX_VALUE
        return nowMillis - last
    }

    fun canShowFullScreen(nowMillis: Long = now()): Boolean =
        millisSinceLastFullScreen(nowMillis) >= FULL_SCREEN_GAP_MILLIS

    /** Seconds still to wait, for the diagnostics screen's verdict line. */
    fun secondsUntilAllowed(nowMillis: Long = now()): Long {
        val elapsed = millisSinceLastFullScreen(nowMillis)
        if (elapsed >= FULL_SCREEN_GAP_MILLIS) return 0L
        return (FULL_SCREEN_GAP_MILLIS - elapsed + 999L) / 1000L
    }

    private const val KEY_LAST_FULL_SCREEN = "last_full_screen"
}
