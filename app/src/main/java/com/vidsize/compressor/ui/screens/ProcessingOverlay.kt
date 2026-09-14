package com.vidsize.compressor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ui.components.ProgressRing
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import kotlin.math.roundToInt


/**
 * Processing state.
 *
 * A full-bleed scrim with one centred panel. It intentionally blocks the screen
 * behind it: while an export is running, every other control on the compression
 * screen would either do nothing or corrupt the job, so showing them as tappable
 * would be a lie.
 *
 * ## v0.8.4: one panel, one lifecycle
 *
 * This composable is called from exactly **one** call site in
 * [CompressionScreen]. It used to be called from two branches of an
 * `if (running) ... else if (starting) ...`, which put it in two different
 * composition slots: crossing from "starting" to "running" disposed one panel
 * and built another, restarting every animation inside it. Combined with the old
 * fixed 72-degree hint arc that read, on a real device, as two separate
 * compressions - one that ran and then reset to 1%.
 *
 * The panel is now created once when processing begins and destroyed once when
 * it ends. [progressKnown] only changes what is painted inside the ring, never
 * the layout, so nothing moves when the first real figure arrives.
 *
 * ## No advertising on this surface at all
 *
 * Up to v0.9.6 this panel carried a 320x50 banner above the ring. Review
 * finding, and it was right: while the user is watching a task they are waiting
 * on, an ad above the progress indicator makes the panel read as an advertising
 * container, competes with the one thing the user is actually looking at, and
 * puts a tappable creative in a modal whose only other control is Cancel.
 *
 * The placement was removed rather than moved. Rewarded is where this product
 * earns; a banner bought at the cost of the app's most anxious moment is a bad
 * trade even before the accidental-click risk.
 *
 * No full-screen ad is ever shown here either. A full-screen ad over a
 * running task is the placement AdMob's policy calls out explicitly, and it is
 * the single loudest complaint in this category's one-star reviews.
 */
@Composable
fun ProcessingOverlay(
    progress: Float,
    progressKnown: Boolean,
    onCancel: () -> Unit,
) {
    val blocker = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Scrim)
            .clickable(
                interactionSource = blocker,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Centred by the Box when it fits; the scroll modifier lets the content
        // scroll inside the viewport when a short screen plus a large font scale
        // would otherwise clip the Cancel button. Either way the card is measured
        // against the viewport, so the panel never grows under the system bars.
        VidsizeCard(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Space.sm, vertical = Space.sm)
                .widthIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            elevation = 24.dp,
            // Zero content padding, with each child carrying its own horizontal
            // padding. Kept from the banner era: it costs nothing now and the
            // children are already written for it.
            contentPadding = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.xl))

                ProgressRing(
                    progress = progress,
                    trackOnly = !progressKnown,
                    centreLabel = if (progressKnown) {
                        "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
                    } else {
                        null
                    },
                )

                Spacer(Modifier.height(Space.lg))

                Text(
                    text = stringResource(R.string.processing_title),
                    modifier = Modifier.fillMaxWidth(),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = when {
                        !progressKnown -> stringResource(R.string.processing_preparing)
                        // The last 10% of the reported figure is the MediaStore
                        // copy, not the encode. Saying "saving" there is accurate.
                        progress >= 0.90f -> stringResource(R.string.processing_finishing)
                        else -> stringResource(R.string.processing_body)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(Space.xl))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}

@Preview(name = "Processing - preparing", widthDp = 360, heightDp = 720)
@Composable
private fun ProcessingPreparingPreview() {
    VidsizeTheme {
        ProcessingOverlay(progress = 0f, progressKnown = false, onCancel = {})
    }
}

@Preview(name = "Processing - running", widthDp = 360, heightDp = 720)
@Composable
private fun ProcessingRunningPreview() {
    VidsizeTheme {
        ProcessingOverlay(progress = 0.42f, progressKnown = true, onCancel = {})
    }
}
