# 03 — Technical feasibility: offline, entity-aware SimSimi-style matching on Android (Tamers Lab)

Status: analysis for the plan. Scope: retrieval/matching engine for a **free-text, offline, no-runtime-LLM**
DigiLine chat that must be **Digimon-contextualized** (species/aliases, level, attribute, moves, world terms,
partner state), bilingual-ready (pt-BR first-class), deterministic and anti-reroll. §4 of the task brief is
integrated below: entity lexicon, context filters/boosts, diegetic fallbacks, pt-BR normalization.

Legend: **[M]** measured this session (repo/host/GCS/Maven), **[S]** sourced from an external page,
**[E]** estimate with stated basis. No Android device was available here; the only device numbers are the
recorded A34 (SM-A346M) values from `docs/performance/BASELINE.md` (2026-10-31), not re-run.

## 1. Recommendation (short)

**MVP: entity-aware lexical hybrid, in-process, build-time index — no ML runtime, no Room FTS.**
Pipeline: `normalize (NFKD, mojibake repair, pt-BR slang map) → entity extraction (species/level/attribute/move/world terms)
→ integer BM25 over an asset binary index of authored reply records → context hard-filters + soft boosts
(speciesKey, personality/core, stageTier, mood band, owned/wild, recent event) → deterministic top-k seeded variant
→ confidence gate → diegetic fallback ladder`. Reuse `DigilineSeed`, `SocialEventAppraisal`, `DigilineStages`,
`CanonicalSpeciesCatalog.key()`, the `DigilineContentProvider/Loader/Validator` pattern.

**Explicitly rejected for this app: on-device sentence embeddings.** The release APK budget is
163,053,568 B and the recorded release APK is 149,019,234 B → **14,034,334 B (13.4 MiB) headroom** [M, BASELINE §3].
The prebuilt ONNX Runtime Android AAR alone is **59,335,382 B (56.6 MiB)** [M]; the small multilingual encoders we
evaluated are ~118 M parameters → **~118 MB int8 / ~471 MB fp32** before tokenizer [M params + arithmetic]. MediaPipe's
own embedder assets are 0.74 MB (English word-average), 5.84 MB (English USE), 24.9 MB (English BERT), 175.3 MB
(Gemma) [M, GCS] — none is a pt-BR encoder. Realistic embedding APK delta: **≥30 MB (English-only) up to ~120–165 MB for
pt-BR-adequate**, plus 120–400 MB RSS [E], for a corpus of a few thousand authored lines: a budget change plus a
product/distribution change (Play Asset Delivery/download), not an engineering option.
Upgrade path is Room/DB index at >15–20k replies and, only if the product ever allows out-of-APK model delivery,
an optional embedding reranker behind a flag.

## 2. Measured constraints (the numbers the design must respect)

| Fact | Value | Provenance |
|---|---|---|
| Release APK budget / recorded APK / headroom | 163,053,568 B / 149,019,234 B / **14,034,334 B** | [M] `docs/adr/0005-performance-budgets.md`, `docs/performance/BASELINE.md` §3 (recorded 2026-10-31, not re-measured at HEAD) |
| Cold start TTID (benchmarkRelease, A34) | median 875.0 ms | [M] BASELINE §3 row 1 |
| Battle frame CPU p95 | 13.3 ms | [M] BASELINE §3 row 2 |
| Stack | Kotlin 2.4.0, Compose, Room **2.8.4**, Koin 4.1.1, minSdk **28**, target/compile 37, R8 + shrinkResources on release, `:baselineprofile` + `:benchmark` modules, `INTERNET` still declared | [M] `app/build.gradle.kts:22-106`, `gradle/libs.versions.toml` |
| Content JSON library | kotlinx.serialization 1.8.1 (`DigilineJson`), Gson 2.14 (saves/network only); **no ML runtime dependency** | [M] `app/build.gradle.kts:237,271-273`; `digiline/content/DigilineContentLoader.kt:21-24` |
| DB | `AppDatabase` **v56**; **zero Room FTS usage today** (grep `Fts`/`MATCH` finds none) | [M] `data/database/AppDatabase.kt`, grep |
| Runtime SQLite | AOSP build flags enable FTS3/FTS4 only on **API 28 branch and main** — **no FTS5, therefore no `bm25()`** | [M] AOSP `external/sqlite/dist/Android.bp` (android-9.0.0_r61 + main) [1][2] |
| Content-load target (plan) | ≤250 ms cold, ≤2 MB per locale | [M] `DIGILINE_CHAT_SYSTEM_PLAN.md` §4.3/§9.1 |
| Current `digiline/v1/en` pack | **2,936,253 B raw** (over the 2 MB plan target), 202,831 B gzip → **6.9 %** on host | [M] assets + gzip proxy (deflate proxy for APK assets) |
| What the packs contain | 263 species questions (789 options), 128 generic questions, 2,882 quizzes, 736 negotiation nodes/1,296 options, 65 event lines, 1,508 lore facts | [M] pack JSON counts |
| Entity source | `species_catalog.json` 13,319,315 B raw / 3,469,018 B gzip: **1,662 species**, 2,721 alias entries (2,602 unique, 1,035 differ from `name`), **4,966 special-move entries (3,980 unique)**, 147 type values, 715 multi-word names, **5 non-ASCII name/alias entries whose encoding is double-encoded UTF-8 mojibake** (`AnheiNÃ¼washou`, `BÅ«mon`, `ChoÂ·Hakkaimon`, `LÃ¶wemon`, `NÃ¼washou`) | [M] catalog parsing |
| Bilingual vocabulary already authored | `chat/lorebook/DigimonWorldLore.kt`: 23 entries, en/pt-BR/ja, 25,225 B (Digital World, Digivolution, Attributes, Jogress, Royal Knights, families, worlds) | [M] |
| Legacy unused asset | `species_chat_examples.json` 172,465 B raw / 57,705 B gzip, 268 species × 4 tamer→digimon exchanges = **1,072 pairs, 108,876 chars, avg 101.6 chars/pair** (licensing: reference-only per D1, not shippable copy) | [M] |
| Attribute data | per-sprite-set code `0..3`→Free/Virus/Data/Vaccine (`CatalogGameplayProfile.attribute`), plus live partner `UserCharacter.attribute`; catalog-derived lore facts have `attribute=null` (needs card/save/authoring source) | [M] `species/CatalogGameplayProfile.kt:24-30`, `digiline/model/ContentDtos.kt:229` |

## 3. Candidate strategies compared

Sizes are APK delta after deflate; corpus assumption for all retrieval rows: **8,000 authored reply records**
(~120 chars each, en) plus entity lexicon; quality = fraction of real player turns that get a reply the player
judges in-character (hypothesis, see §8 measurement #3).

| Strategy | APK delta [E] | RAM [E] | Turn latency [E] | Quality [E] | Determinism | Verdict |
|---|---|---|---|---|---|---|
| Exact + alias dictionary (normalized hash/ map) | +30–80 KB (2–5k aliases) | <0.5 MB | <1 ms | 20–35 % — greetings, thanks, species names; no paraphrase/typos | Bit-exact | Ship as stage 0 layer |
| **In-memory integer BM25, build-time binary index** | **+0.3–0.6 MB** | **2.5–4 MB** | **2–6 ms** | 40–60 % raw; 70–80 % with entity+context+fallback ladder | Bit-exact if Q16 ints, sorted tie-break | **MVP core** |
| Room FTS4 (`@Fts4`, external content, `matchinfo` ranking) | +0.3–0.6 MB corpus, +1–3 MB DB file | DB page cache 2–8 MB | 2–10 ms query; **200–800 ms first-open populate** | same lexical ceiling; tokenizer misses slang/nested names | Deterministic with explicit `ORDER BY` | Defer; revisit >15–20k replies or DB-driven packs |
| Char n-gram + OOV fuzzy (length-bucketed Lev≤1, trigram rerank) | +0–300 KB | +1–2 MB | +0.5–2 ms | +8–15 pp on typos/abbreviations | Bit-exact (bounded int edit distance) | Add in stage B, not stage A |
| On-device embeddings (ORT Mobile / LiteRT / MediaPipe TextEmbedder) | **+30 MB (English-only) to ~160 MB (pt-BR int8)** (see §4) | +120–400 MB | 20–120 ms/query; cold model load 0.5–2 s | improves paraphrase recall; corpus too small to matter | float non-determinism risk; needs quantized+fixed ONNX | **Infeasible now** |

Why not FTS5: it is the only SQLite FTS that ships `bm25()`; Android's platform SQLite is compiled **without**
`SQLITE_ENABLE_FTS5` on both the minSdk-28-era branch and current main [M][1][2], and Room has no FTS5 annotation
(only `@Fts3`/`@Fts4`) [S][3]. FTS4 therefore gives tokenized candidate lookup but ranking must be written in Kotlin
from `matchinfo` anyway — the same work as our own index, plus a DB migration (v56→v57) and first-run populate.

## 4. Why embeddings are unrealistic here (numbers)

| Component | Size | Provenance |
|---|---|---|
| `com.microsoft.onnxruntime:onnxruntime-android:1.32.0` AAR | **59,335,382 B** (prebuilt full package: all ABIs, all operators) | [M] Maven Central listing [4] |
| ORT minimal/custom build (reduced ops, ORT-format model, no exceptions) | smaller but requires C++ toolchain, ops-config, model conversion; per-ABI estimate 5–10 MB + Java/Kotlin glue | [S] ONNX Runtime custom-build docs [5], size [E] |
| MediaPipe TextEmbedder assets | `average_word_embedder.tflite` 774,339 B; `universal_sentence_encoder.tflite` 6,120,274 B; `bert_embedder.tflite` 26,142,232 B; `embedding_gemma.task` 183,816,181 B | [M] GCS bucket listing `mediapipe-models/text_embedder/` [6]; task documented at [7] |
| `paraphrase-multilingual-MiniLM-L12-v2` (Apache-2.0, `pt-br` in language list) | **117,654,272 params** → ~471 MB fp32 / ~118 MB int8 | [M] HF API [8] + arithmetic |
| `intfloat/multilingual-e5-small` (MIT) | **117,654,272 params** → same ~118 MB int8 | [M] HF API [9] + arithmetic |
| `neuralmind/bert-base-portuguese-cased` (BERTimbau, MIT) | 12 layers, 768 hidden, vocab 29,794 → ~110 M params → ~440 MB fp32 / ~110 MB int8 | [M] HF config [10] + arithmetic |
| `minishlab/potion-multilingual-128M` (static model2vec embeddings, MIT) | **128,090,368 params** F32 (~512 MB) — static lookup has no transformer cost but the table is the model | [M] HF API [11] |

Consequences: even the ideal "small" multilingual model is ~118 MB int8, and 14.03 MB is the entire APK headroom;
LiteRT/MediaPipe would shrink the runtime but still need a pt-BR encoder (English-only USE/word-avg assets are not
adequate for pt-BR slang). Cold-start cost (model load 0.5–2 s [E]), RSS (118 MB weights + 1.5–3× runtime overhead [E])
and float determinism all violate ADR-0005 budgets. **Recommendation: no embedding runtime in this feature.**

## 5. Recommended MVP architecture (entity-aware, themed)

### 5.1 Data assets (build-time generated, per locale)

`app/src/main/assets/digiline/v2/free_chat/<locale>/`:
1. `free_chat.json` — authored reply records (source of truth, validated at build + CI):
```json
{"id":"owned.agumon.greet.01","locale":"pt-BR","audience":"owned","text":"...",
 "speciesKey":"agumon","entityTags":["agumon","child","vaccine"],"topics":["greeting"],
 "personalityTypes":[],"core":"VALOR","stageTiers":[1],"moodBands":[0,1,2,3],
 "eventKeys":[],"safetyClass":"safe","loreRefs":[],"contentVersion":5}
```
2. `free_chat.idx` — binary, versioned, hashed; **no JSON parse at runtime**, single asset file (avoid thousands
   of APK zip entries): header `{magic, idxVersion, contentVersion, locale, packSha256, corpusSize, offsets}`
   → term dictionary (sorted, folded) → postings `[replyIdx,tf,fieldMask]` → doc table
   `{textOff/len, speciesKeyId, typeMask16, tierMask4, coreMask4, moodMask4, audience, eventId, topicId, safetyClass}`
   → text blob (UTF-8, replies) → **entity lexicon**, 7 types with measured coverage: SPECIES (1,662 names +
   2,602 aliases, longest-match), LEVEL (10 catalog values Baby I/II, Child, Adult, Perfect, Ultimate, Armor, Hybrid,
   Unknown + pt-BR nível/fase/in-training), ATTRIBUTE (Vaccine/Data/Virus/Free, from card/save + authored map because
   catalog-derived lore facts have `attribute=null` [M]), MOVE (3,980), GROUP/world lore (23 lorebook entries ×
   en/pt-BR/ja [M]), GAME terms (Digivice, DIM card, bits, Digitama, training, quest, farm, evolution) → slang map →
   emoji→intent map.
3. `free_chat_lexicon.json` — authored overrides/additions (pt-BR aliases, world terms, slang), merged at build.

Sizing from measured ratios: 8,000 replies ≈ 0.96 MB JSON → **~70–100 KB deflated** (existing pack deflates 6.9 % [M]);
index ~350–450 KB raw, binary compresses less (~40–60 %) → **~150–250 KB**; entity lexicon ~400 KB JSON → **~60 KB**.
Total en feature ≈ **0.3–0.6 MB APK**, pt-BR duplicate +0.15–0.3 MB [E, derived from measured compression + record sizes].
Generic formula for content planning: **~30–45 B compressed APK / ~0.4–0.5 KB heap per reply** [E].

### 5.2 Retrieval pipeline and scoring

1. **Normalize** (`FreeChatNormalizer`): trim → Unicode **NFKD** → strip combining marks → lowercase(Locale.ROOT) →
   mojibake repair (latin-1/UTF-8 round-trip detection, fixes the 5 catalog names [M]) → replace punctuation/emoticons
   with spaces → apply slang map (`vc→voce`, `pq→porque`, `tb/tbm→tambem`, `kkk/rsrs/haha→LAUGH`, `blz→beleza`,
   `pfv/pf→por favor`, `cmg→comigo`, `tá→esta`, `tô→estou`, `né→nao e`, `bora`, `vlw`, `flw`, `hj`, `mds` …).
   Keep raw surface forms too: Digimon names must match on both raw and folded keys (`Nüwashou`↔`Nuwashou`); never
   rewrite the display name. Reuse/align `CanonicalSpeciesCatalog.key()` (NFKD + letters/digits + X-Antibody/color
   handling) [M] and fix the inconsistency with `DigilineContentPack.normalizeSpeciesKey` (lowercase-only, no NFKD) [M].
2. **Entity extraction** (`EntityLexicon.extract`): longest-match scan of lexicon surfaces (multi-word first:
   `Metal Greymon` before `Greymon`; `Royal Knights`, `Mundo Digital`, `Digivice`, `DIM`, `bits`, `Digitama`).
   Emit `EntityMention(type, targetKey, span, weight, ambiguous)`; ambiguous aliases (1 name → many species) never
   hard-boost, they only add a generic species-topic tag (same conservative rule as `CanonicalSpeciesCatalog.resolve`,
   which returns null for ambiguous names [M]). Entity term ids carry a build-time weight that outweighs generic words:
   species ×1.8, move/group ×1.5, world/game term ×1.3 of base IDF; ~120 en+pt stopwords and generic function words are
   dropped at build time so "digivolve"/"MetalGreymon" beat "you"/"like" on a tie.
3. **Candidates**: postings union/intersection over BM25 terms + entity term ids; if <3 candidates, OOV expansion
   (unknown token → same-length±1 vocabulary bucket, Damerau-Levenshtein ≤1) and retry; if still <3 → fallback ladder.
4. **Hard filters** (applied while traversing the doc table): `locale`, `audience` (owned|wild), `stageTier ∈ tier±1`,
   safety class compatible with input class, and exclusion of the last 2 reply ids in this conversation.
5. **Soft boosts** (integer, additive on a 1000-basis percent score; exact weights tunable in unit tests):

| Context signal | Source | Boost |
|---|---|---|
| `speciesKey` == partner species | `DigilineStages.speciesKey(character.species)` [M] | +15 % |
| species mentioned ≠ partner species (talking about another Digimon) | entity extraction | +8 % |
| `personalityTypes` contains partner type | `DigimonPersonalityTraits` [M] | +10 %; core match +5 % |
| `stageTiers` includes partner tier | `DigilineStages.tier(cardStage, level)` [M] | +8 % |
| `moodBands` includes partner mood band (0–24/25–49/50–74/75–100) | `UserCharacter.mood` [M] | +8 %; opposite extreme −10 % |
| `eventKeys` includes last delivered event | `DigilineEventRecord` [M] | +8 %, decays 10 %/turn |
| topic already used in this conversation | reply history | −20 % |
| same species family/level as an entity mention | catalog `type`/`level` [M] | +5 % |

6. **Score**: `scoreQ16 = bm25_int(k1=1.2, b=0.75, precomputed Q16 idf) * (1000 + boosts) / 1000`. **All arithmetic
   integer**; IDF/doc-length tables are generated at build time (no runtime `Math.log`, no float compare) → bit-exact
   ranking across ART versions/ARM/x86. Score is stored in the turn record for audit.
7. **Confidence + determinism**: sort by `(scoreQ16 desc, entityMatchCount desc, replyId asc)` — no HashMap/insertion
   order. Accept top-1 if `scoreQ16 ≥ 0.30 × maxPossibleQ16` and `top1 − top2 ≥ 5 %`; else fallback. Among acceptable
   candidates pick variant `idx = DigilineSeed.random(seed).nextInt(min(3, size))` with
   `seed = combine(stableHash(normalizedQuery), stableHash(individualId), turnCounter, contentVersion)`.
   `turnCounter` is the persisted per-conversation counter (`DigilineProfileEntity.sessionCounter` pattern [M]) so a
   restart cannot reroll; the chosen `replyId`+`seed`+`indexVersion` are persisted **before** rendering
   (anti-reroll, plan F10 discipline [M]).
8. **Persistence** (Room v57, additive): `FreeChatTurnEntity(id, individualId, userMessageId, normalizedHash,
   replyId, variantSeed, scoreQ16, indexVersion, createdAt)`; reply text itself is written to `ChatMessageEntity.content`
   (`kind = "free_chat_reply"`, `payloadJson` = replyId/topic/entity tags) so history renders without the index and
   survives content updates. Migration test v56→v57 with fixtures.

### 5.3 Out-of-world / unmatched input: diegetic fallback ladder (no assistant voice)

Every turn ends in an in-persona line; nothing is generated, so nothing can be "assistant-like" or hallucinated.

| Rung | Trigger | Response | Source |
|---|---|---|---|
| 1 | Entity matched but no reply above threshold | authored entity reaction template per entity type: species/level/attribute/move/world term ("*Agumon? That's a Child-level Vaccine...*") using only fact fields, no invented claims | lore facts [M] + authored templates |
| 2 | Intent matched (`SocialEventAppraisal`) | existing personality-weighted event lines; extend `event_lines.json` with `freechat.*` events per locale | [M] `domain/personality/SocialEventAppraisal.kt`, `event_lines.json` |
| 3 | Topic known but nothing scored | authored curiosity/deflection per personality + "ask me about X" chips (species, Digital World, training) | authored pack |
| 4 | Nothing matched | short in-character "confused" line + optional card handoff (question/quiz scene already in the gateway) — no dead end | `DigilineGateway` [M] |
| 5 | Safety class hit | neutral authored deflection per class; repeat-hit escalates to rung 4; **never echo user text** | safety pack |

Out-of-world input (politics, other IPs, real-world requests) is rung 1/3: the Digimon answers as a Digital-World
being that does not know about it, without pretending to be an assistant. Unknown species names are not corrected
and not invented — rung 1 template says the name is not in the field records (authored line, no lore claim).

### 5.4 Safety filtering offline (feasible; it is a lexicon problem, not an ML problem)

Because output is 100 % authored, output safety = content validation; only *input* handling needs a filter.
Build-time `safety.json` per locale: profanity/slur list en+pt-BR (200–600 entries), sexual/minor terms, self-harm
terms, personal-data regexes (phone/CPF/e-mail/long digit runs), and an escalation counter. Runtime cost <0.2 ms
[Aho-Corasick/word-boundary scan, E], APK <15 KB [E]. Recall on blatant abuse ~80–90 % [E]; graceful degradation is
acceptable because the reply is always safe and never mirrors the input; a `safetyClass` on every reply record lets
the retriever hard-filter incompatible tone. Validate in CI: no banned term in shipped replies, no reply echoes
`{user}` slots, blocked-class queries never reach a reply with `safe` compatibility.

### 5.5 pt-BR specifics

- Accents folded for **matching only**; display strings stay accent-correct. Lexicon stores raw+folded surfaces.
- pt-BR entity aliases are authored (official/localized names mostly equal English; e.g. lorebook already has
  `Cavaleiros Reais`, `Mundo Digital`, `Digivolução`) — add a `pt-BR` alias file (~1–2k entries, ~20–50 KB raw,
  ~10 KB deflated [E]); never auto-translate species names.
- Tokenization: whitespace/punctuation split after folding is enough for short chat (no linguistic stemmer);
  keep `não/nunca/nunca mais` negation as plain tokens (they are in the intent/safety lexicons).
- Abbreviations/slang: one shared map for en+pt-BR (`vc`, `pq`, `kkk`, `blz`, `tbm`, `dps`, `qnd`, `mds`, `vlw`);
  emoji → intent (LAUGH/SAD/ANGRY/LOVE) via fixed codepoint ranges; numbers >=6 digits → PII safety class.
- Content parity gate: same reply ids across locales, no missing keys (existing validator lints translations [M]).

### 5.6 Integration points (no rewrite of scripted DigiLine)

- New package `com.tamerslab.digiline.freechat`: `FreeChatIndex`/`Loader`/`Provider` (mirror
  `DigilineContentProvider` caching + fail-open empty index [M]), `FreeChatNormalizer`, `EntityLexicon`,
  `FreeChatRetriever`, `FreeChatSafetyFilter`, `FreeChatFallback`, `FreeChatEngine` (pure, JVM-testable),
  `FreeChatRepository` (Room context assembly + turn persistence), `FreeChatGateway`.
- Context assembly next to `OwnedDigilineRepository.ownedInput()` (`OwnedDigilineRepository.kt:435-461` [M]):
  speciesKey/tier/personality already computed there; add `moodBand`, attribute, audience, last event.
- Wire in `DefaultAppContainer` (content provider pattern at `di/DefaultAppContainer.kt:276` [M]); add to
  `AppContainer`; keep the scripted `DigilineGateway` untouched and expose free chat via a new
  `FreeChatGateway.observeConversation/send` so card scenes keep working; `ChatScreen.kt:263-284` input bar
  becomes the free-chat composer only when `BuildConfig.FREE_CHAT_ENABLED` (A/B `-P` flag convention as in
  `app/build.gradle.kts:15-20` [M]). Legacy LLM path stays debug-only and untouched.
- Content pipeline: extend `tools/digiline/build_packs.py` (+ a `build_free_chat.py`) to emit assets + index and run
  a pure-Kotlin validator test through the existing map-backed loader seam (`DigilineContentLoader(reader = …)` [M]).
- R8: no new keep rules expected (kotlinx.serialization `$$serializer` keep already present [M]); index is read via
  `AssetManager` on `Dispatchers.IO` like today's loader.

## 6. Performance budget table for the feature

| Metric | Target / budget | Estimate for MVP | Basis |
|---|---|---|---|
| APK delta (en+pt-BR corpus 8k replies each + index + lexicon + safety) | must stay ≤ ~1.5 MB of the 13.4 MB headroom | **0.5–1.1 MB** | derived from measured deflate ratios [M]+[E] |
| Cold-app-start delta | 0 ms (no work in `Application`/first frame) | 0; index lazy-loaded on first free-chat open | design |
| First free-chat open (index load + decode + verify) | ≤250 ms (plan §4.3) | 30–90 ms | 0.5–1 MB asset stream + decode, [E] |
| Per-turn compute p95 (normalize+entities+rank+variants) | ≤16.7 ms frame budget (ADR-0005 #2) | **2–6 ms**, ranking <2 ms | operation counts [E] |
| Heap delta after first open | ≤5 MB | 2.5–4 MB | byte layout arithmetic [E] |
| DB growth | ≤100 KB / 1,000 turns | ~60–100 KB | row shape |
| Battery/thermal | no wakeups, no GPU/NNAPI | <10 ms CPU/turn, negligible | design |
| Determinism | identical output for identical (state, contentVersion) | yes — all-integer, sorted tie-break | design + tests |
| Index invalidation | version mismatch → feature-off fail-open, scripted cards still work | header hash check | pattern from `DigilineContentProvider` [M] |

Note ADR-0005 §Evidence rule 1: adding an asset pipeline + build script invalidates prior APK-size/cold-start
evidence, so the plan must re-record release APK size and `StartupBenchmark` after merge.

## 7. Risks

1. **Content, not matching, is the quality ceiling.** 1,072 legacy pairs (~107 KB) are reference-only (D1); an 8k-pair
   authored corpus is the real cost. Retrieval cannot exceed its corpus; plan the fallback ladder as first-class UX.
2. **Entity matching false positives** (`Greymon` inside `MetalGreymon`, names that are ordinary words, pt-BR aliases)
   — longest-match + ambiguity rule + unit-test corpus (≥300 utterances with expected entity spans).
3. **pt-BR data quality**: catalog mojibake (5 names [M]), missing pt-BR aliases, accent/slang drift; needs a
   native-reviewed alias file and golden set.
4. **Index/version drift** between authored pack, generated index and app: version+hash check, fail-open, unit test
   that regenerated index bytes are deterministic (same input → same sha256).
5. **Overfit tuning** of boosts/threshold without a measured golden set; freeze the test set before tuning.
6. **Scope creep to embeddings** — see §4; revisit only with an explicit budget/product change.
7. **Safety**: pt-BR slur lists are hard to keep complete; mitigation is never echoing input + authored-safe output,
   and escalation to card scenes rather than trying to classify everything.

## 8. What the plan must measure (top 3)

1. **Release APK delta** on the same build as the candidate (`assembleRelease`, bytes, before/after, per ADR-0005 §4.4)
   and per-locale asset contribution — must leave the APK well under 163,053,568 B.
2. **Latency on the A34**: first free-chat open (index load→first rendered reply) and per-turn p50/p95 (Perfetto
   `TraceSectionMetric` in `:benchmark`, target ≤250 ms open / ≤6 ms compute), plus cold start unchanged (875 ms reference).
3. **Retrieval quality + abstain rate** on a frozen **300–500 utterance pt-BR/en golden set** (intent, expected entity
   spans, acceptable reply topic): top-1 accept %, wrong-context %, fallback-ladder hit %, entity-extraction F1,
   repeat rate over 20 turns.

Also record (secondary): memory delta (`dumpsys meminfo` before/after + 30-min soak), determinism replay test
(same fixture → same reply ids across 2 processes), index sha256 reproducibility, and safety-class confusion counts.

## 9. References

Repo (all read this session): `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`; `docs/adr/0005-performance-budgets.md`;
`docs/performance/BASELINE.md`; `DIGILINE_CHAT_SYSTEM_PLAN.md` §4.3/§5/§9.1; `app/src/main/assets/digiline/v1/**` + `manifest.json`;
`app/src/main/assets/species_catalog.json`; `app/src/main/assets/species_chat_examples.json`;
`digiline/content/DigilineContentLoader.kt|Provider.kt|Validator.kt`; `digiline/model/ContentDtos.kt|DigilineContentPack.kt|EngineModels.kt`;
`digiline/data/DigilineStages.kt|OwnedDigilineRepository.kt`; `digiline/gateway/DigilineGateway.kt`;
`species/CanonicalSpeciesCatalog.kt|CatalogGameplayProfile.kt`; `domain/personality/SocialEventAppraisal.kt`;
`chat/lorebook/DigimonWorldLore.kt`; `chat/ui/ChatScreen.kt`; `domain/chat/ChatMessageEntity.kt`; `tools/digiline/*`.

External (fetched 2026-11-17):
[1] AOSP SQLite build flags, android-9.0.0_r61 — <https://android.googlesource.com/platform/external/sqlite/+/refs/heads/android-9.0.0_r61/dist/Android.bp>
[2] AOSP SQLite build flags, main — <https://android.googlesource.com/platform/external/sqlite/+/refs/heads/main/dist/Android.bp>
[3] androidx Room `Fts4` reference (Room exposes FTS3/FTS4 only) — <https://developer.android.com/reference/androidx/room/Fts4>
[4] Maven Central `onnxruntime-android:1.32.0` (AAR size) — <https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.32.0/>
[5] ONNX Runtime custom/minimal mobile builds — <https://onnxruntime.ai/docs/build/custom.html>
[6] MediaPipe `text_embedder` model assets (byte sizes) — <https://storage.googleapis.com/storage/v1/b/mediapipe-models/o?prefix=text_embedder/>
[7] MediaPipe Tasks TextEmbedder (Android) — <https://developers.google.com/edge/mediapipe/solutions/text/text_embedder>
[8] `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (117,654,272 params; Apache-2.0; pt-BR listed) — <https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2>
[9] `intfloat/multilingual-e5-small` (117,654,272 params; MIT) — <https://huggingface.co/intfloat/multilingual-e5-small>
[10] `neuralmind/bert-base-portuguese-cased` config (BERT-base, MIT) — <https://huggingface.co/neuralmind/bert-base-portuguese-cased/blob/main/config.json>
[11] `minishlab/potion-multilingual-128M` (128,090,368 params; MIT; static embeddings) — <https://huggingface.co/minishlab/potion-multilingual-128M>

## 10. Decisions to confirm before implementation

1. Corpus volume target (content task 02) and whether a first corpus is re-tagged from existing authored packs or
   written fresh (D1 wording requires fresh text for shipped lines; re-tagging only affects the model, not copy).
2. pt-BR alias authorship/review owner (native review gate already required by plan §5.5).
3. Room v57 additive migration accepted (needed for anti-reroll turn records; alternatives: DataStore, but DB keeps
   transactional guarantees with the transcript).
4. Feature flag `FREE_CHAT_ENABLED` + A/B build convention; baseline-profile journeys extended to the free-chat entry
   path; benchmark evidence re-recorded after merge (ADR-0005).
5. Whether the fallback ladder may hand off to card scenes (question/quiz) mid-conversation (design task 04).
