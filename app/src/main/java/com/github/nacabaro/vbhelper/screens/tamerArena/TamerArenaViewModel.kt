package com.github.nacabaro.vbhelper.screens.tamerArena

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAiPolicy
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueLoadout
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineArenaManifest
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSessionViewModel
import com.github.nacabaro.vbhelper.screens.offlineBattle.offlineBattleViewModel
import com.github.nacabaro.vbhelper.screens.offlineBattleParticipant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
) {
    val selectedPartners: List<OfflineBattleParticipant>
        get() = selectedPartnerIds.take(format).mapNotNull { id -> partners.firstOrNull { it.stableId == id } }
    val stages: List<Int> get() = selectedPartners.map { it.stage.coerceIn(0, 5) }
    val teamReady: Boolean get() = selectedPartners.size == format && selectedPartners.map { it.stableId }.distinct().size == format
    val activeRun: ArenaTournament? get() = runs.firstOrNull { it.id == activeRunId }
}

class TamerArenaViewModel(private val app: VBHelper) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ArenaRepository(app.container.db, app.container.currencyRepository)
    private val _state = MutableStateFlow(TamerArenaState())
    private var loadingJob: Job? = null
    val state: StateFlow<TamerArenaState> = _state.asStateFlow()

    init {
        reload()
        scope.launch { repository.runs.collect { rows ->
            val decoded = rows.mapNotNull { runCatching { repository.decodeRun(it) }.getOrNull() }
            _state.value = _state.value.copy(runs = decoded)
        } }
        scope.launch { repository.records.collect { _state.value = _state.value.copy(records = it) } }
        scope.launch { repository.recentMatches.collect { _state.value = _state.value.copy(recentMatches = it) } }
    }

    fun reload() {
        if (_state.value.busy || loadingJob?.isActive == true) return
        _state.value = _state.value.copy(loading = true, error = null)
        loadingJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val tamers = app.assets.open(TamerCatalog.ASSET_PATH).bufferedReader().use { TamerCatalog.parse(it.readText()) }
                    val nicknames = runCatching {
                        app.assets.open(TamerNicknames.ASSET_PATH).bufferedReader().use { TamerNicknames.parse(it.readText()) }
                    }.getOrDefault(emptyMap())
                    val resolver = TamerTeamResolver(tamers, ArenaSpeciesIndex.load(app, app.container.db), nicknames)
                    val db = app.container.db
                    val characters = db.userCharacterDao().getAllCharacters().first().associateBy { it.id }
                    val loadouts = db.digimonTechniqueLoadoutDao().getAll().groupBy { it.individualId }
                    val partners = db.userCharacterDao().getAllBattleParticipantProfiles().mapNotNull { profile ->
                        characters[profile.sourceCharacterId]?.let { character ->
                            offlineBattleParticipant(character, profile).copy(techniqueIds = GenericTechniqueLoadout.resolve(
                                loadouts[profile.individualId].orEmpty().sortedBy { it.slot }.map { it.techniqueId }))
                        }
                    }
                    val active = db.userCharacterDao().getActiveCharacter().first()?.id
                    Triple(resolver, partners, partners.firstOrNull { it.character?.id == active }?.stableId)
                }
                val previous = _state.value.selectedPartnerIds.filter { id -> loaded.second.any { it.stableId == id } }
                val selected = (previous + listOfNotNull(loaded.third) + loaded.second.map { it.stableId }).distinct().take(2)
                _state.value = _state.value.copy(loading = false, resolver = loaded.first, partners = loaded.second,
                    selectedPartnerIds = selected)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = _state.value.copy(loading = false, error = failure.message ?: app.getString(R.string.arena_load_failed))
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
    fun clearError() { _state.value = _state.value.copy(error = null) }

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
            val fixed = repository.registerMatch(match)
            _state.value = _state.value.copy(currentMatch = fixed, activeRunId = null)
        }
    }

    fun startTournament(size: Int, series: String?) {
        val current = _state.value
        if (current.busy || !current.teamReady) return
        val resolver = current.resolver ?: return
        action {
            val candidates = resolver.tamers.filter { series == null || it.series == series }
                .mapNotNull { resolver.resolve(it.id, current.stages) }
            require(size in setOf(8, 16) && candidates.size >= size - 1) { app.getString(R.string.arena_not_enough_tamers) }
            val seed = System.nanoTime()
            val selected = candidates.shuffled(kotlin.random.Random(seed)).take(size - 1)
            val entries = listOf(player(current)) + selected.map { opponent(it, resolver, current.difficulty) }
            val id = UUID.randomUUID().toString()
            val tournament = ArenaTournament.create(id, entries, current.format, current.difficulty.name, seed,
                series ?: app.getString(R.string.arena_all_series)).copy(configuration = configuration(seed),
                resultSpecies = resultArt(entries, resolver))
            repository.registerTournament(tournament)
            _state.value = _state.value.copy(activeRunId = id)
            val match = repository.nextPlayerMatch(id)
            _state.value = _state.value.copy(currentMatch = match)
        }
    }

    fun resumeTournament(id: String) {
        if (_state.value.busy) return
        val resolver = _state.value.resolver ?: return
        action {
            _state.value = _state.value.copy(activeRunId = id)
            repository.refreshLegacyTournament(id, resolver)
            val next = repository.nextPlayerMatch(id)
            _state.value = _state.value.copy(currentMatch = next)
        }
    }

    fun showBracket(id: String?) { _state.value = _state.value.copy(activeRunId = id, selectedTamerId = null) }
    fun closeMatch() { _state.value = _state.value.copy(currentMatch = null) }
    fun withdrawTournament(id: String) { if (!_state.value.busy) action { repository.withdraw(id) } }

    private fun action(block: suspend () -> Unit) {
        _state.value = _state.value.copy(busy = true, error = null)
        scope.launch {
            try { block() } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = _state.value.copy(error = if (failure is CanonicalTeamUnavailable)
                    app.getString(R.string.arena_canonical_art_missing, failure.tamerName)
                    else failure.message ?: app.getString(R.string.arena_operation_failed))
            } finally { _state.value = _state.value.copy(busy = false) }
        }
    }

    override fun onCleared() { scope.cancel() }
}

fun tamerArenaViewModel(context: Context, owner: ViewModelStoreOwner): TamerArenaViewModel =
    ViewModelProvider(owner, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TamerArenaViewModel(context.applicationContext as VBHelper) as T
    })[TamerArenaViewModel::class.java]
