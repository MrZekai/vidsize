package com.vidsize.compressor.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ads.ConsentManager
import com.vidsize.compressor.ads.suppressAppOpenOnReturn
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeType
import kotlinx.coroutines.delay

private enum class LegalPage(val assetFileName: String) {
    Privacy("privacy.html"),
    Terms("terms.html"),
}

/** Taps on the version row that open the hidden ad diagnostics screen. */
private const val DIAGNOSTICS_TAP_COUNT = 7

/** How long a partial tap sequence survives before it is forgotten. */
private const val DIAGNOSTICS_TAP_WINDOW_MS = 2_000L

@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    var legalPageName by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    // Seven taps on the version row opens the ad diagnostics screen.
    //
    // Hidden rather than shipped as a visible row because it is a developer
    // surface, and gesture-based rather than build-flag-gated because the build
    // that most needs diagnosing is the signed one on a real tester's phone -
    // the same binary that goes to Play. A debug-only diagnostics screen can
    // only ever explain a debug build's behaviour, which is not the behaviour
    // anyone is confused about.
    var versionTaps by remember { mutableIntStateOf(0) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }

    // Stray taps decay, so a count never accumulates across a session and opens
    // the screen by accident hours later.
    LaunchedEffect(versionTaps) {
        if (versionTaps in 1 until DIAGNOSTICS_TAP_COUNT) {
            delay(DIAGNOSTICS_TAP_WINDOW_MS)
            versionTaps = 0
        }
    }

    if (showDiagnostics) {
        AdDiagnosticsSheet(
            onDismiss = {
                showDiagnostics = false
                versionTaps = 0
            },
        )
        return
    }

    val page = legalPageName?.let { name ->
        LegalPage.values().firstOrNull { it.name == name }
    }
    if (page != null) {
        LegalDocumentSheet(
            title = stringResource(
                if (page == LegalPage.Privacy) R.string.settings_privacy_policy
                else R.string.settings_terms,
            ),
            assetFileName = page.assetFileName,
            onDismiss = { legalPageName = null },
        )
        return
    }

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

                    if (Build.VERSION.SDK_INT >= 33) {
                        ActionRow(
                            icon = R.drawable.ic_language,
                            tint = VidsizeColor.Indigo,
                            tintSoft = VidsizeColor.IndigoSoft,
                            title = stringResource(R.string.settings_language_title),
                            body = stringResource(R.string.settings_language_body),
                            onClick = {
                                context.suppressAppOpenOnReturn()
                                openLanguageSettings(context)
                            },
                        )
                        Spacer(Modifier.height(Space.md))
                    }

                    ActionRow(
                        icon = R.drawable.ic_notification,
                        tint = VidsizeColor.Cyan,
                        tintSoft = VidsizeColor.CyanSoft,
                        title = stringResource(R.string.settings_notifications_title),
                        body = stringResource(R.string.settings_notifications_body),
                        onClick = {
                            context.suppressAppOpenOnReturn()
                            openNotificationSettings(context)
                        },
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

                    // Claiming "supported by ads" in a build that shows none
                    // would be inaccurate copy, so the row follows the switch.
                    if (AdSlots.enabled) {
                        InfoRow(
                            icon = R.drawable.ic_info,
                            tint = VidsizeColor.Cyan,
                            tintSoft = VidsizeColor.CyanSoft,
                            title = stringResource(R.string.settings_ads_title),
                            body = stringResource(R.string.settings_ads_body),
                        )

                        Spacer(Modifier.height(Space.md))
                    }

                    ActionRow(
                        icon = R.drawable.ic_delete,
                        tint = VidsizeColor.Danger,
                        tintSoft = VidsizeColor.DangerSoft,
                        title = stringResource(R.string.settings_history_title),
                        body = stringResource(R.string.settings_history_body),
                        onClick = { confirmClearHistory = true },
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
                        onClick = { legalPageName = LegalPage.Privacy.name },
                    )

                    LinkRow(
                        label = stringResource(R.string.settings_terms),
                        onClick = { legalPageName = LegalPage.Terms.name },
                    )

                    if (AdSlots.enabled && ConsentManager.privacyOptionsRequired) {
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
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                // No ripple and no role: to a user this stays a
                                // caption, not a control. Announcing it to the
                                // accessibility tree as a button would put a
                                // developer surface in front of screen-reader
                                // users with no way to know what it does.
                                indication = null,
                            ) {
                                versionTaps += 1
                                if (versionTaps >= DIAGNOSTICS_TAP_COUNT) {
                                    versionTaps = 0
                                    showDiagnostics = true
                                }
                            }
                            .padding(vertical = Space.xxs),
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

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.settings_clear_history_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        confirmClearHistory = false
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
        RowIcon(icon, tint, tintSoft)
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = VidsizeType.cardTitle, color = VidsizeColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = VidsizeType.supporting, color = VidsizeColor.Muted)
        }
    }
}

@Composable
private fun ActionRow(
    icon: Int,
    tint: Color,
    tintSoft: Color,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VidsizeShape.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint, tintSoft)
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = VidsizeType.cardTitle, color = VidsizeColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = VidsizeType.supporting, color = VidsizeColor.Muted)
        }
        Spacer(Modifier.width(Space.xs))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = VidsizeColor.Faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RowIcon(icon: Int, tint: Color, tintSoft: Color) {
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

private fun openLanguageSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 33) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
