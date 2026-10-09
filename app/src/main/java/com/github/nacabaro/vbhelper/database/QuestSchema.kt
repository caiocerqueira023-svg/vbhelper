package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object QuestSchema {
    fun addWatchCapabilities(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN availabilityIssue TEXT")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN watchCardId INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN watchCardName TEXT")
        db.execSQL("ALTER TABLE QuestWatchBaseline ADD COLUMN family TEXT")
        db.execSQL("ALTER TABLE QuestWatchBaseline ADD COLUMN cardId INTEGER")
        db.execSQL("ALTER TABLE QuestWatchBaseline ADD COLUMN adventureNext INTEGER")
        db.execSQL("ALTER TABLE QuestWatchBaseline ADD COLUMN adventureLimit INTEGER")
    }

    fun addFollowUps(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN parentQuestId TEXT")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN chainDepth INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN followUpTemplateId TEXT")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN followUpQuestId TEXT")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN followUpBlock TEXT")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN followUpClosed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN partnerDeviceType TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_QuestInstance_parentQuestId ON QuestInstance(parentQuestId)")
    }

    fun addBattleConditions(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN techniqueId TEXT")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN techniqueName TEXT")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN maxBattleMillis INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN maxBattleItems INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN minHealthPercent INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN minTechniqueHits INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN alliedTeamSize INTEGER")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN opposingTeamSize INTEGER")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestBattleReport (interactionId TEXT NOT NULL PRIMARY KEY,
            outcome TEXT NOT NULL, elapsedMillis INTEGER NOT NULL, itemsUsed INTEGER NOT NULL,
            alliedTeamSize INTEGER NOT NULL, opposingTeamSize INTEGER NOT NULL, rulesetVersion INTEGER NOT NULL,
            recordedAt INTEGER NOT NULL)""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestBattleMember (interactionId TEXT NOT NULL,
            individualId TEXT NOT NULL, allied INTEGER NOT NULL, health INTEGER NOT NULL, maxHealth INTEGER NOT NULL,
            PRIMARY KEY(interactionId,individualId))""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestBattleTechniqueHit (interactionId TEXT NOT NULL,
            actorId TEXT NOT NULL, targetId TEXT NOT NULL, techniqueId TEXT NOT NULL, hits INTEGER NOT NULL,
            PRIMARY KEY(interactionId,actorId,targetId,techniqueId))""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestBattleAttempt (questId TEXT NOT NULL,
            objectiveId TEXT NOT NULL, interactionId TEXT NOT NULL, failure TEXT, recordedAt INTEGER NOT NULL,
            PRIMARY KEY(questId,objectiveId,interactionId))""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestBattleAttempt_questId ON QuestBattleAttempt(questId)")
    }

    fun addStorySteps(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN currentPhase INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN phaseStartedAt INTEGER")
        db.execSQL("ALTER TABLE QuestInstance ADD COLUMN partnerName TEXT")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN phase INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN tokenId TEXT")
        db.execSQL("ALTER TABLE QuestObjective ADD COLUMN producesTokenId TEXT")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestToken (
            id TEXT NOT NULL PRIMARY KEY, questId TEXT NOT NULL, kind TEXT NOT NULL, state TEXT NOT NULL,
            acquiredAt INTEGER, deliveredAt INTEGER,
            FOREIGN KEY(questId) REFERENCES QuestInstance(id) ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestToken_questId ON QuestToken(questId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestPhaseReceipt (
            questId TEXT NOT NULL, sourceId TEXT NOT NULL, phase INTEGER NOT NULL, recordedAt INTEGER NOT NULL,
            PRIMARY KEY(questId,sourceId))""".trimIndent())
    }

    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestInstance (
            id TEXT NOT NULL PRIMARY KEY, giverId TEXT NOT NULL, giverCardCharacterId INTEGER NOT NULL,
            giverName TEXT NOT NULL, category TEXT NOT NULL, templateId TEXT NOT NULL, templateVersion INTEGER NOT NULL,
            seed INTEGER NOT NULL, stage INTEGER NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL,
            partnerId TEXT, rewardBits INTEGER NOT NULL, rewardTrust INTEGER NOT NULL, rewardItemId INTEGER,
            rewardItemName TEXT, rewardItemQuantity INTEGER NOT NULL, rewardBattleItemId TEXT,
            rewardBattleItemQuantity INTEGER NOT NULL, createdAt INTEGER NOT NULL, acceptedAt INTEGER,
            finishedAt INTEGER, lastWatchSyncAt INTEGER, watchNotice TEXT, offerMessageId INTEGER)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestInstance_giverId_category_state ON QuestInstance(giverId,category,state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestInstance_partnerId ON QuestInstance(partnerId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestObjective (
            id TEXT NOT NULL PRIMARY KEY, questId TEXT NOT NULL, position INTEGER NOT NULL, type TEXT NOT NULL,
            required INTEGER NOT NULL, progress INTEGER NOT NULL, itemId INTEGER, itemName TEXT, battleItemId TEXT,
            targetIndividualId TEXT, targetCardCharacterId INTEGER, targetName TEXT,
            FOREIGN KEY(questId) REFERENCES QuestInstance(id) ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestObjective_questId ON QuestObjective(questId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestObjective_targetIndividualId ON QuestObjective(targetIndividualId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestEvidence (
            questId TEXT NOT NULL, objectiveId TEXT NOT NULL, sourceId TEXT NOT NULL, amount INTEGER NOT NULL,
            recordedAt INTEGER NOT NULL, PRIMARY KEY(questId,objectiveId,sourceId))""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestEvidence_sourceId ON QuestEvidence(sourceId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS QuestRewardReceipt (questId TEXT NOT NULL PRIMARY KEY, claimedAt INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestWatchBaseline (
            token TEXT NOT NULL PRIMARY KEY, individualId TEXT NOT NULL, charIndex INTEGER NOT NULL,
            history TEXT NOT NULL, won INTEGER NOT NULL, lost INTEGER NOT NULL, trophies INTEGER NOT NULL,
            lifetimeTrophies INTEGER, capturedAt INTEGER NOT NULL)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_QuestWatchBaseline_individualId ON QuestWatchBaseline(individualId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS QuestWallet (id INTEGER NOT NULL PRIMARY KEY, balance INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS QuestBattleStock (itemId TEXT NOT NULL PRIMARY KEY, quantity INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS QuestBattleReservation (
            interactionId TEXT NOT NULL, itemId TEXT NOT NULL, quantity INTEGER NOT NULL, used INTEGER NOT NULL,
            PRIMARY KEY(interactionId,itemId))""".trimIndent())
    }
}
