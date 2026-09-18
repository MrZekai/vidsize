package com.vidsize.compressor.model

import android.net.Uri

/**
 * @param sourceUri the video this output was made from.
 *
 *        Kept as provenance for the completed job and for any future retry or
 *        history repair. The v0.9.13 reward flow chooses the output before the
 *        first encode and never uses this field for a result-screen re-encode.
 *
 * @param watermarked whether this file carries the Vidsize mark. This is what
 *        completion flow reads to decide whether an earned mark-free grant was
 *        delivered, so it is a property of the FILE, not of the app's state.
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
