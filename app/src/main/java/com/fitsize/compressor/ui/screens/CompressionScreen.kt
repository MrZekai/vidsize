package com.fitsize.compressor.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitsize.compressor.R
import com.fitsize.compressor.media.CompressionEngine
import com.fitsize.compressor.media.CompressionPlanner
import com.fitsize.compressor.media.VideoProbe
import com.fitsize.compressor.media.rememberVideoThumbnail
import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.CompressionResult
import com.fitsize.compressor.model.VideoInfo
import com.fitsize.compressor.ui.components.Eyebrow
import com.fitsize.compressor.ui.components.FitsizeCard
import com.fitsize.compressor.ui.components.HairLine
import com.fitsize.compressor.ui.components.IconAction
import com.fitsize.compressor.ui.components.PrimaryButton
import com.fitsize.compressor.ui.components.SectionHeader
import com.fitsize.compressor.ui.components.TintedPill
import com.fitsize.compressor.ui.format.Fmt
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeTheme
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compression screen.
 *
 * Structure mirrors Home: a fixed bar at the top, one scrolling body, and a
 * fixed action bar at the bottom that clears the navigation bar. The primary
 * action never scrolls out of reach — on a 360dp phone with a long preset list
 * that is the difference between a considered product and a form.
 *
 * Progress is real, not decorative: [CompressionEngine] reports encoder progress
 * and this screen renders it. When the encoder has not produced a figure yet the
 * ring shows a hint arc rather than a fake percentage.
 */
@Composable
fun CompressionScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onCompleted: (CompressionResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember(videoUri) { mutableStateOf<VideoInfo?>(null) }
    var probeFailed by remember(videoUri) { mutableStateOf(false) }
    var preset by remember(videoUri) { mutableStateOf(CompressionPreset.BALANCED) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressKnown by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CompressionResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(videoUri) {
        val probed = runCatching {
            withContext(Dispatchers.IO) { VideoProbe.probe(context, videoUri) }
        }.getOrNull()
        info = probed
        probeFailed = probed == null
    }

    // While an export is running, swallow the back gesture: the user cancels
    // deliberately with the button instead of losing work by accident.
    BackHandler(enabled = busy) { }
    BackHandler(enabled = !busy && result == null) { onBack() }

    fun startCompression() {
        error = null
        progress = 0f
        progressKnown = false
        busy = true
        job = scope.launch {
            runCatching {
                CompressionEngine.compress(
                    context = context,
                    input = videoUri,
                    preset = preset,
                    onProgress = { value ->
                        progressKnown = true
                        progress = value
                    },
                )
            }.onSuccess { completed ->
                result = completed
                onCompleted(completed)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    error = throwable.message
                }
            }
            busy = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitsizeColor.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompressionTopBar(onBack = onBack, enabled = !busy)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
            ) {
                Text(
                    text = stringResource(R.string.compress_title),
                    style = FitsizeType.screenTitle,
                    color = FitsizeColor.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.compress_subtitle),
                    style = FitsizeType.body,
                    color = FitsizeColor.Muted,
                )

                Spacer(Modifier.height(Space.lg))

                SelectedVideoCard(videoUri = videoUri, info = info, failed = probeFailed)

                Spacer(Modifier.height(Space.xl))

                SectionHeader(title = stringResource(R.string.section_level))

                Spacer(Modifier.height(Space.sm))

                val currentInfo = info
                CompressionPreset.entries.forEach { option ->
                    val estimate = currentInfo?.let {
                        CompressionPlanner.plan(it, option).estimatedOutputBytes
                    }
                    PresetRow(
                        preset = option,
                        selected = option == preset,
                        estimateBytes = estimate,
                        sourceBytes = currentInfo?.sourceBytes ?: 0L,
                        enabled = !busy,
                        onClick = { preset = option },
                    )
                    Spacer(Modifier.height(Space.xs))
                }

                Spacer(Modifier.height(Space.xxs))

                Text(
                    text = stringResource(R.string.estimate_note),
                    style = FitsizeType.caption,
                    color = FitsizeColor.Faint,
                )

                val message = error
                if (message != null) {
                    Spacer(Modifier.height(Space.md))
                    ErrorCard(detail = message)
                }

                Spacer(Modifier.height(Space.xl))
            }

            CompressionActionBar(
                enabled = info != null && !busy,
                onCompress = { startCompression() },
            )
        }

        if (busy) {
            ProcessingOverlay(
                progress = progress,
                progressKnown = progressKnown,
                onCancel = {
                    job?.cancel()
                    busy = false
                },
            )
        }
    }

    val finished = result
    if (finished != null) {
        ResultSheet(
            result = finished,
            onDismiss = { result = null },
            onCompressAnother = {
                result = null
                onBack()
            },
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Bars                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
private fun CompressionTopBar(onBack: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FitsizeColor.Background)
            .statusBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = { if (enabled) onBack() },
            tint = if (enabled) FitsizeColor.InkSoft else FitsizeColor.Faint,
        )
    }
}

@Composable
private fun CompressionActionBar(enabled: Boolean, onCompress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FitsizeColor.Background),
    ) {
        HairLine()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
        ) {
            PrimaryButton(
                text = stringResource(R.string.cta_compress),
                onClick = onCompress,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Source summary                                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun SelectedVideoCard(videoUri: Uri, info: VideoInfo?, failed: Boolean) {
    val thumbnail by rememberVideoThumbnail(videoUri)

    FitsizeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 72.dp)
                    .clip(FitsizeShape.small)
                    .background(FitsizeColor.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_file),
                        contentDescription = null,
                        tint = FitsizeColor.Faint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (info != null && info.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(FitsizeShape.chip)
                            .background(Color(0xCC0F1222))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = Fmt.duration(info.durationMs),
                            style = FitsizeType.micro,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(text = stringResource(R.string.selected_video))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        info != null -> Fmt.resolution(
                            info.width,
                            info.height,
                            stringResource(R.string.unknown),
                        )
                        failed -> stringResource(R.string.unknown)
                        else -> stringResource(R.string.reading_video)
                    },
                    style = FitsizeType.cardTitle,
                    color = FitsizeColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (info != null && info.sourceBytes > 0L) {
                        Fmt.bytes(info.sourceBytes)
                    } else {
                        "—"
                    },
                    style = FitsizeType.supporting,
                    color = FitsizeColor.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (info != null && info.height > 0) {
                Spacer(Modifier.width(Space.xs))
                TintedPill(text = Fmt.resolutionBadge(info.height))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Preset row                                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun PresetRow(
    preset: CompressionPreset,
    selected: Boolean,
    estimateBytes: Long?,
    sourceBytes: Long,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fraction = if (estimateBytes != null && sourceBytes > 0L) {
        (estimateBytes.toFloat() / sourceBytes.toFloat()).coerceIn(0.06f, 1f)
    } else {
        0f
    }

    FitsizeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        clickEnabled = enabled,
        role = Role.RadioButton,
        shape = FitsizeShape.large,
        color = if (selected) FitsizeColor.IndigoSoft else FitsizeColor.Surface,
        border = if (selected) FitsizeColor.Indigo else FitsizeColor.Border,
        elevation = if (selected) 8.dp else 4.dp,
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionDot(selected = selected)

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(presetTitleRes(preset)),
                    style = FitsizeType.cardTitle,
                    color = FitsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(presetBodyRes(preset)),
                    style = FitsizeType.supporting,
                    color = FitsizeColor.Muted,
                )

                if (fraction > 0f) {
                    Spacer(Modifier.height(Space.xs))
                    SizeBar(fraction = fraction, selected = selected)
                }
            }

            Spacer(Modifier.width(Space.xs))

            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(text = stringResource(R.string.estimate_short))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (estimateBytes != null) {
                        stringResource(R.string.estimate_value, Fmt.estimate(estimateBytes))
                    } else {
                        "—"
                    },
                    style = FitsizeType.cardTitle,
                    color = if (selected) FitsizeColor.Indigo else FitsizeColor.Ink,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(FitsizeShape.chip)
            .background(if (selected) FitsizeColor.Indigo else FitsizeColor.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(FitsizeShape.chip)
                    .background(FitsizeColor.Border),
            )
        }
    }
}

/**
 * Relative-size bar. Reads as "how much of the original is left" at a glance,
 * which is a far more useful comparison between presets than three numbers.
 */
@Composable
private fun SizeBar(fraction: Float, selected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(FitsizeShape.chip)
            .background(if (selected) Color.White else FitsizeColor.SurfaceMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(FitsizeShape.chip)
                .background(
                    if (selected) FitsizeColor.Indigo else FitsizeColor.Indigo.copy(alpha = 0.35f),
                ),
        )
    }
}

@Composable
private fun ErrorCard(detail: String?) {
    FitsizeCard(
        modifier = Modifier.fillMaxWidth(),
        color = FitsizeColor.DangerSoft,
        border = FitsizeColor.DangerBorder,
        elevation = 0.dp,
        contentPadding = Space.md,
    ) {
        Text(
            text = stringResource(R.string.error_title),
            style = FitsizeType.cardTitle,
            color = FitsizeColor.Danger,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.error_generic),
            style = FitsizeType.supporting,
            color = FitsizeColor.Danger.copy(alpha = 0.86f),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = detail,
                style = FitsizeType.micro,
                color = FitsizeColor.Danger.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Preset copy                                                                */
/* ------------------------------------------------------------------------- */

private fun presetTitleRes(preset: CompressionPreset): Int = when (preset) {
    CompressionPreset.BALANCED -> R.string.preset_balanced_title
    CompressionPreset.SMALLER -> R.string.preset_smaller_title
    CompressionPreset.SMALLEST -> R.string.preset_smallest_title
}

private fun presetBodyRes(preset: CompressionPreset): Int = when (preset) {
    CompressionPreset.BALANCED -> R.string.preset_balanced_body
    CompressionPreset.SMALLER -> R.string.preset_smaller_body
    CompressionPreset.SMALLEST -> R.string.preset_smallest_body
}

/* ------------------------------------------------------------------------- */
/* Preview                                                                    */
/* ------------------------------------------------------------------------- */

@Preview(name = "Preset row", widthDp = 360, showBackground = true)
@Composable
private fun PresetRowPreview() {
    FitsizeTheme {
        Column(
            modifier = Modifier
                .background(FitsizeColor.Background)
                .padding(Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            PresetRow(
                preset = CompressionPreset.BALANCED,
                selected = true,
                estimateBytes = 612L * 1024L * 1024L,
                sourceBytes = 1024L * 1024L * 1024L,
                enabled = true,
                onClick = {},
            )
            PresetRow(
                preset = CompressionPreset.SMALLER,
                selected = false,
                estimateBytes = 320L * 1024L * 1024L,
                sourceBytes = 1024L * 1024L * 1024L,
                enabled = true,
                onClick = {},
            )
        }
    }
}
