#!/usr/bin/env python3

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILD_ROM = ROOT / "solar-rom/scripts/build-rom.sh"
APPLY_AVRCP = ROOT / "solar-rom/scripts/apply-avrcp-patches.sh"
INPUT_MANIFEST = ROOT / "solar-rom/scripts/input_manifest.py"
VERIFY_ROM = ROOT / "solar-rom/scripts/verify_rom.py"
VARIANT_ASSETS = ROOT / "solar-rom/scripts/apply-variant-archive-assets.sh"
BUTTON_SCRIPT = ROOT / "solar-rom/system/99Y1ButtonScript"
SOLAR_INIT = ROOT / "solar-rom/system/99SolarInit.sh"
KEYMAP_SYNC = ROOT / "solar-rom/scripts/sync-y1-keymap.sh"


class RomPolicyTest(unittest.TestCase):
    def test_builder_pins_bases_and_uses_variant_specific_boot(self):
        text = BUILD_ROM.read_text()
        self.assertIn('BASE_MANIFEST="$REPO_ROOT/solar-rom/base-images.sha256"', text)
        self.assertIn('apply-variant-archive-assets.sh', text)
        self.assertNotIn('"$MOUNT_SYS/app"/com.*.apk', text)
        self.assertNotIn("-iname '*innioasis*'", text)
        self.assertIn('SOLAR_BASE_ROM_ZIP', text)

    def test_post_package_verifier_is_independent_and_fail_closed(self):
        text = VERIFY_ROM.read_text()
        self.assertIn('unpack_boot_ramdisk(temp / "boot.img")', text)
        self.assertIn('system.fsck()', text)
        self.assertIn('userdata.fsck()', text)
        self.assertIn('final APK inventory mismatch', text)
        self.assertIn('unrelated base APK hash changed', text)
        self.assertIn('stale input manifest', text)
        self.assertIn('"avrcp-profile"', text)
        self.assertIn('"platform-signature"', text)
        self.assertIn('"status": "verified"', text)

    def test_type_a_boot_overlay_has_adb_defaults(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("verify_rom", VERIFY_ROM)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        ramdisk = module.unpack_boot_ramdisk(ROOT / "solar-rom/system/boot.img")
        default_prop = ramdisk["default.prop"].decode()
        init_usb = ramdisk["init.usb.rc"].decode()
        self.assertIn("persist.sys.usb.config=mtp,adb", default_prop)
        stanza = init_usb.split("on property:sys.usb.config=mtp,adb\n", 1)[1]
        stanza = stanza.split("\non property:", 1)[0]
        self.assertIn("start adbd", stanza)

    def test_variant_archive_assets_apply_only_to_type_a(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            overlay = temp / "overlay"
            overlay.mkdir()
            (overlay / "boot.img").write_bytes(b"overlay-boot")
            (overlay / "logo.bin").write_bytes(b"overlay-logo")
            for variant, expected in (("a", b"overlay-boot"), ("b", b"base-boot")):
                firmware = temp / variant
                firmware.mkdir()
                (firmware / "boot.img").write_bytes(b"base-boot")
                (firmware / "logo.bin").write_bytes(b"base-logo")
                subprocess.check_call([str(VARIANT_ASSETS), variant, str(overlay), str(firmware)])
                self.assertEqual(expected, (firmware / "boot.img").read_bytes())

    def test_none_profile_excludes_bridge_and_uses_stock_hashes(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("verify_rom", VERIFY_ROM)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        base = {"/app/Stock.apk", "/app/com.innioasis.y1.apk"}
        inventory = module.expected_apk_inventory(base, "none")
        self.assertEqual({"/app/Stock.apk", "/app/com.solar.launcher.apk"}, inventory)
        self.assertNotIn(module.Y1_BRIDGE_PATH, module.expected_overlays_for_profile("none"))
        rows = module.parse_avrcp_manifest(ROOT / "solar-rom/avrcp-images.sha256", "a")
        self.assertEqual(rows["/lib/libaudio.a2dp.default.so"]["none"],
                         rows["/lib/libaudio.a2dp.default.so"]["metadata"])
        self.assertNotEqual(rows["/lib/libaudio.a2dp.default.so"]["metadata"],
                            rows["/lib/libaudio.a2dp.default.so"]["metadata+no-standby"])

    def test_platform_certificate_is_pinned(self):
        digest = (ROOT / "solar-rom/platform-cert.sha256").read_text().strip()
        self.assertEqual(64, len(digest))
        int(digest, 16)
        self.assertIn("pinned platform certificate", VERIFY_ROM.read_text())

    def test_default_avrcp_profile_retains_normal_a2dp_standby(self):
        build = BUILD_ROM.read_text()
        apply = APPLY_AVRCP.read_text()
        self.assertIn('AVRCP_PROFILE="metadata"', build)
        self.assertIn('if [ "$PROFILE" = "metadata+no-standby" ]', apply)
        self.assertNotIn(' --skip-md5 ', apply)

    def test_base_manifest_has_pinned_y1_hashes(self):
        rows = []
        for line in (ROOT / "solar-rom/base-images.sha256").read_text().splitlines():
            if line and not line.startswith("#"):
                rows.append(line.split("|"))
        self.assertEqual(["a", "b"], [row[0] for row in rows])
        for row in rows:
            self.assertEqual(5, len(row))
            for digest in row[2:]:
                self.assertEqual(64, len(digest))
                int(digest, 16)

    def test_input_manifest_digest_changes_after_one_byte_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            apk = temp / "solar.apk"
            extra = temp / "overlay.sh"
            apk.write_bytes(b"apk-v1")
            extra.write_bytes(b"overlay-v1")

            def generate(name):
                output = temp / name
                subprocess.check_call([
                    "python3", str(INPUT_MANIFEST),
                    "--repo-root", str(ROOT),
                    "--variant", "a",
                    "--profile", "metadata",
                    "--base-sha256", "0" * 64,
                    "--apk", str(apk),
                    "--input", str(extra),
                    "--output", str(output),
                ])
                return json.loads(output.read_text())

            first = generate("first.json")
            extra.write_bytes(b"overlay-v2")
            second = generate("second.json")
            self.assertNotEqual(first["transformationDigest"], second["transformationDigest"])

    def test_input_manifest_ignores_generated_python_cache(self):
        text = INPUT_MANIFEST.read_text()
        self.assertIn('"__pycache__" not in path.parts', text)
        self.assertIn('path.suffix not in (".pyc", ".pyo")', text)

    def test_button_reader_is_blocking_dynamic_and_low_churn(self):
        text = BUTTON_SCRIPT.read_text()
        self.assertIn('device_name" = "mtk-tpd-kpd', text)
        self.assertIn('"$GETEVENT_BIN" -t "$device"', text)
        self.assertIn('[ "$value" -eq 2 ] && continue', text)
        self.assertIn('sleep 1', text)
        self.assertNotIn('DEVICE="/dev/input/event2"', text)
        self.assertNotIn('dd if=', text)
        self.assertNotIn('xxd', text)
        self.assertNotIn('reverse_bytes', text)

    def test_init_is_deferred_and_keymap_restores_read_only(self):
        init_text = SOLAR_INIT.read_text()
        keymap_text = KEYMAP_SYNC.read_text()
        self.assertIn('solar-deferred-init.sh', init_text)
        self.assertNotIn('while [', init_text)
        self.assertIn('mount -o remount,rw /system 2>/dev/null || exit 1', keymap_text)
        self.assertIn('mount -o remount,ro /system', keymap_text)
        self.assertIn("trap 'remount_read_only' 0 1 2 15", keymap_text)

    def test_button_reader_parses_captured_api17_numeric_shape(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            fixture = temp / "events.txt"
            fake_getevent = temp / "getevent"
            log = temp / "actions.log"
            fake_getevent.write_text('#!/bin/sh\ncat "$FIXTURE"\n')
            fake_getevent.chmod(0o755)
            fixture.write_text(
                "[ 100.000000] 0001 0069 00000001\n"
                "[ 100.100000] 0001 006a 00000001\n"
                "[ 105.000000] 0001 006a 00000002\n"
                "[ 111.000000] 0001 0069 00000000\n"
                "[ 200.000000] /dev/input/test: 0001 009e 00000001\n"
                "[ 200.100000] /dev/input/test: 0001 00a4 00000001\n"
                "[ 211.000000] /dev/input/test: 0001 00a4 00000000\n"
                # Interrupted chord: no action.
                "[ 300.000000] 0001 0069 00000001\n"
                "[ 300.100000] 0001 006a 00000001\n"
                "[ 302.000000] 0001 006a 00000000\n"
            )
            env = os.environ.copy()
            env.update({
                "FIXTURE": str(fixture),
                "GETEVENT_BIN": str(fake_getevent),
                "SOLAR_BUTTON_TEST_LOG": str(log),
                "SOLAR_BUTTON_TEST_SCREEN_ON": "1",
            })
            subprocess.check_call([
                "sh", str(BUTTON_SCRIPT), "--test-reader", "/dev/input/test"
            ], env=env)
            self.assertEqual(["restart", "handoff"], log.read_text().splitlines())

            log.unlink()
            env["SOLAR_BUTTON_TEST_SCREEN_ON"] = "0"
            subprocess.check_call([
                "sh", str(BUTTON_SCRIPT), "--test-reader", "/dev/input/test"
            ], env=env)
            self.assertFalse(log.exists())


if __name__ == "__main__":
    unittest.main()
