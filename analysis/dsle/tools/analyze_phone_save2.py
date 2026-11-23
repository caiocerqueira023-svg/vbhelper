"""Analyze the newest phone Lost Evolution save after the second user attempt."""
import hashlib
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
now = (ROOT / "analysis/dsle/device/phone-save/le-now2.sav").read_bytes()
print("sha256", hashlib.sha256(now).hexdigest(), "size", len(now))

for base in (0, 0x8000):
    copied = bytearray(now[base:base + 0x8000])
    copied[:16] = b"\x00" * 16
    valid = hashlib.md5(bytes(copied)).digest() == now[base:base + 16]
    seq = struct.unpack_from("<I", now, base + 0x18)[0]
    print(f"copy {base // 0x8000 + 1}: md5={valid} seq={seq} tag={now[base + 0x10:base + 0x18]!r}")

for base in (0, 0x8000):
    records = []
    for slot in range(40):
        at = base + 0x1850 + slot * 0x78
        identity = struct.unpack_from("<Q", now, at)[0]
        if identity:
            species = struct.unpack_from("<H", now, at + 0x0C)[0]
            level = now[at + 0x3C]
            raw = now[at + 0x0E:at + 0x20].split(b"\x00")[0]
            records.append((slot, hex(identity), species, level, raw))
    party = [struct.unpack_from("<Q", now, base + 0x3E8 + slot * 8)[0] for slot in range(8)]
    farm_count = struct.unpack_from("<I", now, base + 0x11F0)[0]
    farm = []
    for group in range(min(farm_count, 4)):
        group_base = base + 0x11F4 + group * 0xD0
        farm.extend(struct.unpack_from("<Q", now, group_base + 0x18 + slot * 8)[0] for slot in range(8))
    next_id = struct.unpack_from("<Q", now, base + 0x4090)[0]
    print(f"\ncopy {base // 0x8000 + 1} records:")
    for entry in records:
        print("  ", entry)
    print("  party:", [hex(v) for v in party])
    print("  farm:", [hex(v) for v in farm], "farmCount:", farm_count)
    print("  nextId:", hex(next_id))

# Search for the identity we sent earlier (id 5 marker) and any Machinedramon species 265 records.
needle5 = struct.pack("<Q", (0x030405F0 << 32) | 5)
print("\nidentity id5 present at:", [hex(i) for i in range(len(now)) if now[i:i + 8] == needle5])
for base in (0, 0x8000):
    for slot in range(40):
        at = base + 0x1850 + slot * 0x78
        species = struct.unpack_from("<H", now, at + 0x0C)[0]
        if species == 265:
            print("Machinedramon record at copy", base // 0x8000 + 1, "slot", slot)
