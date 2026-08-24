# Vidsize Android

Vidsize is a lightweight Android video compressor focused on one job: **make a video file smaller while preserving a useful level of quality**.

## Product decision

Vidsize does **not** promise an exact output file size and does not force every video under a fixed MB threshold.

The V1 flow is:

1. Select a video.
2. Choose a compression profile: **Balanced / Smaller / Smallest**.
3. Preview an estimated output size.
4. Compress locally on the device.
5. Show a positive success/result screen with the real before/after size.
6. Share, open, or compress another video.

The final output size is determined by the source video, its duration, resolution, bitrate, codec support and the selected quality profile.

## Business model

- Free app
- AdMob is a primary revenue pillar
- Home: banner/adaptive banner
- Result: 300×250 Medium Rectangle (MREC) ad below the successful compression result
- App Open ad: required, with conservative first-session/cooldown rules
- No Pro tier in V1
- No subscription
- No account
- No backend
- No paid API
- No watermark

The 300×250 MREC is an AdMob banner size. Depending on inventory, its creative can be image or video; Vidsize does not assume every MREC impression will be a video creative.

## UI direction

Light Minimal, premium utility design. One dominant **Compress Video** action. No toolbox-style six-card home screen.

## Technical direction

- Kotlin
- Jetpack Compose
- Media3 Transformer
- MediaCodec hardware encoding
- Android Photo Picker
- MediaStore
- Room/DataStore in later milestones
- Google Mobile Ads SDK
- Target API 36

## Build

```bash
./gradlew :app:assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # planner unit tests
./gradlew :app:assembleRelease        # minified release build
```

Requires JDK 17+ and the Android SDK with API 36 installed. Android Studio users
can simply open the project root and run the `app` configuration.

## UI

The UI layer was rebuilt in `v0.3` around a real design system. See
[`docs/UI_SPEC.md`](docs/UI_SPEC.md) for the tokens, the screen architecture and
the ad placement rules.

Direction: **Light Minimal**. Light theme only in V1, on every device, including
phones set to dark mode.

## Legal & compliance

- Privacy policy and terms are served from `/docs` via GitHub Pages:
  [privacy](https://mrzekai.github.io/vidsize/privacy.html) ·
  [terms](https://mrzekai.github.io/vidsize/terms.html)
- Play Data Safety answers: [`docs/PLAY_DATA_SAFETY.md`](docs/PLAY_DATA_SAFETY.md)
- Pre-launch checklist: [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md)
- GDPR/UK consent is implemented with Google's User Messaging Platform in
  `ads/ConsentManager.kt`. **No ad is requested anywhere in the app until
  consent resolves.**

## Current status

`v0.6` — production hardening: Android 10+ scoped-storage floor, mediaProcessing-only
foreground service, completion notification, enabled conservative App Open flow,
GMA v25 large adaptive banner API, privacy/Data Safety corrections and CI audits.

`v0.5` — background compression via a foreground service with live progress and
cancel action, plus Play store assets and listing copy in `store/`.

`v0.4` — Vidsize rebrand, GDPR consent flow, storage pre-flight checks and 8
locales (EN, TR, ES, PT-BR, ID, DE, HI, AR).

`v0.3` — production-intent UI layer: design system (colour, spacing, type,
shapes), redesigned Home and Compression screens, real encoder progress, a
premium result sheet, local compression history, and an adaptive launcher icon.

The compression engine (`media/CompressionEngine.kt`, `CompressionPlanner`,
`VideoProbe`, MediaStore export) is unchanged apart from one additive, optional
`onProgress` callback used to drive the progress ring.

Still open before production: real-device testing across OEMs, AdMob production
app/ad-unit IDs, Play Console declarations, and enabling GitHub Pages for the
legal URLs. UMP consent and all eight shipped locales are already wired.
