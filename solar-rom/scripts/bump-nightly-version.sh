#!/usr/bin/env bash
# Nightly branch: set versionName nightly-{N} + versionCode N in app/build.gradle before commit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
GRADLE="$REPO_ROOT/app/build.gradle"

[ -f "$GRADLE" ] || { echo "missing $GRADLE" >&2; exit 1; }

python3 - "$GRADLE" <<'PY'
import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
name_m = re.search(r'versionName\s+"([^"]+)"', text)
code_m = re.search(r'versionCode\s+(\d+)', text)
if not name_m or not code_m:
    raise SystemExit("could not read versionName/versionCode")

current_name = name_m.group(1)
current_code = int(code_m.group(1))
if current_name.startswith("nightly-"):
    try:
        current_code = max(current_code, int(current_name.split("-", 1)[1]))
    except ValueError:
        pass

next_code = current_code + 1
next_name = f"nightly-{next_code}"

text = re.sub(r'versionName\s+"[^"]+"', f'versionName "{next_name}"', text, count=1)
text = re.sub(r'versionCode\s+\d+', f"versionCode {next_code}", text, count=1)
open(path, "w", encoding="utf-8").write(text)
print(f"bumped nightly version -> {next_name} ({next_code})")
PY
