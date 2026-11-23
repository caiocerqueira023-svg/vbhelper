"""Diff the app-edited copy against the genuine game copy to enumerate every touched region."""
import hashlib
import struct
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
BADD = (ROOT / "analysis/dsle/device/now/le-current.sav").read_bytes()
GOOD = (ROOT / "analysis/dsle/device/now/le-backup1008.sav").read_bytes()

# compare copy2 of good (game 10:04) and copy2 of bad (app-edited)
good = GOOD[0x8000:0x10000]
bad = BADD[0x8000:0x10000]
diffs = [(i, good[i], bad[i]) for i in range(0x8000) if good[i] != bad[i]]
print(f"differing bytes copy2: {len(diffs)}")
start = None
prev = None
for offset, _, _ in diffs:
    if start is None:
        start = prev = offset
    elif offset != prev + 1:
        print(f"  region {start:#06x}..{prev:#06x} ({prev - start + 1} bytes)")
        start = prev = offset
    else:
        prev = offset
if start is not None:
    print(f"  region {start:#06x}..{prev:#06x} ({prev - start + 1} bytes)")

# Show the record and container windows explicitly.
for label, window in (
    ("records", (0x1850, 0x1850 + 40 * 0x78)),
    ("party", (0x3E8, 0x3E8 + 64)),
    ("farm", (0x11F0, 0x11F0 + 4 + 4 * 0xD0)),
    ("farm2", (0x1500, 0x15C0)),
    ("team", (0x159C, 0x159C + 64)),
    ("nextid", (0x4090, 0x409C)),
):
    a, b = window
    changed = [i for i in range(a, b) if good[i] != bad[i]]
    print(f"{label}: {len(changed)} changed bytes {[hex(i) for i in changed[:12]]}")
