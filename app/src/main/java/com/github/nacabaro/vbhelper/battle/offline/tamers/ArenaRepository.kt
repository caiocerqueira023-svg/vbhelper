package com.github.nacabaro.vbhelper.battle.offline.tamers

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/** Safe arena settlement writes records/brackets/wallet only, never owned Digimon or item stock. */
class ArenaRepository(private val db: AppDatabase, private val currency: CurrencyRepository) {
    private val gson = Gson()
    val runs = db.arenaDao().runs()
    val records = db.arenaDao().records()
    val recentMatches = db.arenaDao().recentMatches()

    fun decodeRun(row: ArenaRunEntity): ArenaTournament = gson.fromJson(row.stateJson, ArenaTournament::class.java).also {
        require(it.contentVersion == TamerCatalog.VERSION && it.aiVersion == 1) { "Unsupported tournament version" }
        require(it.canonRevision in 0..TamerCanonicalPartners.REVISION) { "Unsupported canonical partner policy" }
        require(it.entrants.size in setOf(8, 16) && it.format in 1..2)
        require(it.entrants.map { entrant -> entrant.id }.distinct().size == it.entrants.size)
        require(it.matches.map { match -> match.id }.distinct().size == it.matches.size)
    }

    fun decodeMatch(row: ArenaMatchEntity): ArenaMatchSpec = gson.fromJson(row.specJson, ArenaMatchSpec::class.java).also(TamerBattleFactory::validate)

    suspend fun registerMatch(spec: ArenaMatchSpec): ArenaMatchSpec = withContext(Dispatchers.IO) {
        TamerBattleFactory.validate(spec)
        require(spec.left.id == PLAYER_ID && spec.right.tamerId != null)
        val row = ArenaMatchEntity(spec.id, spec.tournamentId, requireNotNull(spec.right.tamerId), spec.left.members.size,
            spec.left.members.maxOf { it.stage }, spec.difficulty.name, gson.toJson(spec), null, 0, 0, 0,
            System.currentTimeMillis(), null)
        db.arenaDao().insertMatch(row)
        decodeMatch(requireNotNull(db.arenaDao().match(spec.id)))
    }

    suspend fun registerTournament(run: ArenaTournament) = withContext(Dispatchers.IO) {
        require(run.entrants.count { it.id == PLAYER_ID } == 1 && run.championId == null)
        check(db.arenaDao().insertRun(ArenaRunEntity(run.id, gson.toJson(run), 0, System.currentTimeMillis())) != -1L)
    }

    private suspend fun mutateRun(id: String, transform: (ArenaTournament) -> ArenaTournament): ArenaTournament = db.withTransaction {
        val row = requireNotNull(db.arenaDao().run(id)) { "Tournament not found" }
        val updated = transform(decodeRun(row))
        check(db.arenaDao().updateRun(id, row.revision, gson.toJson(updated), System.currentTimeMillis()) == 1)
        updated
    }

    suspend fun withdraw(id: String) = withContext(Dispatchers.IO) { mutateRun(id) { it.copy(withdrawn = true) } }

    suspend fun refreshLegacyTournament(id: String, resolver: TamerTeamResolver) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dao = db.arenaDao()
            val row = requireNotNull(dao.run(id))
            val previous = decodeRun(row)
            val updated = ArenaCanonicalMigration.refresh(previous, resolver)
            if (updated === previous) return@withTransaction
            val entries = updated.entrants.associateBy { it.id }
            for (pending in dao.pendingTournamentMatches(id)) {
                val old = decodeMatch(pending)
                fun corrected(entry: ArenaEntrant): ArenaEntrant {
                    if (entry.id == PLAYER_ID) return entry
                    val canonical = entries.getValue(entry.id)
                    return if (old.tiebreakRound == 0) canonical else canonical.copy(members = canonical.members.map {
                        it.copy(initialHealth = (it.trainingStats().health / 4).coerceAtLeast(1))
                    })
                }
                val fixed = old.copy(left = corrected(old.left), right = corrected(old.right),
                    resultSpecies = updated.resultSpecies, canonRevision = TamerCanonicalPartners.REVISION)
                check(dao.rewritePendingSpec(old.id, gson.toJson(fixed)) == 1)
            }
            check(dao.updateRun(id, row.revision, gson.toJson(updated), System.currentTimeMillis()) == 1)
        }
    }

    suspend fun recordTerminal(matchId: String, snapshot: BattleSnapshot): ArenaSettlement = withContext(Dispatchers.IO) {
        val result = requireNotNull(snapshot.result)
        currency.initializeWallet()
        db.withTransaction {
            val dao = db.arenaDao()
            val row = requireNotNull(dao.match(matchId)) { "Arena match not found" }
            val spec = decodeMatch(row)
            if (row.completedAt != null) return@withTransaction ArenaSettlement(BattleOutcome.valueOf(requireNotNull(row.outcome)), row.rewardBits,
                spec.tiebreakRound > 0 && result.outcome == BattleOutcome.DRAW)
            require(snapshot.rulesetVersion == spec.configuration.rulesetVersion && result.elapsedMillis == snapshot.elapsedMillis) {
                "Arena result does not match the registered match setup"
            }
            for ((members, entrant, side) in listOf(Triple(snapshot.alliedMembers, spec.left, BattleSide.ALLIED),
                Triple(snapshot.opposingMembers, spec.right, BattleSide.OPPOSING))) {
                val definitions = TamerBattleFactory.definitions(entrant, side).associateBy { it.combatantId }
                require(members.map { it.combatantId }.toSet() == definitions.keys && members.size == definitions.size) {
                    "Arena result combatants do not match the registered team"
                }
                require(members.all { it.maxHealth == definitions.getValue(it.combatantId).maxHealth && it.health in 0..it.maxHealth }) {
                    "Arena result health does not match the registered team"
                }
            }
            val outcome = if (result.outcome == BattleOutcome.DRAW && spec.tournamentId != null && spec.tiebreakRound > 0) {
                if (leftWinsTiebreak(snapshot, spec.configuration.randomSeed)) BattleOutcome.ALLIED_VICTORY else BattleOutcome.OPPOSING_VICTORY
            } else result.outcome
            val first = dao.victories(row.tamerId, row.format) == 0
            val reward = if (outcome == BattleOutcome.ALLIED_VICTORY)
                ArenaRewards.victoryBits(row.stage, row.format, spec.difficulty) + if (first) ArenaRewards.FIRST_CLEAR_BONUS else 0 else 0
            val now = System.currentTimeMillis()
            check(dao.completeMatch(matchId, outcome.name, reward, snapshot.statistics.itemsUsed,
                if (spec.tiebreakRound == 0) 5 - snapshot.opposingItems.sumOf { it.remaining } else 0, now) == 1)
            grantLocked(matchId, reward, now)
            if (spec.tournamentId != null) {
                val tournament = mutateRun(spec.tournamentId) { run ->
                    when (outcome) {
                        BattleOutcome.ABANDONED -> run.copy(withdrawn = true)
                        BattleOutcome.DRAW -> run
                        else -> run.finish(spec.bracketMatchId ?: spec.id,
                            if (outcome == BattleOutcome.ALLIED_VICTORY) spec.left.id else spec.right.id,
                            if (spec.tiebreakRound > 0) "${outcome.name}:TIEBREAK" else outcome.name).advanceRound()
                    }
                }
                grantTrophyLocked(tournament, now)
            }
            ArenaSettlement(outcome, reward, outcome != result.outcome)
        }
    }

    private suspend fun grantLocked(id: String, bits: Int, now: Long) {
        require(bits >= 0)
        if (db.arenaDao().insertReceipt(ArenaRewardReceipt(id, bits, now)) != -1L && bits > 0) {
            check(db.questDao().addBits(bits) == 1) { "The arena reward could not be credited." }
        }
    }

    private suspend fun grantTrophyLocked(run: ArenaTournament, now: Long) {
        if (run.championId == PLAYER_ID) grantLocked("tournament:${run.id}",
            ArenaRewards.championshipBits(run.entrants.size, ArenaDifficulty.valueOf(run.difficulty.uppercase())), now)
    }

    /** Runs the other bracket bouts off the UI thread, with the exact same policies/catalog as visible bouts. */
    suspend fun nextPlayerMatch(runId: String): ArenaMatchSpec? = withContext(Dispatchers.IO) {
        currency.initializeWallet()
        while (true) {
            currentCoroutineContext().ensureActive()
            var run = decodeRun(requireNotNull(db.arenaDao().run(runId)))
            if (run.withdrawn || run.championId != null) return@withContext null
            require(run.canonRevision == TamerCanonicalPartners.REVISION) { "The tournament partner history needs to be refreshed." }
            val round = run.matches.maxOf { it.round }
            val pending = run.matches.filter { it.round == round && it.winnerId == null }
            for (match in pending.filter { it.leftId != PLAYER_ID && it.rightId != PLAYER_ID }) {
                val spec = matchSpec(run, match, playerOnLeft = false)
                var snapshot = simulate(spec)
                if (snapshot.result?.outcome == BattleOutcome.DRAW) snapshot = simulate(spec.copy(
                    configuration = spec.configuration.copy(randomSeed = match.seed xor 0x738AB1L), tiebreakRound = 1))
                val leftWon = when (snapshot.result?.outcome) {
                    BattleOutcome.ALLIED_VICTORY -> true
                    BattleOutcome.OPPOSING_VICTORY -> false
                    else -> leftWinsTiebreak(snapshot, match.seed)
                }
                run = mutateRun(runId) { latest ->
                    val recorded = latest.matches.single { it.id == match.id }
                    if (recorded.winnerId != null || latest.withdrawn) latest else latest.finish(match.id,
                        if (leftWon) match.leftId else match.rightId, snapshot.result?.outcome?.name ?: "DRAW")
                }
            }
            val playerMatch = run.matches.firstOrNull { it.round == round && it.winnerId == null &&
                (it.leftId == PLAYER_ID || it.rightId == PLAYER_ID) }
            if (playerMatch != null) {
                val original = matchSpec(run, playerMatch, playerOnLeft = true)
                val previous = db.arenaDao().match(original.id)
                if (previous?.outcome == BattleOutcome.DRAW.name) {
                    val stored = decodeMatch(previous)
                    val fixed = if (stored.canonRevision < TamerCanonicalPartners.REVISION) stored.copy(
                        right = original.right, resultSpecies = original.resultSpecies, canonRevision = original.canonRevision) else stored
                    fun quarter(entrant: ArenaEntrant) = entrant.copy(members = entrant.members.map {
                        it.copy(initialHealth = (it.trainingStats().health / 4).coerceAtLeast(1))
                    })
                    return@withContext registerMatch(fixed.copy(id = "${original.id}:tiebreak", left = quarter(fixed.left),
                        right = quarter(fixed.right), tiebreakRound = 1,
                        configuration = fixed.configuration.copy(randomSeed = fixed.configuration.randomSeed xor 0x738AB1L, maxDurationMillis = 30_000)))
                }
                return@withContext registerMatch(original)
            }
            run = mutateRun(runId) { it.advanceRound() }
            db.withTransaction { grantTrophyLocked(run, System.currentTimeMillis()) }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    private fun matchSpec(run: ArenaTournament, match: ArenaBracketMatch, playerOnLeft: Boolean): ArenaMatchSpec {
        val entries = run.entrants.associateBy { it.id }
        val swap = playerOnLeft && match.rightId == PLAYER_ID
        val left = entries.getValue(if (swap) match.rightId else match.leftId)
        val right = entries.getValue(if (swap) match.leftId else match.rightId)
        val resultSpecies = run.entrants.flatMap { entrant -> entrant.members }.flatMap { member ->
            listOfNotNull(member.blastTargetSpecies, member.jogressResultSpecies)
        }.toSet()
        // Result artwork is captured in the tournament entrants' match metadata by the registration owner.
        return ArenaMatchSpec(match.id, left, right, ArenaDifficulty.valueOf(run.difficulty.uppercase()),
            run.configuration.copy(randomSeed = match.seed), run.id, contentVersion = run.contentVersion, aiVersion = run.aiVersion,
            resultSpecies = run.resultSpecies.filter { it.name in resultSpecies },
            bracketMatchId = match.id, canonRevision = run.canonRevision)
    }

    private suspend fun simulate(spec: ArenaMatchSpec): BattleSnapshot {
        val simulator = TamerBattleFactory.create(spec, autoplayLeft = true)
        repeat(60_000) { step ->
            if (step % 128 == 0) currentCoroutineContext().ensureActive()
            simulator.advance(BattleRules.STEP_MILLIS)
            val snapshot = simulator.snapshot()
            if (snapshot.result != null) return snapshot
        }
        simulator.abandon()
        return simulator.snapshot()
    }

    companion object {
        const val PLAYER_ID = "player"
        fun leftWinsTiebreak(snapshot: BattleSnapshot, seed: Long): Boolean {
            fun health(members: List<CombatantSnapshot>) = members.map { it.health.toDouble() / it.maxHealth }.average()
            val difference = health(snapshot.alliedMembers) - health(snapshot.opposingMembers)
            return if (abs(difference) > 0.000001) difference > 0 else Random(seed xor 0x9913BL).nextBoolean()
        }
    }
}
