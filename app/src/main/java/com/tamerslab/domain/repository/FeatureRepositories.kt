package com.tamerslab.domain.repository

import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.offline.data.BattleParticipantProfile
import com.tamerslab.battle.offline.tamers.ArenaMatchEntity
import com.tamerslab.battle.offline.tamers.ArenaMatchSpec
import com.tamerslab.battle.offline.tamers.ArenaRunEntity
import com.tamerslab.battle.offline.tamers.ArenaSettlement
import com.tamerslab.battle.offline.tamers.ArenaTamerRecord
import com.tamerslab.battle.offline.tamers.ArenaTournament
import com.tamerslab.battle.offline.tamers.TamerTeamResolver
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.digifarm.Farm
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.SavedImportPreview
import kotlinx.coroutines.flow.Flow

/**
 * Transfer feature repository: observed game-save sources for the transfer UI.
 * Implementations own the Room/file access; this interface only exposes domain-facing
 * save views.
 */
interface TransferRepository {
    fun sources(): Flow<List<LinkedSaveView>>
    suspend fun refreshAll()
}

/**
 * Battle feature repository: persisted arena runs, legacy records and terminal
 * settlement ownership. The simulation itself lives in `battle/offline`; this interface
 * is the persistence boundary used by [com.tamerslab.domain.usecase.BattleUseCase].
 */
interface BattleRepository {
    val runs: Flow<List<ArenaRunEntity>>
    val records: Flow<List<ArenaTamerRecord>>
    val recentMatches: Flow<List<ArenaMatchEntity>>

    fun decodeRun(row: ArenaRunEntity): ArenaTournament
    suspend fun registerMatch(spec: ArenaMatchSpec): ArenaMatchSpec
    suspend fun registerTournament(run: ArenaTournament)
    suspend fun withdraw(id: String)
    suspend fun refreshLegacyTournament(id: String, resolver: TamerTeamResolver)
    suspend fun nextPlayerMatch(runId: String): ArenaMatchSpec?
    suspend fun recordTerminal(matchId: String, snapshot: BattleSnapshot): ArenaSettlement
    fun settlementLease(): AutoCloseable?
}

/** Save-import repository: prepares a destination preview and reloads a saved one. */
interface SaveImportRepository {
    suspend fun prepare(sourceId: String, expectedRevision: String, selectedKeys: List<String>): SavedImportPreview
    suspend fun load(id: String): SavedImportPreview?
}

/** Chat feature gateway: persisted message history and message sending. */
interface ChatGateway {
    fun history(characterId: Long): Flow<List<ChatMessageEntity>>
    suspend fun send(characterId: Long, text: String): String
}

/** Farm feature gateway: the farms visible to the farm UI. */
interface FarmGateway {
    fun farms(): Flow<List<Farm>>
}

/** Storage feature gateway: stored characters with their sprites. */
interface StorageGateway {
    fun characters(): Flow<List<CharacterDtos.CharacterWithSprites>>
}

/** A single equipped arena technique slot for one individual. */
data class ArenaTechnique(val individualId: String, val slot: Int, val techniqueId: String)

/** Everything the arena needs for one match: characters, techniques, partners, partner id. */
data class ArenaRoster(
    val characters: Map<Long, CharacterDtos.CharacterWithSprites>,
    val techniques: Map<String, List<ArenaTechnique>>,
    val partners: List<BattleParticipantProfile>,
    val activeCharacterId: Long?,
)

/** Arena roster gateway: invalidations plus the current roster/species index. */
interface ArenaRosterGateway {
    val invalidation: Flow<Unit>
    suspend fun load(): ArenaRoster
    suspend fun speciesIndex(): List<com.tamerslab.battle.offline.tamers.ArenaSpecies>
}
