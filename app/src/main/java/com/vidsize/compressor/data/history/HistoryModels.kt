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
    fun clear()
}
