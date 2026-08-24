package com.vidsize.compressor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.vidsize.compressor.R
import com.vidsize.compressor.media.CompressionJobState
import com.vidsize.compressor.media.CompressionPlanner
import com.vidsize.compressor.media.CompressionService
import com.vidsize.compressor.media.StorageGuard
import com.vidsize.compressor.media.VideoProbe
import com.vidsize.compressor.media.rememberVideoThumbnail
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import com.vidsize.compressor.model.VideoInfo
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.components.PrimaryButton
import com.vidsize.compressor.ui.components.SectionHeader
import com.vidsize.compressor.ui.components.TintedPill
import com.vidsize.compressor.ui.format.Fmt
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compression screen.
 *
 * Structure mirrors Home: a fixed bar at the top, one scrolling body, and a
 * fixed action bar at the bottom that clears the navigation bar. The primary
 * action never scrolls out of reach — on a 360dp phone with a long preset list
 * that is the difference between a considered product and a form.
 *
 * Progress is real, not decorative: [CompressionService] reports encoder progress
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

    var info by remember(videoUri) { mutableStateOf<VideoInfo?>(null) }
    var probeFailed by remember(videoUri) { mutableStateOf(false) }
    var preset by remember(videoUri) { mutableStateOf(CompressionPreset.BALANCED) }

    // The job lives in CompressionService, not in this screen, so that leaving
    // the app does not kill it. The screen is a pure view over that state.
    val jobStatus = CompressionJobState.status
    val busy = jobStatus is CompressionJobState.Status.Running
    val result = (jobStatus as? CompressionJobState.Status.Done)?.result
    val failure = jobStatus as? CompressionJobState.Status.Failed

    // Asked for at the moment of first use rather than at launch. A denial is
    // not fatal: the service still runs, it just cannot show progress.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // Pre-flight: what this preset is likely to produce, and whether the device
    // has room for it. Recomputed when the user switches preset.
    val selectedEstimate = info?.let {
        CompressionPlanner.plan(it, preset).estimatedOutputBytes
    }
    val storage = remember(selectedEstimate, context) {
        selectedEstimate?.let { StorageGuard.check(context, it) }
    }

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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        CompressionService.start(context, videoUri, preset)
    }

    // Fires once per finished job so History picks the new row up.
    LaunchedEffect(result) {
        result?.let(onCompleted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Background),
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
                    style = VidsizeType.screenTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.compress_subtitle),
                    style = VidsizeType.body,
                    color = VidsizeColor.Muted,
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
                    style = VidsizeType.caption,
                    color = VidsizeColor.Faint,
                )

                if (storage != null && !storage.hasRoom) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Blocking,
                        title = stringResource(R.string.notice_no_space_title),
                        body = stringResource(
                            R.string.notice_no_space_body,
                            Fmt.bytes(storage.requiredBytes),
                            Fmt.bytes(storage.availableBytes),
                        ),
                    )
                } else if (currentInfo != null && StorageGuard.isLongJob(currentInfo)) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Info,
                        title = stringResource(R.string.notice_long_job_title),
                        body = stringResource(R.string.notice_long_job_body),
                    )
                }

                if (failure != null) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Error,
                        title = stringResource(R.string.error_title),
                        body = stringResource(
                            if (failure.outOfSpace) R.string.error_storage
                            else R.string.error_generic,
                        ),
                        detail = failure.message,
                    )
                }

                Spacer(Modifier.height(Space.xl))
            }

            CompressionActionBar(
                enabled = info != null && !busy && storage?.hasRoom != false,
                onCompress = { startCompression() },
            )
        }

        val running = jobStatus as? CompressionJobState.Status.Running
        if (running != null) {
            ProcessingOverlay(
                progress = running.progress,
                progressKnown = running.progressKnown,
                onCancel = { CompressionService.cancel(context) },
            )
        }
    }

    val finished = result
    if (finished != null) {
        ResultSheet(
            result = finished,
            onDismiss = { CompressionJobState.reset() },
            onCompressAnother = {
                CompressionJobState.reset()
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
            .background(VidsizeColor.Background)
            .statusBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = { if (enabled) onBack() },
            tint = if (enabled) VidsizeColor.InkSoft else VidsizeColor.Faint,
        )
    }
}

@Composable
private fun CompressionActionBar(enabled: Boolean, onCompress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VidsizeColor.Background),
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

    VidsizeCard(
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
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.SurfaceMuted),
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
                        tint = VidsizeColor.Faint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (info != null && info.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(VidsizeShape.chip)
                            .background(Color(0xCC0F1222))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = Fmt.duration(info.durationMs),
                            style = VidsizeType.micro,
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
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
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
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
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

    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        clickEnabled = enabled,
        role = Role.RadioButton,
        shape = VidsizeShape.large,
        color = if (selected) VidsizeColor.IndigoSoft else VidsizeColor.Surface,
        border = if (selected) VidsizeColor.Indigo else VidsizeColor.Border,
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
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(presetBodyRes(preset)),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
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
                    style = VidsizeType.cardTitle,
                    color = if (selected) VidsizeColor.Indigo else VidsizeColor.Ink,
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
            .clip(VidsizeShape.chip)
            .background(if (selected) VidsizeColor.Indigo else VidsizeColor.SurfaceMuted),
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
                    .clip(VidsizeShape.chip)
                    .background(VidsizeColor.Border),
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
            .clip(VidsizeShape.chip)
            .background(if (selected) Color.White else VidsizeColor.SurfaceMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(VidsizeShape.chip)
                .background(
                    if (selected) VidsizeColor.Indigo else VidsizeColor.Indigo.copy(alpha = 0.35f),
                ),
        )
    }
}

private enum class NoticeTone { Info, Blocking, Error }

/**
 * One notice component for three situations: an informational heads-up, a
 * blocking pre-flight failure, and a post-run error. Same shape, different tone,
 * so the screen never grows a second visual language for messages.
 */
@Composable
private fun NoticeCard(
    tone: NoticeTone,
    title: String,
    body: String,
    detail: String? = null,
) {
    val accent = when (tone) {
        NoticeTone.Info -> VidsizeColor.Cyan
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.Danger
    }
    val background = when (tone) {
        NoticeTone.Info -> VidsizeColor.CyanSoft
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.DangerSoft
    }
    val border = when (tone) {
        NoticeTone.Info -> VidsizeColor.CyanBorder
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.DangerBorder
    }

    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        border = border,
        elevation = 0.dp,
        contentPadding = Space.md,
    ) {
        Text(
            text = title,
            style = VidsizeType.cardTitle,
            color = accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = VidsizeType.supporting,
            color = accent.copy(alpha = 0.86f),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = detail,
                style = VidsizeType.micro,
                color = accent.copy(alpha = 0.7f),
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
    VidsizeTheme {
        Column(
            modifier = Modifier
                .background(VidsizeColor.Background)
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
