"""Parse the Lost Evolution roster records and decode the one Shift-JIS name."""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
NAMES = json.loads((ROOT / "analysis/dsle/le_species_names.json").read_text(encoding="utf-8"))["names"]

ROSTER = 0x1850
STRIDE = 0x78
COUNT = 40


def main():
    save = SAVE.read_bytes()
    base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
    print(f"newest copy {base:#x}")
    for slot in range(COUNT):
        at = base + ROSTER + slot * STRIDE
        index = struct.unpack_from("<I", save, at)[0]
        magic1 = struct.unpack_from("<I", save, at + 4)[0]
        magic2 = struct.unpack_from("<I", save, at + 8)[0]
        species = struct.unpack_from("<H", save, at + 0x0C)[0]
        raw = save[at + 0x0E:at + 0x0E + 16]
        ascii_name = raw.split(b"\x00")[0]
        try:
            ascii_text = ascii_name.decode("ascii")
        except UnicodeDecodeError:
            ascii_text = None
        try:
            sjis_text = raw.split(b"\x00")[0].decode("cp932")
        except UnicodeDecodeError:
            sjis_text = None
        used = index != 0 or species != 0
        if not used:
            print(f"slot {slot:2d}: empty")
            continue
        print(f"slot {slot:2d}: index={index} magic={magic1:#010x}/{magic2:#010x} species={species} "
              f"({NAMES[species] if species < len(NAMES) else '?'}) ascii={ascii_text!r} sjis={sjis_text!r}")
        print(f"   raw: {' '.join(f'{b:02x}' for b in save[at:at + 0x78])}")

    print("\n=== byte-mapped unknown regions")
    for at, label in ((0x1000, "0x1000"), (0x1040, "0x1040"), (0x11C0, "0x11c0"), (0x1400, "0x1400"),
                      (0x1B00, "0x1b00"), (0x2C00, "0x2c00"), (0x2C40, "0x2c40")):
        chunk = save[base + at:base + at + 0x40]
        print(f"  {label}: " + " ".join(f"{b:02x}" for b in chunk))
    print("\n=== u32 sweep for count-like values near roster")
    for at in range(0x1800, 0x1B00, 4):
        value = struct.unpack_from("<I", save, base + at)[0]
        if value in (4, 5, 6, 40):
            print(f"  {at:#06x}: {value}")
    print("\n=== party-ish arrays after roster")
    for at in range(0x2B00, 0x2D00, 4):
        value = struct.unpack_from("<I", save, base + at)[0]
        if value:
            print(f"  {at:#06x}: {value:#x}")


if __name__ == "__main__":
    main()
