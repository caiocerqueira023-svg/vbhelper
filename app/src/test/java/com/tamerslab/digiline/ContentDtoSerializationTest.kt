package com.tamerslab.digiline

import com.tamerslab.digiline.model.ContentManifest
import com.tamerslab.digiline.model.EventLinePackDto
import com.tamerslab.digiline.model.LorePackDto
import com.tamerslab.digiline.model.NegotiationOutcome
import com.tamerslab.digiline.model.NegotiationPackDto
import com.tamerslab.digiline.model.PersuasionOutcome
import com.tamerslab.digiline.model.PersuasionPackDto
import com.tamerslab.digiline.model.QuestionPackDto
import com.tamerslab.digiline.model.QuizPackDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DTOs are the contract with the generated asset packs, so parsing real wire JSON is asserted
 * here (field names, optional fields, numeric map keys, missing node ids, unknown extra keys).
 */
class ContentDtoSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun manifestParses() {
        val raw = """
            {"schemaVersion":1,"contentVersion":42,"locale":"en",
             "files":{"species":"species_questions.json","generic":"generic_questions.json"}}
        """.trimIndent()
        val manifest = json.decodeFromString<ContentManifest>(raw)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(42, manifest.contentVersion)
        assertEquals("en", manifest.locale)
        assertEquals("species_questions.json", manifest.files["species"])
    }

    @Test
    fun questionPackParsesShiftVectorsAndOptionalFields() {
        val raw = """
            {"schemaVersion":1,"questions":[
              {"id":"sp.aegiomon.1","speciesKey":"aegiomon","speciesName":"Aegiomon",
               "stageTier":2,"prompt":"Prompt","tags":["species"],
               "options":[
                 {"id":"a","text":"Answer A","reaction":"Reaction A","moodDelta":1,
                  "shift":{"6":1,"7":2},"tone":"bravery"},
                 {"id":"b","text":"Answer B"}
               ]},
              {"id":"gen.6.1","prompt":"Generic","askerTypes":[6],"core":"VALOR",
               "options":[{"id":"a","text":"A"}]}
            ]}
        """.trimIndent()
        val pack = json.decodeFromString<QuestionPackDto>(raw)
        assertEquals(2, pack.questions.size)
        val species = pack.questions[0]
        assertTrue(species.isSpeciesQuestion)
        assertEquals(mapOf(6 to 1, 7 to 2), species.options[0].shiftByType)
        assertEquals(1, species.options[0].moodDelta)
        assertEquals("bravery", species.options[0].tone)
        assertEquals(emptyMap<Int, Int>(), species.options[1].shiftByType)
        assertEquals("Reaction A", species.option("a")?.reaction)
        assertEquals(listOf(6), pack.questions[1].askerTypes)
        assertEquals("VALOR", pack.questions[1].core)
    }

    @Test
    fun negotiationPackParsesNodesWithoutIdsAndOptionEffects() {
        val raw = """
            {"schemaVersion":1,"personalities":{"6":{"1":{"start":"s","nodes":{
              "s":{"speaker":"wild","tag":"greeting","text":"Hello.",
                   "options":[{"id":"talk","text":"Let's talk.","next":"d"}]},
              "d":{"speaker":"wild","tag":"demand","text":"Pay up.",
                   "demand":{"kind":"bits","amount":60},
                   "options":[
                     {"id":"pay","text":"Fine.","next":"j",
                      "effects":{"bits":-60,"itemId":null,"itemQuantity":0,
                                 "persuasionFlag":"paid","depart":false}},
                     {"id":"no","text":"No.","next":"j"}
                   ]},
              "j":{"speaker":"wild","tag":"join","text":"Welcome.","outcome":"JOIN"}
            }}}}}
        """.trimIndent()
        val pack = json.decodeFromString<NegotiationPackDto>(raw)
        val flow = pack.personalities["6"]?.get("1")
        assertNotNull(flow)
        assertEquals("s", flow!!.start)
        assertEquals("Hello.", flow.nodes["s"]?.text)
        // Authored packs omit the id inside node objects; the DTO default handles it.
        assertEquals("", flow.nodes["s"]?.id)
        assertEquals("bits", flow.nodes["d"]?.demand?.kind)
        assertEquals(60, flow.nodes["d"]?.demand?.amount)
        val pay = flow.nodes["d"]?.options?.first { it.id == "pay" }
        assertEquals(-60, pay?.effects?.bits)
        assertEquals(60L, pay?.effects?.bitsCost)
        assertEquals("paid", pay?.effects?.persuasionFlag)
        assertFalse(pay?.effects?.depart == true)
        assertTrue(flow.nodes["j"]?.isTerminal == true)
        assertEquals(NegotiationOutcome.JOIN, NegotiationOutcome.fromWire(flow.nodes["j"]?.outcome))
        assertEquals(NegotiationOutcome.PAY_AND_LEAVE, NegotiationOutcome.fromWire("pay_and_leave"))
        assertNull(NegotiationOutcome.fromWire("not_an_outcome"))
    }

    @Test
    fun persuasionPackSharesTheNegotiationFlowShape() {
        val raw = """
            {"schemaVersion":1,"personalities":{"10":{"0":{"start":"s","nodes":{
              "s":{"tag":"greeting","text":"Hm.","options":[{"id":"a","text":"Listen.","next":"e"}]},
              "e":{"tag":"resolve","text":"Maybe.","outcome":"NEXT_STAGE"}
            }}}}}
        """.trimIndent()
        val pack = json.decodeFromString<PersuasionPackDto>(raw)
        val flow = pack.personalities["10"]?.get("0")
        assertNotNull(flow)
        assertEquals("NEXT_STAGE", flow?.nodes?.get("e")?.outcome)
        assertEquals(PersuasionOutcome.NEXT_STAGE, PersuasionOutcome.fromWire(flow?.nodes?.get("e")?.outcome))
    }

    @Test
    fun quizPackParsesOptionsAndLoreRefs() {
        val raw = """
            {"schemaVersion":1,"questions":[
              {"id":"quiz.1","stageTier":2,"prompt":"Which type is Aegiomon?",
               "options":["Vaccine","Data","Virus","Free"],"correctIndex":0,
               "explanation":"The catalog lists Vaccine.","loreRefs":["species_catalog:aegiomon"]}
            ]}
        """.trimIndent()
        val pack = json.decodeFromString<QuizPackDto>(raw)
        val quiz = pack.questions.single()
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.hasValidAnswer)
        assertEquals("Vaccine", quiz.correctAnswer)
        assertEquals(listOf("species_catalog:aegiomon"), quiz.loreRefs)
    }

    @Test
    fun eventAndLorePacksParse() {
        val eventRaw = """
            {"schemaVersion":1,"lines":[
              {"id":"ev.battle_win.6.1","event":"battle_win","types":[6,7],
               "text":"We won.","moodDelta":1}
            ]}
        """.trimIndent()
        val events = json.decodeFromString<EventLinePackDto>(eventRaw)
        assertEquals(2, events.lines.single().types.size)
        assertEquals(1, events.lines.single().moodDelta)

        val loreRaw = """
            {"schemaVersion":1,"facts":[
              {"speciesKey":"aegiomon","speciesName":"Aegiomon","level":"Adult",
               "type":"Vaccine","source":"app_species_catalog","sourceUrl":null}
            ]}
        """.trimIndent()
        val lore = json.decodeFromString<LorePackDto>(loreRaw)
        val fact = lore.facts.single()
        assertEquals("Adult", fact.level)
        assertEquals("Vaccine", fact.type)
        assertNull(fact.sourceUrl)
        assertEquals("app_species_catalog", fact.source)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val raw = """
            {"schemaVersion":1,"contentVersion":1,"locale":"en","files":{},
             "futureField":{"nested":true}}
        """.trimIndent()
        val manifest = json.decodeFromString<ContentManifest>(raw)
        assertEquals(1, manifest.contentVersion)
    }
}
