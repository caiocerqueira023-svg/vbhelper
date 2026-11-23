"""Reconnaissance for the Digimon Story: Lost Evolution save image.

The supplied save is a 64 KiB image with the live data at 0x0 and an all-zero second slot. The
header carries an ASCII tag ("DM3_000") where Dawn/Dusk used "DSDIG2". This tool finds the checksum
scheme, the copy geometry and the ASCII name region before any layout assumptions are made.
"""
import hashlib
import re
import struct
import sys
import zlib
from pathlib import Path

SAVE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
    r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
ARM9 = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\dsle\arm9.bin")


def u32(data, at):
    return struct.unpack_from("<I", data, at)[0]


def hexdump(data, at, length=0x80, label=""):
    print(f"--- {label} {at:#x}")
    for i in range(0, length, 16):
        chunk = data[at + i:at + i + 16]
        print(f"  {at + i:#06x}: " + " ".join(f"{b:02x}" for b in chunk))


def checksum_candidates(data):
    stored = data[0:16]
    print(f"stored header: {stored.hex()}")
    candidates = []
    for end in (0x7B2C, 0x7B80, 0x8000, len(data)):
        for zeroed_from, label in ((0x0, "zero16"), (0x10, "from16")):
            if zeroed_from == 0x0:
                body = b"\x00" * 16 + data[0x10:end]
            else:
                body = data[0x10:end]
            digest = hashlib.md5(body).digest()
            candidates.append((label, end, digest))
    for label, end, digest in candidates:
        if digest == stored:
            print(f"MD5 MATCH: {label} .. {end:#x}")
        elif digest[::-1] == stored:
            print(f"MD5 MATCH (byte-reversed): {label} .. {end:#x}")
    # CRC32 little endian at other offsets?
    print(f"crc32(0x10..0x7b2c) = {zlib.crc32(data[0x10:0x7B2C]):#010x}")
    # Which bytes of the 16-byte header change if we flip one body byte? Shows hash coverage later.


def region_map(data):
    print("\n--- non-zero extent")
    blocks = []
    for at in range(0, len(data), 0x400):
        chunk = data[at:at + 0x400]
        non_zero = sum(1 for b in chunk if b)
        blocks.append((at, non_zero))
    for at, non_zero in blocks:
        if non_zero:
            print(f"  {at:#06x}: {non_zero}/1024 non-zero")
    print("\n--- ascii strings >=6, first 60")
    seen = 0
    for match in re.finditer(rb"[ -~]{6,}", data):
        seen += 1
        if seen > 60:
            break
        print(f"  {match.start():#06x}: {match.group()[:40]!r}")


def main():
    data = SAVE.read_bytes()
    print(f"save {SAVE.name}: {len(data)} bytes")
    hexdump(data, 0, 0x40, "copy1 header")
    hexdump(data, 0x18C0, 0x120, "asia melk")
    checksum_candidates(data)
    # Test copy-2 detection: compare each 0x7B2C window for all-zero content.
    print("\n--- all-zero windows")
    for base in (0x0, 0x7B80):
        window = data[base:base + 0x7B2C]
        print(f"  {base:#06x}: all_zero={all(b == 0 for b in window)} "
              f"all_ff={all(b == 0xFF for b in window)} first16={window[:16].hex()}")
    region_map(data)
    # Arm9 magic search
    arm9 = ARM9.read_bytes()
    for needle in (b"DM3_000", b"DM3", b"DSDIG", b"DS3"):
        hits = []
        start = 0
        while True:
            at = arm9.find(needle, start)
            if at < 0:
                break
            hits.append(at)
            start = at + 1
        print(f"\narm9 {needle!r}: {len(hits)} hits {[hex(h) for h in hits[:10]]}")


if __name__ == "__main__":
    main()
