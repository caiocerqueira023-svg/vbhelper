"""Print Lost Evolution species names 160..368 to inspect the ordering (stage/line grouping)."""
import json
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
asset = json.loads((ROOT / "app/src/main/assets/le_species.json").read_text(encoding="utf-8"))
names = asset["names"]
for start in range(160, 368, 20):
    row = "  ".join(f"{sid}:{names.get(str(sid), '?')}" for sid in range(start, min(start + 20, 368)))
    print(row)
