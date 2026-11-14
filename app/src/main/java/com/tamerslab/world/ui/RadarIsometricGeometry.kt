package com.tamerslab.world.ui

import com.tamerslab.rendering.sprite3d.MAX_CAMERA_ASSISTED_RELATIVE_YAW
import com.tamerslab.rendering.sprite3d.cameraAssistedSpriteYaw
import com.tamerslab.world.GeoPoint
import com.tamerslab.world.RadarWorldGeometry
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The active partner's four stored sprite poses, ready to be extruded into a 3D token at the
 * centre of the isometric radar plate.
 *
 * Frame keys are `idle`, `idle2`, `walk`, `walk2`; a missing pose is simply omitted so a partner
 * with incomplete artwork still renders every frame it owns. Frames are raw sprite bytes decoded
 * with [width] × [height] (the same encoded format used by [com.tamerslab.world.ecosystem] spawns).
 */
data class RadarPlayerSprite(
    val frames: Map<String, ByteArray>,
    val width: Int,
    val height: Int,
    val stage: Int,
) {
    init {
        require(width > 0 && height > 0) { "Player sprite dimensions must be positive." }
        require(frames.values.all { it.isNotEmpty() } || frames.isEmpty()) {
            "Player sprite frames must not be empty arrays; omit unavailable poses instead."
        }
    }
}

/** Pitch of the fixed isometric camera: high enough to read the plate, low enough to keep sprites. */
/**
 * Default pitch of the radar camera. The owner wants the lowest readable angle by default (2026-11-04);
 * the drag can raise it up to [RADAR_ISO_MAX_PITCH_DEGREES] but never under the floor set here.
 */
internal const val RADAR_ISO_PITCH_DEGREES = 34f

/** Radar-only facing latch: wide enough that a 1° compass step cannot chatter the mirror. */
internal const val RADAR_CARDBOARD_FLIP_THRESHOLD = 0.22f

/** Result of the radar's cardboard orientation pass; both values feed the sprite transform. */
internal data class RadarCardboardOrientation(val visibleYaw: Float, val mirror: Boolean)

/**
 * Copies the Digifarm's cardboard orientation into the radar's north-up, rotating-plate world.
 *
 * The farm feeds a *world* travel bearing and a *camera* yaw into `cameraAssistedSpriteYaw`; the radar
 * camera is fixed but the plate rotates by the compass heading, which is the same relationship. Only a
 * real-world travel bearing is accepted here: the plate turning or zooming must never be interpreted as
 * travel, which is what made the sprites flip on every compass tick.
 *
 * @param worldBearingDegrees real-world travel bearing (0 = north, clockwise positive)
 * @param hasTravel false until this Digimon actually walked once; standing tokens face the camera
 * @param compassHeadingDegrees the radar's current heading in the same convention
 * @param previousMirror the last mirror state, kept inside the latch band
 */
internal fun radarCardboardOrientation(
    worldBearingDegrees: Float,
    hasTravel: Boolean,
    compassHeadingDegrees: Float,
    previousMirror: Boolean,
    flipThreshold: Float = RADAR_CARDBOARD_FLIP_THRESHOLD,
    maxRelativeYaw: Float = MAX_CAMERA_ASSISTED_RELATIVE_YAW,
): RadarCardboardOrientation {
    val farmHeading = if (!hasTravel) {
        0f
    } else {
        val screenBearing = normalizeDegrees(worldBearingDegrees - compassHeadingDegrees)
        val headingRadians = Math.toRadians(90.0 - screenBearing).toFloat()
        normalizeRadians(headingRadians)
    }
    val lateral = cos(farmHeading)
    val mirror = when {
        lateral > flipThreshold -> true
        lateral < -flipThreshold -> false
        else -> previousMirror
    }
    return RadarCardboardOrientation(
        visibleYaw = cameraAssistedSpriteYaw(farmHeading, 0f, maxRelativeYaw),
        mirror = mirror,
    )
}

private fun normalizeRadians(radians: Float): Float {
    val pi = Math.PI.toFloat()
    val wrapped = radians % (2f * pi)
    return if (wrapped < -pi) wrapped + 2f * pi else wrapped
}

private fun normalizeDegrees(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    val wrapped = degrees % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

internal const val RADAR_ISO_MIN_PITCH_DEGREES = 34f
internal const val RADAR_ISO_MAX_PITCH_DEGREES = 60f

/** Zoom envelope: 0.5× is the widest view, 4× the closest. */
internal const val RADAR_ISO_MIN_ZOOM = 0.5f
internal const val RADAR_ISO_MAX_ZOOM = 4f

/**
 * Conversation framing: a smaller virtual plate makes the pinned camera move physically closer to
 * the focused wild Digimon during an overworld negotiation, so the token fills a readable part of
 * the screen while the world stays visible around it.
 */
internal const val RADAR_ISO_FOCUS_PLATE_RADIUS_UNITS = 2.2f

/** Lower, more eye-level pitch used for the conversation framing (the token faces the camera). */
internal const val RADAR_ISO_CONVERSATION_PITCH_DEGREES = 23f

/**
 * Visible radar radius at the neutral zoom, in real-world metres: 200 m by default, 400 m at the
 * widest zoom-out and 50 m at the closest zoom-in. Both ends were pulled in from the old
 * 1000 m/125 m envelope so the plate is filled with nearby Digimon instead of empty ground.
 */
internal const val RADAR_ISO_VISIBLE_RADIUS_AT_ZOOM_ONE_METERS = 200.0

/**
 * Radius of the radar plate in scene units.
 *
 * The plate is the framed disc the camera looks at; it is deliberately independent of the authored
 * GLB scale so the camera framing never changes with zoom. Six units were chosen for token
 * legibility: a stage-2 sprite is 1.4 units tall, i.e. ~23% of the plate radius, which projects to
 * roughly 40dp at the reference portrait aspect on a 24mm lens (see the camera derivation below).
 */
internal const val RADAR_ISO_PLATE_RADIUS_UNITS = 6f

/**
 * Real-world size one authored `world_grid` cell stands for, measured across the plate.
 *
 * The authored arena draws a grid line every two units. The ambient plate is a *linear* metre
 * reference (the radar rings and the entities use the compressed [radarPresentationDistance]
 * legibility curve, as the accepted contract requires), and 60 m reproduces the legacy 2D radar's
 * grid density (`GRID_SIZE_METERS = 60` in `WorldScreen.kt`) across the visible diameter at the
 * neutral zoom. The plate therefore re-scales proportionally with zoom, so the ambient grid always
 * reads as the same real-world ground under the compass rings.
 */
internal const val RADAR_ISO_GRID_CELL_METERS = 60.0

/** Distance between two authored grid lines in `Arena/Radar/radar_grid.glb`. */
internal const val RADAR_ISO_AUTHORED_GRID_CELL_UNITS = 2f

/** Sensor dimension Filament's `Camera.setLensProjection` assumes (24 mm high). */
internal const val RADAR_ISO_SENSOR_HEIGHT_MM = 24f

/** Lens used by [com.google.android.filament.utils.ModelViewer] here: 24 mm on a 24 mm sensor. */
internal const val RADAR_ISO_FOCAL_MM = 24f

/**
 * Extra camera distance over the exact fit so the plate rim never touches a screen edge.
 *
 * The exact fit would put the rim exactly on the shorter edge; the compose compass layer draws just
 * inside that edge, so 1.12 lands the rim at ~89% of the half-shorter-edge, matching it.
 */
internal const val RADAR_ISO_FIT_MARGIN = 1.12f

/** Portrait reference aspect (width / height) used when the caller does not supply the viewport one. */
internal const val RADAR_ISO_REFERENCE_ASPECT_RATIO = 0.4615f

/** Scale multiplier that keeps the partner token the largest, clearest object at the plate centre. */
internal const val RADAR_ISO_PLAYER_SCALE = 1.12f

/**
 * Tangential separation between meeting participants, in plate scene units.
 *
 * Roughly one extruded-token width at stage 2 (the widest sprite) so a two-Digimon meeting reads as
 * two bodies standing beside each other instead of one blob at the meeting point.
 */
internal const val RADAR_ISO_ACTIVITY_SPACING_UNITS = 1.3f

/** Forward lunge distance of a battle participant, in plate scene units. */
internal const val RADAR_ISO_LUNGE_UNITS = 0.3f

/**
 * Vertical half field of view tangent of the pinned lens.
 *
 * Filament's `Camera.setLensProjection(focalLength, aspect, near, far)` assumes a 24 mm-high sensor
 * (`ModelViewer` passes the viewport `width / height` as `aspect`), so
 * `tan(halfVerticalFov) = sensorHeight / (2 * focalLength) = 24 / (2 * 24) = 0.5`,
 * i.e. a 53.13° vertical FOV. The same derivation is documented by the Digifarm and battle cameras.
 */
internal val RADAR_ISO_TAN_HALF_VERTICAL_FOV: Float = RADAR_ISO_SENSOR_HEIGHT_MM / (2f * RADAR_ISO_FOCAL_MM)

/**
 * Fixed isometric camera pose looking at the plate centre.
 *
 * [targetY] is the height of the look-at point (the plate floor, so 0). The eye sits on the +Z axis
 * and therefore looks toward -Z, which is why a bearing of 0° (north) maps to -Z below.
 */
internal data class RadarIsometricCamera(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val targetY: Float,
) {
    /** Eye distance from the look-at point, in scene units. */
    val distance: Float get() = sqrt(eyeX * eyeX + eyeY * eyeY + (eyeZ - targetY) * (eyeZ - targetY))
}

/**
 * Derives the fixed isometric pose that frames a plate of [plateRadiusUnits] from the pinned lens.
 *
 * Derivation (camera eye at distance `S` from the plate centre, elevation `pitch`):
 * - A plate point at bearing φ and radius `R` has camera depth `d = S - R·sinφ·cos(pitch)` and view
 *   space offsets `x = R·cosφ`, `y = -R·sinφ·sin(pitch)` (camera up is tilted).
 * - Horizontal fit: `max |x / (d·tanHalfH)| = R / (tanHalfH·sqrt(S² - R²cos²(pitch)))`, which is
 *   `≤ 1` for `S ≥ R·sqrt(cos²(pitch) + 1/tanHalfH²)`.
 * - Vertical fit: `max |y / (d·tanHalfV)| = R·sin(pitch) / ((S - R·cos(pitch))·tanHalfV)`, which is
 *   `≤ 1` for `S ≥ R·cos(pitch) + R·sin(pitch)/tanHalfV`.
 * - `tanHalfH = tanHalfV · aspect`, so portrait phones (aspect < 1) are horizontally constrained and
 *   wide viewports vertically constrained; [RADAR_ISO_FIT_MARGIN] is applied to the larger distance.
 *
 * @param aspectRatio viewport width / height; defaults to the A34 portrait reference aspect.
 */
internal fun radarIsometricCamera(
    plateRadiusUnits: Float,
    pitchDegrees: Float = RADAR_ISO_PITCH_DEGREES,
    aspectRatio: Float = RADAR_ISO_REFERENCE_ASPECT_RATIO,
): RadarIsometricCamera {
    require(plateRadiusUnits.isFinite() && plateRadiusUnits > 0f) { "Plate radius must be positive." }
    require(pitchDegrees.isFinite()) { "Pitch must be finite." }
    require(aspectRatio.isFinite() && aspectRatio > 0f) { "Aspect ratio must be positive." }
    val pitch = Math.toRadians(pitchDegrees.coerceIn(20f, 80f).toDouble())
    val tanHalfVertical = RADAR_ISO_TAN_HALF_VERTICAL_FOV.toDouble()
    val tanHalfHorizontal = tanHalfVertical * aspectRatio.coerceIn(0.2f, 5f)
    val cosPitch = cos(pitch)
    val sinPitch = sin(pitch)
    val radius = plateRadiusUnits.toDouble()
    val verticalFit = radius * (cosPitch + sinPitch / tanHalfVertical)
    val horizontalFit = radius * sqrt(cosPitch * cosPitch + 1.0 / (tanHalfHorizontal * tanHalfHorizontal))
    val distance = maxOf(verticalFit, horizontalFit) * RADAR_ISO_FIT_MARGIN
    return RadarIsometricCamera(
        eyeX = 0f,
        eyeY = (distance * sinPitch).toFloat(),
        eyeZ = (distance * cosPitch).toFloat(),
        targetY = 0f,
    )
}

/**
 * Maps a real-world distance to the fraction (0..1) of the plate radius it occupies.
 *
 * The existing [radarPresentationDistance] legibility curve is normalised by its value at the
 * current visible radius, so the 3D plate, the Compose metre rings and the entities all share one
 * monotonic mapping. Distances at or beyond the visible radius clamp to the plate rim.
 */
internal fun radarIsometricRadialFraction(
    distanceMeters: Double,
    visibleRadiusMeters: Double,
): Float {
    require(distanceMeters.isFinite()) { "Distance must be finite." }
    require(visibleRadiusMeters.isFinite() && visibleRadiusMeters > 0.0) {
        "Visible radius must be positive."
    }
    val visible = radarPresentationDistance(visibleRadiusMeters)
    require(visible > 0.0) { "Visible radius maps to a zero-length presentation distance." }
    return (radarPresentationDistance(distanceMeters) / visible).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Places a bearing/distance on the north-up plate.
 *
 * North (0°) maps to -Z and east (90°) to +X, matching [RadarRelativePosition.scenePoint] and the
 * legacy 2D radar. The returned point is in plate scene units before [radarIsometricRotate] applies
 * the compass heading.
 */
internal fun radarIsometricWorldPoint(
    bearingDegrees: Double,
    distanceMeters: Double,
    visibleRadiusMeters: Double,
    plateRadiusUnits: Float = RADAR_ISO_PLATE_RADIUS_UNITS,
): RadarScenePoint {
    require(bearingDegrees.isFinite()) { "Bearing must be finite." }
    require(plateRadiusUnits.isFinite() && plateRadiusUnits > 0f) { "Plate radius must be positive." }
    val radius = radarIsometricRadialFraction(distanceMeters, visibleRadiusMeters) * plateRadiusUnits
    val radians = Math.toRadians(bearingDegrees)
    return RadarScenePoint((radius * sin(radians)).toFloat(), (-radius * cos(radians)).toFloat())
}

/**
 * Rotation, in degrees around +Y, applied to the whole radar plate so the map turns with the
 * compass (north-up when uncalibrated, like the legacy 2D radar).
 *
 * The value is normalised to `[0, 360)` and is meant to be handed to
 * `android.opengl.Matrix.rotateM(matrix, offset, degrees, 0f, 1f, 0f)`; a heading of 0° leaves the
 * north-up plate untouched. Under that right-handed rotation a point `(x, z)` becomes
 * `(x·cos + z·sin, -x·sin + z·cos)`, which is exactly what [radarIsometricRotate] applies.
 */
internal fun radarIsometricWorldRotation(headingDegrees: Float): Float {
    require(headingDegrees.isFinite()) { "Heading must be finite." }
    val normalized = headingDegrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

/**
 * Rotates a plate point around the plate centre by the compass heading.
 *
 * This is the single place that turns a north-up world point into the rendered pose, so the plate,
 * the ambient grid and every Digimon stay coherent. See [radarIsometricWorldRotation] for the sign.
 */
internal fun radarIsometricRotate(point: RadarScenePoint, headingDegrees: Float): RadarScenePoint {
    val angle = Math.toRadians(radarIsometricWorldRotation(headingDegrees).toDouble())
    val cosine = cos(angle)
    val sine = sin(angle)
    return RadarScenePoint(
        x = (point.x * cosine + point.z * sine).toFloat(),
        z = (-point.x * sine + point.z * cosine).toFloat(),
    )
}

/**
 * Height multiplier for a wild Digimon token, mirroring the first-person sprite scale contract:
 * later evolution stages read as bigger, and the ordering is part of the accepted contract.
 */
internal fun radarIsometricSpriteScale(stage: Int): Float = when (stage) {
    0 -> 0.85f
    1 -> 1.05f
    else -> 1.4f
}

/**
 * Chooses the visible pose name from the poses baked into an extruded GLB.
 *
 * Mirrors the first-person pose contract: walking wins while motion is enabled, `*2` frames are the
 * alternating frame, and the fallback chain always keeps at least an idle pose visible when the
 * artwork is incomplete. Returns `null` for an asset with no usable pose at all.
 */
internal fun radarIsometricPose(
    walking: Boolean,
    motion: Boolean,
    alternate: Boolean,
    available: Set<String>,
): String? {
    val base = if (walking && motion) "walk" else "idle"
    return listOfNotNull(if (motion && alternate) "${base}2" else null, base, "idle", "idle2")
        .firstOrNull { it in available }
}

/** Plate scene units per real-world metre at the current visible radius. */
internal fun radarIsometricPlateUnitsPerMeter(
    visibleRadiusMeters: Double,
    plateRadiusUnits: Float = RADAR_ISO_PLATE_RADIUS_UNITS,
): Float {
    require(visibleRadiusMeters.isFinite() && visibleRadiusMeters > 0.0) {
        "Visible radius must be positive."
    }
    require(plateRadiusUnits.isFinite() && plateRadiusUnits > 0f) { "Plate radius must be positive." }
    return plateRadiusUnits / visibleRadiusMeters.toFloat()
}

/**
 * Uniform scale for the authored radar plate GLB so the ambient grid represents real metres.
 *
 * The authored `world_grid` draws a line every [RADAR_ISO_AUTHORED_GRID_CELL_UNITS] units; one such
 * cell must measure [RADAR_ISO_GRID_CELL_METERS] real metres on the plate. With
 * `unitsPerMetre = plateRadius / visibleRadius`, the cell's scene size is
 * `GRID_CELL_METERS · unitsPerMetre`, so `scale = cellSceneSize / authoredCellUnits`.
 *
 * At the neutral zoom this is `30 · 6 / 500 = 0.36`; the authored floor (±200 units) then extends
 * ~12 plate radii, enough to cover the camera frustum at every zoom in the envelope.
 */
internal fun radarIsometricPlateAssetScale(
    visibleRadiusMeters: Double,
    plateRadiusUnits: Float = RADAR_ISO_PLATE_RADIUS_UNITS,
): Float {
    val unitsPerMeter = radarIsometricPlateUnitsPerMeter(visibleRadiusMeters, plateRadiusUnits)
    return RADAR_ISO_GRID_CELL_METERS.toFloat() * unitsPerMeter / RADAR_ISO_AUTHORED_GRID_CELL_UNITS
}

/** Real-world visible radius for the shared zoom contract, clamped to the envelope. */
internal fun radarIsometricVisibleRadiusMeters(zoom: Float): Double =
    RADAR_ISO_VISIBLE_RADIUS_AT_ZOOM_ONE_METERS /
        zoom.coerceIn(RADAR_ISO_MIN_ZOOM, RADAR_ISO_MAX_ZOOM).toDouble()

/**
 * Ground scroll phase for the isometric radar, in north-up scene metres.
 *
 * The partner token stays at the plate centre, so walking is only visible if the authored ground
 * slides the other way - the way the legacy 2D radar scrolls its grid under the centred marker. The
 * phase is measured from the first player fix, folded to half a grid cell ([RADAR_ISO_GRID_CELL_METERS])
 * so the finite authored floor never slides out from under the camera, and returned in north-up scene
 * axes (east = +x, north = -Z) ready for [radarIsometricRotate] to apply the compass heading.
 */
internal fun radarIsometricGroundPhase(
    anchor: GeoPoint,
    player: GeoPoint,
    gridCellMeters: Double = RADAR_ISO_GRID_CELL_METERS,
): RadarScenePoint {
    require(gridCellMeters.isFinite() && gridCellMeters > 0.0) { "Grid cell must be positive." }
    val relative = RadarWorldGeometry.relative(anchor, player)
    return RadarScenePoint(
        x = foldToHalfCell(-relative.eastMeters, gridCellMeters),
        z = foldToHalfCell(relative.northMeters, gridCellMeters),
    )
}

/** Folds a distance into [-cell/2, cell/2] so the ground phase stays one cell from its anchor. */
internal fun foldToHalfCell(meters: Double, cellMeters: Double): Float {
    val wrapped = meters - cellMeters * Math.round(meters / cellMeters)
    return wrapped.toFloat()
}
