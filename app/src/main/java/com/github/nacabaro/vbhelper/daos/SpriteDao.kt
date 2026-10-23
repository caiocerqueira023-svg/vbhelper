package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.github.nacabaro.vbhelper.domain.characters.Sprite

@Dao
interface SpriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSprite(sprite: Sprite): Long

    /** Bundled artwork uses reserved IDs; never REPLACE a row and cascade into card data. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBundledSprite(sprite: Sprite): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBundledSprites(sprites: List<Sprite>)

    @Query("SELECT * FROM Sprite WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<Sprite>

    @Query("SELECT id FROM Sprite WHERE id < -1")
    suspend fun getBundledSpriteIds(): List<Int>

    @Query("""SELECT cardCharacterId FROM DisplaySprite WHERE width BETWEEN 1 AND 256 AND height BETWEEN 1 AND 256
        AND length(spriteIdle1) = width * height * 2 AND length(spriteAttack) = width * height * 2""")
    suspend fun getRenderableCharacterIds(): List<Long>

    @Query("SELECT * FROM Sprite")
    suspend fun getAllSprites(): List<Sprite>

    @Query("SELECT COUNT(*) FROM Sprite")
    suspend fun countSprites(): Int

    @Query("""SELECT COUNT(*) FROM Sprite WHERE width BETWEEN 1 AND :maxDimension
        AND height BETWEEN 1 AND :maxDimension
        AND length(spriteIdle1) <= :maxBytes AND length(spriteIdle2) <= :maxBytes
        AND length(spriteWalk1) <= :maxBytes AND length(spriteWalk2) <= :maxBytes
        AND length(spriteRun1) <= :maxBytes AND length(spriteRun2) <= :maxBytes
        AND length(spriteTrain1) <= :maxBytes AND length(spriteTrain2) <= :maxBytes
        AND length(spriteHappy) <= :maxBytes AND length(spriteSleep) <= :maxBytes
        AND length(spriteAttack) <= :maxBytes AND length(spriteDodge) <= :maxBytes""")
    suspend fun countPreviewSprites(maxDimension: Int, maxBytes: Int): Int

    @Query("""SELECT * FROM Sprite WHERE width BETWEEN 1 AND :maxDimension
        AND height BETWEEN 1 AND :maxDimension
        AND length(spriteIdle1) <= :maxBytes AND length(spriteIdle2) <= :maxBytes
        AND length(spriteWalk1) <= :maxBytes AND length(spriteWalk2) <= :maxBytes
        AND length(spriteRun1) <= :maxBytes AND length(spriteRun2) <= :maxBytes
        AND length(spriteTrain1) <= :maxBytes AND length(spriteTrain2) <= :maxBytes
        AND length(spriteHappy) <= :maxBytes AND length(spriteSleep) <= :maxBytes
        AND length(spriteAttack) <= :maxBytes AND length(spriteDodge) <= :maxBytes
        ORDER BY id LIMIT :limit OFFSET :offset""")
    suspend fun getPreviewPage(offset: Int, limit: Int, maxDimension: Int, maxBytes: Int): List<Sprite>

    @Query("SELECT s.* FROM Sprite s JOIN CardCharacter c ON c.spriteId = s.id WHERE c.id = :characterId LIMIT 1")
    suspend fun getOriginalForCharacter(characterId: Long): Sprite?

    @Query("""SELECT s.* FROM Sprite s JOIN CardCharacter c ON c.spriteId = s.id
        JOIN Card card ON card.id = c.cardId LEFT JOIN SpeciesProfile p ON p.cardCharacterId = c.id
        LEFT JOIN SpeciesCatalogEntry custom ON custom.speciesKey = p.appearanceKey OR custom.originalKey = p.appearanceKey
        WHERE c.speciesId = :speciesId OR p.appearanceKey IN (:keys) OR custom.originalKey IN (:keys)
        ORDER BY card.isBEm DESC, c.id LIMIT 1""")
    suspend fun getOriginalForSpecies(speciesId: String?, keys: List<String>): Sprite?

    @Query("SELECT * FROM Sprite WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Sprite?

    @Query("SELECT * FROM DisplaySprite WHERE cardCharacterId = :characterId LIMIT 1")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getForCharacter(characterId: Long): Sprite?
}
