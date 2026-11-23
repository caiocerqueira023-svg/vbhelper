"""Decode the full-name table region found in ARM9 (around 0x1079E0..0x10B000)."""
import struct
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ARM9 = ROOT / "analysis/ds2/dawn/arm9.bin"


def decode(data, at, limit=32):
    values = struct.unpack_from(f"<{limit}H", data, at)
    text = []
    for value in values:
        if value == 0xFFFF:
            break
        if 0x20 <= value <= 0x39:
            text.append(chr(ord("A") + value - 0x20))
        elif 0x3A <= value <= 0x53:
            text.append(chr(ord("a") + value - 0x3A))
        elif value == 0:
            text.append("\u0000")
            break
        else:
            text.append(f"<{value:#x}>")
    return "".join(text)


def main():
    data = ARM9.read_bytes()
    for at in (0x1079E0, 0x108516, 0x10921A):
        print(f"=== around {at:#x}")
        print("  before:", data[at - 16:at].hex())
        print("  text:", repr(decode(data, at)))
        print("  after:", data[at + 32:at + 64].hex())
        print("  next as text:", repr(decode(data, at + 32, 20)))
    # Walk the region looking for UTF-16-like padded entries with 0xFFFF terminators.
    print("\n=== candidate entries in 0x107000..0x10A000")
    base = 0x107000
    found = []
    for offset in range(base, 0x10A000, 2):
        if offset >= len(data) - 40:
            break
        first = struct.unpack_from("<H", data, offset)[0]
        if not (0x20 <= first <= 0x53):
            continue
        text = decode(data, offset)
        if not text or text[0] == "<":
            continue
        if len(text) < 4 or not text.isprintable():
            continue
        found.append((offset, text))
    seen = set()
    for offset, text in found:
        if text in seen:
            continue
        seen.add(text)
        print(f"  {offset:#07x} {text!r}")
        if len(seen) > 60:
            break


if __name__ == "__main__":
    main()
