import json
from pathlib import Path

p = Path(r"quiz_time_digimon_questions.json")
raw = p.read_bytes()
print("BOM:", raw[:3] == b"\xef\xbb\xbf", "| utf-8 decodes:", end=" ")
text = raw.decode("utf-8")
print("yes")
d = json.loads(text)
qs = d["questions"]
print("count:", len(qs), "| CS:", sum(q["game"] == "Cyber Sleuth" for q in qs),
      "| HM:", sum(q["game"] == "Hacker's Memory" for q in qs))
print("backslash anywhere in data:",
      sum("\\" in json.dumps(q) for q in qs))
print("cs_05 correctAnswerText:", repr(qs[4]["correctAnswerText"]))
print("note:", d["meta"]["notes"][1])
print("answers per question:", sorted({len(q["answers"]) for q in qs}))
print("correct index distribution:", {i: sum(q["correctIndex"] == i for q in qs) for i in range(3)})
print("duplicate questions:", len(qs) - len({q["question"] for q in qs}))
print("non-? endings:", [q["id"] for q in qs if not q["question"].endswith("?")])
# make sure every question mentions a Digimon-ish term or is about the franchise
print("questions with no capitalised digimon word:", [q["id"] for q in qs
      if not any(w[:1].isupper() and len(w) > 2 for w in q["question"].split())])
