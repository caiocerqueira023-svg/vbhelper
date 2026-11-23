"""Focused queries: what species/card backs the reported partner."""
import sqlite3
from pathlib import Path

DB = Path(r"C:\Users\julye\IdeaProjects\vbhelper\analysis\ds2\device\internalDb")
INDIVIDUAL = "b8bfaf784b89f62d16187b"


def q(con, label, sql, params=()):
    print(f"\n--- {label}")
    try:
        rows = [dict(row) for row in con.execute(sql, params)]
    except sqlite3.Error as error:
        print("   error:", error)
        return []
    for row in rows:
        print("   ", row)
    if not rows:
        print("   <none>")
    return rows


def main():
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    characters = q(con, "user characters", "SELECT id, charId, individualId, isActive, exportCardCharacterId "
                                         "FROM UserCharacter WHERE individualId=?", (INDIVIDUAL,))
    q(con, "individual", "SELECT individualId, nickname, speciesName, originKind, originCardCharacterId "
                         "FROM DigimonIndividual WHERE individualId=?", (INDIVIDUAL,))
    card_char_ids = {row["exportCardCharacterId"] for row in characters if row["exportCardCharacterId"]}
    print("\nexportCardCharacterId values:", card_char_ids)
    for card_char_id in card_char_ids:
        q(con, "card character by id", "SELECT id, cardId, speciesId, catalogSpeciesId, stage, attribute, "
                                       "baseHp, baseBp, baseAp FROM CardCharacter WHERE id=?", (card_char_id,))
    q(con, "card character by rowid approx", "SELECT id, cardId, speciesId, catalogSpeciesId, stage FROM CardCharacter "
                                             "WHERE id IN (SELECT id FROM CardCharacter LIMIT 0)")
    # cardCharacterId could be the ordinal id in a card-specific table; look for small ids
    q(con, "small card character ids", "SELECT id, cardId, speciesId, catalogSpeciesId, stage FROM CardCharacter "
                                       "WHERE id BETWEEN 1 AND 20")
    # UserCharacter.charId is negative and large: maybe references VBCharacterData primary key
    char_ids = {row["charId"] for row in characters}
    print("\ncharId values:", char_ids)
    for char_id in char_ids:
        q(con, "VBCharacterData", "SELECT * FROM VBCharacterData WHERE id=?", (char_id,))
        q(con, "BECharacterData", "SELECT * FROM BECharacterData WHERE id=?", (char_id,))
    # what cards are in the library containing agumon variants?
    q(con, "card characters named agumon", "SELECT id, cardId, speciesId, catalogSpeciesId, stage FROM CardCharacter "
                                           "WHERE speciesId LIKE '%agumon%' OR catalogSpeciesId LIKE '%agumon%'")
    q(con, "cards of agumon characters", "SELECT c.id, c.cardId, cc.speciesId, cc.catalogSpeciesId "
                                         "FROM Card c JOIN CardCharacter cc ON cc.cardId=c.id "
                                         "WHERE cc.speciesId LIKE '%agumon%'")
    q(con, "card progress", "SELECT * FROM CardProgress LIMIT 0")
    q(con, "cards", "SELECT id, cardId, name, officialStatus FROM Card")


if __name__ == "__main__":
    main()
