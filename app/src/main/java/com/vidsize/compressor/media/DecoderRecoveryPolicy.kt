package com.vidsize.compressor.media

/** Decoder ordering used for one Transformer export attempt. */
internal enum class DecoderRoute {
    /** Android/Media3's normal preference order, with decoder fallback enabled. */
    PLATFORM_ORDER,

    /** The same device codecs, but software implementations are tried first. */
    SOFTWARE_FIRST,
}

/**
 * The decoder half of the export retry policy.
 *
 * Output-size rungs cannot repair an input decoder failure. The useful retry is
 * instead the same output with a different decoder order. It happens once: if
 * the software-first route also produces a decoder error, repeating it at 720p
 * would still decode the exact same full-resolution source and only waste time.
 */
internal object DecoderRecoveryPolicy {
    fun nextRoute(current: DecoderRoute, decoderFailed: Boolean): DecoderRoute? = when {
        !decoderFailed -> null
        current == DecoderRoute.PLATFORM_ORDER -> DecoderRoute.SOFTWARE_FIRST
        else -> null
    }
}
