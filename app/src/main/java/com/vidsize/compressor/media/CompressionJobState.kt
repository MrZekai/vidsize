package com.vidsize.compressor.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vidsize.compressor.model.CompressionResult

/**
 * The single source of truth for "is a compression running, and how is it
 * going" — shared between [CompressionService] and the UI.
 *
 * Why a process-level singleton instead of screen state:
 *
 * Compression now runs in a foreground service so it survives the user leaving
 * the app, which is the fix for the loudest complaint in this whole category
 * ("it stops when I switch apps"). Once the work outlives the screen, the screen
 * can no longer own the state. A started foreground service also keeps the
 * process alive, so this object stays valid for the lifetime of the job.
 *
 * It is Compose state, so the compression screen re-composes on every progress
 * tick with no observer plumbing, no ViewModel and no binder.
 */
object CompressionJobState {

    sealed interface Status {
        /** Nothing running, nothing to show. */
        data object Idle : Status

        /**
         * @param progress 0f..1f
         * @param progressKnown false until the encoder reports a real figure,
         *        so the UI can show a hint arc instead of a fake percentage.
         */
        data class Running(val progress: Float, val progressKnown: Boolean) : Status

        data class Done(val result: CompressionResult) : Status

        /** [outOfSpace] separates "free up space" from "this file won't encode". */
        data class Failed(val message: String?, val outOfSpace: Boolean) : Status
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    val isRunning: Boolean get() = status is Status.Running

    fun markRunning(progress: Float = 0f, progressKnown: Boolean = false) {
        status = Status.Running(progress, progressKnown)
    }

    fun markProgress(progress: Float) {
        status = Status.Running(progress, progressKnown = true)
    }

    fun markDone(result: CompressionResult) {
        status = Status.Done(result)
    }

    fun markFailed(message: String?, outOfSpace: Boolean) {
        status = Status.Failed(message, outOfSpace)
    }

    /** Called by the UI once it has consumed a terminal state. */
    fun reset() {
        status = Status.Idle
    }
}
