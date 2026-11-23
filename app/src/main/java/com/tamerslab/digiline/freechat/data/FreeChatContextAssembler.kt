package com.tamerslab.digiline.freechat.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.data.DigilineStages
import com.tamerslab.digiline.engine.PersonalityShiftEngine
import com.tamerslab.digiline.freechat.model.FreeChatAudience
import com.tamerslab.digiline.freechat.model.FreeChatContext
import com.tamerslab.species.SpeciesRepository

/**
 * Builds one [FreeChatContext] for an owned partner from cheap, already-materialized rows
 * (contract §3).
 *
 * Allowed reads only:
 * - `userCharacterDao().getByIndividualId` (mood, `charId`),
 * - `digimonIndividualDao().getIndividual` / `getPersonality` (species fallback, 16-type personality),
 * - `speciesRepository.getProfileForCharacter` (one indexed `SpeciesProfile` row; no catalog parse),
 * - `freeChatDao().profile` (the persisted *free-chat* turn counter; kept separate from
 *   `DigilineProfile` per plan §8 so card determinism is untouched) and `recentTurns` (last replies).
 *
 * Species key comes from [DigilineStages.speciesKey], stage tier from [DigilineStages.tier] and the
 * core name from [PersonalityShiftEngine.coreNameOf] (the `coreOf` index 1..4 mapped to the authored
 * `PHILANTHROPY|VALOR|WISDOM|AMICABILITY` casing).
 *
 * Never `getAllSpeciesEntries()`, never the 13.3 MB catalog re-parse, never network. The assembler
 * returns `null` for wild contacts or characters without an app row so the repository can fail open.
 */
class FreeChatContextAssembler(
    private val database: AppDatabase,
    private val speciesRepository: SpeciesRepository,
) {

    suspend fun assemble(individualId: String, contentVersion: Int): FreeChatContext? {
        val user = database.userCharacterDao().getByIndividualId(individualId).firstOrNull()
            ?: return null
        val species = runCatching { speciesRepository.getProfileForCharacter(user.charId) }.getOrNull()
        val individual = database.digimonIndividualDao().getIndividual(individualId)
        val traits = database.digimonIndividualDao().getPersonality(individualId)
        val personalityType = traits?.personalityType?.number ?: DEFAULT_PERSONALITY_TYPE
        val speciesName = species?.speciesName?.takeIf { it.isNotBlank() }
            ?: species?.matchedName?.takeIf { it.isNotBlank() }
            ?: individual?.speciesName?.takeIf { it.isNotBlank() }
        val freeChatProfile = database.freeChatDao().profile(individualId)
        val recentReplyIds = database.freeChatDao()
            .recentTurns(individualId, RECENT_TURN_WINDOW)
            .mapTo(mutableSetOf()) { it.replyId }
        return FreeChatContext(
            individualId = individualId,
            locale = DEFAULT_LOCALE,
            speciesKey = DigilineStages.speciesKey(speciesName),
            speciesName = speciesName,
            level = species?.level,
            speciesType = species?.type,
            stageTier = DigilineStages.tier(null, species?.level),
            personalityType = personalityType,
            core = PersonalityShiftEngine.coreNameOf(personalityType),
            mood = user.mood.coerceIn(MIN_MOOD, MAX_MOOD),
            audience = FreeChatAudience.OWNED,
            recentEventKeys = emptySet(),
            recentReplyIds = recentReplyIds,
            turnCounter = freeChatProfile?.turnCounter ?: 0L,
            contentVersion = contentVersion,
        )
    }

    companion object {
        private const val DEFAULT_LOCALE = "en"
        private const val DEFAULT_PERSONALITY_TYPE = 1
        private const val MIN_MOOD = 0
        private const val MAX_MOOD = 100

        /** Contract §3: `recentReplyIds` comes from the last 8 persisted turns. */
        const val RECENT_TURN_WINDOW: Int = 8
    }
}
