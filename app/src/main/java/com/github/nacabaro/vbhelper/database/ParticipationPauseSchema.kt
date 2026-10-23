package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object ParticipationPauseSchema {
    const val CREATE = """CREATE TABLE IF NOT EXISTS ParticipationPause (
        individualId TEXT NOT NULL PRIMARY KEY, pausedAt INTEGER NOT NULL,
        FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)"""

    fun migrate(db: SupportSQLiteDatabase) { db.execSQL(CREATE) }
}
