package com.vidsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType

/**
 * The mark-free option, offered before the encode.
 *
 * ## Why this replaced the one-line version
 *
 * v0.9.5 put this under the primary button as a single caption-sized line. The
 * reasoning was sound - do not fork the screen into two competing primaries,
 * and do not make "watermark" a word the user must know to get past the screen
 * - but the execution overshot. On a real device the line reads as footer text
 * and the eye goes straight from the compression levels to the big purple
 * button. The most valuable action in the app was styled like a disclaimer.
 *
 * It is now a card, and the hierarchy is kept by WHAT it says rather than by
 * how quiet it is:
 *
 *  - The card sells the OUTCOME ("save without the Vidsize mark"), not the ad.
 *    The ad is a small badge, stated plainly, never the headline.
 *  - Its action is a SecondaryButton. The gradient primary below it is still
 *    the one thing that looks like the main action, so the free path is still
 *    visually dominant - which is what keeps this from reading as an ad wall.
 *  - It says in one line that the normal output carries the mark, so the user
 *    cannot finish a job and be surprised by a logo.
 */
@Composable
fun WatermarkFreeCard(
    enabled: Boolean,
    waiting: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    // Keep the disclosure visible even when rewarded inventory cannot be
    // requested. The normal export still contains the mark, and hiding this
    // card would hide that fact along with the ad button.
    val rewardedRequestable = inspecting || AdSlots.rewardedRequestable

    LaunchedEffect(rewardedRequestable) {
        if (!inspecting && rewardedRequestable) RewardedAds.preload(context)
    }

    VidsizeCard(
        modifier = modifier.fillMaxWidth(),
        color = VidsizeColor.SurfaceTint,
        border = VidsizeColor.IndigoBorder,
        elevation = 0.dp,
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    tint = VidsizeColor.Indigo,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.watermark_free_card_title),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // The disclosure. Without this the user can finish a job and
                    // meet a logo they were never told about, which is the
                    // complaint that costs a one-star review.
                    text = stringResource(R.string.watermark_free_card_body),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }

            Spacer(Modifier.width(Space.xs))

            // The cost, stated but not sold. A badge keeps it honest without
            // letting the word "ad" become the headline.
            Text(
                text = stringResource(R.string.watermark_free_card_badge),
                style = VidsizeType.micro,
                color = VidsizeColor.Indigo,
                modifier = Modifier
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.Surface)
                    .padding(horizontal = Space.xs, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(Space.sm))

        SecondaryButton(
            text = stringResource(
                if (waiting) {
                    R.string.watermark_free_card_waiting
                } else {
                    R.string.watermark_free_card_action
                },
            ),
            onClick = onChoose,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !waiting && rewardedRequestable,
        )

        if (!rewardedRequestable) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.watermark_free_unavailable),
                style = VidsizeType.caption,
                color = VidsizeColor.Muted,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WatermarkFreeCardPreview() {
    VidsizeTheme {
        WatermarkFreeCard(enabled = true, waiting = false, onChoose = {})
    }
}
