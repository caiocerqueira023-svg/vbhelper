package com.tamerslab.transfer.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.tamerslab.vitalwear.protos.Character
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.storage.data.EvolutionHistoryRepository
import com.tamerslab.utils.DeviceType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VitalWearCharacterExporter(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun buildCharacterProto(characterId: Long): Character = withContext(Dispatchers.IO) {
        database.withTransaction {
        EvolutionHistoryRepository(database).repairCharacter(characterId)
        val characterWithSprites = database.userCharacterDao().getCharacterWithSprites(characterId)
        val userCharacter = database.userCharacterDao().getCharacter(characterId)
        val sourceCharacter = database.characterDao().getById(userCharacter.charId)
        // Catalog-defined partners share through their exact converted slot.
        val exportTarget = if (sourceCharacter?.cardId == null) {
            val targetId = requireNotNull(userCharacter.exportCardCharacterId) {
                "Convert this partner to a loaded DIM/BEM slot before sharing it."
            }
            requireNotNull(database.characterDao().getById(targetId)) {
                "Converted slot is no longer available."
            }.also { require(it.cardId != null) { "Converted slot is no longer available." } }
        } else null
        val exportCharacter = exportTarget ?: requireNotNull(sourceCharacter)
        val card = database.cardDao().getCardById(requireNotNull(exportCharacter.cardId))
            ?: error("Card not found for character $characterId")
        val cardProgress = database.cardProgressDao().getCardProgressSync(card.id) ?: 0
        Character.newBuilder()
            .setCardId(card.cardId)
            .setCardName(card.name)
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(exportCharacter.charaIndex)
                    .setVitals(characterWithSprites.vitalPoints)
                    .setTrainingTimeRemainingInSeconds(resolveTrainingSeconds(characterId, userCharacter.characterType))
                    .setTimeUntilNextTransformation(characterWithSprites.transformationCountdown.toLong() * 60L)
                    .setTrainedBp(resolveTrainedBp(characterId, userCharacter.characterType))
                    .setTrainedHp(resolveTrainedHp(characterId, userCharacter.characterType))
                    .setTrainedAp(resolveTrainedAp(characterId, userCharacter.characterType))
                    .setTrainedPp(characterWithSprites.trophies)
                    .setInjured(characterWithSprites.injuryStatus.name.lowercase().contains("inj"))
                    .setAccumulatedDailyInjuries(0)
                    .setTotalBattles(characterWithSprites.totalBattlesWon + characterWithSprites.totalBattlesLost)
                    .setCurrentPhaseBattles(characterWithSprites.currentPhaseBattlesWon + characterWithSprites.currentPhaseBattlesLost)
                    .setTotalWins(characterWithSprites.totalBattlesWon)
                    .setCurrentPhaseWins(characterWithSprites.currentPhaseBattlesWon)
                    .setMood(characterWithSprites.mood)
                    .build()
            )
            .setSettings(
                Character.Settings.newBuilder()
                    .setTrainingInBackground(false)
                    .setAllowedBattles(Character.Settings.AllowedBattles.CARD_ONLY)
                    .build()
            )
            .putMaxAdventureCompletedByCard(card.name, (cardProgress - 1).coerceAtLeast(0))
            .addAllTransformationHistory(
                (if (exportTarget != null) {
                    listOf(
                        com.tamerslab.dtos.CharacterDtos.TransformationHistoryExport(
                            stageId = exportTarget.id,
                            monIndex = exportTarget.charaIndex,
                            cardName = card.name,
                            transformationDate = System.currentTimeMillis()
                        )
                    )
                } else {
                    database.userCharacterDao().getTransformationHistoryForExport(characterId)
                }).map {
                    Character.TransformationEvent.newBuilder()
                        .setCardName(it.cardName)
                        .setPhase(0)
                        .setSlotId(it.monIndex)
                        .build()
                }
            )
            .build()
        }
    }

    suspend fun buildShareIntent(characterId: Long): Intent {
        return withContext(Dispatchers.IO) {
            val proto = buildCharacterProto(characterId)

            val exportDir = File(context.cacheDir, "exports").apply { check(isDirectory || mkdirs()) }
            val exportFile = File.createTempFile("vbhelper_character_${characterId}_", ".vitalwear", exportDir)
            exportFile.writeBytes(proto.toByteArray())
            val exportUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)

            Intent(Intent.ACTION_SEND).apply {
                `package` = "com.tamerslab.vitalwear"
                type = VITALWEAR_CHARACTER_MIME
                putExtra(Intent.EXTRA_STREAM, exportUri)
                clipData = ClipData.newRawUri("", exportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private suspend fun resolveTrainingSeconds(characterId: Long, deviceType: DeviceType): Long {
        if (deviceType != DeviceType.BEDevice) {
            return 0L
        }
        return database.userCharacterDao().getBeData(characterId).valueOrNull()?.remainingTrainingTimeInMinutes?.toLong()?.times(60L) ?: 0L
    }

    private suspend fun resolveTrainedBp(characterId: Long, deviceType: DeviceType): Int {
        return if (deviceType == DeviceType.BEDevice) {
            database.userCharacterDao().getBeData(characterId).valueOrNull()?.trainingBp ?: 0
        } else 0
    }

    private suspend fun resolveTrainedHp(characterId: Long, deviceType: DeviceType): Int {
        return if (deviceType == DeviceType.BEDevice) {
            database.userCharacterDao().getBeData(characterId).valueOrNull()?.trainingHp ?: 0
        } else 0
    }

    private suspend fun resolveTrainedAp(characterId: Long, deviceType: DeviceType): Int {
        return if (deviceType == DeviceType.BEDevice) {
            database.userCharacterDao().getBeData(characterId).valueOrNull()?.trainingAp ?: 0
        } else 0
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.valueOrNull(): T? = firstOrNull()

    companion object {
        const val VITALWEAR_CHARACTER_MIME = "application/x-vitalwear-character"
    }
}
