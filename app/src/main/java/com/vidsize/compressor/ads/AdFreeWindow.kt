package com.vidsize.compressor.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * The reward a user gets for watching a rewarded ad: a window during which
 * Vidsize shows no ads at all.
 *
 * ## Why this is a promise and not a setting
 *
 * Everything else about ad frequency lives in the AdMob panel (see [AdPacing]),
 * because frequency is a tempo knob and tempo knobs should not require a new
 * release. This one does not, because it is something the app told the user in
 * so many words: "watch a short video ad, get ten minutes with no ads". A
 * promise made in a string resource has to be kept by code that ships in the
 * same binary as that string.
 *
 * ## Why ten minutes and not thirty
 *
 * A Vidsize compression runs for two to five minutes and is followed by the
 * result screen. Ten minutes covers one full job plus the share/open that
 * follows it, which is exactly the unit of work the user was trying to protect.
 * Thirty minutes would cover most of a session, which sounds generous but is
 * not: it removes the reason to ever watch a second rewarded ad, and rewarded is
 * the highest-eCPM unit in the portfolio. Ten minutes is a real reward that
 * still leaves a reason to come back to the strip later in the day.
 *
 * ## Scope
 *
 * EVERY surface respects this window - banner, native, app open and
 * interstitial. A window that silenced only the full-screen formats would make
 * the app's own copy inaccurate, and inaccurate ad copy is a policy problem, not
 * just a UX one. The single place that enforces it is [AdSlots.requestable]; the
 * one deliberate exception is the rewarded unit itself, which must stay
 * loadable so the strip can offer an extension the moment the window closes.
 *
 * The expiry is stored, so it survives process death: a user who watched an ad
 * and then got a phone call does not lose the window they earned.
 */
object AdFreeWindow {

    /** The reward, in milliseconds. Must match `ad_free_reward_minutes`. */
    const val REWARD_DURATION_MILLIS: Long = 10L * 60L * 1000L

    /** The same number, for string formatting and for the regression gate. */
    const val REWARD_DURATION_MINUTES: Int = 10

    private var prefs: SharedPreferences? = null

    /**
     * When the current window ends, or 0.
     *
     * Compose state rather than a plain Long so that granting the reward makes
     * the banner disappear in the same frame, with no manual invalidation.
     */
    var expiresAtMillis: Long by mutableLongStateOf(0L)
        private set

    /**
     * Milliseconds left, recomputed by [refresh].
     *
     * The clock moving is not an event Compose can observe, so a single ticker
     * (in `VidsizeRoot`) calls [refresh] once a second while a window is open.
     * That is what makes the countdown tick and what brings the banner back at
     * the exact moment the window closes.
     */
    var remainingMillis: Long by mutableLongStateOf(0L)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences(AdPacing.FILE_NAME, Context.MODE_PRIVATE)
        prefs = store
        expiresAtMillis = store.getLong(KEY_EXPIRES_AT, 0L)
        refresh()
    }

    /** Starts (or restarts) the window. Called only from a verified reward. */
    fun grant(nowMillis: Long = System.currentTimeMillis()) {
        val expiry = nowMillis + REWARD_DURATION_MILLIS
        expiresAtMillis = expiry
        prefs?.edit()?.putLong(KEY_EXPIRES_AT, expiry)?.apply()
        refresh(nowMillis)
    }

    /**
     * Recomputes the countdown, and repairs a window the clock has stretched.
     *
     * QA finding: the expiry is a wall-clock instant, and the user owns the wall
     * clock. Moving the device clock backwards made `expiresAt - now` grow
     * without limit, so a ten-minute reward could be held open indefinitely and
     * every ad in the app stayed silenced.
     *
     * The window cannot legitimately have more time left than it was ever
     * granted, so more than [REWARD_DURATION_MILLIS] remaining is proof the
     * clock moved rather than proof of a longer reward. Closing it outright is
     * the right response: re-anchoring to now would hand out a fresh ten minutes
     * for free, which rewards the manipulation instead of undoing it.
     *
     * Wall clock is kept here on purpose, unlike [AdPacing]. This value must
     * survive a reboot - a user who earned ten quiet minutes and restarted their
     * phone should keep them - and uptime does not survive one. The clamp is
     * what makes that trade safe.
     */
    fun refresh(nowMillis: Long = System.currentTimeMillis()) {
        val remaining = expiresAtMillis - nowMillis
        if (remaining > REWARD_DURATION_MILLIS) {
            expiresAtMillis = 0L
            prefs?.edit()?.remove(KEY_EXPIRES_AT)?.apply()
            remainingMillis = 0L
            return
        }
        remainingMillis = remaining.coerceAtLeast(0L)
    }

    /**
     * The authoritative check, safe to call from outside composition.
     *
     * [remainingMillis] is only as fresh as the last [refresh], so anything that
     * decides whether to REQUEST or SHOW an ad asks this instead - managers run
     * on lifecycle callbacks, not on the UI ticker.
     */
    fun isActiveNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val remaining = expiresAtMillis - nowMillis
        // Same clamp as refresh(): a window with more time left than was ever
        // granted is a moved clock, not a reward. Checked here too because the
        // managers ask this from lifecycle callbacks with no ticker running.
        if (remaining > REWARD_DURATION_MILLIS) return false
        return remaining > 0L
    }

    fun remainingMillisNow(nowMillis: Long = System.currentTimeMillis()): Long {
        val remaining = expiresAtMillis - nowMillis
        if (remaining > REWARD_DURATION_MILLIS) return 0L
        return remaining.coerceAtLeast(0L)
    }

    /** `mm:ss` for the strip's countdown. */
    fun formatRemaining(millis: Long = remainingMillis): String {
        val totalSeconds = (millis + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    private const val KEY_EXPIRES_AT = "ad_free_expires_at"
}
