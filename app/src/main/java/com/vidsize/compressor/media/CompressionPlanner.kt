package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import kotlin.math.min

object CompressionPlanner {
    private const val MIN_VIDEO_BITRATE = 350_000

    fun plan(info: VideoInfo, preset: CompressionPreset): CompressionPlan {
        val sourceBitrate = info.sourceBitrate?.takeIf { it > 0 }
            ?: inferSourceBitrate(info)

        val factorTarget = (sourceBitrate * preset.sourceBitrateFactor).toInt()
        val videoBitrate = min(factorTarget, preset.bitrateCap)
            .coerceAtLeast(MIN_VIDEO_BITRATE)
            .coerceAtMost(sourceBitrate)

        val targetHeight = min(info.height.coerceAtLeast(1), preset.maxHeight)
        val estimatedBits = (videoBitrate + preset.audioBitrate) * info.durationSeconds
        val estimatedBytes = (estimatedBits / 8.0).toLong().coerceAtLeast(1L)

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
            info.height >= 2160 -> 20_000_000
            info.height >= 1080 -> 8_000_000
            info.height >= 720 -> 4_000_000
            else -> 2_000_000
        }
    }
}
