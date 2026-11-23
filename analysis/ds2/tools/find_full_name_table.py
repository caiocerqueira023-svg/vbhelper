"""Search the Dawn/Dusk ROM for a full-length species-name table.

The ARM9 table at 0x02112998 (record/status names) is truncated to about 12 glyphs, so
'MetalMamemon' is stored as 'MtlMamemon'. If a full-name table exists elsewhere (message pack or a
second ARM9 table) it is the authoritative name source for species identity.
"""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ROM = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE\Digimon World - Dawn (USA).nds")
ARM9 = ROOT / "analysis/ds2/dawn/arm9.bin"

# Glyph map recovered from the ROM: 'A'..'Z' = 0x20..0x39, 'a'..'z' = 0x3A..0x53.
def glyphs(text):
    out = []
    for char in text:
        if "A" <= char <= "Z":
            out.append(0x20 + ord(char) - ord("A"))
        elif "a" <= char <= "z":
            out.append(0x3A + ord(char) - ord("a"))
        else:
            raise ValueError(char)
    return b"".join(struct.pack("<H", code) for code in out)


def search(haystack, needle, limit=6):
    hits = []
    start = 0
    while len(hits) < limit:
        found = haystack.find(needle, start)
        if found < 0:
            break
        hits.append(found)
        start = found + 1
    return hits


def context(data, at, codes=26):
    values = struct.unpack_from(f"<{codes}H", data, at)
    text = []
    for value in values:
        if value == 0xFFFF:
            break
        if 0x20 <= value <= 0x39:
            text.append(chr(ord("A") + value - 0x20))
        elif 0x3A <= value <= 0x53:
            text.append(chr(ord("a") + value - 0x3A))
        else:
            text.append(f"<{value:#x}>")
    return "".join(text)


def main():
    rom = ROM.read_bytes()
    arm9 = ARM9.read_bytes()
    for name in ("MetalMamemon", "WereGarurumon", "PrinceMamemon", "BlackAgumon", "AgumonBlack"):
        needle = glyphs(name)
        print(f"\n=== {name} ({needle.hex()[:24]}...)")
        for label, data in (("rom", rom), ("arm9", arm9)):
            hits = search(data, needle)
            print(f"  {label}: {len(hits)} hits {[hex(hit) for hit in hits]}")
            for hit in hits[:3]:
                print(f"    @{hit:#x}: {context(data, hit)!r}")
    # Also look for the ASCII/UTF-16 spellings in case a text table exists.
    for name in ("MetalMamemon", "WereGarurumon Black", "PrinceMamemon"):
        utf16 = name.encode("utf-16-le")
        hits = search(rom, utf16)
        print(f"\nUTF-16 {name!r}: {len(hits)} hits {[hex(hit) for hit in hits]}")
        for hit in hits[:2]:
            print(f"    @{hit:#x}: {rom[hit - 32:hit + 64]!r}")


if __name__ == "__main__":
    main()
