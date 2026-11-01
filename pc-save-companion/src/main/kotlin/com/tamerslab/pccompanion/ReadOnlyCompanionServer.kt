package com.tamerslab.pccompanion

import com.tamerslab.companionprotocol.*
import com.tamerslab.gamesave.SaveDiscoveryPolicy
import com.tamerslab.gamesave.SaveReaderRegistry
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Only explicitly configured roots are exposed. No write endpoint or game-save mutation exists. */
class ReadOnlyCompanionServer(private val identity: CompanionIdentity, directories: List<Path>, port: Int = 7428,
    bindAddress: String = "0.0.0.0") : AutoCloseable {
    private val roots = directories.map { it.toRealPath() }.distinct().also { require(it.isNotEmpty() && it.all { root -> Files.isDirectory(root) }) }
    private val executor = ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(32))
    private val server = HttpsServer.create(InetSocketAddress(bindAddress, port), 16)
    val port: Int get() = server.address.port

    init {
        server.httpsConfigurator = object : HttpsConfigurator(identity.ssl) {
            override fun configure(parameters: HttpsParameters) { parameters.setSSLParameters(identity.ssl.defaultSSLParameters) }
        }
        server.executor = executor
        server.createContext("/") { exchange ->
            try { handle(exchange) }
            catch (_: Exception) { runCatching { respond(exchange, 503, "Source unavailable".toByteArray()) } }
            finally { exchange.close() }
        }
    }

    fun start() = server.start()
    override fun close() { server.stop(0); executor.shutdownNow() }

    private data class Found(val id: String, val path: Path, val root: Path, val size: Long)
    private fun discover(): Pair<List<Found>, Boolean> {
        val found = mutableListOf<Found>()
        var visited = 0
        var truncated = false
        for (root in roots) {
            Files.walkFileTree(root, emptySet(), 5, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (++visited > 2000 || found.size >= 32) { truncated = true; return FileVisitResult.TERMINATE }
                    return if (dir != root && dir.fileName.toString().equals("backups", true)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (++visited > 2000 || found.size >= 32) { truncated = true; return FileVisitResult.TERMINATE }
                    if (attrs.isRegularFile && SaveDiscoveryPolicy.isCandidate(file.fileName.toString(), attrs.size())) {
                        val real = file.toRealPath()
                        if (real.startsWith(root) && !Files.isSymbolicLink(file)) {
                            val id = CompanionProtocol.sha256("${identity.serverId}\u0000$root\u0000${root.relativize(real)}".toByteArray(Charsets.UTF_8))
                            found += Found(id, real, root, attrs.size())
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            if (truncated) break
        }
        return found to truncated
    }

    private fun handle(exchange: HttpExchange) {
        if (!CompanionProtocol.local(exchange.remoteAddress.address)) { respond(exchange, 403, "Local network only".toByteArray()); return }
        val auth = exchange.requestHeaders.getFirst("Authorization").orEmpty().toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(auth, "Bearer ${identity.token}".toByteArray(Charsets.UTF_8))) {
            respond(exchange, 401, "Pairing required".toByteArray()); return
        }
        if (exchange.requestMethod != "GET") { respond(exchange, 405, "Read only".toByteArray()); return }
        if (exchange.requestURI.rawQuery != null) { respond(exchange, 400, "Invalid request".toByteArray()); return }
        val path = exchange.requestURI.path
        val (sources, truncated) = discover()
        if (path == "/v1/sources") {
            val listing = CompanionListing(CompanionProtocol.VERSION, identity.serverId,
                sources.map { RemoteSave(it.id, it.path.fileName.toString().take(160), it.size) }, truncated)
            respond(exchange, 200, Gson().toJson(listing).toByteArray(Charsets.UTF_8), "application/json")
        } else if (path.matches(Regex("/v1/saves/[0-9a-f]{64}"))) {
            val source = sources.singleOrNull { it.id == path.substringAfterLast('/') }
            if (source == null) { respond(exchange, 404, "Source unavailable".toByteArray()); return }
            val real = source.path.toRealPath()
            require(real.startsWith(source.root) && !Files.isSymbolicLink(source.path))
            val before = Files.readAttributes(real, BasicFileAttributes::class.java)
            val bytes = Files.newInputStream(real, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
                CompanionProtocol.bounded(it, SaveReaderRegistry().maxInputBytes)
            }
            try {
                val after = Files.readAttributes(real, BasicFileAttributes::class.java)
                if (before.size() != after.size() || before.lastModifiedTime() != after.lastModifiedTime() || before.fileKey() != after.fileKey()) {
                    respond(exchange, 409, "Save changed during read".toByteArray()); return
                }
                respond(exchange, 200, bytes, "application/octet-stream")
            } finally { bytes.fill(0) }
        } else respond(exchange, 404, "Unknown endpoint".toByteArray())
    }

    private fun respond(exchange: HttpExchange, status: Int, bytes: ByteArray, type: String = "text/plain") {
        exchange.responseHeaders.set("Content-Type", type)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
