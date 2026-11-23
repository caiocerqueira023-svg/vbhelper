"""Decode Dawn/Dusk full names with the tables' special glyphs and resolve to catalog species."""
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
CATALOG = ROOT / "app/src/main/assets/species_catalog.json"
FULL = ROOT / "analysis/ds2/ds2_full_names.json"

SPECIAL = {0x0: " ", 0x15: "(", 0x16: ")", 0x19: " "}
STAGE_BANDS = [(0x41, 0x5F, "training"), (0x61, 0xA6, "rookie"), (0xA8, 0x11E, "champion"),
               (0x120, 399, "ultimate"), (401, 0x1F4, "mega")]


def compact(value):
    text = unicodedata.normalize("NFKC", value)
    return "".join(ch for ch in text.lower() if ch.isalnum())


def expand(name):
    def replace(match):
        code = int(match.group(1), 16)
        return SPECIAL.get(code, "")
    return re.sub(r"<(0x[0-9a-f]+)>", replace, name).strip()


def stage_of(species):
    for low, high, label in STAGE_BANDS:
        if low <= species <= high:
            return label
    return "?"


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {s["id"]: s for s in catalog["species"]}
    by_compact = defaultdict(list)
    for species in catalog["species"]:
        for name in (species["name"], *species.get("aliases", [])):
            by_compact[compact(name)].append(species["id"])
    full = json.loads(FULL.read_text(encoding="utf-8"))["dawn"]

    def resolve(name):
        if not name:
            return None
        hits = sorted(set(by_compact.get(compact(name), [])))
        return hits[0] if len(hits) == 1 else None

    special = defaultdict(list)
    unresolved = []
    for key, raw in sorted(full.items(), key=lambda item: int(item[0])):
        species = int(key)
        codes = tuple(sorted({int(code, 16) for code in re.findall(r"<0x([0-9a-f]+)>", raw)}))
        if codes:
            special[codes].append((species, raw))
        name = expand(raw)
        canonical = resolve(name)
        if canonical is None:
            unresolved.append((species, stage_of(species), raw, name))
    print(f"special code groups: {len(special)}")
    for codes, entries in special.items():
        print(f"   codes={[hex(c) for c in codes]} n={len(entries)} e.g. {entries[:4]}")
    print(f"\nstill unresolved: {len(unresolved)} / {len(full)}")
    for species, stage, raw, name in unresolved:
        print(f"   {species:#05x} [{stage:8s}] raw={raw!r} text={name!r}")


if __name__ == "__main__":
    main()
