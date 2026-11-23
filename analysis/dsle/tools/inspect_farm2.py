"""Inspect the second farm identity structure (8 x 0xC at player +0x1508 / save +0x1528)."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
BAD = (ROOT / "analysis/dsle/device/now/le-current.sav").read_bytes()
GOOD = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()

for label, data in (("GOOD(10:04)", GOOD), ("BAD(app)", BAD)):
    base = 0x8000  # copy2
    print(f"\n=== {label} copy2, save offsets 0x1500..0x15A0")
    for at in range(0x1500, 0x15A0, 0x10):
        chunk = data[base + at:base + at + 0x10]
        print(f"  {at:#06x}: {chunk.hex()}  u32={struct.unpack_from('<4I', data, base + at)}")
    # identity-looking entries in 0x1528..0x15A0 (8 x 0xC)
    print("  parsed 8 x 0xC entries at +0x1528:")
    for i in range(8):
        at = base + 0x1528 + i * 0xC
        low, high = struct.unpack_from("<II", data, at)
        marker = data[at + 0xA]
        shadow = struct.unpack_from("<III", data, at - 0xC if i else at) if False else None
        print(f"   entry {i}: low={low:#x} high={high:#x} marker={marker:#x} bytes={data[at:at+0xC].hex()}")
