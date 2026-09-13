package com.vidsize.compressor.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidsize.compressor.BuildConfig
import com.vidsize.compressor.VidsizeApplication
import com.vidsize.compressor.ads.AdDiagnostics
import com.vidsize.compressor.ads.AdFreeWindow
import com.vidsize.compressor.ads.AdGate
import com.vidsize.compressor.ads.AdPacing
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.theme.Space
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeType
import kotlinx.coroutines.delay

/**
 * The hidden ad diagnostics screen. Seven taps on the version row in Settings.
 *
 * ## What this is for
 *
 * Answering, on the device, the only question that matters during ad QA: *why
 * did no ad appear?* Six different causes produce one identical symptom, so
 * without this screen a tester can only report "I didn't see an ad", which is
 * not actionable and is how weeks disappear.
 *
 * ## Read the last line first
 *
 * Everything above the final panel is raw state. The final panel is the
 * conclusion, in one sentence, in plain language. Do not ask a tester to
 * interpret a column of counters and work out which condition is the binding
 * one - the app already knows, so the app says it.
 *
 * ## Deliberately English and deliberately not translated
 *
 * This surface never reaches a user. Keeping it out of `strings.xml` keeps the
 * eight translated files free of developer text and keeps the locale-parity
 * regression gate meaningful.
 */
@Composable
fun AdDiagnosticsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as? VidsizeApplication

    // A one-second refresh, because half of what this screen reports is a clock:
    // seconds since the last full-screen ad, seconds until pacing allows the
    // next, minutes left in the ad-free window. A static snapshot would make the
    // 60-second rule impossible to watch cross its threshold, which is the one
    // thing a tester most often needs to see happen.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            AdFreeWindow.refresh()
            tick += 1
        }
    }

    if (application == null) return
    val snapshot = remember(tick) {
        AdDiagnostics.snapshot(application.appOpenAdPolicy, application.appOpenAdManager)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VidsizeColor.Background),
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
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = Space.lg),
                ) {
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        text = "Ad diagnostics",
                        style = VidsizeType.screenTitle,
                        color = VidsizeColor.Ink,
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = "Internal build surface. Not shown to users.",
                        style = VidsizeType.supporting,
                        color = VidsizeColor.Muted,
                    )

                    Section("Build")
                    Line("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Line("ENABLE_ADS", snapshot.adsEnabledInBuild.yesNo())
                    Line("USE_TEST_ADS", snapshot.usingTestUnits.yesNo())
                    Line(
                        "Sample unit leaked",
                        snapshot.sampleUnitLeaked.yesNo(),
                        alarming = snapshot.sampleUnitLeaked && !snapshot.usingTestUnits,
                    )

                    Section("Consent")
                    Line("UMP resolved", snapshot.consentResolved.yesNo())
                    Line("Can request ads", snapshot.canRequestAds.yesNo())
                    Line("Mobile Ads SDK ready", snapshot.sdkReady.yesNo())

                    Section("Usage")
                    Line("Compressions completed", snapshot.compressions.toString())
                    Line("Sessions", snapshot.sessions.toString())
                    Line("Interstitials this session", snapshot.interstitialsThisSession.toString())
                    Line("Interstitials today", snapshot.interstitialsToday.toString())

                    Section("Pacing — the only rule in code")
                    Line(
                        "Since last full-screen ad",
                        if (snapshot.secondsSinceLastFullScreen < 0) {
                            "never shown"
                        } else {
                            "${snapshot.secondsSinceLastFullScreen}s"
                        },
                    )
                    Line("Gap required", "${AdPacing.FULL_SCREEN_GAP_MILLIS / 1000}s")
                    Line("60s rule satisfied", snapshot.pacingSatisfied.yesNo())
                    if (!snapshot.pacingSatisfied) {
                        Line("Wait", "${snapshot.secondsUntilPacingAllows}s")
                    }

                    Section("Rewarded ad-free window")
                    Line("Active", snapshot.adFreeActive.yesNo())
                    Line("Reward length", "${AdFreeWindow.REWARD_DURATION_MINUTES} min")
                    if (snapshot.adFreeActive) {
                        Line("Remaining", snapshot.adFreeRemaining)
                    }

                    Section("Inventory")
                    Line("Interstitial preloaded", snapshot.interstitialLoaded.yesNo())
                    Line("Interstitial pending (deferred)", snapshot.interstitialPending.yesNo())
                    Line("Rewarded preloaded", snapshot.rewardedLoaded.yesNo())
                    Line("App open preloaded", snapshot.appOpenLoaded.yesNo())
                    Line("App open suppressed next", snapshot.appOpenSuppressed.yesNo())

                    Spacer(Modifier.height(Space.xl))
                    HairLine()
                    Spacer(Modifier.height(Space.md))

                    // The one line a tester is meant to read.
                    VerdictPanel(snapshot.verdict)

                    Spacer(Modifier.height(Space.xl))
                    SecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.xl))
                }
            }
        }
    }
}

/**
 * The conclusion, stated rather than implied.
 *
 * Every branch names the binding condition AND what to do about it. "60s rule
 * not satisfied" alone still leaves a tester guessing whether to wait or to file
 * a bug; "wait N seconds and retry" does not.
 */
@Composable
private fun VerdictPanel(verdict: AdGate.Verdict) {
    val (headline, detail, tone) = when (verdict) {
        AdGate.Verdict.ALLOWED -> Triple(
            "An interstitial CAN show right now.",
            "All conditions pass and a creative is loaded.",
            VidsizeColor.Mint,
        )
        AdGate.Verdict.ADS_DISABLED -> Triple(
            "No ads in this build.",
            "ENABLE_ADS is false: this variant was packaged without a complete " +
                "set of real AdMob identifiers. Nothing will ever show. Supply " +
                "the six Gradle properties and rebuild.",
            VidsizeColor.Danger,
        )
        AdGate.Verdict.NO_CONSENT -> Triple(
            "Blocked: consent.",
            "UMP has not resolved yet, or the user refused. Ads cannot be " +
                "requested. Check the privacy options row in Settings.",
            VidsizeColor.Danger,
        )
        AdGate.Verdict.AD_FREE_WINDOW -> Triple(
            "Blocked: the ad-free window is open.",
            "The user watched a rewarded ad. No ad of any kind will show until " +
                "the countdown above reaches zero. This is correct behaviour.",
            VidsizeColor.Indigo,
        )
        AdGate.Verdict.RUNNING_JOB -> Triple(
            "Blocked: a compression is running.",
            "No full-screen ad ever covers a job in progress. Wait for the job " +
                "to finish, then return to the result screen.",
            VidsizeColor.Indigo,
        )
        AdGate.Verdict.RESULT_WAITING -> Triple(
            "Blocked: a finished result has not been seen yet.",
            "Applies to the app-open ad only, so the user is never met by an ad " +
                "in front of the video they just waited for.",
            VidsizeColor.Indigo,
        )
        AdGate.Verdict.PACING -> Triple(
            "Blocked: the 60-second rule.",
            "A full-screen ad was shown less than 60 seconds ago. Wait for the " +
                "counter above, then retry. This is the only pacing rule in code.",
            VidsizeColor.Indigo,
        )
        AdGate.Verdict.NOT_LOADED -> Triple(
            "Blocked: no creative loaded.",
            "Every condition passes but no interstitial is in hand. Either the " +
                "request is still in flight or the network returned no fill. " +
                "Start a compression: the request goes out when the job starts.",
            VidsizeColor.Muted,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "VERDICT", style = VidsizeType.eyebrow, color = VidsizeColor.Faint)
        Spacer(Modifier.height(Space.xs))
        Text(text = headline, style = VidsizeType.cardTitle, color = tone)
        Spacer(Modifier.height(Space.xxs))
        Text(text = detail, style = VidsizeType.supporting, color = VidsizeColor.Muted)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Space.lg))
    Text(text = title.uppercase(), style = VidsizeType.eyebrow, color = VidsizeColor.Faint)
    Spacer(Modifier.height(Space.xs))
    HairLine()
    Spacer(Modifier.height(Space.xs))
}

@Composable
private fun Line(label: String, value: String, alarming: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = VidsizeType.supporting,
            color = VidsizeColor.Muted,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = value,
            style = VidsizeType.supporting.copy(fontFamily = FontFamily.Monospace),
            color = if (alarming) VidsizeColor.Danger else VidsizeColor.Ink,
        )
    }
}

private fun Boolean.yesNo(): String = if (this) "yes" else "no"
