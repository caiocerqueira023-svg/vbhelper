package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 41 -> 42: attach a source-independent species reference and origin to each
 * permanent individual. Additive only: no existing column is altered or dropped, and
 * all partners, chats, relationships and history are preserved.
 *
 * The reference is a species *name snapshot*, not a card slot: a real canonical id is
 * resolved at runtime through the bundled catalog, which also handles regional aliases
 * and never collapses forms. `originCardCharacterId` is a soft, nullable reference, so
 * deleting a card can never cascade into a partner's permanent identity.
 */
object SpeciesBindingSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "DigimonIndividual", "speciesName")) {
            db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN speciesName TEXT DEFAULT NULL")
        }
        if (!hasColumn(db, "DigimonIndividual", "originKind")) {
            db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN originKind TEXT NOT NULL DEFAULT 'CARD'")
        }
        if (!hasColumn(db, "DigimonIndividual", "originCardCharacterId")) {
            db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN originCardCharacterId INTEGER DEFAULT NULL")
        }
        backfill(db)
    }

    /** Existing partners predate the reference; derive it from their current card slot. */
    private fun backfill(db: SupportSQLiteDatabase) {
        db.execSQL("""
            UPDATE DigimonIndividual
            SET speciesName = (
                SELECT COALESCE(NULLIF(sp.speciesName, ''), NULLIF(sp.matchedName, ''))
                FROM UserCharacter uc
                JOIN SpeciesProfile sp ON sp.cardCharacterId = uc.charId
                WHERE uc.individualId = DigimonIndividual.individualId
                LIMIT 1
            )
            WHERE speciesName IS NULL
              AND EXISTS (
                SELECT 1 FROM UserCharacter uc
                JOIN SpeciesProfile sp ON sp.cardCharacterId = uc.charId
                WHERE uc.individualId = DigimonIndividual.individualId
                  AND COALESCE(NULLIF(sp.speciesName, ''), NULLIF(sp.matchedName, '')) IS NOT NULL)
        """.trimIndent())
        db.execSQL("""
            UPDATE DigimonIndividual
            SET originCardCharacterId = (
                SELECT uc.charId FROM UserCharacter uc
                WHERE uc.individualId = DigimonIndividual.individualId
                LIMIT 1
            )
            WHERE originCardCharacterId IS NULL
        """.trimIndent())
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }
}
