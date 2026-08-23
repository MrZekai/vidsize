package com.fitsize.compressor.data.history

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fitsize.compressor.model.CompressionResult

/**
 * Compose-facing state holder for compression history.
 *
 * Screens never touch [HistoryRepository] directly; they read [summary] and call
 * [record]. That keeps the storage decision (SharedPreferences today, Room
 * later) invisible to the UI layer.
 */
@Stable
class HistoryController(private val repository: HistoryRepository) {

    var summary by mutableStateOf(HistorySummary.Empty)
        private set

    fun refresh() {
        summary = repository.summary()
    }

    /**
     * Records a finished compression. Called once, from the result flow, after
     * the engine has published the file to MediaStore.
     */
    fun record(context: Context, result: CompressionResult) {
        val entry = CompressionHistoryEntry(
            id = System.currentTimeMillis(),
            outputUri = result.outputUri.toString(),
            displayName = readDisplayName(context, result.outputUri),
            sourceBytes = result.sourceBytes,
            outputBytes = result.outputBytes,
            presetTitle = result.preset.title,
            completedAtMillis = System.currentTimeMillis(),
        )
        repository.add(entry)
        refresh()
    }

    fun clear() {
        repository.clear()
        refresh()
    }

    private fun readDisplayName(context: Context, uri: Uri): String {
        val fallback = "Compressed video"
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: fallback
    }
}

/**
 * Creates (once) and remembers the history controller for the current context,
 * loading the stored summary on first composition.
 */
@Composable
fun rememberHistoryController(): HistoryController {
    val context = LocalContext.current
    val controller = remember(context) {
        HistoryController(PrefsHistoryRepository(context))
    }
    LaunchedEffect(controller) { controller.refresh() }
    return controller
}
