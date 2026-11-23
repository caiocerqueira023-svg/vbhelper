"""Search for the Lost Evolution per-save integrity words (+0x30 / +0x34) over the slot body."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
BACKUP = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()
PC = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()

# CRC16 implementations
def crc16_ccitt(data, init=0xFFFF, poly=0x1021, refin=False):
    crc = init
    for byte in data:
        if refin:
            byte = int(f"{byte:08b}"[::-1], 2)
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ poly) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


def crc16_ibm(data, init=0xFFFF):
    crc = init
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF


def fletcher16(data):
    lo = hi = 0
    for byte in data:
        lo = (lo + byte) % 255
        hi = (hi + lo) % 255
    return (hi << 8) | lo


def sum16_le(data):
    return sum(struct.unpack_from("<" + "H" * (len(data) // 2), data, 0)) & 0xFFFF


def sum16_be(data):
    return sum(struct.unpack_from(">" + "H" * (len(data) // 2), data, 0)) & 0xFFFF


def checks(data):
    return {
        "crc16_ccitt": crc16_ccitt(data),
        "crc16_ccitt_ref": crc16_ccitt(data, refin=True),
        "crc16_ibm": crc16_ibm(data),
        "fletcher16": fletcher16(data),
        "sum8": sum(data) & 0xFFFF,
        "sum16le": sum16_le(data),
        "sum16be": sum16_be(data),
        "xor8": __import__("functools").reduce(lambda a, b: a ^ b, data, 0),
    }


for label, data in (("backup", BACKUP),):
    for base in (0, 0x8000):
        slot = data[base:base + 0x8000]
        target_hi, target_lo = struct.unpack_from("<HH", slot, 0x34)
        print(f"\n== {label} copy {base // 0x8000 + 1} targets: hi={target_hi:#06x} lo={target_lo:#06x}")
        ranges = {
            "10:8000": (0x10, 0x8000), "18:8000": (0x18, 0x8000), "1c:8000": (0x1C, 0x8000),
            "20:8000": (0x20, 0x8000), "38:8000": (0x38, 0x8000), "40:8000": (0x40, 0x8000),
            "38:40a0": (0x38, 0x40A0), "20:40a0": (0x20, 0x40A0), "40:40a0": (0x40, 0x40A0),
            "38:409c": (0x38, 0x409C), "20:2ff1f": (0x20, 0x20 + 0x2FF1F) if 0x20 + 0x2FF1F <= 0x8000 else (0x20, 0x8000),
            "20:3ae6a": (0x20, 0x20 + 0x3AE6A) if 0x20 + 0x3AE6A <= 0x8000 else (0x20, 0x8000),
        }
        for rname, (start, end) in ranges.items():
            chunk = slot[start:end]
            for cname, value in checks(chunk).items():
                if value == target_lo or value == target_hi or (value ^ 0xFFFF) in (target_lo, target_hi):
                    print(f"  MATCH {cname} {start:#x}:{end:#x} = {value:#06x}")
