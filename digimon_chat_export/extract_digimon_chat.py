# -*- coding: utf-8 -*-
"""
Extract dialogue text from Digimon Story: Time Stranger .mbe message sheets
(EXPA container -> CHNK chunk -> length-prefixed, 4-byte-aligned UTF-8 strings).

Format notes (reverse engineered from digimon_chat.mbe):
  Header:  "EXPA" | u32 version | u32 sheetNameLen | sheetName (padded to 4)
           u32 columnCount | u32 cellSize(=8) | u32 x3 (unknown) |
           u32 rowStride(=32) | u32 rowCount
  Padding: zeros up to 0x2C000 (the cell-descriptor table lives there in the
           original sheet export; it is zeroed in this file but each cell's
           ordinal is preserved as the u32 tag in front of its string).
  Chunk:   "CHNK" | u32 stringCount
  Strings: u32 tag | u32 byteLen(padded to 4, NUL terminated) | UTF-8 bytes
           tag -> (tag - 64) // 8 = cell ordinal => row = ordinal // 4,
                                                     col = ordinal % 4
"""
import json
import struct
import sys
from pathlib import Path

SRC = Path(r"C:\Users\julye\Downloads\Be Nice To Your Mon - DCO English Version-13-1-5-1762305080\dsts-loader\patch_text01\message\digimon_chat.mbe")
OUT_DIR = Path(r"C:\Users\julye\IdeaProjects\vbhelper\digimon_chat_export")

data = SRC.read_bytes()
assert data[:4] == b"EXPA", "not an EXPA container"

version = struct.unpack_from("<I", data, 4)[0]
sheet_len = struct.unpack_from("<I", data, 8)[0]
sheet_name = data[12:12 + sheet_len].rstrip(b"\x00").decode("utf-8")

p = 12 + sheet_len
hdr = struct.unpack_from("<7I", data, p)          # columnCount, cellSize, u,u,u, rowStride, rowCount
col_count, cell_size, _u1, _u2, _u3, row_stride, row_count = hdr
p += 28

# The cell-descriptor table follows the header: rowCount * rowStride bytes.
# In this file it is entirely zeroed, but each string's tag still encodes the
# cell ordinal, so the grid can be rebuilt exactly.
cell_table_size = row_count * row_stride
assert p + cell_table_size <= len(data), "cell table overruns file"
p += cell_table_size

assert data[p:p + 4] == b"CHNK", f"expected CHNK at {p:#x}"
string_count = struct.unpack_from("<I", data, p + 4)[0]
p += 8

# --- read every length-prefixed string -------------------------------------
cells = []                                        # (tag, text, file_offset)
while p + 8 <= len(data):
    tag, blen = struct.unpack_from("<II", data, p)
    raw = data[p + 8:p + 8 + blen]
    if blen == 0 or blen % 4 or len(raw) != blen:
        raise ValueError(f"bad string length {blen} at {p:#x}")
    end = raw.find(b"\x00")
    if end < 0 or raw[end:].strip(b"\x00"):
        raise ValueError(f"unterminated/overlong string at {p:#x}")
    cells.append((tag, raw[:end].decode("utf-8")))
    p += 8 + blen

assert p == len(data), f"trailing bytes: parsed to {p:#x}, size {len(data):#x}"
assert len(cells) == string_count, f"{len(cells)} != {string_count}"

# --- map cells onto the sheet grid -----------------------------------------
BASE = 64                                         # first cell tag
grid = {}
for tag, text in cells:
    ordinal = (tag - BASE) // cell_size
    row, col = divmod(ordinal, row_stride // cell_size)
    if (row, col) in grid:
        raise ValueError(f"duplicate cell row={row} col={col}")
    grid[(row, col)] = text
assert max(r for r, _ in grid) == row_count - 1, "row count mismatch"
assert max(c for _, c in grid) < col_count, "column index out of range"

COLUMNS = ["id", "speaker", "text", "unused"]

entries = []
for r in range(row_count):
    entries.append({
        "id": grid.get((r, 0), ""),
        "speaker": grid.get((r, 1), ""),
        "text": grid.get((r, 2), ""),
    })
assert all(e["id"] for e in entries), "row without an id"

# --- group rows into conversations -----------------------------------------
import re

def conversation_id(row_id: str) -> str:
    m = re.match(r"^(common\d+)_", row_id)
    if m:
        return m.group(1)
    if row_id.startswith("digimon_chat_ps"):
        return "digimon_chat_ps"
    m = re.match(r"^([A-Za-z0-9\-]+_\d+)_", row_id)
    if m:
        return m.group(1)
    return row_id


def conversation_kind(conv_id: str) -> str:
    if conv_id.startswith("common"):
        return "common_template"      # shared template with age/personality variants
    if conv_id.startswith("digimon_chat"):
        return "system"               # personality-skill / misc lines
    return "digimon_chat"             # per-Digimon chat set


conversations = {}
for e in entries:
    conversations.setdefault(conversation_id(e["id"]), []).append(e)

conv_list = []
for cid, lines in conversations.items():
    character = ""
    for ln in lines:
        if ln["speaker"].startswith("char_"):
            character = ln["speaker"][len("char_"):]
            break
    conv_list.append({
        "id": cid,
        "kind": conversation_kind(cid),
        "character": character,
        "lineCount": len(lines),
        "lines": [{"id": ln["id"], "speaker": ln["speaker"], "text": ln["text"]} for ln in lines],
    })
# sort by first appearance in the sheet instead of dict insertion order
first_row = {}
for i, e in enumerate(entries):
    first_row.setdefault(conversation_id(e["id"]), i)
conv_list.sort(key=lambda c: first_row[c["id"]])

# --- write JSON -------------------------------------------------------------
OUT_DIR.mkdir(parents=True, exist_ok=True)

meta = {
    "sourceFile": str(SRC),
    "container": "EXPA",
    "containerVersion": version,
    "sheet": sheet_name,
    "stringCount": string_count,
    "rowCount": row_count,
    "columnCount": col_count,
    "columns": COLUMNS[:3],
    "note": "Columns 0-2 hold dialogue id / speaker / text. The 4th column slot exists in the format but is empty in this file.",
}

main_json = dict(meta, entries=entries)
(OUT_DIR / "digimon_chat.json").write_text(
    json.dumps(main_json, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

conv_json = {
    "sourceFile": str(SRC),
    "sheet": sheet_name,
    "conversationCount": len(conv_list),
    "lineCount": sum(c["lineCount"] for c in conv_list),
    "conversations": conv_list,
}
(OUT_DIR / "digimon_chat_conversations.json").write_text(
    json.dumps(conv_json, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# --- validation / stats -----------------------------------------------------
back = json.loads((OUT_DIR / "digimon_chat.json").read_text(encoding="utf-8"))
assert [e["id"] for e in back["entries"]] == [e["id"] for e in entries]
assert [e["text"] for e in back["entries"]] == [e["text"] for e in entries]
back_conv = json.loads((OUT_DIR / "digimon_chat_conversations.json").read_text(encoding="utf-8"))
assert sum(c["lineCount"] for c in back_conv["conversations"]) == row_count

texts = [e["text"] for e in entries]
print(f"sheet            : {sheet_name}")
print(f"rows/conversations: {row_count} / {len(conv_list)}")
print(f"entries w/ text   : {sum(1 for t in texts if t)}")
print(f"entries w/ speaker: {sum(1 for e in entries if e['speaker'])}")
print(f"total characters  : {sum(len(t) for t in texts)}")
print(f"unique ids        : {len({e['id'] for e in entries})}")
print(f"unique texts      : {len(set(texts))}")
print(f"empty texts       : {[e['id'] for e in entries if not e['text']]}")
print(f"group size dist   :", {k: sum(1 for c in conv_list if c['lineCount'] == k)
                               for k in sorted({c['lineCount'] for c in conv_list})})
print(f"output            : {OUT_DIR}")
for f in OUT_DIR.iterdir():
    print(f"  {f.name:42} {f.stat().st_size:>9,} bytes")
