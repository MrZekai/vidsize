package com.vidsize.compressor.ui

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Builds the share intent for a compressed video.
 *
 * ## QA finding: the share sheet showed a bare number
 *
 * The intent carried the file only in `EXTRA_STREAM`. The system share sheet
 * builds its preview - the name and the thumbnail above the app grid - from the
 * intent's **ClipData**, and falls back to the URI's last path segment when
 * there is none. For a MediaStore URI that last segment is the row id, so the
 * user was shown `1000010443` and a generic "unknown file" icon for a video the
 * app had just named and saved.
 *
 * Attaching ClipData built with the resolver fixes both at once: the sheet gets
 * a real display name and can resolve a thumbnail, and the read grant now rides
 * on the clip as well as the extra, which is what a receiving app on API 29+
 * actually reads.
 *
 * [EXTRA_STREAM] is kept alongside it. Apps that predate ClipData - and several
 * popular messaging apps still do - look only there.
 */
fun buildVideoShareIntent(context: Context, uri: Uri): Intent {
    val label = displayNameOf(context.contentResolver, uri)
    return Intent(Intent.ACTION_SEND).apply {
        type = MIME_MP4
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * The file's name as MediaStore knows it, or a neutral label.
 *
 * Never throws: a share must not be lost because a cursor could not be opened,
 * and ClipData only needs *a* label.
 */
private fun displayNameOf(resolver: ContentResolver, uri: Uri): String {
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) c.getString(index) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: FALLBACK_LABEL
}

private const val MIME_MP4 = "video/mp4"
private const val FALLBACK_LABEL = "Vidsize"
