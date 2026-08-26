package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CompressionPlannerTest {
    private val sample = VideoInfo(
        durationMs = 60_000,
        width = 1920,
        height = 1080,
        sourceBytes = 80L * 1024L * 1024L,
        sourceBitrate = 10_000_000,
    )

    @Test
    fun smallerPresetProducesLowerEstimateThanBalanced() {
        val balanced = CompressionPlanner.plan(sample, CompressionPreset.BALANCED)
        val smaller = CompressionPlanner.plan(sample, CompressionPreset.SMALLER)
        assertTrue(smaller.estimatedOutputBytes < balanced.estimatedOutputBytes)
    }

    @Test
    fun smallestPresetUsesLowerResolutionCeiling() {
        val smallest = CompressionPlanner.plan(sample, CompressionPreset.SMALLEST)
        assertTrue(smallest.targetHeight <= 480)
    }

    @Test
    fun portraitSmallerUses720pShortEdgeWithoutCrushingLongEdge() {
        val portrait = sample.copy(width = 1080, height = 1920)
        val smaller = CompressionPlanner.plan(portrait, CompressionPreset.SMALLER)
        assertEquals(1280, smaller.targetHeight)
    }

    @Test
    fun invalidDimensionsAreRejectedInsteadOfBecomingOnePixel() {
        val invalid = sample.copy(width = 0, height = 0)
        try {
            CompressionPlanner.plan(invalid, CompressionPreset.BALANCED)
            fail("Expected invalid dimensions to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
