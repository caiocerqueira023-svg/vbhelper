# Free Talk ("Papo") content pipeline â€” workstream A

Deterministic, stdlib-only generator + validator for the Free Talk content pack.
Owns `tools/freechat/**` and `app/src/main/assets/freechat/**` (contract Â§0/Â§1).

## Shipped assets (`app/src/main/assets/freechat/v1/en/`)

| File | Contents |
|---|---|
| `manifest.json` | schemaVersion 1, contentVersion 1, locale `en`, file map (`intents`, `speciesVoice`, `databaseFallback`) |
| `free_chat.json` | **primary answer corpus**: intents (match phrases, keywords, conditions, chips, fallback id), reply variants (tone / personality types / stage tiers / loreRefs), fallback ladder 1â€“4, chips, safety lexicons |
| `species_voice.json` | 1:1 catalog identity atoms for all 1,662 species (speciesKey/name/level/type/moves/tier; no prose) |
| `database_fallback.json` | **last resort only**: filtered, deduped dataset dialogue pairs (source + license per pair) used only when generated content has no answer |

Answer priority (Lead/Kotlin side): matched generated intent/reply â†’ matched intent's authored
fallback â†’ **licensed database pair when it genuinely matches** â†’ **authored repair line** for input
that matches nothing â†’ composed species-voice line / authored deflection (only when the pack has no
database layer) â†’ internal constant. Random dataset quotes are never used (owner playtest 2026-11-18:
they read as nonsense); the database only answers with real lexical evidence. The generator never mixes
the two lanes.

## Pipeline

```bat
python tools/freechat/build_freechat.py        :: build all four assets
python tools/freechat/validate_freechat.py     :: hard gates; exit 0 required
```

Bundled Python used for this workspace:

```
C:\Users\julye\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe
```

### Authored banks (`tools/freechat/data/`)

| Bank | Purpose |
|---|---|
| `topics_*.json` | per-topic tone frame banks (2 frames Ã— 8 tones), stage frames (tiers 1â€“4), chip pool |
| `intents_*.json` | 3 authored openers + 6â€“10 match phrases per intent, conditions/flags, fallback id |
| `grounding.json` | catalog fact-atom frames (`{name}`, `{level}`, `{type}`, `{move}`) rendered per species |
| `chips_*.json` | authored suggestion chips (text is always a match phrase of the target intent) |
| `fallbacks.json` | authored fallback ladder lines (level 1 species voice â†’ level 4 generic confusion) |
| `safety.json` | S1/S2/S3/PII lexicons, normalized, EN + basic PT (pt-BR) |

Replies are composed as `authored opener + topic tone-frame` (deterministic), so one intent yields
several tones and every topic exposes stage-tier variants. "Deep" topics (greeting, bond, comfort,
identity, training, battle, evolution, food/bits, out-of-world, rest) render all 8 tones with both
authored frames; other topics render the 4-tone skeleton. Personality-dependent tone: each reply
`tone` maps to a personality-type group (`types`), so the runtime prefers a matching register.

### Shipped corpus (default `--scale 1`)

- 217 intents across 35 topics
- 5,196 generated replies (+40 fallback-ladder lines = 5,236 reply records)
- 1,797 match phrases
- 78 chips; 1,662 species voices; 8 tones; 16 personality types; stage tiers 1â€“4
- 3,553 database fallback pairs (Cornell / OASST1-en / SODA, filtered for PII, sexual, abusive,
  screenplay junk, chatbot/meta phrasing and monologues)
- every generated reply `authoringStatus: "draft"`, â‰¤120 chars, 1â€“2 sentences

### Flags

| Flag | Meaning |
|---|---|
| `--scale {1,2}` | `1` = shipped Big-lite profile (deep topics 8 tones Ã— 2 frames); `2` = every intent rendered deep (Big v2 draft scale) |
| `--limit N` | smoke run: only the first N ordered intents (never ship this pack) |
| `--database-seeds <dir-or-jsonl>` | owner-provided public-dialogue samples for `database_fallback.json` (default `tools/freechat/_seeds/`, git-ignored) |
| `--seed-pairs <jsonl>` | offline mining report only (`seed_mining_report.json`); assets are byte-identical with or without it |

## Determinism

No randomness, no clock, no timestamps, no locale-dependent sorting. Every variant choice is an
authored-order index; JSON is written with `sort_keys=True`, stable list orders, UTF-8 and `\n`.
Two runs over the same inputs produce byte-identical assets (verified via sha256).

## Validation (`validate_freechat.py`)

Hard gates: manifest/file layout incl. `databaseFallback`; schema and key sets; unique intent /
reply / chip ids; non-empty replies (â‰¥2 per intent); duplicate reply texts; duplicate match phrases;
reply length â‰¤160 chars (authoring target 120; all shipped â‰¤120), 1â€“2 sentences; tone required from
the contract vocabulary (â‰¥6, all 8 shipped); `authoringStatus == "draft"`; fallback levels 1â€“4 present
and `fallbackId` resolution; loreRefs resolve to catalog species keys; per-topic intent/tone/stage
coverage; personality-type 1â€“16 and stage-tier 1â€“4 coverage; chips (unique ids/texts, â‰¤3 per intent,
refs resolve, retriever consistency); safety classes + contract-mandated EN/PT terms; banned
assistant-phrase / out-of-world / third-party lints; 8-word originality shingles vs catalog profiles,
DigiLine packs and the legacy species-chat asset; species voices 1:1 with the catalog and fact-atom
only; database fallback (â‰¥3,000 pairs, unique, â‰¤300 chars, source+license, PII/sexual/abusive/
assistant-row gates). Warnings never fail the run; output is deterministic.

Smoke packs (fewer than 200 intents) fall back to contract floors (60/1,000/500) and a â‰¥500-pair DB
floor, with an explicit warning.

## Provenance

`seed_provenance.json` records every seed source/license/usage and the personal-build status of the
dataset-dialogue fallback for `DISTRIBUTION_GATE.md`. The generated corpus is original project
writing composed from the authored banks plus catalog fact atoms; the catalog `profile` prose is
never read by the generator.
