package com.tamerslab.gamesave

import com.tamerslab.gamesave.WorldDsStatConversion.AppStats
import org.junit.Assert.*
import org.junit.Test

class WorldDsRecordCodecTest {
    private val stats = WorldDsStatConversion.toNative(AppStats(stage = 2, baseHp = 100, baseBp = 100, baseAp = 100))

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (8 * index)) }

    private fun s16(bytes: ByteArray, at: Int): Int = u16(bytes, at).toShort().toInt()

    private fun s32(bytes: ByteArray, at: Int): Int = u32(bytes, at).toInt()

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 255).toByte()
        bytes[at + 1] = ((value shr 8) and 255).toByte()
    }

    private fun put32(bytes: ByteArray, at: Int, value: Long) {
        for (index in 0 until 4) bytes[at + index] = ((value shr (8 * index)) and 255).toByte()
    }

    @Test fun generatedRecordWritesEveryDocumentedFieldLittleEndian() {
        val generated = WorldDsRecordCodec.generate(7, 20, "AGUMON", "AGUMON", stats)
        val bytes = generated.bytes
        assertEquals(384, bytes.size)
        assertEquals(7L, u32(bytes, 0x00))
        assertEquals(20L, u32(bytes, 0x04))
        assertEquals(0x50, u16(bytes, 0x08))
        assertEquals(0x56, u16(bytes, 0x0A))
        assertEquals(0xFFFF, u16(bytes, 0x18))
        assertEquals(stats.hp.toLong(), u32(bytes, 0x3C))
        assertEquals(stats.maxHp.toLong(), u32(bytes, 0x40))
        assertEquals(stats.mp, u16(bytes, 0x44))
        assertEquals(stats.maxMp, u16(bytes, 0x46))
        assertEquals(stats.attack, u16(bytes, 0x48))
        assertEquals(stats.defense, u16(bytes, 0x4A))
        assertEquals(stats.spirit, u16(bytes, 0x4C))
        assertEquals(stats.speed, u16(bytes, 0x4E))
        assertEquals(stats.aptitude, u16(bytes, 0x52))
        assertEquals(stats.level, u16(bytes, 0x74))
        stats.exp.forEachIndexed { index, value -> assertEquals(value, u32(bytes, 0x78 + index * 4)) }
        assertEquals("AGUMON", generated.name)
        assertFalse(generated.nameFallback)
    }

    @Test fun patchingRetainedRecordPreservesUnknownNativeBytesAndSkillsRegion() {
        val seed = ByteArray(384) { 0x35 }
        seed[0x100] = 0x7F
        seed[0x140] = 0x11
        val generated = WorldDsRecordCodec.generate(9, 33, "BETAMON", "BETAMON", stats, seed)
        assertEquals(0x7F, generated.bytes[0x100].toInt() and 255)
        assertEquals(0x11, generated.bytes[0x140].toInt() and 255)
        assertEquals(9L, u32(generated.bytes, 0x00))
        assertEquals(33L, u32(generated.bytes, 0x04))
    }

    @Test fun unencodableNicknameFallsBackToSpeciesName() {
        val generated = WorldDsRecordCodec.generate(3, 20, "カイザー", "GREYMON", stats)
        assertEquals("GREYMON", generated.name)
        assertTrue(generated.nameFallback)
        assertEquals(0x56, u16(generated.bytes, 0x08))
    }

    @Test fun longNamesTruncateAtEightGlyphsWithTerminator() {
        val generated = WorldDsRecordCodec.generate(3, 20, "ABCDEFGHIJK", "AGUMON", stats)
        assertTrue(generated.nameTruncated)
        assertEquals(0x57, u16(generated.bytes, 0x08 + 14))
        assertEquals(0xFFFF, u16(generated.bytes, 0x08 + 16))
    }

    @Test fun namesRoundTripThroughThePublishedGlyphTable() {
        val generated = WorldDsRecordCodec.generate(3, 20, "Agumon!", "AGUMON", stats)
        val codes = List(9) { u16(generated.bytes, 0x08 + it * 2) }.takeWhile { it != 0xFFFF }
        assertEquals("Agumon!", WorldDsRecordCodec.decodeName(codes))
    }

    @Test fun base64RoundTripKeepsEveryByte() {
        val generated = WorldDsRecordCodec.generate(3, 20, "AGUMON", "AGUMON", stats)
        assertArrayEquals(generated.bytes, WorldDsRecordCodec.decodeBase64(WorldDsRecordCodec.base64(generated.bytes)))
    }

    @Test fun identityAndSpeciesArgumentsRejectValuesTheGameNeverHandsOut() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsRecordCodec.generate(0, 20, "AGUMON", "AGUMON", stats)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDsRecordCodec.generate(1, WorldDsSaveReader.SPECIES_COUNT.toLong(), "AGUMON", "AGUMON", stats)
        }
    }

    @Test fun speciesDefaultsFillAbilitiesSkillsHistoryAndMarkers() {
        val defaults = WorldDsRecordDefaults(
            unk1A = 3,
            unk50 = 2,
            ability = 53,
            skills = listOf(1, 19, 186, 149),
            resistances = listOf(187, 500, 850, 500, 500, 500, 500),
        )
        val generated = WorldDsRecordCodec.generate(7, 166, "GARUDAMON", "GARUDAMON", stats, defaults = defaults)
        val bytes = generated.bytes
        assertEquals(3, bytes[0x1A].toInt() and 255)
        assertEquals(2, u16(bytes, 0x50))
        assertEquals(3, bytes[0x1E].toInt() and 255)
        assertEquals(53, u16(bytes, 0x22))
        assertEquals(0xFFFF, u16(bytes, 0x24))
        assertEquals(1, u16(bytes, 0x54))
        assertEquals(19, u16(bytes, 0x56))
        assertEquals(186, u16(bytes, 0x58))
        assertEquals(149, u16(bytes, 0x5A))
        assertEquals(0xFFFF, u16(bytes, 0x5C))
        assertEquals(0, s32(bytes, 0xA0))
        assertEquals(-1, s32(bytes, 0xA4))
        assertEquals(-1, s32(bytes, 0xA8))
        assertEquals(-1, s32(bytes, 0xAC))
        assertEquals(166, s32(bytes, 0xB0))
        assertEquals(-1, s32(bytes, 0xB4))
        assertEquals(3, bytes[0xEE].toInt() and 255)
        assertEquals(0, s16(bytes, 0xF0))
        assertEquals(187, s16(bytes, 0xF2))
        assertEquals(850, s16(bytes, 0xF6))
        assertEquals(33, u16(bytes, 0x70))
        assertEquals(0x40, u16(bytes, 0x108))
        assertEquals(2, bytes[0x1B].toInt() and 255)
        assertTrue(s32(bytes, 0x9C) > 0)
    }

    @Test fun detectsLegacyRecordsThatNeedSpeciesDefaultsButAcceptsARegeneratedRecord() {
        assertTrue(WorldDsRecordCodec.needsSpeciesRepair(ByteArray(384)))
        val legacy = ByteArray(384).also { record ->
            put32(record, 0x00, 0xACA7BA58L)
            put32(record, 0x04, 35)
            put16(record, 0x08, 0x63); put16(record, 0x0A, 0xFFFF)
            record[0x1E] = 1
            put16(record, 0x52, 31)
            put16(record, 0x74, 3)
        }
        assertTrue(WorldDsRecordCodec.needsSpeciesRepair(legacy))
        val defaults = WorldDsRecordDefaults(unk1A = 7, unk50 = 2, ability = 16,
            skills = listOf(3, 145, 331, 20), resistances = List(7) { 500 })
        val repaired = WorldDsRecordCodec.generate(0xACA7BA58L, 35, "RENAMON", "RENAMON", stats, legacy, defaults,
            lineage = listOf(4))
        assertFalse(WorldDsRecordCodec.needsSpeciesRepair(repaired.bytes))
        assertEquals(4, s32(repaired.bytes, 0xA4))
        assertEquals(35, s32(repaired.bytes, 0xA8))
        val wrongHistory = repaired.bytes.copyOf()
        put32(wrongHistory, 0xA4 + 4, 1)
        assertTrue(WorldDsRecordCodec.needsSpeciesRepair(wrongHistory))
        val wrongFlags = repaired.bytes.copyOf()
        put32(wrongFlags, 0xA0, -1L)
        assertTrue(WorldDsRecordCodec.needsSpeciesRepair(wrongFlags))
    }

    @Test fun retainedSeedKeepsNativeMaxLevelAboveAppAptitudeRange() {
        val seed = ByteArray(384)
        put32(seed, 0x00, 0xACA7BA58L)
        put32(seed, 0x04, 313)
        seed[0x1A] = 3
        put16(seed, 0x50, 3)
        put16(seed, 0x52, 999)
        val generated = WorldDsRecordCodec.generate(0xACA7BA58L, 313, "VARODURUMON", "VARODURUMON", stats, seed)
        assertEquals(0xACA7BA58L, u32(generated.bytes, 0x00))
        assertEquals(999, u16(generated.bytes, 0x52))
        assertEquals(999, generated.maxLevel)
        assertEquals(313, s32(generated.bytes, 0xA4 + 4 * 4))
    }

    @Test fun lineageFillsTheFormsTheGameShowsForEachStage() {
        val salamon = WorldDsRecordCodec.generate(1, 29, "SALAMON", "SALAMON", stats, lineage = listOf(8))
        assertEquals(0x18, s32(salamon.bytes, 0xA0))
        assertEquals(listOf(8, 29, -1, -1, -1), (0 until 5).map { s32(salamon.bytes, 0xA4 + it * 4) })
        assertEquals(1, salamon.bytes[0xEE].toInt() and 255)

        val garudamon = WorldDsRecordCodec.generate(1, 166, "GARUDAMON", "GARUDAMON", stats,
            lineage = listOf(91, 19))
        assertEquals(0, s32(garudamon.bytes, 0xA0))
        assertEquals(listOf(-1, 19, 91, 166, -1), (0 until 5).map { s32(garudamon.bytes, 0xA4 + it * 4) })
    }

    @Test fun previousSpeciesIdsReportsFormsBeforeTheCurrentOne() {
        val generated = WorldDsRecordCodec.generate(1, 294, "GRANKUWAGAMON", "GRANKUWAGAMON", stats,
            lineage = listOf(155, 81, 21))
        assertEquals(listOf(21L, 81L, 155L), WorldDsRecordCodec.previousSpeciesIds(generated.bytes))
    }
}

