package com.fitsize.compressor.model

import android.net.Uri

data class CompressionResult(
    val outputUri: Uri,
    val sourceBytes: Long,
    val outputBytes: Long,
    val elapsedMs: Long,
    val preset: CompressionPreset,
)
