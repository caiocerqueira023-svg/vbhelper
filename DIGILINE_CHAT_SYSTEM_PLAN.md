# DigiLine v2 — Deterministic Chat, Questions & Wild Negotiation (LLM-free)

**Status:** implementation plan v1.1 — decisions locked 2026-11-13 (see §0.1)
**Scope:** Android app `TamersLab` (`app/`), DigiLine chat, partner social systems, wild contact systems
**Goal:** remove every runtime LLM dependency from gameplay dialogue and replace it with authored,
data-driven DigiLine content. LLM chat survives only as a debug-only, off-by-default toggle.
**Content inputs:** `digimon_chat_export/` (Time Stranger dialogue, Cyber Sleuth quiz, SMT V negotiation)
**Author:** Lead (plan), based on the current codebase as of 2026-11-13

---

## 0. Executive summary

The app currently generates its DigiLine conversations with an OpenAI-compatible LLM
(`OpenRouterClient` + `ChatRepository`), and the same LLM is also used for event reactions,
diaries, NPC lines, farm/radar social exchanges, item/adventure notices, quest-offer prose and
wild-contact conversations. This plan replaces **all** of those runtime paths with:

1. **Authored content packs** (versioned JSON assets, per locale, validated at build time).
2. **Deterministic engines** — `OwnedDigilineEngine`, `QuizEngine`, `PersonalityShiftEngine`,
   `NegotiationEngine`, `ReactionLineProvider`, `DiaryComposer` — all offline, seeded, save-safe.
3. **A new UI** on top of the existing DigiLine screens: option cards instead of a free-text box
   for authored flows; negotiation uses the same card surface with a phase/relationship header.

Feature behavior requested:

- **Owned Digimon** periodically ask a question (from time to time):
  - species questions are asked **only by that species**;
  - generic questions are assigned to one or more of the **16 personalities** and only those
    Digimon may ask them;
  - the chosen answer **shifts the Digimon's personality** on the 4-quadrant / 16-type grid.
- Owned Digimon periodically ask a **quiz**; correct answer raises **bond**, wrong answer lowers it.
- **Wild Digimon** use a rewritten, Digimon-lore negotiation (structure derived from SMT V):
  talk → probe → bargain (bits/items) → resolve. A successful negotiation makes the wild Digimon
  **join the DigiLine "Wild" tab as a contact** (not Storage). There, further talk must **convince
  them to give their recruitment quest**; completing that quest is still the only route to Storage.
- Negotiation dialogue is **gender-neutral**, divided by **personality** and **stage**, and
  harder/longer for higher stages. Wild Digimon ask for **bits/items** and **try to trick** you.
- LLM chat moves under **Settings → Debug → "Legacy LLM chat (debug)"**, default **off**.

---

## 0.1 Locked decisions (user answers, 2026-11-13)

These override the recommendations in the sections below. They are the implementation contract.
`Qn` labels in the first column refer to the numbered items of the 2026-11-13 decision questionnaire
(that table has been replaced by this log and the remaining items in §12.2).

| # | Question | Decision | Consequences |
|---|---|---|---|
| D1 | Content rights (Q4) | **Rewrite everything.** Exports are reference-only; no third-party line ships. | Content authoring cost is the critical path; lore citations mandatory. |
| D2 | Bond stat (Q1) | **Reuse `mood` as bond.** No new bond column. | Mood is the single owned relationship stat. It also moves with battles/injuries, so DigiLine deltas are rate-limited and documented in UI wording. |
| D3 | Shift ↔ battle (Q2) | **One identity:** a personality shift changes battle AI too. | `PersonalityBattleProfile` intentionally follows the shifted type; warn in docs/help. |
| D4 | Native saves (Q3) | **App-side shift must reach the game.** | Persist the shift and write the personality byte into the Time Stranger save (new validated `TimeStrangerEdits.setPersonality`); requires the verified native-id ↔ app-type map. |
| D5 | Content volume (Q14) | Assign each generic template to **2 types** + author extras + slower cadence. | MVP ≈ 8 questions/type; expansion afterwards. |
| D6 | Join semantics (Q5) | **No trust system anymore.** Join = Wild-tab contact; persuasion is authored, not trust-metered. | Remove trust as player progression/gating; replace with negotiation outcomes + persuasion state machine. |
| D7 | Recruit queue (Q6) | Keep one active recruitment quest + **queued "wants to travel with you" offers**. | New persisted pending-offer queue; UI shows deferred invitations. |
| D8 | Quiz losses (Q7) | **−5 mood (bond), floor 0.** | Risk accepted; mitigate with 1 quiz/individual/day, no repeat-loss stacking, no timer. |
| D9 | Locales (Q9) | **EN MVP**, locale-ready schema; pt-BR/ja later. | Content schema and validator are locale-parameterized from day 1. |
| D10 | Debug LLM (Q10) | **Sandbox:** legacy replies never change mood/trust/save state. | Legacy messages marked, excluded from progress accounting; optional second debug switch for delta parity if ever needed. |
| D11 | Joined farewell (F14) | **Negotiation outcomes may make the Digimon leave** for good, depending on how the talk goes. | Removal is authored per node (not passive mood drain); needs warning tells and clear UI. |
| D12 | Unknown stage (Q8) | **Prefer the DIM card / game-save level information** before catalog fallbacks. | New stage resolution order: card/DIM level → game-save level → catalog `level` → T2 fallback (flagged). |

**Interpretation notes to confirm while implementing:**

- "No trust system anymore" is read as removing `WildRelationship.trust` as the player-facing
  progression/gating meter (contact unlock, recruitment-quest unlock, wild chat delta). The
  wild-vs-wild ecosystem affinity (`WildPairBond.affinity`) is a separate simulation system and is
  not removed by this decision. If the intent is to remove that too, say so before Phase 4.
- "If it shifts on app it shifts on game" is implemented as: app shift persists immediately; a
  pending native-sync record writes the mapped personality byte into the game save through the
  existing guarded save-edit pipeline (decode → single-field edit → re-encode → verify → caller
  journal/backup/readback). It cannot apply while the record is in `FARM` (outside the editable
  scope) or if the native-id mapping is unverified; the UI shows the sync state.

## 0.2 Second decision batch (user answers, 2026-11-13)

| # | Question | Decision | Consequences |
|---|---|---|---|
| R1 | Trust removal scope | **Remove player-facing wild trust only.** Keep wild-vs-wild `WildPairBond.affinity` simulation. | D6 confirmed; ecosystem content unchanged. |
| R2 | Native sync UX | **Automatic on the next save write/export.** | No separate "Sync to game" action; the existing guarded save-write flow performs the personality edit, with backup/journal/readback and a visible result. |
| R3 | Sync failure | **Keep app value, show pending, retry later.** | `NativePersonalitySyncEntity.state` drives the partner UI; retry when the record is editable/mapping verified. |
| R4 | Battle AI change notice | **One-time notice + help entry.** | First shift shows a dialog; battle/profile effects documented in help. |
| R5 | Mood daily cap | **No cap.** | Quiz/question deltas stack with battle mood changes; D8 risk accepted. Mitigate only with quiz cadence (1/day) and 30-day no-repeat. |
| R6 | Departure strictness | **Max once per contact, never first conversation, clear tell.** | `DEPART` nodes: one per contact, tell + choice before it; logged. |
| R7 | Persuasion arc | **Authored 3–6 conversations per personality/tier.** | No fixed universal count; content defines. |
| R8 | Species age variants | **Choose a variant per individual (custom answer).** | Keep the 4 variant lines in the pack; each individual gets a stable, deterministic variant (selected at first question, persisted), not one shared line per species. |
| R9 | Old chat history | **Interactive only in debug mode.** | Normal mode: read-only archive; the legacy LLM toggle enables interaction. |
| R10 | Authoring assistance | **Offline LLM-assisted drafts allowed with human review + citations.** | No runtime calls; citation validator still blocks unsourced lore. |
| R11 | Content owner | **Skipped — unresolved.** | Do not block code; content volume/owner must be settled before Phase 6. |
| R12 | Queued quest stage | **Stage at offer time.** | Matches current `QuestRepository` behavior. |
| — | Next step directive | **Implement all phases in one continuous pass; run build/test only after everything is implemented.** | Implementation order (content→engines→schema→UI→wild→LLM removal→native sync) is internal; no intermediate builds per instruction. |

---

## 1. Goals and non-goals

### Goals

1. Zero runtime LLM calls in normal gameplay (owned chat, wild chat, reactions, diary, NPCs,
   farm/radar social, adventure/items, quest offers).
2. Full determinism: same save + same action sequence ⇒ same results; no save-scum rerolls.
3. Rule-driven personality change and bond change with explicit caps, cooldowns and hysteresis.
4. Wild negotiation integrated with the **existing** wild relationship/recruitment state machine
   (trust, contact unlock, recruitment quest, Storage), not a parallel economy.
5. Content pipeline that can grow (content packs versioned, validated, localized en/pt-BR/ja).
6. Remotely killable legacy LLM path in one release (feature flag, no data dependency).

### Non-goals (this plan)

- New battle systems, evolution rules or quest economy rebalance (only the minimum hooks needed).
- Voice acting/audio, Live2D, or a general visual-novel script format.
- Multiplayer/companion features (but the INTERNET permission audit must not break them).
- Authoring 16-personality content for every Digimon at once — see phased content scope §10.

---

## 2. Current state audit (facts from the repo)

### 2.1 Platform

| Item | Fact |
|---|---|
| Stack | Android, Kotlin, Jetpack Compose, Room, Koin, protobuf in some modules |
| DB | `AppDatabase` version **55** (`app/src/main/java/com/tamerslab/data/database/AppDatabase.kt`) |
| Locales | `values` (en), `values-pt-rBR`, `values-ja` |
| Species catalog | `app/src/main/assets/species_catalog.json` — **1,662** species (`id`, `name`, `aliases`, `level`, `type`, `profile`, `specialMoves`) |
| Already bundled dialogue | `app/src/main/assets/species_chat_examples.json` — **268** species chats (`characterId`, `speciesName`, `opening`, 4 × `{tamer, digimon}` exchanges). `source` = "Digimon Story: Time Stranger - Unique Digimon Chats". **267/267 openings match the DCO export in `digimon_chat_export/digimon_chat.json`** → the app already ships the species chat content that the LLM currently reads as prompt examples. |

### 2.2 Existing DigiLine UI and chat

| Item | Fact |
|---|---|
| Route/screen | `AppRoute.Chat(characterId)` → `chat/ui/ChatScreen.kt`; `DigilineScreen` has tabs **Storage** and **Wild** (`farm/ui/digiline/DigilineScreen.kt`) |
| Owned chat | free-text composer → `ChatScreenControllerImpl.sendMessage` → `ChatRepository.sendMessage` → LLM |
| Wild chat | `AppRoute.WildContact` → `world/ui/WorldChatScreen.kt` → `WorldChatScreenControllerImpl.sendMessage` → `ChatRepository.sendMessageForWildEncounter` → LLM (reply + `moodDelta` + optional quest action) |
| Transcript storage | `ChatMessageEntity(id, individualId, role, content, timestamp, isRead)` — no structured payload yet |
| Unread/preview | home/partner deck reads latest assistant message; DigiLine list previews use `ChatMessageEntity` content |

### 2.3 Personality system (already matches the request)

- `domain/personality/DigimonPersonalityTraits.kt`:
  - 16 persisted types (`DigimonPersonalityType`, `number` 1..16):
    ADORING, DEVOTED, TOLERANT, OVERPROTECTIVE, ZEALOUS, BRAVE, RECKLESS, DARING,
    ENLIGHTENED, SLY, ASTUTE, STRATEGIC, OPPORTUNISTIC, FRIENDLY, SOCIABLE, COMPASSIONATE.
  - Persisted per individual in `DigimonPersonalityTraits` (`individualId`, `personalityType`,
    `generatedAt`, `systemVersion = 3`). Generator weights types by attribute + stage.
- `battle/offline/core/PersonalityBattleProfile.kt` derives **4 cores** from the 16 types:
  - 1–4 → PHILANTHROPY (love), 5–8 → VALOR (courage), 9–12 → WISDOM, 13–16 → AMICABILITY (friendship).
  - The same 4 cores map to the Time Stranger quadrants used by the DCO generic chats
    (`love`, `courage`, `knowledge`, `friendship`).
- Wild Digimon get a personality lazily through `WorldSocialRepository.profile(id)` — generated
  deterministically from the wild's attribute/stage and persisted in `DigimonPersonalityTraits`.
  **Wild negotiation can therefore use the same 16-type personality today.**
- No personality-change API exists yet: types are generated once (or imported from native saves).

### 2.4 Relationship stats that already exist

| Stat | Where | Range / behavior |
|---|---|---|
| Owned mood | `UserCharacter.mood`; updated via `UserCharacterDao` | 0–100; currently moved by LLM reactions |
| Owned chat history | `ChatMessageEntity` | used for preview/unread |
| Wild trust | `WildRelationship.trust` (default 50) | 0–100; `updateTrust` **auto-unlocks contact at trust > 75** |
| Wild mood | `WorldSpawn.mood` | 0 = farewell/leaves; ≥100 = recruitment quest offered |
| Wild contact | `WildRelationship.contactUnlockedAt` + `recruitmentState` (WILD / PENDING_RECRUITMENT / RECRUITED) | Wild tab lists contacts with `contactUnlockedAt != null` and `recruitmentState != RECRUITED` |
| Recruit quest | `QuestRepository` creates a `RECRUITMENT` quest when `trust >= 100` or `PENDING_RECRUITMENT`; **only one active recruitment quest is allowed**, and the Digimon only enters Storage after the quest is completed |

**Decision D2:** owned `mood` is used as the bond stat. The Time Stranger save also contains a
native bonded value (`Float` at `nameOffset + 0x13C`, read as `bondRaw`) and a native personality
byte (`nameOffset + 0xEE`, 1..16); the plan does not write native bond unless asked, but D4
requires writing the personality byte on shift.

**Decision D6:** wild `trust` stops being player progression. The column stays in the schema for
migration/legacy reads only; contact unlock and the recruitment-quest gate move to negotiation
`JOIN` + persuasion state (§7).

### 2.5 LLM touchpoints that must be replaced (not just chat!)

| # | Surface | Current path (files) | User-visible effect today |
|---|---|---|---|
| 1 | Owned chat | `ChatRepository.sendMessage/resendMessage` → `OpenRouterClient`, `DigimonPersonaBuilder`, `PromptLocalization`, lorebook, `BattleTalkDetector` | free-form partner conversation, mood changes |
| 2 | Wild chat | `ChatRepository.sendMessageForWildEncounter` | free-form talk; reply + mood/trust delta; quest action |
| 3 | Wild event reactions | `triggerReactionForWildEncounter`, `reactToPendingBattles`, `reactToPendingQuestOffers` | encounter/battle/quest messages |
| 4 | Owned event reactions | `ChatRepository.triggerReaction` from `ReactionRepository`, `DigimonDiaryService`, `AdventureScreenControllerImpl`, `ItemsScreenControllerImpl`, `DigimonReactionEngine` callers | battle/evolution/injury/item/adventure messages |
| 5 | Diary | `DigimonDiaryService` → `PromptLocalization.diaryPrompt` + LLM | diary entries |
| 6 | NPC dialogue | `NpcDialogueService` | city NPC lines |
| 7 | Farm social | `FarmConversationOrchestrator.generateFarmReply` | farm residents chatting |
| 8 | Radar/ecosystem social | `ChatRepository.generateRadarExchange` + `WorldInteractionOrchestrator` openings | wild-vs-wild and wild-to-player exchanges |
| 9 | Quest offer prose | `reactToPendingQuestOffers` (LLM) | text that offers a generated quest |
| 10 | Legacy plumbing | `OpenRouterClient/Service/Models`, `ChatApiProvider`, `BattleTalkDetector`, `LlmSettingsRepository`, `settings/ui/dialogs/LlmSettingsDialog.kt`, `PromptTemplateDialog`, `assets/species_chat_examples.json` (prompt examples), `INTERNET` permission | settings, keys, prompt templates |

Deterministic fallbacks already exist in places (`publicDialogueAvailable()` checks,
`WorldDialogueCodec.unreadableReply`, `SocialEventAppraisal`) — the new system must make these
the **primary** path and delete the LLM branch from normal play.

### 2.6 Deterministic building blocks to reuse

- `domain/personality/SocialEventAppraisal.kt` — classifies a turn (PRAISE/CARE/CHALLENGE/TEASING/
  REFUSAL/INSULT/AGREEMENT) and resolves a bounded trust delta; works with all 16 profiles.
- `domain/personality/DigimonSocialProfile.kt` — semantic trait vector per type.
- `world/ecosystem/WorldDialogue.kt` + `WorldDialoguePolicy.kt` — existing consent/validation
  pattern for deterministic wild conversations (intents, challenges, `socialDelta`).
- `PromptLocalization` event strings — already-localized templates for battle, injury, evolution,
  milestones; can be promoted from LLM instructions to displayed lines.
- `WorldSocialPlanner` / `EcosystemSeed` — seeded selection pattern to copy for deterministic RNG.
- `QuestRepository` recruitment pipeline, `WildRelationshipDao`, DigiLine Wild tab, `DigiLinePin`.

---

## 3. Design principles

1. **Offline by default.** No HTTP in any DigiLine/social/quest path. No API key required.
2. **Deterministic.** Every selection/branch is seeded by persisted values
   (`individualId`, `contentPackVersion`, `dayIndex`, `sessionCounter`); seeds are stored before
   the content is shown so force-closing cannot reroll.
3. **Content is data, not code.** JSON packs, validated at build time and at load; content can be
   patched independently of app releases.
4. **Rules are explicit and testable.** Personality shift, mood-as-bond, persuasion and demand math live in
   pure functions with unit tests; no hidden randomness.
5. **Never break existing invariants.** Storage entry still requires the recruitment quest; wild
   contact unlock now happens on negotiation `JOIN` (D6); the one-active-recruitment-quest rule is
   respected; joined-contact removal is only an authored `DEPART` outcome (D11).
6. **Locale-safe and neutral.** Authored per locale; en first with pt-BR/ja in the same schema.
   Gender-neutral language is a lint rule, not a one-off review.
7. **Lore-grounded.** Any factual Digimon claim must cite wikimon or digimon.net via a lore
   registry entry; no invented lore. Structural references from SMT V are never shipped as text.
8. **Legacy LLM is quarantined.** Debug-only, clearly labelled, no data path in normal play.

---

## 4. Target architecture

### 4.1 Package layout (new code; `chat/` becomes legacy)

```
com.tamerslab.digiline
├── content/            ContentLoader, ContentPack, ContentValidator, LocaleResolver
├── model/              QuestionCard, AnswerOption, ShiftVector, QuizCard, NegotiationNode,
│                       NegotiationSession, Outcome, ReactionLine, DiaryTemplate
├── engine/             OwnedDigilineEngine, QuestionScheduler, QuizEngine,
│                       PersonalityShiftEngine, NegotiationEngine, PersuasionEngine,
│                       ReactionLineProvider, DiaryComposer
├── data/               DigilineRepository, DigilineDao, Entities, content pack DAO
└── ui/                 DigilineSceneScreen (card renderer), NegotiationHeader, cards/*
```

- Legacy `com.tamerslab.chat` stays for the debug toggle only; nothing else may import it.
- Koin wiring: `DefaultAppContainer` gains `contentRepository`, `digilineRepository`,
  `ownedDigilineEngine`, `negotiationEngine`, `legacyLlmGateway` (debug-gated). `AppServices`
  exposes the new engines; existing controllers delegate to a `DigilineGateway` façade.

### 4.2 Gateways and engines

```kotlin
interface DigilineGateway {
    fun observeConversation(individualId: String): Flow<List<DigilineMessage>>
    suspend fun open(individualId: String): DigilineResult      // resume or start scene
    suspend fun answer(individualId: String, optionId: String): DigilineResult
    suspend fun dismiss(individualId: String): DigilineResult
}
```

- `ScriptedDigilineGateway` (default): routes to `OwnedDigilineEngine` (owned) or
  `NegotiationEngine`/`PersuasionEngine` (wild contacts), plus `QuizEngine`/`ReactionLineProvider`.
- `LegacyLlmGateway` (debug only): wraps `ChatRepository`; enabled only when
  `DebugSettings.legacyLlmChat == true` AND `DebugSettings.enabled == true`.
- `ChatScreenControllerImpl`/`WorldChatScreenControllerImpl` switch to the gateway; no direct
  `ChatRepository` usage outside the legacy gateway.
- Pure decision modules (unit-testable, no Android):
  - `QuestionScheduler.select(context): QuestionKey?`
  - `PersonalityShiftEngine.apply(current, option.shift): ShiftResult`
  - `QuizEngine.resolve(quizId, optionIndex): QuizResult`
  - `NegotiationEngine.step(session, optionId): NegotiationEvent`
  - `DemandResolver` (bits/items affordability + caps)

### 4.3 Content packs (assets)

```
assets/digiline/v1/
├── manifest.json                       # pack version, schema version, hashes, locale list
├── en/  pt-BR/  ja/
│   ├── species_questions.json          # per-species question set (from DCO consolidation)
│   ├── generic_questions.json          # generic question bank keyed by personality type
│   ├── quiz.json                       # quiz bank (stage-tagged, lore-tagged)
│   ├── negotiation.json                # negotiation trees keyed by personality + stage tier
│   ├── persuasion.json                 # post-join wild contact topics
│   ├── event_lines.json                # battle/evolution/quest/item/diary reactions
│   └── lore_species.json               # canonical facts + citations (also used by validators)
└── mapping/
    ├── species_alias_map.json          # export/catalog/legacy characterId → catalog id
    └── personality_tone_map.json       # SMT tone → 16-type mapping (used at build time only)
```

Pack rules:

- `schemaVersion` integer; loader refuses unknown major versions (release: fallback to bundled
  last-known-good pack; debug: loud failure).
- Content is loaded on `Dispatchers.IO` at first use, validated, indexed by
  `speciesId`/`personalityId`/`tier`; target ≤ 250 ms cold load, ≤ 2 MB per locale.
- Build step (`tools/content/`) generates packs from the reference sources into the assets; the
  raw exports in `digimon_chat_export/` are **not** shipped.

### 4.4 Room schema (v55 → v56)

New tables (names indicative; final naming follows `AppDatabase` conventions):

| Table | Purpose | Key columns |
|---|---|---|
| `DigilineProfileEntity` | long-term owned relationship + cadence | `individualId` PK, `lastAskedAt`, `lastQuizAt`, `lastShiftAt`, `sessionCounter`, `contentVersion` (no bond column: D2) |
| `DigilineQuestionRecord` | every question shown/answered | id, `individualId`, `questionKey`, `kind` (SPECIES/GENERIC), `askedAt`, `answeredAt?`, `optionId?`, `shiftJson?`, `moodDelta`, `status` |
| `DigilineQuizRecord` | quiz attempts | id, `individualId`, `quizKey`, `chosenIndex`, `correct`, `moodDelta`, `answeredAt` |
| `PersonalityTendencyEntity` | shift accumulator | `individualId` + `typeNumber` composite PK, `points`, `updatedAt` |
| `NativePersonalitySyncEntity` | app→game personality propagation (D4) | `individualId` PK, `targetType`, `targetNativeId` 1..16, `state` (PENDING / WRITTEN / UNAVAILABLE_FARM / UNVERIFIED_MAPPING), `updatedAt` |
| `NegotiationSessionEntity` | active/resumable wild negotiation | `wildIndividualId` PK, `tier`, `nodeId`, `phase`, `seed`, `demandsJson`, `startedAt`, `updatedAt`, `state` |
| `NegotiationLogEntity` | audit/anti-scum/trick fairness | id, `wildIndividualId`, `nodeId`, `optionId`, `effectsJson`, `createdAt` |
| `WildPersuasionEntity` | trust replacement for joined contacts (D6) | `wildIndividualId` PK, `stageIndex`, `topicsDoneJson`, `questOfferState` (NONE/PENDING/OFFERED/DECLINED/ACCEPTED), `joinedAt`, `lastTalkAt`, `departureState` |
| `DigilineEventRecord` | reaction/diary delivery dedupe | id, `individualId`, `eventKey`, `deliveredAt` |

Alterations:

- `ChatMessageEntity` + `kind TEXT?`, `payloadJson TEXT?` (renders cards in transcript);
  `role` stays for compatibility (`user` = chosen option text, `assistant` = narration/dialogue).
- `WildRelationship`: `trust` becomes **legacy/unused** (kept for migration; no reads/writes in
  player progression per D6); add `joinedAt: Long?`, `recruitQuestOfferedAt: Long?`,
  `lastPersuadeAt: Long?`. Contact unlock now happens on negotiation `JOIN`.
- `DigimonPersonalityTraits`: shifts update `personalityType` in place (D3), so bump
  `CURRENT_PERSONALITY_SYSTEM_VERSION` → 4 with a migration note; never silently re-roll imported
  values. A shift also enqueues `NativePersonalitySyncEntity` (D4).
- Migration tests: v55 fixture → v56 open → insert/read/delete all new tables; verify old chat
  history still lists/previews; verify wild contacts/recruitment state untouched; legacy `trust`
  values remain readable but do not gate anything.

### 4.5 UI changes

| Screen | Change |
|---|---|
| DigiLine list (Storage tab) | Row badge "has something to ask" when a question session is pending; preview = last scene line |
| Chat / scene screen | Replaces free-text field with card area:
  - **question card**: demon line + 2–4 option buttons + "not now" (always present);
  - **quiz card**: demon line + A/B/C buttons + optional hint/reward preview;
  - **reaction card**: narration/dialogue + continue;
  - history renders past exchanges as message rows (chosen option shown as the player's message), with card payload kept for replay/review |
| Wild tab | Status chip: `NEW` (contact unlocked, negotiation available), `TALKING`, `QUEST AVAILABLE`, `QUEST ACTIVE`; last line preview |
| Wild contact screen | Same scene surface with a phase header (Approach/Probe/Bargain/Resolve), an authored persuasion-stage label (no trust meter, D6), a demand card ("wants 120 Bits", "wants 2× [item]"), and the native personality-sync state ("synced with game" / "not written") when relevant |
| Settings | `Settings → Debug` gains:
  - `Legacy LLM chat (debug)` (switch, off by default, requires Debug master switch);
  - existing LLM config dialog moves under Debug and is only reachable when the switch is on;
  - copy: "Legacy mode uses an external AI service and is not part of normal play." |
| Home/PartnerDeck | unchanged previews; new pending-question indicator only |

Accessibility: option buttons ≥ 48 dp, content descriptions, no reliance on color for
phase/feedback; quiz answers not timed; romanized/localized names.

### 4.6 Debug LLM toggle semantics

- **Off (default, release):** `DigilineGateway` = scripted only; LLM settings are not read; no
  network calls; API-key dialog hidden; existing LLM API keys remain stored but unused.
- **On (debug only):** the legacy free-text path is available for comparison/testing:
  - a persistent "LEGACY LLM" badge appears on chat screens;
  - messages are stored with `kind = "legacy_llm"` and excluded from scripted progress/anti-farm
    accounting;
  - **D10 (decided):** legacy replies are sandboxed — no mood change, no state change, no save
    effect; the transcript is marked legacy and excluded from progress accounting. An optional
    second debug-only switch could allow delta parity if QA ever needs it.
- Release builds must pass a CI gate: no reference to `OpenRouterClient`/`ChatApiProvider` outside
  `chat/legacy/` (R8/keep-rule check) and no `INTERNET`-requiring chat call sites in normal flows.

---

## 5. Content pipeline from `digimon_chat_export/`

### 5.1 Source inventory (what exists)

| File | Content | Quality notes |
|---|---|---|
| `digimon_chat.json` | 5,630 rows: 268 species chats (9 lines each = 2,412) + 64 generic templates (50 rows each = 3,200) + 18 system lines | Already mirrored by the bundled `species_chat_examples.json` for the 268 species part (267/267 openings match). Generic part is not bundled. 5 blank text rows, 47 blank/duplicate lines in raw sheet. |
| `digimon_chat_conversations.json` | Same rows grouped into 335 conversations | Structure reference for the card builder |
| `quiz_time_digimon_questions.json` | 94 Digimon quiz questions (54 Cyber Sleuth + 40 Hacker's Memory), A/B/C, correct answer | Too few for long play; needs expansion; answers are game-derived text. |
| `demon_negotiation_dialogue_smt5.json` | 15 tones / 215 topics / 1,126 steps of SMT V negotiation | **Structure reference only** — all text must be rewritten. 47 unresolved choices (wiki gaps), 8 synthetic moon topics, 11 quiz prompts. |
| `README.md` | schemas/statistics | — |

### 5.2 Reference → shipped pack transformations

| Target pack | Source | Transformation |
|---|---|---|
| `species_questions.json` | bundled `species_chat_examples.json` (authoritative; same as DCO species chats) | Convert each of the 268 entries into a question card: `opening` = demon prompt; 4 `exchanges.tamer` = options; matching `exchanges.digimon` = reactions; add `speciesId`, `stageTier`, `shift` vectors, `moodDelta`, `tags`. Keep the four age variants in the pack and assign a **stable variant per individual** (R8), selected deterministically at first use and persisted. Normalize names; add manual alias map for the 23 currently unmatched species (`AEGIOCHUSMON_HOLLY`, `CHRONOMON`, `ROSEMON_BM`, `MEGAGROWLMON`, …). |
| `generic_questions.json` | DCO `common001…common064` rows | Each template becomes a question. Use the 4 `core` variants (`courage/love/friendship/knowledge`) as the **flavor** matched to the asker's core; the 4 age slots are duplicates (only 5 groups differ) and collapse to one line. Author asker assignments (one or more of the 16 types) and answer shift vectors. |
| `quiz.json` | Cyber Sleuth quiz JSON + new authored questions | Keep 94, add stage/difficulty/lore tags, then expand to target (see §6.5). All answers verified against the cited lore registry. |
| `negotiation.json` | SMT V JSON structure | Keep the tone→topic→node shape, **rewrite every line** for Digimon lore and neutral language; map source tones to 16 personalities; gate topics by stage tier; add demand/trick nodes and outcomes bound to the app's wild systems. |
| `event_lines.json` | `PromptLocalization` templates + authored variants | Convert prompt-only strings into displayed, localized lines by personality and event; add variants so repeats are rare. |
| `lore_species.json` | wikimon + digimon.net | Canonical facts (level, attribute, type, field, signature moves, evolution notes) with `sourceUrl` + `retrievedAt` per claim; used by content validators and by authors. |

### 5.3 Mapping the 16 personalities to the sources

- **Generic questions:** assign each of the 64 templates to one or more of the 16 types.
  Coverage rule: every type must have **≥ 8 generic questions** (see §6.4), so the current
  64 templates are insufficient as a 1:1 map; plan either (a) assign each template to 2 types
  (128 assignments → 8 per type) or (b) author ~32 extra templates. Recommended: (a) for MVP
  plus (b) as content expansion.
- **SMT tones → 16 types (proposed, must be reviewed):**

| SMT V tone | Proposed Digimon personality |
|---|---|
| Monster | RECKLESS |
| Tough | BRAVE |
| Beast | ZEALOUS |
| Cute (Male) | ADORING |
| Brute | DARING |
| Old Man | TOLERANT |
| Rookie | SOCIABLE |
| Noble | STRATEGIC |
| Pompous | OVERPROTECTIVE (re-authored as "proud guardian") or ASTUTE — decision needed |
| Wise | ENLIGHTENED |
| Jack Bros | SLY |
| Female Cute | FRIENDLY |
| Peppy | COMPASSIONATE |
| Fatale | OPPORTUNISTIC |
| Tutorial Pixie | DEVOTED |
| **unassigned** | ASTUTE (or Pompous as above) |

  15 source tones for 16 types means at least one personality needs a **new authored tone set**
  (see flaw F7). The mapping is a build-time input only; shipped text is new.

### 5.4 Lore rules (hard requirements)

1. Every factual claim about a Digimon (stage, attribute, type, move, origin, relationship) must
   cite `wikimon.net` or `digimon.net` (or an official Bandai/Toei source) in `lore_species.json`.
2. No invented lore, no SMT terminology (`Demon`, `Macca`, `Netherworld`, `Negotiation`, `Tamer`
   is fine) in shipped Digimon lines; validators reject banned terms.
3. Species-specific questions may reference the species' cited facts only; generic questions must
   not assert species facts at all.
4. Quiz answers must be verifiable from a cited source; ambiguous/unverifiable questions are cut.
5. Content review checklist per line: locale grammar, neutral gender, citation present (if
   factual), personality match, stage match, no spoilers beyond the player's progress.

### 5.5 Localization and gender neutrality

- Authoring source: English. Translation packs for `pt-BR` and `ja` mirror the same IDs.
- Style guide per locale:
  - **en:** second person, no gendered pronouns for the player/Digimon; avoid "he/she".
  - **pt-BR:** avoid gendered adjectives/nouns for the player/Digimon; prefer neutral
    constructions ("você", "essa pessoa", "quem está ao seu lado"); never assume masculine/feminine
    Tamer; keep "Tamer" as a proper title; avoid "obrigado/obrigada" (use neutral thanks).
  - **ja:** neutral first/second person (no 俺/あたし; prefer 私/あなた or role titles), no
    gendered sentence-final particles that imply gender; honorifics by relationship, not gender.
- Validation: same key set across locales, no missing/extra keys, length budget per card,
  banned-word/grammar lint (pt: `ele/ela`, `obrigado(a)`, gendered adjectives list; ja: gendered
  pronoun list), and a Japanese/Portuguese native review gate before release.
- Fallback: missing translation falls back to English for that card and is reported by the
  validator (release blocks if > 0 missing keys in shipped locales).

---

## 6. Owned Digimon DigiLine rules

### 6.1 When questions happen ("from time to time")

- Trigger sources (non-modal, queued; never during battle, negotiation, or active quest turn-in):
  - app open → Home/DigiLine once per session;
  - returning to Home/Farm after a battle or quest;
  - opening DigiLine Storage.
- Caps: per individual ≤ 1 authored question per **12 h** logical time; global ≤ 2 per session;
  quiz ≤ 1 per individual per day and ≤ 1 per session globally; skip if a question is already
  pending for that individual.
- Deterministic seed: `seed = hash(individualId, dayIndex, sessionCounter, packVersion)`; the
  selected question and its shuffled options are persisted before display (anti-reroll).
- Selection priority: species question (if eligible and not recently used) → quiz (if due) →
  generic question for current personality → authored fallback reaction.
- Pending state survives app restart; notification/badge on DigiLine list row.

### 6.2 Question flow and state machine

```
IDLE → SELECTED(persisted) → SHOWN → ANSWERED → REACTION(closes) 
                              ↘ DISMISSED (closes, small/no bond change, cooldown)
```

- Answer options: 2–4 authored options, always including a neutral "Not now"/"Later" that never
  shifts personality and applies at most a small bond change.
- After answering: one reaction line (personality/context aware), optional short narration; the
  card closes; the individual's cooldowns update.
- Transcript rows: the chosen option becomes the player message; the question/reaction becomes
  assistant messages with card payload for history rendering.

### 6.3 Answer → personality shift (16-type grid)

- Each option carries a `shift` vector authored against personality **types** (not cores), e.g.
  `{"BRAVE": +2, "SLY": +1}` (final schema uses numeric type ids). The engine:
  1. adds points to `PersonalityTendencyEntity`;
  2. applies decay: −10 % of points per day (rounded down), floor 0;
  3. caps each type at +20;
  4. shifts the persisted `DigimonPersonalityTraits.personalityType` when a **different** type
     leads the current type by ≥ 4 points **and** ≥ 2 questions were answered since the last
     shift **and** the last shift is ≥ 24 h old (hysteresis + cooldown);
  5. resets/keeps points according to the documented rule (proposal: subtract the current type's
     points by the lead value rather than zeroing all, to avoid ping-pong).
- Consequences and guards:
  - every option must map to at least one type; the UI never shows the numeric shift;
  - the four quadrants (cores) remain derived from the type number — no separate quadrant state;
  - reachability test: from any type, ≥ 3 authored questions must permit moving to each other
    type's quadrant; validator enforces coverage;
  - **battle behavior (D3):** `PersonalityBattleProfile` is derived from the type, so a shift is
    intended to change battle AI tendencies. Surface this in help text; never shift during battle;
  - **native propagation (D4/R2/R3):** after persisting a shift, enqueue
    `NativePersonalitySyncEntity`. The native write is a new validated
    `TimeStrangerEdits.setPersonality(sourceBytes, recordKey, nativePersonalityId)` that mirrors
    the existing rename pattern: decode → write the byte at `nameOffset + 0xEE` (1..16) → re-encode →
    verify every byte outside the edited field is unchanged → caller keeps the journal/backup/
    readback duties. It runs **automatically on the next save write/export** (no separate sync
    action). Requirements: a verified bidirectional mapping (`native personality id 1..16 ↕
    `DigimonPersonalityType`; do not assume equal numbers), only active party/bank records, never
    `FARM` records; show sync state (synced / pending / unavailable) in the partner UI. If the
    mapping or record scope cannot be satisfied, keep the app value and surface "not written to
    the game" instead of silently diverging;
  - `CURRENT_PERSONALITY_SYSTEM_VERSION` bumps to 4 because the value becomes mutable.
- UI: qualitative feedback only ("Something about them feels braver today…" style), no raw
  numbers; a Debug screen can expose points.

### 6.4 Species vs. generic gating

- **Species questions** are asked only by individuals whose species matches the card's
  `speciesKey`. If duplicates of the same species exist, each individual has its own history, but
  the same question won't be asked twice in the same 24 h across individuals (avoid spam).
- **Generic questions** carry `askerTypes: [typeId…]`; only individuals with a matching current
  type may ask them. Because personality shifts, a question can become available/unavailable —
  the pool is evaluated at selection time.
- Every owned individual always has a safe fallback pool (current-type generic + shared neutral
  lines) so a bad mapping cannot leave a Digimon silent.
- Coverage targets (blocking for content sign-off):
  - each of the 16 types: ≥ 8 generic questions;
  - each of the 268 species: ≥ 1 species question (already possible from bundled asset), ideally 2;
  - each stage tier: enough questions to avoid strict repeats for 7 days of normal play;
  - each quadrant: every answer set offers at least one option that moves toward each of ≥ 2 types.

### 6.5 Quiz

- **Bond = `mood` (D2).** No new stat:
  - correct answer: `+3` mood (tunable; no streak bonus in MVP);
  - wrong answer: `−5` mood, **floor 0** (D8, accepted risk);
  - no mood change from dismissing a quiz; one quiz per individual per day;
  - because battles/injuries also move mood, the quiz is one contributor, not the only meter;
    the UI must show the answer result and the new qualitative relationship state, and the help
    text must explain that a wrong quiz lowers bond.
- Selection: quiz questions are tagged `stageTier` and `difficulty`; pick within the individual's
  stage ± 1, avoid last 30 answered per individual, avoid questions answered correctly twice in the
  same week; deterministic seed.
- Answer UI: 3 options; no timer; immediate feedback line + short explanation read from the
  authored `explanation` field (localized), then close. Correct/incorrect wording must not shame
  the player.
- Expansion: 94 → **≥ 300** at launch of the new system, ≥ 500 within one content cycle. Sources:
  cited lore facts; authoring templates per species attribute/type/stage; each question requires
  a citation and two independent checks.
- Anti-farm/anti-spiral: one per individual per day; the question bank cannot repeat within 30 days
  for the same individual; **no daily cap on mood delta magnitude (R5)** — the cadence limits are
  the only guard; no consecutive-quiz penalty stacking; debug can reset cooldowns but cannot award
  mood.

---

## 7. Wild Digimon negotiation

**D6:** the wild trust meter is **removed from player progression**. There is no trust gain/loss,
no trust-gated contact unlock, and no `trust >= 100` recruitment offer. Joining is a negotiation
outcome; convincing is an authored multi-conversation arc (`WildPersuasionEntity`); leaving for
good is an authored negotiation outcome (D11). Legacy `trust` data is read once for migration and
then ignored.

### 7.1 Entry and state machine

- Entry points: radar/Wild Digimon interaction ("Talk") and the DigiLine Wild tab for already
  unlocked contacts. A negotiation is available when the wild is in `WILD`/contact-unlocked but
  not `RECRUITED`, and no other negotiation for that individual is active.
- Phases:

```
APPROACH → PROBE → (BARGAIN ⇄ TEST) → RESOLVE
   │          │            │              ├─ JOIN  → contact unlocked in Wild tab (no Storage)
   │          │            │              ├─ REFUSE → partner leaves, retry cooldown
   │          │            │              ├─ FIGHT  → existing battle handoff
   │          │            │              └─ DEPART → contact can be lost for good (authored, D11)
```

- Length/difficulty by **stage tier** (see 7.3), never by hidden stats the player cannot affect.
- Tier resolution order (D12): **DIM card / card stage → game-save record level → catalog
  `level` → tier 2 fallback**; the source used is recorded in the session for balance review.
- One active `NegotiationSessionEntity` per wild; it is persisted before the first line is shown;
  force-close resumes at the same node (no reroll).
- Leaving/dismissing is always allowed ("Stop talking"); it costs a cooldown only. A `DEPART`
  outcome must be a deliberate authored result of the dialogue (trick exposed, insult, refusal
  chain), never an automatic mood drain — and it must have a warning tell before the final choice.

### 7.2 Personality use

- Wild personality already exists (lazy-generated via `WorldSocialRepository.profile`). The
  negotiation uses that 16-type value (not only the core) for greeting/response selection and for
  the demand style:
  - e.g. SLY/OPPORTUNISTIC more likely to offer tricks; ASTUTE reveals tells; ZEALOUS/BRAVE
    prefer battle probes; TOLERANT/COMPASSIONATE accept polite refusals; OVERPROTECTIVE guards
    against "you'll get hurt" lines.
- If a wild has no persisted personality, generate it deterministically at first contact and store
  it (existing behavior) — negotiation must never invent a new random type per session.
- Rewritten dialogue by personality: 16 type sets; each set must read naturally in all 3 locales
  and stay neutral (no gendered address).

### 7.3 Stage tiers and difficulty

| Tier | Species levels (catalog `level`) | Rounds | Topics | Demands | Trick chance | Content target |
|---|---|---|---|---|---|---|
| T1 | Baby I/II, Child | 2–3 | greeting + 1 probe | 1 (bits or 1 cheap item) | 0 % | 16 personality openings + 6 topics |
| T2 | Adult, Armor, Hybrid | 3–4 | 2 topics | 1–2 | 15 % | + 8 topics, 2 trick patterns |
| T3 | Perfect | 4–5 | 3 topics | 2–3 (bits + item) | 30 % | + 10 topics, 4 trick patterns, follow-up checks |
| T4 | Ultimate | 5–6 | 3–4 topics | 3 (bits + items + promise) | 40 % | + 12 topics, 6 trick patterns |

- "Rounds" = question/response exchanges before a resolve point; a failed probe can end the
  negotiation early (leave) but never consumes items already paid unless the specific node says
  so (see 7.5 fairness).
- Higher stages get **longer and costlier** negotiations, not impossible ones: every session must
  have a valid non-payment path (walk away / refuse / battle) at all times.
- Stage resolution (D12) uses, in order: DIM card / card stage → game-save record level → catalog
  `level` → T2 fallback. Working mapping to verify during Phase 1: card stage 0–1 → T1, 2–3 → T2,
  4 → T3, 5 → T4; catalog `Baby I/II`/`Child` → T1, `Adult`/`Armor`/`Hybrid` → T2, `Perfect` → T3,
  `Ultimate` → T4; the source used is stored in the negotiation session for balance review.
- Stage resolution (D12): **DIM card stage → game-save record level → catalog `level` → T2
  fallback**. Catalog `level == Unknown` (181 entries) and uncatalogued cards are only the last
  resort, flagged for review.

### 7.4 Demands (bits and items)

- `DemandResolver` derives a demand from `(tier, personality, negotiation phase, species)`:
  - bits: `base = tierBitsMin + rand(seed) * range`, capped by a percentage of the player's
    current bits (e.g. ≤ 20 %, and never more than the cap even for rich players);
  - items: only from an allow-list (consumables, materials, catalogs with quantity > 0); never
    quest-critical, equipped, or unique items; quantity 1–2;
  - a promise variant ("bring me X later") is allowed only if the target item is obtainable
    (checked against item DB), with a deadline in logical time and no partial reward on failure.
- If the player cannot afford any demand, the node must still offer a non-paying branch
  (persuade with a personality-appropriate answer, offer an alternative lower demand, or leave).
- Bits/items are only transferred on the node's explicit outcome; every transfer is logged in
  `NegotiationLogEntity` and wrapped in a DB transaction with the session update.
- Trick nodes may ask for extra after payment; rules in 7.5 cap total loss per session
  (`maxLossBits`, `maxLossItems` by tier) and never take the last item of a stack.

### 7.5 Tricks and fair play

- Tricks are authored, seeded branches, not random punishment. Each trick node carries a
  `tell` (a line/trait visible to the player) and at least one counter-option.
- "Fairness budget" per tier: at most 1 successful trick before a resolve point; total loss capped
  (bits % and item count); no trick may remove a recruitment quest item; a tricked player always
  keeps a path to end the conversation without battle if they choose it.
- Personality-linked tells: SLY/OPPORTUNISTIC give verbal tells; ASTUTE/ENLIGHTENED can reveal an
  extra "insight" option; BRAVE/DARING prefer direct confrontation; TOLERANT/COMPASSIONATE
  respond to respectful honesty. The tell must be authored per locale, not machine-generated.
- Failure states must never delete the wild contact automatically: the default failure is a
  refusal/leave with a retry cooldown. Only an authored `DEPART` node (with a preceding tell,
  D11) may remove a contact, and never on the first conversation.

### 7.6 Outcomes and integration with existing wild systems

| Outcome | System effect |
|---|---|
| `JOIN` | `WildRelationship.contactUnlockedAt = now` (the Digimon appears in the DigiLine **Wild** tab), `joinedAt` recorded, `recruitmentState` stays `WILD`; creates a `WildPersuasionEntity` at stage 0; no Storage, no quest, no trust value. Wild may still despawn from the radar — contact persists (existing DigiLine Wild design). |
| `REFUSE` | leaves, retry cooldown, no relationship deletion |
| `FIGHT` | existing radar battle handoff (unchanged) |
| `LEAVE` | cooldown only |
| `PAY_AND_LEAVE` | bits/items logged; contact not unlocked (must negotiate again) |
| `DEPART` | authored loss: contact and relationship are removed (D11). Only reachable through explicit dialogue choices with preceding tells; logged in `NegotiationLogEntity`; unjoined wilds simply despawn. |

- **Join ≠ recruit.** Joining never writes Storage and never completes the recruitment quest. The
  recruitment quest gate is now `WildPersuasionEntity.questOfferState` reaching `OFFERED`
  (authored arc complete) — not a trust threshold.
- **One recruitment quest at a time (D7):** `QuestRepository` allows only one active
  `RECRUITMENT` quest. Joined contacts whose arc completes while the slot is busy enter a persisted
  `PENDING` offer state ("wants to travel with you"); the scheduler offers the quest when the slot
  frees (or the player opens the Wild tab). This replaces the trust-based silent dead end.
- **Removal:** the old "mood 0 → farewell and delete contact" behavior is retired for joined
  contacts. Removal is only the authored `DEPART` outcome; unjoined wilds can leave/despawn
  normally. No passive meter can delete a joined contact.
- **Legacy migration:** existing contacts keep working; a one-time migration maps any old
  `trust`/`PENDING_RECRUITMENT` state to the closest persuasion stage (>=100 → `PENDING` offer,
  >=75 → joined/stage 0, otherwise stays unjoined) and then stops reading `trust`.

### 7.7 Post-join persuasion → recruitment quest

- Wild tab contact opens the scene surface with `PersuasionEngine` (no trust meter, D6):
  - short authored topics (rewritten from the negotiation JSON's "talk" nodes): reason to join,
    personality-matching challenges, relationship-building answers, occasional demands;
  - each answer has explicit authored effects on `WildPersuasionEntity` progress (topic flags,
    stage index), never on owned-Digimon mood;
  - content is gated by tier and personality; the authored arc defines how many conversations are
    needed (target 3–6) before `questOfferState = PENDING`;
  - when the arc completes and no active recruitment quest exists, the next conversation offers the
    recruitment quest with authored lines (replacing the current LLM `reactToPendingQuestOffers`);
    if the slot is busy, the contact stays in `PENDING` and the UI shows "wants to travel with you";
  - accepting/declining uses the existing quest action pipeline; declining keeps the contact and
    allows a later offer after a cooldown;
  - quest generation/difficulty stays as-is (`QuestRepository` recruitment templates by stage).
- The Wild tab displays progress qualitatively by stage ("still wary", "warming up", "ready to
  travel", "wants to travel with you"), never a numeric trust value.
- Anti-abuse: progress is persisted before display; no reroll by restart; one conversation session
  per contact per logical hour; `DEPART` is authored and logged (see 7.1/7.6).

### 7.8 Negotiation content model (schema sketch)

```json
{
  "personality": "SLY",
  "tier": 2,
  "nodes": {
    "start": {
      "speaker": "wild",
      "line": "…",
      "options": [
        {"id": "a", "text": "…", "effects": {"insight": true}, "next": "probe_1"},
        {"id": "b", "text": "…", "next": "probe_1"}
      ]
    },
    "probe_1": {
      "speaker": "wild", "line": "…",
      "options": [
        {"id": "a", "text": "…", "next": "demand_bits"},
        {"id": "b", "text": "I see through you.", "requires": "insight", "next": "trick_reveal"}
      ]
    },
    "demand_bits": {
      "speaker": "wild", "line": "…", "demand": {"kind": "bits", "amount": 120},
      "options": [
        {"id": "pay", "effects": {"bits": -120}, "next": "resolve_join"},
        {"id": "refuse", "next": "resolve_refuse"}
      ]
    },
    "resolve_join": {"outcome": "JOIN", "line": "…"}
  }
}
```

All `line`/`text` fields are localized per locale; effects are validated against caps; `next`
references and reachability are checked at build time.

---

## 8. LLM replacement and decommission plan

### 8.1 Gateway layer

- Introduce `DigilineGateway` (interface) and route all conversation UI through it:
  - `ScriptedDigilineGateway` (default) → owned engine, quiz, negotiation, persuasion;
  - `LegacyLlmGateway` (debug) → existing `ChatRepository` path.
- Keep `ChatRepository` but strip it to LLM plumbing used only by the legacy gateway; move
  deterministic helpers it currently owns (quest-offer fallback, reaction lines) into the new
  engines.
- Remove `publicDialogueAvailable()` gating from normal paths (no key requirement).
- Existing screens (`ChatScreen`, `WorldChatScreen`, `WorldConversationScreen`) consume the
  gateway; no direct `ChatRepository` imports outside `digiline/legacy/` (CI import gate).

### 8.2 Surface-by-surface replacement

| Surface | Replacement | Acceptance test |
|---|---|---|
| Owned chat | card scenes from content packs (species/generic/quiz) | no API key configured → owned chat fully functional; no HTTP |
| Wild chat | negotiation + persuasion engines | wild contact flow complete offline |
| Reactions (owned/wild) | `ReactionLineProvider` with localized templates by event/personality; reuses/extends `PromptLocalization` event copy | every current caller produces a deterministic line |
| Diary | `DiaryComposer` assembling facts from the existing reaction/event snapshot + authored templates | daily entries render in en/pt/ja |
| NPC dialogue | authored NPC line packs + existing quest hooks | existing NPC flows unchanged, offline |
| Farm social | seeded topic packs by personality (reuse `FarmConversationOrchestrator` pipeline without LLM) | conversations repeat safely, no network |
| Radar/ecosystem social | existing `WorldSocialPlanner` + deterministic openings (`SocialOpenings`) | no `generateRadarExchange` in normal path |
| Adventure/items notices | authored event lines from `event_lines.json` | item/adventure events show localized lines |
| Quest offers | deterministic offer lines from giver personality + quest template | offer/accept/decline unchanged |
| Prompt/lorebook/templates | debug-only; remove from release UI | release build has no prompt UI |
| `species_chat_examples.json` | consumed by build pipeline into `species_questions.json`; delete asset from app after migration | asset no longer read at runtime |

### 8.3 Debug toggle specification

- Key: `debug_legacy_llm_chat` (SharedPreferences via `DebugSettingsRepository`), default **false**.
- Visible only when `debug_enabled = true`; toggling `debug_enabled` off forces the LLM switch off.
- When off: hide LLM config dialog, do not read `LlmSettingsRepository` in gameplay paths, no
  network; existing API keys remain in DataStore but unused (option to clear).
- When on: legacy badge on chat; messages marked `legacy_llm`; sandboxed per D10 (no gameplay
  effects).
- Remove the LLM settings section from the non-debug Settings screen (move under Debug).
- CI/release checks: no `OpenRouter` imports outside `com.tamerslab.chat.legacy`; no usage of
  `ChatRepository` outside gateway/legacy; release manifest review of `INTERNET` (keep only if
  online battle/companion features still require it — audit first; chat must not).

### 8.4 Removal checklist

- Delete or move to `chat/legacy/`: `OpenRouterClient`, `OpenRouterModels`, `OpenRouterService`,
  `ChatApiProvider`, `BattleTalkDetector`, `DigimonPersonaBuilder`, prompt templates dialog,
  `LlmSettingsDialog` (from normal settings), `LlmSettingsRepository` (debug access only).
- Remove prompt-only assets/strings from release resources (`species_chat_examples.json` after
  conversion, default prompt templates in `SettingsScreen`).
- Keep `LlmSettingsRepositoryTest` as a legacy/debug test or delete with the feature.
- Update docs: `README`, `docs/`, help strings, settings copy; document that DigiLine is authored.
- Verify `ChatRepository` no longer appears in Koin wiring except for the legacy gateway.

### 8.5 Legacy chat history migration

- Existing `ChatMessageEntity` rows are archived in the new scene history as "legacy" messages
  (no cards, no effects) so the player does not lose their old conversations. **R9:** in normal
  mode they are read-only; they become interactive again only when the debug LLM toggle is on.
- New questions/negotiations write new rows (`kind` non-null); overview previews choose the last
  scene message; unread counts keep working.
- Normal mode shows an archive notice at the top of legacy threads; debug mode may continue them.

---

## 9. Testing and QA

### 9.1 Automated

- **Content validation** (build + CI): schema, unique IDs, reachable nodes, all locales present,
  no missing keys, banned SMT terms, citation coverage for factual claims, answer keys valid,
  option counts, length budgets, personality/type coverage, species mapping coverage (268 →
  catalog; report every unmatched alias), stage tags present.
- **Pure engine unit tests:** shift thresholds/hysteresis/decay/caps; determinism with fixed
  seeds; option effects; demand affordability; trick caps; mood floors; persuasion caps;
  anti-farm windows; resume semantics; no negative-stat underflow.
- **DB tests:** migration v55→v56 fixtures; transactional negotiation payment; concurrent
  answer/double-tap; contact/recruit invariants; queued recruitment offers.
- **UI tests:** scene card rendering per locale (pseudo-locale), no keyboard on authored flows,
  accessibility labels, unread/preview states, debug toggle visibility.
- **No-network proof:** instrumented test with a fake HTTP server that fails on any request +
  assert chat/negotiation/quiz flows complete; static CI grep gate against LLM symbols.
- **Performance:** cold content load ≤ 250 ms mid-range device; no main-thread JSON parse; pack
  size budget; R8; baseline profile impact.
- **Localization:** pseudo-locale expansion, ja/pt font/line-break checks, neutral-language lint.

### 9.2 Manual / playtest

- Fresh save: first question, quiz, wild negotiation, join, persuade, recruit quest, Storage.
- Existing/migrated save: old LLM threads show as archive; personalities/shift state migrate;
  imported native personalities not corrupted.
- Edge cases: closing app mid-question/mid-negotiation; airplane mode; no bits; full item bag;
  full wild tab; active recruitment quest + new joins; personality shift across a battle; stage
  Unknown card; duplicate species; dismiss/leave repeatedly; debug LLM toggle on/off.
- Balance: question cadence not annoying; quiz loss doesn't feel punitive; negotiation fair and
  readable; trick loss caps respected; recruitment pacing.

---

## 10. Delivery plan (phases, exit criteria)

| Phase | Work | Exit criteria |
|---|---|---|
| **0. Audit & kill switch** (1–2 d) | Inventory every `ChatRepository` caller; add `DebugSettings.legacyLlmChat`; stub `DigilineGateway` delegating to legacy; move LLM settings under Debug; add CI import gate + no-network test harness. | Release build runs with LLM off; all existing flows still work via legacy when toggled on; CI gate active. |
| **1. Content pipeline v0** (3–4 d) | Build pack generator from `digimon_chat_export` + bundled `species_chat_examples.json`; alias map (245 by name + 23 manual); expand generic templates to ≥ 8 per type; lore registry loader; content schema + validator; generate en packs. | Validator green: 268 species mapped, 16-type generic coverage, locale skeleton, 94 quiz imported. |
| **2. Owned DigiLine MVP** (4–6 d) | Room v56 additions; `DigilineRepository`; `OwnedDigilineEngine`, `QuestionScheduler`, `QuizEngine`; card UI on `ChatScreen`; cooldowns/persistence; answer effects wired to shift/mood stubs. | Question + quiz flows fully offline in en; state survives restart; no LLM path; unit/UI tests. |
| **3. Personality & mood** (3–4 d) | `PersonalityShiftEngine` + tendency table; mood-as-bond wiring + UI feedback; coverage validator; debug inspector; native personality sync (`TimeStrangerEdits.setPersonality`, verified native-id mapping, sync queue, backup/journal UX). | Shift rules tested; all 16 reachable; battle profile follows shifts; native sync verified against fixture saves; anti-farm tests. |
| **4. Wild negotiation MVP** (5–7 d) | Pack generator/adaptor for negotiation structure; rewrite tutorial tier + 4 personalities; `NegotiationEngine` + session persistence; Join → Wild tab; persuasion-stage engine replacing trust; recruitment-quest queue state; authored `DEPART`; tier from DIM/game level; D6 migration. | Full loop: wild → negotiate → join → persuade → quest → recruit; trust gates nothing; leave/DEPART paths fair; invariants hold; fair-play tests. |
| **5. LLM removal from remaining surfaces** (3–5 d) | ReactionLineProvider for owned/wild events; diary; NPC; farm/radar; adventure/items; quest offers; delete/gate legacy code; settings cleanup; docs. | No runtime LLM imports outside legacy; no-network proof; settings show only debug toggle. |
| **6. Content expansion & localization** (ongoing, 2–4 weeks) | 16-type generic expansion; 94 → 300+ quiz; 268 species second sets; full negotiation matrix T1–T4 × 16 types; pt-BR/ja translation + review; balance pass. | Content targets met; locale parity; native review sign-off; release candidates playtested. |

Estimates assume one developer for code and a separate content authoring effort; content volume is
the dominant risk (see F3/F4). If content cannot be produced at full scope, ship Phase 2–4 with
English and a reduced generic/negotiation matrix (T1–T2, 6 types) and expand by content packs.

**Implementation directive (user, 2026-11-13):** build every phase in one continuous pass and run
the build/tests only after all implementation is done — the table above is an ordering guide, not
a checkpoint schedule. If compile errors appear at the end, fix them iteratively until green.

---

## 11. Flaws and logic problems found in the brief

Ordered by impact. Each entry records the flaw found in the original brief; the decision log in
**§0.1/§12** resolves F2, F6, F8, F9, F12, F13, F14 and F17 (read those entries as history plus
their resolution, not as open recommendations).

**F1 — "Replace anything that uses an LLM" is much larger than the chat screen.** The LLM is used
by 10 surfaces (§2.5): owned/wild chat, reactions, diary, NPC dialogue, farm social, radar social,
adventure/item notices, quest-offer prose, plus prompt/lorebook plumbing. Replacing only DigiLine
would leave the app network-dependent and inconsistent. **Resolution:** the phased surface table in
§8.2; Phase 5 is a hard gate.

**F2 — Content licensing.** The three extracted datasets are not free content:
`digimon_chat.json` is the DCO mod author's rewritten text; the quiz and SMT V files contain
Bandai Namco/ATLUS game text; the wiki is CC BY-SA with its own conditions. Shipping them
verbatim in your app is a legal risk and may violate the mod's implied permission (translation
permission ≠ redistribution permission). **Resolution:** treat all exports as **reference only**;
author/rewrite every shipped line; keep citations for lore; do not bundle the raw exports; obtain
explicit permission if any mod text is reused. This must be settled before Phase 6 content volume
work (blocking).

**F3 — Volume math does not support "from time to time" at launch.** Current pools:
268 species × 1 conversation; 64 generic templates; 94 quizzes. With per-12 h cadence, a player
exhausts the generic pool in days. **Resolution:** per-type coverage targets (≥ 8 generic/type,
≥ 1–2 species sets, ≥ 300 quizzes), cadence caps, and a content-pack update process; Phase 6 is
not optional for a long-lived feature.

**F4 — Personality gating makes the generic pool even smaller.** Assigning each of 64 templates
to 1–2 types yields 4–8 questions per type for the whole game. **Resolution:** assign templates
to 2 types (128 assignments) and add authored templates; validator enforces minimum per type;
fallback pool always available.

**F5 — Species coverage vs. catalog is imperfect.** 268 example entries: 245 match the catalog by
name/alias, 23 do not (e.g. `AEGIOCHUSMON_HOLLY`, `CHRONOMON`, `ROSEMON_BM`, `MEGAGROWLMON`,
`LUCEMON_FM`). The catalog has 1,662 species and 181 `Unknown` levels. **Resolution:** manual
alias table + validator; unknown species fall back to generic; do not expose half-mapped content.

**F6 — No "bond" stat exists for owned Digimon.** The app has `mood` (transient) and wild
`trust`; the brief asks for bond up/down. **Resolved by D2:** `mood` is the bond stat; no new
column. Document the side effect that battles/injuries also move it.

**F7 — 15 SMT tones cannot cover 16 personalities.** At least one personality (e.g. ASTUTE or
DEVOTED, depending on the mapping) has no source tone; others are forced fits. **Resolution:**
author new tone sets for uncovered types and rewrite all text; the SMT files are structure only.
Also SMT's "female/male" tone split is not personality — do not map gender to personality.

**F8 — Direct personality shift changes battle AI.** `PersonalityBattleProfile.from(type)` feeds
offline battle behavior. A social answer could silently change combat. **Resolved by D3:** the
change is accepted (one identity for social and battle); surface it in help/notice text, never
shift during battle or an active quest.

**F9 — Native imports vs. app-side shift.** Personalities can be imported from game saves and
transferred back. Mutating the persisted type may desync exports. **Resolved by D4:** the shift
must propagate to the game — add the validated `TimeStrangerEdits.setPersonality` byte edit and
the native-id mapping, queue failed syncs (FARM/unverified), and show the sync state instead of
silently diverging.

**F10 — Save-scumming and reroll abuse.** Any random selection (question, option shuffle,
negotiation seed) is exploitable by force-close if not persisted first. **Resolution:** persist
the selected question/seed and option order before showing; resume exactly; answer commits in a
transaction; negotiation seeds are stored at session start; no RNG at outcome display.

**F11 — Personality-shift feedback loop.** Shift → different generic pool → answers that shift
again; players can farm a type to unlock content or endlessly flip. **Resolution:** shift cooldown
+ hysteresis + daily point decay; question cadence caps; content reachable from every quadrant;
no "always best" options; debug-only numeric view.

**F12 — Quiz farming and punishment.** Correct → bond up creates a farm; wrong → bond down can
feel punitive and stack with a bad day. **Decision D8 (accepted risk):** wrong = −5 mood, floor 0,
correct = +3; mitigations: one quiz/individual/day, 30-day no-repeat, no consecutive-quiz stacking,
clear feedback before answering, no timers.

**F13 — "Join → Wild tab" collides with the one-active-recruitment-quest rule.** The code allows
only one active RECRUITMENT quest. Multiple joined contacts reaching the offer point would each
want one, but only one can exist; the current code path risks a silent dead end. **Resolved by
D7:** persisted queued-offer state + "wants to travel with you" UI; offers happen when the slot
frees; the one-quest rule is kept.

**F14 — Mood-0 farewell can delete a joined contact.** Current wild rule: mood 0 → farewell/removal.
A joined contact with a rough streak could disappear and lose the unlock. **Resolved by D11:**
removal is only an authored `DEPART` outcome with a preceding tell, never a passive meter; the
default failure is a leave + cooldown that keeps the contact.

**F15 — Demands can brick poor players or softlock the economy.** Costs based on bits/items can be
impossible at low wealth; taking items can remove quest/equipment needs. **Resolution:**
affordability-aware demand selection with caps (≤ 20 % of bits), allow-list for items, never
quest/unique/equipped items, always a non-payment branch, per-session loss caps.

**F16 — Tricks can feel arbitrary/unfair.** SMT-style trick branches copied structurally can turn
into punishment. **Resolution:** authored tells, counter-options, budget caps, no trick on the
first tier, no trick after the player already paid in the same session (unless authored and
clearly preceded by a tell), log for QA.

**F17 — Stage difficulty vs. app stage semantics.** Negotiation difficulty needs a reliable stage
value; the catalog `level` has 181 `Unknown` and the app also uses numeric card stages 0–5.
**Resolved by D12:** resolve the tier from the DIM card/card stage first, then the game-save
record level, then the catalog level, then T2; tag content by tier; the validator asserts every
tier/personality combination has enough nodes.

**F18 — "From time to time" needs a scheduler that does not fight the game.** Interrupting
battles/quests, background timers on Android, clock changes, catch-up spam. **Resolution:** trigger
only on app-open/return-to-Home/opening DigiLine; no background service; caps per session/day;
logical-time seeds; absent-player catch-up limited to one pending card.

**F19 — Existing UX assumes free text.** `ChatScreen` has a composer; players can currently type
anything. Removing it is a product change; the brief does not define how a player who liked free
chat is told. **Resolution:** Phase 0 adds a one-time notice ("DigiLine conversations are now
authored"); no fake typing prompts; if debug LLM is on, free text is clearly marked legacy.

**F20 — Debug LLM toggle can corrupt progress or leak keys.** Current LLM replies can change
mood/state and the LLM settings screen is visible in release; keys live in DataStore.
**Resolved by D10:** sandboxed (no effects), debug-master switch required, dialog hidden when off,
keys unused, never ship keys/default endpoints, audit `INTERNET` usage before removing it.

**F21 — Localization is a huge hidden scope.** 3 locales × ~2,000+ authored cards + negotiation +
quiz + event lines; pt-BR/ja neutral-gender rules conflict with natural phrasing and with existing
prompt text that is not neutral. **Resolution:** content schema from day 1, EN-first with locale
fallback, translation/review gates, automated neutral-language lint, and a realistic content
budget; do not promise 3-locale parity in the MVP.

**F22 — Data quality in the extracted references.** Chat: 5 empty texts, 20 repeated lines;
negotiation: 47 unresolved choices, 8 branch-only topics, 5 blank quiz-ish cells, two misformatted
topic headers; quiz: 94 only, occasional source typos (preserved). **Resolution:** validator
reports reference gaps; authoring work replaces them; never auto-ship blanks.

**F23 — "Never guess lore" needs a mechanism, not a rule.** Authors will need lore quickly;
without a registry they will guess. **Resolution:** `lore_species.json` with citations is a
blocking dependency for Phase 1/6; content validator rejects factual claims without a citation;
a small lore review checklist per species family.

**F24 — Weighted species content is invisible to most players.** A player may own 6 Digimon out of
268 species; most species questions will never be seen. **Resolution:** surface species content in
the Dex/research UI? (out of scope) or accept that species content is aspirational; prioritize
generic/quiz/negotiation content that every player hits, and add species questions for the partner
line the player actually raises.

**F25 — App already bundles the same species chat content twice (prompt asset + new packs).**
Convert once, delete the old asset, and update `SpeciesRepository`'s `conversationExamplesLoader`
(the only current consumer) so nothing reads the removed asset.

---

## 12. Decisions and remaining open questions

### 12.1 Decided (2026-11-13) — implementation contract

See the full log in **§0.1**. Summary: D1 rewrite all content; D2 mood = bond; D3 shifts also
change battle behavior; D4 shifts propagate into the Time Stranger save; D5 two-type generic
assignment + authored extras + slower cadence; D6 no trust system (authored persuasion instead);
D7 one active recruitment quest + queued offers; D8 quiz wrong = −5 mood, floor 0; D9 EN MVP with
locale-ready schema; D10 debug LLM sandboxed; D11 authored negotiation outcomes may remove a
Digimon; D12 tier from DIM card / game-save level before catalog fallbacks.

### 12.2 Second batch — resolved (2026-11-13)

All twelve follow-ups were answered; see §0.2 for the table. Effect on this section: R1–R10, R12
are decisions, not open questions. Only one item remains unresolved:

| # | Question | Status | Blocks |
|---|---|---|---|
| R11 | Who owns content authoring/review (you, a writer, or a paid pass)? | **Unresolved (skipped).** Code work must not block on it; content volume and localization require an answer before Phase 6 sign-off. | Phase 6 |

Implementation directive from the same batch: **implement all phases in one continuous pass and
run build/test only after everything is implemented.** The phase table in §10 stays the ordering
guide; compile/test checkpoints are deferred to the end by explicit instruction.

---

## 13. Appendices

### 13.1 LLM inventory checklist (Phase 0 audit targets)

Grep targets in `app/src/main/java`: `OpenRouter`, `ChatApiProvider`, `ChatRepository`,
`PromptLocalization`, `DigimonPersonaBuilder`, `llmSettingsRepository`, `publicDialogueAvailable`,
`generateFarmReply`, `generateRadarExchange`, `sendMessageForWildEncounter`, `triggerReaction`,
`reactToPending*`, `NpcDialogueService`, `DigimonDiaryService`, `ChatScreenController`,
`WorldChatScreenController`, `FarmConversationOrchestrator`, `WorldInteractionOrchestrator`,
`BattleTalkDetector`, `LlmSettingsDialog`, `PromptTemplateDialog`, `species_chat_examples.json`.

### 13.2 Content pack schema essentials

- stable IDs: `species.<catalogId>`, `generic.<type>.<n>`, `quiz.<n>`, `nego.<type>.<tier>.<n>`,
  `persuade.<type>.<n>`, `event.<key>.<variant>`;
- option: `{id, textKey, shift:{typeId:int}, mood:int, effects:{bits:int,item:{id,qty}}, next}`;
  (owned questions: `mood` is the bond delta; wild negotiation: no trust effect — use authored
  node outcomes and persuasion stage flags instead)
- validators enforce: no duplicate IDs, reachable graph, no dangling `next`, no self-loop without
  cooldown, every prompt has ≥ 2 options and an exit, every outcome terminal, effects within caps,
  all locales have all keys, citations for lore claims, banned-term list.

### 13.3 Data folder reference (extracted inputs)

`digimon_chat_export/` currently holds the reference exports and extractor scripts:
`digimon_chat.json` (5,630 rows), `digimon_chat_conversations.json` (335 groups),
`quiz_time_digimon_questions.json` (94), `demon_negotiation_dialogue_smt5.json` (15 tones / 215
topics / 1,126 steps), plus README and build/validation scripts. These are **build-time inputs**,
not runtime assets. The bundled `app/src/main/assets/species_chat_examples.json` (268 species
chats) is the runtime-equivalent species content today and will be converted by the pipeline.

### 13.4 References

- `digimon_chat_export/README.md` — dataset schemas, statistics, caveats.
- Current code: `app/src/main/java/com/tamerslab/chat/`, `farm/ui/digiline/`, `world/`,
  `quests/`, `domain/personality/`, `settings/`, `di/`.
- `docs/adr/0003-koin-dependency-injection.md`, `docs/adr/0005-performance-budgets.md`.
- Lore sources: <https://wikimon.net>, <https://digimon.net>.
- Time Stranger structure reference: DCO mod data; Cyber Sleuth quiz guide; SMT V negotiation wiki
  (structure only).

---

## 14. Implementation status (2026-11-13)

### 14.1 Delivered

| Area | What was added |
|---|---|
| Content packs | `app/src/main/assets/digiline/v1/` (manifest contentVersion **4** + en packs): species questions (263/268 matchable, 5 documented unmatched), **128 generic questions (16 per personality)**, 2,882 quiz cards generated from the app species catalog, **negotiation 64 authored trees (16 personalities × 4 tiers): 736 nodes / 1,296 in-world answers, every answer carrying an authored demon reaction and continuing its topic (2/3/3/4 exchanges before the price; one-time 55–75 % counter-offer branch in every price phase; legacy generic meta-options removed)**, persuasion (16 types × stages 0–3), 65 event lines, 1,508 lore facts. Generator/validator in `tools/digiline/` (deterministic; originality + schema + coverage + dialogue-continuity + counter-offer gates). |
| Models + engines | `com.tamerslab.digiline.model` / `.engine`: DTOs, `DigilineContentPack`, deterministic seeding, `PersonalityShiftEngine`, `QuestionScheduler`, `QuizEngine`, `NegotiationEngine`, `PersuasionEngine`, `DemandResolver`, `ReactionLineProvider`, `DiaryComposer`, `OwnedDigilineEngine`; 113 JUnit tests. |
| Persistence | Room schema **56** (`DigilineSchema.migrate`): profile/cadence, question & quiz records, personality tendencies, native sync queue, negotiation session + log, persuasion arc, event dedupe; `ChatMessageEntity` `kind`/`payloadJson`; `MIGRATION_55_56` wired in `DefaultAppContainer`; androidTest fixtures updated to 56. |
| Gateway + repositories | `OwnedDigilineRepository` (12 h question / 24 h quiz cadence, seeded anti-reroll, mood-as-bond, shift + native queue), `WildDigilineRepository` (negotiation → JOIN, authored persuasion → recruitment-quest queue, DEPART, tier resolution), `ScriptedDigilineGateway`, `DigilineContentProvider` (cached, empty-pack fallback). |
| UI | **Distilled scene card** in owned chat, wild contact and the in-world Radar negotiation: short framing line, the Digimon's reaction, the current line, an optional Bits price, the authored answers and one quiet exit — no title row, phase/tier badges, rapport readings or duplicate transcript panel. Owned sync notes remain as a small footnote. **Wild negotiation plays out in the Radar world**: the isometric camera eases onto the Digimon, the heading freezes and the sprite faces the player while this card floats over the live world; legacy free-text composer and the trust/mood bar only appear when the debug LLM toggle is on; old LLM transcript rows are filtered out of normal mode at the repository and thread-preview query level; one-time battle-personality notice; settings debug switch + LLM config hidden unless both debug switches are on. |
| LLM removal | `ChatRepository` has a `legacyLlmEnabled` gate: every runtime path (owned/wild chat, reactions, farm, radar, quest offers, diary fallback, recaps) returns authored deterministic offline text in normal mode; `publicDialogueAvailable()` is false unless debug legacy is on; Diary now uses `DiaryComposer` + content-pack event lines. |
| Native save edit | `TimeStrangerEdits.setPersonality` (validated byte edit at `nameOffset + 0xEE`), unit tests, `TimeStrangerPersonalitySync` candidate builder, `NativePersonalitySyncCoordinator` queue/state machine, `NativePersonalitySyncEntity` + sync UI state. Auto-write itself waits on a Time Stranger save writer (see gap 1). |
| Wild/quest integration | Recruitment quest gating moved from `trust >= 100` to the authored persuasion `PENDING/OFFERED/ACCEPTED` states (legacy trust path only for pre-migration saves); one-offer-at-a-time queue; contact unlock on negotiation JOIN; `WildRelationshipDao.clearQuestContact` for authored DEPART. |

### 14.2 Verification (build phase, run only after implementation)

- `gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL**
- `gradlew :app:assembleDebug :app:assembleIntegrityCheckAndroidTest` — **BUILD SUCCESSFUL**
- `gradlew :app:testIntegrityCheckUnitTest :game-save-core:test` — **BUILD SUCCESSFUL** (1,132 app unit tests / 0 failures, 113 of them in `com.tamerslab.digiline`; 201 `game-save-core` test cases / 0 failures including `TimeStrangerPersonalityTest`)
- Final `assembleDebug` after the contentVersion-2 generic expansion (128 generic questions) + `NativePersonalitySyncCoordinator`: **BUILD SUCCESSFUL**; `app-debug.apk` packages all 8 `assets/digiline/**` files (verified by listing the APK entries).
- `python tools/digiline/validate_packs.py` — 0 errors / 0 warnings, **contentVersion 4** (736 negotiation nodes / 1,296 options / 1,296 reactions, depth 2/3/3/4, one 55–75 % counter-offer per flow; originality 0 flags)
- `:app:testIntegrityCheckUnitTest` also runs `DigilineAssetPackTest`: the shipped assets load through the production loader into the Kotlin DTOs and pass the app-side `DigilineContentValidator` (plus reaction/topic-continuity assertions); `DigilineNegotiationDialogueValidatorTest` covers the new validator rules.
- Generated content JSON cross-checked against the Kotlin DTO required fields.

### 14.3 Known gaps / follow-ups

1. **Native auto-sync cannot write yet:** the app has no Time Stranger *save writer* (its capabilities are read/provisional-copy only). The shift is queued and shown as "not written to game save" instead of silently diverging. Completing D4 needs a guarded TS writer (SAF + backup + journal + readback) that calls `TimeStrangerPersonalitySync.prepare(...)` and marks `WRITTEN`; blocked on that product capability, not on the byte edit itself.
2. **Non-negotiation battle handoffs** (parallel/side attacks) are outside this surface; the negotiation FIGHT outcome now closes the in-world talk and starts the Radar encounter battle. Contact-only wilds still have no battle route because they are no longer spawns.
3. **Item payments** are engine-supported but content currently demands bits only; the wild repository passes an empty item inventory and does not transact `itemDelta` yet.
4. `ReactionLineProvider` is wired into the diary; other legacy reaction callers use authored fallback lines (`LegacyLlmFallbacks`), not pack event lines. No LLM is involved either way.
5. DigiLine list pending-question badge and Wild-tab status chips are not implemented (screens are already card-based).
6. EN-only content per D9; pt-BR/ja packs remain Phase 6.
7. Quiz pack is 2,882 cards (~1.25 MB): eager parsing happens off the main thread once and caches; splitting by tier/lazy parsing would reduce memory pressure later.

### 14.4 Commands

```
python tools/digiline/build_packs.py        # regenerate assets (deterministic)
python tools/digiline/validate_packs.py     # schema/coverage gate (exit 0 on success)
gradlew.bat :app:testIntegrityCheckUnitTest :game-save-core:test
gradlew.bat :app:assembleDebug
```
