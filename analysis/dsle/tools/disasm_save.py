"""Disassemble the Lost Evolution save/load state machines."""
import capstone
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
data = (ROOT / "analysis/dsle/decompressed/arm9.bin").read_bytes()
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
md.detail = False


def disassemble(address, size):
    print(f"==== function @ 0x{address:08x} size 0x{size:x}")
    offset = address - 0x02000000
    for insn in md.disasm(data[offset:offset + size], address):
        print(f"{insn.address:08x}: {insn.bytes.hex():<8} {insn.mnemonic:<8} {insn.op_str}")


if __name__ == "__main__":
    import sys
    for argument in sys.argv[1:]:
        address_text, size_text = argument.split(":")
        address = int(address_text, 16)
        if address < 0x02000000:
            address += 0x02000000
        disassemble(address, int(size_text, 16))
