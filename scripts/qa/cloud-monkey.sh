#!/usr/bin/env bash
set -Eeuo pipefail

APK_PATH="${APK_PATH:-app/build/outputs/apk/adsQa/app-adsQa.apk}"
PACKAGE_NAME="${PACKAGE_NAME:-com.vidsize.compressor.adsqa}"
EVENTS="${EVENTS:-10000}"
SEED="${SEED:-20260917}"
API_LEVEL="${API_LEVEL:-unknown}"
RESULT_DIR="${RESULT_DIR:-artifacts/monkey-api-${API_LEVEL}}"

STARTED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
MONKEY_EXIT="not-run"
EVENTS_OBSERVED="0"
RESULT="FAIL"
REASON="The test script stopped before all gates completed."
APK_SHA256="unavailable"

# Interference by software that is not Vidsize.
#
# The emulator image ships its own Google Play Services, and on API 36 that
# process ANR'd 633 events into a 10,000-event run. Monkey stops when the system
# puts an ANR dialog on screen, no matter whose ANR it is - so the run ended
# early, the event-count gate failed, and a green app was reported red. The
# gates below now separate the two questions: did VIDSIZE misbehave (always a
# failure), and did the environment stop the run (reported, never a failure).
FOREIGN_INTERFERENCE=""
WARNINGS=

mkdir -p "$RESULT_DIR"

log() {
  printf '[cloud-monkey] %s\n' "$*"
}

collect_diagnostics() {
  set +e
  if ! adb get-state >/dev/null 2>&1; then
    printf 'ADB device unavailable during diagnostics collection.\n' \
      >"$RESULT_DIR/adb-unavailable.txt"
    set -e
    return
  fi

  adb logcat -d -v threadtime >"$RESULT_DIR/logcat.txt" 2>&1
  adb shell dumpsys meminfo "$PACKAGE_NAME" >"$RESULT_DIR/meminfo.txt" 2>&1
  adb shell dumpsys gfxinfo "$PACKAGE_NAME" >"$RESULT_DIR/gfxinfo.txt" 2>&1
  adb shell dumpsys package "$PACKAGE_NAME" >"$RESULT_DIR/package.txt" 2>&1
  adb shell dumpsys activity processes >"$RESULT_DIR/activity-processes.txt" 2>&1
  adb shell getprop >"$RESULT_DIR/device-properties.txt" 2>&1
  adb shell df -h >"$RESULT_DIR/disk-usage.txt" 2>&1
  adb exec-out screencap -p >"$RESULT_DIR/final-screen.png" 2>"$RESULT_DIR/screenshot-error.txt"
  adb shell uiautomator dump /sdcard/window.xml >"$RESULT_DIR/uiautomator.txt" 2>&1
  adb pull /sdcard/window.xml "$RESULT_DIR/window.xml" >>"$RESULT_DIR/uiautomator.txt" 2>&1
  set -e
}

write_summary() {
  local finished_at
  finished_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

  cat >"$RESULT_DIR/summary.md" <<EOF
# Vidsize Cloud Monkey — ${RESULT}

| Field | Value |
|---|---|
| Android API | ${API_LEVEL} |
| Package | ${PACKAGE_NAME} |
| Requested events | ${EVENTS} |
| Observed events | ${EVENTS_OBSERVED} |
| Seed | ${SEED} |
| Monkey exit | ${MONKEY_EXIT} |
| APK SHA-256 | ${APK_SHA256} |
| Started (UTC) | ${STARTED_AT} |
| Finished (UTC) | ${finished_at} |

**Gate result:** ${REASON}
${WARNINGS:+
## Warnings (did not fail the build)
${WARNINGS}
}
EOF

  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat "$RESULT_DIR/summary.md" >>"$GITHUB_STEP_SUMMARY"
  fi
}

finish() {
  local exit_code=$?
  trap - EXIT
  collect_diagnostics

  if (( exit_code == 0 )); then
    if [[ -n "$WARNINGS" ]]; then
      RESULT="PASS (with warnings)"
      REASON="Every Vidsize crash, ANR, relaunch and device-health gate passed. The run itself did not complete, and that was attributed to interference from another package on the emulator."
    else
      RESULT="PASS"
      REASON="All crash, ANR, event-count, device-health, and relaunch gates passed."
    fi
  fi

  write_summary
  log "$RESULT: $REASON"
  exit "$exit_code"
}
trap finish EXIT

fail_gate() {
  REASON="$1"
  printf '::error::%s\n' "$REASON"
  return 1
}

# Recorded in the summary and surfaced as a GitHub warning, but does not fail
# the build. Reserved for facts about the emulator, never about the app.
warn_gate() {
  WARNINGS="${WARNINGS}
- $1"
  printf '::warning::%s\n' "$1"
}

# True when the run was disturbed by a process that is not Vidsize.
#
# Deliberately narrow: it looks for a crash or ANR belonging to some OTHER
# package. Anything belonging to $PACKAGE_NAME is excluded here and handled by
# the hard gates, so this can never be used to excuse an app defect.
detect_foreign_interference() {
  local monkey_log="$RESULT_DIR/monkey.txt"
  local logcat="$RESULT_DIR/logcat.txt"
  local found=""

  if [[ -f "$monkey_log" ]]; then
    found+="$(grep -E '^// (CRASH|NOT RESPONDING):' "$monkey_log" 2>/dev/null \
      | grep -Fv "$PACKAGE_NAME" || true)"
  fi
  if [[ -f "$logcat" ]]; then
    found+="$(grep -E 'ANR in [a-zA-Z0-9_.]+' "$logcat" 2>/dev/null \
      | grep -Fv "$PACKAGE_NAME" || true)"
  fi

  FOREIGN_INTERFERENCE="$(printf '%s\n' "$found" | grep -v '^$' | head -20 || true)"
  [[ -n "$FOREIGN_INTERFERENCE" ]]
}

require_unsigned_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    fail_gate "$name must be an unsigned integer; received '$value'."
  fi
}

require_unsigned_integer EVENTS "$EVENTS"
require_unsigned_integer SEED "$SEED"
if (( EVENTS < 1 )); then
  fail_gate "EVENTS must be greater than zero."
fi
if [[ ! -s "$APK_PATH" ]]; then
  fail_gate "APK was not found or is empty: $APK_PATH"
fi
if ! command -v adb >/dev/null 2>&1; then
  fail_gate "adb is not available on PATH."
fi

APK_SHA256="$(sha256sum "$APK_PATH" | awk '{print $1}')"

log "Waiting for Android API $API_LEVEL to finish booting"
adb wait-for-device
boot_complete=""
for _ in $(seq 1 120); do
  boot_complete="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "$boot_complete" == "1" ]]; then
    break
  fi
  sleep 2
done
if [[ "$boot_complete" != "1" ]]; then
  fail_gate "The emulator did not complete boot within 240 seconds."
fi

adb shell input keyevent 82 >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

log "Installing $APK_PATH"
adb install -r -t "$APK_PATH" | tee "$RESULT_DIR/install.txt"
if ! adb shell pm path "$PACKAGE_NAME" | grep -q '^package:'; then
  fail_gate "Expected package was not installed: $PACKAGE_NAME"
fi

# A deterministic local clip makes the media picker useful if Monkey reaches it.
# Media generation is a test-data enhancement, not a gate on the app itself.
if command -v ffmpeg >/dev/null 2>&1; then
  if ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i 'testsrc2=size=640x360:rate=24' \
      -f lavfi -i 'sine=frequency=880:sample_rate=44100' \
      -t 6 -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac \
      "$RESULT_DIR/vidsize-monkey-input.mp4"; then
    adb shell mkdir -p /sdcard/Movies
    adb push "$RESULT_DIR/vidsize-monkey-input.mp4" \
      /sdcard/Movies/vidsize-monkey-input.mp4 \
      >"$RESULT_DIR/media-push.txt" 2>&1
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
      -d file:///sdcard/Movies/vidsize-monkey-input.mp4 \
      >>"$RESULT_DIR/media-push.txt" 2>&1 || true
  else
    printf 'ffmpeg could not create the optional media fixture.\n' \
      >"$RESULT_DIR/media-fixture-warning.txt"
  fi
else
  printf 'ffmpeg is unavailable; optional media fixture was skipped.\n' \
    >"$RESULT_DIR/media-fixture-warning.txt"
fi

device_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
device_model="$(adb shell getprop ro.product.model | tr -d '\r')"
{
  printf 'api=%s\n' "$device_api"
  printf 'model=%s\n' "$device_model"
  printf 'package=%s\n' "$PACKAGE_NAME"
  printf 'apk_sha256=%s\n' "$APK_SHA256"
} >"$RESULT_DIR/test-environment.txt"
if [[ "$API_LEVEL" != "unknown" && "$device_api" != "$API_LEVEL" ]]; then
  fail_gate "Requested API $API_LEVEL but emulator reports API $device_api."
fi

if (( device_api >= 33 )); then
  adb shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS \
    >/dev/null 2>&1 || true
fi

adb shell am force-stop "$PACKAGE_NAME"
adb logcat -c
adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 \
  >"$RESULT_DIR/initial-launch.txt" 2>&1
sleep 3
adb exec-out screencap -p >"$RESULT_DIR/initial-screen.png"

log "Running $EVENTS events with seed $SEED"
set +e
adb shell monkey \
  -p "$PACKAGE_NAME" \
  --ignore-security-exceptions \
  --monitor-native-crashes \
  --kill-process-after-error \
  --pct-touch 40 \
  --pct-motion 30 \
  --pct-nav 15 \
  --pct-majornav 15 \
  --pct-syskeys 0 \
  --pct-appswitch 0 \
  --pct-anyevent 0 \
  --throttle 20 \
  -s "$SEED" \
  -v -v "$EVENTS" \
  2>&1 | tee "$RESULT_DIR/monkey.txt"
MONKEY_EXIT="${PIPESTATUS[0]}"
set -e

EVENTS_OBSERVED="$(awk '/Events injected:/ {value=$3} END {print value+0}' \
  "$RESULT_DIR/monkey.txt")"
collect_diagnostics

# ---------------------------------------------------------------------------
# Gates about VIDSIZE. These always fail the build.
# ---------------------------------------------------------------------------
# Checked FIRST, and before anything is forgiven as environmental. An app crash
# is an app crash whatever else the emulator was doing at the time.

if grep -Fq "// CRASH: $PACKAGE_NAME" "$RESULT_DIR/monkey.txt"; then
  fail_gate "Monkey reported an application crash."
fi
if grep -Fq "// NOT RESPONDING: $PACKAGE_NAME" "$RESULT_DIR/monkey.txt"; then
  fail_gate "Monkey reported an application-not-responding event."
fi
if grep -Fq "Process: $PACKAGE_NAME" "$RESULT_DIR/logcat.txt"; then
  fail_gate "Logcat contains an Android Runtime crash for the application."
fi
if grep -Eiq "ANR in ${PACKAGE_NAME}|am_anr.*${PACKAGE_NAME}|am_crash.*${PACKAGE_NAME}|Fatal signal.*>>> ${PACKAGE_NAME} <<<" \
    "$RESULT_DIR/logcat.txt"; then
  fail_gate "Logcat contains a crash, ANR, or native fatal signal for the application."
fi
if [[ "$(adb get-state 2>/dev/null)" != "device" ]]; then
  fail_gate "The emulator disconnected during Monkey execution."
fi

# ---------------------------------------------------------------------------
# Gates about the RUN. Environmental interference downgrades these to warnings.
# ---------------------------------------------------------------------------
# Vidsize is clean at this point - every app-specific gate above has passed. What
# remains is whether the run itself completed, and that can be prevented by
# software Vidsize does not control. The emulator's own Google Play Services
# ANR'ing puts a system dialog on screen; Monkey stops, exits non-zero and
# reports a short event count. Failing on that says "the app is broken" when the
# evidence says the opposite.
#
# When no foreign interference is found, these stay hard failures: a short run
# with a healthy environment is a real signal and must not be swallowed.

detect_foreign_interference || true

if [[ -n "$FOREIGN_INTERFERENCE" ]]; then
  printf '%s\n' "$FOREIGN_INTERFERENCE" >"$RESULT_DIR/foreign-interference.txt"
fi

RUN_INCOMPLETE=""
if (( MONKEY_EXIT != 0 )); then
  RUN_INCOMPLETE="Monkey exited with status $MONKEY_EXIT."
elif [[ "$EVENTS_OBSERVED" != "$EVENTS" ]]; then
  RUN_INCOMPLETE="Monkey injected $EVENTS_OBSERVED of $EVENTS requested events."
elif ! grep -q 'Monkey finished' "$RESULT_DIR/monkey.txt"; then
  RUN_INCOMPLETE="Monkey did not print its completion marker."
fi

if [[ -n "$RUN_INCOMPLETE" ]]; then
  if [[ -n "$FOREIGN_INTERFERENCE" ]]; then
    warn_gate "$RUN_INCOMPLETE Attributed to interference from another package on the emulator, not to Vidsize - see foreign-interference.txt. Every Vidsize crash and ANR gate passed."
  else
    fail_gate "$RUN_INCOMPLETE"
  fi
fi

# A run cut short by interference still has to have exercised the app properly,
# otherwise "no Vidsize crash" means only "Monkey barely touched Vidsize".
MIN_MEANINGFUL_EVENTS="${MIN_MEANINGFUL_EVENTS:-500}"
if (( EVENTS_OBSERVED < MIN_MEANINGFUL_EVENTS )); then
  fail_gate "Only $EVENTS_OBSERVED events reached the app; below $MIN_MEANINGFUL_EVENTS this run proves nothing either way."
fi

log "Verifying that the app can relaunch after Monkey"
adb shell am force-stop "$PACKAGE_NAME"
adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 \
  >"$RESULT_DIR/relaunch.txt" 2>&1
sleep 3
if [[ -z "$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')" ]]; then
  fail_gate "The app could not be relaunched after Monkey execution."
fi

RESULT="PASS"
REASON="All crash, ANR, event-count, device-health, and relaunch gates passed."
log "$REASON"
