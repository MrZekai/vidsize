package com.fitsize.compressor

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitsize.compressor.ui.FitsizeRoot
import com.fitsize.compressor.ui.theme.FitsizeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars, and pin both bars to *dark icons on a
        // transparent background*.
        //
        // The default `enableEdgeToEdge()` follows the device's dark-mode
        // setting, which would give a phone in dark mode white status-bar icons
        // over Fitsize's white canvas — invisible. Because V1 is light-only by
        // product decision, the bar style has to be pinned too, not just the
        // Compose colour scheme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val incomingVideo = extractIncomingVideo(intent)

        setContent {
            FitsizeTheme {
                FitsizeRoot(initialVideo = incomingVideo)
            }
        }
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
