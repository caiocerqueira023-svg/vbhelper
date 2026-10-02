package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object WorldDialogueSchema {
    fun create(db: SupportSQLiteDatabase) {
        if (!hasColumn(db,"deadlineTick")) db.execSQL("ALTER TABLE WorldInteraction ADD COLUMN deadlineTick INTEGER")
        if (!hasColumn(db,"dialogueSequence")) db.execSQL("ALTER TABLE WorldInteraction ADD COLUMN dialogueSequence INTEGER NOT NULL DEFAULT 0")
        if (!hasColumn(db,"publicReason")) db.execSQL("ALTER TABLE WorldInteraction ADD COLUMN publicReason TEXT")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldInteractionMessage (
                id TEXT NOT NULL PRIMARY KEY,interactionId TEXT NOT NULL,sequence INTEGER NOT NULL,speakerId TEXT NOT NULL,
                speakerName TEXT NOT NULL,body TEXT NOT NULL,source TEXT NOT NULL,requestId TEXT NOT NULL,tick INTEGER NOT NULL,createdAt INTEGER NOT NULL,
                FOREIGN KEY(interactionId) REFERENCES WorldInteraction(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_WorldInteractionMessage_interactionId_sequence ON WorldInteractionMessage(interactionId,sequence)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_WorldInteractionMessage_requestId_speakerId ON WorldInteractionMessage(requestId,speakerId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldDialogueIntent (
                id TEXT NOT NULL PRIMARY KEY,interactionId TEXT NOT NULL,sourceRevision INTEGER NOT NULL,initiatorId TEXT NOT NULL,
                targetIdsJson TEXT NOT NULL,evidenceIdsJson TEXT NOT NULL,type TEXT NOT NULL,status TEXT NOT NULL,reason TEXT NOT NULL,
                sparring INTEGER NOT NULL,expiresAtTick INTEGER NOT NULL,linkedBattleId TEXT,
                FOREIGN KEY(interactionId) REFERENCES WorldInteraction(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldDialogueIntent_interactionId ON WorldDialogueIntent(interactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldDialogueIntent_status_expiresAtTick ON WorldDialogueIntent(status,expiresAtTick)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldNpcBattle (
                interactionId TEXT NOT NULL PRIMARY KEY,definitionsJson TEXT NOT NULL,elapsedMillis INTEGER NOT NULL,
                startTick INTEGER NOT NULL,nextRoundTick INTEGER NOT NULL,snapshotJson TEXT NOT NULL,
            FOREIGN KEY(interactionId) REFERENCES WorldInteraction(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldNpcBattle_interactionId ON WorldNpcBattle(interactionId)")
    }
    private fun hasColumn(db:SupportSQLiteDatabase,name:String):Boolean = db.query("PRAGMA table_info(WorldInteraction)").use { cursor ->
        while(cursor.moveToNext()) { if(cursor.getString(1)==name)return@use true };false
    }
}
