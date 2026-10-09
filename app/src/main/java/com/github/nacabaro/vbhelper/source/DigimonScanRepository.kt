package com.github.nacabaro.vbhelper.source

import androidx.room.withTransaction
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.*
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.domain.scan.DigimonScanProgress
import com.github.nacabaro.vbhelper.domain.scan.DigimonScanReward
import com.github.nacabaro.vbhelper.utils.DeviceType

sealed interface ScanConversionResult {
    data class Converted(val characterId: Long) : ScanConversionResult
    data object NotReady : ScanConversionResult
    data object Unavailable : ScanConversionResult
}

class DigimonScanRepository(private val db: AppDatabase, private val now: () -> Long = System::currentTimeMillis) {
    /** Called only inside the battle-result transaction, before enemies are removed. */
    internal suspend fun awardBattleLocked(interactionId: String, awards: Map<Long, Int>, timestamp: Long) {
        val dao = db.digimonScanDao()
        for ((cardCharacterId, gain) in awards) {
            val before = dao.getProgress(cardCharacterId) ?: 0
            val after = DigimonScanPolicy.afterReward(before, gain)
            dao.saveProgress(DigimonScanProgress(cardCharacterId, after, timestamp))
            dao.insertReward(DigimonScanReward(interactionId, cardCharacterId, before, after))
        }
    }

    suspend fun convert(cardCharacterId: Long, nickname: String? = null): ScanConversionResult = db.withTransaction {
        val species = db.characterDao().getById(cardCharacterId) ?: return@withTransaction ScanConversionResult.Unavailable
        val card = db.cardDao().getCardById(species.cardId) ?: return@withTransaction ScanConversionResult.Unavailable
        val timestamp = now()
        if (db.digimonScanDao().consumeCompleteScan(cardCharacterId, timestamp) != 1) return@withTransaction ScanConversionResult.NotReady

        val individualId = IndividualIdentity.generate()
        db.digimonIndividualDao().insert(DigimonIndividual(individualId, timestamp, nickname?.trim()?.takeIf { it.isNotEmpty() }))
        db.digimonIndividualDao().upsertPersonality(DigimonPersonalityGenerator.generate(individualId, species.attribute, species.stage, timestamp))
        val characterId = db.userCharacterDao().insertCharacterData(UserCharacter(
            individualId = individualId, charId = cardCharacterId, ageInDays = 0, mood = 80,
            // VB watch exports require a positive timer; BE supports an exhausted zero timer.
            vitalPoints = 0, transformationCountdown = if (card.isBEm) 0 else 1, injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0, currentPhaseBattlesWon = 0, currentPhaseBattlesLost = 0,
            totalBattlesWon = 0, totalBattlesLost = 0, activityLevel = 0, heartRateCurrent = 0,
            characterType = if (card.isBEm) DeviceType.BEDevice else DeviceType.VBDevice, isActive = false))
        if (card.isBEm) {
            db.userCharacterDao().insertBECharacterData(BECharacterData(
                id = characterId, trainingHp = 0, trainingAp = 0, trainingBp = 0,
                remainingTrainingTimeInMinutes = 6000, itemEffectMentalStateValue = 0,
                itemEffectMentalStateMinutesRemaining = 0, itemEffectActivityLevelValue = 0,
                itemEffectActivityLevelMinutesRemaining = 0, itemEffectVitalPointsChangeValue = 0,
                itemEffectVitalPointsChangeMinutesRemaining = 0, abilityRarity = NfcCharacter.AbilityRarity.None,
                abilityType = 0, abilityBranch = 0, abilityReset = 0, rank = 0,
                itemType = 0, itemMultiplier = 0, itemRemainingTime = 0, otp0 = "", otp1 = "", minorVersion = 0, majorVersion = 0))
        } else {
            db.userCharacterDao().insertVBCharacterData(VBCharacterData(characterId, generation = 0, totalTrophies = 0))
            db.userCharacterDao().insertSpecialMissions(*(0 until 4).map { slot -> SpecialMissions(
                characterId = characterId, goal = 0, watchId = ((characterId * 4 + slot) % 65535L).toInt().coerceAtLeast(1),
                progress = 0, status = SpecialMission.Status.UNAVAILABLE, timeElapsedInMinutes = 0,
                timeLimitInMinutes = 0, missionType = SpecialMission.Type.NONE)
            }.toTypedArray())
        }
        db.userCharacterDao().insertTransformationHistory(TransformationHistory(
            monId = characterId, stageId = cardCharacterId, transformationDate = timestamp))
        db.dexDao().insertCharacter(species.charaIndex, species.cardId, timestamp)
        EvolutionHistoryRepository(db).repairCharacter(characterId)
        ScanConversionResult.Converted(characterId)
    }
}
