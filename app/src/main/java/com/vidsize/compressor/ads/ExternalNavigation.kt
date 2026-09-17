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

/** Unwraps the Activity a Compose tree is hosted in, for full-screen ads. */
fun Context.findHostActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
