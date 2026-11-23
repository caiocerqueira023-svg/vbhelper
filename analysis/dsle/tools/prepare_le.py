"""Prepare Lost Evolution analysis images: decompress ARM9/overlays and wrap them as ELF32.

Usage: prepare_le.py <rom.nds> <out-dir>
Writes <out>/arm9.bin, <out>/ovlNNN.bin and matching .elf wrappers at the RAM addresses from the
overlay table, reusing the decompressor port in decompress_le.py.
"""
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from decompress_le import decompress, decompress_arm9  # noqa: E402


def u32(data, at):
    return struct.unpack_from("<I", data, at)[0]


def write_elf(path, data, vaddr, entry):
    """Minimal ELF32 little-endian ARM with one PT_LOAD at the image base."""
    ident = b"\x7fELF" + bytes([1, 1, 1, 0]) + b"\x00" * 8
    ehdr = bytearray(52)
    ehdr[0:16] = ident
    struct.pack_into("<HH", ehdr, 16, 2, 40)          # e_type=EXEC, e_machine=ARM
    struct.pack_into("<I", ehdr, 20, 1)               # e_version
    struct.pack_into("<I", ehdr, 24, entry)           # e_entry
    struct.pack_into("<I", ehdr, 28, 52)              # e_phoff
    struct.pack_into("<I", ehdr, 32, 0)               # e_shoff
    struct.pack_into("<I", ehdr, 36, 0x05000000)      # e_flags (EF_ARM_EABI_VER5)
    struct.pack_into("<HH", ehdr, 40, 52, 32)         # e_ehsize, e_phentsize
    struct.pack_into("<HH", ehdr, 44, 1, 0)           # e_phnum, e_shentsize
    struct.pack_into("<HH", ehdr, 48, 0, 0)           # e_shnum, e_shstrndx
    phdr = struct.pack("<8I", 1, 0x54, vaddr, vaddr, len(data), len(data), 5, 0x1000)
    path.write_bytes(bytes(ehdr) + phdr + data)


def main():
    rom_path = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    rom = rom_path.read_bytes()
    arm9_offset, arm9_entry, arm9_ram, arm9_size = (
        u32(rom, 0x20), u32(rom, 0x24), u32(rom, 0x28), u32(rom, 0x2C))
    compressed = rom[arm9_offset:arm9_offset + arm9_size]
    arm9 = decompress_arm9(compressed)
    (out / "arm9.bin").write_bytes(arm9)
    write_elf(out / "arm9.elf", arm9, arm9_ram, arm9_entry)
    print(f"arm9: {len(compressed)} -> {len(arm9)} bytes at {arm9_ram:#x} entry {arm9_entry:#x}")

    ovt_offset, ovt_size = u32(rom, 0x50), u32(rom, 0x54)
    fat_offset, fat_size = u32(rom, 0x48), u32(rom, 0x4C)
    overlays = []
    for index in range(ovt_size // 32):
        fields = struct.unpack_from("<8I", rom, ovt_offset + index * 32)
        overlay_id, ram, ram_size, bss, sinit, send, file_id, flags = fields
        file_start, file_end = struct.unpack_from("<II", rom, fat_offset + file_id * 8)
        raw = rom[file_start:file_end]
        expanded = decompress(raw)
        if len(expanded) != ram_size:
            # Overlay 5 is a tiny stub whose table size includes padding; keep the decoded bytes.
            print(f"note: overlay {index}: decompressed {len(expanded)} != table size {ram_size:#x}")
        name = f"ovl{index:03d}"
        (out / f"{name}.bin").write_bytes(expanded)
        write_elf(out / f"{name}.elf", expanded, ram, sinit)
        overlays.append(dict(index=index, ram=f"{ram:#x}", size=ram_size, sinit=f"{sinit:#x}",
                             compressed=len(raw)))
        print(f"{name}: {len(raw)} -> {len(expanded)} bytes at {ram:#x} sinit {sinit:#x}")
    (out / "layout.json").write_text(json.dumps(dict(arm9=len(arm9), overlays=overlays), indent=1),
                                     encoding="utf-8")


if __name__ == "__main__":
    main()
