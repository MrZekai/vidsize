package com.vidsize.compressor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the arithmetic that decides what frame the encoder is asked for.
 *
 * The cases are taken directly from the QA v0.8.7 resolution matrix, because
 * that matrix is the evidence for BUG-05: six of ten common source sizes failed
 * silently, and the geometry handed to the encoder is the prime suspect.
 */
class FrameAlignmentTest {

    /** A typical Android AVC encoder: 2px alignment, up to 1920x1088. */
    private fun fit(
        width: Int,
        height: Int,
        shortEdge: Int = minOf(width, height),
        alignment: Int = 2,
        maxWidth: Int = 4096,
        maxHeight: Int = 4096,
        minEdge: Int = 2,
    ) = FrameAlignment.fit(
        width = width,
        height = height,
        shortEdge = shortEdge,
        widthAlignment = alignment,
        heightAlignment = alignment,
        minWidth = minEdge,
        maxWidth = maxWidth,
        minHeight = minEdge,
        maxHeight = maxHeight,
    )

    /* --------------------------------------------------------------------- */
    /* Alignment primitives                                                   */
    /* --------------------------------------------------------------------- */

    @Test
    fun nearestRoundsToTheClosestMultipleAndNeverToZero() {
        assertEquals(1080, FrameAlignment.nearest(1080, 2))
        assertEquals(1088, FrameAlignment.nearest(1080, 16))
        assertEquals(848, FrameAlignment.nearest(850, 16))
        assertEquals(480, FrameAlignment.nearest(478, 16))
        // Never collapse a tiny edge to zero.
        assertEquals(16, FrameAlignment.nearest(1, 16))
        assertEquals(16, FrameAlignment.nearest(7, 16))
    }

    @Test
    fun downAndUpBracketTheValue() {
        assertEquals(1072, FrameAlignment.down(1080, 16))
        assertEquals(1088, FrameAlignment.up(1080, 16))
        assertEquals(1080, FrameAlignment.down(1080, 2))
        assertEquals(1080, FrameAlignment.up(1080, 2))
        assertEquals(16, FrameAlignment.down(4, 16))
    }

    @Test
    fun alignmentOfOneOrLessIsAPassThrough() {
        assertEquals(853, FrameAlignment.nearest(853, 1))
        assertEquals(853, FrameAlignment.down(853, 0))
        assertEquals(853, FrameAlignment.up(853, -4))
    }

    /* --------------------------------------------------------------------- */
    /* The QA v0.8.7 resolution matrix                                        */
    /* --------------------------------------------------------------------- */

    /**
     * Every source in the QA matrix must come back byte-exact on a normal
     * 2px-aligned encoder. This is the guarantee QA v0.8.7 BUG-03 asked for,
     * and it is what removes the unaligned geometry suspected in BUG-05.
     */
    @Test
    fun theQaResolutionMatrixIsReproducedExactly() {
        val matrix = listOf(
            1920 to 1080,
            1080 to 1920,
            1280 to 720,
            960 to 540,
            862 to 480,
            856 to 480,
            854 to 480,
            640 to 480,
            320 to 240,
        )
        matrix.forEach { (width, height) ->
            assertEquals(
                "$width x $height must survive unchanged",
                width to height,
                fit(width, height),
            )
        }
    }

    /** No output may ever have an odd edge, at any alignment. */
    @Test
    fun noOutputEdgeIsEverOdd() {
        val sources = listOf(
            1920 to 1080, 1080 to 1920, 1280 to 720, 960 to 540,
            862 to 480, 856 to 480, 854 to 480, 640 to 480, 320 to 240,
            3840 to 2160, 1442 to 1079, 999 to 333,
        )
        listOf(2, 4, 8, 16).forEach { alignment ->
            sources.forEach { (width, height) ->
                val result = fit(width, height, alignment = alignment)
                    ?: error("no fit for $width x $height at $alignment")
                assertTrue(
                    "$width x $height at $alignment gave ${result.first}x${result.second}",
                    result.first % 2 == 0 && result.second % 2 == 0,
                )
            }
        }
    }

    /**
     * The macroblock retry rung. Losing up to 8px on an edge is acceptable
     * *as a fallback*; it must never be the first thing tried, which is why
     * [EncoderSupport] only reaches for it after an export has already failed.
     */
    @Test
    fun macroblockAlignmentStaysWithinHalfAMacroblockOfTheRequest() {
        listOf(
            1920 to 1080,
            854 to 480,
            960 to 540,
            320 to 240,
        ).forEach { (width, height) ->
            val (w, h) = fit(width, height, alignment = 16)
                ?: error("no fit for $width x $height")
            assertTrue("width moved from $width to $w", kotlin.math.abs(w - width) <= 8)
            assertTrue("height moved from $height to $h", kotlin.math.abs(h - height) <= 8)
            assertEquals(0, w % 16)
            assertEquals(0, h % 16)
        }
    }

    /* --------------------------------------------------------------------- */
    /* Downscaling                                                            */
    /* --------------------------------------------------------------------- */

    @Test
    fun steppingDownTheShortEdgeKeepsTheAspectRatio() {
        // 4K stepped to a 1080 short edge is exactly 1920x1080.
        assertEquals(1920 to 1080, fit(3840, 2160, shortEdge = 1080))
        // Portrait 4K likewise.
        assertEquals(1080 to 1920, fit(2160, 3840, shortEdge = 1080))
        // A 720 short edge on 16:9.
        assertEquals(1280 to 720, fit(1920, 1080, shortEdge = 720))
    }

    @Test
    fun aRequestLargerThanTheEncoderIsClampedIntoRange() {
        // An encoder that stops at 1920x1088 must not be handed a 4K frame.
        val (w, h) = fit(3840, 2160, maxWidth = 1920, maxHeight = 1088)
            ?: error("no fit")
        assertTrue("width $w exceeds the encoder", w <= 1920)
        assertTrue("height $h exceeds the encoder", h <= 1088)
    }

    @Test
    fun anImpossibleRangeReportsNoFitInsteadOfInventingASize() {
        // A range that cannot contain any 16-aligned frame.
        assertNull(
            FrameAlignment.fit(
                width = 1920,
                height = 1080,
                shortEdge = 1080,
                widthAlignment = 16,
                heightAlignment = 16,
                minWidth = 20,
                maxWidth = 28,
                minHeight = 20,
                maxHeight = 28,
            ),
        )
    }

    @Test
    fun degenerateInputIsRejected() {
        assertNull(fit(0, 1080))
        assertNull(fit(1920, 0))
        assertNull(fit(1920, 1080, shortEdge = 0))
    }

    @Test
    fun shortEdgeIsTheWidthOnPortraitFootage() {
        assertEquals(1080, FrameAlignment.shortEdgeOf(1080, 1920))
        assertEquals(1080, FrameAlignment.shortEdgeOf(1920, 1080))
        assertEquals(480, FrameAlignment.shortEdgeOf(854, 480))
    }
}
