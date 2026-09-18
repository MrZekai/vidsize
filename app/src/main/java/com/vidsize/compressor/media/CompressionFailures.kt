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

/**
 * This device cannot READ the source video.
 *
 * ## The 4K field failure this exists for
 *
 * A 3840x2160 source failed on a real device and the app reported it as
 * [EncoderUnsupportedException]: "Encoder refused 1920x1080. Tried: faithful
 * 1920x1088, 720p-default 1280x720". Every word of that was misleading. The
 * cause chained onto it was `CodecInfo{type=VideoDecoder}` - the decoder never
 * opened the file, and no encoder ever got a frame to refuse.
 *
 * The distinction is not pedantry, it changes what the app should do:
 *
 *  - An encoder that refuses a frame can be offered a smaller one. That is what
 *    the fallback ladder is for, and it works.
 *  - A decoder that cannot read the source cannot be helped by anything the
 *    ladder varies. The source is decoded at its own resolution regardless of
 *    the output size, so every rung fails the same way. The old behaviour spent
 *    minutes proving that three times over.
 *
 * Capability tables are not trusted enough to throw this before work begins.
 * It is thrown only after Media3's normal decoder fallback and one explicit
 * software-first retry both fail. It carries the source geometry so the message
 * can name what the device could not open.
 */
class SourceUndecodableException(
    val width: Int,
    val height: Int,
    override val cause: Throwable? = null,
) : IllegalStateException(
    "Device cannot decode a ${width}x$height source."
)
