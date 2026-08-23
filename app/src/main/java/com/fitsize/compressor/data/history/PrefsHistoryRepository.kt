package com.fitsize.compressor.data.history

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * V1 history storage.
 *
 * SharedPreferences plus a small JSON array. Chosen over Room for V1 because the
 * data set is capped at [MAX_ENTRIES] rows of primitives — adding a database, a
 * DAO and a schema migration for that would be architecture theatre. The
 * [HistoryRepository] interface is what keeps the door open: when history grows
 * real features (search, delete, re-share, filters), the implementation behind
 * this interface changes and the screens do not.
 *
 * `org.json` ships with Android, so this costs zero extra dependencies and zero
 * APK size — which matters when a headline competitor installs at 138 MB.
 */
class PrefsHistoryRepository(context: Context) : HistoryRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun summary(): HistorySummary {
        val entries = readEntries()
        if (entries.isEmpty()) return HistorySummary.Empty
        return HistorySummary(
            entries = entries,
            totalSavedBytes = entries.sumOf { it.savedBytes },
            videoCount = entries.size,
        )
    }

    override fun add(entry: CompressionHistoryEntry) {
        val current = readEntries().toMutableList()
        current.add(0, entry)
        while (current.size > MAX_ENTRIES) {
            current.removeAt(current.size - 1)
        }
        writeEntries(current)
    }

    override fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    // -- storage --------------------------------------------------------------

    private fun readEntries(): List<CompressionHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = ArrayList<CompressionHistoryEntry>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                result.add(
                    CompressionHistoryEntry(
                        id = item.optLong("id"),
                        outputUri = item.optString("uri"),
                        displayName = item.optString("name"),
                        sourceBytes = item.optLong("source"),
                        outputBytes = item.optLong("output"),
                        presetTitle = item.optString("preset"),
                        completedAtMillis = item.optLong("at"),
                    ),
                )
            }
            result.toList()
        }.getOrElse {
            // A corrupt record must never take the app down on launch.
            clear()
            emptyList()
        }
    }

    private fun writeEntries(entries: List<CompressionHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("uri", entry.outputUri)
                    put("name", entry.displayName)
                    put("source", entry.sourceBytes)
                    put("output", entry.outputBytes)
                    put("preset", entry.presetTitle)
                    put("at", entry.completedAtMillis)
                },
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "fitsize_history"
        const val KEY_ENTRIES = "entries"

        /** Keeps the blob small enough that main-thread reads stay trivial. */
        const val MAX_ENTRIES = 50
    }
}
