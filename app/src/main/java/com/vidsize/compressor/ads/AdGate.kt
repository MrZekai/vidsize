package com.vidsize.compressor.ads

import com.vidsize.compressor.media.CompressionJobState

/**
 * The single decision point for "may a full-screen ad appear right now?", and
 * the reason it answers with an enum rather than a Boolean.
 *
 * ## Why an enum
 *
 * Ad logic is made of conditions that decline silently. When an ad does not
 * appear, "it did not appear" is indistinguishable from no fill, from a pacing
 * block, from a consent refusal and from a build with ads switched off - and
 * every one of those has a different fix. v0.8.7 lost weeks to exactly this.
 *
 * So the gate returns *which* condition declined, [AdDiagnosticsSheet] prints it
 * as one sentence in plain language, and a tester never has to guess. Nothing
 * here is more clever than the old code; it is the same conditions, in one
 * place, that say their name out loud.
 *
 * ## The two Vidsize-specific rules
 *
 * This app is not the PDF reader this ad model was first written for, and two
 * differences matter enough to be encoded here rather than left to call sites.
 *
 * **A job runs for minutes, not milliseconds.** A Vidsize compression is two to
 * five minutes of foreground service work, and the user frequently leaves the
 * app while it runs. "Show an ad when the work finishes" - the obvious reading
 * of the model - would fire a full-screen ad while Vidsize is in the background
 * or while the progress overlay is still up. The first is a Play policy
 * violation outright; the second is the placement AdMob's own documentation
 * names. [RUNNING_JOB] blocks both, and the trigger moved from "the job
 * finished" to "the user came back to a finished job" ([InterstitialAds]).
 *
 * **The day has an allowance.** The AdMob panel caps interstitials at three per
 * user per day, and [DailyImpressionPolicy] mirrors that here so the app knows
 * the answer without asking the network - and, more importantly, so the gate can
 * say [Verdict.DAILY_CAP_REACHED] out loud instead of declining behind a pacing
 * message that clears in three minutes and means nothing. The count is passed in
 * rather than read here, because the cap belongs to the interstitial format and
 * not to app-open, which has its own panel setting.
 *
 * **The failure path is not a transition.** A cancelled or failed compression
 * leaves the user with nothing after minutes of waiting. There is a real
 * impression to be had there and the app declines to take it: no show call
 * exists on that path, and [CompressionJobState.Status.Failed] is not Idle, so
 * even a mistaken one would be refused here.
 */
object AdGate {

    /** Why a full-screen ad is, or is not, allowed at this instant. */
    enum class Verdict {
        /** Nothing is in the way. */
        ALLOWED,

        /** This build has no real AdMob identifiers; the SDK never started. */
        ADS_DISABLED,

        /** UMP has not resolved yet, or the user refused. */
        NO_CONSENT,


        /** Inside the shared full-screen ad interval. */
        PACING,

        /**
         * This user has already seen the day's allowance of interstitials.
         *
         * Distinct from [PACING] because the wait is different in kind: pacing
         * clears in minutes, this clears when the calendar day does. A tester
         * told "pacing" would sit and wait for an ad that is not coming until
         * tomorrow, which is the ambiguity this whole enum exists to remove.
         */
        DAILY_CAP_REACHED,

        /** A compression is running; nothing ever covers a job in progress. */
        RUNNING_JOB,

        /**
         * A compression has just finished and the user has not been shown the
         * result yet. Blocks the app-open ad only - see [requireIdleJob].
         */
        RESULT_WAITING,

        /** Everything permits it, but no creative is loaded yet. */
        NOT_LOADED,
    }

    /**
     * @param loaded whether the calling format actually has a creative in hand.
     *   Passed in rather than read here so one gate serves the interstitial and
     *   the app-open manager without knowing about either.
     * @param requireIdleJob how strictly to read the job state, which is the one
     *   place the two full-screen formats legitimately disagree.
     *
     *   The **app-open ad** passes `true`. A user who backgrounded the app
     *   during a five-minute compression and comes back is coming back *for the
     *   result*; putting a full-screen ad between them and the video they just
     *   waited for is the exact "ad in front of the work" pattern, and it is the
     *   loudest one-star complaint in this category.
     *
     *   The **interstitial** passes `false`. Its whole job is the deferred path:
     *   the user tapped Share or Open on the result screen, went to WhatsApp or
     *   a player, and is now returning with that task complete. The job state is
     *   still `Done` at that moment - it stays `Done` for as long as the result
     *   screen is up - so requiring Idle here would defer the ad forever and
     *   quietly delete the format's entire revenue. This is the trap that eats
     *   the deferred-interstitial idea in practice.
     */
    fun evaluate(
        loaded: Boolean,
        requireIdleJob: Boolean = false,
        nowMillis: Long = AdPacing.now(),
        shownToday: Int? = null,
    ): Verdict {
        val job = CompressionJobState.status
        return when {
            !AdSlots.enabled -> Verdict.ADS_DISABLED
            !ConsentManager.adsAllowed -> Verdict.NO_CONSENT
            job is CompressionJobState.Status.Running -> Verdict.RUNNING_JOB
            requireIdleJob && job !is CompressionJobState.Status.Idle -> Verdict.RESULT_WAITING
            // Before pacing on purpose. Both would decline, but a user who has
            // spent the day's allowance should be told that, not handed a
            // three-minute countdown that expires into another refusal.
            shownToday != null && !DailyImpressionPolicy.canShow(shownToday) ->
                Verdict.DAILY_CAP_REACHED
            !AdPacing.canShowFullScreen(nowMillis) -> Verdict.PACING
            !loaded -> Verdict.NOT_LOADED
            else -> Verdict.ALLOWED
        }
    }

    fun allows(
        loaded: Boolean,
        requireIdleJob: Boolean = false,
        nowMillis: Long = AdPacing.now(),
        shownToday: Int? = null,
    ): Boolean =
        evaluate(loaded, requireIdleJob, nowMillis, shownToday) == Verdict.ALLOWED

    /**
     * The gate as the diagnostics screen wants it: "would an interstitial be
     * possible if one were loaded?" Separates a pacing problem from a fill
     * problem, which are the two things a tester actually needs told apart.
     */
    fun evaluateIgnoringFill(
        nowMillis: Long = AdPacing.now(),
        shownToday: Int? = null,
    ): Verdict = evaluate(
        loaded = true,
        requireIdleJob = false,
        nowMillis = nowMillis,
        shownToday = shownToday,
    )
}
