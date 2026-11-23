"""Compare three real Lost Evolution saves: game-written (5 mons), app-written (send), game-written new game."""
import hashlib
import struct
import zlib
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
FILES = {
    "genuine1004": ROOT / "analysis/dsle/device/now/le-backup1008.sav",
    "appsend1130": ROOT / "analysis/dsle/device/now2/sendcand.sav",
    "game_newgame": ROOT / "analysis/dsle/device/now2/le-afteraccident.sav",
}


def slot_md5(data, base):
    copied = bytearray(data[base:base + 0x8000])
    stored = bytes(copied[:16])
    copied[:16] = b"\x00" * 16
    return hashlib.md5(bytes(copied)).digest() == stored


def analyze(label, data):
    print(f"\n===== {label} sha256={hashlib.sha256(data).hexdigest()[:16]}")
    for base in (0, 0x8000):
        index = base // 0x8000 + 1
        words = struct.unpack_from("<14I", data, base)
        seq = struct.unpack_from("<I", data, base + 0x18)[0]
        records = []
        for slot in range(40):
            at = base + 0x1850 + slot * 0x78
            identity = struct.unpack_from("<Q", data, at)[0]
            if identity:
                species = struct.unpack_from("<H", data, at + 0x0C)[0]
                flags = struct.unpack_from("<I", data, at + 0x24)[0]
                records.append(f"slot{slot}:id={identity:#018x}:sp={species}:flags={flags:#x}")
        next_id = struct.unpack_from("<Q", data, base + 0x4090)[0]
        party = [struct.unpack_from("<Q", data, base + 0x3E8 + i * 8)[0] for i in range(8)]
        farm_count = struct.unpack_from("<I", data, base + 0x11F0)[0]
        farm_lists = []
        for group in range(min(farm_count, 4)):
            gb = base + 0x11F4 + group * 0xD0
            farm_lists.append([struct.unpack_from("<Q", data, gb + 0x18 + i * 8)[0] for i in range(8)])
        visitors = [
            struct.unpack_from("<Q", data, base + 0x1534 + i * 0xC)[0]
            for i in range(8)
        ]
        print(f" copy{index} md5={slot_md5(data, base)} seq={seq} nextId={next_id:#018x}")
        print(f"   header[0x1C..]={[hex(w) for w in words[7:]]}")
        print(f"   records={records}")
        print(f"   party={[hex(v) for v in party]}")
        print(f"   farm={[[hex(v) for v in g] for g in farm_lists]}")
        print(f"   visitors(0x1534)={[hex(v) for v in visitors]}")


for label, path in FILES.items():
    if path.exists():
        analyze(label, path.read_bytes())
    else:
        print("missing", label, path)


# Any 32-bit value that could be a checksum of some range? Search a few hashes for each save.
def probe_checksums(label, data):
    slot = data[0:0x8000]
    ranges = [(0x00, 0x8000), (0x10, 0x8000), (0x20, 0x8000), (0x20, 0x409C), (0x20, 0x40A0)]
    found = 0
    for start, end in ranges:
        chunk = slot[start:end]
        values = {
            "crc32": zlib.crc32(chunk) & 0xFFFFFFFF,
            "adler32": zlib.adler32(chunk) & 0xFFFFFFFF,
            "sum32le": sum(struct.unpack_from("<" + "I" * (len(chunk) // 4), chunk)) & 0xFFFFFFFF,
        }
        for name, value in values.items():
            needle = struct.pack("<I", value)
            for at in range(0, len(slot) - 3, 4):
                if slot[at:at + 4] == needle:
                    print(f"CHECKSUM? {label} {name}[{start:#x}:{end:#x}]={value:#010x} found at slot+{at:#x}")
                    found += 1
                    break
    if not found:
        print(f"no simple checksum matches in {label}")


for label, path in FILES.items():
    if path.exists():
        probe_checksums(label, path.read_bytes())
