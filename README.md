# Fitsize Android

Fitsize is a lightweight Android video compressor focused on one job: **make a video file smaller while preserving a useful level of quality**.

## Product decision

Fitsize does **not** promise an exact output file size and does not force every video under a fixed MB threshold.

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

The 300×250 MREC is an AdMob banner size. Depending on inventory, its creative can be image or video; Fitsize does not assume every MREC impression will be a video creative.

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

## Current status

`v0.2` — product direction corrected from target-MB compression to quality-profile compression. Monetization requirements are now locked as Home Banner + Result MREC + App Open. The code is an early source scaffold and still needs CI/build verification and real-device testing.
