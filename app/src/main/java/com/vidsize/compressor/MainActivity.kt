package com.vidsize.compressor

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
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ads.InterstitialAds
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ui.VidsizeRoot
import com.vidsize.compressor.ui.theme.VidsizeTheme

class MainActivity : ComponentActivity() {

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

        val incomingVideo = extractIncomingVideo(intent)
        if (incomingVideo != null) {
            (application as VidsizeApplication).appOpenAdManager.suppressNextForeground()
        }

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
                VidsizeRoot(initialVideo = incomingVideo)
            }
        }
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
     */
    private fun extractIncomingVideo(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("video/") != true) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
