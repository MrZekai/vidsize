package com.fitsize.compressor.media

import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.VideoInfo
import org.junit.Assert.assertTrue
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
}
