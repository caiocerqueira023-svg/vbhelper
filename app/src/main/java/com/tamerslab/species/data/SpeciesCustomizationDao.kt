package com.tamerslab.species.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.species.LoadedSpriteChoice
import com.tamerslab.domain.species.SpeciesCatalogEntry
import com.tamerslab.domain.species.SpeciesSpriteChoice
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesCustomizationDao {
    @Query("SELECT * FROM SpeciesCatalogEntry ORDER BY name COLLATE NOCASE")
    fun observeCatalog(): Flow<List<SpeciesCatalogEntry>>

    @Query("SELECT * FROM SpeciesCatalogEntry ORDER BY name COLLATE NOCASE")
    suspend fun getCatalog(): List<SpeciesCatalogEntry>

    @Query("SELECT * FROM SpeciesCatalogEntry WHERE speciesKey = :key OR originalKey = :key LIMIT 1")
    suspend fun getEntry(key: String): SpeciesCatalogEntry?

    @Upsert suspend fun upsertEntry(entry: SpeciesCatalogEntry)

    @Query("DELETE FROM SpeciesCatalogEntry WHERE speciesKey = :key")
    suspend fun deleteEntry(key: String)

    @Upsert suspend fun upsertSpriteChoice(choice: SpeciesSpriteChoice)

    @Query("DELETE FROM SpeciesSpriteChoice WHERE speciesKey = :key")
    suspend fun clearSpriteChoice(key: String)

    @Query("""SELECT c.spriteId FROM SpeciesSpriteChoice c LEFT JOIN SpeciesCatalogEntry e ON e.speciesKey = c.speciesKey
        WHERE c.speciesKey = :key OR e.originalKey = :key LIMIT 1""")
    fun observeSpriteId(key: String): Flow<Int?>

    @Query("""SELECT c.spriteId FROM SpeciesSpriteChoice c LEFT JOIN SpeciesCatalogEntry e ON e.speciesKey = c.speciesKey
        WHERE c.speciesKey = :key OR e.originalKey = :key LIMIT 1""")
    suspend fun getSpriteId(key: String): Int?

    @Query("""SELECT s.* FROM Sprite s JOIN SpeciesSpriteChoice c ON c.spriteId = s.id
        LEFT JOIN SpeciesCatalogEntry e ON e.speciesKey = c.speciesKey
        WHERE c.speciesKey = :key OR e.originalKey = :key LIMIT 1""")
    suspend fun getChosenSprite(key: String): Sprite?

    @Query("SELECT * FROM SpeciesSpriteChoice")
    suspend fun getSpriteChoices(): List<SpeciesSpriteChoice>

    @Query("SELECT * FROM SpeciesSpriteChoice")
    fun observeSpriteChoices(): Flow<List<SpeciesSpriteChoice>>

    @Query("""SELECT s.id AS spriteId, COALESCE(cc.id, -s.id) AS cardCharacterId, COALESCE(card.name, '') AS cardName,
        COALESCE(p.speciesName, p.matchedName) AS speciesName, COALESCE(cc.charaIndex, -1) AS charaIndex
        FROM Sprite s LEFT JOIN CardCharacter cc ON cc.spriteId = s.id
        LEFT JOIN Card card ON card.id = cc.cardId
        LEFT JOIN SpeciesProfile p ON p.cardCharacterId = cc.id
        WHERE (:matchedOnly = 0 OR p.appearanceKey = :speciesKey)
        AND s.width BETWEEN 1 AND 256 AND s.height BETWEEN 1 AND 256
        AND length(s.spriteIdle1) = s.width * s.height * 2
        AND (:query = '' OR instr(lower(card.name), lower(:query)) > 0
            OR instr(lower(COALESCE(p.speciesName, p.matchedName, '')), lower(:query)) > 0
            OR instr(CAST(s.id AS TEXT), :query) > 0)
        ORDER BY card.name COLLATE NOCASE, cc.charaIndex, cc.id LIMIT :limit OFFSET :offset""")
    suspend fun getSpriteChoices(speciesKey: String, matchedOnly: Boolean, query: String, offset: Int, limit: Int): List<LoadedSpriteChoice>
}
