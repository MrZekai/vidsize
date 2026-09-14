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
)
