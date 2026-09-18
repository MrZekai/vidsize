package com.vidsize.compressor.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vidsize.compressor.model.CompressionResult

/**
 * Process-level state shared by the foreground service and the Compose UI.
 */
object CompressionJobState {

    enum class FailureReason {
        OUT_OF_SPACE,
        INVALID_VIDEO,
        NO_SAVINGS,

        /**
         * The device's own video encoder refused every configuration Vidsize
         * offered, including the conservative fallbacks. Distinct from GENERIC
         * because the advice differs: a different compression level will not
         * help, but a smaller source or a different video will.
         */
        ENCODER_UNSUPPORTED,

        /**
         * The device's video DECODER could not open the source at all.
         *
         * Separate from [ENCODER_UNSUPPORTED] because it is a different fact
         * about the device and carries different advice. An encoder that
         * refuses a frame can be offered a smaller one; a decoder that cannot
         * read the source cannot be helped by any output setting, so the honest
         * message names the resolution and says a smaller-resolution source is
         * what would work.
         *
         * The 4K field failure was reported as ENCODER_UNSUPPORTED and told the
         * user their encoder had failed "even at a lower resolution", which was
         * untrue in both halves.
         */
        SOURCE_UNDECODABLE,

        /**
         * Android stopped the foreground service before the job finished.
         *
         * From API 35 a mediaProcessing foreground service may run for at most
         * six hours per day, after which the system calls `onTimeout` and the
         * service must stop within seconds. Previously that path called
         * `reset()`, so a user who had waited hours found the app back on Home
         * with no output, no error and nothing to explain it - the worst
         * possible ending for the longest possible job.
         */
        TIMEOUT,
        /**
         * Android refused to let the foreground service start.
         *
         * From Android 12, `startForegroundService` throws
         * `ForegroundServiceStartNotAllowedException` when the app is not in a
         * state the platform considers valid for starting one - a call arriving
         * as the user taps COMPRESS, a race with backgrounding, or one of the
         * aggressive OEM restrictions (Xiaomi, Realme) that tighten the rule
         * further.
         *
         * Up to v0.9.14 that exception was uncaught and crashed the app. A crash
         * here is expensive twice over: the user loses the job AND it lands in
         * Android vitals, where a bad enough rate halts a staged rollout. It is
         * also genuinely recoverable - bringing the app to the foreground and
         * tapping again normally works - so it deserves a message, not a
         * stack trace.
         */
        SERVICE_START_FAILED,

        /**
         * The app lost permission to read the video it was given.
         *
         * Introduced with the Photo Picker in v0.9.17, and the honest cost of
         * that change. SAF's `takePersistableUriPermission` survives process
         * death; Photo Picker's grant does not - it lasts as long as the task.
         * So a compression interrupted by the system killing the process, then
         * resumed from the saved URI, can find that URI no longer readable.
         *
         * Rare, but not hypothetical: a five-minute job on a low-memory phone is
         * exactly the situation the platform reclaims processes in. Reported as
         * GENERIC it would have told the user to "try a different compression
         * level", which cannot possibly help. The one thing that does help is
         * picking the video again, so that is what it says.
         */
        SOURCE_ACCESS_LOST,

        GENERIC,
    }

    sealed interface Status {
        data object Idle : Status

        data class Running(
            val progress: Float,
            val progressKnown: Boolean,

            /**
             * Which encode of a size-target job is running, and out of how many.
             *
             * Both 1 for an ordinary job, and the UI says nothing. A size target
             * can need a second or third encode to land under the ceiling, and
             * without this the progress ring would drop back to zero with no
             * explanation - which reads exactly like a crash-and-restart. The
             * ring restarting is fine as long as the screen says why.
             */
            val pass: Int = 1,
            val passCeiling: Int = 1,
        ) : Status

        data class Done(val result: CompressionResult) : Status

        data class Failed(
            val reason: FailureReason,
            val debugMessage: String? = null,
        ) : Status
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    val isRunning: Boolean get() = status is Status.Running

    fun markRunning(progress: Float = 0f, progressKnown: Boolean = true) {
        status = Status.Running(progress, progressKnown)
    }

    fun markProgress(progress: Float) {
        // Carry the pass across a progress tick. Reading it back off the current
        // status rather than taking it as a parameter keeps every existing
        // caller correct: progress is reported from inside the encoder callback,
        // which knows the fraction and has no idea which pass it is on.
        val current = status as? Status.Running
        status = Status.Running(
            progress = progress,
            progressKnown = true,
            pass = current?.pass ?: 1,
            passCeiling = current?.passCeiling ?: 1,
        )
    }

    /**
     * Starts a new encode within the same job.
     *
     * Resets the fraction to zero on purpose: the new pass really is starting
     * over. [ProcessingOverlay] pairs that with the pass number so the reset
     * reads as "second attempt" and not as "it crashed".
     */
    fun markPass(pass: Int, passCeiling: Int) {
        status = Status.Running(
            progress = 0f,
            progressKnown = true,
            pass = pass,
            passCeiling = passCeiling,
        )
    }

    fun markDone(result: CompressionResult) {
        status = Status.Done(result)
    }

    fun markFailed(reason: FailureReason, debugMessage: String? = null) {
        status = Status.Failed(reason, debugMessage)
    }

    fun reset() {
        status = Status.Idle
    }
}
