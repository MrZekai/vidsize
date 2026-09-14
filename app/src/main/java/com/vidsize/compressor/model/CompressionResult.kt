package com.vidsize.compressor.model

import android.net.Uri

/**
 * @param sourceUri the video this output was made from.
 *
 *        Kept because the watermark-free re-export has to start from the
 *        ORIGINAL, never from the marked output: re-encoding an encode
 *        compounds the loss, and the mark is burned into the pixels, so a
 *        second pass over the output could not remove it anyway.
 *
 * @param watermarked whether this file carries the Vidsize mark. This is what
 *        the result screen reads to decide whether to offer the rewarded
 *        removal, so it is a property of the FILE, not of the app's state.
 */
data class CompressionResult(
    val outputUri: Uri,
    val sourceUri: Uri,
    val sourceBytes: Long,
    val outputBytes: Long,
    val elapsedMs: Long,
    val preset: CompressionPreset,
    val watermarked: Boolean,
)
