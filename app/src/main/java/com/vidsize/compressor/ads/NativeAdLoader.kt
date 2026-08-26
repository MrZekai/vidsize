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
        if (!ConsentManager.adsAllowed) {
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
