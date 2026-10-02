package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Enforce claims even for mutations outside the Radar repository (e.g. card deletion). */
object WorldInteractionIntegrity {
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_world_participation_insert BEFORE INSERT ON WorldParticipationClaim
            BEGIN
                SELECT RAISE(ABORT, 'Invalid interaction participant claim') WHERE NOT EXISTS(
                    SELECT 1 FROM WorldInteractionParticipant p
                    JOIN WorldInteraction e ON e.id = p.interactionId
                    JOIN WorldSpawn s ON s.id = p.spawnId AND s.individualId = p.individualId
                    WHERE p.interactionId = NEW.interactionId AND p.individualId = NEW.individualId
                        AND p.spawnId = NEW.spawnId AND p.role = 'WILD' AND s.recruitmentState = 'WILD'
                        AND e.state NOT IN ('ENDED','CANCELLED','INTERRUPTED'));
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS release_terminal_world_claims AFTER UPDATE OF state ON WorldInteraction
            WHEN NEW.state IN ('ENDED','CANCELLED','INTERRUPTED')
            BEGIN DELETE FROM WorldParticipationClaim WHERE interactionId = NEW.id; END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_claimed_wild_contact_recruitment BEFORE UPDATE OF recruitmentState ON WildRelationship
            WHEN NEW.recruitmentState != OLD.recruitmentState
                 AND EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE individualId = OLD.individualId)
            BEGIN SELECT RAISE(ABORT, 'Wild individual is participating in an interaction'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_claimed_world_spawn_delete BEFORE DELETE ON WorldSpawn
            WHEN EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = OLD.id)
            BEGIN SELECT RAISE(ABORT, 'Wild individual is participating in an interaction'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS guard_claimed_world_recruitment BEFORE UPDATE OF recruitmentState ON WorldSpawn
            WHEN NEW.recruitmentState != OLD.recruitmentState
                 AND EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = OLD.id)
            BEGIN SELECT RAISE(ABORT, 'Wild individual is participating in an interaction'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cancel_interaction_before_species_delete BEFORE DELETE ON CardCharacter
            BEGIN
                UPDATE WorldInteraction SET state = 'CANCELLED', revision = revision + 1,
                    endedAt = CAST(strftime('%s','now') AS INTEGER) * 1000, terminalReason = 'SPECIES_DELETED'
                WHERE id IN (SELECT interactionId FROM WorldInteractionParticipant WHERE cardCharacterId = OLD.id)
                    AND state NOT IN ('ENDED','CANCELLED','INTERRUPTED');
                DELETE FROM WorldParticipationClaim WHERE interactionId IN
                    (SELECT id FROM WorldInteraction WHERE state IN ('ENDED','CANCELLED','INTERRUPTED'));
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cancel_interaction_before_individual_delete BEFORE DELETE ON DigimonIndividual
            BEGIN
                UPDATE WorldInteraction SET state = 'CANCELLED', revision = revision + 1,
                    endedAt = CAST(strftime('%s','now') AS INTEGER) * 1000, terminalReason = 'INDIVIDUAL_DELETED'
                WHERE id IN (SELECT interactionId FROM WorldInteractionParticipant WHERE individualId = OLD.individualId)
                    AND state NOT IN ('ENDED','CANCELLED','INTERRUPTED');
                DELETE FROM WorldParticipationClaim WHERE interactionId IN
                    (SELECT id FROM WorldInteraction WHERE state IN ('ENDED','CANCELLED','INTERRUPTED'));
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cancel_interaction_before_owned_delete BEFORE DELETE ON UserCharacter
            BEGIN
                UPDATE WorldInteraction SET state = 'CANCELLED', revision = revision + 1,
                    endedAt = CAST(strftime('%s','now') AS INTEGER) * 1000, terminalReason = 'OWNED_DELETED'
                WHERE id IN (SELECT interactionId FROM WorldInteractionParticipant WHERE ownedCharacterId = OLD.id)
                    AND state NOT IN ('ENDED','CANCELLED','INTERRUPTED');
                DELETE FROM WorldParticipationClaim WHERE interactionId IN
                    (SELECT id FROM WorldInteraction WHERE state IN ('ENDED','CANCELLED','INTERRUPTED'));
            END
        """.trimIndent())
    }

    /** A new database owner cannot restore the previous process's in-memory player simulator. */
    fun recoverOnOpen(db: SupportSQLiteDatabase) {
        db.execSQL("""
            DELETE FROM WorldParticipationClaim WHERE interactionId IN
                (SELECT id FROM WorldInteraction WHERE origin='JOINED_PLAYER' AND type='BATTLE' AND state='RESERVED')
        """.trimIndent())
        db.execSQL("""
            UPDATE WorldInteraction SET state='ACTIVE', reservationExpiresAt=NULL, reservedFrom=NULL, revision=revision+1
            WHERE origin='AUTONOMOUS' AND state='RESERVED' AND id IN
                (SELECT parentInteractionId FROM WorldInteraction WHERE origin='JOINED_PLAYER' AND type='BATTLE' AND state='RESERVED')
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO WorldParticipationClaim(individualId,interactionId,spawnId)
            SELECT p.individualId,p.interactionId,p.spawnId FROM WorldInteractionParticipant p
            JOIN WorldInteraction e ON e.id=p.interactionId JOIN WorldSpawn s ON s.id=p.spawnId
            WHERE e.origin='AUTONOMOUS' AND e.state='ACTIVE' AND p.role='WILD'
                AND e.id IN (SELECT parentInteractionId FROM WorldInteraction WHERE origin='JOINED_PLAYER' AND type='BATTLE' AND state='RESERVED')
        """.trimIndent())
        db.execSQL("""
            UPDATE WorldInteraction SET
                state = CASE WHEN state IN ('PLAYER_CONTROLLED','RESOLVING') THEN 'INTERRUPTED' ELSE 'CANCELLED' END,
                revision = revision + 1, endedAt = CAST(strftime('%s','now') AS INTEGER) * 1000,
                terminalReason = 'PROCESS_INTERRUPTED', reservationExpiresAt = NULL, reservedFrom = NULL
            WHERE origin IN ('DIRECT_PLAYER','JOINED_PLAYER') AND state NOT IN ('ENDED','CANCELLED','INTERRUPTED')
        """.trimIndent())
    }
}
