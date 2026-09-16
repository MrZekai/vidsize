package com.vidsize.compressor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecoderRecoveryPolicyTest {

    @Test
    fun aDecoderFailureGetsOneSoftwareFirstRetry() {
        assertEquals(
            DecoderRoute.SOFTWARE_FIRST,
            DecoderRecoveryPolicy.nextRoute(
                current = DecoderRoute.PLATFORM_ORDER,
                decoderFailed = true,
            ),
        )
    }

    @Test
    fun aSoftwareFirstDecoderFailureStopsImmediately() {
        assertNull(
            DecoderRecoveryPolicy.nextRoute(
                current = DecoderRoute.SOFTWARE_FIRST,
                decoderFailed = true,
            ),
        )
    }

    @Test
    fun anEncoderFailureNeverChangesTheDecoderRoute() {
        assertNull(
            DecoderRecoveryPolicy.nextRoute(
                current = DecoderRoute.PLATFORM_ORDER,
                decoderFailed = false,
            ),
        )
    }
}
