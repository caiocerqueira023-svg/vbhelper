"""Search the Lost Evolution header (+0x1C..+0x38) for self-checksum relationships."""
import itertools
import struct
import zlib
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
BACKUP = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()
PC = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()  # same sample; replaced below if available
# The 07:56 game save is the original PC file:
pc_path = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
               r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
OLD = pc_path.read_bytes() if pc_path.exists() else BACKUP

samples = {"backup": BACKUP[0:0x8000], "old": OLD[0:0x8000]}


def sums(data):
    return {
        "crc32": zlib.crc32(data) & 0xFFFFFFFF,
        "adler": zlib.adler32(data) & 0xFFFFFFFF,
        "sum8": sum(data) & 0xFFFFFFFF,
        "sum16": sum(struct.unpack_from("<" + "H" * (len(data) // 2), data)) & 0xFFFF if len(data) % 2 == 0 else -1,
        "sum32": sum(struct.unpack_from("<" + "I" * (len(data) // 4), data)) & 0xFFFFFFFF if len(data) % 4 == 0 else -1,
        "sum32x": sum(struct.unpack_from(">" + "I" * (len(data) // 4), data)) & 0xFFFFFFFF if len(data) % 4 == 0 else -1,
        "xor32": 0,
        "md5_0": 0,
        "sha1_0": 0,
    }


for label, slot in samples.items():
    print(f"\n== {label}")
    words = struct.unpack_from("<10I", slot, 0x1C)
    print("  +0x1C..:", [hex(v) for v in words])

# Compare +0x2C/+0x30/+0x34 between the two samples.
for label, slot in samples.items():
    header = struct.unpack_from("<10I", slot, 0x1C)
    print(f"{label}: 1C={header[0]:#x} 20={header[1]:#x} 24={header[2]:#x} 28={header[3]:#x} "
          f"2C={header[4]:#x} 30={header[5]:#x} 34={header[6]:#x} 38={header[7]:#x}")

# Test whether +0x34's halves can be a checksum over a short header range.
import functools


def crc16(data, poly=0xA001):
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ poly if crc & 1 else crc >> 1
    return crc & 0xFFFF


def crc16_ccitt(data):
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


for label, slot in samples.items():
    target = struct.unpack_from("<I", slot, 0x34)[0]
    halves = {target & 0xFFFF, (target >> 16) & 0xFFFF, target & 0xFFFFFFFF}
    print(f"\n-- searching {label} target {target:#010x} halves {[hex(h) for h in halves]}")
    for start in range(0, 0x40):
        for end in range(start + 1, 0x40):
            chunk = slot[start:end]
            values = {
                "sum8": sum(chunk),
                "crc16ibm": crc16(chunk),
                "crc16ccitt": crc16_ccitt(chunk),
                "crc32": zlib.crc32(chunk),
            }
            for name, value in values.items():
                for half in halves:
                    if value == half:
                        print(f"   MATCH {name} [{start:#x}:{end:#x}] = {value:#x}")
