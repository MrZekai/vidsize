package com.vidsize.compressor.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vidsize.compressor.model.VideoInfo

object VideoProbe {
    fun probe(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val hasVideo = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val rawWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { ((it % 360) + 360) % 360 }
                ?: 0

            if (!hasVideo || duration <= 0L || rawWidth <= 0 || rawHeight <= 0) {
                throw InvalidVideoException("Readable video metadata is unavailable.")
            }

            // MediaMetadataRetriever reports encoded dimensions and rotation
            // separately. Normalize once here so every downstream calculation and
            // every UI label works with the dimensions the user actually sees.
            val (width, height) = if (rotation == 90 || rotation == 270) {
                rawHeight to rawWidth
            } else {
                rawWidth to rawHeight
            }

            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            val hasAudio = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            val bytes = context.contentResolver
                .openFileDescriptor(uri, "r")
                ?.use { it.statSize }
                ?: -1L

            VideoInfo(
                durationMs = duration,
                width = width,
                height = height,
                sourceBytes = bytes,
                sourceBitrate = bitrate,
                hasAudio = hasAudio,
                frameRate = readFrameRate(retriever, duration),
                usesEfficientCodec = usesEfficientCodec(context, uri),
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Frames per second, from the container's own frame count.
     *
     * `METADATA_KEY_VIDEO_FRAME_COUNT` is the only frame figure that is reliable
     * across camera apps - `METADATA_KEY_CAPTURE_FRAMERATE` is only populated for
     * slow-motion capture. For variable frame rate footage this is the average,
     * which is the right number for a bitrate budget anyway.
     *
     * Returns 0.0 when it cannot be determined; the planner falls back to 30 fps
     * rather than inventing a figure from the resolution.
     */
    private fun readFrameRate(retriever: MediaMetadataRetriever, durationMs: Long): Double {
        if (durationMs <= 0L) return 0.0
        val frames = runCatching {
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
        }.getOrNull() ?: return 0.0
        if (frames <= 0) return 0.0
        return frames * 1000.0 / durationMs
    }

    /**
     * True when the source codec is materially more efficient than the H.264
     * Vidsize produces.
     *
     * `MediaMetadataRetriever` only exposes the *container* mime type, so the
     * video track has to be read with `MediaExtractor`. Every failure mode -
     * unreadable URI, DRM, an exotic container - degrades to false, which simply
     * means the output is budgeted as if the source were already H.264.
     */
    private fun usesEfficientCodec(context: Context, uri: Uri): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                if (mime.startsWith("video/")) {
                    return@runCatching mime == MediaFormat.MIMETYPE_VIDEO_HEVC ||
                        mime == MediaFormat.MIMETYPE_VIDEO_VP9 ||
                        mime == MediaFormat.MIMETYPE_VIDEO_AV1
                }
            }
            false
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)
}
