"""Confirm the Lost Evolution container rules and locate the save verification code."""
import hashlib
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
            r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].sav")
ANALYSIS = ROOT / "analysis/dsle"


def find_all(data, needle, limit=12):
    hits = []
    start = 0
    while len(hits) < limit:
        at = data.find(needle, start)
        if at < 0:
            break
        hits.append(at)
        start = at + 1
    return hits


def main():
    save = SAVE.read_bytes()
    print("=== container rules")
    for base in (0x0, 0x8000):
        stored = save[base:base + 16]
        body = b"\x00" * 16 + save[base + 0x10:base + 0x8000]
        digest = hashlib.md5(body).digest()
        sequence = struct.unpack_from("<I", save, base + 0x18)[0]
        tag = save[base + 0x10:base + 0x1A]
        print(f"copy@{base:#06x}: md5_ok={digest == stored} seq={sequence} tag={tag!r}")
        # also try hashing without zeroing? and with tag included?
        print(f"   md5(zero16+body)={digest.hex()} stored={stored.hex()}")
    # header field census
    print("\nheader words copy1:", [hex(v) for v in struct.unpack_from("<16I", save, 0)])
    print("header words copy2:", [hex(v) for v in struct.unpack_from("<16I", save, 0x8000)])

    print("\n=== tag/code search")
    needles = {
        "DM3": b"DM3",
        "DM3_000": b"DM3_000",
        "md5_iv_a": struct.pack("<I", 0x67452301),
        "md5_iv_b": struct.pack("<I", 0xEFCDAB89),
        "md5_iv_c": struct.pack("<I", 0x98BADCFE),
        "md5_iv_d": struct.pack("<I", 0x10325476),
    }
    for name, path in [("arm9", ANALYSIS / "arm9.bin")] + [
            (f"ovl{i:03d}", ANALYSIS / f"ovl{i:03d}.bin") for i in range(6)]:
        data = path.read_bytes()
        found = {}
        for label, needle in needles.items():
            hits = find_all(data, needle)
            if hits:
                found[label] = [hex(hit) for hit in hits]
        print(f"{name}: {found}")

    print("\n=== record area context 0x1800..0x1900 (newest copy)")
    base = 0x8000 if (struct.unpack_from("<I", save, 0x8018)[0] > struct.unpack_from("<I", save, 0x18)[0]) else 0
    for at in range(base + 0x1800, base + 0x1900, 16):
        chunk = save[at:at + 16]
        print(f"  {at - base:#06x}: " + " ".join(f"{b:02x}" for b in chunk))
    print("\n=== after last record")
    for at in range(base + 0x1A30, base + 0x1B00, 16):
        chunk = save[at:at + 16]
        print(f"  {at - base:#06x}: " + " ".join(f"{b:02x}" for b in chunk))


if __name__ == "__main__":
    main()
