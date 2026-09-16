package com.vidsize.compressor.media

/**
 * Tells a decoder-side export failure apart from an encoder-side one.
 *
 * ## Why the difference decides what the app does next
 *
 * [CompressionEngine]'s fallback ladder exists to answer an encoder that
 * refuses a frame: it offers a smaller one, then a smaller one again. Against a
 * decoder that cannot open the source, that is pure waste - the source is
 * decoded at its own resolution whatever the output size is, so rung two and
 * rung three fail exactly as rung one did. The field report is what this costs:
 * minutes of waiting, a hot phone, and then a dialog blaming the encoder.
 *
 * So the ladder asks this question after every failed rung, and stops the moment
 * the answer is "the decoder".
 *
 * ## Why it matches on text
 *
 * The evidence is the string Media3 actually produced on the user's device:
 *
 * ```
 * ExportException: Codec exception: CodecInfo{type=VideoDecoder, configurationFormat={...
 * ```
 *
 * `ExportException` does carry an `errorCode`, and reading it would be tidier -
 * but this predicate is then a pure function of strings, which means it can be
 * unit-tested exhaustively on a JVM with no Android framework and no Media3
 * classes loaded. Given that the last two releases each shipped a defect that a
 * runnable test would have caught, being able to test it wins.
 *
 * The tokens are deliberately narrow. `type=VideoDecoder` is the observed one;
 * the rest are Media3's own error-code names, which appear in the exception
 * chain when the failure is reported by code rather than by the platform.
 */
object DecoderFailure {

    /**
     * Substrings that mean "the decoder", matched case-insensitively.
     *
     * Every entry names a decoder explicitly. Nothing here can match an encoder
     * failure, which is the property that matters: a false positive would abort
     * a ladder that was about to succeed on its next rung.
     */
    private val DECODER_TOKENS = listOf(
        "type=VideoDecoder",
        "type=AudioDecoder",
        "DECODER_INIT_FAILED",
        "DECODING_FORMAT_UNSUPPORTED",
        "DECODING_FAILED",
        "DecoderInitializationException",
        "DecoderQueryException",
    )

    /**
     * True when any line of an exception chain names a decoder.
     *
     * Pure, so the tests can pin the exact strings from the field report.
     */
    fun mentionsDecoder(lines: List<String>): Boolean = lines.any { line ->
        DECODER_TOKENS.any { token -> line.contains(token, ignoreCase = true) }
    }

    /**
     * Walks a throwable's cause chain and asks [mentionsDecoder] about it.
     *
     * `toString()` rather than `message`: it prepends the exception's class
     * name, which is where `DecoderInitializationException` shows up when the
     * message itself is unhelpful.
     *
     * The chain is walked with a visited set and a hard depth cap. A cause
     * cycle is rare but not impossible, and this runs on the failure path of a
     * job the user is already unhappy about - hanging there would be the worst
     * possible place to spin.
     */
    fun isDecoderSide(throwable: Throwable?): Boolean {
        val lines = mutableListOf<String>()
        val seen = mutableSetOf<Throwable>()
        var current = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
            lines += runCatching { current.toString() }.getOrDefault("")
            current = current.cause
            depth++
        }
        return mentionsDecoder(lines)
    }

    private const val MAX_CAUSE_DEPTH = 16
}
