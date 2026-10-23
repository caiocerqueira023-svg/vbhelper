package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.dtos.CardDtos
import kotlinx.coroutines.flow.Flow

class CardRepository (
    private val db: AppDatabase
) {
    fun getCardIconByCharaId(charaId: Long): Flow<CardDtos.CardIcon> {
        return db
            .cardDao()
            .getCardIconByCharaId(charaId = charaId)
    }

    /** Catalog definitions carry their own artwork instead of a card logo. */
    fun getDefinitionIconByCharaId(charaId: Long): Flow<CardDtos.CardIcon?> {
        return db.characterDao().getDefinitionIconByCharaId(charaId)
    }
}