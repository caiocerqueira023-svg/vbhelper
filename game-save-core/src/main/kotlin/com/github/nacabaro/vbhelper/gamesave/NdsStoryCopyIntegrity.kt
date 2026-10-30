package com.github.nacabaro.vbhelper.gamesave

import java.security.MessageDigest

/**
 * Per-copy integrity header: the digest is computed over the whole copy with the header range
 * zeroed, and stored at the copy's start. Verified byte-exactly for the World DS profile: a stale
 * header is rejected at boot and the card can be erased afterwards, while resealing an unedited
 * copy reproduces the game's own header. Rules come from [NdsStoryProfile.copyHeader].
 */
object NdsStoryCopyIntegrity {
    fun isValid(bytes: ByteArray, copyBase: Int, profile: NdsStoryProfile): Boolean {
        if (copyBase < 0 || copyBase + profile.saveBytes > bytes.size) return false
        val header = profile.copyHeader.headerBytes
        val copy = bytes.copyOfRange(copyBase, copyBase + profile.saveBytes)
        return try {
            val stored = copy.copyOfRange(0, header)
            copy.fill(0, 0, header)
            val digest = MessageDigest.getInstance(profile.copyHeader.digest).digest(copy)
            digest.size == header && digest.contentEquals(stored)
        } finally { copy.fill(0) }
    }

    /** Writes the recomputed header into [bytes] at [copyBase]; the caller owns this candidate. */
    fun seal(bytes: ByteArray, copyBase: Int, profile: NdsStoryProfile) {
        require(copyBase >= 0 && copyBase + profile.saveBytes <= bytes.size) { "Copy base is outside the container" }
        val header = profile.copyHeader.headerBytes
        val copy = bytes.copyOfRange(copyBase, copyBase + profile.saveBytes)
        try {
            copy.fill(0, 0, header)
            val digest = MessageDigest.getInstance(profile.copyHeader.digest).digest(copy)
            digest.copyInto(bytes, copyBase, 0, minOf(header, digest.size))
        } finally { copy.fill(0) }
    }
}
