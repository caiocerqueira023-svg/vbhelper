"""Scan every extracted image for pointer values to key save structures."""
import json
import struct
import sys
from pathlib import Path

DIR = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn")

TARGETS = {
    "saveBase_021357c8": 0x021357C8,
    "dsdig2_ptr_0211da08": 0x0211DA08,
    "dsdig2b_ptr_0211fa10": 0x0211FA10,
    "records_02135a50": 0x02135A50,
    "expCurves_0211e440": 0x0211E440,
    "saveDescriptor_0202e994": 0x0202E994,
    "saveSig_02135714": 0x02135714,
}


def main():
    rom = json.loads((DIR / "rom.json").read_text())
    images = [("arm9", 0x02000000, DIR / "arm9.bin")]
    for ov in rom["overlays"]:
        images.append((f"ovl{ov['index']:03d}", ov["ram"], DIR / f"ovl{ov['index']:03d}.bin"))
    for name, base, path in images:
        data = path.read_bytes()
        hits = {}
        for label, value in TARGETS.items():
            pattern = struct.pack("<I", value)
            offsets = []
            start = 0
            while True:
                found = data.find(pattern, start)
                if found < 0:
                    break
                if found % 4 == 0:
                    offsets.append(base + found)
                start = found + 1
            if offsets:
                hits[label] = [hex(a) for a in offsets[:8]]
        if hits:
            print(name, json.dumps(hits))


if __name__ == "__main__":
    main()
