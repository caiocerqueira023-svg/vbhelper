"""Try to decompress DraStic savestates and search the decoded data for Lost Evolution identities."""
import struct
import zlib
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
MARKER = 0x030405F0


def needle(low):
    return struct.pack("<II", low, MARKER)


patterns = {
    "tag": b"DM3_000\x00",
    "id0": needle(0),
    "id1": needle(1),
    "id2": needle(2),
    "id3": needle(3),
    "id4": needle(4),
    "id5": needle(5),
    "id6": needle(6),
    "gabumon": b"Gabumon",
}

for name in ("temp.dss", "le-slot8.dss"):
    data = (ROOT / "analysis/dsle/device/state" / name).read_bytes()
    print(f"\n== {name}: {len(data)} bytes")
    decoded_parts = []
    for offset in range(len(data) - 2):
        if data[offset] == 0x78 and data[offset + 1] in (0x01, 0x5E, 0x9C, 0xDA):
            try:
                out = zlib.decompress(data[offset:])
            except zlib.error:
                try:
                    decompressor = zlib.decompressobj()
                    out = decompressor.decompress(data[offset:])
                    out += decompressor.flush()
                except zlib.error:
                    continue
            if len(out) > 1024:
                decoded_parts.append((offset, out))
    print(f"  zlib streams decoded: {len(decoded_parts)}")
    for offset, out in decoded_parts[:6]:
        counts = {label: out.count(pattern) for label, pattern in patterns.items()}
        print(f"   stream at {hex(offset)}: {len(out)} bytes {counts}")
    combined = b"".join(out for _, out in decoded_parts)
    if combined:
        Path(f"analysis/dsle/device/state/{name}.decoded").write_bytes(combined)
        print(f"  combined decoded size: {len(combined)}")
