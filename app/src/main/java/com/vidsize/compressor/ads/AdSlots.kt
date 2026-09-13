package com.vidsize.compressor.ads

import com.vidsize.compressor.BuildConfig

/**
 * The one switch every ad surface reads before it emits a single pixel or makes
 * a single request.
 *
 * ## Why this exists (QA v0.8.7 BUG-01, BUG-02, BUG-08 and two UX findings)
 *
 * v0.8.7 had the ad decision spread across four composables, a loader, an
 * App Open manager and a consent object. Each of them independently decided
 * whether to render, and the result was that a build with no usable ad
 * identifiers still reserved space, still requested Google's sample units, and
 * still drew a debug validator popup over the result screen's only action.
 *
 * With ads disabled every consequence of those bugs disappears structurally
 * rather than by careful placement:
 *
 *  - BUG-01: no ad is requested, so no "Test Ad" creative can render.
 *  - BUG-02: no native ad is loaded, so AdMob's native-ad-validator debug popup
 *    cannot be drawn over SHARE VIDEO.
 *  - BUG-08: no full-size native creative is laid out at the coordinates the
 *    progress dialog's Cancel button occupied, so a late Cancel tap can no
 *    longer open the Play Store.
 *  - "Empty ad slots reserve visible space": an absent ad now costs 0dp.
 *
 * ## v0.9.0: the rewarded ad-free window folds in here
 *
 * The rewarded reward is "ten minutes with no ads", and the word that makes
 * that expensive to implement is *no*. Before this version the banner read
 * `AdSlots.enabled` plus `ConsentManager` directly while the App Open manager
 * and the native loader read [requestable] - three surfaces, two different
 * predicates. A reward window bolted onto that arrangement would have silenced
 * the full-screen formats and left the banner running, which would make the
 * app's own copy false.
 *
 * So every surface now goes through [requestable], and the window is checked in
 * exactly one place: here.
 */
object AdSlots {

    /**
     * True only when this build is allowed to run the ads SDK.
     *
     * Set from `ENABLE_ADS`, which Gradle derives from whether a complete set of
     * the developer's own AdMob identifiers was supplied at build time.
     */
    val enabled: Boolean get() = BuildConfig.ENABLE_ADS

    /** The build allows ads, consent permits them, and the SDK has started. */
    val permitted: Boolean get() = enabled && ConsentManager.adsAllowed

    /**
     * True when an ordinary ad may actually be requested or shown right now.
     *
     * Every format except the rewarded unit itself asks this: banner, native,
     * app open, interstitial.
     *
     * ## Why it reads the window twice
     *
     * [AdFreeWindow.remainingMillis] is Compose state maintained by a one-second
     * ticker in `VidsizeRoot`. Touching it here is what subscribes every ad
     * composable to the window, so a granted reward removes the banner in the
     * same frame and its expiry brings the banner back without any surface
     * wiring itself up to anything.
     *
     * But that value is only as fresh as the last tick, and the ticker stops
     * when the UI leaves the composition. Deciding with it alone would mean a
     * user who backgrounds Vidsize mid-window comes back to a value frozen at,
     * say, four minutes remaining - and `AppOpenAdManager`, which runs from a
     * process lifecycle callback with no composition at all, would suppress the
     * app-open ad forever on the strength of a window that expired hours ago.
     *
     * So the stale value drives observation and [AdFreeWindow.isActiveNow]
     * decides. Reading Compose state and then ignoring it looks redundant; it is
     * the difference between a correct answer and a live one.
     */
    val requestable: Boolean
        get() {
            @Suppress("UNUSED_VARIABLE")
            val observed = AdFreeWindow.remainingMillis
            return permitted && !AdFreeWindow.isActiveNow()
        }

    /**
     * The rewarded unit is the one format the ad-free window does not silence.
     *
     * It is user-initiated, it is the thing the user traded for the window in
     * the first place, and keeping it loadable is what lets the strip offer an
     * extension the moment the window runs out. AdMob's rewarded policy is also
     * explicit that a rewarded ad the user opts into is exempt from the
     * unexpected-full-screen rules the other formats live under.
     */
    val rewardedRequestable: Boolean get() = permitted

    /**
     * Whether a banner container should emit its chrome - the divider above the
     * strip and the dead buffer around it.
     *
     * This is [requestable] by another name, and that is the whole point. Hosts
     * used to spell the condition out themselves (`AdSlots.enabled` on Home,
     * `AdSlots.enabled && !adsBlocked` in ProcessingOverlay), so a consent
     * refusal - and now an ad-free window - left a hairline with nothing under
     * it and a 12dp gap at the bottom of the screen. The divider exists to
     * separate a creative from the content above it; with no creative it is a
     * decoration with no meaning.
     */
    val bannerVisible: Boolean get() = requestable
}
