#!/usr/bin/env python3
"""Tests for apply-bt-pairing.sh — the koensayr --bluetooth config port.

Runs the script against a fake mount root (no hardware, no sudo, no mount) and
asserts: correct values applied, idempotent on re-run, exact CoD integer, never
sets persist.bluetooth.avrcpversion, and grep-guards present in the source.
"""

import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
APPLY_BT = ROOT / "solar-rom/scripts/apply-bt-pairing.sh"
BUILD_ROM = ROOT / "solar-rom/scripts/build-rom.sh"


def make_fake_mount(tmp: Path) -> Path:
    """A minimal system mount with stock-ish BT config + build.prop."""
    mount = tmp / "sys"
    (mount / "etc/bluetooth").mkdir(parents=True)
    (mount / "etc/bluetooth/audio.conf").write_text(
        "[General]\nEnable=Source\nMaster=false\n"
    )
    (mount / "etc/bluetooth/auto_pairing.conf").write_text(
        "[General]\nAddressBlacklist=00:11:22\nExactNameBlacklist=Foo\n"
        "PartialNameBlacklist=Bar\n"
    )
    (mount / "etc/bluetooth/blacklist.conf").write_text(
        "scoSocket = 0x1234\nkeepThis = 1\n"
    )
    (mount / "build.prop").write_text("ro.product.model=Y1\nro.build.id=stock\n")
    return mount


def run_apply(mount: Path) -> None:
    subprocess.check_call(["bash", str(APPLY_BT), str(mount)])


class BtPairingTest(unittest.TestCase):
    def test_applies_expected_values(self):
        with tempfile.TemporaryDirectory() as d:
            mount = make_fake_mount(Path(d))
            run_apply(mount)

            audio = (mount / "etc/bluetooth/audio.conf").read_text()
            self.assertIn("Enable=Source,Control,Target", audio)
            self.assertIn("Master=true", audio)

            auto = (mount / "etc/bluetooth/auto_pairing.conf").read_text()
            self.assertIn("AddressBlacklist=\n", auto)
            self.assertIn("ExactNameBlacklist=\n", auto)
            self.assertIn("PartialNameBlacklist=\n", auto)

            blk = (mount / "etc/bluetooth/blacklist.conf").read_text()
            self.assertNotIn("scoSocket", blk)
            self.assertIn("keepThis = 1", blk)

            prop = (mount / "build.prop").read_text()
            self.assertIn("ro.bluetooth.class=10486812", prop)
            self.assertIn("ro.bluetooth.profiles.a2dp.source.enabled=true", prop)
            self.assertIn("ro.bluetooth.profiles.avrcp.target.enabled=true", prop)
            self.assertNotIn("persist.bluetooth.avrcpversion", prop)

    def test_idempotent_second_run_is_byte_stable(self):
        with tempfile.TemporaryDirectory() as d:
            mount = make_fake_mount(Path(d))
            run_apply(mount)
            after_first = {
                p: (mount / p).read_bytes()
                for p in ("build.prop", "etc/bluetooth/audio.conf",
                          "etc/bluetooth/auto_pairing.conf",
                          "etc/bluetooth/blacklist.conf")
            }
            run_apply(mount)
            for p, first in after_first.items():
                self.assertEqual(first, (mount / p).read_bytes(),
                                 f"{p} changed on second run — not idempotent")

    def test_preexisting_cod_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as d:
            mount = make_fake_mount(Path(d))
            bp = mount / "build.prop"
            bp.write_text(bp.read_text() + "ro.bluetooth.class=99999999\n")
            run_apply(mount)
            prop = bp.read_text()
            self.assertIn("ro.bluetooth.class=99999999", prop)
            # No second, conflicting line appended.
            self.assertEqual(1, prop.count("ro.bluetooth.class="))

    def test_missing_build_prop_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            mount = Path(d) / "sys"
            (mount / "etc/bluetooth").mkdir(parents=True)
            proc = subprocess.run(["bash", str(APPLY_BT), str(mount)],
                                  capture_output=True, text=True)
            self.assertNotEqual(0, proc.returncode)
            self.assertIn("build.prop", proc.stderr)

    def test_source_is_grep_guarded_and_no_avrcpversion(self):
        src = APPLY_BT.read_text()
        self.assertIn("grep -q '^Enable=Source,Control,Target'", src)
        # Never assign it (a comment naming it is fine; an `=` assignment is not).
        self.assertNotIn("persist.bluetooth.avrcpversion=", src)
        self.assertIn("10486812", src)

    def test_build_rom_gate_defaults_off_and_audits(self):
        build = BUILD_ROM.read_text()
        self.assertIn("BT_PAIRING=0", build)
        self.assertIn("--bt-pairing", build)
        self.assertIn("apply-bt-pairing.sh", build)
        self.assertIn("ro.bluetooth.class=10486812", build)


if __name__ == "__main__":
    unittest.main()
