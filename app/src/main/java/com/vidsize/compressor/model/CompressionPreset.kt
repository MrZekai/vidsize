package com.vidsize.compressor.model

/**
 * The three compression levels.
 *
 * Ordering contract (enforced by construction in `CompressionPlanner` and by
 * `CompressionPlannerTest.presetsAreStrictlyOrderedAcrossTheWholeBitrateRange`):
 *
 *     BALANCED > SMALLER > SMALLEST
 *
 * for video bitrate, total bitrate and estimated output size — for **every**
 * input, not just high-bitrate ones. That guarantee comes from the fact that
 * every one of the three numbers the planner clamps between
 * (`sourceBitrateFactor`, `minSourceShare`, `bitrateCap`) is itself strictly
 * ordered across the presets. `median(u, lo, hi)` is strictly monotone when all
 * three arguments strictly increase, so the clamp cannot collapse two presets
 * onto the same value.
 *
 * The previous shared absolute floor (`MIN_VIDEO_BITRATE = 350_000`) broke that:
 * below ~683 kbps source bitrate all three presets landed on the same floor and
 * produced identical estimates.
 *
 * @param sourceBitrateFactor share of the source's total bitrate this preset aims for.
 * @param maxShortEdge limit on the **short edge** of the output, not the height.
 *        A portrait 1080x1920 clip at SMALLER becomes 720x1280, not 1080x720.
 * @param bitrateCap absolute ceiling, so a 60 Mbps 4K source does not produce a
 *        40 Mbps "Balanced" output.
 * @param audioBitrate requested AAC bitrate; the planner clamps this down so it
 *        can never exceed a sane share of a low-bitrate source.
 * @param minSourceShare lower bound expressed as a share of the source bitrate.
 *        Replaces the old shared absolute floor and is what keeps the presets
 *        strictly separated at the bottom of the range.
 */
enum class CompressionPreset(
    val title: String,
    val subtitle: String,
    val sourceBitrateFactor: Double,
    val maxShortEdge: Int,
    val bitrateCap: Int,
    val audioBitrate: Int,
    val minSourceShare: Double,
) {
    BALANCED(
        title = "Balanced",
        subtitle = "Smaller file, strong quality",
        // Product knob. 0.70 keeps quality high but only saves ~23% on a typical
        // phone clip. Lower this to ~0.55 for a ~39% saving if store feedback
        // says "Balanced does nothing". Deliberately left at the audited value.
        sourceBitrateFactor = 0.70,
        maxShortEdge = 1080,
        bitrateCap = 5_000_000,
        audioBitrate = 128_000,
        minSourceShare = 0.45,
    ),
    SMALLER(
        title = "Smaller",
        subtitle = "More compression, good quality",
        sourceBitrateFactor = 0.50,
        maxShortEdge = 720,
        bitrateCap = 2_200_000,
        audioBitrate = 112_000,
        minSourceShare = 0.30,
    ),
    SMALLEST(
        title = "Smallest",
        subtitle = "Maximum size reduction",
        sourceBitrateFactor = 0.32,
        maxShortEdge = 480,
        bitrateCap = 1_000_000,
        audioBitrate = 96_000,
        minSourceShare = 0.20,
    ),
}
