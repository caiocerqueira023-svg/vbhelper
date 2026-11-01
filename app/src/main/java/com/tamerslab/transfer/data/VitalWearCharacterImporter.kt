package com.tamerslab.transfer.data

import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.vitalwear.protos.Character
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.BECharacterData
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.storage.data.EvolutionHistoryRepository
import com.tamerslab.utils.DeviceType
import kotlin.math.max
import java.util.concurrent.Callable
import com.tamerslab.domain.identity.TransferFingerprint
import com.tamerslab.domain.identity.WatchImportReceipt

class VitalWearCharacterImporter(
    private val database: AppDatabase
) {
    data class ImportResult(
        val success: Boolean,
        val message: String
    )

    fun importCharacter(character: Character): ImportResult = database.runInTransaction(Callable {
        val fingerprint = TransferFingerprint.bytes(character.toByteArray(), "VitalWear")
        val previous = database.watchTransferDao().getImport(fingerprint)
        if (previous != null) {
            val saved = database.userCharacterDao().getCharacterSync(previous.characterId)
            ImportResult(saved?.individualId == previous.individualId, "This transfer has already been imported.")
        } else importCharacterInTransaction(character)
    })

    private fun importCharacterInTransaction(character: Character): ImportResult {
        val importedCard = resolveCard(character)
            ?: return ImportResult(
                success = false,
                message = "Matching card not found in Tamers Lab. Import that card first."
            )

        val slotId = character.characterStats.slotId
        val cardCharacter = runCatching {
            database.characterDao().getCharacterByMonIndex(slotId, importedCard.id)
        }.getOrNull() ?: return ImportResult(
            success = false,
            message = "Character slot $slotId was not found on card ${importedCard.name}."
        )

        val usable = database.participation?.snapshot()?.card(cardCharacter.id)?.usable != false
        if (usable) database.userCharacterDao().clearActiveCharacter()

        val totalBattles = character.characterStats.totalBattles
        val totalWins = character.characterStats.totalWins.coerceAtMost(totalBattles)
        val currentPhaseBattles = character.characterStats.currentPhaseBattles
        val currentPhaseWins = character.characterStats.currentPhaseWins.coerceAtMost(currentPhaseBattles)

        val userCharacterId = database.userCharacterDao().insertCharacterData(
            UserCharacter(
                charId = cardCharacter.id,
                ageInDays = max(character.transformationHistoryCount - 1, 0),
                mood = character.characterStats.mood,
                vitalPoints = character.characterStats.vitals,
                transformationCountdown = secondsToMinutes(character.characterStats.timeUntilNextTransformation),
                injuryStatus = resolveInjuryStatus(character.characterStats.injured),
                trophies = character.characterStats.trainedPp,
                currentPhaseBattlesWon = currentPhaseWins,
                currentPhaseBattlesLost = (currentPhaseBattles - currentPhaseWins).coerceAtLeast(0),
                totalBattlesWon = totalWins,
                totalBattlesLost = (totalBattles - totalWins).coerceAtLeast(0),
                activityLevel = 0,
                heartRateCurrent = 0,
                characterType = DeviceType.BEDevice,
                isActive = usable
            )
        )

        database.userCharacterDao().insertBECharacterData(
            BECharacterData(
                id = userCharacterId,
                trainingHp = character.characterStats.trainedHp,
                trainingAp = character.characterStats.trainedAp,
                trainingBp = character.characterStats.trainedBp,
                remainingTrainingTimeInMinutes = secondsToMinutes(character.characterStats.trainingTimeRemainingInSeconds),
                itemEffectMentalStateValue = 0,
                itemEffectMentalStateMinutesRemaining = 0,
                itemEffectActivityLevelValue = 0,
                itemEffectActivityLevelMinutesRemaining = 0,
                itemEffectVitalPointsChangeValue = 0,
                itemEffectVitalPointsChangeMinutesRemaining = 0,
                abilityRarity = resolveDefaultAbilityRarity(),
                abilityType = 0,
                abilityBranch = 0,
                abilityReset = 0,
                rank = 0,
                itemType = 0,
                itemMultiplier = 0,
                itemRemainingTime = 0,
                otp0 = "",
                otp1 = "",
                minorVersion = 0,
                majorVersion = 0
            )
        )

        val now = System.currentTimeMillis()
        database.dexDao().insertCharacter(slotId, importedCard.id, now)

        for (transformation in character.transformationHistoryList) {
            val transformationCard = resolveCard(transformation.cardName, character.cardId)
            if (transformationCard != null) {
                database.userCharacterDao().insertTransformation(
                    userCharacterId,
                    transformation.slotId,
                    transformationCard.id,
                    now
                )
                database.dexDao().insertCharacter(transformation.slotId, transformationCard.id, now)
            }
        }

        for ((cardName, maxAdventureCompleted) in character.maxAdventureCompletedByCardMap) {
            val adventureCard = resolveCard(cardName, null) ?: continue
            val currentStage = (maxAdventureCompleted + 1).coerceAtLeast(0)
            database.cardProgressDao().updateCardProgress(
                currentStage = currentStage,
                cardId = adventureCard.id,
                unlocked = currentStage > adventureCard.stageCount
            )
        }

        EvolutionHistoryRepository(database).repairCharacter(userCharacterId)
        val saved = requireNotNull(database.userCharacterDao().getCharacterSync(userCharacterId))
        database.watchTransferDao().recordImport(WatchImportReceipt(
            TransferFingerprint.bytes(character.toByteArray(), "VitalWear"), userCharacterId, saved.individualId,
        ))
        return ImportResult(
            success = true,
            message = "Imported ${importedCard.name} slot $slotId from VitalWear."
        )
    }

    private fun resolveCard(character: Character) = resolveCard(character.cardName, character.cardId)

    private fun resolveCard(cardName: String?, cardId: Int?): com.tamerslab.domain.card.Card? {
        if (!cardName.isNullOrBlank()) {
            database.cardDao().getCardByName(cardName)?.let { return it }
        }

        if (cardId != null) {
            val matches = database.cardDao().getCardByCardId(cardId)
            if (matches.size == 1) {
                return matches.first()
            }
        }

        return null
    }

    private fun secondsToMinutes(seconds: Long): Int {
        if (seconds <= 0L) {
            return 0
        }
        return (seconds / 60L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun resolveInjuryStatus(injured: Boolean): NfcCharacter.InjuryStatus {
        val statuses = enumValues<NfcCharacter.InjuryStatus>()
        if (!injured) {
            return statuses.firstOrNull {
                val normalized = it.name.lowercase()
                normalized.contains("none") || normalized.contains("normal") || normalized.contains("healthy")
            } ?: statuses.first()
        }

        return statuses.firstOrNull {
            val normalized = it.name.lowercase()
            normalized.contains("inj")
        } ?: statuses.last()
    }

    private fun resolveDefaultAbilityRarity(): NfcCharacter.AbilityRarity {
        return enumValues<NfcCharacter.AbilityRarity>().first()
    }
}
