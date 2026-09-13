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
        EvolutionHistoryRepository(database).repairCharacter(characterId)

        val userCharacter = database
            .userCharacterDao()
            .getCharacter(characterId)

        val characterInfo = database
            .characterDao()
            .getCharacterInfo(userCharacter.charId)

        if (userCharacter.characterType == DeviceType.BEDevice)
            nfcToBENfc(characterId, characterInfo, userCharacter)
        else
            nfcToVBNfc(characterId, characterInfo, userCharacter)
    }



    private suspend fun nfcToVBNfc(
        characterId: Long,
        characterInfo: CharacterDtos.CardCharacterInfo,
        userCharacter: UserCharacter
    ): VBNfcCharacter {
        val vbData = database
            .userCharacterDao()
            .getVbData(characterId)
            .first()

        val paddedTransformationArray = generateTransformationHistory(characterId, 9)

        val watchSpecialMissions = generateSpecialMissionsArray(characterId)

        val nfcData = VBNfcCharacter(
            dimId = characterInfo.cardId.toUShort(),
            charIndex = characterInfo.charId.toUShort(),
            stage = characterInfo.stage.toByte(),
            attribute = characterInfo.attribute,
            ageInDays = userCharacter.ageInDays.toByte(),
            nextAdventureMissionStage = characterInfo.currentStage.toByte(),
            mood = userCharacter.mood.toByte(),
            vitalPoints = userCharacter.vitalPoints.toUShort(),
            transformationCountdownInMinutes = userCharacter.transformationCountdown.toUShort(),
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
            appReserved2 = generateUShortAppReserved(userCharacter),
            generation = vbData.generation.toUShort(),
            totalTrophies = vbData.totalTrophies.toUShort(),
            specialMissions = watchSpecialMissions.toTypedArray()
        )

        return nfcData
    }


    private suspend fun generateUShortAppReserved(
        userCharacter: UserCharacter
    ): Array<UShort> {
        val cardData = database
            .cardDao()
            .getCardByCharacterId(userCharacter.id)
            .first()

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
        userCharacter: UserCharacter
    ): BENfcCharacter {
        val beData = database
            .userCharacterDao()
            .getBeData(characterId)
            .first()

        val paddedTransformationArray = generateTransformationHistory(characterId)

        val nfcData = BENfcCharacter(
            dimId = characterInfo.cardId.toUShort(),
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
        length: Int = 8
    ): Array<NfcCharacter.Transformation> {
        val history = database.userCharacterDao().getTransformationHistory(characterId).first()
            .map { NfcEvolutionHistory.transformation(it.monIndex, it.transformationDate) }
        return NfcEvolutionHistory.pad(history, length)
    }
}
