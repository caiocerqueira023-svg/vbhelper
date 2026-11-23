"""Inspect the app DB around the reported Agumon (Black) send and DIM association."""
import json
import sqlite3
from pathlib import Path

DB = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\internalDb")


def rows(con, sql, params=()):
    try:
        return [dict(row) for row in con.execute(sql, params)]
    except sqlite3.Error as error:
        return [{"error": str(error)}]


def show(label, data, limit=12):
    print(f"\n--- {label} rows={len(data)}")
    for row in data[:limit]:
        print("   ", row)


def main():
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    individuals = rows(con, "SELECT individualId, nickname, speciesName, originKind, originCardCharacterId "
                            "FROM DigimonIndividual WHERE lower(speciesName) LIKE '%agumon%' "
                            "OR lower(nickname) LIKE '%agumon%' OR lower(nickname) LIKE '%greymon%'")
    show("agumon-ish individuals", individuals)
    for individual in individuals:
        individual_id = individual["individualId"]
        print(f"\n=== {individual_id} nickname={individual['nickname']} species={individual['speciesName']}")
        show("user characters", rows(con,
             "SELECT id, charId, individualId, isActive, exportCardCharacterId FROM UserCharacter "
             "WHERE individualId=?", (individual_id,)))
        show("card of representative character", rows(con,
             "SELECT c.cardId, c.charId, c.speciesId, c.catalogSpeciesId, c.stage FROM Card c WHERE c.charId IN "
             "(SELECT charId FROM UserCharacter WHERE individualId=?)", (individual_id,)))
        show("snapshots", rows(con,
             "SELECT id, bindingId, sourceLabel, recordKey, importedAt FROM NativeGameSnapshot "
             "WHERE individualId=? ORDER BY importedAt", (individual_id,)))
        show("canonical mappings", rows(con,
             "SELECT m.* FROM NativeCanonicalMapping m JOIN NativeGameSnapshot s ON s.id=m.snapshotId "
             "WHERE s.individualId=?", (individual_id,)))
        show("bindings", rows(con,
             "SELECT bindingId, gameId, formatProfile, identityScheme, nativeIdentity, origin FROM "
             "ExternalIndividualBinding WHERE individualId=?", (individual_id,)))
        show("presence", rows(con, "SELECT * FROM GameEndpointPresence WHERE individualId=?", (individual_id,)))
        show("life events", rows(con,
             "SELECT kind, occurredAt, detail FROM IndividualLifeEvent WHERE individualId=? "
             "ORDER BY occurredAt DESC", (individual_id,)))
    # Which native species ids are archived in snapshots overall (to spot the Agumon variants)?
    show("recent native snapshots (all individuals)", rows(con,
         "SELECT individualId, sourceLabel, recordKey, importedAt FROM NativeGameSnapshot "
         "ORDER BY importedAt DESC"), 20)


if __name__ == "__main__":
    main()
