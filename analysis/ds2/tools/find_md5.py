"""Locate an MD5 implementation (or its constant tables) in the ROM and extracted images."""
import json
import struct
import sys
from pathlib import Path

DIR = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn")

INIT = [0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476]
T_FIRST = [0xD76AA478, 0xE8C7B756, 0x242070DB, 0xC1BDCEEE]


def main():
    rom = json.loads((DIR / "rom.json").read_text())
    images = [("arm9", 0x02000000, DIR / "arm9.bin")]
    for ov in rom["overlays"]:
        images.append((f"ovl{ov['index']:03d}", ov["ram"], DIR / f"ovl{ov['index']:03d}.bin"))
    payload = struct.pack("<4I", *INIT)
    table = struct.pack("<4I", *T_FIRST)
    for name, base, path in images:
        data = path.read_bytes()
        for label, pattern in (("md5-init-seq", payload), ("md5-T-table", table)):
            start = 0
            while True:
                found = data.find(pattern, start)
                if found < 0:
                    break
                print(f"{name} {label} @ {base + found:#010x} (aligned={found % 4 == 0})")
                start = found + 1


if __name__ == "__main__":
    main()
