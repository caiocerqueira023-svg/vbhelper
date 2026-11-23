"""Decode the Dawn/Dusk record history bitmap (0xB0 history, bitmap at 0xC8..0x107)."""
import json
import struct
from pathlib import Path

SAVE = Path(r"C:\Users\julye\Downloads\Caio\ROMs e Saves para RE\Digimon World - Dawn (USA).sav")
ASSET = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\dawn\species_dawn.json")


def main():
    data = SAVE.read_bytes()
    asset = json.loads(ASSET.read_text(encoding="utf-8"))
    names = {int(k): v for k, v in asset["names"].items()}
    previous = {int(k): v for k, v in asset["previousSpecies"].items()}
    for slot in range(3):
        base = 0x288 + slot * 0x164
        rec = data[base:base + 0x164]
        species = struct.unpack_from("<I", rec, 4)[0]
        print(f"slot {slot}: species={species} {names.get(species)}")
        for w in range(32):
            off = 0xC8 + w * 2
            word = struct.unpack_from("<H", rec, off)[0]
            if word:
                bits = [w * 16 + b for b in range(16) if word & (1 << b)]
                labelled = [(hex(s), names.get(s)) for s in bits]
                print(f"   word {w:2d} @ {off:#05x} = {word:#06x} species={labelled}")
        print(f"   stage byte @0x107 = {rec[0x107]}")
        # walk the raw predecessor chain for as long as it stays inside 0..511
        chain = []
        cur = species
        for _ in range(10):
            nxt = previous.get(cur, -1)
            if nxt < 0:
                break
            chain.append(nxt)
            cur = nxt
        print(f"   chain(in-range)={[(hex(c), names.get(c)) for c in chain]}")


if __name__ == "__main__":
    main()
