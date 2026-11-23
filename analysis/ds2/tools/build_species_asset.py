"""Build the Dawn/Dusk species asset with full ROM names and canonical-id overrides.

The save record only stores a species id, so identity must come from the ROM name tables. The
record/status name table at 0x02112998 is truncated (`MtlMamemon`), which is why species like Metal
Mamemon or Black WereGarurumon were reported as "not in Dawn/Dusk". ARM9 also holds the packed full
name table (one 0xFFFF-terminated string per species, id order); this tool extracts it, resolves each
name against the bundled catalog, applies the few documented localization aliases, and writes the
app asset.
"""
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ASSET = ROOT / "app/src/main/assets/ds2_species.json"
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
FULL = ROOT / "analysis/ds2/ds2_full_names.json"

# Glyph codes used by the name tables that are not letters.
SPECIAL = {0x0: " ", 0x15: "(", 0x16: ")", 0x19: " "}

# Names the bundled catalog spells differently (localization differences), decided from the ROM
# evolution chains: Rapidmon 0x144 evolves from Gargomon (the Perfect-level Terriermon line), the
# chessmons inherit their colour from KnightChessmon White/Black, and the rest are the documented
# English/Japanese naming pairs.
OVERRIDES = {
    0x90: ("digimon.dotfalcomon", "Dfalcomon is the ROM's short spelling of Dot Falcomon"),
    0xD7: ("digimon.darktyranomon", "DK = Dark; Tyranomon spelling"),
    0xEC: ("digimon.tuchidarumon", "Tsuchidarumon/Tuchidarumon romanization"),
    0x120: ("digimon.volcamon", "Volcanomon/Volcamon localization"),
    0x133: ("digimon.weregarurumonblack", "War=Were; Black variant (evolves from Ogremon line)"),
    0x134: ("digimon.weregarurumon", "War=Were; regular variant (evolves from Garurumon line)"),
    0x144: ("digimon.rapidmonperfect", "Rapidmon of the Gargomon line = catalog's Rapidmon Perfect"),
    0x17B: ("digimon.bishopchessmonwhite", "inherits KnightChessmon White as its predecessor"),
    0x17C: ("digimon.rookchessmonblack", "inherits KnightChessmon Black as its predecessor"),
    0x1C0: ("digimon.slashangemon", "SL Angemon = SlashAngemon"),
    0x1D0: ("digimon.justimonaccelarm", "DS2 has one Justimon; the catalog base form is Accel Arm"),
}


def compact(value):
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def expand(raw):
    return re.sub(r"<(0x[0-9a-f]+)>",
                  lambda match: SPECIAL.get(int(match.group(1), 16), ""), raw).strip()


def main():
    asset = json.loads(ASSET.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {species["id"]: species for species in catalog["species"]}
    by_compact = defaultdict(list)
    for species in catalog["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    full_raw = json.loads(FULL.read_text(encoding="utf-8"))
    dawn = full_raw["dawn"]
    dusk = full_raw["dusk"]

    def resolve(name):
        if not name:
            return None
        hits = sorted(set(by_compact.get(compact(name), [])))
        return hits[0] if len(hits) == 1 else None

    full_names = {}
    overrides = {}
    unresolved = []
    for key in sorted(dawn, key=int):
        species = int(key)
        name = expand(dawn[key])
        if dusk[key] != dawn[key]:
            raise SystemExit(f"titles disagree on species {species:#x}")
        if not name:
            continue
        full_names[key] = name
        canonical = resolve(name)
        if species in OVERRIDES:
            canonical, reason = OVERRIDES[species]
            if canonical not in by_id:
                raise SystemExit(f"override target missing: {canonical}")
            overrides[key] = canonical
        if canonical is None:
            unresolved.append({"speciesId": species, "name": name})

    # Verify the overrides really are the aliases they claim to be.
    for species, (canonical, reason) in OVERRIDES.items():
        entry = by_id[canonical]
        print(f"{species:#05x} {expand(dawn[str(species)])!r} -> {canonical} ({entry['name']}) [{reason}]")

    resolved_count = len([key for key in full_names
                          if overrides.get(key) or resolve(full_names[key])])
    print(f"\nfull names: {len(full_names)}  resolved: {resolved_count}  "
          f"overrides: {len(overrides)}  unresolved: {len(unresolved)}")
    for item in unresolved:
        print(f"   unresolved {item['speciesId']:#05x} {item['name']!r}")

    asset["fullNames"] = full_names
    asset["canonicalOverrides"] = overrides
    asset["canonicalUnresolved"] = unresolved
    asset["canonicalSource"] = {
        "fullNameTable": "ARM9 packed 0xFFFF-terminated name table (first entry 'Chicchimon' = 0x41)",
        "fullNameTableOffsets": {"A3VE": "arm9+0x1075C6", "A6RE": "arm9+0x1076C2"},
        "note": "record/status name table at nameTableAddresses is truncated to ~12 glyphs",
    }
    ASSET.write_text(json.dumps(asset, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\nwrote {ASSET} ({ASSET.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
