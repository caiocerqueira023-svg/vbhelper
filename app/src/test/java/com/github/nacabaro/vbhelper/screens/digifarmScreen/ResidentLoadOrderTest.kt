package com.github.nacabaro.vbhelper.screens.digifarmScreen

import org.junit.Assert.assertEquals
import org.junit.Test

class ResidentLoadOrderTest {
    @Test
    fun selectedResidentLoadsBeforeTheRestWithoutReorderingPeers() {
        val frames = listOf("a", "b", "c").map { id ->
            ResidentFrames(id, emptyMap(), byteArrayOf(), "key-$id")
        }
        val poses = listOf(
            ResidentPose("a", 0f, 0f, "EXPLORE", false, false),
            ResidentPose("b", 0f, 0f, "EXPLORE", false, false),
            ResidentPose("c", 0f, 0f, "EXPLORE", false, true),
        )

        assertEquals(listOf("c", "a", "b"), prioritizeResidentLoads(frames, poses).map { it.id })
    }
}
