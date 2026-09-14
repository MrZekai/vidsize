package com.vidsize.compressor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType

/**
 * The mark-free option, offered before the encode rather than after it.
 *
 * ## Why one line and not a second button
 *
 * The obvious design is two buttons - "Compress" and "Compress without the
 * mark" - and it was rejected for three reasons.
 *
 * Two equally weighted primary actions turn one clear decision into a fork, and
 * a user who came here to compress a video now has to choose before they can
 * proceed. Worse, the fork is a vocabulary test: "watermark" is not a word
 * every user of a free video tool knows, and in a two-button layout there is no
 * way past the screen without understanding it.
 *
 * And a free path that has been visually demoted next to a paid-with-an-ad path
 * starts to look like functionality behind an ad wall, which is a policy
 * problem as well as a fairness one.
 *
 * One line under the primary button avoids all three. The user who does not
 * know the word is not blocked by it - the big button still does the obvious
 * thing - and the user who does know it is one tap away.
 *
 * ## Why before the encode
 *
 * Choosing here costs ONE encode. The result-screen offer, which stays as the
 * recovery path for anyone who did not notice this line, costs two: the marked
 * export plus a full re-encode from the original. Same reward, half the work,
 * and the user is told what the file will contain before it exists rather than
 * discovering it in a player - or, worse, in the chat they already sent it to.
 */
@Composable
fun WatermarkFreeRow(
    enabled: Boolean,
    waiting: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    // Rewarded is the one format that is not gated on AdSlots.requestable: the
    // user asks for it by name. Consent still applies.
    if (!inspecting && !AdSlots.rewardedRequestable) return

    LaunchedEffect(Unit) {
        if (!inspecting) RewardedAds.preload(context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !waiting) { onChoose() }
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = if (enabled) VidsizeColor.Indigo else VidsizeColor.Muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Space.xxs))
        Text(
            text = stringResource(
                if (waiting) R.string.watermark_free_row_waiting else R.string.watermark_free_row,
            ),
            style = VidsizeType.caption,
            color = if (enabled) VidsizeColor.Indigo else VidsizeColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WatermarkFreeRowPreview() {
    VidsizeTheme {
        WatermarkFreeRow(enabled = true, waiting = false, onChoose = {})
    }
}
