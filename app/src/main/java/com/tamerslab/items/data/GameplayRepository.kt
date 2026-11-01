package com.tamerslab.items.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.BECharacterData
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.device_data.VBCharacterData
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.hardware.nfc.vb.SpecialMission
import com.tamerslab.storage.data.EvolutionHistoryRepository
import com.tamerslab.utils.DeviceType
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.random.Random
import java.time.Instant

class GameplayRepository(
    private val db: AppDatabase,
    private val currency: CurrencyRepository,
) {
    private enum class ItemUse(val id: Int) {
        PPTraining(1),
        HPTraining(2),
        APTraining(3),
        BPTraining(4),
        AllTraining(5),
        EvoTimer(6),
        LimitTimer(7),
        Vitals(8),
        Step8k(9),
        Step4k(10),
        Vitals1000(11),
        Vitals250(12),
        Battle20(13),
        Battle5(14),
        Win10(15),
        Win4(16),
    }

    suspend fun synchronizeSpritePreference(
        preference: com.tamerslab.species.SpritePreference,
        update: suspend () -> Unit,
    ) = db.withTransaction {
        db.expandedCatalog?.synchronizeArtwork(preference)
        update()
    }

    fun closeDatabase() = db.close()

    suspend fun sendToAdventure(characterId: Long, timeInMinutes: Long) {
        val finishesAdventureAt = timeInMinutes * 60
        db.withTransaction {
            db.participation?.snapshot()?.requireOwned(characterId)
            val character = db.userCharacterDao().getCharacter(characterId)
            if (character.isActive) db.userCharacterDao().clearActiveCharacter()
            db.adventureDao().insertNewAdventure(characterId, timeInMinutes, finishesAdventureAt)
        }
    }

    suspend fun claimAdventure(characterId: Long): Pair<ItemDtos.PurchasedItem, Int>? = db.withTransaction {
        db.participation?.reconcileLocked()
        db.participation?.snapshot()?.requireOwned(characterId)
        if (db.adventureDao().getAdventureCharacters().first().none {
                it.id == characterId && it.finishesAdventure <= Instant.now().epochSecond
            }) return@withTransaction null
        db.adventureDao().deleteAdventure(characterId)
        val generatedCurrency = (2..6).random() * 1000
        currency.credit(generatedCurrency)
        generateAdventureItem(characterId) to generatedCurrency
    }

    suspend fun cancelAdventure(characterId: Long) = db.adventureDao().deleteAdventure(characterId)

    suspend fun hasFinishedAdventures(): Boolean {
        val now = Instant.now().epochSecond
        return db.adventureDao().getAdventureCharacters().first().any { it.finishesAdventure <= now }
    }

    suspend fun clearSpecialMission(missionId: Long): SpecialMissionReward {
        val mission = db.specialMissionDao().getSpecialMission(missionId).first()
        db.specialMissionDao().clearSpecialMission(missionId)
        if (mission.status != SpecialMission.Status.COMPLETED) return SpecialMissionReward(null, null)
        val allItems = db.itemDao().getAllItems().first()
        if (allItems.isEmpty()) return SpecialMissionReward(null, null)
        val item = allItems.random()
        val amount = (Random.nextFloat() * 5).roundToInt()
        db.itemDao().purchaseItem(itemId = item.id, itemAmount = amount)
        val credits = (2..6).random() * 1000
        currency.credit(credits)
        return SpecialMissionReward(ItemDtos.PurchasedItem(
            itemId = item.id,
            itemName = item.name,
            itemDescription = item.description,
            itemIcon = item.itemIcon,
            itemLength = item.itemLength,
            itemAmount = amount,
            itemType = item.itemType,
        ), credits)
    }

    suspend fun degenerate(characterId: Long, transformation: CharacterDtos.TransformationHistory) = db.withTransaction {
        val character = db.userCharacterDao().getCharacter(characterId)
        val characterWithSprites = db.userCharacterDao().getCharacterWithSprites(characterId)
        check(character.charId != transformation.stageId) { "This Digimon is already at that stage." }
        check(transformation.stage < characterWithSprites.stage) { "Degeneration requires a strictly lower stage." }
        check(db.questDao().spendBits(DEGENERATION_COST) == 1) {
            "Not enough bits. $DEGENERATION_COST bits are required."
        }
        EvolutionHistoryRepository(db).repairCharacter(characterId)
        val selected = db.userCharacterDao().getTransformationHistoryEntry(characterId, transformation.id)
        check(selected != null && selected.stageId == transformation.stageId) {
            "This evolution is no longer in this Digimon's history."
        }
        db.userCharacterDao().degenerateCharacter(characterId, selected.stageId)
        db.userCharacterDao().deleteTransformationsAfter(characterId, selected.id)
        EvolutionHistoryRepository(db).repairCharacter(characterId)
    }

    suspend fun applyItem(itemId: Long, characterId: Long): String = db.withTransaction {
        db.participation?.snapshot()?.requireOwned(characterId)
        val item = db.itemDao().getItem(itemId)
        check(item.quantity > 0) { "This item is no longer available." }
        val character = db.userCharacterDao().getCharacter(characterId)
        var beData: BECharacterData? = null
        var vbData: VBCharacterData? = null
        if (character.characterType == DeviceType.BEDevice) {
            beData = db.userCharacterDao().getBeData(characterId).first()
        } else if (character.characterType == DeviceType.VBDevice) {
            vbData = db.userCharacterDao().getVbData(characterId).first()
        }
        when {
            item.itemIcon in 1..5 && character.characterType == DeviceType.BEDevice && beData != null -> {
                beData.itemType = item.itemIcon
                beData.itemMultiplier = 3
                beData.itemRemainingTime = item.itemLength
                db.userCharacterDao().updateBECharacterData(beData)
            }
            item.itemIcon in 1..5 && character.characterType == DeviceType.APP -> {
                val state = requireNotNull(db.appPartnerStateDao().state(character.individualId))
                db.appPartnerStateDao().update(state.copy(itemType = item.itemIcon, itemMultiplier = 3,
                    itemRemainingTime = item.itemLength))
            }
            item.itemIcon == ItemUse.LimitTimer.id && character.characterType == DeviceType.APP -> {
                val state = requireNotNull(db.appPartnerStateDao().state(character.individualId))
                db.appPartnerStateDao().update(state.copy(remainingTrainingTimeInMinutes =
                    (state.remainingTrainingTimeInMinutes + item.itemLength).coerceIn(0, 6000)))
            }
            item.itemIcon == ItemUse.EvoTimer.id -> {
                character.transformationCountdown += item.itemLength
                if (character.transformationCountdown < 0) character.transformationCountdown = 0
                if (character.characterType == DeviceType.VBDevice && character.transformationCountdown <= 0) {
                    character.transformationCountdown = 1
                }
                db.userCharacterDao().updateCharacter(character)
            }
            item.itemIcon == ItemUse.LimitTimer.id && character.characterType == DeviceType.BEDevice && beData != null -> {
                beData.remainingTrainingTimeInMinutes =
                    (beData.remainingTrainingTimeInMinutes + item.itemLength).coerceAtMost(6000)
                db.userCharacterDao().updateBECharacterData(beData)
            }
            item.itemIcon == ItemUse.Vitals.id -> {
                character.vitalPoints = (character.vitalPoints + item.itemLength).coerceIn(0, 9999)
                db.userCharacterDao().updateCharacter(character)
            }
            item.itemIcon in ItemUse.Step8k.id..ItemUse.Win4.id &&
                (character.characterType == DeviceType.APP || character.characterType == DeviceType.VBDevice && vbData != null) -> {
                applySpecialMission(item.itemIcon, item.itemLength, characterId)
            }
            else -> error("This item is not applicable to this partner")
        }
        check(db.itemDao().useItem(item.id) == 1) { "This item is no longer available." }
        item.name
    }

    private suspend fun applySpecialMission(itemIcon: Int, itemLength: Int, characterId: Long) {
        val type = when (itemIcon) {
            ItemUse.Step8k.id, ItemUse.Step4k.id -> SpecialMission.Type.STEPS
            ItemUse.Vitals1000.id, ItemUse.Vitals250.id -> SpecialMission.Type.VITALS
            ItemUse.Battle20.id, ItemUse.Battle5.id -> SpecialMission.Type.BATTLES
            ItemUse.Win10.id, ItemUse.Win4.id -> SpecialMission.Type.WINS
            else -> SpecialMission.Type.NONE
        }
        val goal = when (itemIcon) {
            ItemUse.Step8k.id -> 8000
            ItemUse.Step4k.id -> 4000
            ItemUse.Vitals1000.id -> 1000
            ItemUse.Vitals250.id -> 250
            ItemUse.Battle20.id -> 20
            ItemUse.Battle5.id -> 5
            ItemUse.Win10.id -> 10
            ItemUse.Win4.id -> 4
            else -> 0
        }
        val slot = when (itemIcon) {
            ItemUse.Step8k.id, ItemUse.Step4k.id -> 0
            ItemUse.Vitals1000.id, ItemUse.Vitals250.id -> 1
            ItemUse.Battle20.id, ItemUse.Battle5.id -> 2
            ItemUse.Win10.id, ItemUse.Win4.id -> 3
            else -> 0
        }
        val current = db.userCharacterDao().getSpecialMissions(characterId).first()
        val mission = requireNotNull(current.firstOrNull { it.watchId == slot })
        db.userCharacterDao().insertSpecialMissions(SpecialMissions(
            id = mission.id,
            characterId = mission.characterId,
            goal = goal,
            watchId = mission.watchId,
            progress = 0,
            status = SpecialMission.Status.AVAILABLE,
            timeElapsedInMinutes = 0,
            timeLimitInMinutes = itemLength,
            missionType = type,
        ))
    }

    private suspend fun generateAdventureItem(characterId: Long): ItemDtos.PurchasedItem {
        val character = db.userCharacterDao().getCharacterInfo(characterId)
        val item = db.itemDao().getAllItems().first().random()
        val amount = ((Random.nextFloat() * character.stage) + 3).roundToInt()
        db.itemDao().purchaseItem(itemId = item.id, itemAmount = amount)
        return ItemDtos.PurchasedItem(
            itemId = item.id,
            itemAmount = amount,
            itemName = item.name,
            itemIcon = item.itemIcon,
            itemLength = item.itemLength,
            itemDescription = item.description,
            itemType = item.itemType,
        )
    }

    data class SpecialMissionReward(val item: ItemDtos.PurchasedItem?, val credits: Int?)

    private companion object {
        const val DEGENERATION_COST = 5000
    }
}
