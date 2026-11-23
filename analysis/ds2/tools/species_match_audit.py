"""Audit game species-name tables against the bundled canonical species catalog.

Replicates CanonicalSpeciesCatalog.resolve: explicit id, exact compact name, compact aliases.
Prints the names that do not resolve, duplicate canonical assignments, and the variant families
(Black/Blue/etc.) so a curated mapping can be built from evidence.
"""
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
DS1 = ROOT / "app/src/main/assets/world_ds_species.json"
DS2 = ROOT / "app/src/main/assets/ds2_species.json"


def compact(value: str) -> str:
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def load_catalog():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {s["id"]: s for s in data["species"]}
    by_compact = defaultdict(list)
    for species in data["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    return data["species"], by_id, by_compact


def resolve(name_or_id, by_id, by_compact):
    if not name_or_id:
        return None
    if name_or_id in by_id:
        return name_or_id
    hits = sorted(set(by_compact.get(compact(name_or_id), [])))
    return hits[0] if len(hits) == 1 else None


def report(game, entries, by_id, by_compact, limit=40):
    print(f"\n=== {game}: {len(entries)} species")
    unresolved = []
    duplicates = defaultdict(list)
    for species_id, name in entries:
        if not name:
            continue
        canonical = resolve(name, by_id, by_compact)
        if canonical is None:
            unresolved.append((species_id, name))
        else:
            duplicates[canonical].append((species_id, name))
    print(f"  unresolved: {len(unresolved)}")
    for index, name in unresolved[:limit]:
        print(f"    {index:#05x} {name!r}")
    # Game ids that map to the same canonical species: a real ambiguity, not just a name variant.
    collisions = {canonical: entries for canonical, entries in duplicates.items() if len(entries) > 1}
    print(f"  canonical collisions: {len(collisions)}")
    for canonical, entries in list(collisions.items())[:limit]:
        print(f"    {canonical}: {[(hex(i), n) for i, n in entries]}")
    return unresolved, collisions


def main():
    species, by_id, by_compact = load_catalog()
    ds1_names = json.loads(DS1.read_text(encoding="utf-8"))["names"]
    ds2_names = json.loads(DS2.read_text(encoding="utf-8"))["names"]
    # World DS stores a dense list starting at species 0x41; Dawn/Dusk stores decimal id keys.
    ds1 = [(0x41 + index, name) for index, name in enumerate(ds1_names)]
    ds2 = [(int(key), value) for key, value in ds2_names.items()]
    print(f"catalog species: {len(species)}")
    report("World DS", ds1, by_id, by_compact)
    report("Dawn/Dusk", ds2, by_id, by_compact)
    # Every name that looks like a variant family, for both tables.
    interesting = re.compile(r"(black|blue|white|red|gold|silver|metal|were|toy|dot|snow|shadow|x)", re.I)
    print("\n=== variant-looking names")
    for game, entries in (("World DS", ds1), ("Dawn/Dusk", ds2)):
        hits = [(species_id, name) for species_id, name in entries
                if name and interesting.search(name)]
        print(f"  {game}: {len(hits)}")
        for species_id, name in hits[:100]:
            print(f"    {species_id:#05x} {name!r} -> {resolve(name, by_id, by_compact)}")


if __name__ == "__main__":
    main()
