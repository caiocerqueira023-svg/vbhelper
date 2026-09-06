package com.github.nacabaro.vbhelper.domain.identity

import java.security.SecureRandom

/** Codec for the 12-byte NFC appReserved1 field: magic byte + 11 random bytes. */
object IndividualIdentity {
    private const val MAGIC = 0xB6
    private const val ID_SIZE = 11
    private val random = SecureRandom()

    fun decode(appReserved1: ByteArray): String? {
        if (appReserved1.size != ID_SIZE + 1 || (appReserved1[0].toInt() and 0xFF) != MAGIC) return null
        return appReserved1.drop(1).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    fun encode(individualId: String): ByteArray {
        require(individualId.length == ID_SIZE * 2 && individualId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Individual ID must be $ID_SIZE bytes encoded as hexadecimal"
        }
        return ByteArray(ID_SIZE + 1).also { bytes ->
            bytes[0] = MAGIC.toByte()
            repeat(ID_SIZE) { index ->
                bytes[index + 1] = individualId.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }

    fun generate(): String = ByteArray(ID_SIZE).also(random::nextBytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
