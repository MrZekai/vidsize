package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/** GDPR / ePrivacy consent gate for every ad format in Vidsize. */
object ConsentManager {

    private val adsSdkStarted = AtomicBoolean(false)

    /** UMP says an ad request is allowed for the current consent state. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** Mobile Ads initialization has completed after consent resolution. */
    var adsSdkReady by mutableStateOf(false)
        private set

    /** UMP has finished resolving the current request (success or fallback). */
    var consentResolved by mutableStateOf(false)
        private set

    /** True only while both consent and SDK readiness allow a new ad request. */
    val adsAllowed: Boolean
        get() = canRequestAds && adsSdkReady

    /** True where Google requires an in-app entry point to privacy options. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun gatherConsent(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    syncState(activity, consentInformation)
                    consentResolved = true
                }
            },
            {
                // If the lookup fails, UMP's previously stored state still decides
                // whether ads may be requested.
                syncState(activity, consentInformation)
                consentResolved = true
            },
        )

        // v0.9.2 - QA finding: the synchronous syncState() that used to sit here
        // has been removed.
        //
        // It read the cached consent state on the same frame the async update
        // was requested, and syncState() starts the Mobile Ads SDK whenever
        // canRequestAds() is true. So on a warm start the SDK could initialise
        // *before* loadAndShowConsentFormIfRequired had a chance to decide that
        // a form was still due - an expired consent, a changed vendor list, a
        // user who moved into the EEA. And because startAdsSdk guards on a
        // compareAndSet, that start is not revocable: once it has happened,
        // refusing consent in the form seconds later cannot put the SDK back.
        //
        // The cost of removing it is that a returning user's SDK starts one
        // callback later, which is milliseconds and delays nothing the user can
        // see: every ad surface already renders nothing until adsAllowed is
        // true. Consent decides, then the SDK starts. In that order, always.
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            syncState(activity, UserMessagingPlatform.getConsentInformation(activity))
            onDismissed()
        }
    }

    private fun syncState(context: Context, consentInformation: ConsentInformation) {
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        canRequestAds = consentInformation.canRequestAds()
        if (canRequestAds) startAdsSdk(context.applicationContext)
    }

    private fun startAdsSdk(context: Context) {
        if (!adsSdkStarted.compareAndSet(false, true)) return
        Thread(
            {
                MobileAds.initialize(context) {
                    Handler(Looper.getMainLooper()).post {
                        adsSdkReady = true
                    }
                }
            },
            "vidsize-ads-init",
        ).start()
    }
}
