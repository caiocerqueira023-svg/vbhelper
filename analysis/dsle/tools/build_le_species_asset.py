"""Build the Lost Evolution species asset for the app.

Sources (all in the analysis workspace, generated from the user's own ROM):
  - id-ordered name list in MSG/MESPAK00.PAK (index == species id, validated with the real save)
  - species id table in ARM9 at RAM 0x02154278 (743 x 8: parameter index, name-record index)
  - parameter blocks at RAM 0x021325EC (0x38 bytes: stats, growth bits, skills)
  - name/description records at RAM 0x02129FC8 (0x14 bytes: name message id at +0xE)
"""
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = ROOT / "analysis/dsle/decompressed/arm9.bin"
MESPAK = ROOT / "analysis/dsle/nitrofs/MSG/MESPAK00.PAK"
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
OUT = ROOT / "app/src/main/assets/le_species.json"

RAM = 0x02000000
ID_TABLE = 0x02154278
BASE1 = 0x021325EC
BASE3 = 0x02129FC8
BASE1_COUNT = 0x178
ID_COUNT = 0x2E7
FIRST_SPECIES = 54          # ids 0..53 are DigiEggs
LAST_DIGIMON = 367          # ids 368.. are scripted battle units / characters
LAST_SPECIES = 446          # last name-list Digimon id

# Localization aliases the bundled catalog spells differently. Every entry is an LE name the
# catalog's explicit aliases do not cover; ids were derived from the ROM's stage-ordered species
# table and cross-checked against the catalog (see analysis/dsle/le_alias_map.json).
ALIAS_MAP = {
    # (name -> canonical species id) is loaded from ALIAS_FILE below.
}
ALIAS_FILE = ROOT / "analysis/dsle/le_alias_map.json"
STAGE_BANDS = {
    "inTraining": [54, 76],
    "rookie": [77, 116],
    "champion": [117, 176],
    "ultimate": [177, 300],
    "mega": [301, 367],
}


def compact(value: str) -> str:
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def load_catalog():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {species["id"]: species for species in catalog["species"]}
    by_compact = defaultdict(list)
    for species in catalog["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    return by_id, by_compact


def resolve(name, by_compact):
    if not name:
        return None
    hits = sorted(set(by_compact.get(compact(name), [])))
    return hits[0] if len(hits) == 1 else None


def name_list():
    """Name-like token run from MESPAK00; list index == species id (validated on the real save)."""
    data = MESPAK.read_bytes()
    # The list starts at the first DigiEgg name; earlier bytes hold unrelated message data.
    start = data.find(b"Red DigiEgg\x00")
    if start < 0:
        raise SystemExit("name list anchor not found")
    tokens = []
    at = start
    while at < len(data):
        stop = data.find(b"\x00", at)
        if stop < 0:
            break
        raw = data[at:stop]
        at = stop + 1
        if not raw:
            continue
        try:
            text = raw.decode("ascii")
        except UnicodeDecodeError:
            continue
        tokens.append(text)

    def name_like(text):
        return bool(text) and len(text) <= 20 and all(32 <= ord(c) < 127 for c in text) and \
            all(c.isalnum() or c in " .'-():" for c in text) and any(c.isalpha() for c in text)

    best_start = best_len = 0
    index = 0
    while index < len(tokens):
        if name_like(tokens[index]):
            start = index
            while index < len(tokens) and name_like(tokens[index]):
                index += 1
            if index - start > best_len:
                best_start, best_len = start, index - start
        else:
            index += 1
    return tokens[best_start:best_start + best_len]


def main():
    import json as _json
    data = ARM9.read_bytes()
    alias_names = _json.loads(ALIAS_FILE.read_text(encoding="utf-8")) if ALIAS_FILE.is_file() else {}
    names = name_list()
    by_id, by_compact = load_catalog()
    print(f"name list: {len(names)} tokens")

    def entry(species):
        at = ID_TABLE - RAM + species * 8
        first, second = (int.from_bytes(data[at + i:at + i + 4], "little", signed=True) for i in (0, 4))
        return first, second

    def u16(buffer, at):
        return int.from_bytes(buffer[at:at + 2], "little")

    def s16(buffer, at):
        return int.from_bytes(buffer[at:at + 2], "little", signed=True)

    out_names = {}
    base_data = {}
    unresolved = []
    for species in range(0, LAST_SPECIES + 1):
        name = names[species] if species < len(names) else None
        if not name:
            continue
        out_names[str(species)] = name
        first, second = entry(species)
        if 0 <= first < BASE1_COUNT:
            at = BASE1 - RAM + first * 0x38
            block = data[at:at + 0x38]
            base_data[str(species)] = {
                "lineId": u16(block, 0x00),
                "hp": u16(block, 0x06),
                "mp": u16(block, 0x08),
                "attack": u16(block, 0x0A),
                "defense": u16(block, 0x0C),
                "spirit": u16(block, 0x0E),
                "speed": block[0x11],
                "growthBits": u16(block, 0x12),
                "evolutions": [s16(block, 0x14), s16(block, 0x16), s16(block, 0x18)],
                "skills": [u16(block, 0x1E), u16(block, 0x20), u16(block, 0x22)],
            }
        canonical = resolve(name, by_compact) or alias_names.get(name)
        if canonical is None:
            unresolved.append((species, name))

    print(f"species names: {len(out_names)} (ids 0..{LAST_SPECIES})")
    print(f"real Digimon ids: {FIRST_SPECIES}..{LAST_DIGIMON}")
    print(f"parameter blocks: {len(base_data)}")
    print(f"unresolved catalog names: {len(unresolved)}")
    for species, name in unresolved[:40]:
        print(f"  {species:3d} {name!r}")
    unresolved_real = [(species, name) for species, name in unresolved if species >= FIRST_SPECIES]
    print(f"unresolved Digimon (id >= {FIRST_SPECIES}): {len(unresolved_real)}")

    asset = {
        "gameId": "digimon_story_lost_evolution_nds",
        "formatProfile": "le-jp-en-raw-v1",
        "codecVersion": 1,
        "firstSpecies": FIRST_SPECIES,
        "lastSpecies": LAST_SPECIES,
        "lastDigimon": LAST_DIGIMON,
        "source": {
            "romTitle": "BLEJ_DS3_BEC",
            "romGameCode": "BLEJ",
            "romMd5": "9c3a0795d4b7037eabb5fa522ef43e89",
            "translation": "Operation Decoded T-En v1.1 (AP fix)",
            "nameList": "MSG/MESPAK00.PAK id-ordered token run",
            "idTableAddress": "0x02154278",
            "parameterTableAddress": "0x021325EC",
            "nameRecordTableAddress": "0x02129FC8",
            "evidence": "analysis/dsle and game-save-core/LOST_EVOLUTION_RESEARCH.md",
        },
        "names": out_names,
        "baseData": base_data,
        "stageBands": STAGE_BANDS,
        "canonicalOverrides": {str(species): canonical for species, canonical in
                               ((species, alias_names.get(name)) for species, name in
                                ((s, names[s]) for s in range(len(names))))
                               if canonical},
    }
    OUT.write_text(json.dumps(asset, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\nwrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
