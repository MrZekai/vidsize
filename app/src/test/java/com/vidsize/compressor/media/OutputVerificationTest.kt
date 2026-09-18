package com.vidsize.compressor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape check that stands between the encoder and the user's gallery.
 *
 * Every case here is a real measurement, not an invented one. The rejections
 * come from the QA retest that found NEW-01; the acceptances come from the
 * geometry this app's own planner and encoder ladder legitimately produce.
 */
class OutputVerificationTest {

    // ---- must be rejected ---------------------------------------------------

    /**
     * QA NEW-01, verbatim.
     *
     * Source `h264 1080x1920 SAR 1:1 DAR 9:16`; output `h264 1088x1080 SAR 1:1
     * DAR 136:135` with `side_data Display Matrix rotation=-90 -> displayed
     * 1080x1088`. The whole portrait frame squeezed into a near-square, and the
     * app called it a success.
     */
    @Test
    fun theQaPortraitSquashIsRejected() {
        assertFalse(
            "1080x1920 -> 1080x1088 is the NEW-01 squash and must never pass",
            OutputVerification.aspectMatches(1080, 1920, 1080, 1088),
        )
    }

    /** The same failure on landscape footage: 16:9 flattened to near-square. */
    @Test
    fun aLandscapeSquashIsRejected() {
        assertFalse(
            OutputVerification.aspectMatches(1920, 1080, 1088, 1080),
        )
    }

    /** A portrait source that came back landscape is the shape inverted. */
    @Test
    fun aRotatedOutputIsRejected() {
        assertFalse(
            "1080x1920 -> 1920x1080 is the frame on its side",
            OutputVerification.aspectMatches(1080, 1920, 1920, 1080),
        )
    }

    @Test
    fun unusableDimensionsAreRejected() {
        assertFalse(OutputVerification.aspectMatches(0, 1920, 1080, 1920))
        assertFalse(OutputVerification.aspectMatches(1080, 0, 1080, 1920))
        assertFalse(OutputVerification.aspectMatches(1080, 1920, 0, 1920))
        assertFalse(OutputVerification.aspectMatches(1080, 1920, 1080, 0))
        assertFalse(OutputVerification.aspectMatches(-1080, -1920, 1080, 1920))
    }

    // ---- must be accepted ---------------------------------------------------

    @Test
    fun anIdenticalFrameIsAccepted() {
        assertTrue(OutputVerification.aspectMatches(1080, 1920, 1080, 1920))
    }

    /**
     * The encoder ladder's second rung snaps the frame to a multiple of 16, so
     * 1080 becomes 1088. That is a correct video eight pixels wider, and
     * rejecting it would fail the very fallback that rescues the squash.
     */
    @Test
    fun encoderAlignmentIsAccepted() {
        assertTrue(
            "1080x1920 -> 1088x1920 is alignment, not reshaping",
            OutputVerification.aspectMatches(1080, 1920, 1088, 1920),
        )
    }

    /** The ladder's last rung caps the short edge at 720. Same shape, fewer pixels. */
    @Test
    fun aProportionalDownscaleIsAccepted() {
        assertTrue(OutputVerification.aspectMatches(1080, 1920, 720, 1280))
        assertTrue(OutputVerification.aspectMatches(1920, 1080, 1280, 720))
    }

    /**
     * The planner rounds every edge to an even number, which moves the long edge
     * by at most one pixel. 1280 x 480/720 is 853.33, and the planner produces
     * 852.
     */
    @Test
    fun evenNumberRoundingIsAccepted() {
        assertTrue(
            "720x1280 -> 480x852 is the planner's own arithmetic",
            OutputVerification.aspectMatches(720, 1280, 480, 852),
        )
    }

    /** 4:3 and square sources are shapes too. */
    @Test
    fun otherAspectRatiosAreHandled() {
        assertTrue(OutputVerification.aspectMatches(640, 480, 320, 240))
        assertTrue(OutputVerification.aspectMatches(1080, 1080, 720, 720))
        assertFalse(OutputVerification.aspectMatches(640, 480, 640, 360))
    }

    // ---- the boundary itself ------------------------------------------------

    /**
     * The tolerance is a real edge, so both sides of it are pinned. A 1% drift
     * passes and a 5% drift does not, whatever ASPECT_TOLERANCE is later tuned
     * to - if someone widens it far enough to admit a 5% reshape, this fails.
     */
    @Test
    fun theToleranceAdmitsArithmeticAndRefusesReshaping() {
        val height = 1000
        val onePercent = (1080 * 1.01).toInt()
        val fivePercent = (1080 * 1.05).toInt()
        assertTrue(OutputVerification.aspectMatches(1080, height, onePercent, height))
        assertFalse(OutputVerification.aspectMatches(1080, height, fivePercent, height))
    }

    // ---- the diagnostic number ----------------------------------------------

    /**
     * The drift figure is what the hidden diagnostics screen shows, so it has to
     * mean something. For NEW-01 it is 76%: the output is that far from the
     * shape it should have had.
     */
    @Test
    fun driftReportsHowWrongTheShapeIs() {
        val drift = OutputVerification.aspectDrift(1080, 1920, 1080, 1088)
        assertTrue("NEW-01 drift should be about 0.76, was $drift", drift > 0.70 && drift < 0.80)

        assertEquals(0.0, OutputVerification.aspectDrift(1080, 1920, 1080, 1920), 0.0001)
        assertEquals(0.0, OutputVerification.aspectDrift(0, 0, 0, 0), 0.0001)
    }
}
