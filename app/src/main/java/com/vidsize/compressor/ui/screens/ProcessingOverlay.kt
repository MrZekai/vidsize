package com.vidsize.compressor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.ProgressRing
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Processing state.
 *
 * A full-bleed scrim with one centred panel. It intentionally blocks the screen
 * behind it: while an export is running, every other control on the compression
 * screen would either do nothing or corrupt the job, so showing them as tappable
 * would be a lie.
 *
 * No ad is ever shown on this surface. A full-screen ad over a running task is
 * the placement AdMob's policy calls out explicitly, and it is the single
 * loudest complaint in this category's one-star reviews.
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
        VidsizeCard(
            modifier = Modifier
                .padding(horizontal = Space.xl)
                .widthIn(max = 380.dp),
            elevation = 24.dp,
            contentPadding = Space.xl,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = progress,
                    indeterminateSweep = !progressKnown,
                    centreLabel = if (progressKnown) {
                        "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
                    } else {
                        null
                    },
                )
            }

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
                    progress >= 0.99f -> stringResource(R.string.processing_finishing)
                    else -> stringResource(R.string.processing_body)
                },
                modifier = Modifier.fillMaxWidth(),
                style = VidsizeType.supporting,
                color = VidsizeColor.Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Space.lg))

            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Processing", widthDp = 360, heightDp = 700)
@Composable
private fun ProcessingPreview() {
    VidsizeTheme {
        ProcessingOverlay(progress = 0.42f, progressKnown = true, onCancel = {})
    }
}
