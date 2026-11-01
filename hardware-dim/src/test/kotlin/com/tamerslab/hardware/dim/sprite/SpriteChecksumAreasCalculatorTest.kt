package com.tamerslab.hardware.dim.sprite

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpriteChecksumAreasCalculatorTest {
    @Test
    fun `dim and bem calculators expose configured areas`() {
        val dim = SpriteChecksumAreasCalculator.buildForDIM()
        assertEquals(28, dim.numberOfChunks)
        assertEquals(0x10000, dim.chunkInterval)
        assertEquals(0x2000, dim.relativeChecksumStartLocation)
        assertEquals(0x1000, dim.checksumChunkSize)

        val bem = SpriteChecksumAreasCalculator.buildForBEM()
        assertEquals(0x30, bem.numberOfChunks)
        assertEquals(0x10000, bem.chunkInterval)
        assertEquals(0x2000, bem.relativeChecksumStartLocation)
        assertEquals(0x1000, bem.checksumChunkSize)
    }

    @Test
    fun `chunk and checksum boundaries match dim layout`() {
        val calculator = SpriteChecksumAreasCalculator.buildForDIM()

        assertEquals(0, calculator.calculateWhichChunk(0x1FFF))
        assertEquals(0, calculator.calculateWhichChunk(0x2000))
        assertEquals(0, calculator.calculateWhichChunk(0x11FFF))
        assertEquals(1, calculator.calculateWhichChunk(0x12000))

        assertEquals(0x2000, calculator.nextChecksumStart(0x1FFF))
        assertEquals(0x12000, calculator.nextChecksumStart(0x2000))
        assertEquals(0x12000, calculator.nextChecksumStart(0x11FFF))
        assertEquals(Int.MAX_VALUE, calculator.nextChecksumStart(0x1B3001))

        assertEquals(0x3000, calculator.nextChecksumEnd(0x1FFF))
        assertEquals(0x3000, calculator.nextChecksumEnd(0x2000))
        assertEquals(0x3000, calculator.nextChecksumEnd(0x2FFF))
        assertEquals(0x13000, calculator.nextChecksumEnd(0x3000))
        assertEquals(0x13000, calculator.nextChecksumEnd(0x12000))
        assertEquals(0x1B3000, calculator.nextChecksumEnd(0x1B2FFF))
        assertEquals(Int.MAX_VALUE, calculator.nextChecksumEnd(0x1B3001))
    }

    @Test
    fun `checksum membership respects chunk starts and sizes`() {
        val calculator = SpriteChecksumAreasCalculator.buildForDIM()

        assertFalse(calculator.isPartOfChecksum(0x1FFF))
        assertTrue(calculator.isPartOfChecksum(0x2000))
        assertTrue(calculator.isPartOfChecksum(0x2FFF))
        assertFalse(calculator.isPartOfChecksum(0x3000))
        assertTrue(calculator.isPartOfChecksum(0x12000))
        assertTrue(calculator.isPartOfChecksum(0x1B2FFF))
        assertFalse(calculator.isPartOfChecksum(0x1B3000))
        assertFalse(calculator.isPartOfChecksum(0x1C2000))
    }

    @Test
    fun `checksum offsets wrap across the word space`() {
        val calculator = SpriteChecksumAreasCalculator.buildForDIM()

        assertEquals(0, calculator.calculateChecksumOffset(5, 5))
        assertEquals(7, calculator.calculateChecksumOffset(12, 5))
        assertEquals(65533, calculator.calculateChecksumOffset(1, 4))
        assertEquals(0xFFFF, calculator.calculateChecksumOffset(0, 1))
    }

    @Test
    fun `checksum lookup clamps locations before the first area`() {
        val calculator = SpriteChecksumAreasCalculator.buildForDIM()
        val checksums = (0 until 28).toList()

        assertEquals(0, calculator.getChecksumForLocationFromList(-0x10000, checksums))
        assertEquals(0, calculator.getChecksumForLocationFromList(0x2000, checksums))
        assertEquals(1, calculator.getChecksumForLocationFromList(0x12000, checksums))
        assertEquals(27, calculator.getChecksumForLocationFromList(0x1B2000, checksums))
    }

    @Test
    fun `checksum builder ignores words outside checksum areas`() {
        val calculator = SpriteChecksumAreasCalculator.buildForDIM()
        val builder = SpriteChecksumBuilder(calculator)

        builder.addBytes(byteArrayOf(1, 2), 0x1FFE)
        assertEquals(List(28) { 0 }, builder.getChecksums())

        builder.addBytes(byteArrayOf(1, 2), 0x2000)
        assertEquals(0x0201, builder.getChecksums()[0])

        builder.addBytes(byteArrayOf(3, 4, 5, 6), 0x2FFE)
        assertEquals((0x0201 + 0x0403) and 0xFFFF, builder.getChecksums()[0])
        assertEquals(List(27) { 0 }, builder.getChecksums().drop(1))
    }
}
