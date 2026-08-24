package com.vidsize.compressor.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Poster frame extraction for the selected-video card.
 *
 * Purely presentational — this does not touch the compression pipeline. It
 * exists because a thumbnail is the difference between "the app understood my
 * file" and "the app printed some numbers at me".
 *
 * Decoded off the main thread, downscaled at decode time on API 27+ so a 4K
 * source never allocates a full-size bitmap, and wrapped in runCatching because
 * an unreadable or DRM-protected frame must degrade to a placeholder, never
 * crash the screen.
 */
object VideoThumbnail {

    /** Longest edge of the decoded bitmap, in pixels. */
    private const val MAX_EDGE = 480

    suspend fun load(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitmap = extractFrame(retriever)
            bitmap?.asImageBitmap()
        } catch (throwable: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val timeUs = 0L
        val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC

        if (Build.VERSION.SDK_INT >= 27) {
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            if (width > 0 && height > 0) {
                val scale = MAX_EDGE.toFloat() / max(width, height).toFloat()
                val targetWidth = if (scale < 1f) (width * scale).roundToInt() else width
                val targetHeight = if (scale < 1f) (height * scale).roundToInt() else height
                val scaled = runCatching {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        option,
                        targetWidth.coerceAtLeast(2),
                        targetHeight.coerceAtLeast(2),
                    )
                }.getOrNull()
                if (scaled != null) return scaled
            }
        }

        return runCatching { retriever.getFrameAtTime(timeUs, option) }.getOrNull()
    }
}

/**
 * Loads the poster frame for [uri] and exposes it as Compose state.
 * Returns null while loading and null again if the frame cannot be read.
 */
@Composable
fun rememberVideoThumbnail(uri: Uri): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, key1 = uri, key2 = context) {
        value = VideoThumbnail.load(context, uri)
    }
}
