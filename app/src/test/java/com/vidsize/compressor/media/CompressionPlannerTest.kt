package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private data class Case(
        val label: String,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val bytes: Long,
        val hasAudio: Boolean = true,
    ) {
        fun info() = VideoInfo(durationMs, width, height, bytes, null, hasAudio)
    }

    /**
     * The whole bitrate range, not just the comfortable high end.
     *
     * The three cases marked LOW-BITRATE are the ones the previous planner got
     * wrong: a shared `MIN_VIDEO_BITRATE = 350_000` floor made all three presets
     * land on the same video bitrate, and clamping the estimate to the source
     * size then made all three estimates literally equal to the original file.
     * The user saw three identical numbers and, whichever they picked, waited
     * several minutes for a "compression didn't finish" error.
     */
    private val allCases = listOf(
        Case("720x1280 38MB/57s", 720, 1280, 57_000, 37_600_000L),
        Case("704x1252 30MB/48s", 704, 1252, 48_000, 29_700_000L),
        Case("1080p high 101MB/60s", 1920, 1080, 60_000, 101_000_000L),
        Case("1080p 101MB/240s", 1920, 1080, 240_000, 101_000_000L),
        Case("720p low 12MB/90s", 1280, 720, 90_000, 12_000_000L),
        Case("LOW-BITRATE 480x854 18MB/300s", 480, 854, 300_000, 18_000_000L),
        Case("LOW-BITRATE 640x360 2.8MB/60s", 640, 360, 60_000, 2_800_000L),
        Case("LOW-BITRATE 360x640 5MB/120s", 360, 640, 120_000, 5_000_000L),
        Case("4K portrait 450MB/60s", 2160, 3840, 60_000, 450_000_000L),
        Case("1GB 1080p/600s", 1920, 1080, 600_000, 1_100_000_000L),
        Case("very short 2s", 1080, 1920, 2_000, 3_000_000L),
        Case("silent 720p", 1280, 720, 57_000, 37_600_000L, hasAudio = false),
        Case("already-compressed 480p", 480, 848, 60_000, 4_500_000L),
    )

    @Test
    fun presetsAreStrictlyOrderedAcrossTheWholeBitrateRange() {
        allCases.forEach { case ->
            val info = case.info()
            val b = CompressionPlanner.plan(info, CompressionPreset.BALANCED)
            val s = CompressionPlanner.plan(info, CompressionPreset.SMALLER)
            val x = CompressionPlanner.plan(info, CompressionPreset.SMALLEST)

            assertTrue(
                "${case.label}: video bitrate must strictly decrease " +
                    "(${b.videoBitrate} / ${s.videoBitrate} / ${x.videoBitrate})",
                b.videoBitrate > s.videoBitrate && s.videoBitrate > x.videoBitrate,
            )
            assertTrue(
                "${case.label}: estimate must strictly decrease " +
                    "(${b.estimatedOutputBytes} / ${s.estimatedOutputBytes} / " +
                    "${x.estimatedOutputBytes})",
                b.estimatedOutputBytes > s.estimatedOutputBytes &&
                    s.estimatedOutputBytes > x.estimatedOutputBytes,
            )
            assertTrue(
                "${case.label}: resolution must not increase with aggressiveness",
                b.targetHeight >= s.targetHeight && s.targetHeight >= x.targetHeight,
            )
        }
    }

    @Test
    fun presetSeparationIsMeaningfulNotMarginal() {
        // A 3% gap is not a choice. Each step must buy at least 10%.
        allCases.forEach { case ->
            val info = case.info()
            val b = CompressionPlanner.plan(info, CompressionPreset.BALANCED)
            val s = CompressionPlanner.plan(info, CompressionPreset.SMALLER)
            val x = CompressionPlanner.plan(info, CompressionPreset.SMALLEST)
            val smallerPercent = s.estimatedOutputBytes * 100 / b.estimatedOutputBytes
            val smallestPercent = x.estimatedOutputBytes * 100 / s.estimatedOutputBytes
            assertTrue(
                "${case.label}: Smaller is $smallerPercent% of Balanced",
                s.estimatedOutputBytes < b.estimatedOutputBytes * 90 / 100,
            )
            assertTrue(
                "${case.label}: Smallest is $smallestPercent% of Smaller",
                x.estimatedOutputBytes < s.estimatedOutputBytes * 90 / 100,
            )
        }
    }

    @Test
    fun estimateIsNeverSilentlyClampedToTheSourceSize() {
        // The old planner did `.coerceAtMost(sourceBytes)`, which turned "this
        // will not help" into "estimate == original" - three times over.
        allCases.forEach { case ->
            val info = case.info()
            CompressionPreset.entries.forEach { preset ->
                val plan = CompressionPlanner.plan(info, preset)
                assertTrue(
                    "${case.label}/$preset: estimate equals the source size",
                    plan.estimatedOutputBytes != info.sourceBytes,
                )
            }
        }
    }

    @Test
    fun aPresetThatCannotSaveAnythingIsMarkedUnviable() {
        // 480x854 at ~0.48 Mbps: Smallest lands below the encodable floor.
        val clip = Case("LOW-BITRATE 480x854 18MB/300s", 480, 854, 300_000, 18_000_000L).info()
        val x = CompressionPlanner.plan(clip, CompressionPreset.SMALLEST)
        assertFalse("Smallest should not be offered for this source", x.viable)

        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        assertTrue("Balanced still saves meaningfully here", b.viable)
    }

    @Test
    fun viablePresetsAlwaysBeatTheSourceSize() {
        allCases.forEach { case ->
            val info = case.info()
            CompressionPreset.entries.forEach { preset ->
                val plan = CompressionPlanner.plan(info, preset)
                if (plan.viable) {
                    assertTrue(
                        "${case.label}/$preset: marked viable but does not save",
                        plan.estimatedOutputBytes < info.sourceBytes,
                    )
                }
            }
        }
    }

    @Test
    fun audioNeverClaimsAnAbsurdShareOfALowBitrateSource() {
        // 0.48 Mbps source: 128 kbps of AAC would be a quarter of the budget.
        val clip = Case("low bitrate", 480, 854, 300_000, 18_000_000L).info()
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        assertTrue(
            "audio ${b.audioBitrate} is too large for this source",
            b.audioBitrate <= 96_000,
        )
        // A healthy source still gets the full preset bitrate.
        val rich = CompressionPlanner.plan(sample, CompressionPreset.BALANCED)
        assertEquals(128_000, rich.audioBitrate)
    }

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
        val silent = CompressionPlanner.plan(
            sample.copy(hasAudio = false),
            CompressionPreset.BALANCED,
        )
        assertEquals(0, silent.audioBitrate)
    }

    @Test
    fun veryShortClipsUseAMoreConservativeEstimate() {
        // Two seconds is one or two keyframes; rate control never converges and
        // the moov atom is a large share of the file.
        val short = VideoInfo(2_000, 1080, 1920, 3_000_000L, null, true)
        val long = VideoInfo(60_000, 1080, 1920, 90_000_000L, null, true)
        val shortPlan = CompressionPlanner.plan(short, CompressionPreset.BALANCED)
        val longPlan = CompressionPlanner.plan(long, CompressionPreset.BALANCED)

        // Overhead factor = estimate / (totalBitrate * seconds / 8).
        val shortFactor = shortPlan.estimatedOutputBytes.toDouble() /
            ((shortPlan.videoBitrate + shortPlan.audioBitrate) * 2.0 / 8.0)
        val longFactor = longPlan.estimatedOutputBytes.toDouble() /
            ((longPlan.videoBitrate + longPlan.audioBitrate) * 60.0 / 8.0)
        assertTrue(
            "short clips must carry more headroom (short=$shortFactor long=$longFactor)",
            shortFactor > longFactor * 1.15,
        )
    }

    @Test
    fun invalidDimensionsAreRejectedInsteadOfBecomingOnePixel() {
        try {
            CompressionPlanner.plan(sample.copy(width = 0, height = 0), CompressionPreset.BALANCED)
            fail("Expected invalid dimensions to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun zeroDurationIsRejected() {
        try {
            CompressionPlanner.plan(sample.copy(durationMs = 0L), CompressionPreset.BALANCED)
            fail("Expected zero duration to be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
