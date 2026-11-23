"""Inspect card/DIM origin data for the reported Agumon (Black) partner."""
import sqlite3
from pathlib import Path

DB = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\internalDb")
INDIVIDUAL = "b8bfaf784b89f62d16187b"


def columns(con, table):
    return [row[1] for row in con.execute(f"PRAGMA table_info({table})")]


def show(con, label, sql, params=(), limit=20):
    print(f"\n--- {label}")
    try:
        rows = [dict(row) for row in con.execute(sql, params)]
    except sqlite3.Error as error:
        print("   error:", error)
        return
    print(f"   rows={len(rows)}")
    for row in rows[:limit]:
        print("   ", row)


def main():
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    for table in ("Card", "CardCharacter", "Character", "WatchRepresentationSnapshot",
                  "AppPartnerProgress", "VBCharacterData", "BECharacterData", "FirstPartnerEncounter",
                  "DigimonStateSnapshot", "IndividualSocialState"):
        try:
            print(f"\n{table}: {columns(con, table)}")
        except sqlite3.Error as error:
            print(f"\n{table}: {error}")
    print("\n=== user characters of the individual")
    show(con, "characters", "SELECT * FROM UserCharacter WHERE individualId=?", (INDIVIDUAL,))
    show(con, "all characters of same charId", "SELECT * FROM UserCharacter WHERE charId IN "
         "(SELECT charId FROM UserCharacter WHERE individualId=?)", (INDIVIDUAL,))
    show(con, "character rows", "SELECT * FROM Character LIMIT 5")
    show(con, "card characters of charId", "SELECT * FROM CardCharacter LIMIT 20")
    show(con, "card rows", "SELECT * FROM Card LIMIT 10")
    show(con, "watch representations", "SELECT * FROM WatchRepresentationSnapshot WHERE individualId=?",
         (INDIVIDUAL,))
    show(con, "partner progress", "SELECT * FROM AppPartnerProgress WHERE individualId=?", (INDIVIDUAL,))
    show(con, "vb data", "SELECT * FROM VBCharacterData WHERE charId IN "
         "(SELECT charId FROM UserCharacter WHERE individualId=?)", (INDIVIDUAL,))
    show(con, "be data", "SELECT * FROM BECharacterData WHERE charId IN "
         "(SELECT charId FROM UserCharacter WHERE individualId=?)", (INDIVIDUAL,))
    show(con, "first partner encounter", "SELECT * FROM FirstPartnerEncounter LIMIT 10")
    show(con, "state snapshot", "SELECT * FROM DigimonStateSnapshot WHERE individualId=?", (INDIVIDUAL,))
    show(con, "social", "SELECT * FROM IndividualSocialState WHERE individualId=?", (INDIVIDUAL,))


if __name__ == "__main__":
    main()
