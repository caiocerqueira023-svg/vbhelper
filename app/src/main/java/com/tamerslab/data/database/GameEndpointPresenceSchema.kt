package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema 55: verified game-endpoint presence, separate from copies that remain in a save. */
object GameEndpointPresenceSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `GameEndpointPresence` (`individualId` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL, `documentKey` TEXT NOT NULL, `gameId` TEXT NOT NULL,
            `nativeIdentity` TEXT NOT NULL, `operationId` TEXT NOT NULL, `sentAt` INTEGER NOT NULL,
            PRIMARY KEY(`individualId`), FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
            ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_GameEndpointPresence_sourceId` ON `GameEndpointPresence` (`sourceId`)")
    }
}
