package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object WorldSocialSchema {
    fun create(db: SupportSQLiteDatabase) {
        // Retain assigned types and timestamps instead of rerolling legacy individuals on startup.
        db.execSQL("UPDATE DigimonPersonalityTraits SET systemVersion=3 WHERE systemVersion<3")
        db.execSQL("ALTER TABLE WorldInteraction ADD COLUMN socialContextJson TEXT")
        db.execSQL("ALTER TABLE FarmResident ADD COLUMN socialTargetId TEXT")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldSocialMemory (
                observerId TEXT NOT NULL, eventId TEXT NOT NULL, partnerId TEXT NOT NULL, partnerName TEXT NOT NULL,
                motive TEXT NOT NULL, outcome TEXT NOT NULL, summary TEXT NOT NULL, openingKey TEXT NOT NULL,
                tick INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(observerId,eventId),
                FOREIGN KEY(observerId) REFERENCES DigimonIndividual(individualId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldSocialMemory_observerId_partnerId ON WorldSocialMemory(observerId,partnerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldSocialMemory_createdAt ON WorldSocialMemory(createdAt)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS IndividualSocialState (
                individualId TEXT NOT NULL PRIMARY KEY, sessionSeed INTEGER NOT NULL,
                lastPlayerInitiationTick INTEGER NOT NULL, lastPeerInitiationTick INTEGER NOT NULL, lastPartnerId TEXT,
                FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
