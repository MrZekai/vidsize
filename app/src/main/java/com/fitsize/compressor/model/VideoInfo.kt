package com.fitsize.compressor.model

data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sourceBytes: Long,
    val sourceBitrate: Int?,
) {
    val durationSeconds: Double get() = durationMs / 1000.0
}
