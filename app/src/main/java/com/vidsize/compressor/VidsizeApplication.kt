package com.vidsize.compressor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vidsize.compressor.ads.AdDiagnostics
import com.vidsize.compressor.ads.AdPacing
import com.vidsize.compressor.ads.AppOpenAdManager
import com.vidsize.compressor.ads.AppOpenAdPolicy
import com.vidsize.compressor.ads.InterstitialAds
import com.vidsize.compressor.ads.WatermarkOffer
import com.vidsize.compressor.growth.ReviewPrompt
import com.vidsize.compressor.media.CompressionEngine

class VidsizeApplication : Application(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    lateinit var appOpenAdPolicy: AppOpenAdPolicy
        private set

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()

        // Preference-backed ad state, opened before anything can ask it a
        // question. All four share one SharedPreferences file (AdPacing.FILE_NAME)
        // so the 60-second clock, the ad-free expiry and the display counters can
        // never disagree about which store they are reading.
        //
        // None of this touches the Mobile Ads SDK: initialisation still waits for
        // ConsentManager, and with ENABLE_ADS false these objects simply hold
        // zeroes that nothing reads.
        AdPacing.init(this)
        AdDiagnostics.init(this)
        InterstitialAds.init(this)
        WatermarkOffer.init(this)
        ReviewPrompt.init(this)

        // Scratch files from a process that was killed mid-job. Safe here and
        // only here: at process start no job of ours can be running, so every
        // vidsize_* file in the cache is an orphan.
        CompressionEngine.sweepOrphanedTempFiles(this)

        appOpenAdPolicy = AppOpenAdPolicy(this)
        appOpenAdManager = AppOpenAdManager(this, appOpenAdPolicy)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Mobile Ads is intentionally initialized by ConsentManager only after
        // UMP has resolved whether ad requests are allowed.
    }

    override fun onStart(owner: LifecycleOwner) {
        appOpenAdPolicy.registerSession()
        appOpenAdManager.onAppForeground(currentActivity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (!appOpenAdManager.isShowingAd) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (!appOpenAdManager.isShowingAd) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
