"""Find the save-manager context in the post-failure RAM dump and report its state."""
import struct
from pathlib import Path

d = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\dsle\device\now3\le9-ram.bin").read_bytes()
pattern = struct.pack("<II", 0x5F334D44, 0x00303030)
hits = []
pos = 0
while True:
    index = d.find(pattern, pos)
    if index < 0:
        break
    hits.append(index)
    pos = index + 1
print("tag-word pair offsets:", [hex(h) for h in hits][:12])

for hit in hits[:5]:
    ctx = hit - 0x2C
    print(f"--- candidate save ctx at dump offset {ctx:#x} (ram {ctx - 0x31A4A + 0x02000000:#x}?)")
    for offset in (0x00, 0x04, 0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28,
                   0x2C, 0x30, 0x34, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C, 0x50, 0x54, 0xB0, 0xC0, 0xC4):
        value = struct.unpack_from("<I", d, ctx + offset)[0]
        print(f"  +{offset:#04x} = {value:#010x}")
