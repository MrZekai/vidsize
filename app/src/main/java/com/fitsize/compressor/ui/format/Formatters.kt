package com.fitsize.compressor.ui.format

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display formatting for every number the user sees.
 *
 * Centralised on purpose: the app's credibility rests on its numbers, and
 * numbers formatted three different ways in three different screens is exactly
 * what makes a utility app feel homemade.
 *
 * Decimal separators follow the device locale (so a Turkish user sees "1,02 GB"
 * and a US user sees "1.02 GB") while the unit suffix stays in Latin script,
 * which is how storage sizes are written in effectively every locale.
 */
object Fmt {

    private const val KB = 1024.0
    private const val MB = KB * 1024.0
    private const val GB = MB * 1024.0

    /** Human file size. Picks the unit so the number always has 3-4 digits. */
    fun bytes(value: Long): String {
        if (value <= 0L) return "0 MB"
        val locale = Locale.getDefault()
        return when {
            value >= GB -> String.format(locale, "%.2f GB", value / GB)
            value >= MB -> String.format(locale, "%.1f MB", value / MB)
            value >= KB -> String.format(locale, "%.0f KB", value / KB)
            else -> String.format(locale, "%d B", value)
        }
    }

    /**
     * Estimate rendering. Always prefixed by the caller with "≈" — Fitsize
     * never presents a predicted size as a promise.
     */
    fun estimate(value: Long): String = bytes(value)

    /** mm:ss, or h:mm:ss once the clip passes an hour. */
    fun duration(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val locale = Locale.getDefault()
        return if (hours > 0L) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%d:%02d", minutes, seconds)
        }
    }

    /** Elapsed processing time, e.g. "18.4s" or "2m 04s". */
    fun elapsed(milliseconds: Long): String {
        val locale = Locale.getDefault()
        val seconds = milliseconds / 1000.0
        return if (seconds < 60.0) {
            String.format(locale, "%.1fs", seconds)
        } else {
            val whole = milliseconds / 1000L
            String.format(locale, "%dm %02ds", whole / 60L, whole % 60L)
        }
    }

    /** Whole-percent reduction, clamped to a sane range. */
    fun percentSmaller(sourceBytes: Long, outputBytes: Long): Int {
        if (sourceBytes <= 0L || outputBytes <= 0L) return 0
        val ratio = 1.0 - (outputBytes.toDouble() / sourceBytes.toDouble())
        return (ratio * 100.0).coerceIn(0.0, 99.0).roundToInt()
    }

    /** "1920 × 1080", with a fallback when the probe could not read dimensions. */
    fun resolution(width: Int, height: Int, fallback: String): String =
        if (width > 0 && height > 0) "$width × $height" else fallback

    /** Short resolution badge, e.g. "1080p". */
    fun resolutionBadge(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        height > 0 -> "${height}p"
        else -> "SD"
    }
}
