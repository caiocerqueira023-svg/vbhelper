"""Extract Lost Evolution NitroFS files and locate the species-name table."""
import json
import re
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
ROM = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE"
           r"\Digimon Story - Lost Evolution (Japan) [T-En by Operation Decoded v1.1] [AP fix].nds")
OUT = ROOT / "analysis/dsle/nitrofs"


def extract():
    rom = ROM.read_bytes()
    rows = json.loads((ROOT / "analysis/dsle/nitrofs.json").read_text(encoding="utf-8"))
    for row in rows:
        target = OUT / row["path"].lstrip("/")
        target.parent.mkdir(parents=True, exist_ok=True)
        data = rom[row["offset"]:row["offset"] + row["size"]]
        target.write_bytes(data)
    print(f"extracted {len(rows)} files into {OUT}")


def search_names():
    needles = [b"Gabumon", b"Agumon", b"Coronamon", b"MetalMamemon", b"ToyAgumon", b"Dorumon",
               b"Falcomon", b"Kuramon", b"Chicchimon", b"Chaosmon"]
    for path in sorted(OUT.rglob("*")):
        if not path.is_file():
            continue
        data = path.read_bytes()
        hits = {}
        for needle in needles:
            positions = []
            start = 0
            while len(positions) < 4:
                at = data.find(needle, start)
                if at < 0:
                    break
                positions.append(at)
                start = at + 1
            if positions:
                hits[needle.decode()] = [hex(p) for p in positions]
        if hits:
            rel = path.relative_to(OUT)
            print(f"\n{rel} ({len(data)} bytes)")
            for name, positions in hits.items():
                print(f"   {name}: {positions}")


def inspect_parameter_files():
    for name in ("BPDATA.BIN", "BPLAYER.BIN", "BTLEFF.BIN"):
        path = OUT / name
        if not path.exists():
            continue
        data = path.read_bytes()
        print(f"\n=== {name} ({len(data)} bytes) head:")
        for i in range(0, 0x60, 16):
            print(f"  {i:#05x}: " + " ".join(f"{b:02x}" for b in data[i:i + 16]))
        # ASCII strings
        strings = [(m.start(), m.group()) for m in re.finditer(rb"[ -~]{4,}", data)]
        print(f"  ascii strings: {len(strings)}")
        for at, text in strings[:10]:
            print(f"    {at:#06x}: {text[:40]!r}")


if __name__ == "__main__":
    extract()
    search_names()
    inspect_parameter_files()
