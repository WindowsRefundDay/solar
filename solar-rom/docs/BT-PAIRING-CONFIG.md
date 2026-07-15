# Option B — Bluetooth pairing config port (koensayr `--bluetooth`)

Status: **design + runbook only.** No config is applied to real files by this doc. It exists
on an isolated worktree branch so the work can be built and hardware-validated before it ever
reaches a shipped ROM.

## Why

koensayr's `apply.bash --bluetooth` writes a set of Bluetooth config edits that the Solar ROM
build does **not** currently apply (grep-confirmed: no `audio.conf` / `auto_pairing.conf` /
`blacklist.conf` / `ro.bluetooth.class` edits anywhere in the repo). The AVRCP 1.3 compliance
patches (V3/V4/V5 A2DP/AVDTP version bump + `GET_ALL_CAPABILITIES` alias) already ship. This is
the remaining un-ported Bluetooth lever.

The load-bearing edit is `ro.bluetooth.class=10486812` (`0xA0041C`) — the device Class-of-Device.
It advertises the Y1 as Audio/Video Major / Portable-Audio Minor with the Audio + Information
service bits set. Stock CoD lacks the Information service bit, which is the most likely cause of
the "AirPods Pro 2 see the Y1 as a handset" symptom: a strict peer that classifies the source as a
phone/handset can refuse the A2DP audio path while still allowing AVRCP control. Fixing CoD is the
most direct remaining shot at that failure mode.

## Scope of the edits (from koensayr `apply.bash --bluetooth`)

| File | Edit |
|---|---|
| `etc/bluetooth/audio.conf` | `Enable=Source,Control,Target`; `Master=true` |
| `etc/bluetooth/auto_pairing.conf` | clear `AddressBlacklist=`, `ExactNameBlacklist=`, `PartialNameBlacklist=` |
| `etc/bluetooth/blacklist.conf` | delete the `scoSocket` line |
| `build.prop` | append `ro.bluetooth.class=10486812`, `ro.bluetooth.profiles.a2dp.source.enabled=true`, `ro.bluetooth.profiles.avrcp.target.enabled=true` |

**Do NOT** set `persist.bluetooth.avrcpversion` — koensayr dropped it in v2.0.0 because mtkbt
cannot deliver the claimed version; setting it regresses PASSTHROUGH without delivering metadata.

## Step 0 — audit the base first (MANDATORY, blocks all edits)

The rockbox-y1 type-a/type-b base is **not** stock innioasis; its BT config may already differ.
Overwriting a value the base intentionally set could regress currently-working headsets. Before
writing any edit, mount both bases (reuse the loop-mount logic in `build-rom.sh`) and record what
is already there:

```bash
# For each variant a, b: download the pinned base (solar-rom/base-images.sha256),
# loop-mount system.img, then:
for f in etc/bluetooth/audio.conf etc/bluetooth/auto_pairing.conf \
         etc/bluetooth/blacklist.conf build.prop; do
  echo "== $f =="; sudo cat "$MOUNT_SYS/$f" 2>/dev/null || echo "ABSENT"
done
sudo grep -n 'ro.bluetooth' "$MOUNT_SYS/build.prop" || echo "no ro.bluetooth.* keys"
```

Capture, per variant:
- Does each conf file exist? (If absent, decide: create it, or skip that edit.)
- Current `Enable=` and `Master=` in `audio.conf`.
- Current blacklist lines in `auto_pairing.conf` / `blacklist.conf`.
- **Any pre-existing `ro.bluetooth.class`** — if the base already sets a working CoD, do not blindly
  overwrite; reconcile against `0xA0041C` deliberately.

Record the findings in this doc (a "Base audit results" section) before proceeding.

## Step 1 — new script `solar-rom/scripts/apply-bt-pairing.sh`

Model it on `apply-avrcp-patches.sh`: `set -euo pipefail`, a `die()` helper, `die` on missing
targets, idempotent. Signature: `apply-bt-pairing.sh <system_mount_root>`.

Every edit must be **grep-guarded** so a second run is a no-op:
- `audio.conf`: `sed -i 's/^Enable=.*/Enable=Source,Control,Target/'` and
  `s/^Master=.*/Master=true/` (only if the file exists; else skip or template it).
- `auto_pairing.conf`: `sed -i 's/^AddressBlacklist=.*/AddressBlacklist=/'` (+ the two name lists).
- `blacklist.conf`: `sed -i '/^scoSocket/d'`.
- `build.prop`: append each `ro.bluetooth.*` line **only if the key is absent**
  (`grep -q '^ro.bluetooth.class=' || echo 'ro.bluetooth.class=10486812' >> build.prop`).

Match koensayr's exact CoD integer: `10486812` (`0xA0041C`).

## Step 2 — gate + wire into `build-rom.sh`

Add a new opt-in axis (a `--bt-pairing` flag, **default OFF**) — keep it separate from the AVRCP
profile axis so the two don't entangle. When on, call `apply-bt-pairing.sh "$MOUNT_SYS"` inside the
existing `if [ "$TYPE" != "y2" ]` block, after the AVRCP step. Leaving it OFF by default preserves
current shipped behavior and keeps blast radius at zero until validated.

`test_rom_policy.py`'s `AVRCP_PROFILE="metadata"` default assertion must stay intact — this change
is a different axis and should not touch that default.

## Step 3 — extend `audit_rom_contents` in `build-rom.sh`

When the flag is on, assert the resulting on-disk values, fail-closed:
```bash
sudo grep -q 'Enable=Source,Control,Target' "$MOUNT_SYS/etc/bluetooth/audio.conf" \
  || { echo "audit fail: audio.conf Enable not set"; errors=$((errors+1)); }
sudo grep -q '^ro.bluetooth.class=10486812' "$MOUNT_SYS/build.prop" \
  || { echo "audit fail: ro.bluetooth.class not set"; errors=$((errors+1)); }
```
A silently-missed edit then breaks the build instead of shipping a half-applied config.

## Step 4 — provenance + hashes (`verify_rom.py` / `avrcp-images.sha256`)

The conf/build.prop edits change file content, so the post-package verifier must know about them:
- Simplest: have `verify_rom.py` assert the specific lines are present (e.g. the `ro.bluetooth.class`
  line) rather than pinning a whole-file `build.prop` hash (build.prop already varies by injected
  props). Add it as a new named check alongside the existing `avrcp-profile` / `platform-signature`
  checks in the verifier's manifest output.
- Add a `test_rom_policy.py` case asserting `apply-bt-pairing.sh` exists, is idempotent
  (grep-guards present), never sets `persist.bluetooth.avrcpversion`, and uses the exact CoD
  integer `10486812`.

## Step 5 — hardware validation matrix (gates shipping)

Flash type-a with the flag ON. Test **both**:
- **A known-good SBC headset** — regression guard. Must still pair and play audio. If this breaks,
  the CoD or blacklist edit is wrong; do not ship.
- **AirPods Pro 2** — confirm they classify the Y1 as audio (not handset) and that A2DP audio
  reaches `SET_CONFIGURATION → OPEN → START` (not just AVRCP control). Capture with koensayr's
  `btlog` tap + `tools/btlog-parse.py --avrcp` from the reference checkout, per
  `docs/BT-COMPLIANCE.md`.

Rollback: flag defaults OFF, so shipped ROMs stay unaffected until this matrix passes and the flag
is explicitly enabled in `build-release.yml`.

## Risk ledger

| Risk | Mitigation |
|---|---|
| Wrong `ro.bluetooth.class` → all BT devices misclassify | Use koensayr's exact `0xA0041C` / `10486812`; audit base for a pre-existing value first (Step 0). |
| Clearing pairing blacklists re-enables a device the base intentionally blocked | Low risk on a music player; note base's original blacklist contents in Step 0 before clearing. |
| Half-applied config ships silently | Step 3 fail-closed audit + Step 4 verifier check. |
| Blast radius on unvalidated builds | Flag OFF by default; only type-a/type-b, never y2. |

## Relationship to Option A (AH1 no-standby)

Independent and stackable. Option A (flip the CI AVRCP profile to `metadata+no-standby`) reduces
AVDTP teardown/re-negotiate churn; Option B fixes how the peer classifies the device up front. If
AirPods Pro 2 still fail after A, B is the next lever. Neither depends on the other.
