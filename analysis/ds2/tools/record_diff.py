"""Side-by-side raw record dump across the supplied saves.

Prints every occupied party slot as a full annotated hex grid, and marks offsets whose value
differs between records (field candidates) versus constant (structural constants).
"""
import struct
import sys
from pathlib import Path

SLOTS_BASE, SLOT_SIZE, SLOT_COUNT = 0x288, 0x164, 7


def slots(d):
    out = []
    for i in range(SLOT_COUNT):
        base = SLOTS_BASE + i * SLOT_SIZE
        rec = d[base:base + SLOT_SIZE]
        if any(rec):
            out.append((i, base, rec))
    return out


def hexgrid(rec, start, end):
    lines = []
    for off in range(start, end, 16):
        chunk = rec[off:min(off + 16, end)]
        lines.append(f"{off:#05x}  " + " ".join(f"{b:02x}" for b in chunk).ljust(47))
    return lines


def main(paths):
    for path in paths:
        d = Path(path).read_bytes()
        print(f"##### {Path(path).name}")
        for slot, base, rec in slots(d):
            print(f"-- slot {slot} @ {base:#06x} id={struct.unpack_from('<I', rec, 0)[0]:#010x} "
                  f"species={struct.unpack_from('<I', rec, 4)[0]}")
            for line in hexgrid(rec, 0x000, SLOT_SIZE):
                print("   " + line)
        # diff between first two occupied slots
        rows = slots(d)
        if len(rows) >= 2:
            a, b = rows[0][2], rows[1][2]
            diffs = [off for off in range(min(len(a), len(b))) if a[off] != b[off]]
            runs = []
            for off in diffs:
                if runs and off == runs[-1][1] + 1:
                    runs[-1][1] = off
                else:
                    runs.append([off, off])
            print("   differing ranges slot0/slot1: " +
                  ", ".join(f"{s:#05x}-{e:#05x}" for s, e in runs))
            print("   constant ranges: " + str(SLOT_SIZE - len(diffs)) + " bytes equal")


if __name__ == "__main__":
    main(sys.argv[1:])
