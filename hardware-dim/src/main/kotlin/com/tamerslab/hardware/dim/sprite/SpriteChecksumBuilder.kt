package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteUtils
import com.tamerslab.hardware.dim.util.RawChecksumBuilder

class SpriteChecksumBuilder(
    private val spriteChecksumAreasCalculator: SpriteChecksumAreasCalculator
) {
    private val checksumBuilders: ArrayList<RawChecksumBuilder> = ArrayList()

    init {
        for (i in 0 until spriteChecksumAreasCalculator.numberOfChunks) {
            checksumBuilders.add(RawChecksumBuilder())
        }
    }

    constructor() : this(SpriteChecksumAreasCalculator.buildForDIM())

    fun addBytes(bytes: ByteArray, currentRelativeLocation: Int) {
        val unsigned16BitValues = ByteUtils.getUnsigned16Bit(bytes)
        for (i in unsigned16BitValues.indices) {
            val wordLocation = currentRelativeLocation + i * 2
            if (spriteChecksumAreasCalculator.isPartOfChecksum(wordLocation)) {
                add16BitInt(unsigned16BitValues[i], wordLocation)
            }
        }
    }

    private fun add16BitInt(value: Int, relativeLocation: Int) {
        val chunk = spriteChecksumAreasCalculator.calculateWhichChunk(relativeLocation)
        checksumBuilders[chunk].add16BitInt(value)
    }

    fun getChecksums(): ArrayList<Int> {
        val currentChecksums = ArrayList<Int>()
        for (rawChecksumBuilder in checksumBuilders) {
            currentChecksums.add(rawChecksumBuilder.checksum)
        }
        return currentChecksums
    }
}
