package com.github.nacabaro.vbhelper.screens.scanScreen.converters

import androidx.activity.ComponentActivity
import com.github.cfogrady.vbnfc.be.BENfcCharacter
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.identity.resolveReturningIndividual
import com.github.nacabaro.vbhelper.domain.identity.TransferFingerprint
import com.github.nacabaro.vbhelper.domain.identity.WatchImportReceipt
import com.github.nacabaro.vbhelper.domain.identity.WatchTransferSafety
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.domain.device_data.NfcEvolutionHistory
import com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import com.github.nacabaro.vbhelper.database.AppDatabase

class FromNfcConverter (
    private val database: AppDatabase,
    private val sourceDevice: String = "",
    private val onImported: (Long) -> Unit = {},
) {
    constructor(componentActivity: ComponentActivity, sourceDevice: String = "") : this(
        (componentActivity.applicationContext as VBHelper).container.db,
        sourceDevice,
        { id ->
            val app = componentActivity.applicationContext as VBHelper
            app.applicationScope.launch { app.container.reactionRepository.evaluateAndReact(id) }
        },
    )
    
    
    fun addCharacterUsingCard(
        nfcCharacter: NfcCharacter,
        cardId: Long
    ): String {
        val cardData = database
            .cardDao()
            .getCardById(cardId)

        if (cardData == null) {
            return "Card not found"
        }
        check(cardData.cardId == nfcCharacter.dimId.toInt()) { "Selected card does not match the received DIM." }

        return insertCharacter(nfcCharacter, cardData)
    }
    

    fun addCharacter(
        nfcCharacter: NfcCharacter,
        onMultipleCards: (List<Card>, NfcCharacter) -> Unit
    ): String {
        // A lost receive ACK may be retried after another export starts. Resolve the
        // exact committed import first; insertCharacter rechecks it transactionally.
        database.watchTransferDao().getImport(TransferFingerprint.of(nfcCharacter, sourceDevice))?.let { previous ->
            val saved = database.userCharacterDao().getCharacterSync(previous.characterId)
            check(saved != null && saved.individualId == previous.individualId) {
                "This transfer was already received and its Digimon has since left storage."
            }
            val card = requireNotNull(database.cardDao().getCardByCharacterIdSync(saved.id))
            return insertCharacter(nfcCharacter, card)
        }
        val transfer = checkedTransfer(nfcCharacter)
        transfer?.cardId?.let { exportedCardId ->
            val exportedCard = database.cardDao().getCardById(exportedCardId)
            check(exportedCard != null) {
                "The original imported DIM is missing. Restore it before receiving this Digimon."
            }
            check(exportedCard.cardId == nfcCharacter.dimId.toInt()) {
                "O identificador devolvido pelo relógio pertence a outro DIM. O Digimon foi preservado no relógio."
            }
            return insertCharacter(nfcCharacter, exportedCard)
        }
        val appReservedCardId = nfcCharacter
            .appReserved2[0].toLong()

        var cardData: Card? =  null

        if (appReservedCardId != 0L) {
            val fetchedCard = database
                .cardDao()
                .getCardById(appReservedCardId)

            if (fetchedCard != null && fetchedCard.cardId == nfcCharacter.dimId.toInt()) {
                cardData = fetchedCard
            }
        }

        if (cardData == null) {
            val allCards = database
                .cardDao()
                .getCardByCardId(nfcCharacter.dimId.toInt())

            if (allCards.isEmpty())
                return "Card not found"

            if (allCards.size > 1) {
                onMultipleCards(allCards, nfcCharacter)
                return "Multiple cards found"
            }

            cardData = allCards[0]
        }

        return insertCharacter(nfcCharacter, cardData)
    }



    private fun insertCharacter(
        nfcCharacter: NfcCharacter,
        cardData: Card
    ): String {
        val characterId = database.runInTransaction(Callable {
            val fingerprint = TransferFingerprint.of(nfcCharacter, sourceDevice)
            val previous = database.watchTransferDao().getImport(fingerprint)
            if (previous != null) {
                val saved = database.userCharacterDao().getCharacterSync(previous.characterId)
                check(saved != null && saved.individualId == previous.individualId) {
                    "This transfer was already received and its Digimon has since left storage."
                }
                previous.characterId
            } else {
                val insertedId = insertCharacterInTransaction(nfcCharacter, cardData)
                val inserted = requireNotNull(database.userCharacterDao().getCharacterSync(insertedId))
                database.watchTransferDao().recordImport(WatchImportReceipt(fingerprint, insertedId, inserted.individualId))
                insertedId
            }
        })
        onImported(characterId)
        return "Done reading character!"
    }

    private fun insertCharacterInTransaction(
        nfcCharacter: NfcCharacter,
        cardData: Card
    ): Long {
        val transfer = checkedTransfer(nfcCharacter)
        check(transfer?.cardId == null || transfer.cardId == cardData.id) {
            "This individual must return to its original imported DIM."
        }
        val cardCharData = database
            .characterDao()
            .getCharacterByMonIndex(nfcCharacter.charIndex.toInt(), cardData.id)

        updateCardProgress(nfcCharacter, cardData)

        val individualId = resolveIndividualId(
            nfcCharacter = nfcCharacter,
            stage = cardCharData.stage,
            attribute = cardCharData.attribute
        )
        val characterData = UserCharacter(
            individualId = individualId,
            charId = cardCharData.id,
            ageInDays = nfcCharacter.ageInDays.toInt() and 0xFF,
            mood = nfcCharacter.mood.toInt(),
            vitalPoints = nfcCharacter.vitalPoints.toInt(),
            transformationCountdown = nfcCharacter.transformationCountdownInMinutes.toInt(),
            injuryStatus = nfcCharacter.injuryStatus,
            trophies = nfcCharacter.trophies.toInt(),
            currentPhaseBattlesWon = nfcCharacter.currentPhaseBattlesWon.toInt(),
            currentPhaseBattlesLost = nfcCharacter.currentPhaseBattlesLost.toInt(),
            totalBattlesWon = nfcCharacter.totalBattlesWon.toInt(),
            totalBattlesLost = nfcCharacter.totalBattlesLost.toInt(),
            activityLevel = nfcCharacter.activityLevel.toInt(),
            heartRateCurrent = nfcCharacter.heartRateCurrent.toInt(),
            characterType = when (nfcCharacter) {
                is BENfcCharacter -> DeviceType.BEDevice
                else -> DeviceType.VBDevice
            },
            isActive = true
        )

        database
            .userCharacterDao()
            .clearActiveCharacter()

        val characterId: Long = database
            .userCharacterDao()
            .insertCharacterData(characterData)

        if (nfcCharacter is BENfcCharacter) {
            addBeCharacterToDatabase(
                characterId = characterId,
                nfcCharacter = nfcCharacter
            )
        } else if (nfcCharacter is VBNfcCharacter) {
            addVbCharacterToDatabase(
                characterId = characterId,
                nfcCharacter = nfcCharacter
            )
        }

        addTransformationHistoryToDatabase(
            characterId = characterId,
            nfcCharacter = nfcCharacter,
            dimData = cardData
        )

        addVitalsHistoryToDatabase(
            characterId = characterId,
            nfcCharacter = nfcCharacter
        )

        EvolutionHistoryRepository(database).repairCharacter(characterId)
        return characterId
    }

    /** App-reserved bytes are not proven to survive active/backup switching independently.
     * Never use lineage as an alternative identity, or silently create a new identity
     * while this watch has an unresolved export. Rechecked inside the import transaction.
     */
    private fun checkedTransfer(character: NfcCharacter): com.github.nacabaro.vbhelper.domain.identity.WatchTransfer? {
        val transfers = database.watchTransferDao()
        val token = IndividualIdentity.decode(character.appReserved1)
        val transfer = token?.let(transfers::get)
        val pending = transfers.getPendingForWatch(sourceDevice)
        return WatchTransferSafety.checkReturn(character, sourceDevice, transfer, pending)
    }

    /** Only a recorded export with matching lineage can restore an individual. */
    private fun resolveIndividualId(
        nfcCharacter: NfcCharacter,
        stage: Int,
        attribute: NfcCharacter.Attribute
    ): String {
        val token = IndividualIdentity.decode(nfcCharacter.appReserved1)
        val transfers = database.watchTransferDao()
        val transfer = token?.let(transfers::get)
        if (transfer != null) {
            check(transfer.deviceKey.isEmpty() || transfer.deviceKey == sourceDevice) {
                "This transfer belongs to another physical watch."
            }
            check(transfer.matches(nfcCharacter)) {
                "The watch data does not match this individual's transfer. It has been preserved on the watch."
            }
            check(database.digimonIndividualDao().exists(transfer.individualId)) { "The permanent individual record is missing." }
            val local = database.userCharacterDao().getByIndividualIdSync(transfer.individualId)
            if (local.isNotEmpty()) {
                // Recover a send that reached the watch but whose success ACK was lost.
                check(local.size == 1 && local.single().id == transfer.sourceCharacterId &&
                    TransferFingerprint.of(local.single()) == transfer.sourceFingerprint) {
                    "A different local copy already owns this identity. Both copies have been preserved."
                }
                database.userCharacterDao().deleteCharacterById(local.single().id)
            }
            check(!transfers.isPresentLocally(transfer.individualId)) { "This individual is already present in World." }
        }
        val individualId = resolveReturningIndividual(
            character = nfcCharacter,
            findTransfer = transfers::get,
            individualExists = database.digimonIndividualDao()::exists,
            isPresentLocally = transfers::isPresentLocally,
        ) ?: IndividualIdentity.generate()
        // Consumed atomically with the complete import and its retry receipt.
        if (token != null) transfers.consume(token)
        database.runInTransaction {
            database.digimonIndividualDao().insert(
                DigimonIndividual(individualId = individualId, createdAt = System.currentTimeMillis())
            )
            if (database.digimonIndividualDao().getPersonalitySync(individualId) == null) {
                database.digimonIndividualDao().insertPersonalitySync(
                    DigimonPersonalityGenerator.generate(
                        individualId = individualId,
                        attribute = attribute,
                        stage = stage
                    )
                )
            }
        }
        return individualId
    }
    


    private fun updateCardProgress(
        nfcCharacter: NfcCharacter,
        cardData: Card
    ) {
        database
            .cardProgressDao()
            .updateCardProgress(
                currentStage = nfcCharacter.nextAdventureMissionStage.toInt(),
                cardId = cardData.id,
                unlocked = nfcCharacter.nextAdventureMissionStage.toInt() > cardData.stageCount,
            )
    }



    private fun addVbCharacterToDatabase(
        characterId: Long,
        nfcCharacter: VBNfcCharacter
    ) {
        val extraCharacterData = VBCharacterData(
            id = characterId,
            generation = nfcCharacter.generation.toInt(),
            totalTrophies = nfcCharacter.totalTrophies.toInt()
        )

        database
            .userCharacterDao()
            .insertVBCharacterData(extraCharacterData)

        addSpecialMissionsToDatabase(nfcCharacter, characterId)
    }



    private fun addSpecialMissionsToDatabase(
        nfcCharacter: VBNfcCharacter,
        characterId: Long
    ) {
        val specialMissionsWatch = nfcCharacter.specialMissions
        val specialMissionsDb = specialMissionsWatch.map { item ->
            SpecialMissions(
                characterId = characterId,
                goal = item.goal.toInt(),
                watchId = item.id.toInt(),
                progress = item.progress.toInt(),
                status = item.status,
                timeElapsedInMinutes = item.timeElapsedInMinutes.toInt(),
                timeLimitInMinutes = item.timeLimitInMinutes.toInt(),
                missionType = item.type,
            )
        }

        database
            .userCharacterDao()
            .insertSpecialMissions(*specialMissionsDb.toTypedArray())
    }



    private fun addBeCharacterToDatabase(
        characterId: Long,
        nfcCharacter: BENfcCharacter
    ) {
        val extraCharacterData = BECharacterData(
            id = characterId,
            trainingHp = nfcCharacter.trainingHp.toInt(),
            trainingAp = nfcCharacter.trainingAp.toInt(),
            trainingBp = nfcCharacter.trainingBp.toInt(),
            remainingTrainingTimeInMinutes = nfcCharacter.remainingTrainingTimeInMinutes.toInt(),
            itemEffectActivityLevelValue = nfcCharacter.itemEffectActivityLevelValue.toInt(),
            itemEffectMentalStateValue = nfcCharacter.itemEffectMentalStateValue.toInt(),
            itemEffectMentalStateMinutesRemaining = nfcCharacter.itemEffectMentalStateMinutesRemaining.toInt(),
            itemEffectActivityLevelMinutesRemaining = nfcCharacter.itemEffectActivityLevelMinutesRemaining.toInt(),
            itemEffectVitalPointsChangeValue = nfcCharacter.itemEffectVitalPointsChangeValue.toInt(),
            itemEffectVitalPointsChangeMinutesRemaining = nfcCharacter.itemEffectVitalPointsChangeMinutesRemaining.toInt(),
            abilityRarity = nfcCharacter.abilityRarity,
            abilityType = nfcCharacter.abilityType.toInt(),
            abilityBranch = nfcCharacter.abilityBranch.toInt(),
            abilityReset = nfcCharacter.abilityReset.toInt(),
            rank = nfcCharacter.abilityReset.toInt(),
            itemType = nfcCharacter.itemType.toInt(),
            itemMultiplier = nfcCharacter.itemMultiplier.toInt(),
            itemRemainingTime = nfcCharacter.itemRemainingTime.toInt(),
            otp0 = "", //nfcCharacter.value!!.otp0.toString(),
            otp1 = "", //nfcCharacter.value!!.otp1.toString(),
            minorVersion = nfcCharacter.characterCreationFirmwareVersion.minorVersion.toInt(),
            majorVersion = nfcCharacter.characterCreationFirmwareVersion.majorVersion.toInt(),
        )

        database
            .userCharacterDao()
            .insertBECharacterData(extraCharacterData)
    }



    private fun addVitalsHistoryToDatabase(
        characterId: Long,
        nfcCharacter: NfcCharacter
    ) {
        val vitalsHistoryWatch = nfcCharacter.vitalHistory
        val vitalsHistory = vitalsHistoryWatch.map { historyElement ->
            val year = if (historyElement.year.toInt() !in 2021 .. 2035) 0 else historyElement.year.toInt()

            VitalsHistory(
                charId = characterId,
                year = year,
                month = historyElement.month.toInt(),
                day = historyElement.day.toInt(),
                vitalPoints = historyElement.vitalsGained.toInt()
            )
        }

        database
            .userCharacterDao()
            .insertVitals(*vitalsHistory.toTypedArray())
    }


    private fun addTransformationHistoryToDatabase(
        characterId: Long,
        nfcCharacter: NfcCharacter,
        dimData: Card
    ) {
        val transformationHistoryWatch = nfcCharacter.transformationHistory
        transformationHistoryWatch.map { item ->
            if (item.toCharIndex.toInt() != 255) {
                // Calendar expects 0-based months; the watch sends 1-based months.
                // An invalid device date must not prevent saving a received Digimon.
                val date = NfcEvolutionHistory.dateToEpochMillis(item) ?: System.currentTimeMillis()

                database
                    .userCharacterDao()
                    .insertTransformation(
                        characterId,
                        item.toCharIndex.toInt(),
                        dimData.id,
                        date
                    )

                database
                    .dexDao()
                    .insertCharacter(
                        item.toCharIndex.toInt(),
                        dimData.id,
                        date
                    )
            }
        }
    }
}
