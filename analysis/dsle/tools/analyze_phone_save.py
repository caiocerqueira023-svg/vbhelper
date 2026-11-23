"""Analyze the Lost Evolution save currently on the phone vs the app's written candidate."""
import hashlib
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
now = (ROOT / "analysis/dsle/device/phone-save/le-now.sav").read_bytes()
candidate_path = ROOT / "analysis/dsle/device/sendfail/candidate.sav"
candidate = candidate_path.read_bytes() if candidate_path.exists() else None

print("now size", len(now), "sha256", hashlib.sha256(now).hexdigest())
if candidate is not None:
    print("app-written candidate sha256", hashlib.sha256(candidate).hexdigest())
    print("now == app-written candidate?", now == candidate)

for base in (0, 0x8000):
    copied = bytearray(now[base:base + 0x8000])
    copied[:16] = b"\x00" * 16
    valid = hashlib.md5(bytes(copied)).digest() == now[base:base + 16]
    seq = struct.unpack_from("<I", now, base + 0x18)[0]
    print(f"copy {base // 0x8000 + 1}: md5 ok={valid} seq={seq} tag={now[base + 0x10:base + 0x18]!r}")

needle = struct.pack("<II", 5, 0x030405F0)
hits = [offset for offset in range(len(now)) if now[offset:offset + 8] == needle]
print("sent identity refs at", [hex(offset) for offset in hits])

for base in (0, 0x8000):
    names = []
    for slot in range(40):
        at = base + 0x1850 + slot * 0x78
        low, high = struct.unpack_from("<II", now, at)
        if (low, high) != (0, 0):
            species = struct.unpack_from("<H", now, at + 0x0C)[0]
            level = now[at + 0x3C]
            raw = now[at + 0x0E:at + 0x20].split(b"\x00")[0]
            names.append((slot, low, hex(high), species, level, raw))
    print("copy", base // 0x8000 + 1, "records:")
    for entry in names:
        print("   ", entry)

# party/farm identities for both copies
for base in (0, 0x8000):
    party = [struct.unpack_from("<Q", now, base + 0x3E8 + slot * 8)[0] for slot in range(8)]
    farm_count = struct.unpack_from("<I", now, base + 0x11F0)[0]
    farm = []
    for group in range(min(farm_count, 4)):
        group_base = base + 0x11F4 + group * 0xD0
        farm.extend(struct.unpack_from("<Q", now, group_base + 0x18 + slot * 8)[0] for slot in range(8))
    next_id = struct.unpack_from("<Q", now, base + 0x4090)[0]
    print(f"copy {base // 0x8000 + 1} party={[hex(v) for v in party]} farm={[hex(v) for v in farm]} nextId={hex(next_id)}")
