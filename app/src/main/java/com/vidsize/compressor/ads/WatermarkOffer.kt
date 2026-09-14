package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One rewarded impression buys exactly one watermark-free export.
 *
 * The entitlement is persisted because the reward callback can arrive before
 * a multi-minute encode. A process death, crash or activity recreation after
 * the user earned the reward must not charge them a second ad. The grant does
 * not accumulate, expires after 24 hours, and is consumed only after a clean
 * export actually succeeds.
 */
object WatermarkOffer {

    private const val PREF_FILE = "vidsize_reward_state"
    private const val KEY_GRANTED_AT = "watermark_granted_at"
    private const val GRANT_TTL_MILLIS = 24L * 60L * 60L * 1000L

    private lateinit var prefs: SharedPreferences

    var granted: Boolean by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val grantedAt = prefs.getLong(KEY_GRANTED_AT, 0L)
        val age = System.currentTimeMillis() - grantedAt
        granted = grantedAt > 0L && age in 0L..GRANT_TTL_MILLIS
        if (!granted && grantedAt != 0L) {
            prefs.edit().remove(KEY_GRANTED_AT).apply()
        }
    }

    /** Called only from Google Mobile Ads' earned-reward callback. */
    fun grant() {
        granted = true
        if (::prefs.isInitialized) {
            prefs.edit().putLong(KEY_GRANTED_AT, System.currentTimeMillis()).apply()
        }
    }

    /** Consumed only by a watermark-free export that actually completed. */
    fun consume(): Boolean {
        if (!granted) return false
        granted = false
        if (::prefs.isInitialized) prefs.edit().remove(KEY_GRANTED_AT).apply()
        return true
    }

    fun clear() {
        granted = false
        if (::prefs.isInitialized) prefs.edit().remove(KEY_GRANTED_AT).apply()
    }
}
