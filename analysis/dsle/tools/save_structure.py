"""Structural reconnaissance of the Lost Evolution save (names, record stride, regions)."""
import re
import struct
import sys
from pathlib import Path

SAVE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
    r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")


def u16(data, at):
    return struct.unpack_from("<H", data, at)[0]


def u32(data, at):
    return struct.unpack_from("<I", data, at)[0]


def name_at(data, at, width=16):
    raw = data[at:at + width]
    return raw.split(b"\x00")[0].decode("ascii", "replace")


def main():
    data = SAVE.read_bytes()
    print("copy equality:", data[0x10:0x8000] == data[0x8010:0x10000])
    print("copy1 seq @0x18:", u32(data, 0x18), " copy2 seq @0x8018:", u32(data, 0x8018))
    print("copy1 hdr:", data[:16].hex(), " copy2 hdr:", data[0x8000:0x8010].hex())

    # Records with ASCII names at stride 0x78: find every printable-name boundary.
    print("\n--- candidate records (stride 0x78, name at +0x0E)")
    base = 0x18C8
    for index in range(48):
        at = base + index * 0x78
        if at + 0x78 > 0x8000:
            break
        first = u32(data, at)
        species = u16(data, at + 0x0C)
        name = name_at(data, at + 0x0E)
        if not (first < 100 and name and all(32 <= ord(c) < 127 for c in name)):
            print(f"  #{index} at {at:#06x}: stop ({first}, species={species}, name={name!r})")
            break
        rest = data[at + 0x0E + 16:at + 0x78]
        print(f"  #{index} at {at:#06x}: idx={first} species={species}({name}) "
              f"u32@4={u32(data, at+4):#010x} u32@8={u32(data, at+8):#010x}")
        print(f"      +0x1E: {' '.join(f'{b:02x}' for b in rest[:0x40])}")

    # Region dumps
    for at, label in ((0x1000, "region 0x1000"), (0x2C00, "region 0x2c00"), (0x3000, "region 0x3000"),
                      (0x3400, "region 0x3400"), (0x3C00, "region 0x3c00")):
        print(f"\n--- {label}")
        for i in range(0, 0x80, 16):
            chunk = data[at + i:at + i + 16]
            print(f"  {at + i:#06x}: " + " ".join(f"{b:02x}" for b in chunk))

    # ASCII string census with offsets
    print("\n--- strings >=5")
    for match in re.finditer(rb"[ -~]{5,}", data[:0x8000]):
        print(f"  {match.start():#06x}: {match.group()[:50]!r}")


if __name__ == "__main__":
    main()
