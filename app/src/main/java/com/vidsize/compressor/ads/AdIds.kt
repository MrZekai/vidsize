package com.vidsize.compressor.ads

import com.vidsize.compressor.BuildConfig

object AdIds {
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

    val homeBanner: String?
        get() = resolve(TEST_BANNER, BuildConfig.HOME_BANNER_AD_UNIT_ID)

    val compressionBanner: String?
        get() = resolve(TEST_BANNER, BuildConfig.COMPRESSION_BANNER_AD_UNIT_ID)

    val nativeResult: String?
        get() = resolve(TEST_NATIVE, BuildConfig.NATIVE_RESULT_AD_UNIT_ID)

    val appOpen: String?
        get() = resolve(TEST_APP_OPEN, BuildConfig.APP_OPEN_AD_UNIT_ID)

    private fun resolve(testId: String, productionId: String): String? =
        if (BuildConfig.USE_TEST_ADS) {
            testId
        } else {
            productionId.trim().takeIf { it.isNotEmpty() }
        }
}
