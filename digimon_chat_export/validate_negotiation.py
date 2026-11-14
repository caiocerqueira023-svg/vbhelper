# -*- coding: utf-8 -*-
"""Structural validation for demon_negotiation_dialogue_smt5.json."""
import json
from collections import Counter
from pathlib import Path

D = json.loads((Path(__file__).resolve().parent / "demon_negotiation_dialogue_smt5.json")
               .read_text(encoding="utf-8"))

steps = {}
for tone in D["tones"]:
    for topic in tone["topics"]:
        for st in topic["steps"]:
            assert st["id"] not in steps, f"duplicate id {st['id']}"
            steps[st["id"]] = st

meta = D["meta"]
print(f"tones={len(D['tones'])} topics={meta['topicCount']} steps={meta['stepCount']}")
print("kinds:", dict(Counter(s["kind"] for s in steps.values())))

errors = []
for sid, st in steps.items():
    parent = st.get("parent")
    if parent is not None and parent not in steps:
        errors.append(f"{sid}: missing parent {parent}")
    for opt in st.get("options") or []:
        for child in opt.get("stepIds", []):
            if child not in steps:
                errors.append(f"{sid}: missing option target {child}")
    for br in st.get("branches") or []:
        if br["stepId"] not in steps:
            errors.append(f"{sid}: missing branch target {br['stepId']}")
    if st["kind"] == "branch":
        p = steps.get(parent)
        if p and (p.get("outcome") or {}).get("type") not in ("stat_check", "random") \
                and p.get("marker") != "moon_phase":
            errors.append(f"{sid}: branch under unexpected parent")

# every topic has exactly one root step
for tone in D["tones"]:
    for topic in tone["topics"]:
        roots = [s for s in topic["steps"] if s.get("parent") is None]
        if len(roots) != 1:
            errors.append(f"{topic['id']}: {len(roots)} roots")

# no wikitext artifacts in string values
def strings(node):
    if isinstance(node, str):
        yield node
    elif isinstance(node, dict):
        for v in node.values():
            yield from strings(v)
    elif isinstance(node, list):
        for v in node:
            yield from strings(v)

artifacts = Counter()
for s in strings(D):
    for token in ("<br", "{{", "}}", "[[", "]]", "'''", "&amp;"):
        if token in s:
            artifacts[token] += s.count(token)
if artifacts:
    errors.append(f"wikitext artifacts remain: {dict(artifacts)}")

print("option gaps (source incomplete):", sum(
    1 for s in steps.values() for o in (s.get("options") or []) if not o.get("stepIds")))
print("errors:", len(errors))
for e in errors[:10]:
    print("  ", e)
assert not errors
print("OK")
