package com.tamerslab.battle.ui.arena

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.tamerslab.R
import com.tamerslab.battle.offline.core.BattleConfiguration
import com.tamerslab.battle.offline.core.BattleSpeciesIdentity
import com.tamerslab.battle.offline.core.TrainerAiPolicy
import com.tamerslab.battle.offline.data.GenericTechniqueLoadout
import com.tamerslab.battle.offline.tamers.*
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.repository.ArenaRosterGateway
import com.tamerslab.domain.usecase.BattleUseCase
import com.tamerslab.battle.ui.OfflineBattleParticipant
import com.tamerslab.battle.ui.offline.OfflineArenaManifest
import com.tamerslab.battle.ui.offline.OfflineBattleSessionViewModel
import com.tamerslab.battle.ui.offline.offlineBattleViewModel
import com.tamerslab.battle.ui.offlineBattleParticipant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.context.GlobalContext
import java.util.UUID

data class TamerArenaState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val resolver: TamerTeamResolver? = null,
    val partners: List<OfflineBattleParticipant> = emptyList(),
    val selectedPartnerIds: List<String> = emptyList(),
    val format: Int = 1,
    val difficulty: ArenaDifficulty = ArenaDifficulty.NORMAL,
    val selectedTamerId: String? = null,
    val currentMatch: ArenaMatchSpec? = null,
    val activeRunId: String? = null,
    val runs: List<ArenaTournament> = emptyList(),
    val records: List<ArenaTamerRecord> = emptyList(),
    val recentMatches: List<ArenaMatchEntity> = emptyList(),
    val errorDetails: String? = null,
) {
    val selectedPartners: List<OfflineBattleParticipant>
        get() = selectedPartnerIds.take(format).mapNotNull { id -> partners.firstOrNull { it.stableId == id } }
    val stages: List<Int> get() = selectedPartners.map { it.stage.coerceIn(0, 5) }
    val teamReady: Boolean get() = selectedPartners.size == format && selectedPartners.map { it.stableId }.distinct().size == format
    val activeRun: ArenaTournament? get() = runs.firstOrNull { it.id == activeRunId }
}

class TamerArenaViewModel(
    private val app: TamersLabApplication,
    private val battle: BattleUseCase,
    private val roster: ArenaRosterGateway,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(TamerArenaState())
    private var loadingJob: Job? = null
    val state: StateFlow<TamerArenaState> = _state.asStateFlow()

    init {
        reload()
        scope.launch {
            combine(com.tamerslab.di.AppServices.spritePreferenceRepository.preference, roster.invalidation) { _, _ -> Unit }
                .collect { if (_state.value.currentMatch == null) reload() }
        }
        scope.launch { battle.runs.collect { rows ->
            val decoded = rows.mapNotNull { runCatching { battle.decodeRun(it) }.getOrNull() }
            _state.value = _state.value.copy(runs = decoded)
        } }
        scope.launch { battle.records.collect { _state.value = _state.value.copy(records = it) } }
        scope.launch { battle.recentMatches.collect { _state.value = _state.value.copy(recentMatches = it) } }
    }

    fun reload() {
        if (_state.value.busy) return
        loadingJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, errorDetails = null)
        loadingJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val tamers = app.assets.open(TamerCatalog.ASSET_PATH).bufferedReader().use { TamerCatalog.parse(it.readText()) }
                    val nicknames = runCatching {
                        app.assets.open(TamerNicknames.ASSET_PATH).bufferedReader().use { TamerNicknames.parse(it.readText()) }
                    }.getOrDefault(emptyMap())
                    val snapshot = roster.load()
                    val resolver = TamerTeamResolver(tamers, roster.speciesIndex(), nicknames)
                    val partners = snapshot.partners.mapNotNull { profile ->
                        snapshot.characters[profile.sourceCharacterId]?.let { character ->
                            offlineBattleParticipant(character, profile).copy(techniqueIds = GenericTechniqueLoadout.resolve(
                                snapshot.techniques[profile.individualId].orEmpty().sortedBy { it.slot }.map { it.techniqueId }))
                        }
                    }
                    Triple(resolver, partners, partners.firstOrNull { it.character?.id == snapshot.activeCharacterId }?.stableId)
                }
                val previous = _state.value.selectedPartnerIds.filter { id -> loaded.second.any { it.stableId == id } }
                val selected = (previous + listOfNotNull(loaded.third) + loaded.second.map { it.stableId }).distinct().take(2)
                _state.value = _state.value.copy(loading = false, resolver = loaded.first, partners = loaded.second,
                    selectedPartnerIds = selected)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = _state.value.copy(loading = false, error = app.getString(R.string.arena_load_failed), errorDetails = failure.message)
            }
        }
    }

    fun selectTamer(id: String?) { _state.value = _state.value.copy(selectedTamerId = id, error = null) }
    fun setFormat(format: Int) { require(format in 1..2); _state.value = _state.value.copy(format = format) }
    fun setDifficulty(difficulty: ArenaDifficulty) { _state.value = _state.value.copy(difficulty = difficulty) }
    fun selectPartner(slot: Int, id: String) {
        require(slot in 0..1 && _state.value.partners.any { it.stableId == id })
        val ids = _state.value.selectedPartnerIds.toMutableList()
        if (slot < ids.size) ids[slot] = id else ids.add(id)
        if (ids.distinct().size == ids.size) _state.value = _state.value.copy(selectedPartnerIds = ids)
    }
    fun clearError() { _state.value = _state.value.copy(error = null, errorDetails = null) }

    private fun configuration(seed: Long): BattleConfiguration {
        val arena = OfflineArenaManifest.read(app)
        return BattleConfiguration(randomSeed = seed, defaultPaused = true, arenaRadius = arena.playableRadius,
            lineupRowSpacing = arena.lineupRowSpacing, lineupDepthRatio = arena.lineupDepthRatio,
            minimumLineupDepth = arena.minimumLineupDepth, maxDurationMillis = 180_000,
            itemCooldownMillis = 5_000, strictFinisherEligibility = true)
    }

    private fun player(state: TamerArenaState) = ArenaEntrant(ArenaRepository.PLAYER_ID,
        app.getString(R.string.arena_your_team), null, state.selectedPartners.map(TamerBattleFactory::freeze), TrainerAiPolicy())

    private fun opponent(team: ResolvedTamerTeam, resolver: TamerTeamResolver, difficulty: ArenaDifficulty) =
        ArenaEntrant(team.tamer.id, team.tamer.name, team.tamer.id,
            TamerBattleLoadouts.participants(team, resolver), TamerBattleLoadouts.policy(team.tamer.style, difficulty))

    private fun resultArt(entries: List<ArenaEntrant>, resolver: TamerTeamResolver): List<ArenaSpecies> =
        entries.flatMap { it.members }.flatMap { listOfNotNull(it.blastTargetSpecies, it.jogressResultSpecies) }
            .distinctBy(BattleSpeciesIdentity::normalize).mapNotNull(resolver::speciesNamed)

    fun startExhibition(tamerId: String) {
        val current = _state.value
        if (current.busy || !current.teamReady) return
        val resolver = current.resolver ?: return
        val team = resolver.resolve(tamerId, current.stages) ?: return
        action {
            val left = player(current)
            val right = opponent(team, resolver, current.difficulty)
            val match = ArenaMatchSpec(UUID.randomUUID().toString(), left, right, current.difficulty,
                configuration(System.nanoTime()), resultSpecies = resultArt(listOf(left, right), resolver))
            val fixed = battle.registerMatch(match)
            _state.value = _state.value.copy(currentMatch = fixed, activeRunId = null)
        }
    }

    fun startTournament(size: Int, series: String?) {
        val current = _state.value
        if (current.busy || !current.teamReady) return
        val resolver = current.resolver ?: return
        action {
            val candidates = resolver.tamers.filter { series == null || it.series == series }
                .mapNotNull { resolver.resolveForCup(it.id, current.stages) }
            require(size in setOf(8, 16) && candidates.size >= size - 1) { app.getString(R.string.arena_not_enough_tamers) }
            val seed = System.nanoTime()
            val selected = candidates.shuffled(kotlin.random.Random(seed)).take(size - 1)
            val cupResolver = resolver.forCups()
            val entries = listOf(player(current)) + selected.map { opponent(it, cupResolver, current.difficulty) }
            val id = UUID.randomUUID().toString()
            val tournament = ArenaTournament.create(id, entries, current.format, current.difficulty.name, seed,
                series ?: app.getString(R.string.arena_all_series)).copy(configuration = configuration(seed),
                resultSpecies = resultArt(entries, cupResolver))
            battle.registerTournament(tournament)
            _state.value = _state.value.copy(activeRunId = id)
            val match = battle.nextPlayerMatch(id)
            _state.value = _state.value.copy(currentMatch = match)
        }
    }

    fun resumeTournament(id: String) {
        if (_state.value.busy) return
        val resolver = _state.value.resolver ?: return
        action {
            _state.value = _state.value.copy(activeRunId = id)
            battle.refreshLegacyTournament(id, resolver.forCups())
            val next = battle.nextPlayerMatch(id)
            _state.value = _state.value.copy(currentMatch = next)
        }
    }

    fun showBracket(id: String?) { _state.value = _state.value.copy(activeRunId = id, selectedTamerId = null) }
    fun closeMatch() { _state.value = _state.value.copy(currentMatch = null); reload() }
    fun withdrawTournament(id: String) { if (!_state.value.busy) action { battle.withdraw(id) } }

    private fun action(block: suspend () -> Unit) {
        _state.value = _state.value.copy(busy = true, error = null, errorDetails = null)
        scope.launch {
            try { block() } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = _state.value.copy(error = if (failure is com.tamerslab.species.PartnerUnavailableException)
                    app.getString(R.string.participation_inactive_hint) else if (failure is CanonicalTeamUnavailable)
                    app.getString(R.string.arena_canonical_art_missing, failure.tamerName)
                    else app.getString(R.string.arena_operation_failed), errorDetails = failure.message)
            } finally { _state.value = _state.value.copy(busy = false) }
        }
    }

    override fun onCleared() { scope.cancel() }
}

fun tamerArenaViewModel(context: Context, owner: ViewModelStoreOwner): TamerArenaViewModel =
    ViewModelProvider(owner, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GlobalContext.get().get<TamerArenaViewModel>() as T
    })[TamerArenaViewModel::class.java]

