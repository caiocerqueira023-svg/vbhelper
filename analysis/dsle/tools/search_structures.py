"""Search overlays and data files for Lost Evolution species names and locate candidate tables."""
import re
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ANALYSIS = ROOT / "analysis/dsle"
NITRO = ANALYSIS / "nitrofs"
NAMES = ["Gabumon", "Agumon", "Coronamon", "MetalMamemon", "ToyAgumon", "Dorumon", "Falcomon",
         "Chicchimon", "Chaosmon", "Lunamon", "Koromon"]


def ascii_hits(data, needle):
    hits = []
    start = 0
    while len(hits) < 5:
        at = data.find(needle.encode("ascii"), start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    return hits


def main():
    print("=== overlay files")
    for path in sorted(ANALYSIS.glob("ovl*.bin")):
        data = path.read_bytes()
        positions = [(name, ascii_hits(data, name)) for name in NAMES]
        found = [(name, hits) for name, hits in positions if hits]
        print(f"{path.name}: {len(data)} bytes, names={[(n, [hex(h) for h in hits]) for n, hits in found]}")

    print("\n=== other analysis files")
    for path in sorted(ANALYSIS.glob("*.bin")):
        if path.name.startswith("ovl"):
            continue
        data = path.read_bytes()
        found = [(name, ascii_hits(data, name)) for name in NAMES]
        found = [(name, hits) for name, hits in found if hits]
        if found:
            print(f"{path.name}: {[(n, [hex(h) for h in hits]) for n, hits in found]}")

    print("\n=== message pack structures")
    for path in sorted((NITRO / "MSG").glob("*.PAK")):
        data = path.read_bytes()
        count, version = struct.unpack_from("<I4s", data, 0)
        print(f"{path.name}: count={count} version={version!r} size={len(data)}")
        # table of offsets?
        print("   first 8 u32:", [hex(v) for v in struct.unpack_from("<8I", data, 8)])

    print("\n=== BPLAYER table parse")
    data = (NITRO / "BPLAYER.BIN").read_bytes()
    count = struct.unpack_from("<I", data, 0)[0]
    print(f"count={count} (0x{count:x})")
    for index in range(4):
        at = 0x10 + index * 0x10
        print(f"  entry {index} @ {at:#x}: " + " ".join(f"{v:#x}" for v in struct.unpack_from("<4I", data, at)))
    # try entry layout - offsets and sizes
    print("\n=== BPDATA parse")
    data = (NITRO / "BPDATA.BIN").read_bytes()
    print("first u32s:", [hex(v) for v in struct.unpack_from("<12I", data, 0)])


if __name__ == "__main__":
    main()
