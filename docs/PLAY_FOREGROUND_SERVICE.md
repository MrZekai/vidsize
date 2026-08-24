# Play Console — Foreground service declaration

Vidsize declares exactly one foreground-service type:

**Type:** `mediaProcessing`

**Use case:** A user explicitly taps **COMPRESS VIDEO** after selecting a video.
Vidsize transcodes that user-selected media locally. Compression can take several
minutes, so the foreground service lets the user switch apps without the export
being silently killed.

**User visibility and control:**
- A persistent progress notification is shown when Android notification
  permission allows it.
- The notification contains a **Cancel** action.
- The in-app processing panel also contains **Cancel**.
- The service stops on success, failure, cancellation, or Android's timeout.
- It is never started from boot, alarms, background receivers, or silently.

**Why `mediaProcessing`:** Android documents this type for time-consuming media
operations such as converting media to a different format. Video compression is
exactly that use case.

## Suggested Play Console explanation

"Vidsize starts a mediaProcessing foreground service only after the user selects
a video and taps Compress Video. The service performs on-device video transcoding
and keeps the user-started export running while the user temporarily switches
apps. A progress notification and cancel action are provided. The service stops
immediately when the export completes, fails, is cancelled, or times out."

## Demo-video script if Play Console requests evidence

1. Open Vidsize and select a video.
2. Tap **COMPRESS VIDEO**.
3. Show the in-app progress screen.
4. Go to the Android home screen / another app.
5. Show the Vidsize progress notification and its **Cancel** action.
6. Return to Vidsize and show the completed result or cancel the job.
