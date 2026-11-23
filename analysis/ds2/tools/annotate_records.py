"""Compare the live party records of the supplied saves against the documented DS1 struct.

Prints an annotated dump of each occupied slot so DS2 field offsets can be confirmed or moved.
"""
import struct
import sys
from pathlib import Path

SLOTS_BASE, SLOT_SIZE, SLOT_COUNT = 0x288, 0x164, 7

DS1_FIELDS = [
    (0x000, 4, "id"), (0x004, 4, "species"), (0x008, 18, "name"), (0x01A, 1, "unk1A"),
    (0x01B, 1, "expCurve"), (0x01C, 1, "growth"), (0x01D, 1, "unk1D"), (0x01E, 1, "stage"),
    (0x01F, 1, "eggKind"), (0x020, 2, "eggSize"), (0x022, 8, "abilities"), (0x02A, 2, "unk2A"),
    (0x02C, 16, "abilitySources"), (0x03C, 4, "hp"), (0x040, 4, "maxHp"), (0x044, 2, "mp"),
    (0x046, 2, "maxMp"), (0x048, 2, "atk"), (0x04A, 2, "def"), (0x04C, 2, "spi"),
    (0x04E, 2, "spd"), (0x050, 2, "unk50"), (0x052, 2, "maxLevel"), (0x054, 14, "skills"),
    (0x062, 14, "unk62"), (0x070, 2, "unk70"), (0x072, 2, "unk72"), (0x074, 2, "level"),
    (0x076, 2, "unk76"), (0x078, 32, "exp[8]"), (0x098, 4, "unk98"), (0x09C, 4, "expToNext"),
    (0x0A0, 0x60, "history"), (0x100, 2, "unk100"), (0x102, 2, "unk102"), (0x104, 4, "flags"),
    (0x108, 2, "unk108"), (0x10A, 2, "unk10A"), (0x10C, 4, "unk10C"), (0x110, 4, "homeId"),
    (0x114, 20, "parents"), (0x128, 40, "grandparents"), (0x150, 12, "tail"),
]


def u16(d, at):
    return struct.unpack_from("<H", d, at)[0]


def s16(d, at):
    return struct.unpack_from("<h", d, at)[0]


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


def main(path):
    d = Path(path).read_bytes()
    print(f"=== {Path(path).name}")
    for slot in range(SLOT_COUNT):
        base = SLOTS_BASE + slot * SLOT_SIZE
        rec = d[base:base + SLOT_SIZE]
        if not any(rec):
            continue
        print(f"-- slot {slot} @ {base:#06x}")
        for offset, size, label in DS1_FIELDS:
            if offset + size > SLOT_SIZE:
                continue
            raw = rec[offset:offset + size]
            if size == 1:
                value = f"{raw[0]:#04x} ({raw[0]})"
            elif size == 2:
                value = f"u16={u16(rec, offset):#06x} s16={s16(rec, offset)}"
            elif size == 4:
                value = f"u32={u32(rec, offset):#010x} s32={struct.unpack_from('<i', rec, offset)[0]}"
            elif size == 18:
                codes = [u16(rec, offset + i * 2) for i in range(9)]
                value = "codes=" + ",".join(f"{c:04x}" for c in codes)
            elif size == 32:
                value = " ".join(str(struct.unpack_from("<i", rec, offset + i * 4)[0]) for i in range(8))
            else:
                value = " ".join(f"{b:02x}" for b in raw[:24]) + ("..." if size > 24 else "")
            print(f"   {offset:#05x} {label:<16} {value}")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        main(arg)
