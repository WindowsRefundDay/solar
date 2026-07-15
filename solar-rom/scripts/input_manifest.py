#!/usr/bin/env python3
"""Emit a canonical manifest for every input consumed by a ROM transformation."""

import argparse
import hashlib
import json
import os
import stat
import subprocess
from pathlib import Path


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo)] + list(args))


def display_path(repo, path, apk):
    if path == apk:
        return "@apk/" + path.name
    try:
        return path.relative_to(repo).as_posix()
    except ValueError:
        return "@external/" + path.name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True)
    parser.add_argument("--variant", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--base-sha256", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--input", action="append", default=[])
    parser.add_argument("--input-dir", action="append", default=[])
    args = parser.parse_args()

    repo = Path(args.repo_root).resolve()
    apk = Path(args.apk).resolve()
    paths = {apk}
    for item in args.input:
        paths.add(Path(item).resolve())
    for directory in args.input_dir:
        root = Path(directory).resolve()
        paths.update(path for path in root.rglob("*")
                     if path.is_file()
                     and "__pycache__" not in path.parts
                     and path.suffix not in (".pyc", ".pyo"))

    missing = [str(path) for path in paths if not path.is_file()]
    if missing:
        raise SystemExit("missing transformation input(s): " + ", ".join(sorted(missing)))

    entries = []
    for path in paths:
        mode = stat.S_IMODE(path.stat().st_mode)
        entries.append({
            "path": display_path(repo, path, apk),
            "mode": format(mode, "04o"),
            "sha256": sha256_file(path),
            "size": path.stat().st_size,
        })
    entries.sort(key=lambda entry: entry["path"])

    status = git(repo, "status", "--porcelain=v1", "--untracked-files=all")
    tracked_diff = git(repo, "diff", "--binary", "HEAD", "--")
    payload = {
        "schemaVersion": 1,
        "variant": args.variant,
        "avrcpProfile": args.profile,
        "baseZipSha256": args.base_sha256,
        "gitHead": git(repo, "rev-parse", "HEAD").decode("ascii").strip(),
        "gitDirty": bool(status.strip()),
        "trackedDiffSha256": sha256_bytes(tracked_diff),
        "inputs": entries,
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    payload["transformationDigest"] = sha256_bytes(canonical)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
