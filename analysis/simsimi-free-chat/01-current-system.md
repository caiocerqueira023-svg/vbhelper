# 01 — Current DigiLine chat system + integration seams for a retrieval-based free-chat mode

**Scope:** current-state audit for a SimSimi-style, offline, no-runtime-LLM free-text DigiLine chat. Static source/doc audit only
(no build, no device run). Paths are workspace-relative; `:line` citations were read in this session.
**Legend:** **[V]** verified in source/schema now · **[D]** from plan/contract text only · **[U]** unverified/not measurable statically.

## 0. What matters for the plan (time-poor summary)
1. Free text exists **only** behind the debug legacy-LLM toggle; normal play renders authored **scene cards** (`ChatScreen.kt:242-257`,
   `WorldChatScreen.kt:240-254`) and the card currently replaces the composer (`ChatScreen.kt:259-281`). **[V]**
2. The seam is a small interface — `DigilineGateway` (`digiline/gateway/DigilineGateway.kt:61-75`) — implemented by `ScriptedDigilineGateway`,
   wired through `AppContainer`/`DefaultAppContainer`; UI controllers call it for all scenes. **[V]**
3. Authored domains are already **pure Kotlin engines + persisted decisions**; a free-chat selector belongs beside them (`digiline/engine`
   has zero `android.*`/`androidx.*` imports **[V]**).
4. Transcript is one table, `ChatMessageEntity`, with `kind`/`payloadJson` added in v56; `payloadJson` is written/read **nowhere**, and the
   normal-mode UX depends on `kind != null` (history filter + list previews). **[V]**
5. A rich, cheap Digimon-context source set already exists (identity, species profile, 16-type personality + traits, appraisal, mood,
   transcript, quests, wild relationship, native-save sync) — mapped in §5. **[V]**
6. Content packs are loaded eagerly, parsed whole, cached once (7 JSON files, **2,936,586 B ≈ 2.80 MiB** measured) **[V]**, vs the plan's
   ≤2 MiB/locale budget (`DIGILINE_CHAT_SYSTEM_PLAN.md:299,823`); no measured load time exists. **[D/U]**
7. Top risk is **state-machine collision**: `choose()` is driven by a persisted pending-question row (`OwnedDigilineRepository.kt:145-155`),
   not by the visible scene; free chat must not reuse it.

## 1. Conversation surfaces (where the player talks today)
| Surface | Route / screen | Controller → path | UI today | Free text? |
|---|---|---|---|---|
| Owned partner DigiLine | `AppRoute.Chat(characterId)` (`AppRoute.kt:58-59`; nav `AppNavigation.kt:552-557`) → `chat/ui/ChatScreen.kt:34` | `ChatScreenControllerImpl` (`:12`) → `AppServices.digilineGateway` (`:15-18`); history `chatRepository.getHistory` (`:20-21`) | Transcript + `DigilineSceneCard` (`:242-257`); mood only in debug (`:144-148`); sync footnote (`:149-161`); shift notice (`:298-313`) | **No** — `ChatComposer` only if `legacyLlmEnabled` (`:259-281`) |
| DigiLine list | `AppRoute.Digiline` (`AppRoute.kt:16`; nav `:419`) → `farm/ui/digiline/DigilineScreen.kt:77` | `digifarmRepository.observeStorageThreads()` (`:80`) + `observeWildThreads()` (`:81`) | Storage/Wild tabs (`:102`); row preview `lastMessage`/`unreadCount`; wild trust reading only in legacy (`:174-179`) | No |
| Wild contact (joined) | `AppRoute.WildContact(individualId, cardCharacterId)` (`AppRoute.kt:30-31`; nav `:424-439`) → `world/ui/WorldChatScreen.kt:60` | `WorldChatScreenControllerImpl` (`:30`) → gateway (`:37-49`); quest pipeline (`:69-128`); history (`:130-131`) | Scene card (`:240-254`); mood/trust panel only legacy (`:171-179`); composer only legacy (`:256-280`) | **No** — legacy toggle only |
| In-world Radar negotiation | `AppRoute.Radar` (`AppRoute.kt:15`; nav `:412`) → `world/ui/WorldScreen.kt` | direct gateway calls (`WorldScreen.kt:472-473, 491, 507, 540`) | `OverworldNegotiationOverlay` (`:177`) reuses `DigilineSceneCard` over the live world | No |
| Legacy world conversation | `AppRoute.WorldConversation(interactionId)` (`AppRoute.kt:27-28`; nav `:421-422`) | `WorldConversationScreen` | not inspected deeply — **partial [D]** | legacy-gated upstream |
| Debug legacy chat | owned/wild routes above | `ChatRepository.sendMessage` (`ChatRepository.kt:401-448`, gate `:402-407`); `sendMessageForWildEncounter` (`:98-114`, gate `:106-114`); `resendMessage` (`:450-456`); reactions (`:273-274, 354-361, 472-476`); farm/radar/recap (`:516-520, 561-565, 583-586`) | free-text composer + legacy badge + resend | **Yes, debug only** |
| Legacy decommission state | history filter hides rows without `kind` in normal mode (`ChatRepository.kt:64-65`); `publicDialogueAvailable()` = legacy switch **and** API key (`:513-514`); `LegacyLlmFallbacks` neutral lines (`chat/legacy/LegacyLlmFallbacks.kt:11-47`) | — | old LLM rows are read-only archive | — |

Debug switches: `DebugSettings(enabled, legacyLlmChat)`; `legacyLlmEnabled = enabled && legacyLlmChat`
(`settings/data/DebugSettingsRepository.kt:9-20`; persisted key `debug_legacy_llm_chat` `:36`; forced off in `setEnabled` `:43-51`).
**Finding:** `values/strings.xml:656` `ui_digiline_empty_wild` still says “Raise a wild Digimon’s trust above 75…” (pt `:635`, ja `:566`),
contradicting D6 (trust removed) — stale copy a new mode would inherit. **[V]**

## 2. Runtime architecture and signature-level seams
### 2.1 Gateway contract (`digiline/gateway/DigilineGateway.kt`)
- `interface DigilineGateway` (`:61-75`): `observeHistory(individualId): Flow<List<ChatMessageEntity>>` (`:63`),
  `observeScene(individualId): Flow<DigilineScene?>` (`:66`), `suspend ensureScene(individualId, wild: Boolean): DigilineResult` (`:69`),
  `suspend choose(individualId, optionId)` (`:72`), `suspend dismiss(individualId)` (`:75`).
- `DigilineScene` (`:22-45`): `kind` (`Kind` enum `:44` = QUESTION, QUIZ, NEGOTIATION, PERSUASION, REACTION, IDLE — **no TEXT kind**), `lines`,
  `reactionLine`, `options: List<DigilineOptionView>` (`:7-10`), `canDismiss`, `syncLabel`, `demandBits`, `contextKey`, `outcomeKey`.
  `DigilineResult` (`:48-52`): `Scene` / `Closed` / `Error`.
### 2.2 Default routing (`digiline/gateway/ScriptedDigilineGateway.kt`)
Owned vs wild = absence of a `UserCharacter` row (`isWild()` `:66-67`). Scenes are **process-memory only**
(`MutableStateFlow<Map<String, DigilineScene?>>` `:25`, publish `:60-64`); repositories persist progress before returning a scene
(`:11-18`), so process death resumes from DB, not a reroll. **[V]**
### 2.3 Owned path (`digiline/data/OwnedDigilineRepository.kt`)
`ensureScene` (`:84-142`): commit pending ack → re-show persisted pending card (`:91`, `:393-421`) → 12 h cooldown (`:94-96`,
`QUESTION_COOLDOWN_MILLIS :509`) → `OwnedDigilineEngine.plan` (`:99`) → **persist** `DigilineQuestionRecord` + `DigilineProfile.lastAskedAt`
(`:102-137`) before returning. `choose` (`:145-155`) needs a pending record (`:148-149`, “Nothing is waiting for an answer.”); quiz (`:274-338`);
mood clamp 0..100 (`:364-369`); shift + native-sync enqueue (`:342-362`); transcript append `kind = digiline_user|digiline_assistant`
(`:491-503, 523-524`).
### 2.4 Wild path (`digiline/data/WildDigilineRepository.kt`)
`ensureScene` (`:54-69`) branches on `WildRelationship.contactUnlockedAt` → negotiation vs persuasion; session persisted **before first line**
(`:121-134`); `choose` (`:71-81`); JOIN = contact unlock + `WildPersuasion` in one transaction, session deleted (`:210-224`); DEPART clears the
contact (`:226-232`); personality lazily via `WorldSocialRepository.profile` (`:421-424`); tier = card stage → species level (`:426-434`,
`DigilineStages.kt:42-43`); same transcript kinds (`:535-536`).
### 2.5 Pure engines (no Android/Room — verified by import grep)
`OwnedDigilineEngine.plan/answerQuestion/answerQuiz` (`engine/OwnedDigilineEngine.kt:33, 67, 117`); `QuestionScheduler.select/arrange`
(`:21, :67`); `PersonalityShiftEngine.apply/coreOf` (`:54, :122`); `QuizEngine.resolve/moodAfter` (`:18, :47`; +3/−5 at
`model/EngineModels.kt:146-147`); `NegotiationEngine.start/choose/availableOptions` (`:38, :97, :241` + `DemandResolver`);
`PersuasionEngine` (`:31, :58`); `ReactionLineProvider.pick` (`:30-51`); `DiaryComposer.compose`; seeds
`DigilineSeed.sessionSeed/selectionSeed/dayIndex/stableHash` (`model/EngineModels.kt:13-85`).
### 2.6 Wiring points
`AppContainer` exposes `digilineContentProvider`, `ownedDigilineRepository`, `digilineGateway` (`di/AppContainer.kt:46-48`);
`DefaultAppContainer` builds them (`:275-292`: provider `:275-278`, owned `:280-281`, wild `:283-286`, scripted gateway `:288-291`) and
passes the legacy gate to `ChatRepository` (`:252-255`). `AppServices` static accessors are used by both controllers (`di/AppServices.kt:29-30`).
Koin only resolves the container (`di/AppModule.kt`); DigiLine is service-located, not a Koin module.
### 2.7 Where a FreeChat would plug in (recommendation)
- **Add a sibling interface** (`FreeChatGateway`: observe scene, `sendFreeText(individualId, text)`, dismiss), not a method on `DigilineGateway`:
  `choose(optionId)` + the persisted-pending-question state machine do not fit free input, and changing the shared interface touches all four
  UI call sites (owned, wild, radar overlay, debug).
- Implement with `FreeChatRepository` (context + persistence) + **pure** `RetrievalFreeChatEngine` in a new `com.tamerslab.digiline.freechat`
  package; wire in `AppContainer.kt:46-48` + `DefaultAppContainer.kt:288-292` (+ `AppServices.kt:29-30`).
- Controllers gain `sendFreeText(...)` calling the new gateway, **not** `ChatRepository.sendMessage` (the LLM path; returns a canned fallback
  when legacy is off, `ChatRepository.kt:401-407`).
- UI: `ChatScreen.kt:259` becomes `if (legacy) legacyComposer else if (freeChatEnabled) freeComposer`; replies render as transcript rows
  (optionally a `REACTION` card with a new `contextKey`) so nothing collides with question/quiz/negotiation.

## 3. Persistence (Room schema 56)
### 3.1 Version, entities, migration
`@Database(version = 56, exportSchema = true)` (`data/database/AppDatabase.kt:95-97`), `ChatMessageEntity` at `:133`, `digilineDao()` `:263`,
`MIGRATION_55_56` `:266-270` → `DigilineSchema.migrate`. Migrations registered individually in `DefaultAppContainer.kt:65-120` with
`PreservingMigrationFactory` + integrity callback (`:121-124`). `DigilineSchema.kt` creates 9 tables idempotently (`:27-166`) and adds
`ChatMessageEntity.kind`/`payloadJson` behind `hasColumn` guards (`:174-177`). Schema JSON `app/schemas/…/56.json` (285 KB) exists; columns inspected. **[V]**
### 3.2 `ChatMessageEntity` — the transcript (the only place chat lives)
`domain/chat/ChatMessageEntity.kt:9-30`: `id` PK, `individualId` (FK → `DigimonIndividual` CASCADE), `role` (`user|assistant`; also `notice`/`system`
rows), `content`, `timestamp`, `isRead`, `kind: String?`, `payloadJson: String?`. DAO `chat/data/ChatDao.kt:9-37`: `insertMessage`, `getMessages`
Flow, `getMessagesSync`, `getLatestAssistantMessage` (`:20-21`), `getLatestUnreadAssistantMessage` (`:23-24`), `markAssistantMessagesRead`
(`:26-27`), branch deletes (`:32-36`). **No paged `LIMIT` query exists.** **[V]**
**Hard invariants [V]:** normal mode shows only `kind != null` (`ChatRepository.kt:64-65`); both list screens preview/subquery only
`kind IS NOT NULL` rows and count unread as `role='assistant' AND kind IS NOT NULL AND isRead=0` (`farm/data/DigifarmDao.kt:166-168`,
`world/data/WildRelationshipDao.kt:46-48`); a Storage row is listed only if such messages exist (`DigifarmDao.kt:174`).
`payloadJson` is declared+migrated but **written/read nowhere** (grep: entity `:29`, `DigilineSchema.kt:176-177`, loader comment) → cards are
not replayable; free chat can reuse `payloadJson` for rich rows **without a migration**.
### 3.3 DigiLine tables (`digiline/data/DigilineEntities.kt`, exact names)
`DigilineProfile` (PK `individualId`; `speciesVariant`, `lastAskedAt`, `lastQuizAt`, `lastShiftAt`, `sessionCounter`, `contentVersion`,
`nativeNoticeShown`) `:17-27`; `DigilineQuestionRecord` (`id`, `individualId`+index, `questionKey`, `kind` SPECIES/GENERIC/QUIZ, `askedAt`,
`answeredAt?`, `optionId?`, `shiftJson?`, `moodDelta`, `status` ASKED/ANSWERED/DISMISSED) `:30-42`; `DigilineQuizRecord` `:45-54`;
`PersonalityTendency` (PK `individualId`+`typeNumber`, `points`, `updatedAt`) `:57-63`; `NativePersonalitySync` (`targetType`, `targetNativeId?`,
`state` PENDING/WRITTEN/UNAVAILABLE_WRITE_PATH) `:66-73`; `DigilineNegotiation` (PK `wildIndividualId`, `tier`, `nodeId`, `phase`, `seed`,
`demandsJson`, `state`, `departUsed`) `:76-88`; `DigilineNegotiationLog` `:91-99`; `WildPersuasion` (PK `wildIndividualId`, `stageIndex`,
`topicsDoneJson`, `questOfferState` NONE/PENDING/OFFERED/ACCEPTED, `joinedAt`, `lastTalkAt`, `departureState`) `:102-111`;
`DigilineEventRecord` (`individualId`, `eventKey`, `deliveredAt`) `:114-120` — **no caller uses it today** (grep). `DigilineDao.kt` writes all
`@Upsert`; flows for profile/pending question/negotiation/persuasion/native sync. No free-chat/cadence columns exist; `sessionCounter` is shared
by the card flow, so reusing it for free chat would perturb card determinism.
### 3.4 Relationship rows
`WildRelationship` (`domain/digifarm/DigifarmEntities.kt:148-158`): PK `individualId`, `cardCharacterId`, `speciesNameSnapshot`, legacy `trust`,
`contactUnlockedAt?`, `recruitmentState`, `createdAt`, `updatedAt`; `updateTrust` still auto-unlocks at `trust > 75`
(`world/data/WildRelationshipDao.kt:28-29`, legacy path only; JOIN writes `unlockQuestContact` `:13-14`). `DigimonPersonalityTraits`
(`domain/personality/DigimonPersonalityTraits.kt:214-231`): PK `individualId`, `personalityType` (TEXT enum), `generatedAt`, `systemVersion`;
`CURRENT_PERSONALITY_SYSTEM_VERSION = 3` (`:9`). Owned mood is `UserCharacter.mood` (`domain/device_data/UserCharacter.kt:33`).
### 3.5 Migration rules a free-chat change must follow
Version bump + `exportSchema` JSON + `MigrationTestHelper` test + never destructive (`TESTING.md:839-841`); create tables with idempotent SQL in a
`*Schema.kt` object like `DigilineSchema` and register the migration in `DefaultAppContainer.kt:65-120`. **Gap:** this tree has no
`MigrationTestHelper` usage (grep) → v55→v56 is covered only by schema JSON, and a new v57 would inherit that gap. **[V/U]**

## 4. Reusable deterministic building blocks (already shipped)
| Block | Entry point | Notes |
|---|---|---|
| Seeds | `DigilineSeed.stableHash/combine/sessionSeed/selectionSeed/dayIndex` (`model/EngineModels.kt:13-85`) | FNV-1a+SplitMix64; no `String.hashCode`, no clock |
| Owned scheduler | `OwnedDigilineEngine.plan` (`:33-58`): species → quiz (if due) → generic → Idle | 12 h question / 24 h quiz (`OwnedDigilineRepository.kt:509-512`) |
| Shift rules | `PersonalityShiftEngine.apply` (`:54-121`): decay 10 %/day, cap 20, lead ≥4, ≥2 answers, 24 h cooldown | tendencies table + live type |
| Quiz | `QuizEngine.resolve/moodAfter` (+3/−5, floor 0) (`:18, :47`) | 30-day no-repeat window (`:512`) |
| Negotiation/persuasion | `NegotiationEngine`, `PersuasionEngine`, `DemandResolver` (tier caps, never last item) | keyed personality 1..16 × tier/stage |
| Event lines/diary | `ReactionLineProvider.pick` (`:30-51`), `DiaryComposer.compose` | only the diary is wired (plan §14.3 gap 4) |
| Content pack | `DigilineContentPack` (`model/DigilineContentPack.kt:50-196`): `speciesQuestion`, `genericQuestion`, `quiz`, `negotiationFlow`, `persuasionFlow`, `eventLines`, `loreFact` | linear scans/sorts; no index/map |
| Content loading | `DigilineContentLoader` (`content/DigilineContentLoader.kt:41-59`; `fromAssets` `:131-134`), cache `DigilineContentProvider.get()` (`:20-33`) | loader throws; provider degrades to empty pack; no runtime validator |
| Validation | `DigilineContentValidator.report/validate/requireValid` (`:40, 82, 93`) | tests only (`app/src/test/.../content/DigilineAssetPackTest.kt`) |
| Personality/appraisal | `DigimonSocialProfile.forIndividual` (`:50-76`), `SocialEventAppraisal.classify/delta/resolve` (`:7, 28, 39`), `DigimonRoleplayVoice.create` (`:109-132`) | deterministic trait vector; appraisal = word lists (en/pt/ja) |
| Wild personality | `WorldSocialRepository.profile(id)` (`world/ecosystem/WorldSocialRepository.kt:15-27`) | lazily generates + persists on a read path |
Shipped pack counts (counted from JSON this session **[V]**): species 263, generic 128, quiz 2,882, negotiation 64 flows/736 nodes, persuasion
64/384, event lines 65, lore facts 1,508; `species_catalog.json` 1,662 species; `species_chat_examples.json` (172 KB, 268 third-party-derived chats)
is still bundled and lazily loaded, but only legacy prompts read it (`SpeciesRepository.getConversationExamples :304-327`; wiring
`DefaultAppContainer.kt:234-237`) — **not** a legal corpus (D1).

## 5. Context sources available today (Digimon-contextualised free chat)
### 5.1 Partner identity (all cheap, PK-indexed unless noted)
| Data | Source at runtime | Notes |
|---|---|---|
| `individualId` | `DigimonIndividual.individualId` PK (`domain/device_data/DigimonIndividual.kt:13`); `UserCharacter.individualId` (`:30`) | permanent identity; FK target of transcript |
| nickname | `DigimonIndividual.nickname` (`:15`); shown in thread rows (`DigilineScreen.kt:161`) | nullable |
| species name | `DigimonIndividual.speciesName` (`:30`, backfill `DigimonIndividualDao:28-47`); fallback `SpeciesProfile.speciesName`/`matchedName` | survives card deletion |
| card identity/species profile | `UserCharacter.charId` (`:31`) → `SpeciesProfile` PK `cardCharacterId` (`domain/species/SpeciesProfile.kt:15-29`: `level`, `type`, `profileDescription`, `specialMoves`, `source`, `appearanceKey`), read via `SpeciesRepository.getProfileForCharacter(cardCharacterId)` (`species/SpeciesRepository.kt:261-262`) | single PK query, **no network** |
| stage/attribute | `CharacterWithSprites.stage/attribute` (`dtos/CharacterDtos.kt:8`), loaded by `UserCharacterDao.getCharacterWithSprites(id)` (`:208`) / `getCharacterInfo(charId)` (`:332`) | used by legacy persona prompt |
| species key / tier | `DigilineStages.speciesKey(name)` (`digiline/data/DigilineStages.kt:46-49`) → `normalizeSpeciesKey` (`DigilineContentPack.kt:193-194`); `DigilineStages.tier(cardStage, level)` (`:42-43`, mapping `:17-40`) | T1..T4, fallback 2 |
| aliases/canonical record | `CanonicalSpeciesCatalog` (`species/CanonicalSpeciesCatalog.kt:24-36`; alias index `:58-90`, `resolve` `:87-88`) from `species_catalog.json` (`id`,`name`,`aliases`,`level`,`type`,`profile`,`specialMoves`) | parsed lazily from assets (`BundledDotSpriteLibrary.catalog()` `:35-44`, 13.3 MB file) |
| wild identity | `WildRelationship.cardCharacterId/speciesNameSnapshot` (`DigifarmEntities.kt:151-152`); `CharacterDao.getWildCharacterInfo(cardCharacterId)` (`:86`, DTO `CharacterDtos.kt:453`); `WorldSpawn.mood/ecosystemEmotion` (`domain/world/WorldSpawn.kt:41, 61`) | wild species assets can be missing (handled at `ChatRepository.kt:671-680`) |
| owned mood (bond, D2) | `UserCharacter.mood` (`:33`); `UserCharacterDao.observeMood(id)` (`:210-211`) | 0-100, also moved by battles/injuries |
### 5.2 Personality (already first-class)
- Persisted type `DigimonPersonalityTraits.personalityType`: 16 enum values with `number`, localized `displayName`, `promptInstruction`,
  `observation` (`DigimonPersonalityTraits.kt:18-214`; entity `:214-231`); PK read `DigimonIndividualDao.getPersonality` (`:18-19`).
- Cores: `PersonalityShiftEngine.coreOf` (`:122-133`) / `DigilineContentPack.CORE_NAMES` (`:181`); 16 names + quadrant ranges per
  `DIGILINE_IMPLEMENTATION_CONTRACT.md:218-228`. Semantic trait vector: `DigimonSocialProfile.forIndividual` (`:50-76`, fields `:4-16`,
  stable per-individual jitter `SocialRandom.unit :81-89`).
- Player-text appraisal (reusable tone/mood): `SocialEventAppraisal.classify` (PRAISE/CARE/CHALLENGE/TEASING/REFUSAL/INSULT/AGREEMENT, keyword
  lists en/pt/ja `:7-26`), `delta` (`:28-37`), `resolve` (`:39-50`).
- Shift tendency: `DigilineDao.tendencies/observeTendencies` (`:107-111`); write path `OwnedDigilineRepository.applyShift` (`:342-362`) +
  `NativePersonalityMapping` (`digiline/native/TimeStrangerPersonalitySync.kt:19-30`). Wild uses the same table, lazily generated from
  attribute/stage (`DigimonPersonalityGenerator.generate :54-85`, `WorldSocialRepository.profile :15-27`).
### 5.3 Recent context
| Context | Cheapest existing read | Cost note |
|---|---|---|
| Transcript | `ChatDao.getMessages` Flow / `getMessagesSync` (indexed `individualId`; latest-assistant `:20-24`) | full-history sync unbounded; add a `LIMIT :n` query for free chat (none exists) |
| Cadence/relationship | `DigilineProfile` (`DigilineDao.kt:22-32`); `WildPersuasion` (`:169-182`); session (`:138-151`) | PK reads |
| Mood | `UserCharacterDao.observeMood` (`:210-211`) / `getCharacter` (`:213-214`) | single column Flow |
| Battle/injury/age/vitals | same `UserCharacter` row (`:32-46`: age, mood, vitals, injury, battle counters) | one PK read |
| Evolution history | `EvolutionHistoryDao.getPromptHistory(characterId)` (`storage/data/EvolutionHistoryDao.kt:37-48`) | joins TransformationHistory/CardCharacter; legacy prompt only |
| Recent events | `ReactionRepository` diffs a `DigimonStateSnapshot` (`chat/ReactionRepository.kt:22-73`) | snapshot deleted after evaluation; no generic event log |
| Quests | `QuestRepository.observeContact/giverQuests/unfinishedTargets` (`quests/QuestRepository.kt:30-33`), `contextForContact/Partner` (`:724-750`) | DAO accessors are non-suspend → call on IO |
| Currency/items | `CurrencyRepository.currencyValue` Flow (`items/data/CurrencyRepository.kt:27`); `ItemDao.getAllUserItems`/`getItem` (`items/data/ItemDao.kt:27-43`) | whole-inventory flows heavy; prefer single-item checks |
| Battle memories | `WorldChatMemoryDao.getMemories(id)` (latest 6) (`chat/data/WorldChatMemoryDao.kt:11-12`) | written by legacy pipeline → may be empty |
| Authored line pool | `DigilineContentPack.eventLines(event, type)` (65 lines) + 1,508 lore facts | in-memory after pack load |
### 5.4 Cost model and a `FreeTalkContext` seam
- **Cheap (~sub-ms, PK/indexed):** `SpeciesProfile` by `cardCharacterId`, `DigimonPersonalityTraits`, `DigimonIndividual`, `UserCharacter`,
  `DigilineProfile`, `WildRelationship`, `WildPersuasion`, currency flow — safe per message. **[V]**
- **Medium:** transcript reads (`getMessagesSync` full scan; per-thread Flow fine for rendering); `getCharacterWithSprites` joins sprite/name blobs —
  prefer `getCharacter` + `getCharacterInfo`.
- **Expensive — avoid per message:** `SpeciesRepository.getAllSpeciesEntries()` (`:266-284`) because `fetchDatabase()` (`:486-509`) may perform a
  **network** GET (24 h TTL, cache/asset fallback) and merges 1,662 canonical entries; `LorebookRepository.scanForMatches` (`:28-69`) iterates
  `DigimonWorldLore` + DB lorebook + **all species** per legacy message; first `CanonicalSpeciesCatalog` parse = 13.3 MB JSON
  (`BundledDotSpriteLibrary.kt:41-43`); `QuestRepository.contextFor*` string assembly; content-pack first load parses **2.80 MiB** in one call
  (`DigilineContentLoader.kt:41-56`). **[V]**
- **N+1:** thread lists already use one SQL with correlated subqueries (`DigifarmDao.kt:161-178`, `WildRelationshipDao.kt:41-54`); batch any
  per-partner context the same way.
- **Seam:** pure value object + assembler in the new package:
  `FreeTalkContext(individualId, speciesKey, speciesName, level, type, attribute, stageTier, personalityType, core, socialProfile, mood,
  injuryStatus, recentTranscript: List<ChatMessageEntity>, recentEventKeys, wildRelationship?, persuasion?, questSummary?)`, assembled from ~6
  cheap reads, with static species/personality/tone parts cached in-process by `individualId`/`cardCharacterId` + `contentVersion`. Do **not**
  put it in `DigilineContentPack` (content-only, shared). Owned and wild can share it (wild: relationship + persuasion instead of mood/quiz).
### 5.5 Theming surface already available
- Scene renderer `DigilineSceneCard` (`chat/ui/DigilineSceneCard.kt:40-138`): `contextKey` framing (`:49`, maps `:140-149`), `reactionLine`,
  `lines`, `demandBits`, `outcomeKey` (`:151-158`), options as `VitalButton`, per-kind dismiss label (`:123-130`), polite live region (`:61`).
- Chrome: `ChatHistoryPanel` / `ChatMessageBubble` / `ChatComposer` (`components/ChatConversationUi.kt:98, 127, 187`) — plain-text bubbles; empty
  text per mode (`digiline_chat_empty` `values/strings.xml:1140` vs `ui_chat_empty` `:432`); placeholder `:431`; idle hint `:1138`.
- Strings: **37** `digiline_*` keys in each of `values`, `values-pt-rBR`, `values-ja` (counted **[V]**), incl. 6 `digiline_context_*` framings
  (`:1144-1149`), outcomes, sync notices, legacy badge `:1153`. Locale helper `PromptLocalization.currentLanguageTag()`
  (`chat/PromptLocalization.kt:6-9`); new free-chat strings need all three locales (or explicit en fallback).
- Digimon terminology: species `level`/`type` are English canonical strings in `species_catalog.json`/`SpeciesProfile`;
  `LorebookRepository.formatSpeciesContent` (`:152-163`) localizes labels only, not values. Locale-aware technique names exist
  (`battleTechniqueName`, `TESTING.md:99`) but are not a species-context API — **[U]** for theming use.
- Stale copy to fix/avoid: trust wording in `ui_digiline_empty_wild` / `ui_digiline_trust` (all locales, §1).

## 6. Hard constraints / invariants a free-chat feature must respect
1. **Offline/no network.** LLM chat is off unless Debug+legacy (`ChatRepository.kt:43`, `DefaultAppContainer.kt:252-255`); plan §3.1 forbids HTTP
   in DigiLine/social/quest paths. `INTERNET` is still declared (`AndroidManifest.xml:17`) and non-chat code does network
   (`SpeciesRepository.kt:33-34, 486-509`, `RetrofitHelper` in `di/AppModule.kt`) — free chat must not call those or add clients. No CI import
   gate exists yet for `OpenRouter`/`ChatRepository` (plan §8.3 lists it; `.github/workflows/ci.yml:39-53` = build/tests/lint only). **[V/U]**
2. **Determinism + anti-reroll.** Persist the decision before showing it (precedent `OwnedDigilineRepository.kt:102-137`); no `Random.Default`,
   no wall clock in selection; use `DigilineSeed` + a persisted free-chat turn counter — not transcript length (deletes/regenerate change it).
3. **Schema rules.** New column/table ⇒ version bump, new `app/schemas/…/57.json`, raw-SQL migration mirror, migration registration,
   `MigrationTestHelper` test (`TESTING.md:839-841`). `payloadJson` already exists if per-row JSON is wanted (`DigilineSchema.kt:174-177`).
4. **Architecture boundaries.** `ArchitectureBoundaryTest.kt:8-58` keeps `domain/usecase`+`domain/repository` Android/Room/DI-free; keep new
   engines free of `android.*`/Room (current `digiline/engine`, `digiline/model` comply **[V]**).
5. **R8/release.** Release uses R8 + shrink (`app/build.gradle.kts:70-76`), CI assembles release; `com.tamerslab.chat.**` is kept
   (`proguard-rules.pro:10`); pack DTOs need no explicit keep (generated serializers + `**$$serializer` members kept, `:49-51`); if free-chat
   content uses Gson/reflection instead, add a keep rule (precedent `:24-25`).
6. **Content-load perf.** Plan targets ≤250 ms cold load and ≤2 MB/locale (`DIGILINE_CHAT_SYSTEM_PLAN.md:299, 823`); shipped pack is
   **2.80 MiB**, parsed eagerly in one call (`DigilineContentLoader.kt:41-56`); adding a file raises first-use cost; no measured cold-load
   number exists **[U]**. Build the retrieval index once at load, never per message.
7. **Locale strategy.** D9 = EN MVP, locale-ready; loader loads only `manifest.locale`, no runtime fallback (`DigilineContentLoader.kt:44-45`;
   manifest `locale:"en"`). Matching pt/ja input against EN-only content is an open product decision; UI strings already exist in 3 locales.
8. **Content gates.** New pack files must pass the Kotlin asset gate (`DigilineAssetPackTest`: real assets → loader → validator; asserts 64/64
   flows, `contentVersion >= 3` **[V]**) and `python tools/digiline/validate_packs.py` (plan §14.4). Runtime does **not** validate — provider
   degrades to an empty pack (`DigilineContentProvider.kt:20-33`), so a broken free-chat file silently removes the mode.
9. **Transcript/preview/unread.** Set non-null `kind` for both roles; respect `isRead`/`markAssistantMessagesRead`; list visibility requires the
   kind filter (§3.2). Unread counts every assistant row, so don't write debug echoes as assistant messages.
10. **Cadence/anti-farm.** Existing caps only cover cards (12 h/24 h/30 days, `OwnedDigilineRepository.kt:509-512`); no mood daily cap (R5).
    Free chat is unlimited input → needs its own reply-rate cap and a mood-delta policy (default 0 or bounded, else it becomes an uncapped bond
    farm that contradicts D2/D8/R5).
11. **No silent personality changes.** Type changes only via `PersonalityShiftEngine` + hysteresis + native-sync queue; free chat must not write
    `DigimonPersonalityTraits` (would bypass `PersonalityTendency`, 24 h cooldown, battle-AI notice D3/D4).
12. **Tests.** 15 digiline test files / 121 `@Test` (counted) cover pure engines + asset pack; CI runs `:app:testIntegrityCheckUnitTest` +
    debug/release assemble + lint (`.github/workflows/ci.yml:39-53`). No gateway/controller/repository test and no migration test exist (§3.5) —
    add pure-engine tests plus at least one repository-level test with fake content.

## 7. Integration risks / open questions
1. **Scene-vs-free-text collision (top risk):** a pending authored card is persisted and `choose()` is keyed off it (`OwnedDigilineRepository.kt:145-155`,
   `WildDigilineRepository.kt:71-81`). Decide: free chat allowed while a card is pending (recommended, card above composer), queued behind the card,
   or replacing card cadence. Free-chat rows must never enter that state machine.
2. **Unread/preview semantics:** every assistant reply bumps unread and becomes the list preview; if free chat should be “quiet”, write `isRead=1`
   or exclude its kinds from both list SQLs (needs query changes).
3. **Transcript rendering:** bubbles are plain text (no markdown/rich payload; `payloadJson` unused). Decide truncation/typography/multi-line
   “action in asterisks” handling that authored scene layout hides today.
4. **Cadence/UX expectation:** DigiLine is a “from time to time” authored experience; an always-available text box changes the product rhythm and
   risks loops/notification spam (game-design call).
5. **Test/content impact:** `DigilineAssetPackTest` hard-asserts 64/64 negotiation+persuasion flows; adding a manifest file touches
   `DigilineContentFiles.REQUIRED_KEYS` (`content/DigilineContentSource.kt:22`) and the loader's all-required check (`:99-104`) — prefer an
   **optional** manifest key so existing packs still load, and extend loader/pack/validator.
6. **Memory:** 13.3 MB catalog + 2.8 MB packs + 2,882 quiz cards (~1.25 MB) are already a pressure point (plan §14.3 gap 7); a large free-chat
   corpus in the same eager pack is risky — consider a separate lazily loaded file/index.
7. **Locale mismatch:** EN-only content vs pt/ja players (D9); `SocialEventAppraisal` word lists are per-language, so an English matcher will
   underperform for pt/ja input.
8. **Content ownership unresolved:** R11 (`DIGILINE_CHAT_SYSTEM_PLAN.md:90`); D1 forbids shipping the third-party `species_chat_examples.json` lines.
9. **Delete/regenerate semantics:** `deleteFromMessage`/branch deletes exist (`ChatDao.kt:32-36`); deterministic replies must tolerate truncated
   history (persist the turn counter separately).
10. **Native-save coupling:** only personality is synced (`NativePersonalitySync`); free chat must not imply save writes; mood stays `UserCharacter.mood` (D2).

## 8. Recommendation (where FreeChat plugs in, concretely)
1. **New package** `com.tamerslab.digiline.freechat`: `engine/RetrievalFreeChatEngine` (pure: text → intent/tone → scored authored candidates →
   reply id, seeded by `DigilineSeed.sessionSeed(individualId, dayIndex, turnCounter, contentVersion)`), `data/FreeChatRepository` (context
   assembly + persistence in one Room transaction), `content/FreeChatPack` (optional asset file + validator), `FreeChatGateway` interface.
2. **Do not modify `DigilineGateway` or the scripted repositories**; implement the new gateway alongside them and wire it in `AppContainer.kt:46-48`
   + `DefaultAppContainer.kt:288-292` (+ `AppServices.kt:29-30`). Authored path stays untouched/testable.
3. **Persistence:** extend `DigilineProfile` with `freeChatTurnCounter`/`lastFreeChatAt` (schema **57** + JSON + migration + test); write
   `ChatMessageEntity` rows with kinds `freechat_user`/`freechat_assistant` (+ optional `payloadJson` with matched entry id/score); never touch
   `DigilineQuestionRecord`/`sessionCounter`. Mood delta default 0, bounded ±1 with a per-day cap if bond movement is wanted.
4. **UI:** `ChatScreen.kt:259` composer branch and `WorldChatScreen.kt:256` (wild contacts; owned first), reusing `ChatComposer`/`ChatMessageBubble`;
   replies appear in transcript; optional `REACTION` scene for larger authored responses with a new `contextKey` + 3-locale strings.
5. **Context:** implement `FreeTalkContext`/`FreeChatContextAssembler` per §5.4; cache static parts; add a `LIMIT` transcript query to `ChatDao`.
6. **Content:** `assets/digiline/v1/en/free_chat.json` (optional manifest key), authored across the context dimensions already available (species
   identity/level/type/attribute/specialMoves/profile, 16-type personality + core, mood band, recent-event tags). Validator gates: unique ids,
   ≥N entries per personality, species-key coverage, no third-party lines, locale completeness.
7. **Tests:** determinism/coverage/no-crash-on-empty engine tests; assembler test with fake DAO/Room; asset gate; migration 56→57; extend the
   manual acceptance list (`TESTING.md:844-866`) with free-chat TalkBack/large-font/legacy-isolation cases.

## 9. Not verified / limits of this audit
- No build, Gradle, device/emulator or profiling run: runtime timings (cold content load, per-message cost) and real UI behavior are unmeasured.
- `WorldConversationScreen` and the legacy radar private-chat path were skimmed, not audited line-by-line.
- Cross-locale free-text behaviour for pt-BR/ja input and the exact `species_catalog.json` `type` value set were not exhaustively analyzed.
- Plan claims (e.g. 113 digiline unit tests, 250 ms load) were not reproduced; this audit counted 121 `@Test` and static asset sizes instead.
- The v55→v56 migration-test status (§3.5) is a source-tree grep result; a test outside the searched patterns cannot be fully excluded.
