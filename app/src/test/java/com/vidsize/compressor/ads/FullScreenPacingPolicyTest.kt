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
        assertEquals(
            Long.MAX_VALUE,
            FullScreenPacingPolicy.elapsed(lastShownMillis = 80_000L, nowMillis = 20_000L),
        )
        assertTrue(
            FullScreenPacingPolicy.canShow(lastShownMillis = 80_000L, nowMillis = 20_000L),
        )
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
