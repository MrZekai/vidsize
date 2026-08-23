# Locked Product Decisions

These decisions are intentionally short so future commits do not drift from the approved product.

- Product: global Android **Video Compressor**.
- Core job: make a selected video smaller while preserving the selected level of quality.
- V1 does **not** guarantee a fixed output MB value.
- Presets: Balanced / Smaller / Smallest.
- Approved visual direction: Light Minimal.
- No Pro tier and no subscription in V1.
- AdMob is mandatory: Home Banner + Result 300×250 MREC + App Open.
- No ad during active compression.
- Result screen must be motivating and must show only measured before/after/saved values.
- No backend, no paid API, no cloud processing, no watermark.
- Android stack: Kotlin + Jetpack Compose + Media3 Transformer + MediaCodec.
