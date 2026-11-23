"""Dump the current device saves: species ids, stored names, party, and canonical resolution."""
import json
import struct
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
DEVICE = ROOT / "analysis/ds2/device"
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
DS2 = ROOT / "app/src/main/assets/ds2_species.json"
DS1 = ROOT / "app/src/main/assets/world_ds_species.json"

COPIES = [0x0, 0x7B80]
RECORDS = 0x288
STRIDE = 0x164
COUNT = 40


def compact(value):
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def load_catalog():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {s["id"]: s for s in data["species"]}
    by_compact = defaultdict(list)
    for species in data["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    return by_id, by_compact


def resolve(name_or_id, by_id, by_compact):
    if not name_or_id:
        return None
    if name_or_id in by_id:
        return name_or_id
    hits = sorted(set(by_compact.get(compact(name_or_id), [])))
    return hits[0] if len(hits) == 1 else None


def decode_name(record):
    codes = struct.unpack_from("<9H", record, 0x08)
    text = []
    for code in codes:
        if code == 0xFFFF:
            break
        if 0x20 <= code <= 0x39:
            text.append(chr(ord("A") + code - 0x20))
        elif 0x3A <= code <= 0x53:
            text.append(chr(ord("a") + code - 0x3A))
        else:
            text.append("?")
    return "".join(text)


def dump(path, names, is_ds2, by_id, by_compact):
    data = Path(path).read_bytes()
    print(f"\n=== {Path(path).name}")
    for copy_index, base in enumerate(COPIES, start=1):
        if base + 0x7B2C > len(data):
            continue
        if all(b == 0xFF for b in data[base:base + 0x7B2C]):
            print(f"  copy{copy_index}: erased")
            continue
        seq = struct.unpack_from("<I", data, base + 0x18)[0]
        party = [struct.unpack_from("<I", data, base + 0x260 + i * 4)[0] for i in range(10)]
        party = [value for value in party if value]
        print(f"  copy{copy_index}: seq={seq} lastId={struct.unpack_from('<I', data, base + 0x25C)[0]:#x} "
              f"party={[hex(value) for value in party]}")
        for slot in range(COUNT):
            at = base + RECORDS + slot * STRIDE
            local_id = struct.unpack_from("<I", data, at)[0]
            species = struct.unpack_from("<i", data, at + 4)[0]
            if local_id == 0 and species < 0:
                continue
            stored = decode_name(data[at:at + STRIDE])
            table_name = names.get(species) if is_ds2 else (names[species - 0x41] if 0x41 <= species <= 0x1EA else None)
            canonical = resolve(table_name, by_id, by_compact)
            canonical_stored = resolve(stored, by_id, by_compact)
            level = struct.unpack_from("<H", data, at + 0x76)[0]
            print(f"    slot{slot}: id={local_id:#x} species={species:#x} table={table_name!r} "
                  f"stored={stored!r} canonical={canonical} stored_canonical={canonical_stored} lv={level}")


def main():
    by_id, by_compact = load_catalog()
    ds2_names = {int(key): value for key, value in json.loads(DS2.read_text(encoding="utf-8"))["names"].items()}
    ds1_names = json.loads(DS1.read_text(encoding="utf-8"))["names"]
    dump(DEVICE / "dawn-now.sav", ds2_names, True, by_id, by_compact)
    dump(DEVICE / "dusk-now.sav", ds2_names, True, by_id, by_compact)
    dump(DEVICE / "worldds-now.sav", ds1_names, False, by_id, by_compact)


if __name__ == "__main__":
    main()
