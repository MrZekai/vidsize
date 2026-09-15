package com.vidsize.compressor.media

/**
 * The last compression failure's technical detail, kept for the hidden
 * diagnostics screen.
 *
 * ## Why this exists
 *
 * v0.8.8 deliberately put the exception detail in the failure dialog in EVERY
 * build, because QA could otherwise only report "it does nothing" and the
 * v0.8.7 encoder bug took a logcat session to characterise.
 *
 * v0.9.7 took it back out of the dialog, because a production user was shown
 * "NoCompressionSavingsException: Compressed output is not smaller than the
 * source." A Java class name tells that user nothing except that the app is
 * unfinished.
 *
 * Both were right about their own audience, and deleting the detail outright
 * would re-open what v0.8.8 fixed. So it moved rather than disappeared: the
 * dialog shows it only in debuggable builds, and on every build it is recorded
 * here for the seven-tap diagnostics screen. A closed-test tester still has a
 * way to read the exact string; a user never meets it by accident.
 *
 * Deliberately in memory only. It is a debugging aid for the session it
 * happened in, not a log, and writing it to disk would mean deciding how long
 * to keep a string that can carry a file name.
 */
object LastFailure {

    /** The most recent diagnostic, or null if nothing has failed this session. */
    var detail: String? = null
        private set

    /** The reason enum's name, for the same screen. */
    var reason: String? = null
        private set

    fun record(reason: CompressionJobState.FailureReason, detail: String?) {
        this.reason = reason.name
        this.detail = detail?.takeIf { it.isNotBlank() }
    }
}
