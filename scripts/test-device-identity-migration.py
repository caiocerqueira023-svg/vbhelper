"""Exercise production migration SQL on an in-memory copy of the read-only device snapshot."""
from pathlib import Path
import hashlib
import json
import re
import sqlite3

ROOT = Path(__file__).resolve().parent.parent
source = ROOT / "app/build/identity-device-check/internalDb"
original = sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True)
db = sqlite3.connect(":memory:")
original.backup(db)
original.close()
db.execute("PRAGMA foreign_keys=ON")
assert db.execute("PRAGMA user_version").fetchone()[0] == 17, "Expected a version 17 snapshot"

tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
columns = {t: [r[1] for r in db.execute(f'PRAGMA table_info("{t}")')] for t in tables}
def digest(table):
    names = ', '.join('"' + c + '"' for c in columns[table])
    rows = sorted(repr(tuple(r)) for r in db.execute(f'SELECT {names} FROM "{table}"'))
    return hashlib.sha256('\n'.join(rows).encode()).hexdigest()
before = {t: digest(t) for t in tables}

def statements(code):
    for multi, single in re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*(?:\.trimIndent\(\))?\s*\)', code, re.S):
        yield multi or single

directory = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/database"
code = (directory / "AppDatabase.kt").read_text(encoding="utf-8")
migration = code.split("val MIGRATION_17_18", 1)[1].split("val MIGRATION_16_17", 1)[0]
guards = (directory / "IndividualIntegrity.kt").read_text(encoding="utf-8")
with db:
    for sql in statements(migration):
        db.execute(sql)
    for sql in statements(guards):
        db.execute(sql)
assert before == {t: digest(t) for t in tables}, "Migration changed existing data"
assert not db.execute("PRAGMA foreign_key_check").fetchall()
# Installing safeguards a second time must be harmless.
with db:
    for sql in statements(guards):
        db.execute(sql)
assert before == {t: digest(t) for t in tables}
report = {"existing_tables_checked": len(tables), "all_existing_rows_preserved": True,
          "foreign_key_errors": 0, "guard_installation_idempotent": True}
(ROOT / "app/build/identity-device-check/migration-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
db.close()
