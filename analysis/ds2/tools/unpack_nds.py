"""NDS ROM unpacker for static analysis: header, ARM9/ARM7, overlays, NitroFS inventory.

Writes extracted images next to the ROM under an analysis directory, never touching the source.
Also wraps an extracted image into a minimal ELF32 so Ghidra/REA can load it at its true RAM
address even where a raw-binary import is awkward.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

BANNER = "DSDIG2"


class Rom:
    def __init__(self, path: Path):
        self.path = path
        self.data = path.read_bytes()
        d = self.data
        self.title = d[0x00:0x0C].decode("ascii", "replace").rstrip("\x00")
        self.game_code = d[0x0C:0x10].decode("ascii", "replace")
        self.maker = d[0x10:0x12].decode("ascii", "replace")
        self.arm9_offset = u32(d, 0x20)
        self.arm9_entry = u32(d, 0x24)
        self.arm9_ram = u32(d, 0x28)
        self.arm9_size = u32(d, 0x2C)
        self.arm7_offset = u32(d, 0x30)
        self.arm7_entry = u32(d, 0x34)
        self.arm7_ram = u32(d, 0x38)
        self.arm7_size = u32(d, 0x3C)
        self.fnt_offset = u32(d, 0x40)
        self.fnt_size = u32(d, 0x44)
        self.fat_offset = u32(d, 0x48)
        self.fat_size = u32(d, 0x4C)
        self.ovt9_offset = u32(d, 0x50)
        self.ovt9_size = u32(d, 0x54)
        self.ovt9_count = self.ovt9_size // 32
        self.ovt7_offset = u32(d, 0x58)
        self.ovt7_size = u32(d, 0x5C)
        self.ovt7_count = self.ovt7_size // 32

    def header(self):
        return {
            "path": str(self.path),
            "bytes": len(self.data),
            "sha256": hashlib.sha256(self.data).hexdigest(),
            "md5": hashlib.md5(self.data).hexdigest(),
            "title": self.title,
            "gameCode": self.game_code,
            "maker": self.maker,
            "arm9": {"romOffset": self.arm9_offset, "entry": hex(self.arm9_entry),
                     "ramAddress": hex(self.arm9_ram), "size": self.arm9_size},
            "arm7": {"romOffset": self.arm7_offset, "entry": hex(self.arm7_entry),
                     "ramAddress": hex(self.arm7_ram), "size": self.arm7_size},
            "fnt": {"offset": self.fnt_offset, "size": self.fnt_size},
            "fat": {"offset": self.fat_offset, "size": self.fat_size},
            "ovt9": {"offset": self.ovt9_offset, "size": self.ovt9_size, "count": self.ovt9_count},
            "ovt7": {"offset": self.ovt7_offset, "size": self.ovt7_size, "count": self.ovt7_count},
        }

    def files(self):
        """FAT table: (file id, start, end)."""
        count = self.fat_size // 8
        return [struct.unpack_from("<II", self.data, self.fat_offset + i * 8) for i in range(count)]

    def overlays9(self):
        entries = []
        for i in range(self.ovt9_count):
            at = self.ovt9_offset + i * 32
            oid, ram, size, bss, sinit, send, file_id, flags = struct.unpack_from("<IIIIIIII", self.data, at)
            entries.append({"index": i, "id": oid, "ram": ram, "size": size, "bss": bss,
                            "sinit": sinit, "send": send, "fileId": file_id, "flags": flags})
        return entries


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


def nitro_dir(data, fnt, fat, sub_id):
    """Yields (full_name, is_dir, child_or_file_id) for one NitroFS directory (recursive caller)."""
    entry = fnt + (sub_id & 0xFFF) * 8
    pos = fnt + u32(data, entry)
    file_no = struct.unpack_from("<H", data, entry + 4)[0]
    while True:
        length = data[pos]
        if length == 0:
            return
        is_dir = (length & 0x80) != 0
        name_len = length & 0x7F
        name = data[pos + 1:pos + 1 + name_len].decode("ascii", "replace")
        pos += 1 + name_len
        if is_dir:
            child = struct.unpack_from("<H", data, pos)[0]
            pos += 2
            yield name, True, child
        else:
            yield name, False, file_no
            file_no += 1


def walk_nitrofs(rom, out_dir):
    data = rom.data
    fnt, fat = rom.fnt_offset, rom.fat_offset
    files = rom.files()

    def recurse(sub_id, prefix, depth):
        if depth > 8:
            return []
        rows = []
        try:
            entries = list(nitro_dir(data, fnt, fat, sub_id))
        except Exception as error:  # noqa: BLE001
            return [{"error": f"{prefix}: {error}"}]
        for name, is_dir, value in entries:
            full = f"{prefix}{name}"
            if is_dir:
                if depth < 8:
                    rows += recurse(value, full + "/", depth + 1)
            else:
                start, end = files[value] if value < len(files) else (0, 0)
                rows.append({"path": full, "fileId": value, "offset": start, "size": end - start})
        return rows

    rows = recurse(0xF000, "/", 0)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "nitrofs.json").write_text(json.dumps(rows, indent=1), encoding="utf-8")
    return rows


def decompress_lz77(src: bytes) -> bytes:
    """NDS LZ77 ('LZ11'-style not assumed; Nintendo DS BIOS LZ77 0x10 header)."""
    if len(src) < 4:
        raise ValueError("short LZ77 stream")
    header = src[0]
    if header != 0x10:
        raise ValueError(f"not an NDS LZ77 stream (0x{header:02X})")
    size = int.from_bytes(src[1:4], "little")
    out = bytearray()
    pos = 4
    while pos < len(src) and len(out) < size:
        flags = src[pos]
        pos += 1
        for bit in range(8):
            if pos >= len(src) or len(out) >= size:
                break
            if flags & (0x80 >> bit):
                b1, b2 = src[pos], src[pos + 1]
                pos += 2
                length = (b1 >> 4) + 3
                disp = ((b1 & 0x0F) << 8 | b2) + 1
                start = len(out) - disp
                if start < 0:
                    raise ValueError("bad LZ77 displacement")
                for i in range(length):
                    out.append(out[start + i])
            else:
                out.append(src[pos])
                pos += 1
    return bytes(out[:size])


def wrap_elf(image: bytes, base: int, entry: int, out_path: Path) -> None:
    """Minimal ELF32/ARM little-endian executable holding one PT_LOAD at [base, base+len)."""
    ident = b"\x7fELF" + bytes([1, 1, 1, 0]) + b"\x00" * 8  # 32-bit, LE, v1, SysV
    ehsize, phentsize, phnum = 52, 32, 1
    data_off = ehsize + phentsize * phnum
    total = data_off + len(image)
    ehdr = ident + struct.pack("<HHIIIIIHHHHHH", 2, 40, 1, entry, ehsize, 0, 0, ehsize,
                               phentsize, phnum, 40, 0, 0)
    phdr = struct.pack("<IIIIIIII", 1, data_off, base, base, len(image), len(image), 5, 0x1000)
    out_path.write_bytes(ehdr + phdr + image)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: unpack_nds.py <rom.nds> <out-dir>")
    rom = Rom(Path(sys.argv[1]))
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    report = {"header": rom.header()}

    arm9 = bytearray(rom.data[rom.arm9_offset:rom.arm9_offset + rom.arm9_size])
    report["arm9FirstWord"] = hex(struct.unpack_from("<I", arm9, 0)[0])
    (out / "arm9.bin").write_bytes(bytes(arm9))
    # The first 0x800 bytes are the encrypted NDS secure area; real code begins at the entry point.
    secure = max(0, rom.arm9_entry - rom.arm9_ram)
    image = bytearray(arm9)
    image[:secure] = b"\x00" * secure
    wrap_elf(bytes(image), rom.arm9_ram, rom.arm9_entry, out / "arm9.elf")
    arm7 = rom.data[rom.arm7_offset:rom.arm7_offset + rom.arm7_size]
    (out / "arm7.bin").write_bytes(arm7)
    wrap_elf(arm7, rom.arm7_ram, rom.arm7_entry, out / "arm7.elf")

    overlays = []
    files = rom.files()
    for entry in rom.overlays9():
        file_id = entry["fileId"]
        start, end = files[file_id] if file_id < len(files) else (0, 0)
        raw = rom.data[start:end]
        compressed = (entry["size"] < len(raw)) or (entry["flags"] & 0x01000000) != 0
        data = raw
        mode = "raw"
        if compressed:
            try:
                data = decompress_lz77(raw)
                mode = "lz77"
            except Exception:  # noqa: BLE001
                mode = "compressed-unknown"
        (out / f"ovl{entry['index']:03d}.bin").write_bytes(data)
        if data:
            wrap_elf(data, entry["ram"], entry["ram"], out / f"ovl{entry['index']:03d}.elf")
        overlays.append({**entry, "fileBytes": end - start, "extracted": len(data), "mode": mode,
                         "ramHex": hex(entry["ram"])})
    report["overlays"] = overlays
    nitro = walk_nitrofs(rom, out)
    report["nitrofsEntries"] = len(nitro)
    (out / "rom.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    print(json.dumps({k: report[k] for k in report if k != "overlays"}, indent=1))
    print(f"overlays={len(overlays)} compressed={sum(1 for o in overlays if o['mode'] != 'raw')}")
    print(f"nitrofs_entries={len(nitro)}")


if __name__ == "__main__":
    main()
