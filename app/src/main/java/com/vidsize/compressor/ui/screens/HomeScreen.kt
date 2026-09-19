package com.vidsize.compressor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.vidsize.compressor.data.history.CompressionHistoryEntry
import com.vidsize.compressor.data.history.HistorySummary
import com.vidsize.compressor.ui.components.Eyebrow
import com.vidsize.compressor.ui.components.VidsizeCard
import com.vidsize.compressor.ui.components.HeroArt
import com.vidsize.compressor.ui.components.HomeBannerAd
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
 *  - A short, eager Compose column as the only scrolling region.
 *  - An anchored adaptive banner outside that scrolling region.
 *
 * Home never embeds a NativeAdView/MediaView in its scroll. The only ad is the
 * fixed banner below it, so ad loading cannot join a fling, resize the list or
 * intercept vertical gestures. The list has a strict three-row history cap;
 * composing it once avoids lazy-list measurement and item-provider overhead on
 * every swipe without risking an unbounded screen.
 */
@Composable
fun HomeScreen(
    summary: HistorySummary,
    onSelectVideo: () -> Unit,
    onBrowseFiles: () -> Unit = {},
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

            HeroPanel(onSelectVideo = onSelectVideo, onBrowseFiles = onBrowseFiles)

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

            Spacer(Modifier.height(Space.xl))
        }

        HomeBannerAd(modifier = Modifier.fillMaxWidth())
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
private fun HeroPanel(onSelectVideo: () -> Unit, onBrowseFiles: () -> Unit) {
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

        // The second line, deliberately quiet.
        //
        // Nearly every user wants the grid above and will never read this. It
        // exists for the one who downloaded a clip in a browser and cannot find
        // it in the gallery - the case that made this app swap its picker twice.
        // Shown as a text link rather than a second button so the screen still
        // has exactly one obvious thing to do.
        Spacer(Modifier.height(Space.xxs))
        TertiaryButton(
            text = stringResource(R.string.cta_browse_files),
            onClick = onBrowseFiles,
            modifier = Modifier.fillMaxWidth(),
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
        elevation = 0.dp,
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
 * The row is now the route back: a tap opens the video in the device's player,
 * while a labelled share button exposes the second action without a hidden
 * long-press gesture. Rows are only ever rendered for files that still exist -
 * [HistoryController.refresh] prunes the rest - so a tap can no longer be a
 * no-op.
 */
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
            .clickable(
                role = Role.Button,
                onClick = onOpen,
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
                // Was entry.displayName. Every generated name shares a prefix
                // long enough that the ellipsis cut before the part that made
                // it unique, so same-day rows were indistinguishable. See
                // Fmt.dateTime.
                text = Fmt.dateTime(entry.completedAtMillis)
                    .ifBlank { entry.displayName },
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

        // A separate labelled action is discoverable by sight and TalkBack;
        // sharing is never hidden behind a long-press gesture.
        Spacer(Modifier.width(Space.xxs))
        IconAction(
            icon = R.drawable.ic_share,
            contentDescription = stringResource(R.string.result_share),
            onClick = onShare,
        )
    }
}

@Composable
private fun StorageSavedPanel(summary: HistorySummary) {
    VidsizeCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 0.dp,
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
