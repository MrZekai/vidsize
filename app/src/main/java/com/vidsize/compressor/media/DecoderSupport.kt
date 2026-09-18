package com.vidsize.compressor.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
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
 * This object measures the gap. It is the mirror of [EncoderSupport]: the same
 * [MediaCodecList] query, asked of decoders, about the source. Its answer is
 * diagnostic only. Codec capability tables are frequently incomplete; actual
 * Transformer initialization is the eligibility test.
 *
 * ## The one rule this file follows
 *
 * **A query never refuses a job.** A device that will not answer questions
 * about its codecs, an exotic container, or a decoder hidden behind an alias
 * can all make this API say No while a real decode succeeds. Every verdict is
 * retained for diagnostics and UI context, then the engine tries the source.
 */
object DecoderSupport {

    private const val DIAGNOSTIC_TAG = "VidsizeCodec"
    private const val UHD_WIDTH = 3_840
    private const val UHD_HEIGHT = 2_160
    private const val UHD_FRAME_RATE = 30.0

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
         * size. This requests the fallback notice; it does not block the job.
         */
        UNSUPPORTED,

        /** The question could not be answered. Treated as permission to try. */
        UNKNOWN,
        ;

        /** False only when every queried decoder gave a definite No. */
        val precheckPassed: Boolean get() = this != UNSUPPORTED
    }

    /** One decoder's own answer to the 4K questions used during field QA. */
    data class DecoderDiagnostic(
        val codecName: String,
        val mime: String,
        val supportedWidths: String,
        val supportedHeights: String,
        val supports4kLandscape: Boolean?,
        val supports4kPortrait: Boolean?,
        val supports4kLandscape30: Boolean?,
        val hardwareAccelerated: Boolean?,
    )

    /**
     * Full device decoder inventory for the hidden diagnostics screen.
     *
     * This deliberately uses [MediaCodecList.ALL_CODECS]. Some vendors hide
     * specialised, alias or software codecs from the regular list. Seeing both
     * the ranges and each 4K answer on the affected phone explains which route
     * the engine is likely to use without turning a report into a hard block.
     */
    fun fourKDiagnostics(): List<DecoderDiagnostic> = runCatching {
        buildList {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (!type.startsWith("video/", ignoreCase = true)) continue
                    val video = runCatching {
                        info.getCapabilitiesForType(type).videoCapabilities
                    }.getOrNull() ?: continue

                    val row = DecoderDiagnostic(
                        codecName = info.name,
                        mime = type,
                        supportedWidths = runCatching {
                            video.supportedWidths.toString()
                        }.getOrDefault("unknown"),
                        supportedHeights = runCatching {
                            video.supportedHeights.toString()
                        }.getOrDefault("unknown"),
                        supports4kLandscape = runCatching {
                            video.isSizeSupported(UHD_WIDTH, UHD_HEIGHT)
                        }.getOrNull(),
                        supports4kPortrait = runCatching {
                            video.isSizeSupported(UHD_HEIGHT, UHD_WIDTH)
                        }.getOrNull(),
                        supports4kLandscape30 = runCatching {
                            video.areSizeAndRateSupported(
                                UHD_WIDTH,
                                UHD_HEIGHT,
                                UHD_FRAME_RATE,
                            )
                        }.getOrNull(),
                        hardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            runCatching { info.isHardwareAccelerated }.getOrNull()
                        } else {
                            null
                        },
                    )
                    add(row)
                    Log.i(DIAGNOSTIC_TAG, row.logLine())
                }
            }
        }.sortedWith(compareBy(DecoderDiagnostic::mime, DecoderDiagnostic::codecName))
    }.getOrElse { throwable ->
        Log.w(DIAGNOSTIC_TAG, "4K decoder inventory failed", throwable)
        emptyList()
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
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
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
            // ALL_CODECS, or a decoder exposed only when it is initialized,
            // would look identical here. The engine tries either way.
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

    private fun DecoderDiagnostic.logLine(): String =
        "$codecName $mime W=$supportedWidths H=$supportedHeights " +
            "4K_land=$supports4kLandscape 4K_port=$supports4kPortrait " +
            "4K@30=$supports4kLandscape30 hw=$hardwareAccelerated"
}
