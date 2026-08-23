package com.fitsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * App Open ad policy — the decision layer, wired and testable, with the SDK call
 * deliberately not yet connected.
 *
 * Why it exists in V1 with [ENABLED] set to `false`:
 *
 * App Open is the highest-risk placement in this product. AdMob's own guidance
 * is that the format suits apps opened more than once every four hours, and a
 * video compressor is not that app. Shipped carelessly it produces exactly the
 * "I opened the app and got a full-screen ad" reviews that sit under every
 * competitor in this category. So the *rules* ship now — first-session
 * suppression, an install grace period, a hard cooldown, and mutual exclusion
 * with the result interstitial — and the ad itself is switched on only after
 * launch metrics show the retention headroom for it.
 *
 * To enable: flip [ENABLED], load an `AppOpenAd` in `FitsizeApplication`, and
 * call [shouldShow] from an `ON_START` lifecycle observer before showing it.
 * No other code has to move.
 */
class AppOpenAdPolicy(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * True when an App Open ad would be acceptable right now.
     *
     * @param nowMillis current wall clock, injected so the rules are unit-testable.
     */
    fun shouldShow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!ENABLED) return false

        val firstLaunch = firstLaunchMillis(nowMillis)
        if (nowMillis - firstLaunch < INSTALL_GRACE_MILLIS) return false
        if (sessionCount() < MIN_SESSIONS_BEFORE_FIRST_AD) return false

        val lastFullScreen = prefs.getLong(KEY_LAST_FULL_SCREEN, 0L)
        if (nowMillis - lastFullScreen < COOLDOWN_MILLIS) return false

        return true
    }

    /** Call when any full-screen ad is displayed, App Open or interstitial. */
    fun markFullScreenShown(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_FULL_SCREEN, nowMillis).apply()
    }

    /** Call once per cold start. */
    fun registerSession(nowMillis: Long = System.currentTimeMillis()) {
        firstLaunchMillis(nowMillis)
        prefs.edit().putInt(KEY_SESSIONS, sessionCount() + 1).apply()
    }

    fun sessionCount(): Int = prefs.getInt(KEY_SESSIONS, 0)

    private fun firstLaunchMillis(nowMillis: Long): Long {
        val stored = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
        if (stored > 0L) return stored
        prefs.edit().putLong(KEY_FIRST_LAUNCH, nowMillis).apply()
        return nowMillis
    }

    companion object {
        /** V1 ships with App Open switched off. See the class comment. */
        const val ENABLED = false

        /** Google's demo App Open unit, for when this is switched on. */
        const val TEST_APP_OPEN_UNIT = "ca-app-pub-3940256099942544/9257395921"

        /** No full-screen ad in the first three days after install. */
        private const val INSTALL_GRACE_MILLIS = 3L * 24L * 60L * 60L * 1000L

        /** No full-screen ad until the user has come back at least three times. */
        private const val MIN_SESSIONS_BEFORE_FIRST_AD = 3

        /** At most one full-screen ad every six hours, across all formats. */
        private const val COOLDOWN_MILLIS = 6L * 60L * 60L * 1000L

        private const val FILE_NAME = "fitsize_ads"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_LAST_FULL_SCREEN = "last_full_screen"
    }
}
