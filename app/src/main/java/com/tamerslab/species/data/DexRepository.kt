package com.tamerslab.species.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.dtos.CardDtos
import com.tamerslab.dtos.CardEvolutionGraph
import com.tamerslab.dtos.CardSpecificJogressDetails
import com.tamerslab.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DexRepository (
    private val db: AppDatabase
) {
    fun getAllDims(): Flow<List<CardDtos.CardProgress>> {
        return db.dexDao().getCardsWithProgress()
    }

    fun getCharactersByCardId(cardId: Long): Flow<List<CharacterDtos.CardCharaProgress>> {
        return db.dexDao().getSingleCardProgress(cardId)
    }

    fun getCardEvolutionGraph(cardId: Long): Flow<CardEvolutionGraph> = combine(
        db.dexDao().getSingleCardProgress(cardId),
        db.dexDao().getCardEvolutionLinks(cardId),
    ) { characters, links ->
        val ids = characters.mapTo(hashSetOf()) { it.id }
        CardEvolutionGraph(cardId, characters, links.filter { it.fromId in ids && it.toId in ids })
    }

    fun getCharacterProgress(characterId: Long): Flow<CharacterDtos.CardCharaProgress?> {
        return db.dexDao().getCharacterProgress(characterId)
    }

    fun getCharacterPossibleTransformations(characterId: Long): Flow<List<CharacterDtos.EvolutionRequirementsWithSpritesAndObtained>> {
        return db.characterDao().getEvolutionRequirementsForCard(characterId)
    }

    fun getCharacterPossibleFusions(characterId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>> {
        return db.cardFusionsDao().getFusionsForCharacter(characterId)
    }

    fun getCharacterSpecificJogress(characterId: Long): Flow<List<CardSpecificJogressDetails>> =
        db.cardFusionsDao().getSpecificJogressForCharacter(characterId)
}
