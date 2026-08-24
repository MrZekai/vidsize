# Vidsize V1 Product Spec

## Core promise

**Compress your video. Keep the quality you need.**

Vidsize is a global Android video compressor. It reduces video file size locally on the device. It does not promise a specific final MB value.

## Primary flow

Home → Select Video → Inspect source → Choose profile → Compress → Success/Result → Share/Open/Compress Another

### Profiles

- **Balanced** — recommended; strong quality with meaningful reduction.
- **Smaller** — more size reduction; suitable for sharing and uploads.
- **Smallest** — maximum practical reduction; lower resolution/bitrate allowed.

The UI may show an **estimated** output size before compression, clearly marked with ≈. The actual result is measured and shown after encoding.

## Home

- Vidsize header + Settings
- Hero: Compress Video
- Select Video CTA
- AdMob banner/adaptive-banner region
- History entry
- Lifetime storage saved card

No Pro badge. No Pro tab. No exact-MB hero.

## Success / Result

Compression completion is a positive, motivating moment, not a plain technical "Done" state.

Required hierarchy:

1. Positive headline such as **Great! Your video is ready.**
2. Real before/after value, e.g. `1.0 GB → 684 MB`
3. Real storage saved, e.g. `You saved 340 MB`
4. Real percentage smaller
5. Actions: **Share Video**, **Open in Gallery**, **Compress Another**
6. A dedicated **300×250 AdMob MREC** region below the result content, visually separated from action buttons

Never claim a reduction that was not actually measured.

## Monetization — locked for V1

AdMob is required.

- **Home:** banner/adaptive banner
- **Result:** 300×250 Medium Rectangle (MREC)
- **App Open:** required after user warm-up; not on the first app start; if an ad is not ready, never block/delay entry to Home
- **Compression/progress screen:** no ads
- **File picker / active user task:** no full-screen ads
- **Exit/back press:** no ads

No Pro tier and no subscription in V1. No rewarded or interstitial placement is required for V1 unless explicitly approved later.

## App Open behavior

- Do not show on the first app start.
- Use only during a legitimate app loading/entry moment.
- If loading completes before the ad is ready, continue to Home and skip the ad.
- Apply a long cooldown; initial implementation target is at least 6 hours between App Open impressions.
- Suppress around active compression/result transitions and system flows initiated by Vidsize.
- Use Google test ad units until production setup.

## Non-goals for V1

- Exact target MB guarantee
- Video-to-MP3
- GIF
- Merge/split
- Cloud upload
- Accounts
- AI features
- Social/community
