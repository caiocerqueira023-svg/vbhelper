package com.github.nacabaro.vbhelper.screens.worldScreen

import org.junit.Assert.*
import org.junit.Test

class RadarOverheadPlacementTest {
    @Test fun labelsSitFullyAboveTheSpriteAtTheirMeasuredHeight() {
        val position=radarOverheadPosition(160f,200f,120,60,320,8,8)!!
        assertEquals(100,position.x)
        assertEquals(132,position.y)
        assertTrue(position.y+60<200)
    }
    @Test fun longerAndLargerFontLabelsMoveUpRatherThanOverlapTheirDigimon() {
        val short=radarOverheadPosition(160f,240f,100,32,320,8,8)!!
        val expanded=radarOverheadPosition(160f,240f,200,90,320,8,8)!!
        assertTrue(expanded.y<short.y)
        assertEquals(short.y+32,expanded.y+90)
    }
    @Test fun labelsStayInsideHorizontalEdgesAndHideWhenNoOverheadSpaceExists() {
        assertEquals(8,radarOverheadPosition(0f,180f,120,50,320,8,8)!!.x)
        assertEquals(192,radarOverheadPosition(320f,180f,120,50,320,8,8)!!.x)
        assertNull(radarOverheadPosition(160f,20f,120,50,320,8,8))
        assertNull(radarOverheadPosition(160f,180f,400,50,320,8,8))
    }
}
