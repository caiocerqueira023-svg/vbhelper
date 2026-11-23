# Free Talk ("Papo") — Implementation Contract (v1)

Companion to [`DIGILINE_FREE_TALK_PLAN.md`](DIGILINE_FREE_TALK_PLAN.md) (owner decisions locked 2026-11-17:
hybrid big corpus, Big v1 target, owned partners only, conservative relationship effects, personality-dependent tone,
EN first, inline composer, owner+AI drafts, on by default with Debug kill-switch, implement full MVP now).

This file is the **contract between workstreams**: reference it verbatim, do not invent different names/signatures.
Owner map and hard rules below.

---

## 0. Ownership map (write scopes)

| Workstream | Owns | Must not touch |
|---|---|---|
| **A. Content pipeline** | `tools/freechat/**`, `app/src/main/assets/freechat/**` | all `.kt` files |
| **B. Models + engines** | `app/src/main/java/com/tamerslab/digiline/freechat/model/**`, `.../freechat/engine/**`, `app/src/test/java/com/tamerslab/digiline/freechat/**` | assets, Room, UI, DI |
| **C. Content loader + data + gateway** | `.../freechat/content/**`, `.../freechat/data/**`, `.../freechat/gateway/**`, `app/src/main/java/com/tamerslab/data/database/AppDatabase.kt` (freechat additions only) | engine/model, UI, DI registration |
| **D. Controller + UI + settings** | `app/src/main/java/com/tamerslab/chat/ui/**`, `com/tamerslab/settings/data/DebugSettingsRepository.kt`, `com/tamerslab/settings/ui/**` (freechat kill-switch only), `app/src/main/res/values*/strings.xml` (freechat-only keys) | data/engine/model, DI |
| **Lead** | `di/**`, `app/build.gradle.kts`, `docs/**`, `DISTRIBUTION_GATE.md`, integration/build/tests/review | — |

Hard rules:
- **No runtime LLM.** No HTTP in this feature. No `ChatRepository`/`OpenRouter` imports outside the legacy path.
- **Deterministic**: no `Random.Default`, no clock in selection; persist decision before display; reuse
  `com.tamerslab.digiline.model.DigilineSeed` (`stabileHash`, `combine`, `sessionSeed`) and the persisted turn counter.
- `model/` and `engine/` are **pure Kotlin** (no `android.*`, no `androidx.*`, no Room). Tests are JUnit4.
- Kotlin style: match surrounding code (4-space indent, trailing commas, no wildcard imports).
- Do **not** run Gradle from workstreams; the Lead runs build/tests after all files are in.
- Do **not** add new dependencies.

---

## 1. Assets (A produces; C loads)

```
app/src/main/assets/freechat/v1/en/
├── manifest.json
├── free_chat.json        # intents + match phrases + replies + chips + fallbacks + safety terms
├── species_voice.json    # generated identity atoms for catalog species (no prose)
└── database_fallback.json  # OPTIONAL licensed-dataset fallback pairs (last-resort only)
```

`manifest.json`:
```json
{ "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "files": { "intents": "free_chat.json", "speciesVoice": "species_voice.json",
             "databaseFallback": "database_fallback.json" } }
```

`database_fallback.json` (owner requirement 2026-11-18: generated Digimon content is the priority
answer source; raw public-database dialogue is an explicit fallback used only when generated content
has no answer; personal build only — `DISTRIBUTION_GATE.md` covers removal before distribution):
```json
{ "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "source": "dailydialog+cornell+oasst1 samples (personal build)",
  "pairs": [{ "user": "…", "response": "…", "source": "dailydialog", "license": "CC BY-NC-SA 4.0" }] }
```
The engine indexes these user phrases exactly like authors' match phrases, but consults the layer
strictly below every generated rung (see §2 rule 6).

`free_chat.json` (source of truth for authored/generated reply lines; keep line length ≤120 chars, 1–2 sentences):
```json
{ "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "intents": [{
    "id": "fc.greeting.1", "topic": "greeting", "tags": ["owned"],
    "matchPhrases": ["hi", "hello", "hey", "bom dia"],
    "keywords": ["hi", "hello"],
    "conditions": { "audience": "owned", "stageTiers": [], "types": [], "cores": [],
                    "moodBands": [], "recentEvents": [] },
    "replies": [{ "id": "fc.greeting.1.r1", "text": "You came back. Good. I was getting bored of the static here.",
                  "tone": "warm", "stageTiers": [], "types": [], "moodDelta": 0, "energy": 1,
                  "loreRefs": [], "authoringStatus": "draft" }],
    "chips": [{ "id": "chip.what_are_you", "text": "What are you?", "intentId": "fc.identity.1" }],
    "fallbackId": "fallback.2"
  }],
  "chips": [{ "id": "chip.what_are_you", "text": "What are you?", "intentId": "fc.identity.1" }],
  "fallbacks": [
    { "level": 1, "replies": [{ "id": "fallback.1.r1", "text": "...", "tone": null, "stageTiers": [], "types": [],
                                "moodDelta": 0, "energy": 0, "loreRefs": [], "authoringStatus": "draft" }] },
    { "level": 2, "replies": [] }, { "level": 3, "replies": [] }, { "level": 4, "replies": [] }
  ],
  "safety": { "S1": ["idiot", "stupid", "burro", "idiota"],
              "S2": ["sex", "nude", "matar", "kill you"],
              "S3": ["suicid", "me matar", "kill myself"],
              "PII": ["cpf", "address", "endereco", "phone"] } }
```

`species_voice.json`:
```json
{ "schemaVersion": 1, "voices": [
  { "speciesKey": "aegiomon", "speciesName": "Aegiomon", "level": "Adult", "type": "God Man",
    "moves": ["...", "..."], "tier": 2 } ] }
```
Generate one entry for every `species_catalog.json` species (1,662), from the catalog only (level, type,
specialMoves; no `profile` prose). Empty/missing fields are allowed.

A also delivers:
- `tools/freechat/build_freechat.py` — deterministic (sorted output, `sha256` only, no timestamps), stdlib only.
  Generates the three assets. Must support `--scale`/`--limit` (seed default) and optional
  `--seed-pairs <jsonl>` input (public-corpus turn pairs, used for **intent/phrase mining only**, never copied).
- `tools/freechat/data/*.json` — authored frame banks, intent match-phrase banks, fallback/handoff texts, safety
  terms (EN + basic PT terms), chips.
- `tools/freechat/seed_provenance.json` — every seed source name/license/usage recorded (for `DISTRIBUTION_GATE.md`).
- `tools/freechat/validate_freechat.py` — fails on: missing/extra manifest files, invalid JSON/schema, duplicate
  intent/reply ids, empty replies, duplicate reply texts, banned assistant phrases ("as an AI", "i'm here to help",
  "não tenho acesso", …), missing fallback levels 1–4, reply length >160 chars, missing `authoringStatus`,
  `loreRefs` that do not resolve to a catalog species key, unresolved `fallbackId`/`intentId` chip refs,
  intent coverage <60, reply records <1,000, match phrases <500, species_voice entries != 1,662.
- Seed corpus target for this pass: **≥120 intents, ≥1,200 replies, ≥800 match phrases, 1,662 species voices**,
  every reply `authoringStatus: "draft"` (owner approval later; do not claim reviewed).

Frame/render rule: generated lines are composed from authored frames + catalog fact atoms (species name/level/type/
move/tier). Never paste `species_catalog.json` `profile` sentences. Tone is **personality-dependent**: every reply
carries a `tone` from `warm|dry|courage|compassion|insight|friendship|playful|formal`; at least 6 tones across the pack.

---

## 2. Models + engines (B)

Package `com.tamerslab.digiline.freechat.model` — `FreeChatDtos.kt` (kotlinx.serialization):

```kotlin
@Serializable data class FreeChatManifestDto(val schemaVersion: Int = 1, val contentVersion: Int = 1,
    val locale: String = "en", val files: Map<String, String> = emptyMap())

@Serializable data class FreeChatConditionDto(val audience: String? = null,
    val stageTiers: List<Int> = emptyList(), val types: List<Int> = emptyList(),
    val cores: List<String> = emptyList(), val moodBands: List<String> = emptyList(),
    val recentEvents: List<String> = emptyList())

@Serializable data class FreeChatChipDto(val id: String, val text: String, val intentId: String? = null)

@Serializable data class FreeChatReplyDto(val id: String, val text: String, val tone: String? = null,
    val stageTiers: List<Int> = emptyList(), val types: List<Int> = emptyList(),
    val moodDelta: Int = 0, val energy: Int = 0, val loreRefs: List<String> = emptyList(),
    val authoringStatus: String = "draft")

@Serializable data class FreeChatIntentDto(val id: String, val topic: String = "general",
    val tags: List<String> = emptyList(), val matchPhrases: List<String> = emptyList(),
    val keywords: List<String> = emptyList(), val conditions: FreeChatConditionDto = FreeChatConditionDto(),
    val replies: List<FreeChatReplyDto> = emptyList(), val chips: List<FreeChatChipDto> = emptyList(),
    val fallbackId: String? = null)

@Serializable data class FreeChatFallbackDto(val level: Int, val replies: List<FreeChatReplyDto> = emptyList())

@Serializable data class FreeChatPackDto(val schemaVersion: Int = 1, val contentVersion: Int = 1,
    val locale: String = "en", val intents: List<FreeChatIntentDto> = emptyList(),
    val chips: List<FreeChatChipDto> = emptyList(), val fallbacks: List<FreeChatFallbackDto> = emptyList(),
    val safety: Map<String, List<String>> = emptyMap())

@Serializable data class SpeciesVoiceDto(val speciesKey: String, val speciesName: String = "",
    val level: String? = null, val type: String? = null, val moves: List<String> = emptyList(), val tier: Int = 2)

@Serializable data class SpeciesVoicePackDto(val schemaVersion: Int = 1, val voices: List<SpeciesVoiceDto> = emptyList())
```

`FreeChatModels.kt` (pure values):

```kotlin
enum class FreeChatAudience { OWNED, WILD }
enum class FreeChatMoodBand { LOW, SETTLED, POSITIVE;
    companion object { fun of(mood: Int): FreeChatMoodBand = when { mood < 35 -> LOW; mood < 70 -> SETTLED; else -> POSITIVE } } }
enum class FreeChatSafetyClass { SAFE, S1, S2, S3, PII }

data class FreeChatContext(
    val individualId: String, val locale: String = "en",
    val speciesKey: String? = null, val speciesName: String? = null, val level: String? = null,
    val speciesType: String? = null, val stageTier: Int = 2, val personalityType: Int = 1,
    val core: String = "PHILANTHROPY", val mood: Int = 50,
    val audience: FreeChatAudience = FreeChatAudience.OWNED,
    val recentEventKeys: Set<String> = emptySet(), val recentReplyIds: Set<String> = emptySet(),
    val turnCounter: Long = 0, val contentVersion: Int = 0,
) { val moodBand: FreeChatMoodBand get() = FreeChatMoodBand.of(mood) }

data class FreeChatChip(val id: String, val text: String)

data class FreeChatReplyResult(val replyId: String, val text: String, val intentId: String, val topic: String,
    val tone: String?, val moodDelta: Int, val scoreQ16: Int, val fallbackLevel: Int,
    val safetyClass: FreeChatSafetyClass, val chips: List<FreeChatChip>, val contentVersion: Int)

data class FreeChatContent(val manifest: FreeChatManifestDto, val pack: FreeChatPackDto,
    val speciesVoices: Map<String, SpeciesVoiceDto>, val speciesNames: List<String>,
    val contentVersion: Int, val locale: String)
```

Package `com.tamerslab.digiline.freechat.engine` (pure Kotlin, JUnit4 tests):

```kotlin
class FreeChatNormalizer { fun normalize(text: String): String }         // NFKD→strip accents→lowercase(ROOT),
                                                                          // slang map (en+pt), punctuation→space, collapse ws
data class FreeChatEntityMentions(val speciesKeys: Set<String>, val worldTerms: Set<String>,
    val levels: Set<String>)
class EntityLexicon(speciesNames: List<String>) {
    fun extract(normalizedText: String): FreeChatEntityMentions   // longest-match, ambiguous aliases ignored
}
data class ScoredIntent(val intent: FreeChatIntentDto, val scoreQ16: Int, val entityHits: Int)
class FreeChatRetriever(private val pack: FreeChatPackDto) {
    fun candidates(normalizedText: String, entities: FreeChatEntityMentions, limit: Int = 8): List<ScoredIntent>
}
class FreeChatSafetyFilter(private val safety: Map<String, List<String>>) {
    fun classify(normalizedText: String): FreeChatSafetyClass   // never echo input; S3/PII detected here
}
class FreeChatEngine(content: FreeChatContent) {
    fun reply(text: String, context: FreeChatContext): FreeChatReplyResult   // always returns a line
    fun suggestions(context: FreeChatContext, limit: Int = 3): List<FreeChatChip>
    fun classify(text: String): FreeChatSafetyClass
}
```

Engine rules (must be unit-tested):
1. Normalize → safety classify; S2/S3/PII still return an authored deflection reply but `safetyClass` is set.
2. Entity lexicon + exact normalized phrase match → retriever candidates; **confidence gate**: accept only if
   `scoreQ16 > 0` and best intent passes `minScore` (use `>= best/3` heuristic for MVP); else fallback ladder.
3. Hard context filters: audience, `stageTiers` (empty = any; else `context.stageTier in stageTiers`),
   `types` (empty = any; else `context.personalityType in types`), `moodBands` (empty = any).
4. Reply soft filter: `reply.stageTiers`/`reply.types` empty = any; exclude ids in `context.recentReplyIds`;
   same-personality/tone match scores higher.
5. Variant pick: deterministic `DigilineSeed.combine(DigilineSeed.stabileHash(normalized), DigilineSeed.stabileHash(context.individualId), context.turnCounter, context.contentVersion.toLong())` → index in filtered replies (seeded, not `Random`).
6. Fallback ladder (priority order): matched generated intent reply → the matched intent's explicit authored
   `fallbackId` → the **licensed database fallback layer** only when it genuinely matches (exact phrase or
   substantial content-token evidence; `fromDatabase = true`, `fallbackLevel = 4`, owned audience only) →
   **authored conversation-repair line** (`fallbackLevel = 3`, `topic = "repair"`) for input that matches nothing →
   composed species-voice line / authored deflection / generic confusion (only when the pack has no database
   layer) → internal constant. Random dataset quotes are never used: the first device playtest showed they produce
   nonsense. PT-BR input is aliased into the EN intent phrasing for matching only (`FreeChatPtBrAliases`), after
   safety classification; the transcript keeps the raw player text.
7. `moodDelta` from reply, clamped to `-1..+1` in the engine (repository enforces daily cap).
8. Empty pack / no replies at any level: return an internal constant line (never throw).

---

## 3. Content loader + data + gateway (C)

`content/FreeChatContentSource.kt`:
```kotlin
interface FreeChatContentSource { suspend fun load(root: String = "freechat/v1/en"): FreeChatContent }
```
`content/FreeChatContentLoader.kt`:
```kotlin
class FreeChatContentLoader(private val readAsset: (String) -> String) : FreeChatContentSource {
    override suspend fun load(root: String): FreeChatContent   // throws IllegalStateException("<file>: <reason>")
    companion object { fun fromAssets(assets: AssetManager): FreeChatContentLoader }
}
```
It reads `manifest.json`, then `free_chat.json` + `species_voice.json` with
`Json { ignoreUnknownKeys = true; isLenient = true }`, builds `FreeChatContent` (speciesNames = voices' speciesName),
and runs `FreeChatContentValidator` (spec below) in debug/`BuildConfig.DEBUG` — throw on errors.

`content/FreeChatContentProvider.kt`:
```kotlin
class FreeChatContentProvider(private val source: FreeChatContentSource) {
    fun observe(): Flow<Boolean> // optional; not required
    suspend fun get(): FreeChatContent?   // process cache; null on any failure (fail-open: feature hidden)
}
```
`content/FreeChatContentValidator.kt`: pure `fun validate(content: FreeChatContent): List<String>` returning
errors (ids unique, fallbacks 1–4 present, replies non-empty/≤160 chars, safety keys known, loreRefs known species
key when present, chips refs resolve, contentVersion > 0). Used by loader and tests.

`data/FreeChatEntities.kt` (Room v57; exact names):
```kotlin
@Entity(tableName = "FreeChatProfile")
data class FreeChatProfileEntity(
    @PrimaryKey val individualId: String, val turnCounter: Long = 0,
    val lastTalkAt: Long = 0, val lastMoodCreditDay: Long = 0,
    val moodCreditToday: Int = 0, val sessionCounter: Long = 0, val contentVersion: Int = 0)

@Entity(tableName = "FreeChatTurn", indices = [Index("individualId")])
data class FreeChatTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val individualId: String,
    val turnCounter: Long, val normalizedHash: Long, val intentId: String, val replyId: String,
    val variantSeed: Long, val scoreQ16: Int, val fallbackLevel: Int, val safetyClass: String,
    val createdAt: Long)
```
`data/FreeChatDao.kt`: `profile(id)`, `upsertProfile`, `insertTurn`, `recentTurns(id, limit=20)`, `clearTurns(id)`,
`clearProfile(id)`.
`data/FreeChatSchema.kt`: `object FreeChatSchema { fun migrate(db: SupportSQLiteDatabase) }` creating both tables
with `CREATE TABLE IF NOT EXISTS` (idempotent; mirror Room column types: TEXT for strings, INTEGER for numbers/booleans).
`AppDatabase.kt`: bump `version = 57`; add both entities to `entities = [...]`; add `abstract fun freeChatDao():
com.tamerslab.digiline.freechat.data.FreeChatDao`. (Lead registers the migration + schema JSON is generated by build.)

`data/FreeChatContextAssembler.kt`:
```kotlin
class FreeChatContextAssembler(private val database: AppDatabase,
    private val speciesRepository: SpeciesRepository) {
    suspend fun assemble(individualId: String, contentVersion: Int): FreeChatContext?
}
```
Resolve from cheap reads only: `userCharacterDao().getByIndividualId`, `digimonIndividualDao().getIndividual` /
`getPersonality`, `speciesRepository.getProfileForCharacter(charId)`, `digilineDao().profile` (turnCounter) —
**never** `getAllSpeciesEntries()`, never network, never the 13.3 MB catalog re-parse. Species key via
`DigilineStages.speciesKey(...)`, tier via `DigilineStages.tier(null, level)`, core via
`PersonalityShiftEngine.coreOf(type)`. `recentEventKeys` may be empty for MVP; `recentReplyIds` from last 8 turns.

`gateway/FreeChatGateway.kt`:
```kotlin
data class FreeChatSceneView(val lines: List<String> = emptyList(), val chips: List<FreeChatChip> = emptyList(),
    val closeReason: String? = null, val partnerTone: String? = null)
data class FreeChatResult(val scene: FreeChatSceneView? = null, val error: String? = null)

interface FreeChatGateway {
    fun isAvailable(): Boolean
    suspend fun open(characterId: Long): FreeChatResult     // greeting (persisted once per 12h) + chips
    suspend fun send(characterId: Long, text: String): FreeChatResult
    suspend fun clear(characterId: Long)
}
```
`gateway/ScriptedFreeChatGateway.kt`: injects `FreeChatRepository`-like dependencies or the repository itself.
`data/FreeChatRepository.kt`:
```kotlin
class FreeChatRepository(private val database: AppDatabase, private val content: FreeChatContentProvider,
    private val assembler: FreeChatContextAssembler, private val now: () -> Long = System::currentTimeMillis,
    private val contentEnabled: () -> Boolean = { true }) {
    suspend fun open(characterId: Long): FreeChatResult
    suspend fun send(characterId: Long, text: String): FreeChatResult
    suspend fun clear(characterId: Long)
    fun isAvailable(): Boolean
}
```
Behavior: resolve `individualId` from `userCharacterDao().getCharacter(characterId)`; if unavailable return error;
`content.get()` null or `contentEnabled()` false → error/closed. `open`: if `now - profile.lastTalkAt >= 12h`, choose
a greeting intent (`topic == "greeting"`), persist assistant row `kind = "freechat_assistant"` (role `assistant`,
`isRead = true`), update `lastTalkAt`; return chips from pack (fallback to any chips). `send`: clamp input to 160
chars; safety classify; build context (turn counter + 1); engine.reply; persist in a Room transaction:
`ChatMessageEntity(role="user", content=text, kind="freechat_user", isRead=true)` and
`ChatMessageEntity(role="assistant", content=reply.text, kind="freechat_assistant", isRead=true,
payloadJson = {"intentId","replyId","topic","fallbackLevel","safetyClass"})`; insert `FreeChatTurnEntity`; update
`FreeChatProfileEntity` (turnCounter++, lastTalkAt=now, mood credit: if session≥4 and moodCreditToday<2 and
lastMoodCreditDay != today → apply `moodDelta = +1` via `userCharacterDao().updateCharacter(copy(mood=...))` once,
bump counter; normal replies with `moodDelta` other than 0 are clamped to 0 except that session credit — MVP rule:
**only the session credit changes mood, never the line**); return scene with chips (and `closeReason = "SAFETY"`
for S2/S3/PII; S1 returns a boundary line). Never delete a contact, never write personality/shift tables.
`clear`: clear turns + profile + delete `ChatMessageEntity` rows with `kind LIKE 'freechat_%'` (use a ChatDao query
you add? Prefer new `@Query("DELETE FROM ChatMessageEntity WHERE individualId = :individualId AND kind LIKE 'freechat_%'")`
in `FreeChatDao` — allowed, cross-table query is fine).

---

## 4. Controller + UI + settings (D)

- `ChatScreenController` adds:
```kotlin
fun isFreeChatEnabled(): Boolean
fun openFreeChat(characterId: Long, onResult: (Result<FreeChatSceneView?>) -> Unit)
fun sendFreeText(characterId: Long, text: String, onResult: (Result<FreeChatSceneView?>) -> Unit)
fun clearFreeChat(characterId: Long, onDone: () -> Unit)
```
(controller may expose chips through the returned `FreeChatSceneView`).
- `ChatScreenControllerImpl` routes to `AppServices.freeChatGateway` (Lead adds it), not `ChatRepository`.
- `ChatScreen.kt` composer branch: `if (legacyLlmEnabled) legacy composer else if (freeChatEnabled && scene == null)
<free composer + chips>`; when a card scene exists, keep current card-only layout. On first show, call
`openFreeChat` once per screen entry; render returned greeting through the normal history Flow (repo persists it).
Chips render as a row of ≤3 small buttons above the composer; tapping a chip pre-fills/sends its text (send text
directly). Add a quiet "clear conversation" action in the existing long-press menu or a small text button; strings
required. Empty state uses `digiline_chat_empty`/`freechat_empty`.
- `DebugSettingsRepository`: add `freeChatKilled: Boolean = false` to `DebugSettings`; persisted key
  `debug_free_chat_killed`; methods `setFreeChatKilled(Boolean)` (requires master debug switch) and
  `val freeChatAvailable: Boolean get() = !freeChatKilled` (Lead combines with BuildConfig).
- Settings debug UI: add a "DigiLine free talk (off = kill switch)" toggle wired to the repository (follow the
  existing legacy LLM toggle pattern). If the toggle screen is too entangled, at minimum add the persisted switch +
  repository API and tell the Lead.
- Strings (all 3 locales; English copy is acceptable in pt/ja for this pass): `freechat_placeholder`,
  `freechat_send`, `freechat_empty`, `freechat_chips_label`, `freechat_unavailable`, `freechat_clear`,
  `freechat_boundary`, `freechat_closed_safety`, `freechat_clear_done`.

---

## 5. Lead integration

- `app/build.gradle.kts` defaultConfig: `buildConfigField("boolean", "FREE_CHAT_ENABLED", "true")`.
- `AppContainer`: `val freeChatGateway: com.tamerslab.digiline.freechat.gateway.FreeChatGateway`.
- `DefaultAppContainer`: build `FreeChatContentProvider(FreeChatContentLoader.fromAssets(context.assets))`,
  `FreeChatContextAssembler(db, speciesRepository)`, `FreeChatRepository(...)`, `ScriptedFreeChatGateway(...)`;
  `contentEnabled = { BuildConfig.FREE_CHAT_ENABLED && debugSettingsRepository.freeChatAvailable }`.
  Register `MIGRATION_56_57` with the other migrations.
- `AppServices`: expose `freeChatGateway`.
- `DISTRIBUTION_GATE.md` + docs updates + build/test/review.

---

## 6. Verification (Lead runs after all workstreams land)

```
python tools/freechat/build_freechat.py
python tools/freechat/validate_freechat.py
gradlew.bat :app:compileDebugKotlin
gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.tamerslab.digiline.freechat.*"
gradlew.bat :app:assembleDebug
```
A workstream must not run Gradle. If a signature conflict appears after merge, the Lead resolves it centrally.
