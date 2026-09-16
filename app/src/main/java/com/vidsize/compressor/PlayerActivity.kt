package com.vidsize.compressor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vidsize.compressor.ui.screens.VideoPlayerScreen
import com.vidsize.compressor.ui.theme.VidsizeTheme

/**
 * Vidsize's own video player.
 *
 * ## Why this exists
 *
 * Until now "Open video" and a tap on a Recent row both built an implicit
 * `ACTION_VIEW` intent, which hands the file to whatever player the device
 * happens to have. That is a handover, not a feature: the user leaves Vidsize,
 * lands in an unpredictable app - sometimes with its own ads, sometimes a
 * chooser dialog first - and the way back is the system Back button. The file
 * Vidsize made was being shown off by somebody else's software.
 *
 * Playing it here keeps the whole loop inside the app. It also removes a real
 * class of failure: a device with no registered video player showed nothing at
 * all when those buttons were tapped, because `startActivity` simply threw and
 * the `runCatching` around it swallowed the result.
 *
 * ## Why an Activity rather than a screen inside the existing composition
 *
 * [MainActivity] renders a two-destination root and passes callbacks down two
 * levels to the result screen. Threading a third destination up through that
 * would touch both screens' signatures for a surface that has nothing to do
 * with either of them.
 *
 * A separate activity also gets, for free, the things a player wants and the
 * rest of the app does not: its own black backdrop, light-on-dark system bars,
 * a keep-awake flag scoped to this window only, and a Back button that means
 * "close the player" without any state to coordinate. And it is isolated - a
 * player that misbehaves on some device cannot take the compression screen with
 * it.
 */
class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Light icons over the player's black backdrop. MainActivity pins the
        // opposite (dark icons over a white canvas) because the app is
        // light-only by product decision; this window is the one place that is
        // deliberately dark, so it pins its own.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Scoped to this window, so it is released the moment the player closes.
        // A user watching a 40-second clip should not have the screen time out
        // halfway through, and the app should not hold the screen awake a second
        // longer than the player is on it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = readUri(intent)
        if (uri == null) {
            // Nothing to play. Closing immediately is the honest outcome: an
            // empty player with transport controls that do nothing would be
            // worse than the screen never appearing.
            finish()
            return
        }

        setContent {
            VidsizeTheme {
                VideoPlayerScreen(
                    uri = uri,
                    onClose = { finish() },
                )
            }
        }
    }

    private fun readUri(intent: Intent?): Uri? {
        val raw = intent?.getStringExtra(EXTRA_VIDEO_URI)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    companion object {
        private const val EXTRA_VIDEO_URI = "com.vidsize.compressor.extra.VIDEO_URI"

        /**
         * The only way this screen is opened.
         *
         * Explicit component, never `ACTION_VIEW`: an implicit intent for a
         * video is precisely the handover this class replaces, and on a device
         * where Vidsize had been set as the default video player an implicit
         * one could also route straight back here in a loop.
         *
         * `FLAG_GRANT_READ_URI_PERMISSION` is kept because the URI is a
         * MediaStore content URI and the grant costs nothing; the caller is
         * inside this app, so it is belt and braces rather than the mechanism.
         */
        fun intent(context: Context, uri: Uri): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, uri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}
