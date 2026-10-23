package com.github.nacabaro.vbhelper.species

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.*
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.utils.DeviceType

/** Called in the acquisition producer's Room transaction after its progression proof has been validated. */
object AppPartnerFactory {
    fun createLocked(db: AppDatabase, definition: CardCharacter, individualId: String, now: Long,
        nickname: String? = null, activate: Boolean = false, origin: String = IndividualOrigin.EXPANDED): Long {
        check(db.inTransaction())
        require(definition.cardId == null && definition.speciesId != null)
        db.participation?.snapshot()?.requireCard(definition.id)
        check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty()) { "This individual is already in Storage" }
        db.digimonIndividualDao().insert(DigimonIndividual(individualId, now, nickname = nickname,
            speciesName = definition.speciesId, originKind = origin))
        db.digimonIndividualDao().upsertPersonalitySync(DigimonPersonalityGenerator.generate(individualId, definition.attribute, definition.stage, now))
        return createRepresentationLocked(db, definition, individualId, now, activate)
    }

    /** Materialize an already acquired individual without reinserting identity or regenerating personality. */
    suspend fun materializeImportedLocked(db: AppDatabase, definition: CardCharacter, individualId: String, now: Long): Long {
        check(db.inTransaction())
        require(definition.cardId == null && definition.speciesId != null)
        val gate = requireNotNull(db.participation)
        val eligibility = gate.snapshot()
        eligibility.requireCard(definition.id)
        val individual = requireNotNull(db.digimonIndividualDao().getIndividual(individualId))
        require(individual.originKind == IndividualOrigin.IMPORTED)
        require(individual.speciesName.isNullOrEmpty() || individual.speciesName == definition.speciesId)
        requireNotNull(db.digimonIndividualDao().getPersonality(individualId))
        check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty())
        db.digimonIndividualDao().backfillSpeciesName(individualId, requireNotNull(definition.speciesId))
        val id = createRepresentationLocked(db, definition, individualId, now, false)
        // An archive's first gameplay representation has no running Farm/Adventure clock to resume.
        db.openHelper.writableDatabase.execSQL("DELETE FROM ParticipationPause WHERE individualId = ?", arrayOf<Any?>(individualId))
        return id
    }

    private fun createRepresentationLocked(db: AppDatabase, definition: CardCharacter, individualId: String, now: Long,
        activate: Boolean): Long {
        val id = db.userCharacterDao().insertCharacterData(UserCharacter(individualId = individualId, charId = definition.id,
            ageInDays = 0, mood = 80, vitalPoints = 0, transformationCountdown = 0, injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0, currentPhaseBattlesWon = 0, currentPhaseBattlesLost = 0, totalBattlesWon = 0, totalBattlesLost = 0,
            activityLevel = 0, heartRateCurrent = 0, characterType = DeviceType.APP, isActive = false))
        db.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO AppPartnerProgress(individualId, trainingHp, trainingAp, trainingBp) VALUES (?,0,0,0)", arrayOf<Any?>(individualId))
        db.openHelper.writableDatabase.execSQL("""INSERT INTO SpecialMissions(characterId,goal,watchId,progress,status,timeElapsedInMinutes,timeLimitInMinutes,missionType)
            SELECT ?,0,s.slot,0,'UNAVAILABLE',0,0,'NONE' FROM (SELECT 0 AS slot UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) s
            WHERE NOT EXISTS(SELECT 1 FROM SpecialMissions m WHERE m.characterId=? AND m.watchId=s.slot)""", arrayOf<Any?>(id,id))
        db.userCharacterDao().insertTransformationHistory(TransformationHistory(monId = id, stageId = definition.id, transformationDate = now))
        db.dexDao().recordDefinition(definition.id, now)
        if (activate) db.userCharacterDao().setOnlyActiveCharacter(id)
        return id
    }
}
