"""Print the Lost Evolution species ordering to derive the stage bands."""
import json
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
a = json.loads((ROOT / "app/src/main/assets/le_species.json").read_text(encoding="utf-8"))
names = a["names"]
base = a["baseData"]
for start, end in ((54, 110), (110, 190), (190, 260), (300, 368)):
    print(f"=== ids {start}..{end}")
    for sid in range(start, end):
        name = names.get(str(sid))
        block = base.get(str(sid), {})
        print(f"  {sid:3d} {str(name):20s} hp={block.get('hp')} growth={block.get('growthBits')} "
              f"line={block.get('lineId')}")
