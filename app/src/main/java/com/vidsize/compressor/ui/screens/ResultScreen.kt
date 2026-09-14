package com.vidsize.compressor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.ads.AdDiagnostics
import com.vidsize.compressor.ads.deferInterstitialOnReturn
import com.vidsize.compressor.ads.findHostActivity
import com.vidsize.compressor.growth.ReviewPrompt
import com.vidsize.compressor.media.CompressionService
import com.vidsize.compressor.model.CompressionPreset
import com.vidsize.compressor.model.CompressionResult
import com.vidsize.compressor.ui.buildVideoShareIntent
import com.vidsize.compressor.ui.components.WatermarkStrip
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.components.NativeAdCard
import com.vidsize.compressor.ui.components.PrimaryButton
import com.vidsize.compressor.ui.components.SecondaryButton
import com.vidsize.compressor.ui.components.TertiaryButton
import com.vidsize.compressor.ui.format.Fmt
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * How long this screen refuses taps after it appears.
 *
 * Long enough to absorb a touch that was already travelling towards the
 * progress dialog's Cancel button (QA v0.8.7 BUG-08), short enough that a user
 * who deliberately reaches for SHARE never notices it.
 */
private const val ARRIVAL_GUARD_MS = 450L

/**
 * How long the result screen settles before a Play review is requested.
 *
 * Long enough that the dialog reads as a question rather than a glitch, short
 * enough that the user is still on the screen whose result prompted it.
 */
private const val REVIEW_PROMPT_DELAY_MS = 1_800L

/**
 * Result page.
 *
 * ## v0.8.4: a real screen, not a sheet
 *
 * This used to be a `Dialog` holding a bottom-anchored `Surface` capped at 94%
 * of the window height. On a real device that produced three problems at once:
 * the success moment looked like a popup over a dimmed compression screen; the
 * 94% cap plus the bottom anchor meant the Native Advanced card sat below the
 * fold with its call-to-action partly outside the visible area; and anything
 * that grew inside the sheet pushed every button upward.
 *
 * It is now an ordinary full-viewport screen rendered in place of the
 * compression screen: normal background, normal insets, one scrolling column.
 * The whole page scrolls, so the native creative - including its CTA - is always
 * fully reachable, and the actions never move under a travelling thumb.
 *
 * Back closes the result and returns to the compression screen for the same
 * video; [onBack] is responsible for clearing the finished job state so no stale
 * `Done` can be rendered against the next video.
 *
 * ## v0.8.5: the ad is a section, not a footer
 *
 * v0.8.4 still placed the Native Advanced card after every action and the
 * save-location line, which put its top edge about 580dp into the scroll. On a
 * 360x640 phone that is entirely below the fold; on a 393x852 phone about 39% of
 * the card is visible and the call-to-action never is. An ad seen only in
 * fragments is worthless to the advertiser and clutter to the user.
 *
 * The page gave the ad its own labelled section bounded by hairlines, with the
 * summary and Share above it.
 *
 * ## v0.8.8: every way back to the file comes before the ad
 *
 * QA v0.8.7 found that the v0.8.5 arrangement still put "Show in Gallery" and
 * "Open Video" *below* a full-height native creative. On a 720x1600 device that
 * is entirely off screen, so in practice those two actions did not exist - and
 * because the Recent rows on Home were inert as well (BUG-06), leaving this
 * screen meant losing every in-app route back to the compressed file.
 *
 * The order is now: figures, save location, Share, Show in Gallery, Open Video,
 * then the ad section, then "Compress another video". Everything that leads the
 * user to their file is above the fold; the ad keeps a labelled section with
 * real content after it, and it is no longer between the user and the thing
 * they just made.
 *
 * The save location moved up here too - the QA pass noted the app only ever
 * told the user where the file went in the completion notification, which a
 * user who stays in the app never sees.
 */
@Composable
fun ResultScreen(
    result: CompressionResult,
    onBack: () -> Unit,
    onSystemBack: () -> Unit,
    onCompressAnother: () -> Unit,
) {
    val context = LocalContext.current
    // One predicate, asked once. A consent refusal gives this screen no ad
    // section at all - no divider, no "Advertisement" label, no reserved 340dp
    // slot - rather than a labelled empty band.
    val adsVisible = AdSlots.requestable || LocalInspectionMode.current
    val savedBytes = (result.sourceBytes - result.outputBytes).coerceAtLeast(0L)
    val percent = Fmt.percentSmaller(result.sourceBytes, result.outputBytes)

    // QA v0.8.7 BUG-08: a tap aimed at the progress dialog's Cancel button
    // landed on whatever this screen rendered at those coordinates the instant
    // the dialog closed - in v0.8.7 a full-width native ad, which opened a Play
    // Store install sheet.
    //
    // This screen appears by replacing a modal the user may have been reaching
    // for, so for its first fraction of a second nothing here accepts a tap.
    // A touch already in flight is absorbed instead of being routed to an
    // action the user never chose.
    var interactive by remember(result.outputUri) { mutableStateOf(false) }
    LaunchedEffect(result.outputUri) {
        delay(ARRIVAL_GUARD_MS)
        interactive = true
    }

    // The system back gesture and the in-app arrow do the same navigation but
    // are NOT the same event.
    //
    // The arrow is a control this app drew: tapping it is a deliberate in-app
    // transition out of a finished job, and it carries the interstitial like
    // every other such transition.
    //
    // The system gesture is part of Android, not part of Vidsize. Answering it
    // with a full-screen ad is the pattern Play's Better Ads Experiences
    // describes as interfering with navigation, and it is the one placement in
    // this model with a bad risk-to-revenue ratio: the users who leave a result
    // by swiping back rather than tapping an action are a small minority, and
    // every one of them would meet an ad in response to a system gesture.
    //
    // Same destination, different callback, deliberately.
    BackHandler { onSystemBack() }

    // Ask for a Play review at the peak of the experience, not on the way out.
    //
    // The user is looking at a measured result they waited minutes for. That is
    // the moment a person feels like saying something nice, and it is also the
    // moment that is NOT contested by anything else: the interstitial fires on
    // exit, so the two never race for the same instant.
    //
    // The delay lets the screen settle first - a system dialog that arrives in
    // the same frame as the page reads as a glitch rather than a question. It
    // reuses the arrival guard the screen already waits out for BUG-08.
    LaunchedEffect(result.outputUri) {
        delay(REVIEW_PROMPT_DELAY_MS)
        context.findHostActivity()?.let { activity ->
            ReviewPrompt.maybeAsk(activity, AdDiagnostics.compressionCount())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Background)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.sm))

                SuccessMark()

                Spacer(Modifier.height(Space.md))

                Text(
                    text = stringResource(R.string.result_title),
                    style = VidsizeType.screenTitle,
                    color = VidsizeColor.Ink,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Space.lg))

                ResultFigures(
                    sourceBytes = result.sourceBytes,
                    outputBytes = result.outputBytes,
                    savedBytes = savedBytes,
                    percent = percent,
                )

                Spacer(Modifier.height(Space.sm))

                Text(
                    text = stringResource(
                        R.string.result_meta,
                        presetTitle(result.preset),
                        Fmt.elapsed(result.elapsedMs),
                    ),
                    style = VidsizeType.caption,
                    color = VidsizeColor.Faint,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Space.xs))

                // QA v0.8.7 UX finding: "the app never says where the file was
                // saved". The location was only in the completion notification,
                // which a user who stays in the app never sees. It now sits with
                // the figures, above every action.
                Text(
                    text = stringResource(R.string.result_location),
                    style = VidsizeType.caption,
                    color = VidsizeColor.Muted,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Space.md))

                // The action almost everyone wants next stays above the ad, so
                // the common path never has to scroll past a creative.
                // The offer sits ABOVE Share, not below it.
                //
                // QA finding: it used to sit 48 lines under the primary button,
                // after Share, Open Gallery and Open video. The brightest
                // control on the screen therefore sent the marked file before
                // the user had any reason to know there was a mark - and a user
                // who only notices the logo inside the chat they sent it to is
                // past the point where any offer helps.
                //
                // Shown only while this file still carries the mark. Once the
                // clean export lands the offer has nothing left to sell, and a
                // card still sitting there would read as a second charge for
                // something already paid for.
                if (result.watermarked) {
                    WatermarkStrip(
                        enabled = interactive,
                        onRewardGranted = {
                            // Re-encoding starts from the ORIGINAL, never from
                            // the marked output: the mark is burned into those
                            // pixels, and a second pass over an encode
                            // compounds the loss for nothing.
                            //
                            // The grant is NOT consumed here. A failed second
                            // pass would otherwise leave the user having
                            // watched an ad for nothing, which is both a bad
                            // trade and an AdMob policy problem - the reward
                            // has to be delivered. It is spent by the export
                            // that succeeds; see CompressionScreen.
                            CompressionService.start(
                                context = context,
                                uri = result.sourceUri,
                                preset = result.preset,
                                watermark = false,
                                replacing = result.outputUri,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.md))
                }

                PrimaryButton(
                    text = stringResource(R.string.result_share),
                    onClick = { shareVideo(context, result.outputUri) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactive,
                    leadingIcon = R.drawable.ic_share,
                )

                // QA v0.8.7 BUG-06 and the "ad density on the result screen"
                // finding, fixed together by moving both secondary actions ABOVE
                // the ad section.
                //
                // In v0.8.7 "Show in Gallery" and "Open Video" sat below a
                // full-height native creative. On a 720x1600 device that put
                // them entirely off screen, so most users never discovered them
                // - and since the recent list was inert too, leaving this screen
                // meant losing every in-app route back to the file.
                Spacer(Modifier.height(Space.xxs))

                SecondaryButton(
                    text = stringResource(R.string.result_show_in_gallery),
                    onClick = { showInGallery(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactive,
                )

                Spacer(Modifier.height(Space.xxs))

                SecondaryButton(
                    text = stringResource(R.string.result_open),
                    onClick = { openVideo(context, result.outputUri, result.watermarked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactive,
                )

                // "Compress another video" comes BEFORE the ad section.
                //
                // Review finding: the native creative used to sit between the
                // finished job and the way to start the next one, so continuing
                // the core task meant scrolling past an advertisement. The ad
                // keeps its labelled section with real content after it; it is
                // simply no longer standing in the user's path.
                TertiaryButton(
                    text = stringResource(R.string.result_another),
                    onClick = onCompressAnother,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactive,
                )


                if (adsVisible) {
                    // A divider, a label and 24dp of dead space above; a divider
                    // and 32dp below. The creative's boundary is unambiguous in
                    // both directions and no control is ever flush with it.
                    Spacer(Modifier.height(Space.xl))
                    HairLine()
                    Spacer(Modifier.height(Space.sm))
                    Eyebrow(
                        text = stringResource(R.string.ad_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.xs))
                    NativeAdCard(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.md))
                    HairLine()
                    Spacer(Modifier.height(Space.xxl))
                } else {
                    Spacer(Modifier.height(Space.xs))
                }

                Spacer(Modifier.height(Space.xs))

                Text(
                    text = stringResource(R.string.result_recent_hint),
                    style = VidsizeType.micro,
                    color = VidsizeColor.Faint,
                    textAlign = TextAlign.Center,
                )

                // Trailing space so the last control clears the navigation bar
                // and the page never ends flush against the system edge.
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Pieces                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun SuccessMark() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(VidsizeShape.chip)
            .background(VidsizeColor.MintSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = VidsizeColor.Mint,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun ResultFigures(
    sourceBytes: Long,
    outputBytes: Long,
    savedBytes: Long,
    percent: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = VidsizeShape.large,
        color = VidsizeColor.IndigoSoft,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    R.string.result_transform,
                    Fmt.bytes(sourceBytes),
                    Fmt.bytes(outputBytes),
                ),
                style = VidsizeType.figure,
                color = VidsizeColor.Ink,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.result_saved,
                    Fmt.bytes(savedBytes),
                    percent,
                ),
                style = VidsizeType.caption,
                color = VidsizeColor.Indigo,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Intents                                                                    */
/* ------------------------------------------------------------------------- */

/*
 * The three exits below are the deferred-interstitial paths, and they are the
 * reason this pattern earns anything at all.
 *
 * The intuitive wiring is the opposite of this: show an ad when the user leaves
 * for Home, and cancel it when they go to view their file, so nothing gets
 * between them and their video. That reasoning is right about the *placement*
 * and catastrophically wrong about the *outcome* - practically everyone who just
 * compressed a video wants to share, open or find it, so the cancelling branch
 * is the common one and the format earns close to nothing.
 *
 * deferInterstitialOnReturn() keeps the placement and recovers the revenue: no
 * ad now, on the way out; one ad when the user comes back with that task done.
 * It also suppresses the app-open ad in the same call, because an app-open ad on
 * re-entry would both break the "content the user asked for" rule and consume
 * the 60-second window the interstitial needs.
 */

private fun shareVideo(context: Context, uri: Uri) {
    context.deferInterstitialOnReturn()
    val intent = buildVideoShareIntent(context, uri)
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser)),
        )
    }
}

/**
 * Opens the device's gallery app at its video collection.
 *
 * ## QA finding: this button and "Open video" did the same thing
 *
 * Both built `ACTION_VIEW` on the file's own URI. That intent means "play this
 * video", so both buttons handed the file to a player and the user got the same
 * screen twice - the gallery was never opened, and nothing on the result screen
 * led to where the file actually lives.
 *
 * The fix is to point the intent at the COLLECTION rather than at the item:
 * `ACTION_VIEW` on [MediaStore.Video.Media.EXTERNAL_CONTENT_URI] is what a
 * gallery app registers for, so it opens the gallery's video list instead of a
 * player.
 *
 * Being honest about the limit: Android has no intent that means "reveal this
 * one file in its folder". Some OEM galleries land on the newest item, some
 * open the top of the list. The output is the most recent video on the device
 * at this moment, so in practice it is the first thing on screen either way -
 * but the button's own copy promises the gallery, not a scroll position, which
 * is the promise the platform can actually keep.
 */
private fun showInGallery(context: Context) {
    context.deferInterstitialOnReturn()
    val gallery = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
    }
    val launched = runCatching { context.startActivity(gallery) }.isSuccess
    if (launched) return

    // No single app claimed the collection. Ask the user, rather than silently
    // falling back to a player - which is the bug this function exists to fix.
    runCatching {
        context.startActivity(
            Intent.createChooser(gallery, context.getString(R.string.result_show_in_gallery)),
        )
    }
}

private fun openVideo(context: Context, uri: Uri, watermarked: Boolean) {
    // QA finding: coming back from the player is the single highest-intent
    // moment for the rewarded ad - the user has just seen the mark with their
    // own eyes. Arming the deferred interstitial here meant that return was met
    // by a full-screen ad, and the offer they came back for was behind it.
    //
    // A rewarded impression is worth more than an interstitial one, so trading
    // the interstitial away here is not a concession: it is the better half of
    // the trade. And nothing is lost permanently - the interstitial re-arms on
    // the next exit from this screen.
    if (!watermarked) context.deferInterstitialOnReturn()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun presetTitle(preset: CompressionPreset): String =
    stringResource(
        when (preset) {
            CompressionPreset.BALANCED -> R.string.preset_balanced_title
            CompressionPreset.SMALLER -> R.string.preset_smaller_title
            CompressionPreset.SMALLEST -> R.string.preset_smallest_title
        },
    )

/* ------------------------------------------------------------------------- */
/* Preview                                                                    */
/* ------------------------------------------------------------------------- */

@Preview(name = "Result figures", widthDp = 360, showBackground = true)
@Composable
private fun ResultFiguresPreview() {
    VidsizeTheme {
        Column(modifier = Modifier.padding(Space.lg)) {
            SuccessMark()
            Spacer(Modifier.height(Space.md))
            ResultFigures(
                sourceBytes = 1_095_216_660L,
                outputBytes = 641_728_512L,
                savedBytes = 453_488_148L,
                percent = 41,
            )
        }
    }
}
