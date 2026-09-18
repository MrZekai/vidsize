package com.vidsize.compressor.ads

/**
 * How many interstitials one user may see in one day.
 *
 * ## Why the app enforces a cap the AdMob panel already sets
 *
 * The panel's frequency cap was revised to **3 impressions per user per day**,
 * and the honest reading of that is a product decision, not a server setting.
 * Leaving it only in the panel has two costs.
 *
 * The first is that the app stops telling the truth. Until now
 * `InterstitialAds.shownToday()` existed solely to be printed on the
 * diagnostics screen - its own comment said "the daily ceiling is the AdMob
 * panel's job". So a tester who had already seen three ads would ask for a
 * fourth, watch nothing happen, and have no way to tell a cap from no fill from
 * a pacing block. That ambiguity is precisely what [AdGate]'s enum was built to
 * end, and a limit enforced somewhere the app cannot see is a hole in it.
 *
 * The second is wasted work. A capped request still costs a network round trip
 * and a loaded creative held in memory for an ad that will not be served.
 * Knowing the answer locally means not asking.
 *
 * This object is deliberately pure - no Android, no clock, no storage - so the
 * boundary can be tested exhaustively on a JVM. Everything stateful lives in
 * [InterstitialAds], which owns the counter, and in [AdPacing], which owns the
 * interval.
 *
 * ## How it fits with the interval
 *
 * The two limits are independent and both must pass. [FullScreenPacingPolicy]
 * spaces ads three minutes apart so two never arrive back to back; this caps the
 * number per calendar day. Three per day at three minutes apart is not a
 * conflict - a user can reach the cap in ten minutes, and then sees no more
 * until the day rolls over.
 *
 * The cap covers interstitials only. App-open is a separate format with its own
 * panel setting, and [AdGate] reflects that by taking the count as a parameter
 * rather than reading one itself.
 */
internal object DailyImpressionPolicy {

    /**
     * Impressions allowed per user per calendar day.
     *
     * Mirrors the AdMob panel. If the panel value changes, change this one in
     * the same breath: the app is the stricter of the two by design, so a code
     * value higher than the panel's simply hands the decision back to the
     * server, and a lower one silently under-serves.
     */
    const val MAX_PER_DAY: Int = 3

    /**
     * True when another impression is allowed.
     *
     * A negative count is treated as zero rather than rejected. The counter it
     * comes from is preference-backed and can be absent on a first run or after
     * a clear, and refusing to show ads because a stored integer looked odd is
     * the wrong failure direction for a limit whose purpose is only to stop the
     * fourth ad.
     */
    fun canShow(shownToday: Int, maxPerDay: Int = MAX_PER_DAY): Boolean =
        shownToday.coerceAtLeast(0) < maxPerDay

    /** How many are left today; never negative. */
    fun remainingToday(shownToday: Int, maxPerDay: Int = MAX_PER_DAY): Int =
        (maxPerDay - shownToday.coerceAtLeast(0)).coerceAtLeast(0)
}
