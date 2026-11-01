package com.tamerslab.species.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.characters.Sprite
import kotlinx.coroutines.flow.Flow

class SpriteRepository(private val db: AppDatabase) {
    suspend fun count(): Int = db.spriteDao().countSprites()

    suspend fun countPreview(maxDimension: Int, maxBytes: Int): Int =
        db.spriteDao().countPreviewSprites(maxDimension, maxBytes)

    suspend fun previewPage(offset: Int, limit: Int, maxDimension: Int, maxBytes: Int): List<Sprite> =
        db.spriteDao().getPreviewPage(offset, limit, maxDimension, maxBytes)

    suspend fun forCharacter(cardCharacterId: Long): Sprite? =
        db.spriteDao().getForCharacter(cardCharacterId)

    fun observeCustomization(speciesKey: String): Flow<Int?> =
        db.speciesCustomizationDao().observeSpriteId(speciesKey)
}
