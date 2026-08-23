package com.fitsize.compressor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitsize.compressor.BuildConfig
import com.fitsize.compressor.R
import com.fitsize.compressor.ui.components.SecondaryButton
import com.fitsize.compressor.ui.theme.FitsizeColor
import com.fitsize.compressor.ui.theme.FitsizeShape
import com.fitsize.compressor.ui.theme.FitsizeType
import com.fitsize.compressor.ui.theme.Space

/**
 * Settings.
 *
 * V1 has nothing to configure — that is a product decision, not an omission. The
 * sheet exists to answer the three questions a user actually has about a free
 * compressor: why is it light, where do my videos go, and who is paying for
 * this. Answering them plainly is worth more than a toggle list.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
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
                shape = FitsizeShape.sheet,
                color = FitsizeColor.Surface,
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
                                .clip(FitsizeShape.chip)
                                .background(FitsizeColor.Border),
                        )
                    }

                    Spacer(Modifier.height(Space.lg))

                    Text(
                        text = stringResource(R.string.settings_title),
                        style = FitsizeType.screenTitle,
                        color = FitsizeColor.Ink,
                    )

                    Spacer(Modifier.height(Space.lg))

                    SettingsRow(
                        icon = R.drawable.ic_palette,
                        tint = FitsizeColor.Indigo,
                        tintSoft = FitsizeColor.IndigoSoft,
                        title = stringResource(R.string.settings_appearance_title),
                        body = stringResource(R.string.settings_appearance_body),
                    )

                    Spacer(Modifier.height(Space.md))

                    SettingsRow(
                        icon = R.drawable.ic_shield,
                        tint = FitsizeColor.Mint,
                        tintSoft = FitsizeColor.MintSoft,
                        title = stringResource(R.string.settings_privacy_title),
                        body = stringResource(R.string.settings_privacy_body),
                    )

                    Spacer(Modifier.height(Space.md))

                    SettingsRow(
                        icon = R.drawable.ic_info,
                        tint = FitsizeColor.Cyan,
                        tintSoft = FitsizeColor.CyanSoft,
                        title = stringResource(R.string.settings_ads_title),
                        body = stringResource(R.string.settings_ads_body),
                    )

                    Spacer(Modifier.height(Space.lg))

                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = FitsizeType.micro,
                        color = FitsizeColor.Faint,
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
private fun SettingsRow(
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
                .clip(FitsizeShape.small)
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
                style = FitsizeType.cardTitle,
                color = FitsizeColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = FitsizeType.supporting,
                color = FitsizeColor.Muted,
            )
        }
    }
}
