"""Extracts the Digimon World DS (USA) species name tables from the ROM into the app asset.

The ROM is never shipped or copied: this tool reads the user's own file, decodes the two
name tables at the addresses the decompilation marks (getSpeciesName -> data_0211f4e0,
getSpeciesShortName -> data_0211a688), and writes app/src/main/assets/world_ds_species.json.
Every entry of both tables must decode with the published USA text codes already mirrored in
game-save-core WorldDsText; the tool refuses any ROM whose SHA-1 is not the reference
dwds_usa.nds the decompilation targets.

Usage: py tools/extract_world_ds_species.py "path/to/Digimon World DS (USA).nds"
"""

import hashlib
import json
import struct
import sys
from pathlib import Path

REFERENCE_SHA1 = "225ffc25aaf1ab4006fcdce5896d411a8fee870f"  # dwds_decomp dwds_usa.sha1
REFERENCE_CODE = "ADNE"
FULL_TABLE = 0x0211F4E0
SHORT_TABLE = 0x0211A688
SPECIES_COUNT = 426
DEFAULT_OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/world_ds_species.json"


def decode_name(code):
    if 0x50 <= code <= 0x69:
        return chr(ord("A") + code - 0x50)
    if 0x6A <= code <= 0x83:
        return chr(ord("a") + code - 0x6A)
    if 1 <= code <= 10:
        return chr(ord("0") + code - 1)
    simple = {0: " ", 0x0B: "/", 0x0C: "%", 0x0D: ":", 0x0E: "'", 0x0F: '"', 0x11: ",",
              0x12: ".", 0x13: "!", 0x14: "?", 0x15: "(", 0x16: ")", 0x17: "+", 0x18: "-",
              0x19: "=", 0x1B: "~", 0x23: "&"}
    return simple.get(code)


def u16(data, at):
    return struct.unpack_from("<H", data, at)[0]


def s16(data, at):
    return struct.unpack_from("<h", data, at)[0]


def u32(data, at):
    return struct.unpack_from("<I", data, at)[0]


def nitro_dir(data, sub_id):
    """Yields (name, is_dir, id_or_file_number) for one NitroFS directory."""
    fnt = u32(data, 0x40)
    # NitroFS indexes the main table from the start of FNT itself: root 0xF000
    # reads the directory offset at FNT+0, not at FNT+8.
    entry = fnt + (sub_id & 0xFFF) * 8
    pos = fnt + u32(data, entry)
    file_no = u16(data, entry + 4)
    while True:
        length = data[pos]
        if length == 0:
            return
        is_dir = (length & 0x80) != 0
        name_len = length & 0x7F
        name = data[pos + 1:pos + 1 + name_len].decode("ascii")
        pos += 1 + name_len
        if is_dir:
            child = u16(data, pos)
            pos += 2
            yield name, True, child
        else:
            yield name, False, file_no
            file_no += 1


def child_dir(data, parent_id, name):
    """Returns the NitroFS directory id for a named child, or None."""
    return next((entry_id for entry_name, is_dir, entry_id in nitro_dir(data, parent_id)
                 if is_dir and entry_name == name), None)


def file_bytes(data, fat, file_no):
    start = u32(data, fat + file_no * 8)
    end = u32(data, fat + file_no * 8 + 4)
    return data[start:end]


def parse_species_records(data):
    """Reads /dat/dm's 60-byte SpeciesData records.

    func_020a29c4 (addDigimon) copies these fields when it creates a record:
    dm[0x03] to unk1A, dm[0x12] to unk50, dm[0x16] to history.unk50,
    dm[0x27] to ability 0, dm[0x2C..0x33] to skills, dm[0x18..0x25] to
    history resistances.
    """
    fat = u32(data, 0x48)
    dat_id = child_dir(data, 0xF000, "dat")
    dm_id = child_dir(data, dat_id, "dm") if dat_id is not None else None
    if dm_id is None:
        raise SystemExit("NitroFS has no dat/dm species directory")
    records = {}
    for name, is_dir, file_no in nitro_dir(data, dm_id):
        if is_dir or not name.isdigit():
            continue
        raw = file_bytes(data, fat, file_no)
        try:
            offsets = struct.unpack_from("<8I", raw)
        except struct.error:
            raise SystemExit("dm/%s is too small for its offset table" % name)
        for offset in offsets:
            if offset in (0, 0xFFFFFFFF) or offset + 0x3C > len(raw):
                continue
            species = u16(raw, offset)
            if species >= SPECIES_COUNT:
                continue
            if species in records:
                raise SystemExit("species %d appears twice in dm" % species)
            records[species] = [
                raw[offset + 0x03],
                s16(raw, offset + 0x12),
                raw[offset + 0x27],
                *[u16(raw, offset + 0x2C + index * 2) for index in range(4)],
                *[s16(raw, offset + 0x18 + index * 2) for index in range(7)],
                s16(raw, offset + 0x16),
            ]
    missing = [species for species in range(SPECIES_COUNT) if species not in records]
    if missing:
        raise SystemExit("dm is missing species ids: %s" % missing[:8])
    return [records[species] for species in range(SPECIES_COUNT)]


def parse_predecessors(data):
    """Reads /dat/sk's per-species previous form (record +0x54).

    The game's func_020a137c loads sk/<species / 8> once and indexes record
    <species % 8>; every file has eight 0x58-byte records behind an 8-entry
    offset table. The species id is the slot number, not a field in the record.
    """
    fat = u32(data, 0x48)
    dat_id = child_dir(data, 0xF000, "dat")
    sk_id = child_dir(data, dat_id, "sk") if dat_id is not None else None
    if sk_id is None:
        raise SystemExit("NitroFS has no dat/sk species directory")
    previous = [-1] * SPECIES_COUNT
    seen = set()
    for name, is_dir, file_no in nitro_dir(data, sk_id):
        if is_dir or not name.isdigit():
            continue
        raw = file_bytes(data, fat, file_no)
        group = int(name)
        if group * 8 >= SPECIES_COUNT:
            continue
        try:
            offsets = struct.unpack_from("<8I", raw)
        except struct.error:
            raise SystemExit("sk/%s is too small for its offset table" % name)
        for slot, offset in enumerate(offsets):
            species = group * 8 + slot
            if species >= SPECIES_COUNT or offset in (0, 0xFFFFFFFF):
                continue
            if offset + 0x58 > len(raw):
                raise SystemExit("sk/%s record %d exceeds the file" % (name, slot))
            if species in seen:
                raise SystemExit("species %d appears twice in sk" % species)
            previous[species] = struct.unpack_from("<i", raw, offset + 0x54)[0]
            seen.add(species)
    missing = [species for species in range(SPECIES_COUNT) if species not in seen]
    if missing:
        raise SystemExit("sk is missing species ids: %s" % missing[:8])
    if any(value != -1 and not 0 <= value < SPECIES_COUNT for value in previous):
        raise SystemExit("sk has an out-of-range previous species")
    if previous[29] != 8 or previous[38] != 3 or previous[128] != 38 or previous[313] != 207:
        raise SystemExit("sk previous-species chain does not match the known USA ROM")
    return previous


def parse_table(arm9, ram_base, address):
    base = address - ram_base
    offsets = struct.unpack_from("<%dI" % SPECIES_COUNT, arm9, base)
    if offsets[0] != SPECIES_COUNT * 4:
        raise SystemExit("table at 0x%X does not start its strings after %d offsets" % (address, SPECIES_COUNT))
    names = []
    for index, offset in enumerate(offsets):
        at = base + offset
        chars = []
        while True:
            word = struct.unpack_from("<H", arm9, at)[0]
            if word == 0xFFFF:
                break
            char = decode_name(word)
            if char is None:
                raise SystemExit("species %d has undecodable code 0x%04X" % (index, word))
            chars.append(char)
            at += 2
        names.append("".join(chars))
    return names


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    rom_path = Path(sys.argv[1])
    data = rom_path.read_bytes()
    digest = hashlib.sha1(data).hexdigest()
    if digest != REFERENCE_SHA1:
        raise SystemExit("ROM SHA-1 %s is not the reference dwds_usa.nds (%s)" % (digest, REFERENCE_SHA1))
    code = data[0x0C:0x10].decode("ascii", "replace")
    if code != REFERENCE_CODE:
        raise SystemExit("game code %r is not %s" % (code, REFERENCE_CODE))
    arm9_rom, = struct.unpack_from("<I", data, 0x20)
    arm9_ram, = struct.unpack_from("<I", data, 0x28)
    arm9_size, = struct.unpack_from("<I", data, 0x2C)
    arm9 = data[arm9_rom:arm9_rom + arm9_size]

    names = parse_table(arm9, arm9_ram, FULL_TABLE)
    short_names = parse_table(arm9, arm9_ram, SHORT_TABLE)
    record_defaults = parse_species_records(data)
    previous_species = parse_predecessors(data)
    # Duplicate names are the game's own variant and boss entries sharing one species
    # name; empty names are unused slots. Both exist by design and stay explicit.
    for index, (full, short) in enumerate(zip(names, short_names)):
        if len(short) > len(full):
            raise SystemExit("species %d short name %r does not shorten %r" % (index, short, full))
    if names[0] != "Koromon" or names[-6:] != ["Pile Egg", "Omega Egg", "Baby Egg", "Hero Egg", "Angel Digi-Egg", "Saber Egg"]:
        raise SystemExit("unexpected first or last species names; wrong table?")
    filled = sum(1 for name in names if name)
    duplicates = len(names) - len(set(names))
    asset = {
        "gameId": "digimon_world_ds_nds",
        "formatProfile": "world-ds-usa-drastic-raw-v1",
        "source": {
            "romTitle": "DIGIMONWORLD",
            "romGameCode": REFERENCE_CODE,
            "romSha1": REFERENCE_SHA1,
            "fullNameTableAddress": "0x%08X" % FULL_TABLE,
            "shortNameTableAddress": "0x%08X" % SHORT_TABLE,
            "decompilation": "ReGame-Labs/dwds_decomp @ 91efd17c4c05ce106ce0f973155149d23d9de519",
        },
        "speciesCount": SPECIES_COUNT,
        "names": names,
        "shortNames": short_names,
        "recordDefaults": record_defaults,
        "previousSpecies": previous_species,
    }
    DEFAULT_OUT.parent.mkdir(parents=True, exist_ok=True)
    DEFAULT_OUT.write_text(json.dumps(asset, separators=(",", ":")) + "\n", encoding="utf-8")
    print("wrote %s: %d names (%d named, %d name variants, %d unused)" % (
        DEFAULT_OUT, len(names), filled, duplicates, len(names) - filled))


if __name__ == "__main__":
    main()
