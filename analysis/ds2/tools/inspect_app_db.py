"""Inspect the app's linked-save observations for the failing send destination."""
import json
import sqlite3
import sys
from pathlib import Path

DB = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\internalDb")


def default(value):
    if isinstance(value, (bytes, bytearray)):
        return f"<{len(value)} bytes>"
    return str(value)


def main(db_path=DB):
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    tables = [row[0] for row in con.execute("SELECT name FROM sqlite_master WHERE type='table'")]
    relevant = [t for t in tables if "Linked" in t or "Save" in t or "Presence" in t]
    print("tables:", relevant)
    for table in relevant:
        print(f"\n=== {table}")
        try:
            rows = list(con.execute(f"SELECT * FROM {table}"))
        except sqlite3.Error as error:
            print("  error:", error)
            continue
        print(f"  rows={len(rows)}")
        for row in rows[:20]:
            record = dict(row)
            for key, value in list(record.items()):
                if isinstance(value, str) and len(value) > 300:
                    record[key] = value[:300] + f"...<{len(value)} chars>"
            print("  ", json.dumps(record, ensure_ascii=False, default=default)[:1500])


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else DB)
