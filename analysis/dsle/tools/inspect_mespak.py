"""Inspect the message pack region that appears to hold a Digimon name list."""
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
PAK = ROOT / "analysis/dsle/nitrofs/MSG/MESPAK00.PAK"


def printable(byte):
    return chr(byte) if 32 <= byte < 127 else "."


def main():
    data = PAK.read_bytes()
    for start, end in ((0x2800, 0x2E00), (0x3000, 0x3400)):
        print(f"\n=== MESPAK00 {start:#05x}..{end:#05x}")
        view = data[start:end]
        text = "".join(printable(b) for b in view)
        for offset in range(0, len(text), 128):
            print(f"  {start + offset:#06x}: {text[offset:offset + 128]!r}")
    # Break the 0x2800..0x2e00 region into 0x00-separated strings with offsets.
    print("\n=== nul-separated tokens (0x2800..0x2e00)")
    at = 0x2800
    while at < 0x2E00:
        end = data.find(b"\x00", at)
        if end < 0:
            break
        token = data[at:end]
        if token and all(32 <= b < 127 for b in token):
            print(f"  {at:#06x}: {token.decode('ascii')!r}")
        at = end + 1
    print("\n=== header words")
    import struct
    print("  ", [hex(v) for v in struct.unpack_from("<16I", data, 0)])


if __name__ == "__main__":
    main()
