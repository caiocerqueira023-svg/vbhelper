package com.github.nacabaro.vbhelper.source

import androidx.room.withTransaction
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.device_data.IndividualOrigin
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.dtos.DebugSpawnCharacter
import com.github.nacabaro.vbhelper.species.AppPartnerFactory
import com.github.nacabaro.vbhelper.utils.DeviceType

/** Debug-only direct-to-Storage creation from loaded cards or app definitions. */
class DebugStorageRepository(
    private val db: AppDatabase,
    private val debugEnabled: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun candidates(): List<DebugSpawnCharacter> {
        if (!debugEnabled()) return emptyList()
        db.expandedCatalog?.ensureReadyDefinitions()
        return (db.characterDao().getDebugSpawnCharacters() + db.characterDao().getDebugCatalogCharacters())
            .distinctBy { it.id }.takeIf { debugEnabled() }.orEmpty()
    }

    /** Returns the new Storage row id without changing the selected artwork preference. */
    suspend fun addToStorage(cardCharacterId: Long): Long {
        check(debugEnabled()) { "Debug access was revoked" }
        return db.withTransaction {
            check(debugEnabled()) { "Debug access was revoked" }
            val selected = db.characterDao().getById(cardCharacterId) ?: error("Digimon not found")
            if (selected.cardId == null) {
                val individualId = IndividualIdentity.generate()
                AppPartnerFactory.createLocked(db, selected, individualId, clock(),
                    origin = IndividualOrigin.EXPANDED)
            } else {
                val character = db.characterDao().getById(cardCharacterId) ?: error("Digimon not found")
                val slotCardId = requireNotNull(character.cardId)
                val card = db.cardDao().getCardById(slotCardId) ?: error("Card not found")
                db.participation?.snapshot()?.requireCard(character.id)
                val now = clock()
                val individualId = IndividualIdentity.generate()
                val profile = db.speciesProfileDao().getByCardCharacterId(character.id)
                val speciesName = profile?.speciesName?.takeIf { it.isNotBlank() }
                    ?: profile?.matchedName?.takeIf { it.isNotBlank() }
                db.digimonIndividualDao().insert(DigimonIndividual(individualId, now,
                    speciesName = speciesName, originKind = IndividualOrigin.CARD,
                    originCardCharacterId = character.id))
                db.digimonIndividualDao().upsertPersonality(
                    DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now))
                val rowId = db.userCharacterDao().insertCharacterData(UserCharacter(
                    individualId = individualId, charId = character.id, ageInDays = 0, mood = 80,
                    vitalPoints = 0, transformationCountdown = if (card.isBEm) 0 else 1,
                    injuryStatus = NfcCharacter.InjuryStatus.None, trophies = 0,
                    currentPhaseBattlesWon = 0, currentPhaseBattlesLost = 0,
                    totalBattlesWon = 0, totalBattlesLost = 0, activityLevel = 0, heartRateCurrent = 0,
                    characterType = if (card.isBEm) DeviceType.BEDevice else DeviceType.VBDevice,
                    isActive = false))
                if (card.isBEm) {
                    db.userCharacterDao().insertBECharacterData(BECharacterData(
                        id = rowId, trainingHp = 0, trainingAp = 0, trainingBp = 0,
                        remainingTrainingTimeInMinutes = 6000, itemEffectMentalStateValue = 0,
                        itemEffectMentalStateMinutesRemaining = 0, itemEffectActivityLevelValue = 0,
                        itemEffectActivityLevelMinutesRemaining = 0, itemEffectVitalPointsChangeValue = 0,
                        itemEffectVitalPointsChangeMinutesRemaining = 0, abilityRarity = NfcCharacter.AbilityRarity.None,
                        abilityType = 0, abilityBranch = 0, abilityReset = 0, rank = 0,
                        itemType = 0, itemMultiplier = 0, itemRemainingTime = 0, otp0 = "", otp1 = "", minorVersion = 0, majorVersion = 0))
                } else {
                    db.userCharacterDao().insertVBCharacterData(VBCharacterData(id = rowId, generation = 0, totalTrophies = 0))
                    db.userCharacterDao().insertSpecialMissions(*(0 until 4).map { slot -> SpecialMissions(
                        characterId = rowId, goal = 0,
                        watchId = ((rowId * 4 + slot) % 65535L).toInt().coerceAtLeast(1),
                        progress = 0, status = SpecialMission.Status.UNAVAILABLE, timeElapsedInMinutes = 0,
                        timeLimitInMinutes = 0, missionType = SpecialMission.Type.NONE)
                    }.toTypedArray())
                }
                db.userCharacterDao().insertTransformationHistory(TransformationHistory(
                    monId = rowId, stageId = character.id, transformationDate = now))
                db.dexDao().insertCharacter(character.charaIndex, slotCardId, now)
                rowId
            }
        }
    }
}
