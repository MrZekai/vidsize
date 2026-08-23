package com.fitsize.compressor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitsize.compressor.R
import com.fitsize.compressor.model.CompressionPreset
import com.fitsize.compressor.model.CompressionResult
import com.fitsize.compressor.ui.components.AdLabel
import com.fitsize.compressor.ui.components.MrecSlot
import com.fitsize.compressor.ui.components.PrimaryButton
import com.fitsize.compressor.ui.components.SecondaryButton
import com.fitsize.compressor.ui.components.TertiaryButton
import com.fitsize.compressor.ui.format.Fmt
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeTheme
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Space

/**
 * Result sheet.
 *
 * A bottom-anchored sheet rather than an inline card, because the result is a
 * moment, not a row: the compression finished, here is the win, here is what to
 * do with it. Built on [Dialog] rather than `ModalBottomSheet` so the surface has
 * no experimental Material API in its path — this screen must not break on a
 * Material 3 point release.
 *
 * Ad placement follows the plan exactly: the 300x250 MREC sits **below** all
 * three actions, after the user has seen the outcome. Nothing full-screen fires
 * here, and the ad's space is reserved so the buttons never move under a thumb
 * that is already travelling towards them.
 */
@Composable
fun ResultSheet(
    result: CompressionResult,
    onDismiss: () -> Unit,
    onCompressAnother: () -> Unit,
) {
    val context = LocalContext.current
    val savedBytes = (result.sourceBytes - result.outputBytes).coerceAtLeast(0L)
    val percent = Fmt.percentSmaller(result.sourceBytes, result.outputBytes)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetMaxHeight = maxHeight * 0.94f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = sheetMaxHeight),
                shape = FitsizeShape.sheet,
                color = FitsizeColor.Surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(Space.sm))
                    SheetHandle()
                    Spacer(Modifier.height(Space.lg))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SuccessMark()

                        Spacer(Modifier.height(Space.md))

                        Text(
                            text = stringResource(R.string.result_title),
                            style = FitsizeType.screenTitle,
                            color = FitsizeColor.Ink,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(Space.lg))

                        ResultFigures(
                            sourceBytes = result.sourceBytes,
                            outputBytes = result.outputBytes,
                            savedBytes = savedBytes,
                            percent = percent,
                        )

                        Spacer(Modifier.height(Space.sm))

                        Text(
                            text = stringResource(
                                R.string.result_meta,
                                presetTitle(result.preset),
                                Fmt.elapsed(result.elapsedMs),
                            ),
                            style = FitsizeType.caption,
                            color = FitsizeColor.Faint,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(Space.xl))

                        PrimaryButton(
                            text = stringResource(R.string.result_share),
                            onClick = { shareVideo(context, result.outputUri) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = R.drawable.ic_share,
                        )

                        Spacer(Modifier.height(Space.xs))

                        SecondaryButton(
                            text = stringResource(R.string.result_open),
                            onClick = { openVideo(context, result.outputUri) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(Space.xxs))

                        TertiaryButton(
                            text = stringResource(R.string.result_another),
                            onClick = onCompressAnother,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(Space.xs))

                        Text(
                            text = stringResource(R.string.result_location),
                            style = FitsizeType.micro,
                            color = FitsizeColor.Faint,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(Space.lg))

                        AdLabel()

                        Spacer(Modifier.height(6.dp))
                    }

                    MrecSlot(modifier = Modifier.padding(horizontal = Space.md))

                    Spacer(Modifier.height(Space.lg))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Pieces                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
            .clip(FitsizeShape.chip)
            .background(FitsizeColor.Border),
    )
}

@Composable
private fun SuccessMark() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(FitsizeShape.chip)
            .background(FitsizeColor.MintSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = FitsizeColor.Mint,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun ResultFigures(
    sourceBytes: Long,
    outputBytes: Long,
    savedBytes: Long,
    percent: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FitsizeShape.large,
        color = FitsizeColor.IndigoSoft,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    R.string.result_transform,
                    Fmt.bytes(sourceBytes),
                    Fmt.bytes(outputBytes),
                ),
                style = FitsizeType.figure,
                color = FitsizeColor.Ink,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.result_saved,
                    Fmt.bytes(savedBytes),
                    percent,
                ),
                style = FitsizeType.caption,
                color = FitsizeColor.Indigo,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Intents                                                                    */
/* ------------------------------------------------------------------------- */

private fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser)),
        )
    }
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun presetTitle(preset: CompressionPreset): String =
    stringResource(
        when (preset) {
            CompressionPreset.BALANCED -> R.string.preset_balanced_title
            CompressionPreset.SMALLER -> R.string.preset_smaller_title
            CompressionPreset.SMALLEST -> R.string.preset_smallest_title
        },
    )

/* ------------------------------------------------------------------------- */
/* Preview                                                                    */
/* ------------------------------------------------------------------------- */

@Preview(name = "Result figures", widthDp = 360, showBackground = true)
@Composable
private fun ResultFiguresPreview() {
    FitsizeTheme {
        Column(modifier = Modifier.padding(Space.lg)) {
            SuccessMark()
            Spacer(Modifier.height(Space.md))
            ResultFigures(
                sourceBytes = 1_095_216_660L,
                outputBytes = 641_728_512L,
                savedBytes = 453_488_148L,
                percent = 41,
            )
        }
    }
}
