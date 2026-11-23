"""Dump the Lost Evolution save-section table and the save manager function inventory."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = ROOT / "analysis/dsle/decompressed/arm9.bin"
FUNCS = ROOT / "analysis/dsle/ghidra/out-arm9/functions.tsv"

data = ARM9.read_bytes()
print("=== data around the DM3 section table (RAM 0x02152E00..0x02153240)")
base = 0x152E00
for at in range(base, 0x153240, 16):
    chunk = data[at:at + 16]
    words = struct.unpack_from("<4I", data, at)
    ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
    print(f"{0x02000000 + at:#010x}: " + " ".join(f"{b:02x}" for b in chunk) +
          "  " + " ".join(f"{w:#010x}" for w in words) + f"  |{ascii_part}|")

print("\n=== ARM9 functions in the 0x0209C000..0x0209E200 save-manager window")
for line in FUNCS.read_text(encoding="utf-8").splitlines():
    parts = line.split("\t")
    if len(parts) < 3:
        continue
    try:
        address = int(parts[0], 16)
    except ValueError:
        continue
    if 0x0209C000 <= address <= 0x0209E200:
        print("  " + "  ".join(parts[:3]))
