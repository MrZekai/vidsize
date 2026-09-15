package com.vidsize.compressor.media

import kotlin.math.abs

/**
 * Decides whether a file the encoder just produced is the file that was asked
 * for.
 *
 * ## The defect this exists to stop (QA NEW-01, High)
 *
 * A 1080x1920 portrait source was exported as a file coded 1088x1080 carrying a
 * -90 degree display matrix, so it displays as 1080x1088: the whole 9:16 frame
 * squeezed into a near-square. Everyone in the picture is 57% of their real
 * height.
 *
 * The app showed a green tick, "13.4 MB -> 9.8 MB · 27% smaller", and saved it
 * to the gallery. Nothing anywhere in the pipeline looked at the file after it
 * was written, so a geometrically destroyed video and a perfect one were
 * indistinguishable to the code.
 *
 * ## Why this is a check and not a fix
 *
 * The squash happens inside the platform encoder, underneath Media3's own
 * fallback. Vidsize asks for 1080x1920; the device's encoder cannot do it,
 * Media3 quietly reshapes the request into something the encoder accepts, and
 * the result is whatever that reshaping produced. There is no reliable way to
 * predict from the encoder's advertised capabilities which devices will do
 * this - the same capability query that said 1080x1920 was supported is what
 * produced the failure.
 *
 * So the pipeline stops trusting the request and starts measuring the result.
 * [CompressionEngine] already tries progressively safer encoder configurations;
 * a geometry mismatch now counts as that attempt failing, which sends the job
 * to the next rung. A device that squashes at 1080x1920 gets a correct 720x1280
 * file instead of a broken 1080x1088 one, and a device that cannot produce a
 * correct frame at any rung gets an honest error dialog rather than a false
 * success.
 *
 * ## Why the aspect ratio and not the exact dimensions
 *
 * Exact dimensions cannot be required: the ladder deliberately snaps to encoder
 * alignment (1080 -> 1088) and deliberately caps the resolution on later rungs,
 * and both of those produce a correct video at a different size. What must
 * never change is the SHAPE. A viewer notices a stretched face instantly and
 * does not notice eight pixels of alignment at all.
 */
object OutputVerification {

    /**
     * How far the output's display aspect ratio may drift from the source's.
     *
     * Sized from the two things that legitimately move it. Encoder alignment
     * changes one edge by at most 15 pixels, which on a 1080-wide frame is 1.4%;
     * even-number rounding in the planner moves an edge by at most 1 pixel.
     * Anything beyond 2% is not arithmetic, it is a reshaped picture.
     *
     * The failure this catches is nowhere near the boundary: the NEW-01 output
     * was off by 76%.
     */
    const val ASPECT_TOLERANCE = 0.02

    /**
     * True when [outputWidth] x [outputHeight] has the same shape as
     * [sourceWidth] x [sourceHeight].
     *
     * All four values must be the dimensions as DISPLAYED - that is, after any
     * rotation metadata has been applied. [VideoProbe] normalises rotation for
     * exactly this reason: the NEW-01 output was only wrong once its -90 degree
     * display matrix was taken into account, and comparing coded dimensions
     * would have called it correct.
     *
     * Non-positive dimensions answer false. A frame with no size is not a frame
     * whose shape can be vouched for.
     */
    fun aspectMatches(
        sourceWidth: Int,
        sourceHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): Boolean {
        if (sourceWidth <= 0 || sourceHeight <= 0) return false
        if (outputWidth <= 0 || outputHeight <= 0) return false

        val source = sourceWidth.toDouble() / sourceHeight.toDouble()
        val output = outputWidth.toDouble() / outputHeight.toDouble()
        return abs(output / source - 1.0) <= ASPECT_TOLERANCE
    }

    /**
     * How far off the shape is, as a share of the source's aspect ratio, for the
     * diagnostic line. 0.76 means the output is 76% the wrong shape.
     *
     * Returns 0.0 when either pair is unusable, so the caller never has to guard
     * a division it did not perform.
     */
    fun aspectDrift(
        sourceWidth: Int,
        sourceHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): Double {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 0.0
        if (outputWidth <= 0 || outputHeight <= 0) return 0.0
        val source = sourceWidth.toDouble() / sourceHeight.toDouble()
        val output = outputWidth.toDouble() / outputHeight.toDouble()
        return abs(output / source - 1.0)
    }
}

/**
 * Thrown when an encode finished but produced a file that is not the shape of
 * the source.
 *
 * Caught by [CompressionEngine]'s attempt ladder exactly like an encoder
 * refusal, because it means the same thing: this configuration did not work on
 * this device. It carries the numbers so the hidden diagnostics screen can show
 * what was asked for and what came back.
 */
class OutputGeometryException(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
) : IllegalStateException(
    "Encoder returned ${outputWidth}x$outputHeight for a " +
        "${sourceWidth}x$sourceHeight source " +
        "(shape off by ${
            (
                OutputVerification.aspectDrift(
                    sourceWidth, sourceHeight, outputWidth, outputHeight,
                ) * 100
                ).toInt()
        }%).",
)

/**
 * Thrown when the file an encode produced cannot be read back at all.
 *
 * Treated as a failed attempt rather than a failed job. A muxer that wrote
 * something `MediaMetadataRetriever` cannot open has not produced a video the
 * user can play, and publishing it to their gallery would be worse than trying
 * the next encoder configuration.
 */
class OutputUnreadableException :
    IllegalStateException("The encoded file could not be read back for verification.")
