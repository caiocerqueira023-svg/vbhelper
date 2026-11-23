"""Exploratory decoder for the supplied Dawn/Dusk saves.

Uses the published DS2 2-byte glyph table (RomEditor strings.py at the pinned commit) to decode
player and Digimon names, then dumps the documented party-slot region so record windows can be
located empirically. Read-only; prints only first-party bytes for validation.
"""
import struct
import sys
from pathlib import Path

COPY = 0x7B2C
SLOTS_BASE, SLOT_SIZE, SLOT_COUNT = 0x288, 0x164, 7
PLAYER_NAME = 0x238

# DS2 glyph table (subset: printable ASCII + space + terminator), from the pinned RomEditor.
_CODES = {0x00: " "}
for i, ch in enumerate("0123456789"):
    _CODES[0x01 + i] = ch
for i in range(0x0B, 0x1A):
    pass
_CODE_SIMPLE = {
    0x0B: "/", 0x0C: "%", 0x0D: ":", 0x0E: "'", 0x0F: '"', 0x10: "\u00b7", 0x11: ",",
    0x12: ".", 0x13: "!", 0x14: "?", 0x15: "(", 0x16: ")", 0x17: "+", 0x18: "-", 0x19: "=",
    0x54: "*", 0x5B: "&", 0x69: "#", 0x6A: ";", 0x6B: "$",
}
_CODES.update(_CODE_SIMPLE)
for i in range(26):
    _CODES[0x20 + i] = chr(ord("A") + i)
for i in range(26):
    _CODES[0x3A + i] = chr(ord("a") + i)


def decode_codes(codes):
    out = []
    for code in codes:
        if code == 0xFFFF:
            break
        out.append(_CODES.get(code, "?"))
    return "".join(out)


def u16(d, at):
    return struct.unpack_from("<H", d, at)[0]


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


def s16(d, at):
    return struct.unpack_from("<h", d, at)[0]


def read_string(d, at, max_codes=12):
    codes = []
    for i in range(max_codes):
        code = u16(d, at + i * 2)
        codes.append(code)
        if code == 0xFFFF:
            break
    return decode_codes(codes), codes


def main(path):
    d = Path(path).read_bytes()
    print(f"=== {Path(path).name} ===")
    name, codes = read_string(d, PLAYER_NAME, 8)
    print(f"player_name@0x238 = {name!r} codes={[hex(c) for c in codes]}")
    print(f"money@0x244 = {u32(d, 0x244)}  tamer_rank@0x3A28 = {u32(d, 0x3A28)}  "
          f"tamer_points@0x3A2C = {u32(d, 0x3A2C)}")
    for slot in range(SLOT_COUNT):
        base = SLOTS_BASE + slot * SLOT_SIZE
        rec = d[base:base + SLOT_SIZE]
        live = any(rec)
        print(f"-- slot {slot} @ {base:#06x} all_zero={not live}")
        if not live:
            continue
        # Try to locate a name: run of 2-byte codes ending in 0xFFFF at even offsets.
        found = []
        for off in range(0, SLOT_SIZE - 24, 2):
            codes = []
            ok = True
            for i in range(10):
                code = u16(rec, off + i * 2)
                if code == 0xFFFF:
                    break
                if code not in _CODES:
                    ok = False
                    break
                codes.append(code)
            if ok and codes and len(codes) <= 9:
                found.append((off, decode_codes(codes + [0xFFFF])))
        print(f"   name_candidates={found[:6]}")
        # Candidate species ids (u16) in documented range 0x41..0x1F4.
        sp16 = [(o, u16(rec, o)) for o in range(0, 0x40, 2) if 0x41 <= u16(rec, o) <= 0x1F4]
        sp32 = [(o, u32(rec, o)) for o in range(0, 0x40, 4) if 0x41 <= u32(rec, o) <= 0x1F4]
        print(f"   u16 in species range: {[(hex(o), hex(v)) for o, v in sp16[:8]]}")
        print(f"   u32 in species range: {[(hex(o), hex(v)) for o, v in sp32[:8]]}")
        for off in range(0, 0x60, 0x10):
            print(f"   {off:#04x}: " + " ".join(f"{b:02x}" for b in rec[off:off + 0x10]))


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        main(arg)
