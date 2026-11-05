package com.tamerslab.world.ui

import com.tamerslab.rendering.sprite3d.MAX_CAMERA_ASSISTED_RELATIVE_YAW
import com.tamerslab.world.RadarWorldGeometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarIsometricGeometryTest {

    private val plate = RADAR_ISO_PLATE_RADIUS_UNITS

    @Test
    fun playerSpriteCarriesFramesAndRequiresPositiveDimensions() {
        val sprite = RadarPlayerSprite(mapOf("idle" to byteArrayOf(1, 2, 3)), 12, 16, 2)
        assertEquals(2, sprite.stage)
        assertEquals(12, sprite.width)
        assertEquals(16, sprite.height)
        assertEquals(1, sprite.frames.size)
        assertThrows(IllegalArgumentException::class.java) {
            RadarPlayerSprite(emptyMap(), 0, 16, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadarPlayerSprite(emptyMap(), 12, 0, 0)
        }
    }

    @Test
    fun interactionRingMatchesTheRangeUsedForEncounters() {
        // The violet ring under the partner must enclose exactly the Digimon the Radar treats as
        // reachable: it is the same radial fraction a Digimon at the interaction-range distance gets.
        val interactionRange = RadarWorldGeometry.INTERACTION_RANGE_METERS
        for (visibleRadius in listOf(125.0, 500.0, 1_000.0)) {
            val ringRadius = radarIsometricRadialFraction(interactionRange, visibleRadius) *
                RADAR_ISO_PLATE_RADIUS_UNITS
            val placedAtRange = radarIsometricWorldPoint(
                bearingDegrees = 0.0,
                distanceMeters = interactionRange,
                visibleRadiusMeters = visibleRadius,
                plateRadiusUnits = RADAR_ISO_PLATE_RADIUS_UNITS,
            )
            assertEquals(ringRadius, -placedAtRange.z, 1e-4f)
        }
    }

    @Test
    fun cameraLooksDownAtThePlateFromTheSouthWithThePinnedPitch() {
        val pose = radarIsometricCamera(plate)
        assertEquals(0f, pose.eyeX, 0f)
        assertEquals(0f, pose.targetY, 0f)
        assertTrue(pose.eyeY > 0f)
        assertTrue(pose.eyeZ > 0f)
        val pitchDegrees = Math.toDegrees(atan(pose.eyeY.toDouble() / pose.eyeZ.toDouble()))
        assertEquals(RADAR_ISO_PITCH_DEGREES.toDouble(), pitchDegrees, 0.001)
        assertTrue(pose.distance.isFinite())
    }

    /**
     * The distance must be the larger of the vertical and horizontal fits, times the framing margin,
     * so the whole plate fits every orientation. The projections below use the pose's own camera
     * basis: depth = S - R·sinφ·cos(pitch), view x = R·cosφ, view y = -R·sinφ·sin(pitch).
     */
    @Test
    fun cameraFitsTheWholePlateInPortraitAndLandscape() {
        val pitch = Math.toRadians(RADAR_ISO_PITCH_DEGREES.toDouble())
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val tanHalfVertical = RADAR_ISO_TAN_HALF_VERTICAL_FOV.toDouble()
        for (aspect in listOf(0.3f, RADAR_ISO_REFERENCE_ASPECT_RATIO, 0.75f, 1f, 1.7f, 2.2f)) {
            val pose = radarIsometricCamera(plate, RADAR_ISO_PITCH_DEGREES, aspect)
            val distance = pose.distance.toDouble()
            val tanHalfHorizontal = tanHalfVertical * aspect
            var maxHorizontal = 0.0
            var maxVertical = 0.0
            for (step in 0 until 360) {
                val phi = step * PI / 180.0
                val depth = distance - plate * sin(phi) * cosPitch
                assertTrue("plate point must stay in front of the camera", depth > 0.0)
                maxHorizontal = maxOf(
                    maxHorizontal,
                    abs(plate * cos(phi) / (depth * tanHalfHorizontal)),
                )
                maxVertical = maxOf(
                    maxVertical,
                    abs(-plate * sin(phi) * sinPitch / (depth * tanHalfVertical)),
                )
            }
            assertTrue("horizontal NDC must fit at aspect $aspect: $maxHorizontal", maxHorizontal <= 1.0)
            assertTrue("vertical NDC must fit at aspect $aspect: $maxVertical", maxVertical <= 1.0)
            // When the horizontal fit binds (portrait viewports) the rim lands on 1/margin of the
            // half-width; wide viewports are vertically constrained and keep extra horizontal slack.
            val horizontalFit = plate * sqrt(
                cosPitch * cosPitch + 1.0 / (tanHalfHorizontal * tanHalfHorizontal),
            )
            val verticalFit = plate * (cosPitch + sinPitch / tanHalfVertical)
            if (horizontalFit >= verticalFit) {
                assertEquals(1.0 / RADAR_ISO_FIT_MARGIN, maxHorizontal, 0.02)
            }
        }
    }

    @Test
    fun widerViewportsNeverNeedMoreDistanceThanPortrait() {
        // The camera distance is the larger of the fixed vertical fit and the aspect-dependent
        // horizontal fit, so it is monotonically non-increasing as the viewport gets wider. At the
        // lowest angle the square viewport is still horizontally constrained, while at the highest
        // angle square and landscape can both be vertically constrained (equal distances).
        for (pitch in listOf(RADAR_ISO_PITCH_DEGREES, RADAR_ISO_MAX_PITCH_DEGREES)) {
            val portrait = radarIsometricCamera(plate, pitch, 0.4615f)
            val square = radarIsometricCamera(plate, pitch, 1f)
            val landscape = radarIsometricCamera(plate, pitch, 2.2f)
            val ultraWide = radarIsometricCamera(plate, pitch, 6f)
            assertTrue("portrait vs square @$pitch", portrait.distance > square.distance)
            assertTrue("square vs landscape @$pitch", square.distance >= landscape.distance - 0.0001f)
            assertTrue("landscape vs ultra-wide @$pitch", landscape.distance >= ultraWide.distance - 0.0001f)
            assertTrue("portrait vs ultra-wide @$pitch", portrait.distance > ultraWide.distance)
        }
    }

    @Test
    fun radialFractionIsMonotonicBoundedAndAnchoredAtTheVisibleRadius() {
        for (visibleRadius in listOf(125.0, 500.0, 1_000.0)) {
            assertEquals(0f, radarIsometricRadialFraction(0.0, visibleRadius), 0f)
            assertEquals(1f, radarIsometricRadialFraction(visibleRadius, visibleRadius), 0.000001f)
            assertEquals(1f, radarIsometricRadialFraction(visibleRadius * 4.0, visibleRadius), 0f)
            var previous = -1f
            for (metres in 0..1_200 step 5) {
                val next = radarIsometricRadialFraction(metres.toDouble(), visibleRadius)
                assertTrue("fraction must be monotonic on [0,1200] at $visibleRadius", next >= previous)
                assertTrue(next in 0f..1f)
                previous = next
            }
        }
        // The curve is the existing first-person legibility curve normalised by the visible radius.
        assertEquals(
            (radarPresentationDistance(40.0) / radarPresentationDistance(500.0)).toFloat(),
            radarIsometricRadialFraction(40.0, 500.0),
            0.000001f,
        )
        assertEquals(
            radarIsometricRadialFraction(40.0, 500.0),
            radarIsometricRadialFraction(40.000001, 500.0),
            0.000001f,
        )
    }

    @Test
    fun worldPointMapsNorthToMinusZAndEastToPlusX() {
        val north = radarIsometricWorldPoint(0.0, 500.0, 500.0, plate)
        assertEquals(0f, north.x, 0.0001f)
        assertEquals(-plate, north.z, 0.0001f)
        val east = radarIsometricWorldPoint(90.0, 500.0, 500.0, plate)
        assertEquals(plate, east.x, 0.0001f)
        assertEquals(0f, east.z, 0.0001f)
        val south = radarIsometricWorldPoint(180.0, 500.0, 500.0, plate)
        assertEquals(0f, south.x, 0.0001f)
        assertEquals(plate, south.z, 0.0001f)
        val west = radarIsometricWorldPoint(270.0, 500.0, 500.0, plate)
        assertEquals(-plate, west.x, 0.0001f)
        assertEquals(0f, west.z, 0.0001f)
        val northEast = radarIsometricWorldPoint(45.0, 500.0, 500.0, plate)
        assertEquals(plate.toDouble() / sqrt(2.0), northEast.x.toDouble(), 0.0001)
        assertEquals(-plate.toDouble() / sqrt(2.0), northEast.z.toDouble(), 0.0001)
    }

    @Test
    fun closerDigimonSitCloserToThePlateCentreAndNeverExceedTheRim() {
        val centre = sqrt(
            radarIsometricWorldPoint(0.0, 0.0, 500.0, plate).let { it.x * it.x + it.z * it.z }.toDouble(),
        )
        val near = radarIsometricWorldPoint(30.0, 10.0, 500.0, plate)
        val far = radarIsometricWorldPoint(30.0, 300.0, 500.0, plate)
        val nearRadius = sqrt((near.x * near.x + near.z * near.z).toDouble())
        val farRadius = sqrt((far.x * far.x + far.z * far.z).toDouble())
        assertEquals(0.0, centre, 0.0001)
        assertTrue(nearRadius < farRadius)
        assertTrue(farRadius <= plate + 0.0001)
        // The same distance occupies more of the plate when the visible radius shrinks (zoom in).
        val zoomedIn = radarIsometricWorldPoint(30.0, 300.0, 125.0, plate)
        val zoomedInRadius = sqrt((zoomedIn.x * zoomedIn.x + zoomedIn.z * zoomedIn.z).toDouble())
        assertTrue(zoomedInRadius >= farRadius)
    }

    @Test
    fun worldRotationTurnsTheMapWithTheCompass() {
        assertEquals(0f, radarIsometricWorldRotation(0f), 0f)
        assertEquals(270f, radarIsometricWorldRotation(-90f), 0f)
        assertEquals(0f, radarIsometricWorldRotation(720f), 0f)
        assertEquals(45f, radarIsometricWorldRotation(405f), 0f)
        val north = RadarScenePoint(0f, -plate)
        val rotatedNorth = radarIsometricRotate(north, 90f)
        // Facing east: north appears on the left of the screen, exactly like the legacy 2D radar.
        assertEquals(-plate, rotatedNorth.x, 0.0001f)
        assertEquals(0f, rotatedNorth.z, 0.0001f)
        val identity = radarIsometricRotate(RadarScenePoint(3f, -4f), 0f)
        assertEquals(3f, identity.x, 0.0001f)
        assertEquals(-4f, identity.z, 0.0001f)
        val back = radarIsometricRotate(radarIsometricRotate(RadarScenePoint(3f, -4f), 137f), -137f)
        assertEquals(3f, back.x, 0.0001f)
        assertEquals(-4f, back.z, 0.0001f)
    }

    @Test
    fun worldRotationMatchesTheFilamentYAxisRotation() {
        // Matrix.rotateM(degrees, 0,1,0) applies x' = x·cos + z·sin; z' = -x·sin + z·cos.
        val heading = 120f
        val angle = Math.toRadians(radarIsometricWorldRotation(heading).toDouble())
        val point = RadarScenePoint(2.5f, -3.5f)
        val expectedX = point.x * cos(angle) + point.z * sin(angle)
        val expectedZ = -point.x * sin(angle) + point.z * cos(angle)
        val rotated = radarIsometricRotate(point, heading)
        assertEquals(expectedX.toFloat(), rotated.x, 0.0001f)
        assertEquals(expectedZ.toFloat(), rotated.z, 0.0001f)
    }

    @Test
    fun spriteScaleOrdersEvolutionStages() {
        assertTrue(radarIsometricSpriteScale(0) > 0f)
        assertTrue(radarIsometricSpriteScale(0) < radarIsometricSpriteScale(1))
        assertTrue(radarIsometricSpriteScale(1) < radarIsometricSpriteScale(2))
        assertEquals(radarIsometricSpriteScale(2), radarIsometricSpriteScale(3), 0f)
        assertTrue(radarIsometricSpriteScale(2) * RADAR_ISO_PLAYER_SCALE > radarIsometricSpriteScale(2))
    }

    @Test
    fun poseContractMirrorsTheFirstPersonFallbackChain() {
        assertEquals(
            "idle",
            radarIsometricPose(walking = true, motion = true, alternate = true, available = setOf("idle")),
        )
        assertEquals(
            "walk",
            radarIsometricPose(
                walking = true,
                motion = true,
                alternate = true,
                available = setOf("idle", "walk"),
            ),
        )
        assertEquals(
            "walk2",
            radarIsometricPose(
                walking = true,
                motion = true,
                alternate = true,
                available = setOf("idle", "walk", "walk2"),
            ),
        )
        assertEquals(
            "idle",
            radarIsometricPose(
                walking = true,
                motion = false,
                alternate = true,
                available = setOf("idle", "idle2", "walk"),
            ),
        )
        assertEquals(
            "idle2",
            radarIsometricPose(
                walking = false,
                motion = true,
                alternate = true,
                available = setOf("idle2"),
            ),
        )
        assertEquals(null, radarIsometricPose(false, true, false, emptySet()))
        assertNotNull(radarIsometricPose(false, false, false, setOf("idle")))
    }

    @Test
    fun plateScaleKeepsOneAuthoredGridCellAtSixtyMeters() {
        assertEquals(0.012f, radarIsometricPlateUnitsPerMeter(500.0, plate), 0.000001f)
        assertEquals(0.36f, radarIsometricPlateAssetScale(500.0, plate), 0.000001f)
        for (visibleRadius in listOf(125.0, 250.0, 500.0, 750.0, 1_000.0)) {
            val unitsPerMetre = radarIsometricPlateUnitsPerMeter(visibleRadius, plate)
            val assetScale = radarIsometricPlateAssetScale(visibleRadius, plate)
            val cellMetres = RADAR_ISO_AUTHORED_GRID_CELL_UNITS * assetScale / unitsPerMetre
            assertEquals(RADAR_ISO_GRID_CELL_METERS.toFloat(), cellMetres, 0.01f)
        }
        assertTrue(radarIsometricPlateAssetScale(1_000.0, plate) < radarIsometricPlateAssetScale(500.0, plate))
    }

    @Test
    fun zoomEnvelopeMatchesTheAdvertisedVisibleRadii() {
        assertEquals(200.0, radarIsometricVisibleRadiusMeters(1f), 0.0001)
        assertEquals(50.0, radarIsometricVisibleRadiusMeters(RADAR_ISO_MAX_ZOOM), 0.0001)
        assertEquals(400.0, radarIsometricVisibleRadiusMeters(RADAR_ISO_MIN_ZOOM), 0.0001)
        assertEquals(400.0, radarIsometricVisibleRadiusMeters(0.01f), 0.0001)
        assertEquals(50.0, radarIsometricVisibleRadiusMeters(99f), 0.0001)
    }

    @Test
    fun cameraRejectsDegenerateInputs() {
        assertThrows(IllegalArgumentException::class.java) { radarIsometricCamera(0f) }
        assertThrows(IllegalArgumentException::class.java) { radarIsometricCamera(plate, 58f, 0f) }
        assertThrows(IllegalArgumentException::class.java) {
            radarIsometricRadialFraction(Double.NaN, 500.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            radarIsometricRadialFraction(10.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            radarIsometricWorldPoint(0.0, 10.0, 500.0, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) { radarIsometricWorldRotation(Float.NaN) }
    }

    @Test
    fun cardboardOrientationIgnoresCompassMotionForStandingDigimon() {
        // A Digimon that never walked faces the camera at every compass heading; turning the plate
        // must never look like the sprite changing direction.
        var mirror = false
        for (heading in 0 until 360 step 17) {
            val orientation = radarCardboardOrientation(
                worldBearingDegrees = 0f,
                hasTravel = false,
                compassHeadingDegrees = heading.toFloat(),
                previousMirror = mirror,
            )
            assertEquals(0f, orientation.visibleYaw, 0.0001f)
            mirror = orientation.mirror
        }
        // Standing tokens latch their facing once and stay stable afterwards.
        assertTrue(mirror)
        val stable = radarCardboardOrientation(0f, false, 123f, mirror)
        assertTrue(stable.mirror)
    }

    @Test
    fun cardboardOrientationMatchesTheFarmForTheFourTravelDirections() {
        // East: farm heading 0, front-facing mirrored walk frame.
        val east = radarCardboardOrientation(90f, true, 0f, previousMirror = false)
        assertEquals(0f, east.visibleYaw, 0.0001f)
        assertTrue(east.mirror)
        // West: farm heading π, front-facing unmirrored frame.
        val west = radarCardboardOrientation(270f, true, 0f, previousMirror = true)
        assertEquals(0f, west.visibleYaw, 0.0001f)
        assertFalse(west.mirror)
        // North and south are profile walks, clamped by the farm's camera assistance and latched.
        val north = radarCardboardOrientation(0f, true, 0f, previousMirror = false)
        assertEquals(MAX_CAMERA_ASSISTED_RELATIVE_YAW, abs(north.visibleYaw), 0.0001f)
        assertFalse(north.mirror)
        val south = radarCardboardOrientation(180f, true, 180f, previousMirror = true)
        assertEquals(MAX_CAMERA_ASSISTED_RELATIVE_YAW, abs(south.visibleYaw), 0.0001f)
        assertTrue(south.mirror)
    }

    @Test
    fun cardboardOrientationFollowsThePlateWhenTheCompassTurns() {
        // Walking east while the radar turns: the sprite rotates with the plate instead of flipping.
        val first = radarCardboardOrientation(90f, true, 0f, previousMirror = false)
        val turned = radarCardboardOrientation(90f, true, 10f, previousMirror = first.mirror)
        assertEquals(10f, Math.toDegrees(turned.visibleYaw.toDouble()).toFloat(), 0.01f)
        assertTrue(turned.mirror)
        // A ±1° compass jitter around the latch threshold must not chatter the mirror flag.
        val latched = radarCardboardOrientation(90f, true, 91f, previousMirror = true)
        assertTrue(latched.mirror)
        val latchedBack = radarCardboardOrientation(90f, true, 90f, previousMirror = latched.mirror)
        assertTrue(latchedBack.mirror)
    }

    @Test
    fun fitMarginKeepsTheRimInsideTheShorterEdge() {
        val pose = radarIsometricCamera(plate, RADAR_ISO_PITCH_DEGREES, RADAR_ISO_REFERENCE_ASPECT_RATIO)
        val distance = pose.distance.toDouble()
        val pitch = Math.toRadians(RADAR_ISO_PITCH_DEGREES.toDouble())
        val halfWidthAtRim = plate /
            (sqrt(distance * distance - plate * plate * cos(pitch) * cos(pitch)) *
                RADAR_ISO_TAN_HALF_VERTICAL_FOV.toDouble() * RADAR_ISO_REFERENCE_ASPECT_RATIO)
        assertTrue(halfWidthAtRim < 1.0)
        assertEquals(1.0 / RADAR_ISO_FIT_MARGIN, halfWidthAtRim, 0.02)
    }
}
