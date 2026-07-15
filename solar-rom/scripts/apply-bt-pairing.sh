#!/usr/bin/env bash
# Apply koensayr-style Bluetooth pairing config to a mounted system partition.
# Ports `apply.bash --bluetooth`: audio.conf / auto_pairing.conf / blacklist.conf
# + build.prop ro.bluetooth.class (Class-of-Device) so strict A2DP sinks (e.g.
# AirPods Pro 2) classify the Y1 as an audio device, not a handset.
#
# Idempotent: every edit is grep-guarded, so re-running is a no-op.
# Deliberately does NOT set persist.bluetooth.avrcpversion (koensayr dropped it —
# mtkbt cannot deliver the claimed version; setting it regresses PASSTHROUGH).
#
# See solar-rom/docs/BT-PAIRING-CONFIG.md for rationale + validation matrix.
#
# Usage: apply-bt-pairing.sh <system_mount_root>
set -euo pipefail

die() { echo "apply-bt-pairing: $*" >&2; exit 1; }
log() { echo "  $*"; }

MOUNT="${1:-}"
[ -n "$MOUNT" ] || die "usage: apply-bt-pairing.sh <system_mount_root>"
[ -d "$MOUNT" ] || die "mount root not a directory: $MOUNT"

# In-place sed that works on both GNU (Linux build host) and BSD (macOS dev).
if sed --version >/dev/null 2>&1; then
    sed_i() { sed -i "$@"; }
else
    sed_i() { sed -i '' "$@"; }
fi

# Class-of-Device 0xA0041C: Audio/Video major, Portable-Audio minor,
# Audio + Information service bits. Matches koensayr exactly.
COD_VALUE="10486812"

AUDIO_CONF="$MOUNT/etc/bluetooth/audio.conf"
AUTO_PAIRING="$MOUNT/etc/bluetooth/auto_pairing.conf"
BLACKLIST="$MOUNT/etc/bluetooth/blacklist.conf"
BUILD_PROP="$MOUNT/build.prop"

echo "==> Bluetooth pairing config (koensayr-derived; CoD ${COD_VALUE})"

# build.prop is mandatory — the CoD + profile-enable props are the load-bearing edits.
[ -f "$BUILD_PROP" ] || die "missing $BUILD_PROP"

# audio.conf: advertise Source+Control+Target and act as pairing master.
# Only touch keys that already exist (do not template a stock-absent file).
if [ -f "$AUDIO_CONF" ]; then
    grep -q '^Enable=Source,Control,Target' "$AUDIO_CONF" \
        || sed_i 's/^Enable=.*/Enable=Source,Control,Target/' "$AUDIO_CONF"
    grep -q '^Master=true' "$AUDIO_CONF" \
        || sed_i 's/^Master=.*/Master=true/' "$AUDIO_CONF"
    log "audio.conf: Enable=Source,Control,Target Master=true"
else
    log "audio.conf absent — skipped (base has no BlueZ audio.conf)"
fi

# auto_pairing.conf: clear name/address blacklists so strict peers auto-pair.
if [ -f "$AUTO_PAIRING" ]; then
    sed_i 's/^AddressBlacklist=.*/AddressBlacklist=/' "$AUTO_PAIRING"
    sed_i 's/^ExactNameBlacklist=.*/ExactNameBlacklist=/' "$AUTO_PAIRING"
    sed_i 's/^PartialNameBlacklist=.*/PartialNameBlacklist=/' "$AUTO_PAIRING"
    log "auto_pairing.conf: blacklists cleared"
else
    log "auto_pairing.conf absent — skipped"
fi

# blacklist.conf: drop the scoSocket line (blocks headset SCO on some peers).
if [ -f "$BLACKLIST" ]; then
    if grep -q '^scoSocket' "$BLACKLIST"; then
        sed_i '/^scoSocket/d' "$BLACKLIST"
        log "blacklist.conf: scoSocket removed"
    else
        log "blacklist.conf: no scoSocket line (already clean)"
    fi
else
    log "blacklist.conf absent — skipped"
fi

# build.prop: append-if-absent. Never overwrite a pre-existing ro.bluetooth.class
# (Step 0 base audit decides that reconciliation deliberately).
append_prop() {
    local key="$1" val="$2"
    if grep -q "^${key}=" "$BUILD_PROP"; then
        local cur
        cur="$(grep "^${key}=" "$BUILD_PROP" | head -1 | cut -d= -f2-)"
        if [ "$cur" != "$val" ]; then
            log "build.prop: ${key} already set to '${cur}' (leaving as-is; audit Step 0)"
        else
            log "build.prop: ${key}=${val} (already present)"
        fi
        return 0
    fi
    printf '%s=%s\n' "$key" "$val" >> "$BUILD_PROP"
    log "build.prop: +${key}=${val}"
}

# Header comment once (grep-guarded) so appended block is self-documenting.
grep -q '^# koensayr-derived Bluetooth pairing config' "$BUILD_PROP" \
    || printf '\n# koensayr-derived Bluetooth pairing config (apply-bt-pairing.sh)\n' >> "$BUILD_PROP"

append_prop "ro.bluetooth.class" "$COD_VALUE"
append_prop "ro.bluetooth.profiles.a2dp.source.enabled" "true"
append_prop "ro.bluetooth.profiles.avrcp.target.enabled" "true"

echo "==> Bluetooth pairing config applied"
