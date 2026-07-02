#!/usr/bin/env bash
# Solar library-scan A/B performance harness.
#
# Measures full library scan time on the Innioasis Y1.
# For the baseline build, only total time is available.
# For the PR build, per-phase timing is read from /sdcard/solar/scan-perf.log.
#
# Usage:
#   ./scripts/measure-scan-perf.sh baseline [baseline.apk]
#   ./scripts/measure-scan-perf.sh pr [pr.apk]
#
# Requirements:
#   - adb installed and Y1 connected
#   - Music files already on the device (default /Music root)
#   - For baseline: any recent Solar release APK
#   - For pr: the PR build containing ScanPerfLog + LibraryScanner

set -euo pipefail

PKG="com.solar.launcher"
DB_PATH="/data/data/$PKG/databases/music_library.db"
PERF_LOG="/storage/sdcard0/solar/scan-perf.log"
REMOTE_DIR="/storage/sdcard0/solar"
TIMEOUT_SEC=600

MODE="${1:-}"
APK="${2:-}"

if [[ "$MODE" != "baseline" && "$MODE" != "pr" ]]; then
  echo "Usage: $0 baseline|pr [apk_file]"
  exit 1
fi

if [[ -n "$APK" && ! -f "$APK" ]]; then
  echo "APK not found: $APK"
  exit 1
fi

echo "=== Solar scan perf: $MODE ==="

# --- Install APK if provided -------------------------------------------------
if [[ -n "$APK" ]]; then
  echo "Installing $APK ..."
  adb install -r "$APK"
fi

# --- Clear music library cache for a cold full-scan --------------------------
echo "Clearing music library database ..."
adb shell am force-stop "$PKG" || true
adb shell "rm -f $DB_PATH $DB_PATH-wal $DB_PATH-shm" || true
adb shell "mkdir -p $REMOTE_DIR"

# --- Ensure log is clean on PR builds ----------------------------------------
if [[ "$MODE" == "pr" ]]; then
  adb shell "rm -f $PERF_LOG $PERF_LOG.old" || true
fi

# --- Launch Solar and trigger scan -------------------------------------------
echo "Launching Solar and triggering library scan ..."
adb shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 2
adb shell am broadcast -a com.solar.launcher.action.START_LIBRARY_SCAN -n "$PKG/.ScanTriggerReceiver" >/dev/null

# --- Wait for scan completion ------------------------------------------------
echo "Waiting for scan to finish (timeout ${TIMEOUT_SEC}s) ..."
START=$(date +%s)
while true; do
  NOW=$(date +%s)
  if (( NOW - START > TIMEOUT_SEC )); then
    echo "Timeout waiting for scan."
    exit 1
  fi

  # PR build writes SolarScanPerf log lines on completion.
  if [[ "$MODE" == "pr" ]]; then
    if adb shell "grep -q 'SolarScanPerf' $PERF_LOG" >/dev/null 2>&1; then
      break
    fi
  fi

  # Both builds log "scan finished" via Debug898913Log.
  if adb logcat -d -s "SolarDbg898913:D" | grep -q "scan finished"; then
    break
  fi

  sleep 2
done

# --- Collect total time from logcat (both builds) ----------------------------
echo ""
echo "--- Total scan time from logcat ---"
adb logcat -d -s "SolarDbg898913:D" | grep -E "(scan started|scan finished|filesystem walk done|parallel scan done)" | tail -10 || true

# --- Collect detailed perf log on PR builds ----------------------------------
if [[ "$MODE" == "pr" ]]; then
  echo ""
  echo "--- Pulling detailed scan perf log ---"
  adb pull "$PERF_LOG" "./scan-perf-$MODE.log" >/dev/null 2>&1 || true
  if [[ -f "./scan-perf-$MODE.log" ]]; then
    echo ""
    echo "Latest entries:"
    tail -5 "./scan-perf-$MODE.log"
    echo ""
    echo "Summary (latest):"
    python3 - <<'PY' ./scan-perf-$MODE.log
import sys, json
with open(sys.argv[1]) as f:
    line = f.readlines()[-1].strip()
data = json.loads(line)
tracks = data.get("tracks", 0)
ms = data.get("totalMs", 0)
phases = data.get("phases", {})
print(f"tracks:        {tracks}")
print(f"total_ms:      {ms}")
print(f"tracks/sec:    {tracks*1000/ms:.1f}" if ms else "tracks/sec:    N/A")
for k, v in phases.items():
    pct = v*100/ms if ms else 0
    print(f"{k:20} {v:6} ms ({pct:.1f}%)")
PY
  else
    echo "Perf log not found on device."
  fi
fi

echo ""
echo "=== $MODE measurement complete ==="
