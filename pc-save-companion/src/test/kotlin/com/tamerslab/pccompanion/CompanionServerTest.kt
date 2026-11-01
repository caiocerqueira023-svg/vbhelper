package com.tamerslab.pccompanion

import com.tamerslab.companionprotocol.*
import com.tamerslab.gamesave.TimeStrangerSaveReader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CompanionServerTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun rotatingPairingRevokesOldTokensWithoutChangingPeerIdentity() {
        val directory = temporary.newFolder("rotation").toPath()
        val initial = CompanionIdentity.load(directory)
        val rotated = CompanionIdentity.load(directory, rotateToken = true)
        assertEquals(initial.serverId, rotated.serverId)
        assertEquals(initial.pin, rotated.pin)
        assertNotEquals(initial.token, rotated.token)
        assertEquals(rotated.token, CompanionIdentity.load(directory).token)
    }
    @Test fun pinnedTlsDiscoveryAndReadAreAuthenticatedBoundedAndReadOnly() {
        val identity = CompanionIdentity.load(temporary.newFolder("identity").toPath())
        val root = temporary.newFolder("saves").toPath()
        val file = root.resolve("0001.bin")
        val bytes = ByteArray(TimeStrangerSaveReader.FILE_BYTES).also { it[it.lastIndex] = 42 }
        Files.write(file, bytes)
        Files.createDirectories(root.resolve("backups"))
        Files.write(root.resolve("backups/0002.bin"), bytes)
        ReadOnlyCompanionServer(identity, listOf(root), 0, "127.0.0.1").use { server ->
            server.start()
            val pair = CompanionPairing(identity.serverId, "127.0.0.1", server.port, identity.pin, identity.token)
            val listing = CompanionProtocol.connection(pair, "/v1/sources").let { connection ->
                try { CompanionProtocol.listing(connection.inputStream.use { CompanionProtocol.bounded(it, CompanionProtocol.MAX_LISTING_BYTES) }, pair) }
                finally { connection.disconnect() }
            }
            assertEquals(1, listing.saves.size)
            val source = listing.saves.single()
            val read = CompanionProtocol.connection(pair, "/v1/saves/${source.id}")
            try { assertArrayEquals(bytes, read.inputStream.use { CompanionProtocol.bounded(it, bytes.size) }) } finally { read.disconnect() }
            val write = CompanionProtocol.connection(pair, "/v1/saves/${source.id}").apply { requestMethod = "POST" }
            try { assertEquals(405, write.responseCode) } finally { write.disconnect() }
            val unauthorized = CompanionProtocol.connection(CompanionPairing(pair.serverId, pair.host, pair.port, pair.pin, "x".repeat(43)), "/v1/sources")
            try { assertEquals(401, unauthorized.responseCode) } finally { unauthorized.disconnect() }
            val wrongPin = CompanionProtocol.connection(CompanionPairing(pair.serverId, pair.host, pair.port, "0".repeat(64), pair.token), "/v1/sources")
            try { assertThrows(Exception::class.java) { wrongPin.inputStream.close() } } finally { wrongPin.disconnect() }
        }
        assertArrayEquals(bytes, Files.readAllBytes(file))
        assertEquals(identity.pin, CompanionIdentity.load(temporary.root.toPath().resolve("identity")).pin)
    }
}
