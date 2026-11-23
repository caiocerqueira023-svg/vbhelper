"""Extract the full Dawn/Dusk species-name table from ARM9.

The table consumed by the save/status UI (0x02112998 on Dawn) is truncated to about 12 glyphs, so
'MetalMamemon' appears as 'MtlMamemon'. ARM9 also holds a packed full-name table (first entry
'Chicchimon' = species 0x41) with one 0xFFFF-terminated string per species in id order. This tool
decodes it for both titles, checks them against each other, and writes the canonical JSON used to
map game species ids to bundled species identities.
"""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
TITLES = {
    "dawn": ROOT / "analysis/ds2/dawn/arm9.bin",
    "dusk": ROOT / "analysis/ds2/dusk/arm9.bin",
}
FIRST_SPECIES = 0x41
SPECIES_COUNT = 436


def encode(text):
    out = []
    for char in text:
        if "A" <= char <= "Z":
            out.append(0x20 + ord(char) - ord("A"))
        elif "a" <= char <= "z":
            out.append(0x3A + ord(char) - ord("a"))
        else:
            raise ValueError(char)
    return b"".join(struct.pack("<H", code) for code in out)


def find_table(data):
    """The packed full-name table starts with 'Chicchimon' (first species, id 0x41)."""
    needle = encode("Chicchimon")
    position = data.find(needle)
    if position < 0:
        raise SystemExit("full-name table not found")
    return position


def decode_string(data, at):
    codes = []
    while True:
        value = struct.unpack_from("<H", data, at)[0]
        at += 2
        if value == 0xFFFF:
            break
        codes.append(value)
    text = []
    for code in codes:
        if 0x20 <= code <= 0x39:
            text.append(chr(ord("A") + code - 0x20))
        elif 0x3A <= code <= 0x53:
            text.append(chr(ord("a") + code - 0x3A))
        else:
            text.append(f"<{code:#x}>")
    return "".join(text), at


def extract(path):
    data = Path(path).read_bytes()
    at = find_table(data)
    base = at
    names = {}
    for index in range(SPECIES_COUNT):
        text, at = decode_string(data, at)
        names[FIRST_SPECIES + index] = text
    return base, names, at


def main():
    results = {}
    for title, path in TITLES.items():
        base, names, end = extract(path)
        results[title] = names
        print(f"{title}: table at arm9 {base:#07x}, ends {end:#07x}, entries {len(names)}")
        for species in (0x41, 0x42, 0x85, 0x9C, 0x124, 0x133, 0x159, 0x1B7, 0x1F4):
            print(f"   {species:#05x}: {names.get(species)!r}")
    dawn = results["dawn"]
    dusk = results["dusk"]
    differences = {species: (dawn[species], dusk[species]) for species in dawn if dawn[species] != dusk[species]}
    print(f"\nDawn/Dusk full-name differences: {len(differences)}")
    for species, pair in list(differences.items())[:20]:
        print(f"   {species:#05x}: dawn={pair[0]!r} dusk={pair[1]!r}")
    output = ROOT / "analysis/ds2/ds2_full_names.json"
    output.write_text(json.dumps({"firstSpecies": FIRST_SPECIES,
                                  "dawn": {str(k): v for k, v in dawn.items()},
                                  "dusk": {str(k): v for k, v in dusk.items()}},
                                 ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\nwrote {output}")


if __name__ == "__main__":
    main()
