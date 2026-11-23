"""Inspect the latest Lost Evolution transfer operations recorded by the app."""
import json
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
for name in ("move1", "move2", "send1"):
    op = json.loads((ROOT / "analysis/dsle/device/ops" / f"{name}.json").read_text(encoding="utf-8"))
    print(f"\n== {name}: {op.get('phase')}  type={'move' if 'canonicalSpeciesId' in op else '?'}")
    print("  sourceRevision:", op.get("sourceRevision"))
    print("  candidateRevision:", op.get("candidateRevision"))
    print("  documentKey:", op.get("documentKey", "")[:160])
    observation = op.get("observation") or {}
    print("  observation:", observation.get("gameId"), observation.get("formatProfile"),
          "codec", observation.get("codecVersion"), "records", len(observation.get("records") or []))
    record = op.get("record") or {}
    print("  record:", record.get("recordKey"), record.get("nativeIdentity"), "species",
          record.get("nativeSpeciesId"), "name", record.get("storedName"), "container", record.get("container"),
          "level", record.get("level"), "issues", record.get("issues"))
    print("  individualId:", op.get("individualId"), "bindingId:", op.get("bindingId"),
          "generated", op.get("generated"), "replaced", op.get("replaced"))
    if name.startswith("move"):
        print("  canonicalSpeciesId:", op.get("canonicalSpeciesId"), "authority:", op.get("canonicalAuthority"))
