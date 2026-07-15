#!/usr/bin/env bash
# Apply only archive-level assets whose compatibility is pinned to a ROM variant.
set -euo pipefail

TYPE="${1:-}"
OVERLAY_DIR="${2:-}"
BASE_DIR="${3:-}"

if [ -z "$TYPE" ] || [ -z "$OVERLAY_DIR" ] || [ -z "$BASE_DIR" ]; then
    echo "usage: $0 <a|b|y2> <overlay-dir> <firmware-dir>" >&2
    exit 2
fi

if [ "$TYPE" != "a" ]; then
    echo "==> Preserve type-${TYPE} base boot.img and logo.bin"
    exit 0
fi

for image in boot.img logo.bin; do
    [ -f "$OVERLAY_DIR/$image" ] || {
        echo "missing Type-A overlay: $OVERLAY_DIR/$image" >&2
        exit 1
    }
    cp "$OVERLAY_DIR/$image" "$BASE_DIR/$image"
    echo "==> Replace $image in Type-A ROM archive"
done
