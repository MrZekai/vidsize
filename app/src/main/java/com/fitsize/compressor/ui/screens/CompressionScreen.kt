package com.fitsize.compressor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitsize.compressor.media.CompressionEngine
import com.fitsize.compressor.media.CompressionPlanner
import com.fitsize.compressor.media.VideoProbe
import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.CompressionResult
import com.fitsize.compressor.model.VideoInfo
import com.fitsize.compressor.ui.components.FitsizeMrecAd
import com.fitsize.compressor.ui.theme.FitsizeAccent
import com.fitsize.compressor.ui.theme.FitsizeAccentSoft
import com.fitsize.compressor.ui.theme.FitsizeBorder
import com.fitsize.compressor.ui.theme.FitsizeCard
import com.fitsize.compressor.ui.theme.FitsizeInk
import com.fitsize.compressor.ui.theme.FitsizeMuted
import com.fitsize.compressor.ui.theme.FitsizeSoft
import com.fitsize.compressor.ui.theme.FitsizeSuccess
import com.fitsize.compressor.ui.theme.FitsizeSuccessSoft
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
        info = runCatching {
            withContext(Dispatchers.IO) {
                VideoProbe.probe(context, videoUri)
            }
        }.onFailure {
            error = it.message ?: it::class.java.simpleName
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitsizeSoft)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = "←  Back",
                color = FitsizeAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Choose compression",
            color = FitsizeInk,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(7.dp))

        Text(
            text = "Choose the balance you want between quality and file size.",
            color = FitsizeMuted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(22.dp))

        info?.let { video ->
            SelectedVideoCard(video)

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Compression level",
                color = FitsizeInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(11.dp))

            CompressionPreset.entries.forEach { option ->
                val plan = CompressionPlanner.plan(video, option)
                PresetCard(
                    preset = option,
                    selected = option == preset,
                    estimate = plan.estimatedOutputBytes,
                    onClick = { preset = option },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFF1F0),
                border = BorderStroke(1.dp, Color(0xFFFFD5D2)),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                busy = true
                error = null
                result = null

                scope.launch {
                    runCatching {
                        CompressionEngine.compress(context, videoUri, preset)
                    }.onSuccess {
                        result = it
                    }.onFailure {
                        error = it.message ?: it::class.java.simpleName
                    }
                    busy = false
                }
            },
            enabled = info != null && !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FitsizeAccent,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD9D5F7),
                disabledContentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "COMPRESSING…",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                )
            } else {
                Text(
                    text = "COMPRESS VIDEO",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    result?.let { compressionResult ->
        ResultDialog(
            context = context,
            result = compressionResult,
            onCompressAnother = onBack,
        )
    }
}

@Composable
private fun SelectedVideoCard(info: VideoInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = FitsizeCard,
        border = BorderStroke(1.dp, FitsizeBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = "SELECTED VIDEO",
                color = Color(0xFF98A2B3),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = "${info.width} × ${info.height}",
                color = FitsizeInk,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = buildString {
                    append(formatDuration(info.durationMs))
                    if (info.sourceBytes > 0L) {
                        append("  •  ")
                        append(formatSize(info.sourceBytes))
                    }
                },
                color = FitsizeMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
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
    val background = if (selected) FitsizeAccentSoft else FitsizeCard
    val border = if (selected) FitsizeAccent else FitsizeBorder

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = if (selected) FitsizeAccent else Color.Transparent,
                        shape = CircleShape,
                    )
                    .then(
                        if (selected) {
                            Modifier
                        } else {
                            Modifier.background(Color(0xFFF2F4F7), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 7.dp else 8.dp)
                        .background(
                            color = if (selected) Color.White else Color(0xFFB8BEC9),
                            shape = CircleShape,
                        ),
                )
            }

            Spacer(Modifier.size(13.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = preset.title,
                    color = FitsizeInk,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preset.subtitle,
                    color = FitsizeMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "EST.",
                    color = Color(0xFF98A2B3),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = "≈ ${formatMb(estimate)} MB",
                    color = if (selected) FitsizeAccent else FitsizeInk,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun ResultDialog(
    context: Context,
    result: CompressionResult,
    onCompressAnother: () -> Unit,
) {
    val savedBytes = (result.sourceBytes - result.outputBytes).coerceAtLeast(0L)
    val savedPercent = if (result.sourceBytes > 0L) {
        (
            (1.0 - result.outputBytes.toDouble() / result.sourceBytes.toDouble()) * 100.0
        ).coerceIn(0.0, 100.0)
    } else {
        0.0
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(30.dp),
            color = FitsizeCard,
            border = BorderStroke(1.dp, FitsizeBorder),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(FitsizeSuccessSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            color = FitsizeSuccess,
                            fontSize = 28.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Spacer(Modifier.height(13.dp))

                    Text(
                        text = "Great! Your video is ready.",
                        color = FitsizeInk,
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Spacer(Modifier.height(7.dp))

                    Text(
                        text = "Smaller file. Same moment. Ready to share.",
                        color = FitsizeMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = FitsizeAccentSoft,
                    ) {
                        Column(
                            modifier = Modifier.padding(17.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "${formatSize(result.sourceBytes)}  →  ${formatSize(result.outputBytes)}",
                                color = FitsizeInk,
                                fontSize = 21.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Black,
                            )

                            Spacer(Modifier.height(5.dp))

                            Text(
                                text = if (savedBytes > 0L) {
                                    "You saved ${formatSize(savedBytes)}  •  ${
                                        String.format(Locale.US, "%.0f", savedPercent)
                                    }% smaller"
                                } else {
                                    "Compression complete"
                                },
                                color = FitsizeAccent,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(11.dp))

                    Text(
                        text = "${result.preset.title}  •  ${
                            String.format(Locale.US, "%.1f", result.elapsedMs / 1000.0)
                        }s",
                        color = Color(0xFF98A2B3),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(Modifier.height(17.dp))

                    Button(
                        onClick = { shareVideo(context, result.outputUri) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FitsizeAccent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = "SHARE VIDEO",
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { openVideo(context, result.outputUri) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, FitsizeBorder),
                    ) {
                        Text(
                            text = "OPEN VIDEO",
                            color = FitsizeInk,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = onCompressAnother,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Compress another video",
                            color = FitsizeAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Advertisement",
                        color = Color(0xFF98A2B3),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(7.dp))

                FitsizeMrecAd(
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }
}

private fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "Share compressed video")
    )
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)

private fun formatSize(bytes: Long): String {
    val mib = bytes / 1024.0 / 1024.0
    return if (mib >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mib / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mib)
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000L
    return "%d:%02d".format(total / 60L, total % 60L)
}
