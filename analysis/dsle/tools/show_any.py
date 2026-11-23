"""Print one function body from a Ghidra text export (any module)."""
import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")


def show(module: str, address: str):
    text = (ROOT / f"analysis/dsle/ghidra/out-{module}/all_pseudocode.c").read_text(
        encoding="utf-8", errors="replace")
    pattern = re.compile(r"// ---- " + address + r" ")
    match = pattern.search(text)
    if not match:
        print(f"== {address}: not found in {module}")
        return
    start = match.start()
    end = text.find("// ----", match.end())
    body = text[start:end if end > 0 else len(text)]
    print(body.rstrip())
    print()


if __name__ == "__main__":
    module = sys.argv[1]
    for address in sys.argv[2:]:
        show(module, address)
