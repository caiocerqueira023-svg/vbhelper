package com.github.nacabaro.vbhelper.quests

import org.junit.Assert.*
import org.junit.Test

class QuestDialogueCodecTest {
    private val quest = QuestInstance("q", "giver", 1, "Giver", QuestCategory.NORMAL, "supplies", 4, 42, 2, revision = 5, createdAt = 0)
    private fun envelope(id: String = "q", revision: Int = 5, evidence: String = "private:10", type: String = "ACCEPT", partner: String = "null") =
        """{"lines":[{"speakerId":"giver","text":"A request."}],"questAction":{"type":"$type","questId":"$id","revision":$revision,"evidenceId":"$evidence","partnerId":$partner}}"""
    @Test fun `only saved giver scoped current revision and current user evidence is actionable`() {
        assertNotNull(QuestDialogueCodec.parse(envelope(), "giver", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse(envelope(id = "invented"), "giver", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse(envelope(), "other", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse(envelope(revision = 4), "giver", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse(envelope(evidence = "private:9"), "giver", listOf(quest), 10))
    }
    @Test fun `partner selection must name a stored individual and only applies to acceptance`() {
        assertNotNull(QuestDialogueCodec.parse(envelope(partner = "\"partner\""), "giver", listOf(quest), 10, setOf("partner")))
        assertNull(QuestDialogueCodec.parse(envelope(partner = "\"invented\""), "giver", listOf(quest), 10, setOf("partner")))
        assertNull(QuestDialogueCodec.parse(envelope(type = "TURN_IN", partner = "\"partner\""), "giver", listOf(quest), 10, setOf("partner")))
    }
    @Test fun `malformed oversized and invented command envelopes cannot execute`() {
        assertNull(QuestDialogueCodec.parse("{", "giver", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse("x".repeat(12001), "giver", listOf(quest), 10))
        assertNull(QuestDialogueCodec.parse(envelope(type = "GRANT_REWARD"), "giver", listOf(quest), 10))
    }
}
