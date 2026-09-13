package com.vidsize.compressor.media

/** Input exists, but it does not expose usable video metadata. */
class InvalidVideoException(message: String) : IllegalArgumentException(message)

/** Re-encoding completed, but the result would not save the user any storage. */
class NoCompressionSavingsException :
    IllegalStateException("Compressed output is not smaller than the source.")

/**
 * Pre-flight storage check failed. Thrown before the encode starts so the user
 * is not asked to wait several minutes for a disk-full failure that was
 * predictable from numbers already in hand.
 */
class OutOfSpaceException :
    IllegalStateException("Not enough free space to complete this compression.")

/**
 * Every encoder configuration this device advertised was rejected.
 *
 * ## QA v0.8.7 BUG-05
 *
 * This is the exception that did not exist. A device encoder that refused the
 * requested frame produced a bare `ExportException`, which the service mapped
 * to GENERIC and the screen rendered as a notice below the fold - so the user
 * saw nothing at all and concluded the app was broken.
 *
 * It is thrown only after [CompressionEngine] has exhausted its fallback
 * ladder, and it carries the geometry that was refused so the message the user
 * sees can name the resolution instead of apologising vaguely.
 */
class EncoderUnsupportedException(
    val width: Int,
    val height: Int,
    val attempted: String,
    override val cause: Throwable?,
) : IllegalStateException(
    "Encoder refused ${width}x$height. Tried: $attempted"
)
