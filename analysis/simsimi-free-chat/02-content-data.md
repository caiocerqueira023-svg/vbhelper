# 02 — Content & data feasibility: Digimon-contextualized, retrieval-based free chat

> **Superseded in part by [`05-big-corpus-strategy.md`](05-big-corpus-strategy.md)** (owner requirement,
> 2026-11-17: a *big* response database). This file remains valid for the curated core, source inventory,
> context layers, grounding rules and effort model; §05 defines the scale layers (frames + generated/validated
> lines + licensed pattern mining) that sit on top of it.

Scope: content/data needed for offline SimSimi-style free text with **owned Digimon (and wild contacts)** in Tamers Lab
under `DIGILINE_CHAT_SYSTEM_PLAN.md` **D1** (exports reference-only; every shipped line authored/rewritten; lore cited)
and **R10** (offline LLM-assisted drafts + human review allowed). Legend: **F** = verified in repo (path cited);
**E** = estimate (formula stated); **R** = recommendation.

## 0. Verdict

- Feasible offline and on-brand, but **content is the critical path, not matching**. Launch corpus ≈ **1.7k authored
  response lines (~15k words) + ~1.6k match patterns + generated per-species fact atoms** — roughly the volume of the
  whole existing bank (`tools/digiline/content_banks.py`: **F** 3,745 lines / 5,045 text cells / 22,256 words).
- **pt-BR is not free**: packs are EN-only (**F** `manifest.json` locale `en`; plan §14.3 gap 6) while pt-BR UI ships
  (**F** `app/src/main/res/values-pt-rBR/` 1,188 strings vs 1,187 EN). Tied translation adds work, not authoring weeks.
- **R** Stage: MVP = **~520 lines / 60 intents** (exact+lexical, owned only, en) → launch = full corpus + wild + species
  voice layer → mature = 3-month no-repeat horizon. Biggest blocker: **no owner/capacity for D1-clean authored dialogue**
  (plan **R11 unresolved**).

## 1. Existing authored content usable as free-chat source material

### 1.1 Shipped DigiLine v2 packs — **F** (`app/src/main/assets/digiline/v1/en/*.json`, contentVersion 4)

| Pack | Units | Text cells | Unique | Bytes | Reuse for free chat |
|---|---|---|---|---|---|
| `species_questions.json` | 263 | 1,841 | 116 | 347,146 | **Partial**: 789 player-answer→reaction pairs usable; 263 prompts are Digimon speech (proactive). Facts: level/type only. |
| `generic_questions.json` | 128 | 896 | 506 | 156,223 | **Partial**: 384 answer→reaction pairs; lore-neutral style pool; tags need rework. |
| `quiz.json` | 2,882 | 17,292 | 5,915 | 1,253,200 | **Data only**: explanations are 3rd-person meta ("According to the species catalog…"); re-voice every line before shipping. |
| `negotiation.json` | 736 nodes / 1,296 options | 3,328 | 1,039 | 608,362 | **Wild only**: 1,296 answer→wild-reaction pairs; negotiation-flavored, needs re-tagging for casual wild talk. |
| `persuasion.json` | 384 nodes / 448 options | 832 | 206 | 216,847 | **Weak**: 448 options but only **18 unique texts**; fails a repetition gate as-is. |
| `event_lines.json` | 65 | 65 | 65 | 14,420 | **Yes**: reactions to 13 real app events; direct input for proactive free-chat turns. |
| `lore_species.json` | 1,508 facts | — | — | 340,055 | **Grounding only**: level/type per referenced species; `attribute` is null for all 1,508. |
| **Total** | | **24,254** | **7,847** | **2,936,586 (2.80 MiB)** | |

- **F** Packs already exceed the plan's **≤2 MB/locale** budget (§4.3), so free chat needs its own budget (§7.4). Extend the
  verified toolchain: `tools/digiline/content_banks.py` + `build_packs.py` (deterministic generator) + `validate_packs.py`
  (schema/coverage/originality/language gates).

### 1.2 Reference-only sources — must not ship (**F**, D1 / §5.1 / `digimon_chat_export/README.md`)

| Source | Size | Status |
|---|---|---|
| `digimon_chat_export/digimon_chat.json` | 5,630 rows / 3,268 unique texts / 508,443 chars: 268 species chats ×9 (2,412) + 64 generic templates (3,200) + 18 system | DCO mod text — **reference only**; originality validator rejects matches. |
| `app/src/main/assets/species_chat_examples.json` | 268 chats ×9 (2,412 lines) | Time Stranger text — **shape/coverage reference only**. |
| `quiz_time_digimon_questions.json` | 94 (Bandai text) | **reference only**; current quiz regenerated from catalog instead. |
| `demon_negotiation_dialogue_smt5.json` | 15 tones / 215 topics / 1,126 steps | **structure only** (ATLUS ©). |
| User research doc (Downloads) | DailyDialog, Cornell, Ubuntu, EmpatheticDialogues, OpenSubtitles | **Patterns only**: turn-pair extraction, staged exact→lexical/semantic retrieval, thresholds, fallback ladder, pt-BR cautions. No text shippable; its "import a public dataset" plan contradicts D1. |

### 1.3 Per-locale status (**F**)

- Content **EN only** (manifest `locale: en`; no `pt-BR/`/`ja/` under `digiline/v1/`); UI ships `values/` (EN) and
  `values-pt-rBR/` (incl. 25+ `digiline_*` strings); language picker in `settings/ui/dialogs/LanguageDialog.kt`.
- Personality/voice text already bilingual: `domain/personality/DigimonPersonalityTraits.kt` (EN+PT instructions/observations per type) and `chat/PromptLocalization.kt` (EN/PT/JA owned & wild prompts).
- **R** Locale dirs `v1/en/` + `v1/pt-BR/`, identical IDs enforced by validator.

## 2. Digimon context layers (never generic chatbot voice)

### 2.1 Context available today — **F** (free chat must consume it)

| Layer | Source | Values |
|---|---|---|
| Species identity | `app/src/main/assets/species_catalog.json` | 1,662 species (1,650 `kind=DIGIMON`); `name`, `aliases`, `level`, `type`, `profile`, `specialMoves`, `cardSlots`, `dotSprites` |
| Stage tier | plan §6.3 mapping; `build_packs.py` `LEVEL_TO_TIER` | T1 314 / T2 615 / T3 311 / T4 422 species |
| Personality | `domain/personality/DigimonPersonalityTraits.kt` | 16 types 1..16 in 4 cores (PHILANTHROPY 1–4, VALOR 5–8, WISDOM 9–12, AMICABILITY 13–16); authored EN/PT instruction + observation each |
| Per-individual voice + social priorities | `domain/personality/DigimonRoleplayVoice.kt`; `domain/personality/DigimonSocialProfile.kt` | energy/formality/playfulness/initiative 0–2 (deterministic per individual); 10 social traits 0..1 per type + jitter |
| Bond (mood) | `digiline/data/OwnedDigilineRepository.kt` (mood = bond, D2; clamp 0..100) | bands 0–19 / 20–39 / 40–59 / 60–79 / 80–100 |
| Owned vs wild | `chat/PromptLocalization.kt`; `WildRelationship` (`recruitmentState`, `contactUnlockedAt`) | partner vs Radar contact; wild knows no partner Tamer, familiarity from recorded contact/chat |
| Recent events / history | `event_lines.json` + `PromptLocalization.reactionInstruction`; `domain/chat/ChatMessageEntity.kt` | 13 event kinds: battle_win/loss, injury, heal, evolution, degeneration, milestone, quest_offer/complete, item_found, diary, wild_depart, recruit_join; last N response ids + familiarity per individual |
| Attribute | **absent** from catalog (no `attribute` field; `build_lore` writes null); recoverable for **1,041/1,662 (62.6%)** from `spriteSets[].attribute` via `species/CatalogGameplayProfile.kt` (0=Free,1=Virus,2=Data,3=Vaccine); 621 unresolved → optional layer only |

### 2.2 Schema mapping + engine filter/composition (**R**)

Schema encodes: species → `conditions.speciesTypes` + `speciesVoice` + `loreRefs`; stage → `stageTiers`; personality →
`types` + `tone` + voice tags; core → derived from `types` (never authored separately); bond/relationship →
`moodMin`/`moodMax` (+ familiarity band); recent in-app events → `recentEvents`; wild-vs-owned → `conditions.owned`.
Engine: (1) context `{speciesKey, stageTier, personalityType, core, moodBand, owned|wild, recentEvent, familiarity,
lastResponseKeys}`; (2) hard-filter `conditions` (AND), then soft-score `tone`/`energy` against voice dims; (3) pick
deterministically excluding last N≈8 ids, seed = hash(individualId, dayIndex, sessionId, packVersion); (4) identity
replies compose an authored frame + catalog fact slots (`{name}{level}{type}{move}{trait}`), never raw numbers on screen;
(5) stage tier sets register (T1 simple/short, T3–T4 confident/complex); (6) wild mode excludes owned framing ("your
Tamer", home, DIM) until familiarity, owned mode excludes stranger framing; (7) a type-tagged line is a hard filter so
tone never contradicts the personality instruction.

## 3. Digimon-world domains and forbidden content

### 3.1 In-universe topics (**F** = app reality exists; intent list is **R**)

| Topic family | Intents | Grounded on |
|---|---|---|
| Self/identity (name, species, level, type, moves, likes) | 15 | catalog fields + lore facts |
| Digital World, servers, fields, layers | 18 | `LORE_FOUNDATION.md` (Digimon.net About scope) |
| Evolution/Digivolution, stages, Blast Evolution | 10 | catalog `level`, app evolution rules |
| Training, vitals, trophies, battles, injuries | 14 | device/game records + event lines |
| Bits, items, shop, food/data feeding | 10 | app economy/items |
| Partner bond, Tamer, trust, nicknames, memories | 16 | mood/bond + recorded history |
| Other Digimon, wild contacts, teams, Radar | 12 | wild relationship state |
| Quests, adventures, diary, missions | 10 | quest/adventure systems |
| Device/DIM, Vital Bracelet, save transfer; homesickness/belonging/fear/hope | 16 | app device features + recorded history |
| Playful requests (joke, sing, tease, guess, story) | 8 | authored in-world only |
| Meta/unknown/short input ("ok", "?", emoji, gibberish) | 6 | fallback ladder |
| Safety redirects (abuse, sexual, self-harm, real-world harm, personal data, other IP) | 6 | fixed authored lines |

### 3.2 Forbidden — lint/review-blocked (**R**)

- **Generic assistant/small-talk and SimSimi filler**: "As an AI…", "I'm here to help", "How can I help you?", "That's a
  great question", "I understand", "Is there anything else?", "Let me know if…", generic condolence, disclaimers, helpdesk
  phrasing, task offers, echoing user text, "haha lol", "I don't know what to say" (only as authored in-character lines).
- **Out-of-universe**: real brands/tech (internet, phone, app, AI, LLM, prompt), real geography, modern trivia,
  politics/religion, celebrity, medical/legal/financial advice, other franchises, romance escalation, claims of being
  human or of real-world knowledge.
- **Third-party residue**: Demon, Macca, Netherworld, Negotiation, Compendium (rejected by `validate_packs.py`
  `SMT_TERM_RE`); gendered third-person pronouns (`GENDERED_RE`); pt-BR adds `ele/ela`, `obrigado/obrigada`, gendered adjectives.
- **Invented lore**: origin stories, unrecorded memories/acquaintances, canonical character identity.
  `LORE_FOUNDATION.md`: a species profile does not establish an individual's birthplace, memories, or relationships;
  catalog name/sprite matching is not official verification.

## 4. Grounding rules and data sources

1. **Factual claims** only from `app/src/main/assets/species_catalog.json` (`level`, `type`, `specialMoves`, `profile`)
   or `lore_species.json`; every fact line carries `loreRefs: ["species_catalog:<key>"]` (existing quiz convention) and
   the validator rejects fact-bearing lines without a resolvable ref. **Species vocabulary** = catalog `level`, `type`,
   move names, and profile-derived traits — only as authored phrasing.
2. **World/lore claims** outside a species' own facts must map to `LORE_FOUNDATION.md` scope (Digimon.net About, history,
   game portal, Profile, Iliad/Shambhala, Seekers, Re:Digitize, Liberator); otherwise cut the line.
3. **Individual ≠ species**: never derive personal memories, birthplace, acquaintances, or ownership from species data;
   only recorded app history and mood support personal claims.
4. **Open citation gap (F, blocking)**: all 1,508 `lore_species.json` facts are sourced `app_species_catalog` with
   `sourceUrl: null`, while §5.4 requires wikimon/digimon.net URLs. Resolve: (a) amend §5.4 to accept the in-app catalog
   as canonical for game-data facts (already used by quiz), or (b) enrich the registry with per-claim URLs.
5. **Profile prose is not shippable speech (R)**: 178,256 words of third-person catalog profile exist for 1,349 species;
   use only extracted fact atoms + authored trait phrasing, never pasted profile sentences. **Safety lines** are fixed
   authored copy reviewed for a children audience (Play rating — see 04 game-design).

## 5. Covering 1,662 species without every species sounding the same

### 5.1 Realistic authored vs generated split (**R**; data **F**)

| Tier | Species | Approach | Authored |
|---|---|---|---|
| A. Partner-relevant (species with existing cards) | **263** | per-species signature pack: 2 lines + quirk/opinion keyed to catalog facts | 526–789 lines |
| B. Other species with facts | ~1,400 | generated identity atoms (level/type/first move/stage) in authored frames; trait phrase only where authored lexicon matches — measured: a 45-adjective lexicon matches only **453/1,349 profiles (33.6%)**, so traits must be curated | frames authored once |
| C. Edge: `level` Unknown 181 (+2 "(?)", 1 "None"); non-DIGIMON kinds 12 | ~200 | safe archetype by type bucket + T2 guess; no fabricated facts | inside family lines |
| Voice family layer | **147** distinct `type` values (top 20 cover 63.4%; top 30 74.3%) | group into **~20 authored family buckets** × 4 tiers × 3 tones | 240 lines |

### 5.2 Voice variation dimensions (selection keys)

`stageTier` (4) × `typeBucket` (~20) × `attributeBucket` (5, optional) × `personalityType` (16) × `core` (4) ×
`roleplayVoice` (~81) × `moodBand` (5) × `owned|wild` (2) × `familiarityBand` (3) × species fact atoms. Every reply
must combine ≥5 dimensions; a response may ignore one only by declaring it neutral (`conditions: {}` = any).

### 5.3 Distinctness gates (validator, **R**)

- 100% of 1,662 species render **≥3 fact-bearing identity replies** with all `{slots}` resolved (hard error otherwise).
- No two species render an identical full signature line; authored-signature coverage ≥18% (300+), floor 263.
- A single (family, frame, trait) combination is used by ≤8 species; assignment deterministic by `sha256(speciesKey)`;
  any two species in one family+tier bucket differ on ≥2 of 3 axes (frame, fact, tone).
- **Known limit**: lexical coverage is cheap; *deep voice distinctness* only reaches A-tier (263); the rest are correct-but-archetypal — acceptable because a player owns a handful of species.

## 6. Corpus math for a SimSimi-like feel

### 6.1 Assumptions (**E**)

Session = 20 player messages; 4 sessions/week → **~347 messages/month**. 130 intents = 10 top (50% traffic →
17.4 hits/intent/month) + 30 mid (35% → 4.0) + 90 tail (15% → 0.6). Anti-repeat memory N = 8 per individual.
Required variants `R = ceil(monthly_hits × horizon) + 1`; a repeat is noticed when the fresh pool ≤ hits in horizon.

### 6.2 Targets (**E**, derived)

| Layer | MVP | Launch | Math |
|---|---|---|---|
| Intent clusters / match patterns per intent | 60 / 8 | 130 / 12 | topic table §3.1; 130×12 ≈ 1,560 short strings |
| Response variants per intent | top 8 / mid 4 / tail 2 (8/22/30 intents) | top **18** / mid **6** / tail **2** | 10×18+30×6+90×2 = **540** base |
| Stage-extreme variants (T1/T4) | 20 | 60 | 10 intents × 2 extremes × 3 |
| Species signature lines (A-tier) | 100×1 | 263×2 | 526 |
| Type-family voice lines | 60 | 240 | 20 buckets × 4 tiers × 3 tones |
| Mood/bond + familiarity overlays | 40 | 120 | 5 bands × 12 families × 2 |
| Event/proactive lines | 40 | ~117 | 13 events × 4 + existing 65 |
| Fallback ladder + safety redirects | 48 | 78 | 5 levels × 12 + 6 categories × 3 |
| **Total authored lines** | **~520** | **~1,681** (≈15.1k words) | 9 words/line; +1,662 generated species fact atoms |

- 1-month horizon gives the launch 18/6/2 targets; a 3-month top-intent horizon in cycle 2 needs R≈53 (+~350 lines, mature
  ≈2.4–2.6k). Tail repeat is acceptable; greeting/bond/identity repetition is not.

## 7. Proposed free-chat content model

### 7.1 File + schema (**R**)

Additive eighth pack `app/src/main/assets/digiline/v1/<locale>/free_chat.json` (+ generated `species_voice.json`).
Add manifest `files.freechat`, bump `contentVersion` to **5**, keep `schemaVersion: 1`: loader uses
`ignoreUnknownKeys = true` and only requires its 7 `REQUIRED_KEYS` (`digiline/content/DigilineContentSource.kt`), so an
optional new key is backward-compatible; app-side `DigilineContentPack` gets a defaulted `freeChat` field.

```json
{ "schemaVersion": 1, "contentVersion": 1, "locale": "en",
  "topics": ["self","digital_world","evolution","training","bits","food","other_digimon","tamer_bond","device_dim",
             "field","battle","quest","home","wild","play","fallback","safety"],
  "intents": [{ "id": "fc.greeting.1", "topic": "greeting", "tags": ["owned","wild"],
    "match": { "phrases": ["hello","hi","hey","bom dia"], "keywords": [["hello"],["bom dia"]], "minScore": 0.72 },
    "conditions": { "owned": true, "stageTiers": [1,2,3,4], "types": [1,"…",16], "moodMin": 0, "moodMax": 100,
                    "recentEvents": [], "speciesTypes": [], "familiarity": ["any"] },
    "responses": [{ "id": "fc.greeting.1.r1", "text": "You came back. Good. I was getting bored of the noise here.",
                    "tone": "friendship", "types": [14,15], "stageTiers": [2,3], "energy": 1, "moodDelta": 0,
                    "shift": { "14": 1 }, "loreRefs": [] }],
    "fallback": "fc.fallback.2" }],
  "speciesVoice": [], "moodOverlays": [], "eventReactions": [], "fallbacks": [], "safety": {} }
```

Reuse existing field names: `stageTiers` (from `stageTier`), `types` (event lines), `tone` (4 existing core tones),
`moodDelta`, `shift`, `tags`, `authoringStatus`, `loreRefs` (quiz). New: `match`, `conditions`, `speciesVoice`,
`fallbacks`, `safety`, `energy`/`voice` tags.

**IDs (R)**: `fc.<topic>.<n>` intent · `fc.<topic>.<n>.r<k>` response · `fc.fallback.<level>.<k>` ·
`fc.safety.<category>.<k>` · `fv.<speciesKey>` species voice card · `ff.<family>.<tier>.<k>` family line ·
`fm.<band>.<topic>.<k>` mood overlay. Lowercase `[a-z0-9._]`, stable across locales.

### 7.3 Validator extension (`tools/digiline/validate_packs.py` + `digiline/content/DigilineContentValidator.kt`, **R**)

`validate_free_chat()`: ID uniqueness across all packs; every intent has ≥1 phrase and ≥1 response; coverage of every
topic, personality 1..16 (≥2 intents or explicit core fallback), stage tier, `owned` and `wild`, and 5/5 mood bands;
fallback ladder complete/bounded; safety categories present; `speciesVoice` keys + `loreRefs` resolve; fact lint
(fact topic or named species/level/move requires refs); **reuse** the originality check (same reference corpus),
language lint (`GENDERED_RE`, `SMT_TERM_RE`, placeholders), pt-BR extra lint, cross-locale ID/condition parity;
rendered-species coverage 1,662/1,662 with unresolved-slot errors; distinctness gates §5.3; per-topic unique response
ratio ≥90%. Add `freechat` to `EXPECTED_FILES` and collect its text/phrases into `authored_texts`.

### 7.4 Size budget (**E**)

1,681 lines × ~190 B ≈ 310–330 KiB/locale + lexicon ≈53 KiB + generated `species_voice.json` ≈145 KiB (1,662 × ~90 B of
keys/flags, not profile prose) → **~500–520 KiB/locale**, ~1.0–1.1 MiB for EN+pt-BR on top of the 2.80 MiB pack set.
**R** cap free chat at ≤600 KiB/locale; flag parse-budget impact to 03 (loader is eager today).

## 8. Build-time content pipeline (D1/R10-clean)

1. **Derive candidates** (no prose reuse): 1,173 answer→reaction pairs from question packs, 1,296 from negotiation, 65
   event lines, quiz *facts*; catalog fact atoms for all 1,662 species; A-tier key/alias map from `sp.*` ids + `MANUAL_ALIASES`.
   Then **offline LLM-assisted drafts** (R10): context object + style guide + "invent no lore" + required refs, tagged by
   topic/tone/stage/type; drafts never ship directly — human rewrite, then 2-reviewer sign-off.
3. **Dedupe, citation and coverage gates**: normalized duplicate check against all packs (reuse `normalized_exact`/`loose`),
   per-intent variant counts, opening-word repeat check; every fact line resolves `loreRefs` or the build fails; report
   per-species coverage (level/type/move/profile availability) into `tools/digiline/free_chat_coverage.json`.
4. **Generation + gates**: extend `build_packs.py` with `build_free_chat()`/`build_species_voice()` using only `sha256`
   stable hashes (byte-identical output); run `build_packs.py` → `validate_packs.py` (0 errors) → app-side
   `DigilineAssetPackTest` → playtest corpus gate (§10.2 risk 2).

## 9. Localization: pt-BR-first, tied translation

- **F** UI is already pt-BR (`values-pt-rBR`), personality/voice text bilingual; content packs are EN-only. **R** author and review in **EN** (§5.5), then translate **tied** (same IDs, conditions, variant counts, order) — not parallel authoring.
- **Terminology pinned to existing UI strings (F)**: Digimon, **Tamer** (proper title), **Bits**, **Atributo**,
  **Vacina / Vírus / Dados / Livre** (`values-pt-rBR/strings.xml` 949–950, 1245–1248), **Evolução** (0 occurrences of
  "Digivolução" today). Stage labels in `chat/DigimonPersonaBuilder.kt:541`: **Bebê I / Bebê II / Criança (Rookie) /
  Adulto (Champion) / Perfeito (Ultimate) / Ultimate (Mega)** — flag this mix for product review before hard-coding labels.
- Workflow: EN freeze → offline machine draft (R10) → bilingual translation + terminology pass → native pt-BR review →
  validator parity/lint → release; neutral gender (§5.5); cost ≈ 11–16 person-days for 1.7k lines (**E**), ~1.5–2 weeks
  parallel; missing keys fall back to EN and are reported; release blocks on missing keys in a shipped locale (§5.5).

## 10. Effort, risks, blocker

### 10.1 Effort (**E**; repo has no historical velocity — only volume: 22,256 words already authored)

| Work package | MVP | Launch (EN) | Basis |
|---|---|---|---|
| Authored lines + match patterns | 520 + 480 @75/200/day ≈ **9.5 d** | 1,681 + 1,560 @50/75/110 ≈ **42/30/23 d** | short lines; each needs tone/stage/lore check |
| Species/family generation code | ~2 dev-days | ~3 dev-days | deterministic generator |
| Review + citation/originality QA | +20% ≈ **1.4 d** | +20% ≈ **5 d** | separate reviewer |
| pt-BR tied translation + native review | ≈ **5 d** | ≈ **16 d** | translator |
| **Elapsed (single-threaded)** | **~3 weeks** | **~10–11 weeks** | 2 writers + 1 translator in parallel: **~7–8 weeks** |
| Cycle 2 (3-month horizons, +700 lines, more species) | | +**4–6 weeks** | |

Sensitivity: launch EN at 50/75/110 lines/day = 6.7 / 4.5 / 3.1 weeks for one writer (before review/translation); content
is the critical path, so **R11** (content owner) must be settled before Phase 6.

### 10.2 Top risks

1. **D1 licensing/authoring capacity (blocking)**: every line original + reviewed + cited; public datasets from the
   research doc are unusable; no named owner. DCO/Time Stranger reuse would demo well and fail the originality/legal gate.
2. **Repetition exposure**: the short/surprising promise breaks fastest on small corpora; top intents need 18+ fresh
   lines/context or repeats appear within a month (§6). Existing packs cannot substitute (persuasion: 18 unique of 448).
3. **Species coverage vs voice quality / attribute data**: 1,662 species, 263 with cards, ~300 authorable at launch; rest
   are archetype + catalog nouns; unknown-level species need safe neutrals; attribute is only 62.6% recoverable → optional.
4. **Lore/citation gap**: all 1,508 lore facts have `sourceUrl: null`; §5.4 vs catalog-only facts must be resolved (F23).
5. **Safety/children audience**: free text invites insults, sexual content, self-harm, personal data; offline fixed
   redirects only, no server moderation; SimSimi's own ban history (per the brief, not verified here) is the precedent (04).
6. **pt-BR quality/terminology + size budget**: translation can drift tone/length; official vs English terms are not
   fully consistent today; 2.80 MiB packs already exceed the ≤2 MB/locale target and free chat adds ~0.5 MiB/locale.
7. **Voice contradiction / assistant drift**: tone tags must be hard filters (else SLY/ENLIGHTENED sound compassionate);
   LLM-assisted drafts default to helpful-assistant register → banned-phrase lint + review rubric.

### 10.3 Single biggest blocker

**No owner/capacity to author and review a D1-clean corpus (~1.7k EN lines + tied pt-BR ≈ 3.4k line-units) for a feature
whose perceived quality collapses below that volume.** Engineering can build engine + validator in parallel; content sign-off is the critical path (plan R11 + D1 consequence).

## 11. Open decisions for the product owner

1. Amend §5.4 (catalog canonical for game facts) or fund a cited lore registry; authored species depth: 263 (existing
   cards) / 300 (target) / more, rest accept archetype voice?
2. Free chat owned-DigiLine only first or wild too (wild raises D6/persuasion/safety scope), and pt-BR at launch or
   EN-only MVP (audience suggests pt-BR first)?
3. Do free-chat turns move mood/bond at all (daily cap? D2/D3/D4 propagation), and which retrieval layers are in scope
   now — exact+lexical MVP vs embeddings later (see 03)?
