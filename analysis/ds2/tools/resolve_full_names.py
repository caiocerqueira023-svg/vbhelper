"""Resolve Dawn/Dusk full species names against the bundled catalog and list what remains."""
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
FULL = ROOT / "analysis/ds2/ds2_full_names.json"
SHORT = ROOT / "app/src/main/assets/ds2_species.json"


def compact(value):
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {s["id"]: s for s in catalog["species"]}
    by_compact = defaultdict(list)
    for species in catalog["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    full = json.loads(FULL.read_text(encoding="utf-8"))["dawn"]
    short = json.loads(SHORT.read_text(encoding="utf-8"))["names"]

    def resolve(name):
        if not name:
            return None
        hits = sorted(set(by_compact.get(compact(name), [])))
        return hits[0] if len(hits) == 1 else None

    unresolved = []
    special_codes = defaultdict(list)
    for key, name in sorted(full.items(), key=lambda item: int(item[0])):
        species = int(key)
        codes = set(re.findall(r"<0x([0-9a-f]+)>", name))
        if codes:
            special_codes[tuple(sorted(codes))].append((species, name))
        canonical = resolve(name) or resolve(short.get(key))
        if canonical is None:
            unresolved.append((species, name, short.get(key)))
    print(f"unresolved with full names: {len(unresolved)} / {len(full)}")
    for species, name, short_name in unresolved:
        print(f"   {species:#05x} full={name!r} short={short_name!r}")
    print("\nspecial glyph codes:")
    for codes, entries in special_codes.items():
        print(f"   {codes}: {len(entries)} e.g. {entries[:6]}")

    # Print a few names for families the user reported.
    for species in ("292", "307", "341", "388", "439", "373", "423", "472"):
        print(f"   {int(species):#05x}: full={full.get(species)!r} short={short.get(species)!r} "
              f"-> {resolve(full.get(species)) or resolve(short.get(species))}")


if __name__ == "__main__":
    main()
