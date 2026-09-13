"""Read-only snapshot audit. Never opens or modifies the phone's live SQLite database."""
from pathlib import Path
import io
import json
import sqlite3
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent.parent
ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = ROOT / "app/build/identity-device-check"
OUT.mkdir(parents=True, exist_ok=True)
payload = subprocess.run([
    str(ADB), "exec-out", "run-as", "com.github.nacabaro.vbhelper", "tar", "cf", "-",
    "databases/internalDb", "databases/internalDb-wal",
], check=True, capture_output=True).stdout
with tarfile.open(fileobj=io.BytesIO(payload)) as archive:
    for name in ("databases/internalDb", "databases/internalDb-wal"):
        data = archive.extractfile(name)
        if data:
            (OUT / Path(name).name).write_bytes(data.read())
db = sqlite3.connect(f"file:{(OUT / 'internalDb').as_posix()}?mode=ro", uri=True)
def scalar(sql):
    return db.execute(sql).fetchone()[0]
report = {
    "database_version": scalar("PRAGMA user_version"),
    "quick_check": scalar("PRAGMA quick_check"),
    "foreign_key_errors": len(db.execute("PRAGMA foreign_key_check").fetchall()),
    "stored_characters": scalar("SELECT count(*) FROM UserCharacter"),
    "permanent_individuals": scalar("SELECT count(*) FROM DigimonIndividual"),
    "blank_storage_identities": scalar("SELECT count(*) FROM UserCharacter WHERE length(trim(individualId)) = 0"),
    "duplicate_storage_identities": scalar("SELECT count(*) FROM (SELECT individualId FROM UserCharacter GROUP BY individualId HAVING count(*) > 1)"),
    "missing_permanent_individuals": scalar("SELECT count(*) FROM UserCharacter u LEFT JOIN DigimonIndividual d USING(individualId) WHERE d.individualId IS NULL"),
    "simultaneous_world_and_storage": scalar("SELECT count(*) FROM WorldSpawn w JOIN UserCharacter u USING(individualId)"),
    "orphan_chat_messages": scalar("SELECT count(*) FROM ChatMessageEntity c LEFT JOIN DigimonIndividual d USING(individualId) WHERE d.individualId IS NULL"),
    "histories_above_current_stage": scalar("SELECT count(*) FROM TransformationHistory h JOIN CardCharacter past ON past.id=h.stageId JOIN UserCharacter u ON u.id=h.monId JOIN CardCharacter current ON current.id=u.charId WHERE past.stage>current.stage OR (past.stage=current.stage AND past.id!=current.id)"),
}
(OUT / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
db.close()
