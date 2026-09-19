package com.vidsize.compressor.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenPacingPolicyTest {

    @Test
    fun firstFullScreenAdIsImmediatelyAllowed() {
        assertTrue(FullScreenPacingPolicy.canShow(lastShownMillis = 0L, nowMillis = 10L))
        assertEquals(
            0L,
            FullScreenPacingPolicy.secondsUntilAllowed(lastShownMillis = 0L, nowMillis = 10L),
        )
    }

    @Test
    fun anotherAdIsBlockedInsideThreeMinutes() {
        assertFalse(
            FullScreenPacingPolicy.canShow(
                lastShownMillis = 1_000L,
                nowMillis = 180_999L,
            ),
        )
        assertEquals(
            1L,
            FullScreenPacingPolicy.secondsUntilAllowed(
                lastShownMillis = 1_000L,
                nowMillis = 180_999L,
            ),
        )
    }

    @Test
    fun anotherAdIsAllowedAtExactlyThreeMinutes() {
        assertTrue(
            FullScreenPacingPolicy.canShow(
                lastShownMillis = 1_000L,
                nowMillis = 181_000L,
            ),
        )
    }

    @Test
    fun futureTimestampFromPreviousBootDoesNotBlock() {
        // A stamp larger than `now` can only have come from a previous boot,
        // because elapsedRealtime restarts near zero. It must not block ads
        // forever - but it is no longer worth Long.MAX_VALUE either.
        assertEquals(
            FullScreenPacingPolicy.BOOT_GRACE_MILLIS,
            FullScreenPacingPolicy.elapsed(lastShownMillis = 80_000L, nowMillis = 20_000L),
        )
        assertTrue(
            FullScreenPacingPolicy.canShow(lastShownMillis = 80_000L, nowMillis = 20_000L),
        )
    }

    /**
     * The reboot grace admits ONE ad, not two.
     *
     * Pre-production audit finding: a stale stamp returned Long.MAX_VALUE, so
     * the three-minute interval was simply skipped on the first session after a
     * reboot and an app-open ad plus an interstitial could land back to back.
     * The grace now equals exactly one interval, so the first ad is allowed and
     * the second must wait its full turn from the stamp that ad writes.
     */
    @Test
    fun theRebootGraceAdmitsOneAdAndThenPacesNormally() {
        assertTrue(
            "the first ad after a reboot is allowed",
            FullScreenPacingPolicy.canShow(lastShownMillis = 80_000L, nowMillis = 20_000L),
        )
        // That ad writes a stamp on the CURRENT timeline; a second ad one
        // second later is refused like any other.
        assertFalse(
            "a second ad immediately after must still wait",
            FullScreenPacingPolicy.canShow(lastShownMillis = 20_000L, nowMillis = 21_000L),
        )
        assertEquals(FullScreenPacingPolicy.GAP_MILLIS, FullScreenPacingPolicy.BOOT_GRACE_MILLIS)
    }

    @Test
    fun elapsedUsesOneMonotonicTimeline() {
        assertEquals(
            42_000L,
            FullScreenPacingPolicy.elapsed(
                lastShownMillis = 5_000L,
                nowMillis = 47_000L,
            ),
        )
    }
}
