#!/usr/bin/env bash
# Convert Nicotine+ country flag SVGs to small PNGs for Reach (GPL-3.0, see assets/flags/README.txt).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/reference/nicotine-plus-3.3.10/pynicotine/gtkgui/icons/hicolor/scalable/intl"
DEST="$ROOT/app/src/main/assets/flags"
mkdir -p "$DEST"
if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "rsvg-convert required" >&2
  exit 1
fi
count=0
for svg in "$SRC"/nplus-flag-*.svg; do
  base=$(basename "$svg" .svg)
  cc="${base#nplus-flag-}"
  rsvg-convert -w 24 -h 16 "$svg" -o "$DEST/${cc}.png"
  count=$((count + 1))
done
echo "Wrote $count flags to $DEST"
