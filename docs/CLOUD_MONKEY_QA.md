# Cloud Monkey QA

`Vidsize Cloud Monkey QA` runs the `adsQa` APK on clean GitHub-hosted Android
emulators. It does not use a developer phone and it does not publish an APK.

## Test matrix

| Android | Events | Default seed | Purpose |
|---|---:|---:|---|
| API 29 | 10,000 | 20260917 | Minimum supported Android version |
| API 36 | 10,000 | 20260917 | Target Android version |

The same seed makes failures reproducible across reruns and comparable between
the two Android versions. A manual run can override both the event count and
seed from **Actions → Vidsize Cloud Monkey QA → Run workflow**.

## Pass gates

Every matrix job must:

1. install `app-adsQa.apk` as `com.vidsize.compressor.adsqa`;
2. inject the exact requested number of Monkey events;
3. finish without an app crash, ANR, native fatal signal, or device disconnect;
4. relaunch the app successfully after the stress run.

The workflow fails closed if any gate is incomplete. Security exceptions are
ignored because Android can reject an attempted privileged action without that
being an application defect; crashes and timeouts are deliberately not ignored.

## Evidence

Each Android version uploads a separate `cloud-monkey-api-*` artifact for 14
days. It includes:

- Monkey and logcat output;
- initial and final screenshots;
- package, activity, memory, graphics, disk, and device diagnostics;
- the generated media fixture and APK SHA-256;
- a Markdown pass/fail summary.

The APK is built once and reused by both jobs, so the matrix tests identical
bytes. The workflow is automatically triggered only when its own QA files change
on `feature/ads-v0.9.0`; normal use is an explicit manual run on that branch.

## LambdaTest / Appium boundary

This workflow supplies the deterministic GitHub Monkey stress layer. Appium
real-device coverage belongs in a separate credentialed workflow because it
requires LambdaTest/TestMu account secrets and a selected device catalogue.
Those secrets must be stored as GitHub Actions secrets and must never be placed
in source files or artifacts.
