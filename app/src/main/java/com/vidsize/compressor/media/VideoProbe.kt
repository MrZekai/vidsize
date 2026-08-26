package com.vidsize.compressor.media

import android.content.Context
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
            )
        } finally {
            retriever.release()
        }
    }
}
