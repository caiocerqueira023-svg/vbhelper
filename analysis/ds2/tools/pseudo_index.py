"""Index and query the exported Ghidra pseudocode for one ARM9 image.

Subcommands:
  find <literal>            functions whose body contains the literal text
  grep <regex>              functions whose body matches the regex
  show <addr|name>          print one function body
  callers <addr|name>       functions calling the given function
  callees <addr|name>       functions called by the given function
  stats                     function count and largest functions
"""
import os
import re
import sys
from pathlib import Path

OUT = Path(os.environ.get("PSEUDO_OUT", r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\ghidra\out-dawn"))
PSEUDO = OUT / "all_pseudocode.c"
FUNCS = OUT / "functions.tsv"


def blocks():
    text = PSEUDO.read_text(encoding="utf-8", errors="replace")
    chunks = re.split(r"(?m)^// ---- ", text)
    out = []
    for chunk in chunks:
        if not chunk.strip():
            continue
        header, _, body = chunk.partition("\n")
        parts = header.split()
        if not parts:
            continue
        address = parts[0]
        name = parts[1] if len(parts) > 1 else ""
        out.append({"address": address, "name": name, "header": header, "body": body})
    return out


def find_block(query):
    for b in blocks():
        if query.lower() in (b["address"].lower(), b["name"].lower()):
            return b
    norm = query.lower().replace("0x", "")
    for b in blocks():
        if b["address"].lower().endswith(norm) or b["name"].lower().endswith("_" + norm):
            return b
    return None


def main():
    cmd = sys.argv[1]
    arg = sys.argv[2] if len(sys.argv) > 2 else ""
    all_blocks = blocks()
    if cmd == "find":
        hits = [b for b in all_blocks if arg in b["body"]]
        print(f"{len(hits)} functions contain {arg!r}")
        for b in hits:
            print(f"  {b['address']} {b['name']} ({len(b['body'].splitlines())} lines)")
    elif cmd == "grep":
        hits = [b for b in all_blocks if re.search(arg, b["body"])]
        print(f"{len(hits)} functions match {arg!r}")
        for b in hits:
            print(f"  {b['address']} {b['name']}")
    elif cmd == "show":
        b = find_block(arg)
        if b:
            print(f"// {b['header']}")
            print(b["body"])
        else:
            print("not found")
    elif cmd in ("callers", "callees"):
        b = find_block(arg)
        if not b:
            print("not found")
            return
        target = b["name"]
        if cmd == "callers":
            hits = [x for x in all_blocks if re.search(r"\b" + re.escape(target) + r"\s*\(", x["body"])
                    and x["name"] != target]
            print(f"{len(hits)} callers of {target}")
            for x in hits:
                print(f"  {x['address']} {x['name']}")
        else:
            names = sorted(set(re.findall(r"\b(FUN_[0-9a-fA-F]+|func_0x[0-9a-fA-F]+)\s*\(", b["body"])))
            print(f"{len(names)} callees of {target}")
            for name in names:
                print("  " + name)
    elif cmd == "range":
        lo = int(sys.argv[2], 0)
        hi = int(sys.argv[3], 0)
        rows = []
        for line in FUNCS.read_text(encoding="utf-8", errors="replace").splitlines():
            parts = line.split("\t")
            if not parts:
                continue
            addr = int(parts[0], 16)
            if lo <= addr <= hi:
                rows.append(line)
        print(f"{len(rows)} functions in {lo:#x}..{hi:#x}")
        for row in rows:
            print("  " + row)
    elif cmd == "stats":
        print(f"functions={len(all_blocks)}")
        biggest = sorted(all_blocks, key=lambda x: len(x["body"].splitlines()), reverse=True)[:15]
        for b in biggest:
            print(f"  {b['address']} {b['name']} {len(b['body'].splitlines())} lines")
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
