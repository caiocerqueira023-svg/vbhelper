"""Scan Lost Evolution overlays for calls into ARM9, to find who uses the farm/party/create APIs."""
import struct
from collections import Counter
from pathlib import Path

import capstone

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9_LO, ARM9_HI = 0x02000000, 0x02160000

BASES = {
    "ovl000": 0x0223BC00, "ovl001": 0x0223BC00, "ovl002": 0x0223BC00,
    "ovl003": 0x02264100, "ovl004": 0x02264100,
}
INTERESTING = {
    0x020ADF08: "create Digimon (high level)",
    0x020B0100: "record initializer",
    0x020AFCE8: "party insert",
    0x020AFAE8: "party remove",
    0x020EA994: "farm resident remove",
    0x020EAC08: "farm second-list add",
    0x020EACA4: "farm second-list remove",
    0x020EAD60: "farm group list add",
    0x020EB04C: "farm group list remove",
    0x020AFF8C: "roster free slot",
    0x020B04D4: "record clear",
    0x020AE0AC: "delete Digimon",
    0x020EAA5C: "farm resident get",
    0x020EB460: "0x157C container get",
    0x020EB41C: "0x157C container remove",
}

for name, base in BASES.items():
    path = ROOT / f"analysis/dsle/decompressed/{name}.bin"
    if not path.exists():
        continue
    data = path.read_bytes()
    print(f"\n== {name} base={base:#x} size={len(data)}")
    for mode_name, mode in (("ARM", capstone.CS_MODE_ARM), ("THUMB", capstone.CS_MODE_THUMB)):
        md = capstone.Cs(capstone.CS_ARCH_ARM, mode)
        md.detail = True
        targets = Counter()
        interesting_hits = []
        count = 0
        for insn in md.disasm(data, base):
            count += 1
            if insn.mnemonic.startswith(("bl", "b.")) and insn.operands:
                op = insn.operands[0]
                if op.type == capstone.arm.ARM_OP_IMM:
                    target = op.imm
                    if ARM9_LO <= target < ARM9_HI:
                        targets[target] += 1
                        if target in INTERESTING:
                            interesting_hits.append((insn.address, target, INTERESTING[target]))
        print(f"  {mode_name}: {count} insns, arm9 branch targets {len(targets)}")
        for target, hits in targets.most_common(12):
            print(f"    -> {target:#010x} x{hits} {INTERESTING.get(target, '')}")
        for address, target, label in interesting_hits[:20]:
            print(f"    HIT caller {address:#010x} -> {target:#010x} {label}")
