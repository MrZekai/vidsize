package com.vidsize.compressor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ads.suppressAppOpenOnReturn
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.components.NativeAdCard
import com.vidsize.compressor.ui.components.PrimaryButton
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.components.TertiaryButton
import com.vidsize.compressor.ui.format.Fmt
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space

/**
 * Result page.
 *
 * ## v0.8.4: a real screen, not a sheet
 *
 * This used to be a `Dialog` holding a bottom-anchored `Surface` capped at 94%
 * of the window height. On a real device that produced three problems at once:
 * the success moment looked like a popup over a dimmed compression screen; the
 * 94% cap plus the bottom anchor meant the Native Advanced card sat below the
 * fold with its call-to-action partly outside the visible area; and anything
 * that grew inside the sheet pushed every button upward.
 *
 * It is now an ordinary full-viewport screen rendered in place of the
 * compression screen: normal background, normal insets, one scrolling column.
 * The whole page scrolls, so the native creative - including its CTA - is always
 * fully reachable, and the actions never move under a travelling thumb.
 *
 * Back closes the result and returns to the compression screen for the same
 * video; [onBack] is responsible for clearing the finished job state so no stale
 * `Done` can be rendered against the next video.
 */
@Composable
fun ResultScreen(
    result: CompressionResult,
    onBack: () -> Unit,
    onCompressAnother: () -> Unit,
) {
    val context = LocalContext.current
    val adsVisible = ConsentManager.adsAllowed || LocalInspectionMode.current
    val savedBytes = (result.sourceBytes - result.outputBytes).coerceAtLeast(0L)
    val percent = Fmt.percentSmaller(result.sourceBytes, result.outputBytes)

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Background)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.sm))

                SuccessMark()

                Spacer(Modifier.height(Space.md))

                Text(
                    text = stringResource(R.string.result_title),
                    style = VidsizeType.screenTitle,
                    color = VidsizeColor.Ink,
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
                    style = VidsizeType.caption,
                    color = VidsizeColor.Faint,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Space.lg))

                PrimaryButton(
                    text = stringResource(R.string.result_share),
                    onClick = { shareVideo(context, result.outputUri) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.ic_share,
                )

                Spacer(Modifier.height(Space.xs))

                SecondaryButton(
                    text = stringResource(R.string.result_show_in_gallery),
                    onClick = { showInGallery(context, result.outputUri) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Space.xxs))

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
                    style = VidsizeType.micro,
                    color = VidsizeColor.Faint,
                    textAlign = TextAlign.Center,
                )

                // A full gutter of separation so a thumb travelling to
                // "Compress another video" cannot land on the creative.
                if (adsVisible) {
                    Spacer(Modifier.height(Space.xxl))
                    NativeAdCard(modifier = Modifier.fillMaxWidth())
                }

                // The page scrolls, and this trailing space is what guarantees
                // the native call-to-action can be brought clear of the
                // navigation bar instead of resting under it.
                Spacer(Modifier.height(Space.xxxl))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Pieces                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun SuccessMark() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(VidsizeShape.chip)
            .background(VidsizeColor.MintSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = VidsizeColor.Mint,
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
        shape = VidsizeShape.large,
        color = VidsizeColor.IndigoSoft,
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
                style = VidsizeType.figure,
                color = VidsizeColor.Ink,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.result_saved,
                    Fmt.bytes(savedBytes),
                    percent,
                ),
                style = VidsizeType.caption,
                color = VidsizeColor.Indigo,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Intents                                                                    */
/* ------------------------------------------------------------------------- */

private fun shareVideo(context: Context, uri: Uri) {
    context.suppressAppOpenOnReturn()
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

/**
 * Opens the saved file in the device's gallery/photos app, which is the only
 * reliable way to "show the file where it lives" across OEMs.
 *
 * A literal folder-browser intent (ACTION_VIEW on a directory document) is
 * honoured by some Files apps and ignored by many others, so the gallery route
 * is used first and a generic chooser is the fallback.
 */
private fun showInGallery(context: Context, uri: Uri) {
    context.suppressAppOpenOnReturn()
    val gallery = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val launched = runCatching {
        context.startActivity(gallery)
    }.isSuccess
    if (!launched) {
        runCatching {
            context.startActivity(
                Intent.createChooser(gallery, context.getString(R.string.result_show_in_gallery)),
            )
        }
    }
}

private fun openVideo(context: Context, uri: Uri) {
    context.suppressAppOpenOnReturn()
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
    VidsizeTheme {
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
