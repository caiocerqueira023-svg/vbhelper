"""Validate the current Lost Evolution save and compare it with the pre-test backup."""
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
NOW = (ROOT / "analysis/dsle/device/now/le-current.sav").read_bytes()
BACKUP = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()


def analyze(name, data):
    print(f"\n=== {name}: {len(data)} bytes sha256={hashlib.sha256(data).hexdigest()}")
    for base in (0, 0x8000):
        copied = bytearray(data[base:base + 0x8000])
        stored = bytes(copied[:16])
        copied[:16] = b"\x00" * 16
        digest = hashlib.md5(bytes(copied)).digest()
        seq = struct.unpack_from("<I", data, base + 0x18)[0]
        tag = data[base + 0x10:base + 0x18]
        print(f" copy {base // 0x8000 + 1}: md5={digest == stored} seq={seq} tag={tag!r}")
        records = []
        for slot in range(40):
            at = base + 0x1850 + slot * 0x78
            identity = struct.unpack_from("<Q", data, at)[0]
            if identity:
                species = struct.unpack_from("<H", data, at + 0x0C)[0]
                level = data[at + 0x3C]
                flags = struct.unpack_from("<I", data, at + 0x24)[0]
                namebytes = data[at + 0x0E:at + 0x20].split(b"\x00")[0]
                records.append((slot, hex(identity), species, level, hex(flags), namebytes))
        party = [struct.unpack_from("<Q", data, base + 0x3E8 + i * 8)[0] for i in range(8)]
        farm_count = struct.unpack_from("<I", data, base + 0x11F0)[0]
        farm = []
        for group in range(min(farm_count, 4)):
            gb = base + 0x11F4 + group * 0xD0
            farm.extend(struct.unpack_from("<Q", data, gb + 0x18 + i * 8)[0] for i in range(8))
        next_id = struct.unpack_from("<Q", data, base + 0x4090)[0]
        print(f"   records: {records}")
        print(f"   party: {[hex(v) for v in party]}")
        print(f"   farm (count {farm_count}): {[hex(v) for v in farm]}")
        print(f"   nextId: {hex(next_id)}")


analyze("current", NOW)
analyze("backup-10:08", BACKUP)


def diff_ops():
    for name in ("move-new", "move-old"):
        op = json.loads((ROOT / "analysis/dsle/device/now" / f"{name}.json").read_text(encoding="utf-8"))
        record = op.get("record") or {}
        print(f"\n{name}: {op.get('phase')} source={op.get('sourceRevision')[:12]} candidate={str(op.get('candidateRevision'))[:12]}")
        print(f"   record={record.get('recordKey')} {record.get('nativeIdentity')} species={record.get('nativeSpeciesId')} "
              f"container={record.get('container')} position={record.get('position')} individual={op.get('individualId')}")


diff_ops()
