"""Validate the derived DS2 layout against the supplied real saves (offline harness)."""
import json
import struct
import sys
from pathlib import Path

SAVE_DIR = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE")
DAWN_SPECIES = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn\species_dawn.json")

SAVE_BYTES = 0x7B2C
COPY_STRIDE = 0x7B80
RECORDS_BASE = 0x288
RECORD_STRIDE = 0x164
RECORD_COUNT = 40
PARTY_BASE = 0x260
PARTY_SLOTS = 10
LAST_ID = 0x25C

GLYPHS = json.loads(DAWN_SPECIES.read_text(encoding="utf-8"))["names"]


def u16(d, at):
    return struct.unpack_from("<H", d, at)[0]


def s16(d, at):
    return struct.unpack_from("<h", d, at)[0]


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


GLYPH = {0x00: " "}
for i in range(10):
    GLYPH[0x01 + i] = str(i)
GLYPH.update({0x0B: "/", 0x0C: "%", 0x0D: ":", 0x0E: "'", 0x0F: '"', 0x10: ".", 0x11: ",", 0x12: ".",
              0x13: "!", 0x14: "?", 0x15: "(", 0x16: ")", 0x17: "+", 0x18: "-", 0x19: "="})
for i in range(26):
    GLYPH[0x20 + i] = chr(ord("A") + i)
    GLYPH[0x3A + i] = chr(ord("a") + i)


def name_of(record):
    out = []
    for i in range(9):
        code = u16(record, 0x08 + i * 2)
        if code == 0xFFFF:
            break
        out.append(GLYPH.get(code, "?"))
    return "".join(out)


def inspect(path):
    d = Path(path).read_bytes()
    print("=====", Path(path).name)
    last_id = u32(d, LAST_ID)
    party = [u32(d, PARTY_BASE + i * 4) for i in range(PARTY_SLOTS)]
    print(f"lastId={last_id:#010x} party={[hex(x) for x in party if x]}")
    for slot in range(RECORD_COUNT):
        base = RECORDS_BASE + slot * RECORD_STRIDE
        rec = d[base:base + RECORD_STRIDE]
        if u32(rec, 0) == 0:
            continue
        species = u32(rec, 4)
        species_name = GLYPHS.get(str(species), "?")
        hp, max_hp = u32(rec, 0x3C), u32(rec, 0x40)
        mp, max_mp = s16(rec, 0x44), s16(rec, 0x46)
        atk, dfn, spi, spd = (s16(rec, 0x48), s16(rec, 0x4A), s16(rec, 0x4C), s16(rec, 0x4E))
        level = s16(rec, 0x76)
        max_level = s16(rec, 0x52)
        stage = rec[0x1E]
        in_party = u32(rec, 0) in party
        exp = [u32(rec, 0x78 + i * 4) for i in range(9)]
        forms = [struct.unpack_from("<i", rec, 0xB4 + i * 4)[0] for i in range(5)]
        flags120 = u16(rec, 0x120)
        print(f"slot {slot:2d} id={u32(rec, 0):#010x} species={species} ({species_name}) "
              f"name={name_of(rec)!r} lv={level} stage={stage} maxLv={max_level} party={in_party}")
        print(f"        hp={hp}/{max_hp} mp={mp}/{max_mp} atk={atk} def={dfn} spi={spi} spd={spd} "
              f"flags120={flags120:#06x} forms={ [hex(f) for f in forms] } expNonZero={[i for i, v in enumerate(exp) if v]}")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        inspect(arg)
