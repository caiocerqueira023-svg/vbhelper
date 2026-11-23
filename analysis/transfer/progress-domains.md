# Dual progress domains for transferred Digimon

Status: proposed, waiting for owner confirmation before schema work.
Scope: Digimon World DS / Dawn / Dusk / Lost Evolution (story domain) and Vital Bracelet family
(VB domain). App Storage sits between them and stores both domains.

## Why the current system is wrong

Today there is exactly one app-side progression, and each transfer *converts* between that single
block and the target layout:

- `UserCharacter` holds the VB-ish values (vital points, mood, battles, activity, trophies, age).
- `AppPartnerState` holds training HP/BP/AP, generation and rank.
- `NativeAppProjection` stores the checkpoint of that app state at the last native sync.
- On send, `WorldDsOutboundState` / `Ds2OutboundState` / `LeOutboundState` build a native record from
  that single block (`WorldDsStatConversion.toNative`, `Ds2StatConversion`, `LeStatConversion`).
- On import/materialization, the native record is converted back into the same block
  (`WorldDsStatConversion.toApp`, `Ds2ReceiveConversion`, `LeReceiveConversion`).

So every send/return round-trip rewrites the one and only progression, and VB/story values fight each
other. That is the "ass" behaviour the owner wants gone.

## Target model

Every individual owns up to two canonical progression blocks plus immutable identity metadata.

1. `StoryProgress` — **one block shared by every story-series game.**
   - `level`, `exp` (total, or per-channel normalized), `maxHp/hp`, `maxMp/mp`, `attack`, `defense`,
     `spirit`, `speed`.
   - Optional per-title extras that have no equivalent elsewhere stay out of the shared block and
     live in a per-title sidecar (e.g. Lost Evolution aptitude, DS exp channels if we keep them).
   - `seededAt`, `seedSource` (`VB`, `STORY`, `IMPORT`), `revision`.
   - Writing story game A → reading A back → writing story game B must reproduce B with the same
     story level/stats. Converting story↔story must be identity, not a formula.

2. `VbProgress` — **one block for the bracelet/device side.**
   - Vital points, stage, mood, training HP/BP/AP, trophies, wins/losses, activity, age,
     generation, rank, plus the existing `NativeAppProjection` as its native sync checkpoint.
   - Transfers to/from VB only ever read and write this block.

3. `IndividualProfile` — metadata always replicated on every write regardless of domain:
   canonical species id, nickname/stored name, personality, sex/attribute if the target supports it,
   awards/keys, identity token. Never used as a progression source.

## Rules

| From → To | What is written | Conversion |
|---|---|---|
| Story game A → Storage | update `StoryProgress` from the native record | title layout → shared story block (identity or clamped) |
| Story game A → Story game B | `StoryProgress` | identity write, no formula |
| Storage → Story game (first time) | bootstrap `StoryProgress` once from `VbProgress`, then write | one-time `VB→Story` bootstrap |
| Storage → Story game (later) | current `StoryProgress` | identity write |
| VB → Storage | update `VbProgress` | VB → canonical VB block |
| Storage → VB (first time) | bootstrap `VbProgress` once from `StoryProgress` | one-time `Story→VB` bootstrap |
| Storage → VB (later) | current `VbProgress` | identity write |
| any → anywhere | `IndividualProfile` | copied each time |

Bootstrap happens at most once per domain and is marked (`seededAt/seedSource`), so later gains in
one domain can never overwrite the other. Sending story→story never touches `VbProgress`, and VB
writes never touch `StoryProgress`.

## Bootstrap formulas (owner decisions)

Owner decisions:

1. **Shared story block:** level + core stats (HP/MP/ATK/DEF/SPI/SPD) + one normalized total EXP.
   Lost Evolution aptitude and per-game EXP channels stay per-title sidecars.
2. **Bootstrap conversion:** the old weighted conversion is discarded for seeding. The one-time
   bootstrap is:
   - level: the established VP→level curve (vital points 0..9999 → level 1..99, linear), so Ultimate
     7500 VP becomes level 75 exactly as the owner's example;
   - stats: **the stats the game itself would give that species at that level** — i.e. the native
     creation formula, as if the Digimon were created at that level in-game (cheat-created), using the
     game's species base tables and level-growth formula, not Tamers Lab's old weighted mapping.
   - Story → VB seeding uses the inverse of the same policy: level maps to the VB stage/VP band, and
     the VB block is written as the VB model would create that stage (details in phase 3).
3. **No divergence warning:** progress can only change while the Digimon is inside a save, and the
   transfer flow imports/moves the save back before it can be sent elsewhere, so the stored story
   block is current by construction when a send starts. Import updates `StoryProgress`; send writes it.
   No block/warning state is needed.

Implementation note: the per-title native creation formula is the only new reverse-engineering item —
read the game's own level→stat growth from the ROM/record creation path (DS1 record codec + species
records, DS2/LE species base data and growth curves) and encode it as `NativeCreationStats` per title.
No conversion is used once both domains exist.

## Schema

- New Room table `story_progress` (PK `individualId`): the shared fields above plus
  `seededAt`, `seedSource`, `revision`, `updatedAt`.
- New Room table `vb_progress` (PK `individualId`): canonical VB block; existing `UserCharacter` VB
  columns stay for UI/card compatibility but stop being the transfer source. Migration copies
  `UserCharacter` + `AppPartnerState` into `vb_progress` for every individual.
- New Room table `progress_seed` or columns on `story_progress` / `vb_progress` to record one-time
  seeding; no re-seed ever.
- The existing `NativeAppProjection` becomes the VB native checkpoint and gets a story sibling
  (`StoryNativeProjection`) only for mismatch detection, never as a progression source.

## Code touchpoints

Core:
- `game-save-core/.../WorldDsStatConversion.kt` (toNative/toApp) — reserved for bootstrap only.
- DS2/LE stat conversions — same reservation; remove them from the steady-state send path.

App:
- `transfer/data/gameSave/ExperimentalWorldDsSendRepository.kt` — outbound state builders read
  `StoryProgress`; remove `WorldDsAppStatsReader` from steady-state sends (stats no longer come from
  `UserCharacter`), keep its output only for the first VB→Story seed.
- Receive/materialization path that writes native stats into `UserCharacter` — update
  `StoryProgress` instead; keep `VbProgress` untouched.
- `transfer/data/gameSave/DestinationPreviewRepository.kt` — previews show story progression and
  label a first send as "will seed story progress from VB".
- `game-save-core` editors (`WorldDsPartyEdits`, `Ds2SaveEdits`, `LeSaveEdits`) unchanged: they still
  write the title layout; only the projection source changes.
- VB side: bracelet/DIM export/import services read/write `VbProgress`; first story-born export seeds
  it.

UI:
- Inspector: two labeled groups — **Story progress** (level, stats, EXP/aptitude if present) and
  **VB progress** (stage, vital points, training, battles) with a provenance note
  ("seeded from VB" / "seeded from story").
- Box cells and send preview: level badge comes from `StoryProgress` when present, else the VB-derived
  seed value; a subtle marker shows a domain that hasn't been created yet.
- Native details block keeps per-game native facts, clearly separated from both tracked domains.

## Migration & compatibility

1. Backfill `story_progress` from the current native projection when a trustworthy level/stat set
   exists (story-born Digimon); otherwise leave NULL (means "not yet in a story game").
2. Backfill `vb_progress` from `UserCharacter` + `AppPartnerState`.
3. Existing saves and carried records keep working: send code falls back to the one-time bootstrap
   whenever the target domain block is NULL.
4. No user-visible reset; renames/personality/species always synced.

## Phases

1. **Foundation (done, no behaviour change):** schema + DAOs + `ProgressDomainRepository` + migration +
   unit tests (domain isolation, seeding once, backfill). Implemented as DB v59: `story_progress`,
   `vb_progress`, `ProgressDomainRules` (VP→level curve, clamp, seed-once), `ProgressDomainRepository`,
   `AppDatabase.MIGRATION_58_59`, 6 unit tests. The app was installed over the v58 database and launched
   without a migration error.
2. **Story domain wiring:** outbound/import paths read/write `StoryProgress`; steady-state sends stop
   converting VB↔story; bootstrap only when NULL; tests for story→story identity.
3. **VB domain wiring:** bracelet/DIM and V-Pet paths read/write `VbProgress`; story→VB bootstrap.
4. **UI split:** inspector/box/send provenance; localized copy; DESIGN.md update.
5. **Verification:** unit + save round-trip tests; in-game acceptance for each story title; owner
   device run.

## Owner decisions recorded

- Shared story block: level + core stats + normalized total EXP (per-title extras stay sidecars).
- First-time conversion: VP→level curve for level, game-native created-stat formula for stats;
  old weighted conversion retired from seeding and steady-state transfers.
- No save-divergence warnings: import-then-send is the only path, so story progress is current.
- Remaining product question for phase 3: what VB stats a story-born Digimon should receive on its
  first VB write (stage from level band, VP = inverse VP→level curve, base HP/BP/AP inverse-scaled
  from story stats, or VB defaults for the stage). To be confirmed when VB wiring starts.

## Phases

1. **Foundation (no behaviour change):** schema + DAOs + `ProgressDomainRepository` + migration +
   unit tests (domain isolation, seeding once, backfill).
2. **Story domain wiring:** outbound/import paths read/write `StoryProgress`; steady-state sends stop
   converting VB↔story; bootstrap only when NULL; tests for story→story identity.
3. **VB domain wiring:** bracelet/DIM and V-Pet paths read/write `VbProgress`; story→VB bootstrap.
4. **UI split:** inspector/box/send provenance; localized copy; DESIGN.md update.
5. **Verification:** unit + save round-trip tests; in-game acceptance for each story title; owner
   device run.

## Decision correction (owner, round 6)

**VB progress is not a new domain.** It is the progression VBHelper already keeps — `UserCharacter`
plus `AppPartnerState` and the existing native projection. Only **`story_progress` is new**. The
experimental `vb_progress` table from the first implementation pass was therefore removed (DB v60,
`MIGRATION_59_60` drops it) together with its wiring/tests:

- watch export/import keep reading and writing the existing app fields (VBHelper's historic behaviour);
- story materialization only records `story_progress`; story returns no longer mirror stat deltas into
  the app/VB fields;
- the transfer inspector shows the tracked **Story progress** block next to the native facts; VB values
  continue to live in the app's normal partner screens.

What remains from the original plan: the first-send bootstrap (a Digimon whose VB/app progression
exists but has no story block yet) and the two-domain review in UI wherever the app already shows VB
values. Bootstrap still needs the game-native created-at-level stat formula per title.

## Bootstrap research (round 7)

The Dawn/Dusk level-up formula was recovered from the decompiled ARM9 (`analysis/ds2/ghidra/out-dawn/
all_pseudocode.c`):

- `FUN_02055bd0(record, expChannels)` sums the record's eight EXP channels, asks the EXP curve how many
  levels were reached (`FUN_02055860`), and calls `FUN_020558e8` once per level.
- `FUN_020558e8(record, out)` computes six per-level stat gains from the species growth row selected by
  `record +0x1c`: a 12-byte row with six low/high byte pairs (HP, MP, ATK, DEF, SPI, SPD). Each pair is
  `random(1..lowByte) + highByte` (RNG `FUN_02082090`: `seed = seed*7+5`), scaled by the fixed-point
  constant `DAT_02055b10` (`>> 34`), added to the current stat, then clamped by `DAT_02055b14`
  (HP/MP) and `DAT_02055b18` (other stats).
- `StoryBootstrapPolicy` now owns the level half (7500 VP → level 75) and the per-title
  `NativeCreationStats` provider seam; bootstrap refuses to invent stats until a provider exists.
- Remaining extraction: the growth table pointer/value (`DAT_02055b0c`), `DAT_02055b10`,
  `DAT_02055b14`, `DAT_02055b18`, and the per-species base records/growth index. LE and DS1 need their
  equivalent engine formula recovered the same way.

## Verification (round 8)

`StoryRoundTripTest` now proves the shared story block survives a real record write/read cycle in all
three story layouts (World DS, Dawn/Dusk, Lost Evolution):

- World DS: level, HP/MP/ATK/DEF/SPI/SPD, normalized EXP total and the nickname all round-trip; a
  retained seed keeps `save-local:<id>` identity while the story stats are written.
- Lost Evolution: level/stats/aptitude round-trip; its generated records still zero the EXP counter
  (tracked value retained in the block, not representable in that codec yet).
- Transfer package: 156 tests / 0 failures.

Remaining for the objective: bootstrap providers from the recovered engine formula, DS2/LE EXP writing,
and in-game acceptance.

## Round 9 status

All three story layouts have write/read verification for the shared story block. Dawn/Dusk round trip
covers level/stats/nickname; its EXP counters are still zeroed by the codec. The remaining objective
gaps are the bootstrap providers (engine growth table/constants), EXP writing for DS2/LE, and in-game
acceptance.

## Round 10: Dawn/Dusk bootstrap shipped

Using the owner's ROMs at `C:\Users\julye\Downloads\Caio\ROMs e Saves para RE`, the Dawn/Dusk
creation data was extracted from `analysis/ds2/dawn/arm9.bin`:

- growth table `DAT_02055b0c = 0x0211E298` (file 0x11E298): six 12-byte rows of low/high pairs for
  HP, MP, ATK, DEF, SPI, SPD;
- scale `DAT_02055b10 = 0x66666667` (fixed-point 1/10, `>> 34`);
- clamps `DAT_02055b14 = 9999` (HP/MP), `DAT_02055b18 = 999` (other stats);
- per-level gain = `floor((rand(1..low) + high) / 10)`; the provider uses the deterministic expectation
  (mean of the random range), which is what such a record normally has.

`Ds2CreationStats` implements `NativeCreationStats` from the species base record (`baseHp/MP/ATK/DEF/
SPI/SPD` at `baseLevel`) + expected growth up to the requested level, clamped to the engine caps.
`outbound()` now performs the one-time Dawn/Dusk bootstrap when a story block is missing: level from the
VP curve, stats from `Ds2CreationStats`, saved with `seedSource = VB`, then used for the send.

Tests: `Ds2CreationStatsTest` (base level equals species record, growth inside caps, species max-level
clamp). Transfer package: 160/0 green; debug APK installed.

Remaining: Lost Evolution and World DS created-at-level providers (LE creation code `FUN_020b0100` and
its growth multiplier table `DAT_020cf844` are located in `analysis/dsle/ghidra/out-arm9` for the next
extraction), DS2/LE EXP counters, and the owner's in-game acceptance.

## Round 11: Lost Evolution bootstrap shipped

LE level-up data recovered from the retail ARM9 (`analysis/dsle/decompressed/arm9.bin`):

- growth pairs `DAT_020cf870 = 0x02142258`: per growth row (species `growthBits & 7`) five stat pairs
  (base byte, random range byte) for HP, MP, ATK, DEF, SPI; speed is not grown;
- scale `DAT_020b0d48 = 0x66666667` (fixed-point 1/10);
- clamps `DAT_020b0d4c = 9999` (HP/MP), `DAT_020b0d50 = 999` (combat); speed copied from the species,
  capped at 99.

`LeCreationStats` implements the provider and `outbound()` seeds the LE story block once from app/VB
progress before the first write. Transfer package: 162/0 green; APK installed.

Remaining: World DS created-at-level provider; DS2/LE EXP counters; in-game acceptance.

## Round 20: World DS bootstrap shipped (all three titles covered)

The DS1 created-at-level routine was isolated in the World DS Ghidra export:

- `FUN_020a1c70` creates the record from the species master (`FUN_020a29c4`), reads the level and
  scales every stat by `(level * growth + 100000) / 100000` using the 14-byte growth row selected by
  the species growth index (`dm +0x2A`), table at RAM `0x0210AEC8` (`DAT_020a1f1c`);
- row order: attack, defense, spirit, speed, HP, MP, max-level/aptitude; clamps 999 (combat) and
  9999 (HP/MP), same integer truncation as the engine (`0x14F8B589 >> 45` = 1/100000);
- the species master's base block is at `dm +0x04..+0x14` (HP u32, MP u16, ATK/DEF/SPI/SPD u16,
  extra u16, max level byte), base level at `dm +0x02`, growth index at `dm +0x2A`.

Changes shipped:
- `tools/extract_world_ds_species.py` now extracts the creation block (asset rows extended to 25
  fields) and `app/src/main/assets/world_ds_species.json` was regenerated from the reference USA ROM;
- `WorldDsSpeciesTable` parses/exposes `CreationRow` (base stats, level, growth index);
- `WorldDsCreationStats` implements the DS1 created-at-level formula with the dumped growth rows;
- `outbound()` now performs the one-time World DS story seed for partners that only have app/VB
  progress, like Dawn/Dusk and Lost Evolution.

Tests: `WorldDsCreationStatsTest` (base-level start, growth inside caps, level clamp). Transfer package:
165 / 0 green; APK built and installed.

## Round 21: EXP writing and final state

- Dawn/Dusk record generation now accepts the spread EXP channels and writes all nine counters
  (`Ds2RecordCodec.generate(exp = ...)`); Lost Evolution writes its single EXP word
  (`LostEvolution.buildRecord(exp = ...)`). The send projection passes the tracked story total.
- Round-trip tests assert the EXP total survives World DS, Dawn/Dusk and Lost Evolution.
- Transfer package: 165/0 green; APK installed and smoke-launched.

All planned programme work is implemented: story_progress tracking, story-authoritative sends,
one-time bootstraps for all three story titles (DS1/DS2/LE), EXP carry, metadata (species/nickname/
stored name) preserved on writes, story-only UI group, and round-trip tests. The only remaining
objective item is the owner's in-game acceptance run.

## Round 22: story backfill for pre-tracking partners

Some stored individuals had a species but no `story_progress` row and no usable app-character seed, so
every destination fell back to the "no saved game record" hint. Fix: `outbound()` now backfills the
story block from the individual's own retained native snapshot (`StoryProgressTracking.fromRecord`)
before any bootstrap. That record is the partner's actual story progress, so sends carry its level and
stats instead of being blocked. Species that genuinely do not exist in a title still get the explicit
species blocker. Tests: 169/0; APK installed.

## Round 23: on-device sendability verification

A debug probe (`outbound()` for every missing-story partner, then removed) confirmed the fix on the
owner's real database. Blocked partners with supported species now project records: Devidramon
(Dawn/Dusk + LE), Were Garurumon (Black), Karatenmon, Machinedramon, Falcomon, SkullGreymon, Otamamon
and Gottsumon. Two bugs were found and fixed on the way: retained-snapshot story backfill, and a Lost
Evolution "speed outside the record range" failure caused by unclamped story stats (Dawn/Dusk records
carry speed 0, LE requires 1..99) — all story→title writes now clamp into the destination bands.
Remaining blocked partners are exactly those whose species no supported title contains: Ryudamon,
Sakumon, GulusGammamon and Sunarizamon.

## Round 24: default-target sendability fix

The send screen's landing projection called `outbound(individualId)` with no target. That path assumed
World DS whenever the partner had no source game, so a species absent from World DS (e.g. Devidramon)
returned null and the UI disabled every destination even though Dawn/Dusk and Lost Evolution proved
sendable. `outbound(null target)` now tries World DS → Dawn/Dusk → Lost Evolution and returns the first
projection that succeeds. Verified on-device with a temporary blocker probe (removed afterwards):
Devidramon's Dawn/Dusk and Lost Evolution blockers are null and projections return level 6; World DS
only reports the explicit species blocker.

## Round 25: seed only on successful transfer

Previews (`outbound`), destination blockers and the send screen's landing projection used to persist
the one-time story seed as a side effect, spending the conversion when a partner was merely selected.
Now those paths compute the would-be block in memory (`preview*` helpers) and `send()` persists it via
`persistStorySeedIfMissing()` only after the journal reaches ACKNOWLEDGED (write verified and
committed). Backing out of the confirmation or leaving the screen leaves no story block behind.
