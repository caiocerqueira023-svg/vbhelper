package com.github.nacabaro.vbhelper.pccompanion

import com.github.nacabaro.vbhelper.companionprotocol.CompanionPairing
import com.github.nacabaro.vbhelper.companionprotocol.CompanionProtocol
import java.net.NetworkInterface
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.Locale
import com.google.gson.Gson
import com.github.nacabaro.vbhelper.gamesave.WorldDsResearchInspector
import java.nio.file.Files

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--inspect-world-ds") {
        require(args.size == 2) { "Usage: pc-save-companion --inspect-world-ds <persisted .sav or .dsv file>" }
        Files.newInputStream(Path.of(args[1])).use { WorldDsResearchInspector.inspect(it) }.summary().forEach(::println)
        return
    }
    val roots = mutableListOf<Path>()
    var state = Path.of(System.getProperty("user.home"), ".vbhelper-companion")
    var port = 7428
    var rotate = false
    var capture: String? = null
    var session = "controlled"
    var label: String? = null
    var expected = ControlledChange.BASELINE
    var comparison: Pair<String,String>? = null
    var index = 0
    fun value(): String { require(index < args.size) { "Missing option value" }; return args[index++] }
    while (index < args.size) {
        when (args[index++]) {
            "--save-dir" -> roots.add(Path.of(value()))
            "--state-dir" -> state = Path.of(value())
            "--port" -> port = value().toInt().also { require(it in 1..65535) }
            "--rotate-pairing" -> rotate = true
            "--capture" -> capture = value()
            "--session" -> session = value()
            "--label" -> label = value()
            "--expected" -> expected = ControlledChange.valueOf(value().uppercase(Locale.ROOT).replace('-', '_'))
            "--compare-captures" -> comparison = value() to value()
            else -> error("Usage: pc-save-companion --save-dir <authorized folder> [--state-dir <private folder>] [--port 7428] [--rotate-pairing]; or --capture <0001.bin> --session <id> --label <label> --expected <action>; or --compare-captures <before-label> <after-label> --session <id>; or --inspect-world-ds <save-file>")
        }
    }
    require(capture == null || comparison == null) { "Choose capture or comparison, not both" }
    val samples = ControlledSamples(state)
    capture?.let { source ->
        val captured = samples.capture(roots, session, requireNotNull(label) { "Capture requires --label" }, source, expected)
        println(Gson().toJson(captured))
        println("Private source copy captured; native identity/ownership remains unverified.")
        return
    }
    comparison?.let { (before, after) ->
        println(Gson().toJson(samples.compare(session, before, after)))
        println("Research evidence only: candidate matches, farm persistence and expected-action flags do not establish native identity or ownership.")
        return
    }
    require(roots.isNotEmpty()) { "Select at least one authorized save folder with --save-dir" }
    val identity = CompanionIdentity.load(state, rotate)
    val server = ReadOnlyCompanionServer(identity, roots, port)
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    server.start()
    println("VBHelper local companion: read-only saved files; no game writes or live-memory access.")
    println("Paste one pairing link into Transfer on the phone. Pairing links grant read access; keep them private.")
    val addresses = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.filter { it.address.size == 4 && CompanionProtocol.local(it) }
    for (address in addresses) println(CompanionPairing(identity.serverId, address.hostAddress, port, identity.pin, identity.token).link())
    if (addresses.isEmpty()) println("No local-network IPv4 address is available. Connect this PC to the phone's local network.")
    CountDownLatch(1).await()
}
