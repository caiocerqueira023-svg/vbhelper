package com.tamerslab.world.ui

import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry for the radar chrome: the compass layer drawn over the isometric world, and the
 * clamped label overlays (entity pills, speech bubbles).
 *
 * No Compose or Android types live here on purpose: the angle convention, the ring-radius fractions
 * and the label clamping are the shared contract between [RadarCompassLayer], the projected entity
 * overlays and the Lead's projection code, and they are unit tested on the JVM.
 *
 * Angle convention: every screen angle is measured in degrees **clockwise from the top of the
 * viewport** (0° = up, 90° = right, 180° = down, 270° = left), matching the old 2D radar's
 * `(cardinalAzimuth - heading - 90°)` arrow math. The compass ring therefore keeps the phone's
 * heading at the top: north sits at 0° when the phone points north and at 270° when it points east.
 */

/** A point in viewport pixels; used instead of Compose's Offset so this file stays platform-free. */
internal data class RadarChromePoint(val x: Float, val y: Float)

/** Normalizes an angle into [0, 360); non-finite input collapses to 0. */
internal fun radarChromeNormalizeDegrees(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    val wrapped = degrees % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

/**
 * Screen angle of a cardinal azimuth once the world is rotated by [headingDegrees].
 *
 * With a north-pointing phone (heading 0) N is at 0° and E is at 90°; a phone pointing east
 * (heading 90) puts N at 270° and E at 0°.
 */
internal fun radarChromeCardinalScreenAngle(azimuthDegrees: Float, headingDegrees: Float): Float =
    radarChromeNormalizeDegrees(azimuthDegrees - headingDegrees)

/** Unit vector for a screen angle (0° = up, 90° = right). */
internal fun radarChromeScreenDirection(screenAngleDegrees: Float): RadarChromePoint {
    val radians = Math.toRadians(radarChromeNormalizeDegrees(screenAngleDegrees).toDouble())
    return RadarChromePoint(sin(radians).toFloat(), -cos(radians).toFloat())
}

/** Point [radiusPx] away from the viewport centre at a screen angle. */
internal fun radarChromePolarPoint(
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    screenAngleDegrees: Float
): RadarChromePoint {
    val direction = radarChromeScreenDirection(screenAngleDegrees)
    val radius = if (radiusPx.isFinite()) radiusPx else 0f
    return RadarChromePoint(centerX + direction.x * radius, centerY + direction.y * radius)
}

/**
 * Radius in pixels of a [fraction] of **half the shorter viewport edge**, the unit used by
 * [RadarRingMark.fraction]. Materials outside the viewport are clamped so an out-of-range mark can
 * never invert a ring.
 */
internal fun radarChromeRingRadiusPx(
    fraction: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float
): Float {
    val shorterEdge = minOf(viewportWidthPx, viewportHeightPx)
    if (!shorterEdge.isFinite() || shorterEdge <= 0f) return 0f
    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    return safeFraction * shorterEdge / 2f
}

/**
 * Top-left corner of a range-label box drawn on the inside of the ring at a fixed screen angle.
 *
 * The anchor is a pure function of its arguments, so the "200 m" / "400 m" labels never drift
 * between frames: the label centre sits [insetPx] plus half the label's longest side inside
 * [radiusPx], and the top-left corner is returned ready for drawing.
 */
internal fun radarChromeRingLabelAnchor(
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    screenAngleDegrees: Float,
    labelWidthPx: Float,
    labelHeightPx: Float,
    insetPx: Float
): RadarChromePoint {
    val direction = radarChromeScreenDirection(screenAngleDegrees)
    val halfExtent = maxOf(labelWidthPx, labelHeightPx).coerceAtLeast(0f) / 2f
    val anchorRadius = (radiusPx - insetPx - halfExtent).coerceAtLeast(0f)
    val labelCenterX = centerX + direction.x * anchorRadius
    val labelCenterY = centerY + direction.y * anchorRadius
    return RadarChromePoint(labelCenterX - labelWidthPx / 2f, labelCenterY - labelHeightPx / 2f)
}

/**
 * Clamps a label rectangle inside the viewport and returns its corrected top-left corner.
 *
 * A label wider than the available space sticks to the starting margin instead of producing an
 * inverted range, so a long localized name can never be drawn off-screen or with a negative width.
 */
internal fun radarChromeClampLabel(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    margin: Float
): RadarChromePoint {
    val safeMargin = margin.coerceAtLeast(0f)
    val maxLeft = (viewportWidth - safeMargin - width).coerceAtLeast(safeMargin)
    val maxTop = (viewportHeight - safeMargin - height).coerceAtLeast(safeMargin)
    return RadarChromePoint(
        left.coerceIn(safeMargin, maxLeft),
        top.coerceIn(safeMargin, maxTop)
    )
}

/**
 * True when a label rectangle crosses the chrome bands reserved at the top and bottom of the
 * viewport (the floating top cluster and the bottom signal band). Such labels are skipped rather
 * than drawn under the chrome.
 */
internal fun radarChromeIntrudesChromeBand(
    top: Float,
    height: Float,
    viewportHeight: Float,
    topClearance: Float,
    bottomClearance: Float
): Boolean = top < topClearance || top + height > viewportHeight - bottomClearance

/** True when two label rectangles overlap (plus an optional [gap]); used to keep ring labels apart. */
internal fun radarChromeLabelsOverlap(
    first: RadarChromePoint,
    firstWidth: Float,
    firstHeight: Float,
    second: RadarChromePoint,
    secondWidth: Float,
    secondHeight: Float,
    gap: Float = 0f
): Boolean {
    val safeGap = gap.coerceAtLeast(0f)
    return first.x < second.x + secondWidth + safeGap &&
        second.x < first.x + firstWidth + safeGap &&
        first.y < second.y + secondHeight + safeGap &&
        second.y < first.y + firstHeight + safeGap
}
