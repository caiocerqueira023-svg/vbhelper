package com.tamerslab.domain.usecase

import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.offline.tamers.ArenaMatchEntity
import com.tamerslab.battle.offline.tamers.ArenaMatchSpec
import com.tamerslab.battle.offline.tamers.ArenaRunEntity
import com.tamerslab.battle.offline.tamers.ArenaSettlement
import com.tamerslab.battle.offline.tamers.ArenaTamerRecord
import com.tamerslab.battle.offline.tamers.ArenaTournament
import com.tamerslab.battle.offline.tamers.TamerTeamResolver
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.digifarm.Farm
import com.tamerslab.domain.repository.BattleRepository
import com.tamerslab.domain.repository.ChatGateway
import com.tamerslab.domain.repository.FarmGateway
import com.tamerslab.domain.repository.SaveImportRepository
import com.tamerslab.domain.repository.TransferRepository
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.SavedImportPreview
import kotlinx.coroutines.flow.Flow

/**
 * Transfer feature boundary: lists linked save sources and refreshes their observations.
 * Exposes domain-facing save views, not Room entities or DAOs.
 */
class TransferUseCase(private val repository: TransferRepository) {
    fun sources(): Flow<List<LinkedSaveView>> = repository.sources()
    suspend fun refresh(): UseCaseResult<Unit> = useCaseResult { repository.refreshAll() }
}

/**
 * Battle feature boundary for the arena/roster flows. The offline simulator stays in
 * `battle/offline`; this use case only exposes persisted arena state and settlement
 * ownership to the UI layer.
 */
class BattleUseCase(private val repository: BattleRepository) {
    val runs: Flow<List<ArenaRunEntity>> get() = repository.runs
    val records: Flow<List<ArenaTamerRecord>> get() = repository.records
    val recentMatches: Flow<List<ArenaMatchEntity>> get() = repository.recentMatches

    fun decodeRun(row: ArenaRunEntity): ArenaTournament = repository.decodeRun(row)
    suspend fun registerMatch(spec: ArenaMatchSpec): ArenaMatchSpec = repository.registerMatch(spec)
    suspend fun registerTournament(run: ArenaTournament) = repository.registerTournament(run)
    suspend fun withdraw(id: String) = repository.withdraw(id)
    suspend fun refreshLegacyTournament(id: String, resolver: TamerTeamResolver) =
        repository.refreshLegacyTournament(id, resolver)
    suspend fun nextPlayerMatch(runId: String): ArenaMatchSpec? = repository.nextPlayerMatch(runId)
    suspend fun recordTerminal(matchId: String, snapshot: BattleSnapshot): UseCaseResult<ArenaSettlement> =
        useCaseResult { repository.recordTerminal(matchId, snapshot) }

    /** Returns the visible-battle lease while a settlement is being recorded, if any. */
    fun settlementLease(): AutoCloseable? = repository.settlementLease()
}

/** Save-import feature boundary: prepares and reloads a persisted import preview. */
class SaveImportUseCase(private val repository: SaveImportRepository) {
    suspend fun prepare(sourceId: String, expectedRevision: String, selectedKeys: List<String>): UseCaseResult<SavedImportPreview> =
        useCaseResult { repository.prepare(sourceId, expectedRevision, selectedKeys) }
    suspend fun load(id: String): UseCaseResult<SavedImportPreview?> = useCaseResult { repository.load(id) }
}

/** Chat feature boundary: history stream plus a typed send outcome. */
class ChatUseCase(private val repository: ChatGateway) {
    fun history(characterId: Long): Flow<List<ChatMessageEntity>> = repository.history(characterId)
    suspend fun send(characterId: Long, text: String): UseCaseResult<String> =
        useCaseResult { repository.send(characterId, text) }
}

/** Farm feature boundary: the farms visible to the farm UI. */
class FarmUseCase(private val repository: FarmGateway) {
    fun farms(): Flow<List<Farm>> = repository.farms()
}
