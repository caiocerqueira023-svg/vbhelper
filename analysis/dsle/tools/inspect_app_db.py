"""Inspect the pulled app database for Lost Evolution transfer state."""
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SRC = ROOT / "analysis/dsle/device/db"
WORK = SRC / "work.db"
if WORK.exists():
    WORK.unlink()
shutil.copy(SRC / "internalDb", WORK)
shutil.copy(SRC / "internalDb-wal", Path(str(WORK) + "-wal"))
conn = sqlite3.connect(WORK)
conn.row_factory = sqlite3.Row

names = [row[0] for row in conn.execute("select name from sqlite_master where type='table' order by name")]
interesting = [name for name in names if any(key in name.lower() for key in
              ("game", "transfer", "material", "presence", "archive", "linked", "journal", "native"))]
print("interesting tables:", interesting)

for name in interesting:
    columns = [row[1] for row in conn.execute(f'pragma table_info("{name}")')]
    print(f"\n== {name}: {columns}")

def dump(table, limit=5, order=None):
    try:
        query = f'select * from "{table}"'
        if order:
            query += f' order by {order} desc'
        query += f' limit {limit}'
        for row in conn.execute(query):
            print(f"  {dict(row)}")
    except Exception as error:  # noqa: BLE001
        print(f"  could not dump {table}: {error}")

print("\n=== linked game saves")
dump("linked_game_save", 10)
print("\n=== native presence")
dump("native_game_presence", 10)
print("\n=== native archive bindings")
dump("native_game_archive_binding", 10)
print("\n=== import receipts")
dump("game_import_receipt", 10)
