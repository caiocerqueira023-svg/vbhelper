"""Dump the Lost Evolution rows from the pulled app database (real table names)."""
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


def dump(query, label):
    print(f"\n=== {label}")
    for row in conn.execute(query):
        print(" ", {key: (value[:120] + "..." if isinstance(value, str) and len(value) > 120 else value)
                   for key, value in dict(row).items()})


dump("select * from LinkedGameSave", "LinkedGameSave")
dump("select sourceId, contentRevision, observedAt, gameId, formatProfile from LinkedSaveObservation", "LinkedSaveObservation")
dump("select * from LinkedSaveLocation", "LinkedSaveLocation")
dump("select * from GameEndpointPresence", "GameEndpointPresence")
dump("select individualId, id, bindingId, contentRevision, recordKey, importedAt from NativeGameSnapshot order by importedAt desc limit 8", "NativeGameSnapshot")
dump("select * from NativeMaterializationReceipt order by createdAt desc limit 8", "NativeMaterializationReceipt")
dump("select individualId, snapshotId, projectionVersion, baselineKind, recordedAt from NativeAppProjection order by recordedAt desc limit 8", "NativeAppProjection")
print("\n=== native containers (archived saves)")
for row in conn.execute("select revision, byteCount, archivedAt from NativeSaveContainer order by archivedAt desc limit 8"):
    print(" ", dict(row))
