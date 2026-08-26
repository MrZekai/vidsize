package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences

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

    fun registerSession(nowMillis: Long = System.currentTimeMillis()) {
        firstLaunchMillis(nowMillis)
        val previous = prefs.getLong(KEY_LAST_SESSION, 0L)
        if (previous > 0L && nowMillis - previous < SESSION_GAP_MILLIS) {
            prefs.edit().putLong(KEY_LAST_SESSION, nowMillis).apply()
            return
        }
        prefs.edit()
            .putInt(KEY_SESSIONS, sessionCount() + 1)
            .putLong(KEY_LAST_SESSION, nowMillis)
            .apply()
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
        private const val INSTALL_GRACE_MILLIS = 3L * 24L * 60L * 60L * 1000L
        private const val MIN_SESSIONS_BEFORE_FIRST_AD = 3
        private const val COOLDOWN_MILLIS = 6L * 60L * 60L * 1000L
        private const val FILE_NAME = "vidsize_ads"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_LAST_FULL_SCREEN = "last_full_screen"
        private const val KEY_LAST_SESSION = "last_session"
        private const val SESSION_GAP_MILLIS = 30L * 60L * 1000L
    }
}
