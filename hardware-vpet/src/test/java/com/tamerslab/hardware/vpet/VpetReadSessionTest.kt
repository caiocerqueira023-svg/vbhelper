package com.tamerslab.hardware.vpet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session state machine behaviour, exercised with a fake scheduler and fake link. */
class VpetReadSessionTest {

    private class Harness(model: VpetModel, initialVersion: Int) {
        val scheduler = FakeVpetScheduler()
        val sent = ArrayList<String>()
        val listener = RecordingVpetListener()
        val session: VpetReadSession = VpetReadSession(
            model = model,
            initialVersion = initialVersion,
            send = { command -> sent.add(command) },
            scheduler = scheduler,
            listener = listener,
        ).also { session -> session.timeSource = { scheduler.nowMillis } }
    }

    private fun deliver(harness: Harness, lines: List<String>) {
        for (line in lines) {
            if (harness.listener.results.isNotEmpty() || harness.listener.failures.isNotEmpty()) return
            harness.session.receiveLine(line)
        }
    }

    private fun sentCommands(harness: Harness): List<String> = harness.sent.filter { it != VpetDigiRom.STOP_COMMAND }

    @Test
    fun dm20SuccessDeliversResultAndSendsStopCommand() {
        val harness = Harness(VpetModel.DM20, 3)
        val responder = VpetSimulator.responsesFor(VpetModel.DM20, 3, 4)

        harness.session.start()
        assertEquals(listOf(VpetDigiRom.commandFor(VpetModel.DM20, 3)), harness.sent.toList())
        deliver(harness, responder(harness.sent.last()))

        assertEquals(1, harness.listener.results.size)
        val result = harness.listener.results.single()
        assertEquals(VpetModel.DM20, result.model)
        assertEquals(3, result.version)
        assertEquals(4, result.index)
        assertEquals("Greymon", result.name)
        assertEquals(2, result.stage)
        assertEquals(0, result.attribute)
        assertEquals(100, result.power)
        assertTrue(result.checksumValid)
        assertTrue(result.payloadHex.isNotBlank())
        assertEquals(3, harness.session.learnedVersion())
        assertEquals(VpetDigiRom.STOP_COMMAND, harness.sent.last())
        assertTrue(harness.listener.progress.contains(95))
        assertTrue(harness.listener.failures.isEmpty())
    }

    @Test
    fun wrongVersionIsLearnedAndRequestedAgain() {
        val harness = Harness(VpetModel.DM20, 0)
        val responder = VpetSimulator.responsesFor(VpetModel.DM20, 3, 4)

        harness.session.start()
        assertEquals(VpetDigiRom.commandFor(VpetModel.DM20, 0), harness.sent.last())
        deliver(harness, responder(harness.sent.last()))

        assertTrue("the read must not finish while the VPet hides its species", harness.listener.results.isEmpty())
        assertTrue(harness.listener.failures.isEmpty())
        assertEquals(VpetDigiRom.commandFor(VpetModel.DM20, 3), harness.sent.last())
        assertTrue(harness.listener.progress.contains(VpetReadSession.VERSION_LEARN_PROGRESS))
        assertTrue(harness.listener.statuses.any { it.contains("version 3") })

        deliver(harness, responder(harness.sent.last()))

        assertEquals(1, harness.listener.results.size)
        val result = harness.listener.results.single()
        assertEquals(3, result.version)
        assertEquals(4, result.index)
        assertEquals("Greymon", result.name)
        assertEquals(3, harness.session.learnedVersion())
        assertEquals(
            listOf(
                VpetDigiRom.commandFor(VpetModel.DM20, 0),
                VpetDigiRom.commandFor(VpetModel.DM20, 3),
                VpetDigiRom.STOP_COMMAND,
            ),
            harness.sent,
        )
    }

    @Test
    fun learnedVersionWithRevealedIndexFinishesWithoutAReRequest() {
        val harness = Harness(VpetModel.DM20, 0)
        harness.session.start()
        // The VPet answers with its real version and reveals the species number.
        val answered = VpetSimulator.responsesFor(VpetModel.DM20, 3, 4)(VpetDigiRom.commandFor(VpetModel.DM20, 3))
        deliver(harness, answered)

        assertEquals(1, harness.listener.results.size)
        val result = harness.listener.results.single()
        assertEquals(3, result.version)
        assertEquals(4, result.index)
        assertEquals(3, harness.session.learnedVersion())
        assertEquals(
            listOf(VpetDigiRom.commandFor(VpetModel.DM20, 0), VpetDigiRom.STOP_COMMAND),
            harness.sent,
        )
    }

    @Test
    fun noResponseFailsAfterEightAttempts() {
        val harness = Harness(VpetModel.DM20, 0)
        harness.session.start()

        repeat(7) { harness.scheduler.advanceBy(VpetReadSession.REQUEST_TIMEOUT_MS) }
        assertEquals(8, sentCommands(harness).size)
        assertTrue(harness.listener.failures.isEmpty())
        assertTrue(harness.listener.results.isEmpty())

        harness.scheduler.advanceBy(VpetReadSession.REQUEST_TIMEOUT_MS)

        assertEquals(1, harness.listener.failures.size)
        val failure = harness.listener.failures.single()
        assertTrue(failure is VpetReadFailure.NoResponse)
        assertEquals(8, (failure as VpetReadFailure.NoResponse).attempts)
        assertEquals(VpetDigiRom.STOP_COMMAND, harness.sent.last())
        assertTrue(harness.listener.results.isEmpty())
    }

    @Test
    fun packetTimeoutRearmsTo15Seconds() {
        val harness = Harness(VpetModel.DM20, 0)
        harness.session.start()
        assertEquals(1, sentCommands(harness).size)

        harness.session.receiveLine("r:0301")
        assertEquals(10, harness.listener.progress.last())

        harness.scheduler.advanceBy(VpetReadSession.PACKET_TIMEOUT_MS - 1)
        assertEquals(1, sentCommands(harness).size)

        harness.scheduler.advanceBy(1)
        assertEquals(2, sentCommands(harness).size)
        assertTrue(harness.listener.statuses.any { it.contains("does not look like") })
        assertTrue(harness.listener.results.isEmpty())
    }

    @Test
    fun stopCancelsPendingTimersAndSendsStopCommand() {
        val harness = Harness(VpetModel.DM20, 0)
        harness.session.start()
        harness.session.receiveLine("r:0301")
        harness.session.stop()

        assertEquals(2, harness.sent.size)
        assertEquals(VpetDigiRom.STOP_COMMAND, harness.sent.last())

        harness.scheduler.advanceBy(120_000L)
        assertTrue(harness.listener.results.isEmpty())
        assertTrue(harness.listener.failures.isEmpty())
        assertEquals(2, harness.sent.size)

        // Lines after stop are ignored instead of crashing or resurrecting the read.
        harness.session.receiveLine("r:1234")
        assertTrue(harness.listener.results.isEmpty())
    }

    @Test
    fun dmcColorBlockDeliversTheRead() {
        val harness = Harness(VpetModel.DMC, 0)
        val responder = VpetSimulator.responsesFor(VpetModel.DMC, 2, 3)
        harness.session.start()
        assertEquals(VpetDigiRom.commandFor(VpetModel.DMC, 0), harness.sent.last())
        assertTrue(harness.listener.progress.contains(10))

        deliver(harness, responder(harness.sent.last()))

        assertEquals(1, harness.listener.results.size)
        val result = harness.listener.results.single()
        assertEquals(VpetModel.DMC, result.model)
        assertEquals(2, result.version)
        assertEquals(3, result.index)
        assertNotNull(result.name)
        assertEquals(100, result.power)
        assertTrue(result.checksumValid)
        assertEquals(VpetDigiRom.STOP_COMMAND, harness.sent.last())
    }

    @Test
    fun dmcResendsTheColorBlockEveryTwoAndAHalfSeconds() {
        val harness = Harness(VpetModel.DMC, 0)
        harness.session.start()

        fun colorSends(): Int = harness.sent.count { it.startsWith("C1-") }

        assertEquals(1, colorSends())
        harness.scheduler.advanceBy(VpetReadSession.COLOR_RESEND_MS + 499L)
        assertEquals(1, colorSends())
        harness.scheduler.advanceBy(1L)
        assertEquals(2, colorSends())
    }

    @Test
    fun pencColorBlockDeliversTheRead() {
        val harness = Harness(VpetModel.PENC, 0)
        val responder = VpetSimulator.responsesFor(VpetModel.PENC, 0, 1)
        harness.session.start()

        deliver(harness, responder(harness.sent.last()))

        assertEquals(1, harness.listener.results.size)
        val result = harness.listener.results.single()
        assertEquals(VpetModel.PENC, result.model)
        assertEquals(0, result.version)
        assertEquals(1, result.index)
        assertEquals(VpetTables.keyedEntry("penc", "1-01")?.name, result.name)
        assertTrue(result.checksumValid)
        assertEquals(VpetDigiRom.STOP_COMMAND, harness.sent.last())
    }

    @Test
    fun pencDiscardsInvalidChecksumBlocksAndKeepsListening() {
        val harness = Harness(VpetModel.PENC, 0)
        harness.session.start()
        val validLine = VpetGoldenVectors.load()
            .decodes.first { it.model == VpetModel.PENC }
            .lines.single()
        val block = validLine.removePrefix("r:")
        val invalidTail = ((block.takeLast(4).toInt(16) + 1) and 0xFFFF)
            .toString(16).uppercase().padStart(4, '0')

        harness.session.receiveLine("r:" + block.dropLast(4) + invalidTail)

        assertTrue(harness.listener.results.isEmpty())
        assertTrue(harness.listener.statuses.any { it.contains("checksum") })
        assertEquals(60, harness.listener.progress.last())

        // A new block after an "s:" start marker is processed and finishes the read.
        harness.session.receiveLine("s:$block")
        harness.session.receiveLine("r:$block")

        assertEquals(1, harness.listener.results.size)
        assertTrue(harness.listener.results.single().checksumValid)
    }

    @Test
    fun pencDoesNotResendOnItsOwnAndRetriesAfterTheRequestTimeout() {
        val harness = Harness(VpetModel.PENC, 0)
        harness.session.start()
        assertEquals(1, sentCommands(harness).size)

        harness.scheduler.advanceBy(20_000L)
        assertEquals(1, sentCommands(harness).size)

        harness.scheduler.advanceBy(10_000L)
        assertEquals(2, sentCommands(harness).size)
        assertTrue(harness.listener.failures.isEmpty())
    }

    @Test
    fun simulatorReadsWorkForEveryModel() {
        val cases = listOf(
            Triple(VpetModel.DM20, 3, 4),
            Triple(VpetModel.DMX, 4, 5),
            Triple(VpetModel.PEN20, 6, 18),
            Triple(VpetModel.PENZ, 6, 10),
            Triple(VpetModel.DMC, 2, 3),
            Triple(VpetModel.PENC, 0, 1),
        )
        for ((model, version, index) in cases) {
            val harness = Harness(model, version)
            val responder = VpetSimulator.responsesFor(model, version, index)
            harness.session.start()
            deliver(harness, responder(harness.sent.last()))

            assertEquals("$model should finish the read", 1, harness.listener.results.size)
            val result = harness.listener.results.single()
            assertEquals(model, result.model)
            assertEquals("$model version", version, result.version)
            assertEquals("$model index", index, result.index)
            assertNotNull("$model name", result.name)
            assertTrue("$model checksum", result.checksumValid)
            assertEquals("$model stop command", VpetDigiRom.STOP_COMMAND, harness.sent.last())
        }
    }

    @Test
    fun scriptedLinkExercisesTheWholeReadFlow() {
        val scheduler = FakeVpetScheduler()
        val listener = RecordingVpetListener()
        lateinit var session: VpetReadSession
        val link = ScriptedVpetLink(
            responder = VpetSimulator.responsesFor(VpetModel.DM20, 3, 4),
            onLine = { line -> session.receiveLine(line) },
            scheduler = scheduler,
        )
        session = VpetReadSession(
            model = VpetModel.DM20,
            initialVersion = 0,
            send = { command -> link.send(command) },
            scheduler = scheduler,
            listener = listener,
        ).also { created -> created.timeSource = { scheduler.nowMillis } }

        session.start()
        scheduler.advanceBy(2_000L)
        link.close()
        session.stop()

        assertEquals(1, listener.results.size)
        val result = listener.results.single()
        assertEquals(3, result.version)
        assertEquals(4, result.index)
        assertEquals("Greymon", result.name)
        assertTrue(listener.failures.isEmpty())
    }

    @Test
    fun unsupportedInitialVersionFallsBackToTheModelDefault() {
        // Pendulum Ver.20th defaults to version 6, exactly like lector.js verGuardada.
        val harness = Harness(VpetModel.PEN20, 0)
        assertEquals(6, harness.session.learnedVersion())
        harness.session.start()
        assertEquals(
            VpetDigiRom.commandFor(VpetModel.PEN20, 6),
            harness.sent.last(),
        )
        assertFalse(harness.listener.statuses.isEmpty())
    }
}
