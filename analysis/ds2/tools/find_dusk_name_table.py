"""Locate the species name table in another title's ARM9 by matching Dawn's offset table.

The offset table content (relative offsets into the name blob) is expected to be identical
between USA Dawn and Dusk because the names are compiled in the same order; only the table's
base address differs.
"""
import struct
import sys
from pathlib import Path

DAWN_ARM9 = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn\arm9.bin")
DAWN_TABLE = 0x02112998
DAWN_RAM = 0x02000000


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: find_dusk_name_table.py <dusk-arm9.bin>")
    dusk = Path(sys.argv[1]).read_bytes()
    dawn = DAWN_ARM9.read_bytes()
    dawn_base = DAWN_TABLE - DAWN_RAM
    entries = 32
    pattern = dawn[dawn_base:dawn_base + entries * 4]
    print("dawn offsets:", [hex(struct.unpack_from('<I', pattern, i * 4)[0]) for i in range(entries)])
    start = 0
    hits = 0
    while True:
        found = dusk.find(pattern, start)
        if found < 0:
            break
        hits += 1
        print(f"hit at file 0x{found:X} -> RAM 0x{found + DAWN_RAM:08X}")
        start = found + 1
    print("total hits:", hits)


if __name__ == "__main__":
    main()
