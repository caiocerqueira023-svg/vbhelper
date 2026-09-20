package com.github.nacabaro.vbhelper.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Database-level protection also applies to background jobs and future importers. */
object IndividualIntegrity {
    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            install(db)
        }
    }

    fun install(db: SupportSQLiteDatabase) {
        // Repair missing permanent rows without changing any existing identity or chat.
        db.execSQL("""
            INSERT OR IGNORE INTO DigimonIndividual(individualId, createdAt, lastCelebratedWinsMilestone, lastCelebratedTrophyMilestone)
            SELECT individualId, 0, 0, 0 FROM UserCharacter WHERE length(trim(individualId)) > 0
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_user_individual_insert BEFORE INSERT ON UserCharacter
            BEGIN
                SELECT RAISE(ABORT, 'Individual identity cannot be empty') WHERE length(trim(NEW.individualId)) = 0;
                SELECT RAISE(ABORT, 'Individual is already in storage') WHERE EXISTS(
                    SELECT 1 FROM UserCharacter WHERE individualId = NEW.individualId AND id != NEW.id);
                SELECT RAISE(ABORT, 'Individual is still in World') WHERE EXISTS(
                    SELECT 1 FROM WorldSpawn WHERE individualId = NEW.individualId);
                INSERT INTO DigimonIndividual(individualId, createdAt, lastCelebratedWinsMilestone, lastCelebratedTrophyMilestone)
                    SELECT NEW.individualId, CAST(strftime('%s', 'now') AS INTEGER) * 1000, 0, 0
                    WHERE NOT EXISTS(SELECT 1 FROM DigimonIndividual WHERE individualId = NEW.individualId);
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_user_individual_update BEFORE UPDATE OF individualId ON UserCharacter
            WHEN NEW.individualId != OLD.individualId
            BEGIN SELECT RAISE(ABORT, 'An existing Digimon cannot change individual identity'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_world_individual_insert BEFORE INSERT ON WorldSpawn
            BEGIN
                SELECT RAISE(ABORT, 'Individual identity cannot be empty') WHERE length(trim(NEW.individualId)) = 0;
                SELECT RAISE(ABORT, 'Individual is already in storage') WHERE EXISTS(
                    SELECT 1 FROM UserCharacter WHERE individualId = NEW.individualId);
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_world_individual_update BEFORE UPDATE OF individualId ON WorldSpawn
            WHEN NEW.individualId != OLD.individualId
            BEGIN SELECT RAISE(ABORT, 'An existing World Digimon cannot change individual identity'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS remove_farm_resident_before_storage_delete
            BEFORE DELETE ON UserCharacter
            BEGIN
                DELETE FROM FarmResident WHERE individualId = OLD.individualId;
            END
        """.trimIndent())
    }
}
