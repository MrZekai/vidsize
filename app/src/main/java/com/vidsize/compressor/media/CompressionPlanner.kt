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
 *  - `requested`  = f x S - audio        -> f is strictly ordered, audio is not
 *                                           increasing, so this is too.
 *  - `lowerBound` = minSourceShare x S   -> strictly ordered.
 *  - `upperBound` = min(qualityCeiling, bitrateCeiling)
 *                   qualityCeiling scales with targetPixels (non-increasing
 *                   across presets) and qualityBitsPerPixel (strictly
 *                   decreasing), so it is strictly decreasing; bitrateCeiling is
 *                   strictly decreasing; and the pointwise min of two strictly
 *                   decreasing sequences is strictly decreasing.
 *
 * The removed constant `MIN_VIDEO_BITRATE = 350_000` was what broke this before
 * v0.8.3: for any source under ~683 kbps all three presets hit the same floor.
 *
 * ## v0.8.5: the cap is a quality target, not a flat number
 *
 * v0.8.4 gave each preset one absolute cap. On a 16 Mbps 1080p phone clip
 * Balanced's 5 Mbps cap bound long before its 70%-of-source policy did, so the
 * output was 31% of the source from a preset that promises quality first. The
 * ceiling is now derived from the output the preset actually intends to produce:
 *
 *     ceiling = targetWidth x targetHeight x fps x qualityBitsPerPixel x codecFactor
 *
 * That is bits per pixel per frame, the standard way to express encoder quality.
 * It respects resolution and frame rate, which a flat cap could not, and it
 * leaves low-bitrate sources completely untouched: down there `requested` and
 * `lowerBound` bind, never the ceiling, so every v0.8.3 low-bitrate regression
 * case produces byte-identical numbers.
 *
 * ## The estimate
 *
 * Two separate factors instead of one vague 1.10 "headroom":
 *  - [CONTAINER_OVERHEAD] - MP4 moov atom and sample tables.
 *  - [ENCODER_VARIANCE]   - hardware VBR overshoot. Calibrate this from device
 *                           measurements; 1.08 is a starting point, not a
 *                           measured constant.
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

    /** Used when the probe could not read a frame count. */
    private const val DEFAULT_FRAME_RATE = 30.0
    private const val MIN_FRAME_RATE = 15.0
    private const val MAX_FRAME_RATE = 120.0

    /**
     * How much more H.264 bitrate an HEVC/VP9/AV1 source needs to keep its
     * quality. These codecs are roughly 30-50% more efficient, so matching what
     * the viewer already has means giving the AVC output more bits than the
     * source figure alone would suggest.
     */
    private const val EFFICIENT_CODEC_CEILING_FACTOR = 1.30

    /**
     * Media3 can transmux instead of transcode when nothing about the video
     * changes. In practice `DefaultEncoderFactory.videoNeedsEncoding()` already
     * forces a transcode because custom `VideoEncoderSettings` are supplied, so
     * this nudge is belt-and-braces. It costs a pointless GPU resample and
     * produces non-16-aligned sizes; remove it only once a device test confirms
     * the requested bitrate reaches the encoder without it.
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

        val sourceShortEdge = min(info.width, info.height)
        val requestedShortEdge = min(sourceShortEdge, preset.maxShortEdge)

        val targetShortEdge = if (
            requestedShortEdge == sourceShortEdge &&
            sourceShortEdge > FORCE_TRANSCODE_DELTA_PX + 2
        ) {
            (sourceShortEdge - FORCE_TRANSCODE_DELTA_PX).toEvenAtLeastTwo()
        } else {
            requestedShortEdge.toEvenAtLeastTwo()
        }

        // The short edge is the width on portrait footage and the height on
        // landscape or square footage. Both edges are needed for the pixel count
        // the quality ceiling is built from.
        val targetWidth: Int
        val targetHeight: Int
        if (info.height > info.width) {
            targetWidth = targetShortEdge
            targetHeight = (info.height.toDouble() * targetShortEdge / info.width.toDouble())
                .roundToInt()
                .toEvenAtLeastTwo()
        } else {
            targetHeight = targetShortEdge
            targetWidth = (info.width.toDouble() * targetShortEdge / info.height.toDouble())
                .roundToInt()
                .toEvenAtLeastTwo()
        }

        val frameRate = info.frameRate
            .takeIf { it > 0.0 }
            ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
            ?: DEFAULT_FRAME_RATE
        val codecFactor = if (info.usesEfficientCodec) EFFICIENT_CODEC_CEILING_FACTOR else 1.0

        val qualityCeiling = (
            targetWidth.toDouble() * targetHeight.toDouble() *
                frameRate * preset.qualityBitsPerPixel * codecFactor
            ).toInt()

        val requestedVideoBitrate =
            (sourceTotalBitrate * preset.sourceBitrateFactor).toInt() - outputAudioBitrate
        val lowerBound = (sourceTotalBitrate * preset.minSourceShare).toInt()
        val upperBound = min(qualityCeiling, preset.bitrateCeiling)

        // median(requested, lowerBound, upperBound). All three are strictly
        // ordered across presets, so the result is too. Do not add a shared
        // absolute floor here: that is what collapsed the presets before v0.8.3.
        val videoBitrate = min(max(requestedVideoBitrate, lowerBound), upperBound)

        val outputTotalBitrate = videoBitrate + outputAudioBitrate

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
            targetWidth = targetWidth,
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
