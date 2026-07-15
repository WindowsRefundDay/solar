#!/usr/bin/env python3
"""Independent, read-only verification of a completed Solar firmware archive."""

import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path


REQUIRED_IMAGES = ("boot.img", "logo.bin", "system.img", "userdata.img")
SYSTEM_OVERLAYS = {
    "/etc/init.d/99SolarInit.sh": "solar-rom/system/99SolarInit.sh",
    "/etc/init.d/99Y1ButtonScript": "solar-rom/system/99Y1ButtonScript",
    "/etc/solar/switch-to-stock.sh": "solar-rom/scripts/switch-to-stock.sh",
    "/etc/solar/switch-to-rockbox.sh": "solar-rom/scripts/switch-to-rockbox.sh",
    "/etc/solar/sync-rockbox-libs.sh": "solar-rom/scripts/sync-rockbox-libs.sh",
    "/etc/solar/sync-y1-keymap.sh": "solar-rom/scripts/sync-y1-keymap.sh",
    "/etc/solar/disable-rockbox-for-solar.sh": "solar-rom/scripts/disable-rockbox-for-solar.sh",
    "/etc/solar/solar-usb-recovery-agent.sh": "solar-rom/scripts/solar-usb-recovery-agent.sh",
    "/etc/solar/solar-deferred-init.sh": "solar-rom/scripts/solar-deferred-init.sh",
    "/etc/solar/Y1-Rockbox.kl": "solar-rom/scripts/Y1-Rockbox.kl",
}
Y1_BRIDGE_PATH = "/app/Y1Bridge.apk"
Y1_BRIDGE_SOURCE = "solar-rom/system/app/Y1Bridge.apk"
REMOVED_LAUNCHER_APKS = {
    "/app/com.innioasis.y1.apk", "/app/com.innioasis.y2.apk",
    "/priv-app/com.innioasis.y1.apk", "/priv-app/com.innioasis.y2.apk",
}


def fail(message):
    raise RuntimeError(message)


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def tool(name, extra=()):
    found = shutil.which(name)
    if found:
        return found
    for candidate in extra:
        if Path(candidate).is_file():
            return str(candidate)
    fail("missing required tool: " + name)


def run(command, ok=(0,), text=True):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=text, check=False)
    if result.returncode not in ok:
        stderr = result.stderr.strip() if text else result.stderr.decode("utf-8", "replace").strip()
        fail("command failed ({}): {}\n{}".format(result.returncode, " ".join(command), stderr))
    return result


def parse_base_manifest(path, variant):
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("|")
        if len(fields) == 5 and fields[0] == variant:
            return dict(zip(("variant", "url", "zipSha256", "bootSha256", "scatterSha256"), fields))
    fail("base manifest has no entry for type " + variant)


def parse_avrcp_manifest(path, variant):
    rows = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("|")
        if len(fields) != 5 or fields[0] != variant:
            continue
        for digest in fields[2:]:
            if not re.fullmatch(r"[0-9a-f]{64}", digest):
                fail("invalid AVRCP SHA-256 manifest row")
        rows[fields[1]] = {
            "none": fields[2],
            "metadata": fields[3],
            "metadata+no-standby": fields[4],
        }
    if not rows:
        fail("AVRCP manifest has no entry for type " + variant)
    return rows


def safe_extract_member(archive, member, destination):
    info = archive.getinfo(member)
    if info.is_dir() or Path(member).name != member:
        fail("unsafe or nested firmware member: " + member)
    with archive.open(info) as source, Path(destination).open("wb") as output:
        shutil.copyfileobj(source, output, 1024 * 1024)


def unpack_boot_ramdisk(path):
    data = Path(path).read_bytes()
    if data[:8] != b"ANDROID!" or len(data) < 48:
        fail("boot.img is not an Android boot image")
    kernel_size = struct.unpack_from("<I", data, 8)[0]
    ramdisk_size = struct.unpack_from("<I", data, 16)[0]
    page_size = struct.unpack_from("<I", data, 36)[0]
    if page_size < 512 or page_size > 65536 or page_size & (page_size - 1):
        fail("invalid boot image page size")
    ramdisk_offset = page_size + ((kernel_size + page_size - 1) // page_size) * page_size
    compressed = data[ramdisk_offset:ramdisk_offset + ramdisk_size]
    # MediaTek boot images prepend a 512-byte ROOTFS header to the gzip stream.
    if not compressed.startswith(b"\x1f\x8b") and compressed[512:514] == b"\x1f\x8b":
        compressed = compressed[512:]
    try:
        cpio = gzip.decompress(compressed)
    except OSError as error:
        fail("boot ramdisk is not valid gzip: " + str(error))
    files = {}
    offset = 0
    while offset + 110 <= len(cpio):
        header = cpio[offset:offset + 110]
        if header[:6] not in (b"070701", b"070702"):
            fail("unsupported or corrupt ramdisk cpio")
        namesize = int(header[94:102], 16)
        filesize = int(header[54:62], 16)
        offset += 110
        name_bytes = cpio[offset:offset + namesize]
        if len(name_bytes) != namesize or not name_bytes.endswith(b"\0"):
            fail("corrupt ramdisk cpio name")
        name = name_bytes[:-1].decode("utf-8", "replace").lstrip("./")
        offset = (offset + namesize + 3) & ~3
        payload = cpio[offset:offset + filesize]
        if len(payload) != filesize:
            fail("corrupt ramdisk cpio payload")
        if name == "TRAILER!!!":
            break
        files[name] = payload
        offset = (offset + filesize + 3) & ~3
    return files


class ExtImage:
    def __init__(self, image, debugfs, dumpe2fs, e2fsck, temp):
        self.image = str(image)
        self.debugfs = debugfs
        self.dumpe2fs = dumpe2fs
        self.e2fsck = e2fsck
        self.temp = Path(temp)

    def command(self, expression):
        result = run([self.debugfs, "-R", expression, self.image])
        return result.stdout + "\n" + result.stderr

    def stat(self, path):
        output = self.command("stat " + path)
        if "File not found" in output or "not found by ext2_lookup" in output:
            return None
        match = re.search(r"Mode:\s+([0-7]+).*?User:\s+(\d+)\s+Group:\s+(\d+).*?Size:\s+(\d+)",
                          output, re.S)
        if not match:
            fail("could not parse ext stat for " + path)
        return {"mode": match.group(1).zfill(4), "uid": int(match.group(2)),
                "gid": int(match.group(3)), "size": int(match.group(4))}

    def dump(self, path):
        if self.stat(path) is None:
            fail("missing image path: " + path)
        destination = self.temp / (hashlib.sha256(path.encode()).hexdigest() + ".dump")
        self.command("dump -p {} {}".format(path, destination))
        if not destination.is_file():
            fail("debugfs did not extract " + path)
        return destination

    def list_dir(self, path):
        output = self.command("ls -p " + path)
        entries = []
        for line in output.splitlines():
            fields = line.strip().split("/")
            if len(fields) >= 7 and fields[5] not in (".", "..", ""):
                entries.append({"mode": fields[2], "uid": int(fields[3]),
                                "gid": int(fields[4]), "name": fields[5]})
        return entries

    def free_bytes(self):
        output = run([self.dumpe2fs, "-h", self.image]).stdout
        free_blocks = re.search(r"^Free blocks:\s+(\d+)", output, re.M)
        block_size = re.search(r"^Block size:\s+(\d+)", output, re.M)
        if not free_blocks or not block_size:
            fail("could not read filesystem free space")
        return int(free_blocks.group(1)) * int(block_size.group(1))

    def fsck(self):
        result = run([self.e2fsck, "-fn", self.image], ok=(0, 1))
        if result.returncode != 0:
            fail("read-only fsck reported filesystem errors: " + self.image)


def apk_inventory(image):
    inventory = {}
    for directory in ("/app", "/priv-app"):
        if image.stat(directory) is None:
            continue
        for entry in image.list_dir(directory):
            if entry["name"].lower().endswith(".apk"):
                path = directory + "/" + entry["name"]
                inventory[path] = sha256_file(image.dump(path))
    return inventory


def expected_overlays_for_profile(profile):
    overlays = dict(SYSTEM_OVERLAYS)
    if profile != "none":
        overlays[Y1_BRIDGE_PATH] = Y1_BRIDGE_SOURCE
    return overlays


def expected_apk_inventory(base_paths, profile):
    expected = set(base_paths) - REMOVED_LAUNCHER_APKS
    expected.add("/app/com.solar.launcher.apk")
    if profile != "none":
        expected.add(Y1_BRIDGE_PATH)
    return expected


def verify_avrcp_profile(system, base_system, repo, variant, profile):
    expected = parse_avrcp_manifest(repo / "solar-rom/avrcp-images.sha256", variant)
    hashes = {}
    for path, profiles in sorted(expected.items()):
        base_hash = sha256_file(base_system.dump(path))
        if base_hash != profiles["none"]:
            fail("pinned base AVRCP hash mismatch: " + path)
        final_hash = sha256_file(system.dump(path))
        if final_hash != profiles[profile]:
            fail("final AVRCP hash mismatch for {}: {}".format(profile, path))
        hashes[path] = final_hash

    bridge = system.stat(Y1_BRIDGE_PATH)
    if profile == "none":
        if bridge is not None:
            fail("none AVRCP profile unexpectedly contains Y1Bridge.apk")
    else:
        if bridge is None:
            fail(profile + " AVRCP profile is missing Y1Bridge.apk")
        bridge_hash = sha256_file(system.dump(Y1_BRIDGE_PATH))
        if bridge_hash != sha256_file(repo / Y1_BRIDGE_SOURCE):
            fail("Y1Bridge.apk differs from pinned source")
        hashes[Y1_BRIDGE_PATH] = bridge_hash
    return hashes


def inspect_apk(apk, aapt, apksigner):
    badging = run([aapt, "dump", "badging", str(apk)]).stdout
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", badging, re.M)
    if not package:
        fail("could not parse APK package/version")
    cert_output = run([apksigner, "verify", "--print-certs", str(apk)]).stdout
    cert = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", cert_output)
    if not cert:
        fail("could not parse APK signer certificate")
    return {"packageName": package.group(1), "versionCode": package.group(2),
            "versionName": package.group(3), "sha256": sha256_file(apk),
            "certificateSha256": cert.group(1).lower()}


def verify_provenance(repo, sidecar, apk, variant, base, temp):
    payload = json.loads(sidecar.read_text(encoding="utf-8"))
    if payload.get("variant") != variant or payload.get("baseZipSha256") != base["zipSha256"]:
        fail("input manifest variant/base binding mismatch")
    profile = payload.get("avrcpProfile")
    if profile not in ("none", "metadata", "metadata+no-standby"):
        fail("invalid AVRCP profile in input manifest")
    fresh = Path(temp) / "fresh-inputs.json"
    staged_apk = Path(temp) / "solar.apk"
    shutil.copyfile(apk, staged_apk)
    command = ["python3", str(repo / "solar-rom/scripts/input_manifest.py"),
               "--repo-root", str(repo), "--variant", variant, "--profile", profile,
               "--base-sha256", base["zipSha256"], "--apk", str(staged_apk), "--output", str(fresh),
               "--input", str(repo / "solar-rom/base-images.sha256"),
               "--input", str(repo / "solar-rom/avrcp-images.sha256"),
               "--input", str(repo / "solar-rom/platform-cert.sha256"),
               "--input", str(repo / "solar-rom/scripts/build-rom.sh"),
               "--input", str(repo / "scripts/stage-y1-system-prep.sh"),
               "--input", str(repo / "scripts/apply-y1-system-prep.sh"),
               "--input-dir", str(repo / "solar-rom/system"),
               "--input-dir", str(repo / "solar-rom/scripts"),
               "--input-dir", str(repo / "app/src/main/assets/certs")]
    if profile != "none":
        command += ["--input-dir", str(repo / "solar-rom/patches/avrcp")]
    run(command)
    current = json.loads(fresh.read_text(encoding="utf-8"))
    if current.get("transformationDigest") != payload.get("transformationDigest"):
        fail("stale input manifest: current transformation digest differs")
    return payload


def acquire_base_zip(base, destination):
    supplied = os.environ.get("SOLAR_BASE_ROM_ZIP", "")
    if supplied:
        source = Path(supplied)
        if not source.is_file():
            fail("SOLAR_BASE_ROM_ZIP does not exist")
        shutil.copyfile(source, destination)
    else:
        urllib.request.urlretrieve(base["url"], destination)
    actual = sha256_file(destination)
    if actual != base["zipSha256"]:
        fail("base ZIP SHA-256 mismatch: expected {}, got {}".format(base["zipSha256"], actual))


def verify(args):
    repo = Path(__file__).resolve().parents[2]
    rom = Path(args.zip).resolve()
    apk = Path(args.apk).resolve()
    sidecar = Path(str(rom) + ".inputs.json")
    for path in (rom, apk, sidecar):
        if not path.is_file():
            fail("missing required file: " + str(path))
    base = parse_base_manifest(repo / "solar-rom/base-images.sha256", args.type)
    debugfs = tool("debugfs", ("/opt/homebrew/opt/e2fsprogs/sbin/debugfs",))
    dumpe2fs = tool("dumpe2fs", ("/opt/homebrew/opt/e2fsprogs/sbin/dumpe2fs",))
    e2fsck = tool("e2fsck", ("/opt/homebrew/opt/e2fsprogs/sbin/e2fsck",))
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", "/opt/homebrew/share/android-commandlinetools"))
    aapt = tool("aapt", tuple(str(path) for path in sorted((sdk / "build-tools").glob("*/aapt"), reverse=True)))
    apksigner = tool("apksigner", tuple(str(path) for path in sorted((sdk / "build-tools").glob("*/apksigner"), reverse=True)))

    with tempfile.TemporaryDirectory(prefix="solar-rom-verify-") as raw_temp:
        temp = Path(raw_temp)
        provenance = verify_provenance(repo, sidecar, apk, args.type, base, temp)
        with zipfile.ZipFile(rom) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                fail("ROM ZIP contains duplicate member names")
            for member in REQUIRED_IMAGES:
                if member not in names:
                    fail("ROM ZIP missing " + member)
                safe_extract_member(archive, member, temp / member)
            scatter = "MT6572_Android_scatter.txt" if args.type in ("a", "b") else "MT6582_Android_scatter.txt"
            if scatter not in names:
                fail("ROM ZIP missing scatter file")
            safe_extract_member(archive, scatter, temp / scatter)

        image_hashes = {name: sha256_file(temp / name) for name in REQUIRED_IMAGES}
        if sha256_file(temp / scatter) != base["scatterSha256"]:
            fail("final scatter file differs from pinned base")

        ramdisk = unpack_boot_ramdisk(temp / "boot.img")
        default_prop = ramdisk.get("default.prop", b"").decode("utf-8", "replace")
        init_usb = ramdisk.get("init.usb.rc", b"").decode("utf-8", "replace")
        if args.type == "a":
            overlay_boot = repo / "solar-rom/system/boot.img"
            if image_hashes["boot.img"] != sha256_file(overlay_boot):
                fail("Type-A final boot image differs from validated overlay")
            if image_hashes["logo.bin"] != sha256_file(repo / "solar-rom/system/logo.bin"):
                fail("Type-A final logo differs from validated overlay")
            if "persist.sys.usb.config=mtp,adb" not in default_prop:
                fail("Type-A boot default.prop does not retain mtp,adb")
            mtp_adb = re.search(r"on property:sys\.usb\.config=mtp,adb\n(.*?)(?=\non property:|\Z)",
                                init_usb, re.S)
            if not mtp_adb or "start adbd" not in mtp_adb.group(1):
                fail("Type-A init.usb.rc has no ADB-starting USB stanza")
        elif image_hashes["boot.img"] != base["bootSha256"]:
            fail("Type-B final boot image differs from pinned base boot")

        system = ExtImage(temp / "system.img", debugfs, dumpe2fs, e2fsck, temp / "system-dumps")
        userdata = ExtImage(temp / "userdata.img", debugfs, dumpe2fs, e2fsck, temp / "userdata-dumps")
        (temp / "system-dumps").mkdir()
        (temp / "userdata-dumps").mkdir()
        system.fsck()
        userdata.fsck()
        free_space = {"system": system.free_bytes(), "userdata": userdata.free_bytes()}
        if free_space["system"] < 16 * 1024 * 1024 or free_space["userdata"] < 64 * 1024 * 1024:
            fail("insufficient free space in final images")

        installed_apk = system.dump("/app/com.solar.launcher.apk")
        if sha256_file(installed_apk) != sha256_file(apk):
            fail("Solar APK in system.img differs from requested release APK")
        solar_stat = system.stat("/app/com.solar.launcher.apk")
        if solar_stat["mode"] != "0644" or solar_stat["uid"] != 0 or solar_stat["gid"] != 0:
            fail("Solar APK mode/ownership is not root:root 0644")
        apk_info = inspect_apk(apk, aapt, apksigner)
        if apk_info["packageName"] != "com.solar.launcher":
            fail("unexpected APK package: " + apk_info["packageName"])
        expected_cert = (repo / "solar-rom/platform-cert.sha256").read_text(
                encoding="ascii").strip().lower()
        if apk_info["certificateSha256"] != expected_cert:
            fail("Solar APK is not signed by the pinned platform certificate")

        overlays = {}
        expected_overlays = expected_overlays_for_profile(provenance["avrcpProfile"])
        for image_path, repo_path in expected_overlays.items():
            extracted = system.dump(image_path)
            expected = repo / repo_path
            actual_hash = sha256_file(extracted)
            expected_hash = sha256_file(expected)
            if actual_hash != expected_hash:
                fail("system overlay differs from source: " + image_path)
            stat_info = system.stat(image_path)
            expected_mode = "0755" if image_path.endswith(".sh") or "/init.d/" in image_path else "0644"
            if stat_info["mode"] != expected_mode or stat_info["uid"] != 0 or stat_info["gid"] != 0:
                fail("bad mode/ownership for " + image_path)
            overlays[image_path] = actual_hash

        data_switch = userdata.dump("/data/switch-to-stock.sh")
        if sha256_file(data_switch) != overlays["/etc/solar/switch-to-stock.sh"]:
            fail("userdata switch-to-stock.sh differs from system overlay")
        data_switch_stat = userdata.stat("/data/switch-to-stock.sh")
        if data_switch_stat["mode"] != "0755" or data_switch_stat["uid"] != 0 or data_switch_stat["gid"] != 0:
            fail("bad mode/ownership for userdata switch-to-stock.sh")
        forbidden_userdata = ("/data/com.innioasis.y1.apk", "/data/com.innioasis.y2.apk",
                              "/data/initialized", "/data/.solar_rom_home_ready")
        for path in forbidden_userdata:
            if userdata.stat(path) is not None:
                fail("stale seeded userdata path remains: " + path)

        base_zip = temp / "base.zip"
        acquire_base_zip(base, base_zip)
        with zipfile.ZipFile(base_zip) as archive:
            safe_extract_member(archive, "system.img", temp / "base-system.img")
            safe_extract_member(archive, "logo.bin", temp / "base-logo.bin")
        if args.type == "b" and image_hashes["logo.bin"] != sha256_file(temp / "base-logo.bin"):
            fail("Type-B final logo differs from pinned base")
        base_system = ExtImage(temp / "base-system.img", debugfs, dumpe2fs, e2fsck, temp / "base-dumps")
        (temp / "base-dumps").mkdir()
        avrcp_hashes = verify_avrcp_profile(system, base_system, repo, args.type,
                                            provenance["avrcpProfile"])
        base_inventory = apk_inventory(base_system)
        final_inventory = apk_inventory(system)
        removed = set(base_inventory) & REMOVED_LAUNCHER_APKS
        expected_inventory = expected_apk_inventory(base_inventory, provenance["avrcpProfile"])
        if set(final_inventory) != expected_inventory:
            fail("final APK inventory mismatch; missing={}, unexpected={}".format(
                sorted(expected_inventory - set(final_inventory)),
                sorted(set(final_inventory) - expected_inventory)))
        changed_unrelated = [path for path in sorted(set(base_inventory) - removed - {Y1_BRIDGE_PATH})
                             if final_inventory.get(path) != base_inventory[path]]
        if changed_unrelated:
            fail("unrelated base APK hash changed: " + ", ".join(changed_unrelated))

        result = {
            "schemaVersion": 1,
            "status": "verified",
            "variant": args.type,
            "avrcpProfile": provenance["avrcpProfile"],
            "baseZipSha256": base["zipSha256"],
            "builderGitHead": provenance["gitHead"],
            "builderGitDirty": provenance["gitDirty"],
            "trackedDiffSha256": provenance["trackedDiffSha256"],
            "transformationDigest": provenance["transformationDigest"],
            "inputManifestSha256": sha256_file(sidecar),
            "romZip": {"name": rom.name, "size": rom.stat().st_size, "sha256": sha256_file(rom)},
            "images": {name: {"sha256": digest, "size": (temp / name).stat().st_size}
                       for name, digest in sorted(image_hashes.items())},
            "apk": apk_info,
            "avrcpBinaryHashes": avrcp_hashes,
            "freeBytes": free_space,
            "apkInventoryCount": len(final_inventory),
            "unchangedBaseApkCount": len(base_inventory) - len(removed)
                    - (1 if Y1_BRIDGE_PATH in base_inventory else 0),
            "overlayHashes": overlays,
            "checks": ["zip-members", "pinned-base", "boot-ramdisk", "read-only-fsck",
                       "free-space", "apk-identity", "platform-signature", "avrcp-profile",
                       "apk-inventory", "unrelated-apk-hashes",
                       "overlay-equality", "ownership-modes", "userdata-seed", "provenance-binding"],
        }
        output = Path(args.output) if args.output else Path(str(rom) + ".manifest.json")
        output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return output, result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("type", choices=("a", "b"))
    parser.add_argument("zip")
    parser.add_argument("apk")
    parser.add_argument("--output")
    args = parser.parse_args()
    try:
        output, result = verify(args)
    except Exception as error:
        print("verify-rom: FAIL: " + str(error), file=sys.stderr)
        return 1
    print("verify-rom: VERIFIED {} ({})".format(result["romZip"]["name"], result["romZip"]["sha256"]))
    print("verify-rom: manifest " + str(output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
