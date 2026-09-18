package com.vidsize.compressor.data.history

/**
 * One completed compression, as shown in the Recent list.
 *
 * Deliberately a plain data class with primitive fields: it is what a Room
 * entity would look like, so the V1.1 migration from SharedPreferences to Room
 * is a storage swap rather than a UI rewrite.
 */
data class CompressionHistoryEntry(
    val id: Long,
    val outputUri: String,
    val displayName: String,
    val sourceBytes: Long,
    val outputBytes: Long,
    val presetTitle: String,
    val completedAtMillis: Long,
) {
    val savedBytes: Long get() = (sourceBytes - outputBytes).coerceAtLeast(0L)
}

/**
 * Everything the Home screen needs in one immutable snapshot, so a recomposition
 * can never show a recent list and a total that disagree with each other.
 */
data class HistorySummary(
    val entries: List<CompressionHistoryEntry>,
    val totalSavedBytes: Long,
    val videoCount: Int,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        val Empty = HistorySummary(emptyList(), 0L, 0)
    }
}

/**
 * Storage contract for compression history.
 *
 * The UI depends only on this interface. V1 ships [PrefsHistoryRepository];
 * swapping in a Room-backed implementation later requires no screen changes.
 */
interface HistoryRepository {
    fun summary(): HistorySummary
    fun add(entry: CompressionHistoryEntry)

    /**
     * Removes one row by id.
     *
     * Added in v0.8.8 for QA BUG-07: a row whose file the user deleted outside
     * the app has to leave storage, not just be filtered out of the view, or the
     * "Storage saved" total keeps counting it on every launch.
     */
    fun remove(id: Long)

    /**
     * Removes every row pointing at an output file, by URI.
     *
     * Added in v0.9.9 for replacement/repair jobs. The v0.9.13 reward flow no
     * longer performs a result-screen replacement, but keeping this repository
     * primitive ensures any service-side file replacement removes its row too.
     *
     * The result was one bug with three faces - a row that opened nothing, a
     * "Space saved" total inflated by every removal, and two rows a user could
     * not tell apart. Deleting by the thing both sides actually share closes all
     * three.
     *
     * Removes ALL matches rather than the first. Duplicate rows for one URI
     * should not exist, but if an earlier build wrote any, this is the call that
     * cleans them up rather than leaving one behind each time.
     */
    fun removeByOutputUri(outputUri: String)

    fun clear()
}
