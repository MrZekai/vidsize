package com.vidsize.compressor.model

/**
 * The three compression levels.
 *
 * ## Ordering contract
 *
 *     BALANCED > SMALLER > SMALLEST
 *
 * for video bitrate, total bitrate and estimated output size - for **every**
 * input, not just high-bitrate ones. That guarantee is structural. The planner
 * computes `median(requested, lowerBound, upperBound)` and every one of those
 * three numbers is strictly ordered across the presets, so the clamp cannot
 * collapse two presets onto the same value. See `CompressionPlanner` for the
 * full argument.
 *
 * ## v0.8.5: quality ceiling instead of a flat bitrate cap
 *
 * Up to v0.8.4 each preset carried a single absolute `bitrateCap` - 5 Mbps for
 * Balanced. On modern phone footage that cap, not [sourceBitrateFactor], was
 * almost always the binding constraint: a 16 Mbps 1080p clip came out at 5 Mbps,
 * i.e. 31% of source, from a preset whose stated policy is 70%. Balanced behaved
 * like an aggressive preset and the "quality first" promise was not kept.
 *
 * The cap is now derived from what the *output* actually needs:
 *
 *     ceiling = targetWidth x targetHeight x fps x qualityBitsPerPixel x codecFactor
 *
 * Bits per pixel per frame is the standard way to express encoder quality, and
 * it automatically respects the two things a flat cap ignored: resolution (a
 * 720p output needs far fewer bits than 1080p for the same quality) and frame
 * rate (1080p60 needs roughly twice 1080p30). [bitrateCeiling] survives only as
 * an outer sanity bound so a 4K60 source cannot ask for an absurd figure.
 *
 * @param sourceBitrateFactor share of the source's total bitrate this preset
 *        aims for. Unchanged from v0.8.3 - it is what keeps low-bitrate sources
 *        behaving exactly as they did.
 * @param maxShortEdge limit on the **short edge** of the output, not the height.
 *        A portrait 1080x1920 clip at SMALLER becomes 720x1280, not 1080x720.
 * @param qualityBitsPerPixel bits per pixel per frame the output is allowed.
 *        Reference points for H.264: ~0.13 is a near-transparent 1080p30
 *        (~8 Mbps, matching the usual 1080p upload recommendation), ~0.10 is
 *        comfortable viewing quality, ~0.08 is visibly compressed but clean.
 * @param bitrateCeiling absolute outer bound. Only binds on very large, very
 *        high frame-rate output.
 * @param audioBitrate requested AAC bitrate; the planner clamps this down so it
 *        can never exceed a sane share of a low-bitrate source.
 * @param minSourceShare lower bound expressed as a share of the source bitrate.
 *        This is what keeps the presets strictly separated at the bottom of the
 *        range; do not replace it with a shared absolute floor.
 */
enum class CompressionPreset(
    val title: String,
    val subtitle: String,
    val sourceBitrateFactor: Double,
    val maxShortEdge: Int,
    val qualityBitsPerPixel: Double,
    val bitrateCeiling: Int,
    val audioBitrate: Int,
    val minSourceShare: Double,
) {
    /**
     * Quality first. The average viewer should struggle to see the difference.
     * At 1080p30 the ceiling works out at about 8 Mbps.
     */
    BALANCED(
        title = "Balanced",
        subtitle = "Smaller file, strong quality",
        sourceBitrateFactor = 0.70,
        maxShortEdge = 1080,
        qualityBitsPerPixel = 0.130,
        bitrateCeiling = 16_000_000,
        audioBitrate = 128_000,
        minSourceShare = 0.45,
    ),

    /**
     * Clearly smaller, still good for normal viewing and social sharing.
     * At 720p30 the ceiling works out at about 2.9 Mbps.
     */
    SMALLER(
        title = "Smaller",
        subtitle = "More compression, good quality",
        sourceBitrateFactor = 0.50,
        maxShortEdge = 720,
        qualityBitsPerPixel = 0.105,
        bitrateCeiling = 8_000_000,
        audioBitrate = 112_000,
        minSourceShare = 0.30,
    ),

    /**
     * Maximum practical reduction; visible quality loss is acceptable.
     * At 480p30 the ceiling works out at about 1.0 Mbps, which is where the old
     * flat cap already sat - this preset is deliberately left as it was.
     */
    SMALLEST(
        title = "Smallest",
        subtitle = "Maximum size reduction",
        sourceBitrateFactor = 0.32,
        maxShortEdge = 480,
        qualityBitsPerPixel = 0.080,
        bitrateCeiling = 4_000_000,
        audioBitrate = 96_000,
        minSourceShare = 0.20,
    ),
}
