# DigiLine implementation data

Plain UTF-8 JSON data files for the new DigiLine feature. Add future JSONs here so the
implementation has one data folder.

> **Implementation plan:** [`../DIGILINE_CHAT_SYSTEM_PLAN.md`](../DIGILINE_CHAT_SYSTEM_PLAN.md)
> — how these files become deterministic DigiLine content (owned questions, quiz, personality
> shift, wild negotiation) and how the LLM chat is removed (debug-only toggle).
> These exports are **build-time references, not runtime assets**.

## Files

| File | Dataset | Contents |
|---|---|---|
| `digimon_chat.json` | Time Stranger dialogue | Metadata + all **5,630** rows of the DCO mod's `digimon_chat.mbe` in sheet order (`id`, `speaker`, `text`). |
| `digimon_chat_conversations.json` | Time Stranger dialogue | The same 5,630 lines grouped into the **335** chat sets they belong to. |
| `quiz_time_digimon_questions.json` | Cyber Sleuth quiz | **94** Digimon-only "Quiz time!" questions (54 Cyber Sleuth + 40 Hacker's Memory) with A/B/C choices and correct answer. |
| `demon_negotiation_dialogue_smt5.json` | SMT V negotiation | **15 tones / 215 topics / 1,126 steps** of demon negotiation dialogue — the reference model for the wild-Digimon talk system. |
| `extract_digimon_chat.py` | tool | Rebuilds the two dialogue JSONs from the `.mbe` (edit `SRC` at the top). |
| `build_quiz_json.py` | tool | Rebuilds `quiz_time_digimon_questions.json` from the transcribed guide tables. |
| `download_negotiation_wikitext.py`, `build_negotiation_json.py` | tool | Re-download the SMT V wiki sections into `_src/` and rebuild `demon_negotiation_dialogue_smt5.json`. |
| `validate_export.py`, `validate_quiz.py`, `validate_negotiation.py` | tool | Validation / sanity checks for the JSONs. |

---

# Dataset 1 — Time Stranger dialogue (`digimon_chat.mbe`)

Source: `Be Nice To Your Mon - DCO English Version` mod (Digimon Chat Overhaul / DCO v1.5).
File: `dsts-loader\patch_text01\message\digimon_chat.mbe` (1,041,200 bytes)
Sheet name embedded in the file: **`Sheet1 - DCO-Official`**

## Understood binary format (reverse engineered)

```
"EXPA"                        magic
u32   1                       container version
u32   24                      sheet-name length (padded to 4)
char[] "Sheet1 - DCO-Official\0\0\0"
u32   4                       column slots
u32   8                       bytes per cell descriptor
u32   7, 7, 7                 unknown/reserved
u32   32                      row stride (4 columns x 8 bytes)
u32   5630                    row count
[ 5630 x 32 bytes ]           cell-descriptor table — all zero in this file
"CHNK"                        string chunk
u32   13762                   number of strings
repeat 13762:
    u32 tag                   cell ordinal: (tag - 64) / 8
    u32 byteLen               padded to 4, NUL-terminated UTF-8
    char[] text
```

A cell ordinal maps back to the grid with `row = ordinal / 4`, `column = ordinal % 4`.
The descriptor table is zeroed here, but each string's `tag` still carries the
original cell position, so the sheet reconstructs exactly: every one of the
5,630 rows has a column 0 (id), 2,507 rows also have column 1 (speaker) and
5,625 rows have column 2 (text). Column 3 is never populated in this file.

## Data shape

- `id` — internal dialogue key, e.g. `aigio_001_2_reaction_char_AEGIOMON`, `common005_1_reaction_old_love`.
- `speaker` — `char_*` asset id, e.g. `char_AEGIOMON`, `char_PLAYER_M`; empty when the game uses the current partner/narrator.
- `text` — dialogue line. `\n` marks the in-game line break. Placeholders stay verbatim, e.g. `{fc9{d0}}` = a name, `{d1}` = a personality.
- Stats: 5,630 unique ids, 5,625 non-empty texts, 3,269 unique texts, 508,443 characters total.
  Five rows are intentionally textless: `common010_2_reaction_old_love`, `common013_2_reaction_female_knowledge`,
  `common013_2_reaction_old_knowledge`, `common016_1_reaction_female_courage`, `common016_1_reaction_old_courage`.

### Conversation kinds (`digimon_chat_conversations.json`)

| kind | count | lines each | meaning |
|---|---|---|---|
| `digimon_chat` | 268 | 9 | per-Digimon chat: 1 greeting + 4 player replies (`_N_replay`) + 4 reactions (`_N_reaction_char_*`) |
| `common_template` | 64 | 50 | shared templates: 16 variant openings (child/male/female/old × courage/love/friendship/knowledge) + 2 replays + 32 variant reactions |
| `system` | 3 | 1, 1, 16 | `char_DIGIVICE` personality-change messages + personality-skill lines (`digimon_chat_ps_*`) |

## Generic vs. Digimon-specific dialogue

The file contains **both** kinds, and the generic pool is actually the larger one:

| Category | Rows | Share | What it is |
|---|---|---|---|
| Generic / shared (`common001`–`common064`) | 3,200 | 56.8% | 64 template conversations reused by any Digimon |
| Digimon-specific (`<mon>_001`) | 2,412 | 42.8% | 268 individual chats, 9 lines each |
| System (`digimon_chat_ps_*`, `digimon_chat_personality_change_*`) | 18 | 0.3% | personality-skill lines + `char_DIGIVICE` notices |

**Generic templates** are one conversation each (opening → player reply → reaction), with the
opening and reactions branched by the partner's current **personality**
(`courage`, `love`, `friendship`, `knowledge` = 4 versions of each line). Every line also has
four "age" slots (`child`, `male`, `female`, `old`) that almost always carry the *same* text —
only 5 groups (in `common009/010/013/016/029`) actually differ, and 5 cells are blank
(`common010_2_reaction_old_love`, `common013_2_reaction_female_knowledge`,
`common013_2_reaction_old_knowledge`, `common016_1_reaction_female_courage`,
`common016_1_reaction_old_courage`). Net unique generic lines: 876. They never name a
specific Digimon; they talk about "our team members"/"Agent" generically.

**Digimon-specific chats** = 1 opener + 4 player reply options (`_N_replay`) + 4 reactions,
written for that species. 2,387 of the 2,412 lines are unique; only 20 short lines (e.g.
`"Not at all."`) repeat across species. 260 distinct `char_*` speakers are named (some forms
share a character, e.g. the AEGIOCHUSMON colours).

---

# Dataset 2 — Cyber Sleuth "Quiz time!" Digimon questions

Source guide: **GameFAQs — DigiLine Quiz Guide (PC) by GStelar04**, v1.1 (2023-10-30)
- Cyber Sleuth: <https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/faqs/78530/cyber-sleuth-quiz-answers>
- Hacker's Memory: <https://gamefaqs.gamespot.com/pc/268807-digimon-story-cyber-sleuth-complete-edition/faqs/78530/hackers-memory-quiz-answers>

The guide splits each game's quiz into Real Life / Digimon / Game questions.
`quiz_time_digimon_questions.json` keeps **only the Digimon questions**:

| Game | Questions |
|---|---|
| Cyber Sleuth | 54 |
| Hacker's Memory | 40 |
| **Total** | **94** |

### Schema

```json
{
  "id": "cs_01",
  "game": "Cyber Sleuth",
  "number": 1,
  "question": "Quiz time! Omnimon ____ can Digivolve from BlackWarGreymon. What goes in ____?",
  "answers": ["Black!", "Zwart!", "Sloppy Mode!"],
  "correctLetter": "B",
  "correctIndex": 1,
  "correctAnswerText": "Zwart!"
}
```

- `id` — `cs_01`…`cs_54` (Cyber Sleuth), `hm_01`…`hm_40` (Hacker's Memory).
- `number` — order in the game's code (the guide's first text variation).
- `question` — as shown in game; the guide's `<br>` line wraps are joined into one line.
- `answers` — the 3 choices in A/B/C order (no letter prefixes).
- `correctLetter` / `correctIndex` — correct choice, as letter and 0-based index.
- `correctAnswerText` — the guide's "Real Answer" text (occasionally includes an accent the
  choice text lacks, e.g. CS Q05 `Claíomh Solais.`).

### Notes

- Text is the first variation ripped from the game files; alternate in-game wordings are not covered.
- Source typos are preserved as-is (they come from the game): e.g. `Brakedramon`, `ore Digimon`,
  `made from the body a cursed Cherrymon`, `Turuiemon great leaping power`, and HM Q25 ending in `.`.
- CS Digimon Q39 had a stray `*1*` note marker in the guide; it was removed from the question text.
- Question text is Bandai Namco's; this file is a data compilation for modding, not a text dump of the guide.

---

# Dataset 3 — SMT V demon negotiation dialogue (wild-Digimon talk basis)

Source: **Megami Tensei Wiki — "Negotiation Dialogue in Shin Megami Tensei V"**
<https://megamitensei.fandom.com/wiki/Negotiation_Dialogue_in_Shin_Megami_Tensei_V>

Why this file: SMT V's negotiation flow is a **tone (personality) → topic → demon prompt →
player options → demon replies → outcome** tree, which is exactly the shape a wild-Digimon
talk system needs. Tones map to Digimon personalities, topics map to talk moves
(greet/threaten/bribe/joke/quiz/leave…), and outcomes map to talk results
(bargain, fight, leave, instant recruit, stat check).

Stats: **15 tones, 215 topics, 1,126 steps, 837 demon lines (60,382 characters).**
Raw wikitext is kept in `_src/` for reproducibility.

| Voice | Tones (topics) |
|---|---|
| Male | Monster (18), Tough (22), Beast (17), Cute (14), Brute (9), Old Man (12), Rookie (23), Noble (14), Pompous (7), Wise (12), Jack Bros (13) |
| Female | Cute (24), Peppy (17), Fatale (10) |
| Unique | Tutorial Pixie (3) |

### Schema

```
tones[]  { id, tone, voice, sourceSection, topicCount, topics[] }
topics[] { id, name, steps[] }
steps[]  flat, ordered dialogue nodes:
  { id, kind, parent, demon, choice, random, options[], outcome, marker,
    condition, branches[], optionIndex, alternativeOf }
```

- `kind`: `prompt` (demon line that offers player options) · `choice` (resolution of one
  chosen option; may itself offer follow-up options) · `branch` (conditional result such as
  GOOD/BAD OUTCOME, USER STRENGTH IS HIGHER, Pass/Fail).
- `parent`: id of the step this one follows from. Every topic has exactly one root step.
- `options[]`: `{ text, random, stepIds[] }`. `stepIds` point at the `choice` steps that
  resolve that option (more than one = random alternatives).
- `outcome`: normalized terminal result, with `raw` preserved:
  `enemy_phase`, `bargain_phase`, `leaves`, `instant_recruit`, `gives_item`, `gives_macca`,
  `receives_item`, `talk_end`, `dialogue_switch` (+`to` = target topic), `stat_check`
  (+`stat`), `random`, `next_section`, `repeat_conversation`, `switch_to_join_conversation`.
- `branches[]`: for `stat_check`/`random` steps: `{ condition, stepId }`.
- `marker`: `GUESS RANDOM MONSTER` (quiz prompt) or `moon_phase` (synthetic root, see below).
- `alternativeOf`: several random responses for the same option reference each other.

Minimal traversal:

```text
node = topic.steps[0]
while node is prompt:
    option = pick(node.options)
    node = steps[option.stepIds[0]]        # one of the random alternatives
    if node got options -> treat as a new prompt
    elif node.outcome     -> apply outcome (bargain / fight / leave / recruit)
```

### Caveats (source data quality)

- The wiki article is marked **under construction**. 47 option entries have no resolution row
  in the source, so their `stepIds` are empty (count recorded in `meta.counts.unresolvedOptions`).
  Empty demon replies likewise exist where the wiki left cells blank.
- 8 "moon phase" topics (Full Moon, New Moon, Waning/Waxing gibbous) contain only
  `[GOOD OUTCOME]` / `[BAD OUTCOME]` rows; they get a synthetic `prompt` root marked
  `moon_phase` so the steps stay linked.
- Two topic headers (`Quiz` in Tough, `Bow` in Rookie) lost their `align="center"` markup;
  they are still detected as topics (215 topics = 215 headers in the source).
- Text is © ATLUS / SEGA; the wiki text is CC BY-SA. This file is a data compilation used as
  a structural/content basis for modding.
