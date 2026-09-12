package com.vidsize.compressor.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The arithmetic behind "what frame size should we ask the encoder for".
 *
 * Deliberately free of every Android import so it can be unit-tested on the
 * JVM. [EncoderSupport] supplies the device's real numbers; this object decides
 * what to do with them.
 *
 * ## Why this is separate - QA v0.8.7 BUG-03 and BUG-05
 *
 * The v0.8.7 geometry was produced by two pieces of arithmetic that nobody could
 * test: a "-2px to force a transcode" nudge in the planner, and Media3's
 * floating-point derivation of the second edge inside `Presentation`. Together
 * they turned 1920x1080 into 1916x1078 (BUG-03) and 854x480 into 850x478 - a
 * width that is not a multiple of 4, which no hardware AVC encoder is obliged to
 * accept (a plausible cause of BUG-05).
 *
 * Now every frame handed to the encoder comes from [fit], and [fit] is covered
 * by tests.
 */
object FrameAlignment {

    /**
     * Rounds to the nearest multiple of [alignment], never below one multiple.
     *
     * Nearest, not down: rounding down twice (once here, once in Media3) is how
     * the compounding loss in BUG-03 happened.
     */
    fun nearest(value: Int, alignment: Int): Int {
        if (alignment <= 1) return value.coerceAtLeast(1)
        val aligned = (value.toDouble() / alignment).roundToInt() * alignment
        return if (aligned < alignment) alignment else aligned
    }

    /** Largest multiple of [alignment] that is not greater than [value]. */
    fun down(value: Int, alignment: Int): Int {
        if (alignment <= 1) return value.coerceAtLeast(1)
        val aligned = (value / alignment) * alignment
        return if (aligned < alignment) alignment else aligned
    }

    /** Smallest multiple of [alignment] that is not less than [value]. */
    fun up(value: Int, alignment: Int): Int {
        if (alignment <= 1) return value.coerceAtLeast(1)
        return ((value + alignment - 1) / alignment) * alignment
    }

    /**
     * The frame to request: [width] x [height] reduced to a short edge of
     * [shortEdge], snapped to the given alignment, and clamped into the
     * encoder's supported ranges.
     *
     * The source aspect ratio is what is being protected. The long edge is
     * derived from the ratio at the requested short edge and only then snapped,
     * so the deviation from the true ratio is at most half the alignment on one
     * edge - under 1% at every resolution this app handles, and 0% in the common
     * case where both edges are already aligned (1920x1080 with an alignment of
     * 2 comes back as exactly 1920x1080).
     *
     * Returns null when the supplied ranges cannot contain an aligned frame at
     * all, which tells the caller to try the next rung rather than to invent a
     * size.
     */
    @Suppress("LongParameterList")
    fun fit(
        width: Int,
        height: Int,
        shortEdge: Int,
        widthAlignment: Int,
        heightAlignment: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
    ): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || shortEdge <= 0) return null

        val aspect = width.toDouble() / height.toDouble()
        val portrait = height > width
        val longEdge = (shortEdge * max(aspect, 1.0 / aspect)).roundToInt()

        val rawWidth = if (portrait) shortEdge else longEdge
        val rawHeight = if (portrait) longEdge else shortEdge

        val loW = up(minWidth, widthAlignment)
        val hiW = down(maxWidth, widthAlignment)
        val loH = up(minHeight, heightAlignment)
        val hiH = down(maxHeight, heightAlignment)
        if (loW > hiW || loH > hiH) return null

        val outWidth = nearest(rawWidth, widthAlignment).coerceIn(loW, hiW)
        val outHeight = nearest(rawHeight, heightAlignment).coerceIn(loH, hiH)
        if (outWidth < 2 || outHeight < 2) return null

        return outWidth to outHeight
    }

    /** The short edge of a frame, which is the width on portrait footage. */
    fun shortEdgeOf(width: Int, height: Int): Int = min(width, height)
}
