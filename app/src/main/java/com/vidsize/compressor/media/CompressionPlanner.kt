package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CompressionPlanner {
    private const val MIN_VIDEO_BITRATE = 350_000

    // If a preset keeps the exact source resolution, Media3 may be able to keep
    // much more of the original encoded stream than the requested encoder
    // bitrate formula alone suggests. Do not promise an unrealistically tiny
    // file in that case. This affects DISPLAYED ESTIMATE ONLY; the actual
    // compression/transcoding pipeline is intentionally untouched.
    private const val SAME_RESOLUTION_ESTIMATE_FLOOR_RATIO = 0.90
    private const val TRANSCODE_ESTIMATE_HEADROOM = 1.08

    fun plan(info: VideoInfo, preset: CompressionPreset): CompressionPlan {
        require(info.durationMs > 0L) { "Video duration must be positive." }
        require(info.width > 0 && info.height > 0) { "Video dimensions must be positive." }

        val sourceBitrate = info.sourceBitrate?.takeIf { it > 0 }
            ?: inferSourceBitrate(info)

        val factorTarget = (sourceBitrate * preset.sourceBitrateFactor).toInt()
        val videoBitrate = min(factorTarget, preset.bitrateCap)
            .coerceAtLeast(MIN_VIDEO_BITRATE)
            .coerceAtMost(sourceBitrate)

        // Preset maxHeight means the familiar 1080p/720p/480p short edge.
        // For portrait input the Presentation effect still needs a target HEIGHT,
        // so preserve aspect ratio and convert the short-edge ceiling to the
        // corresponding long-edge height (1080x1920 -> 720x1280, not 405x720).
        val sourceShortEdge = min(info.width, info.height)
        val targetShortEdge = min(sourceShortEdge, preset.maxHeight)
        val rawTargetHeight = if (info.height > info.width) {
            (info.height.toDouble() * targetShortEdge.toDouble() / info.width.toDouble())
                .roundToInt()
        } else {
            targetShortEdge
        }
        val targetHeight = rawTargetHeight.toEvenAtLeastTwo()

        val estimatedAudioBitrate = if (info.hasAudio) preset.audioBitrate else 0
        val estimatedBits = (videoBitrate + estimatedAudioBitrate) * info.durationSeconds
        val bitrateEstimate = ((estimatedBits / 8.0) * TRANSCODE_ESTIMATE_HEADROOM)
            .toLong()
            .coerceAtLeast(1L)

        // Important: this is an estimate correction, NOT forced extra compression.
        // When the selected preset keeps the video's short edge unchanged, the
        // pipeline can legitimately finish much closer to the original file size.
        // In that case use a conservative floor so e.g. a 101 MB source is not
        // advertised as ~36 MB when the real result is around 90 MB.
        val sameResolution = targetShortEdge == sourceShortEdge
        val sameResolutionFloor = if (sameResolution && info.sourceBytes > 0L) {
            (info.sourceBytes * SAME_RESOLUTION_ESTIMATE_FLOOR_RATIO).toLong()
        } else {
            0L
        }
        val estimatedBytes = maxOf(bitrateEstimate, sameResolutionFloor)
            .coerceAtMost(info.sourceBytes.takeIf { it > 0L } ?: Long.MAX_VALUE)
            .coerceAtLeast(1L)

        return CompressionPlan(
            preset = preset,
            targetHeight = targetHeight,
            videoBitrate = videoBitrate,
            audioBitrate = preset.audioBitrate,
            estimatedOutputBytes = estimatedBytes,
        )
    }

    private fun inferSourceBitrate(info: VideoInfo): Int {
        if (info.sourceBytes > 0 && info.durationSeconds > 0) {
            return ((info.sourceBytes * 8.0) / info.durationSeconds)
                .toInt()
                .coerceAtLeast(MIN_VIDEO_BITRATE)
        }
        return when {
            max(info.width, info.height) >= 2160 -> 20_000_000
            max(info.width, info.height) >= 1080 -> 8_000_000
            max(info.width, info.height) >= 720 -> 4_000_000
            else -> 2_000_000
        }
    }

    private fun Int.toEvenAtLeastTwo(): Int {
        val value = coerceAtLeast(2)
        return if (value % 2 == 0) value else value - 1
    }
}
