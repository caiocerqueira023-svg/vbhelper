"""Locate Lost Evolution data tables: species names, save magic, NitroFS files."""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = (ROOT / "analysis/dsle/arm9.bin").read_bytes()
ROM = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
           r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].nds").read_bytes()


def find_all(haystack, needle, limit=8):
    hits = []
    start = 0
    while len(hits) < limit:
        at = haystack.find(needle, start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    return hits


def main():
    print("=== ARM9 ASCII name search")
    for needle in (b"Gabumon", b"Agumon", b"Coronamon", b"ToyAgumon", b"Dorumon", b"Falcomon",
                   b"Greymon", b"MetalMamemon", b"DigiEgg", b"DIGIEGG", b"Egg"):
        hits = find_all(ARM9, needle)
        print(f"  {needle!r}: {[hex(h) for h in hits]}")

    print("\n=== full ROM ASCII name search")
    for needle in (b"Gabumon", b"Agumon", b"Coronamon", b"MetalMamemon"):
        print(f"  {needle!r}: {[hex(h) for h in find_all(ROM, needle)]}")

    print("\n=== NitroFS inventory from unpack")
    nitro = ROOT / "analysis/dsle/nitrofs.json"
    if nitro.exists():
        rows = json.loads(nitro.read_text(encoding="utf-8"))
        print(f"  entries={len(rows)}")
        for row in rows[:40]:
            print("   ", row)
    # Read FNT directly
    fnt, fnt_size, fat, fat_size = struct.unpack_from("<IIII", ROM, 0x40)
    print(f"\nFNT {fnt:#x}+{fnt_size:#x} FAT {fat:#x}+{fat_size:#x} files={fat_size//8}")
    for index in range(fat_size // 8):
        start, end = struct.unpack_from("<II", ROM, fat + index * 8)
        print(f"  file {index:2d}: {start:#010x}..{end:#010x} ({end - start} bytes) head={ROM[start:start+16].hex()}")


if __name__ == "__main__":
    main()
