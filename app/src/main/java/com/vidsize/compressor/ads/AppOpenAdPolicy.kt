package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * Conservative frequency policy for App Open ads.
 *
 * The placement is enabled, but never on the first three days after first use,
 * never before the third foreground session, and never more than once every six
 * hours. The ad manager also suppresses App Open while a compression/result is
 * active and when Vidsize is opened from another app's share sheet.
 */
class AppOpenAdPolicy(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun shouldShow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!ENABLED) return false

        val firstLaunch = firstLaunchMillis(nowMillis)
        if (nowMillis - firstLaunch < INSTALL_GRACE_MILLIS) return false
        if (sessionCount() < MIN_SESSIONS_BEFORE_FIRST_AD) return false

        val lastFullScreen = prefs.getLong(KEY_LAST_FULL_SCREEN, 0L)
        if (nowMillis - lastFullScreen < COOLDOWN_MILLIS) return false
        return true
    }

    fun markFullScreenShown(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_FULL_SCREEN, nowMillis).apply()
    }

    /** Count real process foreground sessions, not only process creation. */
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
        const val ENABLED = true
        const val TEST_APP_OPEN_UNIT = "ca-app-pub-3940256099942544/9257395921"

        private const val INSTALL_GRACE_MILLIS = 3L * 24L * 60L * 60L * 1000L
        private const val MIN_SESSIONS_BEFORE_FIRST_AD = 3
        private const val COOLDOWN_MILLIS = 6L * 60L * 60L * 1000L

        private const val FILE_NAME = "vidsize_ads"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_LAST_FULL_SCREEN = "last_full_screen"
    }
}
