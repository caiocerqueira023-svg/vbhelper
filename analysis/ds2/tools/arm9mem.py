"""Read ARM9/overlay memory by virtual address from the extracted images.

Usage:
  arm9mem.py <image> <addr>            hexdump up to 64 bytes
  arm9mem.py <image> u32 <addr>        one u32
  arm9mem.py <image> ptr <addr>        follow one pointer and dump there
  arm9mem.py <image> str <addr>        decode a 0xFFFF-terminated glyph string
Image argument: arm9 | ovlNNN (resolved against the dawn analysis directory).
"""
import struct
import sys
from pathlib import Path

DIR = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn")
IMAGE_BASES = {"arm9": 0x02000000}


def load(name):
    path = DIR / f"{name}.bin"
    data = path.read_bytes()
    base = IMAGE_BASES.get(name)
    if base is None:
        # overlays: parse from rom.json
        import json
        rom = json.loads((DIR / "rom.json").read_text())
        for ov in rom["overlays"]:
            if f"ovl{ov['index']:03d}" == name:
                base = ov["ram"]
                break
    if base is None:
        raise SystemExit(f"unknown image base for {name}")
    return data, base


GLYPHS = {0x00: " "}
for i in range(10):
    GLYPHS[0x01 + i] = str(i)
for code, ch in {0x0B: "/", 0x0C: "%", 0x0D: ":", 0x0E: "'", 0x0F: '"', 0x10: "\u00b7", 0x11: ",",
                 0x12: ".", 0x13: "!", 0x14: "?", 0x15: "(", 0x16: ")", 0x17: "+", 0x18: "-",
                 0x19: "=", 0x54: "*", 0x5B: "&", 0x69: "#", 0x6A: ";", 0x6B: "$"}.items():
    GLYPHS[code] = ch
for i in range(26):
    GLYPHS[0x20 + i] = chr(ord("A") + i)
    GLYPHS[0x3A + i] = chr(ord("a") + i)


def decode(data, off, max_codes=64):
    out = []
    for i in range(max_codes):
        if off + i * 2 + 2 > len(data):
            break
        code = struct.unpack_from("<H", data, off + i * 2)[0]
        if code in (0xFFFF, 0xFFFE):
            return "".join(out)
        out.append(GLYPHS.get(code, "?"))
    return "".join(out)


def main():
    name = sys.argv[1]
    data, base = load(name)
    cmd = sys.argv[2]
    if cmd in ("u32", "ptr", "str"):
        addr = int(sys.argv[3], 0)
        off = addr - base
        if off < 0 or off >= len(data):
            raise SystemExit(f"address {addr:#x} outside {name} ({base:#x}..{base + len(data):#x})")
        if cmd == "u32":
            print(f"{addr:#010x} = {struct.unpack_from('<I', data, off)[0]:#010x} "
                  f"({struct.unpack_from('<i', data, off)[0]})")
        elif cmd == "ptr":
            value = struct.unpack_from("<I", data, off)[0]
            print(f"{addr:#010x} -> {value:#010x}")
            target = value - base
            if 0 <= target < len(data):
                for i in range(0, 64, 16):
                    print(f"  {value + i:#010x}: " + " ".join(f"{b:02x}" for b in data[target + i:target + i + 16]))
        else:
            print(repr(decode(data, off)))
        return
    addr = int(cmd, 0)
    off = addr - base
    for i in range(0, 64, 16):
        print(f"{addr + i:#010x}: " + " ".join(f"{b:02x}" for b in data[off + i:off + i + 16]))


if __name__ == "__main__":
    main()
