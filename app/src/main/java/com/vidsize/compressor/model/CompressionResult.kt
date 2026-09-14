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

    /**
     * The size the user asked for, when this job came from a size target.
     * Null when it came from a quality level.
     */
    val targetBytes: Long? = null,

    /**
     * Whether the requested size was actually reached.
     *
     * Always true in preset mode. In target mode it can be false: the encoder
     * gets a fixed number of attempts, and some sources cannot be squeezed
     * further without dropping below what the hardware will encode at all.
     *
     * The result screen must say so when it is false. Reporting "done" on a file
     * that is over the limit the user is about to send it through would push the
     * failure to the moment they try to attach it, which is the worst possible
     * place to discover it.
     */
    val targetMet: Boolean = true,
)
