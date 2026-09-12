package com.vidsize.compressor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.vidsize.compressor.R
import com.vidsize.compressor.VidsizeApplication
import com.vidsize.compressor.ads.suppressAppOpenOnReturn
import com.vidsize.compressor.data.history.CompressionHistoryEntry
import com.vidsize.compressor.data.history.rememberHistoryController
import com.vidsize.compressor.ui.screens.CompressionScreen
import com.vidsize.compressor.ui.screens.HomeScreen

/**
 * Navigation root.
 *
 * Two destinations, one piece of state. A navigation library would add a
 * dependency and a graph definition to express `null | Uri`, which is not a
 * trade worth making at this size — and the selected video survives rotation and
 * process death through [rememberSaveable].
 *
 * Both the media picker and the history controller live here so the screens
 * below stay stateless and previewable.
 */
@Composable
fun VidsizeRoot(initialVideo: Uri?) {
    val context = LocalContext.current
    val history = rememberHistoryController()

    var selectedVideo by rememberSaveable { mutableStateOf(initialVideo?.toString()) }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) selectedVideo = uri.toString()
    }

    val launchVideoPicker: () -> Unit = {
        (context.applicationContext as? VidsizeApplication)
            ?.appOpenAdManager
            ?.suppressNextForeground()
        picker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }

    val current = selectedVideo

    // Re-reads history every time Home comes back into view. This is what
    // notices that the user deleted their outputs from Movies/Vidsize while the
    // app was in the background, so the recent list and the "Storage saved"
    // total stop counting files that are gone (QA v0.8.7 BUG-07).
    LaunchedEffect(current) {
        if (current == null) history.refreshAndPrune(context)
    }

    if (current == null) {
        HomeScreen(
            summary = history.summary,
            onSelectVideo = launchVideoPicker,
            onClearHistory = { history.clear() },
            // QA v0.8.7 BUG-06: the recent rows are the route back to a
            // compressed file, so they have to actually do something.
            onOpenEntry = { entry -> openHistoryEntry(context, entry) },
            onShareEntry = { entry -> shareHistoryEntry(context, entry) },
        )
    } else {
        CompressionScreen(
            videoUri = Uri.parse(current),
            onBack = { selectedVideo = null },
            onSelectAnother = launchVideoPicker,
            // The service writes the history row (it owns the job); the UI
            // only needs to re-read it.
            onCompleted = { history.refresh() },
        )
    }
}

private fun openHistoryEntry(context: Context, entry: CompressionHistoryEntry) {
    if (entry.outputUri.isBlank()) return
    context.suppressAppOpenOnReturn()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(entry.outputUri), "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareHistoryEntry(context: Context, entry: CompressionHistoryEntry) {
    if (entry.outputUri.isBlank()) return
    context.suppressAppOpenOnReturn()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(entry.outputUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser)),
        )
    }
}
