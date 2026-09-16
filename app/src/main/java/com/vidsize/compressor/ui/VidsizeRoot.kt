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
import com.vidsize.compressor.PlayerActivity
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
fun VidsizeRoot(
    initialVideo: Uri?,
    onVideoConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val history = rememberHistoryController()

    var selectedVideo by rememberSaveable { mutableStateOf(initialVideo?.toString()) }

    // A SECOND share arriving while the app is already open.
    //
    // With launchMode="singleTask" that intent reaches onNewIntent rather than
    // creating a new activity, so the only thing left to do is notice the new
    // value here and switch to it. Without this the app would keep showing the
    // first video and the share the user just performed would appear to have
    // done nothing.
    //
    // Keyed on the incoming value, so re-composition for any other reason does
    // not drag the user back to a video they already navigated away from; the
    // activity clears its state through onVideoConsumed once it is taken.
    LaunchedEffect(initialVideo) {
        val incoming = initialVideo?.toString() ?: return@LaunchedEffect
        if (incoming != selectedVideo) selectedVideo = incoming
        onVideoConsumed()
    }

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

/**
 * Opens a Recent row in Vidsize's own player.
 *
 * This used to build an implicit `ACTION_VIEW`, which handed the file to
 * whichever player the device had and took the user out of the app. Two things
 * changed with it:
 *
 *  - `suppressAppOpenOnReturn()` is gone. It existed only to stop an app-open
 *    ad firing when the user came BACK from the external player. There is no
 *    longer a return to suppress, and calling it would arm a suppression that
 *    nothing ever clears.
 *  - `runCatching` no longer hides a real failure. An implicit intent could
 *    find no handler at all - on a device with no video player, tapping a
 *    Recent row did nothing and said nothing. An explicit intent to a component
 *    declared in this app's own manifest cannot go unresolved.
 */
private fun openHistoryEntry(context: Context, entry: CompressionHistoryEntry) {
    if (entry.outputUri.isBlank()) return
    context.startActivity(PlayerActivity.intent(context, Uri.parse(entry.outputUri)))
}

private fun shareHistoryEntry(context: Context, entry: CompressionHistoryEntry) {
    if (entry.outputUri.isBlank()) return
    context.suppressAppOpenOnReturn()
    // Same builder as the result screen, so a share started from a Recent row
    // gets the same named, thumbnailed preview rather than a bare row id.
    val intent = buildVideoShareIntent(context, Uri.parse(entry.outputUri))
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser)),
        )
    }
}
