package com.tamerslab.hardware.dim.sprite

class SpriteChecksumAreasCalculator(
    val numberOfChunks: Int,
    val chunkInterval: Int,
    val relativeChecksumStartLocation: Int,
    val checksumChunkSize: Int
) {
    private fun isAfterChecksumStart(relativeLocation: Int): Boolean =
        relativeLocation >= relativeChecksumStartLocation

    fun calculateWhichChunk(relativeLocation: Int): Int {
        val relativeLoc = relativeLocation - relativeChecksumStartLocation
        return relativeLoc / chunkInterval
    }

    fun getChecksumStartLocation(checksumIndex: Int): Int {
        val locationRelativeToStartOfSprites = checksumIndex * chunkInterval
        return locationRelativeToStartOfSprites + relativeChecksumStartLocation
    }

    fun nextChecksumStart(relativeLocation: Int): Int {
        if (beforeStart(relativeLocation)) {
            return relativeChecksumStartLocation
        } else if (afterEnd(relativeLocation)) {
            return Int.MAX_VALUE
        }
        val relativeLoc = relativeLocation - relativeChecksumStartLocation
        val chunkOfNextStart = (relativeLoc / chunkInterval) + 1
        return (chunkOfNextStart * chunkInterval) + relativeChecksumStartLocation
    }

    fun beforeStart(relativeLocation: Int): Boolean =
        relativeLocation < relativeChecksumStartLocation

    fun afterEnd(relativeLocation: Int): Boolean =
        relativeLocation > (numberOfChunks - 1) * chunkInterval +
            relativeChecksumStartLocation + checksumChunkSize

    fun nextChecksumEnd(relativeLocation: Int): Int {
        if (beforeStart(relativeLocation)) {
            return relativeChecksumStartLocation + checksumChunkSize
        } else if (afterEnd(relativeLocation)) {
            return Int.MAX_VALUE
        }
        val locationRelativeToChecksumStart = relativeLocation - relativeChecksumStartLocation
        val locInChunk = locationRelativeToChecksumStart % chunkInterval
        var currentLocationChunk = locationRelativeToChecksumStart / chunkInterval
        if (locInChunk >= checksumChunkSize) {
            currentLocationChunk++
        }
        return currentLocationChunk * chunkInterval + checksumChunkSize +
            relativeChecksumStartLocation
    }

    fun isPartOfChecksum(relativeLocation: Int): Boolean {
        val locationRelativeToChecksumStart = relativeLocation - relativeChecksumStartLocation
        val locInChunk = locationRelativeToChecksumStart % chunkInterval
        return isAfterChecksumStart(relativeLocation) &&
            locInChunk < checksumChunkSize &&
            locationRelativeToChecksumStart / chunkInterval < numberOfChunks
    }

    fun calculateChecksumOffset(expected: Int, current: Int): Int {
        if (expected == current) {
            return 0
        } else if (expected > current) {
            return expected - current
        }
        return expected + WORD_SPACE - current
    }

    fun getChecksumForLocationFromList(relativeLocation: Int, checksums: List<Int>): Int {
        var checksumPortionIndex = calculateWhichChunk(relativeLocation)
        if (checksumPortionIndex < 0) {
            checksumPortionIndex = 0
        }
        return checksums[checksumPortionIndex]
    }

    companion object {
        const val WORD_SPACE = 0x10000

        @JvmStatic
        fun buildForDIM(): SpriteChecksumAreasCalculator {
            val numberOfChunks = 28
            val chunkInterval = 0x10000
            val relativeChecksumStartLocation = 0x2000
            val checksumChunkSize = 0x1000
            return SpriteChecksumAreasCalculator(
                numberOfChunks,
                chunkInterval,
                relativeChecksumStartLocation,
                checksumChunkSize
            )
        }

        @JvmStatic
        fun buildForBEM(): SpriteChecksumAreasCalculator {
            val numberOfChunks = 0x30
            val chunkInterval = 0x10000
            val relativeChecksumStartLocation = 0x2000
            val checksumChunkSize = 0x1000
            return SpriteChecksumAreasCalculator(
                numberOfChunks,
                chunkInterval,
                relativeChecksumStartLocation,
                checksumChunkSize
            )
        }
    }
}

