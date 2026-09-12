package com.vidsize.compressor.ads

import com.vidsize.compressor.BuildConfig

/**
 * One switch every ad surface reads before it emits a single pixel.
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
 *  - "Ads inside the modal progress dialog": nothing actionable is rendered
 *    beside Cancel.
 */
object AdSlots {

    /**
     * True only when this build is allowed to run the ads SDK.
     *
     * Set from `ENABLE_ADS`, which Gradle derives from whether a complete set of
     * the developer's own AdMob identifiers was supplied at build time. The
     * closed-test and Play upload variants are hard-wired to false.
     */
    val enabled: Boolean get() = BuildConfig.ENABLE_ADS

    /**
     * True when an ad may actually be requested right now: the build allows it,
     * consent permits it and the SDK has finished initialising.
     */
    val requestable: Boolean get() = enabled && ConsentManager.adsAllowed
}
