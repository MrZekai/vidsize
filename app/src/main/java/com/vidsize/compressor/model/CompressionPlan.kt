package com.vidsize.compressor.model

data class CompressionPlan(
    val preset: CompressionPreset,
    val targetHeight: Int,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val estimatedOutputBytes: Long,
)
