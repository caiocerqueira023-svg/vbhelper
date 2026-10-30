package com.github.nacabaro.vbhelper.screens.scanScreen.converters

import android.util.Log
import androidx.activity.ComponentActivity
import com.github.cfogrady.vbnfc.be.BENfcCharacter
import com.github.cfogrady.vbnfc.be.FirmwareVersion
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.device_data.NfcEvolutionHistory
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.hasPhysicalSlot
import com.github.nacabaro.vbhelper.domain.device_data.isCatalogPartner
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository

class ToNfcConverter(
    private val database: AppDatabase,
) {
    constructor(componentActivity: ComponentActivity) : this((componentActivity.applicationContext as VBHelper).container.db)



    suspend fun characterToNfc(
        characterId: Long
    ): NfcCharacter = database.withTransaction {
        database.participation?.snapshot()?.requireOwned(characterId)
        EvolutionHistoryRepository(database).repairCharacter(characterId)

        val userCharacter = database
            .userCharacterDao()
            .getCharacter(characterId)
        val sourceCharacter = database.characterDao().getById(userCharacter.charId)
        // Catalog-defined partners have no physical slot of their own; the outgoing
        // payload uses the exact converted DIM/BEM slot recorded by conversion.
        val exportTarget = if (sourceCharacter == null || sourceCharacter.isCatalogPartner) {
            val targetId = requireNotNull(userCharacter.exportCardCharacterId) {
                "Convert this partner to a loaded DIM/BEM slot before sending it to a watch."
            }
            val target = requireNotNull(database.characterDao().getById(targetId)) {
                "Converted slot is no longer available."
            }
            require(target.hasPhysicalSlot && target.catalogSpeciesId == null) {
                "The converted slot must be an exact loaded DIM/BEM entry."
            }
            require(exportSpeciesMatches(sourceCharacter, target)) {
                "The converted slot must be the exact same species and form."
            }
            target
        } else null
        val effectiveCharacter = exportTarget ?: requireNotNull(sourceCharacter)
        val effectiveCard = effectiveCharacter.cardId?.let { database.cardDao().getCardById(it) }
        val characterInfo = if (exportTarget != null && effectiveCard != null) {
            val progress = database.cardProgressDao().getCardProgressSync(effectiveCard.id) ?: 0
            CharacterDtos.CardCharacterInfo(
                cardId = effectiveCard.cardId.toLong(),
                charId = effectiveCharacter.charaIndex,
                stage = effectiveCharacter.stage,
                attribute = effectiveCharacter.attribute,
                currentStage = progress
            )
        } else {
            database.characterDao().getCharacterInfo(userCharacter.charId)
        }

        if (userCharacter.characterType == DeviceType.BEDevice ||
            userCharacter.characterType == DeviceType.APP && effectiveCard?.isBEm == true)
            nfcToBENfc(characterId, characterInfo, userCharacter, exportTarget)
        else
            nfcToVBNfc(characterId, characterInfo, userCharacter, exportTarget)
    }

    private suspend fun exportSpeciesMatches(
        source: com.github.nacabaro.vbhelper.domain.card.CardCharacter?,
        target: com.github.nacabaro.vbhelper.domain.card.CardCharacter
    ): Boolean {
        if (source?.speciesId != null) {
            if (target.speciesId != null) return target.speciesId == source.speciesId
        }
        suspend fun profileName(id: Long) = database.speciesProfileDao().getByCardCharacterId(id)
            ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName }
        val left = source?.let { profileName(it.id) } ?: return false
        val right = profileName(target.id) ?: return false
        return com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity.normalize(left) ==
            com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity.normalize(right)
    }



    private suspend fun nfcToVBNfc(
        characterId: Long,
        characterInfo: CharacterDtos.CardCharacterInfo,
        userCharacter: UserCharacter,
        exportTarget: com.github.nacabaro.vbhelper.domain.card.CardCharacter? = null
    ): VBNfcCharacter {
        val vbData = database
            .userCharacterDao()
            .getVbDataOrNull(characterId)
            ?: com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData(characterId, generation = 0, totalTrophies = 0)

        val paddedTransformationArray = generateTransformationHistory(characterId, 9, exportTarget)

        val watchSpecialMissions = generateSpecialMissionsArray(characterId)

        val nfcData = VBNfcCharacter(
            dimId = requireNotNull(characterInfo.cardId) { "Converted export requires a loaded card." }.toUShort(),
            charIndex = characterInfo.charId.toUShort(),
            stage = characterInfo.stage.toByte(),
            attribute = characterInfo.attribute,
            ageInDays = userCharacter.ageInDays.toByte(),
            nextAdventureMissionStage = characterInfo.currentStage.toByte(),
            mood = userCharacter.mood.toByte(),
            vitalPoints = userCharacter.vitalPoints.toUShort(),
            transformationCountdownInMinutes = (if (exportTarget != null)
                userCharacter.transformationCountdown.coerceAtLeast(1) else userCharacter.transformationCountdown).toUShort(),
            injuryStatus = userCharacter.injuryStatus,
            trophies = userCharacter.trophies.toUShort(),
            currentPhaseBattlesWon = userCharacter.currentPhaseBattlesWon.toUShort(),
            currentPhaseBattlesLost = userCharacter.currentPhaseBattlesLost.toUShort(),
            totalBattlesWon = userCharacter.totalBattlesWon.toUShort(),
            totalBattlesLost = userCharacter.totalBattlesLost.toUShort(),
            activityLevel = userCharacter.activityLevel.toByte(),
            heartRateCurrent = userCharacter.heartRateCurrent.toUByte(),
            transformationHistory = paddedTransformationArray,
            vitalHistory = generateVitalsHistoryArray(characterId),
            // ScanScreenController attaches a one-time transfer token before sending.
            appReserved2 = generateUShortAppReserved(userCharacter, exportTarget),
            generation = vbData.generation.toUShort(),
            totalTrophies = vbData.totalTrophies.toUShort(),
            specialMissions = watchSpecialMissions.toTypedArray()
        )

        return nfcData
    }


    private suspend fun generateUShortAppReserved(
        userCharacter: UserCharacter,
        exportTarget: com.github.nacabaro.vbhelper.domain.card.CardCharacter? = null
    ): Array<UShort> {
        val cardData = if (exportTarget != null) {
            database.cardDao().getCardById(requireNotNull(exportTarget.cardId))
        } else {
            database
                .cardDao()
                .getCardByCharacterId(userCharacter.id)
                .first()
        } ?: error("Card not found for export")

        val appReserved = Array<UShort>(3) {
            0u
        }

        appReserved[0] = cardData.id.toUShort()

        return appReserved
    }



    private suspend fun generateSpecialMissionsArray(
        characterId: Long
    ): List<SpecialMission> {
        val specialMissions = database
            .userCharacterDao()
            .getSpecialMissions(characterId)
            .first()

        val watchSpecialMissions = specialMissions.map {
            SpecialMission(
                goal = it.goal.toUShort(),
                id = it.watchId.toUShort(),
                progress = it.progress.toUShort(),
                status = it.status,
                timeElapsedInMinutes = it.timeElapsedInMinutes.toUShort(),
                timeLimitInMinutes = it.timeLimitInMinutes.toUShort(),
                type = it.missionType
            )
        }

        return watchSpecialMissions
    }



    private suspend fun generateVitalsHistoryArray(
        characterId: Long
    ): Array<NfcCharacter.DailyVitals> {
        val vitalsHistory = database
            .userCharacterDao()
            .getVitalsHistory(characterId)

        val nfcVitalsHistory = Array(7) {
            NfcCharacter.DailyVitals(0u, 0u, 0u, 0u)
        }

        vitalsHistory.mapIndexed { index, historyElement ->
            var actualYear = 0
            if (historyElement.year != 2000) {
                actualYear = historyElement.year
            }
             nfcVitalsHistory[index] = NfcCharacter.DailyVitals(
                day = historyElement.day.toUByte(),
                month = historyElement.month.toUByte(),
                year = actualYear.toUShort(),
                vitalsGained = vitalsHistory[index].vitalPoints.toUShort()
            )
        }

        nfcVitalsHistory.map {
            Log.d("NFC", it.toString())
        }

        return nfcVitalsHistory
    }



    private suspend fun nfcToBENfc(
        characterId: Long,
        characterInfo: CharacterDtos.CardCharacterInfo,
        userCharacter: UserCharacter,
        exportTarget: com.github.nacabaro.vbhelper.domain.card.CardCharacter? = null
    ): BENfcCharacter {
        // A DIM association suppresses APP/cross-product bonuses. Their permanent
        // progress stays in AppPartnerProgress; only native BE training belongs here.
        val beData = (if (userCharacter.characterType != DeviceType.APP)
            database.userCharacterDao().getBeDataOrNull(characterId) else null)
            ?: com.github.nacabaro.vbhelper.domain.device_data.BECharacterData(
                id = characterId, trainingHp = 0, trainingAp = 0,
                trainingBp = 0, remainingTrainingTimeInMinutes = 0,
                itemEffectMentalStateValue = 0, itemEffectMentalStateMinutesRemaining = 0,
                itemEffectActivityLevelValue = 0, itemEffectActivityLevelMinutesRemaining = 0,
                itemEffectVitalPointsChangeValue = 0, itemEffectVitalPointsChangeMinutesRemaining = 0,
                abilityRarity = NfcCharacter.AbilityRarity.None, abilityType = 0, abilityBranch = 0,
                abilityReset = 0, rank = 0, itemType = 0, itemMultiplier = 0, itemRemainingTime = 0,
                otp0 = "", otp1 = "", minorVersion = 0, majorVersion = 0)

        val paddedTransformationArray = generateTransformationHistory(characterId, 8, exportTarget)

        val nfcData = BENfcCharacter(
            dimId = requireNotNull(characterInfo.cardId) { "Converted export requires a loaded card." }.toUShort(),
            charIndex = characterInfo.charId.toUShort(),
            stage = characterInfo.stage.toByte(),
            attribute = characterInfo.attribute,
            ageInDays = userCharacter.ageInDays.toByte(),
            nextAdventureMissionStage = characterInfo.currentStage.toByte(),
            mood = userCharacter.mood.toByte(),
            vitalPoints = userCharacter.vitalPoints.toUShort(),
            itemEffectMentalStateValue = beData.itemEffectMentalStateValue.toByte(),
            itemEffectMentalStateMinutesRemaining = beData.itemEffectMentalStateMinutesRemaining.toByte(),
            itemEffectActivityLevelValue = beData.itemEffectActivityLevelValue.toByte(),
            itemEffectActivityLevelMinutesRemaining = beData.itemEffectActivityLevelMinutesRemaining.toByte(),
            itemEffectVitalPointsChangeValue = beData.itemEffectVitalPointsChangeValue.toByte(),
            itemEffectVitalPointsChangeMinutesRemaining = beData.itemEffectVitalPointsChangeMinutesRemaining.toByte(),
            transformationCountdownInMinutes = userCharacter.transformationCountdown.toUShort(),
            injuryStatus = userCharacter.injuryStatus,
            trainingPp = userCharacter.trophies.toUShort(),
            currentPhaseBattlesWon = userCharacter.currentPhaseBattlesWon.toUShort(),
            currentPhaseBattlesLost = userCharacter.currentPhaseBattlesLost.toUShort(),
            totalBattlesWon = userCharacter.totalBattlesWon.toUShort(),
            totalBattlesLost = userCharacter.totalBattlesLost.toUShort(),
            activityLevel = userCharacter.activityLevel.toByte(),
            heartRateCurrent = userCharacter.heartRateCurrent.toUByte(),
            transformationHistory = paddedTransformationArray,
            vitalHistory = generateVitalsHistoryArray(characterId),
            // ScanScreenController attaches a one-time transfer token before sending.
            appReserved2 = Array(3) {0u},
            trainingHp = beData.trainingHp.toUShort(),
            trainingAp = beData.trainingAp.toUShort(),
            trainingBp = beData.trainingBp.toUShort(),
            remainingTrainingTimeInMinutes = beData.remainingTrainingTimeInMinutes.toUShort(),
            abilityRarity = beData.abilityRarity,
            abilityType = beData.abilityType.toUShort(),
            abilityBranch = beData.abilityBranch.toUShort(),
            abilityReset = beData.abilityReset.toByte(),
            rank = beData.rank.toByte(),
            itemType = beData.itemType.toByte(),
            itemMultiplier = beData.itemMultiplier.toByte(),
            itemRemainingTime = beData.itemRemainingTime.toByte(),
            otp0 = byteArrayOf(8),
            otp1 = byteArrayOf(8),
            characterCreationFirmwareVersion = FirmwareVersion(
                minorVersion = beData.minorVersion.toByte(),
                majorVersion = beData.majorVersion.toByte()
            )
        )

        return nfcData
    }



    private suspend fun generateTransformationHistory(
        characterId: Long,
        length: Int = 8,
        exportTarget: com.github.nacabaro.vbhelper.domain.card.CardCharacter? = null
    ): Array<NfcCharacter.Transformation> {
        // A converted export carries the target slot as its watch lineage; the app
        // keeps the full catalog history separately and never rewrites it.
        val history = if (exportTarget != null) {
            listOf(NfcEvolutionHistory.transformation(exportTarget.charaIndex, System.currentTimeMillis()))
        } else {
            database.userCharacterDao().getTransformationHistory(characterId).first()
                .map { NfcEvolutionHistory.transformation(it.monIndex, it.transformationDate) }
        }
        return NfcEvolutionHistory.pad(history, length)
    }
}
