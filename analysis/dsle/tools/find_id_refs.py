"""Find every place in the save that references a Digimon record identity."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")

save = SAVE.read_bytes()
base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
slot = save[base:base + 0x8000]
MARKER = 0x030405F0

for record_index in range(5):
    needle = struct.pack("<II", record_index, MARKER)
    hits = []
    start = 0
    while True:
        at = slot.find(needle, start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    print(f"record id {record_index}: {[hex(hit) for hit in hits]}")

# Also look for the marker alone to catch other id layouts.
print("\nmarker occurrences:", [hex(at) for at in range(len(slot))
                                if slot[at:at + 4] == struct.pack("<I", MARKER)])
