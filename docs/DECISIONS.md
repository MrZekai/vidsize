# Locked Product Decisions

These decisions are intentionally short so future commits do not drift from the approved product.

- Product: global Android **Video Compressor**.
- Core job: make a selected video smaller while preserving the selected level of quality.
- V1 does **not** guarantee a fixed output MB value.
- Presets: Balanced / Smaller / Smallest.
- Approved visual direction: Light Minimal.
- No Pro tier and no subscription in V1.
- AdMob is mandatory: Home Banner + Result native + App Open + Interstitial + Rewarded.
- No FULL-SCREEN ad during active compression, and none in front of a finished
  result the user has not seen yet. The compression screen and the progress
  panel each carry one standard 320x50 banner; that placement is deliberate and
  policy-compliant, and it is the longest-dwell surface in the app.
  (Supersedes the earlier "No ad during active compression", which the code had
  never matched: ProcessingOverlay has carried a banner since v0.8.5. The rule
  was always about full-screen formats.)
- The rewarded reward is 10 minutes with no ads of any kind, banner included.
  Not 30: a half-day of silence removes any reason to watch a second one, and
  rewarded is the highest-eCPM unit in the portfolio.
- Exactly two pacing conditions live in code, because both are promises made to
  the user in words: the 60-second gap between full-screen ads, and the
  rewarded ad-free window. Every other frequency control lives in the AdMob
  panel so it can be retuned without a release. See docs/ADS.md.
- No interstitial after a cancelled or failed compression. The user waited
  minutes and has nothing; the impression is not worth the review.
- The history rows on Home stay clean - no deferred interstitial on reopening an
  already-compressed file. That is a maintenance flow, and the panel quota
  already bounds the format.
- Result screen must be motivating and must show only measured before/after/saved values.
- No backend, no paid API, no cloud processing, no watermark.
- Android stack: Kotlin + Jetpack Compose + Media3 Transformer + MediaCodec.
