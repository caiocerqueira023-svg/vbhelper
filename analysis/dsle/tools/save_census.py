"""Census of text and structured regions in the Lost Evolution save."""
import re
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")

save = SAVE.read_bytes()
base = 0x8000 if struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0] else 0
slot = save[base:base + 0x8000]
print(f"slot {base:#x}")


def decode_sjis(raw):
    try:
        return raw.decode("cp932")
    except UnicodeDecodeError:
        return None


print("\n=== physical layout: non-zero blocks")
for start in range(0, 0x8000, 0x200):
    chunk = slot[start:start + 0x200]
    nonzero = sum(1 for byte in chunk if byte)
    if nonzero:
        print(f"  {start:#06x}: {nonzero:4d}/512 non-zero")

print("\n=== ASCII strings >=4")
for match in re.finditer(rb"[ -~]{4,}", slot):
    print(f"  {match.start():#06x}: {match.group()[:50]!r}")

print("\n=== SJIS-looking regions (runs of 0x81-0x9F lead bytes)")
for start in range(0, 0x8000, 16):
    chunk = slot[start:start + 16]
    lead = sum(1 for byte in chunk if 0x81 <= byte <= 0x9F)
    if lead >= 4:
        text = decode_sjis(chunk)
        print(f"  {start:#06x}: {chunk.hex()} -> {text!r}")

print("\n=== candidate u64 id arrays (marker 0x030405F0 interleaved)")
for start in range(0, 0x8000 - 16, 4):
    word0, word1 = struct.unpack_from("<II", slot, start)
    if word0 == 0x030405F0 and word1 < 0x100:
        print(f"  {start:#06x}: marker + id {word1}")
