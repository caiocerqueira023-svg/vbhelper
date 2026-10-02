package com.github.nacabaro.vbhelper.screens.worldScreen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.chat.WorldDialogueCodec
import com.github.nacabaro.vbhelper.components.*
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.ecosystem.*
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.*

/** The regular chat shell, backed by the existing public event rather than a private history. */
@Composable
fun WorldConversationScreen(navController: NavController, interactionId: String) {
    val context=LocalContext.current
    val resources=LocalResources.current
    val app=context.applicationContext as VBHelper
    val db=app.container.db
    val coordinator=app.container.worldEcosystemCoordinator
    val orchestrator=app.container.worldInteractionOrchestrator
    val snapshot by coordinator.snapshot.collectAsState()
    val event by remember(interactionId) { db.worldInteractionDao().observeInteraction(interactionId) }.collectAsState(null)
    val messages by remember(interactionId) { db.worldInteractionDao().observeMessages(interactionId) }.collectAsState(emptyList())
    val intents by remember(interactionId) { db.worldInteractionDao().observeIntents(interactionId) }.collectAsState(emptyList())
    val battle by remember(interactionId) { db.worldInteractionDao().observeConversationBattle(interactionId) }.collectAsState(null)
    val owned by db.userCharacterDao().getActiveCharacter().collectAsState(null)
    val lifecycle=LocalLifecycleOwner.current
    val lease=remember(interactionId) { "world-conversation:${java.util.UUID.randomUUID()}" }
    val scope=rememberCoroutineScope()
    var resumed by remember { mutableStateOf(false) }
    var names by remember(interactionId) { mutableStateOf(emptyMap<String,String>()) }
    var input by rememberSaveable(interactionId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var returning by remember { mutableStateOf(false) }
    val list=rememberLazyListState()
    var previousCount by remember(interactionId) { mutableIntStateOf(0) }
    val canParticipate=resumed && worldConversationCanParticipate(event,snapshot)

    fun returnToRadar(id: String) {
        if(returning) return
        returning=true
        val world=runCatching { navController.getBackStackEntry(NavigationItems.World.route) }.getOrNull()
        if(world==null) navController.navigate(NavigationItems.World.route)
        navController.getBackStackEntry(NavigationItems.World.route).savedStateHandle["radar-interaction"]=id
        navController.popBackStack(NavigationItems.World.route,false)
    }

    fun showFailure(failure: Throwable) {
        if(failure is CancellationException) throw failure
        error=when(failure) {
            is RadarCommandException -> resources.getString(failure.messageResource())
            is WorldInteractionException -> resources.getString(failure.messageResource())
            else -> failure.message ?: resources.getString(R.string.ui_world_encounter_unavailable)
        }
    }

    LaunchedEffect(interactionId,lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                withContext(Dispatchers.IO) { coordinator.acquire(lease,autonomous=false) }
                resumed=true
                awaitCancellation()
            } finally {
                resumed=false
                withContext(NonCancellable+Dispatchers.IO) { coordinator.release(lease) }
            }
        }
    }
    LaunchedEffect(interactionId) {
        val privateTarget=withContext(Dispatchers.IO) { WorldChatMemoryRepository(db).adoptPlayerConversation(interactionId) }
        if(privateTarget!=null) {
            navController.navigate(NavigationItems.WildContact.route.replace("{individualId}",android.net.Uri.encode(privateTarget.individualId))
                .replace("{cardCharacterId}",privateTarget.cardCharacterId.toString())) {
                popUpTo(NavigationItems.WorldConversation.route) { inclusive=true }
                launchSingleTop=true
            }
            return@LaunchedEffect
        }
        names=withContext(Dispatchers.IO) { db.worldInteractionDao().getParticipants(interactionId)
            .filter { it.role==InteractionRole.WILD }.associate { participant ->
                participant.individualId to (participant.spawnId?.let { db.worldSpawnDao().getSpawnById(it)?.speciesName }
                    ?: participant.cardCharacterId?.let { db.speciesProfileDao().getByCardCharacterId(it)?.speciesName } ?: "Digimon")
            } }
    }
    LaunchedEffect(messages.size) {
        if(messages.isNotEmpty() && (previousCount==0 || (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)>=previousCount-1))
            list.scrollToItem(messages.lastIndex)
        previousCount=messages.size
    }
    LaunchedEffect(battle?.id,resumed) { if(resumed) battle?.let { returnToRadar(it.id) } }

    val permitted=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    DisposableEffect(resumed,permitted,snapshot.leaseEpoch) {
        if(!resumed || !permitted) onDispose {} else {
            val client=LocationServices.getFusedLocationProviderClient(context)
            val epoch=snapshot.leaseEpoch
            var attached=true
            fun accept(location: Location) {
                if(!attached) return
                val position=GeoPoint.fromOrNull(location.latitude,location.longitude) ?: return
                val fix=WorldPlayerFix(position,location.time,location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() && it>=0f })
                scope.launch { withContext(Dispatchers.IO) { if(attached) coordinator.acceptPlayerFix(lease,epoch,fix) } }
            }
            val callback=object:LocationCallback() {
                override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::accept) }
            }
            client.lastLocation.addOnSuccessListener { it?.let(::accept) }
            client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,2_000)
                .setMinUpdateIntervalMillis(1_000).setMinUpdateDistanceMeters(0f).setWaitForAccurateLocation(false).build(),callback,context.mainLooper)
            onDispose { attached=false;client.removeLocationUpdates(callback) }
        }
    }

    val group=names.size>1
    Scaffold(topBar={ TopBanner(text=if(group) stringResource(R.string.ui_world_group_chat)
        else names.values.firstOrNull() ?: stringResource(R.string.ui_world_wild_digimon),onBackClick={navController.popBackStack()}) },
        contentWindowInsets=WindowInsets.statusBars) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=16.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(group || !canParticipate) ChatContextPanel {
                if(group) Text(names.values.joinToString(" · "),style=MaterialTheme.typography.labelLarge,color=TextSecondaryOnDark)
                if(!canParticipate) Text(stringResource(when {
                    event==null || event?.state?.terminal==true -> R.string.ui_world_interaction_ended
                    snapshot.status!=EcosystemStatus.READY -> R.string.ui_world_updating
                    snapshot.playerFix?.isFresh(snapshot.observedAt)!=true -> R.string.ui_world_location_stale
                    else -> R.string.ui_world_too_far
                }),style=MaterialTheme.typography.bodySmall,color=TextSecondaryOnDark)
            }
            ChatHistoryPanel(isEmpty=messages.isEmpty(),emptyMessage=stringResource(R.string.ui_chat_empty),
                modifier=Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state=list,modifier=Modifier.fillMaxSize().padding(horizontal=8.dp)) {
                    items(messages,key={it.id}) { message ->
                        val text=if(message.source==DialogueTextSource.PLAYER) message.body else WorldDialogueCodec.visibleText(message.body,message.speakerId)
                            ?: WorldDialogueCodec.unreadableReply(PromptLocalization.currentLanguageTag())
                        ChatMessageBubble(text=text,isUser=message.source==DialogueTextSource.PLAYER,
                            authorLabel=message.speakerName.takeIf { group || message.source in listOf(DialogueTextSource.SYSTEM,DialogueTextSource.RECAP) })
                    }
                }
            }
            intents.filter { it.status==DialogueIntentStatus.PENDING && it.sparring && it.type==DialogueIntentType.CHALLENGE_BATTLE &&
                runCatching { "trainer" in Gson().fromJson(it.targetIdsJson,Array<String>::class.java) }.getOrDefault(false) }.forEach { intent ->
                Text(intent.reason,style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    VitalButton(onClick={ scope.launch {
                        try {
                            val frame=coordinator.snapshot.value
                            val id=withContext(Dispatchers.IO) { coordinator.executeCommand(lease,RadarCommand(frame.commandStamp!!,
                                RadarCommandKind.EVENT_PARTICIPATION,interactionId=interactionId)) { ctx ->
                                orchestrator.prepareHumanChallengeSource(intent.id,ctx.playerFix)
                            }.getOrThrow() } ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            returnToRadar(id)
                        } catch(failure:Exception) { showFailure(failure) }
                    } },enabled=canParticipate && owned!=null) { Text(stringResource(R.string.ui_world_accept_challenge)) }
                    TextButton(onClick={scope.launch { withContext(Dispatchers.IO) { orchestrator.declineIntent(intent.id) } }}) {
                        Text(stringResource(R.string.ui_world_decline_challenge))
                    }
                }
            }
            ChatComposer(value=input,onValueChange={input=it},placeholder=stringResource(R.string.ui_chat_placeholder),sendLabel=stringResource(R.string.ui_send),
                sending=sending,enabled=canParticipate,errorMessage=error,maxLength=1000,onSend={
                    val text=input.trim()
                    if(text.isNotEmpty() && !sending) {
                        sending=true;error=null
                        scope.launch {
                            try {
                                val frame=coordinator.snapshot.value
                                val prepared=withContext(Dispatchers.IO) { coordinator.executeCommand(lease,RadarCommand(frame.commandStamp!!,
                                    RadarCommandKind.EVENT_PARTICIPATION,interactionId=interactionId)) { ctx ->
                                    val current=db.worldInteractionDao().getInteraction(interactionId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                                    if(current.state==InteractionState.ACTIVE && !orchestrator.joinConversation(current.id,current.revision,ctx.playerFix))
                                        throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                                    val joined=db.worldInteractionDao().getInteraction(interactionId)!!
                                    joined.revision to ctx.playerFix
                                }.getOrThrow() }
                                val sent=withContext(Dispatchers.IO) { orchestrator.sendGroupMessage(interactionId,prepared.first,text,prepared.second) }
                                if(!sent) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                                if(input.trim()==text) input=""
                            } catch(failure:Exception) { showFailure(failure) } finally { sending=false }
                        }
                    }
                })
        }
    }
}
