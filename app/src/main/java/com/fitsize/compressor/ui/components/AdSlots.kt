package com.fitsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitsize.compressor.R
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Sizes
import com.fitsize.compressor.ui.theme.Space
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Google's official demo banner unit.
 *
 * Development and closed testing run entirely on test units. Swap this for the
 * real unit id only at production release, and never tap a live ad on your own
 * device — that is the fastest route to an AdMob suspension.
 */
private const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"

/** Same demo unit, requested at 300x250 for the result sheet. */
private const val TEST_MREC_UNIT = "ca-app-pub-3940256099942544/6300978111"

/**
 * Anchored adaptive banner, pinned to the bottom of the Home screen.
 *
 * Three deliberate choices here:
 *  1. The slot sits on the app's own muted surface with a hairline above it, so
 *     it reads as a separate strip rather than as a black box glued to content.
 *  2. Its height is reserved before the ad fills, so the layout never jumps —
 *     a jumping layout is what produces accidental taps and invalid traffic.
 *  3. There is breathing room above it, so a mis-aimed tap on the last row of
 *     content does not land on the creative.
 */
@Composable
fun AnchoredBannerSlot(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FitsizeColor.SurfaceMuted),
    ) {
        HairLine()
        Spacer(Modifier.height(Space.xs))
        AdLabel(modifier = Modifier.padding(horizontal = Space.gutter))
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xs),
            contentAlignment = Alignment.Center,
        ) {
            val inspecting = LocalInspectionMode.current
            val context = LocalContext.current
            val widthDp = maxWidth.value.toInt().coerceAtLeast(1)

            if (inspecting) {
                AdPlaceholder(widthDp = widthDp, heightDp = 50)
            } else {
                val adSize = remember(widthDp) {
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
                }
                Box(
                    modifier = Modifier
                        .width(adSize.width.dp)
                        .height(adSize.height.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FixedSizeAd(adSize = adSize, unitId = TEST_BANNER_UNIT)
                }
            }
        }
    }
}

/**
 * 300x250 medium rectangle for the result sheet.
 *
 * The full 300x250dp is reserved whether or not an ad fills, so the sheet's
 * height is stable and the buttons above never shift under the user's thumb.
 */
@Composable
fun MrecSlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val inspecting = LocalInspectionMode.current
        Box(
            modifier = Modifier
                .width(Sizes.mrecWidth)
                .height(Sizes.mrecHeight)
                .clip(FitsizeShape.medium)
                .background(FitsizeColor.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            if (inspecting) {
                AdPlaceholder(widthDp = 300, heightDp = 250)
            } else {
                FixedSizeAd(adSize = AdSize.MEDIUM_RECTANGLE, unitId = TEST_MREC_UNIT)
            }
        }
    }
}

/** "Advertisement" disclosure. Small, grey, never hidden. */
@Composable
fun AdLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.ad_label),
        modifier = modifier,
        style = FitsizeType.micro,
        color = FitsizeColor.Faint,
    )
}

@Composable
private fun FixedSizeAd(adSize: AdSize, unitId: String) {
    val context = LocalContext.current
    val adView = remember(context, adSize, unitId) {
        AdView(context).apply {
            adUnitId = unitId
            setAdSize(adSize)
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = Modifier
            .width(adSize.width.dp)
            .height(adSize.height.dp),
    )
}

/** Neutral stand-in so Android Studio previews never try to load a real ad. */
@Composable
private fun AdPlaceholder(widthDp: Int, heightDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(FitsizeShape.small)
            .background(FitsizeColor.Border),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Ad slot ${widthDp}x$heightDp",
            style = FitsizeType.micro,
            color = FitsizeColor.Muted,
        )
    }
}
