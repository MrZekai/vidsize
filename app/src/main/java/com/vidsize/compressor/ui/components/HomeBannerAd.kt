package com.vidsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.vidsize.compressor.BuildConfig
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeType

private const val TEST_BANNER_UNIT =
    "ca-app-pub-3940256099942544/9214589741"

/**
 * Revenue banner pinned to the bottom of Home.
 *
 * Debug/QA uses Google's official test unit. Release uses
 * VIDSIZE_HOME_BANNER_AD_UNIT_ID. No request is made until UMP allows ads.
 */
@Composable
fun HomeBannerAd(modifier: Modifier = Modifier) {
    val inspecting = LocalInspectionMode.current
    if (!inspecting && !ConsentManager.adsAllowed) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VidsizeColor.Surface)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.ad_label),
            modifier = Modifier.padding(
                horizontal = Space.gutter,
                vertical = 4.dp,
            ),
            style = VidsizeType.micro,
            color = VidsizeColor.Faint,
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val widthDp = maxWidth.value.toInt().coerceAtLeast(1)

            if (inspecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(VidsizeColor.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Adaptive banner",
                        style = VidsizeType.micro,
                        color = VidsizeColor.Muted,
                    )
                }
            } else {
                val context = LocalContext.current
                val unitId = if (BuildConfig.DEBUG) {
                    TEST_BANNER_UNIT
                } else {
                    BuildConfig.HOME_BANNER_AD_UNIT_ID
                }

                if (unitId.isNotBlank()) {
                    val adSize = remember(context, widthDp) {
                        AdSize.getLargeAnchoredAdaptiveBannerAdSize(
                            context,
                            widthDp,
                        )
                    }

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
            }
        }
    }
}
