"""Recover the Lost Evolution species list from MESPAK00 and validate it against the save."""
import struct
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
PAK = ROOT / "analysis/dsle/nitrofs/MSG/MESPAK00.PAK"
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")


def tokens(data, start, end):
    at = start
    while at < end:
        stop = data.find(b"\x00", at)
        if stop < 0:
            break
        token = data[at:stop]
        yield at, token
        at = stop + 1


def main():
    data = PAK.read_bytes()
    names = []
    for at, token in tokens(data, 0x3080, 0x3400):
        if not token:
            continue
        try:
            text = token.decode("ascii")
        except UnicodeDecodeError:
            continue
        if not text or not all(32 <= ord(c) < 127 for c in text):
            continue
        names.append((at, text))
    print(f"tokens in 0x3080..0x3400: {len(names)}")
    for index, (at, text) in enumerate(names[:80]):
        print(f"  {index:3d} {at:#06x} {text}")

    # Save anchors
    save = SAVE.read_bytes()
    # newest copy = highest sequence
    sequences = [(struct.unpack_from("<I", save, base + 0x18)[0], base) for base in (0x0, 0x8000)]
    sequence, base = max(sequences)
    print(f"\nnewest copy at {base:#x} seq={sequence}")
    records = []
    for slot in range(40):
        at = base + 0x18C8 + slot * 0x78
        identifier = struct.unpack_from("<I", save, at)[0]
        species = struct.unpack_from("<H", save, at + 0x0C)[0]
        raw_name = save[at + 0x0E:at + 0x0E + 16].split(b"\x00")[0]
        if identifier == 0 and species == 0 and not raw_name:
            break
        records.append((slot, identifier, species, raw_name.decode("ascii", "replace")))
    print(f"records: {len(records)}")
    for record in records:
        print("  ", record)
    # Try base offset candidates for the name list -> id mapping
    print("\n--- anchor check")
    name_index = {text: index for index, (at, text) in enumerate(names)}
    for slot, identifier, species, name in records:
        index = name_index.get(name)
        if index is not None:
            print(f"  {name}: save id {species} -> list index {index}, id-index={species - index}")
        else:
            print(f"  {name}: not in the first {len(names)} tokens")
    print(f"\ntotal tokens examined: {len(names)} (list may continue past 0x3400)")


if __name__ == "__main__":
    main()
