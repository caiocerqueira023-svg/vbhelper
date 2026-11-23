"""Locate the record-creation code by finding the record magic constants in ARM9/overlays."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
FILES = [("arm9", ROOT / "analysis/dsle/decompressed/arm9.bin")] + [
    (f"ovl{index:03d}", ROOT / f"analysis/dsle/decompressed/ovl{index:03d}.bin") for index in range(4)]
NEEDLES = {
    "magic 0x030405F0": struct.pack("<I", 0x030405F0),
    "stride 0x78": struct.pack("<I", 0x78),
    "record base 0x1850": struct.pack("<I", 0x1850),
    "slot size 0x8000": struct.pack("<I", 0x8000),
}


def find(data, needle, limit=20):
    positions = []
    start = 0
    while len(positions) < limit:
        at = data.find(needle, start)
        if at < 0:
            break
        positions.append(at)
        start = at + 1
    return positions


for name, path in FILES:
    if not path.exists():
        continue
    data = path.read_bytes()
    print(f"== {name} ({len(data)} bytes)")
    for label, needle in NEEDLES.items():
        hits = find(data, needle)
        if hits:
            print(f"  {label}: {[hex(h) for h in hits]}")
