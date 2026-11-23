"""Dump the Lost Evolution player-state container regions from the newest save slot."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")

save = SAVE.read_bytes()
base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
print(f"newest slot {base:#x}")
for label, start, length in (
        ("header", 0x00, 0x40),
        ("party container (ram 0x3ac)", 0x20 + 0x3ac, 0x60),
        ("container (ram 0x1020)", 0x20 + 0x1020, 0x60),
        ("groups (ram 0x11d0)", 0x20 + 0x11d0, 0x100),
        ("container (ram 0x157c)", 0x20 + 0x157c, 0x80),
        ("roster header (ram 0x1830)", 0x20 + 0x1830, 0x20),
):
    print(f"\n=== {label} @ save {start:#06x}")
    for at in range(start, start + length, 16):
        chunk = save[base + at:base + at + 16]
        words = struct.unpack_from("<4I", save, base + at)
        ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        print(f"  {at:#06x}: " + " ".join(f"{value:#010x}" for value in words) + f"  |{ascii_part}|")
