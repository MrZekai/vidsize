package com.vidsize.compressor.model

/**
 * A file size the user asks for by name, instead of a quality level.
 *
 * ## Why this exists alongside the presets
 *
 * The three presets answer "how much quality am I willing to give up?". That is
 * the right question when the user simply wants a smaller file. It is the wrong
 * question when something outside the app has already decided the answer:
 * WhatsApp refuses above 16 MB, most mail refuses above 25 MB, a forum post caps
 * at 50. In those cases the user does not want Balanced or Smaller - they want
 * *under sixteen megabytes*, and they do not care which level gets them there.
 *
 * Before this, the only way to serve that was to run a preset, read the result,
 * guess, and run another one. The app made the user do binary search by hand.
 *
 * ## Why these five numbers
 *
 * Each is a real limit somebody else imposed, not a round number chosen for
 * looking tidy:
 *
 *  - **10 MB** - the common ceiling for a mail attachment on older corporate
 *    servers, and a comfortable size for a messaging app to send without its own
 *    re-compression kicking in.
 *  - **16 MB** - WhatsApp's video limit. Almost certainly the single most
 *    requested number in this whole app's problem space.
 *  - **25 MB** - Gmail's attachment limit.
 *  - **50 MB** - Discord Nitro-less upload, and a typical forum cap.
 *  - **100 MB** - Telegram-comfortable, and the point where "make it smaller"
 *    stops being about a hard limit and starts being about courtesy.
 *
 * ## Megabyte means 1 000 000 bytes here, deliberately
 *
 * [com.vidsize.compressor.ui.format.Fmt.bytes] formats with a decimal
 * megabyte, so a 16 MB target that produced 15 999 000 bytes must read as
 * "16.0 MB" on the result screen and not as "15.3 MB". Using the binary
 * megabyte here would make the app's own two numbers disagree with each other,
 * which is a worse failure than the 4.8% of headroom it would buy - and the
 * services quoting these limits are themselves inconsistent about which
 * megabyte they mean, so the safe reading is the smaller one.
 */
enum class SizeTarget(val megabytes: Int) {
    MB_10(10),
    MB_16(16),
    MB_25(25),
    MB_50(50),
    MB_100(100),
    ;

    val bytes: Long get() = megabytes.toLong() * BYTES_PER_MB

    companion object {
        const val BYTES_PER_MB = 1_000_000L

        /**
         * Bounds for a hand-typed target.
         *
         * The floor is not a quality judgement - [CompressionPlanner] refuses an
         * unreachable target on its own merits, with a reason. It exists so the
         * field cannot be driven to zero, where every downstream division would
         * have to defend itself.
         */
        const val CUSTOM_MIN_MB = 1
        const val CUSTOM_MAX_MB = 4_000

        fun bytesFor(megabytes: Int): Long =
            megabytes.coerceIn(CUSTOM_MIN_MB, CUSTOM_MAX_MB).toLong() * BYTES_PER_MB
    }
}
