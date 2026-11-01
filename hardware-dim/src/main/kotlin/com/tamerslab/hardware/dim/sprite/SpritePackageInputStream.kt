package com.tamerslab.hardware.dim.sprite

import com.tamerslab.hardware.dim.util.ByteOffsetInputStream
import java.io.IOException

class SpritePackageInputStream(
    private val generalInputStream: ByteOffsetInputStream,
    private val spriteChecksumBuilder: SpriteChecksumBuilder
) {
    private var location: Int = 0

    @Throws(IOException::class)
    fun readNBytes(n: Int): ByteArray {
        val bytes = generalInputStream.readNBytes(n)
        spriteChecksumBuilder.addBytes(bytes, location)
        location += n
        return bytes
    }

    @Throws(IOException::class)
    fun readToOffset(offset: Int): ByteArray {
        if (offset <= location) {
            return ByteArray(0)
        }
        return readNBytes(offset - location)
    }

    fun getSpriteChecksums(): ArrayList<Int> =
        spriteChecksumBuilder.getChecksums()
}
