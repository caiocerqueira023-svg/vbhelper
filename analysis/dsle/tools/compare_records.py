"""Compare the five Lost Evolution roster records field by field to locate varying fields."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
NAMES = {77: "Agumon", 80: "Dorumon", 82: "Gabumon", 90: "ToyAgumon", 101: "Falcomon"}

save = SAVE.read_bytes()
base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
records = []
for slot in range(40):
    at = base + 0x1850 + slot * 0x78
    identifier = struct.unpack_from("<I", save, at)[0]
    species = struct.unpack_from("<H", save, at + 0x0C)[0]
    if identifier == 0 and species == 0:
        continue
    records.append((slot, save[at:at + 0x78]))

print(f"records: {[(slot, NAMES.get(struct.unpack_from('<H', rec, 0x0C)[0])) for slot, rec in records]}")
print("\noffset  " + "  ".join(f"{NAMES.get(struct.unpack_from('<H', rec, 0x0C)[0], '?'):>10}" for _, rec in records) + "   note")
for offset in range(0x00, 0x78, 2):
    values = [struct.unpack_from("<H", rec, offset)[0] for _, rec in records]
    all_same = len(set(values)) == 1
    note = "" if not all_same else ("constant" if values[0] != 0 else "")
    print(f"{offset:#04x}    " + "  ".join(f"{value:#010x}" for value in values) + f"   {note}")
print("\nu32 views")
for offset in range(0x00, 0x78, 4):
    values = [struct.unpack_from("<I", rec, offset)[0] for _, rec in records]
    if len(set(values)) == 1:
        continue
    print(f"{offset:#04x}    " + "  ".join(f"{value:#010x}" for value in values))
