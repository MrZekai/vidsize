package com.vidsize.compressor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vidsize.compressor.BuildConfig
import com.vidsize.compressor.R
import com.vidsize.compressor.media.CompressionJobState
import com.vidsize.compressor.media.CompressionPlanner
import com.vidsize.compressor.media.CompressionService
import com.vidsize.compressor.media.StorageGuard
import com.vidsize.compressor.media.VideoProbe
import com.vidsize.compressor.media.rememberVideoThumbnail
import com.vidsize.compressor.model.CompressionPlan
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import com.vidsize.compressor.model.SizeTarget
import com.vidsize.compressor.model.TargetVerdict
import com.vidsize.compressor.model.VideoInfo
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.HairLine
import androidx.compose.runtime.rememberCoroutineScope
import com.vidsize.compressor.ads.AdDiagnostics
import com.vidsize.compressor.ads.InterstitialAds
import com.vidsize.compressor.ads.RewardedAds
import com.vidsize.compressor.ads.WatermarkOffer
import com.vidsize.compressor.ads.findHostActivity
import com.vidsize.compressor.ui.components.WatermarkFreeCard
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.components.PrimaryButton
import com.vidsize.compressor.ui.components.SectionHeader
import com.vidsize.compressor.ui.components.TintedPill
import com.vidsize.compressor.ui.format.Fmt
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Compression screen.
 *
 * Structure mirrors Home: a fixed bar at the top, one scrolling body, and a
 * fixed action bar at the bottom that clears the navigation bar. The primary
 * action never scrolls out of reach - on a 360dp phone with a long preset list
 * that is the difference between a considered product and a form.
 *
 * Progress is real, not decorative: [CompressionService] reports encoder progress
 * and this screen renders it. Until the encoder has produced a figure the ring
 * shows a hint arc rather than a stationary "0%".
 *
 * A preset whose plan is not [CompressionPlan.viable] is shown but not
 * selectable: running it would mean several minutes of work ending in a
 * "compression didn't finish" error.
 */
/**
 * How long the mark-free row waits for a rewarded ad that is not yet in hand.
 *
 * Long enough for a normal fill on a normal connection, short enough that the
 * user does not read it as a hang. Past it the app says so and starts the free
 * export - the result screen still carries the offer, so nothing is lost but
 * one encode.
 */
private const val REWARDED_WAIT_MILLIS = 5_000L

/** Polling interval while waiting. RewardedAds.isLoaded is Compose state. */
private const val REWARDED_POLL_MILLIS = 150L

@Composable
fun CompressionScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onSelectAnother: () -> Unit,
    onCompleted: (CompressionResult) -> Unit,
) {
    val context = LocalContext.current

    var info by remember(videoUri) { mutableStateOf<VideoInfo?>(null) }
    var probeFailed by remember(videoUri) { mutableStateOf(false) }
    var preset by remember(videoUri) { mutableStateOf(CompressionPreset.BALANCED) }

    // True between the tap on COMPRESS and the service reporting Running. Without
    // it the button stays enabled and the screen looks inert for the duration of
    // the service start plus the probe.
    var starting by remember(videoUri) { mutableStateOf(false) }

    // The job lives in CompressionService, not in this screen, so that leaving
    // the app does not kill it. The screen is a pure view over that state.
    val jobStatus = CompressionJobState.status
    val busy = jobStatus is CompressionJobState.Status.Running
    val result = (jobStatus as? CompressionJobState.Status.Done)?.result
    val failure = jobStatus as? CompressionJobState.Status.Failed

    // Never write state during composition: clear the local "starting" latch
    // from an effect once the service has actually reported Running.
    LaunchedEffect(busy) {
        if (busy) starting = false
    }

    // One flag for "a job is on screen". Everything that must be disabled while
    // the overlay is up reads this, so the preparing and running phases can
    // never disagree about what is interactive.
    val processing = busy || starting

    // Asked for at the moment of first use rather than at launch. A denial is
    // not fatal: the service still runs, it just cannot show progress.
    //
    // QA finding: the old code launched the dialog and started the service in
    // the same frame, so startForeground posted its notification while the
    // permission dialog was still up. On a denial-then-grant the channel was
    // already created without permission and the user watched a blank overlay
    // until the first percentage arrived. The service now starts from the
    // permission RESULT - granted or denied, but never concurrently with the
    // question.
    var pendingStart by remember(videoUri) { mutableStateOf(false) }
    var pendingWatermark by remember(videoUri) { mutableStateOf(true) }
    // Held for the same reason as pendingWatermark: the permission dialog is a
    // round trip, and a choice that does not survive it is a choice the user
    // made and did not get.
    var pendingTarget by remember(videoUri) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    var awaitingRewarded by remember(videoUri) { mutableStateOf(false) }
    var rewardedUnavailable by remember(videoUri) { mutableStateOf(false) }

    // Which question the user is answering: "how much quality am I giving up?"
    // or "what size does this have to be under?".
    //
    // Only one of the two is on screen at a time, deliberately. Showing both a
    // preset list and a size row would take more vertical space than this screen
    // has - which is the fault that shipped in v0.9.8, where the mark-free card
    // pushed the third preset off the bottom - and would also ask the user to
    // reconcile two answers that can contradict each other.
    var sizeMode by remember(videoUri) { mutableStateOf(false) }
    var sizeTarget by remember(videoUri) { mutableStateOf(SizeTarget.MB_16) }
    var customMode by remember(videoUri) { mutableStateOf(false) }
    var customText by remember(videoUri) { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (pendingStart) {
            pendingStart = false
            starting = true
            val chosen = pendingWatermark
            val chosenTarget = pendingTarget
            InterstitialAds.preload(context)
            CompressionService.start(
                context,
                videoUri,
                preset,
                watermark = chosen,
                targetBytes = chosenTarget,
            )
        }
    }

    // Pre-flight: what every preset is likely to produce, and whether the device
    // has room for the selected one.
    //
    // All three plans are computed up front rather than inside the row loop, so
    // "is anything viable at all?" is known *before* the screen is laid out. In
    // v0.8.7 that answer only existed after the loop had already run, which is
    // why the explanation for a dead COMPRESS button could only be appended
    // below the preset list - and therefore below the fold (QA BUG-05).
    val plans: Map<CompressionPreset, CompressionPlan>? = info?.let { probed ->
        CompressionPreset.entries.associateWith { CompressionPlanner.plan(probed, it) }
    }
    // The bytes the user is asking for, or null when they are picking a level.
    val targetBytes: Long? = when {
        !sizeMode -> null
        customMode -> customText.toIntOrNull()?.let { SizeTarget.bytesFor(it) }
        else -> sizeTarget.bytes
    }

    // Planned on the same probe as the presets, through the same constants, so
    // the estimate on this screen means the same thing in both modes.
    val targetPlan: CompressionPlan? = remember(info, targetBytes) {
        val probed = info
        val bytes = targetBytes
        if (probed == null || bytes == null) {
            null
        } else {
            runCatching { CompressionPlanner.planForTarget(probed, bytes) }.getOrNull()
        }
    }

    val presetPlan: CompressionPlan? = plans?.get(preset)
    val selectedPlan: CompressionPlan? = if (sizeMode) targetPlan else presetPlan
    val anyViable = plans?.values?.any { it.viable } ?: true
    val storage = remember(selectedPlan?.estimatedOutputBytes, info?.sourceBytes, context) {
        selectedPlan?.let {
            StorageGuard.check(context, it.estimatedOutputBytes, info?.sourceBytes ?: 0L)
        }
    }
    val currentInfo = info
    val blockedByStorage = storage != null && !storage.hasRoom

    // A negative codec-table answer is useful context, not proof. Media3 may
    // still open the source through a lower-priority or software decoder, so it
    // earns an informational notice and never disables the compression action.
    val decoderFallbackExpected = currentInfo != null && !currentInfo.decoderPrecheckPassed

    // Only an actually unreadable file blocks the whole screen. Codec tables do
    // not: the engine is the authority because it performs a real decode.
    val blockedEntirely = probeFailed

    // Never leave the selection parked on a level that cannot run while another
    // one can. v0.8.7 defaulted to Balanced and stayed there, so a source whose
    // Balanced plan was not viable presented a disabled button with no hint that
    // a different level would have worked.
    LaunchedEffect(plans) {
        val available = plans ?: return@LaunchedEffect
        val current = available[preset]
        if (current != null && !current.viable) {
            available.entries.firstOrNull { it.value.viable }?.let { preset = it.key }
        }
    }

    LaunchedEffect(videoUri) {
        // CompressionJobState is a process singleton. Without this, a Failed or
        // Done state left over from the previous video is rendered against the
        // new one before the user has touched anything.
        if (CompressionJobState.status !is CompressionJobState.Status.Running) {
            CompressionJobState.reset()
        }
        val probed = runCatching {
            withContext(Dispatchers.IO) { VideoProbe.probe(context, videoUri) }
        }.getOrNull()
        info = probed
        probeFailed = probed == null
    }

    // Preloading an interstitial is essentially free in this app, and that is a
    // genuine structural advantage over the reader app this ad model came from.
    //
    // The usual failure mode for interstitials is requesting one at the moment
    // of display and losing the impression on a slow connection - which is why
    // the source model argues for a nine-second load window. Vidsize has minutes
    // of runway: the request goes out when the compression screen opens and
    // again when the job starts, and the earliest a result screen can exist is
    // two minutes later. There is no load timeout here because the user is never
    // waiting on this request.
    LaunchedEffect(videoUri) {
        InterstitialAds.preload(context)
    }

    fun startCompression(watermark: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Defer the start to the permission callback rather than racing it.
            // The choice has to survive the round trip, or a user who granted a
            // mark-free export and then met the permission dialog would get a
            // marked file after watching an ad for the opposite.
            pendingWatermark = watermark
            pendingTarget = targetBytes
            pendingStart = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        starting = true
        InterstitialAds.preload(context)
        CompressionService.start(
            context,
            videoUri,
            preset,
            watermark = watermark,
            targetBytes = targetBytes,
        )
    }

    /**
     * The mark-free path: one rewarded ad, then ONE encode with no mark.
     *
     * The ad may not be in hand at the moment of the tap. Rather than refusing
     * or hiding the option - both of which make it look broken - the row waits,
     * briefly and visibly, and then tells the truth and gets on with the job the
     * user actually came for. A marked file plus an honest sentence is a better
     * outcome than a dead control.
     */
    fun startWithoutWatermark() {
        // A grant already in hand is one the user paid for and did not receive -
        // a previous mark-free export that failed. Charging them a second ad for
        // the same reward would be taking payment twice.
        if (WatermarkOffer.granted) {
            startCompression(watermark = false)
            return
        }
        val activity = context.findHostActivity() ?: run {
            startCompression(watermark = true)
            return
        }
        awaitingRewarded = true
        scope.launch {
            val ready = withTimeoutOrNull(REWARDED_WAIT_MILLIS) {
                RewardedAds.preload(context)
                while (!RewardedAds.isLoaded) delay(REWARDED_POLL_MILLIS)
                true
            } == true
            awaitingRewarded = false
            if (!ready) {
                // Not an error and not silent: the user asked for something the
                // network could not supply, and the result screen still carries
                // the offer, so say so and start the free export.
                rewardedUnavailable = true
                startCompression(watermark = true)
                return@launch
            }
            // RewardedAds grants from the SDK's own reward callback; this
            // starts the export the grant paid for. The grant is NOT consumed
            // here - see the completion effect below.
            RewardedAds.show(activity) { startCompression(watermark = false) }
        }
    }

    // The grant is spent HERE, by the export that actually delivered.
    //
    // QA finding: it used to be consumed at launch. A second pass that failed -
    // no space, encoder refusal, the platform's six-hour limit - left the user
    // having watched a rewarded ad and still holding a marked file, with the
    // grant gone. That is a bad trade and an AdMob policy problem: the reward
    // has to be delivered. Failure now leaves the grant standing, so the next
    // attempt costs nothing.
    LaunchedEffect(result) {
        if (result != null && !result.watermarked) WatermarkOffer.consume()
    }

    // Fires once per finished job so History picks the new row up.
    LaunchedEffect(result) {
        result?.let {
            onCompleted(it)
            // Diagnostics only. "How many jobs has this user finished" is the
            // number a tester needs when asking why an interstitial has not
            // appeared yet; it gates nothing.
            AdDiagnostics.recordCompression()
        }
    }

    // The result is a full screen, not a dialog over a dimmed compression
    // screen. Rendering it in place of this screen's content is what makes it a
    // real page: normal background, normal insets, and a page that can scroll
    // far enough to expose the whole native creative.
    val finished = result
    if (finished != null) {
        ResultScreen(
            result = finished,
            // The in-app arrow: a deliberate transition out of a finished
            // job, so it carries the ad like every other such transition.
            //
            // reset() first: AdGate refuses a full-screen ad while the job is
            // non-idle, so the reverse order would be declined every time.
            onBack = {
                CompressionJobState.reset()
                context.findHostActivity()?.let(InterstitialAds::showNow)
            },
            // The system back gesture: same navigation, no ad. Answering a
            // platform gesture with a full-screen ad is the one placement in
            // this model whose risk outweighs its return.
            onSystemBack = { CompressionJobState.reset() },
            onCompressAnother = {
                // The immediate half of the deferred pattern: this is a plain
                // in-app transition back to Home with the work finished, which
                // is exactly the moment an ad belongs.
                //
                // reset() runs FIRST and the ordering is load-bearing. AdGate
                // refuses a full-screen ad while a job is anything other than
                // idle, so showing before the reset would be silently declined
                // on every single attempt - the kind of bug that looks like no
                // fill and takes a week to find.
                CompressionJobState.reset()
                context.findHostActivity()?.let(InterstitialAds::showNow)
                onBack()
            },
        )
        return
    }

    // While an export is running, swallow the back gesture: the user cancels
    // deliberately with the button instead of losing work by accident.
    BackHandler(enabled = processing) { }
    BackHandler(enabled = !processing) { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompressionTopBar(onBack = onBack, enabled = !processing)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter),
            ) {
                Text(
                    text = stringResource(R.string.compress_title),
                    style = VidsizeType.screenTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.compress_subtitle),
                    style = VidsizeType.body,
                    color = VidsizeColor.Muted,
                )
                Spacer(Modifier.height(Space.sm))
            }

            // The fixed top banner was removed in v0.9.9. This is a decision
            // screen, and it had run out of room:
            //
            // Measured on a 393x873dp device, the fixed chrome - banner 50dp,
            // divider and spacing 9dp, mark-free card ~160dp, action bar ~73dp -
            // left a ~366dp window for ~510dp of content. The third compression
            // level was never on screen, and the second one's estimate was cut
            // in half. The user could not see what they were choosing between.
            //
            // Two things paid that back: the mark-free card moved into the
            // scrolling column below, and this banner went. A banner shown while
            // someone is comparing three options is also the banner most likely
            // to be hit by accident on a scroll, and the app still carries four
            // other placements - home banner, result native, interstitial and
            // app-open - so the inventory lost here is the least valuable of
            // them. Space on this screen is worth more than one impression.

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
            ) {
                Spacer(Modifier.height(Space.lg))

                SelectedVideoCard(videoUri = videoUri, info = info, failed = probeFailed)

                // QA v0.8.7 BUG-04 and BUG-05.
                //
                // Anything that blocks the primary action is rendered here -
                // directly under the selected-video card and above the preset
                // list - because that is the only position on a 360x800dp phone
                // that is guaranteed to be on screen without scrolling. In
                // v0.8.7 all of these sat after the preset rows and the estimate
                // note, so a user whose file could not be compressed saw a
                // COMPRESS VIDEO button that did nothing and no explanation
                // anywhere they would look.
                if (probeFailed) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Error,
                        title = stringResource(R.string.error_unreadable_title),
                        body = stringResource(R.string.error_invalid_video),
                    )
                } else if (currentInfo != null && !anyViable) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Blocking,
                        title = stringResource(R.string.notice_no_savings_title),
                        body = stringResource(R.string.error_no_savings),
                    )
                } else if (storage != null && !storage.hasRoom) {
                    // This was `blockedByStorage && storage != null`, and the
                    // compiler warned that the second half is always true:
                    // blockedByStorage is itself defined as
                    // `storage != null && !storage.hasRoom`, so K2 already knew
                    // storage was non-null here. Spelling the predicate out is
                    // the identical condition, drops the dead test, and is the
                    // null check that grants the smart cast used just below.
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Blocking,
                        title = stringResource(R.string.notice_no_space_title),
                        body = stringResource(
                            R.string.notice_no_space_body,
                            Fmt.bytes(storage.requiredBytes),
                            Fmt.bytes(storage.availableBytes),
                        ),
                    )
                } else if (decoderFallbackExpected && currentInfo != null) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Info,
                        title = stringResource(R.string.notice_decoder_fallback_title),
                        body = stringResource(
                            R.string.notice_decoder_fallback_body,
                            currentInfo.width,
                            currentInfo.height,
                        ),
                    )
                } else if (currentInfo != null && StorageGuard.isLongJob(currentInfo)) {
                    Spacer(Modifier.height(Space.md))
                    NoticeCard(
                        tone = NoticeTone.Info,
                        title = stringResource(R.string.notice_long_job_title),
                        body = stringResource(R.string.notice_long_job_body),
                    )
                }

                // A file with no readable video has no compression levels to
                // offer. v0.8.7 still drew all three rows, still highlighted
                // Balanced with a selection tick, and still showed three "—"
                // estimates, which read as "this will work" (QA BUG-04).
                if (!blockedEntirely) {
                    Spacer(Modifier.height(Space.xl))

                    // Which question is being asked. One selector, two bodies -
                    // never both at once. See `sizeMode`.
                    ModeSwitch(
                        sizeMode = sizeMode,
                        enabled = !processing,
                        onSelect = { sizeMode = it },
                    )

                    Spacer(Modifier.height(Space.md))

                    if (sizeMode) {
                        SizeTargetSection(
                            selected = sizeTarget,
                            custom = customMode,
                            customText = customText,
                            plan = targetPlan,
                            sourceBytes = currentInfo?.sourceBytes ?: 0L,
                            enabled = !processing,
                            onSelect = { chosen ->
                                sizeTarget = chosen
                                customMode = false
                            },
                            onCustom = { customMode = true },
                            onCustomText = { customText = it },
                        )
                    } else {
                        SectionHeader(title = stringResource(R.string.section_level))

                        Spacer(Modifier.height(Space.sm))

                        CompressionPreset.entries.forEach { option ->
                            val plan = plans?.get(option)
                            PresetRow(
                                preset = option,
                                selected = option == preset,
                                estimateBytes = plan?.estimatedOutputBytes,
                                sourceBytes = currentInfo?.sourceBytes ?: 0L,
                                viable = plan?.viable ?: true,
                                enabled = !processing,
                                onClick = { if (plan?.viable != false) preset = option },
                            )
                            Spacer(Modifier.height(Space.xs))
                        }
                    }

                    Spacer(Modifier.height(Space.xxs))

                    Text(
                        text = stringResource(R.string.estimate_note),
                        style = VidsizeType.caption,
                        color = VidsizeColor.Faint,
                    )

                    // The mark-free card, INSIDE the scroll now.
                    //
                    // v0.9.7 pinned it above the action bar, and the card is
                    // ~160dp: on a 393x873dp device that left ~366dp for ~510dp
                    // of content, so the third compression level was never on
                    // screen. The choice the user came here to make was hidden
                    // by the offer attached to it.
                    //
                    // The card was right to be a card - as a caption line under
                    // the button it read as a disclaimer - and it is unchanged.
                    // What moved is where it is anchored. Scrolled to the
                    // bottom, which is where a user is when they are about to
                    // tap COMPRESS, it still sits directly above the action bar
                    // and reads exactly as it did. Scrolled to the top, it is
                    // out of the way of the levels.
                    WatermarkFreeCard(
                        enabled = info != null &&
                            !processing &&
                            selectedPlan?.viable == true &&
                            storage?.hasRoom != false,
                        waiting = awaitingRewarded,
                        onChoose = { startWithoutWatermark() },
                        modifier = Modifier.padding(top = Space.lg),
                    )
                    if (rewardedUnavailable) {
                        Text(
                            text = stringResource(R.string.watermark_free_unavailable),
                            style = VidsizeType.caption,
                            color = VidsizeColor.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.xxs),
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
            }

            CompressionActionBar(
                text = stringResource(
                    if (blockedEntirely) R.string.cta_select_video else R.string.cta_compress,
                ),
                enabled = if (blockedEntirely) {
                    !processing
                } else {
                    info != null &&
                        !processing &&
                        selectedPlan?.viable == true &&
                        storage?.hasRoom != false
                },
                // A button that cannot run must say so on itself rather than
                // relying on a notice the user may never scroll to.
                hint = when {
                    blockedEntirely -> null
                    info == null -> null
                    processing -> null
                    // Size-target refusals come first and are specific. "Pick a
                    // different level" is wrong advice for someone who is not
                    // looking at levels, and the two refusals point in opposite
                    // directions - one says ask for less, the other says ask for
                    // more - so they must never collapse into one sentence.
                    sizeMode && targetBytes == null ->
                        stringResource(R.string.cta_blocked_target_missing)
                    targetPlan?.targetVerdict == TargetVerdict.NOT_SMALLER_THAN_SOURCE ->
                        stringResource(R.string.cta_blocked_target_not_smaller)
                    targetPlan?.targetVerdict == TargetVerdict.TOO_SMALL_FOR_DURATION ->
                        stringResource(R.string.cta_blocked_target_too_small)
                    selectedPlan?.alreadyEfficient == true ->
                        stringResource(R.string.cta_blocked_already_efficient)
                    !sizeMode && !anyViable -> stringResource(R.string.cta_blocked_no_savings)
                    blockedByStorage -> stringResource(R.string.cta_blocked_no_space)
                    selectedPlan?.viable == false -> stringResource(R.string.cta_blocked_level)
                    else -> null
                },
                onClick = {
                    if (blockedEntirely) onSelectAnother() else startCompression()
                },
            )
        }

        // ONE call site. Two branches of an if/else put this composable in two
        // different composition slots, so crossing from "starting" to "running"
        // tore the panel down and built a new one - every animation inside it
        // restarted and the ring appeared to go back to the beginning. The panel
        // now lives for exactly as long as the job does.
        val running = jobStatus as? CompressionJobState.Status.Running
        if (processing) {
            ProcessingOverlay(
                progress = running?.progress ?: 0f,
                progressKnown = running?.progressKnown == true,
                onCancel = {
                    starting = false
                    CompressionService.cancel(context)
                },
                pass = running?.pass ?: 1,
                passCeiling = running?.passCeiling ?: 1,
            )
        }

        // QA v0.8.7 BUG-05 - the single most important change in this release.
        //
        // A failure used to be a card appended to the bottom of a scrolling
        // column, which on the test device was entirely below the fold. Combined
        // with a failure that arrives within a few hundred milliseconds - too
        // fast for the progress panel to register as having appeared - the
        // observable behaviour of a failed compression was *nothing at all*.
        //
        // A failure is now a modal dialog. It cannot be off screen, it cannot be
        // scrolled past, and it cannot be mistaken for the app ignoring the tap.
        if (failure != null) {
            FailureDialog(
                failure = failure,
                onDismiss = { CompressionJobState.reset() },
                onSelectAnother = {
                    CompressionJobState.reset()
                    onSelectAnother()
                },
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Failure                                                                    */
/* ------------------------------------------------------------------------- */

/**
 * Modal, unmissable report of a failed compression.
 *
 * Each reason gets its own body text and its own advice, because "try a
 * different compression level" is actively misleading for two of them: a device
 * whose encoder refused the format will refuse it at every level, and a file
 * with no readable video has no level that helps.
 */
@Composable
private fun FailureDialog(
    failure: CompressionJobState.Status.Failed,
    onDismiss: () -> Unit,
    onSelectAnother: () -> Unit,
) {
    val bodyRes = when (failure.reason) {
        CompressionJobState.FailureReason.OUT_OF_SPACE -> R.string.error_storage
        CompressionJobState.FailureReason.INVALID_VIDEO -> R.string.error_invalid_video
        CompressionJobState.FailureReason.NO_SAVINGS -> R.string.error_no_savings
        CompressionJobState.FailureReason.ENCODER_UNSUPPORTED -> R.string.error_encoder_unsupported
        CompressionJobState.FailureReason.SOURCE_UNDECODABLE -> R.string.error_source_undecodable
        CompressionJobState.FailureReason.TIMEOUT -> R.string.error_timeout_body
        CompressionJobState.FailureReason.GENERIC -> R.string.error_generic
    }

    // Picking another video is the useful next step for the two reasons where
    // retrying this one cannot succeed.
    // Picking another video is also the useful step after a timeout: this one
    // hit the platform's daily background limit, so retrying it unchanged will
    // hit the same wall.
    val offerAnotherVideo = failure.reason == CompressionJobState.FailureReason.INVALID_VIDEO ||
        failure.reason == CompressionJobState.FailureReason.ENCODER_UNSUPPORTED ||
        failure.reason == CompressionJobState.FailureReason.SOURCE_UNDECODABLE ||
        failure.reason == CompressionJobState.FailureReason.NO_SAVINGS ||
        failure.reason == CompressionJobState.FailureReason.TIMEOUT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_title)) },
        text = {
            Column {
                Text(stringResource(bodyRes))

                // Debuggable builds only.
                //
                // Review finding, and it was right: a production user was shown
                // "NoCompressionSavingsException: Compressed output is not
                // smaller than the source." A Java class name in a dialog tells
                // the user nothing and tells them the app is unfinished.
                //
                // The QA value is real though, so it is not deleted - it is
                // moved. `adsQa` and `debug` are debuggable and keep the line;
                // closedTest and release do not, and a tester on those builds
                // reads the same text from the hidden diagnostics screen.
                val detail = failure.debugMessage.takeIf { BuildConfig.DEBUG }
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = detail,
                        style = VidsizeType.micro,
                        color = VidsizeColor.Faint,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (offerAnotherVideo) onSelectAnother else onDismiss) {
                Text(
                    stringResource(
                        if (offerAnotherVideo) R.string.cta_pick_another else R.string.try_again,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/* ------------------------------------------------------------------------- */
/* Bars                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
private fun CompressionTopBar(onBack: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VidsizeColor.Background)
            .statusBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = { if (enabled) onBack() },
            tint = if (enabled) VidsizeColor.InkSoft else VidsizeColor.Faint,
        )
    }
}

/**
 * The bottom action bar.
 *
 * [hint] is the fix for the worst part of QA v0.8.7 BUG-05: a full-width,
 * brand-gradient button that reads as the one thing to press, is disabled, and
 * says nothing about why. The reason now sits immediately above it, inside the
 * fixed bar, so it is on screen whenever the button is.
 */
@Composable
private fun CompressionActionBar(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    hint: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VidsizeColor.Background),
    ) {
        HairLine()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
        ) {
            if (hint != null) {
                Text(
                    text = hint,
                    style = VidsizeType.caption,
                    color = VidsizeColor.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
            }
            PrimaryButton(
                text = text,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Source summary                                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun SelectedVideoCard(videoUri: Uri, info: VideoInfo?, failed: Boolean) {
    val thumbnail by rememberVideoThumbnail(videoUri)

    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 72.dp)
                    .clip(VidsizeShape.small)
                    .background(VidsizeColor.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_file),
                        contentDescription = null,
                        tint = VidsizeColor.Faint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (info != null && info.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(VidsizeShape.chip)
                            .background(Color(0xCC0F1222))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = Fmt.duration(info.durationMs),
                            style = VidsizeType.micro,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(text = stringResource(R.string.selected_video))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        info != null -> Fmt.resolution(
                            info.width,
                            info.height,
                            stringResource(R.string.unknown),
                        )
                        failed -> stringResource(R.string.unknown)
                        else -> stringResource(R.string.reading_video)
                    },
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (info != null && info.sourceBytes > 0L) {
                        Fmt.bytes(info.sourceBytes)
                    } else {
                        "—"
                    },
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (info != null && info.height > 0) {
                Spacer(Modifier.width(Space.xs))
                TintedPill(text = Fmt.resolutionBadge(minOf(info.width, info.height)))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Preset row                                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun PresetRow(
    preset: CompressionPreset,
    selected: Boolean,
    estimateBytes: Long?,
    sourceBytes: Long,
    viable: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fraction = if (estimateBytes != null && sourceBytes > 0L) {
        (estimateBytes.toFloat() / sourceBytes.toFloat()).coerceIn(0.06f, 1f)
    } else {
        0f
    }

    VidsizeCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (viable) 1f else 0.55f),
        onClick = onClick,
        clickEnabled = enabled && viable,
        role = Role.RadioButton,
        shape = VidsizeShape.large,
        color = if (selected && viable) VidsizeColor.IndigoSoft else VidsizeColor.Surface,
        border = if (selected && viable) VidsizeColor.Indigo else VidsizeColor.Border,
        elevation = if (selected && viable) 8.dp else 4.dp,
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionDot(selected = selected && viable)

            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(presetTitleRes(preset)),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(presetBodyRes(preset)),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )

                if (viable && fraction > 0f) {
                    Spacer(Modifier.height(Space.xs))
                    SizeBar(fraction = fraction, selected = selected)
                }

                // QA v0.8.7 UX finding: a greyed-out level with no explanation.
                // Greying it out is right - running it would waste minutes for
                // nothing - but the user is owed the one-line reason.
                if (!viable) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = stringResource(R.string.preset_not_viable_hint),
                        style = VidsizeType.caption,
                        color = VidsizeColor.Faint,
                    )
                }
            }

            Spacer(Modifier.width(Space.xs))

            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(text = stringResource(R.string.estimate_short))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        !viable -> stringResource(R.string.preset_not_viable)
                        estimateBytes != null ->
                            stringResource(R.string.estimate_value, Fmt.estimate(estimateBytes))
                        else -> "—"
                    },
                    style = if (viable) VidsizeType.cardTitle else VidsizeType.supporting,
                    color = when {
                        !viable -> VidsizeColor.Faint
                        selected -> VidsizeColor.Indigo
                        else -> VidsizeColor.Ink
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(VidsizeShape.chip)
            .background(if (selected) VidsizeColor.Indigo else VidsizeColor.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(VidsizeShape.chip)
                    .background(VidsizeColor.Border),
            )
        }
    }
}

/**
 * Relative-size bar. Reads as "how much of the original is left" at a glance,
 * which is a far more useful comparison between presets than three numbers.
 */
@Composable
private fun SizeBar(fraction: Float, selected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(VidsizeShape.chip)
            .background(if (selected) Color.White else VidsizeColor.SurfaceMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(VidsizeShape.chip)
                .background(
                    if (selected) VidsizeColor.Indigo else VidsizeColor.Indigo.copy(alpha = 0.35f),
                ),
        )
    }
}

internal enum class NoticeTone { Info, Blocking, Error }

/**
 * One notice component for three situations: an informational heads-up, a
 * blocking pre-flight failure, and a post-run error. Same shape, different tone,
 * so the screen never grows a second visual language for messages.
 *
 * `internal` rather than file-private since v0.9.9: the result screen has to
 * report a size target that was missed, and that is the same kind of message in
 * the same visual language. Copying the component there would have been the
 * first step towards the second visual language this exists to prevent.
 */
@Composable
internal fun NoticeCard(
    tone: NoticeTone,
    title: String,
    body: String,
    detail: String? = null,
) {
    val accent = when (tone) {
        NoticeTone.Info -> VidsizeColor.Cyan
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.Danger
    }
    val background = when (tone) {
        NoticeTone.Info -> VidsizeColor.CyanSoft
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.DangerSoft
    }
    val border = when (tone) {
        NoticeTone.Info -> VidsizeColor.CyanBorder
        NoticeTone.Blocking, NoticeTone.Error -> VidsizeColor.DangerBorder
    }

    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        border = border,
        elevation = 0.dp,
        contentPadding = Space.md,
    ) {
        Text(
            text = title,
            style = VidsizeType.cardTitle,
            color = accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = VidsizeType.supporting,
            color = accent.copy(alpha = 0.86f),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = detail,
                style = VidsizeType.micro,
                color = accent.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Preset copy                                                                */
/* ------------------------------------------------------------------------- */

private fun presetTitleRes(preset: CompressionPreset): Int = when (preset) {
    CompressionPreset.BALANCED -> R.string.preset_balanced_title
    CompressionPreset.SMALLER -> R.string.preset_smaller_title
    CompressionPreset.SMALLEST -> R.string.preset_smallest_title
}

private fun presetBodyRes(preset: CompressionPreset): Int = when (preset) {
    CompressionPreset.BALANCED -> R.string.preset_balanced_body
    CompressionPreset.SMALLER -> R.string.preset_smaller_body
    CompressionPreset.SMALLEST -> R.string.preset_smallest_body
}

/* ------------------------------------------------------------------------- */
/* Preview                                                                    */
/* ------------------------------------------------------------------------- */

@Preview(name = "Preset row", widthDp = 360, showBackground = true)
@Composable
private fun PresetRowPreview() {
    VidsizeTheme {
        Column(
            modifier = Modifier
                .background(VidsizeColor.Background)
                .padding(Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            PresetRow(
                preset = CompressionPreset.BALANCED,
                selected = true,
                estimateBytes = 612L * 1024L * 1024L,
                sourceBytes = 1024L * 1024L * 1024L,
                viable = true,
                enabled = true,
                onClick = {},
            )
            PresetRow(
                preset = CompressionPreset.SMALLER,
                selected = false,
                estimateBytes = 320L * 1024L * 1024L,
                sourceBytes = 1024L * 1024L * 1024L,
                viable = true,
                enabled = true,
                onClick = {},
            )
            PresetRow(
                preset = CompressionPreset.SMALLEST,
                selected = false,
                estimateBytes = 1_000L * 1024L * 1024L,
                sourceBytes = 1024L * 1024L * 1024L,
                viable = false,
                enabled = true,
                onClick = {},
            )
        }
    }
}

/**
 * Chooses between "how much quality?" and "what size?".
 *
 * ## Why a switch and not both sections stacked
 *
 * Stacking them would put a three-row preset list and a six-chip size grid on a
 * screen that, measured, has room for one of them. v0.9.8 already proved what
 * happens when this screen is over-committed: content the user needs goes below
 * the fold silently. It would also invite the user to answer both questions and
 * then wonder which one won.
 *
 * ## Why it does not look like a tab bar
 *
 * Tabs imply two places. This is one place answering one question two ways, and
 * the selected half is filled rather than underlined so the state survives a
 * glance - the failure mode of an underline on a phone is that nobody sees it.
 */
@Composable
private fun ModeSwitch(
    sizeMode: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VidsizeShape.small)
            .background(VidsizeColor.SurfaceMuted)
            .padding(4.dp),
    ) {
        ModeSwitchHalf(
            label = stringResource(R.string.mode_level),
            selected = !sizeMode,
            enabled = enabled,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        ModeSwitchHalf(
            label = stringResource(R.string.mode_size),
            selected = sizeMode,
            enabled = enabled,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSwitchHalf(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(VidsizeShape.small)
            .background(if (selected) VidsizeColor.Surface else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VidsizeType.button,
            color = if (selected) VidsizeColor.Ink else VidsizeColor.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The size-target body: the ceilings people actually run into, plus a field for
 * the one they were given by somebody else.
 *
 * ## Why two fixed rows of three and not a flowing grid
 *
 * Six chips do not fit on one line at 393dp, and a horizontally scrolling row
 * would hide options off the right edge - the same "the user cannot see what
 * they are choosing between" failure this release exists to fix, rotated ninety
 * degrees. Two rows of three, each chip on an equal weight, is deterministic at
 * every width and every font scale.
 *
 * ## Why the outcome card is always present
 *
 * The chips say what was asked for. The card says what the app will actually do
 * about it - the frame it will produce, or the reason it will not. A target the
 * app is going to refuse has to say so here, next to the number, and not only as
 * a hint under a greyed-out button at the bottom of the screen.
 */
@Composable
private fun SizeTargetSection(
    selected: SizeTarget,
    custom: Boolean,
    customText: String,
    plan: CompressionPlan?,
    sourceBytes: Long,
    enabled: Boolean,
    onSelect: (SizeTarget) -> Unit,
    onCustom: () -> Unit,
    onCustomText: (String) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.section_size))

    Spacer(Modifier.height(Space.sm))

    val entries = SizeTarget.entries
    Row(modifier = Modifier.fillMaxWidth()) {
        entries.take(3).forEachIndexed { index, target ->
            if (index > 0) Spacer(Modifier.width(Space.xs))
            SizeChip(
                label = "${target.megabytes} MB",
                selected = !custom && target == selected,
                enabled = enabled,
                onClick = { onSelect(target) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(Space.xs))

    Row(modifier = Modifier.fillMaxWidth()) {
        entries.drop(3).forEach { target ->
            SizeChip(
                label = "${target.megabytes} MB",
                selected = !custom && target == selected,
                enabled = enabled,
                onClick = { onSelect(target) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.xs))
        }
        SizeChip(
            label = stringResource(R.string.size_custom),
            selected = custom,
            enabled = enabled,
            onClick = onCustom,
            modifier = Modifier.weight(1f),
        )
    }

    if (custom) {
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = customText,
            // Digits only, filtered on the way in rather than validated on the
            // way out: a numeric keyboard is a hint, not a guarantee, and a
            // pasted "16 MB" would otherwise sit in the field looking accepted
            // while toIntOrNull quietly returned null and the button stayed
            // dead with no explanation.
            onValueChange = { raw ->
                onCustomText(raw.filter { it.isDigit() }.take(4))
            },
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.size_custom_label)) },
            suffix = { Text("MB") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(Space.sm))

    SizeOutcomeCard(plan = plan, sourceBytes = sourceBytes)
}

@Composable
private fun SizeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(VidsizeShape.small)
            .background(if (selected) VidsizeColor.IndigoSoft else VidsizeColor.Surface)
            .border(
                width = 1.dp,
                color = if (selected) VidsizeColor.IndigoBorder else VidsizeColor.Border,
                shape = VidsizeShape.small,
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.sm, horizontal = Space.xxs)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VidsizeType.button,
            color = if (selected) VidsizeColor.IndigoDeep else VidsizeColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** What the app will do about the requested size, or why it will not. */
@Composable
private fun SizeOutcomeCard(plan: CompressionPlan?, sourceBytes: Long) {
    val verdict = plan?.targetVerdict
    when {
        plan == null -> {
            NoticeCard(
                tone = NoticeTone.Info,
                title = stringResource(R.string.size_pending_title),
                body = stringResource(R.string.size_pending_body),
            )
        }

        verdict == TargetVerdict.NOT_SMALLER_THAN_SOURCE -> {
            NoticeCard(
                tone = NoticeTone.Blocking,
                title = stringResource(R.string.size_not_smaller_title),
                body = stringResource(
                    R.string.size_not_smaller_body,
                    Fmt.bytes(sourceBytes),
                ),
            )
        }

        verdict == TargetVerdict.TOO_SMALL_FOR_DURATION -> {
            NoticeCard(
                tone = NoticeTone.Blocking,
                title = stringResource(R.string.size_too_small_title),
                body = stringResource(R.string.size_too_small_body),
            )
        }

        else -> {
            VidsizeCard(
                modifier = Modifier.fillMaxWidth(),
                color = VidsizeColor.SurfaceTint,
                border = VidsizeColor.IndigoBorder,
                elevation = 0.dp,
                contentPadding = Space.md,
            ) {
                Text(
                    text = stringResource(
                        R.string.size_plan_title,
                        Fmt.bytes(plan.estimatedOutputBytes),
                    ),
                    style = VidsizeType.cardTitle,
                    color = VidsizeColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // The resolution is here because the target planner is
                    // allowed to step it down to make the number reachable, and
                    // a user who asked for 10 MB and silently received 480p
                    // would rightly call that a bug.
                    text = stringResource(
                        R.string.size_plan_body,
                        plan.targetWidth,
                        plan.targetHeight,
                    ),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }
        }
    }
}
