"""Inspect Lost Evolution individuals, bindings, snapshots and personalities in the app DB."""
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SRC = ROOT / "analysis/dsle/device/db2"
SRC.mkdir(parents=True, exist_ok=True)
DB = SRC / "internalDb"
WAL = SRC / "internalDb-wal"
WORK = SRC / "work.db"
if not DB.exists():
    raise SystemExit("pull the db first")
if WORK.exists():
    WORK.unlink()
shutil.copy(DB, WORK)
if WAL.exists():
    shutil.copy(WAL, Path(str(WORK) + "-wal"))
conn = sqlite3.connect(WORK)
conn.row_factory = sqlite3.Row


def show(label, query):
    print(f"\n=== {label}")
    try:
        for row in conn.execute(query):
            print(" ", {k: (v[:80] + "..." if isinstance(v, str) and len(v) > 80 else v)
                        for k, v in dict(row).items()})
    except Exception as error:  # noqa: BLE001
        print("  error:", error)


show("individuals", "select * from DigimonIndividual order by createdAt desc limit 15")
show("personalities", "select * from DigimonPersonalityTraits order by createdAt desc limit 15")
show("bindings", "select * from ExternalIndividualBinding order by createdAt desc limit 15")
show("snapshots", "select id, individualId, bindingId, contentRevision, recordKey, importedAt from NativeGameSnapshot order by id desc limit 15")
show("presence", "select * from GameEndpointPresence")
show("receipts", "select * from NativeMaterializationReceipt order by createdAt desc limit 10")
show("linked saves", "select sourceId, displayName, lastError from LinkedGameSave limit 10")
