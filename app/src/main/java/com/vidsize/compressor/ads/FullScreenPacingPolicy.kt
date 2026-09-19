package com.vidsize.compressor.ads

/** Pure policy behind the shared app-open/interstitial/rewarded interval. */
internal object FullScreenPacingPolicy {
    const val GAP_MILLIS: Long = 3L * 60L * 1000L

    /**
     * Milliseconds since the last full-screen ad, on one monotonic timeline.
     *
     * `lastShownMillis > nowMillis` means the stored stamp came from a previous
     * boot: `SystemClock.elapsedRealtime()` restarts near zero, so a value saved
     * before the reboot is larger than anything measurable after it. There is no
     * way to know how long ago it really was, and the two possible answers are
     * not equally bad - refusing ads forever on a device that rebooted would
     * disable the format outright, where allowing one costs at most a single
     * ad arriving sooner than the three-minute promise.
     *
     * So it opens, deliberately. [BOOT_GRACE_MILLIS] keeps that from being free:
     * the interval is treated as just satisfied rather than infinite, so the
     * next ad is allowed but a SECOND one still has to wait its full turn. That
     * closes the case the audit found - an app-open ad and an interstitial
     * landing back to back on the first session after a reboot.
     */
    fun elapsed(lastShownMillis: Long, nowMillis: Long): Long {
        if (lastShownMillis <= 0L) return Long.MAX_VALUE
        if (lastShownMillis > nowMillis) return BOOT_GRACE_MILLIS
        return nowMillis - lastShownMillis
    }

    /**
     * What a stamp from a previous boot is worth.
     *
     * Exactly [GAP_MILLIS]: the interval counts as met, so one ad may show, and
     * the moment it does `markFullScreenShown` writes a stamp on the current
     * timeline and normal pacing resumes.
     */
    const val BOOT_GRACE_MILLIS: Long = GAP_MILLIS

    fun canShow(lastShownMillis: Long, nowMillis: Long): Boolean =
        elapsed(lastShownMillis, nowMillis) >= GAP_MILLIS

    fun secondsUntilAllowed(lastShownMillis: Long, nowMillis: Long): Long {
        val elapsed = elapsed(lastShownMillis, nowMillis)
        if (elapsed >= GAP_MILLIS) return 0L
        return (GAP_MILLIS - elapsed + 999L) / 1000L
    }
}
