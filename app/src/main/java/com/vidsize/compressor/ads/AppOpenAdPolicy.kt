package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences

/**
 * App-open eligibility, and a short record of what was deleted from it.
 *
 * ## What this class used to do
 *
 * Until v0.8.9 it required FOUR conditions to pass simultaneously before an
 * app-open ad could appear:
 *
 *  1. a three-day grace period after install,
 *  2. at least three recorded sessions,
 *  3. a six-hour cooldown since the last full-screen ad,
 *  4. (in [AppOpenAdManager]) a four-hour creative expiry.
 *
 * On a real device those did not add up to "conservative", they added up to
 * "never". A closed-test user who installs the app, uses it for an afternoon
 * and reports back has satisfied none of the first three. And because each
 * condition returned false silently, the outcome was indistinguishable from no
 * fill, from a consent refusal, and from a build with ads switched off. That
 * ambiguity, not the conservatism, is what cost the time.
 *
 * ## What is left
 *
 * Conditions 1 and 2 are gone outright. There is no version of "this user has
 * not earned an ad yet" that belongs in a binary - it makes the app untestable
 * and the AdMob panel already caps app-open ads at four per day per user, which
 * is the same protection expressed somewhere it can be retuned without a
 * release.
 *
 * Condition 3 survives only as the shared three-minute rule in [AdPacing], applied
 * identically to every full-screen format instead of privately to this one.
 *
 * Condition 4 was never a pacing rule and stays in [AppOpenAdManager] where it
 * belongs. Treating a creative's four-hour freshness limit as the interval
 * between two ads is the specific mistake that silences an app-open ad for an
 * entire afternoon while every counter looks healthy.
 *
 * The class itself remains because the session count is genuinely useful - in
 * the diagnostics sheet, where a number a tester can read is worth having, and
 * where it gates nothing at all.
 */
class AppOpenAdPolicy(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(AdPacing.FILE_NAME, Context.MODE_PRIVATE)

    /**
     * The one question the manager asks.
     *
     * `requireIdleJob = true` is the app-open ad's distinguishing rule: a user
     * foregrounding Vidsize while a compression is running, or just after one
     * finished, is coming back for their video. A full-screen ad in front of the
     * result is the placement this whole model exists to prevent.
     */
    fun shouldShow(nowMillis: Long = AdPacing.now()): Boolean =
        AdGate.allows(loaded = true, requireIdleJob = true, nowMillis = nowMillis)

    /** Kept as the manager's callback name; the clock itself is shared now. */
    fun markFullScreenShown(nowMillis: Long = AdPacing.now()) {
        AdPacing.markFullScreenShown(nowMillis)
    }

    fun registerSession(nowMillis: Long = System.currentTimeMillis()) {
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

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_LAST_SESSION = "last_session"
        private const val SESSION_GAP_MILLIS = 30L * 60L * 1000L
    }
}
