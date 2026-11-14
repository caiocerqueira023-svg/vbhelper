# -*- coding: utf-8 -*-
"""
Build a JSON dataset of the Shin Megami Tensei V demon-negotiation dialogue.

Source: Megami Tensei Wiki - "Negotiation Dialogue in Shin Megami Tensei V"
  https://megamitensei.fandom.com/wiki/Negotiation_Dialogue_in_Shin_Megami_Tensei_V
Raw wikitext was downloaded per section into _src/ by download_negotiation_wikitext.py.

Output: demon_negotiation_dialogue_smt5.json  (used as the basis for the
wild-Digimon talk system).

Data model
----------
tones[]            : negotiation tone groups. In SMT V these are the demon
                     "personalities" (Monster, Tough, Beast, Cute, Brute,
                     Old Man, Rookie, Noble, Pompous, Wise, Jack Bros,
                     Female: Cute/Peppy/Fatale, Unique: Tutorial Pixie).
tones[].topics[]   : one negotiation topic (Man-stink, Ignore, Hair, Quiz, ...).
topics[].steps[]   : flat, ordered dialogue steps. Every step has an id and,
                     where in the tree, a parent step:
                      - kind=prompt   demon line that offers player options
                      - kind=choice   resolution of one player option
                      - kind=branch   conditional result (good/bad, higher/lower,
                                      Pass/Fail, ...)
Steps link to each other through the parent prompt's options:
                      prompt.options[i].stepIds -> choice step ids
                      choice.options[i].stepIds -> next prompt's choices (nested)
A log-keeping runtime can walk: prompt (options) -> pick option -> choice(s)
-> maybe outcome / nested prompt -> ...
"""
import difflib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "_src"
OUT = HERE / "demon_negotiation_dialogue_smt5.json"

SOURCE_URL = ("https://megamitensei.fandom.com/wiki/"
              "Negotiation_Dialogue_in_Shin_Megami_Tensei_V")

SECTION_FILES = [
    ("Male", "Monster", "section_02_Male_Monster.txt"),
    ("Male", "Tough", "section_03_Male_Tough.txt"),
    ("Male", "Beast", "section_04_Male_Beast.txt"),
    ("Male", "Cute", "section_05_Male_Cute.txt"),
    ("Male", "Brute", "section_06_Male_Brute.txt"),
    ("Male", "Old Man", "section_07_Male_Old_Man.txt"),
    ("Male", "Rookie", "section_08_Male_Rookie.txt"),
    ("Male", "Noble", "section_09_Male_Noble.txt"),
    ("Male", "Pompous", "section_10_Male_Pompous.txt"),
    ("Male", "Wise", "section_11_Male_Wise.txt"),
    ("Male", "Jack Bros", "section_12_Male_Jack_Bros.txt"),
    ("Female", "Cute", "section_14_Female_Cute.txt"),
    ("Female", "Peppy", "section_15_Female_Peppy.txt"),
    ("Female", "Fatale", "section_16_Female_Fatale.txt"),
    ("Unique", "Tutorial Pixie", "section_18_Unique_Tutorial_Pixie.txt"),
]

# --------------------------------------------------------------------------
# wikitext helpers
# --------------------------------------------------------------------------
ATTR_RE = re.compile(r'^\s*(?:(?:colspan|rowspan|width|align|style|class)\s*=\s*(?:"[^"]*"|[^"\s|]+)\s*)+')
ATTR_ODD = re.compile(r'^\s*(?:colspan|rowspan)\s*"\s*[^"]*\s*"\s*')
COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
LINK_RE = re.compile(r"\[\[([^\]|]*)(?:\|([^\]]*))?\]\]")
ENTITIES = {"&amp;": "&", "&quot;": '"', "&#39;": "'", "&nbsp;": " ",
            "&lt;": "<", "&gt;": ">", "&mdash;": "—", "&ndash;": "–"}
MARKER_RE = re.compile(r"^\[[^\[\]]{2,80}\]$")
RANDOM_TAG_RE = re.compile(r"\s*[\(\[]\s*random\s*[\)\]]\s*$", re.I)

CONDITION_MARKERS = {
    "GOOD OUTCOME", "BAD OUTCOME",
    "USER STRENGTH IS HIGHER", "USER STRENGTH IS LOWER",
    "USER AGILITY IS HIGHER", "USER AGILITY IS LOWER",
    "USER MAGIC IS HIGHER", "USER MAGIC IS LOWER",
    "USER LUCK IS HIGHER", "USER LUCK IS LOWER",
}

STAT_CHECK_RE = re.compile(
    r"^(?:\[)?(?:(?P<who>[A-Za-z ]+?) stat between the 2 (?P<subj>users|demons) is compared|"
    r"(?P<stat2>[A-Za-z]+)(?: stat)? (?:is|are) compared)(?:\])?$", re.I)


def clean_wikitext(text: str) -> str:
    text = COMMENT_RE.sub("", text)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.I)
    for k, v in ENTITIES.items():
        text = text.replace(k, v)
    text = LINK_RE.sub(lambda m: (m.group(2) or m.group(1)), text)
    text = text.replace("'''", "").replace("''", "")
    lines = [ln.strip() for ln in text.split("\n")]
    return "\n".join(ln for ln in lines if ln)


def split_attr(cell: str):
    """Return (attrs_string, content) for a raw wikitext cell."""
    attrs = ""
    while True:
        m = ATTR_RE.match(cell) or ATTR_ODD.match(cell)
        if not m:
            break
        attrs += " " + m.group(0)
        cell = cell[m.end():]
    if cell.startswith("|"):
        cell = cell[1:]
    return attrs, cell


def parse_tables(wikitext: str):
    """Yield lists of rows; each row is a list of {'attrs', 'raw'} cells."""
    tables = re.findall(r"\{\|.*?\n\|\}", wikitext, flags=re.S)
    for table in tables:
        rows, cur = [], None
        for line in table.splitlines():
            s = line.rstrip()
            if s.startswith("|}"):
                if cur is not None:
                    rows.append(cur)
                cur = None
                break
            if s.startswith("{|"):
                continue
            if s.startswith("|-"):
                if cur is not None:
                    rows.append(cur)
                cur = []
                continue
            if s.startswith(("|", "!")):
                attrs, content = split_attr(s[1:])
                if cur is None:
                    cur = []
                cur.append({"attrs": attrs, "raw": content})
                continue
            if cur:
                cur[-1]["raw"] += "\n" + s
        if cur:
            rows.append(cur)
        yield rows


def parse_cell(raw: str):
    """Split a cell into options / markers / dialogue lines."""
    text = clean_wikitext(raw)
    options, markers, dialogue = [], [], []
    for line in text.split("\n"):
        s = line.strip()
        if not s:
            continue
        if s.startswith(">"):
            body = re.sub(r"^>+\s*", "", s).strip()
            if not body:
                continue
            random = bool(RANDOM_TAG_RE.search(body))
            body = RANDOM_TAG_RE.sub("", body).strip()
            options.append({"text": body, "random": random,
                            "key": option_key(body)})
        elif MARKER_RE.match(s):
            markers.append(s)
        else:
            dialogue.append(s)
    return {"options": options, "markers": markers, "dialogue": dialogue}


def option_key(text: str) -> str:
    s = re.sub(r"^>+\s*", "", text)
    s = RANDOM_TAG_RE.sub("", s)
    s = s.replace("'", "").replace("\u2019", "")
    s = re.sub(r"[^\w\s]", " ", s.lower())
    s = re.sub(r"\s+", " ", s).strip()
    return s


def slug(text: str) -> str:
    s = text.lower().replace("'", "")
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s


# --------------------------------------------------------------------------
# outcome normalisation
# --------------------------------------------------------------------------
def normalise_outcome(markers):
    if not markers:
        return None
    raw = markers[0].strip("[]").strip()
    m = re.match(r"^DIALOGUE SWITCH\s*(?:>>\s*(?P<to>.+))?$", raw, re.I) or \
        re.match(r"^DIALOUGE SWITCH\s*(?:>>\s*(?P<to>.+))?$", raw, re.I)
    if m:
        return {"type": "dialogue_switch", "to": (m.group("to") or "").strip() or None, "raw": raw}
    if raw == "ENEMY PHASE":
        return {"type": "enemy_phase", "raw": raw}
    if raw == "BARGAIN PHASE":
        return {"type": "bargain_phase", "raw": raw}
    if raw == "LEAVES":
        return {"type": "leaves", "raw": raw}
    if raw == "INSTANT RECRUIT":
        return {"type": "instant_recruit", "raw": raw}
    if raw.startswith("GIVES ITEM"):
        return {"type": "gives_item", "leaves": "LEAVES" in raw, "raw": raw}
    if raw.startswith("GIVES MACCA"):
        return {"type": "gives_macca", "leaves": "LEAVES" in raw,
                "enemyTurn": "ENEMY TURN" in raw, "raw": raw}
    if raw.startswith("ITEM RECEIVED"):
        return {"type": "receives_item", "leaves": "LEAVES" in raw, "raw": raw}
    if raw.startswith("TALK END"):
        return {"type": "talk_end", "turnUsed": "TURN USED" in raw and "ISN'T" not in raw, "raw": raw}
    if raw == "RANDOM":
        return {"type": "random", "raw": raw}
    if raw == "GUESS RANDOM MONSTER":
        return {"type": "guess_random_monster", "raw": raw}
    if raw in CONDITION_MARKERS:
        return {"type": "condition", "raw": raw}
    sm = STAT_CHECK_RE.match(raw)
    if sm:
        stat = (sm.group("who") or sm.group("stat2") or "").strip()
        return {"type": "stat_check", "stat": stat,
                "between": ("demons" if sm.groupdict().get("subj") == "demons" else "users"),
                "raw": raw}
    if raw == "NEXT SECTION":
        return {"type": "next_section", "raw": raw}
    if raw == "SWITCH TO JOIN CONVERSATION":
        return {"type": "switch_to_join_conversation", "raw": raw}
    if raw == "REPEAT CONVERSATION":
        return {"type": "repeat_conversation", "raw": raw}
    return {"type": "other", "raw": raw}


# --------------------------------------------------------------------------
# topic builder
# --------------------------------------------------------------------------
def build_topic(rows, tone_id, topic_id, topic_name, warnings, counters):
    steps = []
    step_by_id = {}
    stack = []          # [{"step": step, "opts": options, "cursor": int}]
    last_step = None
    last_choice = None
    branch_parent = None
    orphan_parent = None

    def new_step(**kw):
        step = {"id": f"{topic_id}.{len(steps) + 1}", "kind": kw.pop("kind")}
        for key, value in kw.items():
            if value is not None and value != [] and value != "":
                step[key] = value
        steps.append(step)
        step_by_id[step["id"]] = step
        return step

    def find_pending(key):
        for fi in range(len(stack) - 1, -1, -1):
            frame = stack[fi]
            for k in range(frame["cursor"], len(frame["opts"])):
                if frame["opts"][k]["key"] == key:
                    return fi, k
        # fuzzy fallback for wiki typos / reworded labels
        best = None
        for fi in range(len(stack) - 1, -1, -1):
            frame = stack[fi]
            for k in range(frame["cursor"], len(frame["opts"])):
                ratio = difflib.SequenceMatcher(None, key, frame["opts"][k]["key"]).ratio()
                if ratio >= 0.85 and (best is None or ratio > best[0]):
                    best = (ratio, fi, k)
        return (best[1], best[2]) if best else None

    def link_option(parent_id, option_index, child_id):
        parent = step_by_id.get(parent_id)
        if parent is None:
            return
        opts = parent.get("options") or []
        if option_index is not None and 0 <= option_index < len(opts):
            opts[option_index].setdefault("stepIds", []).append(child_id)

    for row in rows:
        cells = []
        for c in row:
            parsed = parse_cell(c["raw"])
            parsed["attrs"] = c["attrs"]
            cells.append(parsed)
        if not cells:
            continue
        c0 = cells[0]

        # table header row (Dialogue / Outcome) should not appear here, but guard
        texts = [t for c in cells for t in c["dialogue"]]
        if texts and set(texts) <= {"Dialogue", "Outcome"}:
            continue

        # ---- label detection -------------------------------------------
        label = None
        random_note = bool(c0["dialogue"]) and all(
            re.fullmatch(r"\(?\s*random\s*\)?", t.strip(), re.I) for t in c0["dialogue"])
        if len(c0["options"]) == 1 and not c0["markers"] and (
                not c0["dialogue"] or random_note):
            label = dict(c0["options"][0])
            if random_note:
                label["random"] = True
        elif len(c0["dialogue"]) == 1 and not c0["options"] and not c0["markers"]:
            key = option_key(c0["dialogue"][0])
            if find_pending(key):
                label = {"text": c0["dialogue"][0], "random": False, "key": key}

        rest = cells[1:] if label else cells
        options, markers, dialogues = [], [], []
        for c in rest:
            options.extend(c["options"])
            markers.extend(c["markers"])
            dialogues.extend(c["dialogue"])
        demon = "\n".join(dialogues).strip() or None

        # ---- branch condition row --------------------------------------
        cond = None
        if not label and c0["markers"] and not c0["dialogue"] and not c0["options"]:
            mk = c0["markers"][0].strip("[]").strip()
            if mk in CONDITION_MARKERS:
                cond = mk
                markers = [m for m in markers if m.strip("[]").strip() != cond]

        prompt_marker = None
        for mk in markers:
            if mk.strip("[]").strip().upper() == "GUESS RANDOM MONSTER":
                prompt_marker = "GUESS RANDOM MONSTER"
        # one quiz prompt has the marker inline in the demon line
        if demon:
            inline = re.search(r"\s*\[GUESS RANDOM MONSTER\]\s*", demon, re.I)
            if inline:
                prompt_marker = "GUESS RANDOM MONSTER"
                demon = (demon[:inline.start()] + demon[inline.end():]).strip() or None
        outcome = normalise_outcome([m for m in markers
                                     if m.strip("[]").strip().upper() != "GUESS RANDOM MONSTER"])

        if cond and not label:
            parent = branch_parent or last_step
            if parent is None:
                # topic consists only of conditional result rows (moon phase topics)
                if orphan_parent is None:
                    orphan_parent = new_step(kind="prompt", marker="moon_phase")
                    counters["synthetic_root"] += 1
                    warnings.append(f"{topic_id}: branch-only topic, synthetic root added")
                parent = orphan_parent
            step = new_step(kind="branch", condition=cond, demon=demon, outcome=outcome,
                            parent=(parent["id"] if parent else None))
            if parent is not None:
                parent.setdefault("branches", []).append(
                    {"condition": cond, "stepId": step["id"]})
            counters["branch"] += 1
            continue

        # ---- choice row -------------------------------------------------
        if label is not None:
            match = find_pending(label["key"])
            parent_id = option_index = None
            alternative = False
            if match:
                fi, k = match
                del stack[fi + 1:]
                stack[fi]["cursor"] = k + 1
                parent_id = stack[fi]["step"]["id"]
                option_index = k
                counters["choice_matched"] += 1
            elif branch_parent is not None and label["key"] in ("pass", "fail"):
                parent_id = branch_parent["id"]
                counters["choice_branch"] += 1
            elif last_choice is not None:
                parent_id = last_choice.get("parent")
                option_index = last_choice.get("optionIndex")
                alternative = True
                counters["choice_alt"] += 1
            else:
                parent_id = last_step["id"] if last_step else None
                warnings.append(f"{topic_id}: unresolved choice {label['text']!r}")
                counters["choice_unmatched"] += 1

            step = new_step(kind="choice", demon=demon, choice=label["text"],
                            random=(True if label["random"] else None),
                            parent=parent_id, optionIndex=option_index,
                            outcome=outcome)
            if alternative and last_choice is not None:
                step["alternativeOf"] = last_choice["id"]
            if parent_id is not None:
                link_option(parent_id, option_index, step["id"])
            if options:
                step["options"] = options
                stack.append({"step": step, "opts": options, "cursor": 0})
            branch_parent = step if (outcome and outcome["type"] in ("stat_check", "random")) else None
            last_choice = step
            last_step = step
            counters["choice"] += 1
            continue

        # ---- prompt row, or random-alternative response ------------------
        if last_choice is not None:
            # extra row that belongs to the option picked above (one of several
            # random responses); it may itself offer the follow-up options
            parent_id = last_choice.get("parent")
            option_index = last_choice.get("optionIndex")
            step = new_step(kind="choice", demon=demon, choice=last_choice.get("choice"),
                            parent=parent_id, optionIndex=option_index,
                            alternativeOf=last_choice["id"],
                            options=options or None, outcome=outcome)
            if parent_id is not None:
                link_option(parent_id, option_index, step["id"])
            counters["choice_alt_prompt" if options else "choice_alt"] += 1
        else:
            parent_id = last_step["id"] if last_step else None
            step = new_step(kind="prompt", demon=demon, parent=parent_id,
                            options=options or None, outcome=outcome, marker=prompt_marker)
            counters["prompt"] += 1

        if options:
            stack.append({"step": step, "opts": options, "cursor": 0})
            last_choice = None
        branch_parent = step if (outcome and outcome["type"] in ("stat_check", "random")) else None
        last_step = step

    if not steps:
        warnings.append(f"{topic_id}: no steps parsed")
    return steps


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def main():
    tones = []
    warnings = []
    counters = {k: 0 for k in ("prompt", "choice", "choice_matched", "choice_branch",
                               "choice_alt", "choice_alt_prompt", "choice_unmatched",
                               "branch", "synthetic_root")}
    total_topics = 0

    for voice, tone_name, fname in SECTION_FILES:
        path = SRC / fname
        if not path.exists():
            warnings.append(f"missing source {fname}")
            continue
        wikitext = path.read_text(encoding="utf-8")
        tone_id = f"{voice.lower()}.{slug(tone_name)}"

        # split wikitext into (topic_name, rows) blocks
        def topic_header_name(row, cells):
            """Topic headers are centred single cells; two have lost the align attr."""
            if len(cells) != 1:
                return None
            c = cells[0]
            if c["options"] or c["markers"] or len(c["dialogue"]) != 1:
                return None
            if 'align="center"' in row[0]["attrs"]:
                return c["dialogue"][0]
            text = c["dialogue"][0]
            if len(text) <= 40 and not re.search(r"[.!?\u2026,;:]", text):
                return text          # e.g. the bare "Quiz" / "Bow" topic headers
            return None

        blocks = []
        cur_name, cur_rows = None, []
        for rows in parse_tables(wikitext):
            for row in rows:
                if not row:
                    continue
                cells = [parse_cell(c["raw"]) for c in row]
                header = topic_header_name(row, cells)
                if header is not None:
                    if cur_name is not None or cur_rows:
                        blocks.append((cur_name, cur_rows))
                    cur_name = header
                    cur_rows = []
                    continue
                if any("Dialogue" in c["dialogue"] or "Outcome" in c["dialogue"]
                       for c in cells) and len(cells) <= 2 and not cells[0]["options"]:
                    continue  # table header row
                cur_rows.append(row)
        if cur_name is not None or cur_rows:
            blocks.append((cur_name, cur_rows))

        topics = []
        for name, rows in blocks:
            name = (name or "Untitled").strip()
            topic_id = f"{tone_id}.topics.{slug(name)}"
            steps = build_topic(rows, tone_id, topic_id, name, warnings, counters)
            topics.append({"id": topic_id, "name": name, "steps": steps})
            total_topics += 1

        tones.append({
            "id": tone_id,
            "tone": tone_name,
            "voice": voice,
            "sourceSection": fname,
            "topicCount": len(topics),
            "topics": topics,
        })

    total_steps = sum(len(t["steps"]) for tone in tones for t in tone["topics"])
    unresolved_options = sum(1 for tone in tones for tp in tone["topics"]
                             for st in tp["steps"] for o in (st.get("options") or [])
                             if not o.get("stepIds"))
    demon_lines = sum(1 for tone in tones for tp in tone["topics"]
                      for st in tp["steps"] if st.get("demon"))
    demon_chars = sum(len(st.get("demon") or "") for tone in tones
                      for tp in tone["topics"] for st in tp["steps"])
    counters["unresolvedOptions"] = unresolved_options
    counters["stepsWithDemonText"] = demon_lines
    counters["demonTextChars"] = demon_chars

    payload = {
        "meta": {
            "name": "Demon negotiation dialogue — Shin Megami Tensei V",
            "usage": "Basis for the wild-Digimon talk system (tone/topic/step structure).",
            "game": "Shin Megami Tensei V",
            "sourcePage": "Negotiation Dialogue in Shin Megami Tensei V",
            "sourceUrl": SOURCE_URL,
            "sourceWiki": "Megami Tensei Wiki (Fandom); text © ATLUS",
            "extracted": "2026-11-13",
            "toneCount": len(tones),
            "topicCount": total_topics,
            "stepCount": total_steps,
            "counts": counters,
            "schema": {
                "tone": "{ id, tone, voice (Male/Female/Unique), topics[] }",
                "topic": "{ id, name, steps[] }",
                "step.kind": "prompt = demon line offering options | choice = resolution of a chosen option | branch = conditional result (good/bad, higher/lower, Pass/Fail)",
                "step.parent": "id of the step this follows from (prompt for choices, choice for nested prompts/branches)",
                "step.options": "player options [{text, random, stepIds[]}]",
                "step.outcome": "{type, raw, ...} terminal result: enemy_phase, bargain_phase, leaves, instant_recruit, gives_item, gives_macca, talk_end, dialogue_switch{to}, stat_check{stat,between}, random, next_section, ...",
                "step.branches": "for stat_check/random: [{condition, stepId}]",
                "step.marker": "special prompt marker (GUESS RANDOM MONSTER)",
                "step.alternativeOf": "when several random responses exist for the same option",
            },
            "notes": [
                "The wiki renders each negotiation topic as an ordered table; this file keeps that order and infers parent links via the listed options.",
                "Options without stepIds have no matching resolution row in the source table (the wiki article is still under construction); they are preserved as listed choices.",
                "Eight 'moon phase' topics consist only of [GOOD OUTCOME]/[BAD OUTCOME] rows in the source; they get a synthetic prompt (marker 'moon_phase') as their root.",
                "Two topic headers ('Quiz' in Tough, 'Bow' in Rookie) lost their align=\"center\" attribute in the wiki; they are still detected as topics.",
                "Some table cells are empty in the source (incomplete article); those lines are preserved as empty/none values.",
                "Line breaks inside a line are the game's text breaks (kept as \\n).",
            ],
            "warnings": warnings[:200],
        },
        "outcomesLegend": [
            {"type": "enemy_phase", "meaning": "demon attacks; turn passes to the enemy"},
            {"type": "bargain_phase", "meaning": "negotiation moves on to the bargain/demand step"},
            {"type": "instant_recruit", "meaning": "demon joins immediately"},
            {"type": "leaves", "meaning": "demon leaves the negotiation"},
            {"type": "gives_item", "meaning": "demon hands over an item (and usually leaves)"},
            {"type": "gives_macca", "meaning": "demon hands over Macca (and usually leaves)"},
            {"type": "talk_end", "meaning": "conversation ends; turn may or may not be used"},
            {"type": "dialogue_switch", "meaning": "conversation jumps to another topic (see 'to')"},
            {"type": "stat_check", "meaning": "stat comparison; outcome follows in branches"},
            {"type": "random", "meaning": "random outcome; good/bad branches follow"},
        ],
        "tones": tones,
    }

    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size:,} bytes)")
    print(f"tones={len(tones)} topics={total_topics} steps={total_steps}")
    print("counters:", counters)
    print(f"warnings={len(warnings)}")
    for w in warnings[:40]:
        print("  WARN", w)


if __name__ == "__main__":
    main()
