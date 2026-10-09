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

    fun start(
        context: Context,
        sessionId: String,
        allies: List<OfflineBattleParticipant>,
        opponents: List<OfflineBattleParticipant>,
        randomSeed: Long = System.nanoTime(),
        arenaManifestPath: String = OfflineArenaManifest.DEFAULT_MANIFEST_PATH
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
        _state.value = OfflineBattleSessionState(sessionId = sessionId, loading = true)
        loadingJob = scope.launch {
            runCatching {
                TrainingBattlePresentationFactory.create(context.applicationContext, this@OfflineBattleSessionViewModel.allies,
                    this@OfflineBattleSessionViewModel.opponents, seed, this@OfflineBattleSessionViewModel.arenaManifestPath,
                    extraItems = if (this@OfflineBattleSessionViewModel.arenaManifestPath == OfflineArenaManifest.RADAR_MANIFEST_PATH) {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val db = (context.applicationContext as com.github.nacabaro.vbhelper.di.VBHelper).container.db
                            com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).loadoutLocked(sessionId)
                        }
                    } else emptyList())
            }.onSuccess { presentation ->
                if (_state.value.sessionId != sessionId) return@onSuccess
                val next = BattleSessionController(presentation.simulator, scope)
                pauseReasons.forEach { next.setPaused(it, true) }
                next.setPaused("menu", false)
                controller = next
                baseFighters = presentation.fighters
                _state.value = OfflineBattleSessionState(
                    sessionId = sessionId,
                    snapshot = next.snapshot.value,
                    fighters = baseFighters,
                    arenaManifest = presentation.arenaManifest,
                    preparedForms = presentation.preparedForms,
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
                            // The renderer owns active models/visibility using the finisher snapshot.
                            // Retain both prepared maps and their GLB arrays across every simulation tick.
                            _state.value = _state.value.copy(snapshot = snapshot)
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

    fun issueOrder(actorId: String, action: TrainerAction, interrupt: Boolean = false): OrderUpdate? =
        controller?.issueOrder(actorId, action, interrupt)

    fun setPaused(reason: String, paused: Boolean) {
        if (paused) pauseReasons += reason else pauseReasons -= reason
        controller?.setPaused(reason, paused)
    }

    fun abandon(): BattleOutcome? = controller?.abandon()

    fun finishSession() {
        releaseCurrentSession()
        allies = emptyList()
        opponents = emptyList()
        pauseReasons.clear()
        _state.value = OfflineBattleSessionState()
    }

    fun retry(context: Context, preserveEncounter: Boolean = false) {
        val sessionId = _state.value.sessionId ?: return
        if (preserveEncounter) {
            start(context, sessionId, allies, opponents, seed, arenaManifestPath)
            return
        }
        val candidateSeed = System.nanoTime()
        val nextSeed = if (candidateSeed != seed) candidateSeed else seed + 1L
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
    }

    override fun onCleared() {
        releaseCurrentSession()
        scope.cancel()
        super.onCleared()
    }
}

fun offlineBattleViewModel(owner: androidx.lifecycle.ViewModelStoreOwner): OfflineBattleSessionViewModel =
    ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[OfflineBattleSessionViewModel::class.java]
