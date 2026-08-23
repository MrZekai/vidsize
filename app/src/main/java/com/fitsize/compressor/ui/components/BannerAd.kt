package com.fitsize.compressor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun FitsizeBannerAd(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }

        FitsizeFixedAd(
            adSize = adSize,
            modifier = Modifier
                .width(adSize.width.dp)
                .height(adSize.height.dp),
        )
    }
}

@Composable
fun FitsizeMrecAd(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        FitsizeFixedAd(
            adSize = AdSize.MEDIUM_RECTANGLE,
            modifier = Modifier
                .width(300.dp)
                .height(250.dp),
        )
    }
}

@Composable
private fun FitsizeFixedAd(
    adSize: AdSize,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val adView = remember(context, adSize) {
        AdView(context).apply {
            adUnitId = TEST_BANNER_ID
            setAdSize(adSize)
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier,
    )
}
