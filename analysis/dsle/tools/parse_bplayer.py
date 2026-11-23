"""Parse BPLAYER.BIN (per-Digimon battle parameters) and correlate with the save record stats."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
NITRO = ROOT / "analysis/dsle/nitrofs"
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")


def main():
    data = (NITRO / "BPLAYER.BIN").read_bytes()
    count = struct.unpack_from("<I", data, 0)[0]
    print("BPLAYER count", count, "size", len(data))
    entries = []
    for index in range(count):
        at = 0x10 + index * 16
        offset, size1, size2, flags = struct.unpack_from("<4I", data, at)
        entries.append((offset, size1, size2, flags))
    print("first entries:", [(hex(o), hex(s1), hex(s2), hex(f)) for o, s1, s2, f in entries[:6]])
    print("last entries:", [(hex(o), hex(s1), hex(s2), hex(f)) for o, s1, s2, f in entries[-3:]])
    sizes = sorted({s1 for _, s1, _, _ in entries})
    print("distinct size1 values:", sizes[:12], "...", sizes[-3:])
    # print block for species 82 (Gabumon in our save)
    for species in (0, 54, 77, 82, 90):
        offset, size1, size2, flags = entries[species]
        block = data[offset:offset + size1]
        print(f"\nspecies {species} ({data[offset:offset + size1][:0].hex()}): offset={offset:#x} "
              f"size1={size1:#x} size2={size2:#x} flags={flags:#x} first0x60:")
        for i in range(0, min(len(block), 0x60), 16):
            print(f"  {i:#04x}: " + " ".join(f"{b:02x}" for b in block[i:i + 16]))
        print("  u16 values:", [struct.unpack_from("<H", block, i)[0] for i in range(0, min(len(block), 0x40), 2)])

    # Save roster record for Gabumon
    save = SAVE.read_bytes()
    base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
    record = save[base + 0x18C8:base + 0x18C8 + 0x78]
    print("\nGabumon save record u16s from +0x1E:")
    print([hex(struct.unpack_from("<H", record, i)[0]) for i in range(0x1E, 0x78, 2)])
    print("as u32:", [hex(struct.unpack_from("<I", record, i)[0]) for i in range(0x00, 0x78, 4)])


if __name__ == "__main__":
    main()
