package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.ChatComposer
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.world.ecosystem.*
import com.github.nacabaro.vbhelper.ui.theme.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorldInteractionOverlay(
    id:String,db:AppDatabase,snapshot:EcosystemSnapshot,onClose:()->Unit,
    onJoinBattle:(Long,String?,Long?)->Unit,
    hasOwnedPartner:Boolean=false,onDefend:()->Unit={},onOpenConversation:()->Unit={}
) {
    val event by remember(id) { db.worldInteractionDao().observeInteraction(id) }.collectAsState(null)
    val messages by remember(id) { db.worldInteractionDao().observeMessages(id) }.collectAsState(emptyList())
    val npc by remember(id) { db.worldInteractionDao().observeNpcBattle(id) }.collectAsState(null)
    var participants by remember(id) { mutableStateOf(emptyList<WorldInteractionParticipant>()) }
    var owned by remember(id) { mutableStateOf(emptyList<Pair<Long,String>>()) }
    var secondOwned by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    var activeName by remember(id) { mutableStateOf("") }
    var previewAlly by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var previewBoth by rememberSaveable(id) { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable(id) { mutableStateOf(false) }
    val list=rememberLazyListState()
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(id) {
        withContext(Dispatchers.IO) {
            participants=db.worldInteractionDao().getParticipants(id)
            val profiles=db.userCharacterDao().getAllBattleParticipantProfiles()
            val active=db.userCharacterDao().getActiveCharacter().first()
            activeName=profiles.firstOrNull { it.sourceCharacterId==active?.id }?.displayName.orEmpty()
            owned=profiles.filter { it.sourceCharacterId!=active?.id }.map { it.sourceCharacterId to it.displayName }
        }
    }
    LaunchedEffect(list) { snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index==list.layoutInfo.totalItemsCount-1 }.collect { followBottom=it } }
    LaunchedEffect(messages.size) { if(followBottom && messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }
    val current=event
    if(current?.type!=InteractionType.BATTLE) {
        LaunchedEffect(current?.type) { if(current?.type==InteractionType.CHAT) onOpenConversation() }
        return
    }
    val wilds=participants.filter { it.role==InteractionRole.WILD }
    val joinable=current!=null && !current.state.terminal && snapshot.status==EcosystemStatus.READY &&
        snapshot.playerFix?.isFresh(snapshot.observedAt)==true && wilds.all { p ->
            snapshot.individuals.firstOrNull { it.individualId==p.individualId }?.let { actor ->
                com.github.nacabaro.vbhelper.world.RadarWorldGeometry.relative(snapshot.playerFix.position,actor.position).withinInteractionRange
            }==true
        }
    val battleState=npc?.let { runCatching {
        Gson().fromJson(it.snapshotJson,NpcBattleSummary::class.java).also { summary -> summary.alliedMembers.size;summary.opposingMembers.size }
    }.getOrNull() }
    val names=wilds.associate { p -> p.individualId to (messages.lastOrNull { it.speakerId==p.individualId }?.speakerName ?: p.individualId.takeLast(6)) }
    Surface(color=SurfaceDeepPurple,contentColor=TextPrimaryOnDark,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(if(current?.type==InteractionType.BATTLE) R.string.ui_world_spectate_battle else R.string.ui_world_spectate_chat),
                    style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick=onClose,modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_stop_spectating)) }
            }
            Text(wilds.joinToString(" · ") { names[it.individualId].orEmpty() },style=MaterialTheme.typography.bodyMedium)
            if(current==null || current.state.terminal) Text(stringResource(R.string.ui_world_interaction_ended))
            if(current?.type==InteractionType.BATTLE) {
                if(wilds.size==1 && !hasOwnedPartner) Text(stringResource(R.string.ui_world_battle_choose_active),style=MaterialTheme.typography.bodySmall)
                if(wilds.size==1 && current.isPlayerBattle && current.state==InteractionState.ACTIVE)
                    TextButton(onClick=onDefend,enabled=joinable && hasOwnedPartner && (snapshot.session?.tickIndex ?: 0)>=current.nextActionTick,
                        modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(if(current.isWildAttack) R.string.ui_world_defend_attack else R.string.ui_world_encounter_fight)) }
                battleState?.let { state -> (state.alliedMembers+state.opposingMembers).forEach { fighter ->
                    Text(stringResource(R.string.ui_world_battle_progress,(state.elapsedMillis/6000+1).toInt(),fighter.health,fighter.maxHealth))
                } }
            }
            if(messages.isNotEmpty()) TextButton(onClick={showHistory=!showHistory},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_conversation_record)) }
            if(showHistory) LazyColumn(state=list,modifier=Modifier.fillMaxWidth().heightIn(min=80.dp,max=180.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(messages,key={it.id}) { message ->
                    Column {
                        Text(message.speakerName,style=MaterialTheme.typography.labelLarge,color=VitalCyan)
                        Text(message.body,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if(wilds.size==2 && current.state==InteractionState.ACTIVE && current.origin==InteractionOrigin.AUTONOMOUS && battleState?.result==null) {
                wilds.forEach { p -> VitalButton(onClick={previewAlly=p.individualId;previewBoth=false;previewVisible=true},enabled=joinable,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_side_with,names[p.individualId].orEmpty())) } }
                owned.forEach { (characterId,name) -> Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected=secondOwned==characterId,onClick={secondOwned=characterId})
                    Text(name.ifBlank { "#$characterId" },modifier=Modifier.padding(top=12.dp))
                } }
                VitalButton(onClick={previewBoth=true;previewAlly=null;previewVisible=true},enabled=joinable && secondOwned!=null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_challenge_both)) }
                if(secondOwned==null) Text(stringResource(R.string.ui_world_missing_teammate),style=MaterialTheme.typography.bodySmall)
            }
            if(!joinable && current?.state?.terminal==false) Text(stringResource(if(snapshot.playerFix?.isFresh(snapshot.observedAt)!=true)R.string.ui_world_location_stale else R.string.ui_world_too_far),style=MaterialTheme.typography.bodySmall)
        }
    }
    if(previewVisible && current!=null) AlertDialog(onDismissRequest={previewVisible=false},title={Text(stringResource(R.string.ui_world_team_preview))},
        text={Text(if(previewBoth) "$activeName + ${owned.firstOrNull { it.first==secondOwned }?.second}  ×  ${names.values.joinToString(" + ")}" else
            "$activeName + ${names[previewAlly]}  ×  ${names.filterKeys { it!=previewAlly }.values.joinToString()}")},
        confirmButton={VitalButton(onClick={previewVisible=false;onJoinBattle(current.revision,previewAlly,if(previewBoth)secondOwned else null)},enabled=joinable) { Text(stringResource(R.string.ui_world_accept_challenge)) }},
        dismissButton={TextButton(onClick={previewVisible=false}) { Text(stringResource(R.string.ui_cancel)) }})
}
