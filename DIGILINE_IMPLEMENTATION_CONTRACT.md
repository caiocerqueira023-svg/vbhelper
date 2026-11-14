# DigiLine v2 — Implementation Contract (code-level)

Companion to `DIGILINE_CHAT_SYSTEM_PLAN.md` (locked decisions §0.1/§0.2). This file is the
**contract between workstreams**: share it verbatim, do not invent different field names.

**Hard rule for this implementation pass (user directive):** do NOT run Gradle builds, do NOT run
Android/unit tests, do NOT run `./gradlew`. Only static work (Kotlin/Compose writing, Python
content generation, JSON validation with Python, plain file checks). The Lead runs the first build
and tests only after every workstream is done.

---

## 0. Ownership map (who writes which files)

| Workstream | Owns | Must not touch |
|---|---|---|
| A. Content pipeline | `tools/digiline/**`, `app/src/main/assets/digiline/**` | all `.kt` files |
| B. Models + engines | `app/src/main/java/com/tamerslab/digiline/model/**`, `.../digiline/engine/**`, `app/src/test/java/com/tamerslab/digiline/**` | assets, Room, UI, app wiring |
| C. Data + loader + gateway | `app/src/main/java/com/tamerslab/digiline/data/**`, `.../digiline/content/**`, `.../digiline/gateway/DigilineGateway.kt` (interface + scripted impl), `app/src/main/java/com/tamerslab/data/database/AppDatabase.kt` (digiline additions only) | `engine/**`, UI, `di/DefaultAppContainer.kt` (Lead integrates), `chat/**` |
| D. Lead integration | UI (`chat/ui`, `farm/ui/digiline`, `world/ui`, `settings/ui`), controllers, `di/*`, wild/quest/native-save/LLM decommission, resources | B/C owned files unless coordinated |

Impl file scopes are recorded on the shared task board. If you need to touch another scope,
message the Lead first.

---

## 1. Assets layout (workstream A produces; C loads)

```
app/src/main/assets/digiline/v1/
├── manifest.json
└── en/
    ├── species_questions.json
    ├── generic_questions.json
    ├── quiz.json
    ├── negotiation.json
    ├── persuasion.json
    ├── event_lines.json
    └── lore_species.json
```

`manifest.json`:

```json
{ "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "files": { "species": "species_questions.json", "generic": "generic_questions.json",
             "quiz": "quiz.json", "negotiation": "negotiation.json",
             "persuasion": "persuasion.json", "events": "event_lines.json",
             "lore": "lore_species.json" } }
```

All text is **original content written for this project** (plan D1). Do not copy lines from
`digimon_chat_export/`, the bundled `species_chat_examples.json`, SMT V, or Cyber Sleuth exports.
The bundled species chats may be used only as *data-shape reference*; if a line is reused it must be
rewritten. Lore facts must come from `app/src/main/assets/species_catalog.json` (game data), and
each factual line carries a `loreRefs` entry; no invented Digimon lore.

### 1.1 `species_questions.json`

```json
{
  "schemaVersion": 1,
  "questions": [
    {
      "id": "sp.<normalizedSpeciesName>.<n>",
      "speciesKey": "aegiomon",
      "speciesName": "Aegiomon",
      "stageTier": 2,
      "prompt": "…original line in the Digimon's voice…",
      "options": [
        { "id": "a", "text": "…player answer…", "reaction": "…Digimon reply…",
          "moodDelta": 1, "shift": { "6": 1 }, "tone": "bravery" }
      ],
      "tags": ["species"]
    }
  ]
}
```

- `speciesKey`: lowercase, letters/digits only (strip spaces, punctuation, parentheses).
- `stageTier`: 1..4 from the species level using §7.3 mapping (Baby/Child→1, Adult/Armor/Hybrid→2,
  Perfect→3, Ultimate→4; unknown→2).
- 3–4 options per question; ids are unique inside the question; `shift` keys are personality type
  numbers 1..16 with int points (see §2 personality map).
- `moodDelta` per option: −1..+2 (questions should be gentle; quizzes carry the −5 risk).
- Coverage target for this pass: **all matchable species that have an app catalog entry** (one
  question each, 3–4 options). The team may generate option lines from authored per-core templates
  instead of writing 1,000 unique lines; mark `"authoringStatus": "template"` on those questions.
  Unmatchable species are skipped and listed in `tools/digiline/unmatched_species.json`.

### 1.2 `generic_questions.json`

```json
{
  "schemaVersion": 1,
  "questions": [
    { "id": "gen.<typeNumber>.<n>", "askerTypes": [6], "core": "VALOR", "stageTier": 1,
      "prompt": "…", "options": [ { "id": "a", "text": "…", "reaction": "…",
      "moodDelta": 0, "shift": { "6": 2, "7": 1 } } ] }
  ]
}
```

- `askerTypes`: 1..2 personality type numbers. Coverage target this pass: **every type 1..16 has
  ≥ 2 generic questions**; `core` is VALOR/PHILANTHROPY/WISDOM/AMICABILITY.
- Only original text; no species names/facts (generic questions are lore-neutral).

### 1.3 `quiz.json`

```json
{
  "schemaVersion": 1,
  "questions": [
    { "id": "quiz.<n>", "stageTier": 2,
      "prompt": "Which type is Aegiomon?",
      "options": ["Vaccine", "Data", "Virus", "Free"],
      "correctIndex": 0,
      "explanation": "Aegiomon is listed as a Vaccine-type Digimon in the species catalog.",
      "loreRefs": ["species_catalog:aegiomon"] }
  ]
}
```

- Generate only facts that exist in `species_catalog.json` (`level`, `type` — inspect actual field
  names/values first; if `type` is empty, fall back to `level` questions or evolution-ladder pairs).
- Target: **≥ 100 questions**, 3 or 4 options, correctIndex valid, no duplicate prompts.
- `loreRefs` must reference `species_catalog:<normalized name>`.

### 1.4 `negotiation.json`

Authored dialogue trees, keyed by personality type number then stage tier. Every personality 1..16
and tiers 1..4 must exist. These are SMT-style exchanges, not a recruitment skeleton: each question
offers 2–3 in-world answers, **every answer carries an authored demon reaction that responds to that
answer**, and the follow-up prompt continues the same authored `topic` (or deliberately ends it at a
`demand`). Options must be speech that answers the prompt, never interface/meta lines.

```json
{
  "schemaVersion": 1,
  "personalities": {
    "6": {
      "1": { "start": "p6.t1.start", "nodes": {
        "p6.t1.start": { "speaker": "wild", "tag": "greeting", "topic": "intent", "text": "…",
          "options": [
            { "id": "a", "topic": "intent", "text": "…answer…",
              "reaction": "…demon's response to that answer…", "next": "p6.t1.warm" },
            { "id": "b", "topic": "intent", "text": "…different answer…",
              "reaction": "…response…", "next": "p6.t1.cold" } ] },
        "p6.t1.warm": { "speaker": "wild", "tag": "probe", "topic": "intent", "text": "…follow-up…",
          "options": [ { "id": "a", "topic": "demand", "text": "…",
                         "reaction": "…", "next": "p6.t1.demand" } ] },
        "p6.t1.demand": { "speaker": "wild", "tag": "demand", "topic": "demand", "text": "…",
          "demand": { "kind": "bits", "amount": 60 },
          "options": [ { "id": "pay", "topic": "demand", "text": "…", "reaction": "…",
                         "next": "p6.t1.join", "effects": { "bits": -60 } },
                       { "id": "no", "topic": "demand", "text": "…", "reaction": "…",
                         "next": "p6.t1.refuse" } ] },
        "p6.t1.join": { "speaker": "wild", "tag": "join", "topic": "demand", "text": "…",
                        "outcome": "JOIN" }
      } }
    }
  }
}
```

Rules the validator enforces: non-terminal answers have a non-empty `reaction`; `option.topic`
equals its non-demand follow-up node's `topic`; every node has a `topic`; terminal nodes inherit the
last topic; demand nodes use `topic: "demand"`; every question node has at least 2 answers; every
flow reaches JOIN and a REFUSE/LEAVE path; authored depth is 2/3/3/4 exchanges for tiers 1..4. The
primary price node may offer exactly one one-time counter-offer answer (`id: "counter"`) that leads
to a reduced-demand node and never appears again in the flow. Node tags: `greeting`, `probe`,
`demand`, `trick`, `insight`, `resolve`, `join`, `refuse`, `depart`.

Effects allowed on an option: `bits` (int, negative = paid), `itemId` + `itemQuantity` (optional,
must come from the allowed item catalog chosen by the Lead later; emitter may leave `null`),
`persuasionFlag` (string), `depart` (bool, only on the final tell option). Outcomes: `JOIN`,
`REFUSE`, `LEAVE`, `FIGHT`, `PAY_AND_LEAVE`, `DEPART`.

### 1.5 `persuasion.json`

Same node shape as negotiation, keyed `personalities -> "<type>" -> "<stage 0..3>"` with a `start`
node per stage. Terminal outcomes: `NEXT_STAGE`, `QUEST_OFFER`, `REFUSE`, `DEPART`. Each stage
must contain at least 2 authored choice nodes. Every personality needs stages 0..3.

### 1.6 `event_lines.json`

```json
{ "schemaVersion": 1, "lines": [
  { "id": "ev.battle_win.6.1", "event": "battle_win", "types": [6, 7], "text": "…",
    "moodDelta": 1 } ] }
```

Events required: `battle_win`, `battle_loss`, `injury`, `heal`, `evolution`, `degeneration`,
`milestone`, `quest_offer`, `quest_complete`, `item_found`, `diary`, `wild_depart`, `recruit_join`.
At least 2 lines per event; personality-specific where possible, otherwise core-wide (use
`types` or omit for shared). `moodDelta` range −2..+2 (event reactions only, not quiz).

### 1.7 `lore_species.json`

```json
{ "schemaVersion": 1, "facts": [
  { "speciesKey": "aegiomon", "speciesName": "Aegiomon", "level": "Adult", "type": "Vaccine",
    "attribute": "…if present…", "source": "app_species_catalog", "sourceUrl": null } ] }
```

Generated from `app/src/main/assets/species_catalog.json`. Only fields that actually exist.

### 1.8 Python deliverables (workstream A)

- `tools/digiline/build_packs.py` — deterministic generator (reads `species_chat_examples.json`
  and `species_catalog.json`, writes `app/src/main/assets/digiline/v1/...`).
- `tools/digiline/validate_packs.py` — validates the JSON against §1 schemas + coverage report.
- `tools/digiline/unmatched_species.json` — generated report.
- Run both with `python`; report counts in the hand-off message. No Gradle.

---

## 2. Personality reference (already in the app)

`DigimonPersonalityType` in `app/src/main/java/com/tamerslab/domain/personality/DigimonPersonalityTraits.kt`
has public `number` (1..16) and `displayName`. Quadrant mapping (do not persist a separate value):

| numbers | core |
|---|---|
| 1–4 | PHILANTHROPY (love) |
| 5–8 | VALOR (courage) |
| 9–12 | WISDOM (knowledge) |
| 13–16 | AMICABILITY (friendship) |

---

## 3. Kotlin models (workstream B; package `com.tamerslab.digiline.model`)

Use `kotlinx.serialization` (`@Serializable`, `@SerialName`), `ignoreUnknownKeys = true` at parse
site. Exact shapes (add helper properties freely, but keep constructors compatible):

```kotlin
@Serializable data class ContentManifest(val schemaVersion: Int, val contentVersion: Int,
    val locale: String, val files: Map<String, String>)

@Serializable data class AnswerOptionDto(val id: String, val text: String,
    val reaction: String = "", val moodDelta: Int = 0,
    val shift: Map<String, Int> = emptyMap(), val tone: String? = null)

@Serializable data class QuestionCardDto(val id: String, val prompt: String,
    val options: List<AnswerOptionDto>, val stageTier: Int = 1,
    val speciesKey: String? = null, val speciesName: String? = null,
    val askerTypes: List<Int> = emptyList(), val core: String? = null,
    val tags: List<String> = emptyList(), val authoringStatus: String? = null)

@Serializable data class QuestionPackDto(val schemaVersion: Int = 1,
    val questions: List<QuestionCardDto> = emptyList())

@Serializable data class QuizCardDto(val id: String, val stageTier: Int, val prompt: String,
    val options: List<String>, val correctIndex: Int, val explanation: String,
    val loreRefs: List<String> = emptyList())

@Serializable data class QuizPackDto(val schemaVersion: Int = 1, val questions: List<QuizCardDto> = emptyList())

@Serializable data class NegotiationEffectsDto(val bits: Int = 0, val itemId: String? = null,
    val itemQuantity: Int = 0, val persuasionFlag: String? = null, val depart: Boolean = false)

@Serializable data class NegotiationOptionDto(val id: String, val text: String,
    val next: String? = null, val requires: String? = null,
    val reaction: String? = null, val topic: String? = null,
    val effects: NegotiationEffectsDto = NegotiationEffectsDto())

@Serializable data class NegotiationNodeDto(val id: String, val text: String,
    val speaker: String = "wild", val tag: String? = null, val topic: String? = null,
    val demand: DemandDto? = null, val outcome: String? = null,
    val options: List<NegotiationOptionDto> = emptyList())

@Serializable data class DemandDto(val kind: String, val amount: Int = 0, val itemId: String? = null)

@Serializable data class NegotiationFlowDto(val start: String,
    val nodes: Map<String, NegotiationNodeDto> = emptyMap())

@Serializable data class NegotiationPackDto(val schemaVersion: Int = 1,
    val personalities: Map<String, Map<String, NegotiationFlowDto>> = emptyMap())

@Serializable data class PersuasionPackDto(val schemaVersion: Int = 1,
    val personalities: Map<String, Map<String, NegotiationFlowDto>> = emptyMap())

@Serializable data class EventLineDto(val id: String, val event: String,
    val types: List<Int> = emptyList(), val text: String, val moodDelta: Int = 0)

@Serializable data class EventLinePackDto(val schemaVersion: Int = 1, val lines: List<EventLineDto> = emptyList())

@Serializable data class LoreFactDto(val speciesKey: String, val speciesName: String = "",
    val level: String? = null, val type: String? = null, val attribute: String? = null,
    val source: String = "app_species_catalog", val sourceUrl: String? = null)

@Serializable data class LorePackDto(val schemaVersion: Int = 1, val facts: List<LoreFactDto> = emptyList())
```

Check the real `type`/`attribute` field names in `species_catalog.json` before finalising
`LoreFactDto`; adjust and tell the Lead if a field is missing (the app catalog is the source of
truth for quiz facts).

In-memory pack (package `com.tamerslab.digiline.content`, workstream C, but B's engines consume it):

```kotlin
class DigilineContentPack(
    val manifest: ContentManifest,
    val questions: List<QuestionCardDto>,          // species questions
    val genericQuestions: List<QuestionCardDto>,
    val quizzes: List<QuizCardDto>,
    val negotiation: NegotiationPackDto,
    val persuasion: PersuasionPackDto,
    val eventLines: List<EventLineDto>,
    val lore: List<LoreFactDto>,
) {
    fun speciesQuestion(speciesKey: String, variant: Int): QuestionCardDto?   // deterministic pick
    fun genericQuestion(typeNumber: Int, seed: Long): QuestionCardDto?
    fun quiz(stageTier: Int, seed: Long, excludedIds: Set<String>): QuizCardDto?
    fun negotiationFlow(type: Int, tier: Int): NegotiationFlowDto?
    fun persuasionFlow(type: Int, stage: Int): NegotiationFlowDto?
    fun eventLines(event: String, type: Int): List<EventLineDto>
}
```

### 3.1 Pure engine contracts (workstream B)

All engines are pure Kotlin (no Android imports) with injected `now: Long` and seeded
`kotlin.random.Random`/stable hash helper. Suggested files:

- `model/EngineModels.kt`: `ShiftResult`, `QuestionSelection`, `QuizOutcome`, `NegotiationState`,
  `NegotiationStep`, `PersuasionState`, `PersuasionStep`, `ReactionPick`, `DigilineSeed`.
- `engine/PersonalityShiftEngine.kt`
  `fun apply(points: Map<Int,Int>, currentType: Int, optionShift: Map<String,Int>, now: Long,
   lastShiftAt: Long?, answeredSinceShift: Int): ShiftOutcome` with the plan rules
  (points ±, decay 10 %/day, cap 20, lead ≥4, ≥2 answers, 24 h cooldown, hysteresis `BRAVE` example).
  Must return normalized points (1..16 keys) and a shift flag. Also `fun coreOf(type:Int):Int`.
- `engine/QuestionScheduler.kt`
  `fun select(candidates: List<QuestionCardDto>, kind: Kind, seed: Long, recentIds: Set<String>,
   variant: Int): QuestionCardDto?` deterministic; never throws on empty.
- `engine/QuizEngine.kt`
  `fun resolve(quiz: QuizCardDto, chosenIndex: Int): QuizOutcome(moodDelta: Int, correct: Boolean)`
  correct=+3, wrong=−5; floor handled by repository.
- `engine/NegotiationEngine.kt`
  `fun start(flow, seed, tier): NegotiationState`, `fun choose(state, optionId): NegotiationStep`
  (validates option id, applies effects; outcomes mapped to enum `NegotiationOutcome`), demand
  affordability helpers take `bits: Long`, `items: Map<String,Int>`.
- `engine/DemandResolver.kt` — affordability/caps; never demands last item; non-payment branch
  fallback.
- `engine/PersuasionEngine.kt` — stage progression, `QUEST_OFFER` outcome, flags.
- `engine/ReactionLineProvider.kt` — deterministic line pick from `eventLines` by event+type.
- `engine/DiaryComposer.kt` — deterministic diary line assembly (event + type + facts list),
  no LLM.
- `engine/OwnedDigilineEngine.kt` — pure state machine (Idle → Question/Quiz → Answered/Reaction →
  Idle). Repository persists state; engine decides next. Keep it free of Room/Android.

Unit tests in `app/src/test/java/com/tamerslab/digiline/`: determinism (same seed same result),
shift thresholds/cooldown/decay, quiz deltas, negotiation effects/outcomes, demand caps,
persuasion stage progression, no crash on empty content. **Write tests but do not run them**
(no Gradle).

---

## 4. Room schema (workstream C + Lead integration)

`AppDatabase` version **55 → 56**, `exportSchema = true`. Add entities (package
`com.tamerslab.digiline.data`; names exact — Lead will reference them):

```kotlin
@Entity(tableName = "DigilineProfile")
data class DigilineProfileEntity(
    @PrimaryKey val individualId: String,
    val speciesVariant: Int = 0,
    val lastAskedAt: Long = 0,
    val lastQuizAt: Long = 0,
    val lastShiftAt: Long = 0,
    val sessionCounter: Long = 0,
    val contentVersion: Int = 0,
    val nativeNoticeShown: Boolean = false,
)

@Entity(tableName = "DigilineQuestionRecord", indices = [Index("individualId")])
data class DigilineQuestionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String, val questionKey: String, val kind: String,
    val askedAt: Long, val answeredAt: Long? = null, val optionId: String? = null,
    val shiftJson: String? = null, val moodDelta: Int = 0, val status: String = "ASKED",
)

@Entity(tableName = "DigilineQuizRecord", indices = [Index("individualId")])
data class DigilineQuizRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String, val quizKey: String, val chosenIndex: Int,
    val correct: Boolean, val moodDelta: Int, val answeredAt: Long,
)

@Entity(tableName = "PersonalityTendency", primaryKeys = ["individualId", "typeNumber"])
data class PersonalityTendencyEntity(
    val individualId: String, val typeNumber: Int, val points: Int, val updatedAt: Long,
)

@Entity(tableName = "NativePersonalitySync")
data class NativePersonalitySyncEntity(
    @PrimaryKey val individualId: String, val targetType: Int, val targetNativeId: Int?,
    val state: String, val updatedAt: Long,
)

@Entity(tableName = "DigilineNegotiation")
data class DigilineNegotiationEntity(
    @PrimaryKey val wildIndividualId: String, val tier: Int, val nodeId: String,
    val phase: String, val seed: Long, val demandsJson: String?, val startedAt: Long,
    val updatedAt: Long, val state: String, val departUsed: Boolean = false,
)

@Entity(tableName = "DigilineNegotiationLog", indices = [Index("wildIndividualId")])
data class DigilineNegotiationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wildIndividualId: String, val nodeId: String, val optionId: String?, 
    val effectsJson: String?, val createdAt: Long,
)

@Entity(tableName = "WildPersuasion")
data class WildPersuasionEntity(
    @PrimaryKey val wildIndividualId: String, val stageIndex: Int = 0,
    val topicsDoneJson: String = "[]", val questOfferState: String = "NONE",
    val joinedAt: Long, val lastTalkAt: Long = 0, val departureState: String = "NONE",
)

@Entity(tableName = "DigilineEventRecord", indices = [Index("individualId")])
data class DigilineEventRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String, val eventKey: String, val deliveredAt: Long,
)
```

DAO (`DigilineDao`) with suspend functions for read/insert/update/delete per table + read-only
flows where cheap. Keep columns exactly as above (Room will validate).

`ChatMessageEntity` gets two nullable columns via migration:
`ALTER TABLE ChatMessageEntity ADD COLUMN kind TEXT` and `ADD COLUMN payloadJson TEXT`. Add fields
to the entity with defaults (`val kind: String? = null, val payloadJson: String? = null`).

`GameEndpointPresenceSchema`-style migration file convention: create
`app/src/main/java/com/tamerslab/data/database/DigilineSchema.kt` with
`object DigilineSchema { fun migrate(db: SupportSQLiteDatabase) { ... CREATE TABLE IF NOT EXISTS ... } }`
and `AppDatabase.MIGRATION_55_56 = object : Migration(55,56) { override fun migrate(db) = DigilineSchema.migrate(db) }`.
Add the new entities to `@Database(entities=[...])` and `abs fun digilineDao()`.
Do **not** edit `DefaultAppContainer` (Lead adds `.addMigrations(AppDatabase.MIGRATION_55_56)`).

Content loader (workstream C, package `com.tamerslab.digiline.content`):

```kotlin
class DigilineContentLoader(
    private val assets: AssetManager,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : DigilineContentSource {
    override suspend fun load(assetRoot: String = "digiline/v1"): DigilineContentPack
}
```
Reads `manifest.json` then the listed files, parses DTOs, throws `IllegalStateException` with file
name on malformed JSON. Add `DigilineContentValidator` (pure) checking ids/options/shift ranges/
correctIndex/node reachability; expose `fun validate(pack): List<String>` warnings/errors and a
`strict` mode. Loader must be usable without Android by tests? Keep `AssetManager` dependency
isolated: accept `(String) -> String` reader for tests.

Gateway interface (workstream C, package `com.tamerslab.digiline.gateway`):

```kotlin
data class DigilineOptionView(val id: String, val text: String)
data class DigilineScene(
    val kind: Kind,               // QUESTION, QUIZ, NEGOTIATION, PERSUASION, REACTION, IDLE
    val title: String? = null,    // e.g. negotiation phase
    val lines: List<String> = emptyList(),
    val options: List<DigilineOptionView> = emptyList(),
    val canDismiss: Boolean = true,
    val relationshipLabel: String? = null,
    val syncLabel: String? = null,
    val meta: String? = null? = null,
) { enum class Kind { QUESTION, QUIZ, NEGOTIATION, PERSUASION, REACTION, IDLE } }

sealed interface DigilineResult {
    data class Scene(val scene: DigilineScene) : DigilineResult
    data class Closed(val message: String? = null) : DigilineResult
    data class Error(val message: String) : DigilineResult
}

interface DigilineGateway {
    fun observeHistory(individualId: String): Flow<List<ChatMessageEntity>>
    fun observeScene(individualId: String): Flow<DigilineScene?>   // nullable until loaded
    suspend fun ensureScene(individualId: String, wild: Boolean): DigilineResult
    suspend fun choose(individualId: String, optionId: String): DigilineResult
    suspend fun dismiss(individualId: String): DigilineResult
}
```

(`meta` typo guard: use exactly one `meta: String? = null` field.)

C also writes `ScriptedDigilineGateway` only if B's engines + C's repo are ready; otherwise the
Lead will. Do not touch `chat/ui` controllers.

---

## 5. Lead integration surface (for reference; do not do it in B/C)

- `ChatScreenControllerImpl`/`ChatScreen` → use `DigilineGateway`; debug toggle shows legacy free
  text.
- `WorldChatScreenControllerImpl`/`WorldChatScreen` → negotiation/persuasion scenes.
- `DigilineScreen` → status chips/badges.
- `QuestRepository` recruitment gating: replace `giver.trust >= 100` with
  `WildPersuasionEntity.questOfferState` (PENDING/OFFERED) and queue logic; contact unlock on
  negotiation JOIN.
- `WildRelationshipDao.updateTrust` unlock path retired from player progression (keep column).
- `WorldRepository`/`WorldChatScreenController` stop using trust for unlock/progress.
- Native save sync: `TimeStrangerEdits.setPersonality(...)` + sync coordinator; auto-run on save
  write/export; `NativePersonalitySyncEntity` states.
- LLM decommission: `ChatRepository` LLM methods become legacy-gated (debug + sandboxed);
  `publicDialogueAvailable()` returns false in normal mode; other surfaces use deterministic
  fallbacks (`ReactionLineProvider`, `DiaryComposer`, existing `SocialOpenings`/codecs).
- `DebugSettingsRepository` + settings screen legacy toggle.
- Strings (en at minimum; pt/ja fall back to en string resources where content is English).

---

## 6. Workstream acceptance checklist

**A (content):** `python tools/digiline/build_packs.py` succeeds deterministically; validator
reports 0 errors; all required files exist; manifest matches files; negotiation/persuasion cover
16 personalities × 4 tiers/stages; quiz ≥100; every type has ≥2 generic questions; species
questions matchable count reported; no copied third-party lines (spot-check policy documented).

**B (engines):** files compile *logically* against §3 (no Gradle): no Android imports, no
unresolved references outside `digiline.model/content`; unit tests written for every engine;
no TODO stubs that return fake data (use content DTOs + pure logic).

**C (data):** all entities/DAO/migration/time columns match §4 exactly; loader parses all packs
using a file-reader lambda (testable without Android); validator implemented; gateway interface
exact; no edits outside owned scope (except AppDatabase digiline additions).

Report completion with: files changed, key decisions, anything the Lead must wire, and known gaps.
Do not run builds/tests.
