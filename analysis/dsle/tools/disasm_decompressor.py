"""Disassemble the ARM9 in-ROM decompressor (0x02000950..0x02000A40) to port it exactly."""
from pathlib import Path

import capstone

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
d = (ROOT / "analysis/dsle/arm9.bin").read_bytes()
start = 0x950
end = 0xA80
code = d[start:end]
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
for insn in md.disasm(code, 0x02000000 + start):
    print(f"{insn.address:#010x}: {insn.bytes.hex():<8} {insn.mnemonic}{' ' + insn.op_str if insn.op_str else ''}")
