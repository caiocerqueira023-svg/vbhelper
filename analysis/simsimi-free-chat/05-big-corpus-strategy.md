# 05 — "Whole lotta chat data": big-corpus strategy for DigiLine Free Talk

**Status:** addendum to `02-content-data.md`, written after the owner requirement *"i want the project to use a big
response data base like the ones in the research so chat feels more free"* (2026-11-17).
**Scope:** how to get a large response/match database that is (a) large enough to feel free, (b) Digimon-contextualized,
(c) offline, (d) affordable to produce and review.
**Owner override (2026-11-17):** personal, non-distributed build — licensing is a **distribution gate**, not a
blocker. The owner takes responsibility and will remove licensed content/assets if the project is ever distributed.
The chosen source strategy is **hybrid: public dialogue seeds + authored frames + generated lines**, with every seed
rewritten into an in-world line before shipping and a provenance file for later removal.
Legend: **[M]** measured/verified · **[S]** sourced from a page (URL given) · **[E]** estimate with basis.

## 0. Verdict

- **Big corpus: yes, via hybrid sources.** Public datasets are no longer a blocker for the personal build; they are
  used **offline as seeds/patterns only**, every shipped line is rewritten/generated for this project. A
  `DISTRIBUTION_GATE.md` checklist must list every licensed source and its removal/rewrite step before any release.
- **"Big" is achieved through generation + combinatorial rendering**, not by bundling downloaded text:
  ~1.5k–3k reviewed *frames*, ~10k–50k generated-and-validated lines, and a frame × slot composer that yields
  **100k+ distinct in-world surface replies from a small shipped dataset**. Match-phrase stock of 20k–50k makes
  input coverage feel free (target scale locked: **Big v1 = 20k lines / 20k phrases / ~2k frames**).
- **Fits the offline APK budget**: chat assets target **2–6 MB compressed** inside the 14.03 MB headroom; heap
  target 6–10 MB; per-turn cost stays single-digit ms with a compact integer index **[E]**.
- **The bottleneck is quality control at scale**, not writing: automated gates (dedupe, safety, lore citation,
  originality, banned-phrase) + tiered human review (100% of frames/identity/safety, 10–20% sampled generated
  lines) + playtest metrics. Accepting "big" means accepting a **generated content pipeline** as a first-class system.

## 1. Licensing matrix — what can and cannot ship (verified 2026-11-17)

| Dataset | Size (reported) | License | Verified source | Can ship in Tamers Lab? |
|---|---|---|---|---|
| **Cornell Movie-Dialogs** | 304,713 utterances / 220,579 exchanges / 617 movies | **No license stated; extracted movie scripts** | [Cornell page](https://www.cs.cornell.edu/~cristian/Cornell_Movie-Dialogs_Corpus.html) **[S]** | **No** — copyrighted dialogue, research distribution only |
| **DailyDialog** | ~13k dialogues | **CC BY-NC-SA 4.0** | [HF card](https://huggingface.co/datasets/li2017dailydialog/daily_dialog) **[S]** | **No** — non-commercial + share-alike; translation/adaptation is a derivative |
| **EmpatheticDialogues** | ~25k conversations | **CC BY-NC 4.0** | [HF card](https://huggingface.co/datasets/facebook/empathetic_dialogues) **[S]** | **No** — non-commercial |
| **Ubuntu Dialogue Corpus** | 1M examples / ~449k dialogues (v2 generation) | **No explicit data license** (IBM 2015 script; downloads 1-on-1 IRC logs) | [README](https://raw.githubusercontent.com/rkadlec/ubuntu-ranking-dataset-creator/master/README.md) **[S]** | **No** — unclear rights + technical register |
| **OpenSubtitles / OPUS** | hundreds of millions of sentences | **Varies per release; generally copyright-imposed** | OPUS (research brief) | **No** |
| **SODA** | **1,486,896 dialogues** (train 1,191,582 / val 146,346 / test 148,968), machine-generated | **CC BY 4.0** | [HF card](https://huggingface.co/datasets/allenai/soda) **[S]** | **Patterns only, not verbatim** — generic real-world social scenarios; usable as structure/dialogue-act reference with attribution |
| **OASST1** | 161,443 messages, 35 languages, **Portuguese (Brazil) 2,969 messages** | **Apache-2.0** | [HF card](https://huggingface.co/datasets/OpenAssistant/oasst1) **[S]** | **Patterns only, not verbatim** — assistant-style; the pt-BR prompter messages are valuable for *paraphrase mining* |

**Rule adopted:** third-party dialogue text is **never shipped verbatim**; at most it informs the intent taxonomy,
dialogue-act inventory and paraphrase list, and all shipped lines are written/generated for this project.
**Non-commercial datasets are not usable** even if the app is free: a Play app that could be monetized plus Digimon
IP makes the NC constraint and SA obligations unworkable without legal sign-off. (If the owner ever accepts a
strictly non-commercial build, revisit with a lawyer — not part of this plan.)

## 2. What "feels more free" actually means (measurable)

Freedom is **coverage + graceful deflection**, not response length or raw corpus megabytes:

| Driver | Metric | Target (launch big-corpus) |
|---|---|---|
| Input understanding | golden-set top-1 accept (any acceptable intent/topic) | **≥85–90%** (vs ≤70% for the small MVP) |
| No-match | share of inputs that end in rung-4 "confused" fallback | **≤10–15%** (small MVP: ~30%) |
| Topic breadth | played topics with ≥1 authored/generated reply | **≥90%** of the themed topic inventory |
| Phrasing breadth | distinct match patterns | 20k–50k |
| Repetition | same reply twice within 7 days, per individual | ≤10% |
| In-worldness | blind-reader "sounds like a Digimon partner" | ≥90% of sampled replies |
| Safety | banned-topic leakage | 0 (authored outputs) |

Corpus size alone does not move these; **entity recognition + intent coverage + paraphrase stock + the fallback
ladder** do. The big corpus is what keeps the *reply* side fresh once the input side is understood.

## 3. Big-corpus architecture (layers)

The shipped "database" is a set of layers with different generation/review costs:

| Layer | Content | Target size | Cost/notes |
|---|---|---|---|
| **L0 Curated core** | hand-reviewed high-traffic lines: greetings, bond, identity, comfort, boundaries | ~800–1,500 lines | Existing plan; highest quality; 100% human-reviewed |
| **L1 Generated + validated lines** | Digimon-flavored responses per intent/topic/personality/stage/mood, batch-generated offline then filtered | **10k–50k lines** | Automated gates + sampled human review; the volume layer |
| **L2 Combinatorial rendering** | reviewed **frames** with typed slots (species name/level/type/move, event, mood, stage, topic, nickname) composed at runtime | 1.5k–3k frames → **100k+ distinct surface replies** | Every frame human-reviewed once; slots are grounded facts; deterministic |
| **L3 Match-phrase stock** | paraphrases per intent (mined from license-clean corpora + generated), used only by the retriever | **20k–50k phrases** | Never displayed; cheap to validate; biggest "freedom" win |
| **L4 Open-topic deflection** | in-world "I don't know that, but…" templates keyed by detected entity/topic; `unknown` handling | thousands of rendered variants | Turns misses into character content |
| **L5 Licensed pattern mining** | OASST1 pt-BR prompter messages (2,969) and SODA dialogue-act inventory, used to seed L3 phrasing + L2 frames | reference only | Attribution file; no verbatim shipping |

### 3.1 Why frames × slots = "big data" without a big file

A frame such as `"{Species}, {level} — when {event} happened, I felt {tone}. {followUp}"` with grounded slots
(1,662 species × 10 levels × 13 events × 16 personality tones) renders a **combinatorial space** far larger than any
shippable corpus, while every rendered line is deterministic, grounded and reviewable *at the frame level*.
This is how the feature can promise "it always has something to say" inside a few megabytes.

### 3.2 Generation pipeline (offline, D1/R10-clean)

```
intent/topic inventory (dialogue-act taxonomy + Digimon domains)
        │
        ├── L2 frame authoring (human) ──► reviewed frames
        ├── L1 batch LLM drafting (offline; context matrix: topic × personality × stage × mood)
        └── L3 paraphrase generation (offline) + OASST1 pt-BR prompter mining
        │
        ▼
automated gates: schema · duplicate/near-duplicate (MinHash/SimHash) · banned-assistant phrases ·
                 lore-citation · fact-slot resolution · PII/toxicity/safety classes · originality vs reference corpora ·
                 neutral-gender lint (pt-BR/en) · per-intent/per-personality coverage · length/voice rubric
        │
        ▼
tiered human review:
  100%: L0, L2 frames, identity/bond/safety lines, every species fact line
  10–20% stratified sample: L1 generated lines, per topic/personality bucket
  100%: red-team suite results
        │
        ▼
deterministic pack build (byte-identical) ──► sharded assets + binary BM25 index ──► playtest golden set
```

- **Review sampling must be stratified** (by topic × personality × stage), otherwise a whole sub-corpus can ship
  unreviewed. Any automated-gate failure blocks the build.
- **Originality at scale**: hash/3-gram overlap against the reference corpora (including the existing DCO exports)
  catches accidental near-copies; LLM drafts are paraphrased/re-written, never pasted.
- **Content refresh**: versioned packs with a diff report (added/removed lines, coverage deltas); removing lines is
  safe, adding ships with the app (offline).
- **Generation compute**: batch drafting 50k short lines is hours of offline compute (local model or an offline
  API pass), not runtime; cost is dominated by review, not tokens **[E]**.

## 4. Size / performance budget for "a whole lotta" data

Assumptions: reply ≈ 120 chars ≈ 190 B JSON; JSON packs deflate to ~7–20% (existing DigiLine pack: 2.94 MB →
0.20 MB **[M]**); binary index compresses less (~40–60%) **[E]**.

| Corpus scale | Lines/phrases | Pack JSON raw | Pack compressed | Index compressed | Heap | Per-turn [E] | Verdict |
|---|---|---|---|---|---|---|---|
| Small (current plan) | ~0.5–1k lines / 0.5k phrases | 0.2 MB | <0.1 MB | <0.1 MB | <2 MB | 2–6 ms | MVP only |
| **Big v1 (recommended)** | **20k lines / 20k phrases** | 3.8 MB | **0.5–0.8 MB** | 0.8–1.5 MB | 6–10 MB | 3–8 ms | **Fits 14 MB headroom** |
| **Big v2** | **50k lines / 50k phrases** | 9.5 MB | **1.2–2 MB** | 2–4 MB | 12–18 MB | 5–12 ms | Feasible; measure on A34 |
| Huge | 100k+ lines | 19 MB+ | 3–5 MB | 5–10 MB | 25–40 MB | 10–20 ms | Needs Play Asset Delivery / topic-sharded lazy loads + memory work |

- Recommended shipped target: **20k response lines + 20k match phrases + ~2k frames** (≈3–5 MB total APK delta
  including index and species voice), growing to 50k in a second content cycle after device measurement.
- Above ~50k, move to **sharded packs** (load topic shards lazily) or Play Asset Delivery; do not ship a single
  giant parsed JSON.
- Re-measure APK/DB/RSS per `docs/adr/0005-performance-budgets.md`; the 14.03 MB headroom is the hard ceiling.

## 5. pt-BR at scale

- OASST1 provides **2,969 pt-BR messages** for prompter-phrasing mining **[S]**; SODA is English-only, so pt-BR
  frames/lines must be authored/generated natively with terminology review (no machine translation of humor).
- If both locales ship: L1 target divides (e.g., 10k EN + 10k pt-BR) and the index doubles; the 50k plan is for EN
  plus a pt-BR v1 at half size, or 25k+25k.
- Native review of sampled pt-BR lines remains mandatory (neutral gender + Digimon terminology, see plan §7.5).

## 6. Effort and cost model [E]

| Work | Big v1 (20k lines, EN) | Big v2 (50k, EN+pt-BR) |
|---|---|---|
| Frame authoring (L2, 100% reviewed) | 1.5k frames @ 40–60/day ≈ **4–6 wks** | +500 frames ≈ +1–2 wks |
| Batch generation + prompt design (L1) | 1–2 wks to build/iterate pipeline; compute hours | reuse pipeline |
| Automated gates/tooling | 2–3 dev-weeks | reuse |
| Sampled review (10–20% of 20k = 2–4k lines) | **3–5 wks** reviewer | 8–12 wks |
| Match-phrase generation/review (L3) | 1–2 wks | +2 wks |
| pt-BR native authoring/review | — | **8–12 wks** (dominant cost) |
| **Elapsed with 2 writers + 1 reviewer** | **~8–10 wks** | **~16–22 wks** |

The "big" plan trades hand-authoring for **frame quality + pipeline quality + sampled review**. It is faster and
scales, but it is *not* free: the reviewer and the validator are the quality moat.

## 7. Risks specific to a big corpus

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Generated slop / voice drift** — lines sound like generic chatbot prose | context-constrained prompts, banned-phrase lint, blind-reader rubric, stratified sampling, frame-first authoring |
| 2 | **Lore hallucination at scale** — generated lines invent facts | fact-slot-only policy; every species/level/move claim must resolve `loreRefs`; validator rejects fact lines without refs |
| 3 | **Review sampling misses a bad bucket** | stratified sampling + per-bucket coverage report; ship only if sample pass rate ≥98% per bucket |
| 4 | **License contamination** — a draft paraphrases dataset text | originality gate vs reference corpora; no verbatim mining; attribution file for pattern sources (SODA/OASST1) |
| 5 | **Size/memory blow-up** | hard budgets above; sharding >50k; measure on the recorded device before expanding |
| 6 | **Perceived repetitiveness despite volume** — frames repeat visibly | frame-level no-repeat + slot variation + recency memory; repeat-rate metric |
| 7 | **Safety at scale** — bigger output surface, more chance of a bad line | 100% review of safety classes and boundary lines; red-team suite; fail-closed pack validation |
| 8 | **Content pipeline becomes a second product** | treat generator/validator/review tooling as first-class scope (phases P1/P4), versioned packs, CI gates |
| 9 | **"Feels free" false progress** — megabytes shipped but no-match unchanged | freedom metrics in §2 are the acceptance gate, not corpus size |

## 8. Recommendation

1. **Adopt the big-corpus architecture** (L0–L5) instead of "ship a public dataset": it is the only path that is
   legally shippable, Digimon-contextualized, offline and reviewable.
2. **Ship Big v1 = 20k lines / 20k phrases / ~2k frames** (plus generated species voice for 1,662 species) as the
   launch corpus; keep the 800–1.5k curated core as the quality spine.
3. **Do not bundle DailyDialog/Cornell/EmpatheticDialogues/Ubuntu/OpenSubtitles text.** Use SODA/OASST1 only for
   pattern mining with attribution.
4. **Gate on coverage, not size**: no-match ≤10–15%, top-1 accept ≥85–90%, repeat ≤10% weekly, blind-reader ≥90%
   in-world — measured on the frozen golden set + playtests.
5. **Re-measure APK/heap/latency on the A34** before expanding to 50k; use sharded packs/Play Asset Delivery only
   if the 14 MB headroom is exceeded.
