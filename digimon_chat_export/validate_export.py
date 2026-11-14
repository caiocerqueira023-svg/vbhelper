import json
from pathlib import Path

d = json.loads(Path(r"digimon_chat_export/digimon_chat.json").read_text(encoding="utf-8"))
c = json.loads(Path(r"digimon_chat_export/digimon_chat_conversations.json").read_text(encoding="utf-8"))
print("keys:", list(d.keys()))
print("entries:", len(d["entries"]), "| convs:", c["conversationCount"], "| conv lines:", c["lineCount"])
print("counts: ids", sum(1 for e in d["entries"] if e["id"]),
      "speakers", sum(1 for e in d["entries"] if e["speaker"]),
      "texts", sum(1 for e in d["entries"] if e["text"]))
assert len(d["entries"]) == 5630 and c["lineCount"] == 5630
assert len({e["id"] for e in d["entries"]}) == 5630
alltext = "".join(e["text"] for e in d["entries"])
print("replacement chars:", alltext.count("\ufffd"), "| newlines:", alltext.count("\n"))
print("texts w/ trailing space:", sum(1 for e in d["entries"] if e["text"] != e["text"].rstrip()))
print("texts w/ leading space:", sum(1 for e in d["entries"] if e["text"] != e["text"].lstrip()))
print("longest text len:", max(len(e["text"]) for e in d["entries"]))
print("first:", json.dumps(d["entries"][0], ensure_ascii=False))
print("last :", json.dumps(d["entries"][-1], ensure_ascii=False))
print("row 3200:", json.dumps(d["entries"][3200], ensure_ascii=False)[:220])
print()
print("conv kinds:", {k: sum(1 for x in c["conversations"] if x["kind"] == k) for k in {x["kind"] for x in c["conversations"]}})
print("first conv:", json.dumps(c["conversations"][0], ensure_ascii=False)[:300])
