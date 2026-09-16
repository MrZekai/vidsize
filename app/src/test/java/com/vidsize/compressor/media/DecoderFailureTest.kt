package com.vidsize.compressor.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The predicate that decides whether the retry ladder is worth continuing.
 *
 * A false negative costs what the field report cost: three rungs, three
 * identical failures, minutes of the user's time. A false POSITIVE is worse - it
 * aborts a ladder that was about to succeed on its next rung and tells the user
 * their device cannot read a video it can read perfectly well. So the encoder
 * cases below matter at least as much as the decoder ones.
 */
class DecoderFailureTest {

    // ---- the actual field failure ------------------------------------------

    /**
     * The string from the user's screenshot, as it appeared.
     *
     * A 3840x2160 source on a real device. Note that the outer message talks
     * only about the encoder and three output resolutions; the single word that
     * identifies the real failure is `type=VideoDecoder`, three frames down the
     * cause chain.
     */
    @Test
    fun theFieldFailureIsRecognisedAsDecoderSide() {
        val chain = listOf(
            "EncoderUnsupportedException: Encoder refused 1920x1080. " +
                "Tried: faithful 1920x1088, 720p-default 1280x720",
            "ExportException: Codec exception: " +
                "CodecInfo{type=VideoDecoder, configurationFormat={mime=video/avc}}",
        )
        assertTrue(
            "The 4K field failure must stop the ladder",
            DecoderFailure.mentionsDecoder(chain),
        )
    }

    @Test
    fun anAudioDecoderFailureAlsoStopsTheLadder() {
        assertTrue(
            DecoderFailure.mentionsDecoder(
                listOf("Codec exception: CodecInfo{type=AudioDecoder, ...}"),
            ),
        )
    }

    @Test
    fun media3DecoderErrorCodeNamesAreRecognised() {
        assertTrue(DecoderFailure.mentionsDecoder(listOf("ERROR_CODE_DECODER_INIT_FAILED")))
        assertTrue(
            DecoderFailure.mentionsDecoder(listOf("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED")),
        )
        assertTrue(DecoderFailure.mentionsDecoder(listOf("ERROR_CODE_DECODING_FAILED")))
        assertTrue(
            DecoderFailure.mentionsDecoder(
                listOf("androidx.media3.exoplayer.mediacodec.DecoderInitializationException"),
            ),
        )
    }

    // ---- and the cases that must NOT stop it -------------------------------

    /**
     * The encoder failure the ladder exists to answer.
     *
     * QA v0.8.7 BUG-05: the device's AVC encoder refuses a frame, and the next
     * rung with a smaller frame succeeds. If this were read as decoder-side, the
     * fix for one bug would reintroduce another.
     */
    @Test
    fun anEncoderFailureDoesNotStopTheLadder() {
        assertFalse(
            "An encoder refusal must fall through to the next rung",
            DecoderFailure.mentionsDecoder(
                listOf(
                    "ExportException: Codec exception: " +
                        "CodecInfo{type=VideoEncoder, configurationFormat={mime=video/avc}}",
                ),
            ),
        )
        assertFalse(DecoderFailure.mentionsDecoder(listOf("ERROR_CODE_ENCODER_INIT_FAILED")))
        assertFalse(DecoderFailure.mentionsDecoder(listOf("ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED")))
    }

    @Test
    fun theGeometryCheckFailureDoesNotStopTheLadder() {
        // OutputGeometryException is deliberately a rung failure: the point of
        // QA NEW-01's fix is that the NEXT encoder configuration gets a turn.
        assertFalse(
            DecoderFailure.mentionsDecoder(
                listOf("OutputGeometryException: source 1080x1920, output 1080x1088"),
            ),
        )
    }

    @Test
    fun unrelatedFailuresAreNotDecoderSide() {
        assertFalse(DecoderFailure.mentionsDecoder(emptyList()))
        assertFalse(DecoderFailure.mentionsDecoder(listOf("")))
        assertFalse(DecoderFailure.mentionsDecoder(listOf("java.io.IOException: ENOSPC")))
    }

    // ---- the chain walk -----------------------------------------------------

    @Test
    fun theCauseChainIsWalkedNotJustTheTopException() {
        val root = IllegalStateException("Codec exception: CodecInfo{type=VideoDecoder}")
        val middle = IllegalStateException("export failed", root)
        val top = IllegalStateException("Encoder refused 1920x1080", middle)
        assertTrue("The decoder is three levels down", DecoderFailure.isDecoderSide(top))
    }

    @Test
    fun aNullOrCleanThrowableIsNotDecoderSide() {
        assertFalse(DecoderFailure.isDecoderSide(null))
        assertFalse(DecoderFailure.isDecoderSide(IllegalStateException("something else")))
    }

    /**
     * A self-referencing cause must not hang.
     *
     * This runs on the failure path of a job the user is already waiting on, so
     * a spin here would be the worst possible place for one. The visited set is
     * what prevents it, and this is the test that proves it.
     */
    @Test
    fun aCyclicCauseChainTerminates() {
        val a = object : RuntimeException("a") {
            var link: Throwable? = null
            override val cause: Throwable? get() = link
        }
        a.link = a
        assertFalse(DecoderFailure.isDecoderSide(a))
    }
}
