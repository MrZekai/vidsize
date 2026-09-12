package com.vidsize.compressor.data.history

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
import com.vidsize.compressor.model.CompressionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /** Re-reads stored history. Cheap, main-thread safe, no I/O beyond prefs. */
    fun refresh() {
        summary = repository.summary()
    }

    /**
     * Re-reads history and drops rows whose file no longer exists.
     *
     * ## QA v0.8.7 BUG-07
     *
     * The recent list and the "Storage saved" total were computed purely from
     * stored rows, so deleting the outputs from Movies/Vidsize outside the app
     * left the list advertising videos that were gone and the headline figure
     * claiming 14.8 MB saved across 14 videos when only six files existed.
     * Tapping such a row did nothing, which is also half of BUG-06.
     *
     * Existence is checked by opening the MediaStore row - no bytes are read,
     * and it is the only authority on whether the user still has the file. A row
     * that has gone is removed from storage rather than merely hidden, so the
     * totals converge instead of drifting further on every launch.
     *
     * Suspending, and the checks run on [Dispatchers.IO], because there can be
     * up to fifty rows: fifty content-provider round trips on the main thread
     * would be exactly the kind of launch jank this app does not currently
     * have. The stored summary is published immediately so Home draws at once,
     * and the pruned summary replaces it when the checks finish.
     */
    suspend fun refreshAndPrune(context: Context) {
        val stored = repository.summary()
        summary = stored
        if (stored.isEmpty) return

        val missing = withContext(Dispatchers.IO) {
            stored.entries.filterNot { exists(context, it) }
        }
        if (missing.isEmpty()) return

        missing.forEach { repository.remove(it.id) }
        summary = repository.summary()
    }

    private fun exists(context: Context, entry: CompressionHistoryEntry): Boolean {
        if (entry.outputUri.isBlank()) return false
        return runCatching {
            context.contentResolver
                .openFileDescriptor(Uri.parse(entry.outputUri), "r")
                ?.use { true }
                ?: false
        }.getOrDefault(false)
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
    // The context-aware refresh is what prunes rows whose file the user deleted
    // outside the app (BUG-07), so first composition must use it.
    LaunchedEffect(controller) { controller.refreshAndPrune(context) }
    return controller
}
