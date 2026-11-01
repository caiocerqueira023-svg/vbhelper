package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetOutputStream
import com.tamerslab.hardware.dim.util.ByteUtils
import com.tamerslab.hardware.dim.util.InMemoryOutputStream
import com.tamerslab.hardware.dim.util.RawChecksumBuilder
import com.tamerslab.hardware.dim.util.RelativeByteOffsetOutputStream
import java.io.IOException
import java.nio.charset.Charset

class UnorderedSpriteChecksumHacker(
    private val spriteChecksumAreasCalculator: SpriteChecksumAreasCalculator,
    private val pixelPointerTableStart: Int,
    private val rawChecksumBuilder: RawChecksumBuilder
) {
    class PixelDataWithPointers(
        val pixelData: ByteArray,
        val pointerTable: IntArray
    )

    @Throws(IOException::class)
    fun writeSpritesUnorderedToCorrectChecksums(
        spriteData: SpriteData,
        mainOutputStream: ByteOffsetOutputStream
    ) {
        val spritePackageOutputStream = RelativeByteOffsetOutputStream(mainOutputStream)
        val pixelDataWithPointers = createPixelDataAndPointersSection(spriteData)
        writeMetaToOutput(
            spriteData,
            pixelDataWithPointers.pointerTable,
            spritePackageOutputStream
        )
        writePointerTableToOutput(
            pixelDataWithPointers.pointerTable,
            spritePackageOutputStream
        )
        spritePackageOutputStream.writeBytes(pixelDataWithPointers.pixelData)
    }

    @Throws(IOException::class)
    private fun createPixelDataAndPointersSection(spriteData: SpriteData): PixelDataWithPointers {
        val spriteFinder = SpriteFinder.createSpriteFinder(spriteData.sprites)
        var currentSpot = calculateAddressImmediatelyAfterTable(spriteData)
        val pointerTable = IntArray(spriteData.sprites.size + 1)
        val checksumArea = ByteArray(spriteChecksumAreasCalculator.checksumChunkSize)
        val inMemoryOutputStream = InMemoryOutputStream(currentSpot)
        var checksumOffsetLocation: Int? = null
        var checkSumAreaLocation = 0
        while (!spriteFinder.isEmpty()) {
            if (!spriteChecksumAreasCalculator.isPartOfChecksum(currentSpot)) {
                val nextStart = spriteChecksumAreasCalculator.nextChecksumStart(currentSpot)
                val nextEnd = spriteChecksumAreasCalculator.nextChecksumEnd(currentSpot)
                val size = (nextEnd - 2) - currentSpot
                val spriteWithIndex = spriteFinder.getNextSmallestSize(size)
                if (spriteWithIndex == null) {
                    inMemoryOutputStream.writeZerosUntilOffset(nextStart)
                    currentSpot = nextStart
                } else {
                    val spriteSize = spriteWithIndex.size
                    if (spriteSize + currentSpot <= nextStart) {
                        inMemoryOutputStream.writeBytes(spriteWithIndex.pixelData)
                        pointerTable[spriteWithIndex.index] = currentSpot
                        currentSpot += spriteWithIndex.size
                    } else {
                        val bytesBeforeChecksumStart = nextStart - currentSpot
                        inMemoryOutputStream.writeBytes(
                            spriteWithIndex.pixelData.copyOfRange(
                                0,
                                bytesBeforeChecksumStart
                            )
                        )
                        checkSumAreaLocation = writeToArray(
                            spriteWithIndex.pixelData,
                            bytesBeforeChecksumStart,
                            checksumArea,
                            checkSumAreaLocation
                        )
                        pointerTable[spriteWithIndex.index] = currentSpot
                        currentSpot += spriteWithIndex.size
                    }
                }
            } else {
                val nextEnd = spriteChecksumAreasCalculator.nextChecksumEnd(currentSpot)
                val spriteWithIndex = spriteFinder.getNextLargestSprite()
                if (checksumOffsetLocation == null) {
                    checksumOffsetLocation = checkSumAreaLocation
                    currentSpot += 2
                    checkSumAreaLocation += 2
                }
                val spriteSize = spriteWithIndex.size
                if (currentSpot + spriteSize < nextEnd) {
                    checkSumAreaLocation += writeToArray(
                        spriteWithIndex.pixelData,
                        0,
                        checksumArea,
                        checkSumAreaLocation
                    )
                    pointerTable[spriteWithIndex.index] = currentSpot
                    currentSpot += spriteSize
                } else {
                    val bytesWritten = writeToArray(
                        spriteWithIndex.pixelData,
                        0,
                        checksumArea,
                        checkSumAreaLocation
                    )
                    pointerTable[spriteWithIndex.index] = currentSpot
                    val offset = calculateChecksumOffset(
                        currentSpot,
                        checksumArea,
                        spriteData.spriteChecksums!!
                    )
                    writeOffset(offset, checksumOffsetLocation, checksumArea)
                    inMemoryOutputStream.writeBytes(checksumArea)
                    inMemoryOutputStream.writeBytes(
                        spriteWithIndex.pixelData.copyOfRange(bytesWritten, spriteSize)
                    )
                    currentSpot += spriteSize
                    checksumOffsetLocation = null
                    checkSumAreaLocation = 0
                    clearArray(checksumArea)
                }
            }
        }
        if (checkSumAreaLocation != 0) {
            if (checksumOffsetLocation == null) {
                checksumOffsetLocation = currentSpot
                currentSpot += 2
            }
            val offset = calculateChecksumOffset(
                currentSpot,
                checksumArea,
                spriteData.spriteChecksums!!
            )
            writeOffset(offset, checksumOffsetLocation, checksumArea)
            inMemoryOutputStream.writeBytes(checksumArea)
        }
        if (spriteChecksumAreasCalculator.isPartOfChecksum(currentSpot)) {
            currentSpot = spriteChecksumAreasCalculator.nextChecksumEnd(currentSpot)
            inMemoryOutputStream.writeZerosUntilOffset(currentSpot)
        }
        currentSpot = fillRemainingChecksums(inMemoryOutputStream, currentSpot, spriteData)
        pointerTable[pointerTable.size - 1] = currentSpot
        inMemoryOutputStream.writeBytes(TERMINATION_SPRITE.pixelData)
        return PixelDataWithPointers(inMemoryOutputStream.getBytes(), pointerTable)
    }

    @Throws(IOException::class)
    private fun fillRemainingChecksums(
        inMemoryOutputStream: InMemoryOutputStream,
        currentSpot: Int,
        spriteData: SpriteData
    ): Int {
        val originalChecksums = spriteData.spriteChecksums!!
        val lastChecksumStartLocation = spriteChecksumAreasCalculator.getChecksumStartLocation(
            originalChecksums.size - 1
        )
        var nextChecksumStart =
            spriteChecksumAreasCalculator.nextChecksumStart(currentSpot)
        var lastChecksumEnd = currentSpot
        while (nextChecksumStart <= lastChecksumStartLocation &&
            nextChecksumStart < Int.MAX_VALUE
        ) {
            inMemoryOutputStream.writeZerosUntilOffset(nextChecksumStart)
            val checksum = spriteChecksumAreasCalculator.getChecksumForLocationFromList(
                nextChecksumStart,
                originalChecksums
            )
            inMemoryOutputStream.write16BitInt(checksum)
            lastChecksumEnd =
                spriteChecksumAreasCalculator.nextChecksumEnd(nextChecksumStart)
            inMemoryOutputStream.writeZerosUntilOffset(lastChecksumEnd)
            nextChecksumStart =
                spriteChecksumAreasCalculator.nextChecksumStart(lastChecksumEnd)
        }
        return lastChecksumEnd
    }

    private fun calculateAddressImmediatelyAfterTable(spriteData: SpriteData): Int =
        pixelPointerTableStart + 4 * spriteData.sprites.size + 4

    private fun clearArray(data: ByteArray) {
        data.fill(0)
    }

    private fun writeOffset(offset: Int, offsetLocation: Int, checksumArea: ByteArray) {
        val srcBytes = ByteUtils.convert16BitIntToBytes(offset)
        writeToArray(srcBytes, 0, checksumArea, offsetLocation)
    }

    private fun writeToArray(
        src: ByteArray,
        srcStart: Int,
        dst: ByteArray,
        dstStart: Int
    ): Int {
        val bytesRemainingInSrc = src.size - srcStart
        val bytesRemainingInDst = dst.size - dstStart
        val bytesToCopy = minOf(bytesRemainingInSrc, bytesRemainingInDst)
        if (bytesToCopy >= 0) {
            System.arraycopy(src, srcStart, dst, dstStart, bytesToCopy)
        }
        return bytesToCopy
    }

    private fun calculateChecksumFromBytes(data: ByteArray): Int {
        rawChecksumBuilder.reset()
        rawChecksumBuilder.addBytes(data)
        return rawChecksumBuilder.checksum
    }

    private fun calculateChecksumOffset(
        currentSpot: Int,
        checksumArea: ByteArray,
        originalChecksums: List<Int>
    ): Int {
        val currentChecksum = calculateChecksumFromBytes(checksumArea)
        val expectedChecksum =
            spriteChecksumAreasCalculator.getChecksumForLocationFromList(
                currentSpot,
                originalChecksums
            )
        return spriteChecksumAreasCalculator.calculateChecksumOffset(
            expectedChecksum,
            currentChecksum
        )
    }

    @Throws(IOException::class)
    private fun writeMetaToOutput(
        spriteData: SpriteData,
        pointerTable: IntArray,
        outputStream: ByteOffsetOutputStream
    ) {
        outputStream.writeBytes(spriteData.text.toByteArray(Charset.defaultCharset()))
        outputStream.writeZerosUntilOffset(SpriteWriter.TERMINATION_BYTES_OF_POINTER_TABLE)
        val terminationBytesPointer = pointerTable[pointerTable.size - 1]
        outputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(terminationBytesPointer)
        )
        outputStream.writeZerosUntilOffset(SpriteWriter.TABLE_START)
        outputStream.writeBytes(ByteUtils.convert32BitIntToBytes(1))
        outputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(SpriteWriter.NUMBER_OF_SPRITES_LOCATION)
        )
        outputStream.writeBytes(
            ByteUtils.convert32BitIntToBytes(spriteData.sprites.size)
        )
    }

    @Throws(IOException::class)
    private fun writePointerTableToOutput(
        pointerTable: IntArray,
        outputStream: ByteOffsetOutputStream
    ) {
        for (pointer in pointerTable) {
            outputStream.writeInt(pointer)
        }
    }

    companion object {
        private val TERMINATION_SPRITE: SpriteData.Sprite = SpriteData.Sprite.builder()
            .height(1)
            .width(2)
            .pixelData(ByteUtils.convert32BitIntToBytes(SpriteWriter.TERMINATION_BYTES))
            .build()
    }
}
