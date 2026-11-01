package com.tamerslab.di.repository

import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.offline.tamers.ArenaMatchEntity
import com.tamerslab.battle.offline.tamers.ArenaMatchSpec
import com.tamerslab.battle.offline.tamers.ArenaRepository
import com.tamerslab.battle.offline.tamers.ArenaRunEntity
import com.tamerslab.battle.offline.tamers.ArenaSettlement
import com.tamerslab.battle.offline.tamers.ArenaTamerRecord
import com.tamerslab.battle.offline.tamers.ArenaTournament
import com.tamerslab.battle.offline.tamers.TamerTeamResolver
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.di.DefaultAppContainer
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.digifarm.Farm
import com.tamerslab.domain.repository.ArenaRoster
import com.tamerslab.domain.repository.ArenaRosterGateway
import com.tamerslab.domain.repository.ArenaTechnique
import com.tamerslab.domain.repository.BattleRepository
import com.tamerslab.domain.repository.ChatGateway
import com.tamerslab.domain.repository.FarmGateway
import com.tamerslab.domain.repository.SaveImportRepository
import com.tamerslab.domain.repository.StorageGateway
import com.tamerslab.domain.repository.TransferRepository
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.SavedImportPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ContainerTransferRepository(private val container: DefaultAppContainer) : TransferRepository {
    override fun sources(): Flow<List<LinkedSaveView>> = container.linkedGameSaves.sources
    override suspend fun refreshAll() = container.linkedGameSaves.refreshAll()
}

class ArenaBattleRepository(
    private val db: AppDatabase,
    currency: CurrencyRepository,
) : BattleRepository {
    private val delegate = ArenaRepository(db, currency)
    override val runs: Flow<List<ArenaRunEntity>> = delegate.runs
    override val records: Flow<List<ArenaTamerRecord>> = delegate.records
    override val recentMatches: Flow<List<ArenaMatchEntity>> = delegate.recentMatches
    override fun decodeRun(row: ArenaRunEntity): ArenaTournament = delegate.decodeRun(row)
    override suspend fun registerMatch(spec: ArenaMatchSpec): ArenaMatchSpec = delegate.registerMatch(spec)
    override suspend fun registerTournament(run: ArenaTournament) = delegate.registerTournament(run)
    override suspend fun withdraw(id: String) {
        delegate.withdraw(id)
    }
    override suspend fun refreshLegacyTournament(id: String, resolver: TamerTeamResolver) =
        delegate.refreshLegacyTournament(id, resolver)
    override suspend fun nextPlayerMatch(runId: String): ArenaMatchSpec? = delegate.nextPlayerMatch(runId)
    override suspend fun recordTerminal(matchId: String, snapshot: BattleSnapshot): ArenaSettlement =
        delegate.recordTerminal(matchId, snapshot)
    override fun settlementLease(): AutoCloseable? = db.participation?.battleLease()
}

class ContainerSaveImportRepository(private val container: DefaultAppContainer) : SaveImportRepository {
    override suspend fun prepare(sourceId: String, expectedRevision: String, selectedKeys: List<String>): SavedImportPreview =
        container.saveImportPreviews.prepare(sourceId, expectedRevision, selectedKeys)
    override suspend fun load(id: String): SavedImportPreview? = container.saveImportPreviews.load(id)
}

class ContainerChatGateway(private val container: DefaultAppContainer) : ChatGateway {
    override fun history(characterId: Long): Flow<List<ChatMessageEntity>> =
        container.chatRepository.getHistory(characterId)
    override suspend fun send(characterId: Long, text: String): String =
        container.chatRepository.sendMessage(characterId, text)
}

class ContainerFarmGateway(private val container: DefaultAppContainer) : FarmGateway {
    override fun farms(): Flow<List<Farm>> = container.digifarmRepository.observeFarms()
}

class ContainerStorageGateway(private val container: DefaultAppContainer) : StorageGateway {
    private val delegate = StorageRepository(container.db)
    override fun characters(): Flow<List<CharacterDtos.CharacterWithSprites>> = delegate.getAllCharacters()
}

class RoomArenaRosterGateway(
    private val db: AppDatabase,
    private val context: android.content.Context,
) : ArenaRosterGateway {
    override val invalidation: Flow<Unit> = db.invalidationTracker.createFlow(
        "Card", "CardCharacter", "UserCharacter", "SpeciesProfile", "SpeciesCatalogEntry",
        "SpeciesSpriteChoice", "SpeciesDefaultSprite", "AppSourceState").map { }

    override suspend fun load(): ArenaRoster {
        db.expandedCatalog?.ensureReadyDefinitions()
        val eligibility = db.participation?.snapshot()
        val characters = db.userCharacterDao().getAllCharacters().first().associateBy { it.id }
        val techniques = db.digimonTechniqueLoadoutDao().getAll()
            .map { ArenaTechnique(it.individualId, it.slot, it.techniqueId) }
            .groupBy { it.individualId }
        val partners = db.userCharacterDao().getAllBattleParticipantProfiles()
            .filter { eligibility?.owned(it.sourceCharacterId)?.usable != false }
        val active = db.userCharacterDao().getActiveCharacter().first()?.id
        return ArenaRoster(characters, techniques, partners, active)
    }

    override suspend fun speciesIndex(): List<com.tamerslab.battle.offline.tamers.ArenaSpecies> =
        com.tamerslab.battle.offline.tamers.ArenaSpeciesIndex.load(context, db)
}
