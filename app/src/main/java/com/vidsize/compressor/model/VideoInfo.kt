package com.vidsize.compressor.model

data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sourceBytes: Long,
    val sourceBitrate: Int?,
    val hasAudio: Boolean = true,
    /**
     * Frames per second, or 0.0 when the probe could not determine it. The
     * planner falls back to 30 fps rather than guessing from resolution.
     *
     * This matters because output bitrate scales with frame rate: 1080p60 needs
     * roughly twice the bits of 1080p30 for the same quality, and the v0.8.4
     * flat bitrate cap had no way to express that.
     */
    val frameRate: Double = 0.0,
    /**
     * True when the source is encoded with a codec materially more efficient
     * than the H.264 Vidsize outputs (HEVC, VP9, AV1).
     *
     * A 100 MB HEVC file carries noticeably more visual information than a
     * 100 MB H.264 file, so transcoding it to H.264 at the bitrate its source
     * figure suggests would throw quality away. The planner raises the quality
     * ceiling for these sources instead.
     */
    val usesEfficientCodec: Boolean = false,
) {
    val durationSeconds: Double get() = durationMs / 1000.0
}
