package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import org.junit.Assert.*
import org.junit.Test

class WorldWildInitiationPolicyTest {
    private val actor=WorldSpawn(id=1,cardCharacterId=1,individualId="wild",latitude=0.0,longitude=0.0,spawnedAt=0,expiresAt=100_000)
    private val fix=WorldPlayerFix(GeoPoint(0.0,0.0),10_000)

    @Test fun friendlyWildsCanOpenAChatWithoutAPlayerRequest() {
        assertEquals(InteractionType.CHAT,WorldWildInitiationPolicy.kind(DigimonPersonalityType.FRIENDLY,0,true,0.99))
        assertEquals(InteractionType.BATTLE,WorldWildInitiationPolicy.kind(DigimonPersonalityType.FRIENDLY,0,true,0.0))
    }
    @Test fun hostilityCanInitiateAnAttackWithoutTargetConsentButRequiresAnOwnedPartner() {
        assertEquals(InteractionType.BATTLE,WorldWildInitiationPolicy.kind(DigimonPersonalityType.RECKLESS,-30,true,0.0))
        assertEquals(InteractionType.CHAT,WorldWildInitiationPolicy.kind(DigimonPersonalityType.RECKLESS,-30,false,0.0))
        assertTrue(WorldDialoguePolicy.canStartWithoutReply(DialogueIntentType.CHALLENGE_BATTLE,false))
        assertFalse(WorldDialoguePolicy.canStartWithoutReply(DialogueIntentType.CHALLENGE_BATTLE,true))
        assertFalse(WorldDialoguePolicy.canStartWithoutReply(DialogueIntentType.DECLINE_CHALLENGE,false))
    }
    @Test fun playerInitiationRequiresLivePresenceAndFreshPhysicalProximity() {
        assertTrue(WorldWildInitiationPolicy.eligible(actor,fix,10_000,true,false,false))
        assertFalse(WorldWildInitiationPolicy.eligible(actor,fix,10_000,false,false,false))
        assertFalse(WorldWildInitiationPolicy.eligible(actor,fix,10_000,true,true,false))
        assertFalse(WorldWildInitiationPolicy.eligible(actor,fix,10_000,true,false,true))
        assertFalse(WorldWildInitiationPolicy.eligible(actor,fix,40_001,true,false,false))
        val far=RadarWorldGeometry.offset(fix.position,41.0,0.0)
        assertFalse(WorldWildInitiationPolicy.eligible(actor.copy(latitude=far.latitude,longitude=far.longitude),fix,10_000,true,false,false))
    }
    @Test fun sceneTapsFindPublicActivityAndSpeechExpiresWithoutExposingPrivateEvents() {
        val public=EcosystemInteractionSummary("chat",InteractionType.CHAT,InteractionOrigin.AUTONOMOUS,InteractionState.ACTIVE,1,listOf("wild"),100_000,
            utterances=listOf(EcosystemUtterance("wild","Hello!",10)))
        val snapshot=EcosystemSnapshot(session=WorldEcosystemSession(seed=1,tickIndex=10,lastCheckpointAt=0),interactions=listOf(public))
        assertEquals("chat",snapshot.publicInteractionFor("wild")!!.id)
        assertEquals("Hello!",snapshot.speechFor("wild"))
        assertNull(snapshot.copy(session=snapshot.session!!.copy(tickIndex=19)).speechFor("wild"))
        assertNull(snapshot.copy(interactions=listOf(public.copy(origin=InteractionOrigin.DIRECT_PLAYER))).publicInteractionFor("wild"))
        assertNull(snapshot.copy(interactions=listOf(public.copy(state=InteractionState.ENDED))).publicInteractionFor("wild"))
    }

    @Test fun approachCanStartOutsideInteractionRangeOnlyWhenTerritoryMakesItReachable() {
        val home = RadarWorldGeometry.offset(fix.position,50.0,0.0)
        val near = actor.copy(latitude=home.latitude,longitude=home.longitude,homeLatitude=home.latitude,homeLongitude=home.longitude)
        assertFalse(WorldWildInitiationPolicy.eligible(near,fix,10_000,true,false,false))
        assertTrue(WorldWildInitiationPolicy.eligible(near,fix,10_000,true,false,false,allowApproach=true))
        val distantHome = RadarWorldGeometry.offset(fix.position,100.0,0.0)
        assertFalse(WorldWildInitiationPolicy.eligible(near.copy(latitude=distantHome.latitude,longitude=distantHome.longitude,
            homeLatitude=distantHome.latitude,homeLongitude=distantHome.longitude),fix,10_000,true,false,false,allowApproach=true))
    }

    @Test fun closingAnEncounterRetainsSpeechBrieflyWithoutKeepingItInteractable() {
        val snapshot = EcosystemSnapshot(session=WorldEcosystemSession(seed=1,tickIndex=10,lastCheckpointAt=0),
            trailingUtterances=listOf(EcosystemUtterance("wild","I'll keep my distance.",10)))
        assertNull(snapshot.publicInteractionFor("wild"))
        assertEquals("I'll keep my distance.",snapshot.speechFor("wild"))
        assertNull(snapshot.copy(session=snapshot.session!!.copy(tickIndex=19)).speechFor("wild"))
    }
}
