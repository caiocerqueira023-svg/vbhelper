package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.CharacterCapabilities
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter

/**
 * Loads the [CharacterCapabilities] for a stored partner from its definition and export target.
 * Callers use this instead of branching on the stored [com.github.nacabaro.vbhelper.utils.DeviceType]
 * when the real question is "can this reach a watch / does it need a DIM".
 */
class CharacterCapabilityResolver(private val db: AppDatabase) {
    suspend fun resolve(characterId: Long): CharacterCapabilities? {
        val character = db.userCharacterDao().getCharacter(characterId) ?: return null
        return resolve(character)
    }

    suspend fun resolve(character: UserCharacter): CharacterCapabilities {
        val definition = db.characterDao().getById(character.charId)
        val exportTarget = character.exportCardCharacterId?.let { db.characterDao().getById(it) }
        return CharacterCapabilities(
            definition = definition,
            exportTarget = exportTarget,
            exportCardIsBem = exportTarget?.cardId?.let { db.cardDao().getCardById(it)?.isBEm } ?: false,
            definitionCardIsBem = definition?.cardId?.let { db.cardDao().getCardById(it)?.isBEm } ?: false,
        )
    }
}
