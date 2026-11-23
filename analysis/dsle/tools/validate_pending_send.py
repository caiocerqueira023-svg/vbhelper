"""Validate the pending mirrored send candidate/current Lost Evolution save."""
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
CAND = (ROOT / "analysis/dsle/device/now2/sendcand.sav").read_bytes()
NOW = (ROOT / "analysis/dsle/device/now2/le-current.sav").read_bytes()
OP = json.loads((ROOT / "analysis/dsle/device/now2/sendop.json").read_text(encoding="utf-8"))

print("op phase:", OP.get("phase"), "generated", OP.get("generated"), "replaced", OP.get("replaced"))
print("sourceRevision:", OP.get("sourceRevision"))
print("candidateRevision:", OP.get("candidateRevision"))
record = OP.get("record") or {}
print("record:", record.get("recordKey"), record.get("nativeIdentity"), record.get("nativeSpeciesId"),
      record.get("storedName"), record.get("container"))
print("current save sha256:", hashlib.sha256(NOW).hexdigest())
print("candidate sha256:   ", hashlib.sha256(CAND).hexdigest())
print("current == candidate:", NOW == CAND)

MARKER = 0x030405F0


def analyze(name, data):
    print(f"\n== {name}")
    for base in (0, 0x8000):
        copied = bytearray(data[base:base + 0x8000])
        stored = bytes(copied[:16])
        copied[:16] = b"\x00" * 16
        valid = hashlib.md5(bytes(copied)).digest() == stored
        seq = struct.unpack_from("<I", data, base + 0x18)[0]
        records = []
        for slot in range(40):
            at = base + 0x1850 + slot * 0x78
            identity = struct.unpack_from("<Q", data, at)[0]
            if identity:
                records.append((slot, hex(identity), struct.unpack_from("<H", data, at + 0x0C)[0]))
        party = [struct.unpack_from("<Q", data, base + 0x3E8 + i * 8)[0] for i in range(8)]
        farm_count = struct.unpack_from("<I", data, base + 0x11F0)[0]
        farm = []
        for group in range(min(farm_count, 4)):
            gb = base + 0x11F4 + group * 0xD0
            farm.extend(struct.unpack_from("<Q", data, gb + 0x18 + i * 8)[0] for i in range(8))
        next_id = struct.unpack_from("<Q", data, base + 0x4090)[0]
        print(f" copy {base // 0x8000 + 1}: md5={valid} seq={seq} nextId={hex(next_id)}")
        print("   records:", records)
        print("   party:", [hex(v) for v in party])
        print("   farm :", [hex(v) for v in farm])


analyze("current", NOW)
analyze("candidate", CAND)
# Compare bodies (excluding header and seq)
body_equal = NOW[0x1C:0x8000] == NOW[0x801C:0x10000]
print("\ncurrent copies same body (0x1C..0x8000):", body_equal)
