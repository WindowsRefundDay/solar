#!/usr/bin/env bash
# Build .gradle/platform.keystore from AOSP test platform.pk8 + platform.x509.pem
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

OUT_DIR="$ROOT/.gradle"
OUT="$OUT_DIR/platform.keystore"
TMP_KEY="$(mktemp)"
trap 'rm -f "$TMP_KEY"' EXIT

[[ -f "$SOLAR_PLATFORM_KEY_PK8" && -f "$SOLAR_PLATFORM_KEY_PEM" ]] || {
  echo "ERROR: platform keys missing (set SOLAR_PLATFORM_KEY_PK8 / SOLAR_PLATFORM_KEY_PEM)" >&2
  exit 1
}

mkdir -p "$OUT_DIR"
openssl pkcs8 -inform DER -nocrypt -in "$SOLAR_PLATFORM_KEY_PK8" -out "$TMP_KEY"
openssl pkcs12 -export \
  -in "$SOLAR_PLATFORM_KEY_PEM" \
  -inkey "$TMP_KEY" \
  -out "$OUT" \
  -name platform \
  -password pass:android
echo "Platform keystore: $OUT"
