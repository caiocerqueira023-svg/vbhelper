"""Compare the two card copies of the current Dawn/Dusk saves on the device."""
import hashlib
import struct
from pathlib import Path

COPIES = [0x0, 0x7B80]
SIZE = 0x7B2C
RECORDS = 0x288
STRIDE = 0x164
COUNT = 40


def u32(d, at):
    return struct.unpack_from("<I", d, at)[0]


def copy_report(path):
    d = Path(path).read_bytes()
    print(f"=== {Path(path).name} ({len(d)} bytes)")
    hashes = []
    for base in COPIES:
        if base + SIZE > len(d):
            continue
        copy = bytearray(d[base:base + SIZE])
        erased = all(b == 0xFF for b in copy)
        zeroed = all(b == 0 for b in copy)
        stored = bytes(copy[0:16])
        digest = hashlib.md5(bytes(copy[0:0]) + b"\x00" * 16 + bytes(copy[16:])).digest()
        seq = u32(d, base + 0x18)
        live = [u32(d, base + RECORDS + i * STRIDE) for i in range(COUNT)]
        live = [i for i in live if i]
        party = [u32(d, base + 0x260 + i * 4) for i in range(10)]
        party = [i for i in party if i]
        body = hashlib.sha256(bytes(copy[16:])).hexdigest()[:16]
        hashes.append(body if not erased else "ERASED")
        print(f"  copy@{base:#06x}: erased={erased} blank={zeroed} md5_ok={digest == stored} seq={seq} "
              f"records={len(live)} party={[hex(x) for x in party]} body_sha={body}")
    print(f"  copies_identical_body={len(set(hashes)) == 1}")


if __name__ == "__main__":
    for arg in [
        r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\dawn-current.sav",
        r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\dusk-current.sav",
    ]:
        copy_report(arg)
