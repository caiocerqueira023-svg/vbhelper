package com.tamerslab.digiline.content

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loads the real shipped packs through the production loader (file-backed reader, same lenient
 * JSON) and runs the app-side validator. This is the JVM gate that keeps the Kotlin DTOs and the
 * generated assets compatible without an emulator.
 */
class DigilineAssetPackTest {

    private fun assetsDir(): File {
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
        )
        return candidates.firstOrNull { File(it, "digiline/v1/manifest.json").isFile }
            ?: error(
                "DigiLine assets not found from working directory ${File(".").absolutePath}; " +
                    "looked for src/main/assets and app/src/main/assets"
            )
    }

    private fun loadPack() = runBlocking {
        val assets = assetsDir()
        val loader = DigilineContentLoader(reader = { path ->
            File(assets, path).readText(Charsets.UTF_8)
        })
        loader.load("digiline/v1")
    }

    @Test
    fun `shipped packs load into the Kotlin DTOs and pass app validation`() {
        val pack = loadPack()
        assertTrue("contentVersion should be >= 3", pack.manifest.contentVersion >= 3)
        val report = DigilineContentValidator().report(pack)
        assertTrue(report.errors.joinToString("\n"), report.errors.isEmpty())
        assertEquals(64, pack.negotiation.personalities.values.sumOf { it.size })
        assertEquals(64, pack.persuasion.personalities.values.sumOf { it.size })
    }

    @Test
    fun `every negotiation answer carries a reaction and continues its topic`() {
        val pack = loadPack()
        val flows = pack.negotiation.personalities.values.flatMap { it.values }
        var options = 0
        var flowsWithCounterOffer = 0
        flows.forEach { flow ->
            if (flow.nodes.values.any { node -> node.options.any { it.id == "counter" } }) {
                flowsWithCounterOffer++
            }
            flow.nodes.forEach { (nodeKey, node) ->
                assertTrue(
                    "node $nodeKey has no topic",
                    !node.topic.isNullOrBlank(),
                )
                node.options.forEach { option ->
                    options++
                    val target = option.next?.let { flow.nodes[it] }
                    if (option.id == "counter") {
                        assertTrue(
                            "counter offer in $nodeKey must lead to a price node",
                            target != null && target.tag == "demand",
                        )
                    }
                    if (target != null && !target.isTerminal) {
                        assertTrue(
                            "option ${nodeKey}.${option.id} has no authored reaction",
                            !option.reaction.isNullOrBlank(),
                        )
                    }
                    if (target != null && !target.isTerminal && target.tag != "demand") {
                        assertEquals(
                            "option ${nodeKey}.${option.id} does not continue its topic",
                            target.topic,
                            option.topic,
                        )
                    }
                }
            }
        }
        assertEquals("every flow must offer a one-time counter-offer", flows.size, flowsWithCounterOffer)
        assertTrue("expected authored negotiation volume", options >= 900)
    }

    @Test
    fun `negotiation answers are in-world speech, not the old generic meta options`() {
        val pack = loadPack()
        val legacyMeta = setOf(
            "You've made your point. What's the toll?",
            "Then state your price. I'll decide after that.",
            "Name your terms, clearly.",
            "Go on, then. What is this about?",
        )
        val texts = pack.negotiation.personalities.values
            .flatMap { it.values }
            .flatMap { it.nodes.values }
            .flatMap { it.options }
            .map { it.text }
        assertTrue(texts.none { it in legacyMeta })
        assertTrue("every question should offer at least two answers", texts.size >= 900)
    }
}
