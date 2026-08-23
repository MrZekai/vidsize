package com.fitsize.compressor.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fitsize.compressor.ui.screens.CompressionScreen
import com.fitsize.compressor.ui.screens.HomeScreen

@Composable
fun FitsizeRoot(initialVideo: Uri?) {
    var selected by remember { mutableStateOf(initialVideo) }
    if (selected == null) {
        HomeScreen(onVideoSelected = { selected = it })
    } else {
        CompressionScreen(videoUri = selected!!, onBack = { selected = null })
    }
}
