"""Inspect LE bindings, individuals and any dangling references behind the failed move."""
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
SRC = ROOT / "analysis/dsle/device/db2"
WORK = SRC / "work2.db"
for suffix in ("", "-wal"):
    target = Path(str(WORK) + suffix)
    if target.exists():
        target.unlink()
shutil.copy(SRC / "internalDb", WORK)
shutil.copy(SRC / "internalDb-wal", Path(str(WORK) + "-wal"))
conn = sqlite3.connect(WORK)
conn.row_factory = sqlite3.Row


def cols(table):
    return [row[1] for row in conn.execute(f'pragma table_info("{table}")')]


print("bindings columns:", cols("ExternalIndividualBinding"))
print("personality columns:", cols("DigimonPersonalityTraits"))

print("\n=== LE bindings")
for row in conn.execute("select * from ExternalIndividualBinding where gameId like '%lost%' or nativeIdentity like '%30405f0%'"):
    print(" ", dict(row))

print("\n=== LE snapshots")
for row in conn.execute("select id, individualId, bindingId, contentRevision, recordKey, importedAt from NativeGameSnapshot where bindingId in (select bindingId from ExternalIndividualBinding where gameId like '%lost%') order by id desc"):
    print(" ", dict(row))

print("\n=== individuals like 6a83b4 / 23389599 / 9e0a1e")
for row in conn.execute("select * from DigimonIndividual where individualId like '6a83b4%' or individualId like '23389599%' or individualId like '9e0a1e%'"):
    print(" ", dict(row))

print("\n=== personality rows for those")
for row in conn.execute("select * from DigimonPersonalityTraits where individualId like '6a83b4%' or individualId like '23389599%' or individualId like '9e0a1e%'"):
    print(" ", dict(row))

print("\n=== bindings for those individuals")
for row in conn.execute("select * from ExternalIndividualBinding where individualId like '6a83b4%' or individualId like '23389599%' or individualId like '9e0a1e%'"):
    print(" ", dict(row))
