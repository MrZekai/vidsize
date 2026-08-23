# UI Spec — Light Minimal (v0.3)

This is the implemented spec, not a wish list. Every token and rule below exists
in code under `app/src/main/java/com/fitsize/compressor/ui/`.

## 1. Design system

| Layer | File | Contents |
|---|---|---|
| Colour | `ui/theme/Color.kt` | `FitsizeColor` — every colour in the product, named. No hex literals in screens. |
| Spacing | `ui/theme/Dimens.kt` | `Space` (4→40dp scale, `gutter = 20dp`), `Radius` (14→28dp), `FitsizeShape`, `Sizes`. |
| Type | `ui/theme/Type.kt` | `FitsizeType` — 12 named styles on the platform sans family, each with ≥1.2× line height. |
| Theme | `ui/theme/FitsizeTheme.kt` | Material 3 `lightColorScheme` + shapes + typography. Light only, by decision. |

**Palette.** Canvas `#F6F6FB`, surfaces white, hairline borders `#EBECF3`.
Primary accent indigo `#5559EE` with violet `#8250F5` as its gradient partner.
Mint `#0EA97A` and cyan `#0C93B4` appear only as small supporting signals.

**Rules the code enforces:**
- The brand gradient appears in exactly one component: `PrimaryButton`.
- Coloured shadows appear in exactly one component: `PrimaryButton`.
- Every panel is `FitsizeCard` — white, 1dp border, soft colour-matched shadow.
- Button heights are minimums (`defaultMinSize`), never fixed, so a large system
  font scale grows the control instead of clipping the label.

## 2. Screen architecture

Both screens use the same three-part frame:

```
┌──────────────────────────┐
│ fixed bar  (statusBarsPadding)   ← never scrolls, never under the status bar
├──────────────────────────┤
│ scrolling content   weight(1f)   ← the only scrollable region
├──────────────────────────┤
│ fixed footer (navigationBarsPadding) ← ad strip (Home) / CTA (Compress)
└──────────────────────────┘
```

### Home — `ui/screens/HomeScreen.kt`
1. **App bar** — `Fitsize` wordmark, `Video Compressor` subtitle, settings icon.
2. **Hero card** — tinted panel: `VIDEO COMPRESSOR` pill, "Make your video
   smaller.", supporting line, Canvas illustration, full-width `SELECT VIDEO`.
3. **Trust row** — On-device / Fast / No watermark, three equal pills.
4. **Recent** — up to three compressed videos with before → after and a −N% chip;
   an empty state row before the first compression.
5. **Storage saved** — total saved, video count, and an 8-bar `SavingsChart`.
6. **Anchored adaptive banner** — pinned above the navigation bar.

### Compress — `ui/screens/CompressionScreen.kt`
1. **Bar** — back only (disabled while an export runs).
2. **Title block** — "Choose compression" + one supporting line.
3. **Selected video card** — poster frame with a duration badge, resolution,
   original size, resolution pill.
4. **Compression level** — Balanced / Smaller / Smallest, each with an estimate
   and a relative-size bar.
5. **Estimate note** — states plainly that these are estimates.
6. **Action bar** — `COMPRESS VIDEO`, always reachable.

### Processing — `ui/screens/ProcessingOverlay.kt`
Scrim + one panel: gradient progress ring driven by real encoder progress,
stage copy, Cancel. **No ad may ever appear on this surface.**

### Result — `ui/screens/ResultSheet.kt`
Bottom sheet built on `Dialog` (no experimental Material API):
handle → success mark → "Great! Your video is ready 🎉" → `1.02 GB → 612 MB`
→ `You saved 408 MB • 40% smaller` → `Balanced • 18.4s` → **SHARE VIDEO** →
Open Video → Compress another video → saved-location line → 300×250 MREC.

## 3. Responsiveness

- Verified layout targets: **360dp, 393dp, 412dp** width.
- Everything sits inside a single scroll region; no nested scrollables.
- Text wraps rather than truncates, except filenames and single-line figures,
  which use explicit `TextOverflow.Ellipsis`.
- Insets: `statusBarsPadding()` on both top bars, `navigationBarsPadding()` on
  the ad strip, the action bar and both sheets. Works with gesture and 3-button
  navigation.
- `MainActivity` pins both system bars to **dark icons** so the light UI stays
  legible on a phone in dark mode.

## 4. Ads

| Surface | Format | Rule |
|---|---|---|
| Home footer | Anchored adaptive banner | Height reserved before fill, hairline + label above, spacing below content. |
| Result sheet | 300×250 MREC | Below all three actions. Full 300×250dp reserved so buttons never shift. |
| App Open | — | Policy implemented in `ads/AppOpenAdPolicy.kt`, `ENABLED = false` in V1. |
| Processing | none | Never. |

Development uses Google demo units only. No interstitial, no rewarded, no Pro,
no subscription in V1.
