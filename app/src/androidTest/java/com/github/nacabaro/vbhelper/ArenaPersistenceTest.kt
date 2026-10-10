package com.github.nacabaro.vbhelper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArenaPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var currency: CurrencyRepository
    private lateinit var repository: ArenaRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val preferences = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(emptyPreferences())
        }
        currency = CurrencyRepository(preferences, db)
        repository = ArenaRepository(db, currency)
    }

    @After fun close() { db.close() }

    private fun entrant(id: String, player: Boolean) = ArenaEntrant(id, id, if (player) null else id,
        listOf(OfflineBattleParticipant(null, "fixture", "Agumon", 1, 1, stage = 2,
            speciesName = "Agumon", battleInstanceId = id)), TrainerAiPolicy())

    private fun spec() = ArenaMatchSpec("receipt-fixture", entrant(ArenaRepository.PLAYER_ID, true), entrant("taichi", false),
        ArenaDifficulty.NORMAL, BattleConfiguration(strictFinisherEligibility = true, itemCooldownMillis = 5_000))

    private fun victory(spec: ArenaMatchSpec): BattleSnapshot {
        fun members(entrant: ArenaEntrant, side: BattleSide, defeated: Boolean) = TamerBattleFactory.definitions(entrant, side).map { def ->
            CombatantSnapshot(def.combatantId, def.sourceCharacterId, def.externalCharacterId, def.displayName, side,
                if (defeated) 0 else def.maxHealth, def.maxHealth, def.maxEnergy, def.maxEnergy,
                BattlePosition(0f, 0f), null, if (defeated) CombatantState.DEFEATED else CombatantState.IDLE,
                def.strategy, null, emptyList(), emptyMap())
        }
        return BattleSnapshot(34, false, null, 0, 100, members(spec.left, BattleSide.ALLIED, false),
            members(spec.right, BattleSide.OPPOSING, true), emptySet(), BattleResult(BattleOutcome.ALLIED_VICTORY, 34, 1),
            emptyList(), opposingItems = TamerBattleFactory.items(spec.right.members).map {
                BattleItemSnapshot(it.itemId, it.displayName, it.quantity, 0)
            })
    }

    @Test fun concurrentResultSettlementCreditsOneRewardAndCreatesNoOwnedDigimon() = runBlocking {
        val spec = repository.registerMatch(spec())
        val snapshot = victory(spec)
        val first = async(Dispatchers.IO) { repository.recordTerminal(spec.id, snapshot) }
        val second = async(Dispatchers.IO) { repository.recordTerminal(spec.id, snapshot) }
        assertEquals(first.await(), second.await())
        assertEquals(10_700, currency.currencyValue.first())
        assertEquals(1, repository.records.first().single().victories)
        assertTrue(db.userCharacterDao().getAllCharacters().first().isEmpty())
        assertTrue(db.questDao().battleStock().isEmpty())
    }

    @Test fun walletFailureRollsBackResultAndReceiptSoSettlementCanBeRetried() = runBlocking {
        val spec = repository.registerMatch(spec())
        currency.setCurrencyValue(Int.MAX_VALUE)
        try {
            repository.recordTerminal(spec.id, victory(spec))
            fail("An overflowing credit must fail")
        } catch (_: IllegalStateException) { }
        assertNull(db.arenaDao().match(spec.id)!!.completedAt)
        currency.setCurrencyValue(0)
        val settlement = repository.recordTerminal(spec.id, victory(spec))
        assertEquals(700, settlement.rewardBits)
        assertEquals(700, currency.currencyValue.first())
    }
}
