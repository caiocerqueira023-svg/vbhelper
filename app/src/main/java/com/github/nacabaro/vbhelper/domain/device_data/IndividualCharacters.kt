package com.github.nacabaro.vbhelper.domain.device_data

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.utils.DeviceType

/**
 * The representative storage row for an individual.
 *
 * A materially represented individual normally has exactly one [UserCharacter]. When more than one
 * exists (a leftover device row beside an app partner), the primary app partner wins so callers do
 * not each reimplement the same `firstOrNull { characterType == APP }` guess.
 */
object IndividualCharacters {
    fun representative(characters: List<UserCharacter>): UserCharacter? =
        characters.firstOrNull { it.characterType == DeviceType.APP } ?: characters.firstOrNull()

    suspend fun representative(db: AppDatabase, individualId: String): UserCharacter? =
        representative(db.userCharacterDao().getByIndividualIdSync(individualId))
}
