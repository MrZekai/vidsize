package com.vidsize.compressor.data.history

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
     * v0.8.8 fixed this by opening a file descriptor, which was not enough:
     * Android 11+ trashes media rather than deleting it, and a trashed file
     * still opens. See [exists] - the MediaStore row is now the authority. A row
     * the user no longer has is removed from storage rather than merely hidden,
     * so the totals converge instead of drifting further on every launch.
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

    /**
     * True only when the user still has this file.
     *
     * ## Why this is a MediaStore query and not an open()
     *
     * v0.8.8 answered this by opening a file descriptor, and that was the wrong
     * question. **Android 11+ does not delete media — it trashes it.** A delete
     * from the system file manager renames the file to
     * `.trashed-<expiry>-<name>`, sets `IS_TRASHED` on its MediaStore row, and
     * keeps both for about thirty days. The bytes are still on disk and the row
     * is still there, so `openFileDescriptor` succeeds and the entry kept being
     * counted in "Storage saved" — exactly the symptom BUG-07 described, just
     * arriving through a different door.
     *
     * The MediaStore row is the authority on whether the user still has the
     * file, so that is what is asked. A trashed or still-pending row counts as
     * gone: the user's intent was to delete it.
     *
     * On API 30+ a trashed item is normally filtered out of query results
     * anyway, so both an empty cursor and an `IS_TRASHED` row lead to the same
     * answer — the flag is checked explicitly rather than relying on that
     * filtering, because OEM media providers vary.
     */
    private fun exists(context: Context, entry: CompressionHistoryEntry): Boolean {
        if (entry.outputUri.isBlank()) return false
        val uri = runCatching { Uri.parse(entry.outputUri) }.getOrNull() ?: return false

        val columns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.IS_TRASHED,
                MediaStore.MediaColumns.IS_PENDING,
            )
        } else {
            arrayOf(MediaStore.MediaColumns._ID)
        }

        val answered = runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (cursor.flagSet(MediaStore.MediaColumns.IS_TRASHED)) return@use false
                    if (cursor.flagSet(MediaStore.MediaColumns.IS_PENDING)) return@use false
                }
                true
            }
        }.getOrNull()

        if (answered != null) return answered

        // A URI the media provider cannot answer for — a document provider, or
        // an OEM variant that rejects the projection. Fall back to opening it,
        // which is still better than dropping a row the user may still have.
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun Cursor.flagSet(column: String): Boolean {
        val index = getColumnIndex(column)
        return index >= 0 && !isNull(index) && getInt(index) != 0
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
