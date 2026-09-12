package com.vidsize.compressor.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object NativeAdLoader {
    fun load(
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit,
    ) {
        // QA v0.8.7 BUG-02: a build with no real identifiers must not reach the
        // AdMob SDK at all. The sample native unit is what rendered the
        // "native ad validator" debug popup over the result screen.
        if (!AdSlots.requestable) {
            onFailed()
            return
        }

        val unitId = AdIds.nativeResult
        if (unitId.isNullOrBlank()) {
            onFailed()
            return
        }

        val options = NativeAdOptions.Builder()
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
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
