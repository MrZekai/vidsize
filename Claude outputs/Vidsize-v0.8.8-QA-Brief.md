# Vidsize 0.8.8 — Closed Test QA Brief

**To:** Anshara
**Build:** Vidsize 0.8.8 (versionCode 16), closed test track
**Package:** com.vidsize.compressor
**Previous report:** VidSize QA / Bug Report — v0.8.7 (build 15), vivo V2029 / Android 12, 10–11 Sep 2026

Thank you for the v0.8.7 report. All nine defects have been addressed. This brief
asks you to verify the fixes and, for the critical one, to re-run a specific
matrix from your own report.

Please use the **same device as last time (vivo V2029 / Android 12)** if you
still have it. That device is where BUG-05 reproduced, and it is the only place
the fix can be confirmed. If you have access to a second device on a different
chipset, a second pass there is valuable but secondary.

---

## 1. Priority one — BUG-05 resolution matrix

This is the release blocker and the only item that must be completed in full.

Re-run the exact matrix from section 2 of your report. For each source, select
the video, choose **Balanced**, and tap **COMPRESS VIDEO**.

| Source resolution | Format | v0.8.7 result | v0.8.8 result |
|---|---|---|---|
| 3840 × 2160 | 4K | FAIL (silent) | |
| 1920 × 1080 | 1080p landscape | PASS | |
| 1080 × 1920 | 1080p portrait | PASS | |
| 1280 × 720 | 720p | PASS | |
| 960 × 540 | qHD 16:9 | FAIL (silent) | |
| 862 × 480 | SD 16:9 | FAIL (silent) | |
| 856 × 480 | SD 16:9 | FAIL (silent) | |
| 854 × 480 | SD 16:9 (WhatsApp) | FAIL (silent) | |
| 640 × 480 | SD 4:3 | PASS | |
| 320 × 240 | QVGA | FAIL (silent) | |

**Pass criteria — one of these two must happen for every row:**

1. A compressed file is produced, **or**
2. A **modal error dialog** appears explaining why it could not finish.

**Any row where tapping COMPRESS VIDEO produces neither a file nor a dialog is a
FAIL and is the single most important thing to report.**

### If a dialog appears

The dialog carries a small grey technical line under the message. **Please
capture that line verbatim** — a screenshot is fine, but typed-out text is
better. It names the resolution that was refused and lists the encoder
configurations that were attempted, for example:

```
EncoderUnsupportedException: Encoder refused 1920x1080.
Tried: faithful 1920x1080, aligned 1920x1088, 1080p 1920x1088, 720p-default 1280x720
```

That line is what identifies the root cause. Without it the failure cannot be
diagnosed remotely.

### Also worth noting

If a file **is** produced but its resolution is **lower than expected** (for
example a 1080p source that comes out at 720p), please record it. The app now
retries with progressively safer encoder settings, so a lower-than-expected
output means the first attempt was rejected and a fallback succeeded. That is
still useful information even though it is not a failure.

---

## 2. Regression checks — the other eight defects

Each item lists the pass criterion. Please mark PASS / FAIL / NOT TESTED.

### BUG-01 — Google test ad units in a production build

**This build ships with advertising switched off entirely.** No banner, no
interstitial, no native ad, no app-open ad. The ads SDK is never initialised.

**Pass:** No "Test Ad" label appears anywhere in the app. No ad slot, empty or
filled, appears on any screen.

*Please do not file "ads are missing" as a defect — it is intentional for this
build. Real advertising returns in 0.9.0 with the developer's own ad units.*

### BUG-02 — AdMob native ad validator popup over SHARE VIDEO

**Pass:** Complete a compression and reach the result screen. No debug popup of
any kind renders over the screen. SHARE VIDEO is reachable immediately.

### BUG-03 — Output loses 4 × 2 px and the aspect ratio changes

**Pass:** Pull a compressed file off the device and probe it with ffmpeg. The
output resolution must match the source **exactly**, and the display aspect
ratio must be unchanged.

Expected, using your own examples:

| Source | v0.8.7 output | v0.8.8 expected output |
|---|---|---|
| 1920 × 1080 (DAR 16:9) | 1916 × 1078 (DAR 958:539) | **1920 × 1080 (DAR 16:9)** |
| 1280 × 720 (DAR 16:9) | 1276 × 718 (DAR 638:359) | **1280 × 720 (DAR 16:9)** |
| 640 × 480 (DAR 4:3) | 638 × 478 | **640 × 480 (DAR 4:3)** |

Please also **re-compress an already-compressed output**. In v0.8.7 the loss
compounded (1280×720 → 1276×718 → 1272×716). The resolution must now stay
stable across repeated passes.

Audio codec, sample rate and duration should remain correct, as they were in
v0.8.7.

### BUG-04 — Corrupt or unreadable video gives a silent dead end

**Pass:** Select a corrupt or truncated .mp4. The app must show a clear message
("This video can't be read") **above the fold, without scrolling**, and the
three compression levels must **not** be drawn at all. The bottom button reads
SELECT VIDEO.

### BUG-06 — Recent list entries are inert

**Pass, three parts:**

1. Tap a row in **Recent** on the home screen — the video opens in a player.
2. Long-press a row — the share sheet opens.
3. On the result screen, **Show in Gallery** and **Open Video** are visible
   without scrolling, above any advertising section.

### BUG-07 — Recent list and "Storage saved" count deleted files

**Pass:** Compress two or three videos. Delete the outputs from
Movies/Vidsize **using a file manager, outside the app**. Reopen Vidsize.

The deleted entries must be gone from Recent, and the "Storage saved" total and
video count must drop accordingly. Stale rows must not merely be greyed out —
they must be removed.

### BUG-08 — Native ad occupies the Cancel button's position

**Pass:** Start a compression and tap **Cancel** at the exact moment the job
completes and the dialog closes. Nothing unintended may open — no Play Store
sheet, no share sheet, no video player.

The result screen now ignores all touches for roughly the first half-second
after it appears, so a tap already in flight is absorbed.

### BUG-09 — Size estimates consistently under-predict

**Pass:** Note the "EST." figure shown for the selected level, run the
compression, and compare with the final size on the result screen.

The estimate should now be close to the actual size or slightly **above** it.
The v0.8.7 behaviour — the real output consistently larger than the estimate
(+10.8% and +16.7% in your two measurements) — should be gone. A small
overshoot in either direction is acceptable; a consistent under-prediction
across several runs is not.

---

## 3. New behaviour introduced in 0.8.8

These did not exist in v0.8.7. Please confirm each behaves sensibly.

**Failure is now modal.** Any failed compression produces a dialog that cannot
be scrolled past or missed. It offers "Pick another video" or "Close" depending
on the reason.

**A disabled COMPRESS VIDEO button now states why.** When no compression level
can produce a meaningful saving, or the device is out of space, the reason
appears in red directly above the button, and a matching notice appears under
the selected-video card. Previously the button was simply disabled with the
explanation off-screen.

**A greyed-out compression level now explains itself.** An unavailable level
shows a one-line reason under it ("This video is already small enough for this
level").

**Automatic level selection.** If the default level cannot run but another one
can, the app now switches to a level that works instead of leaving the user on
a dead selection.

**Save location is shown in-app.** The result screen states that the file is in
Movies/Vidsize. In v0.8.7 this appeared only in the completion notification.

---

## 4. Known and intentional — please do not file these

- **No advertising of any kind.** Deliberate for this build.
- **The Settings sheet has no "Ads" row and no "Ad privacy options" row.** Both
  are hidden because advertising is off.
- **Two yellow warnings in Play Console** (missing deobfuscation file, missing
  native debug symbols). Expected: code shrinking is deliberately disabled for
  this track, and the only native library comes from a Google dependency.

---

## 5. Out of scope for this pass

Not changed in 0.8.8, so not worth re-testing unless something looks obviously
broken:

- Privacy model and permissions
- Background processing and notifications
- Cancellation behaviour
- Clear-history flow
- Legal screens
- Orientation handling

---

## 6. Please report

For every failure:

- Device model and Android version
- Source video: resolution, duration, file size, container and codec
- Compression level selected
- What you expected and what happened
- Screenshot
- **For BUG-05 failures: the technical line from the error dialog, verbatim**
- Logcat filtered to the app's PID, if available

A short table of the resolution matrix with a result in each row would be the
single most useful thing this pass can produce.

---

## 7. Summary of what changed, for context

| ID | Severity | Fix |
|---|---|---|
| BUG-05 | Critical | Encoder frame size now derived from the device's own MediaCodec capabilities; both dimensions passed explicitly; bitrate clamped to the codec's range; four-rung retry ladder; every failure surfaced in a modal dialog |
| BUG-01 | High | Advertising disabled unless the developer's own AdMob identifiers are supplied; a release containing a Google sample identifier now fails the build |
| BUG-02 | High | No native ad is requested, so the SDK's validator popup cannot be drawn |
| BUG-03 | Medium | The 2px "force transcode" adjustment removed; output keeps the source geometry exactly |
| BUG-04 | Medium | Compression levels are not drawn for an unreadable file; explicit error shown above the fold |
| BUG-06 | Medium | Recent rows open and share; secondary result actions moved above the ad section |
| BUG-07 | Low | Entries whose file no longer exists are removed from storage, so totals converge |
| BUG-08 | Medium | Result screen ignores touches for ~450 ms after it appears |
| BUG-09 | Low | Estimate factor recalibrated from the two measurements in your report |

The full engineering write-up is in `docs/QA_V087_FIX_REPORT.md` in the
repository if you would like the reasoning behind any of these.
