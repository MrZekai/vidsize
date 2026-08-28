package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CompressionPlanner {
    private const val MIN_VIDEO_BITRATE = 350_000
    private const val ESTIMATE_HEADROOM = 1.10
    private const val FORCE_TRANSCODE_DELTA_PX = 2

    fun plan(info: VideoInfo, preset: CompressionPreset): CompressionPlan {
        require(info.durationMs > 0L) { "Video duration must be positive." }
        require(info.width > 0 && info.height > 0) { "Video dimensions must be positive." }

        // Base size prediction on the real file bytes/duration first. This keeps
        // the estimate tied to the file the user actually selected instead of
        // trusting container bitrate metadata that varies between camera apps.
        val sourceTotalBitrate = inferSourceTotalBitrate(info)
        val outputAudioBitrate = if (info.hasAudio) preset.audioBitrate else 0

        // Each preset owns a distinct total-bitrate budget.
        val targetTotalBitrate = (sourceTotalBitrate * preset.sourceBitrateFactor).toInt()
        val requestedVideoBitrate = (targetTotalBitrate - outputAudioBitrate)
            .coerceAtLeast(MIN_VIDEO_BITRATE)
        val videoBitrate = min(requestedVideoBitrate, preset.bitrateCap)
            .coerceAtMost(sourceTotalBitrate)
            .coerceAtLeast(MIN_VIDEO_BITRATE.coerceAtMost(sourceTotalBitrate))

        val sourceShortEdge = min(info.width, info.height)
        val requestedShortEdge = min(sourceShortEdge, preset.maxHeight)
        val outputTotalBitrate = videoBitrate + outputAudioBitrate
        val bitrateReductionRequested =
            outputTotalBitrate < (sourceTotalBitrate * 0.98).toInt()

        // For a single MediaItem Media3 can avoid transcoding when it decides no
        // video transformation is necessary. If only bitrate changes while the
        // dimensions stay byte-for-byte identical, the requested encoder bitrate
        // can therefore be bypassed. A genuine 2px short-edge resize is visually
        // negligible but makes transcoding necessary and keeps actual output near
        // the selected preset's bitrate budget.
        val targetShortEdge = if (
            bitrateReductionRequested &&
            requestedShortEdge == sourceShortEdge &&
            sourceShortEdge > FORCE_TRANSCODE_DELTA_PX + 2
        ) {
            (sourceShortEdge - FORCE_TRANSCODE_DELTA_PX).toEvenAtLeastTwo()
        } else {
            requestedShortEdge.toEvenAtLeastTwo()
        }

        val rawTargetHeight = if (info.height > info.width) {
            (info.height.toDouble() * targetShortEdge.toDouble() / info.width.toDouble())
                .roundToInt()
        } else {
            targetShortEdge
        }
        val targetHeight = rawTargetHeight.toEvenAtLeastTwo()

        // MediaCodec bitrate is a target, not a byte-exact promise. 10% headroom
        // covers normal encoder/container variation without collapsing presets.
        val estimatedBits = outputTotalBitrate * info.durationSeconds
        val estimatedBytes = ((estimatedBits / 8.0) * ESTIMATE_HEADROOM)
            .toLong()
            .coerceAtLeast(1L)
            .coerceAtMost(info.sourceBytes.takeIf { it > 0L } ?: Long.MAX_VALUE)

        return CompressionPlan(
            preset = preset,
            targetHeight = targetHeight,
            videoBitrate = videoBitrate,
            audioBitrate = outputAudioBitrate,
            estimatedOutputBytes = estimatedBytes,
        )
    }

    private fun inferSourceTotalBitrate(info: VideoInfo): Int {
        if (info.sourceBytes > 0L && info.durationSeconds > 0.0) {
            return ((info.sourceBytes * 8.0) / info.durationSeconds)
                .toInt()
                .coerceAtLeast(MIN_VIDEO_BITRATE)
        }
        info.sourceBitrate?.takeIf { it > 0 }?.let {
            return it.coerceAtLeast(MIN_VIDEO_BITRATE)
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
