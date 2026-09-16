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
import com.vidsize.compressor.ads.AdSlots
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

/**
 * Currently called from nowhere.
 *
 * v0.9.9 removed the compression screen's banner: that screen's fixed chrome
 * had grown to the point where the third compression level was off screen, and
 * this was the cheapest 59dp to reclaim, as well as the placement most likely to
 * be tapped by accident while scrolling a list of options.
 *
 * Kept rather than deleted, and said out loud rather than left to be discovered.
 * The unit id behind it (`VIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID`) is still
 * configured, still gated in CI, and still documented in docs/ADS.md; removing
 * the composable would mean unpicking all of that for a function R8 already
 * strips from every shipped build. If a second banner surface is ever wanted,
 * this is the one to use - but a CI gate now forbids re-adding it to the
 * compression screen specifically, because that is where it did damage.
 */
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

    // One predicate, asked once.
    //
    // QA v0.8.7 BUG-01: a variant with no real identifiers has no ads at all,
    // and returning before any layout is emitted is what removes the "blank
    // white band where a banner should be" that read as a rendering fault - an
    // absent ad costs zero pixels instead of 50dp of empty surface.
    //
    // v0.9.0: this used to spell the condition out itself - `AdSlots.enabled`,
    // then two separate ConsentManager checks - while the App Open manager and
    // the native loader went through AdSlots.requestable. That divergence is
    // precisely what used to let one format disagree with the others about
    // whether ads were permitted. Every automatic ad surface now reads
    // `requestable`, so consent and build configuration stay consistent.
    if (!inspecting && !AdSlots.requestable) return

    // No fill and no id are the same thing to the layout: emit nothing rather
    // than a reserved 320x50 hole.
    if (!inspecting && (!active || unitId.isNullOrBlank())) return

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

        if (unitId.isNullOrBlank()) return@Box

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
