package com.vidsize.compressor.ui.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ads.NativeAdLoader
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape

/**
 * The large Native Advanced ad on the compression result sheet.
 *
 * Renders **nothing at all** until an ad is actually in hand — no placeholder,
 * no reserved grey rectangle. If there is no fill the sheet simply has one less
 * card, which is both better looking and what AdMob's policy expects.
 *
 * The SDK owns every click: the only interactive elements are the asset views
 * registered on [NativeAdView] before `setNativeAd`. Nothing here attaches a
 * click listener, and no Vidsize control is drawn on top of the card.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    if (inspecting) {
        // Android Studio previews must never touch the ads SDK.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(VidsizeShape.extraLarge)
                .background(VidsizeColor.SurfaceMuted),
        )
        return
    }

    val adsAllowed = ConsentManager.adsAllowed
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    // Requested once per result sheet, and only after consent resolves.
    DisposableEffect(adsAllowed) {
        var cancelled = false
        if (adsAllowed && nativeAd == null) {
            NativeAdLoader.load(
                context = context,
                onLoaded = { loaded ->
                    if (cancelled) {
                        // The sheet closed while the request was in flight.
                        loaded.destroy()
                    } else {
                        nativeAd = loaded
                    }
                },
                onFailed = { /* No fill: the card stays absent. */ },
            )
        }
        onDispose {
            cancelled = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.native_ad_result, null) as NativeAdView)
                .also { it.bind(ad) }
        },
        update = { /* Assets are intentionally bound once per NativeAd instance. */ },
    )
}

/**
 * Binds the ad's assets and registers each view with the SDK.
 *
 * Every optional asset is hidden when the creative does not supply it — showing
 * an empty advertiser line or a zero-star rating would misrepresent the ad.
 */
private fun NativeAdView.bind(ad: NativeAd) {
    val media = findViewById<com.google.android.gms.ads.nativead.MediaView>(R.id.ad_media)
    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView>(R.id.ad_body)
    val icon = findViewById<ImageView>(R.id.ad_icon)
    val advertiser = findViewById<TextView>(R.id.ad_advertiser)
    val stars = findViewById<RatingBar>(R.id.ad_stars)
    val cta = findViewById<Button>(R.id.ad_cta)

    mediaView = media
    headlineView = headline
    bodyView = body
    iconView = icon
    advertiserView = advertiser
    starRatingView = stars
    callToActionView = cta

    headline.text = ad.headline

    val bodyText = ad.body
    if (bodyText.isNullOrBlank()) {
        body.visibility = View.GONE
    } else {
        body.text = bodyText
        body.visibility = View.VISIBLE
    }

    val iconAsset = ad.icon
    if (iconAsset?.drawable == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(iconAsset.drawable)
        icon.visibility = View.VISIBLE
    }

    val advertiserText = ad.advertiser
    if (advertiserText.isNullOrBlank()) {
        advertiser.visibility = View.GONE
    } else {
        advertiser.text = advertiserText
        advertiser.visibility = View.VISIBLE
    }

    val rating = ad.starRating
    if (rating == null || rating <= 0.0) {
        stars.visibility = View.GONE
    } else {
        stars.rating = rating.toFloat()
        stars.visibility = View.VISIBLE
    }

    // Never a hard-coded label. If the creative supplies no call to action the
    // button is hidden rather than invented.
    val ctaText = ad.callToAction
    if (ctaText.isNullOrBlank()) {
        cta.visibility = View.GONE
    } else {
        cta.text = ctaText
        cta.visibility = View.VISIBLE
    }

    // Must be the last call: it wires impression and click tracking.
    setNativeAd(ad)
}
