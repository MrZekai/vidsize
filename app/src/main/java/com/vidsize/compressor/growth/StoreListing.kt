package com.vidsize.compressor.growth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.vidsize.compressor.ads.suppressAppOpenOnReturn

/**
 * Sends the user to Vidsize's own Play listing, to leave a rating.
 *
 * ## Why this is not the in-app review API
 *
 * [ReviewPrompt] already asks for a review at three milestones, and it uses
 * Play's in-app review flow. That flow is the right tool there and the wrong
 * tool here, for one reason: Google's quota is undocumented and
 * `launchReviewFlow` is allowed to complete having shown nothing at all, with
 * no way for the caller to tell.
 *
 * A prompt the app decided to raise can absorb that. The user did not ask for
 * it, so nothing is owed if it never appears. A button the user deliberately
 * pressed cannot: they tapped "rate the app", and a tap that produces no
 * visible result reads as a broken app, which is a strange thing to hand
 * someone at the moment they were feeling generous. Google's own guidance says
 * the same - do not put the in-app review flow behind a button.
 *
 * So the button opens the store listing, where the rating control always
 * exists.
 *
 * ## Two intents, because one of them is not always there
 *
 * `market://` opens the Play app directly on the listing. It is the better
 * experience and it fails outright on a device with no Play Store - a
 * manufacturer's build, an emulator image without Google services, a
 * sideloaded install. The `https://play.google.com/...` form works anywhere
 * there is a browser, so it is the fallback rather than the first choice.
 *
 * Both can still fail on a device with neither, which is why every launch is
 * guarded and the function reports whether anything opened. The caller decides
 * what to say; this object does not show UI.
 */
object StoreListing {

    /**
     * Open the listing as a courtesy exit, suppressing the app-open ad.
     *
     * ## Why the suppression lives here and not at the call site
     *
     * The result screen forbids a bare `suppressAppOpenOnReturn()`, and the
     * rule is a good one: every departure from that screen that leads back to
     * the user's own file must go through `InterstitialAds.markPending`, which
     * decides the interstitial and the app-open ad together. A bare
     * suppression there kills the interstitial silently, which is the bug the
     * rule exists to prevent.
     *
     * Leaving to rate the app is a different kind of departure. It does not
     * lead to the file, and it must NOT queue an interstitial: showing a
     * full-screen ad to someone returning from doing the developer a favour is
     * a poor trade at any fill rate. So it needs app-open suppression with no
     * interstitial pairing - the exact combination the result screen's rule
     * forbids, for reasons that do not apply to it.
     *
     * Putting it behind a named function here keeps both true. The result
     * screen stays as strict as it was and still catches an unpaired
     * suppression slipped in among the export actions; this one case is
     * spelled out where its reasoning can be read.
     *
     * @return false when nothing could be opened - see [open].
     */
    fun openForRating(context: Context, packageName: String): Boolean {
        context.suppressAppOpenOnReturn()
        return open(context, packageName)
    }

    /**
     * Open the Play listing for this app.
     *
     * @return false when neither the Play app nor a browser could be opened,
     *   so the caller can tell the user instead of appearing to do nothing.
     */
    fun open(context: Context, packageName: String): Boolean {
        val market = "market://details?id=$packageName".toUri()
        val web = "https://play.google.com/store/apps/details?id=$packageName".toUri()
        return launch(context, market) || launch(context, web)
    }

    private fun launch(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // The sheet that hosts the button is not an Activity context in
            // every configuration, and a non-Activity context cannot start an
            // Activity without its own task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Coming back should return to Vidsize as the user left it, not to
            // a second copy of the store on top of our task.
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
