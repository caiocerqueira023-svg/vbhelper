"""Suggest catalog matches for the Lost Evolution abbreviated species names."""
import difflib
import json
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ASSET = ROOT / "app/src/main/assets/le_species.json"
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"


def compact(value):
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def main():
    asset = json.loads(ASSET.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    canonical = [(s["id"], s["name"]) for s in catalog["species"]]
    exact = defaultdict(list)
    for species_id, name in canonical:
        exact[compact(name)].append(species_id)
    unresolved = []
    for key, name in asset["names"].items():
        species = int(key)
        if species < 54 or species > 367:
            continue
        if not exact.get(compact(name)):
            unresolved.append((species, name))
    print(f"unresolved Digimon: {len(unresolved)}")
    compact_names = [(species_id, name, compact(name)) for species_id, name in canonical]
    for species, name in unresolved:
        key = compact(name)
        scored = difflib.get_close_matches(key, [entry[2] for entry in compact_names], n=4, cutoff=0.55)
        suggestions = []
        for match in scored:
            for species_id, display, compacted in compact_names:
                if compacted == match:
                    suggestions.append(f"{species_id}")
                    break
        print(f"{species:3d} {name!r:20s} -> {suggestions}")


if __name__ == "__main__":
    main()
