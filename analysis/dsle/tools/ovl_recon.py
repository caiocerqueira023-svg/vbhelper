"""Inspect Lost Evolution overlay compression headers."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ROM = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
           r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].nds")


def main():
    rom = ROM.read_bytes()
    ovt, ovt_size = struct.unpack_from("<II", rom, 0x50)
    print(f"ovt {ovt:#x} size {ovt_size}")
    for index in range(ovt_size // 32):
        values = struct.unpack_from("<8I", rom, ovt + index * 32)
        print(f"ovl{index}: " + " ".join(f"{value:#x}" for value in values))
    for index in range(6):
        path = ROOT / f"analysis/dsle/ovl{index:03d}.bin"
        data = path.read_bytes()
        print(f"\novl{index}: {len(data)} bytes first16={data[:16].hex()}")
        # decode possible header words
        first = data[0]
        print(f"  first=0x{first:02x} next3={data[1:4].hex()}")
        if first in (0x10, 0x11, 0x40):
            decompressed = struct.unpack_from("<I", data, 1)[0] & 0xFFFFFF
            print(f"  standard-LZ type {first:#x}, decoded size {decompressed:#x}")
        else:
            # Guess a BLZ-style header: 0x? then sizes
            print(f"  u32@0={struct.unpack_from('<I', data, 0)[0]:#x} u32@4={struct.unpack_from('<I', data, 4)[0]:#x}")
    # ARM9 magic/identifier search
    arm9 = (ROOT / "analysis/dsle/arm9.bin").read_bytes()
    for needle in (b"DM3", b"DS3", b"Digimon", b"SAVE", b"save"):
        positions = []
        start = 0
        while len(positions) < 10:
            at = arm9.find(needle, start)
            if at < 0:
                break
            positions.append(at)
            start = at + 1
        print(f"\narm9 {needle!r}: {[hex(p) for p in positions]}")


if __name__ == "__main__":
    main()
