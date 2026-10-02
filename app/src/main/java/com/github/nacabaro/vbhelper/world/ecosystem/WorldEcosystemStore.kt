package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

data class EcosystemPopulation(
    val spawns: List<WorldSpawn>,
    val claimedIndividuals: Set<String> = emptySet(),
    val interactions: List<EcosystemInteractionSummary> = emptyList(),
    val personalities: Map<String, DigimonPersonalityType> = emptyMap()
)

interface WorldEcosystemStore {
    suspend fun loadSession(): WorldEcosystemSession?
    suspend fun saveSession(session: WorldEcosystemSession)
    suspend fun loadSpawns(now: Long): List<WorldSpawn>
    suspend fun loadPopulation(now: Long): EcosystemPopulation = EcosystemPopulation(loadSpawns(now))
    suspend fun <T> transaction(action: suspend () -> T): T = action()
    suspend fun saveMovement(actors: List<WorldSpawn>) {}
    suspend fun recordInput(input: WorldEcosystemInput) {}
    suspend fun advanceInteractions(session: WorldEcosystemSession, actors: List<WorldSpawn>, replay: Boolean, playerFix: WorldPlayerFix? = null): Boolean = false
    fun setRadarActive(active: Boolean) {}
    fun setRadarAutonomousEnabled(enabled: Boolean) {}
}

class RoomWorldEcosystemStore(private val db: AppDatabase, private val interactions: WorldInteractionOrchestrator? = null,
    private val clock: () -> Long = System::currentTimeMillis) : WorldEcosystemStore {
    private val gson=com.google.gson.Gson()
    override suspend fun <T> transaction(action: suspend () -> T): T {
        try { return db.withTransaction { action() } } catch (failure: Throwable) { interactions?.invalidateBattleCache(); throw failure }
    }
    override fun setRadarActive(active: Boolean) { interactions?.setVisible(active) }
    override fun setRadarAutonomousEnabled(enabled: Boolean) { interactions?.setAutonomousEnabled(enabled) }
    override suspend fun advanceInteractions(session: WorldEcosystemSession, actors: List<WorldSpawn>, replay: Boolean, playerFix: WorldPlayerFix?) = interactions?.step(session,actors,replay,playerFix) ?: false
    override suspend fun loadSession() = db.worldEcosystemDao().getSession()
    override suspend fun saveSession(session: WorldEcosystemSession) = db.worldEcosystemDao().saveSession(session)
    override suspend fun recordInput(input: WorldEcosystemInput) = db.worldEcosystemDao().recordInput(input)
    override suspend fun saveMovement(actors: List<WorldSpawn>) {
        actors.forEach { actor -> db.worldSpawnDao().checkpointMovement(actor.individualId, actor.latitude, actor.longitude,
            actor.wanderTargetLatitude, actor.wanderTargetLongitude, actor.movementState, actor.movementTick, actor.nextDecisionTick) }
    }
    override suspend fun loadSpawns(now: Long) = db.worldSpawnDao().getActiveSpawnsSync(now)
    override suspend fun loadPopulation(now: Long) = db.withTransaction {
        WorldInteractionRepository(db) { now }.reconcileLocked()
        db.worldSpawnDao().deleteExpired(now)
        val dao = db.worldInteractionDao()
        val summaries = dao.getOpenInteractions().map { event -> EcosystemInteractionSummary(
            event.id, event.type, event.origin, event.state, event.revision,
            dao.getParticipants(event.id).map { it.individualId }, event.expiresAt, event.publicReason,
            dao.getMessages(event.id).filter { it.source==DialogueTextSource.MODEL || it.source==DialogueTextSource.AUTHORED }
                .groupBy { it.speakerId }.values.map { lines -> lines.last().let { EcosystemUtterance(it.speakerId,it.body,it.tick) } },event.nextActionTick,
            dao.getNpcBattle(event.id)?.let { record -> runCatching {
                val state=gson.fromJson(record.snapshotJson,NpcBattleSummary::class.java)
                (state.alliedMembers+state.opposingMembers).associateBy { it.combatantId.substringAfter(':') }
            }.getOrDefault(emptyMap()) } ?: emptyMap()
        ) }
        val actors = db.worldSpawnDao().getActiveSpawnsSync(now)
        val personalities = actors.mapNotNull { actor -> db.digimonIndividualDao().getPersonality(actor.individualId)?.let { actor.individualId to it.personalityType } }.toMap()
        EcosystemPopulation(actors, dao.getClaimedIndividuals().toSet(), summaries, personalities)
    }
}
