package com.github.nacabaro.vbhelper.world

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class WorldSpawnSelectorTest {
    @Test
    fun `a species has one chance even when present on multiple cards`() {
        val agumonVariants = (1L..3L).map { cardId -> character(id = cardId, cardId = cardId, name = byteArrayOf(1)) }
        val gabumon = character(id = 4, cardId = 4, name = byteArrayOf(2))
        val characters = agumonVariants + gabumon
        val counts = mutableMapOf<Long, Int>()

        repeat(40_000) {
            val selected = WorldSpawnSelector.selectCharacter(characters, random = Random(it))!!
            val species = if (selected.nameSprite.contentEquals(byteArrayOf(1))) 1L else 2L
            counts[species] = (counts[species] ?: 0) + 1
        }

        val agumonRate = counts.getValue(1L) / 40_000.0
        assertTrue("Agumon rate was $agumonRate", abs(agumonRate - 0.5) < 0.02)
    }

    @Test
    fun `available stage weights are normalized instead of falling back to another stage`() {
        val characters = listOf(
            character(id = 1, cardId = 1, stage = 2, name = byteArrayOf(2)),
            character(id = 2, cardId = 2, stage = 3, name = byteArrayOf(3))
        )
        var childCount = 0

        repeat(50_000) {
            if (WorldSpawnSelector.selectCharacter(characters, random = Random(it))!!.stage == 2) childCount++
        }

        // 0.40 / (0.40 + 0.24): only stages that have loaded Digimon participate.
        assertTrue(abs(childCount / 50_000.0 - 0.625) < 0.02)
    }

    @Test
    fun `canonical species names group variants whose name sprites differ`() {
        val characters = listOf(
            character(id = 1, cardId = 1, name = byteArrayOf(1)),
            character(id = 2, cardId = 2, name = byteArrayOf(9)),
            character(id = 3, cardId = 3, name = byteArrayOf(2))
        )
        var agumonCount = 0

        repeat(30_000) {
            val selected = WorldSpawnSelector.selectCharacter(
                characters,
                speciesNames = mapOf(1L to "Agumon", 2L to "agumon", 3L to "Gabumon"),
                random = Random(it)
            )!!
            if (selected.id != 3L) agumonCount++
        }

        assertTrue(abs(agumonCount / 30_000.0 - 0.5) < 0.02)
    }

    private fun character(id: Long, cardId: Long, stage: Int = 2, name: ByteArray) = CardCharacter(
        id = id,
        cardId = cardId,
        spriteId = id,
        charaIndex = 0,
        stage = stage,
        attribute = NfcCharacter.Attribute.Vaccine,
        baseHp = 0,
        baseBp = 0,
        baseAp = 0,
        nameSprite = name,
        nameWidth = 1,
        nameHeight = 1
    )
}
