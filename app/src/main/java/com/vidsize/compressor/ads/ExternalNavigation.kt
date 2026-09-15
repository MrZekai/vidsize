package com.vidsize.compressor.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.vidsize.compressor.VidsizeApplication

/**
 * The handful of places where Vidsize deliberately hands the user off to
 * another app, and what the ad layer owes them.
 */

/**
 * "Do not greet this user with an app-open ad when they come back."
 *
 * Applies wherever Vidsize itself sent the user out: the media picker, the share
 * sheet, a video player, the gallery, system settings. Returning from an errand
 * the app asked for is not a session start, and an app-open ad there is the
 * single most common way an otherwise well-behaved app earns a policy
 * complaint.
 */
fun Context.suppressAppOpenOnReturn() {
    (applicationContext as? VidsizeApplication)
        ?.appOpenAdManager
        ?.suppressNextForeground()
}

/**
 * The deferred-ad handoff: suppress the app-open ad AND mark an interstitial as
 * owed on return.
 *
 * Exists as one function rather than two calls because the two must always
 * travel together. Suppressing without deferring gives away the impression;
 * deferring without suppressing lets the app-open ad land first, which then
 * blocks the interstitial for sixty seconds and reads to a tester as "the
 * deferred ad does not work". Making it a single call is what stops a future
 * hand from reintroducing that asymmetry at a new exit point.
 *
 * Use this at exits where the user is going to *do something with their file*.
 * Use [suppressAppOpenOnReturn] alone at maintenance exits - system settings,
 * the media picker on the way in - where no work has been completed and an ad
 * would interrupt a task rather than punctuate one.
 */
fun Context.deferInterstitialOnReturn() {
    suppressAppOpenOnReturn()
    InterstitialAds.markPending(this)
}

/** Unwraps the Activity a Compose tree is hosted in, for full-screen ads. */
fun Context.findHostActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
