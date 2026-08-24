package com.vidsize.compressor.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidsize.compressor.BuildConfig
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space

/**
 * Settings.
 *
 * V1 has almost nothing to configure — that is a product decision, not an
 * omission. The sheet answers the three questions a user actually has about a
 * free compressor (why is it light, where do my videos go, who is paying for
 * this) and then carries the legal surface Google Play requires: a reachable
 * privacy policy, terms, and — where the user's region requires it — a permanent
 * entry point back into the ad consent form.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = VidsizeShape.sheet,
                color = VidsizeColor.Surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = Space.lg),
                ) {
                    Spacer(Modifier.height(Space.sm))

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(VidsizeShape.chip)
                                .background(VidsizeColor.Border),
                        )
                    }

                    Spacer(Modifier.height(Space.lg))

                    Text(
                        text = stringResource(R.string.settings_title),
                        style = VidsizeType.screenTitle,
                        color = VidsizeColor.Ink,
                    )

                    Spacer(Modifier.height(Space.lg))

                    InfoRow(
                        icon = R.drawable.ic_palette,
                        tint = VidsizeColor.Indigo,
                        tintSoft = VidsizeColor.IndigoSoft,
                        title = stringResource(R.string.settings_appearance_title),
                        body = stringResource(R.string.settings_appearance_body),
                    )

                    Spacer(Modifier.height(Space.md))

                    InfoRow(
                        icon = R.drawable.ic_shield,
                        tint = VidsizeColor.Mint,
                        tintSoft = VidsizeColor.MintSoft,
                        title = stringResource(R.string.settings_privacy_title),
                        body = stringResource(R.string.settings_privacy_body),
                    )

                    Spacer(Modifier.height(Space.md))

                    InfoRow(
                        icon = R.drawable.ic_info,
                        tint = VidsizeColor.Cyan,
                        tintSoft = VidsizeColor.CyanSoft,
                        title = stringResource(R.string.settings_ads_title),
                        body = stringResource(R.string.settings_ads_body),
                    )

                    Spacer(Modifier.height(Space.lg))
                    HairLine()
                    Spacer(Modifier.height(Space.md))

                    Text(
                        text = stringResource(R.string.settings_legal_section),
                        style = VidsizeType.eyebrow,
                        color = VidsizeColor.Faint,
                    )

                    Spacer(Modifier.height(Space.xs))

                    LinkRow(
                        label = stringResource(R.string.settings_privacy_policy),
                        onClick = { openUrl(context, context.getString(R.string.url_privacy)) },
                    )

                    LinkRow(
                        label = stringResource(R.string.settings_terms),
                        onClick = { openUrl(context, context.getString(R.string.url_terms)) },
                    )

                    // Shown only where the user's region requires a re-openable
                    // consent control. Outside those regions the row would be a
                    // dead end, so it is not rendered at all.
                    if (ConsentManager.privacyOptionsRequired) {
                        LinkRow(
                            label = stringResource(R.string.settings_ad_privacy),
                            onClick = {
                                context.findActivity()?.let { activity ->
                                    ConsentManager.showPrivacyOptions(activity)
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(Space.md))

                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = VidsizeType.micro,
                        color = VidsizeColor.Faint,
                    )

                    Spacer(Modifier.height(Space.lg))

                    SecondaryButton(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(Space.lg))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: Int,
    tint: Color,
    tintSoft: Color,
    title: String,
    body: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(VidsizeShape.small)
                .background(tintSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(Modifier.width(Space.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = VidsizeType.cardTitle,
                color = VidsizeColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = VidsizeType.supporting,
                color = VidsizeColor.Muted,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VidsizeShape.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = VidsizeType.cardTitle,
            color = VidsizeColor.Ink,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = VidsizeColor.Faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Compose's LocalContext is not always the Activity; unwrap it safely. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
