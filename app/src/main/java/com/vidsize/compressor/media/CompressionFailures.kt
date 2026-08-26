package com.vidsize.compressor.media

/** Input exists, but it does not expose usable video metadata. */
class InvalidVideoException(message: String) : IllegalArgumentException(message)

/** Re-encoding completed, but the result would not save the user any storage. */
class NoCompressionSavingsException :
    IllegalStateException("Compressed output is not smaller than the source.")
