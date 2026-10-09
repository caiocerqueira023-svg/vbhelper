package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.google.gson.Gson
import org.json.JSONObject
import androidx.room.withTransaction

/** Encounter context and public-only memories stay tied to permanent individual identity. */
class WorldSocialRepository(private val db: AppDatabase) {
    private val gson = Gson()
    private val dao = db.worldSocialDao()

    suspend fun profile(id: String): DigimonSocialProfile = db.withTransaction {
        val existing = db.digimonIndividualDao().getPersonality(id)
        if (existing != null) return@withTransaction DigimonSocialProfile.forIndividual(id,existing.personalityType)
        val actor = db.worldSpawnDao().getByIndividualId(id)
        val info = actor?.let { db.characterDao().getWildCharacterInfo(it.cardCharacterId) }
        val assigned = info?.let {
            com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator.generate(id,it.attribute,it.stage,
                now = actor.spawnedAt,
                random = kotlin.random.Random(EcosystemSeed.mix(0,id,"missing-social-personality",0)))
                .also { traits -> db.digimonIndividualDao().upsertPersonality(traits) }
        }
        DigimonSocialProfile.forIndividual(id,assigned?.personalityType ?: DigimonPersonalityType.FRIENDLY)
    }

    fun encode(decision: SocialEncounterDecision) = gson.toJson(decision)

    fun decision(event: WorldInteraction): SocialEncounterDecision? = event.socialContextJson?.let { raw ->
        if (raw.length > 4_000) return@let null
        runCatching {
            val json = JSONObject(raw)
            val initiator = json.getString("initiatorId")
            val target = json.getString("targetId")
            val variation = json.getInt("variation")
            require(initiator.isNotBlank() && target.isNotBlank() && initiator.length <= 160 && target.length <= 160 && variation in 0..63)
            SocialEncounterDecision(initiator,target,SocialMotive.valueOf(json.getString("motive")),variation,
                json.optBoolean("listenerSpeaks",true),json.optBoolean("listenerDeclines",false),
                json.optString("contextDetail").takeIf { it.isNotBlank() }?.take(240))
        }.getOrNull()
    }

    suspend fun opening(event: WorldInteraction, decision: SocialEncounterDecision, language: String,
        id: String = decision.initiatorId, responding: Boolean = false): SocialOpening =
        SocialOpenings.create(decision, profile(id), language, dao.recent(id).map { it.openingKey }.toSet(), responding)

    suspend fun recordInitiation(event: WorldInteraction, decision: SocialEncounterDecision, sessionSeed: Long,
        tick: Long, openingKey: String = "", language: String = "en") {
        val participants = db.worldInteractionDao().getParticipants(event.id)
        for (participant in participants.filter { it.role == InteractionRole.WILD }) {
            val id = participant.individualId
            val old = dao.getState(id)?.takeIf { it.sessionSeed == sessionSeed } ?: IndividualSocialState(id, sessionSeed)
            dao.saveState(if (decision.targetId == "trainer") old.copy(lastPlayerInitiationTick = tick, lastPartnerId = "trainer")
                else old.copy(lastPeerInitiationTick = tick, lastPartnerId = participants.firstOrNull { it.individualId != id }?.individualId))
            if (event.state == InteractionState.PROPOSED) continue
            val other = participants.firstOrNull { it.individualId != id }
            val partner = other?.individualId ?: "trainer"
            val partnerName = if (other != null) other.spawnId?.let { db.worldSpawnDao().getSpawnById(it)?.speciesName } ?: "Digimon"
                else when { language.startsWith("pt") -> "Tamer"; language.startsWith("ja") -> "テイマー"; else -> "Tamer" }
            dao.saveMemory(WorldSocialMemory(id, event.id, partner, partnerName, decision.motive.name,
                if (partner == "trainer") "GREETED" else "MET", (decision.description() +
                    (decision.contextDetail?.let { "; $it" } ?: "")).take(360), if (id == decision.initiatorId) openingKey else "",
                tick, event.createdAt))
            dao.prune(id)
        }
    }

    suspend fun recordExchange(event: WorldInteraction, exchange: DialogueExchange, tick: Long) {
        val choice = decision(event) ?: return
        val participants = db.worldInteractionDao().getParticipants(event.id)
        participants.filter { it.role == InteractionRole.WILD }.forEach { participant ->
            val id = participant.individualId
            val previous = dao.recent(id).firstOrNull { it.eventId == event.id } ?: return@forEach
            val ownLine = exchange.lines.firstOrNull { it.speakerId == id }
            val otherLine = exchange.lines.firstOrNull { it.speakerId != id }
            val outcome = when {
                id == choice.targetId && WorldSocialExchangePolicy.response(choice,exchange.lines) == SocialResponse.DECLINE_INVITATION -> "DECLINED"
                ownLine == null -> "OBSERVED"
                otherLine == null -> "SPOKE"
                else -> "EXCHANGED"
            }
            // The excerpt is already validated public speech; no private history is queried.
            val summary = choice.description() + "; " + outcome +
                (otherLine?.let { "; ${previous.partnerName}: ${it.text.take(180)}" } ?: "")
            dao.saveMemory(previous.copy(outcome = outcome, summary = summary.take(360), tick = tick,
                openingKey = ownLine?.openingKey ?: previous.openingKey))
        }
    }

    suspend fun publicContext(event: WorldInteraction): String {
        val choice = decision(event)
        val participants = db.worldInteractionDao().getParticipants(event.id).filter { it.role == InteractionRole.WILD }
        return buildString {
            appendLine("Actual public encounter: ${choice?.description() ?: event.publicReason ?: "Meeting nearby"}.")
            choice?.let {
                appendLine("Initiator: ${it.initiatorId}; intended listener: ${it.targetId}.")
                it.contextDetail?.let { detail -> appendLine("Observed reason: $detail") }
                if (!it.listenerSpeaks) appendLine("${it.targetId} is observing quietly; do not invent a spoken reply for them.")
                if (it.listenerDeclines) appendLine("${it.targetId} has chosen to decline this invitation. Respect that choice; no agreement has occurred.")
            }
            for (participant in participants) {
                val id = participant.individualId
                val actor = db.worldSpawnDao().getByIndividualId(id)
                appendLine("$id: current emotion=${actor?.ecosystemEmotion ?: 0}; ${profile(id).instruction("en")}")
                for (other in participants.filter { it.individualId != id }) {
                    val pair = WildPairBond.create(id, other.individualId)
                    val bond = db.worldEcosystemDao().getBond(pair.individualA, pair.individualB)
                    val previousMeetings = (dao.familiarity(id,other.individualId) - 1).coerceAtLeast(0)
                    appendLine("Relationship $id → ${other.individualId}: affinity=${bond?.affinity ?: 0}, previous meetings=$previousMeetings, previous spoken exchanges=${bond?.chatCount ?: 0}.")
                    dao.withPartner(id, other.individualId).filter { it.eventId != event.id }.take(3).forEach {
                        appendLine("Recorded public memory of ${it.partnerName}: ${it.summary}")
                    }
                }
            }
            append("Use actual recorded context and current speakers. A prior invitation or boast is not a completed activity or battle.")
        }.take(8_000)
    }
}
