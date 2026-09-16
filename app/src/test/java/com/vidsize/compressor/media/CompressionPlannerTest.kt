package com.vidsize.compressor.media

import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CompressionPlannerTest {

    /**
     * The production VIABLE_RATIO, mirrored here on purpose.
     *
     * CompressionPlanner keeps it private, and it should stay private - it is an
     * internal calibration, not an API. But a test suite that cannot name the
     * number it is testing can only ever check hand-picked examples, and this
     * constant has moved twice now, breaking a different example each time.
     *
     * A CI gate asserts this literal matches the planner's, so the two cannot
     * drift apart silently: changing one without the other fails the build with
     * a message saying so, instead of failing a test with a stale expectation.
     */
    private companion object {
        const val VIABLE_RATIO_UNDER_TEST = 0.85
    }

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

    /**
     * The viability floor, and the honest history of this one assertion.
     *
     * ## It flipped once, wrongly, and is flipped back here
     *
     * v0.9.9 raised the floor from 0.92 to 0.85 and this test was edited to say
     * Balanced was no longer viable for this source. The edit was faithful to
     * the code and wrong about the world: the estimate Balanced was being judged
     * on carried `ENCODER_VARIANCE = 1.22`, a 22% inflation that existed only
     * because the encoder was ignoring the bitrate it was asked for. Tightening
     * a decision rule on top of a measurement known to be broken is how Balanced
     * came to be refused on three of four real videos in the field.
     *
     * v0.9.11 fixed the measurement instead: the encoder is now pinned to CBR,
     * so it delivers roughly the bitrate it is given and the variance drops to
     * 1.03. Balanced's estimate for this clip falls to 13.2 MB - a 26% saving on
     * an 18 MB source - which clears the 15% floor comfortably and honestly.
     *
     * So all three presets are pinned here for what they are: Smallest still
     * cannot run (its bitrate lands below the encodable floor, which is a
     * hardware limit, not a policy), and the other two can.
     */
    @Test
    fun aPresetThatCannotSaveAnythingIsMarkedUnviable() {
        // 480x854 at ~0.48 Mbps: Smallest lands below the encodable floor.
        val clip = Case("LOW-BITRATE 480x854 18MB/300s", 480, 854, 300_000, 18_000_000L).info()
        val x = CompressionPlanner.plan(clip, CompressionPreset.SMALLEST)
        assertFalse("Smallest should not be offered for this source", x.viable)

        // Balanced saves ~26% under the CBR-calibrated estimate: clearly worth it.
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        assertTrue("Balanced saves enough here to be worth offering", b.viable)

        val s = CompressionPlanner.plan(clip, CompressionPreset.SMALLER)
        assertTrue("Smaller still saves meaningfully here", s.viable)
    }

    /**
     * The floor is a floor: nothing offered may save less than it promises to.
     *
     * Written as the general rule rather than as one more example, because the
     * constant it depends on has now moved twice and a rule survives that where
     * a hand-picked case does not.
     */
    @Test
    fun nothingViableSavesLessThanTheFloor() {
        allCases.forEach { case ->
            val info = case.info()
            CompressionPreset.entries.forEach { preset ->
                val plan = CompressionPlanner.plan(info, preset)
                if (plan.viable && info.sourceBytes > 0L) {
                    val share = plan.estimatedOutputBytes.toDouble() / info.sourceBytes.toDouble()
                    assertTrue(
                        "${case.label}/$preset: offered while saving only " +
                            "${((1 - share) * 100).toInt()}%",
                        share < VIABLE_RATIO_UNDER_TEST,
                    )
                }
            }
        }
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
        // v0.8.8: Balanced separates itself by bitrate here, not by quietly
        // losing 2px off the short edge (QA v0.8.7 BUG-03). A 704px short edge
        // is under both Balanced's 1080 cap and Smaller's 720 cap, so only
        // Smallest downscales - the separation between the first two is
        // entirely bitrate, which is exactly what this test is named for.
        assertEquals(clip.height, b.targetHeight)
        assertEquals(clip.height, s.targetHeight)
        assertTrue(x.targetHeight < clip.height)
    }

    @Test
    fun real720pExampleDoesNotCollapseBalancedAndSmaller() {
        val clip = VideoInfo(57_000, 720, 1280, 37_600_000L, null, true)
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        val s = CompressionPlanner.plan(clip, CompressionPreset.SMALLER)
        val x = CompressionPlanner.plan(clip, CompressionPreset.SMALLEST)
        assertTrue(s.estimatedOutputBytes < b.estimatedOutputBytes * 0.80)
        assertTrue(x.estimatedOutputBytes < s.estimatedOutputBytes)
        // v0.8.8: a 720px short edge is at Smaller's cap and under Balanced's,
        // so neither downscales and the separation is entirely bitrate - which
        // is what this test is named for. Only Smallest (480) resizes.
        // Until v0.8.7 Balanced shrank the short edge by 2px purely to force a
        // transcode, and this assertion locked that pixel loss in (BUG-03).
        assertEquals(clip.height, b.targetHeight)
        assertEquals(clip.height, s.targetHeight)
        assertTrue(x.targetHeight < clip.height)
    }

    /**
     * ## v0.8.8 - QA v0.8.7 BUG-03
     *
     * This test asserted the opposite until v0.8.8. The planner used to shrink
     * the short edge by 2px whenever the preset was not already downscaling, so
     * that Media3 would transcode rather than transmux, and this test locked
     * that behaviour in.
     *
     * It was the wrong guarantee. Every output lost 4x2 px, 1920x1080 became
     * 1916x1078, the display aspect ratio moved off 16:9, the loss compounded
     * on re-compression, and the app's own "1080p" badge became a lie. The
     * transcode was never at risk: a `Presentation` effect is always attached
     * and non-default `VideoEncoderSettings` are always supplied, and either one
     * alone forces `TransformerUtil.shouldTranscodeVideo` to return true.
     *
     * A bitrate-only reduction must now keep the source geometry exactly.
     */
    @Test
    fun bitrateOnlyReductionKeepsTheSourceResolutionExactly() {
        val b = CompressionPlanner.plan(sample, CompressionPreset.BALANCED)
        assertEquals(1920, b.targetWidth)
        assertEquals(1080, b.targetHeight)
    }

    /** The same guarantee on portrait footage, and on a non-16:9 ratio. */
    @Test
    fun portraitAndOddRatioSourcesAlsoKeepTheirExactGeometry() {
        val portrait = VideoInfo(30_000, 1080, 1920, 60_000_000L, null, true)
        val p = CompressionPlanner.plan(portrait, CompressionPreset.BALANCED)
        assertEquals(1080, p.targetWidth)
        assertEquals(1920, p.targetHeight)

        // The WhatsApp/streaming SD size from the QA resolution matrix.
        val sd = VideoInfo(8_000, 854, 480, 1_700_000L, null, true)
        val s = CompressionPlanner.plan(sd, CompressionPreset.BALANCED)
        assertEquals(854, s.targetWidth)
        assertEquals(480, s.targetHeight)
    }

    /**
     * Re-compressing an already-compressed file must not shrink it again.
     *
     * QA v0.8.7 BUG-03 measured the compounding directly:
     * 1280x720 -> 1276x718 -> 1272x716.
     */
    @Test
    fun recompressionDoesNotCompoundAResolutionLoss() {
        var width = 1280
        var height = 720
        repeat(3) {
            val plan = CompressionPlanner.plan(
                VideoInfo(60_000, width, height, 12_000_000L, null, true),
                CompressionPreset.BALANCED,
            )
            width = plan.targetWidth
            height = plan.targetHeight
        }
        assertEquals(1280, width)
        assertEquals(720, height)
    }

    /**
     * The display aspect ratio must survive the round trip.
     *
     * 1916x1078 is 1.77736 against 16:9's 1.77778 - small, but it is what made
     * every output's DAR metadata read 958:539 instead of 16:9.
     */
    @Test
    fun displayAspectRatioIsPreserved() {
        listOf(
            Triple(1920, 1080, 16.0 / 9.0),
            Triple(1280, 720, 16.0 / 9.0),
            Triple(640, 480, 4.0 / 3.0),
            Triple(320, 240, 4.0 / 3.0),
        ).forEach { (w, h, ratio) ->
            val plan = CompressionPlanner.plan(
                VideoInfo(30_000, w, h, 20_000_000L, null, true),
                CompressionPreset.BALANCED,
            )
            val actual = plan.targetWidth.toDouble() / plan.targetHeight.toDouble()
            assertTrue(
                "${w}x$h produced ${plan.targetWidth}x${plan.targetHeight} (ratio $actual)",
                kotlin.math.abs(actual - ratio) < 0.0005,
            )
        }
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

    /* --------------------------------------------------------------------- */
    /* v0.8.5 - quality ceiling                                               */
    /* --------------------------------------------------------------------- */

    /**
     * The case that motivated v0.8.5: a 114 MB, 56 s, ~16 Mbps 1080p phone clip.
     *
     * Under v0.8.4 both Balanced and Smaller were pinned to their flat caps
     * (5.0 and 2.2 Mbps), so `sourceBitrateFactor` never ran at all and a
     * quality-first preset produced 31% of the source. Balanced must now be
     * governed by what 1080p30 actually needs, not by a constant.
     */
    @Test
    fun highBitrate1080pIsNoLongerPinnedToTheOldFlatCap() {
        val clip = VideoInfo(56_000, 1920, 1080, 114_000_000L, null, true, frameRate = 30.0)
        val b = CompressionPlanner.plan(clip, CompressionPreset.BALANCED)
        assertTrue(
            "Balanced must clear the old 5 Mbps cap (was ${b.videoBitrate})",
            b.videoBitrate > 5_000_000,
        )
        assertTrue(
            "Balanced at 1080p30 should land near 8 Mbps (was ${b.videoBitrate})",
            b.videoBitrate in 7_000_000..9_500_000,
        )
        val share = b.estimatedOutputBytes.toDouble() / clip.sourceBytes.toDouble()
        assertTrue("Balanced should keep about half the source (was $share)", share > 0.45)
    }

    /** Frame rate has to move the ceiling; a flat cap could not express this. */
    @Test
    fun higherFrameRateRaisesTheQualityCeiling() {
        val at30 = VideoInfo(56_000, 1920, 1080, 114_000_000L, null, true, frameRate = 30.0)
        val at60 = at30.copy(frameRate = 60.0)
        val b30 = CompressionPlanner.plan(at30, CompressionPreset.BALANCED)
        val b60 = CompressionPlanner.plan(at60, CompressionPreset.BALANCED)
        assertTrue(
            "1080p60 must get more bits than 1080p30 (${b60.videoBitrate} vs ${b30.videoBitrate})",
            b60.videoBitrate > b30.videoBitrate,
        )
    }

    /** An HEVC source needs more H.264 bits to hold the same quality. */
    @Test
    fun efficientSourceCodecRaisesTheQualityCeiling() {
        val avc = VideoInfo(56_000, 1920, 1080, 114_000_000L, null, true, frameRate = 30.0)
        val hevc = avc.copy(usesEfficientCodec = true)
        val a = CompressionPlanner.plan(avc, CompressionPreset.BALANCED)
        val h = CompressionPlanner.plan(hevc, CompressionPreset.BALANCED)
        assertTrue(
            "HEVC source must be budgeted higher (${h.videoBitrate} vs ${a.videoBitrate})",
            h.videoBitrate > a.videoBitrate,
        )
    }

    /** A missing frame count must not collapse the ceiling to zero. */
    @Test
    fun unknownFrameRateFallsBackToThirty() {
        val unknown = VideoInfo(56_000, 1920, 1080, 114_000_000L, null, true, frameRate = 0.0)
        val explicit = unknown.copy(frameRate = 30.0)
        assertEquals(
            CompressionPlanner.plan(explicit, CompressionPreset.BALANCED).videoBitrate,
            CompressionPlanner.plan(unknown, CompressionPreset.BALANCED).videoBitrate,
        )
    }

    /** The ceiling is derived from the target geometry, so both edges are real. */
    @Test
    fun targetGeometryIsReportedForBothEdges() {
        val portrait = VideoInfo(60_000, 1080, 1920, 90_000_000L, null, true, frameRate = 30.0)
        val smaller = CompressionPlanner.plan(portrait, CompressionPreset.SMALLER)
        assertEquals(720, smaller.targetWidth)
        assertEquals(1280, smaller.targetHeight)

        val landscape = VideoInfo(60_000, 1920, 1080, 90_000_000L, null, true, frameRate = 30.0)
        val land = CompressionPlanner.plan(landscape, CompressionPreset.SMALLER)
        assertEquals(720, land.targetHeight)
        assertEquals(1280, land.targetWidth)
    }

    /**
     * The whole point of deriving the ceiling from the *output* is that
     * low-bitrate sources never reach it. These are the three regressions fixed
     * in v0.8.3; their numbers must not move at all.
     */
    @Test
    fun lowBitrateSourcesAreUntouchedByTheQualityCeiling() {
        val lowBitrate = listOf(
            Case("LOW-BITRATE 480x854 18MB/300s", 480, 854, 300_000, 18_000_000L),
            Case("LOW-BITRATE 640x360 2.8MB/60s", 640, 360, 60_000, 2_800_000L),
            Case("LOW-BITRATE 360x640 5MB/120s", 360, 640, 120_000, 5_000_000L),
        )
        // Values captured from v0.8.4, which the quality ceiling must not alter.
        val expectedTotals = mapOf(
            "LOW-BITRATE 480x854 18MB/300s" to listOf(336_000, 240_000, 168_000),
            "LOW-BITRATE 640x360 2.8MB/60s" to listOf(261_333, 186_666, 138_666),
            "LOW-BITRATE 360x640 5MB/120s" to listOf(233_333, 166_666, 130_666),
        )
        lowBitrate.forEach { case ->
            val info = case.info()
            val totals = CompressionPreset.entries.map {
                val plan = CompressionPlanner.plan(info, it)
                plan.videoBitrate + plan.audioBitrate
            }
            val expected = expectedTotals.getValue(case.label)
            totals.forEachIndexed { index, actual ->
                val want = expected[index]
                assertTrue(
                    "${case.label}/${CompressionPreset.entries[index]}: total moved " +
                        "from $want to $actual",
                    kotlin.math.abs(actual - want) <= 2_000,
                )
            }
        }
    }

    // ========================================================================
    // Size targets: the correction that feeds the last pass
    // ========================================================================

    /**
     * The field measurement this exists for.
     *
     * A 720x1280 / 60 s / 52.6 MB clip aimed at 25 MB is planned at the full
     * 720x1280. The device's encoder then overshot the requested bitrate by
     * 2.15x and returned 40.8 MB - measured on a real device, not assumed.
     *
     * Correcting the bitrate alone leaves 1.47 Mbps spread over 720x1280, which
     * is 0.053 bits per pixel: below the floor the planner uses to decide a
     * frame is worth its size. The same budget over 480x852 is 0.12 bpp, which
     * looks fine and hits the target. So the last chance spends itself on a
     * smaller, clean frame instead of a large, mushy one that also misses.
     */
    @Test
    fun theLastCorrectionDropsTheFrameWhenTheBudgetNoLongerCarriesIt() {
        val info = Case("field 720x1280 52.6MB/60s", 720, 1280, 60_000, 52_600_000L).info()
        val plan = CompressionPlanner.planForTarget(info, 25_000_000L)
        assertTrue("25 MB must be reachable for this source", plan.viable)
        assertEquals(720, plan.targetWidth)
        assertEquals(1280, plan.targetHeight)

        val actual = 40_800_000L

        val held = CompressionPlanner.correctedForTarget(
            info = info,
            plan = plan,
            actualBytes = actual,
            allowResolutionDrop = false,
        )
        assertEquals(
            "an ordinary correction must not silently change the frame",
            720,
            held!!.targetWidth,
        )
        assertTrue("it must still lower the bitrate", held.videoBitrate < plan.videoBitrate)

        val dropped = CompressionPlanner.correctedForTarget(
            info = info,
            plan = plan,
            actualBytes = actual,
            allowResolutionDrop = true,
        )
        assertTrue(
            "the last chance must buy a smaller frame rather than give up",
            dropped!!.targetWidth < plan.targetWidth,
        )

        // A smaller frame is fine. A different SHAPE is the NEW-01 defect, and
        // the output check would reject it after the encode - so the planner
        // must not produce one in the first place.
        assertTrue(
            "the dropped frame must keep the source's shape",
            OutputVerification.aspectMatches(
                sourceWidth = info.width,
                sourceHeight = info.height,
                outputWidth = dropped.targetWidth,
                outputHeight = dropped.targetHeight,
            ),
        )
    }

    /**
     * The mirror case: when the corrected budget still carries the frame, the
     * last chance changes nothing. A resolution drop is a cost, and it is only
     * paid when it buys something.
     */
    @Test
    fun theLastCorrectionKeepsTheFrameWhenTheBudgetStillCarriesIt() {
        val info = Case("field 720x1280 52.6MB/60s", 720, 1280, 60_000, 52_600_000L).info()
        val plan = CompressionPlanner.planForTarget(info, 16_000_000L)
        assertTrue(plan.viable)

        val dropped = CompressionPlanner.correctedForTarget(
            info = info,
            plan = plan,
            actualBytes = 25_700_000L,
            allowResolutionDrop = true,
        )
        assertEquals(plan.targetWidth, dropped!!.targetWidth)
        assertEquals(plan.targetHeight, dropped.targetHeight)
        assertTrue(dropped.videoBitrate < plan.videoBitrate)
    }

    /** A pass that already met the target has nothing to correct. */
    @Test
    fun aPassThatMetTheTargetIsNotCorrected() {
        val info = Case("field 720x1280 52.6MB/60s", 720, 1280, 60_000, 52_600_000L).info()
        val plan = CompressionPlanner.planForTarget(info, 25_000_000L)
        assertNull(
            CompressionPlanner.correctedForTarget(
                info = info,
                plan = plan,
                actualBytes = 24_000_000L,
                allowResolutionDrop = true,
            ),
        )
    }

    /** A preset plan has no target, so there is nothing to correct towards. */
    @Test
    fun aPresetPlanIsNeverCorrected() {
        val info = Case("field 720x1280 52.6MB/60s", 720, 1280, 60_000, 52_600_000L).info()
        val plan = CompressionPlanner.plan(info, CompressionPreset.BALANCED)
        assertNull(
            CompressionPlanner.correctedForTarget(
                info = info,
                plan = plan,
                actualBytes = 99_000_000L,
                allowResolutionDrop = true,
            ),
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
