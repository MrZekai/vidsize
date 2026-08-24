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
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val bytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            VideoInfo(duration, width, height, bytes, bitrate)
        } finally {
            retriever.release()
        }
    }
}
