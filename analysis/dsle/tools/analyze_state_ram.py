"""Analyze the DraStic NDS RAM dump for the Lost Evolution roster/party state at 10:04."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
RAM = (ROOT / "analysis/dsle/device/state/temp.dss.decoded").read_bytes()
MARKER = 0x030405F0


def ident(low):
    return (MARKER << 32) | low


def bytes_of(value):
    return struct.pack("<Q", value)


print("RAM size", len(RAM))
for low in range(7):
    pattern = bytes_of(ident(low))
    hits = []
    start = 0
    while True:
        at = RAM.find(pattern, start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    print(f"identity id{low}: {len(hits)} hits {[hex(h) for h in hits]}")

# Locate the record table: a run of 0x78-byte stride records whose +0x0C is the species and
# +0x00 is an identity. Search for Agumon record 0 (species 77, identity id0).
agumon = bytearray(0x78)
struct.pack_into("<Q", agumon, 0, ident(0))
struct.pack_into("<H", agumon, 0x0C, 77)
pattern = bytes(agumon[0x0C:0x0E]) + b""  # placeholder
hits = []
start = 0
while True:
    at = RAM.find(bytes_of(ident(0)), start)
    if at < 0:
        break
    hits.append(at)
    start = at + 1

record_bases = []
for hit in hits:
    base = hit
    species = struct.unpack_from("<H", RAM, base + 0x0C)[0]
    level = RAM[base + 0x3C]
    if 0 < species < 500 and 1 <= level <= 99:
        record_bases.append((base, species, level))

print("\ncandidate record-0 bases (identity id0 with plausible species/level):")
for base, species, level in record_bases:
    print(f"  {base:#x} species={species} level={level}")
    ids = []
    for slot in range(10):
        at = base + slot * 0x78
        value = struct.unpack_from("<Q", RAM, at)[0]
        sp = struct.unpack_from("<H", RAM, at + 0x0C)[0]
        lv = RAM[at + 0x3C]
        ids.append((slot, hex(value), sp, lv))
    for entry in ids:
        print("    ", entry)

# Party array: find the three-member sequence {id0,id4,id3} as u64s.
party = bytes_of(ident(0)) + bytes_of(ident(4)) + bytes_of(ident(3))
hits = []
start = 0
while True:
    at = RAM.find(party, start)
    if at < 0:
        break
    hits.append(at)
    start = at + 1
print("\nparty sequence {0,4,3} hits:", [hex(h) for h in hits])
for hit in hits:
    container = hit - 0x1C  # accessors read elements at container+0x1C
    print(f"\n  elements at {hit:#x}; container base ~{container:#x}")
    print("  header u32s:", [hex(struct.unpack_from('<I', RAM, container + i * 4)[0]) for i in range(7)])
    elements = [struct.unpack_from("<Q", RAM, hit + i * 8)[0] for i in range(8)]
    print("  elements:", [hex(v) for v in elements])
    print("  next id after array:", hex(struct.unpack_from("<Q", RAM, hit + 8 * 8)[0]))

# Farm: count + group residents (identity id2/id1) near any hit of id2.
for low in (1, 2):
    hits = [i for i in range(len(RAM)) if RAM.startswith(bytes_of(ident(low)), i)]
    for hit in hits[:6]:
        species = struct.unpack_from("<H", RAM, hit + 0x0C)[0]
        print(f"\nid{low} hit {hit:#x} as-record species={species}")
