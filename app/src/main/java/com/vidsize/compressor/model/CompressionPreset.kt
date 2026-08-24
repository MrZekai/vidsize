package com.vidsize.compressor.model

enum class CompressionPreset(
    val title: String,
    val subtitle: String,
    val sourceBitrateFactor: Double,
    val maxHeight: Int,
    val bitrateCap: Int,
    val audioBitrate: Int,
) {
    BALANCED(
        title = "Balanced",
        subtitle = "Smaller file, strong quality",
        sourceBitrateFactor = 0.70,
        maxHeight = 1080,
        bitrateCap = 5_000_000,
        audioBitrate = 128_000,
    ),
    SMALLER(
        title = "Smaller",
        subtitle = "More compression, good quality",
        sourceBitrateFactor = 0.50,
        maxHeight = 720,
        bitrateCap = 2_200_000,
        audioBitrate = 112_000,
    ),
    SMALLEST(
        title = "Smallest",
        subtitle = "Maximum size reduction",
        sourceBitrateFactor = 0.32,
        maxHeight = 480,
        bitrateCap = 1_000_000,
        audioBitrate = 96_000,
    ),
}
