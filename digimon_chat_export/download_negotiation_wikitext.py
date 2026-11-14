# -*- coding: utf-8 -*-
"""Download the full wikitext of the SMT V negotiation dialogue page, one section at a time."""
import json
import time
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "_src"
OUT.mkdir(exist_ok=True)

PAGE = "Negotiation_Dialogue_in_Shin_Megami_Tensei_V"
API = ("https://megamitensei.fandom.com/api.php?action=parse&page=" + PAGE +
       "&prop=wikitext&format=json&formatversion=2&section=")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/122.0 Safari/537.36")

# section index -> label, from the API sections listing
SECTIONS = [
    (2, "Male_Monster"), (3, "Male_Tough"), (4, "Male_Beast"), (5, "Male_Cute"),
    (6, "Male_Brute"), (7, "Male_Old_Man"), (8, "Male_Rookie"), (9, "Male_Noble"),
    (10, "Male_Pompous"), (11, "Male_Wise"), (12, "Male_Jack_Bros"),
    (14, "Female_Cute"), (15, "Female_Peppy"), (16, "Female_Fatale"),
    (18, "Unique_Tutorial_Pixie"),
]

combined = []
for idx, label in SECTIONS:
    url = API + str(idx)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    for attempt in range(3):
        try:
            payload = json.loads(urllib.request.urlopen(req, timeout=45).read().decode("utf-8"))
            break
        except Exception as exc:
            print(f"  {label}: attempt {attempt + 1} failed: {exc}")
            time.sleep(2)
    else:
        raise SystemExit(f"could not download section {idx}")

    text = payload["parse"]["wikitext"]
    (OUT / f"section_{idx:02d}_{label}.txt").write_text(text, encoding="utf-8")
    combined.append(text)
    print(f"  section {idx:>2} {label:<22} {len(text):>6} chars")
    time.sleep(1.0)

(OUT / "all_sections.txt").write_text("\n\n".join(combined) + "\n", encoding="utf-8")
print(f"\ncombined: {(OUT / 'all_sections.txt').stat().st_size:,} bytes")
