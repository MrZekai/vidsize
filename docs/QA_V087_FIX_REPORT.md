# v0.8.8 — fixes for the v0.8.7 QA / bug report

Vidsize 0.8.8 (versionCode 16). Every defect in the v0.8.7 QA pass
(vivo V2029 / Android 12, 9 confirmed defects, NO-GO verdict) is addressed
below, with the root cause and the specific change.

The QA pass named two release blockers: **BUG-05** and **BUG-01**. Both are
closed structurally rather than cosmetically — the conditions that produced
them can no longer be expressed in a packaged build.

---

## BUG-05 — COMPRESS VIDEO silently does nothing (CRITICAL)

**Reported:** 4K and a wide band of common SD/qHD sources (3840×2160, 960×540,
862×480, 856×480, 854×480, 320×240) produced no dialog, no toast, no error and
no output. 1920×1080, 1080×1920, 1280×720 and 640×480 worked.

There were **two independent causes**, and only fixing both makes the button
honest.

### Cause 1 — the failure was invisible, not absent

For a source with a viable plan the job *did* start and then failed. The error
was rendered as a card appended to the **bottom of a scrolling column**, after
the selected-video card, three preset rows and the estimate note. On a
720×1600 / 320dpi device that position is entirely below the fold. A failure
that arrives in a few hundred milliseconds also flashes the progress panel too
briefly to register. Net observable behaviour: nothing.

For a very low-bitrate source (320×240 at 49 KB) no preset was viable at all,
so the button was *disabled* — while still rendered full-width in the brand
gradient, looking exactly like the one thing to press — and the explanation was
again below the fold.

**Fix**

- A failed compression is now a **modal `AlertDialog`** (`FailureDialog` in
  `CompressionScreen.kt`). It cannot be off screen, scrolled past, or mistaken
  for the app ignoring the tap.
- The dialog carries the real diagnostic chain in **every build**, not only
  debug (`Throwable.diagnostic()` in `CompressionService.kt`). Media3 reports
  failures as `ExportException` whose own message is just an error code; the
  useful text is one or two levels down. This is the line that made QA read
  logcat to characterise the bug.
- Anything that blocks the primary action — unreadable file, no viable preset,
  no free space — is now rendered **directly under the selected-video card**,
  above the preset list, which is the only position guaranteed to be on screen.
- The action bar shows the reason **next to the disabled button**
  (`CompressionActionBar(hint = …)`).
- If the selected level is not viable but another one is, the selection now
  moves to a viable level instead of sitting on a dead default.

### Cause 2 — the encoder was handed geometry the device never agreed to

Nothing asked the device what it could encode, and the frame size was produced
by two pieces of untestable arithmetic: a `-2px` nudge in the planner (see
BUG-03) and Media3's floating-point derivation of the second edge inside
`Presentation.createForHeight`. A 854×480 source became **850×478** — a width
that is not even a multiple of 4, which no hardware AVC encoder is obliged to
accept. That matches the QA logcat signature of an encoder starting and then
aborting on its first buffer.

**Fix**

- New `EncoderSupport.kt` queries
  `MediaCodecInfo.CodecCapabilities.getVideoCapabilities()` for the AVC encoder
  that will actually do the work: width/height alignment, the supported
  width/height ranges, the supported bitrate range, and a real
  `isSizeSupported()` check.
- New `FrameAlignment.kt` holds the geometry arithmetic with **no Android
  imports**, so it is unit-tested on the JVM (`FrameAlignmentTest.kt`).
- `CompressionEngine` now passes **both edges** via
  `Presentation.createForWidthAndHeight(...)` and clamps the bitrate into the
  encoder's own range.
- `CompressionEngine.runExportWithFallbacks` retries a failed export down a
  ladder before reporting anything:

  | Rung | Geometry | Encoder settings |
  |------|----------|------------------|
  | 1 | Exact planner target, device-reported alignment | Vidsize bitrate |
  | 2 | Same frame, 16-aligned (macroblock) | Vidsize bitrate |
  | 3 | 1080p-capped, 16-aligned | Vidsize bitrate |
  | 4 | 720p-capped, 16-aligned | Media3's own |

  Cancellation and out-of-space are never retried. Only when every rung fails
  is `EncoderUnsupportedException` thrown, and it names the resolution and the
  configurations tried.

**Verified:** every source in the QA resolution matrix now produces exact, even
geometry — `FrameAlignmentTest.theQaResolutionMatrixIsReproducedExactly` and
`noOutputEdgeIsEverOdd`.

> **Still device-dependent.** The exact chipset-level reason the v0.8.7
> configuration was refused could not be reproduced without that handset. The
> ladder means a device that refuses one configuration gets three more, and the
> modal dialog means the user is told either way. Re-run the resolution matrix
> on the vivo V2029 before declaring BUG-05 empirically closed.

---

## BUG-01 — production build ships Google's sample ad units (HIGH)

**Root cause, confirmed in the repository:** the `closedTest` build type carried
`ENABLE_ADS = true` together with `USE_TEST_ADS = true` and the Google test
AdMob App ID. Every slot therefore requested
`ca-app-pub-3940256099942544/…` — Google's public sample publisher — so every
tester saw "Test Ad" and the developer earned nothing.

**Fix** — ads are off unless the developer's own identifiers are present, and
that is enforced at build time, not by convention:

- `adsConfigured` in `app/build.gradle.kts` is true only when all five
  identifiers are supplied **and** none contains the sample publisher.
- `closedTest` and the Play upload variant are hard-wired to
  `ENABLE_ADS = false`. `release` derives it from `adsConfigured`. `debug` is
  off unless a developer passes `-PVIDSIZE_ENABLE_TEST_ADS=true`.
- `verifyProductionAdConfig` now **fails the build** if a release would be
  packaged with a Google sample identifier.
- `verifyAdsOffWithoutRealIds` asserts that a placeholder App ID can never ship
  alongside ads being enabled.
- `AdSlots.enabled` is the single switch every ad surface reads before it emits
  a pixel. `AdIds.resolve` refuses to return a sample or all-zeros unit outside
  a debug build even if one is compiled in.

**Consequence:** with ads off, `MobileAds.initialize` is never called, no ad is
ever requested, and no ad slot reserves any space.

### To turn ads back on

Supply your own identifiers — no code change:

```bash
gradle :app:bundleRelease \
  -PVIDSIZE_ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY \
  -PVIDSIZE_HOME_BANNER_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111 \
  -PVIDSIZE_COMPRESSION_BANNER_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222 \
  -PVIDSIZE_NATIVE_RESULT_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/3333333333 \
  -PVIDSIZE_APP_OPEN_AD_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/4444444444
```

---

## BUG-02 — AdMob debug "native ad validator" popup covers SHARE VIDEO (HIGH)

That popup is drawn by the AdMob SDK for Google's **sample native** unit. No
native ad is requested any more (`NativeAdCard` returns before touching the
SDK, `NativeAdLoader` checks `AdSlots.requestable`), so the popup cannot be
drawn. The result screen's call to action is unobstructed.

---

## BUG-03 — output loses 4×2 px and the aspect ratio changes (MEDIUM)

**Root cause:** `CompressionPlanner.FORCE_TRANSCODE_DELTA_PX = 2` subtracted 2px
from the short edge whenever the preset was not already downscaling, to make
sure Media3 transcoded rather than transmuxed. 1920×1080 became 1916×1078, the
display aspect ratio moved from 16:9 to 958:539, the loss compounded on every
pass (1280×720 → 1276×718 → 1272×716), and the app's own "1080p" badge became
inaccurate.

It was never necessary. `TransformerUtil.shouldTranscodeVideo` transcodes if
**either** `encoderFactory.videoNeedsEncoding()` is true or the effect list is
non-empty, and Vidsize satisfies both on every job: it always supplies
non-default `VideoEncoderSettings` (bitrate + I-frame interval) and always
attaches a `Presentation` effect.

**Fix:** the nudge is deleted. The output keeps the source geometry exactly; the
only thing that may move it is the device encoder's own alignment, applied in
`EncoderSupport` where it belongs.

Covered by `bitrateOnlyReductionKeepsTheSourceResolutionExactly`,
`portraitAndOddRatioSourcesAlsoKeepTheirExactGeometry`,
`recompressionDoesNotCompoundAResolutionLoss` and
`displayAspectRatioIsPreserved`. A CI gate forbids the constant from returning.

---

## BUG-04 — corrupt video gives a silent dead end (MEDIUM)

An unreadable file navigated to "Choose compression" showing `Unknown`, `—` for
the size and `—` for all three estimates, with the three preset rows still
drawn and Balanced still ticked — which reads as "this will work".

**Fix:** the preset list is no longer rendered at all for an unreadable file.
An explicit error card ("This video can't be read") appears directly under the
selected-video card, and the button reads SELECT VIDEO.

---

## BUG-06 — Recent list entries are inert (MEDIUM)

**Fix**

- Recent rows are interactive: tap opens the video, long-press shares it, and a
  trailing chevron makes the affordance visible.
- Rows are only rendered for files that still exist (see BUG-07), so a tap can
  no longer be a no-op.
- On the result screen, **"Show in Gallery" and "Open Video" moved above the ad
  section**. In v0.8.7 they sat below a full-height native creative and were off
  screen on a 720×1600 device, so combined with the inert Recent rows a user who
  left the result screen had no in-app route back to their file.

---

## BUG-07 — Recent list and "Storage saved" count deleted files (LOW)

**Fix:** `HistoryController.refreshAndPrune` opens each stored row's MediaStore
URI — no bytes are read — and **removes** rows whose file is gone, rather than
hiding them, so the totals converge instead of drifting on every launch. It
runs on `Dispatchers.IO` (up to 50 rows) and publishes the stored summary first
so Home still draws immediately. It runs on first composition and every time
Home comes back into view.

---

## BUG-08 — native ad occupies the Cancel button's position (MEDIUM)

A tap aimed at Cancel at the moment compression finished landed on the result
screen's full-width native ad and opened a Play Store install sheet.

**Fix**

- With ads off, no creative is laid out there at all.
- Independently of ads: the result screen accepts **no taps for its first
  450 ms** (`ARRIVAL_GUARD_MS`). A touch already in flight is absorbed instead
  of being routed to an action the user never chose.

---

## BUG-09 — size estimates consistently under-predict (LOW)

`ENCODER_VARIANCE` was documented as "a starting point, not a measured
constant". The QA pass measured it, and the error was one-directional:

| Source | Estimated | Actual | Error |
|--------|-----------|--------|-------|
| 1920×1080 | ~15.7 MB | 17.4 MB | +10.8% |
| 1280×720 | ~4.8 MB | 5.6 MB | +16.7% |

`1.08 × 1.108 = 1.197` and `1.08 × 1.167 = 1.260`. **1.22** sits between them,
so the 1080p case is now marginally conservative and the 720p case marginally
optimistic, instead of both being optimistic. `SHORT_CLIP_VARIANCE` moves with
it (1.35 → 1.52). Still presented as "≈" with an "estimates only" note.

---

## UI / UX observations from section 4

| Observation | Change |
|---|---|
| Ad density on the result screen | Both secondary actions moved above the ad section (see BUG-06) |
| The app never says where the file was saved | `Movies/Vidsize` now shown with the figures, above every action |
| Ads inside the modal progress dialog | Banner moved to the top of the panel; nothing actionable beside Cancel |
| Empty ad slots reserve visible space | An absent ad now emits **zero** layout instead of a blank 50dp band |
| "Smallest" preset silently disabled | One-line reason under the greyed-out row |

---

## Verification performed

- All `CompressionPlannerTest` assertions re-verified, including the three that
  encoded the old pixel-loss behaviour as correct (now inverted) and the
  `lowBitrateSourcesAreUntouchedByTheQualityCeiling` numbers, which are
  unchanged.
- New `FrameAlignmentTest` covers the alignment primitives and the full QA
  resolution matrix.
- Every grep-based CI gate in both workflows was executed against this tree and
  passes, including the new v0.8.8 gates.
- All eight locales have complete string parity (103 keys; the three missing in
  translations are `translatable="false"`).

## Still to verify on hardware

1. **The resolution matrix in section 2 of the QA report, on the vivo V2029.**
   Rung 1 of the fallback ladder should now succeed for every row; if any row
   still fails, the dialog will name the reason and the attempted
   configurations.
2. Codecs beyond H.264/AAC in MP4, and containers such as MKV/MOV/AVI/WebM.
3. Interrupted-state durability (incoming call, process death, reboot
   mid-compression).
4. TalkBack, display scaling and RTL layouts.
