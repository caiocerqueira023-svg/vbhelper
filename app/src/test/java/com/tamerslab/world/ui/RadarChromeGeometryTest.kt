package com.tamerslab.world.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the radar chrome geometry: angle conventions, ring anchors, viewport clamping
 * and the chrome-band collision rule the compass layer relies on.
 */
class RadarChromeGeometryTest {

    @Test
    fun cardinalScreenAnglePutsNorthAtTopAndEastAtRight() {
        assertEquals(0f, radarChromeCardinalScreenAngle(0f, 0f), 0f)
        assertEquals(90f, radarChromeCardinalScreenAngle(90f, 0f), 0f)
        assertEquals(180f, radarChromeCardinalScreenAngle(180f, 0f), 0f)
        assertEquals(270f, radarChromeCardinalScreenAngle(270f, 0f), 0f)
    }

    @Test
    fun headingRotationCarriesTheCompassRingAroundThePhone() {
        // Pointing east: east is at the top, north has swung to the left.
        assertEquals(0f, radarChromeCardinalScreenAngle(90f, 90f), 0f)
        assertEquals(270f, radarChromeCardinalScreenAngle(0f, 90f), 0f)
        // Pointing north-east: north is 45° left of the top.
        assertEquals(315f, radarChromeCardinalScreenAngle(0f, 45f), 0f)
        // Negative/over-turned headings stay in [0, 360).
        assertEquals(30f, radarChromeCardinalScreenAngle(0f, -30f), 0f)
        assertEquals(10f, radarChromeCardinalScreenAngle(370f, 0f), 0f)
        assertEquals(0f, radarChromeCardinalScreenAngle(0f, 720f), 0f)
    }

    @Test
    fun screenDirectionsUseTheClockwiseFromTopConvention() {
        val up = radarChromeScreenDirection(0f)
        val right = radarChromeScreenDirection(90f)
        val down = radarChromeScreenDirection(180f)
        val left = radarChromeScreenDirection(270f)
        assertEquals(0f, up.x, 0.00001f)
        assertEquals(-1f, up.y, 0.00001f)
        assertEquals(1f, right.x, 0.00001f)
        assertEquals(0f, right.y, 0.00001f)
        assertEquals(0f, down.x, 0.00001f)
        assertEquals(1f, down.y, 0.00001f)
        assertEquals(-1f, left.x, 0.00001f)
        assertEquals(0f, left.y, 0.00001f)
    }

    @Test
    fun polarPointMovesFromTheCentreAlongTheScreenAngle() {
        assertEquals(50f, radarChromePolarPoint(50f, 50f, 20f, 0f).x, 0.00001f)
        assertEquals(30f, radarChromePolarPoint(50f, 50f, 20f, 0f).y, 0.00001f)
        assertEquals(70f, radarChromePolarPoint(50f, 50f, 20f, 90f).x, 0.00001f)
        val diagonal = radarChromePolarPoint(0f, 0f, 10f, 45f)
        assertEquals(7.071068f, diagonal.x, 0.0001f)
        assertEquals(-7.071068f, diagonal.y, 0.0001f)
    }

    @Test
    fun ringRadiusIsAFractionOfHalfTheShorterEdge() {
        // The shorter edge (100) owns the unit, so the ring stays circular.
        assertEquals(25f, radarChromeRingRadiusPx(0.5f, 100f, 200f), 0.00001f)
        assertEquals(50f, radarChromeRingRadiusPx(1f, 100f, 100f), 0.00001f)
        assertEquals(0f, radarChromeRingRadiusPx(0f, 100f, 100f), 0f)
        // Out-of-range and degenerate input never inverts or explodes a ring.
        assertEquals(50f, radarChromeRingRadiusPx(4f, 100f, 100f), 0.00001f)
        assertEquals(0f, radarChromeRingRadiusPx(-1f, 100f, 100f), 0f)
        assertEquals(0f, radarChromeRingRadiusPx(0.5f, 0f, 100f), 0f)
    }

    @Test
    fun ringLabelAnchorIsDeterministicAndSitsInsideTheRing() {
        val first = radarChromeRingLabelAnchor(
            centerX = 100f, centerY = 100f, radiusPx = 80f, screenAngleDegrees = 55f,
            labelWidthPx = 40f, labelHeightPx = 16f, insetPx = 6f
        )
        val second = radarChromeRingLabelAnchor(
            centerX = 100f, centerY = 100f, radiusPx = 80f, screenAngleDegrees = 55f,
            labelWidthPx = 40f, labelHeightPx = 16f, insetPx = 6f
        )
        assertEquals(first, second)
        // Every corner of the label box lives inside the ring that owns it.
        listOf(
            first,
            RadarChromePoint(first.x + 40f, first.y),
            RadarChromePoint(first.x, first.y + 16f),
            RadarChromePoint(first.x + 40f, first.y + 16f)
        ).forEach { corner ->
            val distance = kotlin.math.hypot((corner.x - 100f).toDouble(), (corner.y - 100f).toDouble())
            assertTrue("corner $corner outside ring", distance <= 80.0 + 0.001)
        }
    }

    @Test
    fun ringLabelsAtTheTopOfTheViewportAreInsideTheirRing() {
        // A label on the top of a ring: the returned top-left leaves room for the text height.
        val anchor = radarChromeRingLabelAnchor(
            centerX = 200f, centerY = 200f, radiusPx = 100f, screenAngleDegrees = 0f,
            labelWidthPx = 30f, labelHeightPx = 14f, insetPx = 4f
        )
        assertEquals(200f - 15f, anchor.x, 0.00001f)
        // radius(100) - inset(4) - halfExtent(15) puts the label centre 81px above the centre.
        assertEquals(200f - 100f + 4f + 15f - 7f, anchor.y, 0.00001f)
    }

    @Test
    fun labelsClampAtAllFourEdges() {
        val width = 100f
        val height = 20f
        val viewportWidth = 400f
        val viewportHeight = 800f
        val margin = 8f
        assertEquals(
            RadarChromePoint(8f, 100f),
            radarChromeClampLabel(-5f, 100f, width, height, viewportWidth, viewportHeight, margin)
        )
        assertEquals(
            RadarChromePoint(400f - 8f - width, 100f),
            radarChromeClampLabel(350f, 100f, width, height, viewportWidth, viewportHeight, margin)
        )
        assertEquals(
            RadarChromePoint(100f, 8f),
            radarChromeClampLabel(100f, -12f, width, height, viewportWidth, viewportHeight, margin)
        )
        assertEquals(
            RadarChromePoint(100f, 800f - 8f - height),
            radarChromeClampLabel(100f, 790f, width, height, viewportWidth, viewportHeight, margin)
        )
    }

    @Test
    fun labelsInsideTheViewportAreNotMoved() {
        assertEquals(
            RadarChromePoint(100f, 200f),
            radarChromeClampLabel(100f, 200f, 80f, 24f, 400f, 800f, 8f)
        )
    }

    @Test
    fun oversizedLabelsStickToTheStartingMargin() {
        assertEquals(
            RadarChromePoint(8f, 8f),
            radarChromeClampLabel(-40f, -40f, 500f, 900f, 400f, 800f, 8f)
        )
    }

    @Test
    fun chromeBandIntrusionMatchesTheReservedBands() {
        // Top band: anything whose top is above the clearance is skipped.
        assertTrue(radarChromeIntrudesChromeBand(10f, 20f, 800f, 100f, 120f))
        // Just clear of the top band.
        assertFalse(radarChromeIntrudesChromeBand(120f, 20f, 800f, 100f, 120f))
        // Bottom band: 800 - 120 = 680 is the last clear pixel row.
        assertTrue(radarChromeIntrudesChromeBand(670f, 20f, 800f, 100f, 120f))
        assertFalse(radarChromeIntrudesChromeBand(650f, 20f, 800f, 100f, 120f))
    }

    @Test
    fun overlappingLabelBoxesAreDetectedWithTheRequestedGap() {
        val a = RadarChromePoint(10f, 10f)
        val b = RadarChromePoint(40f, 10f)
        assertTrue(radarChromeLabelsOverlap(a, 40f, 16f, b, 40f, 16f))
        assertFalse(radarChromeLabelsOverlap(a, 20f, 16f, b, 20f, 16f))
        assertTrue(radarChromeLabelsOverlap(a, 20f, 16f, b, 20f, 16f, gap = 12f))
        assertTrue(radarChromeLabelsOverlap(a, 20f, 16f, RadarChromePoint(10f, 20f), 20f, 16f))
    }
}
