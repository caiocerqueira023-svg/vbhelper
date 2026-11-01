package com.tamerslab.species.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.Card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class ScanRepository(
    val database: AppDatabase
) {
    /** Converted partners show their exact target card; everyone else keeps the legacy path. */
    fun getCardDetails(characterId: Long): Flow<Card> = flow {
        val userCharacter = database.userCharacterDao().getCharacter(characterId)
        val targetId = userCharacter.exportCardCharacterId
        val targetCard = targetId
            ?.let { database.characterDao().getById(it) }
            ?.cardId?.let { database.cardDao().getCardById(it) }
        if (targetCard != null) emit(targetCard)
        else emitAll(database.cardDao().getCardByCharacterId(characterId))
    }
}