package com.tamerslab.pccompanion

import com.tamerslab.companionprotocol.CompanionProtocol
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class CompanionIdentity private constructor(val serverId: String, val token: String, val pin: String, val ssl: SSLContext) {
    override fun toString() = "CompanionIdentity($serverId, token=<redacted>)"
    private data class State(val serverId: String, val token: String, val password: String)
    companion object {
        private fun secret() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun load(directory: Path, rotateToken: Boolean = false): CompanionIdentity {
            Files.createDirectories(directory)
            val metadata = directory.resolve("identity.json")
            val keystore = directory.resolve("identity.p12")
            require(Files.exists(metadata) == Files.exists(keystore)) { "Incomplete companion identity; restore the state directory" }
            val gson = Gson()
            var state = if (Files.exists(metadata)) {
                Files.newInputStream(metadata).use { gson.fromJson(CompanionProtocol.bounded(it, 8192).toString(Charsets.UTF_8), State::class.java) }
            } else {
                val created = State(UUID.randomUUID().toString(), secret(), secret())
                val executable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool")
                val process = ProcessBuilder(executable.toString(), "-genkeypair", "-alias", "companion", "-keyalg", "EC", "-keysize", "256",
                    "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass:env", "VBHELPER_TLS_PASSWORD", "-keypass:env", "VBHELPER_TLS_PASSWORD",
                    "-dname", "CN=Tamers Lab-${created.serverId}", "-validity", "3650", "-ext", "EKU=serverAuth", "-noprompt")
                    .redirectErrorStream(true).apply { environment()["VBHELPER_TLS_PASSWORD"] = created.password }.start()
                if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("TLS initialization timed out") }
                process.inputStream.close()
                check(process.exitValue() == 0) { "TLS initialization failed" }
                Files.write(metadata, gson.toJson(created).toByteArray(Charsets.UTF_8))
                created
            }
            if (rotateToken) {
                state = state.copy(token = secret())
                Files.write(metadata, gson.toJson(state).toByteArray(Charsets.UTF_8))
            }
            require(state.token.matches(Regex("[A-Za-z0-9_-]{43}")))
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(keystore).use { store.load(it, state.password.toCharArray()) }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, state.password.toCharArray()) }
            val ssl = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, SecureRandom()) }
            return CompanionIdentity(state.serverId, state.token, CompanionProtocol.sha256(store.getCertificate("companion").encoded), ssl)
        }
    }
}
