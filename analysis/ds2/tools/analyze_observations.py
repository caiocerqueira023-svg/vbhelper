"""Analyze the cached Dawn/Dusk observations to explain the authoritative-copy failure."""
import json
import sqlite3
from pathlib import Path

DB = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\internalDb")


def main():
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    for row in con.execute("SELECT * FROM LinkedSaveObservation"):
        snapshot = json.loads(row["snapshotJson"])
        print(f"=== {row['gameId']} observedAt={row['observedAt']} revision={row['contentRevision'][:12]}")
        print(f"    copyIntegrity={snapshot.get('copyIntegrity')} notices={snapshot.get('notices')}")
        records = snapshot.get("records", [])
        print(f"    records={len(records)}")
        by_copy = {}
        for record in records:
            parameters = record.get("nativeParameters") or {}
            copy = parameters.get("saveBlock")
            by_copy.setdefault(copy, []).append(record)
        for copy, entries in sorted(by_copy.items(), key=lambda item: (item[0] is None, item[0])):
            sequences = sorted({(entry.get("nativeParameters") or {}).get("saveSequence") for entry in entries},
                               key=lambda value: (value is None, value))
            containers = sorted({entry.get("container") for entry in entries})
            issues = sorted({issue for entry in entries for issue in entry.get("issues", [])})
            identities = [entry.get("nativeIdentity") for entry in entries]
            print(f"    copy {copy}: n={len(entries)} sequences={sequences} containers={containers}")
            print(f"        issues={issues}")
            print(f"        identities={identities}")
        print()


if __name__ == "__main__":
    main()
