package com.github.nacabaro.vbhelper.domain.identity

import com.google.gson.Gson
import java.security.MessageDigest

/** Exact payload comparison for retries; never a species-based identity heuristic. */
object TransferFingerprint {
    private val gson = Gson()
    fun of(value: Any, sourceDevice: String = ""): String {
        val payload = "v1|$sourceDevice|${value.javaClass.name}|${gson.toJson(value)}"
        return bytes(payload.toByteArray(Charsets.UTF_8))
    }

    fun bytes(payload: ByteArray, namespace: String = ""): String =
        MessageDigest.getInstance("SHA-256").digest(namespace.toByteArray(Charsets.UTF_8) + byteArrayOf(0) + payload)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
}
