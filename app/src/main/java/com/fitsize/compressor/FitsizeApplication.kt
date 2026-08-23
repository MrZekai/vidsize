package com.fitsize.compressor

import android.app.Application
import com.google.android.gms.ads.MobileAds

class FitsizeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Test SDK initialization. Consent/UMP is deliberately added before real ad units.
        MobileAds.initialize(this) {}
    }
}
