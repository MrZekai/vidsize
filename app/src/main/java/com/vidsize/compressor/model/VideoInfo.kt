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
    /**
     * The video track's mime type, or null when it could not be read.
     *
     * This is the TRACK mime from `MediaExtractor`, never the container's:
     * `video/mp4` says nothing about whether the stream inside is AVC, HEVC or
     * AV1, and the decoder question can only be asked about the codec.
     */
    val sourceMime: String? = null,
    /**
     * False only when the device codec table reports "cannot read this".
     *
     * ## Why a video can be unprocessable before anything is encoded
     *
     * A 4K source failed in the field with a decoder error, after the app had
     * offered three compression levels and then spent minutes retrying three
     * different OUTPUT sizes. No output size can help: the decoder has to read
     * the source at its full resolution whatever the destination is.
     *
     * The answer travels with the video for diagnostics and a non-blocking UI
     * warning only. It is never an eligibility decision: vendor capability
     * tables under-report alias and software codecs, so [CompressionEngine]
     * always performs a real decode with platform and software-first fallback.
     * Unknown defaults to true because there is no negative report to surface.
     */
    val decoderPrecheckPassed: Boolean = true,
) {
    val durationSeconds: Double get() = durationMs / 1000.0
}
