"""Execute the production dex queries against synthetic, card-scoped SQLite data."""
import re
import json
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/DexDao.kt"


class DexEvolutionQueriesTest(unittest.TestCase):
    def setUp(self):
        self.queries = {name: sql for sql, name in re.findall(
            r'@Query\s*\(\s*"""(.*?)"""\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
            DAO.read_text(encoding="utf-8"), re.S)}
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            CREATE TABLE Card(id INTEGER PRIMARY KEY, cardId INTEGER, name TEXT, logo BLOB, logoWidth INTEGER,
                logoHeight INTEGER, officialStatus TEXT, nameIsUserEdited INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE CardCharacter(id INTEGER, cardId INTEGER, spriteId INTEGER,
                charaIndex INTEGER, stage INTEGER, attribute TEXT, nameSprite BLOB,
                nameWidth INTEGER, nameHeight INTEGER, baseHp INTEGER, baseBp INTEGER, baseAp INTEGER);
            CREATE TABLE Sprite(id INTEGER, spriteIdle1 BLOB, spriteIdle2 BLOB, width INTEGER, height INTEGER);
            CREATE TABLE Dex(id INTEGER, discoveredOn INTEGER);
            CREATE TABLE UserCharacter(id INTEGER, charId INTEGER);
            CREATE TABLE PossibleTransformations(charaId INTEGER, toCharaId INTEGER);
            CREATE TABLE CardFusions(fromCharaId INTEGER, toCharaId INTEGER, attribute TEXT);
            CREATE TABLE CardSpecificJogress(fromCharaId INTEGER, toCharaId INTEGER, partnerCardNumber INTEGER, partnerCharaIndex INTEGER);
            INSERT INTO Card VALUES (7,42,'Zulu',X'01',1,1,'CUSTOM',0), (12,42,'alpha',X'02',1,1,'CUSTOM',0);
            INSERT INTO Sprite VALUES (309,X'10',X'11',1,1), (410,X'20',X'21',1,1);
            INSERT INTO CardCharacter VALUES
                (101,7,309,0,0,'Data',X'01',1,1,1,1,1),
                (102,7,309,1,1,'Virus',X'01',1,1,1,1,1),
                (103,7,309,2,2,'Free',X'01',1,1,1,1,1),
                (201,12,410,0,0,'Data',X'02',1,1,1,1,1);
            INSERT INTO PossibleTransformations VALUES (101,102),(101,102),(102,103),(101,201),(201,101),(101,NULL);
            INSERT INTO CardFusions VALUES (102,103,'Data'),(102,103,'Virus'),(102,201,'Free');
            INSERT INTO Dex VALUES (103,1234);
        """)

    def tearDown(self):
        self.db.close()

    def run_query(self, name, **args):
        self.assertIn(name, self.queries, f"Missing production query {name}")
        return list(self.db.execute(self.queries[name], args))

    def test_routes_are_card_scoped_deduplicated_and_preserve_fusion_conditions(self):
        rows = self.run_query("getCardEvolutionLinks", cardId=7)
        self.assertEqual({(101,102,None),(102,103,None),(102,103,'Data'),(102,103,'Virus')},
                         {(r['fromId'],r['toId'],r['fusionAttribute']) for r in rows})
        self.assertEqual(4, len(rows))
        self.assertEqual([], self.run_query("getCardEvolutionLinks", cardId=12))

    def test_all_characters_remain_visible_and_ownership_changes_without_changing_topology(self):
        rows = self.run_query("getSingleCardProgress", cardId=7)
        self.assertEqual([0,1,2], [r['charaIndex'] for r in rows])
        self.assertEqual([None,None,1234], [r['discoveredOn'] for r in rows])
        self.assertEqual([0,0,0], [r['isCurrentlyAvailable'] for r in rows])
        self.assertEqual(b'\x10', rows[0]['spriteIdle'])
        self.db.execute("INSERT INTO UserCharacter VALUES (99,103)")
        self.assertEqual([0,0,1], [r['isCurrentlyAvailable'] for r in self.run_query("getSingleCardProgress",cardId=7)])
        self.db.execute("DELETE FROM UserCharacter")
        last = self.run_query("getSingleCardProgress",cardId=7)[-1]
        self.assertEqual(1234, last['discoveredOn'])
        self.assertEqual(0, last['isCurrentlyAvailable'])

    def test_dim_order_and_progress_count_characters_not_owned_individuals(self):
        self.db.executescript("INSERT INTO UserCharacter VALUES (99,103),(100,103);")
        rows = self.run_query("getCardsWithProgress")
        self.assertEqual([12,7], [r['cardId'] for r in rows])
        self.assertEqual(3, rows[1]['totalCharacters'])
        self.assertEqual(1, rows[1]['obtainedCharacters'])

    def test_fusion_details_resolve_the_same_destination_sprite_as_the_chart(self):
        source = (DAO.parent / "CardFusionsDao.kt").read_text(encoding="utf-8")
        queries = {name: sql for sql, name in re.findall(
            r'@Query\s*\(\s*"""(.*?)"""\s*\)\s*(?:suspend\s+)?fun\s+(\w+)', source, re.S)}
        rows = list(self.db.execute(queries['getFusionsForCharacter'], {'charaId': 102}))
        destination = self.run_query("getSingleCardProgress", cardId=7)[-1]
        local = [row for row in rows if row['charaId'] == 103]
        self.assertEqual(2, len(local))
        self.assertEqual(destination['spriteIdle'], local[0]['spriteIdle'])

    def test_specific_jogress_connects_both_local_partners_without_leaking_into_a_custom_variant(self):
        self.db.execute("INSERT INTO CardSpecificJogress VALUES (101,103,42,1)")
        rows = self.run_query("getCardEvolutionLinks", cardId=7)
        specific = {(r['fromId'], r['toId']) for r in rows if r['isJogress'] and r['fusionAttribute'] is None}
        self.assertEqual({(101,103),(102,103)}, specific)
        self.assertEqual([], self.run_query("getCardEvolutionLinks", cardId=12))

    def test_external_jogress_partner_is_retained_as_a_requirement_without_a_false_local_link(self):
        self.db.execute("INSERT INTO CardSpecificJogress VALUES (101,103,999,1)")
        rows = self.run_query("getCardEvolutionLinks", cardId=7)
        specific = {(r['fromId'], r['toId']) for r in rows if r['isJogress'] and r['fusionAttribute'] is None}
        self.assertEqual({(101,103)}, specific)

    def test_automatic_name_refresh_and_manual_name_protection_execute_production_queries(self):
        source = (DAO.parent / "CardDao.kt").read_text(encoding="utf-8")
        queries = {name: multiline or single for multiline, single, name in re.findall(
            r'@Query\s*\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)', source, re.S)}
        self.assertIn('refreshImportedName', queries)
        self.db.execute(queries['refreshImportedName'], {'id':7, 'newName':'New filename'})
        self.assertEqual('New filename', self.db.execute('SELECT name FROM Card WHERE id=7').fetchone()[0])
        self.db.execute(queries['renameCard'], {'id':7, 'newName':'My name'})
        self.db.execute(queries['refreshImportedName'], {'id':7, 'newName':'Another filename'})
        self.assertEqual(('My name',1), tuple(self.db.execute('SELECT name,nameIsUserEdited FROM Card WHERE id=7').fetchone()))


class JogressMigrationTest(unittest.TestCase):
    def test_additive_migration_preserves_existing_rows_and_matches_the_new_room_table(self):
        path = ROOT / 'app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase'
        old = json.loads((path / '30.json').read_text(encoding='utf-8'))['database']
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.execute('PRAGMA foreign_keys=ON')
        for entity in old['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        def seed(table, **overrides):
            values = dict(overrides)
            for _, name, kind, required, default, _ in db.execute(f'PRAGMA table_info(`{table}`)'):
                if name not in values and required and default is None and name != 'id':
                    values[name] = '' if kind == 'TEXT' else b'\0' if kind == 'BLOB' else 0
            db.execute(f"INSERT INTO `{table}` ({','.join(values)}) VALUES ({','.join('?' for _ in values)})",tuple(values.values()))
        seed('Card',id=7,cardId=42,name='Saved card')
        seed('Card',id=12,cardId=42,name='BEM card',isBEm=1)
        seed('Sprite',id=309)
        seed('CardCharacter',id=101,cardId=7,spriteId=309,charaIndex=0,attribute='Data')
        seed('CardCharacter',id=102,cardId=7,spriteId=309,charaIndex=1,attribute='Data')
        seed('CardCharacter',id=201,cardId=12,spriteId=309,charaIndex=0,attribute='Data')
        seed('CardCharacter',id=202,cardId=12,spriteId=309,charaIndex=1,attribute='Data')
        seed('PossibleTransformations',charaId=101,toCharaId=102,requiredAdventureLevelCompleted=0)
        seed('PossibleTransformations',charaId=201,toCharaId=202,requiredAdventureLevelCompleted=0)
        seed('Dex',id=101,discoveredOn=1234)
        seed('DigimonIndividual',individualId='owned-test')
        seed('UserCharacter',id=99,charId=101,individualId='owned-test')
        before = {table: db.execute(f'SELECT * FROM {table}').fetchall() for table in ('CardCharacter','Dex','DigimonIndividual','UserCharacter')}
        migration = ROOT / 'app/src/main/java/com/github/nacabaro/vbhelper/database/CardJogressSchema.kt'
        source = migration.read_text(encoding='utf-8')
        statements = re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)',source,re.S)
        for multiline,single in statements:
            db.execute(multiline or single)
        for table,rows in before.items():
            self.assertEqual(rows,db.execute(f'SELECT * FROM {table}').fetchall())
        self.assertEqual(('Saved card',0),db.execute('SELECT name,nameIsUserEdited FROM Card WHERE id=7').fetchone())
        self.assertEqual(-1,db.execute('SELECT requiredAdventureLevelCompleted FROM PossibleTransformations WHERE charaId=101').fetchone()[0])
        self.assertEqual(0,db.execute('SELECT requiredAdventureLevelCompleted FROM PossibleTransformations WHERE charaId=201').fetchone()[0])
        new = json.loads((path / '31.json').read_text(encoding='utf-8'))['database']
        entity = next(e for e in new['entities'] if e['tableName']=='CardSpecificJogress')
        fresh = sqlite3.connect(':memory:')
        self.addCleanup(fresh.close)
        fresh.execute(entity['createSql'].replace('${TABLE_NAME}','CardSpecificJogress'))
        for pragma in ('table_info','foreign_key_list'):
            self.assertEqual(fresh.execute(f'PRAGMA {pragma}(CardSpecificJogress)').fetchall(),db.execute(f'PRAGMA {pragma}(CardSpecificJogress)').fetchall())
        db.execute('INSERT INTO CardSpecificJogress VALUES (101,102,42,1)')
        db.execute('DELETE FROM CardCharacter WHERE id=102')
        self.assertEqual([],db.execute('SELECT * FROM CardSpecificJogress').fetchall())


if __name__ == "__main__":
    unittest.main()
