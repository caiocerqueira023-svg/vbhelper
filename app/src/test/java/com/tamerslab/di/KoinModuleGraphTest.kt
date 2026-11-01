package com.tamerslab.di

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
import com.tamerslab.domain.usecase.BattleUseCase
import com.tamerslab.domain.usecase.ChatUseCase
import com.tamerslab.domain.usecase.FarmUseCase
import com.tamerslab.domain.usecase.SaveImportUseCase
import com.tamerslab.domain.usecase.TransferUseCase
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.SavedImportPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class KoinModuleGraphTest {
    @Test
    fun useCaseGraphResolvesWithFeatureRepositories() {
        val fakes = module {
            single<TransferRepository> { FakeTransferRepository() }
            single<BattleRepository> { FakeBattleRepository() }
            single<SaveImportRepository> { FakeSaveImportRepository() }
            single<ChatGateway> { FakeChatGateway() }
            single<FarmGateway> { FakeFarmGateway() }
        }
        val application = koinApplication { modules(fakes, useCaseModule) }
        assertNotNull(application.koin.get<TransferUseCase>())
        assertNotNull(application.koin.get<BattleUseCase>())
        assertNotNull(application.koin.get<SaveImportUseCase>())
        assertNotNull(application.koin.get<ChatUseCase>())
        assertNotNull(application.koin.get<FarmUseCase>())
    }

    private class FakeTransferRepository : TransferRepository {
        override fun sources(): Flow<List<LinkedSaveView>> = emptyFlow()
        override suspend fun refreshAll() = Unit
    }

    private class FakeBattleRepository : BattleRepository {
        override val runs: Flow<List<ArenaRunEntity>> = emptyFlow()
        override val records: Flow<List<ArenaTamerRecord>> = emptyFlow()
        override val recentMatches: Flow<List<ArenaMatchEntity>> = emptyFlow()
        override fun decodeRun(row: ArenaRunEntity): ArenaTournament = error("unused")
        override suspend fun registerMatch(spec: ArenaMatchSpec): ArenaMatchSpec = spec
        override suspend fun registerTournament(run: ArenaTournament) = Unit
        override suspend fun withdraw(id: String) = Unit
        override suspend fun refreshLegacyTournament(id: String, resolver: TamerTeamResolver) = Unit
        override suspend fun nextPlayerMatch(runId: String): ArenaMatchSpec? = null
        override suspend fun recordTerminal(matchId: String, snapshot: BattleSnapshot): ArenaSettlement =
            error("unused")
        override fun settlementLease(): AutoCloseable? = null
    }

    private class FakeSaveImportRepository : SaveImportRepository {
        override suspend fun prepare(sourceId: String, expectedRevision: String, selectedKeys: List<String>): SavedImportPreview =
            error("unused")
        override suspend fun load(id: String): SavedImportPreview? = null
    }

    private class FakeChatGateway : ChatGateway {
        override fun history(characterId: Long): Flow<List<ChatMessageEntity>> = emptyFlow()
        override suspend fun send(characterId: Long, text: String): String = ""
    }

    private class FakeFarmGateway : FarmGateway {
        override fun farms(): Flow<List<Farm>> = emptyFlow()
    }
}
