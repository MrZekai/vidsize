package com.vidsize.compressor.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import kotlin.math.roundToInt

/**
 * What *this* device's video DECODER will accept.
 *
 * ## Why this exists - the 4K field failure
 *
 * A 3840x2160 source, 336 MB over 39 s (~69 Mbps), failed on a real device with:
 *
 * ```
 * EncoderUnsupportedException: Encoder refused 1920x1080.
 * Tried: faithful 1920x1088, 720p-default 1280x720
 *   <- ExportException: Codec exception: CodecInfo{type=VideoDecoder, ...}
 * ```
 *
 * Read the last line. The failing codec is the **decoder**, not the encoder -
 * and every other part of that message was wrong about what happened.
 *
 * [EncoderSupport] was written for the opposite problem and does its job: it
 * asks what the device can *write*. Nothing asked what the device can *read*.
 * That gap produced three separate failures at once:
 *
 *  1. **The retry ladder was useless.** Every rung changes the OUTPUT geometry -
 *     1920x1088, then 1280x720. But the decoder must decode the source at its
 *     full 3840x2160 no matter how small the output is. Three rungs, three
 *     identical decoder failures, several minutes of the user's time, and a
 *     hot phone. There is no output size that rescues a source the device
 *     cannot read.
 *  2. **The error blamed the wrong component.** The user was told their encoder
 *     could not handle the video "even at a lower resolution", which is both
 *     untrue and unactionable.
 *  3. **The app offered the job in the first place.** The selection screen
 *     showed three levels and three estimates for a video it was never going to
 *     be able to open.
 *
 * This object closes the gap. It is the mirror of [EncoderSupport]: the same
 * [MediaCodecList] query, asked of decoders, about the source.
 *
 * ## The one rule this file follows
 *
 * **Only refuse on a definite No.** A device that will not answer questions
 * about its codecs, an exotic container, a mime type nothing claims - all of
 * these return [Verdict.UNKNOWN], and an unknown verdict lets the job run. The
 * cost of a wrong "cannot decode" is a video the user is refused for no reason;
 * the cost of a wrong "can decode" is the failure they already have, no worse
 * than today. Those are not symmetric, so uncertainty always favours trying.
 */
object DecoderSupport {

    /**
     * Frame rate assumed when the probe could not read one. Used only to ask
     * `areSizeAndRateSupported`; a wrong guess here can only make the check
     * abstain, never refuse (see [canDecode]).
     */
    private const val ASSUMED_FRAME_RATE = 30.0

    /** The highest frame rate worth asking about; above this, size alone is asked. */
    private const val MAX_QUERIED_FRAME_RATE = 240.0

    enum class Verdict {
        /** A decoder on this device reports it can read this source. */
        SUPPORTED,

        /**
         * Every decoder that claims this format reports it cannot read this
         * size. This is the only value that blocks a job.
         */
        UNSUPPORTED,

        /** The question could not be answered. Treated as permission to try. */
        UNKNOWN,
        ;

        /** True unless this device gave a definite No. */
        val allowsAttempt: Boolean get() = this != UNSUPPORTED
    }

    /**
     * Can this device decode [width] x [height] of [mime]?
     *
     * @param mime the video track's mime type, from `MediaExtractor` - not the
     *   container's. A container mime says nothing about the codec inside it.
     * @param width coded width, as the extractor reports it.
     * @param height coded height, as the extractor reports it.
     * @param frameRate source frame rate, or 0.0 when unknown.
     */
    fun canDecode(mime: String?, width: Int, height: Int, frameRate: Double = 0.0): Verdict {
        if (mime.isNullOrBlank() || !mime.startsWith("video/")) return Verdict.UNKNOWN
        if (width <= 0 || height <= 0) return Verdict.UNKNOWN

        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var sawDecoderForMime = false

            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                val supportsMime = info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                if (!supportsMime) continue

                val video = runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities
                }.getOrNull() ?: continue

                sawDecoderForMime = true
                if (accepts(video, width, height, frameRate)) return@runCatching Verdict.SUPPORTED
            }

            // No decoder claims this mime type at all. That is not the same as a
            // decoder saying no: an unusual codec the platform hides from
            // REGULAR_CODECS, or a software decoder Media3 supplies itself,
            // would look identical here. Let it try.
            if (!sawDecoderForMime) Verdict.UNKNOWN else Verdict.UNSUPPORTED
        }.getOrDefault(Verdict.UNKNOWN)
    }

    /**
     * Whether one decoder accepts this frame.
     *
     * Both orientations are asked. `isSizeSupported` is not symmetric - a
     * decoder may advertise 3840x2160 and refuse 2160x3840 - and a portrait 4K
     * recording is stored coded-landscape with rotation metadata often enough
     * that judging it on the stored orientation alone would refuse videos the
     * device can in fact read.
     *
     * The frame rate is asked about only when it is plausible. A probe that
     * returned nonsense, or a high-speed clip whose nominal rate the decoder
     * does not advertise, must not be the reason a job is blocked - so an
     * implausible rate falls back to the size-only question.
     */
    private fun accepts(
        video: MediaCodecInfo.VideoCapabilities,
        width: Int,
        height: Int,
        frameRate: Double,
    ): Boolean {
        val rate = when {
            frameRate > 0.0 && frameRate <= MAX_QUERIED_FRAME_RATE -> frameRate
            else -> ASSUMED_FRAME_RATE
        }.roundToInt().coerceAtLeast(1)

        val sizeAndRate = runCatching {
            video.areSizeAndRateSupported(width, height, rate.toDouble()) ||
                video.areSizeAndRateSupported(height, width, rate.toDouble())
        }.getOrDefault(false)
        if (sizeAndRate) return true

        // Rate refused, or the query threw. Fall back to size alone: a decoder
        // that can open the frame but not at the source's nominal rate will
        // still decode it here, because Transformer is not playing in real time.
        return runCatching {
            video.isSizeSupported(width, height) || video.isSizeSupported(height, width)
        }.getOrDefault(false)
    }
}
