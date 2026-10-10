"""Read-only Wikimon partner-history audit; output is JSON, never roster edits.

Use: py -3 -B tools/audit_tamer_canon.py [--ids id,id] [--series text]
Only character infobox relationships and character-specific Box Evo templates
are extracted. Generic species evolution tables and TCG navigation are excluded.
"""

import argparse
import concurrent.futures
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


def templates(text, name):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    pattern = re.compile(r"\{\{\s*" + re.escape(name) + r"(?=[\s|\n])", re.I)
    result = []
    for match in pattern.finditer(text):
        depth = 1
        pos = match.end()
        while depth and pos < len(text):
            opening = text.find("{{", pos)
            closing = text.find("}}", pos)
            if closing < 0:
                break
            if 0 <= opening < closing:
                depth += 1
                pos = opening + 2
            else:
                depth -= 1
                pos = closing + 2
        if not depth:
            result.append(text[match.end():pos - 2])
    return result


def fields(body):
    matches = list(re.finditer(r"^\s*\|\s*([\w ]+)\s*=", body, re.M))
    return {match.group(1).strip(): body[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(body)].strip()
            for i, match in enumerate(matches)}


def links(text):
    text = re.sub(r"<ref\b[^>]*>.*?</ref>|<ref\b[^>]*/>", "", text, flags=re.S | re.I)
    text = re.sub(r"\{\{(?:ref|rfc|rfe|note)\b.*?\}\}", "", text, flags=re.S | re.I)
    return [{"page": title.split("#")[0].strip(), "name": (label or title).strip()}
            for title, label in re.findall(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", text)
            if not title.startswith(("File:", "Category:", "Template:"))]


def fetch(page):
    url = "https://wikimon.net/index.php?" + urllib.parse.urlencode({"title": page, "action": "raw"})
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 VBHelperCanonAudit/1.0", "Accept": "text/plain"})
    for attempt in range(2):
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                text = response.read().decode("utf-8")
            redirect = re.match(r"\s*#redirect\s*\[\[([^\]]+)\]\]", text, re.I)
            if redirect:
                target = redirect.group(1)
                if target != page:
                    return fetch(target)
            return {"page": page, "text": text, "error": None}
        except Exception as error:
            if attempt:
                return {"page": page, "text": "", "error": str(error)}
            time.sleep(1)


def load_pages(pages):
    titles = sorted(set(pages))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        return dict(zip(titles, executor.map(fetch, titles)))


def evolution(text):
    boxes = []
    for body in templates(text, "Box Evo"):
        entries = []
        for level, value in fields(body).items():
            candidates = []
            for part in re.split(r"<br\s*/?>", value, flags=re.I):
                found = links(part)
                if found:
                    fusion = re.search(r"\(with\s+\[\[", part, re.I)
                    candidates.append({**found[0], "fusion": bool(fusion),
                                       "companions": links(part[fusion.start():])[:1] if fusion else []})
            if candidates:
                entries.append({"level": level, "forms": candidates})
        if entries:
            boxes.append(entries)
    return boxes


def transformations(text):
    return {name: [{key: links(value) for key, value in fields(body).items()}
                   for body in templates(text, name)]
            for name in ("Box Hybrid", "Box Xros") if templates(text, name)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ids")
    parser.add_argument("--series")
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()
    path = Path(__file__).resolve().parents[1] / "app/src/main/assets/tamers.tsv"
    rows = [line.split("|") for line in path.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
    if args.ids:
        selected = set(args.ids.split(","))
        rows = [row for row in rows if row[0] in selected]
    if args.series:
        rows = [row for row in rows if args.series.casefold() in row[3].casefold()]
    characters = load_pages(row[10] for row in rows)
    relationship_map = {}
    for row in rows:
        source = characters[row[10]]
        char = templates(source["text"], "Char")
        info = fields(char[0]) if char else {}
        relationship_map[row[0]] = {"partners": links(info.get("pd", "")), "used": links(info.get("du", ""))}
    partner_pages = load_pages(link["page"] for relations in relationship_map.values()
                               for key in ("partners", "used") for link in relations[key] if link["page"])
    audit = []
    for row in rows:
        source = characters[row[10]]
        histories = []
        for key in ("partners", "used"):
            for link in relationship_map[row[0]][key]:
                history = partner_pages.get(link["page"], {"text": "", "error": None})
                boxes = evolution(history["text"])
                # Character-specific relationships are also confirmed in the linked partner infobox.
                char = templates(history["text"], "Char")
                info = fields(char[0]) if char else {}
                histories.append({"relationship": key, **link, "forms": boxes,
                                  "owners": links(info.get("hp", "")), "error": history["error"],
                                  "transformations": transformations(history["text"])})
        audit.append({"id": row[0], "series": row[3], "source": row[10],
                      "current": {"primary": row[4], "secondary": row[5], "lineClaim": row[9]},
                      "relationships": relationship_map[row[0]], "histories": histories,
                      "characterForms": evolution(source["text"]), "characterTransformations": transformations(source["text"]),
                      "error": source["error"]})
    if args.compact:
        print(json.dumps({"characters": len(rows), "characterPages": len(characters), "partnerPages": len(partner_pages)}))
        for item in audit:
            histories = [{"partner": history["name"], "page": history["page"], "relationship": history["relationship"],
                          "forms": {entry["level"]: [form["name"] + (" + " + form["companions"][0]["name"] if form["fusion"] and form["companions"] else "")
                                                       for form in entry["forms"]]
                                    for box in history["forms"] for entry in box}, "transformations": history["transformations"],
                          "error": history["error"]}
                         for history in item["histories"]]
            print(json.dumps({"id": item["id"], "series": item["series"], "source": item["source"],
                              "histories": histories, "characterForms": item["characterForms"],
                              "characterTransformations": item["characterTransformations"], "error": item["error"]}, ensure_ascii=True))
    else:
        print(json.dumps({"characters": len(rows), "characterPages": len(characters), "partnerPages": len(partner_pages),
                          "audit": audit}, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
