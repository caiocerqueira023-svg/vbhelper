package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.source.proto.Secrets.HmacKeys
import org.junit.Assert
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.nio.ByteOrder
import java.security.InvalidKeyException


fun assertHmacKeysArePopulated(msg: String, hmacKeys: HmacKeys) {
    Assert.assertFalse("$msg hmacKey1 is empty", hmacKeys.hmacKey1.isEmpty())
    Assert.assertFalse("$msg hmacKey2 is empty", hmacKeys.hmacKey2.isEmpty())
}

class DexFileSecretsImporterTest {

    @Test
    fun testThatImportSecretsIsPopulated() {
        val dexFileSecretsImporter = DexFileSecretsImporter()
        val url = getAndAssertClassesDexFile()
        val file = File(url.toURI())
        file.inputStream().use {
            val secrets = dexFileSecretsImporter.importSecrets(it)
            Assert.assertEquals("Cipher size isn't correct", 16, secrets.vbCipherCount)
            Assert.assertEquals("BE Cipher size isn't correct", 16, secrets.beCipherCount)
            Assert.assertFalse("AES Key is empty", secrets.aesKey.isEmpty())
            assertHmacKeysArePopulated("VBDM", secrets.vbdmHmacKeys)
            assertHmacKeysArePopulated("VBC", secrets.vbcHmacKeys)
            assertHmacKeysArePopulated("BE", secrets.beHmacKeys)
        }
    }

    @Test
    fun testThatImportWrongSecretsThrows() {
        val dexFileSecretsImporter = DexFileSecretsImporter()
        val url = getAndAssertClassesDexFile()
        val file = File(url.toURI())
        val content = file.readBytes()
        val badCipher = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15).toByteArray(ByteOrder.BIG_ENDIAN)
        badCipher.copyInto(content, DexFileSecretsImporter.BE_SUBSTITUTION_CIPHER_IDX)
        ByteArrayInputStream(content).use {
            Assert.assertThrows("Secrets are validated", InvalidKeyException::class.java) {
                dexFileSecretsImporter.importSecrets(it)
            }
        }
    }

    private fun getAndAssertClassesDexFile(): URL {
        val url = javaClass.getResource("classes.dex")
        // Optional integration fixture, extracted locally from the proprietary official APK.
        assumeNotNull(url)
        return url!!
    }

    @Test fun rejectsSyntheticKeysThatCannotAuthenticateTheKnownWatch() {
        val content = ByteArray(DexFileSecretsImporter.BE_HMAC_KEY_2_IDX + 24)
        val cipher = IntArray(16) { 15 - it }.toByteArray(ByteOrder.BIG_ENDIAN)
        cipher.copyInto(content, DexFileSecretsImporter.BE_SUBSTITUTION_CIPHER_IDX)
        cipher.copyInto(content, DexFileSecretsImporter.VBDM_SUBSTITUTION_CIPHER_IDX)
        // Public test-only keys from the NFC library fixture, not actual device secrets.
        "8A4PEGIXJS454EFRTX9F5PCT".toByteArray().copyInto(content, DexFileSecretsImporter.AES_KEY_IDX)
        for (offset in listOf(DexFileSecretsImporter.BE_HMAC_KEY_1_IDX,
            DexFileSecretsImporter.VBDM_HMAC_KEY_1_IDX, DexFileSecretsImporter.VBC_HMAC_KEY_1_IDX)) {
            "40nz2LdPI99D+x748XmQmw==".toByteArray().copyInto(content, offset)
        }
        for (offset in listOf(DexFileSecretsImporter.BE_HMAC_KEY_2_IDX,
            DexFileSecretsImporter.VBDM_HMAC_KEY_2_IDX, DexFileSecretsImporter.VBC_HMAC_KEY_2_IDX)) {
            "5Jz9lWtNg28qxqIBoR5kLw==".toByteArray().copyInto(content, offset)
        }
        Assert.assertThrows(InvalidKeyException::class.java) {
            DexFileSecretsImporter().importSecrets(ByteArrayInputStream(content))
        }
    }
}

fun IntArray.toByteArray(byteOrder: ByteOrder = ByteOrder.nativeOrder()): ByteArray {
    val byteArray = ByteArray(this.size*4)
    for(i in this.indices) {
        val byteArrayIndex = i*4
        this[i].toByteArray(byteArray, byteArrayIndex, byteOrder)
    }
    return byteArray
}

fun Int.toByteArray(bytes: ByteArray, dstIndex: Int, byteOrder: ByteOrder = ByteOrder.nativeOrder()) {
    val asUInt = this.toUInt()
    for(i in 0 until 4) {
        if(byteOrder == ByteOrder.LITTLE_ENDIAN) {
            bytes[i+dstIndex] = ((asUInt shr 8*i) and 255u).toByte()
        } else {
            bytes[(3-i) + dstIndex] = ((asUInt shr 8*i) and 255u).toByte()
        }
    }
}
