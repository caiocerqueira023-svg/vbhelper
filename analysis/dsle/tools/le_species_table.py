"""Examine the Lost Evolution species id table and parameter block layout."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = ROOT / "analysis/dsle/decompressed/arm9.bin"
data = ARM9.read_bytes()
RAM = 0x02000000
ID_TABLE = 0x02154278
BASE1 = 0x021325EC
BASE3 = 0x02129FC8
BASE1_COUNT = 0x178
ID_COUNT = 0x2E7


def entries():
    for species in range(ID_COUNT):
        at = ID_TABLE - RAM + species * 8
        yield species, *struct.unpack_from("<ii", data, at)


def block(base, index, size):
    at = base - RAM + index * size
    return data[at:at + size]


def main():
    table = list(entries())
    print("id table samples:")
    for species, first, second in table[:8] + [table[i] for i in (53, 54, 77, 100, 185, 186, 375, 376, 400, 446, 447, 450, 500, 742)]:
        print(f"  id {species:3d}: first={first:4d} second={second:4d}")
    valid = [species for species, first, second in table if 0 <= first < BASE1_COUNT]
    print(f"\nentries with a valid base1 index: {len(valid)} / {ID_COUNT}")
    print("max species id with a valid base1 index:", max(valid))
    print("max base1 index used:", max(first for _, first, _ in table))
    print("max base3 index used:", max(second for _, _, second in table))
    for species in (54, 77, 82, 101, 446):
        first = table[species][1]
        second = table[species][2]
        if 0 <= first < BASE1_COUNT:
            print(f"\nspecies {species}: template {first}")
            block1 = block(BASE1, first, 0x38)
            for at in range(0, 0x38, 8):
                print(f"  {at:#04x}: {block1[at:at + 8].hex()}  u16={struct.unpack_from('<4H', block1, at)}")
        if 0 <= second:
            block3 = block(BASE3, second, 0x14)
            print(f"  base3 {second}: {block3.hex()} u16={struct.unpack_from('<2H', block3, 0)} "
                  f"nameMsg={struct.unpack_from('<H', block3, 0xE)[0]}")


if __name__ == "__main__":
    main()
