"""Extract Lost Evolution per-species base data from the static ARM9 tables and compare with the
real save records. This anchors the record field map (stats, level, skills, growth)."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = ROOT / "analysis/dsle/decompressed/arm9.bin"
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")

RAM_BASE = 0x02000000
BASE1_RAM = 0x021325EC          # 376 x 0x38 species parameter blocks
BASE3_RAM = 0x02129FC8          # base3 blocks, 0x14 bytes (name message id at +0xE)
ID_TABLE_RAM = 0x02154278       # 743 entries x 8 bytes: {base1 index, base3 index}
BASE1_COUNT = 0x178
ID_COUNT = 0x2E7

data = ARM9.read_bytes()


def read_u32(ram):
    return struct.unpack_from("<I", data, ram - RAM_BASE)[0]


def read_i32(ram):
    return struct.unpack_from("<i", data, ram - RAM_BASE)[0]


def block1(index):
    at = BASE1_RAM - RAM_BASE + index * 0x38
    return data[at:at + 0x38]


def block3(index):
    at = BASE3_RAM - RAM_BASE + index * 0x14
    return data[at:at + 0x14]


def species(species_id):
    at = ID_TABLE_RAM - RAM_BASE + species_id * 8
    first, second = struct.unpack_from("<ii", data, at)
    return (block1(first) if 0 <= first < BASE1_COUNT else None,
            block3(second) if second >= 0 else None,
            first, second)


def u16(buf, at):
    return struct.unpack_from("<H", buf, at)[0]


def main():
    save = SAVE.read_bytes()
    base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
    names = {77: "Agumon", 82: "Gabumon", 90: "ToyAgumon", 80: "Dorumon", 101: "Falcomon"}
    print("species  template  baseHP baseMP baseATK baseDEF baseSPI baseSPD  growthStage  skills")
    for species_id, name in names.items():
        block, block3, first, second = species(species_id)
        if block is None:
            print(f"{name}: no template ({first}, {second})")
            continue
        stats = [u16(block, 6), u16(block, 8), u16(block, 0xA), u16(block, 0xC), u16(block, 0xE),
                 block[0x11]]
        growth_stage = u16(block, 0x12)
        skills = [u16(block, 0x1E + index * 2) for index in range(6)]
        print(f"{species_id:3d} {first:3d}/{second:3d}  {stats[0]:6d} {stats[1]:6d} {stats[2]:7d} "
              f"{stats[3]:7d} {stats[4]:7d} {stats[5]:7d}  {growth_stage:#06x}  {skills}")
        record = save[base + 0x1850 + (species_id - 77 if False else 0):0]
    print("\nrecord values for comparison")
    for slot in range(5):
        record = save[base + 0x1850 + slot * 0x78:base + 0x1850 + (slot + 1) * 0x78]
        species_id = u16(record, 0x0C)
        print(f"slot{slot} species={species_id} ({names.get(species_id)}) "
              f"hp={u16(record,0x28)}/{u16(record,0x2A)} mp={u16(record,0x2C)}/{u16(record,0x2E)} "
              f"atk={u16(record,0x30)} def={u16(record,0x32)} spi={u16(record,0x34)} spd={record[0x3D]} "
              f"b20={record[0x20]} b38={u16(record,0x38)} b3a={record[0x3A]} b3b={record[0x3B]} "
              f"b3c={record[0x3C]} b3d={record[0x3D]} exp={struct.unpack_from('<I',record,0x40)[0]} "
              f"u44={u16(record,0x44)} skills={[u16(record,0x46+i*2) for i in range(6)]}")

    # Dump the full template for a few species for field discovery.
    for species_id in (77, 82):
        block, block3, first, second = species(species_id)
        print(f"\ntemplate {first} (species {species_id}) 0x38 bytes:")
        for at in range(0, 0x38, 8):
            print(f"  {at:#04x}: {' '.join(f'{b:02x}' for b in block[at:at+8])}")
        if block3 is not None:
            print(f"base3 {second}: {' '.join(f'{b:02x}' for b in block3)}")


if __name__ == "__main__":
    main()
