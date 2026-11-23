"""Print selected functions from the Ghidra pseudocode export."""
import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
EXPORTS = {
    "arm9": ROOT / "analysis/dsle/ghidra/out-arm9/all_pseudocode.c",
    "ovl000": ROOT / "analysis/dsle/ghidra/out-ovl000/all_pseudocode.c",
    "ovl001": ROOT / "analysis/dsle/ghidra/out-ovl001/all_pseudocode.c",
    "ovl002": ROOT / "analysis/dsle/ghidra/out-ovl002/all_pseudocode.c",
}


def load(name):
    text = EXPORTS[name].read_text(encoding="utf-8", errors="replace")
    chunks = {}
    for match in re.finditer(r"// ---- ([0-9a-fA-F]+) (\S+) size=(\d+) ----", text):
        start = match.start()
        chunks[match.group(1).lower()] = (start, match)
    return text, chunks


def show(name, addresses):
    text, chunks = load(name)
    keys = sorted(chunks)
    for address in addresses:
        address = address.lower()
        if address not in chunks:
            print(f"{name}: function {address} not found")
            continue
        start, match = chunks[address]
        index = keys.index(address)
        end = chunks[keys[index + 1]][0] if index + 1 < len(keys) else len(text)
        print(text[start:end])


if __name__ == "__main__":
    module = sys.argv[1]
    show(module, sys.argv[2:])
