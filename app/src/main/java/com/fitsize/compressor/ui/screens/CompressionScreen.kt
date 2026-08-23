package com.fitsize.compressor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitsize.compressor.media.CompressionEngine
import com.fitsize.compressor.media.CompressionPlanner
import com.fitsize.compressor.media.VideoProbe
import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.CompressionResult
import com.fitsize.compressor.model.VideoInfo
import com.fitsize.compressor.ui.components.FitsizeMrecAd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun CompressionScreen(
    videoUri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var preset by remember { mutableStateOf(CompressionPreset.BALANCED) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CompressionResult?>(null) }

    LaunchedEffect(videoUri) {
        info = runCatching { withContext(Dispatchers.IO) { VideoProbe.probe(context, videoUri) } }
            .onFailure { error = it.message }
            .getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(20.dp))
        Text("Choose compression", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pick the balance you want between quality and file size.", color = Color(0xFF6B7280))
        Spacer(Modifier.height(20.dp))

        info?.let { v ->
            InfoCard(v)
            Spacer(Modifier.height(18.dp))
            CompressionPreset.entries.forEach { option ->
                val plan = CompressionPlanner.plan(v, option)
                PresetCard(
                    preset = option,
                    selected = option == preset,
                    estimate = plan.estimatedOutputBytes,
                    onClick = { preset = option },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                busy = true
                error = null
                result = null
                scope.launch {
                    runCatching { CompressionEngine.compress(context, videoUri, preset) }
                        .onSuccess { result = it }
                        .onFailure { error = it.message ?: it::class.java.simpleName }
                    busy = false
                }
            },
            enabled = info != null && !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
            else Text("COMPRESS VIDEO", fontWeight = FontWeight.Bold)
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        result?.let {
            Spacer(Modifier.height(18.dp))
            ResultCard(context, it, onCompressAnother = onBack)
        }
    }
}

@Composable
private fun PresetCard(
    preset: CompressionPreset,
    selected: Boolean,
    estimate: Long,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(preset.title, fontWeight = FontWeight.Bold)
                Text(preset.subtitle, fontSize = 13.sp, color = Color(0xFF6B7280))
            }
            Text("≈ ${formatMb(estimate)} MB", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(info: VideoInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Selected video", fontWeight = FontWeight.Bold)
            Text("${info.width} × ${info.height}  •  ${formatDuration(info.durationMs)}")
            if (info.sourceBytes > 0) Text("Original: ${formatSize(info.sourceBytes)}")
        }
    }
}

@Composable
private fun ResultCard(
    context: Context,
    result: CompressionResult,
    onCompressAnother: () -> Unit,
) {
    val savedBytes = (result.sourceBytes - result.outputBytes).coerceAtLeast(0L)
    val savedPercent = if (result.sourceBytes > 0) {
        ((1.0 - result.outputBytes.toDouble() / result.sourceBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    } else 0.0

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Great! Your video is ready.", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatSize(result.sourceBytes)} → ${formatSize(result.outputBytes)}",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Text(
                "You saved ${formatSize(savedBytes)} • ${String.format(Locale.US, "%.0f", savedPercent)}% smaller",
                color = Color(0xFF4B5563),
            )
            Text("${result.preset.title} • ${result.elapsedMs / 1000.0}s", color = Color(0xFF6B7280))

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { shareVideo(context, result.outputUri) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("SHARE VIDEO") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openVideo(context, result.outputUri) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("OPEN IN GALLERY") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCompressAnother,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("COMPRESS ANOTHER") }

            Spacer(Modifier.height(20.dp))
            Text("Ad", fontSize = 10.sp, color = Color(0xFF9CA3AF))
            Spacer(Modifier.height(6.dp))
            FitsizeMrecAd()
        }
    }
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share compressed video"))
}

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)
private fun formatSize(bytes: Long): String {
    val mib = bytes / 1024.0 / 1024.0
    return if (mib >= 1024.0) String.format(Locale.US, "%.2f GB", mib / 1024.0)
    else String.format(Locale.US, "%.1f MB", mib)
}
private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
