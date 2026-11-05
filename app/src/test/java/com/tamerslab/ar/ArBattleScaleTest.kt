package com.tamerslab.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2

class ArBattleScaleTest {

    private val epsilon = 1e-4f

    private fun assertNear(expected: Float, actual: Float, message: String = "", tolerance: Float = epsilon) {
        assertEquals("$message expected <$expected> but was <$actual>", expected.toDouble(), actual.toDouble(), tolerance.toDouble())
    }

    private fun horizontalHit(x: Float, y: Float, z: Float) = ArPose.yaw(floatArrayOf(x, y, z), 0f)

    // --- recommendedPlan ---------------------------------------------------------------

    @Test
    fun `small table recommends the tabletop plan`() {
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(ArPlaneSize(0.8f, 1.2f)))
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(ArPlaneSize(0.25f, 0.25f)))
    }

    @Test
    fun `recommendation uses the shortest extent, not the longest`() {
        // A long narrow counter is still a table because its depth is under 1.4 m.
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(ArPlaneSize(10f, 1.0f)))
        // A large rug is a room floor once both sides pass the tabletop threshold.
        assertEquals(ArBattleScalePlan.ROOM, ArBattleScale.recommendedPlan(ArPlaneSize(10f, 2.0f)))
    }

    @Test
    fun `plan recommendation boundaries are exact`() {
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(ArPlaneSize(1.39f, 5f)))
        assertEquals(ArBattleScalePlan.ROOM, ArBattleScale.recommendedPlan(ArPlaneSize(1.4f, 5f)))
        assertEquals(ArBattleScalePlan.ROOM, ArBattleScale.recommendedPlan(ArPlaneSize(3.99f, 9f)))
        assertEquals(ArBattleScalePlan.OPEN_SPACE, ArBattleScale.recommendedPlan(ArPlaneSize(4f, 9f)))
    }

    // --- targetBattleDiameterMeters ----------------------------------------------------

    @Test
    fun `fixed plans map to their target diameters`() {
        val plane = ArPlaneSize(0.8f, 2f)
        assertNear(0.9f, ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.TABLETOP, plane))
        assertNear(2.5f, ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.ROOM, plane))
        assertNear(8f, ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.OPEN_SPACE, plane))
    }

    @Test
    fun `auto plan uses 75 percent of the shortest extent`() {
        assertNear(3f, ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.AUTO, ArPlaneSize(4f, 9f)))
        assertNear(1.5f, ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.AUTO, ArPlaneSize(2f, 2f)))
    }

    @Test
    fun `target diameter is clamped on both ends`() {
        // 75% of a 0.2 m surface would be 0.15 m: too small to fight on.
        assertNear(
            ArBattleScale.MIN_BATTLE_DIAMETER_METERS,
            ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.AUTO, ArPlaneSize(0.2f, 0.2f)),
        )
        // 75% of a 40 m surface would be 30 m: beyond tracking range.
        assertNear(
            ArBattleScale.MAX_BATTLE_DIAMETER_METERS,
            ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.AUTO, ArPlaneSize(40f, 40f)),
        )
    }

    @Test
    fun `recommended plan and target diameter agree`() {
        val table = ArPlaneSize(1.0f, 1.0f)
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(table))
        assertEquals(
            ArBattleScale.targetBattleDiameterMeters(ArBattleScale.recommendedPlan(table), table),
            ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.TABLETOP, table),
        )
    }

    // --- metersPerSceneUnit ------------------------------------------------------------

    @Test
    fun `radar arena of 16 units maps to the plan diameter`() {
        val plane = ArPlaneSize(10f, 10f)
        assertNear(0.05625f, ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.TABLETOP, plane, 16f))
        assertNear(0.15625f, ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.ROOM, plane, 16f))
        assertNear(0.5f, ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.OPEN_SPACE, plane, 16f))
    }

    @Test
    fun `meters per scene unit round-trips through battleDiameterMeters`() {
        val plane = ArPlaneSize(1f, 1f)
        val plan = ArBattleScalePlan.TABLETOP
        val mpu = ArBattleScale.metersPerSceneUnit(plan, plane, ArBattleScale.RADAR_ARENA_WIDTH_UNITS)
        assertNear(
            ArBattleScale.targetBattleDiameterMeters(plan, plane),
            ArBattleScale.battleDiameterMeters(mpu, ArBattleScale.RADAR_ARENA_WIDTH_UNITS),
        )
        assertNear(
            ArBattleScale.RADAR_ARENA_WIDTH_UNITS,
            ArBattleScale.sceneUnitsForBattleDiameter(
                ArBattleScale.targetBattleDiameterMeters(plan, plane), mpu,
            ),
        )
    }

    @Test
    fun `meters per scene unit respects the supported clamp range`() {
        val plane = ArPlaneSize(1f, 1f)
        // 8 m across a 1-unit arena would be 8 m per unit: clamped to 1.5.
        assertNear(1.5f, ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.OPEN_SPACE, plane, 1f))
        // 0.9 m across a 1000-unit arena is 0.0009 m per unit: clamped to 0.02.
        assertNear(0.02f, ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.TABLETOP, plane, 1000f))
        // NaN/invalid values fall back to the minimum instead of creating an invalid placement.
        assertNear(0.02f, ArBattleScale.clampMetersPerSceneUnit(Float.NaN))
        assertEquals(1.5f, ArBattleScale.clampMetersPerSceneUnit(99f), 1e-3f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive arena width is rejected`() {
        ArBattleScale.metersPerSceneUnit(ArBattleScalePlan.ROOM, ArPlaneSize(1f, 1f), 0f)
    }

    @Test
    fun `pinch scale factor stays inside its bounds`() {
        assertNear(0.6f, ArBattleScale.clampPlanScaleFactor(0.01f))
        assertNear(1.6f, ArBattleScale.clampPlanScaleFactor(10f))
        assertNear(1f, ArBattleScale.clampPlanScaleFactor(Float.NaN))
        assertNear(1.25f, ArBattleScale.clampPlanScaleFactor(1.25f))
    }

    // --- makePlacement -----------------------------------------------------------------

    @Test
    fun `placement anchors on the hit point with the plane normal up`() {
        val hit = horizontalHit(1.5f, 0.4f, -2f)
        val camera = horizontalHit(1.5f, 1.6f, 3f)
        val placement = ArBattleScale.makePlacement(hit, camera, 0.1f)

        assertNear(1.5f, placement.anchor.translation[0])
        assertNear(0.4f, placement.anchor.translation[1])
        assertNear(-2f, placement.anchor.translation[2])
        assertNear(0.1f, placement.metersPerSceneUnit)

        val up = placement.anchor.rotateVector(floatArrayOf(0f, 1f, 0f))
        assertNear(0f, up[0])
        assertNear(1f, up[1])
        assertNear(0f, up[2])
    }

    @Test
    fun `placement plus Z points at the camera`() {
        val hit = horizontalHit(0f, 0f, 0f)
        val camera = horizontalHit(3f, 1.6f, 4f)
        val placement = ArBattleScale.makePlacement(hit, camera, 0.1f)

        val forward = placement.anchor.rotateVector(floatArrayOf(0f, 0f, 1f))
        val expected = atan2(3f, 4f)
        assertNear(kotlin.math.sin(expected), forward[0], "forward.x")
        assertNear(0f, forward[1], "forward.y")
        assertNear(kotlin.math.cos(expected), forward[2], "forward.z")
        // The camera's horizontal offset from the anchor lies exactly along the arena +Z axis.
        assertTrue(forward[0] * 3f + forward[2] * 4f > 0f)
    }

    @Test
    fun `camera exactly behind the hit point yields a zero yaw`() {
        val placement = ArBattleScale.makePlacement(
            horizontalHit(0f, 0f, 0f),
            horizontalHit(0f, 1.6f, 2f),
            0.05f,
        )
        assertNear(0f, ArBattleScale.yawTowardCamera(horizontalHit(0f, 0f, 0f), horizontalHit(0f, 1.6f, 2f)))
        val forward = placement.anchor.rotateVector(floatArrayOf(0f, 0f, 1f))
        assertNear(0f, forward[0])
        assertNear(1f, forward[2])
    }

    @Test
    fun `camera directly above the hit point does not produce NaN`() {
        val yaw = ArBattleScale.yawTowardCamera(
            horizontalHit(1f, 0f, 2f),
            horizontalHit(1f, 2.5f, 2f),
        )
        assertEquals(0f, yaw, 0f)
        val placement = ArBattleScale.makePlacement(
            horizontalHit(1f, 0f, 2f),
            horizontalHit(1f, 2.5f, 2f),
            0.1f,
        )
        assertTrue(placement.anchor.rotation.all { it.isFinite() })
    }

    @Test
    fun `placement meters per scene unit is clamped to the supported range`() {
        val hit = horizontalHit(0f, 0f, 0f)
        val camera = horizontalHit(0f, 1.6f, 2f)
        assertNear(1.5f, ArBattleScale.makePlacement(hit, camera, 42f).metersPerSceneUnit)
        assertNear(0.02f, ArBattleScale.makePlacement(hit, camera, 0f).metersPerSceneUnit)
    }

    @Test
    fun `rotating the placement yaw keeps the anchor position and scale`() {
        val placement = ArBattleScale.makePlacement(
            horizontalHit(2f, 0.3f, -1f),
            horizontalHit(2f, 1.6f, 3f),
            0.2f,
        )
        val rotated = ArBattleScale.rotatePlacementYaw(placement, (PI / 2.0).toFloat())
        assertNear(2f, rotated.anchor.translation[0])
        assertNear(0.3f, rotated.anchor.translation[1])
        assertNear(-1f, rotated.anchor.translation[2])
        assertNear(0.2f, rotated.metersPerSceneUnit)

        val before = placement.anchor.rotateVector(floatArrayOf(0f, 0f, 1f))
        val after = rotated.anchor.rotateVector(floatArrayOf(0f, 0f, 1f))
        // A quarter turn around +Y moves +Z onto +X: newZ = (oldZ.x, 0, -oldZ.z).
        assertNear(before[2], after[0], "rotated forward.x")
        assertNear(-before[0], after[2], "rotated forward.z")
        // Rotating by zero returns the same placement.
        assertEquals(placement, ArBattleScale.rotatePlacementYaw(placement, 0f))
    }

    // --- planeSizeOf -------------------------------------------------------------------

    @Test
    fun `plane snapshot extents become a plane size`() {
        val plane = ArPlaneSnapshot(
            id = "p1",
            type = ArPlaneType.HORIZONTAL_UPWARD,
            tracking = ArTrackingState.TRACKING,
            centerPose = ArPose.IDENTITY,
            extentX = 1.2f,
            extentZ = 3.4f,
            polygon = floatArrayOf(),
        )
        val size = ArBattleScale.planeSizeOf(plane)
        assertNear(1.2f, size.widthMeters)
        assertNear(3.4f, size.depthMeters)
        assertNear(1.2f, size.minExtentMeters)
        assertNear(3.4f, size.maxExtentMeters)
        assertTrue(size.minExtentMeters <= size.maxExtentMeters)
    }

    @Test
    fun `a zero sized plane still recommends a plan`() {
        assertEquals(ArBattleScalePlan.TABLETOP, ArBattleScale.recommendedPlan(ArPlaneSize(0f, 0f)))
        assertNear(
            ArBattleScale.MIN_BATTLE_DIAMETER_METERS,
            ArBattleScale.targetBattleDiameterMeters(ArBattleScalePlan.AUTO, ArPlaneSize(0f, 0f)),
        )
    }

    // --- gravity-aligned placement (depth / instant hits) --------------------------------

    @Test
    fun `gravity aligned placement keeps world up and faces the camera`() {
        // Camera standing 1.4 m above the floor and 2 m/3 m away horizontally.
        val camera = ArPose.translation(2f, 1.4f, 3f)
        val placement = ArBattleScale.makeGravityAlignedPlacement(
            hitTranslation = floatArrayOf(0f, 0f, 0f),
            cameraPose = camera,
            metersPerSceneUnit = 0.5f,
        )
        // Anchor sits on the surface level with the world (no camera-oriented tilt).
        assertNear(0f, placement.anchor.translation[1])
        // The anchor's +Z axis points at the camera, projected horizontally.
        val forward = placement.anchor.rotateVector(floatArrayOf(0f, 0f, 1f))
        val horizontal = kotlin.math.sqrt(2f * 2f + 3f * 3f)
        assertNear(2f / horizontal, forward[0], tolerance = 1e-3f)
        assertNear(0f, forward[1], tolerance = 1e-3f)
        assertNear(3f / horizontal, forward[2], tolerance = 1e-3f)
        assertNear(0.5f, placement.metersPerSceneUnit)
    }

    @Test
    fun `gravity aligned placement clamps the metres per scene unit`() {
        val placement = ArBattleScale.makeGravityAlignedPlacement(
            hitTranslation = floatArrayOf(0f, 0f, 0f),
            cameraPose = ArPose.translation(0f, 1f, 1f),
            metersPerSceneUnit = 50f,
        )
        assertNear(ArBattleScale.MAX_METERS_PER_SCENE_UNIT, placement.metersPerSceneUnit)
    }
}
