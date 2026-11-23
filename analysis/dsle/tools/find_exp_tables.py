"""Scan Lost Evolution ARM9/overlays for level-up tables (99 increasing values)."""
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
FILES = [("arm9", ROOT / "analysis/dsle/decompressed/arm9.bin")] + [
    (f"ovl{index:03d}", ROOT / f"analysis/dsle/decompressed/ovl{index:03d}.bin") for index in range(4)]


def find_runs(data, width):
    runs = []
    length = 99
    for start in range(0, len(data) - length * width, 4):
        values = []
        ok = True
        for index in range(length):
            offset = start + index * width
            if width == 2:
                value = struct.unpack_from("<H", data, offset)[0]
            else:
                value = struct.unpack_from("<I", data, offset)[0]
            if value == 0:
                ok = False
                break
            if values and value <= values[-1]:
                ok = False
                break
            if value > 20_000_000:
                ok = False
                break
            values.append(value)
        if ok and values[0] <= 500 and values[-1] >= 1000:
            runs.append((start, values[0], values[-1]))
    return runs


for name, path in FILES:
    if not path.exists():
        continue
    data = path.read_bytes()
    for width in (2, 4):
        runs = find_runs(data, width)
        # merge overlapping hits (the same table detected at shifted starts)
        merged = []
        for start, first, last in runs:
            if merged and start - merged[-1][0] < 99 * width:
                continue
            merged.append((start, first, last))
        print(f"{name} width={width}: {len(merged)} candidates")
        for start, first, last in merged[:10]:
            limit = len(data) - start
            values = [struct.unpack_from("<H" if width == 2 else "<I", data, start + index * width)[0]
                      for index in range(0, min(99, limit // width))]
            print(f"  off {start:#08x}: first={first} last={last} head={values[:6]}")
