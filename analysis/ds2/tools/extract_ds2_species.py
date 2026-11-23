"""Extract Digimon World Dawn/Dusk species data from a USA ROM into an app asset.

Sources (derived from the user's own ROM; only names/values are written, never ROM bytes):
  ARM9 u32 offset table at 0x02112998 -> 0xFFFF-terminated 2-byte glyph names, indexed by species id
  NitroFS /dat/dm/<n>  -> 8 u32 offsets + species base-data records (fields per FUN_02030d44)
  NitroFS /dat/sk/<n>  -> 8 u32 offsets + lineage records whose first s32 is the previous species

Layout facts come from the ARM9 decompilation recorded in analysis/ds2/DAWN_DUSK_SAVE_LAYOUT.md.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

NAME_TABLES = {
    "A3VE": 0x02112998,
    "A6RE": 0x02112A94,
}
SPECIES_FIRST = 0x41
SPECIES_LAST = 0x1F4
REFERENCE = {
    "A3VE": ("digimon_world_dawn_nds", "61a532b01d97392bb2a2d68955486d03"),
    "A6RE": ("digimon_world_dusk_nds", "35b6209cf0a69b68a5f78c07d9433c27"),
}

GLYPHS = {0x00: "\u0020"}
for _i in range(10):
    GLYPHS[0x01 + _i] = str(_i)
GLYPHS.update({
    0x0B: "/", 0x0C: "%", 0x0D: ":", 0x0E: "'", 0x0F: "\"", 0x10: "\u00b7", 0x11: ",",
    0x12: ".", 0x13: "!", 0x14: "?", 0x15: "(", 0x16: ")", 0x17: "+", 0x18: "-", 0x19: "=",
    0x54: "*", 0x55: "\u00d7", 0x56: "\u208b", 0x57: "\u2192", 0x58: "\u2190", 0x59: "\u2191",
    0x5A: "\u2193", 0x5B: "&", 0x5C: "\u25a1", 0x5D: "\u00a4", 0x5E: "\u00a7", 0x5F: "\u25ac",
    0x60: "\u03b1", 0x61: "\u03b2", 0x62: "\u03b3", 0x63: "\u03b5", 0x64: "\u03b4", 0x65: "\u221e",
    0x66: "\u2665", 0x67: "\u266a", 0x68: "\u2605", 0x69: "#", 0x6A: ";", 0x6B: "$",
})
for _i in range(26):
    GLYPHS[0x20 + _i] = chr(ord("A") + _i)
    GLYPHS[0x3A + _i] = chr(ord("a") + _i)
for _i, _ch in enumerate(
    "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf"
    "\u0110\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00a1\u00bf"
    "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef"
    "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd"
):
    GLYPHS[0x80 + _i] = _ch


def u16(d, at):
    return struct.unpack_from("<H", d, at)[0]


def s16(d, at):
    return struct.unpack_from("<h", d, at)[0]


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


def decode_name(d, at):
    out = []
    for i in range(24):
        code = u16(d, at + i * 2)
        if code == 0xFFFF:
            break
        out.append(GLYPHS.get(code, "\ufffd"))
    return "".join(out)


class Rom:
    def __init__(self, path):
        self.data = Path(path).read_bytes()
        self.code = self.data[0x0C:0x10].decode("ascii", "replace")
        self.title = self.data[0x00:0x0C].decode("ascii", "replace").rstrip("\x00")
        self.arm9_offset = u32(self.data, 0x20)
        self.arm9_ram = u32(self.data, 0x28)
        self.fnt = u32(self.data, 0x40)
        self.fat = u32(self.data, 0x48)
        self.fat_size = u32(self.data, 0x4C)
        self.files = [struct.unpack_from("<II", self.data, self.fat + i * 8)
                      for i in range(self.fat_size // 8)]

    def arm9_read(self, va, size):
        off = self.arm9_offset + (va - self.arm9_ram)
        return self.data[off:off + size]

    def walk(self):
        data, fnt = self.data, self.fnt
        found = {}

        def recurse(sub_id, prefix, depth):
            if depth > 8:
                return
            entry = fnt + (sub_id & 0xFFF) * 8
            pos = fnt + u32(data, entry)
            file_no = u16(data, entry + 4)
            while True:
                length = data[pos]
                if length == 0:
                    return
                is_dir = (length & 0x80) != 0
                name_len = length & 0x7F
                name = data[pos + 1:pos + 1 + name_len].decode("ascii", "replace")
                pos += 1 + name_len
                if is_dir:
                    child = u16(data, pos)
                    pos += 2
                    recurse(child, prefix + name + "/", depth + 1)
                else:
                    start, end = self.files[file_no] if file_no < len(self.files) else (0, 0)
                    found[prefix + name] = data[start:end]
                    file_no += 1

        recurse(0xF000, "/", 0)
        return found


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: extract_ds2_species.py <rom.nds> <out.json>")
    rom = Rom(sys.argv[1])
    digest = hashlib.md5(rom.data).hexdigest()
    expected = REFERENCE.get(rom.code)
    if expected is None or expected[1] != digest:
        raise SystemExit(f"unexpected ROM: code={rom.code} md5={digest}")

    full_table = NAME_TABLES[rom.code]
    files = rom.walk()
    name_table = rom.arm9_read(full_table, (SPECIES_LAST + 1) * 4)
    names = {}
    for species in range(SPECIES_FIRST, SPECIES_LAST + 1):
        offset = u32(name_table, species * 4)
        names[species] = decode_name(rom.arm9_read(full_table + offset, 48), 0)

    base = {}
    for group_name, blob in files.items():
        leaf = group_name.rsplit("/", 1)[-1]
        if not group_name.startswith("/dat/dm/") or not leaf.isdigit():
            continue
        group = int(leaf)
        offsets = [u32(blob, i * 4) for i in range(8)]
        for slot, offset in enumerate(offsets):
            if offset in (0, 0xFFFFFFFF) or offset + 0x40 > len(blob):
                continue
            species = group * 8 + slot
            if not (SPECIES_FIRST <= species <= SPECIES_LAST):
                continue
            rec = blob[offset:offset + 0x40]
            base[species] = {
                "id": u16(rec, 0x00),
                "level": rec[0x02],
                "unk1A": rec[0x03],
                "hp": u32(rec, 0x04),
                "mp": u16(rec, 0x08),
                "attack": u16(rec, 0x0A),
                "defense": u16(rec, 0x0C),
                "spirit": u16(rec, 0x0E),
                "speed": u16(rec, 0x10),
                "unk50": u16(rec, 0x12),
                "maxLevel": rec[0x14],
                "resistances": [s16(rec, 0x16 + i * 2) for i in range(8)],
                "unk27": rec[0x27],
                "abilities": [rec[0x28 + i] for i in range(4)],
                "growth": rec[0x2D],
                "skills": [u16(rec, 0x30 + i * 2) for i in range(4)],
                "expCurve": rec[0x3C],
            }

    previous = {}
    for group_name, blob in files.items():
        leaf = group_name.rsplit("/", 1)[-1]
        if not group_name.startswith("/dat/sk/") or not leaf.isdigit():
            continue
        group = int(leaf)
        offsets = [u32(blob, i * 4) for i in range(8)]
        for slot, offset in enumerate(offsets):
            if offset in (0, 0xFFFFFFFF) or offset + 4 > len(blob):
                continue
            species = group * 8 + slot
            if SPECIES_FIRST <= species <= SPECIES_LAST:
                previous[species] = struct.unpack_from("<i", blob, offset)[0]

    missing_names = [s for s in range(SPECIES_FIRST, SPECIES_LAST + 1) if not names.get(s)]
    asset = {
        "gameId": expected[0],
        "source": {
            "romTitle": rom.title,
            "romGameCode": rom.code,
            "romMd5": digest,
            "nameTableAddress": "0x%08X" % full_table,
        },
        "speciesRange": [SPECIES_FIRST, SPECIES_LAST],
        "names": {str(k): names[k] for k in sorted(names)},
        "baseData": {str(k): base[k] for k in sorted(base)},
        "previousSpecies": {str(k): previous[k] for k in sorted(previous)},
    }
    Path(sys.argv[2]).write_text(json.dumps(asset, separators=(",", ":"), ensure_ascii=False) + "\n",
                                 encoding="utf-8")
    print(f"wrote {sys.argv[2]}: names={len(names)} missingNames={len(missing_names)} "
          f"base={len(base)} lineage={len(previous)}")
    sample = ", ".join(f"{hex(s)}={names[s]}" for s in (0x41, 0x9C, 0xE2, 0x14B) if s in names)
    print("sample:", sample.encode("ascii", "backslashreplace").decode())


if __name__ == "__main__":
    main()
