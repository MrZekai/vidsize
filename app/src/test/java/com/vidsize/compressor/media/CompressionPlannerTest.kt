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
        hasAudio = true,
    )

    @Test
    fun presetEstimatesAreStrictlyOrdered() {
        val b = CompressionPlanner.plan(sample, CompressionPreset.BALANCED)
        val s = CompressionPlanner.plan(sample, CompressionPreset.SMALLER)
        val x = CompressionPlanner.plan(sample, CompressionPreset.SMALLEST)
        assertTrue(s.estimatedOutputBytes < b.estimatedOutputBytes)
        assertTrue(x.estimatedOutputBytes < s.estimatedOutputBytes)
    }

    @Test
    fun real704pExampleDoesNotCollapseBalancedAndSmaller() {
        val clip = VideoInfo(48_000, 704, 1252, 29_700_000L, null, true)
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        val s = CompressionPlanner.plan(clip, CompressionPreset.SMALLER)
        val x = CompressionPlanner.plan(clip, CompressionPreset.SMALLEST)
        assertTrue(s.estimatedOutputBytes < b.estimatedOutputBytes * 0.80)
        assertTrue(x.estimatedOutputBytes < s.estimatedOutputBytes)
        assertTrue(b.targetHeight < clip.height)
    }

    @Test
    fun real720pExampleDoesNotCollapseBalancedAndSmaller() {
        val clip = VideoInfo(57_000, 720, 1280, 37_600_000L, null, true)
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        val s = CompressionPlanner.plan(clip, CompressionPreset.SMALLER)
        val x = CompressionPlanner.plan(clip, CompressionPreset.SMALLEST)
        assertTrue(s.estimatedOutputBytes < b.estimatedOutputBytes * 0.80)
        assertTrue(x.estimatedOutputBytes < s.estimatedOutputBytes)
        assertTrue(b.targetHeight < clip.height)
    }

    @Test
    fun sameResolutionBitrateReductionForcesRealResize() {
        val b = CompressionPlanner.plan(sample, CompressionPreset.BALANCED)
        assertTrue(b.targetHeight < sample.height)
    }

    @Test
    fun portraitSmallerStillUses720pShortEdgeWhenRealDownscaleIsNeeded() {
        val portrait = sample.copy(width = 1080, height = 1920)
        val smaller = CompressionPlanner.plan(portrait, CompressionPreset.SMALLER)
        assertEquals(1280, smaller.targetHeight)
    }

    @Test
    fun silentSourceDoesNotBudgetPhantomAudio() {
        val silent = CompressionPlanner.plan(sample.copy(hasAudio = false), CompressionPreset.BALANCED)
        assertEquals(0, silent.audioBitrate)
    }

    @Test
    fun invalidDimensionsAreRejectedInsteadOfBecomingOnePixel() {
        try {
            CompressionPlanner.plan(sample.copy(width = 0, height = 0), CompressionPreset.BALANCED)
            fail("Expected invalid dimensions to be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
