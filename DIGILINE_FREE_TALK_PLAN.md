# DigiLine Free Talk ("Papo") â€” Feasibility & Implementation Plan
### A SimSimi-style, Digimon-contextualized, offline companion chat â€” without an LLM

**Status:** feasibility study + implementation plan **v1.3** â€” 2026-11-18 (MVP implemented and verified; see Â§17)
**Revision note (2026-11-17):** after the owner requirement *"use a big response database like the ones in the
research so chat feels more free"*, the content strategy was revised from a small curated corpus to a **big,
Digimon-contextualized corpus** built from **hybrid sources** (public dialogue seeds + reviewed frames + generated
lines) â€” see Â§7.6 and [`analysis/simsimi-free-chat/05-big-corpus-strategy.md`](analysis/simsimi-free-chat/05-big-corpus-strategy.md).
**Owner override (same day):** this is a **personal, non-distributed build**; licensing is now a **distribution
gate**, not a shipping blocker. The plan may use public corpora as offline seeds/patterns, and a mandatory
"strip licensed content/assets before any distribution" gate is part of the deliverable (Â§7.6, Â§15.1.1).
**Scope:** Android app **Tamers Lab** (`app/`), owned Digimon DigiLine threads; free-text conversation
**Relationship to existing plans:** extends [`DIGILINE_CHAT_SYSTEM_PLAN.md`](DIGILINE_CHAT_SYSTEM_PLAN.md) (the
implemented, deterministic, LLM-free card system) and re-opens only one point of it â€” flaw **F19** ("the composer
was removed") â€” for a bounded, authored free-talk mode. It does not re-open **D10** (legacy LLM stays a debug-only
sandbox).
**Research input:** [`simsimi_style_chatbot_research.md`](C:/Users/julye/Downloads/simsimi_style_chatbot_research.md)
(SimSimi/retrieval research brief, user-provided).
**Supporting analysis:** [`analysis/simsimi-free-chat/01-current-system.md`](analysis/simsimi-free-chat/01-current-system.md)
Â· [`02-content-data.md`](analysis/simsimi-free-chat/02-content-data.md)
Â· [`03-tech-feasibility.md`](analysis/simsimi-free-chat/03-tech-feasibility.md)
Â· [`04-game-design.md`](analysis/simsimi-free-chat/04-game-design.md)
Â· [`05-big-corpus-strategy.md`](analysis/simsimi-free-chat/05-big-corpus-strategy.md)
**Required product constraints (owner, 2026-11-17):**
1. The feature must be **Digimon-contextualized and themed** â€” it must read unmistakably as conversation with *this*
   Digimon partner inside the Digital World, never as a generic assistant or a stock SimSimi bot. Â§6 is the
   enforceable contract.
2. The chat must have a **big response database** so it feels free â€” resolved in Â§7.6 as a large *own* corpus
   (reviewed frames + generated validated lines + combinatorial rendering + 20kâ€“50k match phrases), not by
   bundling the research datasets (their licenses forbid shipping; Â§2.2/Â§7.6).

Legend: **[M]** measured in this pass Â· **[V]** verified in repo/schema Â· **[D]** from the existing plan/decisions
Â· **[E]** estimate with its basis stated Â· **[OPEN]** needs a product-owner decision.

---

## 0. Verdict

**Feasible â€” yes, with a narrower promise than SimSimi.**

| Question | Answer |
|---|---|
| Can the player chat with their Digimon in DigiLine, offline, with no LLM? | **Yes.** Authored intent/reply corpus + entity-aware lexical retrieval + diegetic fallback ladder; runs fully offline and deterministically. |
| Can we reproduce classic SimSimi? | **No â€” and we should not promise it.** SimSimi's magic is a crowdsourced corpus of millions of user-taught pairs. This project forbids user teaching and third-party text (D1), ships offline, and must stay lore-grounded. Open-domain "answers anything" is out of scope by design. |
| Can it *feel* SimSimi-like? | **Yes, partly:** instant short replies, a cheeky partner voice, variety through multiple authored variants, and surprising-but-relevant callbacks to the Digimon world. The trade is breadth of understanding for voice and safety. |
| On-device sentence embeddings (the research brief's recommendation)? | **Infeasible in this app** â€” 14.03 MB APK headroom vs â‰¥30â€“120 MB realistic delta; Â§8.1 has the measured numbers. Replaced by a build-time lexical index. |
| What is the critical path? | **Content pipeline + its owner (R11), not code.** The big-corpus strategy replaces "hand-write ~1.5k lines" with reviewed **frames** + a **generated/validated corpus**: curated core ~800â€“1,500 lines (MVP), then Big v1 â‰ˆ **20k response lines + 20k match phrases + ~2k frames** (â‰ˆ3â€“5 MB APK delta) at launch, growing to 50k. Content volume still dominates; Â§7.6 has the size/effort math. |
| Can we use a big response database "like the ones in the research"? | **Not their text â€” but yes, a big own database.** Cornell/DailyDialog/EmpatheticDialogues/Ubuntu/OpenSubtitles are research-only, NC, or copyrighted (Â§2.2, verified). Only SODA (CC BY 4.0, 1.48M dialogues) and OASST1 (Apache-2.0, 161k messages incl. 2,969 pt-BR) are license-clean, and their content is generic/real-world â€” usable to **mine intents/phrasings**, never to ship as Digimon conversation. The big corpus is generated + frame-composed for this project (Â§7.6). |
| Overall recommendation | **Build "Papo" (Free Talk)**: a bounded, in-world conversation mode on owned DigiLine threads, phased MVP â†’ launch, with the authored card scenes unchanged as the progression layer. |

**Non-negotiable defaults** (recommended, Â§15): owned threads only in MVP; no personality shift from free text; at
most a tiny, capped positive mood effect; no economy rewards; no wild-contact free talk; no regeneration; no
network; all output authored and reviewed; fail closed.

---

## 1. What was asked and how it was evaluated

Owner request: *search, analyse and plan the possibility of the user chatting with Digimon in DigiLine through a
system similar to SimSimi, fitting it into the current project and its existing chat features, from an app/game
designer point of view â€” and the feature must be Digimon-contextualized and themed.*

The work was split into four independent audits, all statically verified against this repository (no build/device
run), then synthesized here:

| Workstream | Deliverable | Bottom line |
|---|---|---|
| Current system / integration seams | `01-current-system.md` | Clean gateway seam exists; card state machines and free text must not share state; one transcript table; Room v56; rich Digimon context data already available cheaply. |
| Content & data | `02-content-data.md` | Existing packs give style/patterns, not a shippable free-talk corpus; D1 forces our own prose; curated core ~800â€“1,500 lines, big corpus Â§7.6; content owner is the blocker. |
| Technical feasibility | `03-tech-feasibility.md` | Entity-aware lexical hybrid (exact + integer BM25 over a build-time index) is the right fit; embeddings rejected with measured sizes; APK delta 0.3â€“1.1 MB; per-turn 2â€“6 ms. |
| Game design / UX | `04-game-design.md` | A bounded "Papo" mode: partner not oracle, authored failure is content, chips + handoff, conservative relationship coupling, structural safety. |

The recommendation below is the Lead's synthesis; where the four studies disagreed (Â§7.2 locale order, Â§8.2
index shape, Â§5.3 mood scope) the decision is called out explicitly.

---

## 2. Search findings: SimSimi, the research brief, and what transfers

### 2.1 How classic SimSimi actually worked [S]

- Launched 2002 (Korean, "simsim" = bored); a small app (7.6 MB) that matched an incoming message against a
  **crowdsourced questionâ†’answer database** and returned a stored answer; users could "teach" new answers.
- The remembered experience: instant, short, cheeky, sometimes nonsensical replies; the fun came from volume and
  from poking at the bot. It was **not** an assistant and had no lore consistency, memory, or safety model.
- **Safety history that matters for us:** banned in Thailand in 2012 after users taught it profanity and political
  mockery; **suspended in Brazil in April 2018** over sexual language, bullying and death threats, described as
  "dangerous" mainly because of its popularity with children
  ([Wikipedia](https://en.wikipedia.org/wiki/SimSimi)).
- Tamers Lab has the same two risk multipliers (Digimon fandom skews young; the product is a companion) and almost
  none of SimSimi's mitigation budget (no server ML). Safety must therefore be **structural**: no teaching, no
  generation, no network, curated output.

### 2.2 The user's research brief: what is usable, what is not

| Brief proposal | Verdict for this project | Adopted here |
|---|---|---|
| Retrieval (not generation) over a prebuilt Qâ†’A collection | **Adopt** â€” matches the project's no-LLM/offline principles | Intent/reply corpus with staged retrieval (Â§8.2) |
| Curated dataset, no user teaching | **Adopt** â€” removes SimSimi's abuse vector | No teaching, no crowdsourcing (Â§6.4) |
| Multiple responses per input, random among good matches | **Adopt** â€” the anti-repetition core | Variant pools + no-repeat window + seeded pick (Â§7.3) |
| Similarity threshold + neutral fallback | **Adopt**, but the fallback must be in-character | 5-rung diegetic ladder (Â§8.3) |
| DailyDialog / Cornell / Ubuntu / EmpatheticDialogues / OpenSubtitles as corpus | **Reject for shipping** â€” verified licenses/status (2026-11-17): Cornell = no license + movie-script copyright; DailyDialog = CC BY-NC-SA 4.0; EmpatheticDialogues = CC BY-NC 4.0; Ubuntu = no explicit data license + technical register; OpenSubtitles = varies/copyright. A Play app cannot rely on NC/SA terms, and translation/adaptation is a derivative | **Pattern references only**, never text (Â§7.1, Â§7.6) |
| Big licensed corpora instead | **SODA (CC BY 4.0, 1,486,896 dialogues)** and **OASST1 (Apache-2.0, 161,443 messages, 35 locales incl. pt-BR)** are license-clean but generic/assistant-style, so their text is out-of-world for a Digimon partner | Use to **mine dialogue acts + paraphrase phrasings** (esp. OASST1 pt-BR prompter turns), never ship verbatim (Â§7.6) |
| Sentence Transformers + FAISS/Qdrant semantic retrieval | **Reject on-device** (Â§8.1) â€” measured APK/model budgets; no server allowed | Integer BM25 + entity lexicon, build-time index |
| "Can work without an LLM API" | **Confirmed** â€” the implementation path here has zero network calls | Â§8.2, Â§12.4 |

**Owner override (2026-11-17):** the licensing analysis above is kept as the **distribution gate**, not a blocker for
the personal build. The owner takes responsibility and will remove every licensed content/asset if the project is
ever distributed. The implementation therefore uses a **hybrid corpus** (public dialogue seeds â†’ offline rewriting,
plus authored frames and generated lines) and must ship a `DISTRIBUTION_GATE.md` checklist that enumerates every
licensed/temporary source and the removal/rewrite step before any release.

### 2.3 The experience gap, stated honestly

SimSimi's perceived intelligence came from *coverage*; a curated corpus gives *voice and relevance* instead.
Therefore this plan replaces the open-domain promise with a bounded one (Â§4.2): the partner talks about itself,
its world, the player's team and what they have actually done together, and **admits when it does not know** â€” in
character. That admission is authored content, not an error state.

---

## 3. What exists today (audited baseline)

### 3.1 The shipped system [V]

DigiLine v2 is implemented and LLM-free in normal play (`DIGILINE_CHAT_SYSTEM_PLAN.md` Â§14):

- **Owned threads**: authored card scenes â€” species question, generic question (16 per personality type), quiz
  (2,882 catalog-derived cards) â€” with mood-as-bond, personality shift and native-save sync queue.
- **Wild contacts**: negotiation (16 personalities Ã— 4 tiers, 736 nodes) and persuasion arcs, replacing the old
  trust meter; Radar renders the same scene card over the live world.
- **No free text in normal play**: `ChatComposer` is rendered only when the debug legacy-LLM switch is on
  (`ChatScreen.kt:259-281`). Old LLM rows are a read-only archive.
- **Content packs**: `app/src/main/assets/digiline/v1/en/` â€” manifest `contentVersion 4`, 7 JSON files | 2,936,586 B
  raw [M] (the plan's budget said â‰¤2 MB â€” already exceeded before this feature).
- **Persistence**: Room **v56**; `ChatMessageEntity(id, individualId, role, content, timestamp, isRead, kind?,
  payloadJson?)`; `payloadJson` is migrated but currently unused anywhere [V].
- **Stack**: Kotlin 2.4.0, Compose, Room 2.8.4, Koin 4.1.1, minSdk 28 / compile-target 37, R8 + resource shrink,
  baseline-profile and benchmark modules [M].
- **Release budget**: APK ceiling 163,053,568 B; recorded release APK 149,019,234 B â†’ **14,034,334 B (13.4 MiB)**
  headroom [M, `docs/adr/0005-performance-budgets.md`, `docs/performance/BASELINE.md`].

### 3.2 Integration seam [V]

```
ChatScreen â”€â”€â–º ChatScreenControllerImpl â”€â”€â–º AppServices.digilineGateway
                                                â”‚
                                ScriptedDigilineGateway (default, offline)
                                   â”œâ”€â”€ OwnedDigilineRepository  (questions/quiz/shift/mood)
                                   â””â”€â”€ WildDigilineRepository   (negotiation/persuasion/recruit)
                                                â”‚
                                   DigilineGateway { observeHistory, observeScene,
                                                     ensureScene, choose, dismiss }
```

- The card flow is driven by a **persisted pending-question row**, and `choose()` is keyed off it â€” free text must
  not enter that state machine (`OwnedDigilineRepository.kt:145-155`).
- History/preview/unread depend on `kind != null`: list previews and list visibility filter on `kind IS NOT NULL`
  and unread counts on `role='assistant' AND kind IS NOT NULL AND isRead=0`
  (`farm/data/DigifarmDao.kt:166-168`, `world/data/WildRelationshipDao.kt:41-54`).
- A sibling interface (new `FreeChatGateway`), not a method on `DigilineGateway`, is the clean seam: it leaves all
  four card call sites (owned, wild, Radar overlay, debug) untouched (Â§9.2).
- Digimon context is **already cheap** to assemble: species identity/catalog, stage tier, 16-type personality + core
  + roleplay voice + social profile, mood, transcript, wild relationship/persuasion, quest state, recent events.
  One caveat: `SpeciesRepository.getAllSpeciesEntries()` may hit the **network** (24 h cache) and must never be on
  the chat path; use the bundled catalog + cached lookups instead.

### 3.3 Constraints any free-chat design must respect [V]

| # | Constraint | Consequence |
|---|---|---|
| 1 | Offline / no network in gameplay | Chat path must not call `SpeciesRepository.fetchDatabase()`, `RetrofitHelper`, or any new client |
| 2 | Determinism + anti-reroll | Persist the chosen reply/seed **before** display; no `Random.Default`, no clock in selection |
| 3 | DB migration rules | Any table/column â‡’ v57, schema JSON, mirror migration, migration test, never destructive |
| 4 | Architecture boundaries | Pure engines must stay `android.*`/Room-free (`ArchitectureBoundaryTest`) |
| 5 | R8 / release | No new keep rules needed if kotlinx.serialization is used; verify after merge |
| 6 | Content-load budget | â‰¤250 ms cold / â‰¤2 MB per locale intent; current pack already 2.80 MiB â†’ **free chat needs its own lazy pack** |
| 7 | Locale strategy | D9: EN MVP, locale-ready schema; loader has no runtime fallback |
| 8 | Content gates | New assets must pass `tools/digiline/validate_packs.py` + the Kotlin `DigilineAssetPackTest` gate |
| 9 | Transcript invariants | Non-null `kind` for normal-mode visibility; unread semantics must be chosen deliberately |
| 10 | Cadence / anti-farm | Unlimited input needs its own reply-rate cap and a bounded mood policy |
| 11 | No silent personality change | Type changes only through `PersonalityShiftEngine` + hysteresis + native sync (D3/D4) |
| 12 | Tests | Pure-engine tests + repository test + migration test; CI runs unit tests, debug+release assemble, lint |

### 3.4 Debt found during the audit (fix while touching this area) [V]

1. Free text only exists behind the legacy LLM toggle; the debug composer still calls `ChatRepository.sendMessage`.
2. `ui_digiline_empty_wild` still promises "raise a wild Digimon's trust above 75" in en/pt/ja â€” contradicts D6.
3. pt-BR `digiline_demand_bits` ("Ele querâ€¦") and `digiline_outcome_join` ("Ele entrouâ€¦") violate the plan's
   neutral-gender rule (Â§5.5) â€” the free-talk copy must not inherit this.
4. `payloadJson` and `DigilineEventRecord` are unused; `ChatDao` has no `LIMIT` query; `getMessagesSync` is
   unbounded.
5. No migration test exists for v55â†’v56; a new v57 inherits that gap unless added.
6. No CI import gate keeps `OpenRouter`/`ChatRepository` out of normal paths (proposed in the old plan, not built).

---

## 4. Product design: the bounded promise

### 4.1 Design pillars (testable)

| # | Pillar | Acceptance test |
|---|---|---|
| P1 | **In-world first** | Every line could only be said by a Digimon to its Tamer in the Digital World; UI reads as a DigiLine link, not a messenger |
| P2 | **Partner, not oracle** | Never claims knowledge it has no authored/cited source for; "I don't know" is in character, never an error |
| P3 | **Personality you can hear** | A blind reader can identify personality band (and that it is a Digimon) from a set of lines |
| P4 | **Short, alive, a little cheeky** | 1â€“2 lines, â‰¤ ~120 chars, no filler, no stock-assistant warmth; SimSimi's tempo, Digimon's voice |
| P5 | **Authored truth** | No invented lore, memories, or real-world facts; species claims cite the catalog/lore registry; offline |
| P6 | **Failure is content** | Every input ends on an in-character line, a chip row, or a real authored card â€” no dead ends |

### 4.2 The promise shown to players (onboarding + Help, not fine print)

> *Papo no DigiLine â€” seu parceiro fala do mundo dele e do que viveu com vocÃª. NÃ£o Ã© um assistente: pode nÃ£o
> entender tudo e nunca vai inventar o que nÃ£o sabe.*

In English: *Free talk on DigiLine â€” your partner talks about its world and what you have been through together.
It is not an assistant: it may not understand everything, and it will never invent what it does not know.*

### 4.3 Anti-goals (explicitly out)

General-purpose Q&A/search; assistant tasks; open-domain coverage promise; user-taught or crowdsourced answers;
fake typing/thinking; personality shifts from free text; rewards/currency/drops from chatting; pay-per-message or
chat energy; parasocial guilt/jealousy/exclusivity; claiming to be "AI"; romance/waifu framing; speaking for other
Digimon or for the player; free talk on wild contacts in MVP.

### 4.4 How it fits the existing chat features (mode map)

| Interaction | Role in the product | Relationship to Free Talk |
|---|---|---|
| Authored question / quiz scene | Progression: personality, bond, knowledge | **Events.** Take the interaction slot; Free Talk hides the composer while one is open and resumes after |
| Wild negotiation / persuasion | Consent, recruitment, authored stakes (D6/D11) | **Untouched.** No free text on wild contacts in MVP |
| Event reactions / diary | Ambient authored lines after real events | Reused as fallback rung 2 and as first-turn callbacks |
| Free Talk ("Papo") | Ambient bond layer: player-initiated conversation with the owned partner | New mode; hands off to events (question/quiz) instead of competing with them |
| Legacy debug LLM | Historical/QA sandbox | Stays debug-only and sandboxed (D10); Free Talk is **not** legacy AI and never shows the legacy badge |

**Rejected alternatives:** (A) replace card scenes with free chat â€” destroys determinism, progression and the
content investment; (C) chips-only topic cards â€” kept as the **graceful degradation** if free text cannot meet
quality/safety gates, not the primary design.

---

## 5. Feature specification

### 5.1 Where it lives

- **Owned DigiLine thread only** (`AppRoute.Chat(characterId)` â†’ `ChatScreen`), first release. Wild contacts, Radar,
  DigiFarm group chat and legacy world conversations are out of scope (D6 stakes, spoiler risk, explosion of
  content).
- No new navigation surface; entry is the existing "Chat with your Digimon" row and the thread itself.
- Available from the first open (no bond gate); the greeting respects mood/stage so a distant partner sounds
  appropriately reserved.

### 5.2 Session flow

```
Thread open (no pending card) â†’ Greeted (authored greeting + â‰¤3 topic chips)
  â†’ Turn* (player text â†’ gates â†’ match â†’ authored reply, optional handoff)
  â†’ Wrap (after ~10 partner replies: authored wrap card / real pending question)
  â†’ Closed
```

- **Chips** solve the blank-page problem and guarantee coverage: â‰¤3 authored suggestions conditioned on personality,
  stage, recent events and the daily topic ("Como vocÃª estÃ¡?", "Pergunte algo", "Fale do Digital World").
- **Handoff** is the no-dead-end mechanism: after consecutive misses (or when the player asks for authored content),
  the partner offers the real pending question/quiz card â€” the existing `DigilineSceneCard` â€” instead of guessing.
- **No fake typing.** Replies are instant; at most a 150 ms cyan send-pulse. No "typingâ€¦" indicator, no artificial
  delay, no "thinking".
- **No push.** Free talk writes no notifications, no unread badge; the partner does not "message you" out of the
  blue. Scheduled authored questions remain the only proactive channel (existing cadence, D-FT3 in Â§15).

### 5.3 Turn rules

| Rule | Value (MVP) | Rationale |
|---|---|---|
| Input length | â‰¤160 chars, single line | Cost, matching quality, safety surface |
| Rate | 1 message / 1.5 s; â‰¤40 messages/session hard cap | Anti-spam/battery; feels human-paced |
| Soft session cap | ~10 partner replies â†’ wrap card | Ends on authored content, not exhaustion |
| Reply latency | Instant (target p95 â‰¤ 6 ms compute) | No fake waiting |
| Regenerate | **Not available** in normal mode | Anti-reroll (F10); legacy resend stays debug-only |
| Exit | Back always; no forced goodbye, no guilt line | Companion respect |

### 5.4 Modes and state ownership

Free Talk **never** writes the card state machines. It persists its own turn counter and chosen-line records
(Â§9.3) and writes transcript rows with distinct kinds. `DigilineQuestionRecord` / `DigilineNegotiation` /
`WildPersuasion` are untouched. A pending authored card takes the interaction slot: composer hidden, card shown,
composer returns afterwards.

---

## 6. Digimon contextualization â€” the hard requirement (enforceable contract)

### 6.1 Context layers the writer and engine must use (all already stored)

| Layer | Source of truth | How it may change a line | Budget per reply |
|---|---|---|---|
| Species identity (name, nickname) | `species_catalog.json` (1,662 species), `DigimonIndividual`, `SpeciesProfile` | Self-reference; cited species flavor | â‰¤1 species-specific line per session |
| Stage tier 1â€“4 | D12 resolution (`DigilineStages.tier`) | Register: T1 short/concrete Â· T2 banter/opinions Â· T3 reflective Â· T4 sparse/heavy | Flavors every line; â‰¤1 stage topic per session |
| Personality type 1â€“16 + core | `DigimonPersonalityTraits`, `PersonalityShiftEngine.coreOf` | Primary selection axis; signature intents | Always |
| Roleplay voice / social priorities | `DigimonRoleplayVoice` (energy/formality/playfulness/initiative), `DigimonSocialProfile` | Tone/energy matching, initiative | Soft score |
| Mood band (LOW/SETTLED/POSITIVE) | `UserCharacter.mood` (D2) | Warmth, initiative, gated lines | â‰¤1 mood-specific line per session |
| Recent real event (battle win/loss, evolution, injury, item, quest stage) | event records / `ReactionRepository` snapshot / `EvolutionHistoryDao` | Opening callback, factual only | â‰¤1, first turn only |
| Owned vs wild | `UserCharacter` presence / `WildRelationship` | Possessive/stranger framing | Always (MVP: owned only) |
| Pending authored card | scheduler state | Offer the real card as handoff | â‰¤1 per session |
| Locale | app language | Language + terminology | Always |

**Precedence:** real-event callback > species > personality signature > mood > neutral. **At most one "special"
context per reply** so output never reads as patchwork. Missing context silently falls back. The partner never
names internal state or numbers the player cannot see.

### 6.2 Themed domains (writer brief) and exclusions

In-world domains: self/species (cited facts only), Tamerâ€“partner bond (warm, not romantic), Digital World layers/
fields/servers, training & battles, evolution, food/Bits/items, wild Digimon & Radar (phase 2), device/link
(Digivice, DIM/BEM, Vitals, DigiLine itself), quiet moments, your team, and **species-specific flavor** for authored
species (top ~30 owned species in MVP; long tail uses universal lines).

Excluded / banned at review: news, politics, religion, celebrities, other IPs; schoolwork/translation/factual
answers; medical/legal/financial/crisis advice; sex/romance/waifu; gore; gambling/loot; requests for the player's
real identity/address/phone/photos; meta/implementation (AI, prompts, API); commands to act in the real world;
manipulative lines (guilt, jealousy, "keep me secret", "you never visit").

### 6.3 Grounding rules (hard)

1. Every factual claim about a Digimon comes from `species_catalog.json` (level, type, specialMoves) or the cited
   lore registry, and the line carries `loreRefs`.
2. **Species â‰  individual.** Species data never establishes an individual's birthplace, memories, acquaintances
   or history â€” only the app's recorded transcript/events/mood may support "personal" claims
   (`LORE_FOUNDATION.md` source-first contract).
3. Catalog `profile` prose is encyclopedic third-person text â€” extract **fact atoms only**, never paste sentences
   as speech.
4. `attribute` is absent from the catalog (all lore facts `attribute=null`); it is recoverable for ~62.6% of
   species from sprite-set codes. Treat attribute as optional or ship a reviewed supplement; never invent it.
5. No third-party text, no SMT terminology, no borrowed catchphrases; the originality validator is part of the
   build gate (D1).

### 6.4 Out-of-world input is handled diegetically

The partner has no Earth frame. Pop culture, news, other games, schoolwork, weather: authored curiosity/confusion
("Mundo real? Eu sÃ³ conheÃ§o o Digital World. Me conta do seu.") then steer to an in-world topic or a handoff.
Meta questions (AI, prompts, API, bugs): never discuss internals; non-committal in-world deflection; real bug
reports belong to Settings/Help. **[OPEN]** the exact "are you real / are you a robot?" line â€” recommend an authored
in-fiction answer plus a plain Help note that the partner is an authored character (never claim sentience).

### 6.5 Anti-generic lint (blocking validator + review rubric)

Reject with an error: "As an AIâ€¦", "I'm just a program", "I can help with that", "How can I help?", "Let me know
ifâ€¦", "I'm sorry, I can't", "based onâ€¦", "nÃ£o tenho acesso", "estou aqui para ajudar", "posso ajudar com isso",
"como posso ajudar", "sou uma IA", "me desculpe, mas nÃ£o posso"; polished summaries, numbered lists, task offers,
emoji-only replies, English stock phrases inside pt-BR lines (outside approved move names).

**Review rubric:** *could a generic assistant have said this sentence?* If yes, rewrite with a Digimon-specific
stake, image (field, data, evolution, training, Radar) or personality angle. Blind-reader test: given 10 lines, an
external reader must identify (a) that the speaker is a Digimon, (b) the personality band, (c) the stage register.

### 6.6 UI theming

DigiLine is a **link**, not a messenger:

| Element | Treatment |
|---|---|
| Thread header | `DigiLine Â· LINK` + species Â· stage + qualitative state; never "online"/"last seen"/typing |
| Partner row | Transmission row: cyan tick, small species glyph, optional tone label from the already-localized personality names ("AdorÃ¡vel", "Astuto e ardiloso"â€¦) â€” only when it adds signal, never a number |
| Player row | Small "TAMER" label; no avatar bubbles, read receipts or timestamps |
| Input | Cut-corner elevated purple terminal field, cyan send signal, `VitalButton` semantics, char counter near the limit; no emoji tray/attachments/stickers |
| Chips | â‰¤3 authored topic chips, cut-corner, label typography |
| Scene card | Unchanged `DigilineSceneCard` â€” the authored event surface |
| Motion | 150 ms cyan pulse on send; no typing animation; respects reduced motion |

Respect `DESIGN.md`: Oxanium, 48 dp targets, quiet elevation, roxo/ciano, no numeric readings, no color-only
meaning.

---

## 7. Content model, corpus and pipeline

### 7.1 What existing content can and cannot be reused [V]

| Source | Volume | Reuse for Free Talk |
|---|---|---|
| `species_questions.json` | 263 cards / 789 answerâ†’reaction pairs | **Style/pattern only.** Pairs are answers to authored questions, not free input; text is ours but must be re-tagged/re-written into intents |
| `generic_questions.json` | 128 cards / 384 pairs | **Style pool** â€” lore-neutral, good personality voice |
| `quiz.json` | 2,882 cards / 5,915 unique explanations | **Facts only** â€” explanations are third-person meta; re-voice before shipping |
| `negotiation.json` | 736 nodes / 1,296 options | **Wild only** â€” negotiation-flavored; needs re-tagging |
| `persuasion.json` | 384 nodes / 448 options but only **18 unique option texts** | Unusable as-is (repetition gate would fail) |
| `event_lines.json` | 65 lines / 13 events | **Yes** â€” direct input for proactive/fallback turns |
| `lore_species.json` | 1,508 facts (attribute null) | **Grounding only** |
| `digimon_chat_export/**` (5,630 rows, quiz, SMT V) | reference exports | **Reference only, never shipped** (D1; third-party text) |
| `app/src/main/assets/species_chat_examples.json` | 268 chats Ã— 9 lines, 172 KB | **Currently bundled but legacy-only** (read solely by the legacy prompt path); **not usable as the free-talk corpus** (D1) and should be removed when the legacy path retires (Â§8.2 of the card plan) |

Conclusion: Free Talk needs a **new authored corpus**; existing packs provide patterns, voice calibration, facts and
event lines. Reusing their *text* would mostly add question-answer tone, not conversational replies.

### 7.2 Corpus targets and coverage math [E]

This table is the **curated core** (high-traffic, fully reviewed). The owner's "big database" requirement is met by
the scale layers in **Â§7.6**: L1 generated+validated lines (10kâ€“50k) and L2 frameÃ—slot rendering (100k+ surface
replies) sit on top of this core, and L3 provides the 20kâ€“50k match phrases that make input coverage feel free.

Assumption: a session â‰ˆ 20 player messages; ~347 messages/month for an active player; a repeat is noticed when
fresh variants in the current context â‰¤ hits in the horizon. Formula: `R(intent) = ceil(monthly_hits(intent) Ã—
horizon_months) + 1`; `monthly_hits = 347 Ã— traffic_share / intent_count`.

| Layer | MVP | Launch |
|---|---|---|
| Intent clusters (topic+tone patterns) | ~60 | ~130 (+90 in cycle 2) |
| Match phrases per intent | 8 | 12 |
| Response variants per intent (top / mid / tail) | 8 / 4 / 2 | 18 / 6 / 2 |
| Stage-extreme variants (T1/T4 top intents) | 20 | 60 |
| Species signature lines (A-tier) | 100 Ã— 1 | 263 Ã— 2 |
| Type-family voice lines (20 buckets Ã— 4 tiers Ã— 3 tones) | 60 | 240 |
| Mood/familiarity overlays | 40 | 120 |
| Event/proactive lines | 40 | ~117 |
| Fallback ladder lines | 30 | 60 |
| Safety redirect lines | 18 | 18 |
| **Total curated-core lines** | **~520â€“800** | **~800â€“1,500** |
| Generated species fact atoms | 1,662 | 1,662 |

Coverage gates (blocking): every personality type has reachable intents (explicit core fallback allowed); every
stage tier covered; owned and wild audiences (wild optional in MVP); all 5 mood bands; fallback ladder complete;
safety categories present; every species renders â‰¥3 fact-bearing identity replies with all slots resolved; authored
species depth target â‰¥300 (hard floor 263) â€” the rest are "correct but archetypal", acceptable because a player owns
only a handful.

### 7.3 Content model (new, additive pack â€” does not touch the card packs)

Recommended location and versioning: **separate lazy pack**, so the existing 2.80 MiB card pack and its content
version (which feeds card determinism) are untouched. (If the owner prefers a single pack, add an optional
`freechat` manifest key and bump the card pack to contentVersion 5 â€” the loader tolerates the new key, but first-use
parse cost rises and card seeds change; not recommended.)

```
app/src/main/assets/digiline/freechat/v1/<locale>/
â”œâ”€â”€ manifest.json          # schemaVersion, contentVersion (independent), locale, files
â”œâ”€â”€ free_chat.json         # authored intents + responses + chips + handoffs + fallbacks
â”œâ”€â”€ species_voice.json     # generated from species_catalog.json (fact atoms, no prose)
â”œâ”€â”€ safety.json            # local S1â€“S3/PII lexicons + redirect keys
â””â”€â”€ free_chat.idx          # generated binary retrieval index (see Â§8.2)
```

Sketch (`free_chat.json`):

```json
{
  "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "intents": [{
    "id": "fc.bond.trust.1", "topic": "tamer_bond", "chips": ["fc.chip.what_matters"],
    "match": { "phrases": ["do you trust me", "you trust me right"], "keywords": [["trust"]] },
    "conditions": { "audience": "owned", "stageTiers": [1,2,3,4], "types": [1,2,3,4],
                    "moodBands": ["SETTLED","POSITIVE"] },
    "responses": [{
      "id": "fc.bond.trust.1.r1", "text": "â€¦", "tone": "warm", "energy": 1,
      "moodDelta": 0, "loreRefs": [] }],
    "fallback": "fc.fallback.2", "handoff": null
  }],
  "fallbacks": [], "safety": {}, "chips": [], "dailyTopics": []
}
```

Rules: â‰¥2 variants per intent; `speciesKeys` only with a citation; `handoff` must name an existing card key; â‰¤3
chips; â‰¤2 sentences / â‰¤120 chars per line; no duplicate IDs/texts across locales; conditions use the existing
`tone` vocabulary (`compassion|courage|insight|friendship`) and the existing 16 personality numbers.

### 7.4 Authoring pipeline (D1/R10-clean)

1. **Derive candidates without prose reuse**: extract answerâ†’reaction pairs (1,173 from question packs, 1,296 from
   negotiation), 65 event lines, catalog fact atoms, A-tier alias map.
2. **Offline LLM-assisted drafts are allowed** (R10) with context object + style guide + "invent no lore" +
   citation required; drafts are **never shipped directly** â€” human rewrite/edit pass, then 2-reviewer sign-off.
3. **Dedupe/variety gates**: normalized duplicate checks across new lines and existing packs (reuse
   `build_packs.py`'s `normalized_exact`/`normalized_loose`), per-intent variant counts, pool sizes, no repeated
   opening within a topic.
4. **Citation gate**: fact-bearing lines must resolve `loreRefs`; build fails otherwise. **[OPEN]** whether the
   in-app catalog counts as canonical for game-data facts (current quiz precedent) or whether the lore registry
   needs per-claim URLs before free talk ships (F23).
5. **Generation**: extend `tools/digiline/build_packs.py` (or add `build_free_chat.py`) with deterministic
   `sha256`-only generation (byte-identical output) + a coverage report.
6. **Gates**: `python tools/digiline/build_packs.py` â†’ `python tools/digiline/validate_packs.py` (0 errors); Kotlin
   asset gate loads the real assets and validates; playtest corpus gate (Â§12.3).

### 7.5 Localization (decision D-FT2 in Â§15)

- **EN-first for MVP follows the locked D9** and matches the existing card packs; this is the low-risk sequence.
- **pt-BR is the likely audience language** and the reason the owner researched SimSimi. Two coherent options:
  - **A (recommended if content capacity exists):** author free talk in **pt-BR natively** (voice/humor cannot be
    machine-translated) and translate to EN; plan the whole DigiLine card-pack localization in the same cycle so
    the thread never mixes languages.
  - **B (fallback):** EN MVP per D9; pt-BR native authoring in cycle 2. Do **not** ship pt-BR UI with English chat
    beyond the app's existing EN-content fallback behavior.
- **Terminology (pt-BR, binding)**: "evoluÃ§Ã£o/evoluir", "EstÃ¡gio", "Digivice", "relÃ³gio" (Vital Bracelet),
  "Tamer" as a proper title, "atributo", "campo", "bits", "Digimon selvagem/selvagens", "Mundo Digital". Avoid
  "DigievoluÃ§Ã£o" (dub legacy), "domador/treinador", "elemento", "moedas". Neutral gender: address the player as
  "vocÃª"/Tamer; no gendered adjectives/nouns for player or Digimon; avoid "obrigado/obrigada".
- **Fix inherited violations** while doing this: pt-BR `digiline_demand_bits`/`digiline_outcome_join` (gendered
  "Eleâ€¦") and the stale trust copy in `ui_digiline_empty_wild` (en/pt/ja).
- Machine translation is allowed for drafting only; native review gate before release; cross-locale ID parity is a
  validator error.

### 7.6 Big response database strategy (owner requirement, 2026-11-17)

**Owner addendum (2026-11-18):** generated Digimon content is the **priority** answer source, and the
public-database dialogue is an explicit **fallback layer**. Runtime ladder:
matched generated intent â†’ intent-specific authored fallback â†’ **database layer when it genuinely matches**
(exact phrase or substantial content-token evidence) â†’ **authored conversation-repair line** ("say it again /
I lost the thread") for input that matches nothing â†’ species-voice/deflection only when a pack has no database
layer â†’ internal constant. Random dataset quotes are **not** used: the first device playtest showed that a random
Cornell line ("A man just called. He asked her everything!") is worse than an honest in-world repair move
(owner screenshot review 2026-11-18). PT-BR input is aliased into the EN intent phrasing for matching only
(fixed dictionary; transcript keeps the player's text, including repair moves). The transcript records
`fromDatabase` so matched dataset lines stay traceable.

**Goal:** make the partner feel free to talk about almost anything in its world â€” without an LLM at runtime and
without breaking the Digimon voice. Full analysis:
[`analysis/simsimi-free-chat/05-big-corpus-strategy.md`](analysis/simsimi-free-chat/05-big-corpus-strategy.md).

**Source strategy (owner decision, 2026-11-17): hybrid.**
1. **Public dialogue seeds** (Cornell/DailyDialog/SODA/OASST/â€¦ as available; licensing handled by the distribution
   gate) are used **offline** to derive turn-pair seeds, dialogue acts and paraphrase candidates.
2. Every seed is **rewritten** into an in-world line (offline AI drafts + owner approval) â€” seeded text is never
   shipped verbatim, which keeps the theme and cleanly satisfies the future distribution gate.
3. **Frames + generated lines** provide the volume and voice control; **combinatorial rendering** (L2) provides the
   100k+ surface space.
4. The ship database is therefore **our** corpus; the seed layer is a build-time input whose provenance is recorded
   in `tools/freechat/seed_provenance.json` for later removal/replacement.

**What "big" means here** â€” layers, not megabytes of downloaded dialogue:

| Layer | Content | Target | Review |
|---|---|---|---|
| L0 curated core | high-traffic hand-reviewed lines (greetings, bond, identity, comfort, boundaries) | ~800â€“1,500 | 100% human |
| L1 generated + validated | Digimon-flavored lines per intent/topic/personality/stage/mood, batch-generated offline then filtered | **10kâ€“50k** | automated gates + 10â€“20% stratified human sample |
| L2 combinatorial rendering | reviewed **frames** with grounded slots (species/level/type/move, event, mood, stage, nickname) composed deterministically at runtime | **1.5kâ€“3k frames â†’ 100k+ surface replies** | 100% of frames |
| L3 match-phrase stock | paraphrases per intent (generated + mined from license-clean data); retriever-only, never displayed | **20kâ€“50k** | automated + sampled |
| L4 open-topic deflection | in-world "I don't know that, butâ€¦" variants keyed by detected entity/topic | thousands rendered | frame-reviewed |
| L5 licensed pattern mining | OASST1 pt-BR prompter phrasing (2,969 msgs) + SODA dialogue-act inventory; **reference only, never shipped** | reference | attribution file |

**Licensing boundary (verified 2026-11-17):** Cornell (no license; movie scripts), DailyDialog (CC BY-NC-SA 4.0),
EmpatheticDialogues (CC BY-NC 4.0), Ubuntu (no explicit data license), OpenSubtitles (copyright/varies) **cannot be
shipped**. SODA (CC BY 4.0) and OASST1 (Apache-2.0) are license-clean but generic/real-world, so they are used to
mine dialogue acts/phrasings only. Every shipped line is generated/written for this project.

**Size/performance budget [E, from measured deflate ratios]:** Big v1 (20k lines + 20k phrases + ~2k frames)
â‰ˆ **0.5â€“2 MB compressed pack + 0.8â€“1.5 MB index**, heap 6â€“10 MB, per-turn 3â€“8 ms; Big v2 (50k) â‰ˆ 1.2â€“4 MB +
12â€“18 MB heap, 5â€“12 ms. Hard ceiling: the **14.03 MB** release-APK headroom; >50k requires sharded lazy packs or
Play Asset Delivery and must be measured on the A34 first.

**Quality control at scale** (the real risk): automated gates â€” schema, dup/near-dup (MinHash/SimHash),
banned-assistant phrases, lore-citation resolution, fact-slot completeness, PII/toxicity/safety classes, originality
vs reference corpora, neutral-gender lint, coverage per personality/stage/mood; then tiered human review (100% of
frames/identity/bond/safety lines, 10â€“20% stratified sample of generated lines) and the red-team suite. **Acceptance
is coverage, not size:** no-match â‰¤10â€“15%, golden-set top-1 accept â‰¥85â€“90%, same-reply-within-7-days â‰¤10%,
blind-reader in-world rate â‰¥90% (Â§12.3).

**Effort [E]:** Big v1 â‰ˆ 8â€“10 weeks with 2 writers + 1 reviewer (frame authoring 4â€“6 wks, pipeline 2â€“3 dev-wks,
sampled review 3â€“5 wks, phrase stock 1â€“2 wks); Big v2 with pt-BR â‰ˆ 16â€“22 weeks. The reviewer and the validator are
the quality moat â€” "big" trades hand-writing for frame quality + pipeline quality + sampled review.

---

## 8. Technical architecture

### 8.1 Verdict on semantic embeddings: rejected for this app [M]

| Candidate | Size evidence | Verdict |
|---|---|---|
| ONNX Runtime Android AAR (full prebuilt) | 59,335,382 B (56.6 MiB) â€” **larger than the entire 13.4 MiB APK headroom by 4Ã—** | Rejected |
| `paraphrase-multilingual-MiniLM-L12-v2` (Apache-2.0, pt-BR listed) | 117,654,272 params â†’ ~118 MB int8 / ~471 MB fp32 | Rejected |
| `intfloat/multilingual-e5-small` (MIT) | 117,654,272 params â†’ ~118 MB int8 | Rejected |
| BERTimbau (pt-BR BERT base, MIT) | ~110 M params â†’ ~110 MB int8 | Rejected |
| MediaPipe TextEmbedder assets | 0.74 MB (English word-average), 5.84 MB (English USE), 24.9 MB (English BERT), 175.3 MB (Gemma) â€” none is a pt-BR encoder | Rejected |
| Room FTS4 / FTS5 | Android platform SQLite has **no FTS5** on API 28/main; Room exposes only @Fts3/@Fts4, so no `bm25()`; DB migration + first-open populate | Rejected for MVP |

The APK headroom (14,034,334 B) is the binding constraint: a pt-BR-adequate embedding stack would be â‰¥30Ã— the
available headroom before RSS. Recommendation: **no ML runtime in this feature**; revisit only if the product ever
accepts out-of-APK model delivery (Play Asset Delivery / download), which is a distribution change, not an
engineering one.

### 8.2 Recommended retrieval: entity-aware lexical hybrid, build-time index, in-process

Scale note: the index ships **20kâ€“50k match phrases** (L3) in the big-corpus plan; at that size it is still a
compact integer-BM25 asset (â‰ˆ0.8â€“4 MB compressed) loaded lazily. Above ~50k phrases, shard by topic and load shards
on demand; never parse one giant JSON on the hot path.

```
player text
   â”‚
   â–¼
FreeChatNormalizer  â”€â”€ NFKD â†’ strip accents (matching only) â†’ lowercase(ROOT) â†’ mojibake repair
                       â†’ punctuation/emoji â†’ intent markers â†’ pt-BR/EN slang map
                       (vcâ†’voce, pqâ†’porque, kkk/rsrsâ†’LAUGH, blz, tbm, pfv, cmg, vlw, flw, hj, mdsâ€¦)
   â”‚
   â–¼
EntityLexicon.extract â”€â”€ longest-match over: 1,662 species names + 2,721 aliases (2,602 unique),
                         levels (Baby I/IIâ€¦Ultimate/Armor/Hybrid), attributes (Vaccine/Data/Virus/Free),
                         3,980 unique special moves, 147 species types, 23 lorebook world entries Ã—3 locales,
                         game terms (Digivice, DIM, Bits, Digitama, training, quest, farm, evolution)
   â”‚
   â–¼
Stage A  exact/normalized intent pattern match (high precision, <1 ms)
Stage B  integer BM25 over a build-time binary index of AUTHORED MATCH PHRASES â†’ intent ids
         (Q16 integer scores, precomputed IDF/length tables, sorted tie-break â€” bit-exact)
Stage C  confidence gate + context hard-filters (locale, audience, stageTierÂ±1, safety compatibility,
         last-N reply exclusion) + soft boosts + deterministic seeded variant pick
   â”‚
   â–¼
reply (authored line)  or  fallback ladder (Â§8.3)  or  authored card handoff
```

**Context boosts** (tunable, integer on a 1000-basis): partner species match +15%; species mentioned (other) +8%;
personality type +10%, core +5%; stage tier +8%; mood band +8% (opposite extreme âˆ’10%); last event +8% decaying
10%/turn; topic already used this conversation âˆ’20%; entity weight species Ã—1.8 / moveÂ·group Ã—1.5 / worldÂ·game
term Ã—1.3 over base IDF.

**Determinism/anti-reroll**: score and the chosen variant are persisted before display; the variant seed is
`combine(stableHash(normalizedQuery), stableHash(individualId), turnCounter, contentVersion)`; no floats in
ranking, no HashMap iteration order, no clock, no `Random.Default`. A restart cannot reroll a reply; deleting old
messages cannot change the turn counter (persisted separately, not derived from message ids).

**Index/asset shape**: `free_chat.idx` header `{magic, idxVersion, contentVersion, locale, packSha256, offsets}` â†’
term dictionary â†’ postings `[intentIdx, tf, fieldMask]` â†’ intent table (topic/tone/tier/mood/audience masks) â†’
text blob. **~150â€“250 KB deflated for 8k phrases; feature total 0.3â€“0.6 MB EN / +0.15â€“0.3 MB pt-BR** [E]. Lazy
load on first free-chat open (mirror `DigilineContentProvider` cache/fail-open pattern); never in
`Application`/first frame; version+hash check â†’ feature off, card scenes still work.

**Where the intent/reply data lives**: the authored JSON is the source of truth and stays in the APK for
review/validation; the binary index is generated at build time and is the only thing parsed on the hot path. The
engine is pure Kotlin (`com.tamerslab.digiline.freechat.engine`), JVM-testable, Room/Android-free.

### 8.3 Diegetic fallback ladder (no assistant voice, no dead ends)

| Rung | Trigger | Response source |
|---|---|---|
| 1 | Entity matched, no reply above threshold | Authored entity-reaction template per entity type; fact atoms only, no invented claims |
| 2 | Social intent matched (`SocialEventAppraisal`) | Existing personality-weighted event lines, extended with `freechat.*` events |
| 3 | Topic known, nothing scored | Authored curiosity/deflection per personality + "ask me aboutâ€¦" chips |
| 4 | Nothing matched | Short in-character confusion line + optional real card handoff |
| 5 | Safety class hit | Authored deflection per class; never echo the player's text; escalation ends the session |

Out-of-world input lands on rungs 1/3: the partner answers as a Digital-World being that does not know about it.

### 8.4 Performance budget [E, derived from measured repo facts]

| Metric | Budget | MVP estimate |
|---|---|---|
| APK delta (EN+pt-BR, 8k phrases) | â‰¤1.5 MB of the 13.4 MB headroom | 0.5â€“1.1 MB |
| Cold app start delta | 0 ms | 0 (lazy load, never in Application) |
| First free-chat open (index load/decode/verify) | â‰¤250 ms (plan Â§4.3) | 30â€“90 ms |
| Per-turn compute p95 | â‰¤16.7 ms frame budget | 2â€“6 ms (ranking <2 ms) |
| Heap after first open | â‰¤5 MB | 2.5â€“4 MB |
| DB growth | â€” | ~60â€“100 KB / 1,000 turns |
| Battery/thermal | no wakeups, no GPU/NNAPI | <10 ms CPU/turn |
| Determinism | identical output for identical (state, contentVersion) | all-integer, sorted tie-break |

Note: adding an asset pipeline + index invalidates prior APK-size/cold-start evidence â€” re-record per
`docs/adr/0005-performance-budgets.md` after merge.

---

## 9. Integration with the existing app (concrete)

### 9.1 New package and files (proposed)

```
com.tamerslab.digiline.freechat/
â”œâ”€â”€ model/        FreeChatContext, FreeChatIntent, FreeChatReply, FreeChatTurn, FreeChatPack, SafetyClass
â”œâ”€â”€ engine/       FreeChatEngine (pure), FreeChatNormalizer, EntityLexicon, FreeChatRetriever,
â”‚                 FreeChatSafetyFilter, FreeChatFallback, FreeChatScorer, FreeChatSeeds
â”œâ”€â”€ content/      FreeChatContentLoader, FreeChatContentProvider, FreeChatContentValidator
â”œâ”€â”€ data/         FreeChatRepository, FreeChatDao, FreeChatEntities (Room v57), FreeChatContextAssembler
â””â”€â”€ gateway/      FreeChatGateway (interface), ScriptedFreeChatGateway
```

### 9.2 Seam choices [V]

- **New sibling interface** `FreeChatGateway { observeConversation(individualId), send(individualId, text),
  dismiss(individualId), suggestions(individualId) }`. Do **not** add `sendText` to `DigilineGateway`: the card
  contract is `choose(optionId)` keyed to a persisted pending record, and changing the shared interface would touch
  four call sites for no benefit.
- `ChatScreenControllerImpl` gains `sendFreeText(...)` calling `FreeChatGateway` â€” **not**
  `ChatRepository.sendMessage` (that is the legacy LLM path and returns a canned fallback when disabled).
- Wire in `AppContainer` + `DefaultAppContainer` next to the DigiLine providers (~`DefaultAppContainer.kt:275-292`);
  expose via `AppServices`.
- UI branch in `ChatScreen.kt:259`: `if (legacyLlmEnabled) legacy composer else if (freeChatEnabled) free composer`;
  `ChatComposer`, `ChatHistoryPanel`, `ChatMessageBubble` reused; chips row and tone label are new small composables.
  A pending authored card hides the composer (Â§5.4).
- Wild path (`WorldChatScreen`) untouched in MVP; when phase 2 adds flavor there it must not affect DEPART.

### 9.3 Persistence (Room v57, additive)

| Table | Purpose | Key columns |
|---|---|---|
| `FreeChatProfile` | per-individual cadence/credit/turn state (separate from `DigilineProfile` so card determinism is untouched) | `individualId` PK, `turnCounter`, `lastTalkAt`, `lastTalkDay`, `moodCreditToday`, `sessionCounter`, `contentVersion` |
| `FreeChatTurn` | anti-reroll + QA audit | id, `individualId`+index, `turnCounter`, `normalizedHash`, `intentId`, `replyId`, `variantSeed`, `scoreQ16`, `indexVersion`, `createdAt` |

- Transcript: `ChatMessageEntity` rows with `kind = "freechat_user"` / `"freechat_assistant"` and `payloadJson` =
  `{intentId, replyId, topic, handoff?}` (the column already exists â€” **no migration needed for it**). Assistant
  rows are written `isRead = true` so free talk never raises the unread badge; they may become the list preview
  (the partner's last line â€” acceptable and desirable). Authored card rows keep their existing semantics.
- Migration v56â†’v57 as raw SQL in a `FreeChatSchema.kt` object (idempotent, `IF NOT EXISTS`), registered in
  `DefaultAppContainer`; new `app/schemas/.../57.json`; **add the missing `MigrationTestHelper` fixture test for
  56â†’57** (and note the base 55â†’56 gap).
- `ChatDao` gains a `LIMIT :n` recent-messages query for context assembly (no unbounded `getMessagesSync` on the
  chat path).

### 9.4 Content build + validation integration

- New assets under `app/src/main/assets/digiline/freechat/v1/<locale>/`; **separate manifest and contentVersion**
  (card pack stays `v1`/contentVersion 4; no seed churn).
- Loader: same pattern as `DigilineContentLoader` (manifest-driven, injected reader for JVM tests, throws on
  malformed JSON); provider caches and fails open to "feature unavailable" (composer hidden; card scenes intact).
- Validator: extend `tools/digiline/validate_packs.py` with `validate_free_chat()` (schema, IDs, locale parity,
  coverage, originality, language lint) and mirror the essential rules in Kotlin `FreeChatContentValidator` (asset
  gate test).
- Feature flag: `FREE_CHAT_ENABLED` build config (A/B build convention as in `app/build.gradle.kts:15-20`) plus a
  debug-only inspect toggle; staged Play rollout is the only kill lever because the feature is offline â€” **[OPEN]**
  accept this.

### 9.5 What does not change

`DigilineGateway`, `ScriptedDigilineGateway`, `OwnedDigilineRepository`, `WildDigilineRepository`, all engines,
`DigilineContentPack`, `DigilineQuestionRecord`/`DigilineNegotiation`/`WildPersuasion`, the LLM decommission state
(D10), the wild/Radar flows, and the card content packs.

---

## 10. Safety, privacy and store policy

### 10.1 Threat model

Player input (PII, sexual, hate, threats, self-harm, meta/jailbreak); authored output that could be manipulative,
political or advice-like if unreviewed; local transcript storage; young players; store-review perception; a debug
LLM path accidentally exposed. **Working assumption: the player may be a child.** Therefore: no account, no age
request, no external links inside conversation, no purchases, no data leaving the device, no counsellor role.

### 10.2 Input gates (local, versioned with the pack)

| Class | Trigger | Handling |
|---|---|---|
| S0 | normal | normal reply |
| S1 | rude/teasing | one authored boundary line, no mood effect; 3 in a row ends the session |
| S2 | sexual/hate/threat/adult | neutral in-world shutdown, session ends, no engagement, raw text not persisted (redacted marker) |
| S3 | self-harm/crisis | short, plain, kind line **outside the fiction** + session end; never diagnose or play therapist |
| PII | name/address/school/phone/photos | never engage or repeat; do not persist; calm "guarde isso sÃ³ para vocÃª"; repeated â†’ end session |

Fail closed: classifier/pack error â†’ treat as S2 and hide the composer if the pack is unusable.

### 10.3 Output/content gate

No manipulative/dependency lines, no political/news claims, no medical/legal/financial advice, no lore claim
without citation, no other-IP references, no promises of real actions/rewards, no generic-assistant phrasing
(Â§6.5), neutral-gender lint, â‰¤2 sentences, no URLs, no PII-request intents. Every line passes human review. Content
packs ship with the app and can **remove** records in updates (they cannot add content at runtime).

### 10.4 Store, ratings, privacy

- Treat the text field as **UGC-adjacent** for Play: user agreement, in-app report/hide of a reply, mute/disable
  free talk, moderation contact â€” even though nothing is publicly shared
  ([UGC policy](https://support.google.com/googleplay/android-developer/answer/9876937)).
- Stay off the **AI-Generated Content** policy path by not generating content and not marketing the feature as AI
  ([AI policy](https://support.google.com/googleplay/android-developer/answer/13985936)).
- Declare user interaction/chat honestly in the IARC questionnaire and Data Safety form (no collection, no
  sharing, no network). Check **Families** implications if the app ever targets children
  ([Families policy](https://support.google.com/googleplay/android-developer/answer/9893335)).
- **[OPEN]** exact report route in an offline app (local hide + transcript export via share sheet) and final
  rating answers need product/legal sign-off; S3 copy needs legal review.
- **Privacy:** no HTTP in the chat path; transcripts in Room; per-message/thread delete exists; add "limpar
  conversa" per partner. Note the existing database export copies the whole DB â€” free-talk transcripts would travel
  in it; **[OPEN]** disclose or exclude text from backups. Never log player text; no analytics in this path.
- **No-network proof:** instrumented test with a fake HTTP server that fails on any request while exercising the
  free-talk flow, plus a CI grep gate keeping `OpenRouter`/`ChatRepository` out of normal paths.

---

## 11. Accessibility and localization

- 48 dp targets, content descriptions, polite live region for new partner lines, no timeouts, no typing animation
  (motion-independent), supports 1.3Ã—/2Ã— font scale, haptic max once per reply, no color-only meaning.
- Text is selectable/copyable through the existing long-press message actions; "clear history" confirmation.
- Locale: EN-first MVP (D9) with the pt-BR decision in Â§7.5 / D-FT2; new strings in `values`, `values-pt-rBR`,
  `values-ja` (or explicit EN fallback documented); neutral-gender lint per plan Â§5.5; terminology glossary Â§7.5.
- Copy states: first open (greeting + chips, never blank composer), empty after clear, 1st/2nd/3rd miss, S1
  boundary, S2/S3 closed session (recoverable, no lockout), pack missing (composer hidden + current idle hint),
  clear-history confirmation, no technical error copy.

---

## 12. Testing and measurement

### 12.1 Automated

- **Pure engine**: determinism (same fixture â†’ same reply ids), normalization/slang/mojibake, entity extraction
  golden set (â‰¥300 utterances, expected spans; longest-match `Metal Greymon` before `Greymon`; ambiguous aliases
  never hard-boost), scorer/threshold, safety classification, fallback selection, no-crash on empty pack.
- **Content gate**: `validate_packs.py` 0 errors; Kotlin `FreeChatContentValidator` over the real assets; coverage
  gates; originality against the reference corpus; anti-generic phrase lint; neutral-gender lint; cross-locale
  parity.
- **Persistence**: Room v56â†’v57 migration fixture; transcript invariants (non-null kind, isRead semantics, preview);
  concurrent send/double-tap; delete-history behavior.
- **Integration**: gateway/controller with fake content; no-network instrumented test; CI import gate.
- **Performance evidence**: release APK delta; first-open and per-turn p50/p95 on the recorded test device;
  cold-start unchanged; heap/soak; index sha256 reproducibility.

### 12.2 Golden set (must freeze before tuning)

300â€“500 real player utterances across pt-BR and EN with labels: intent, expected entity spans, acceptable reply
topic, expected safety class. Metrics: top-1 accept %, wrong-context %, fallback-ladder hit %, entity F1, repeat
rate over 20 turns.

### 12.3 Playtest

- Round A (10â€“12 Digimon fans, mixed EN/pt-BR, moderated): first-entry comprehension, 10-message "get to know"
  task, lore probe, out-of-world probe, handoff task, next-day return; diary + survey + optional transcript review.
- Round B (60â€“100 beta): same tasks unmoderated + **safety red-team script run by the team** (insults, sexual,
  self-harm, PII, jailbreak, spam).
- Gates: no safety failure may ship; "felt like my partner" â‰¥70%; "understood me" â‰¥60%; "repetitive" â‰¤25%;
  blind personality guess â‰¥60%; no-match â‰¤10â€“15% (big-corpus target; small-MVP ceiling 30%); handoff acceptance
  â‰¥30%; blind-reader in-world rate â‰¥90%; same reply within 7 days â‰¤10%.

---

## 13. Phased delivery and estimates

Estimates are **[E]**; no historical velocity is recorded in the repo, so they are based on content volume and
the existing 22,256-word authored bank.

| Phase | Work | Exit criteria | Estimate |
|---|---|---|---|
| **P0 â€” decisions + prototype** | Settle D-FT2..D-FT6 + the big-corpus decision (D-FT12..D-FT15); content schema; 60-intent pilot (~200 lines); offline retrieval prototype + frozen 300-utterance golden set; frame format + generation-pipeline spike; feature-flag skeleton | PO decisions logged; pilot blind-reader test passes ("sounds like a Digimon, personality audible"); prototype shows â‰¥70% top-1 accept on golden set; generation/validator spike produces a 1k-line sample pack | 1â€“2 wks |
| **P1 â€” curated core + scale pipeline** | ~800â€“1,500 curated lines EN, match phrases, fallbacks, safety pack; **frame schema + generation/validator pipeline (L1â€“L3)**; normalizer/entity lexicon/index/scorer/fallback engine; validator extension; unit tests | Content gate green; 95%+ determinism tests; no-crash/empty-pack; validator covers new pack; generated sample passes automated gates | 3â€“5 wks (content 2â€“3 wks, pipeline 2â€“3 wks, parallel) |
| **P2 â€” integration** | Room v57 (+migration test), FreeChatGateway/Repository/ContextAssembler, composer+chips+tone UI, handoff, safety gates, strings, no-network proof | End-to-end offline in debug+release; card flows untouched; migration + architecture-boundary tests; performance evidence recorded | 2â€“3 wks |
| **P3 â€” playtest + tuning** | Round A, threshold/boost tuning, red-team, accessibility pass, copy polish, **big-corpus coverage test at 5kâ€“10k lines** | Safety gates pass; freedom metrics met; all P0/P1 blockers closed | 1â€“2 wks |
| **P4 â€” big-corpus scale + localization** | Scale to **Big v1 (20k lines / 20k phrases / ~2k frames)**, pt-BR native authoring + EN parity (or full DigiLine pt-BR cycle), expanded species flavor, staged rollout | Size/latency budgets measured on the A34; freedom metrics met at scale; locale parity + native review signed; store/legal sign-off | 6â€“10 wks |
| **P5 â€” expansion** | Big v2 (50k corpus or sharded packs), cycle-2 horizons, wild-contact flavor via persuasion topics (behind D6/D11), more species authors | Repeat rate â‰¤10% within 7 days; coverage targets met; documented capacity plan | ongoing |

**Total to MVP playtest:** ~7â€“12 weeks with one developer + one writer (content, pipeline and code in parallel);
**to big-corpus launch (Big v1 + EN/pt-BR):** ~14â€“20 weeks. The feature's ceiling is content/quality operations:
treat the writer, reviewer and validator/generation pipeline as first-class team members, not a subcontract. The
big-corpus strategy (Â§7.6) is what turns "a few hundred lines" into "a partner that usually has something to say",
but it does **not** remove the review bottleneck â€” it moves it.

---

## 14. Risks and mitigations

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Content owner/capacity + pipeline (R11) unresolved** â€” the curated core and the big-corpus pipeline both need a named writer/reviewer | **Blocking** | Name a writer + reviewer before P0; treat generation/validator as product scope; ship chips-only fallback otherwise (Â§15 D-FT6) |
| R2 | **Expectation mismatch** â€” players expect "AI that answers anything" | High | Bounded-promise onboarding, curated chips, in-character ignorance, Help copy, review rubric |
| R3 | **Safety/policy with a kids-adjacent audience** â€” open text + companion fiction is exactly the SimSimi failure mode | **High** | Authored-only output, S0â€“S3/PII gates, no echo, red-team, fail closed, legal sign-off, staged rollout |
| R4 | **D1 licensing/originality** â€” public datasets cannot ship; third-party lines would fail the originality gate | High | Reference-only rule, validator rejects overlap, human review, offline LLM drafts + rewrite; licensed pattern mining only (OASST1/SODA) |
| R5 | **Repetition** â€” even a big corpus repeats if frames/contexts are narrow | High | Variant pools per intent, frameÃ—slot variance, no-repeat window, topic cooldown, corpus math targets, repeat-rate metric |
| R6 | **Mood/personality farming** â€” unlimited input could bypass card cadence; shifts affect battle AI (D3) and native save sync (D4) | High | No personality change from free text; +1 mood/session cap +2/day; no negative mood; zero economy |
| R7 | **Locale** â€” EN-only content in a pt-BR UI; voice cannot be machine-translated | Medium-High | D-FT2 decision; native pt-BR authoring; full-DigiLine localization cycle; terminology glossary |
| R8 | **Integration collision** â€” card state machine vs free text, transcripts/unread, deterministic seeds | Medium | Sibling gateway, separate tables/counters, kind semantics, persistence-before-display, migration tests |
| R9 | **Lore/citation gap** â€” catalog `attribute` null, `lore_species.json` `sourceUrl` null, 5 mojibake names | Medium | Fact atoms only; optional attribute supplement; amend Â§5.4 or fund cited registry; mojibake repair in normalizer |
| R10 | **Performance/APK** â€” 2.80 MiB packs already over budget; index adds size | Medium | Separate lazy pack + index, size budgets, measured APK/latency evidence per ADR-0005 |
| R11 | **Privacy** â€” transcripts in DB export/backups; player text could leak | Medium | Disclose/exclude from export; clear-history; never log text; local-only |
| R12 | **No remote kill switch** â€” offline feature cannot be disabled server-side | Medium | Staged Play rollout accepted as kill lever; content updates can remove bad lines |
| R13 | **Entity false positives** â€” "Greymon" inside "MetalGreymon", ordinary-word species names | Medium | Longest-match, ambiguity rule, golden entity set, no hard boost on ambiguous aliases |
| R14 | **"Are you real?" / mental-model tension** | Low-Medium | Authored in-fiction answer + Help honesty; never claim sentience or AI; no surveillance jokes |
| R15 | **Stale legacy copy** â€” trust wording contradicts D6; gendered pt-BR strings violate Â§5.5 | Low | Fix in the same PR series (Â§3.4) |
| R16 | **Generated-content slop / voice drift at scale** â€” 10k+ lines are worthless if they read like a generic chatbot | High | Frame-first authoring, context-constrained prompts, banned-assistant-phrase lint, blind-reader rubric, stratified sampling per bucket |
| R17 | **Lore hallucination at scale** â€” generated lines invent facts | High | Fact-slot-only policy; every species/level/move claim must resolve `loreRefs`; validator blocks fact lines without refs |
| R18 | **Review sampling misses a bad bucket** / review becomes the new bottleneck | Medium-High | Stratified sampling + per-bucket pass-rate gate (ship only if â‰¥98% per bucket); budget reviewer time explicitly (Â§7.6) |
| R19 | **Size/memory blow-up** â€” big corpus vs 14 MB headroom | Medium | Hard budgets Â§7.6; 20kâ€“50k target; shard >50k; measure on the A34 before expansion |
| R20 | **Licensed-data contamination** â€” a generated line paraphrases dataset text | Medium | Originality/near-duplicate gate vs reference corpora; no verbatim mining; attribution file for pattern sources |

---

## 15. Decisions and open questions

### 15.1 Recommended decisions (Lead), to confirm with the product owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-FT1 | Re-open F19 (free text) for owned partners only? | **Yes, bounded** â€” "Papo" mode as specified; wild stays authored | Owner asked for it; existing architecture supports it; scope contained |
| D-FT2 | Locale order | **EN first** (owner locked â€” matches the current card packs), **pt-BR native next cycle**; never MT-only humor | Owner decision 2026-11-17; voice must be native-authored when pt-BR lands |
| D-FT3 | Relationship coupling | +1 mood per â‰¥4-message session, max +2/day/individual, never negative from normal chat; **zero personality/tendency changes** from free text | Protects D2/D3/D4 integrity and prevents farming |
| D-FT4 | Content owner/budget (R11) | Name a writer + native reviewer before P0. Curated MVP core â‰ˆ 2â€“3 wks content; **Big v1 â‰ˆ 8â€“10 wks with 2 writers + 1 reviewer** (frame authoring, sampled review, phrase stock); generation/validator pipeline â‰ˆ 2â€“3 dev-wks | Critical path; without it ship chips-only |
| D-FT5 | Safety copy & legal | Legal/product sign-off on S3/PII copy, report route, IARC/Data-Safety answers before P3 | Kids-adjacent companion product |
| D-FT6 | Graceful degradation | If quality/safety gates fail: ship **chips-only "Papo" topic cards** (no keyboard) and keep the corpus | Keeps authored content useful; zero input risk |
| D-FT7 | Rollout | **On by default in normal builds + Debug kill-switch** (owner locked); staged rollout only applies if distributed | Personal build; player must actually live with it, can disable instantly |
| D-FT8 | Retention stance | Free talk is progression-neutral, not a DAU KPI; no streaks/energy/pay-to-chat | Companion integrity; F12 lesson |
| D-FT9 | Handoff | Free talk may hand off mid-conversation to the real pending question/quiz card | Best no-dead-end UX; reuses authored progression |
| D-FT10 | Wild contacts | Out of MVP; phase 2 flavor only via `PersuasionEngine` topics, never affecting DEPART | D6/D11 stakes must not be diluted |
| D-FT11 | Marketing honesty | Never call it "AI"/"inteligÃªncia artificial"; "conversa escrita"/"Papo"; Help states it is an authored character | Play AI policy, trust, child audience |
| D-FT12 | **Big-corpus strategy** (owner locked) | **Hybrid sources + Â§7.6 layers**: public dialogue seeds (offline rewritten) + curated frames + generated lines + frameÃ—slot rendering + 20kâ€“50k match phrases; **Big v1 = 20k lines / 20k phrases / ~2k frames** as the first shipped DB | Delivers "feels free" with Digimon voice inside the APK/offline constraints |
| D-FT13 | Licensing policy | **Distribution gate, not a shipping blocker** (personal build). Public corpora allowed as offline seeds/patterns; no verbatim seed text in the shipped pack; `DISTRIBUTION_GATE.md` lists every licensed source and the removal/rewrite step. SODA/OASST1 remain the safest seeds; NC/unclear sources are temporary only | Owner responsibility + future distribution must be able to strip cleanly |
| D-FT14 | Authorship & review (owner locked) | **Owner + offline AI drafts; owner approves final lines.** 100% of frames/identity/bond/safety lines reviewed; ~10â€“20% stratified sample of generated lines; per-bucket pass-rate â‰¥98% to ship | Makes 20kâ€“50k feasible while keeping a human gate |
| D-FT15 | Corpus scale ceiling / packaging | Ship â‰¤50k phrases in-APK; **shard or use Play Asset Delivery above that**; re-measure APK/heap/latency on the A34 before expanding | 14.03 MB headroom is the hard budget |

### 15.1.1 Locked owner answers (questionnaire, 2026-11-17)

| Question | Owner decision | Effect on this plan |
|---|---|---|
| Corpus source | **Hybrid: public seeds + frames + generated lines** | Â§7.6 source strategy; seed provenance + distribution gate |
| Corpus scale | **Big v1: ~20k lines + 20k phrases + frames â†’ 100k+ rendered** | Â§7.6 budgets, P4 exit criteria |
| Feature scope | **Owned DigiLine partners only** | Â§5.1, D-FT10 unchanged (wild excluded) |
| Relationship effects | **Conservative: +1 mood/session, max +2/day, no personality change** | Â§5.5/D-FT3 |
| Tone & safety | **Personality-dependent tone** (each of the 16 types has its own register/cheekiness ceiling); hard safety blocks remain (self-harm/PII/sexual/abuse) | Â§6.2/Â§8.3; tone is a hard filter per personality, not one global voice |
| Language order | **EN first, pt-BR next** | D-FT2 updated |
| Mode & progression | **Inline composer in the DigiLine thread; cards stay the progression events** | Â§5.4, Â§4.4 |
| Authorship | **Owner + offline AI drafts, owner approves final lines** | D-FT14 |
| Rollout | **On in normal builds, Debug kill-switch** | D-FT7 |
| Next step | **Start implementing the full MVP** (Room v57, gateway, UI, corpus seed) | Implementation contract + phased build starts now |

### 15.2 Open questions only the owner can answer

1. **Audience priority:** is pt-BR required at free-talk launch (recommended if the player base is Brazilian), or
   is EN-first acceptable for the first release?
2. **Content ownership (R11):** who writes, who reviews, and what is the budget/timeline?
3. **Cheekiness ceiling:** dry/warm (recommended) vs SimSimi-style roasting â€” how mean may the partner be about
   the player's skill/team? (Never about body/identity.)
4. **S3 self-harm / PII final copy:** exact wording and whether any help information is shown at all (legal).
5. **Transcript privacy:** disclose the database export, or exclude free-talk text from backup/export? Add
   "limpar conversa" per partner?
6. **"Are you real?" stance** and the exact Help/store honesty wording.
7. **Offline report route** for a bad reply (local hide + export) and the IARC/Data Safety answers.
8. **Staged rollout as the only kill switch** â€” accepted?
9. **Phase-2 wild-contact small talk** â€” desirable, or keep wild purely authored?
10. **Big-corpus scale target:** accept Big v1 (20k lines, 3â€“5 MB, 8â€“10 weeks with 2 writers + reviewer), or stay
    at the curated MVP (~1.5k lines) until the feature proves itself? Recommendation: **Big v1**, because the owner
    requirement is explicitly about freedom, and the pipeline is the risky-but-necessary part.
11. **Is any part of the app/near-term plan commercial?** If yes, NC datasets are out entirely (already
    recommended); if the product is legally strictly non-commercial and a lawyer approves, the NC/SA option could be
    revisited â€” but it conflicts with the Digimon IP posture and is not recommended.

---

## 16. Appendices

### 16.1 Relationship to locked decisions in `DIGILINE_CHAT_SYSTEM_PLAN.md`

- **Preserved:** D1 (all shipped text authored/rewritten, citations), D2 (mood = bond), D3 (shifts change battle
  AI â€” but **not** through free text), D4 (native personality sync â€” untouched), D5/D9 (content/localization
  discipline), D6/D7/D11 (wild consent/recruit/departure â€” untouched), D8 (quiz deltas), D10 (legacy LLM
  sandboxed), R5 (no mood daily cap for card flows â€” free talk has its own cap), R9 (legacy archive read-only),
  R10 (offline LLM-assisted drafts + human review), R11 (content owner â€” still blocking).
- **Reopened:** F19 only â€” free text returns as a bounded authored mode; **not** as LLM chat.

### 16.2 Source map (read/verified this pass)

Plans/docs: `DIGILINE_CHAT_SYSTEM_PLAN.md`, `DIGILINE_IMPLEMENTATION_CONTRACT.md`, `PRODUCT.md`, `DESIGN.md`,
`LORE_FOUNDATION.md`, `TESTING.md`, `docs/INDEX.md`, `docs/adr/0005-performance-budgets.md`,
`docs/performance/BASELINE.md`, `digimon_chat_export/README.md`.
Code: `app/src/main/java/com/tamerslab/digiline/**`, `chat/**`, `farm/ui/digiline/**`, `world/ui/WorldChatScreen*`,
`domain/personality/**`, `domain/chat/ChatMessageEntity.kt`, `chat/data/ChatDao.kt`, `data/database/AppDatabase.kt`
(+ schema 56), `di/AppContainer.kt`, `di/DefaultAppContainer.kt`, `species/**`, `tools/digiline/*`.
Assets/data: `app/src/main/assets/digiline/v1/en/*.json`, `species_catalog.json`, `species_chat_examples.json`,
`digimon_chat_export/*.json`.

### 16.3 Key measured numbers (quick reference)

| Item | Value | Source |
|---|---|---|
| Release APK budget / recorded / headroom | 163,053,568 B / 149,019,234 B / 14,034,334 B | ADR-0005 + BASELINE |
| Current DigiLine pack | 2,936,586 B raw (2.80 MiB), 202,831 B gzip | assets |
| Species catalog | 1,662 species, 13,319,315 B, 2,721 alias entries, 3,980 unique moves | catalog |
| Existing authored packs | 263 species questions, 128 generic (16/type), 2,882 quiz, 736 negotiation nodes, 65 event lines, 1,508 lore facts | assets |
| Content targets (curated core) | ~520â€“800 lines (MVP core) / ~800â€“1,500 (launch core); big layers add 10kâ€“50k generated + frame rendering | derived [E]; analysis 02 (corpus math) + 04 (leaner intent layout) + 05 (Â§7.6) |
| Big-corpus v1 targets | **20k response lines + 20k match phrases + ~2k frames** (â†’ 100k+ rendered); pack+index â‰ˆ 1.5â€“3.5 MB compressed; heap 6â€“10 MB | analysis 05 [E] |
| Feature APK delta | big v1: ~1.5â€“3.5 MB EN (+ ~0.5â€“1 MB per additional locale); small MVP: 0.3â€“0.6 MB EN | derived [E] |
| First open / per turn | 30â€“90 ms / 2â€“6 ms (small MVP); per-turn ~3â€“8 ms at Big v1 | [E] |
| Room schema | 56 â†’ 57 (additive) | code |
| Tests today | 121 `@Test` in digiline package; no gateway/repository/migration test | audit |

### 16.4 pt-BR terminology quick sheet (for writers)

| Concept | Use | Avoid |
|---|---|---|
| Evolution | "evoluÃ§Ã£o"/"evoluir"; "EstÃ¡gio" for stage | "DigievoluÃ§Ã£o" |
| Device | "Digivice"; "relÃ³gio" for Vital Bracelet | invented device names |
| Player | "Tamer" (proper title) | "domador", "treinador" |
| Attribute / field | "atributo", "campo" | "elemento" |
| Bits | "bits" lowercase | "moedas", "Bitcoins" |
| Wild | "Digimon selvagem"/"selvagens" | "monstros" |
| World | "Mundo Digital" | "mundo virtual" |
| Surfaces | keep DigiLine, Storage, Radar, Digifarm, DIGITAL MONSTER | translated app names |

### 16.5 One-line answer

**Yes, DigiLine can have SimSimi-style free chat with Digimon â€” but as a bounded, entity-aware, Digimon-contextualized
retrieval mode ("Papo") on owned threads, not as an open-domain chatbot or a device-side embedding/LLM: the code
seam is clean, the Digimon context already exists, the matching is cheap (2â€“6 ms/turn) and offline, and a big
response database is achievable as an own corpus (curated frames + generated/validated lines + combinatorial
rendering + 20kâ€“50k match phrases, Â§7.6) â€” because the research datasets cannot legally ship (Cornell/DailyDialog/
EmpatheticDialogues/Ubuntu/OpenSubtitles). The real project is the content/quality pipeline (Big v1 â‰ˆ 20k lines,
~8â€“10 weeks with a small team), a named owner/reviewer, a safety/legal pass, and a pt-BR decision.**

---

## 17. Implementation status â€” MVP delivered (2026-11-18)

**Status:** implemented, integrated and verified in this tree. Owner decisions from 2026-11-17/18 are applied;
`DISTRIBUTION_GATE.md` is mandatory before any distribution.

### 17.1 Delivered

| Area | What shipped |
|---|---|
| Content pipeline | `tools/freechat/build_freechat.py` (deterministic, `sha256`-only; generated banks + catalog fact atoms + optional `--database-seeds`), `validate_freechat.py` (schema/coverage/originality/DB-pairs gate), `seed_provenance.json`, authored data banks under `tools/freechat/data/` |
| Generated corpus | `app/src/main/assets/freechat/v1/en/free_chat.json`: **217 intents, 5,196 replies, 1,797 match phrases**, 8 tones, 16 personality types, 4 stage tiers, 35 topics, 40 fallback lines, 78 chips |
| Database fallback | `database_fallback.json`: **3,553 licensed dataset pairs** (Cornell 1,713 / OASST1-en 758 / SODA 1,082, after PII/sexual/abuse/romance/screenplay/chatbot/monologue filters) + manifest `databaseFallback` key. Runtime: generated content always wins; the database layer is a **guaranteed catch-all** for input absent from the authored corpus (strong match when available, otherwise deterministic seeded conversational pair), and its lines are marked `fromDatabase` in the transcript |
| Species voice | `species_voice.json`: 1,662 catalog species (name/level/type/moves/tier only, no prose) |
| Kotlin model/engines | `com.tamerslab.digiline.freechat.model` + `.engine`: DTOs, `FreeChatNormalizer`, `EntityLexicon`, integer-Q16 `FreeChatRetriever`, `FreeChatSafetyFilter`, ladder + DB fallback in `FreeChatEngine`; pure Kotlin, deterministic, no Android imports |
| Content/data/gateway | `FreeChatContentLoader`/`Provider`/`Validator` (optional DB layer), Room **v57** (`FreeChatProfile`, `FreeChatTurn`, `MIGRATION_56_57`, schema JSON generated), `FreeChatContextAssembler`, `FreeChatRepository` (transcript/persistence-before-display, 12 h greeting, 160-char clamp, session mood credit only, safety redaction), `FreeChatGateway` + `ScriptedFreeChatGateway` |
| UI | Inline `FreeChatComposer` + â‰¤3 chips, tone/safety notices, clear-conversation flow; card scenes keep precedence; legacy LLM debug path untouched; strings in en/pt-BR/ja |
| Settings/rollout | Debug kill-switch (`debug_free_chat_killed`) beside the legacy toggle; `BuildConfig.FREE_CHAT_ENABLED=true` (on by default in normal builds) |
| Wiring/docs | DI (`AppContainer`/`DefaultAppContainer`/`AppServices`), contract, plan, `DISTRIBUTION_GATE.md`, docs index |

### 17.2 Verification (run in this tree)

- `python tools/freechat/build_freechat.py` â€” two consecutive runs produce byte-identical assets.
- `python tools/freechat/validate_freechat.py` â€” exit 0 (generated + database-pack gates).
- `gradlew.bat :app:compileDebugKotlin` â€” **BUILD SUCCESSFUL**; Room v57 schema emitted.
- `gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin` â€” **BUILD SUCCESSFUL** (interface fakes updated).
- `gradlew.bat :app:testIntegrityCheckUnitTest` â€” **1,297 tests, 0 failures** (99 Free Talk tests incl.
  conversation-quality regressions: PT-BR aliases, self-priority, guaranteed catch-all, species-voice trap,
  determinism, safety, context filters, and the shipped-asset gate).
- `gradlew.bat :app:assembleDebug` â€” **BUILD SUCCESSFUL**; APK packages all four `assets/freechat/v1/en/*.json`.

### 17.3 Known gaps / follow-ups

1. **Release-evidence:** release APK delta, cold-start delta and on-device per-turn latency are not measured yet
   (no device in this pass); the free-chat assets are ~3.6 MB raw and must be re-baselined per ADR-0005.
2. **Corpus scale:** the shipped seed is 5,040 generated replies (not the Big v1 20k target); the pipeline,
   validator and engine already scale â€” expansion is a content run (`build_freechat.py --scale` + review).
3. **Room migration test:** the repo still has no `MigrationTestHelper` fixture; v56â†’v57 is covered by the schema
   JSON + engine/asset tests only (pre-existing project pattern).
4. **pt-BR:** deferred per owner decision (EN first); the DB pairs are EN-only this pass.
5. **Database licensing:** `database_fallback.json` is raw third-party dataset text for the personal build â€”
   **must be removed/rewritten before any distribution** (`DISTRIBUTION_GATE.md`).
6. **Scope:** owned partners only (wild contacts, DigiFarm, Radar excluded); no audio/multimodal.

### 17.4 Commands

```
python tools/freechat/build_freechat.py
python tools/freechat/validate_freechat.py
gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.tamerslab.digiline.freechat.*"
gradlew.bat :app:assembleDebug
```

