"""Try to identify the Lost Evolution save header field at +0x34 (0x3292BA03) as a checksum."""
import hashlib
import struct
import zlib
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()

for base in (0, 0x8000):
    slot = SAVE[base:base + 0x8000]
    fields = struct.unpack_from("<8I", slot, 0x1C)
    target = fields[6]  # +0x34 = 0x3292BA03
    print(f"copy {base // 0x8000 + 1}: header +0x1C.. = {[hex(v) for v in fields]} target={target:#010x}")
    ranges = [(0x10, 0x8000), (0x20, 0x8000), (0x20, 0x409C), (0x20, 0x40A0), (0x10, 0x40A0),
              (0x00, 0x8000), (0x20, 0x7FE0), (0x34, 0x8000)]
    for start, end in ranges:
        chunk = slot[start:end]
        candidates = {
            "crc32": zlib.crc32(chunk) & 0xFFFFFFFF,
            "adler32": zlib.adler32(chunk) & 0xFFFFFFFF,
            "sum8": sum(chunk) & 0xFFFFFFFF,
            "sum32": sum(struct.unpack_from("<" + "I" * (len(chunk) // 4), chunk, 0)) & 0xFFFFFFFF,
            "md5_first4": struct.unpack_from("<I", hashlib.md5(chunk).digest(), 0)[0],
            "md5_last4": struct.unpack_from("<I", hashlib.md5(chunk).digest(), 12)[0],
        }
        for name, value in candidates.items():
            if value == target or (value ^ 0xFFFFFFFF) & 0xFFFFFFFF == target:
                print(f"  MATCH {name} over {start:#x}..{end:#x}: {value:#010x}")
    # Also compare header words between copies; maybe +0x34 is per-slot hash of some region.
    print("  header words 0x34 of both copies will be compared separately")
