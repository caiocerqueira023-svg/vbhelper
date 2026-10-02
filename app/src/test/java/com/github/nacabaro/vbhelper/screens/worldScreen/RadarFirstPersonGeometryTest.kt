package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.world.RadarRelativePosition
import org.junit.Assert.*
import org.junit.Test

class RadarFirstPersonGeometryTest {
    @Test fun compressionIsContinuousMonotonicAndPreservesTheNearRange() {
        assertEquals(0.0, radarPresentationDistance(0.0), 0.0)
        assertEquals(4.0, radarPresentationDistance(40.0), 0.0)
        assertEquals(40.0, radarPresentationDistance(1000.0), 0.001)
        assertEquals(radarPresentationDistance(40.0), radarPresentationDistance(40.000001), 0.000001)
        var previous = 0.0
        for (d in 0..1000) { val next = radarPresentationDistance(d.toDouble()); assertTrue(next >= previous); previous = next }
    }
    @Test fun northIsNegativeZAndEastPositiveX() {
        val north = RadarRelativePosition(40.0, 0.0, 40.0, 0.0).scenePoint()
        val east = RadarRelativePosition(0.0, 40.0, 40.0, 90.0).scenePoint()
        assertEquals(-4f, north.z, 0.001f)
        assertEquals(4f, east.x, 0.001f)
    }
    @Test fun billboardFrontFacesThePlayerFromEveryCardinalDirectionAndHeight() {
        val points=listOf(RadarScenePoint(0f,-4f),RadarScenePoint(4f,0f),RadarScenePoint(0f,4f),
            RadarScenePoint(-4f,0f),RadarScenePoint(3f,-2f))
        for(point in points) for(height in listOf(0.85f,1.05f,1.4f)) {
            val pose=radarFirstPersonBillboard(point,height,0.85)
            val yaw=pose.yawRadians.toDouble()
            val pitch=pose.pitchRadians.toDouble()
            val normal=doubleArrayOf(kotlin.math.sin(yaw)*kotlin.math.cos(pitch),-kotlin.math.sin(pitch),kotlin.math.cos(yaw)*kotlin.math.cos(pitch))
            val toPlayer=doubleArrayOf(-point.x.toDouble(),0.85-pose.centerY,-point.z.toDouble())
            val length=kotlin.math.sqrt(toPlayer.sumOf { it*it })
            normal.indices.forEach { axis -> assertEquals(toPlayer[axis]/length,normal[axis],0.00001) }
        }
    }
    @Test fun projectedBillboardCornersUseTheSameCenterAndBasisAsTheSprite() {
        val pose=radarFirstPersonBillboard(RadarScenePoint(3f,-2f),1.4f,0.85)
        val center=pose.worldPoint(0f,0f)
        val left=pose.worldPoint(-0.7f,0.7f)
        val right=pose.worldPoint(0.7f,-0.7f)
        assertEquals(3f,center[0],0.00001f)
        assertEquals(pose.centerY,center[1],0.00001f)
        assertEquals(-2f,center[2],0.00001f)
        center.indices.forEach { axis -> assertEquals(center[axis],(left[axis]+right[axis])/2,0.00001f) }
        val toPlayer=floatArrayOf(-center[0],0.85f-center[1],-center[2])
        for(basis in listOf(pose.worldPoint(1f,0f),pose.worldPoint(0f,1f))) {
            assertEquals(0f,center.indices.sumOf { axis -> ((basis[axis]-center[axis])*toPlayer[axis]).toDouble() }.toFloat(),0.00001f)
        }
    }
    @Test fun coincidentBillboardCoordinatesHaveAFiniteOrientation() {
        val pose=radarFirstPersonBillboard(RadarScenePoint(0f,0f),1.4f,0.85)
        assertTrue(pose.yawRadians.isFinite())
        assertTrue(pose.pitchRadians.isFinite())
        assertTrue(pose.worldPoint(0.5f,0.5f).all { it.isFinite() })
    }
    @Test fun closeRangeMeshAndHitTargetUseTheSameSafeDisplayPoint() {
        val coincident = RadarRelativePosition(0.0, 0.0, 0.0, 0.0)
        assertEquals(RadarScenePoint(0f, -1.8f), coincident.firstPersonDisplayPoint())
        val near = RadarRelativePosition(1.0, 0.0, 1.0, 0.0)
        assertEquals(coincident.firstPersonDisplayPoint(), near.firstPersonDisplayPoint())
        val distant = RadarRelativePosition(40.0, 0.0, 40.0, 0.0)
        assertEquals(distant.scenePoint(), distant.firstPersonDisplayPoint())
    }
    @Test fun closeDigimonStayClearOfTheCameraWithoutLosingTheirBearing() {
        val east = RadarRelativePosition(0.0, 1.0, 1.0, 90.0).firstPersonDisplayPoint()
        assertEquals(1.8f, east.x, 0.001f)
        assertEquals(0f, east.z, 0.001f)
        val boundary = RadarRelativePosition(18.0, 0.0, 18.0, 0.0)
        assertEquals(boundary.scenePoint(), boundary.firstPersonDisplayPoint())
    }
    @Test fun cameraCanLookDownAtNearbyDigimonAndPitchIsBounded() {
        val pose = radarFirstPersonCamera(0f, -45f)
        assertTrue(pose.eyeHeight < 1.0)
        assertTrue(pose.targetY < pose.eyeHeight)
        assertEquals(45f, radarPitchAfterDrag(-50f, 1000f, 300f), 0f)
        assertEquals(-60f, radarPitchAfterDrag(40f, -1000f, 300f), 0f)
        assertTrue(radarFirstPersonSpriteScale(3) > 1f)
    }
    @Test fun verticalDraggingUsesTheReversedLookDirectionAndButtonsKeepTheirMeaning() {
        assertTrue(radarPitchAfterDrag(0f,30f,300f)>0f)
        assertTrue(radarPitchAfterDrag(0f,-30f,300f)<0f)
        assertTrue(radarFirstPersonCamera(0f,radarPitchStep(0f,true)).targetY>radarFirstPersonCamera(0f,0f).targetY)
        assertTrue(radarFirstPersonCamera(0f,radarPitchStep(0f,false)).targetY<radarFirstPersonCamera(0f,0f).targetY)
    }
    @Test fun rendererRequestsAndReleaseAcknowledgmentsPublishObservableState() {
        val ownership = RadarRendererOwnership()
        val initial = ownership.state.value
        val token = ownership.request(RadarRendererOwner.FIRST_PERSON)
        assertNotEquals(initial, ownership.state.value)
        ownership.request(RadarRendererOwner.NONE)
        assertTrue(ownership.state.value.releasing)
        ownership.released(RadarRendererOwner.FIRST_PERSON, token)
        assertEquals(RadarRendererOwner.NONE, ownership.state.value.owner)
        assertFalse(ownership.state.value.releasing)
    }
    @Test fun meetingParticipantsHaveDistinctDisplayPositionsAndReducedMotionStopsLunges() {
        val relative=RadarRelativePosition(0.0,0.0,0.0,0.0)
        val left=relative.activityDisplayPoint(0,2,0f)
        val right=relative.activityDisplayPoint(1,2,0f)
        assertTrue(right.x-left.x>=1.4f)
        assertEquals(left.z,right.z,0f)
        assertEquals(0f,radarActivityLunge(900_000_000,0,false),0f)
        assertEquals(1f,radarActivityLunge(900_000_000,0,true),0.001f)
        assertEquals(0f,radarActivityLunge(900_000_000,1,true),0f)
    }
    @Test fun incompleteAnimationAssetsAlwaysKeepAnAvailablePoseVisible() {
        assertEquals("idle", radarSpritePose(walking = true, motion = true, alternate = true, available = setOf("idle")))
        assertEquals("walk", radarSpritePose(walking = true, motion = true, alternate = true, available = setOf("idle", "walk")))
        assertEquals("walk2", radarSpritePose(walking = true, motion = true, alternate = true, available = setOf("idle", "walk", "walk2")))
        assertEquals("idle", radarSpritePose(walking = true, motion = false, alternate = true, available = setOf("idle", "idle2", "walk")))
    }
    @Test fun nextRendererCannotAcquireBeforeThePreviousGenerationDetaches() {
        val machine = RadarRendererOwnership()
        val fp = machine.request(RadarRendererOwner.FIRST_PERSON)
        machine.request(RadarRendererOwner.BATTLE)
        assertEquals(RadarRendererOwner.FIRST_PERSON, machine.owner)
        assertTrue(machine.released(RadarRendererOwner.FIRST_PERSON, fp))
        assertEquals(RadarRendererOwner.BATTLE, machine.owner)
        val battle = machine.generation
        machine.request(RadarRendererOwner.FIRST_PERSON)
        assertFalse(machine.released(RadarRendererOwner.FIRST_PERSON, fp))
        assertTrue(machine.released(RadarRendererOwner.BATTLE, battle))
        assertEquals(RadarRendererOwner.FIRST_PERSON, machine.owner)
    }
    @Test fun rapidToggleBackCannotReuseAnEngineAlreadyBeingDetached() {
        val machine=RadarRendererOwnership()
        val token=machine.request(RadarRendererOwner.FIRST_PERSON)
        machine.request(RadarRendererOwner.NONE)
        machine.request(RadarRendererOwner.FIRST_PERSON)
        assertTrue(machine.releasing)
        assertEquals(token,machine.generation)
        assertTrue(machine.released(RadarRendererOwner.FIRST_PERSON,token))
        assertFalse(machine.releasing)
        assertTrue(machine.generation>token)
    }
}
