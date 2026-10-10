package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.Context
import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.OrderUpdate
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAction
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.battle.offline.session.BattleSessionController
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.tamers.ArenaMatchSpec
import com.github.nacabaro.vbhelper.battle.offline.tamers.ArenaRepository
import com.github.nacabaro.vbhelper.battle.offline.tamers.ArenaSettlement
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class OfflineBattleSessionState(
    val sessionId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val snapshot: BattleSnapshot? = null,
    val fighters: Map<String, BattleFighterPresentation> = emptyMap(),
    val arenaManifest: OfflineArenaManifest? = null,
    val countdown: Int = 3,
    val preparedForms: Map<String, BattleFighterPresentation> = emptyMap(),
    val assetGeneration: Long = 0L,
    val techniques: List<TechniqueDefinition> = emptyList(),
    val items: List<com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition> = emptyList(),
    val arenaSettlement: ArenaSettlement? = null,
    val recordingArenaResult: Boolean = false,
    val arenaRecordError: String? = null,
)

/**
 * Visible fighter per combatant: the prebuilt transformed model while the
 * snapshot reports an active Blast transform, else the base model.
 */
internal fun resolveVisibleFighters(
    base: Map<String, BattleFighterPresentation>,
    alts: Map<String, BattleFighterPresentation>,
    snapshot: BattleSnapshot?
): Map<String, BattleFighterPresentation> {
    if (snapshot == null) return base
    val transformed = (snapshot.alliedMembers + snapshot.opposingMembers)
        .associate { it.combatantId to it.blastFormSpecies }
    return base.mapValues { (id, fighter) ->
        val form = transformed[id]
        if (form != null) {
            alts[id]?.takeIf { it.displayName.equals(form, ignoreCase = true) } ?: fighter
        } else fighter
    }
}

/** Keeps the pure battle controller and its render assets alive across screen recreation. */
class OfflineBattleSessionViewModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(OfflineBattleSessionState())
    val state: StateFlow<OfflineBattleSessionState> = _state.asStateFlow()

    private var controller: BattleSessionController? = null
    private var snapshotJob: Job? = null
    private var loadingJob: Job? = null
    private var countdownJob: Job? = null
    // Reasons survive asynchronous loading and Activity/renderer recreation.
    private val pauseReasons = linkedSetOf<String>()
    private var baseFighters: Map<String, BattleFighterPresentation> = emptyMap()
    private var allies: List<OfflineBattleParticipant> = emptyList()
    private var opponents: List<OfflineBattleParticipant> = emptyList()
    private var seed = 1L
    private var arenaManifestPath = OfflineArenaManifest.DEFAULT_MANIFEST_PATH
    private var preparedPresentation: TrainingBattlePresentation? = null
    private var assetGeneration = 0L
    private var arenaSpec: ArenaMatchSpec? = null
    private var applicationContext: Context? = null

    fun startArena(context: Context, match: ArenaMatchSpec) {
        val previous = arenaSpec
        val prepared = preparedPresentation
        val warm = previous != null && prepared != null && _state.value.snapshot?.result != null &&
            _state.value.error == null && previous.left == match.left && previous.right == match.right &&
            previous.resultSpecies == match.resultSpecies && previous.tiebreakRound == match.tiebreakRound &&
            previous.configuration.copy(randomSeed = match.configuration.randomSeed) == match.configuration
        if (warm) {
            val restarted = requireNotNull(prepared).rematch(match.configuration.randomSeed)
            releaseCurrentSession()
            pauseReasons.retainAll(setOf("background"))
            pauseReasons += "intro"
            allies = match.left.members
            opponents = match.right.members
            seed = match.configuration.randomSeed
            arenaSpec = match
            applicationContext = context.applicationContext
            installPresentation(context.applicationContext, match.id, restarted)
            return
        }
        start(context, match.id, match.left.members, match.right.members, match.configuration.randomSeed, arenaSpec = match)
    }

    fun start(
        context: Context,
        sessionId: String,
        allies: List<OfflineBattleParticipant>,
        opponents: List<OfflineBattleParticipant>,
        randomSeed: Long = System.nanoTime(),
        arenaManifestPath: String = OfflineArenaManifest.DEFAULT_MANIFEST_PATH,
        arenaSpec: ArenaMatchSpec? = null,
    ) {
        if (_state.value.sessionId == sessionId && (controller != null || _state.value.loading)) return
        releaseCurrentSession()
        pauseReasons.retainAll(setOf("background"))
        pauseReasons += "scene"
        pauseReasons += "intro"
        this.allies = allies.toList()
        this.opponents = opponents.toList()
        seed = randomSeed
        this.arenaManifestPath = arenaManifestPath
        this.arenaSpec = arenaSpec
        applicationContext = context.applicationContext
        _state.value = OfflineBattleSessionState(sessionId = sessionId, loading = true, assetGeneration = ++assetGeneration)
        loadingJob = scope.launch {
            runCatching {
                TrainingBattlePresentationFactory.create(context.applicationContext, this@OfflineBattleSessionViewModel.allies,
                    this@OfflineBattleSessionViewModel.opponents, seed, this@OfflineBattleSessionViewModel.arenaManifestPath,
                    extraItems = if (this@OfflineBattleSessionViewModel.arenaManifestPath == OfflineArenaManifest.RADAR_MANIFEST_PATH) {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val db = (context.applicationContext as com.github.nacabaro.vbhelper.di.VBHelper).container.db
                            com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).loadoutLocked(sessionId)
                        }
                    } else emptyList(), arenaSpec = this@OfflineBattleSessionViewModel.arenaSpec)
            }.onSuccess { presentation ->
                if (_state.value.sessionId != sessionId) return@onSuccess
                installPresentation(context.applicationContext, sessionId, presentation)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (_state.value.sessionId == sessionId) {
                    _state.value = OfflineBattleSessionState(
                        sessionId = sessionId,
                        error = failure.message?.takeIf(String::isNotBlank)
                            ?: context.getString(com.github.nacabaro.vbhelper.R.string.ui_battle_error_open_arena)
                    )
                }
            }
        }
    }

    private fun installPresentation(context: Context, sessionId: String, presentation: TrainingBattlePresentation) {
        preparedPresentation = presentation
        val next = BattleSessionController(presentation.simulator, scope)
        pauseReasons.forEach { next.setPaused(it, true) }
        next.setPaused("menu", false)
        controller = next
        baseFighters = presentation.fighters
        _state.value = OfflineBattleSessionState(
            sessionId = sessionId, snapshot = next.snapshot.value, fighters = baseFighters,
            arenaManifest = presentation.arenaManifest, preparedForms = presentation.preparedForms,
            assetGeneration = assetGeneration,
            techniques = presentation.techniqueDefinitions,
            items = presentation.initialItems,
        )
        snapshotJob = scope.launch {
            var recordedTerminal = false
            var recordedItems = emptyList<com.github.nacabaro.vbhelper.battle.offline.core.BattleItemSnapshot>()
            next.snapshot.collect { snapshot ->
                if (_state.value.sessionId == sessionId) {
                    if (arenaManifestPath == OfflineArenaManifest.RADAR_MANIFEST_PATH &&
                        (snapshot.trainingItems != recordedItems || (snapshot.result != null && !recordedTerminal))) {
                        try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                                val db = (context.applicationContext as com.github.nacabaro.vbhelper.di.VBHelper).container.db
                                db.withTransaction {
                                    val timestamp = System.currentTimeMillis()
                                    com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).checkpointLocked(sessionId, snapshot.trainingItems, timestamp)
                                    if (snapshot.result != null && !recordedTerminal) {
                                        com.github.nacabaro.vbhelper.quests.QuestBattleFacts(db).recordTerminalLocked(sessionId, snapshot, timestamp)
                                    }
                                }
                            }
                            recordedItems = snapshot.trainingItems
                            if (snapshot.result != null) recordedTerminal = true
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            next.setPaused("battle-record", true)
                            _state.value = _state.value.copy(error = failure.message ?: "Could not record the terminal battle facts.")
                            return@collect
                        }
                    }
                    _state.value = _state.value.copy(snapshot = snapshot)
                    if (snapshot.result != null && arenaSpec != null && !recordedTerminal) {
                        recordArenaOutcome(context, requireNotNull(arenaSpec), snapshot)
                        recordedTerminal = _state.value.arenaSettlement != null
                    }
                }
            }
        }
        next.start()
        countdownJob = scope.launch {
            var activeMillis = 0
            while (_state.value.sessionId == sessionId && _state.value.countdown > 0) {
                delay(100)
                if (pauseReasons.any { it != "intro" }) continue
                activeMillis += 100
                if (activeMillis >= 1000) {
                    activeMillis = 0
                    _state.value = _state.value.copy(countdown = _state.value.countdown - 1)
                }
            }
            if (_state.value.sessionId == sessionId) setPaused("intro", false)
        }
    }

    fun issueOrder(actorId: String, action: TrainerAction, interrupt: Boolean = false): OrderUpdate? =
        controller?.issueOrder(actorId, action, interrupt)

    fun setPaused(reason: String, paused: Boolean) {
        if (paused) pauseReasons += reason else pauseReasons -= reason
        controller?.setPaused(reason, paused)
    }

    fun abandon(): BattleOutcome? {
        val outcome = controller?.abandon()
        val spec = arenaSpec
        val context = applicationContext
        val snapshot = controller?.snapshot?.value
        if (spec != null && context != null && snapshot?.result != null) {
            scope.launch { recordArenaOutcome(context, spec, snapshot) }
        }
        return outcome
    }

    private suspend fun recordArenaOutcome(context: Context, spec: ArenaMatchSpec, snapshot: BattleSnapshot) {
        if (_state.value.recordingArenaResult || _state.value.arenaSettlement != null) return
        _state.value = _state.value.copy(recordingArenaResult = true, arenaRecordError = null)
        try {
            val app = context.applicationContext as VBHelper
            val settlement = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                ArenaRepository(app.container.db, app.container.currencyRepository).recordTerminal(spec.id, snapshot)
            }
            if (_state.value.sessionId == spec.id) _state.value = _state.value.copy(
                recordingArenaResult = false, arenaSettlement = settlement)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (_state.value.sessionId == spec.id) _state.value = _state.value.copy(recordingArenaResult = false,
                arenaRecordError = failure.message ?: "Could not save the arena result.")
        }
    }

    fun retryArenaRecord() {
        val spec = arenaSpec ?: return
        val context = applicationContext ?: return
        val snapshot = controller?.snapshot?.value?.takeIf { it.result != null } ?: return
        scope.launch { recordArenaOutcome(context, spec, snapshot) }
    }

    fun finishSession() {
        releaseCurrentSession()
        allies = emptyList()
        opponents = emptyList()
        pauseReasons.clear()
        arenaSpec = null
        _state.value = OfflineBattleSessionState()
    }

    fun retry(context: Context, preserveEncounter: Boolean = false) {
        val sessionId = _state.value.sessionId ?: return
        arenaSpec?.let { match ->
            releaseCurrentSession()
            startArena(context, match)
            return
        }
        if (preserveEncounter) {
            start(context, sessionId, allies, opponents, seed, arenaManifestPath)
            return
        }
        val candidateSeed = System.nanoTime()
        val nextSeed = if (candidateSeed != seed) candidateSeed else seed + 1L
        val prepared = preparedPresentation?.takeIf {
            arenaManifestPath != OfflineArenaManifest.RADAR_MANIFEST_PATH &&
                _state.value.snapshot?.result != null && _state.value.error == null
        }
        if (prepared != null) {
            val restarted = prepared.rematch(nextSeed)
            releaseCurrentSession()
            pauseReasons.retainAll(setOf("background"))
            pauseReasons += "intro"
            seed = nextSeed
            installPresentation(context.applicationContext, "$sessionId:retry:$candidateSeed", restarted)
            return
        }
        start(context, "$sessionId:retry:$candidateSeed", allies, opponents, nextSeed, arenaManifestPath)
    }

    private fun releaseCurrentSession() {
        loadingJob?.cancel()
        loadingJob = null
        countdownJob?.cancel()
        countdownJob = null
        snapshotJob?.cancel()
        snapshotJob = null
        controller?.close()
        controller = null
        baseFighters = emptyMap()
        preparedPresentation = null
    }

    override fun onCleared() {
        releaseCurrentSession()
        scope.cancel()
        super.onCleared()
    }
}

fun offlineBattleViewModel(owner: androidx.lifecycle.ViewModelStoreOwner): OfflineBattleSessionViewModel =
    ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[OfflineBattleSessionViewModel::class.java]
