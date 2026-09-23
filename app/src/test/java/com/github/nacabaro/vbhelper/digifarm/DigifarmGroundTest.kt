package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.digifarm.map.DigifarmGround
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DigifarmGroundTest {
    @Test
    fun movementUsesContinuousPointsAtAConstantWorldSpeed() {
        val start = MapPoint(200f, 300f)
        val target = MapPoint(340f, 450f)
        val first = DigifarmGround.advance(start, target, 0.4f)
        val second = DigifarmGround.advance(first, target, 0.4f)
        assertTrue(first.x > start.x && first.x < target.x)
        assertTrue(first.y > start.y && first.y < target.y)
        assertTrue(second.x > first.x && second.y > first.y)
        assertTrue(DigifarmGround.isWalkable(first))
        assertEquals(0.056f, DigifarmGround.worldDistance(start, first), 0.0001f)
    }

    @Test
    fun destinationsAndSpawnsStayOnTheIsland() {
        assertTrue(DigifarmGround.safeSpawns.all(DigifarmGround::isWalkable))
        val random = Random(21)
        repeat(200) { assertTrue(DigifarmGround.isWalkable(DigifarmGround.randomPoint(random))) }
        assertTrue(DigifarmGround.isWalkable(DigifarmGround.clamp(MapPoint(-100f, 900f))))
    }
}
