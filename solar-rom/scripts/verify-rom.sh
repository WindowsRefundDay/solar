#!/usr/bin/env bash
# Immutable post-package verifier for a completed Solar Y1 firmware ZIP.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "$#" -lt 3 ]; then
    echo "usage: $0 <a|b> <rom.zip> <app-release.apk> [--output manifest.json]" >&2
    exit 2
fi

exec python3 "$SCRIPT_DIR/verify_rom.py" "$@"
