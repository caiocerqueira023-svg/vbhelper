"""Dump Lost Evolution save state-machine jump tables and handler addresses."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
data = (ROOT / "analysis/dsle/decompressed/arm9.bin").read_bytes()


def read_words(address, count):
    offset = address - 0x02000000
    return [struct.unpack_from("<I", data, offset + index * 4)[0] for index in range(count)]


for label, table, count in (
        ("load jump table", 0x0209c418, 7),
        ("save jump table", 0x0209c68c, 11),
):
    entries = read_words(table, count)
    print(f"{label} @ 0x{table:08x}:")
    for index, entry in enumerate(entries):
        print(f"  state {index}: 0x{entry:08x}")
    print()
