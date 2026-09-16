package com.vidsize.compressor.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vidsize.compressor.R
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.theme.Space

/**
 * Plays one video, inside Vidsize.
 *
 * ## The three things that make an embedded player correct
 *
 * **1. The player is released exactly once, and never leaked.** An [ExoPlayer]
 * holds a codec, a surface and a wake lock. Created in [remember] and released
 * in the matching [DisposableEffect], it cannot outlive the composition - and
 * because the key is the URI, playing a different video releases the old player
 * rather than quietly stacking a second one on a device that has a small number
 * of codec instances to give out.
 *
 * **2. Playback follows the lifecycle.** Without this the audio keeps running
 * after the user presses Home, which is the single most complained-about bug in
 * embedded players. `onPause` stops playback and `onStop` releases nothing but
 * lets the surface go; the position is kept, so returning resumes where the user
 * left off rather than restarting.
 *
 * **3. A failure says so.** `PlayerView` renders a black rectangle when the
 * player errors, which is indistinguishable from a video that has not started.
 * [Player.Listener.onPlayerError] flips this screen to a readable message
 * instead - the same principle as the compression screen, where a silent
 * failure was the worst outcome the QA pass found.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    uri: Uri,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(uri) { mutableStateOf(false) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            // The user tapped "play". Anything else would ask them to tap twice.
            playWhenReady = true
            // A compressed clip is usually short and usually watched to check
            // it came out right, which is a thing people do more than once.
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Stop on background, resume on return. Keyed on the owner rather than on
    // the player so the observer is registered once for the screen's life.
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!failed) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A failed player must not keep a surface or a codec busy while its error
    // message is on screen.
    LaunchedEffect(failed) {
        if (failed) exoPlayer.pause()
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (failed) {
            Text(
                text = stringResource(R.string.player_error),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Space.xl),
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = exoPlayer
                        // The controller is the whole point of using PlayerView:
                        // scrubbing, play/pause and the time readout, already
                        // localised and already accessible.
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                // AndroidView caches the view across recompositions, and a
                // released player left attached to it is a crash waiting for the
                // next frame. Re-binding here keeps the two in step.
                update = { view -> view.player = exoPlayer },
                onRelease = { view -> view.player = null },
            )
        }

        // Above the video, inside the status bar inset. The system Back button
        // does the same thing; this exists because a full-screen black surface
        // with no visible way out reads as a hang.
        IconAction(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = onClose,
            tint = Color.White,
            background = Color.Black.copy(alpha = 0.45f),
            border = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(Space.sm),
        )
    }
}
