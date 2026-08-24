package com.vidsize.compressor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vidsize.compressor.ads.AppOpenAdManager
import com.vidsize.compressor.ads.AppOpenAdPolicy

class VidsizeApplication : Application(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    lateinit var appOpenAdPolicy: AppOpenAdPolicy
        private set

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
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
