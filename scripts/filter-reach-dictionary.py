#!/usr/bin/env python3
"""Filter an English word list to 3-5 letter lowercase words for Reach friend codes."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "app/src/main/assets/reach-dictionary.txt"
WORD_RE = re.compile(r"^[a-z]{3,5}$")


def filter_words(paths):
    words = set()
    for path in paths:
        p = Path(path)
        if not p.is_file():
            print(f"skip missing: {p}", file=sys.stderr)
            continue
        with p.open(encoding="utf-8", errors="ignore") as f:
            for line in f:
                w = line.strip().lower()
                if WORD_RE.fullmatch(w):
                    words.add(w)
    return sorted(words)


def main():
    args = sys.argv[1:]
    out = DEFAULT_OUT
    sources = []
    i = 0
    while i < len(args):
        if args[i] == "-o" and i + 1 < len(args):
            out = Path(args[i + 1])
            i += 2
        else:
            sources.append(args[i])
            i += 1
    if not sources:
        sources = ["/usr/share/dict/words", "/usr/share/dict/american-english"]
    words = filter_words(sources)
    if len(words) < 200:
        print("ERROR: too few words; pass a word list path", file=sys.stderr)
        sys.exit(1)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(words) + "\n", encoding="utf-8")
    print(f"Wrote {len(words)} words to {out}")


if __name__ == "__main__":
    main()
