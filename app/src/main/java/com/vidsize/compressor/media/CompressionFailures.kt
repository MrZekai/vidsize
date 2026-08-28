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
