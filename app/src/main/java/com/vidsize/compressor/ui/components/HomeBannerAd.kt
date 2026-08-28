package com.vidsize.compressor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.vidsize.compressor.ads.AdIds
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ui.theme.VidsizeColor

private val BannerWidth = 320.dp
private val BannerHeight = 50.dp

/**
 * Dead space kept between an anchored banner and whatever borders it - the
 * scrolling content above, and the system navigation area below.
 *
 * With gesture navigation `navigationBarsPadding()` is only about 24dp, so a
 * home-swipe that starts a little high, or a thumb reaching for the back button
 * on a three-button bar, lands very close to the creative. This buffer is
 * app-coloured and has no click handler of its own, so it is a genuine miss
 * region rather than a visual gap.
 */
private val SystemEdgeBuffer = 12.dp

@Composable
fun HomeBannerAd(modifier: Modifier = Modifier) {
    FixedBannerAd(
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
    FixedBannerAd(
        unitId = AdIds.compressionBanner,
        modifier = modifier,
        includeNavigationPadding = false,
        active = active,
    )
}

/** Compact standard banner for the two persistent bottom placements. */
@Composable
private fun FixedBannerAd(
    unitId: String?,
    modifier: Modifier,
    includeNavigationPadding: Boolean,
    active: Boolean,
) {
    val inspecting = LocalInspectionMode.current

    if (!inspecting && ConsentManager.consentResolved && !ConsentManager.canRequestAds) {
        return
    }

    val container = if (includeNavigationPadding) {
        modifier
            .fillMaxWidth()
            .background(VidsizeColor.Surface)
            .navigationBarsPadding()
            .padding(vertical = SystemEdgeBuffer)
    } else {
        modifier
            .fillMaxWidth()
            .background(VidsizeColor.Surface)
    }

    Box(
        modifier = container.height(BannerHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (inspecting) {
            Spacer(
                Modifier
                    .width(BannerWidth)
                    .height(BannerHeight)
                    .background(VidsizeColor.SurfaceMuted),
            )
            return@Box
        }

        if (!active || !ConsentManager.adsAllowed || unitId.isNullOrBlank()) {
            Spacer(Modifier.width(BannerWidth).height(BannerHeight))
            return@Box
        }

        val context = LocalContext.current
        val adView = remember(context, unitId) {
            AdView(context).apply {
                adUnitId = unitId
                setAdSize(AdSize.BANNER)
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
            if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
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
            modifier = Modifier.width(BannerWidth).height(BannerHeight),
        )
    }
}
