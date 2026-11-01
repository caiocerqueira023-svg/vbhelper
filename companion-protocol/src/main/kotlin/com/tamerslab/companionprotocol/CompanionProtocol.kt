package com.tamerslab.companionprotocol

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class RemoteSave(val id: String, val name: String, val bytes: Long)
data class CompanionListing(val version: Int, val serverId: String, val saves: List<RemoteSave>, val truncated: Boolean = false)

/** Secret-bearing pairing material intentionally has a redacted string representation. */
class CompanionPairing(val serverId: String, val host: String, val port: Int, val pin: String, val token: String) {
    init {
        require(serverId.matches(Regex("[0-9a-f-]{36}")) && host.isNotBlank() && host.length <= 253 && port in 1..65535)
        require(pin.matches(Regex("[0-9a-f]{64}")) && token.matches(Regex("[A-Za-z0-9_-]{43}")))
    }
    override fun toString() = "CompanionPairing($serverId, token=<redacted>)"
    fun link(): String = "vbhelper-companion://$serverId?host=${URLEncoder.encode(host, "UTF-8")}&port=$port&pin=$pin&token=$token"
    companion object {
        fun parse(link: String): CompanionPairing {
            require(link.length <= 2048)
            val uri = URI(link.trim())
            require(uri.scheme == "vbhelper-companion" && uri.userInfo == null && uri.fragment == null && uri.path.orEmpty().isEmpty())
            val entries = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            require(entries.all { it.size == 2 } && entries.map { it[0] }.toSet() == setOf("host", "port", "pin", "token") && entries.size == 4)
            val query = entries.associate { it[0] to URLDecoder.decode(it[1], "UTF-8") }
            return CompanionPairing(requireNotNull(uri.host), query.getValue("host"), query.getValue("port").toInt(), query.getValue("pin"), query.getValue("token"))
        }
    }
}

object CompanionProtocol {
    const val VERSION = 1
    const val MAX_LISTING_BYTES = 256 * 1024
    val SOURCE_ID = Regex("[0-9a-f]{64}")
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun local(address: InetAddress): Boolean = address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
        (address.address.size == 16 && address.address[0].toInt() and 0xfe == 0xfc)

    fun bounded(input: InputStream, max: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32768)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, max + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val value = input.read()
                if (value < 0) break
                output.write(value)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "Input exceeds protocol limit" }
        }
        return output.toByteArray()
    }

    /** Pin is the peer identity; DNS names/addresses are only locators for a locally paired endpoint. */
    fun connection(pairing: CompanionPairing, path: String): HttpsURLConnection {
        require(path == "/v1/sources" || path.matches(Regex("/v1/saves/[0-9a-f]{64}")))
        val addresses = InetAddress.getAllByName(pairing.host)
        require(addresses.isNotEmpty() && addresses.all(::local)) { "Only local-network endpoints are supported" }
        val address = addresses.first().hostAddress!!.substringBefore('%')
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = error("Client certificates are not used")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                require(chain.isNotEmpty())
                chain[0].checkValidity()
                if (!MessageDigest.isEqual(sha256(chain[0].encoded).toByteArray(), pairing.pin.toByteArray()))
                    throw java.security.cert.CertificateException("Companion identity mismatch")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return URI("https", null, address, pairing.port, path, null, null).toURL().openConnection().let { it as HttpsURLConnection }.apply {
            sslSocketFactory = ssl.socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                runCatching { sha256(session.peerCertificates.first().encoded) == pairing.pin }.getOrDefault(false)
            }
            instanceFollowRedirects = false
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer ${pairing.token}")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    fun listing(bytes: ByteArray, pairing: CompanionPairing): CompanionListing {
        require(bytes.size <= MAX_LISTING_BYTES)
        val result = Gson().fromJson(bytes.toString(Charsets.UTF_8), CompanionListing::class.java)
        require(result.version == VERSION && result.serverId == pairing.serverId && result.saves.size <= 32)
        require(result.saves.map { it.id }.distinct().size == result.saves.size && result.saves.all {
            SOURCE_ID.matches(it.id) && it.name.length in 1..160 && it.name.none(Char::isISOControl) && it.bytes in 1..(64L * 1024 * 1024)
        })
        return result
    }
}
