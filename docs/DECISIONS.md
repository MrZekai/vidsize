# Locked Product Decisions

These decisions are intentionally short so future commits do not drift from the approved product.

- Product: global Android **Video Compressor**.
- Core job: make a selected video smaller while preserving the selected level of quality.
- V1 does **not** guarantee a fixed output MB value.
- Presets: Balanced / Smaller / Smallest.
- Approved visual direction: Light Minimal.
- No Pro tier and no subscription in V1.
- AdMob is the revenue model: Home/Result native + Interstitial + Rewarded;
  banner and App Open stay configurable by placement/unit availability.
- No FULL-SCREEN ad during active compression, and none in front of a finished
  result the user has not seen yet. Vidsize's own player is also a clean path;
  it never arms an interstitial for the return.
- The rewarded reward is one export without the Vidsize mark. The choice is
  shown after Compress and before any encode: marked output now, or one short
  rewarded ad followed by one direct mark-free encode. Never encode twice.
- Exactly one pacing condition lives in code: 180 seconds between full-screen
  ads, measured on one monotonic clock. Every other frequency control lives in
  the AdMob panel so it can be retuned without a release. See docs/ADS.md.
- No interstitial after a cancelled or failed compression. The user waited
  minutes and has nothing; the impression is not worth the review.
- The history rows on Home stay clean - no deferred interstitial on reopening an
  already-compressed file. That is a maintenance flow, and the panel quota
  already bounds the format.
- Result screen must be motivating and must show only measured before/after/saved values.
- No backend, no paid API, no cloud processing. Free output may carry a small
  Vidsize mark; one rewarded ad buys one mark-free export.
- Android stack: Kotlin + Jetpack Compose + Media3 Transformer + MediaCodec.
