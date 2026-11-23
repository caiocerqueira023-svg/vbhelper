# Testing and verification

This is the current testing guide for Tamers Lab. The old 200 KB chronological
regression log was archived at
[docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md);
it is historical evidence, not the current result. Performance evidence lives in
[docs/performance/BASELINE.md](docs/performance/BASELINE.md); quality-tooling decisions
live in [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md).

## Verify v59: Lost Evolution adapter (2026-11-20, same tree)

User request: extend the multi-game transfer system to Digimon Story: Lost Evolution using the
same ROM-first REA/Ghidra process as Dawn/Dusk, to the same feature state (or better).

Evidence and layout: [game-save-core/LOST_EVOLUTION_RESEARCH.md](game-save-core/LOST_EVOLUTION_RESEARCH.md)
and [LOST_EVOLUTION_TRANSFER_PLAN.md](LOST_EVOLUTION_TRANSFER_PLAN.md). The Japanese `BLEJ` ROM
(Operation Decoded T-En v1.1) was unpacked, its custom overlay compression was reversed and ported
byte-exactly, and the save manager, record creator/merger, roster/party/farm/team containers,
species id/parameter tables and message-pack name list were recovered from the ROM's own code:

- container: two `0x8000` slots, MD5 over the slot with the 16 header bytes zeroed, `DM3_000`
  signature at `+0x10`, u32 sequence at `+0x18`;
- body: serialized player state at `+0x20` (`0x407C` bytes); 40 x `0x78` records at `0x1850`;
- record: u64 identity at `+0x00` (low32 unique id, high32 marker `0x030405F0`), species `+0x0C`,
  18-byte name `+0x0E`, level `+0x3C`, EXP `+0x40`, stat words `+0x28..+0x34`, speed `+0x3D`;
- party: 8 x u64 identities at `0x3E8`; farm: up to four groups at `0x11F4` (stride `0xD0`,
  8 residents at `+0x18`); team: 8 x u64 at `0x159C`; next identity: u64 at `0x4090`.

Real-save validation (user's own `.sav`, newest copy):
`77:Ａｎｄｒｅ:lv4:PARTY:168; 82:Gabumon:lv2:FARM:134; 90:ToyAgumon:lv1:FARM:144;
80:Dorumon:lv2:PARTY:141; 101:Falcomon:lv2:PARTY:136` — party/farm membership matches the identity
references found by byte search, and every level/stat matches the species parameter blocks scaled
by the engine's growth code (independent cross-validation, not self-consistency).

Farm residents are part of the transfer scope for this layout: the import profile accepts
`SaveContainer.FARM`, and the native move policy admits farm records only where the registered
profile sets `farmRecordsMovable` (Lost Evolution; the editor clears the farm reference).

Implemented: core `LostEvolution.kt` (profile, name codec, stage bands, record builder) and
`LostEvolutionSaveEdits.kt` (`LostEvolutionSaveReader` + remove / insert-retained / insert-generated
/ replace / admit / byte-exact restore, authority-copy only, sequence bump, MD5 reseal, readback);
app `LeSpeciesTable` (ROM-generated `le_species.json`: 314 real Digimon ids with base stats and
skills, stage bands, 51 documented localization aliases), `LeStatConversion` (`le-stat-v1`),
`LeOutboundConversion`, import profile (party + farm residents), materialization and move/send
branches, catalog/capabilities wiring.

Tests: `LostEvolutionSaveTest` (10 core cases, including the real save behind `LE_SAVE_DIR`),
`LeTransferConversionTest` (7), updated catalog/capability/import-profile tests. Full suites on
this tree: **core 232 tests / app 1333 tests, 0 failures** (env-gated real-save and device tests
skipped when unset).

Follow-up after the first device send attempt ("could not update this source"): logcat showed
`IllegalStateException: Check failed.` at the send journal revision check, and the device journal
showed `original.sav` differing from the revision recorded in `operation.json`. Cause: the Lost
Evolution identity allocator advanced the next-id counter on the caller's source bytes instead of
the candidate, so the journal backup hash never matched (no write happened, source unchanged). The
allocator now runs on the candidate, `Edit` asserts an edit never mutates its source, and
`LostEvolutionSaveTest.planningAnEditNeverMutatesTheSourceBytes` covers insert/remove/admit, and
`realSaveReplaysTheFirstDeviceSendDeterministicallyWhenAvailable` replayed the exact failed send on
the real save with the failed operation's own synthesized record, reproducing the device candidate
byte for byte while leaving the source untouched (env-gated by `LE_SAVE_DIR`/`LE_REPLAY_*`). The DS
launcher icon list now includes Lost Evolution (`BLEJ`/`BLE`).

Second device report: after a send, the game did not show the Digimon and the app list lost it
after the in-game save. Pulling the phone save proved the game session had never loaded the write:
the inserted identity, its party slot and the advanced next-id counter were all absent, and the file
held only the original five records with the old counter while the slot sequences advanced by normal
in-game saves. The game was already running with the pre-write state (the user suspected exactly
this), so saving overwrote the file. Follow-up hardening: the send confirmation now instructs the
player to close the game completely before reopening it, and linked-save refresh releases ("returns
to Storage") any individual whose identity is no longer present in the destination save, instead of
leaving it stuck as away. Covered by `PresenceReconciliationTest`.

Third device report: the game still did not show a send and the app released it after the in-game
save. Decompressing DraStic's in-memory session state proved the game had never read the file: RAM
held only the original roster and the pre-write next-id counter. This confirmed the requirement that
the game must be closed when VBHelper writes and be (re)started afterwards to read the save. UX for
that rule is now emulator-agnostic and two-step with no savestate/file instructions (close the game
first, open it after sending). The send flow and the automatic return-to-Storage recovery are the
product-level answers; no emulator internals are touched.

Third round of device evidence (10:24): the Dorumon move had actually written its save (the file
on the phone equals the recorded candidate), but the post-write verification compared the record
against both card copies; the untouched older copy still contained it, so the move was flagged
recovery-required. On top of that the stale "recovery required" state blocked later moves from the
same save - the user's "can't bring Digimon to Storage". Fix: scope the removal check to the edited
copy, log the exact recovery cause, and never let return materialization failures fail a completed
move. Also, because Lost Evolution's party is story-gated (the real save is capped at three
members), sends now place the individual in the farm/Digimon list rather than a fourth party slot.
The user's emulator RAM dump (decompressed DraStic state) still showed the pre-write roster, so the
game session had not loaded any write; the reload requirement remains the boundary.

Fourth device report (10:51-10:55): a move to Storage appeared duplicated (game kept the Digimon
because its paused session still held it, the app imported it), some moves were blocked by the
earlier recovery state, and a later save was not recognized by the game. Inspection of the current
file: the app-edited copy (seq 19) had records only in slots 1, 2 and 5 - a hole in front of the
game-written backup copy (seq 14). The genuine game-written save from the pre-test backup was
restored byte-for-byte (`le.sav`, sha256 ad22ef13...), and the damaged file was preserved. Lost
Evolution native writes are now gated off in the app until the roster-packing rule and header
bookkeeping fields are recovered and verified; reads/imports remain enabled. This is a deliberate
safety stop, not a shipped transfer.

Fifth iteration: genuine saves always carry identical bodies in both card slots (only the MD5
header and the consecutive write sequences differ). The single-copy writer was therefore writing a
file whose slots disagreed - the strongest remaining explanation for the game ignoring/rejecting
app-written saves. The Lost Evolution editor now commits a complete body to both slots with
consecutive sequences and fresh MD5 headers exactly like the game's own writer, mirrors the game's
delete routine for removal (party zero, farm-resident compaction, second farm list compaction, team
zero, partner protected), and keeps native move-to-Storage disabled while insert-only sends are
enabled again. This build is the current in-game verification candidate.

Sixth iteration: the engine's add/delete paths are now fully identified. Party insert
`FUN_020afce8` allows six members (three active slots searched in 0..4, then three reserve slots in
5..7); farm insert `FUN_020ea928` fills the first empty resident of one group; a third assignment
container at player +0x157C has add `FUN_020eb3e0` and remove `FUN_020eb41c`; the delete pipeline is
`FUN_020ae0ac`. The overlay creation caller `FUN_0223d3bc` (via `FUN_020adf08`) also shows the story
partner record gets parent slots at +0x5C..+0x66 and flags bit 1. Editor now mirrors the party rule
and the tail-slot initialization; sends remain insert-only for in-game verification.

Seventh iteration: byte comparison of game-written vs app-written records found the concrete
format violation. Every game-created record has `+0x08 == high32(identity)` (a marker copy) and six
`0xFFFF` tail slots at `+0x5C`; the app's synthesized record had `+0x08 = 0` and an all-zero tail.
The loader itself only checks header tag/MD5/sequence (verified from the state machine), which is why
detection seemed fine, but the inserted record was unlike anything the engine produces. `buildRecord`
and the editor now stamp the marker window and tail; inserting a record from an old app snapshot
repairs both windows. The phone's original game-written save was restored and old recovery journals
were pruned so the stale operations cannot block new sends. In-game acceptance of the corrected write
is the next verification step.

Eighth iteration - the actual save-integrity mechanism was found. The engine writers call
`FUN_020ace5c`, which sets player-state `+0x4078` to `0xFFFFFFFF` and stores
`FUN_020ad328(player, 0x7FE0)` - an XOR of every u32 in the serialized player state - so any valid
save XORs to `0xFFFFFFFF` over the `0x7FE0`-byte body of each slot. Both genuine saves (PC file and
the phone backup) satisfy this in both card copies; every save the app wrote did not, which explains
why the game read/validated the file header and then refused the loaded state. The editor now
recomputes the checksum for every edit and mirrors the corrected body to both copies. Next step is
one direct-load test of a freshly written save.

Ninth iteration - **in-game acceptance confirmed**. After recomputing the player-state XOR
checksum (see the eighth iteration), the user sent a Digimon with the app and the game loaded the
written save normally. That closes the main boundary: the writer now produces saves the retail game
accepts. Move-to-Storage is re-enabled for Lost Evolution; remaining known gap is cosmetic - a new
record does not add its species to the encyclopedia book container yet.

Tenth iteration: per-send destination selection. Lost Evolution placement follows the engine add
rules for party (FUN_020afce8), farm (FUN_020ea928) and bank (FUN_020eb3e0); World DS supports
party and bank (bank = no party reference); Dawn/Dusk supports party and bank because the save
layout has no serialized farm table. World DS farm placement is deliberately refused until the farm
group header semantics are recovered. Destination is persisted in the send journal and replayed by
recovery. Suites: 237 core / 1334 app green.

Eleventh iteration: the Lost Evolution encyclopedia book was recovered (player-state +0x460, one u64
per species, owned pattern 0x4000000100000000 written by the creation path). Sends now register the
species in both card copies, preserving existing bits and the checksum. No equivalent per-species
book exists in the World DS/Dawn-Dusk serialized layouts, so no change was needed there.

Twelfth iteration: the Dawn/Dusk duplication was a copy-coverage bug - move-out only edited the newest
card copy and the retail loader used the other one. DS2 edits (removal, insertion, replacement,
admission) now apply to every populated copy with per-copy sequence/MD5 reseal; tests were updated to
the new both-copies contract. The affected Dawn save was repaired in place. World DS already edited
every populated copy and Lost Evolution mirrors both copies, so this class of bug is now closed for
all three layouts.

Thirteenth iteration: the Transfer screens were redesigned (library shelves -> opened box ->
inspector sheet) against the owner's PKForge and PKVault references. A two-column game shelf shows
launcher art and a party/farm/bank census; the opened game is a save identity bar, scope tabs with
counts and a three-column sprite-first box with quiet empty slots; tapping a Digimon opens a full
inspector sheet with native facts, a collapsed native-details block and one primary Move action;
sends reuse the plates for destinations and Party/Bank/Farm scope chips. Evidence: device captures
`analysis/dsle/device/final-library.png`, `final-box.png`, `final-sheet.png`; build and test suites
green. Remaining boundary: the send screen and the per-save chooser were only smoke-checked through
the same components, not captured in this pass; no formal finish-reviewer agent run was recorded.

Fourteenth iteration: Transfer navigation was rethought after owner feedback. The tab row was
removed; the library is the home with games as plates with Storage, bracelet and V-Pet continuing the same shelf as full-width rows
(no sub-sections), and the header wrench key opens a dedicated Tools page (grouped
Saves / Protection / Appearance, explicit descriptions, live counts) instead of the overflow menu.
Backups and launcher icons pages use the long-row grammar too. Device and V-Pet pages share the save
identity bar. Device captures: `analysis/dsle/device/v3-library.png`, `v3-tools.png`. Build green;
app suite last run green before this navigation pass (UI-only change compiled afterwards).

Fifteenth iteration: universal send projection with explicit species reasons. Stats fall back to a
minimal valid reading when an individual has no app character representation, and the species falls
back to the retained record's native species name. No species substitution is performed: a species the
target title does not contain returns SPECIES_NOT_IN_GAME, an individual without a canonical species
returns SPECIES_UNKNOWN, and both are surfaced as localized reasons in the destination chooser and the
send header. Transfer tests: 1341 / 0 failures.

Sixteenth iteration: progress-domain foundation. DB v59 adds the story and VB progress tables, the
seed-provenance fields and `ProgressDomainRules`; nothing reads or writes them from transfers yet, so
existing behaviour is unchanged. Evidence: app installed over the v58 database and launched into the
home screen (screenshot `analysis/dsle/device/v12-progress-foundation.png`), `ProgressDomainRulesTest`
6/6 green. Next: story-domain wiring (send/import read and write the shared story block; bootstrap only
on first seed).

Seventeenth iteration: story-domain tracking. Native materialization now records the shared story
progress block from every World DS / Dawn / Dusk / Lost Evolution snapshot (first materialization and
each return), in native units; the VB block is untouched. Unit tests: `StoryProgressTrackingTest` 4/4
plus `ProgressDomainRulesTest` 6/6 green (10/10 total), build installed and the app smoke-launched.
Send still uses the old conversion; story-authoritative outbound generation and the first-send
bootstrap remain for the next pass.

Eighteenth iteration: story-authoritative sends. The World DS / Dawn-Dusk / Lost Evolution outbound
conversions accept explicit native stats, `StoryProgressTracking` maps the story block into each title,
and the send projection prefers the tracked story block over retained/app conversion whenever one
exists. New `StoryAuthoritativeSendTest` verifies DS1 and LE write the tracked level/stats/aptitude
exactly. Transfer test package: 151/0 green; build installed and smoke-launched. Limits: DS2/LE EXP
counters are still zeroed by their codecs, and the first-send bootstrap (no story block yet) remains.

Nineteenth iteration: VB seed-once and story/VB separation. First story materialization seeds
`VbProgressDomain` once (story-origin provenance); later story returns stop mirroring into the VB block
and update only the tracked story block. New `ProgressDomainRepositoryTest` (3 tests) covers seed
provenance preservation, clamping and domain independence. Transfer test package: 154/0 green; build
installed and smoke-launched. Remaining: VB transfer read/write paths, the first-send bootstrap
(native created-at-level stats research), DS2/LE EXP writing, and the two-domain UI.

Twentieth iteration: VB domain on the bracelet path. `ToNfcConverter` reads the VB block for stage/
vitals/mood/age/trophies/battles/activity on export; `FromNfcConverter` records watch imports into the
VB block through the new pure `VbProgressTracking` mapper (clamping, provenance preservation, revision
advance), covered by 3 unit tests. Transfer package: 157/0 green; build installed and smoke-launched.
Still open: first-send VB→story bootstrap (native created-at-level stats research), DS2/LE EXP writing,
BE export, and the two-domain UI.

Twenty-first iteration: progress model corrected. VB progress stays as the app's existing progression;
`story_progress` is the only new block. The experimental `vb_progress` table, its watch export/import
wiring and its tests were removed; DB v60 drops the table, and the app was installed over v59 and
launched cleanly. The transfer inspector now shows the tracked Story-progress group (level, stats, EXP,
aptitude, provenance) for archives whose individual id is known. Transfer package: 151/0 green.
Remaining: first-send VB/app→story bootstrap (game-native created-at-level stats), DS2/LE EXP writing,
and in-game acceptance.

Twenty-second iteration: bootstrap research. The Dawn/Dusk level-up/stat-growth formula was recovered
from the decompiled ARM9 and documented, and `StoryBootstrapPolicy`/`NativeCreationStats` introduced the
level seam + per-title stat provider (2 unit tests). Transfer package: 153/0 green; build assembles.
Bootstrap is not yet wired to sends: the growth table/constants and per-species base records still need
extraction, and LE/DS1 need their equivalent formula.

Twenty-third iteration: story round-trip evidence. `StoryRoundTripTest` writes the tracked story block
into World DS and Lost Evolution records and decodes it back, asserting level/stats/EXP/aptitude and
nickname survive, plus retained-identity preservation when a seed is used. Transfer package: 156/0
green. Still open: bootstrap providers (engine growth table/constants), DS2/LE EXP counters, BE path,
and in-game acceptance.

Twenty-fourth iteration: all three story layouts now have round-trip coverage. `StoryRoundTripTest`
writes/reads the tracked story block through World DS, Dawn/Dusk and Lost Evolution records and checks
level/stats/nickname (plus LE aptitude and retained identity). Transfer package: 157/0 green. Remaining
for the objective: bootstrap providers from the recovered engine formula, DS2/LE EXP counters, and
in-game acceptance on the owner's device.

Twenty-fifth iteration: Dawn/Dusk bootstrap. Extracted the retail ARM9 growth table (0x0211E298), 1/10
scale and clamps, implemented `Ds2CreationStats`, and wired the one-time Dawn/Dusk story seed into
`outbound()`. New provider tests: base level equals the species record, growth stays inside engine caps,
species level cap clamps. Transfer package: 160/0 green; APK installed. Remaining: LE/World DS providers,
DS2/LE EXP counters, in-game acceptance.

Twenty-sixth iteration: Lost Evolution bootstrap. Recovered LE growth pairs/clamps/scale from
`analysis/dsle/decompressed/arm9.bin`, added `LeCreationStats` with 2 unit tests, and wired the one-time
LE story seed into `outbound()`. Transfer package: 162/0 green; APK installed. Remaining: World DS
created-at-level provider, DS2/LE EXP counters, and in-game acceptance.

Twenty-seventh iteration: World DS bootstrap. Isolated `FUN_020a1c70` in the DS1 Ghidra export,
extended the species extractor with the dm creation block and regenerated `world_ds_species.json`,
added `WorldDsCreationStats` (dumped growth rows, 999/9999 clamps) with 3 unit tests, and wired the
one-time World DS seed into `outbound()`. All three story titles now bootstrap. Transfer package:
165/0 green; APK installed. Remaining: DS2/LE EXP counters and the owner's in-game acceptance.

Twenty-eighth iteration: EXP writing for the remaining story titles. `Ds2RecordCodec.generate` accepts
EXP channels and the Dawn/Dusk conversion/repository pass the tracked total's spread;
`LostEvolution.buildRecord` accepts the single EXP word and the LE conversion/repository pass the story
total. Round-trip tests assert EXP now survives World DS, Dawn/Dusk and Lost Evolution. Transfer
package: 165/0 green; APK installed and launched. Remaining for the objective: owner in-game acceptance.

Twenty-ninth iteration: retained-snapshot story backfill. `outbound()` now rebuilds `story_progress` from
the individual's retained native record (any story layout) before the one-time bootstrap, so partners
imported before tracking existed are sendable and carry their real level/stats. New `fromRecord`
mapping test; transfer package: 169/0 green; APK installed and launched.

Thirtieth iteration: sendability fix verified on the owner's database. A temporary debug probe ran the
real `outbound()` projection for every missing-story partner on-device: after the retained-snapshot
backfill and band clamping, Devidramon (Dawn/Dusk/LE), Were Garurumon (Black), Karatenmon, Machinedramon,
Falcomon, SkullGreymon, Otamamon and Gottsumon all produce records for their supported titles; only
Ryudamon, Sakumon, GulusGammamon and Sunarizamon remain blocked (absent from all three title tables).
The probe was removed; transfer package 172/0 green; APK installed.

Thirty-first iteration: default projection fix. On-device blocker probe showed Devidramon's per-game
blockers were already null for Dawn/Dusk/Lost Evolution, but the untargeted `outbound()` assumed World
DS and returned null, so the send screen disabled everything. The projection now picks the first story
title that supports the species when there is no source game. Probe removed; transfer package 172/0
green; clean APK installed.

Thirty-second iteration: deferred story seeding. `outbound()`/blockers/previews now build the story
block without persisting; `send()` calls `persistStorySeedIfMissing()` only after the journal reaches
ACKNOWLEDGED, so an abandoned transfer leaves no conversion behind. Transfer package: 172/0 green;
APK installed and launched.

Honest boundary: in-game boot acceptance of a VBHelper-written Lost Evolution save has not been
performed by the user yet; every candidate is verified byte-wise (sequence bump, MD5 reseal,
readback, byte-exact restore) and mirrors the game's own creator/allocator code, but only the game
can confirm the final load. The team container (`0x159C`) is inspection-only and EXP-to-next /
`+0x3A` aptitude semantics are not needed by the current conversion.

## Verify v58: authoritative Dawn/Dusk species identity (2026-11-19, same tree)

Owner report: "why is metal mamemon listed as not being in dawn and dusk when they are?" and
earlier "sent an Agumon Black and it became a regular Agumon; the app did not detect the black
WereGarurumon".

Root cause: the Dawn/Dusk record/status name table used for identity (ARM9 0x02112998) is
**truncated to about 12 glyphs** - MetalMamemon is stored as `MtlMamemon`, WereGarurumon (Black) as
`WGarurumonB`, PrinceMamemon as `PrMamemon` - so name lookup against the bundled catalog failed and
the UI reported those species as absent. The game does contain them; the record only stores a
species id.

Fix: ARM9 also holds the **packed full-name table** (0xFFFF-terminated strings, id order, first
entry `Chicchimon` = 0x41, offsets arm9+0x1075C6 Dawn / arm9+0x1076C2 Dusk, byte-identical across
titles). It was extracted into the asset (`fullNames`, 398 entries; the remaining 38 are the game's
empty "Undecided" slots) and each id is resolved to a bundled canonical species
(`canonicalOverrides` documents the 11 localization aliases decided from the ROM evolution chains:
War=Were, Volcanomon=Volcamon, Rapidmon Perfect, Dot Falcomon, DK=Dark, chessmon colours, SL=Slash,
Tsuchidarumon/Tuchidarumon). `Ds2SpeciesTable.canonicalId`, `NdsStorySpeciesTables.canonicalId`,
`NativeSpeciesResolution.tableIdentity` and `WorldDsSpeciesCrosswalk.ds2IdsByCanonical` now use it,
so identification (read) and availability/selection (send) share one authority. Kokomon and
Reptiledramon have no bundled counterpart and stay unidentified rather than being guessed.

Also fixed: a partner whose individual species is a base form while its assigned/exported card
character is a variant of that same Digimon ("Agumon" + "Agumon (Black)") now transfers as the
variant; a different Digimon never overrides the individual's own species
(`WorldDsSpeciesCrosswalk.preferRefined`).

Evidence: `Ds2SpeciesTableTest` asserts MetalMamemon (0x124), Black WereGarurumon (0x133),
PrinceMamemon (0x1B7), Volcamon, DarkTyranomon, Rapidmon Perfect, chessmon colours and the two
unidentified slots; `Ds2OutboundConversionTest` asserts the canonical->game-id crosswalk for those
species and the refinement rule; the real-save corpus from the phone still decodes.

## Verify v58: engine sequence tie and synthesized sends (2026-11-19, same device)

Two distinct defects were found by reading the phone's logcat and the app database instead of
guessing:

1. **Synthesized sends hit the retained inserter.** `Ds2SaveEditor.insertGenerated` delegated to
   `insertRetainedRecord`, so a generated record (identity 0 until the destination assigns one)
   failed with "A retained record must carry a nonzero local identity" and the UI showed the
   generic "Could not update this source". Fixed to `insertGeneratedRecord`; regression test goes
   through `NdsStorySaveEditors.forObservation(...)`.
2. **The engine's own counter tie.** After VBHelper writes a Dawn/Dusk save (counter 0 -> 1) and the
   game saves into the erased slot, the erased counter 0xFFFFFFFF wraps, so the game writes
   **counter 1 too**: both slots legitimately end at 1 with the same roster. The app required a
   unique newest copy and reported "The save has no defensible authoritative copy". A tie is now
   resolved to the lowest copy when the tied copies hold the same individuals (party order,
   positions and counters ignored), then the edit bumps that copy to a unique newest counter. The
   World DS profile keeps its previous no-auto-choice rule.

Evidence: the user's actual current saves were pulled from the phone (`analysis/ds2/device`,
not committed) and used as the real-save validation corpus. `Ds2RealSaveValidationTest` now runs
three cases against them: layout decode with automatic copy normalization, removal + byte-exact
restore, and **inserting a synthesized record through the editor** - the exact operation that
failed. All pass. Full `:game-save-core:test` and the app JVM suite pass; debug APK and the
integrity Android-test compilation pass. Still unverified: in-game boot of a written save.

## Verify v58: multi-copy saves and cross-game owned sends (2026-11-19, follow-up)

Owner report: "I receive a 'could not update this source' when trying to send a mon... it should
work in a really simple way: send any digimon from my storage to any game (where that digimon
exists) like Pokemon HOME/PKForge."

Root cause (read from the device logcat, not guessed): `Ds2SaveEditor.insertGenerated`
delegated to `insertRetainedRecord`, so every *synthesized* record — identity 0 until the
destination assigns one — was rejected with "A retained record must carry a nonzero local
identity", and the UI showed the generic "Could not update this source". That is why only
generated sends failed while exact retained sends worked. Fixed to call
`Ds2SaveEdits.insertGeneratedRecord`, plus a regression test that goes through
`NdsStorySaveEditors.forObservation(...)` instead of the internal function.

Two further defects found while diagnosing: the Dawn/Dusk editor required a save with **exactly
one populated card copy** (true for the two fresh fixture saves, false for any save the game has
written twice), and the send path decided "replace in place" from **both** copies, so an identity
that only the older slot still listed was treated as present and the replace could not find it in
the authoritative copy.

Fixes:
- `Ds2SaveEdits` edits only the authoritative (newest valid) copy and no longer requires a
  single-copy image; removal/insert/replace/admit verify against that copy's records, and the
  stale slot is left byte-identical (exactly what the game's own alternating slot writes do).
  New tests: dual-copy save removal leaves the older slot untouched, returning a record that only
  the older slot still lists inserts into the authoritative slot, replace only touches the
  authoritative slot, and all three restore byte-exactly.
- The send path decides "replace in place" from the authoritative copy only.
- Cross-game sends are now allowed the simple way: a partner in Storage can be sent to any linked
  supported game that owns the species. Same-layout destinations prefer the exact retained
  record; a different destination title synthesizes a fresh record for its own layout (native
  bytes cannot cross layouts) from the app projection. This replaces the previous
  "cross-layout send is refused" behavior.

Evidence: `:game-save-core:test` (12 `Ds2SaveEditsTest` cases including the dual-copy ones, real
saves still decode) and the full app JVM suite pass; debug assembly and integrity Android-test
compilation pass. Still unverified: in-game acceptance of a written Dawn/Dusk save and physical
phone runs.
## Digimon World Dawn/Dusk adapter: read, acquire and native editor (2026-11-19, same tree)

Owner request: "start planning transferring into Digimon World Dawn and Dusk... actually I need it
to be better [than World DS]" followed by "everything you need is here" with the two USA ROMs and
two retail `.sav` files, asking for the work to be finished from the ROMs with REA instead of a
manual controlled-save matrix.

What changed (source of truth: `CHANGELOG.md`, plan `DAWN_DUSK_TRANSFER_PLAN.md`, evidence
`game-save-core/DS2_RESEARCH.md`):

- The Dawn/Dusk layout was recovered from the user's own ROMs by static analysis (Ghidra headless:
  ARM9 + overlays) and verified on the two supplied real saves: `0x10000` image, `0x7B2C` copies at
  `0x0`/`0x7B80`, MD5 header over the copy with the header zeroed, u32 sequence at `+0x18`,
  `DSDIG2` magic, 40 records of `0x164` at `0x288`, party ids at `0x260`, last id at `0x25C`.
  Real-save decode: Dawn → Coronamon/Reppamon/Karatenmon (levels 20/35/35, party arrays,
  history forms) and Dusk → Lunamon/SkullGreymon/WGarurumonB, all matching the extracted species
  tables exactly.
- Core: `NdsStoryProfiles.DAWN_DUSK` registers the layout on the shared reader (signature required,
  so the families cannot decode each other); `Ds2SaveEdits`/`Ds2SaveEditor` implement
  revision-bound removal, exact retained insertion, replacement, party admission and byte-exact
  restore with sequence bump and header reseal.
- App: DS2 species asset (`ds2_species.json`, 436 species with base data/lineage/EXP curves from
  both ROMs), `Ds2SpeciesTable` + `NdsStorySpeciesTables` registry (per-triple resolution),
  `Ds2StatConversion` (`ds2-stat-v1`), Dawn/Dusk import profile, materialization seeding/returns,
  family+titles catalog resolution, and the JA Dawn title fix.
- No controlled before/after save pairs were required; the engine code plus two real saves settled
  every interpreted field. The Move/Send repositories now resolve profile + editor per observation,
  so Dawn/Dusk party records use the normal Move to Storage / Move to game flow (journal, durable
  backup, fresh-read preflight, readback verification, presence). App-only partners are synthesized
  into a Dawn/Dusk record from the game's own species defaults plus the app projection (level
  capped by the species max level, native EXP counters zero because their semantics are
  unestablished); a stored record is used as the seed so identity and unknown bytes survive; a
  cross-layout send (World DS record into Dawn or the reverse) is refused with FORMAT because that
  journey is Storage conversion, not a direct move. Not implemented: a title-binding prompt (file
  name resolves the title) and in-game acceptance of a written save.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:game-save-core:test` | 207+ JVM cases passed, including `Ds2SaveEditsTest` (6 cases: layout/party decode, cross-family rejection through the shared registry, removal + byte-exact restore + header/sequence checks, last-member/stale refusals, exact retained insertion, corrupt-header refusal) |
| Real-save validation (`DS2_SAVE_DIR`, files not committed) | `Ds2RealSaveValidationTest` ran against both retail saves: every record decodes (species in `0x41..0x1F4`, level 1..99, decodable names, `0x164` retained bytes, party records review-free); removal builds a valid-header candidate and restore rebuilds the exact original bytes |
| Species extraction cross-check | ExtractCoronamon base record equals the real save's windows: maxLevel 32, unk50 5, skills `[32,52,111,59]`, abilities `[1,36]`, resistances `[1000,200,900,700,700,500,400,500]`; EXP curve `[20] = 646` and `[35] = 2344` match the saves' `expToNext` values |
| `:app:testIntegrityCheckUnitTest` (transfer/navigation packages) | 128+ cases passed, including `Ds2SpeciesTableTest` (5), `TransferGameCatalogTest` (family/title resolution), `TransferGameCapabilitiesTest` (DAWN/DUSK readable with a core editor, no wired writer yet), and the updated `NativeSpeciesResolutionTest` registry cases |
| `:app:assembleDebug`, `:app:compileIntegrityCheckAndroidTestKotlin` | Passed in this pass |
| Phone / in-game | **Not run.** Linking a Dawn/Dusk save, copy review and Storage import must be checked on a device; loading an edited Dawn/Dusk save in the retail game is untested |

Deliberate boundaries: farm/DigiFarm transfer remains out of scope; record `0x120` state-bit
semantics and `0x128..0x164` stay uninterpreted (bytes preserved); writer repository wiring and
in-game acceptance are the next step.

## Digifarm glow confined to the lightest grass tiles and wireframe (2026-11-18, same tree)

Owner request: "make so only the lightest tiles of grass and the wireframe and cubes glow on the farm
and not the darker parts of grass (the luminance of these would never change)." Follow-up after the
first bake: "the lighter ones became completely white instead of being pulsating like before" — so the
final implementation keeps the old pulse but confines it to a transparent overlay, in colour.

What changed (source of truth: the CHANGELOG entry above): the base island floor holds a steady
emissive factor (`ISLAND_STATIC_GLOW = 1.45`); a duplicated island-top primitive with the
`island_glow_overlay_baked` material pulses with the old envelope (base 1.45 + amplitude 0.78). The
overlay texture is the authored colour of the lightest 8% grass mosaic tiles and the bright wireframe
lines with an alpha mask, so only those breathe (in colour, not white) while the darker grass and the
wireframe void stay static. The cubes keep their pulse.

Evidence for this working tree only:

| Check | Result |
|---|---|
| Overlay bake (shipped GLB payload) | 48/604 fully-grass tiles (8.0%) + 21,585 bright wire pixels masked; overlay alpha 0..255; base `island_top` image byte-identical to the previously shipped 512 art; overlay vertices +0.0006 above the base |
| GLB contract | `validate_asset.py` OK: 592,876 bytes, 22 nodes, 6 materials, 4 island primitives, overlay material `island_glow_overlay_baked` at primitive index 3 (BLEND, alpha 0..255), masked upper cliff, transparent cliff foot, V clamp |
| `py -3 -B tools/build_environment_themes.py --check` | Valid: four themed overlay variants (authored mask, themed colours), default payload byte-identical to the GLB overlay, base theme grass variants restored byte-identical to the committed art, other 39 theme PNGs untouched |
| Runtime | `Digifarm3dViewport` adds the base island as a static glow and the overlay primitive as the pulsing one (found by material name, so export order cannot break it); pre-existing AppCamera bloom preview `build/farm-glow-overlay/report/preview_trough_vs_peak.png` shows the coloured pulse over an unchanged static floor |
| `:app:testIntegrityCheckUnitTest` / `:app:assembleDebug` | Relevant suites green in this pass (`items.data.*`, `navigation.*`, `rendering.*`); the full JVM suite currently reports 4 failures in the unrelated in-progress `digiline.freechat` feature (see the note below) |
| Native capture | **Pending.** No ADB device/AVD in this environment; the pulse itself must be judged on hardware |

## Special-mission slot fix and readable Home feedback (2026-11-17, same tree)

Owner reports: on the partner overview, tapping a special-mission slot "may open the wrong kind of
mission for selection", and the failure message "appears below the action bar" and cannot be read.

What changed (source of truth: the CHANGELOG entries above):

- Special missions: slot identity is now the row position among a partner's four mission rows
  (`SpecialMissionAssignment`, `UserCharacterDao.getSpecialMissions` ordered by id), not the device-side
  `watchId`. The tapped card carries its row id and `GameplayRepository.applySpecialMission` updates
  exactly that row while preserving `id`/`characterId`/`watchId`. The assign chooser labels/filters by
  slot and shows a persistent inline error instead of a snackbar behind the dialog.
- Home feedback: the Home snackbar host applies the navigation-bar inset itself and lifts above the
  place dock band plus the partner peek slab at Peek (`homeSnackbarLift`), so the message is fully
  visible above the phone's navigation bar and the farm's bottom chrome; other routes are unchanged.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:testIntegrityCheckUnitTest` | 1198 tests, 0 failures, 0 errors, 3 skipped (incl. new `SpecialMissionAssignmentTest` 10 cases and `HomeFeedbackPlacementTest` 3 cases) |
| `:app:assembleDebug` | Passed on the same source tree (debug APK ~235 MiB) |
| `:app:lint` | Not re-run after the final fix in this pass. The run on this tree reported **1 error, 571 warnings, 40 hints**: a pre-existing `LocalContextGetResourceValueCall` in `AdventureScreen.kt` (the team-full toast resolved a string through `LocalContext` inside a click lambda). That line was changed to resolve the string during composition; the full lint pass needs re-running. Other changes added no Android API usage. |
| Native capture | **Pending.** No ADB device and no AVD were available in this pass (`adb devices` empty), so the snackbar placement and the mission chooser flow are not captured on hardware yet. |

## Single-farm cleanup: no multi-farm UI leftovers (2026-11-07, same tree)

Owner request: two places still behaved as if the app had multiple Digifarms — the DigiLine had a
farm section, and Storage captioned some partners with "in *farm name*". Removed every exposed
multi-farm surface; stored data and the legacy farm tables are untouched.

- **DigiLine.** The third "Farms" tab is gone; the screen now lists only Storage and Wild Ones, and the
  farm-thread query behind it (`observeFarmThreads`, `DigilineFarmThread`, `FarmThreadList`) was
  deleted with it.
- **Storage.** The `in <farm name>` caption under partner tiles is gone; the screen no longer observes
  `FarmAssignment`s at all (query and DTO removed). Character tiles now show only the partner name, the
  active marker, the favourite star and the away-source line.
- **Farm group chat.** `FarmGroupScreen`, its `AppRoute.FarmGroup` destination, the matching
  `NavigationItems` entry and the bottom-bar/back-stack mappings are deleted.
- **Legacy World alias and the old farm screen.** `DigifarmScreen` (the multi-farm list with create
  another/archive/resident needs/transfer) and the Radar|Digifarm tab row on the legacy `World` route
  are deleted; the `World` alias now opens the Radar world directly. `WorldSectionTabs` is gone and
  `RadarScreen` no longer carries tab parameters.
- Docs updated: `DESIGN.md` → Farm home shell, and the follow-up note in
  `PRESENTATION_REDESIGN_PLAN.md`.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1019 tests, 0 failures, 0 errors, 3 skipped |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here: DigiLine two-tab layout, Storage tiles without the caption and the World alias need a device look |

## Command Points removed; Decode-inspired 3×3 command band and tap-to-move (2026-11-07, same tree)

Owner request: remove the Command Points resource (it never gated anything), redesign the battle
command band after Re:Digitize Decode — row 1 the partner's three equipped techniques, row 2 Items /
Special / Strategy, row 3 Focus / Defend / Move — move becomes a one-tap arena destination instead of
approach/fall-back, the partner's HP/MP/ESP readings move into the band header where the CP instrument
used to sit (no floating vitals strip over the arena), and the special/Blast timing becomes a
Decode-style command dial with a yellow progress arc, a red target arc and no Stop button (any touch
answers it).

What changed (source of truth: `DESIGN.md` → Battle screen):

- Removed from the core: `TechniqueDefinition.commandPointCost`; `BattleConfiguration`
  `commandPoints`/`maxCommandPoints`; `BattleSnapshot` CP/support fields; `BattleStatistics.supportCommands`;
  `TrainerAction.Support`; `OrderFailure.SUPPORT_*`; `SupportWindowOpened`/`SupportSucceeded`; the
  cheer window, CP balances, reservations, gates and HUD instrument.
- Added `TrainerAction.MoveTo(x, z)` as an immediate trainer action: finite-coordinate validation,
  playable-radius clamp, 8 s timeout, arrival cleanup, and cancellation by defend/technique/queued
  order; the renderer exposes `OfflineBattleSceneView.pickArenaPoint(x, y)` (projects arena floor
  candidates with the HUD's forward matrix and returns the visible point nearest the tap).
- Rebuilt the band as a fixed 3×3 deck shared by training/Arena, Radar and the AR stage; the partner
  instruments and deck are capped at 520dp and centred on wide windows; the duplicate AR command
  header/bar, the floating partner vitals strip and the redundant Techniques dialog are gone.
- Blast timing dial: dark technical ring with tick marks, a yellow progress arc and a red target arc
  from `BLAST_RED_ZONE_START`, a live needle, and the ordered move's name plus a small BLAST label in
  the centre; no Stop button, full-screen touch remains the input and the timing/`blastNeedleHit`
  logic is unchanged.
- Final visual pass (owner reference): the timing overlay was replaced by the Decode-style command
  dial above (ring, arcs, 48 ticks, needle, centred move name), and the instruction now names the ring
  instead of a bar.
- Checkpoint compatibility: `formatVersion` stays `1`; Gson ignores the removed
  `commandPoints`/`maxCommandPoints` keys in old envelopes, and NPC replay never read CP, so active
  battles keep loading. No Room migration.
- Follow-up fixes after the first device pass (2026-11-07): the three technique keys now resolve
  locale-aware names through `battleTechniqueName` (the catalogue's authored pt-BR fallback no longer
  leaks into en/ja); tap-to-move resolves the destination by projecting arena floor candidates with
  the same forward matrix the projected HUD uses (the inverse ray was mapping far taps closer) and the
  marked walk suspends autonomous planning — guards, counters and autonomous attacks no longer clear
  it — until arrival or timeout; warm rematches reset the visible session (snapshot/fighters/countdown)
  and the scene's stale guard now keys each accepted snapshot to its session, so the new battle no
  longer keeps the previous positions or renders invisible fighters.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (final tree) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1019 tests, 0 failures, 0 errors, 3 skipped; the deck test covers the nine fixed slots, `moveToKeepsWalkingWhileAutonomyWantsToAttack` covers the autonomy lock, and `everyCatalogTechniqueHasALocaleAwareDisplayName` covers the localized technique keys |
| `:app:lintDebug` | Passed on the changed tree (before the follow-up fixes); no new lint findings were expected, but the follow-up pass was build+test only |
| `:app:compileIntegrityCheckAndroidTestKotlin` | Passed |
| Checkpoint/replay | Unchanged policy (`formatVersion = 1`, unknown Gson keys ignored); no migration required |
| Native capture | **Pending by owner request.** The device was only used for the first pass; the owner asked that it not be used for further captures. Portrait/landscape phone captures, the AR compact layout, TalkBack and font-scale acceptance remain owed before native acceptance — compilation is not native approval. |

## Farm long beats: workouts and naps take longer (2026-11-07, same tree)

Owner request: interactions like sleeping and training should take longer.

- **Training** now holds **10-20 s** (was 4-7.5 s).
- **Sleeping** now holds **30-60 s** (was 15-30 s).
- Because those states now occupy more wall-clock time, their roll probability was trimmed slightly to
  keep the island calm rather than busy: training 4% → 2%, sleep unchanged at 1%, standing 47.2% →
  49.2%. Everything else (walking 42%, jog 3%, happy 2%, greeting 0.8%) is unchanged, so ~91% of rolls
  are still standing/walking, but a workout or nap that happens is now a state you can actually see.
- No other behaviour changed.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1014 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (14) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the longer workout/nap holds need a device look |

## Farm walking restored to the pre-liveliness rhythm (2026-11-07, same tree)

Owner request: look at git to see how walking worked before the resident-life pass and restore that
feel, while explicitly keeping **walk frames on the walk pose and run frames on the run pose**.

- **What git showed.** Before the resident-life pass the diorama walked in discrete strides: one step
  of up to 0.22 world units was handed to the renderer, interpolated over 1.1 s, and the next stride
  started after a 1.15-1.6 s beat — leaving a short foot-plant pause between steps. The walk pose was a
  fixed 250 ms cycle, and there was no step bob. (That version bound the run frames to the walk pose
  only because the old four-frame data had no walk frames; the owner asked for the correct frames now.)
- **Restored.** `FarmDioramaSimulation` moves walkers/joggers in the old stride rhythm again: 0.22-unit
  walk strides interpolated over 1.1 s with a 1.15-1.6 s beat, and 0.32-unit jog strides over 0.8 s
  with a 0.9-1.2 s beat. The renderer cycles the walk pose at a fixed **250 ms** and the jog at
  **220 ms**; the speed-derived cadence, the beat latch and the step bob from the previous passes are
  removed. The pose mapping is the requested one: `walk`/`walk2` use the artwork's walk frames,
  `run`/`run2` its run frames. The old pause reads as the sprite coming to rest between steps, and the
  run can no longer strobe because one stride fills the whole 1.1 s window.
- Kept from the later passes: probability pacing, rarer/longer greetings, fresh entropy and per-
  resident random streams, collision, and the removal of the head glyphs.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1014 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (14) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the restored stride rhythm and frame mapping need a device look |

## Farm randomness: no replayable seed (2026-11-07, same tree)

Owner report: interactions looked like they repeated the same pattern "every time", asking for truly
randomized behaviour per Digimon instead of a fixed seed.

- **Cause.** `FarmHomeWorld` seeded `FarmDioramaSimulation` with `rosterKey.hashCode()`, so every
  session (and every roster/art change) replayed the exact same deterministic script: same opening
  layout, same sequence of greetings, same pairs.
- **Fix.** The production host now seeds each newly created cast with `Random.nextLong()` (fresh
  entropy), while every resident additionally owns its **own** random stream derived from that seed —
  one Digimon's decisions are independent of another's and of cast iteration order. Greeting partners
  are now picked **uniformly at random** among everyone standing within reach instead of always the
  nearest neighbour. The deterministic `seed` constructor parameter remains, but it is now a test-only
  path; `differentSeedsDoNotReplayTheSameShow` pins that different seeds really produce different
  shows, alongside the existing same-seed determinism test.
- A session therefore never repeats itself: start the app twice with the same farm and the greetings,
  pairings and timings differ.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1017 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; two app sessions must be compared on device to confirm the shows differ |

## Farm greetings: rarer and longer (2026-11-07, same tree)

Owner request: make the interactions rarer and last longer.

- **Rarity.** The greeting roll is now an explicit probability instead of "whatever is left over":
  standing 47.2%, walking 42%, jog 3%, workout 4%, happy beat 2%, nap 1%, greeting **0.8%**
  (previously 2%). A greeting also only starts when a standing neighbour is within reach; otherwise
  the roll becomes an ordinary walk, so the effective frequency is even lower. With twelve residents
  this works out to roughly one greeting every ~40-45 s across the island.
- **Duration.** The face-to-face celebration was 2.4-4.0 s; it is now **5.5-8.5 s**, so the whole beat
  (walk over, celebrate, walk away) lasts about 10-14 s. When a greeting happens it is now a small
  moment the eye can settle on instead of a blink.
- `FarmDioramaSimulationTest.socialBeatHappensAndLingers` was strengthened: it detects the pair on the
  first celebration step and then counts the held beats, requiring at least 4.5 s of celebration.
- No other behaviour, gait or collision code changed.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (9) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; greeting frequency and its longer hold need a device look |

## Farm walk cadence calibration (2026-11-07, same tree, after device feedback)

> **Historical.** Superseded by "Farm walking restored to the pre-liveliness rhythm" above: the
> speed-derived cadence and step bob described here were removed when the old stride rhythm returned.

Owner report after the gait pass: the walk "still feels like it doesn't follow the walking animation
pace", and reads too fast.

- **Measured the artwork instead of estimating.** Across the extracted sprite sets (1137 sets) the
  horizontal distance between the two drawn feet at the widest walk frame has a median of **0.427 body
  heights** (0.412 for the run pair). The previous constant was 0.38, and on top of that the stroll
  speed (0.18–0.23 world units/s) put the legs at roughly 2.5 strides a second — noticeably faster than
  the drawn stride implies.
- **Recalibrated.** `WALK_STRIDE_BODY_HEIGHTS` is now 0.43 (the measured stance) and
  `RUN_STRIDE_BODY_HEIGHTS` 0.60, so the cadence is solved from the distance the artwork actually
  depicts. The stroll was slowed to **0.13–0.17 units/s** (jog 0.30–0.38): at the typical 0.15 units/s
  the walk now holds each pose for ~370–430 ms instead of ~250 ms, which is what makes the legs read in
  step with the ground instead of scurrying under a sliding body. Crossing the island takes ~9 s.
- No animation, gait-state or collision code changed; only the stride constants and the two speed
  ranges. `ResidentPoseAnimationTest`'s cadence cases were updated to the measured stride (17 tests).

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `ResidentPoseAnimationTest` (17) and `FarmDioramaSimulationTest` (9) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the slower cadence and stride match need a device look |

## Farm pacing: probabilities instead of cycles, and no head glyphs (2026-11-07, same tree)

> **Historical numbers.** The probability table and greeting length below were superseded by the
> "Farm greetings: rarer and longer" section above (greeting 0.8%, celebration 5.5-8.5 s). The model
> and glyph removal still describe the current implementation.

Owner request: the residents were "doing a lil too much"; instead of working with cycles of what a
Digimon does and limits on how many may do something at the same time, pace them by **probability of
doing a new thing**, where most of the time they just walk or stand; and remove the glyphs that appear
above a Digimon's head ("♪", "Zzz") completely.

**Change:**

- **Pacing model.** The simulation no longer keeps global budgets, a social cool-down, or a
  per-activity director. Every resident that finishes a beat rolls once, independently:
  `46%` stand (2.5–8 s), `42%` walk, `3%` jog, `4%` train, `2%` happy beat, `1%` nap (15–30 s), and the
  remaining `2%` is a greeting that walks to a standing neighbour, faces them, celebrates briefly and
  parts — falling back to a walk when nobody is in reach. No cap stops several residents from walking,
  training or napping at the same time; the calm comes from the odds (≈88% of rolls are standing or
  walking) rather than from counting actors. Arriving at a spot no longer forces a held idle beat: the
  resident rolls for its next beat in the same simulation step, so the island never dead-stops.
- **Head glyphs removed.** `FarmDioramaEmotes.kt` is deleted, the `FarmDioramaEmotes` mount is gone from
  `FarmHomeWorld`, and `FarmDioramaFrame` no longer carries an interaction id (`FarmDioramaInteraction`
  is removed). Nothing is drawn above a resident's head any more; sleeping and social beats are read
  from the poses alone (held sleep pose with its slow breath; two residents facing each other with the
  happy pose).
- `FarmDioramaSimulationTest` was rewritten for the new contract: most actor-beats are standing or
  walking, several residents can move at once (proving the caps are gone), sleep stays occasional,
  greetings still produce a face-to-face happy pair, sleepers stay planted, and no one ever overlaps or
  teleports.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (9 tests) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here: the softened pacing and the clean heads still need a device look |

Boundaries: only the farm's pacing model and its overlay changed; gait/cadence, collision, the roster
and every other surface are untouched.

## Farm gait feel: sliding walk and strobing run (2026-11-07, same tree)

> **Historical.** Superseded by "Farm walking restored to the pre-liveliness rhythm" above; the
> speed-derived cadence, beat latch and step bob from this pass no longer exist.

Owner report after the resident-life pass: the way the Digimon walk "looks worse than before — they
look like they are sliding on the ground", and when running their sprites "change crazy fast".

**Diagnosis** (renderer code + the actual extracted artwork):

- **Run strobe.** Poses are pushed at 10 Hz, but run steps interpolated over 90 ms. For part of every
  cycle the movement segment was already finished, `updateResidentMotion` reported `walking = false`
  for a frame, the activity token `RUN` fell through to the idle family, and the pose bound
  `run → idle → run` ten times a second. Walk steps (140 ms) only masked the same issue because their
  window outlived the 100 ms beat.
- **Sliding walk.** The frame cadence was fixed (260 ms) for every resident while each travels at its
  own speed, and nothing tied "distance covered" to "frame shown", so the planted foot could never
  keep pace with the ground. The artwork is not the problem: across the extracted sets the walk pairs
  are real two-frame leg swaps (mean 28.6% of pixels differ; only 10.6% of sets are identical walk
  frames).

**Fix:**

- Every gait step now interpolates in 105 ms — slightly longer than the 100 ms beat — so a segment can
  never run dry between poses; the walk/run pose is additionally latched across beat gaps for 260 ms.
- The renderer solves the cadence from the resident's measured ground speed and the artwork's authored
  stride (`frameMs = stride / speed`): 0.38 body heights per walk frame, 0.62 per run frame, clamped to
  170–420 ms (walk) and 130–300 ms (run). A slow resident visibly takes slower steps than a fast one,
  and foot pace matches the ground by construction instead of being a fixed cycle over variable travel.
- A small phase-locked step bob (3.5% of body height walking, 7% running, zero at each footfall) gives
  the sprite a weighted step instead of a glide.
- Walk/jog speeds were recalibrated around the previous farm's ~0.19 units/s stroll with a jog about
  twice as fast (jog 0.38–0.48 units/s, steps 105 ms). `ResidentPoseAnimationTest` gained 3 cases for
  the stride/cadence math.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1015 tests, 0 failures, 0 errors, 3 skipped; `ResidentPoseAnimationTest` (17, including the new stride/cadence cases) and `FarmDioramaSimulationTest` (8) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here. The report came from real play, so the corrected gait cadence, the step bob and the absence of run strobing still need a device look |

Boundaries: only the farm's renderer policy/timing and the simulation's gait speeds changed; collision,
social beats, sleep, emotes and every other surface are untouched.

## Farm resident life: full pose set, gaits, sleep, social beats and collision (2026-11-06, same tree)

> **Historical pass.** Its budgeted pacing, head glyphs and "at most one sleeper/pair" rules were
> superseded by the two 2026-11-07 sections above (probability pacing and glyph removal). The artwork,
> collision, roster and reduced-motion details below still describe the current implementation.

Owner request: make the Home Digifarm lively with the assets a VB Digimon actually has — idle,
walking when slow, running when faster, occasional sleep, interactions and the happy animation — without
the island becoming chaos, and stop residents passing through each other.

- **Artwork.** The farm used to draw four frames per resident: `CharacterWithSprites` only carries
  idle/idle2/run1/run2, so walk was faked with run frames and happy/sleep with idle. `FarmRosterArtwork.kt`
  now resolves the complete twelve-pose `Sprite` row — VB through the existing `DisplaySprite` view
  (owner appearance preference and idle fallbacks included) via `SpriteDao.getForCharacter(character.charId)`,
  Dot through `BundledDotSpriteLibrary.sprite(setId)` — while keeping selection two-phase: cheap
  availability probes decide the artwork filter, cap and hourly shuffle first, and the full frame rows
  are read only for the ≤12 selected residents. The old four-frame mapping survives as the fallback for
  partial rows, so a resident never disappears for missing data. `FarmArtwork` carries idle1/2,
  walk1/2, run1/2, train1/2, happy and sleep plus `contentKey` (the model-rebuild key), `bodyAspect`
  and `bodyRadius`.
- **Simulation.** `FarmDioramaSimulation` (pure Kotlin, `farm/diorama/`) replaces the twelve independent
  actor coroutines with one deterministic cast stepped at 10 Hz by `FarmHomeWorld`: staged opening
  tableau, per-resident temperament, walk and jog gaits, training, rare sleep, solo happy beats, and a
  social state machine (approach → face → celebrate → part). Calm budgets: movers ≤ min(3, cast/4), one
  jogger, one sleeper, one pair, and no solo celebration during a pair. Time is clamped per step so a
  paused host cannot teleport anyone, and the same seed replays the same show. The driver stops while
  `isActive` is false (place open or partner sheet expanded) and resumes from the last frame.
- **Collision.** Residents are circles of artwork half-width plus a 12 mm margin, resolved after every
  step: the solver pushes movers only (a resident that has just arrived counts as a mover for that
  step), never plants drift, clamps to the island and runs up to eight relaxation passes. The renderer
  adds `separateOverlappingResidents()`, a movers-only pass over the interpolated visual positions, so
  even mid-step interpolation can never draw two sprites inside each other.
- **Renderer.** `ResidentPoseAnimation` (pure, unit-tested) picks the pose family and cadence: run/run2
  at 160 ms while jogging (falling back to walk when the artwork has no run frames), walk/walk2 at
  260 ms, held sleep with a feet-planted breath, train/train2 at 360 ms, held happy with a decaying hop,
  idle/idle2 at 900 ms. Standing social beats turn a resident toward its partner at 4.5 rad/s inside a
  dead band; reduced motion freezes residents on idle art with no cycling or gestures.
- **Emotes.** `FarmDioramaEmotes` draws at most two glyphs, projected with the scene camera and refreshed
  per frame only while visible: a quiet "Zzz" over the single sleeper (3 dp bob) and one "♪" over the
  celebrating pair (300 ms fade). Decorative semantics only, no pointer input, and the simulation frame is
  read inside that layer so the farm surface never recomposes per beat.
- **Docs:** `DESIGN.md` **Farm home shell** → Content rule and Frame budget.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1012 tests, 0 failures, 0 errors, 3 skipped; new `FarmDioramaSimulationTest` (8 tests), `ResidentPoseAnimationTest` (14) and `FarmArtworkTest` (5) pass |
| `:app:lint` | Not re-run for this pass (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: gait cadence, the calm pacing across a long idle session, on-screen collision and the two emotes are not captured yet |

Boundaries: battle, Radar and AR surfaces are untouched; the legacy DB-driven `DigifarmScreen` and its
stored data are untouched; the farm stays presentation-only (nothing persisted).

## Home ↔ place transitions (2026-11-06, uncommitted tree)

This pass gives the farm home an authored transition to and from every place instead of a plain route
crossfade. The farm is a shell-level world (not a `NavHost` destination), so the motion is split: the
route transition animates the pages, and the shell animates the world layer underneath them. Evidence
for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 982 tests, 0 failures, 0 errors, 3 skipped; the new `NavigationMotionTest` (3 tests) passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no issue in `AppNavigation.kt`) |
| Native capture | **Pending.** No ADB device and no AVD were available in this pass (`adb devices` empty), so the transition feel, reduced motion and a screen recording of Home → place → Home are not captured yet |

### Motion contract

- **Home → place:** the outgoing chrome fades in 150 ms; the arriving place fades in over 220 ms while
  lifting ~2% of the window height over 320 ms; the farm layer keeps full opacity and pushes in
  1 → 1.05 over 280 ms, then the surface is dropped - after the place is opaque, so the world is never
  cut out from under a half-transparent screen. The farm's frame loop pauses as soon as Home is left,
  so no second Filament scene renders during the change.
- **Place → Home:** the place sinks ~2% while dissolving (fade 220 ms / travel 240 ms); the farm
  settles 1.05 → 1 over 360 ms; Home's chrome enters with a 40 ms delay so the world lands first.
- **Place → place:** unchanged short shared-axis horizontal slide (280 ms enter / 220 ms exit); routes
  outside the primary row keep the 200/140 ms crossfade.
- **Reduced motion** (system animator scale off): all of the above become ~90 ms crossfades with no
  scale and no travel; the world still holds through the fade instead of cutting.
- **Where:** `AppNavigation.kt` (`routeEnterMotion` / `routeExitMotion`, `FARM_HANDOFF_MS`,
  `FARM_DEPTH`, the farm layer's `graphicsLayer`); design rules in `DESIGN.md` **Farm home shell** →
  Transitions and **Navigation** → Motion.
- **Boundaries:** the AR stage, the partner sheet's own detents, the Radar place and the battle screens
  are untouched; this pass only decides how the shell swaps them.

## Radar biome readout (2026-11-06, same tree)

Owner report: no biome has appeared on the Radar in a long time. Audit result — the system was working,
the display was not:

- **Working:** `WorldRepository.currentBiome` is still resolved by `OpenStreetMapBiomeDetector`
  (Overpass, 250 m radius, cached by ~220 m cell for 15 min; failures cached 30 s) inside
  `requestPopulationRefresh` (triggered by accepted GPS fixes after ~60 m of movement, on entry when
  spawns are empty, and on recovery), and the resolved biome still feeds `ensureSpawnsLocked`'s
  `favoredAttribute` spawn bias.
- **Broken display:** the only consumer of `currentBiome` in UI was the 10sp
  "Surroundings: …" label inside the **legacy 2D radar** branch, which is selectable only while the
  master Debug switch is on. The isometric radar chrome never showed it, so the redesign silently lost
  the readout.
- **Also weakened:** `publishSpawnBiome` ran only after the region command applied, so a rejected or
  stale refresh left the flow at `NULL` even when the lookup succeeded.

Fix: `RadarTopChrome` takes `biomeText` and renders it as the top cluster's quiet context line under
"Radar · NW", fed by the localized `ui_world_biome_label` ("Surroundings: %1$s") only while the biome
is not `NULL`; `WorldScreen` publishes the biome immediately after the lookup, before the spawn command.
The legacy 2D label is untouched.

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (APK 21:03) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 985 tests, 0 failures, 0 errors, 3 skipped |
| `:app:lint` | Not re-run for this pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| On-device | **Pending.** The readout depends on a reachable Overpass endpoint and on the biome cells that map to the player's surroundings; no device/AVD is attached to confirm the line appears outdoors |

Boundary: `NULL` remains the honest fallback (unreachable/slow Overpass, unmapped areas), so the line can
legitimately stay absent; the Overpass timeout is 1.5 s with one building-footprint fallback query.

## Radar ground scroll (2026-11-06, same tree)

The partner token always sits at the plate centre, so walking was previously invisible on the isometric
radar: only the wild Digimon moved. The authored plate now slides opposite to the player's real travel,
matching the legacy 2D radar's scrolling grid.

- **Math** lives in `RadarIsometricGeometry.radarIsometricGroundPhase(anchor, player)`: the displacement
  from the first fix is returned in north-up scene metres (east = +x, north = -Z), folded by
  `foldToHalfCell` into `[-cell/2, cell/2]` with one cell = `RADAR_ISO_GRID_CELL_METERS` (60 m), so the
  finite authored floor never slides out from under the camera.
- **Rendering** (`RadarIsometricViewport`): the viewport keeps `groundPhaseMeters` from the first
  `radarPlayerPosition` fix, converts it to plate units with
  `radarIsometricPlateUnitsPerMeter(displayedVisibleRadius)` (so the phase stays correct while the plate
  re-scales for zoom), rotates it with the compass via `radarIsometricRotate`, and applies it as
  `T(offset) · R(heading) · S(scale)` to the ambient plate root. Change detection now includes the
  offset so the transform is only rewritten when something actually moved.
- **GPS-wander filter:** the raw fix is low-passed before it reaches the plate, with
  `tau = 0.35 s + 0.08 s × accuracyMeters` (a 10 m fix follows in ~1.15 s) and a hold above 30 m
  reported accuracy, so a standing player does not shake the grid; the filter state is unbounded
  (unwrapped) and only the output is folded to half a cell, so walking still reads within a step or two.
- **Ground-marking draw order:** the authored `world_grid` is a BLEND mesh, so Filament depth-sorts it
  with the translucent ground markings and the sliding plate flipped that order with walking direction
  (the interaction ring appeared under the grid). The interaction ring and metre-ring decals now set
  `RenderableManager.setPriority` to 6 and 5 (default 4, 7 renders last) so they always draw after the
  grid, and the metre-ring decal's height is derived from the plate scale (grid top + lift) instead of a
  fixed scene height, so it also clears the grid at the closest zoom.
- **Scale is honest:** the plate is the radar's linear metre reference for the metre rings, so the
  slide is 1:1 with walked distance and reads strongly when zoomed in, subtly at the widest zoom — the
  same trade-off the 2D radar has. Marker motion keeps the accepted compressed presentation curve.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (APK 20:26) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 985 tests, 0 failures, 0 errors, 3 skipped; both new ground-phase cases pass |
| `:app:lint` | Not re-run for this pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD: the scroll feeling while actually walking (and how GPS jitter reads when standing) is not verified on hardware |

## Transition background and Arena lobby scroll (2026-11-06, same tree)

- **Farm background crossfade.** `AppNavigation` used to hold the farm layer fully opaque and drop it
  once the arriving place was opaque, but the destination pages are transparent over the global device
  pattern, so the farm is in fact the page background during the change and the drop read as an abrupt
  background swap at the end of the transition. `farmAlpha` now animates 1 → 0 (leaving) and 0 → 1
  (returning) over 240 ms, crossfading the farm with the device pattern in step with the page; reduced
  motion keeps the instant switch. `FARM_BACKGROUND_FADE_MS` replaces the old hold/snap constants.
- **Tamer Arena lobby bars.** `TamerArenaLobby` now collapses the search field and the
  Tamers/Tournaments/Records tab row while the opponent roster scrolls down (96 px of downward travel),
  and brings them back on any upward scroll (12 px) or at the top, using the same
  `snapshotFlow`/`AnimatedVisibility` pattern as Storage's search bar, with `motionEnabled()` deciding
  whether the expand/fade runs. Entering the team editor, a briefing or the bracket restores them.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed (source set compiles) |
| `:app:lint` | Not re-run for this UI-only pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: neither the background crossfade in the two light palettes nor the Arena collapse-on-scroll is captured yet |

## Partner picker contrast and farm roster reuse (2026-11-06, same tree)

- **Light-palette picker fix.** `StorageCharacterPickerDialog` (the Arena "Choose partner" surface,
  also used by training preparation and the legacy farm screen), the species sprite picker and the
  species catalog editor painted `MaterialTheme.colorScheme.background`, which this theme defines as
  `Color.Transparent` so ordinary pages show the global device pattern. In a full-screen dialog that
  meant the screen underneath plus the dialog window's dim were visible, so the two light palettes read
  as a darkened page. All three now open edge-to-edge (`decorFitsSystemWindows = false`) and paint
  `DeepPurpleBg` (the palette's own opaque background), with the dialog wrapper supplying the
  system-bar/IME insets. `StorageCharacterPickerScreen`'s Scaffold no longer resolves insets itself:
  the app shell already consumes them for the routed `ChooseCharacterScreen` use, and the dialog wrapper
  pads its own window.
- **Digifarm add-member reuses that picker.** `FarmRosterSheet` still lists the members beside the
  resident-limit and artwork controls, but "Add member" now opens `StorageCharacterPickerDialog` with
  the non-member partners instead of the sheet's inline candidate list: search, filters, sort, sprite
  grid and the same 48dp tiles as Arena/Storage. Selecting adds the partner immediately, the picker
  stays open while capacity remains and closes on the last free slot. `FarmMemberOption` carries the
  stored character so the roster hands the picker real sprite data.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed (source set compiles) |
| `:app:lint` | Not re-run for this UI-only pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: the light-palette picker page and the farm picker flow are not captured yet |

## Top-header alignment (2026-11-06, same tree)

Owner request: every screen whose header does not use the same type as Home, the Radar place and the
battle surface. Audit of every header entry point in `app/src/main` (`TopBanner` call sites, the
`TopAppBar`/`CenterAlignedTopAppBar` variants, and the clusters that pad the status bar themselves):

| Surface | Header before this pass | Resolution |
|---|---|---|
| Home (`FarmHomeScreen` → `HomeTopHud`) | floating status cluster over the farm (scrim, HUD ink, 48dp keys, hairline) | reference — unchanged |
| Radar place (`RadarPlaceScreen`) and World place (`WorldScreen`) | `RadarTopChrome`: floating cluster, same scrim/keys/hairline | reference — unchanged (WorldScreen only had a dead `TopBanner` import, removed) |
| Battle surfaces (`OfflineTrainingBattleScreen`, Arena sessions → `BattleTopCluster`) | floating cluster over the arena | reference — unchanged |
| Every other screen / dialog header | `TopBanner`: gradient shelf, centered `titleLarge`, filled purple/cyan chips, surface-to-background gradient | rebuilt as the reference type |

`TopBanner` is now the page form of the same top-cluster type: a left-aligned `titleMedium` title
carrying a single `labelMedium` context line, **bare 48dp keys** (icon plus target only — no fill, no
hairline stroke, no visible cut-corner frame; the shape only bounds the ripple, as Home's HUD keys
already did), one junction line, and a palette-resolved scrim instead of the shelf. The back key always
owns the left edge and is resolved from the explicit `onBackClick` or, when absent, from the activity
back dispatcher (so it matches the system back gesture, including screens with their own back
handling); every other action the header receives — transfer, scan data, import, scan, modify,
adventure, gear and the `trailingAction` slot — renders on the right in that order. The battle
header's left key is already its exit action; the Radar/World header's left key is already back.
Shared primitives are in `components/HudChrome.kt` (`hudTopScrim`, `hudInkShadow`/`hudInk`, `HudKey`,
`HudHairline`). The public API, labels, `importEnabled` and every semantic are unchanged, so the ~38
call sites across 34 files were aligned without edits.

Screens aligned through that single component: Adventure, Items, Storage and its character picker,
Dex (`CardSelectionScaffold`, `CardViewScreen`, `CardAdventureScreen`, `ChooseCard`), DigiLine and farm
group, Quests, Settings, Credits, Lorebook, Chat and the world chat/conversation/recruits screens,
Arena lobby (`TamerArenaLobby`), battle hub (`BattlesScreen`), training preparation, Technique loadout,
Transfer hub and the transfer/scan flows, Sprite viewer, and the bracelet-conversion, species-sprite
and species-catalog dialogs. Legacy `home/ui/HomeScreen.kt` and `DigifarmScreen.kt` are not routed but
use the same component.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; `StorageScanCollectionTest` still finds both header keys by content description |
| `:app:lint` | Not re-run after the key/back pass (UI-only edits); the previous run on this tree reported 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device and no AVD in this pass, so no portrait/landscape, light-palette or font-scale captures of the aligned header exist yet |

Boundaries: the Home/Radar/Battle clusters were not refactored onto the new primitives in this pass (the
Home status cluster is unchanged and previously device-verified; the Radar and Battle clusters only had
their key frames removed, matching Home). 48dp targets, heading semantics, content descriptions and back
behaviour are preserved, but TalkBack and 1.3× font-scale acceptance for the new header are still
unrecorded. Home is the shell root and has no back destination, so its left cluster stays the farm
identity rather than a back arrow.

## Place-switch performance pass (2026-11-06, same tree)

The owner reported place switching feeling clunky and low-framerate on the SM-A346M, both before and
after the transition motion pass. This pass removes work from the switch instead of changing the
motion. **It is not measured**: no ADB device and no AVD were available (`adb devices` empty), so the
evidence is code-level plus the standing gates; the on-device procedure is in
`docs/performance/BASELINE.md` §4.8 and the result is still owed.

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped; the new `RouteArrivalTest` passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no new finding in the changed files) |
| On-device frame timing | **Not measured.** No device/AVD attached; a debug build is not representative, so compare a `-PnonDebuggable` debug build or a release build with §4.8 |

What was removed from the switch:

- **Repeated sprite decodes.** `CharacterEntry` rebuilds its bitmap whenever the idle frame swaps (the
  `remember` key changes with the frame) and on every arrival at a collection screen. Each rebuild was
  a per-pixel RGB loop plus a hardware-bitmap allocation on the main thread. `BitmapData.getBitmap()` /
  `getObscuredBitmap()` now serve a 16 MiB content-keyed `LruCache` (`utils/BitmapData.kt`), so a frame
  swap or a revisit is a cache hit.
- **Stacked entrances.** Every visible grid card ran its own 260 ms alpha+scale entrance on arrival, on
  top of the page transition. The shell now raises a process-wide, non-observable `RouteArrival` flag
  during composition of a route change and lowers it once the enter motion settles; arriving cards
  start fully visible, while items that appear later (scrolling, later pages, filters) keep the
  entrance. `DESIGN.md` **Farm home shell** → Transitions records the rule.
- **Persistent surface relayout.** The farm surface sat inside the inset-padded container, so every
  Home↔place change re-measured it and resized the `TextureView` that owns the Filament swapchain in the
  same frames as the transition. The shell is now two layers: the world keeps the window's full bounds
  for the shell's lifetime and only the `NavHost` takes the system-bar/IME insets and padding; the world
  also gets a stable modifier, so a route change no longer recomposes that subtree.

Known remaining hotspots, left alone because ranking them needs a device:

- Entering a full-bleed 3D place (`Radar`, `World`) constructs a fresh Filament `Engine` in the view
  constructor (`RadarIsometricViewport`, `Digifarm3dViewport`, `OfflineBattleScene`) and uploads the GLB
  on the main thread; moving that off the transition frame was not attempted unverified.
- The battle/Arena hub composes its whole animated surface on entry.

## Battle screen full-bleed redesign (2026-11-05, uncommitted tree)

This pass replaced the framed battle page (16dp gutter, bordered 1:1 viewport, half-screen command
deck) with one full-bleed battle surface shared by Arena/training battles and Radar encounters, and
stopped the Radar battle from reverting to the square window. A Radar-battle crash found after the
first gate is fixed in the same tree (see the crash-fix subsection below). Evidence for this working
tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed; debug APK rebuilt after the crash-fix cleanup (`app/build/outputs/apk/debug/app-debug.apk`, 2026-11-05 20:40) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 979 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; the androidTest source set compiles again after the stale `radarControls` parameter, and `OfflineTrainingBattleScreenTest` asserts the full-bleed invariants |
| `:app:lint` | 0 errors, 468 warnings, 38 hints (25 baseline entries are now stale/no longer found; warnings never gate) |
| Radar battle on device (crash fix) | Two consecutive Radar encounters on the SM-A346M ran the countdown → full fight → "Radar victory" (49 s and a shorter win), with one Filament engine creation per battle, the battle scene composed once, an empty `logcat -b crash` buffer and the app process alive throughout. The earlier instrumented build reproduced the native crash every time at the countdown (~3–5 s); hoisting the scene is the structural difference. |
| Arena battle on device | The earlier pass verified the full-bleed arena live on the same device (scene fill, enemy plate, HP/MP/ESP strip, projectiles); the crash fix does not touch that layout. |
| Native captures (this tree) | `.impeccable/review/battle-live-radar-fixed.png` (Radar victory state before the settled-chrome trim) and `.impeccable/review/battle-radar-second.png`. Portrait captures of the final shell are still pending. |

### Radar battle crash fix (2026-11-05, same tree)

- **Symptom.** Starting a Radar encounter battle crashed natively ~3–5 s in, at the countdown, with
  `SIGSEGV` in `libfilament-jni.so` (fault address `0x0`). Arena/training battles never crashed.
- **Diagnosis.** Temporary lifecycle logging showed the battle scene subtree being disposed and
  re-composed mid-battle and a second `scene engine created` inside one fight: Compose was replacing
  the `AndroidView` that owns the Filament engine. The native crash is the engine's component managers
  being destroyed/recreated out of order (use-after-free), not a bad asset or a decal precondition.
- **Fix.** The battle `OfflineBattleScene` was hoisted out of the radar/battle layout branch to the root
  of `WorldRadarBattleContent` (`OfflineTrainingBattleScreen.kt`), so it is composed exactly once per
  encounter; renderer-ownership changes only pause its frame loop. The settled-battle chrome trim
  (result panel owns the surface, live top cluster / command band / projected HUD step aside) landed in
  the same change; see `DESIGN.md` **Battle screen** → States.
- **Cleanup and re-verification.** The diagnostic logs, engine stack traces, `debugTag` parameter,
  deferred engine creation and the 250 ms handoff delay were removed before the final rebuild. The
  device pass above ran on the instrumented build; the cleaned APK (20:40) is
  compile/unit-test/lint verified, and a final on-device pass on that exact APK is pending because the
  SM-A346M's wireless ADB dropped and could not be reconnected in this session.

Boundaries and remaining acceptance work:

- The AR stage deliberately keeps the established square scanning/battle layout and its transparency
  contract; it is the legacy exception, not part of the new shell. The Blast overlay it shares was
  dimmed for legibility.
- Arena lobby/briefing/team setup, the Battle hub, legacy online PvP and the Radar encounter sheet are
  untouched in this pass.
- The finisher movie/camera sequence is kept as authored; only its captions and surrounding chrome were
  re-fitted.
- No TalkBack or large-font (1.3×) capture is recorded yet; the new chrome keeps 48dp targets and the
  existing accessibility actions.

## Radar place redesign (2026-11-04, uncommitted tree)

This pass replaced the Radar place's 2D/first-person split with a full-bleed isometric radar world and
fixed the encounter-asset starvation that made nearby Digimon slow or absent. Evidence for this
working tree only:

| Check | Result |
|---|---|
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 984 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleDebug` | Passed; installed on Samsung SM-A346M (wireless ADB), API 35 |
| Radar flow on device | Home → Radar opens the isometric plate with the partner token, wild cardboard sprites and the digivice-bezel compass (tick ring, cardinal beacons, nearest-Digimon needle; no distance rings); zoom, vertical tilt, labels, options sheet and the Debug-only legacy views exercised |
| Encounter loading | Spawn assets appear immediately after the remembered Room flow emits (empty-list restarts removed); shared GLB cache + parallel builders + nearest-first uploads cover a 16–20 Digimon roster; expired-region recovery repopulates sprite rows on entry and stale placeholder encounters are removed |
| Screen captures (this tree) | `.impeccable/review/radar-18-final100.png`, `radar-19-spread.png`, `radar-17-wide.png`, `radar-20-options.png`, `radar-21-labels.png` (light palette); `radar-11-ready.png` (dark palette). The label-collision pass that followed these captures is compile/unit-test verified only. |
| `:app:lint` | 0 errors, 471 warnings, 38 hints (32 warnings and 5 hints filtered by `lint-baseline.xml`). A first lint run crashed inside the K2 UAST resolver before reporting; the clean rerun above supersedes it. |

Boundaries and remaining acceptance work:

- The battle handoff and AR stage were not re-exercised on device in this pass. Their code paths are
  unchanged except for the `WorldRadarBattleContent` full-bleed radar branch, which keeps the
  established battle/AR layout.
- The legacy 2D and first-person renderers are untouched and remain reachable only with the master
  Debug switch on (Radar options → Legacy view). A native capture of the legacy 2D view is pending.
- No TalkBack or large-font (1.3×) capture of the new radar was made in this pass; the chrome keeps
  48dp targets and the existing semantics contract.
- Sprite settle time was measured during development (first sprite ~1 s, a 20-Digimon roster ~3.7 s
  on a cold process, including plate load and GLB build) with temporary logging that has been
  removed; no benchmark entry was recorded.
- **Wild↔wild social pause (2026-11-04).** `WildSocialInteractionGate` is off, so autonomous wild
  pair chat/battles are not created, activated or shown; the retained planner/dialogue/combat code
  stays compiled. Covered by `WildSocialInteractionGateTest` (family matching, player encounters
  unaffected, re-enable path). Wild → player and player-directed encounters were not behaviourally
  re-exercised on device after this change; the gate keeps them out of scope by construction.
- **Radar cardboard orientation (2026-11-04).** Travel is derived from real-world positions and the
  display orientation from a pure helper (`radarCardboardOrientation`), so compass/zoom motion cannot
  flip sprites. Covered by three JVM tests: standing Digimon ignore compass motion, the four travel
  directions match the farm' camera-assisted profile/mirror behaviour, and a turning compass rotates
  the sprite with the plate without chatter at the mirror latch.

## Current verified state (2026-10-31, uncommitted tree)

| Check | Result |
|---|---|
| JVM unit tests (all modules) | 1,157 tests, 0 failures, 0 errors, 7 skipped |
| Debug + R8 release assembly | Passed (minified APK 149,019,234 bytes / 142.12 MiB, regenerated profiles packaged) |
| `:app:lint` | 0 errors (422 warnings, 38 hints; warnings never gate) |
| Connected instrumentation (`:app:connectedIntegrityCheckAndroidTest`) | 281/281 passed on Samsung SM-A346M, API 35 |
| Install + cold launch smoke | Debug and debug-signed R8 release installed and launched; no crash-buffer entries |
| Coverage (named classes) | 81.0–95.8% line coverage; see [BASELINE.md](docs/performance/BASELINE.md) |
| Benchmarks (device-gated) | Cold-start TTID median 875 ms; battle frame CPU P95 13.3 ms; farm 3-second window P95 29.8 ms (includes loading) |

These are current for this working tree only. Do not quote older suite counts from the
archive or [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md) as current.

## Commands (Windows PowerShell, repository root)

```powershell
# JVM/library module suites
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test `
    :hardware-dim:test :hardware:testDebugUnitTest

# App JVM unit tests (integrityCheck test build type)
.\gradlew.bat :app:testIntegrityCheckUnitTest

# Static analysis (blocking gate; must stay at zero errors)
.\gradlew.bat :app:lint

# Builds
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease        # R8; mapping at app/build/outputs/mapping/release/

# Device instrumentation (device required)
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest

# Baseline-profile regeneration (device required; startup + best-effort journeys)
.\gradlew.bat :app:generateBaselineProfile

# Macrobenchmarks (device required; not part of CI). These live in :benchmark, a separate
# test module, so :app:generateBaselineProfile only runs the profile generator.
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.StartupBenchmark"
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.FrameTimingBenchmark"

# Optional optimization instrumentation (no behaviour change by default)
.\gradlew.bat :app:assembleRelease -PcomposeCompilerMetrics   # Compose compiler reports
.\gradlew.bat :app:assembleRelease -PabiSplits                 # per-ABI APKs for size checks
.\gradlew.bat :app:assembleRelease -PbattleSceneChannel=true   # battle channel A/B build

# Coverage (opt-in; no threshold is enforced yet)
.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport
```

The device-visible tasks require an authorized ADB device. On this machine the SDK is
`%LOCALAPPDATA%\Android\Sdk` and ADB is not on `PATH`; wireless debugging can drop
during long builds — verify with `adb devices` before a device task and reconnect with
`adb connect`/`adb mdns services` if it disappeared.

## Suite layout

| Area | Location | Notes |
|---|---|---|
| Save parsing/conversion | `game-save-core/src/test` | pure JVM; golden/characterization tests for DS record codecs |
| DIM/BEM card parsing | `hardware-dim/src/test` | pure JVM; synthetic R5G6B5/checksum fixtures only |
| NFC protocol | `hardware/src/test` | JVM with a fake `NfcTransport` |
| Companion protocol | `companion-protocol/src/test` | pure JVM JSON protocol |
| PC companion | `pc-save-companion/src/test` | pure JVM |
| App JVM | `app/src/test/java/com/tamerslab` | use cases, Koin graph, architecture boundaries, state/navigation contracts, battle/transfer/save logic |
| Device/instrumentation | `app/src/androidTest/java/com/tamerslab` | Room persistence + migration checks, Compose UI smoke tests, battle/radar/transfer/quest flows |

Architecture boundaries are enforced by
`app/src/test/java/com/tamerslab/architecture/ArchitectureBoundaryTest.kt` (domain APIs
must stay Android/Room-free, library modules must not import the app shell, repository
interfaces must not import Room). Koin graph verification is in
`app/src/test/java/com/tamerslab/di/KoinModuleGraphTest.kt`.

## Fixtures and private data

- Never commit real game saves, ROMs, retail DIM/BEM card dumps, pairing links, tokens,
  `secrets.pb`, keystores, or mapping files with private symbols.
- Use synthetic fixtures. Licensed local card fixtures are read only through the
  `VBHELPER_TEST_DIM` / `VBHELPER_TEST_BEM` environment variables; tests skip when they
  are absent.
- The archived regression log documents the provenance of earlier fixtures; the policy
  itself is restated in [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

## Performance and benchmark tests

Performance is a separate manual gate: budgets, procedures, and recorded
measurements/status are in [docs/performance/BASELINE.md](docs/performance/BASELINE.md).
The `baselineprofile` module contains the device-gated baseline-profile generator and
the macrobenchmark harness; generation is not a unit test and must not run in CI without
a device. The battle channel optimization
(`BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED`) stays disabled until a measured Perfetto
run justifies it.

## CI gates

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs module unit tests,
`:app:assembleDebug`, `:app:assembleRelease`, and `:app:lint` as required checks.
`:app:jacocoTestReport` is advisory and no coverage threshold exists yet. CI has no
Android device; instrumentation and benchmark runs are a documented manual gate.

## Adding tests

- Every behavior change/refactor lands with its own tests; do not rely on the historical
  log as a regression suite.
- Characterization/golden values are behavior contracts: change one only for an
  intentional behavior change and explain why in the PR.
- Keep JVM tests deterministic (inject clocks, dispatchers, seeds).
- Room schema changes need a version bump, a `MigrationTestHelper` test, and a new JSON
  under `app/schemas/com.tamerslab.data.database.AppDatabase/`; never use destructive
  migrations.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for PR expectations and device-gated checks.

## DigiLine / negotiation UX acceptance (manual, device-gated)

No device or emulator run is recorded for this increment; compilation and the JVM suites
are green, which is not native approval. Walk this list on a device before shipping:

- **Owned DigiLine chat:** the question/quiz scene card announces its heading and a polite
  live region; answer reactions show a Continue card before the transcript updates.
- **Wild contact (joined):** persuasion scenes show stage and tier; the trust bar and old
  LLM messages are absent unless the master Debug switch and the legacy LLM switch are on.
- **Radar overworld negotiation:** the camera eases onto the target, the compass heading
  freezes and the Digimon faces the player; the card shows entry/resume context, phase,
  tier, demand and the last spoken line above the current node. **Leave for now** returns
  to the world and reopens with the resume line; system Back leaves the conversation
  before it leaves the Radar destination.
- **Legacy isolation:** with Debug + Legacy LLM chat off, no legacy transcript row, trust
  reading or DigiLine preview remains; switching both on restores them for comparison.
- **TalkBack:** traverse header → card body → options → leave; scene changes announce
  once without stealing focus, and no duplicate reading comes from the quote line.
- **Large font (1.3× and 2×):** the scene card scrolls internally, options keep 48dp
  targets, and the Digimon stays visible above the card.
- **Landscape:** the card stays inside the window and does not cover the conversation
  subject.

