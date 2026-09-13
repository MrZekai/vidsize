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
import com.vidsize.compressor.ads.AdFreeWindow
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ads.findHostActivity
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType

/**
 * The rewarded-ad offer, and the countdown it turns into.
 *
 * ## Why it is a strip on Home and not a row in Settings
 *
 * This is the highest-eCPM unit in the app and the only thing Vidsize can offer
 * a user who does not want ads, since there is no paid tier. Hiding it behind a
 * settings sheet is the difference between a unit that earns and a unit that
 * exists. It sits in the main scroll, visible without scrolling on a 360dp
 * phone, from the very first launch - there is no "let the user see some ads
 * first" threshold, because the banner is already on screen and the offer is
 * therefore already meaningful.
 *
 * It is deliberately NOT adjacent to the Select Video button. Tapping it opens a
 * full-screen ad, so the trust row sits between the two and a thumb travelling
 * to the primary action never passes over this card.
 *
 * ## The copy is a policy artefact, not marketing
 *
 * AdMob's rewarded policy requires that the user be told, before they opt in,
 * both what they are about to watch and what they get for it. "Watch a video"
 * does not satisfy that - it fails to disclose that the video is an ad. The
 * strings therefore say "a short ad video" and state the duration explicitly.
 *
 * The duration is interpolated from [AdFreeWindow.REWARD_DURATION_MINUTES]
 * rather than written into the strings, in all eight languages. A reward whose
 * code says ten minutes and whose Turkish string says fifteen is a live policy
 * problem and one of the easiest regressions to ship; making the number
 * impossible to state independently removes the category.
 */
@Composable
fun AdFreeStrip(modifier: Modifier = Modifier) {
    val inspecting = LocalInspectionMode.current
    val context = LocalContext.current

    // Leading spacing lives inside each rendered branch rather than in the host,
    // so a strip with nothing to show costs exactly zero pixels instead of
    // leaving a gap between the trust row and the Recent section.

    // The window is open: countdown, no offer.
    if (AdFreeWindow.remainingMillis > 0L) {
        Spacer(Modifier.height(Space.sm))
        ActiveWindowCard(modifier = modifier)
        return
    }

    if (inspecting) {
        Spacer(Modifier.height(Space.sm))
        OfferCard(modifier = modifier, enabled = true, onWatch = {})
        return
    }

    // The offer only appears when it can actually be honoured. A button that
    // opens nothing is worse than no button, and it is also how a user concludes
    // the reward is fake.
    if (!AdSlots.rewardedRequestable) return

    LaunchedEffect(AdSlots.rewardedRequestable) {
        RewardedAds.preload(context)
    }

    if (!RewardedAds.isLoaded) return

    Spacer(Modifier.height(Space.sm))
    OfferCard(
        modifier = modifier,
        enabled = !RewardedAds.isShowing,
        onWatch = {
            context.findHostActivity()?.let { activity ->
                RewardedAds.show(activity) { /* AdFreeWindow.grant() already ran */ }
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
                    text = stringResource(
                        R.string.ad_free_title,
                        AdFreeWindow.REWARD_DURATION_MINUTES,
                    ),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.ad_free_body,
                        AdFreeWindow.REWARD_DURATION_MINUTES,
                    ),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }
        }

        Spacer(Modifier.height(Space.sm))

        SecondaryButton(
            text = stringResource(R.string.ad_free_action),
            onClick = onWatch,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

@Composable
private fun ActiveWindowCard(modifier: Modifier) {
    VidsizeCard(
        modifier = modifier.fillMaxWidth(),
        color = VidsizeColor.MintSoft,
        border = VidsizeColor.MintBorder,
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
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = VidsizeColor.Mint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ad_free_active_title),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.ad_free_active_body,
                        AdFreeWindow.formatRemaining(),
                    ),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }
        }
    }
}

@Preview(name = "Ad-free · offer", widthDp = 360, showBackground = true)
@Composable
private fun AdFreeOfferPreview() {
    VidsizeTheme {
        OfferCard(modifier = Modifier, enabled = true, onWatch = {})
    }
}

@Preview(name = "Ad-free · active", widthDp = 360, showBackground = true)
@Composable
private fun AdFreeActivePreview() {
    VidsizeTheme {
        ActiveWindowCard(modifier = Modifier)
    }
}
