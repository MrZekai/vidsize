package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a probed video plus a preset into the numbers the encoder is asked for
 * and the number the user is shown.
 *
 * ## The ordering guarantee
 *
 * BALANCED > SMALLER > SMALLEST holds for video bitrate, total bitrate and
 * estimated size for *every* input. This is structural, not empirical: the final
 * bitrate is `median(requested, lowerBound, upperBound)` and all three of those
 * are strictly ordered across the presets, so the clamp can never collapse two
 * presets onto the same value.
 *
 * The removed shared constant `MIN_VIDEO_BITRATE = 350_000` was what broke this:
 * for any source under ~683 kbps all three presets hit the same floor and the
 * user saw three identical estimates, followed a few minutes later by a
 * "compression didn't finish" error.
 *
 * ## The estimate
 *
 * Two separate factors instead of one vague 1.10 "headroom":
 *  - [CONTAINER_OVERHEAD] - MP4 moov atom and sample tables.
 *  - [ENCODER_VARIANCE]   - hardware VBR overshoot. Calibrate this from device
 *                           measurements (see the audit's T3 test); 1.08 is the
 *                           starting point, not a measured constant.
 *
 * Clips shorter than [SHORT_CLIP_SECONDS] use [SHORT_CLIP_VARIANCE] instead:
 * with one or two keyframes the rate control never converges and the container
 * overhead is proportionally much larger.
 */
object CompressionPlanner {

    /** Below this a hardware encoder may refuse the format outright. */
    private const val MIN_ENCODABLE_VIDEO_BITRATE = 100_000

    /** Absolute sanity floor for the inferred *source* bitrate only. */
    private const val MIN_INFERRED_SOURCE_BITRATE = 120_000

    private const val CONTAINER_OVERHEAD = 1.02
    private const val ENCODER_VARIANCE = 1.08
    private const val SHORT_CLIP_SECONDS = 4.0
    private const val SHORT_CLIP_VARIANCE = 1.35

    /** A preset must beat this share of the source to be worth running. */
    private const val VIABLE_RATIO = 0.92

    /** Audio may never claim more than this share of a low-bitrate source. */
    private const val MAX_AUDIO_SOURCE_SHARE = 0.15
    private const val MIN_AUDIO_BITRATE = 64_000

    /**
     * Media3 can transmux instead of transcode when nothing about the video
     * changes. In practice `DefaultEncoderFactory.videoNeedsEncoding()` already
     * forces a transcode because custom `VideoEncoderSettings` are supplied, so
     * this nudge is belt-and-braces. It costs a pointless GPU resample and
     * produces non-16-aligned sizes; remove it once device test T1 confirms the
     * requested bitrate reaches the encoder without it.
     */
    private const val FORCE_TRANSCODE_DELTA_PX = 2

    fun plan(info: VideoInfo, preset: CompressionPreset): CompressionPlan {
        require(info.durationMs > 0L) { "Video duration must be positive." }
        require(info.width > 0 && info.height > 0) { "Video dimensions must be positive." }

        // Base the prediction on the real file bytes/duration first. This keeps
        // the estimate tied to the file the user actually selected instead of
        // trusting container bitrate metadata that varies between camera apps.
        val sourceTotalBitrate = inferSourceTotalBitrate(info)

        // Never spend 128 kbps of a 480 kbps source on audio, and never upsample
        // a 64 kbps source track to 128 kbps - that grows the file for nothing.
        val outputAudioBitrate = if (info.hasAudio) {
            min(
                preset.audioBitrate,
                max(MIN_AUDIO_BITRATE, (sourceTotalBitrate * MAX_AUDIO_SOURCE_SHARE).toInt()),
            )
        } else {
            0
        }

        val requestedVideoBitrate =
            (sourceTotalBitrate * preset.sourceBitrateFactor).toInt() - outputAudioBitrate
        val lowerBound = (sourceTotalBitrate * preset.minSourceShare).toInt()
        val upperBound = preset.bitrateCap

        // median(requested, lowerBound, upperBound). All three are strictly
        // ordered across presets, so the result is too. Do not add a shared
        // absolute floor here: that is what collapsed the presets before.
        val videoBitrate = min(max(requestedVideoBitrate, lowerBound), upperBound)

        val outputTotalBitrate = videoBitrate + outputAudioBitrate

        val sourceShortEdge = min(info.width, info.height)
        val requestedShortEdge = min(sourceShortEdge, preset.maxShortEdge)
        val bitrateReductionRequested =
            outputTotalBitrate < (sourceTotalBitrate * 0.98).toInt()

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

        val variance = if (info.durationSeconds < SHORT_CLIP_SECONDS) {
            SHORT_CLIP_VARIANCE
        } else {
            ENCODER_VARIANCE
        }
        // Deliberately NOT clamped to info.sourceBytes. An estimate larger than
        // the source is real information, and `viable` is how it is surfaced.
        val estimatedBytes = ((outputTotalBitrate * info.durationSeconds / 8.0) *
            CONTAINER_OVERHEAD * variance)
            .toLong()
            .coerceAtLeast(1L)

        val savesEnough = info.sourceBytes <= 0L ||
            estimatedBytes < (info.sourceBytes * VIABLE_RATIO).toLong()
        val encodable = videoBitrate >= MIN_ENCODABLE_VIDEO_BITRATE

        return CompressionPlan(
            preset = preset,
            targetHeight = targetHeight,
            videoBitrate = videoBitrate,
            audioBitrate = outputAudioBitrate,
            estimatedOutputBytes = estimatedBytes,
            viable = savesEnough && encodable,
        )
    }

    private fun inferSourceTotalBitrate(info: VideoInfo): Int {
        if (info.sourceBytes > 0L && info.durationSeconds > 0.0) {
            return ((info.sourceBytes * 8.0) / info.durationSeconds)
                .toInt()
                .coerceAtLeast(MIN_INFERRED_SOURCE_BITRATE)
        }
        info.sourceBitrate?.takeIf { it > 0 }?.let {
            return it.coerceAtLeast(MIN_INFERRED_SOURCE_BITRATE)
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
