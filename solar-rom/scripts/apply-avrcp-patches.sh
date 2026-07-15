#!/usr/bin/env bash
# Apply Koensayr-style AVRCP patches to a mounted system partition (or sysroot).
# Does NOT touch Y1-Rockbox.kl / hardware keylayouts.
#
# Usage: apply-avrcp-patches.sh <system_mount_root> <metadata|metadata+no-standby>
set -euo pipefail

die() { echo "apply-avrcp-patches: $*" >&2; exit 1; }

MOUNT="${1:-}"
PROFILE="${2:-metadata}"
[ -n "$MOUNT" ] || die "usage: apply-avrcp-patches.sh <system_mount_root> <metadata|metadata+no-standby>"
case "$PROFILE" in
    metadata|metadata+no-standby) ;;
    *) die "unknown AVRCP profile: $PROFILE" ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Vendored from koensayr (GPL-3) — reference/ is gitignored and absent in CI.
KOENSAYR_PATCHES="$SCRIPT_DIR/../patches/avrcp"
Y1_BRIDGE_SRC="$SCRIPT_DIR/../system/app/Y1Bridge.apk"

[ -d "$KOENSAYR_PATCHES" ] || die "missing koensayr patches at $KOENSAYR_PATCHES"

patch_in_place() {
    local rel="$1" script="$2" mode="${3:-644}"
    local src="$MOUNT/$rel"
    [ -f "$src" ] || die "missing $src"
    local stage
    stage="$(mktemp -d)"
    trap 'rm -rf "$stage"' RETURN
    cp "$src" "$stage/stock"
    echo "  patching $rel via $script"
    python3 "$KOENSAYR_PATCHES/$script" "$stage/stock" --output "$stage/patched" \
        || die "$script rejected $rel (unknown input or output hash)"
    if [ -f "$stage/patched" ]; then
        cp "$stage/patched" "$src"
        chmod "$mode" "$src"
        chown root:root "$src" 2>/dev/null || true
    else
        echo "  $rel: already patched"
    fi
    rm -rf "$stage"
    trap - RETURN
}

echo "==> AVRCP patches (Koensayr-derived, hardware keylayout untouched)"

patch_in_place "app/MtkBt.odex" "patch_mtkbt_odex.py" 644
patch_in_place "bin/mtkbt" "patch_mtkbt.py" 755
patch_in_place "lib/libextavrcp_jni.so" "patch_libextavrcp_jni.py" 644
patch_in_place "lib/libextavrcp.so" "patch_libextavrcp.py" 644
if [ "$PROFILE" = "metadata+no-standby" ]; then
    patch_in_place "lib/libaudio.a2dp.default.so" "patch_libaudio_a2dp.py" 644
else
    echo "  retaining stock libaudio.a2dp.default.so standby behavior"
fi
patch_in_place "usr/keylayout/AVRCP.kl" "patch_avrcp_kl.py" 644

if [ -f "$Y1_BRIDGE_SRC" ]; then
    echo "==> Installing Y1Bridge.apk (unchanged Koensayr build)"
    mkdir -p "$MOUNT/app"
    cp "$Y1_BRIDGE_SRC" "$MOUNT/app/Y1Bridge.apk"
    chmod 644 "$MOUNT/app/Y1Bridge.apk"
    chown root:root "$MOUNT/app/Y1Bridge.apk" 2>/dev/null || true
else
    die "Y1Bridge.apk not found at $Y1_BRIDGE_SRC"
fi

echo "==> AVRCP patch apply complete"
