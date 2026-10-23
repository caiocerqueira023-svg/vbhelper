package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object SourceIndependentSchema {
    /** Called with foreign keys disabled before Room's upgrade transaction by PreservingMigrationFactory. */
    fun migrate(db: SupportSQLiteDatabase) {
        check(db.query("PRAGMA foreign_keys").use { it.moveToFirst(); it.getInt(0) == 0 }) {
            "Source-independent migration requires the preserving open-helper factory"
        }
        db.execSQL("DROP VIEW IF EXISTS DisplaySprite")
        db.execSQL("""CREATE TABLE CardCharacter44 (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cardId INTEGER, spriteId INTEGER NOT NULL,
            charaIndex INTEGER NOT NULL, stage INTEGER NOT NULL, attribute TEXT NOT NULL,
            baseHp INTEGER NOT NULL, baseBp INTEGER NOT NULL, baseAp INTEGER NOT NULL,
            nameSprite BLOB NOT NULL, nameWidth INTEGER NOT NULL, nameHeight INTEGER NOT NULL,
            speciesId TEXT DEFAULT NULL, catalogSpeciesId TEXT DEFAULT NULL,
            FOREIGN KEY(cardId) REFERENCES Card(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(spriteId) REFERENCES Sprite(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""INSERT INTO CardCharacter44(id,cardId,spriteId,charaIndex,stage,attribute,baseHp,baseBp,baseAp,nameSprite,nameWidth,nameHeight)
            SELECT id,cardId,spriteId,charaIndex,stage,attribute,baseHp,baseBp,baseAp,nameSprite,nameWidth,nameHeight FROM CardCharacter""")
        db.execSQL("DROP TABLE CardCharacter")
        db.execSQL("ALTER TABLE CardCharacter44 RENAME TO CardCharacter")
        db.execSQL("CREATE INDEX index_CardCharacter_cardId ON CardCharacter(cardId)")
        db.execSQL("CREATE INDEX index_CardCharacter_spriteId ON CardCharacter(spriteId)")
        db.execSQL("CREATE INDEX index_CardCharacter_speciesId ON CardCharacter(speciesId)")
        db.execSQL("CREATE UNIQUE INDEX index_CardCharacter_catalogSpeciesId ON CardCharacter(catalogSpeciesId)")
        db.execSQL("ALTER TABLE UserCharacter ADD COLUMN exportCardCharacterId INTEGER DEFAULT NULL")
        db.execSQL("CREATE TABLE AppSourceState (id INTEGER NOT NULL PRIMARY KEY, mode TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE SpeciesDefaultSprite (cardCharacterId INTEGER NOT NULL PRIMARY KEY, spriteId INTEGER NOT NULL,
            FOREIGN KEY(cardCharacterId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(spriteId) REFERENCES Sprite(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX index_SpeciesDefaultSprite_spriteId ON SpeciesDefaultSprite(spriteId)")
        db.execSQL("""CREATE TABLE AppPartnerProgress (individualId TEXT NOT NULL PRIMARY KEY,
            trainingHp INTEGER NOT NULL, trainingAp INTEGER NOT NULL, trainingBp INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE FirstPartnerEncounter (id INTEGER NOT NULL PRIMARY KEY,
            individualId TEXT NOT NULL, speciesId TEXT NOT NULL, step INTEGER NOT NULL, completedAt INTEGER)""")
        // Room compares the stored CREATE VIEW SQL, including identifier quoting.
        db.execSQL("CREATE VIEW `DisplaySprite` AS ${SpeciesCustomizationSchema.DISPLAY_SPRITE_QUERY}")
        check(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }) { "Migration would leave invalid source references" }
    }
}
