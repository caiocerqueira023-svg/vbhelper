"""Summarize the game send/move journal records pulled from the device."""
import json
from pathlib import Path

DEVICE = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device")


def summarize(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    record = data.get("record") or {}
    parameters = record.get("nativeParameters") or {}
    outbound = {
        "operation": data.get("operationId", "")[:8],
        "kind": "send" if "sends" in str(path) else "move",
        "phase": data.get("phase"),
        "at": data.get("createdAt"),
        "source": Path(data.get("sourceId", "")).name,
        "game": (data.get("observation") or {}).get("gameId"),
        "individual": (data.get("individualId") or "")[:8],
        "generated": data.get("generated"),
        "replacing": data.get("replacing"),
        "recordKey": record.get("recordKey"),
        "identity": record.get("nativeIdentity"),
        "nativeSpeciesId": record.get("nativeSpeciesId"),
        "storedName": record.get("storedName"),
        "level": record.get("level"),
        "maxLevel": parameters.get("maxLevel"),
    }
    print(json.dumps(outbound, ensure_ascii=False))


def main():
    events = []
    for folder in ("experimental-game-sends", "experimental-game-moves"):
        for path in sorted((DEVICE / folder).glob("*.json")):
            before = len(events)
            summarize(path)
    print("\n=== sortable timeline")
    timeline = []
    for folder in ("experimental-game-sends", "experimental-game-moves"):
        for path in sorted((DEVICE / folder).glob("*.json")):
            data = json.loads(path.read_text(encoding="utf-8"))
            record = data.get("record") or {}
            timeline.append({
                "at": data.get("createdAt"),
                "kind": "send" if folder.endswith("sends") else "move",
                "phase": data.get("phase"),
                "individual": (data.get("individualId") or "")[:8],
                "nativeSpeciesId": record.get("nativeSpeciesId"),
                "storedName": record.get("storedName"),
                "level": record.get("level"),
                "recordKey": record.get("recordKey"),
                "identity": record.get("nativeIdentity"),
                "generated": data.get("generated"),
                "replacing": data.get("replacing"),
                "operation": data.get("operationId", "")[:8],
            })
    for row in sorted(timeline, key=lambda item: item["at"] or 0):
        print(json.dumps(row, ensure_ascii=False))


if __name__ == "__main__":
    main()
