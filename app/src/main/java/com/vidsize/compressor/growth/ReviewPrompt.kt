package com.vidsize.compressor.growth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * The Play in-app review request, and the one moment worth spending it on.
 *
 * ## Why this exists at all
 *
 * After the store listing itself, rating volume and average are the largest
 * levers on Play ranking, and a utility with a handful of ratings is invisible
 * next to competitors with thousands. Vidsize shipped without ever asking.
 *
 * ## The moment
 *
 * A compressor has an unusually good one. The user has just watched a job they
 * waited minutes for finish, and is looking at a concrete number - "25.8 MB to
 * 21.9 MB, 15% smaller". That is the peak of the experience, and it is measured
 * rather than claimed, which is exactly when a person feels like saying
 * something nice.
 *
 * So the prompt fires on the RESULT screen, not on exit. Exit is where the
 * interstitial lives, and two full-screen surfaces competing for the same
 * instant would produce a race that Google's quota would silently resolve by
 * dropping the review - the thing this class exists to deliver.
 *
 * ## The thresholds, and why they are not "silent quotas"
 *
 * [MILESTONES] gates a *review prompt*, not an ad. The regression gate that
 * bans usage counters in `ads/` is about conditions that make ads unreachable
 * and undiagnosable; a review asked on the 3rd, 10th and 30th success is the
 * opposite - it is asked rarely on purpose, because Google's own quota drops
 * anything more frequent and because a user asked too early has nothing to rate
 * yet.
 *
 * Three is the first milestone rather than one because a single success is not
 * yet a habit, and because the first run is where a bad encode is most likely
 * to have soured the user.
 *
 * ## It can silently do nothing, and that is fine
 *
 * Google's quota is opaque and undocumented by design. `launchReviewFlow` may
 * complete without ever showing anything, and the API deliberately gives no way
 * to tell. So this class never blocks, never retries, and never reports failure
 * to the user. The milestone is consumed either way - asking again on the next
 * compression because the first attempt was quietly dropped is how an app ends
 * up nagging.
 */
object ReviewPrompt {

    /** Completed compressions at which a review is worth asking for. */
    private val MILESTONES = setOf(3, 10, 30)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * @param completedCompressions the running total, from AdDiagnostics.
     * @return true when this call consumed a milestone and started the flow.
     */
    fun maybeAsk(activity: Activity, completedCompressions: Int): Boolean {
        val store = prefs ?: return false
        if (completedCompressions !in MILESTONES) return false
        if (store.getInt(KEY_LAST_ASKED_AT, 0) >= completedCompressions) return false

        // Consumed before the flow starts, not after. The API cannot tell us
        // whether anything was shown, so an attempt has to count as the attempt.
        store.edit().putInt(KEY_LAST_ASKED_AT, completedCompressions).apply()

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            runCatching { manager.launchReviewFlow(activity, task.result) }
        }
        return true
    }

    private const val FILE_NAME = "vidsize_growth"
    private const val KEY_LAST_ASKED_AT = "review_last_asked_at"
}
