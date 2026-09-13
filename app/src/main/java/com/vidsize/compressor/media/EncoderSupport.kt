package com.vidsize.compressor.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size
import kotlin.math.max
import kotlin.math.min

/**
 * What *this* device's H.264 encoder will actually accept.
 *
 * ## Why this exists - QA v0.8.7 BUG-05 (Critical)
 *
 * v0.8.7 handed the encoder whatever geometry the aspect-ratio arithmetic
 * produced and trusted Media3's fallback to sort it out. On the test device that
 * silently failed for 4K and for a wide band of common SD/qHD sources
 * (3840x2160, 960x540, 862x480, 856x480, 854x480, 320x240) while 1920x1080,
 * 1080x1920, 1280x720 and 640x480 worked. The user got no dialog, no toast and
 * no file.
 *
 * Two things were wrong and both are addressed here.
 *
 * **1. Unaligned geometry.** `Presentation.createForHeight` derives the other
 * edge by floating-point arithmetic, so a 854x480 source produced 850x478 - a
 * width that is not a multiple of 4, let alone the 16 that most hardware AVC
 * encoders report as their alignment. An encoder that cannot honour the request
 * may `configure()` and then abort on the first buffer, which is exactly the
 * "encoder starts and then aborts" signature in the QA logcat.
 *
 * **2. Capability blindness.** Nothing asked the device what it could encode.
 * A phone whose AVC encoder tops out at 1920x1088 was still asked for a 4K
 * frame, and a phone whose encoder has a minimum supported height was still
 * asked for 238 lines.
 *
 * Every number handed to the encoder now comes from
 * [MediaCodecInfo.CodecCapabilities.getVideoCapabilities] for the codec that
 * will actually do the work: alignment, the supported width/height ranges, the
 * supported bitrate range, and a real [MediaCodecInfo.VideoCapabilities.isSizeSupported]
 * check rather than an assumption.
 *
 * Everything degrades to a conservative default if the query fails, because a
 * device that will not answer questions about its codecs must still compress
 * video.
 */
object EncoderSupport {

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    /**
     * Alignment assumed when the device does not report one.
     *
     * 2, not 16, on purpose. Almost every Android AVC encoder reports an
     * alignment of 2 and handles 1080 lines through H.264's own cropping
     * metadata, so forcing 16 would turn a faithful 1920x1080 output into
     * 1920x1088 and re-introduce the resolution drift of QA v0.8.7 BUG-03 in a
     * new form. 16-alignment is available as an explicit retry
     * ([SAFE_ALIGNMENT]) for the case where the device rejects its own
     * advertised geometry.
     */
    private const val FALLBACK_ALIGNMENT = 2

    /**
     * Macroblock alignment, used only as a retry after a failed export. Losing
     * up to 8px on an edge is a far better outcome than the silent no-op of QA
     * v0.8.7 BUG-05.
     */
    const val SAFE_ALIGNMENT = 16

    /** Conservative geometry bounds used when the query fails outright. */
    private const val FALLBACK_MIN_EDGE = 64
    private const val FALLBACK_MAX_EDGE = 1920

    private const val FALLBACK_MIN_BITRATE = 64_000
    private const val FALLBACK_MAX_BITRATE = 40_000_000

    /**
     * Short-edge ladder used to step down when the requested frame is larger
     * than the device can encode. 4K sources land on 1080p rather than failing.
     */
    private val SHORT_EDGE_LADDER = intArrayOf(2160, 1440, 1080, 720, 540, 480, 360, 240, 144)

    data class Capabilities(
        val widthAlignment: Int,
        val heightAlignment: Int,
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val minBitrate: Int,
        val maxBitrate: Int,
        /**
         * Null when the real capability object could not be obtained. Callers
         * then rely on the ranges and alignment above instead of asking about a
         * specific size.
         */
        val video: MediaCodecInfo.VideoCapabilities?,
    ) {
        fun supportsSize(width: Int, height: Int): Boolean {
            val caps = video ?: return width in minWidth..maxWidth && height in minHeight..maxHeight
            return runCatching { caps.isSizeSupported(width, height) }.getOrDefault(false)
        }
    }

    /**
     * Queried once. Codec capabilities are a property of the device, and
     * [MediaCodecList] is expensive enough that a compression screen switching
     * presets should not repeat it.
     */
    val capabilities: Capabilities by lazy { query() }

    private fun query(): Capabilities = runCatching {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        // Prefer a hardware encoder, but accept a software one rather than
        // reporting no encoder at all on an unusual device.
        var chosen: MediaCodecInfo.VideoCapabilities? = null
        var fallback: MediaCodecInfo.VideoCapabilities? = null

        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(MIME, ignoreCase = true) }) continue
            val video = runCatching {
                info.getCapabilitiesForType(MIME).videoCapabilities
            }.getOrNull() ?: continue

            val hardware = runCatching { info.isHardwareAccelerated }.getOrDefault(false)
            if (hardware && chosen == null) chosen = video
            if (fallback == null) fallback = video
        }

        val video = chosen ?: fallback ?: return@runCatching fallbackCapabilities()

        Capabilities(
            widthAlignment = video.widthAlignment.coerceAtLeast(2),
            heightAlignment = video.heightAlignment.coerceAtLeast(2),
            minWidth = video.supportedWidths.lower.coerceAtLeast(2),
            maxWidth = video.supportedWidths.upper,
            minHeight = video.supportedHeights.lower.coerceAtLeast(2),
            maxHeight = video.supportedHeights.upper,
            minBitrate = video.bitrateRange.lower.coerceAtLeast(1),
            maxBitrate = video.bitrateRange.upper,
            video = video,
        )
    }.getOrElse { fallbackCapabilities() }

    private fun fallbackCapabilities() = Capabilities(
        widthAlignment = FALLBACK_ALIGNMENT,
        heightAlignment = FALLBACK_ALIGNMENT,
        minWidth = FALLBACK_MIN_EDGE,
        maxWidth = FALLBACK_MAX_EDGE,
        minHeight = FALLBACK_MIN_EDGE,
        maxHeight = FALLBACK_MAX_EDGE,
        minBitrate = FALLBACK_MIN_BITRATE,
        maxBitrate = FALLBACK_MAX_BITRATE,
        video = null,
    )

    /**
     * The frame this device will actually encode, as close to [width] x [height]
     * as its alignment and limits allow.
     *
     * The source aspect ratio is the thing being protected: the requested size
     * is snapped to the codec's alignment grid (which moves each edge by at most
     * half the alignment), and if the result is still unsupported the whole
     * frame is stepped down the [SHORT_EDGE_LADDER] with the ratio recomputed at
     * each rung rather than one edge being clipped.
     */
    fun fitToEncoder(
        width: Int,
        height: Int,
        minAlignment: Int = 1,
        maxShortEdge: Int = Int.MAX_VALUE,
    ): Size {
        val caps = capabilities
        if (width <= 0 || height <= 0) return Size(caps.minWidth, caps.minHeight)

        val widthAlignment = max(caps.widthAlignment, minAlignment)
        val heightAlignment = max(caps.heightAlignment, minAlignment)

        val requestedShortEdge = min(FrameAlignment.shortEdgeOf(width, height), maxShortEdge)

        // Rung 0 is the requested size itself; the ladder only supplies the
        // retries, and only rungs that are genuine reductions.
        val rungs = buildList {
            add(requestedShortEdge)
            SHORT_EDGE_LADDER.forEach { if (it < requestedShortEdge) add(it) }
        }

        rungs.forEach { shortEdge ->
            val candidate = snap(shortEdge, width, height, caps, widthAlignment, heightAlignment)
            if (candidate != null && caps.supportsSize(candidate.width, candidate.height)) {
                return candidate
            }
        }

        // Nothing on the ladder was accepted. Fall back to the largest aligned
        // frame inside the reported ranges, which every encoder accepts by
        // definition of those ranges.
        val w = FrameAlignment.down(min(width, caps.maxWidth), widthAlignment)
            .coerceAtLeast(FrameAlignment.up(caps.minWidth, widthAlignment))
        val h = FrameAlignment.down(min(height, caps.maxHeight), heightAlignment)
            .coerceAtLeast(FrameAlignment.up(caps.minHeight, heightAlignment))
        return Size(w, h)
    }

    private fun snap(
        shortEdge: Int,
        width: Int,
        height: Int,
        caps: Capabilities,
        widthAlignment: Int,
        heightAlignment: Int,
    ): Size? = FrameAlignment.fit(
        width = width,
        height = height,
        shortEdge = shortEdge,
        widthAlignment = widthAlignment,
        heightAlignment = heightAlignment,
        minWidth = caps.minWidth,
        maxWidth = caps.maxWidth,
        minHeight = caps.minHeight,
        maxHeight = caps.maxHeight,
    )?.let { (w, h) -> Size(w, h) }

    /** Keeps the encoder's own bitrate range from being violated. */
    fun clampBitrate(bitrate: Int): Int {
        val caps = capabilities
        return bitrate.coerceIn(caps.minBitrate, caps.maxBitrate)
    }
}
