package com.vidsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType

/** Why the rewarded route is still waiting on the user's choice. */
enum class RewardedChoiceMessage {
    UNAVAILABLE,
    NOT_EARNED,
}

/**
 * The final, explicit export decision shown after COMPRESS is tapped.
 *
 * Both outcomes are visible at the same time. The ordinary path starts one
 * marked export immediately. The optional path states its price and reward
 * before the ad starts, then creates one clean export after the SDK confirms
 * the reward. Nothing is encoded twice.
 */
@Composable
fun OutputChoiceDialog(
    waitingForAd: Boolean,
    markFreeEnabled: Boolean,
    message: RewardedChoiceMessage?,
    onWithMark: () -> Unit,
    onWithoutMark: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!waitingForAd) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !waitingForAd,
            dismissOnClickOutside = !waitingForAd,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = VidsizeShape.extraLarge,
                color = VidsizeColor.Surface,
            ) {
                Column(
                    modifier = Modifier.padding(Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.watermark_choice_title),
                        style = VidsizeType.screenTitle,
                        color = VidsizeColor.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = stringResource(R.string.watermark_choice_body),
                        style = VidsizeType.body,
                        color = VidsizeColor.Muted,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(Space.lg))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        OutputChoiceCard(
                            title = stringResource(R.string.watermark_with_mark_title),
                            body = stringResource(R.string.watermark_with_mark_body),
                            badge = stringResource(R.string.watermark_with_mark_badge),
                            icon = R.drawable.ic_video_file,
                            enabled = !waitingForAd,
                            onClick = onWithMark,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutputChoiceCard(
                            title = stringResource(R.string.watermark_free_card_title),
                            body = stringResource(R.string.watermark_free_card_body),
                            badge = stringResource(
                                if (waitingForAd) {
                                    R.string.watermark_free_card_waiting
                                } else {
                                    R.string.watermark_free_card_badge
                                },
                            ),
                            icon = R.drawable.ic_shield,
                            enabled = markFreeEnabled && !waitingForAd,
                            onClick = onWithoutMark,
                            modifier = Modifier.fillMaxWidth(),
                            highlighted = true,
                        )
                    }

                    if (AdSlots.bannerVisible) {
                        Spacer(Modifier.height(Space.lg))
                        HairLine()
                        Spacer(Modifier.height(Space.sm))
                        Eyebrow(
                            text = stringResource(R.string.ad_label),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Space.xs))
                        CompressionBannerAd(modifier = Modifier.fillMaxWidth())
                    }

                    val messageText = when (message) {
                        RewardedChoiceMessage.UNAVAILABLE ->
                            stringResource(R.string.watermark_free_unavailable)
                        RewardedChoiceMessage.NOT_EARNED ->
                            stringResource(R.string.watermark_reward_not_earned)
                        null -> null
                    }
                    if (messageText != null) {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = messageText,
                            style = VidsizeType.caption,
                            color = VidsizeColor.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(Space.xs))
                    TertiaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !waitingForAd,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputChoiceCard(
    title: String,
    body: String,
    badge: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    VidsizeCard(
        modifier = modifier.heightIn(min = 116.dp),
        color = if (highlighted) VidsizeColor.IndigoSoft else VidsizeColor.SurfaceMuted,
        border = if (highlighted) VidsizeColor.IndigoBorder else VidsizeColor.Border,
        elevation = 0.dp,
        contentPadding = Space.md,
        onClick = onClick,
        clickEnabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (highlighted) VidsizeColor.Indigo else VidsizeColor.InkSoft,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Space.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = VidsizeType.cardTitle,
                    color = if (enabled) VidsizeColor.Ink else VidsizeColor.Faint,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    style = VidsizeType.supporting,
                    color = if (enabled) VidsizeColor.Muted else VidsizeColor.Faint,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = badge,
                    style = VidsizeType.micro,
                    color = if (highlighted) VidsizeColor.Indigo else VidsizeColor.InkSoft,
                    modifier = Modifier
                        .clip(VidsizeShape.small)
                        .background(VidsizeColor.Surface)
                        .padding(horizontal = Space.xs, vertical = 3.dp),
                )
            }
        }
    }
}

/** Brief confirmation shown after the rewarded creative closes. */
@Composable
fun RewardGrantedDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = VidsizeShape.extraLarge,
            color = VidsizeColor.Surface,
        ) {
            Column(
                modifier = Modifier.padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
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
                Spacer(Modifier.height(Space.md))
                Text(
                    text = stringResource(R.string.watermark_reward_granted_title),
                    style = VidsizeType.figure,
                    color = VidsizeColor.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = stringResource(R.string.watermark_reward_granted_body),
                    style = VidsizeType.body,
                    color = VidsizeColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun OutputChoicePreview() {
    VidsizeTheme {
        OutputChoiceDialog(
            waitingForAd = false,
            markFreeEnabled = true,
            message = null,
            onWithMark = {},
            onWithoutMark = {},
            onDismiss = {},
        )
    }
}
