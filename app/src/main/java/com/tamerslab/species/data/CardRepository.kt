package com.tamerslab.species.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.OfficialStatus
import com.tamerslab.dtos.CardDtos
import com.tamerslab.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow

class CardRepository(private val db: AppDatabase) {
    fun allCards() = db.cardDao().getAllCards()

    fun getCardIconByCharaId(charaId: Long): Flow<CardDtos.CardIcon> =
        db.cardDao().getCardIconByCharaId(charaId)

    fun getDefinitionIconByCharaId(charaId: Long): Flow<CardDtos.CardIcon?> =
        db.characterDao().getDefinitionIconByCharaId(charaId)

    suspend fun rename(cardId: Long, name: String) = db.cardDao().renameCard(cardId.toInt(), name)

    suspend fun delete(cardId: Long) = db.cardDao().deleteCard(cardId)

    fun adventures(cardId: Long): Flow<List<CardDtos.CardAdventureWithSprites>> =
        db.cardAdventureDao().getAdventureForCard(cardId)

    fun progress(cardId: Long): Flow<Int> = db.cardProgressDao().getCardProgress(cardId)

    fun fusions(characterId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>> =
        db.cardFusionsDao().getFusionsForCharacter(characterId)

    suspend fun setOfficialStatus(cardId: Long, status: OfficialStatus) =
        db.cardDao().updateOfficialStatus(cardId, status)
}
