package com.vidsize.compressor.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.vidsize.compressor.BuildConfig

/**
 * Loads the single Native Advanced ad shown on the compression result sheet.
 *
 * Design rules this enforces, all of them AdMob policy requirements rather than
 * preferences:
 *
 *  - **One ad, loaded once.** No refresh loop. A result sheet is seen for a few
 *    seconds; refreshing there would inflate impressions without user value and
 *    is exactly the pattern that draws invalid-traffic attention.
 *  - **Consent first.** Nothing is requested until [ConsentManager.adsAllowed],
 *    which means UMP resolved *and* the Mobile Ads SDK finished initialising.
 *  - **No fill is a valid outcome.** On failure the caller renders nothing —
 *    never an empty grey box where an ad should be.
 *  - **The caller must call [NativeAd.destroy]** when the sheet goes away.
 *    That is done in the Compose wrapper's `onDispose`.
 */
object NativeAdLoader {

    /**
     * Google's official Native Advanced test unit. Debug builds always use it;
     * a real unit id is only ever compiled into a release build.
     *
     * The production id comes from `VIDSIZE_NATIVE_RESULT_AD_UNIT_ID` in
     * `local.properties` / CI secrets and is surfaced through BuildConfig, so a
     * developer's device can never click a live ad.
     */
    private const val TEST_NATIVE_UNIT = "ca-app-pub-3940256099942544/2247696110"

    private val unitId: String
        get() = if (BuildConfig.DEBUG || BuildConfig.NATIVE_RESULT_AD_UNIT_ID.isBlank()) {
            TEST_NATIVE_UNIT
        } else {
            BuildConfig.NATIVE_RESULT_AD_UNIT_ID
        }

    /**
     * @param onLoaded receives the ad on success. Called on the main thread.
     * @param onFailed called when there is no fill or the request errors, so
     *        the caller can keep the result sheet at its normal height.
     */
    fun load(
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit,
    ) {
        if (!ConsentManager.adsAllowed) {
            onFailed()
            return
        }

        val options = NativeAdOptions.Builder()
            // Landscape media reads as a premium promotional card and keeps the
            // sheet's height predictable.
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
            // AdChoices must stay visible; the layout leaves this corner empty.
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .setRequestMultipleImages(false)
            .build()

        val loader = AdLoader.Builder(context.applicationContext, unitId)
            .forNativeAd { nativeAd -> onLoaded(nativeAd) }
            .withNativeAdOptions(options)
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onFailed()
                    }
                },
            )
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }
}
