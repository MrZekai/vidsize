package com.vidsize.compressor.model

data class CompressionPlan(
    val preset: CompressionPreset,
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
)
