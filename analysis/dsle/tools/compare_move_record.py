"""Compare the failed move's journaled record with a fresh read of its source save."""
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
op = json.loads((ROOT / "analysis/dsle/device/ops/move1.json").read_text(encoding="utf-8"))
save = (ROOT / "analysis/dsle/device/ops/m1-orig.sav").read_bytes()
record = op["record"]
print("journaled record:")
for key in ("recordKey", "container", "position", "nativeSpeciesId", "storedName", "level",
            "nativeIdentity", "identityCandidate", "membership", "issues", "totalExp"):
    print(f"  {key}: {record.get(key)!r}")
print("  nativeRecordBase64 len:", len(record.get("nativeRecordBase64") or ""))
print("  nativeParameters:", record.get("nativeParameters"))

MARKER = 0x030405F0


def identity_at(slot):
    at = 0x8000 + 0x1850 + slot * 0x78  # kind of: use newest copy base below
    return struct.unpack_from("<Q", save, at)[0]


# Find which copy holds the record key's slot and the identity at that slot.
copy_number = 2
base = (copy_number - 1) * 0x8000
slot = 3
at = base + 0x1850 + slot * 0x78
identity = struct.unpack_from("<Q", save, at)[0]
species = struct.unpack_from("<H", save, at + 0x0C)[0]
raw = save[at:at + 0x78]
print(f"\nsource copy {copy_number} slot {slot}: identity={identity:#x} species={species}")

# party/farm arrays for that copy
party = [struct.unpack_from("<Q", save, base + 0x3E8 + i * 8)[0] for i in range(8)]
farm_count = struct.unpack_from("<I", save, base + 0x11F0)[0]
farm = []
for group in range(min(farm_count, 4)):
    group_base = base + 0x11F4 + group * 0xD0
    farm.extend(struct.unpack_from("<Q", save, group_base + 0x18 + i * 8)[0] for i in range(8))
print("  party:", [hex(v) for v in party])
print("  farm:", [hex(v) for v in farm])
if identity in party:
    print("  expected container PARTY position", party.index(identity) + 1)
elif identity in farm:
    print("  expected container FARM position", farm.index(identity) + 1)
else:
    print("  expected container UNASSIGNED position", slot + 1)

import base64
print("\nrecord bytes equal?", base64.b64decode(record["nativeRecordBase64"]) == raw)
print("journaled base64 first bytes:", record["nativeRecordBase64"][:32])
print("raw base64 first bytes:", base64.b64encode(raw).decode()[:32])
