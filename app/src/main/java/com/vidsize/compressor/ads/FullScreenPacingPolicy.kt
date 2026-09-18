package com.vidsize.compressor.ads

/** Pure policy behind the shared app-open/interstitial/rewarded interval. */
internal object FullScreenPacingPolicy {
    const val GAP_MILLIS: Long = 3L * 60L * 1000L

    fun elapsed(lastShownMillis: Long, nowMillis: Long): Long {
        if (lastShownMillis <= 0L || lastShownMillis > nowMillis) return Long.MAX_VALUE
        return nowMillis - lastShownMillis
    }

    fun canShow(lastShownMillis: Long, nowMillis: Long): Boolean =
        elapsed(lastShownMillis, nowMillis) >= GAP_MILLIS

    fun secondsUntilAllowed(lastShownMillis: Long, nowMillis: Long): Long {
        val elapsed = elapsed(lastShownMillis, nowMillis)
        if (elapsed >= GAP_MILLIS) return 0L
        return (GAP_MILLIS - elapsed + 999L) / 1000L
    }
}
