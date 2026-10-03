package com.github.nacabaro.vbhelper.screens.cardScreen

import org.junit.Assert.*
import org.junit.Test

class CardChartViewportTest {
    @Test fun fitCentersNarrowContentAndStartsTallChartsAtTheTop() {
        val fit = CardChartViewport.fit(CardChartSize(200f, 1000f), CardChartSize(400f, 500f))
        assertEquals(1f, fit.scale, .001f)
        assertEquals(100f, fit.x, .001f)
        assertEquals(0f, fit.y, .001f)
    }

    @Test fun fitDoesNotShrinkSpriteTouchTargetsBelow48Dp() {
        val fit = CardChartViewport.fit(CardChartSize(2000f, 700f), CardChartSize(320f, 480f))
        assertTrue(fit.scale * CardEvolutionLayout.NODE_SIZE >= 48f)
    }

    @Test fun zoomKeepsThePointUnderTheFingersInPlace() {
        val content = CardChartSize(2000f, 2000f)
        val viewport = CardChartSize(400f, 500f)
        val before = CardChartViewport(1f, -200f, -300f)
        val finger = CardChartPoint(150f, 200f)
        val after = before.transform(content, viewport, 1.5f, CardChartPoint(0f, 0f), finger)
        assertEquals((finger.x - before.x) / before.scale, (finger.x - after.x) / after.scale, .001f)
        assertEquals((finger.y - before.y) / before.scale, (finger.y - after.y) / after.scale, .001f)
    }

    @Test fun panningIsBoundedSoTheChartCannotBeLost() {
        val content = CardChartSize(1000f, 1000f)
        val viewport = CardChartSize(400f, 500f)
        val high = CardChartViewport(1f, 10000f, 10000f).clamped(content, viewport)
        val low = CardChartViewport(1f, -10000f, -10000f).clamped(content, viewport)
        assertEquals(0f, high.x, .001f)
        assertEquals(0f, high.y, .001f)
        assertEquals(-600f, low.x, .001f)
        assertEquals(-500f, low.y, .001f)
    }

    @Test fun smallContentStaysCenteredEvenWhenDragged() {
        val value = CardChartViewport(1f, 999f, -999f)
            .clamped(CardChartSize(100f, 100f), CardChartSize(400f, 500f))
        assertEquals(150f, value.x, .001f)
        assertEquals(200f, value.y, .001f)
    }

    @Test fun accessibilityScrollPositionsAreZeroForCenteredAxesAndBoundedForOverflow() {
        val content = CardChartSize(120f, 1000f)
        val view = CardChartSize(304f, 400f)
        val fit = CardChartViewport.fit(content, view)
        assertEquals(CardChartPoint(0f, 0f), fit.scrollPosition(content, view))
        assertEquals(CardChartPoint(0f, 600f), CardChartViewport(1f, 92f, -2000f).scrollPosition(content, view))
    }

    @Test fun zoomLimitsAndInvalidGesturesKeepTheViewportFinite() {
        val content = CardChartSize(1000f, 1000f)
        val viewport = CardChartSize(400f, 500f)
        val initial = CardChartViewport.fit(content, viewport)
        val huge = initial.transform(content, viewport, 1000f, CardChartPoint(0f, 0f), CardChartPoint(200f, 250f))
        assertEquals(CardChartViewport.MAX_SCALE, huge.scale, .001f)
        assertEquals(initial, initial.transform(content, viewport, Float.NaN,
            CardChartPoint(0f, 0f), CardChartPoint(0f, 0f)))
    }
}
