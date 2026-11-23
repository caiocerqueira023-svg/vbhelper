"""Byte-pattern scan across ARM9 and overlay images to locate save-related code.

Maps every hit to a virtual address using the image's RAM base, so results can be fed straight
into Ghidra (naive xref: addresses stored literally are common for globals in ARM code).
"""
import json
import re
import struct
import sys
from pathlib import Path

INTEREST = {
    "DSDIG2": b"DSDIG2",
    "DSDIG": b"DSDIGI",
    "saveBytes_7B2C": struct.pack("<I", 0x7B2C),
    "saveBytes_7DC0": struct.pack("<I", 0x7DC0),
    "copy2_7B80": struct.pack("<I", 0x7B80),
    "copy2_7E00": struct.pack("<I", 0x7E00),
    "md5_init_A": struct.pack("<I", 0x67452301),
    "crc_init": struct.pack("<I", 0xFFFFFFFF),
    "seq_18": struct.pack("<H", 0x18),
    "hal_word_0x7B2C_half": struct.pack("<H", 0x7B2C),
}


def images(directory: Path):
    rom = json.loads((directory / "rom.json").read_text())
    rows = [("arm9", directory / "arm9.bin", int(rom["header"]["arm9"]["ramAddress"], 16))]
    for ov in rom["overlays"]:
        rows.append((f"ovl{ov['index']:03d}", directory / f"ovl{ov['index']:03d}.bin", ov["ram"]))
    return rows


def scan(directory: Path):
    out = {}
    for name, path, base in images(directory):
        if not path.exists():
            continue
        data = path.read_bytes()
        hits = {}
        for label, pattern in INTEREST.items():
            addresses = [m.start() for m in re.finditer(re.escape(pattern), data)]
            if addresses:
                hits[label] = [f"{base + a:#010x}" for a in addresses[:40]]
        if hits:
            out[name] = {"base": f"{base:#010x}", "size": len(data), "hits": hits}
    return out


if __name__ == "__main__":
    print(json.dumps(scan(Path(sys.argv[1])), indent=1))
