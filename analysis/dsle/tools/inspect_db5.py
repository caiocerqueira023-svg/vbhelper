"""Inspect bindings/schema constraints relevant to the failed Dorumon acquisition."""
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SRC = ROOT / "analysis/dsle/device/db2"
WORK = SRC / "work3.db"
for suffix in ("", "-wal"):
    target = Path(str(WORK) + suffix)
    if target.exists():
        target.unlink()
shutil.copy(SRC / "internalDb", WORK)
shutil.copy(SRC / "internalDb-wal", Path(str(WORK) + "-wal"))
conn = sqlite3.connect(WORK)
conn.row_factory = sqlite3.Row

print("=== all ExternalIndividualBinding rows")
for row in conn.execute("select bindingId, gameId, identityScheme, nativeIdentity, individualId, sourceId from ExternalIndividualBinding"):
    print(" ", dict(row))

print("\n=== bindings for 98776689")
print(list(conn.execute("select * from ExternalIndividualBinding where bindingId like '98776689%'")))

print("\n=== schema")
for row in conn.execute("select sql from sqlite_master where name in ('ExternalIndividualBinding','NativeGameSnapshot','NativeSaveContainer','NativeSaveContainerChunk','NativeSnapshotContainer')"):
    print(row[0])
    print()
