#!/usr/bin/env bash
# Stream useful Y1 logs in a dedicated terminal while retaining full logcat.
# Stop with Ctrl-C to create and copy a compact handoff report.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CAPTURE_DIR="$REPO_ROOT/debug-captures/y1-live-logs"
PACKAGE="com.solar.launcher"
CLEAR_LOGS=1
SHOW_ALL=0

usage() {
  cat <<'EOF'
Usage: scripts/y1-live-logs.sh [--all] [--no-clear]

  --all       Show every logcat line (the full stream is always saved).
  --no-clear  Keep existing device logcat instead of starting a clean capture.

Reproduce the problem on the Y1, then press Ctrl-C. A compact handoff report
is copied to the macOS clipboard and all capture files remain in debug-captures/.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) SHOW_ALL=1 ;;
    --no-clear) CLEAR_LOGS=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

command -v adb >/dev/null 2>&1 || {
  echo "adb is not installed or not on PATH." >&2
  exit 1
}

mapfile_devices() {
  adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

DEVICE_COUNT="$(mapfile_devices | wc -l | tr -d ' ')"
if [[ "$DEVICE_COUNT" == "0" ]]; then
  echo "Waiting for a Y1 over ADB..."
  adb wait-for-device
  DEVICE_COUNT="$(mapfile_devices | wc -l | tr -d ' ')"
fi
if [[ "$DEVICE_COUNT" != "1" ]]; then
  echo "Expected exactly one authorized ADB device; found $DEVICE_COUNT:" >&2
  adb devices -l >&2
  exit 1
fi

SERIAL="$(mapfile_devices)"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
ANDROID="$(adb -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
BUILD="$(adb -s "$SERIAL" shell getprop ro.build.display.id 2>/dev/null | tr -d '\r')"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$CAPTURE_DIR"
RAW_LOG="$CAPTURE_DIR/y1-$STAMP-full.logcat"
VISIBLE_LOG="$CAPTURE_DIR/y1-$STAMP-visible.logcat"
HANDOFF="$CAPTURE_DIR/y1-$STAMP-handoff.txt"

LOGCAT_PID=""
FINISHED=0

finish() {
  local status=$?
  if [[ "$FINISHED" == "1" ]]; then
    exit "$status"
  fi
  FINISHED=1
  trap - EXIT INT TERM

  if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi

  {
    echo "Y1 LOG HANDOFF"
    echo "Captured: $(date '+%Y-%m-%d %H:%M:%S %Z')"
    echo "Device: $MODEL | Android $ANDROID | build $BUILD | serial $SERIAL"
    echo "Package: $PACKAGE"
    echo "Full capture: $RAW_LOG"
    echo
    echo "--- Live filtered log ---"
    tail -n 1200 "$VISIBLE_LOG" 2>/dev/null || true
    echo
    echo "--- Recent full-log errors and crashes ---"
    grep -E ' [EWF] |FATAL EXCEPTION|ANR in |tombstone|backtrace|signal [0-9]+' "$RAW_LOG" 2>/dev/null \
      | grep -v ' ADB_SERVICES:' \
      | tail -n 300 || true
  } > "$HANDOFF"

  echo
  echo "Capture stopped."
  echo "Handoff: $HANDOFF"
  echo "Full log: $RAW_LOG"
  if command -v pbcopy >/dev/null 2>&1; then
    pbcopy < "$HANDOFF"
    echo "The handoff report is on your clipboard — paste it into this task."
  fi
  exit "$status"
}
trap finish EXIT INT TERM

if [[ "$CLEAR_LOGS" == "1" ]]; then
  adb -s "$SERIAL" logcat -c
fi

echo "Y1 LIVE LOGS"
echo "Device: $MODEL | Android $ANDROID | build $BUILD"
echo "Showing Solar activity, crashes, warnings, media, Wi-Fi, USB, and storage."
echo "The complete unfiltered stream is also being saved."
echo
echo "Reproduce the problem now. Press Ctrl-C when finished."
echo "--------------------------------------------------------------------------"

adb -s "$SERIAL" logcat -v threadtime > "$RAW_LOG" 2>&1 &
LOGCAT_PID=$!

if [[ "$SHOW_ALL" == "1" ]]; then
  tail -n 0 -F "$RAW_LOG" | tee "$VISIBLE_LOG"
else
  tail -n 0 -F "$RAW_LOG" \
    | python3 -u -c '
import re
import sys

interesting = re.compile(
    r"Solar|DeezerDownload|com\.solar\.launcher|AndroidRuntime|FATAL EXCEPTION|ANR in |"
    r"ActivityManager|MediaPlayer|AudioFlinger|AudioTrack|NuPlayer|Stagefright|"
    r"Wifi|wifi|wpa_|Connectivity|Usb|USB|vold|MountService|PackageManager|"
    r"SQLite|StrictMode|OutOfMemory|SIG[A-Z]+|tombstone|backtrace"
)
priority = re.compile(r"\s[WEF]\s")
adb_noise = re.compile(r"\sADB_SERVICES:")
runtime_tool_noise = re.compile(
    r"\sAndroidRuntime: (?:>>>>>> AndroidRuntime START|JNI trace buf|CheckJNI is |"
    r"language=|Calling main entry com\\.android\\.commands\\.pm\\.Pm|Shutting down VM)"
)
vendor_media_noise = re.compile(
    r"\s(?:linker  |DataSource|FlvExtractor|MPEG4Extractor|DrmMtkUtil/DrmUtil|"
    r"DrmMtkPlugIn|StagefrightMetadataRetriever|MediaPlayerFactory):"
)

try:
    for line in sys.stdin:
        if (adb_noise.search(line) or runtime_tool_noise.search(line)
                or vendor_media_noise.search(line)):
            continue
        if interesting.search(line) or priority.search(line):
            sys.stdout.write(line)
            sys.stdout.flush()
except KeyboardInterrupt:
    pass
' \
    | tee "$VISIBLE_LOG"
fi
