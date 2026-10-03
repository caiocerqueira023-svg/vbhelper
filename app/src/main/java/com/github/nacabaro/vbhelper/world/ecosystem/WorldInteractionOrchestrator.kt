package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.NpcDialogueService
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.world.worldRadarBattleParticipant
import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/** Public conversation and renderer-free combat owner. Every external reply is a revisioned input. */
class WorldInteractionOrchestrator(private val db: AppDatabase, private val chat: ChatRepository) {
    private val gson = Gson()
    private val dao = db.worldInteractionDao()
    private val npcDialogue = NpcDialogueService(chat) { id -> db.digimonIndividualDao().getPersonality(id)?.personalityType }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkMutex = Mutex()
    private val battles = mutableMapOf<String, BattleSimulator>()
    @Volatile private var visible = false
    @Volatile private var autonomousEnabled = false
    @Volatile private var generation = 0L
    private var dialogueJob: Job? = null
    private var lastAutonomousRequestAt = Long.MIN_VALUE
    private val pendingPlayers=java.util.concurrent.atomic.AtomicInteger(0)
    private val playerJobs=java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()
    var commitInput: (suspend (suspend ()->Boolean)->Boolean)? = null

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value; generation++
        if (!value) { dialogueJob?.cancel(); dialogueJob = null; playerJobs.forEach { it.cancel() }; playerJobs.clear() }
    }
    fun invalidateBattleCache() { battles.clear() }
    fun setAutonomousEnabled(enabled: Boolean) {
        autonomousEnabled=enabled
        if(!enabled) { dialogueJob?.cancel();dialogueJob=null }
    }

    suspend fun step(session: WorldEcosystemSession, actors: List<WorldSpawn>, replay: Boolean, playerFix: WorldPlayerFix? = null): Boolean {
        val tick = session.tickIndex
        val repository = WorldInteractionRepository(db) { session.lastCheckpointAt }
        var changed = false
        var open = dao.getOpenInteractions()
        // Positions used for meeting/activation are exactly this logical step, not a display interpolation.
        if (tick % 40L == 0L || open.any { it.state == InteractionState.PROPOSED }) {
            actors.forEach { savePosition(it) }
        }
        if(!replay && visible && autonomousEnabled && tick%20L==0L && open.none { it.isPlayerDirected } &&
            tick-(dao.getLatestPlayerInitiationTick() ?: -WorldWildInitiationPolicy.GLOBAL_COOLDOWN_TICKS)>=WorldWildInitiationPolicy.GLOBAL_COOLDOWN_TICKS) {
            val claimed=dao.getClaimedIndividuals().toSet()
            val hasPartner=db.userCharacterDao().getActiveCharacter().first()!=null
            for(actor in actors.sortedBy { it.individualId }) {
                if(!WorldWildInitiationPolicy.eligible(actor,playerFix,System.currentTimeMillis(),visible,replay,actor.individualId in claimed)) continue
                if(tick-(dao.getLatestPlayerInitiationTick(actor.individualId) ?: -WorldWildInitiationPolicy.INDIVIDUAL_COOLDOWN_TICKS)<WorldWildInitiationPolicy.INDIVIDUAL_COOLDOWN_TICKS) continue
                val personality=db.digimonIndividualDao().getPersonality(actor.individualId)?.personalityType
                    ?: com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.FRIENDLY
                val type=WorldWildInitiationPolicy.kind(personality,actor.ecosystemEmotion,hasPartner,
                    WorldEcosystemEngine.unit(session.seed,actor.individualId,"player-initiative",tick))
                savePosition(actor)
                val id="wild-player:${session.seed}:$tick:${actor.individualId}"
                val initiated=try { repository.initiateWildPlayerInteraction(id,type,actor.id,checkNotNull(playerFix),
                    EcosystemSeed.mix(session.seed,id,"wild-player-event",tick),tick) } catch(rejected:WorldInteractionException) { continue }
                val name=db.worldSpawnDao().getSpawnById(actor.id)?.speciesName ?: "Digimon"
                val line=if(type==InteractionType.BATTLE) publicText("You are in my territory. Defend yourself!","Você está no meu território. Defenda-se!","ここは私の縄張りだ。勝負だ！")
                    else publicText("Hello there! I saw you passing by.","Olá! Vi você passando por aqui.","こんにちは！ 通りかかったのを見かけたよ。")
                dao.insertMessage(WorldInteractionMessage("opening:$id",id,1,actor.individualId,name,line,DialogueTextSource.AUTHORED,
                    "opening:$id",tick,System.currentTimeMillis()))
                val event=initiated.copy(dialogueSequence=1)
                dao.updateInteraction(event)
                if(type==InteractionType.CHAT) WorldChatMemoryRepository(db) { session.lastCheckpointAt }.adoptPlayerConversation(event.id,close=false)
                db.worldSpawnDao().checkpointMovement(actor.individualId,actor.latitude,actor.longitude,null,null,WorldMovementState.HOME,tick+1,tick+80)
                open=open+event
                changed=true
                break
            }
        }
        if (tick % 40L == 0L && (replay || autonomousEnabled)) {
            val byId = actors.associateBy { it.individualId }
            val claimed = dao.getClaimedIndividuals().toSet()
            val bonds = db.worldEcosystemDao().getBonds()
            for (bond in bonds.sortedWith(compareByDescending<WildPairBond> { kotlin.math.abs(it.affinity) }.thenBy { it.individualA })) {
                val a = byId[bond.individualA] ?: continue
                val b = byId[bond.individualB] ?: continue
                if (a.individualId in claimed || b.individualId in claimed || tick < bond.cooldownUntilTick) continue
                val type = when { bond.affinity >= 30 -> InteractionType.CHAT; bond.affinity <= -30 -> InteractionType.BATTLE; else -> continue }
                if (open.count { it.origin == InteractionOrigin.AUTONOMOUS && it.type == type } >= if (type == InteractionType.CHAT) 2 else 1) continue
                val ah = GeoPoint(a.homeLatitude,a.homeLongitude); val bh = GeoPoint(b.homeLatitude,b.homeLongitude)
                val between = RadarWorldGeometry.relative(ah,bh)
                if (between.distanceMeters > a.anchorRadiusMeters + b.anchorRadiusMeters) continue
                val fromA = ((between.distanceMeters + a.anchorRadiusMeters - b.anchorRadiusMeters) / 2).coerceIn(0.0, between.distanceMeters)
                val ratio = if (between.distanceMeters == 0.0) 0.0 else fromA / between.distanceMeters
                val meeting = RadarWorldGeometry.offset(ah, between.northMeters * ratio, between.eastMeters * ratio)
                val id = "npc:${session.seed}:$tick:${a.individualId}:${b.individualId}"
                val event = runCatching { repository.proposeNpcInteraction(id,type,listOf(a.id,b.id),
                    EcosystemSeed.mix(session.seed,id,"social",tick),tick) }.getOrNull() ?: continue
                listOf(a,b).forEach { actor -> db.worldSpawnDao().checkpointMovement(actor.individualId,actor.latitude,actor.longitude,
                    meeting.latitude,meeting.longitude,WorldMovementState.APPROACHING,tick+1,tick+60) }
                db.worldEcosystemDao().saveBond(bond.copy(cooldownUntilTick=tick+80))
                open = open + event; changed = true
                break // Bounded density; do not fill the entire region in one scheduling tick.
            }
        }
        for (event in open.sortedBy { it.id }) {
            if (event.origin != InteractionOrigin.AUTONOMOUS && event.origin != InteractionOrigin.JOINED_PLAYER) continue
            if (event.state == InteractionState.RESERVED || event.state == InteractionState.PLAYER_CONTROLLED && event.type == InteractionType.BATTLE) continue
            if (event.deadlineTick != null && tick >= event.deadlineTick && event.state != InteractionState.RESOLVING) {
                repository.cancel(event.id); changed = true; continue
            }
            if(event.isPlayerBattle) continue // Player handoff owns this 1v1; there is no NPC opponent simulator.
            if (event.state == InteractionState.PROPOSED) {
                if (repository.activateNpcInteraction(event.id,event.revision,tick)) {
                    val activated = dao.getInteraction(event.id)!!
                    dao.updateInteraction(activated.copy(nextActionTick=tick+if(event.type==InteractionType.BATTLE)4 else 1))
                    if (event.type == InteractionType.BATTLE) systemLine(activated, "standoff", publicText("A rivalry turns into a standoff.","Uma rivalidade vira um confronto.","ライバル同士が対峙しています。"),tick)
                    changed = true
                }
                continue
            }
            if (event.type == InteractionType.BATTLE && event.state == InteractionState.ACTIVE && tick >= event.nextActionTick) {
                val record = dao.getNpcBattle(event.id) ?: createBattle(event,tick)
                val simulator = battles.getOrPut(event.id) {
                    NpcBattleAdapter.recover(event, record)
                }
                repeat(6) { simulator.advance(250) }
                val state = simulator.snapshot()
                dao.saveNpcBattle(record.copy(elapsedMillis=state.elapsedMillis,
                    nextRoundTick=if(tick>=record.nextRoundTick)tick+4 else record.nextRoundTick,
                    snapshotJson=if(tick>=record.nextRoundTick || state.result!=null)gson.toJson(NpcBattleSummary.from(state)) else record.snapshotJson))
                changed = true // A resumable battle step commits together with the logical clock.
                if (tick >= record.nextRoundTick || state.result != null) {
                    dao.saveNpcBattle(record.copy(elapsedMillis=state.elapsedMillis,nextRoundTick=tick+4,snapshotJson=gson.toJson(NpcBattleSummary.from(state))))
                    changed = true
                }
                state.result?.let { result ->
                    repository.completeBattle(event.id,result.outcome)
                    battles.remove(event.id)
                    systemLine(event,"outcome",publicText("The battle has ended.","A batalha terminou.","バトルが終了しました。"),tick)
                    changed=true
                }
            } else if (event.type == InteractionType.CHAT && event.state in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) {
                if(event.publicReason?.startsWith("WILD_CHAT:")==true && dao.getParticipants(event.id).size==1) continue // Regular private chat owns all replies.
                val messages = dao.getMessages(event.id)
                val pending=dao.getPendingIntents().filter { it.interactionId==event.id }
                if (event.origin == InteractionOrigin.AUTONOMOUS && messages.count { it.source==DialogueTextSource.MODEL || it.source==DialogueTextSource.AUTHORED } >= 6 && pending.isEmpty()) {
                    repository.endConversationLocked(event,"CONVERSATION_COMPLETE"); changed=true
                } else if (!replay && visible && autonomousEnabled && event.origin==InteractionOrigin.AUTONOMOUS && tick>=event.nextActionTick &&
                    messages.count { it.source==DialogueTextSource.MODEL || it.source==DialogueTextSource.AUTHORED } < 6) {
                    queueExchange(event)
                }
            }
        }
        for (intent in dao.getPendingIntents()) {
            val source=dao.getInteraction(intent.interactionId) ?: continue
            if (tick>=intent.expiresAtTick || source.state.terminal && source.origin==InteractionOrigin.AUTONOMOUS) { dao.updateIntent(intent.copy(status=DialogueIntentStatus.EXPIRED)); changed=true; continue }
            val targets=jsonStrings(intent.targetIdsJson)
            val attacking=WorldDialoguePolicy.canStartWithoutReply(intent.type,intent.sparring)
            if(attacking && "trainer" in targets && !replay && visible && playerFix?.isFresh(System.currentTimeMillis())==true) {
                val attacker=actors.firstOrNull { it.individualId==intent.initiatorId }
                if(attacker!=null && RadarWorldGeometry.relative(playerFix.position,GeoPoint(attacker.latitude,attacker.longitude)).withinInteractionRange) {
                    val battle=repository.transferWildAttackLocked(source,intent.initiatorId,intent.reason,tick)
                    if(battle!=null) { dao.updateIntent(intent.copy(status=DialogueIntentStatus.ACCEPTED,linkedBattleId=battle.id));changed=true }
                }
            } else if ((intent.type == DialogueIntentType.ACCEPT_CHALLENGE || attacking) && "trainer" !in targets) {
                val battle=repository.transferNpcBattleLocked(source,(if(intent.sparring)"SPARRING:" else "DISPUTE:")+intent.reason,tick)
                if (battle!=null) { dao.updateIntent(intent.copy(status=DialogueIntentStatus.ACCEPTED,linkedBattleId=battle.id)); changed=true }
            }
        }
        battles.keys.filter { id -> open.none { it.id==id && !it.state.terminal } }.forEach { battles.remove(it) }
        db.worldEcosystemDao().pruneInputs(tick-2400)
        db.worldEcosystemDao().pruneDens()
        db.worldEcosystemDao().pruneBonds(session.lastCheckpointAt-7*24*60*60*1000L)
        return changed
    }

    private suspend fun savePosition(actor: WorldSpawn) = db.worldSpawnDao().checkpointMovement(actor.individualId,actor.latitude,actor.longitude,
        actor.wanderTargetLatitude,actor.wanderTargetLongitude,actor.movementState,actor.movementTick,actor.nextDecisionTick)

    private suspend fun createBattle(event: WorldInteraction,tick:Long):WorldNpcBattle {
        val definitions=dao.getParticipants(event.id).filter { it.role==InteractionRole.WILD }.map { p ->
            val spawn=db.worldSpawnDao().getSpawnById(p.spawnId!!) ?: error("Missing participant")
            val participant=worldRadarBattleParticipant(spawn).copy(personalityType=db.digimonIndividualDao().getPersonality(p.individualId)?.personalityType
                ?: com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType.FRIENDLY)
            TrainingBattleFactory.definition(TrainingParticipantInput(instanceId=p.individualId,externalCharacterId=participant.externalCharacterId,
                displayName=participant.displayName,stage=participant.stage,maxHealth=participant.maxHp,attack=participant.attackPower,
                vitalStats=participant.vitalStats,attribute=participant.attribute,stableRngKey=p.individualId,personalityType=participant.personalityType,
                techniqueIds=participant.techniqueIds,aiProfile=participant.aiProfile),
                if(p.side==InteractionSide.ALLIED)BattleSide.ALLIED else BattleSide.OPPOSING)
        }
        val empty=WorldNpcBattle(event.id,NpcBattleAdapter.encode(definitions,
            BattleConfiguration(randomSeed=event.seed,defaultPaused=false,arenaRadius=8f)),startTick=tick,nextRoundTick=tick)
        val record=empty.copy(snapshotJson=gson.toJson(NpcBattleSummary.from(NpcBattleAdapter.recover(event,empty).snapshot())))
        dao.saveNpcBattle(record)
        return record
    }

    private fun queueExchange(event:WorldInteraction) {
        val now=System.currentTimeMillis()
        if(dialogueJob?.isActive==true || lastAutonomousRequestAt!=Long.MIN_VALUE && now-lastAutonomousRequestAt<15_000) return
        lastAutonomousRequestAt=now
        val token=generation
        dialogueJob=scope.launch {
            networkMutex.withLock {
                if(!visible || token!=generation) return@withLock
                val participants=dao.getParticipants(event.id).filter { it.role==InteractionRole.WILD }
                val messages=dao.getMessages(event.id)
                val intents=dao.getPendingIntents().filter { it.interactionId==event.id }
                val context=messages.takeLast(12).joinToString("\n") { "${it.id} ${it.speakerId} (${it.speakerName}): ${it.body}" }+
                    "\nPending challenges: "+intents.joinToString { "${it.id}: ${it.type} ${it.initiatorId} ${it.reason}; sparring=${it.sparring}" }
                val exchange=npcDialogue.exchange(participants.map { it.individualId to it.cardCharacterId!! },context,messages.map { it.id }.toSet())
                if(visible && token==generation) acceptInput { commitExchange(event.id,event.revision,"exchange:${event.id}:${event.dialogueSequence}",exchange) }
            }
        }
    }

    suspend fun commitExchange(id:String,revision:Long,requestId:String,exchange:DialogueExchange):Boolean = db.withTransaction {
        val event=dao.getInteraction(id) ?: return@withTransaction false
        if(event.revision!=revision || event.type!=InteractionType.CHAT || event.state !in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) return@withTransaction false
        val participants=dao.getParticipants(id).associateBy { it.individualId }
        if(exchange.source !in listOf(DialogueTextSource.MODEL,DialogueTextSource.AUTHORED) ||
            exchange.lines.map { it.speakerId }.toSet()!=participants.keys ||
            exchange.lines.size!=participants.size || exchange.lines.any { it.text.isBlank() || it.text.length>500 }) return@withTransaction false
        val existing=dao.getMessages(id)
        if(existing.any { it.requestId==requestId }) return@withTransaction false
        val tick=db.worldEcosystemDao().getSession()?.tickIndex ?: event.nextActionTick
        var sequence=event.dialogueSequence
        val committed=exchange.lines.map { line ->
            val name=participants[line.speakerId]?.spawnId?.let { db.worldSpawnDao().getSpawnById(it)?.speciesName } ?: line.speakerId
            WorldInteractionMessage("$requestId:${line.speakerId}",id,++sequence,line.speakerId,name,line.text,exchange.source,requestId,tick,System.currentTimeMillis()).also { dao.insertMessage(it) }
        }
        val pending=dao.getPendingIntents().filter { it.interactionId==id }
        val proposal=exchange.intent?.takeIf { exchange.source==DialogueTextSource.MODEL }?.let { raw ->
            raw.copy(evidenceIds=raw.evidenceIds.map { evidenceId ->
                if(evidenceId=="this:${raw.speakerId}") committed.firstOrNull { it.speakerId==raw.speakerId }?.id ?: evidenceId else evidenceId
            })
        }
        val acceptedProposal=proposal?.let { WorldDialoguePolicy.validate(it,participants.keys,(existing+committed).map { message -> message.id }.toSet(),
            pending.filter { intent -> intent.type==DialogueIntentType.CHALLENGE_BATTLE }.map { intent ->
                PendingDialogueChallenge(intent.id,intent.initiatorId,jsonStrings(intent.targetIdsJson).toSet(),intent.sparring,intent.expiresAtTick)
            },tick,event.origin==InteractionOrigin.JOINED_PLAYER || event.isPlayerDirected) }
        acceptedProposal?.let { accepted ->
            val validated=accepted.proposal
            val refusing=validated.type==DialogueIntentType.DECLINE_CHALLENGE || validated.type==DialogueIntentType.DEESCALATE
            pending.filter { it.id in accepted.challengeIds || refusing &&
                it.type==DialogueIntentType.ACCEPT_CHALLENGE && (
                    it.initiatorId in validated.targetIds && validated.speakerId in jsonStrings(it.targetIdsJson) ||
                    it.initiatorId==validated.speakerId && jsonStrings(it.targetIdsJson).toSet()==validated.targetIds.toSet()) }
                .forEach { dao.updateIntent(it.copy(status=if(refusing || validated.type==DialogueIntentType.CHALLENGE_BATTLE)
                    DialogueIntentStatus.DECLINED else DialogueIntentStatus.ACCEPTED)) }
            val deadline=if(validated.type==DialogueIntentType.ACCEPT_CHALLENGE)
                minOf(tick+20,pending.filter { it.id in accepted.challengeIds }.minOf { it.expiresAtTick }) else tick+20
            dao.insertIntent(WorldDialogueIntent("intent:$requestId",id,revision,validated.speakerId,gson.toJson(validated.targetIds),gson.toJson(validated.evidenceIds),
                validated.type,if(refusing)DialogueIntentStatus.DECLINED else DialogueIntentStatus.PENDING,validated.reason,validated.sparring,deadline))
            if(validated.type==DialogueIntentType.CHALLENGE_BATTLE) systemLine(event,"challenge:$requestId",
                publicText("A challenge was proposed: ","Um desafio foi proposto: ","挑戦が提案されました：")+validated.reason,tick)
        }
        val wilds=participants.keys.sorted()
        val socialDelta=WorldDialoguePolicy.socialDelta(acceptedProposal)
        wilds.forEach { db.worldSpawnDao().adjustEcosystemEmotion(it,socialDelta) }
        if(wilds.size==2) {
            val pair=WildPairBond.create(wilds[0],wilds[1])
            val old=db.worldEcosystemDao().getBond(pair.individualA,pair.individualB) ?: pair
            db.worldEcosystemDao().saveBond(old.copy(chatCount=old.chatCount+1,affinity=(old.affinity+socialDelta).coerceIn(-100,100),lastInteractionAt=System.currentTimeMillis(),lastInteractionTick=tick))
        }
        val next=dao.getMessages(id).maxOfOrNull { it.sequence } ?: sequence
        dao.updateInteraction(event.copy(revision=revision+1,dialogueSequence=next,nextActionTick=tick+10))
        true
    }

    suspend fun joinConversation(id:String,revision:Long,fix:WorldPlayerFix):Boolean=db.withTransaction {
        val event=dao.getInteraction(id) ?: return@withTransaction false
        if(event.type!=InteractionType.CHAT || event.origin==InteractionOrigin.DIRECT_PLAYER || event.state!=InteractionState.ACTIVE || event.revision!=revision || !fix.isFresh(System.currentTimeMillis())) return@withTransaction false
        validateParticipation(event,fix)
        dialogueJob?.cancel()
        dao.updateInteraction(event.copy(state=InteractionState.PLAYER_CONTROLLED,origin=InteractionOrigin.JOINED_PLAYER,revision=revision+1))
        true
    }

    suspend fun sendGroupMessage(id:String,revision:Long,text:String,fix:WorldPlayerFix):Boolean {
        require(text.isNotBlank() && text.length<=1000)
        if(!visible)return false
        if(pendingPlayers.incrementAndGet()>8) { pendingPlayers.decrementAndGet();throw WorldInteractionException(InteractionFailure.BUSY) }
        val playerJob=currentCoroutineContext()[Job]
        playerJob?.let { playerJobs.add(it) }
        try {
        var acceptedEvent:WorldInteraction?=null
        if(!acceptInput { db.withTransaction {
            val current=dao.getInteraction(id) ?: return@withTransaction false
            if(!visible || current.type!=InteractionType.CHAT || current.state!=InteractionState.PLAYER_CONTROLLED || current.revision!=revision) return@withTransaction false
            validateParticipation(current,fix)
            val message=WorldInteractionMessage("player:$id:${current.dialogueSequence+1}",id,current.dialogueSequence+1,"trainer",
                publicText("Trainer","Tamer","テイマー"),text,DialogueTextSource.PLAYER,"player:$id:${current.dialogueSequence+1}",
                db.worldEcosystemDao().getSession()?.tickIndex ?: 0,System.currentTimeMillis())
            dao.insertMessage(message)
            acceptedEvent=current.copy(revision=revision+1,dialogueSequence=message.sequence).also { dao.updateInteraction(it) }
            true
        } })return false
        val event=checkNotNull(acceptedEvent)
        dialogueJob?.cancel() // Player requests take precedence over queued autonomous exchanges.
        val token=generation
        networkMutex.withLock {
            if(!visible) return false
            val speakers=dao.getParticipants(id)
            val messages=dao.getMessages(id)
            val pending=dao.getPendingIntents().filter { it.interactionId==id }
            val exchange=npcDialogue.exchange(speakers.map { it.individualId to it.cardCharacterId!! },
                messages.takeLast(16).joinToString("\n") { "${it.id} ${it.speakerId}: ${it.body}" }+
                    "\nPending challenges: "+pending.joinToString { "${it.id}: ${it.type} ${it.initiatorId} ${it.reason}; sparring=${it.sparring}" },messages.map { it.id }.toSet())
            if(visible && token==generation) acceptInput { commitExchange(id,event.revision,"player-reply:$id:${event.dialogueSequence}",exchange) }
        }
        return true
        } finally { pendingPlayers.decrementAndGet();playerJob?.let { playerJobs.remove(it) } }
    }

    suspend fun declineIntent(id:String) = db.withTransaction {
        dao.getIntent(id)?.takeIf { it.status==DialogueIntentStatus.PENDING && "trainer" in jsonStrings(it.targetIdsJson) }
            ?.let { dao.updateIntent(it.copy(status=DialogueIntentStatus.DECLINED)) }
    }

    suspend fun recapOnDemand(id:String) {
        if(!visible)return
        val current=dao.getInteraction(id) ?: return
        if(!current.state.terminal || dao.getMessages(id).any { it.source==DialogueTextSource.RECAP })return
        if(current.origin==InteractionOrigin.DIRECT_PLAYER && current.type==InteractionType.CHAT)return
        val facts="${current.type}; ${current.state}; ${current.terminalReason.orEmpty()}; reason=${current.publicReason.orEmpty()}\n"+
            dao.getMessages(id).filter { it.source==DialogueTextSource.SYSTEM }.joinToString("\n") { it.body }
        val token=generation
        networkMutex.withLock {
            if(!visible || token!=generation)return@withLock
            val raw=runCatching { withTimeout(15_000) { chat.generateRadarRecap(facts) } }.getOrElse {
                if(it is CancellationException && it !is TimeoutCancellationException)throw it
                publicText("This interaction has ended. Its public record remains available.","Esta interação terminou. O registro público continua disponível.","この交流は終了しました。公開記録は引き続き閲覧できます。")
            }.take(700)
            if(!visible || token!=generation)return@withLock
            db.withTransaction {
                if(dao.getMessages(id).none { it.source==DialogueTextSource.RECAP }) {
                    val messages=dao.getMessages(id)
                    dao.insertMessage(WorldInteractionMessage("recap:$id",id,(messages.maxOfOrNull { it.sequence } ?: 0)+1,"system",
                        publicText("Offscreen summary","Resumo fora de tela","不在中の概要"),raw,DialogueTextSource.RECAP,"recap:$id",
                        db.worldEcosystemDao().getSession()?.tickIndex ?: 0,System.currentTimeMillis()))
                }
            }
        }
    }

    suspend fun recordPrivateProposal(sourceId:String?,individualId:String,cardId:Long,proposal:DialogueProposal,sourceMessages:List<Long>,userMessageId:Long?=null):String? = db.withTransaction {
        if(proposal.speakerId!=individualId || proposal.targetIds!=listOf("trainer")) return@withTransaction null
        val transcript=db.chatDao().getMessagesSync(individualId)
        val history=transcript.map { it.id }.toSet()
        if(sourceMessages.isEmpty() || sourceMessages.any { it !in history }) return@withTransaction null
        if(proposal.type==DialogueIntentType.DECLINE_CHALLENGE || proposal.type==DialogueIntentType.DEESCALATE) {
            dao.getPendingIntents().filter { it.initiatorId==individualId && "trainer" in jsonStrings(it.targetIdsJson) &&
                dao.getInteraction(it.interactionId)?.origin==InteractionOrigin.DIRECT_PLAYER }
                .forEach { dao.updateIntent(it.copy(status=DialogueIntentStatus.DECLINED)) }
            return@withTransaction null
        }
        val userTurn=userMessageId?.takeIf { id -> transcript.lastOrNull { it.role=="user" }?.id==id }
        val disposition=WorldPrivateDuelPolicy.classify(proposal,individualId,userTurn,sourceMessages.toSet()) ?: return@withTransaction null
        val tick=db.worldEcosystemDao().getSession()?.tickIndex ?: 0
        val id=sourceId ?: "private-context:$individualId:${sourceMessages.last()}"
        if(sourceId==null && dao.getInteraction(id)==null) {
            dao.insertInteraction(WorldInteraction(id,InteractionType.CHAT,InteractionOrigin.DIRECT_PLAYER,InteractionState.ENDED,0,
                startTick=tick,nextActionTick=tick,createdAt=System.currentTimeMillis(),expiresAt=System.currentTimeMillis()+300_000,
                endedAt=System.currentTimeMillis()))
            dao.insertParticipants(listOf(WorldInteractionParticipant(id,individualId,InteractionRole.WILD,InteractionSide.NEUTRAL,cardCharacterId=cardId)))
        }
        val intentId="private-challenge:$individualId:${sourceMessages.last()}"
        dao.insertIntent(WorldDialogueIntent(intentId,id,dao.getInteraction(id)?.revision ?: 0,individualId,
            gson.toJson(listOf("trainer")),gson.toJson(sourceMessages.map { "private:$it" }),
            if(disposition==PrivateDuelDisposition.ACCEPTED_FRIENDLY) DialogueIntentType.ACCEPT_CHALLENGE else DialogueIntentType.CHALLENGE_BATTLE,
            DialogueIntentStatus.PENDING,proposal.reason,disposition!=PrivateDuelDisposition.HOSTILE_ATTACK,tick+80))
        intentId
    }

    suspend fun prepareHumanChallengeSource(intentId:String,fix:WorldPlayerFix):String? = db.withTransaction {
        val intent=dao.getIntent(intentId) ?: return@withTransaction null
        val source=dao.getInteraction(intent.interactionId) ?: return@withTransaction null
        if(intent.status!=DialogueIntentStatus.PENDING || "trainer" !in jsonStrings(intent.targetIdsJson) || source.type!=InteractionType.CHAT || source.state.terminal) return@withTransaction null
        validateParticipation(source,fix)
        val tick=db.worldEcosystemDao().getSession()?.tickIndex ?: 0
        if(tick>=intent.expiresAtTick)return@withTransaction null
        if(dao.getParticipants(source.id).count { it.role==InteractionRole.WILD }==1) {
            val battle=WorldInteractionRepository(db).transferWildAttackLocked(source,intent.initiatorId,intent.reason,tick,sparring=true)
                ?: return@withTransaction null
            dao.updateIntent(intent.copy(status=DialogueIntentStatus.ACCEPTED,linkedBattleId=battle.id))
            return@withTransaction battle.id
        }
        val battle=WorldInteractionRepository(db).transferNpcBattleLocked(source,
            (if(intent.sparring)"SPARRING:" else "DISPUTE:")+"PLAYER_CHALLENGE",tick) ?: return@withTransaction null
        val staged=battle.copy(state=InteractionState.ACTIVE,nextActionTick=Long.MAX_VALUE,revision=battle.revision+1)
        dao.updateInteraction(staged)
        createBattle(staged,tick)
        dao.updateIntent(intent.copy(status=DialogueIntentStatus.ACCEPTED,linkedBattleId=battle.id))
        battle.id
    }

    private suspend fun validateParticipation(event:WorldInteraction,fix:WorldPlayerFix) {
        if(!fix.isFresh(System.currentTimeMillis())) throw WorldInteractionException(InteractionFailure.STALE_LOCATION)
        dao.getParticipants(event.id).filter { it.role==InteractionRole.WILD }.forEach { p ->
            val spawn=db.worldSpawnDao().getByIndividualId(p.individualId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
            if(dao.getClaim(p.individualId)?.interactionId!=event.id || !RadarWorldGeometry.relative(fix.position,GeoPoint(spawn.latitude,spawn.longitude)).withinInteractionRange)
                throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
        }
    }
    private suspend fun systemLine(event:WorldInteraction,key:String,text:String,tick:Long) {
        val existing=dao.getMessages(event.id)
        if(existing.any { it.requestId==key }) return
        dao.insertMessage(WorldInteractionMessage("system:${event.id}:$key",event.id,(existing.maxOfOrNull { it.sequence } ?: 0)+1,
            "system","Radar",text,DialogueTextSource.SYSTEM,key,tick,System.currentTimeMillis()))
    }
    private fun jsonStrings(json:String)=gson.fromJson(json,Array<String>::class.java).toList()
    private suspend fun acceptInput(action:suspend ()->Boolean)=commitInput?.invoke(action) ?: action()
    private fun publicText(en:String,pt:String,ja:String)=when { PromptLocalization.currentLanguageTag().startsWith("pt")->pt; PromptLocalization.currentLanguageTag().startsWith("ja")->ja; else->en }
}
