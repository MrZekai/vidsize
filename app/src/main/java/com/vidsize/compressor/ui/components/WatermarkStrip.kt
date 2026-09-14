package com.vidsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.vidsize.compressor.ads.findHostActivity
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeType

/**
 * The offer to remove the Vidsize mark from the file the user just made.
 *
 * ## Why it lives on the result screen and nowhere else
 *
 * The predecessor (`AdFreeStrip`) appeared on Home, on the compression screen
 * and on the result screen, because the thing it sold - ten quiet minutes - was
 * not attached to anything in particular. This one is attached to one specific
 * file, so it belongs on the one screen where that file is the subject.
 *
 * Showing it on Home would be asking the user to pay for a property of a video
 * they have not chosen yet; showing it during compression would be asking them
 * to decide while a progress ring is spinning.
 *
 * ## Why the mark is applied first and removed second
 *
 * Offering the choice BEFORE the export would cost one encode instead of two,
 * and that was the original design. It was changed deliberately: an offer to
 * avoid a mark nobody has seen is an abstraction, and it arrives at the moment
 * the user is least willing to read anything - they have picked a video and
 * want it compressed.
 *
 * After the export the mark is a fact on screen, the offer is concrete, and the
 * user is idle. The price is a second encode from the ORIGINAL source, which
 * the caller is responsible for starting; this component only sells the ad.
 *
 * ## The rewarded unit is not gated by [AdSlots.requestable]
 *
 * It asks [AdSlots.rewardedRequestable], which is consent alone. A rewarded ad
 * the user taps is exempt from the rules the unexpected formats live under, and
 * gating it on the same predicate would make it the one format that can decline
 * for reasons having nothing to do with the user's own choice.
 */
@Composable
fun WatermarkStrip(
    enabled: Boolean,
    onRewardGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    if (!inspecting && !AdSlots.rewardedRequestable) return

    // Kept warm, because this strip is only ever rendered at a moment the user
    // might act on it immediately.
    LaunchedEffect(Unit) {
        if (!inspecting) RewardedAds.preload(context)
    }

    OfferCard(
        modifier = modifier,
        enabled = enabled,
        onWatch = {
            context.findHostActivity()?.let { activity ->
                RewardedAds.show(activity, onRewardGranted)
            }
        },
    )
}

@Composable
private fun OfferCard(
    modifier: Modifier,
    enabled: Boolean,
    onWatch: () -> Unit,
) {
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
                    .size(40.dp)
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    tint = VidsizeColor.Indigo,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.watermark_offer_title),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // Says plainly that the file is made again. The second
                    // encode is the price, and a user who is told about it up
                    // front reads the wait as expected rather than as a fault.
                    text = stringResource(R.string.watermark_offer_body),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }
        }

        Spacer(Modifier.height(Space.sm))

        SecondaryButton(
            text = stringResource(R.string.watermark_offer_action),
            onClick = onWatch,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WatermarkStripPreview() {
    VidsizeTheme {
        OfferCard(modifier = Modifier, enabled = true, onWatch = {})
    }
}
