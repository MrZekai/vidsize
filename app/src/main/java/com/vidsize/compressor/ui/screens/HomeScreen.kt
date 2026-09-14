package com.vidsize.compressor.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vidsize.compressor.R
import com.vidsize.compressor.ads.AdSlots
import com.vidsize.compressor.data.history.CompressionHistoryEntry
import com.vidsize.compressor.data.history.HistorySummary
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.HeroArt
import com.vidsize.compressor.ui.components.HairLine
import com.vidsize.compressor.ui.components.NativeAdCard
import com.vidsize.compressor.ui.components.IconAction
import com.vidsize.compressor.ui.components.PrimaryButton
import com.vidsize.compressor.ui.components.SavingsChart
import com.vidsize.compressor.ui.components.SectionHeader
import com.vidsize.compressor.ui.components.TertiaryButton
import com.vidsize.compressor.ui.components.TintedPill
import com.vidsize.compressor.ui.format.Fmt
import com.vidsize.compressor.ui.theme.VidsizeColor
import com.vidsize.compressor.ui.theme.VidsizeShape
import com.vidsize.compressor.ui.theme.VidsizeTheme
import com.vidsize.compressor.ui.theme.VidsizeType
import com.vidsize.compressor.ui.theme.Space

/**
 * Home.
 *
 * Layout contract:
 *  - A fixed app bar that clears the status bar via [statusBarsPadding].
 *  - A single scrolling content column between the bar and the ad.
 *  - An anchored ad strip pinned above the navigation bar.
 *
 * The bar and the ad never scroll; only the content between them does. That is
 * what makes the screen feel like an app rather than a long web page, and it is
 * also what keeps the banner in a stable, non-accidental position.
 */
@Composable
fun HomeScreen(
    summary: HistorySummary,
    onSelectVideo: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenEntry: (CompressionHistoryEntry) -> Unit = {},
    onShareEntry: (CompressionHistoryEntry) -> Unit = {},
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VidsizeColor.Background),
    ) {
        HomeTopBar(onSettings = { showSettings = true })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.xs))

            HeroPanel(onSelectVideo = onSelectVideo)

            Spacer(Modifier.height(Space.sm))

            TrustRow()

            // The rewarded offer.
            //
            // Placed here, below the trust row, for two reasons. It is above the
            // fold on a 360dp phone, so the highest-eCPM unit in the app is
            // actually discoverable rather than buried in Settings. And the
            // trust row sits between it and the Select Video button, so a thumb
            // travelling to the primary action never crosses a control that
            // opens a full-screen ad.
            //
            // The composable renders nothing at all when there is no offer to
            // make - ads off, consent refused, or no creative loaded - so no
            // spacing is reserved for an absent card.

            Spacer(Modifier.height(Space.xxl))

            SectionHeader(
                title = stringResource(R.string.section_recent),
                action = {
                    if (!summary.isEmpty) {
                        TertiaryButton(
                            text = stringResource(R.string.clear_history),
                            onClick = { confirmClearHistory = true },
                            color = VidsizeColor.Muted,
                        )
                    }
                },
            )

            Spacer(Modifier.height(Space.sm))

            RecentPanel(
                entries = summary.entries,
                onOpenEntry = onOpenEntry,
                onShareEntry = onShareEntry,
            )

            Spacer(Modifier.height(Space.sm))

            StorageSavedPanel(summary = summary)

            // The in-content native ad, at the very end of the scroll.
            //
            // ## Why here and not higher
            //
            // Home's entire job is "tap Select Video", and a user in a hurry
            // never reaches this. Everything above it is the product; this sits
            // after the last piece of real content, so a user who arrives here
            // scrolled deliberately. It is also as far from the Select Video
            // button as the screen allows - the opposite end of the scroll -
            // which matters because a native creative carries its own tappable
            // call to action.
            //
            // ## Why it does not replace the anchored banner
            //
            // Native eCPM runs several times banner eCPM, which is an argument
            // for swapping them and a bad one here. The anchored banner is the
            // only ad that reaches a user who never scrolls, and it refreshes on
            // a 60-second cycle; this one is a single impression that requires
            // the user to travel to it. They earn from different behaviour, so
            // they are not substitutes. The whole scroll separates them, and
            // neither is adjacent to a Vidsize control.
            //
            // reserveSpace = false: nothing sits below this, so no-fill cannot
            // displace anything, and holding 340dp open on a page that may never
            // fill it would add a screen of dead scroll for nothing.
            if (AdSlots.requestable) {
                Spacer(Modifier.height(Space.xl))
                HairLine()
                Spacer(Modifier.height(Space.sm))
                Eyebrow(
                    text = stringResource(R.string.ad_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                NativeAdCard(
                    modifier = Modifier.fillMaxWidth(),
                    reserveSpace = false,
                )
            }

            Spacer(Modifier.height(Space.xl))
        }

        // Monetization stays visible without interrupting the user's workflow.
        // The scrollable content remains above this consent-gated banner.
        //
        // The banner stays anchored rather than scrolling with the content. In
        // the content it would end up beside the Select Video button, the Clear
        // history action or the history rows - all of them app controls, which is
        // a worse accidental-click neighbourhood than the system navigation area,
        // and it would scroll out of view entirely. What the anchored placement
        // did lack was separation, so it now carries a divider above it and a
        // 12dp dead buffer on both sides (see SystemEdgeBuffer).
        // The divider exists to separate the creative from the content above it.
        // With ads off there is no creative, so a dangling rule at the bottom of
        // the screen would be a decoration with no meaning.
        // v0.9.1: the anchored banner is gone from Home.
        //
        // Home now carries the rewarded offer and an in-content native at the
        // end of the scroll. A third ad surface on the app's front door - a
        // screen whose entire job is "tap Select Video" and where the median
        // visit is a few seconds - was density without a matching return. The
        // banner survives where it actually earns: the compression screen, and
        // above all the progress panel, which is the longest-dwell surface in
        // the app and refreshes on a 60-second cycle for the whole job.
    }

    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onClearHistory = onClearHistory,
        )
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

/* ------------------------------------------------------------------------- */
/* App bar                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun HomeTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VidsizeColor.Background)
            .statusBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = VidsizeType.wordmark,
                color = VidsizeColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = VidsizeType.caption,
                color = VidsizeColor.Muted,
            )
        }

        IconAction(
            icon = R.drawable.ic_settings,
            contentDescription = stringResource(R.string.home_settings),
            onClick = onSettings,
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Hero                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
private fun HeroPanel(onSelectVideo: () -> Unit) {
    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        shape = VidsizeShape.hero,
        color = VidsizeColor.SurfaceTint,
        border = VidsizeColor.IndigoBorder,
        elevation = 0.dp,
        contentPadding = Space.lg,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TintedPill(
                    text = stringResource(R.string.hero_eyebrow),
                    background = VidsizeColor.Surface,
                    border = VidsizeColor.IndigoBorder,
                    foreground = VidsizeColor.Indigo,
                    uppercase = true,
                )

                Spacer(Modifier.height(Space.sm))

                Text(
                    text = stringResource(R.string.hero_headline),
                    style = VidsizeType.hero,
                    color = VidsizeColor.Ink,
                )

                Spacer(Modifier.height(Space.xs))

                Text(
                    text = stringResource(R.string.hero_body),
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }

            Spacer(Modifier.width(Space.xs))

            HeroArt(size = 96.dp)
        }

        Spacer(Modifier.height(Space.lg))

        PrimaryButton(
            text = stringResource(R.string.cta_select_video),
            onClick = onSelectVideo,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = R.drawable.ic_video_file,
            trailingIcon = R.drawable.ic_chevron_right,
        )

        // The share-sheet entry point, said out loud.
        //
        // The manifest has declared an ACTION_SEND filter for video/* since
        // v0.8.x: a user can share a video into Vidsize straight from Gallery
        // and never open the app at all. Nothing in the UI has ever mentioned
        // it, so effectively nobody knows.
        //
        // For a tool people reach for occasionally, that path is the whole
        // retention story - it removes the step where the user has to remember
        // this app exists. One caption is the cheapest feature in the product.
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(R.string.hero_share_hint),
            style = VidsizeType.caption,
            color = VidsizeColor.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrustRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        TrustItem(
            text = stringResource(R.string.trust_on_device),
            modifier = Modifier.weight(1f),
            background = VidsizeColor.IndigoSoft,
            border = VidsizeColor.IndigoBorder,
            foreground = VidsizeColor.Indigo,
        )
        TrustItem(
            text = stringResource(R.string.trust_fast),
            modifier = Modifier.weight(1f),
            background = VidsizeColor.CyanSoft,
            border = VidsizeColor.CyanBorder,
            foreground = VidsizeColor.Cyan,
        )
        TrustItem(
            text = stringResource(R.string.trust_no_watermark),
            modifier = Modifier.weight(1f),
            background = VidsizeColor.MintSoft,
            border = VidsizeColor.MintBorder,
            foreground = VidsizeColor.Mint,
        )
    }
}

@Composable
private fun TrustItem(
    text: String,
    modifier: Modifier,
    background: Color,
    border: Color,
    foreground: Color,
) {
    Box(
        modifier = modifier
            .clip(VidsizeShape.chip)
            .background(background)
            .padding(horizontal = Space.xs, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = VidsizeType.caption,
            color = foreground,
            textAlign = TextAlign.Center,
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Activity                                                                   */
/* ------------------------------------------------------------------------- */

@Composable
private fun RecentPanel(
    entries: List<CompressionHistoryEntry>,
    onOpenEntry: (CompressionHistoryEntry) -> Unit,
    onShareEntry: (CompressionHistoryEntry) -> Unit,
) {
    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Space.md,
    ) {
        if (entries.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(VidsizeShape.small)
                        .background(VidsizeColor.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = null,
                        tint = VidsizeColor.Faint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recent_empty_title),
                        style = VidsizeType.cardTitle,
                        color = VidsizeColor.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.recent_empty_body),
                        style = VidsizeType.supporting,
                        color = VidsizeColor.Muted,
                    )
                }
            }
        } else {
            entries.take(MAX_RECENT_ROWS).forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(Space.sm))
                RecentRow(
                    entry = entry,
                    onOpen = { onOpenEntry(entry) },
                    onShare = { onShareEntry(entry) },
                )
            }
        }
    }
}

/**
 * One row of the Recent list.
 *
 * ## QA v0.8.7 BUG-06
 *
 * These rows looked exactly like list items - icon, title, supporting line,
 * trailing pill - and were completely inert. The accessibility tree confirmed no
 * clickable ancestor anywhere in the row, while the empty state promised "your
 * compressed videos will show up here". Combined with the result screen burying
 * "Show in Gallery" and "Open Video" beneath a full-height native ad, a user who
 * left the result screen had no route back to their file at all.
 *
 * The row is now the route back: a tap opens the video in the device's player, a
 * long press shares it, and the trailing chevron makes the affordance visible
 * rather than implied. Rows are only ever rendered for files that still exist -
 * [HistoryController.refresh] prunes the rest - so a tap can no longer be a
 * no-op.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    entry: CompressionHistoryEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VidsizeShape.small)
            .combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                onLongClick = onShare,
            )
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(VidsizeShape.small)
                .background(VidsizeColor.IndigoSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_video_file),
                contentDescription = null,
                tint = VidsizeColor.Indigo,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(Space.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = VidsizeType.cardTitle,
                color = VidsizeColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.recent_row_summary,
                    Fmt.bytes(entry.sourceBytes),
                    Fmt.bytes(entry.outputBytes),
                ),
                style = VidsizeType.supporting,
                color = VidsizeColor.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(Space.xs))

        TintedPill(
            text = "−${Fmt.percentSmaller(entry.sourceBytes, entry.outputBytes)}%",
            background = VidsizeColor.MintSoft,
            border = VidsizeColor.MintBorder,
            foreground = VidsizeColor.Mint,
        )

        // Makes the row's interactivity visible instead of leaving the user to
        // guess, which is what BUG-06 was really about.
        Spacer(Modifier.width(Space.xxs))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = VidsizeColor.Faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun StorageSavedPanel(summary: HistorySummary) {
    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Space.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(text = stringResource(R.string.storage_saved_label))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Fmt.bytes(summary.totalSavedBytes),
                    style = VidsizeType.hero,
                    color = VidsizeColor.Indigo,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (summary.isEmpty) {
                        stringResource(R.string.storage_saved_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.video_count,
                            summary.videoCount,
                            summary.videoCount,
                        )
                    },
                    style = VidsizeType.supporting,
                    color = VidsizeColor.Muted,
                )
            }

            Spacer(Modifier.width(Space.md))

            SavingsChart(
                values = summary.entries.map { it.savedBytes }.reversed(),
                modifier = Modifier.weight(0.9f),
            )
        }
    }
}

private const val MAX_RECENT_ROWS = 3

/* ------------------------------------------------------------------------- */
/* Previews                                                                   */
/* ------------------------------------------------------------------------- */

@Preview(name = "Home · empty · 360dp", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    VidsizeTheme {
        HomeScreen(
            summary = HistorySummary.Empty,
            onSelectVideo = {},
            onClearHistory = {},
        )
    }
}

@Preview(name = "Home · with history · 412dp", widthDp = 412, heightDp = 880, showBackground = true)
@Composable
private fun HomeWithHistoryPreview() {
    val sample = listOf(
        CompressionHistoryEntry(1, "", "Vidsize_1042.mp4", 1_096_000_000L, 612_000_000L, "Balanced", 0L),
        CompressionHistoryEntry(2, "", "Vidsize_0931.mp4", 240_000_000L, 96_000_000L, "Smaller", 0L),
        CompressionHistoryEntry(3, "", "Vidsize_0820.mp4", 88_000_000L, 24_000_000L, "Smallest", 0L),
    )
    VidsizeTheme {
        HomeScreen(
            summary = HistorySummary(
                entries = sample,
                totalSavedBytes = sample.sumOf { it.savedBytes },
                videoCount = sample.size,
            ),
            onSelectVideo = {},
            onClearHistory = {},
        )
    }
}
