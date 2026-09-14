package com.vidsize.compressor

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ads.InterstitialAds
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ui.VidsizeRoot
import com.vidsize.compressor.ui.theme.VidsizeTheme

class MainActivity : ComponentActivity() {

    /**
     * The video a share-sheet intent carried in, kept as state so a *second*
     * share arriving while the app is already open replaces the first.
     *
     * QA finding: the activity had no `onNewIntent` and no `launchMode`, so a
     * second shared video started a second MainActivity instance on top of the
     * first. CompressionJobState is a process singleton, so both instances then
     * rendered the same job - and the completion notification's SINGLE_TOP
     * intent was delivered to `onNewIntent` and dropped on the floor.
     */
    private var incomingVideoState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars, and pin both bars to *dark icons on a
        // transparent background*.
        //
        // The default `enableEdgeToEdge()` follows the device's dark-mode
        // setting, which would give a phone in dark mode white status-bar icons
        // over Vidsize's white canvas — invisible. Because V1 is light-only by
        // product decision, the bar style has to be pinned too, not just the
        // Compose colour scheme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        handleIncoming(intent)

        // Consent first, ads second. Nothing requests an ad until UMP allows it
        // and Mobile Ads has completed initialization.
        if (BuildConfig.ENABLE_ADS) ConsentManager.gatherConsent(this)

        setContent {
            val adsReady = BuildConfig.ENABLE_ADS && ConsentManager.adsAllowed
            LaunchedEffect(adsReady) {
                if (adsReady) {
                    (application as VidsizeApplication).appOpenAdManager.preload()
                }
            }
            VidsizeTheme {
                VidsizeRoot(
                    initialVideo = incomingVideoState.value?.let(Uri::parse),
                    onVideoConsumed = { incomingVideoState.value = null },
                )
            }
        }
    }

    /**
     * With `launchMode="singleTask"` a second share, and the completion
     * notification's own intent, both arrive here instead of creating a new
     * activity. Routing them through the same handler as `onCreate` is what
     * makes a second shared video replace the first rather than open a second
     * copy of the app on top of it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val video = extractIncomingVideo(intent) ?: return
        incomingVideoState.value = video.toString()
        // Arriving from another app is not a session start; see
        // Context.suppressAppOpenOnReturn.
        (application as VidsizeApplication).appOpenAdManager.suppressNextForeground()
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.ENABLE_ADS) return

        // No delay and no forced display: this only preloads when consent and
        // SDK initialization have already completed. With ENABLE_ADS false the
        // manager short-circuits, so the ads SDK is never touched.
        (application as VidsizeApplication).appOpenAdManager.preload()

        // The deferred interstitial lands here, and only here.
        //
        // This is the one callback that fires for every way back into Vidsize:
        // the share sheet dismissing, a video player or the gallery being
        // closed, the task switcher. Putting the show call on a specific
        // screen's focus effect would have meant re-deriving "did the user come
        // back?" once per exit point and forgetting one of them - which is the
        // single most common way this pattern leaks revenue.
        //
        // It is safe against loops: showPendingIfAny consumes the flag before it
        // shows, and the ad's own dismissal re-enters onResume with nothing
        // pending. It is safe against stacking: AdGate consults the same
        // 60-second clock the app-open ad writes to, so a return that has just
        // been met by an app-open ad skips the interstitial rather than
        // following one full-screen ad with another.
        InterstitialAds.showPendingIfAny(this)

        // Rewarded is preloaded here rather than on the Home composable alone so
        // the offer strip is ready on the first frame of a warm return, not one
        // network round-trip later.
        RewardedAds.preload(this)
    }

    /**
     * Supports the share-sheet entry point declared in the manifest: the user
     * picks a video in Gallery or Files, taps Share, and lands directly on the
     * compression screen.
     *
     * ## Why the URI is validated (QA finding: confused deputy)
     *
     * This is the one place another application decides what Vidsize opens, and
     * whatever comes back is read with Vidsize's own identity and written to a
     * world-readable folder. Taking the extra unchecked turned the app into a
     * deputy for the sender:
     *
     *  - A `file://` URI names a path rather than a grant. Any app could point
     *    it at something inside Vidsize's private storage - or at any file the
     *    app can read but the sender cannot - and receive the contents back as
     *    a new video in `Movies/Vidsize`.
     *  - A `content://` URI carrying *Vidsize's own* authority is the same trick
     *    through the front door: the app would be asked to read from itself and
     *    republish the result publicly.
     *
     * A `content://` URI from any other authority is the safe case and the only
     * one accepted: it is a permission grant the sender had to hold in order to
     * pass on, and the platform enforces it. Anything else is dropped and the
     * app simply opens on Home, which is also what a malformed share should do.
     */
    private fun extractIncomingVideo(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("video/") != true) return null

        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: return null

        if (!ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) return null
        if (uri.authority.equals(packageName, ignoreCase = true)) return null
        if (uri.authority?.startsWith("$packageName.", ignoreCase = true) == true) return null

        return uri
    }
}
