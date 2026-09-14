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
        GENERIC,
    }

    sealed interface Status {
        data object Idle : Status

        data class Running(
            val progress: Float,
            val progressKnown: Boolean,
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
        status = Status.Running(progress, progressKnown = true)
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
