"""Dump the smallest overlay and test whether it is real ARM code."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
d = (ROOT / "analysis/dsle/ovl005.bin").read_bytes()
print("len", len(d))
for i in range(0, len(d), 4):
    word = struct.unpack_from("<I", d, i)[0]
    print(f"{i:#04x}: {d[i:i + 4].hex()}  u32={word:#010x}")

print("\n--- ARM decoding (capstone if available)")
try:
    import capstone
    md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for insn in md.disasm(d, 0x2264100):
        print(f"  {insn.address:#010x}: {insn.mnemonic} {insn.op_str}")
    print("--- Thumb decoding")
    mdt = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
    for insn in mdt.disasm(d, 0x2264100):
        print(f"  {insn.address:#010x}: {insn.mnemonic} {insn.op_str}")
except ImportError:
    print("capstone not installed; raw words only")
