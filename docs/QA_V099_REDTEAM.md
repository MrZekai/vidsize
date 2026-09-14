# VidSize v0.9.9 — QA Red-Team Hardening

## Scope

This revision hardens trust, rewarded-ad UX and release QA without redesigning the product.

### P0/P1 changes

- Raw Kotlin/Java/Media3 exception text is never rendered in the user-facing failure dialog, including QA/debug variants. Exact details remain available in hidden diagnostics through `LastFailure`.
- An explicit **watermark-free** request can no longer silently fall back to a watermarked encode when rewarded inventory is unavailable. The user keeps control: retry the rewarded option or explicitly choose standard compression.
- The watermark disclosure card remains visible even when rewarded inventory cannot be requested; only its rewarded action is disabled.
- A successfully earned watermark-free entitlement is persisted for one unspent export for up to 24 hours. Process death/crash after the reward callback cannot force the user to watch a second ad.
- Reward is consumed only after a watermark-free export actually completes.
- Output names prefer `<original-name>_VidSize.mp4`; the timestamped `Vidsize_...mp4` name remains the fallback.
- Existing v0.9.8 fixes are preserved: no ad in the processing overlay, Gallery and Open Video are separate actions, already-efficient sources are detected before encoding, and Compress Another appears before the result native ad.

## Mandatory device QA

1. Standard compression: disclose the Vidsize mark before start; output is marked.
2. Rewarded success: watch once, encode once, result is watermark-free.
3. Rewarded no-fill/offline: no encode starts automatically; standard compression still works.
4. Rewarded early close: no clean entitlement is granted and the UI remains usable.
5. Earn reward, kill app/process before completion, reopen and retry: entitlement is still available and no second rewarded ad is required.
6. Clean export failure (storage/encoder): entitlement remains unspent.
7. Already-efficient 9.6 MB / ~51 s 1080p-style source: Balanced should be blocked or warned before a doomed encode; Smaller/Smallest remain available when viable.
8. Failure dialog: no class names, stack traces or `NoCompressionSavingsException` visible. Hidden diagnostics still records exact detail.
9. Gallery: opens a Gallery/Photos video collection; Open Video opens the file/player.
10. Background: switch apps / lock screen during compression and verify the foreground-service promise.
11. Cancel during encode and MediaStore copy: no half-written visible file remains.
12. Storage-full path: friendly message; original file untouched; temp rows cleaned.
13. Share: compressed output, correct MP4 MIME, readable filename and thumbnail.
14. Filename: `holiday.mp4` -> `holiday_VidSize.mp4`; duplicate names are safely handled by MediaStore.
15. Font scale 100/130/150% and small-height device: no clipped cards/buttons or inaccessible CTA.
16. Production/closed-test: no Google sample ads, no visible debug exception text, and core compression remains usable when ad inventory is unavailable.
