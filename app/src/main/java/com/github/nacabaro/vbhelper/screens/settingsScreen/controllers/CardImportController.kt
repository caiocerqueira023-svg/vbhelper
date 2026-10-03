package com.github.nacabaro.vbhelper.screens.settingsScreen.controllers

import android.util.Log
import androidx.room.withTransaction
import com.github.cfogrady.vb.dim.card.BemCard
import com.github.cfogrady.vb.dim.card.DimCard
import com.github.cfogrady.vb.dim.card.DimReader
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.battle.ImportedAttackArtReader
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.card.CardProgress
import com.github.nacabaro.vbhelper.domain.card.CardSpecificJogress
import com.github.nacabaro.vbhelper.source.JogressImportReader
import com.github.nacabaro.vbhelper.source.CardReimportCandidate
import com.github.nacabaro.vbhelper.source.CardReimportPolicy
import com.github.nacabaro.vbhelper.source.CardImportResult
import kotlinx.coroutines.CancellationException
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import java.io.InputStream

class CardImportController(
    private val database: AppDatabase,
    private val speciesRepository: com.github.nacabaro.vbhelper.species.SpeciesRepository? = null
) {
    suspend fun importCard(
        fileReader: InputStream?,
        sourceFileName: String? = null,
        onCardCreated: suspend (cardId: Long, cardName: String) -> Unit = { _, _ -> },
    ): Long {
        val dimReader = DimReader()
        val card = dimReader.readCard(fileReader, false)
        return importParsedCard(card, sourceFileName, onCardCreated)
    }

    suspend fun importCardWithResult(fileReader: InputStream, sourceFileName: String? = null): CardImportResult =
        importParsedCardWithResult(parseCard(fileReader), sourceFileName)

    fun parseCard(fileReader: InputStream): com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *> =
        DimReader().readCard(fileReader, false)

    internal suspend fun importParsedCard(
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>,
        sourceFileName: String? = null,
        onCardCreated: suspend (cardId: Long, cardName: String) -> Unit = { _, _ -> }
    ): Long = importParsedCardWithResult(card, sourceFileName, onCardCreated).cardId

    internal suspend fun importParsedCardWithResult(
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>,
        sourceFileName: String? = null,
        onCardCreated: suspend (cardId: Long, cardName: String) -> Unit = { _, _ -> },
    ): CardImportResult {
        val result = database.withTransaction {
            var created = false
            val id = importParsedCardRows(card, sourceFileName) { cardId, cardName ->
                created = true
                onCardCreated(cardId, cardName)
            }
            val stored = checkNotNull(database.cardDao().getCardById(id))
            CardImportResult(id, stored.name, created)
        }
        // Matching can do I/O: it belongs after commit, never in the card transaction.
        if (result.isNew) speciesRepository?.let { repo ->
            try {
                val matched = repo.matchOfficialSpeciesForCard(result.cardId)
                if (matched > 0) database.cardDao().updateOfficialStatus(result.cardId,
                    com.github.nacabaro.vbhelper.domain.card.OfficialStatus.OFFICIAL)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("CardImportController", "Failed to match imported species", e) }
        }
        return result
    }

    private suspend fun importParsedCardRows(
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>,
        sourceFileName: String?,
        onCardCreated: suspend (Long, String) -> Unit,
    ): Long {
        val cardModel = Card(
            cardId = card.header.dimId,
            logo = card.spriteData.sprites[0].pixelData,
            name = nameFromSourceFile(sourceFileName, card.spriteData.text),
            stageCount = card.adventureLevels.levels.size,
            logoHeight = card.spriteData.sprites[0].height,
            logoWidth = card.spriteData.sprites[0].width,
            isBEm = card is BemCard
        )

        val characters = readCharacterData(card)
        // Older imports discarded attack assignments. Re-reading the same card
        // restores them in place, so existing individuals keep their species keys.
        val bodyMatches = database.cardDao().getCardByCardId(cardModel.cardId).filter { existing ->
            existing.isBEm == cardModel.isBEm && existing.logoWidth == cardModel.logoWidth &&
                existing.logoHeight == cardModel.logoHeight && existing.logo.contentEquals(cardModel.logo)
        }.filter { existing -> matchesCharacterData(existing.id, characters) }
        val candidates = bodyMatches.map { existing ->
            val oldArt = database.cardAttackArtDao().getForCard(existing.id)
            CardReimportCandidate(existing.id, existing.name,
                attackMatches = oldArt.size == characters.size && oldArt.all { art ->
                    val species = database.characterDao().getById(art.cardCharacterId) ?: return@all false
                    art == ImportedAttackArtReader.read(species.id, card.characterStats.characterEntries[species.charaIndex], card.spriteData, card is BemCard)
                }, missingArt = oldArt.isEmpty())
        }
        val matchingCardId = CardReimportPolicy.select(candidates, cardModel.name)
        if (matchingCardId != null) {
            database.withTransaction {
                importAttackArt(matchingCardId, card)
                importEvoData(matchingCardId, card)
                importCardFusions(matchingCardId, card)
                if (!sourceFileName.isNullOrBlank()) database.cardDao().refreshImportedName(matchingCardId, cardModel.name)
            }
            return matchingCardId
        }

        val cardId = database
            .cardDao()
            .insertNewCard(cardModel)

        updateCardProgress(cardId = cardId)
        onCardCreated(cardId, cardModel.name)

        importCharacterData(cardId, characters)
        importAttackArt(cardId, card)

        importEvoData(cardId, card)

        importAdventureMissions(cardId, card)

        importCardFusions(cardId, card)

        return cardId
    }

    private fun nameFromSourceFile(sourceFileName: String?, fallbackName: String): String {
        val fileName = sourceFileName?.trim()?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
        if (fileName.isBlank()) return fallbackName

        return fileName
            .substringBeforeLast('.', missingDelimiterValue = fileName)
            .trim()
            .ifBlank { fallbackName }
    }

    private fun updateCardProgress(
        cardId: Long,
    ) {
        database
            .cardProgressDao()
            .insertCardProgress(
                CardProgress(
                    cardId = cardId,
                    currentStage = 1,
                    unlocked = false
                )
            )
    }

    private data class ImportedCharacter(val character: CardCharacter, val sprite: Sprite)

    private fun readCharacterData(
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>
    ): List<ImportedCharacter> {
        var spriteCounter = when (card is BemCard) {
            true -> 54
            false -> 10
        }

        val domainCharacters = mutableListOf<ImportedCharacter>()

        val characters = card
            .characterStats
            .characterEntries

        for (index in 0 until characters.size) {
            val domainSprite: Sprite
            if (index < 2 && card is DimCard) {
                domainSprite = Sprite(
                    width = card.spriteData.sprites[spriteCounter + 1].spriteDimensions.width,
                    height = card.spriteData.sprites[spriteCounter + 1].spriteDimensions.height,
                    spriteIdle1 = card.spriteData.sprites[spriteCounter + 1].pixelData,
                    spriteIdle2 = card.spriteData.sprites[spriteCounter + 2].pixelData,
                    spriteWalk1 = card.spriteData.sprites[spriteCounter + 1].pixelData,
                    spriteWalk2 = card.spriteData.sprites[spriteCounter + 3].pixelData,
                    spriteRun1 = card.spriteData.sprites[spriteCounter + 1].pixelData,
                    spriteRun2 = card.spriteData.sprites[spriteCounter + 3].pixelData,
                    spriteTrain1 = card.spriteData.sprites[spriteCounter + 1].pixelData,
                    spriteTrain2 = card.spriteData.sprites[spriteCounter + 3].pixelData,
                    spriteHappy = card.spriteData.sprites[spriteCounter + 4].pixelData,
                    spriteSleep = card.spriteData.sprites[spriteCounter + 5].pixelData,
                    spriteAttack = card.spriteData.sprites[spriteCounter + 2].pixelData,
                    spriteDodge = card.spriteData.sprites[spriteCounter + 3].pixelData
                )
            } else {
                domainSprite = Sprite(
                    width = card.spriteData.sprites[spriteCounter + 1].spriteDimensions.width,
                    height = card.spriteData.sprites[spriteCounter + 1].spriteDimensions.height,
                    spriteIdle1 = card.spriteData.sprites[spriteCounter + 1].pixelData,
                    spriteIdle2 = card.spriteData.sprites[spriteCounter + 2].pixelData,
                    spriteWalk1 = card.spriteData.sprites[spriteCounter + 3].pixelData,
                    spriteWalk2 = card.spriteData.sprites[spriteCounter + 4].pixelData,
                    spriteRun1 = card.spriteData.sprites[spriteCounter + 5].pixelData,
                    spriteRun2 = card.spriteData.sprites[spriteCounter + 6].pixelData,
                    spriteTrain1 = card.spriteData.sprites[spriteCounter + 7].pixelData,
                    spriteTrain2 = card.spriteData.sprites[spriteCounter + 8].pixelData,
                    spriteHappy = card.spriteData.sprites[spriteCounter + 9].pixelData,
                    spriteSleep = card.spriteData.sprites[spriteCounter + 10].pixelData,
                    spriteAttack = card.spriteData.sprites[spriteCounter + 11].pixelData,
                    spriteDodge = card.spriteData.sprites[spriteCounter + 12].pixelData
                )
            }

            domainCharacters.add(
                ImportedCharacter(CardCharacter(
                    cardId = 0,
                    spriteId = 0,
                    charaIndex = index,
                    nameSprite = card.spriteData.sprites[spriteCounter].pixelData,
                    stage = characters[index].stage,
                    attribute = NfcCharacter.Attribute.entries[characters[index].attribute],
                    baseHp = characters[index].hp,
                    baseBp = characters[index].dp,
                    baseAp = characters[index].ap,
                    nameWidth = card.spriteData.sprites[spriteCounter].spriteDimensions.width,
                    nameHeight = card.spriteData.sprites[spriteCounter].spriteDimensions.height
                ), domainSprite)
            )

            spriteCounter += if (card is BemCard) {
                14
            } else {
                when (index) {
                    0 -> 6
                    1 -> 7
                    else -> 14
                }
            }
        }

        return domainCharacters
    }

    private suspend fun matchesCharacterData(cardId: Long, incoming: List<ImportedCharacter>): Boolean {
        val existing = database.characterDao().getCharactersForCard(cardId).sortedBy { it.charaIndex }
        if (existing.size != incoming.size) return false
        for ((stored, source) in existing.zip(incoming)) {
            if (stored.copy(id = 0, cardId = 0, spriteId = 0) != source.character) return false
            val sprite = database.spriteDao().getForCharacter(stored.id) ?: return false
            if (sprite.copy(id = 0) != source.sprite) return false
        }
        return true
    }

    private suspend fun importCharacterData(cardId: Long, characters: List<ImportedCharacter>) {
        val models = characters.map { source ->
            source.character.copy(cardId = cardId, spriteId = database.spriteDao().insertSprite(source.sprite))
        }
        database.characterDao().insertCharacter(*models.toTypedArray())
    }

    private suspend fun importAttackArt(cardId: Long, card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>) {
        val art = database.characterDao().getCharactersForCard(cardId).map { character ->
            ImportedAttackArtReader.read(character.id, card.characterStats.characterEntries[character.charaIndex],
                card.spriteData, card is BemCard)
        }
        database.cardAttackArtDao().put(art)
    }

    private suspend fun importAdventureMissions(
        cardId: Long,
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>
    ) {
        Log.d("importAdventureMissions", "Importing adventure missions")
        if (card is BemCard) {
            card.adventureLevels.levels.forEach {
                database
                    .cardAdventureDao()
                    .insertNewAdventure(
                        cardId = cardId,
                        characterId = it.bossCharacterIndex,
                        steps = it.steps,
                        bossAp = it.bossAp,
                        bossHp = it.bossHp,
                        bossDp = it.bossDp,
                        bossBp = it.bossBp
                    )
            }
        } else if (card is DimCard) {
            card.adventureLevels.levels.map {
                database
                    .cardAdventureDao()
                    .insertNewAdventure(
                        cardId = cardId,
                        characterId = it.bossCharacterIndex,
                        steps = it.steps,
                        bossAp = it.bossAp,
                        bossHp = it.bossHp,
                        bossDp = it.bossDp,
                        bossBp = null
                    )
            }
        }
    }

    private suspend fun importCardFusions(
        cardId: Long,
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>
    ) {
        val routes = JogressImportReader.read(card)
        val characters = database.characterDao().getCharactersForCard(cardId).associateBy { it.charaIndex }
        database.withTransaction {
            val dao = database.cardFusionsDao()
            dao.deleteAttributeRoutesForCard(cardId)
            dao.deleteSpecificRoutesForCard(cardId)
            routes.attributes.forEach { dao.insertNewFusion(cardId, it.fromIndex, it.attribute, it.toIndex) }
            routes.specific.forEach { dao.insertSpecificJogress(CardSpecificJogress(
                characters.getValue(it.fromIndex).id, characters.getValue(it.toIndex).id,
                it.partnerCardNumber, it.partnerCharaIndex)) }
        }
    }

    private suspend fun importEvoData(
        cardId: Long,
        card: com.github.cfogrady.vb.dim.card.Card<*, *, *, *, *, *>
    ) {
        database.characterDao().deleteTransformationsForCard(cardId)
        for (index in 0 until card.transformationRequirements.transformationEntries.size) {
            val evo = card.transformationRequirements.transformationEntries[index]

            var transformationTimerHours: Int
            var unlockAdventureLevel: Int

            if (card is BemCard) {
                transformationTimerHours = card
                    .transformationRequirements
                    .transformationEntries[index]
                    .minutesUntilTransformation / 60
                unlockAdventureLevel = if (
                    card
                        .transformationRequirements
                        .transformationEntries[index]
                        .requiredCompletedAdventureLevel == 65535
                ) {
                    -1
                } else {
                    card
                        .transformationRequirements
                        .transformationEntries[index]
                        .requiredCompletedAdventureLevel
                }
            } else {
                transformationTimerHours = (card as DimCard)
                    .transformationRequirements
                    .transformationEntries[index]
                    .hoursUntilEvolution
                unlockAdventureLevel = if (
                    card
                        .adventureLevels
                        .levels
                        .lastOrNull()
                        ?.bossCharacterIndex == card.transformationRequirements.transformationEntries[index].toCharacterIndex
                ) {
                    14
                    /*
                    Magic number incoming!!

                    In the case of DiMCards, stage 15 is the one that unlocks the locked character.
                    We know it is a locked character if the last adventure level's boss character index
                    is the current index. If it is, we add stage 15 complete as a requirement for transformation.
                     */
                } else {
                    -1
                    /*
                    -1 means no adventure requirement; zero is a valid BEM adventure index.
                     */
                }
            }

            database
                .characterDao()
                .insertPossibleTransformation(
                    cardId = cardId,
                    fromChraraIndex = evo.fromCharacterIndex,
                    toChraraIndex = evo.toCharacterIndex,
                    requiredVitals = evo.requiredVitalValues,
                    requiredTrophies = evo.requiredTrophies,
                    requiredBattles = evo.requiredBattles,
                    requiredWinRate = evo.requiredWinRatio,
                    requiredAdventureLevelCompleted = unlockAdventureLevel,
                    changeTimerHours = transformationTimerHours
                )
        }
    }
}
