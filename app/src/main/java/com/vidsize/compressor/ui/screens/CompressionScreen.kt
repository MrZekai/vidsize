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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.vidsize.compressor.model.VideoInfo
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ads.AdDiagnostics
import com.vidsize.compressor.ads.InterstitialAds
import com.vidsize.compressor.ads.findHostActivity
import com.vidsize.compressor.ui.components.AdFreeStrip
import com.vidsize.compressor.ui.components.CompressionBannerAd
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
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (pendingStart) {
            pendingStart = false
            starting = true
            InterstitialAds.preload(context)
            CompressionService.start(context, videoUri, preset)
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
    val selectedPlan: CompressionPlan? = plans?.get(preset)
    val anyViable = plans?.values?.any { it.viable } ?: true
    val storage = remember(selectedPlan?.estimatedOutputBytes, info?.sourceBytes, context) {
        selectedPlan?.let {
            StorageGuard.check(context, it.estimatedOutputBytes, info?.sourceBytes ?: 0L)
        }
    }
    val currentInfo = info
    val blockedByStorage = storage != null && !storage.hasRoom

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

    fun startCompression() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Defer the start to the permission callback rather than racing it.
            pendingStart = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        starting = true
        InterstitialAds.preload(context)
        CompressionService.start(context, videoUri, preset)
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

            // Fixed top banner: separated from Back by non-clickable intro copy,
            // and far away from the primary COMPRESS button. Switched off for the
            // whole processing phase - preparing included - so this one and the
            // banner inside ProcessingOverlay are never live at the same time.
            CompressionBannerAd(active = !processing)

            // Without this divider the scrolling preset rows come to rest flush
            // against the banner's bottom edge, which is an accidental-click
            // surface the moment the user scrolls.
            HairLine()
            Spacer(Modifier.height(Space.xs))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
            ) {
                // The rewarded offer, repeated here on purpose.
                //
                // This is the strongest moment in the app to make it. The user
                // is one tap from starting a job that will take two to five
                // minutes and will end in an interstitial; "watch a short ad
                // video, get ten minutes with no ads" is a trade that makes
                // obvious sense right now in a way it does not on Home, where
                // the user has not yet committed to anything.
                //
                // Hidden during processing: the offer opens a full-screen ad,
                // and nothing covers a running job.
                if (!processing) {
                    AdFreeStrip(modifier = Modifier.fillMaxWidth())
                }

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
                } else if (blockedByStorage && storage != null) {
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
                if (!probeFailed) {
                    Spacer(Modifier.height(Space.xl))

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

                    Spacer(Modifier.height(Space.xxs))

                    Text(
                        text = stringResource(R.string.estimate_note),
                        style = VidsizeType.caption,
                        color = VidsizeColor.Faint,
                    )
                }

                Spacer(Modifier.height(Space.xl))
            }

            CompressionActionBar(
                text = stringResource(
                    if (probeFailed) R.string.cta_select_video else R.string.cta_compress,
                ),
                enabled = if (probeFailed) {
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
                    probeFailed -> null
                    info == null -> null
                    processing -> null
                    !anyViable -> stringResource(R.string.cta_blocked_no_savings)
                    blockedByStorage -> stringResource(R.string.cta_blocked_no_space)
                    selectedPlan?.viable == false -> stringResource(R.string.cta_blocked_level)
                    else -> null
                },
                onClick = {
                    if (probeFailed) onSelectAnother() else startCompression()
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
        failure.reason == CompressionJobState.FailureReason.NO_SAVINGS ||
        failure.reason == CompressionJobState.FailureReason.TIMEOUT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_title)) },
        text = {
            Column {
                Text(stringResource(bodyRes))

                // Shown in every build, not just debug. When a user reports
                // "it does nothing", this one line is the difference between a
                // reproducible bug and a shrug - and it is the reason QA had to
                // read logcat to characterise BUG-05 at all.
                val detail = failure.debugMessage
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

private enum class NoticeTone { Info, Blocking, Error }

/**
 * One notice component for three situations: an informational heads-up, a
 * blocking pre-flight failure, and a post-run error. Same shape, different tone,
 * so the screen never grows a second visual language for messages.
 */
@Composable
private fun NoticeCard(
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
