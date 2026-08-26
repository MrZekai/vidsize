package com.vidsize.compressor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.vidsize.compressor.VidsizeApplication
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
    if (current == null) {
        HomeScreen(
            summary = history.summary,
            onSelectVideo = launchVideoPicker,
            onClearHistory = { history.clear() },
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
