package com.vidsize.compressor.model

data class CompressionPlan(
    val preset: CompressionPreset,
    val targetWidth: Int,
    val targetHeight: Int,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val estimatedOutputBytes: Long,
    /**
     * False when this preset cannot produce a meaningfully smaller file for this
     * source, or when the resulting bitrate is too low for a hardware encoder to
     * accept. The UI must refuse to start a non-viable job instead of letting the
     * user wait several minutes for a NoCompressionSavingsException.
     *
     * estimatedOutputBytes is intentionally NOT clamped to the source size:
     * clamping hid exactly this situation by reporting "estimate == original".
     */
    val viable: Boolean,
    /**
     * True when the SOURCE is already efficiently encoded and this preset does
     * not reduce the resolution, so re-encoding would gain little or nothing.
     *
     * Separate from [viable] because the two need different words. A plan that
     * is not viable because the estimate is close to the source size is a
     * "pick a stronger level" problem; this is a "your video is already well
     * compressed" fact, and the user should be told which one they have hit.
     */
    val alreadyEfficient: Boolean = false,

    /**
     * The size the user asked for, when this plan came from a size target
     * rather than from a quality level. Null in preset mode.
     *
     * The engine needs this after the export, not just before it: hitting a
     * requested size is the one job where the output has to be measured against
     * what was asked and the encode repeated if it missed.
     */
    val targetBytes: Long? = null,

    /**
     * Why a size target was refused, or [TargetVerdict.REACHABLE] when it was
     * not refused. Always [TargetVerdict.REACHABLE] in preset mode.
     *
     * A boolean would have been enough to disable the button. It would not have
     * been enough to tell the user what to do next, and "compress" being greyed
     * out with no reason is the failure this app has already had to fix once
     * (QA v0.8.7 BUG-04).
     */
    val targetVerdict: TargetVerdict = TargetVerdict.REACHABLE,
) {
    /** True when this plan is a size target rather than a quality level. */
    val isSizeTarget: Boolean get() = targetBytes != null
}

/** Whether a requested output size can be produced, and if not, why not. */
enum class TargetVerdict {
    REACHABLE,

    /**
     * The target is at or above the source size, so there is nothing to do.
     * Asking for "under 100 MB" on a 38 MB file is not an error the user made -
     * it is a question the app can simply answer.
     */
    NOT_SMALLER_THAN_SOURCE,

    /**
     * Even at the lowest resolution and the lowest bitrate a hardware encoder
     * will accept, the file cannot be made this small - the video is simply too
     * long for the number requested.
     *
     * Distinct from [NOT_SMALLER_THAN_SOURCE] because the advice is the
     * opposite: that one says pick a smaller number, this one says pick a
     * bigger one.
     */
    TOO_SMALL_FOR_DURATION,
}
