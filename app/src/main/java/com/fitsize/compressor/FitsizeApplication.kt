package com.fitsize.compressor

import android.app.Application
import com.fitsize.compressor.ads.AppOpenAdPolicy
import com.google.android.gms.ads.MobileAds

class FitsizeApplication : Application() {

    /** Ad frequency rules. See [AppOpenAdPolicy] for why App Open is off in V1. */
    lateinit var appOpenAdPolicy: AppOpenAdPolicy
        private set

    override fun onCreate() {
        super.onCreate()

        appOpenAdPolicy = AppOpenAdPolicy(this).also { it.registerSession() }

        // Test units only during development. Consent/UMP is deliberately added
        // before the first real ad unit ships, not after.
        MobileAds.initialize(this) {}
    }
}
