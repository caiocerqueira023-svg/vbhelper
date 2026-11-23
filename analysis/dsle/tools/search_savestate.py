"""Search DraStic savestates for the Lost Evolution identities (is the state raw NDS RAM?)."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")

MARKER = 0x030405F0


def needle(low):
    return struct.pack("<II", low, MARKER)


def search(data, pattern):
    hits = []
    start = 0
    while True:
        at = data.find(pattern, start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    return hits


for name in ("temp.dss", "le-slot8.dss"):
    path = ROOT / "analysis/dsle/device/state" / name
    data = path.read_bytes()
    print(f"\n== {name}: {len(data)} bytes, header {data[:32].hex()}")
    for label, pattern in (
        ("identity id0", needle(0)),
        ("identity id1", needle(1)),
        ("identity id2", needle(2)),
        ("identity id3", needle(3)),
        ("identity id4", needle(4)),
        ("identity id5", needle(5)),
        ("identity id6", needle(6)),
        ("tag DM3_000", b"DM3_000\x00"),
        ("Gabumon ascii", b"Gabumon"),
        ("name andre", b"\x82`\x82\x8e\x82\x84\x82\x92\x82\x85"),
    ):
        hits = search(data, pattern)
        print(f"  {label}: {len(hits)} hits {[hex(h) for h in hits[:5]]}")
