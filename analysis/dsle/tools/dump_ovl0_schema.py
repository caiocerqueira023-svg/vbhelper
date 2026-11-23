"""Dump the candidate record-schema table in overlay 000."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
data = (ROOT / "analysis/dsle/decompressed/ovl000.bin").read_bytes()
RAM = 0x0223BC00

start, end = 0x26800, 0x27C00
for at in range(start, end, 16):
    chunk = data[at:at + 16]
    words = struct.unpack_from("<4I", data, at)
    ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
    print(f"{RAM + at:#010x}: " + " ".join(f"{value:#010x}" for value in words) + f"  |{ascii_part}|")
