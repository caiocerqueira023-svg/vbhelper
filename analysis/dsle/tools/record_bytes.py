"""Print every byte of the five roster records with offsets to settle the field map."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")

save = SAVE.read_bytes()
base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
names = {0: "Agumon", 1: "Gabumon", 2: "ToyAgumon", 3: "Dorumon", 4: "Falcomon"}
records = {}
for slot in range(5):
    records[slot] = save[base + 0x1850 + slot * 0x78:base + 0x1850 + (slot + 1) * 0x78]

print("bytes 0x00..0x77")
for offset in range(0x78):
    values = [records[slot][offset] for slot in range(5)]
    text = " ".join(f"{value:3d}" for value in values)
    highlight = "  <" if len(set(values)) > 1 else ""
    print(f"{offset:#04x}: {text}{highlight}")

print("\nASCII view")
for slot in range(5):
    raw = records[slot][0x0E:0x20]
    print(f"  {names[slot]:10s} name bytes: {raw.hex()} text={raw.split(b'\\x00')[0]!r}")
