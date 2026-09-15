package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.TargetVerdict
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

    /**
     * Hardware VBR overshoot.
     *
     * ## v0.8.8 recalibration - QA v0.8.7 BUG-09
     *
     * 1.08 was documented as "a starting point, not a measured constant", and
     * the QA pass measured it. Both runs overshot in the same direction:
     *
     *  - 1920x1080: estimated ~15.7 MB, produced 17.4 MB (+10.8%)
     *  - 1280x720:  estimated ~4.8 MB,  produced 5.6 MB  (+16.7%)
     *
     * A one-directional error is a calibration error, not noise: at 1.08 the
     * estimate was systematically optimistic, which is the worse direction for
     * a storage tool because the user plans around it. Multiplying the old
     * factor through the measured overshoot gives 1.08 x 1.108 = 1.197 and
     * 1.08 x 1.167 = 1.260; 1.22 sits between them, which leaves the 1080p case
     * marginally conservative and the 720p case marginally optimistic instead of
     * both being optimistic.
     *
     * This is still a *device* characteristic measured on one device. It is
     * deliberately a single named constant so the next QA pass can move it
     * again, and [CompressionPlan.estimatedOutputBytes] is still presented as
     * "≈" with an "estimates only" note rather than as a promise.
     */
    private const val ENCODER_VARIANCE = 1.22

    private const val SHORT_CLIP_SECONDS = 4.0

    /** Short clips overshoot more, so this moves with [ENCODER_VARIANCE]. */
    private const val SHORT_CLIP_VARIANCE = 1.52

    /**
     * A preset must beat this share of the source to be worth running.
     *
     * ## Why 0.85 and not the 0.92 this used to be
     *
     * At 0.92 a job that saved 9% counted as viable. On a 17 MB clip that is
     * 1.4 MB, bought with a minute of waiting, a warm phone, and a re-encode the
     * user cannot undo - and the result screen then celebrated it with a party
     * emoji. The app was technically right and practically wrong: it had done
     * what it said, and the user had lost quality for nothing they would notice
     * in their storage.
     *
     * 0.85 means the app only offers to run when it can take at least a seventh
     * off. Below that it now says so up front, which is a better answer than a
     * result screen nobody is happy with. Sources that fall below the bar are
     * usually already-efficient ones, and [ALREADY_EFFICIENT_BPP] catches the
     * subset of those that would also have failed outright.
     */
    private const val VIABLE_RATIO = 0.85

    /**
     * Bits per pixel per frame below which a source counts as already encoded
     * efficiently.
     *
     * ## The failure this exists to stop
     *
     * A 1080x1920 clip of 51s at 9.6 MB is 1.5 Mbps, which is 0.024 bpp. The
     * planner asked for 85% of that and predicted 8.3 MB; the hardware encoder
     * returned a file LARGER than the source and the job failed after the user
     * had waited for it. Straight-up broken trust: the app promised 8.3 MB and
     * then said it could not do better than 9.6.
     *
     * The fixed [ENCODER_VARIANCE] cannot model this. Hardware VBR tracks a
     * requested bitrate well when there is slack in the source and overshoots
     * badly when there is none - and at 0.024 bpp there is none. Asking for a
     * lower number does not make the encoder produce one.
     *
     * A phone camera writes 1080p at roughly 0.08-0.15 bpp, so this threshold
     * sits far below anything straight off a camera and catches the case it is
     * meant to: video that has already been through WhatsApp, Instagram or
     * another compressor.
     *
     * Only applied when the preset keeps the source resolution. Dropping the
     * frame size changes the arithmetic completely - fewer pixels need fewer
     * bits - which is exactly why "Smaller" and "Smallest" remain available and
     * honest on a source this tight.
     */
    private const val ALREADY_EFFICIENT_BPP = 0.035

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
     * ## v0.8.8: the 2px "force transcode" nudge is gone
     *
     * Up to v0.8.7 the planner subtracted 2px from the short edge whenever the
     * preset was not already downscaling, to make sure Media3 transcoded rather
     * than transmuxed. It cost far more than it bought (QA v0.8.7 BUG-03):
     *
     *  - Every output lost 4px horizontally and 2px vertically, so a 1920x1080
     *    source became 1916x1078 and its display aspect ratio moved from 16:9
     *    to 958:539. The "1080p" label the app itself showed was then wrong.
     *  - The loss compounded per pass: 1280x720 -> 1276x718 -> 1272x716.
     *  - The derived long edge was arbitrary rather than aligned. A 854x480
     *    source produced 850x478, and a width that is not even a multiple of 4
     *    is a plausible cause of the encoder aborting in QA v0.8.7 BUG-05.
     *
     * It was never needed. `TransformerUtil.shouldTranscodeVideo` transcodes
     * when either of two conditions holds, and Vidsize satisfies both on every
     * job:
     *
     *  1. `encoderFactory.videoNeedsEncoding()` is true, because
     *     [CompressionEngine] always supplies non-default `VideoEncoderSettings`
     *     (a bitrate and an I-frame interval).
     *  2. `editedMediaItem.effects.videoEffects` is non-empty, because a
     *     `Presentation` effect is always attached.
     *
     * So the output now keeps the source geometry exactly, and the only thing
     * that may move it is the device encoder's own alignment, applied in
     * [EncoderSupport.fitToEncoder] where it belongs.
     */
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

        // No nudge, no pixel loss: the short edge is the preset's cap or the
        // source, whichever is smaller, and nothing else.
        val targetShortEdge = requestedShortEdge.toEvenAtLeastTwo()

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

        // Refuse BEFORE the encode, not after a job the user waited through.
        val keepsResolution = targetShortEdge >= sourceShortEdge
        val sourceBpp = if (frameRate > 0.0) {
            sourceTotalBitrate / (info.width.toDouble() * info.height.toDouble() * frameRate)
        } else {
            Double.MAX_VALUE
        }
        val alreadyEfficient = keepsResolution && sourceBpp < ALREADY_EFFICIENT_BPP

        return CompressionPlan(
            preset = preset,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            videoBitrate = videoBitrate,
            audioBitrate = outputAudioBitrate,
            estimatedOutputBytes = estimatedBytes,
            viable = savesEnough && encodable && !alreadyEfficient,
            alreadyEfficient = alreadyEfficient,
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

    // ========================================================================
    // Size targets
    // ========================================================================

    /**
     * Short edges the target planner is allowed to step down to, largest first.
     *
     * A given bitrate always looks better spread over fewer pixels. When the
     * requested size does not leave enough bits for the source resolution, the
     * honest move is to hand back a smaller, clean frame rather than a full-size
     * blocky one - so this walks down until the bits per pixel are acceptable.
     */
    private val TARGET_SHORT_EDGES = intArrayOf(2160, 1440, 1080, 720, 480, 360)

    /**
     * Bits per pixel per frame below which the target planner steps the
     * resolution down instead of accepting the frame size.
     *
     * Chosen just under SMALLEST's 0.085: at this density the picture is
     * visibly compressed but still clean, which is the correct trade when the
     * user has explicitly asked for a hard size ceiling. Note this is a floor
     * for CHOOSING a resolution, not a floor for the encode - once the ladder
     * bottoms out at 360p the planner accepts whatever the budget allows, and
     * only [MIN_ENCODABLE_VIDEO_BITRATE] can refuse outright.
     */
    private const val TARGET_MIN_BPP = 0.075

    /**
     * Audio floor in target mode, below the preset floor.
     *
     * At a 10 MB ceiling on a long clip, a 64 kbps audio track can be a double
     * digit share of the whole budget. Speech stays intelligible at 48 kbps AAC,
     * and an audible-but-thin soundtrack beats a video the app refuses to make.
     */
    private const val TARGET_MIN_AUDIO_BITRATE = 48_000

    /** Aim slightly under the ceiling: landing exactly on it is landing over it. */
    private const val TARGET_HEADROOM = 0.97

    /**
     * Plans an encode that aims at a requested output size instead of a quality
     * level.
     *
     * ## Why this is the inverse of [plan] and not a new algorithm
     *
     * [plan] walks forward: preset -> bitrate -> predicted bytes. This walks the
     * same chain backwards - bytes -> bitrate -> resolution - through the same
     * [CONTAINER_OVERHEAD] and the same encoder variance. Using one set of
     * constants in both directions is what keeps the estimate the user sees on
     * this screen consistent with the estimate they see on the other one.
     *
     * ## Why the estimate is recomputed forward at the end
     *
     * The ladder and the clamps can move the bitrate away from the budget. If
     * the screen showed the requested size as the estimate it would be showing
     * the user their own input back, which tells them nothing - so the last step
     * runs the forward arithmetic on the numbers actually chosen.
     *
     * The returned plan is not a promise. Hardware VBR does not hit a requested
     * bitrate exactly, which is why [CompressionEngine] measures the result and
     * re-encodes when it misses. This gets the first attempt close enough that
     * one correction is usually the last one.
     */
    fun planForTarget(info: VideoInfo, targetBytes: Long): CompressionPlan {
        require(info.durationMs > 0L) { "Video duration must be positive." }
        require(info.width > 0 && info.height > 0) { "Video dimensions must be positive." }
        require(targetBytes > 0L) { "Target size must be positive." }

        val sourceTotalBitrate = inferSourceTotalBitrate(info)
        val sourceShortEdge = min(info.width, info.height)
        val frameRate = info.frameRate
            .takeIf { it > 0.0 }
            ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
            ?: DEFAULT_FRAME_RATE
        val variance = if (info.durationSeconds < SHORT_CLIP_SECONDS) {
            SHORT_CLIP_VARIANCE
        } else {
            ENCODER_VARIANCE
        }

        // Asking for a ceiling the file is already under is a question, not a
        // mistake. Answer it here rather than letting the user wait for an
        // encode that cannot improve on what they have.
        if (info.sourceBytes > 0L && targetBytes >= (info.sourceBytes * VIABLE_RATIO).toLong()) {
            return refusedTarget(
                info, targetBytes, sourceShortEdge, TargetVerdict.NOT_SMALLER_THAN_SOURCE,
            )
        }

        // bytes -> bits -> bits per second, minus what the container and the
        // encoder's own overshoot will claim.
        val budgetTotalBitrate = (
            targetBytes * 8.0 * TARGET_HEADROOM /
                (info.durationSeconds * CONTAINER_OVERHEAD * variance)
            ).toInt()

        val audioBitrate = if (info.hasAudio) {
            val budgetShare = (budgetTotalBitrate * MAX_AUDIO_SOURCE_SHARE).toInt()
            val sourceShare = (sourceTotalBitrate * MAX_AUDIO_SOURCE_SHARE).toInt()
            min(
                CompressionPreset.BALANCED.audioBitrate,
                max(TARGET_MIN_AUDIO_BITRATE, min(budgetShare, sourceShare)),
            )
        } else {
            0
        }

        // Never ask for more bits than the source carries: that inflates the
        // file and buys nothing. The ceiling is the source, not the budget.
        val videoBudget = min(
            budgetTotalBitrate - audioBitrate,
            (sourceTotalBitrate * VIABLE_RATIO).toInt() - audioBitrate,
        )

        val shortEdge = chooseShortEdgeForBudget(
            sourceShortEdge = sourceShortEdge,
            sourceWidth = info.width,
            sourceHeight = info.height,
            frameRate = frameRate,
            videoBudget = videoBudget,
        )
        val (targetWidth, targetHeight) = frameFor(info, shortEdge)

        if (videoBudget < MIN_ENCODABLE_VIDEO_BITRATE) {
            return refusedTarget(
                info, targetBytes, sourceShortEdge, TargetVerdict.TOO_SMALL_FOR_DURATION,
            )
        }

        val outputTotalBitrate = videoBudget + audioBitrate
        val estimatedBytes = ((outputTotalBitrate * info.durationSeconds / 8.0) *
            CONTAINER_OVERHEAD * variance)
            .toLong()
            .coerceAtLeast(1L)

        return CompressionPlan(
            preset = presetLabelFor(shortEdge),
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            videoBitrate = videoBudget,
            audioBitrate = audioBitrate,
            estimatedOutputBytes = estimatedBytes,
            viable = true,
            targetBytes = targetBytes,
            targetVerdict = TargetVerdict.REACHABLE,
        )
    }

    /**
     * The plan for the next attempt after one missed its target.
     *
     * Scales the video bitrate by how far the last attempt overshot. Hardware
     * VBR error is broadly proportional - a run that came in 20% over at 3 Mbps
     * lands close when asked for 2.4 - so a single multiply usually converges in
     * one step, and [CompressionEngine] caps how many steps are allowed.
     *
     * The frame size is deliberately NOT reduced between passes. The user chose
     * a size, not a resolution; silently shrinking the picture on the second
     * attempt would change what they get without telling them. If the bitrate
     * falls below what an encoder accepts, the attempt is abandoned and the best
     * result so far is kept.
     *
     * Returns null when no useful correction exists.
     */
    fun correctedForTarget(
        info: VideoInfo,
        plan: CompressionPlan,
        actualBytes: Long,
        allowResolutionDrop: Boolean = false,
    ): CompressionPlan? {
        val target = plan.targetBytes ?: return null
        if (actualBytes <= 0L || actualBytes <= target) return null

        val factor = (target.toDouble() / actualBytes.toDouble()) * TARGET_HEADROOM
        val corrected = (plan.videoBitrate * factor).toInt()

        val frameRate = info.frameRate
            .takeIf { it > 0.0 }
            ?.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
            ?: DEFAULT_FRAME_RATE

        // The frame the corrected budget can actually carry.
        //
        // Bitrate alone runs out of room. On a long clip with a small ceiling the
        // second correction can land below what any hardware encoder accepts, and
        // before this the job simply gave up and reported the target as missed -
        // which is the app failing at the one thing that mode exists to do.
        //
        // Fewer pixels need fewer bits, so the honest move is to hand back a
        // smaller, clean frame rather than refuse. The same ladder and the same
        // bits-per-pixel floor that chose the first frame choose this one, so a
        // dropped resolution is never a surprise shape - only a smaller one, and
        // the screen already says which.
        val shortEdge = if (allowResolutionDrop) {
            chooseShortEdgeForBudget(
                sourceShortEdge = min(info.width, info.height),
                sourceWidth = info.width,
                sourceHeight = info.height,
                frameRate = frameRate,
                videoBudget = corrected,
            )
        } else {
            min(plan.targetWidth, plan.targetHeight)
        }
        val (width, height) = frameFor(info, shortEdge)
        val frameChanged = width != plan.targetWidth || height != plan.targetHeight

        if (corrected < MIN_ENCODABLE_VIDEO_BITRATE) return null
        // A correction that barely moves is a wasted encode - unless the frame
        // is changing too, in which case the encode produces a genuinely
        // different file even at a similar bitrate.
        if (!frameChanged && corrected >= (plan.videoBitrate * 0.98).toInt()) return null

        return plan.copy(
            targetWidth = width,
            targetHeight = height,
            videoBitrate = corrected,
            estimatedOutputBytes = (plan.estimatedOutputBytes * factor).toLong().coerceAtLeast(1L),
        )
    }

    /**
     * The largest rung whose bits per pixel clear [TARGET_MIN_BPP], or the
     * smallest rung when none of them do.
     */
    private fun chooseShortEdgeForBudget(
        sourceShortEdge: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        frameRate: Double,
        videoBudget: Int,
    ): Int {
        val rungs = TARGET_SHORT_EDGES.filter { it <= sourceShortEdge }
        if (rungs.isEmpty() || videoBudget <= 0) {
            return min(sourceShortEdge, TARGET_SHORT_EDGES.last()).toEvenAtLeastTwo()
        }
        val longRatio = max(sourceWidth, sourceHeight).toDouble() /
            min(sourceWidth, sourceHeight).toDouble()
        rungs.forEach { edge ->
            val pixels = edge.toDouble() * (edge * longRatio)
            if (videoBudget / (pixels * frameRate) >= TARGET_MIN_BPP) {
                return edge.toEvenAtLeastTwo()
            }
        }
        return rungs.last().toEvenAtLeastTwo()
    }

    /** Frame size for a short edge, preserving the source aspect ratio. */
    private fun frameFor(info: VideoInfo, shortEdge: Int): Pair<Int, Int> =
        if (info.height > info.width) {
            shortEdge to (info.height.toDouble() * shortEdge / info.width.toDouble())
                .roundToInt()
                .toEvenAtLeastTwo()
        } else {
            (info.width.toDouble() * shortEdge / info.height.toDouble())
                .roundToInt()
                .toEvenAtLeastTwo() to shortEdge
        }

    /**
     * The preset whose resolution cap matches the frame a target plan settled
     * on, used only as a label.
     *
     * Nothing in the encode path reads it - [CompressionEngine.buildAttempts]
     * works purely from the geometry and bitrate - but [CompressionPlan] and
     * [com.vidsize.compressor.model.CompressionResult] both carry a preset, and
     * a plausible one beats a hardcoded default that would make every target
     * job look like BALANCED in the history.
     */
    private fun presetLabelFor(shortEdge: Int): CompressionPreset = when {
        shortEdge > 720 -> CompressionPreset.BALANCED
        shortEdge > 480 -> CompressionPreset.SMALLER
        else -> CompressionPreset.SMALLEST
    }

    private fun refusedTarget(
        info: VideoInfo,
        targetBytes: Long,
        sourceShortEdge: Int,
        verdict: TargetVerdict,
    ): CompressionPlan {
        val (width, height) = frameFor(info, sourceShortEdge.toEvenAtLeastTwo())
        return CompressionPlan(
            preset = CompressionPreset.BALANCED,
            targetWidth = width,
            targetHeight = height,
            videoBitrate = 0,
            audioBitrate = 0,
            estimatedOutputBytes = targetBytes,
            viable = false,
            targetBytes = targetBytes,
            targetVerdict = verdict,
        )
    }
}
