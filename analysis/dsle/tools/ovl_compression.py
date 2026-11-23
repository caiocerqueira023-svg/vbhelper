"""Find the Lost Evolution overlay compression format by scanning for header candidates."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
OVL = [(index, ROOT / f"analysis/dsle/ovl{index:03d}.bin") for index in range(6)]
# From the overlay table.
META = {
    0: dict(ram=0x223BC00, size=0x27D20, bss=0x5A0, sinit=0x2260B10, file=0x17B00),
    1: dict(ram=0x223BC00, size=0x25E40, bss=0x26C0, sinit=0x22604A4, file=0x17DB8),
    2: dict(ram=0x223BC00, size=0x23B00, bss=0x11C0, sinit=0x225F2DC, file=0x157D4),
    3: dict(ram=0x2264100, size=0x118C0, bss=0x29C0, sinit=0x2274B8C, file=0xA188),
    4: dict(ram=0x2264100, size=0x1DA0, bss=0x20, sinit=0x2265E78, file=0x1284),
    5: dict(ram=0x2264100, size=0x60, bss=0x0, sinit=0x2264150, file=0x58),
}


def plausible_thumb_arm(data):
    """Count common prologue/epilogue patterns as a code-likelihood signal."""
    arm_push = sum(1 for i in range(0, len(data) - 4, 2) if data[i:i + 4] in (b"\x10\x40\x2d\xe9", b"\xf0\x4f\x2d\xe9"))
    thumb_push = 0
    thumb_bx = 0
    for i in range(0, len(data) - 2, 2):
        value = struct.unpack_from("<H", data, i)[0]
        if 0xB5F0 <= value <= 0xB5FF:
            thumb_push += 1
        if value == 0x4770:
            thumb_bx += 1
    return arm_push, thumb_push, thumb_bx


def scan_header(index, path):
    data = path.read_bytes()
    meta = META[index]
    targets = {meta["size"], meta["size"] - meta["bss"], meta["file"]}
    print(f"\novl{index}: file={len(data)} ram_size={meta['size']:#x} bss={meta['bss']:#x} "
          f"sinit_off={meta['sinit'] - meta['ram']:#x}")
    print(f"  code-likelihood (arm_push, thumb_push, thumb_bx) = {plausible_thumb_arm(data)}")
    candidates = []
    for offset in range(0, min(len(data), 0x200)):
        marker = data[offset]
        if marker not in (0x00, 0x10, 0x11, 0x20, 0x24, 0x28, 0x30, 0x40):
            continue
        if offset + 4 > len(data):
            break
        decoded = int.from_bytes(data[offset + 1:offset + 4], "little")
        if decoded in targets or abs(decoded - meta["size"]) < 0x100:
            candidates.append((offset, marker, decoded))
    for offset, marker, decoded in candidates[:10]:
        print(f"  header candidate @ {offset:#04x}: type={marker:#04x} decoded={decoded:#x}")
    print(f"  first 24 bytes: {data[:24].hex()}")
    print(f"  last 24 bytes: {data[-24:].hex()}")


def main():
    for index, path in OVL:
        scan_header(index, path)


if __name__ == "__main__":
    main()
