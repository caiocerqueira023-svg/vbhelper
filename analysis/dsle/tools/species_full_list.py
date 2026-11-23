"""Extract the complete species name list from MESPAK00 and verify against the save's species ids."""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
PAK = ROOT / "analysis/dsle/nitrofs/MSG/MESPAK00.PAK"
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
OUT = ROOT / "analysis/dsle/le_species_names.json"


def is_name(text):
    if not text or len(text) > 20:
        return False
    if not all(32 <= ord(c) < 127 for c in text):
        return False
    # species names are a single token: letters/digits plus a few separators
    return all(c.isalnum() or c in " .'-():" for c in text) and any(c.isalpha() for c in text)


def main():
    data = PAK.read_bytes()
    tokens = []
    at = 0
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
        tokens.append((at - len(raw) - 1, text))

    # Find the longest run of name-like tokens.
    best_start = best_len = 0
    index = 0
    while index < len(tokens):
        if is_name(tokens[index][1]):
            start = index
            while index < len(tokens) and is_name(tokens[index][1]):
                index += 1
            length = index - start
            if length > best_len:
                best_start, best_len = start, length
        else:
            index += 1
    block = tokens[best_start:best_start + best_len]
    print(f"name-like run: {best_len} tokens at offsets "
          f"{block[0][0]:#x}..{block[-1][0]:#x}")
    print(f"first: {[text for _, text in block[:8]]}")
    print(f"last: {[text for _, text in block[-8:]]}")
    for index, (offset, text) in enumerate(block[:20]):
        print(f"  {index:3d} {offset:#06x} {text}")

    # Anchor check: the save's species ids should be (base + index).
    save = SAVE.read_bytes()
    sequences = [(struct.unpack_from("<I", save, base + 0x18)[0], base) for base in (0x0, 0x8000)]
    _, base = max(sequences)
    anchors = []
    for slot in range(40):
        record = base + 0x18C8 + slot * 0x78
        identifier = struct.unpack_from("<I", save, record)[0]
        species = struct.unpack_from("<H", save, record + 0x0C)[0]
        name = save[record + 0x0E:record + 0x0E + 16].split(b"\x00")[0].decode("ascii", "replace")
        if identifier == 0 and species == 0 and not name:
            break
        anchors.append((name, species))
    print(f"\nanchors: {anchors}")
    index_by_name = {}
    for index, (offset, text) in enumerate(block):
        index_by_name.setdefault(text, index)
    offsets = []
    for name, species in anchors:
        position = index_by_name.get(name)
        if position is None:
            print(f"  {name}: NOT in list")
            continue
        offsets.append(species - position)
        print(f"  {name}: id={species} index={position} base={species - position}")
    if offsets:
        print(f"bases: {sorted(set(offsets))}")

    OUT.write_text(json.dumps({
        "source": "MESPAK00.PAK name list",
        "count": len(block),
        "names": [text for _, text in block],
        "anchorBase": sorted(set(offsets)),
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\nwrote {OUT}")


if __name__ == "__main__":
    main()
