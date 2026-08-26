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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdIds
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeType

@Composable
fun HomeBannerAd(modifier: Modifier = Modifier) {
    AdaptiveBannerAd(
        unitId = AdIds.homeBanner,
        modifier = modifier,
        includeNavigationPadding = true,
        active = true,
    )
}

@Composable
fun CompressionBannerAd(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    AdaptiveBannerAd(
        unitId = AdIds.compressionBanner,
        modifier = modifier,
        includeNavigationPadding = false,
        active = active,
    )
}

/**
 * Stable anchored-ad slot.
 *
 * The slot is reserved while UMP / Mobile Ads resolves, so a late ad can never
 * push a primary control under a travelling finger. If UMP resolves to a state
 * where ads cannot be requested, the slot is removed. While compression runs,
 * [active] is false: the geometry stays reserved but no AdView remains behind
 * the processing scrim.
 */
@Composable
private fun AdaptiveBannerAd(
    unitId: String?,
    modifier: Modifier,
    includeNavigationPadding: Boolean,
    active: Boolean,
) {
    val inspecting = LocalInspectionMode.current

    if (!inspecting && ConsentManager.consentResolved && !ConsentManager.canRequestAds) {
        return
    }

    val containerModifier = if (includeNavigationPadding) {
        modifier
            .fillMaxWidth()
            .background(VidsizeColor.Surface)
            .navigationBarsPadding()
    } else {
        modifier
            .fillMaxWidth()
            .background(VidsizeColor.Surface)
    }

    val showCreative = inspecting || (active && ConsentManager.adsAllowed)

    Column(modifier = containerModifier) {
        // Fixed disclosure row: the text may appear later, its geometry does not.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = Space.gutter),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showCreative) {
                Text(
                    text = stringResource(R.string.ad_label),
                    style = VidsizeType.micro,
                    color = VidsizeColor.InkSoft,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val context = LocalContext.current
            val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
            val adSize = remember(context, widthDp) {
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
            }
            val slotHeight = adSize.height.coerceAtLeast(50).dp

            if (inspecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight)
                        .background(VidsizeColor.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Adaptive banner",
                        style = VidsizeType.micro,
                        color = VidsizeColor.Muted,
                    )
                }
            } else if (active && ConsentManager.adsAllowed) {
                if (!unitId.isNullOrBlank()) {
                    val adView = remember(context, adSize, unitId) {
                        AdView(context).apply {
                            adUnitId = unitId
                            setAdSize(adSize)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                    val lifecycleOwner = context as? LifecycleOwner

                    DisposableEffect(adView, lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> adView.resume()
                                Lifecycle.Event.ON_PAUSE -> adView.pause()
                                else -> Unit
                            }
                        }
                        lifecycleOwner?.lifecycle?.addObserver(observer)
                        if (lifecycleOwner?.lifecycle?.currentState
                                ?.isAtLeast(Lifecycle.State.RESUMED) == true
                        ) {
                            adView.resume()
                        }

                        onDispose {
                            lifecycleOwner?.lifecycle?.removeObserver(observer)
                            adView.pause()
                            adView.destroy()
                        }
                    }

                    AndroidView(
                        factory = { adView },
                        modifier = Modifier
                            .width(adSize.width.dp)
                            .height(slotHeight),
                    )
                } else {
                    Spacer(Modifier.height(slotHeight))
                }
            } else {
                // Consent/SDK pending or compression active: reserve, do not serve.
                Spacer(Modifier.height(slotHeight))
            }
        }

        // Keep the compression CTA comfortably separated from the creative.
        Spacer(Modifier.height(if (includeNavigationPadding) 8.dp else 20.dp))
    }
}
