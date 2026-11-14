package com.tamerslab.security

import com.tamerslab.security.proto.Secrets

/**
 * Connection secrets baked into the build so a fresh install can talk to the watch without the
 * manual "Import secrets" step.
 *
 * Provenance: extracted once, with the same fixed offsets and validation as
 * [DexFileSecretsImporter], from `classes.dex` of the official VB Arena 2.1.0 base APK
 * (`com.bandai.vitalbraceletarena.apk`, build from the APKPure XAPK published 2026-09-19).
 *
 * The keys are Bandai's; they are embedded here deliberately instead of being re-imported by
 * the user. [BundledSecretsTest] pins them by regenerating the known NFC test passwords for
 * the VBDM, VBC and BE watch families, so a truncated or replaced value fails the build's
 * unit tests instead of shipping silently.
 *
 * Manual import stays available in Settings and still wins over these values, which is what
 * you need when a future Arena release rotates the keys.
 */
object BundledSecrets {

    private const val AES_KEY = "E2C56DB5DFFB48D2B060D0F5"

    private val VB_CIPHER = intArrayOf(9, 14, 4, 8, 0, 10, 15, 6, 7, 13, 1, 12, 5, 11, 3, 2)
    private val BE_CIPHER = intArrayOf(5, 2, 4, 6, 3, 15, 0, 14, 1, 12, 13, 7, 11, 8, 10, 9)

    private const val VBDM_HMAC_KEY_1 = "cX+3kmjEVevKSARuiLqkuQ=="
    private const val VBDM_HMAC_KEY_2 = "32tJnlfeP3ETQM4KdE/X6w=="
    private const val VBC_HMAC_KEY_1 = "0k1bpdBrjizR3Xrs1fNmtw=="
    private const val VBC_HMAC_KEY_2 = "W7JeDXv1C7S9h1XggnsNBQ=="
    private const val BE_HMAC_KEY_1 = "n98u7qRmbCHK4YyEcOdedg=="
    private const val BE_HMAC_KEY_2 = "ouurqHkOIuHGvMPoBWV+8g=="

    val value: Secrets by lazy { build() }

    private fun build(): Secrets = Secrets.newBuilder()
        .setAesKey(AES_KEY)
        .addAllVbCipher(VB_CIPHER.toList())
        .addAllBeCipher(BE_CIPHER.toList())
        .setVbdmHmacKeys(hmacKeys(VBDM_HMAC_KEY_1, VBDM_HMAC_KEY_2))
        .setVbcHmacKeys(hmacKeys(VBC_HMAC_KEY_1, VBC_HMAC_KEY_2))
        .setBeHmacKeys(hmacKeys(BE_HMAC_KEY_1, BE_HMAC_KEY_2))
        .build()

    private fun hmacKeys(key1: String, key2: String): Secrets.HmacKeys = Secrets.HmacKeys.newBuilder()
        .setHmacKey1(key1)
        .setHmacKey2(key2)
        .build()
}
