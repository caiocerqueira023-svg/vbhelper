package com.github.nacabaro.vbhelper.source.gameSave

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.github.nacabaro.vbhelper.companionprotocol.*
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.CompanionConnection
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.gamesave.SaveReaderRegistry
import com.github.nacabaro.vbhelper.gamesave.CapturedSave
import kotlinx.coroutines.*
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/** Android Keystore protects pairing tokens; the database stores only authenticated ciphertext. */
class CompanionTokenCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vbhelper_pc_pairing_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vbhelper_pc_pairing_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun encrypt(id: String, secret: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8)))
    }
    @Synchronized fun decrypt(id: String, value: String): String {
        try {
            val bytes = Base64.getDecoder().decode(value)
            require(bytes.size in 29..1024)
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(id.toByteArray())
                doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
            }
        } catch (_: Exception) { throw SecurityException("Pair this companion again") }
    }
}

class LocalCompanionClient(private val db: AppDatabase, private val tokens: CompanionTokenCipher = CompanionTokenCipher()) : SaveDocumentAccess {
    private val dao get() = db.transferContinuityDao()

    suspend fun pair(link: String, label: String): CompanionConnection = withContext(Dispatchers.IO) {
        val pairing = CompanionPairing.parse(link)
        val clean = label.trim()
        require(clean.isNotEmpty() && clean.length <= 160 && clean.none(Char::isISOControl))
        listing(pairing) // Pair only after validating the actual pinned/authenticated peer.
        val previous = dao.byServer(pairing.serverId)
        val id = previous?.connectionId ?: UUID.randomUUID().toString()
        CompanionConnection(id, pairing.serverId, clean, pairing.host, pairing.port, pairing.pin,
            tokens.encrypt(id, pairing.token), System.currentTimeMillis()).also { dao.saveConnection(it) }
    }

    private fun pairing(connection: CompanionConnection) = CompanionPairing(connection.serverId, connection.host,
        connection.port, connection.certificatePin, tokens.decrypt(connection.connectionId, connection.encryptedToken))

    private suspend fun listing(pairing: CompanionPairing): CompanionListing = request(pairing, "/v1/sources") { connection ->
        connection.inputStream.use { CompanionProtocol.listing(CompanionProtocol.bounded(it, CompanionProtocol.MAX_LISTING_BYTES), pairing) }
    }

    suspend fun discover(id: String): SaveFolderResult = withContext(Dispatchers.IO) {
        val connection = dao.connection(id) ?: throw SecurityException("Companion was forgotten")
        val found = listing(pairing(connection))
        SaveFolderResult(found.saves.map { source -> SaveDocument("pc:${connection.serverId}:${source.id}",
            "companion://$id/${source.id}", "companion://$id", source.name) }, found.truncated)
    }

    override suspend fun read(uri: String, registry: SaveReaderRegistry): SaveObservation = withContext(Dispatchers.IO) {
        capture(uri, registry).use { it.observation }
    }

    override suspend fun capture(uri: String, registry: SaveReaderRegistry): CapturedSave = withContext(Dispatchers.IO) {
        val locator = Uri.parse(uri)
        require(locator.scheme == "companion" && locator.pathSegments.size == 1 && CompanionProtocol.SOURCE_ID.matches(locator.pathSegments.single()))
        val connection = dao.connection(requireNotNull(locator.host)) ?: throw SecurityException("Companion was forgotten")
        val result = request(pairing(connection), "/v1/saves/${locator.pathSegments.single()}") { remote -> remote.inputStream.use(registry::capture) }
        if (dao.connection(connection.connectionId)?.encryptedToken != connection.encryptedToken) {
            result.close(); throw SecurityException("Companion pairing changed during read")
        }
        result
    }

    private suspend fun <T> request(pairing: CompanionPairing, path: String, consume: (HttpsURLConnection) -> T): T = withTimeout(30_000) {
        withContext(Dispatchers.IO) { coroutineScope {
            val connection = CompanionProtocol.connection(pairing, path)
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { connection.disconnect() }
            }
            try {
                when (connection.responseCode) {
                    200 -> consume(connection)
                    401, 403 -> throw SecurityException("Companion pairing or local access required")
                    else -> throw IOException("Companion source unavailable")
                }
            } finally { connection.disconnect(); watcher.cancelAndJoin() }
        } }
    }
}

class LinkedSaveDocuments(private val local: AndroidSaveDocuments, private val pc: LocalCompanionClient) : SaveDocumentAccess {
    override suspend fun read(uri: String, registry: SaveReaderRegistry): SaveObservation =
        if (uri.startsWith("companion://")) pc.read(uri, registry) else local.read(uri, registry)
    override suspend fun capture(uri: String, registry: SaveReaderRegistry): CapturedSave =
        if (uri.startsWith("companion://")) pc.capture(uri, registry) else local.capture(uri, registry)
}
