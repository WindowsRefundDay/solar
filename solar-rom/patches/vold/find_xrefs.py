#!/usr/bin/env python3
"""Find code sites referencing a .rodata string via vold's Thumb-2 PIC idiom:

    ldr rX, [pc, #N]   @ pool_addr   (pool holds a signed delta, NOT the absolute addr)
    add rX, pc                       (rX = delta + (add_instr_addr + 4))

objdump's own comment on the `ldr` line already resolves the pool address correctly
(it accounts for the literal's align-down-to-4 rule). The `add rX, pc` PC value is
just add_instr_addr + 4 -- do NOT align that one down, unlike the ldr literal rule.
Getting that alignment backwards silently lands 2 bytes short of the real target
whenever the add isn't itself 4-aligned (~50% of the time in Thumb).

Usage:
    python3 find_xrefs.py vold_thumb2.asm stock/vold "needle string" [more strings...]
"""
import re
import struct
import sys

LDR_PAT = re.compile(
    r"^\s*([0-9a-f]+):\s+[0-9a-f]+\s+ldr(?:\.w)?\s+(r\d+),\s*\[pc,\s*#(-?0x[0-9a-f]+)\]\s*@\s*0x([0-9a-f]+)"
)
ADD_PAT = re.compile(
    r"^\s*([0-9a-f]+):\s+[0-9a-f]+\s+add(?:\.w)?\s+(r\d+),\s*(?:r\d+,\s*)?pc\b"
)


def find_string_addr(data: bytes, needle: bytes) -> int:
    off = data.find(needle + b"\x00")
    if off == -1:
        raise ValueError(f"string not found: {needle!r}")
    return off


def build_xref_map(asm_path: str, data: bytes) -> dict[int, list[int]]:
    xrefs: dict[int, list[int]] = {}
    pending: dict[str, tuple[int, int]] = {}
    with open(asm_path) as f:
        for line in f:
            m = LDR_PAT.match(line)
            if m:
                ldr_addr = int(m.group(1), 16)
                reg = m.group(2)
                pool_addr = int(m.group(4), 16)
                if pool_addr + 4 <= len(data):
                    delta = struct.unpack_from("<i", data, pool_addr)[0]
                    pending[reg] = (ldr_addr, delta)
                continue
            m2 = ADD_PAT.match(line)
            if m2:
                add_addr = int(m2.group(1), 16)
                reg = m2.group(2)
                if reg in pending:
                    ldr_addr, delta = pending.pop(reg)
                    target = (delta + add_addr + 4) & 0xFFFFFFFF
                    xrefs.setdefault(target, []).append(ldr_addr)
    return xrefs


def main() -> None:
    asm_path, bin_path, *needles = sys.argv[1:]
    data = open(bin_path, "rb").read()
    xrefs = build_xref_map(asm_path, data)
    for needle in needles:
        addr = find_string_addr(data, needle.encode())
        sites = xrefs.get(addr, [])
        print(f"{needle!r} @ 0x{addr:x} <- referenced from: {[hex(s) for s in sites]}")


if __name__ == "__main__":
    main()
