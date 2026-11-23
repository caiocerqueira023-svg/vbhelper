package com.tamerslab.species.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ScanRepository(
    val database: AppDatabase
) {
    /**
     * Converted partners show their exact target card; everyone else keeps the legacy path.
     * A card-independent definition may legitimately have no card row at all, so this resolves
     * to a neutral empty card instead of collecting a single-result query that can be empty.
     *
     * The screens collect this from Compose, so the builder must never touch the database on the
     * caller's (main) dispatcher: `CardDao.getCardById`/`getCardByCharacterIdSync` are blocking
     * methods and Room rejects them off a worker thread with
     * "Cannot access database on the main thread". This only ran for a partner with an export
     * target (an assigned DIM/BEM), which is why watch-origin partners never hit it.
     */
    fun getCardDetails(characterId: Long): Flow<Card> = flow {
        // The transfer deletes the source row when the watch confirms the write. The writing
        // screens may still have this flow collected during the completion recomposition, so a
        // missing row is a valid state and must resolve to the neutral card instead of throwing
        // from a single-row Room query (which was crashing the app right after a successful send).
        val userCharacter = database.userCharacterDao().getCharacterSync(characterId)
        val card = if (userCharacter == null) {
            EMPTY_CARD
        } else {
            val assignedCard = userCharacter.exportCardCharacterId
                ?.let { database.characterDao().getById(it) }
                ?.cardId?.let { database.cardDao().getCardById(it) }
            assignedCard ?: database.cardDao().getCardByCharacterIdSync(characterId) ?: EMPTY_CARD
        }
        emit(card)
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** Same neutral value the writing screens already use before their first emission. */
        val EMPTY_CARD = Card(
            id = 0,
            cardId = 0,
            name = "",
            logo = byteArrayOf(),
            logoHeight = 0,
            logoWidth = 0,
            stageCount = 0,
            isBEm = false
        )
    }
}
