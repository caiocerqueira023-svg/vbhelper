# Digimon World DS: phone reading and structural research

## Active DS-first native-move work (2026-10-17)

The user paused Time Stranger transfer development and selected actual DS moves as
the next goal, starting with the first Story / World DS adapter already present.
The initial native scope is party removal into a durably retained vault record and
return of that exact native individual. Farm transfer stays excluded. Native records,
history, skills, equipment, stats and all eight EXP channels must survive intact;
source removal must not be replaced by a snapshot copy with a Move label.

Requested reference:
[joaomlsantos/DigimonNDSRomEditor](https://github.com/joaomlsantos/DigimonNDSRomEditor)
at **`cc3ff499edfd86e382e724d1da7f155b6b830e5f`**, GPL-3.0. Its `digimon_core/rom.py`
and `constants.py` identify USA Dawn/Dusk ROMs; `model.py`, `loaders.py` and `strings.py`
provide their species/stat/move/evolution/text format baseline and byte-round-trip
approach. It edits ROM data rather than persisted individual saves: these offsets
must not be used as World DS save offsets. No upstream source or game assets are
vendored. The existing pinned World DS decompilation remains the evidence for its
party table, local-ID allocator and 384-byte individual structure.

The supplied World DS fixture references only one party member in its party table (a
second live record sits outside the party). The last-usable-member guard therefore
keeps its move blocked by design; a playable removal needs a save with at least two
party-referenced members. Checksum/sequence acceptance remains a writer dependency;
dual-copy agreement (below) is verified structurally, not cryptographically.

### Structural party removal/return core

`WorldDsPartyEdits.removeForResearch` now builds an **in-memory structural candidate**:
it retains all 384 original native bytes, clears that record slot, compacts the ten
party references and re-parses to ensure that all surviving individuals are unchanged
apart from their party positions. Removals accept two shapes: an ordinary party member
(party compacted) and an **unreferenced storage record** — a live record outside party
and farm, flagged only `UNASSIGNED_LOCATION`, whose slot is cleared without touching any
party table; in both shapes one usable Digimon must remain in the authoritative copy. Raw
dual-block reads carry `AMBIGUOUS_SAVE_BLOCK` on every record, but that describes the
copy layout, never the record's content: ambiguity does not block a move, and the
writer's own byte-consistency verification is the guarantee. Live ownership flags at
+0x108 — guest (2), lent (4), locked (8), egg (0x10), cross-DS (0x20) — farm residents,
duplicate party references and item/special-selection references are refused; every
other unk108 bit, including the unmodeled 0x40 observed on an ordinary stored Digimon,
travels verbatim. Every byte outside the selected slot and party table, including the
opaque header and padding, stays identical.
Multi-copy saves use the **newest valid copy (or an explicitly reviewed copy) as
authority**. Removals clear the identity's own slot and party reference in every copy
where it exists; the last-usable-member guard is evaluated only against the
authoritative copy. Older copies never block a move because their bytes, container or
membership differ; a copy that no longer contains the identity is left alone except
for clearing a dangling party reference. Admissions add the identity to the
authoritative copy's visible party and update compatible older copies best-effort;
stale copies that lack the record or have no safe free slot are left untouched, apart
from removing a dangling party reference. `restoreResearchRemoval`/`restoreResearchInsertion`
accept only that exact candidate revision and must reconstruct the original file
byte-for-byte; they cannot be applied to later game progress. `insertRetainedRecord`
still writes the exact retained bytes into a free slot in every populated copy for
returns, and each copy's own 0x208 high-water mark is raised only when needed.

The two populated card copies observed in the retail fixture (sha256 prefix
95e15d20d7bd1131) agree byte-for-byte across both 384-byte record windows, the party
tables and the 0x208 id high-water mark; they differ only in ~291 bytes outside them
(headers and counters; the copy sequence at +0x18 reads 10 vs 11 in the current
fixture and is never touched). The app treats the unique newest valid counter as the
copy the game intends to load. A move therefore uses that copy's record even when the
older copy differs or lacks it; if the newest copy lacks the Digimon, the move is
blocked as “only in an older copy” rather than guessing. The legacy all-copy equality
check remains the fallback only when no unique newest/reviewed copy can be determined.
The decompilation's `runSaveWriter`/card-rotation body is not in the partial checkout,
so the load policy stays unproven.

This is not general insertion into another save or a playable-save writer. The header
is deliberately not guessed/rebuilt. Candidates are written only by the user-requested
experimental move/send repositories, which also handle the review-selected copy (the
raw image still flags its blocks ambiguous); production insert/remove capabilities
remain false. Tests cover middle/edge removal and exact return in either and in both
copies, storage-record removal (slot-only, both parties untouched, last-usable-Digimon
refusal), unrelated-byte preservation, last-usable-member refusal, stale revisions,
divergence refusals, farm/guest/duplicate references and native item/special-selection
references requiring review.

## Schema 53: provisional party acquisition and shared Home

The observed raw profile now permits **provisional snapshot copies of eligible party
records** into Storage. It is not a move, writer or verified global native-identity
policy. Exact records and captured original documents are retained; receipts prevent
replaying the same acquisition. World DS local IDs are matched within the logical
linked source/document, not globally across unrelated saves. Same-source later records
require explicit identity-continuity review before refresh.

Multiple populated copies can be reviewed in Transfer. The selected copy/revision is
persisted on the linked source and reapplied only to that exact file revision. Without
a manual choice, the unique newest valid copy is selected automatically and the older
copies are marked ambiguous; this uses the same boot-verified maximum valid `+0x18`
sequence rule as automatic app reconciliation. A manual review overrides that choice
for the exact revision. Changing a choice invalidates affected previews; a changed
source revision requires renewed review.

The native archive stores **NULL native personality**. A deterministic app-only trait
is initialized once from the permanent app UUID, retained on refresh/materialization
and clearly labelled separately from native data. The ordinary reviewed canonical
mapping/materialization flow now accepts the World DS profile and uses the existing
shared DIM-style Home. Authored origin influence uses the declared native level range
1..99 through the existing versioned bounded policy; native HP/MP, maxLevel (`0x052`,
formerly called aptitude) and the eight EXP counters remain untouched. This is not native progress-equivalence proof.

Schema 52→53 adds revision-bound copy selection and makes native-personality metadata
nullable while preserving native rows, child records, lifetime triggers and autoincrement
sequence. `PreservingMigrationFactory` disables FK cascades before the rebuild and
restores enforcement on open; the SQLite preservation check exercises the upgrade.

Current flow: **Transfer → link/refresh → review copy if needed → select eligible
party records → import snapshot → Storage → Game archives → review species/form →
create APP representation → choose partner → shared Home**.

The read-only-profile description below records the preceding schema-52 milestone;
its acquisition-disabled statements are superseded by this provisional copy policy.

## Current phone support: game-grounded USA raw profile

`WorldDsSaveReader` is registered in the normal shared reader registry. Android
Transfer can link and refresh the user's regular **65536-byte `.sav`** directly;
folder discovery accepts arbitrary `.sav` filenames by size family, and any name
at all for known container sizes. No PC companion is required for phone-local
reading.

The user supplied a newly created USA save played on DraStic. Read-only inspection
found one populated save copy at the start of the 64 KiB image, no second copy (the
remainder of the image erased), and one party record.
The first 16 header bytes are opaque and the next 16 are zero. The record layout,
local-ID counter/references, native level/maxLevel/stats and eight EXP counters are
structurally consistent with the published USA structures. Private bytes, names and
native local IDs are not shipped in fixtures or aggregate reports.

Two successive recognition gates were shaped by single snapshots and both rejected
real saves; both are gone:

- The first demanded the observed opaque header with zero bytes 16..32. The matching
  decompilation shows the game itself writes `DSDIGI` at `save+0x10` on new-game
  initialization (`writeSaveSignature` in `src/engine/save/save_data.c`); neither real
  save in hand carries it (both are zero there), so header shape was never a sound
  rule.
- The second capped `lastId` below 0x100000. `takeNextId` hands out one monotonic id
  sequence starting at a per-save random base (0xACA7BA00 and 0x37720000 observed,
  both 256-aligned) with the handout count in the low bytes, crossing byte boundaries
  as it grows. The cap rejected the user's progressing DraStic save (lastId
  0xACA7BA4F) even though that file's copies are internally consistent. A
  gameplay-magnitude gate must never reject an otherwise self-consistent save.

Identification is now grounded in the game's own cross-references:

- The written `DSDIGI\0\0` signature at +0x10, when present, confirms a block once the
  local-ID allocator is sane (1 ≤ lastId < 0xFFFFFF00, the game's own `ID_RESERVED`
  bound in `takeNextId`).
- Without a signature, a block is recognized only when the allocator is sane, at
  least one of the forty record slots carries a live id (0 and -1 are the game's empty
  slots), and at least one piece of the game's own bookkeeping names a live record:
  party slots (**0x20C**), farm residents and each farm's last-visitor reference
  (**0x3E40** stride 0x6C, +0x44 and +0x64), Digimon items (**0x43D8** stride 0x0C,
  +0x04) or the unk443C reference. Unrelated bytes cannot reproduce those
  cross-references (estimated false-positive probability below one in a million). On
  the user's save the party reference, the first farm group and unk4438 all name live
  records; on GameFAQs save 21365 the party and farm references do.
- A block whose every record was released and that carries no signature cannot be
  distinguished from unrelated bytes and is not claimed. Species, level, stat and EXP
  deviations remain per-record review issues and never reject a whole save.

Profile: `world-ds-usa-drastic-raw-v1`, codec 1, game `digimon_world_ds_nds`.
A single populated copy supplies observed roster candidates; with several populated
copies the unique newest valid `+0x18` sequence is selected automatically, subject to
manual review. Ties, missing counters and invalid MD5 headers remain unresolved rather
than guessed.

Native checksums are **not verified**. All World DS acquisition/removal/insertion
capabilities remain false. Farm, guest/lent/foreign/egg, conflicting and unassigned
records are visible with review states, not silently treated as transferable Bank
residents. Party positions follow the party-reference array rather than physical
record order. Every observed record retains its exact 384 native bytes.

The shared observation model now represents absent personality/scalar EXP/talent/bond
as null and retains World DS native parameters separately. Android shows native HP/MP,
attack/defense/spirit/speed, maxLevel and the eight EXP channels in original order.
Existing Time Stranger JSON remains compatible. No database schema migration is needed.

### USA name-code evidence

Published text-code facts were checked against RigleGit's SDB2TXT at
**`29218eff19dbe39b07279cd5af5b02f79d3bc92d`** (CC0-1.0),
[sdb2txt.c](https://github.com/RigleGit/SDB2TXT/blob/29218eff19dbe39b07279cd5af5b02f79d3bc92d/sdb2txt.c).
Uppercase A–Z maps from 0x50–0x69, lowercase a–z from 0x6A–0x83 and digits from
1–10; supported punctuation uses explicit mapped codes. These are game glyph codes,
not UTF-16. Unmapped codes render as replacement characters with an explicit review
issue while the exact original bytes remain retained. A stored display name never
maps a species; identification uses the record's species id against the game's own
name tables below.

### USA species name tables (instant identification)

The matching decompilation exposes the game's own species names: `getSpeciesName`
returns entry `species` of the u32 offset table at **data_0211f4e0** and
`getSpeciesShortName` from **data_0211a688** (`src/engine/text/text_banks.c`;
declared in `include/engine/data.h`), and a record's species at **+0x04** indexes
them directly (`struct Digimon`, `loadSpeciesData` id). The user supplied the USA
ROM (SHA1 **225ffc25aaf1ab4006fcdce5896d411a8fee870f**, matching the decompilation's
recorded `dwds_usa.sha1`; game code ADNE). `tools/extract_world_ds_species.py`
verifies that hash and game code, parses both tables with the same glyph codes the
app already decodes (`WorldDsText`), sanity-checks the first and last names, and
writes `app/src/main/assets/world_ds_species.json` with provenance (ROM SHA1,
table addresses, decompilation revision). No ROM bytes and no private save bytes
are shipped. Cross-check against the user's save: the untouched record at species 2
decodes "Poyomon", exactly `names[2]`; the renamed Tanemon at species 4 shows the
display name diverging while the id stays authoritative. The 426 entries include
26 unused empty slots, Digi-Eggs (420–425), pods/environment entries and duplicate
variant names (e.g. seven "Daemon" forms). Of the 400 named entries, 335 resolve
exactly through the shipped canonical catalog; duplicates identify the same species
and everything unresolvable (eggs, pods, spellings the catalog lacks) stays
unidentified and reviewable — identification never reviews, materializes or
overrides a mapping, and a reviewed mapping always wins over the table. Move
auto-materialization records its provenance: an identity taken from the game's table
is stored with authority `GAME_TABLE`, while a user-reviewed identity stays
`USER_CONFIRMED`; the review dialog presents the table-identified species as a
suggestion to confirm or replace.

## Earlier signature-based research boundary

The sections below describe the separate opt-in research inspector. Its signature
assumption did not recognize the supplied DraStic save and is not used by the phone's
observed raw profile above. Historical no-fixture/no-registered-reader statements
apply to that earlier research checkpoint.

## Delivered scope

The desktop inspector accepts raw backup candidates and DeSmuME version-0 `.dsv`
containers. It searches for complete, 4-byte-aligned `DSDIGI` signature candidates
and reports **every** candidate block separately. It does not select the newest or
authoritative block, identify the actual region, validate native checksums, acquire
Digimon, award progress or write saves. It is intentionally not registered in
`SaveReaderRegistry` or automatic Android/PC discovery.

This research path was preparation for the second adapter, not accepted Digimon World DS
roster/import support. No actual game-written World DS fixture has been supplied.
The USA layout facts below must be checked against an authorized persisted save
before that adapter is enabled. Farm transfer is excluded by user choice; farm
references are inspected only to avoid misclassifying farm residents as other records.

## Pinned public evidence

The independent inspector uses published format facts, not vendored editor/emulator
code or game binaries. Nothing from these repositories is executed during inspection.

- ReGame Labs, `dwds_decomp`, revision
  **`91efd17c4c05ce106ce0f973155149d23d9de519`** (MIT, copyright 2026 ReGame Labs):
  - [README: USA ADNE evidence](https://github.com/ReGame-Labs/dwds_decomp/blob/91efd17c4c05ce106ce0f973155149d23d9de519/README.md).
  - [Native Digimon, SaveData and Farm structures](https://github.com/ReGame-Labs/dwds_decomp/blob/91efd17c4c05ce106ce0f973155149d23d9de519/include/engine/data.h).
  - [SAVE_SIZE and save writer buffer](https://github.com/ReGame-Labs/dwds_decomp/blob/91efd17c4c05ce106ce0f973155149d23d9de519/include/engine/save/save_data.h).
  - [DSDIGI signature initialization](https://github.com/ReGame-Labs/dwds_decomp/blob/91efd17c4c05ce106ce0f973155149d23d9de519/src/engine/save/save_data.c).
  - [Signature placement at +0x10](https://github.com/ReGame-Labs/dwds_decomp/blob/91efd17c4c05ce106ce0f973155149d23d9de519/src/engine/save/card_backup.c).
- DeSmuME's published container structure, `desmume/src/mc.cpp` at
  **`06c64b02666a4ac5246aad95fc8e9ab9e69e5a53`**:
  [format reference](https://github.com/TASEmulators/desmume/blob/06c64b02666a4ac5246aad95fc8e9ab9e69e5a53/desmume/src/mc.cpp).
  Its source is GPL-2.0-or-later; only container-format facts were used in an independent
  parser. No emulator code, executable or library is included.
- albertowd `dsv-tool` at **`b669a8448f6ec192c0bf17c5268a8fa6aff6f5d3`** (MIT):
  [footer metadata indexes and 24+16 trailer length](https://github.com/albertowd/dsv-tool/blob/b669a8448f6ec192c0bf17c5268a8fa6aff6f5d3/app/plugins/dsv-tools.client.ts).
  Its `|-DESMUME SAVE-|` cookie, `DSV_FOOTER_LENGTH = 24 + 16` and padded-size
  behavior establish that DeSmuME and DraStic share one trailer format; only
  container-format facts were reimplemented independently.

## Structural interpretation

- Candidate save block length: **0x7DC0** (`SAVE_SIZE` in the matching
  decompilation: `include/engine/save/save_data.h`). The game's own signature is
  eight bytes `DSDIGI\0\0` at block-relative **0x10**, written by
  `writeSaveSignature` (`src/engine/save/save_data.c`). A signature is evidence for
  a candidate only; its resemblance to the USA structure does not prove title/region
  or a valid save.
- The game keeps its two copies back-to-back at the card's page-rounded stride:
  `allocSaveBuffer` (`src/menus/digiconvert/digiconvert_open.c`) allocates `SAVE_SIZE`
  rounded up to the card's page size — **0x7E00** here (`0x7DC0` rounded up to the
  `0x100` page). A publicly available converted save (GameFAQs save 21365) confirms
  placement: the second copy's money, food, playtime, last-id, party array and record
  table all land exactly at **+0x7E00** rather than +0x8000. The reader probes exactly
  `0` and `0x7E00` (`WorldDsSaveReader.COPY_STRIDE`) and never guesses which copy is newer.
- Local-ID counter at **0x208** (`lastId`): the last ID `takeNextId` handed out
  (`src/engine/ui/gauge.c`). IDs are never 0, never -1 and never at or above
  `ID_RESERVED` 0xFFFFFF00; the counter wraps only after ~4.29 billion handouts.
  The sequence starts at a per-save random base with the handout count in the low
  bytes (0xACA7BA00 and 0x37720000 observed): absolute magnitudes differ per save
  and are not a plausibility signal.
- Party reference array: ten 32-bit local record IDs at **0x20C**, zero for absent. Only slots
  **0..7** are visible/ordinary: the battle party touch layout marks the last two entries
  `/* the party slots, the last two unused */` (`data_ov002_0225a710`), `func_020a10d4` searches
  only 0..7, and `func_020a1188` reads reserves 5..7. App insertion/admission therefore caps at
  eight and never writes slots 8/9.
- Forty individual records start at **0x234**, stride **0x180** (`struct Digimon`
  in `include/engine/data.h` ends exactly at 0x180; `addDigimon` zeroes the whole
  stride). Local ID at +0x00 (0 or -1 = empty), native species at +0x04.
  `SPECIES_COUNT` is 426, so species 0..425 is the game's own bound.
- Name storage is nine 16-bit code units at **+0x08**, terminated by **0xFFFF**.
  Codes remain codes: no UTF-16/Unicode conversion or species-name inference.
- Native HP/max HP: +0x3C/+0x40 (signed 32-bit); MP/max MP: +0x44/+0x46
  (signed 16-bit); attack/defense/spirit/speed: +0x48/+0x4A/+0x4C/+0x4E.
- Aptitude/max level: **+0x52**; native level: **+0x74** (signed 16-bit).
- Eight separate signed 32-bit EXP counters start at **+0x78**. They are not summed,
  normalized into Time Stranger EXP or treated as a semantic progress ledger.
- History starts at **+0xA0**: flags at +0x00, five stage-indexed signed 32-bit forms at
  +0x04, stage at +0x4E, +0x4F unknown, +0x50 a u16 copied from `/dat/dm` +0x16, and seven
  signed 16-bit resistances at +0x52 copied from dm +0x18/+0x1A/...+0x24. `func_020a2894`
  sets +0x00 from the stage (`0x1C` baby, `0x18` rookie, `0x10` champion, otherwise 0) and
  walks `/dat/sk` +0x54 previous-species links backward to fill earlier forms. `/dat/dm` is
  the species master (0x3C-byte records, eight per file); `/dat/sk` lineage records are
  0x58 bytes with eight per file, and a species id is its slot number, not a record field.
- Flags at **+0x108** preserve guest (2), lent (4), locked (8), egg (0x10) and
  other-DS-record (0x20) evidence. None authorizes ownership or ordinary acquisition;
  moves and returns refuse exactly these five bits and carry every other bit, including
  the unmodeled 0x40 seen on an ordinary stored Digimon, verbatim.
- The separate game-maintained flags word at **+0x104** (e.g. `createFarm` sets 0x10 on
  every kept Digimon, farm membership is bit 2, `DIGIMON_FLAGS_CLEARED` 0x1780 is cleared
  daily) belongs to the game's own bookkeeping and is never written by these edits.
- Four farm structures at **0x3E40**, stride **0x6C**. Nonzero farm IDs have eight
  resident ID references at +0x44. Conflicting/multiple references, duplicate local
  IDs and dangling references are reported; unreferenced individuals are **unassigned**,
  never automatically labelled Bank/owned. No cross-block or cross-save merge occurs.
- Every nonempty candidate record retains its exact 384 bytes as Base64 in the
  in-memory research result, including unmapped history, ancestry, skills and flags.
  The CLI prints counts only. This is not an individual-export/native-return guarantee.

## Container and input boundary

The research input limit is 8 MiB plus a DeSmuME footer, independently of the normal
Time Stranger registry limit. There may be at most 32 complete candidate blocks.
The shared bounded stream reader clears its working buffers; explicit byte-array
inspection does not modify caller-owned bytes.

The normal reader accepts the canonical 64 KiB image (the two card copies at 0 and
**0x7E00**), the bare **0x7DC0** save region the game writes per copy, and the same
content behind a small wrapper (export headers, emulator structures) up to a bounded
0x400-byte slack with a 4-byte-step aligned scan — no particular header length is
assumed and content still decides. Anything else is a container failure.

**Unified emulator footer (DeSmuME and DraStic):** both append the same 40-byte
trailer — 24 metadata bytes and the 16-byte `|-DESMUME SAVE-|` cookie (per
albertowd/dsv-tool's `DSV_FOOTER_LENGTH = 24 + 16` and metadata indexes). The
metadata's padded-size field (offset +4) is where the payload ends for both
emulators; DeSmuME's 82-byte snip notice is cosmetic text between payload and
footer. A cookie-bearing file must validate padded size ≤ payload end, version 0
and written ≤ padded else it is a recognized-but-malformed container
(INVALID_CONTAINER), never a silent raw fallback. A notice without a cookie is also
treated as a malformed DSV. The exact original-container revision is separate from
the unwrapped payload revision; padding and unknown payload bytes are not trimmed
or rewritten.

no$GBA wrappers/compression are unsupported and rejected. Export raw persisted backup
data first. Save states, ROMs, DUC/DSS exports, other DSV versions and other game
regions are not accepted title-specific formats by this research implementation.

## Desktop use

From the extracted companion distribution:

```powershell
.\bin\pc-save-companion.bat --inspect-world-ds "C:\path\to\Digimon World DS.sav"
.\bin\pc-save-companion.bat --inspect-world-ds "C:\path\to\Digimon World DS.dsv"
```

This standalone mode requires no phone, pairing, server, authorized-folder scan or
private companion state. It exits after printing aggregate candidate-block counts,
location-reference counts and unresolved-identity/region/writer status. It cannot be
combined with network/capture options. The core inspector supports the same flag.

## Remaining format evidence

The supplied USA/DraStic raw save establishes the current read-only profile above.
Checksum/sequence authority, other regions/containers and broader populated rosters
still need their own evidence. Identity lineage, acquisition and writers remain
separate from structural reading. Additional sample collection is not a prerequisite
for using the supported phone-local raw-file reader.

## Copy integrity header: cracked and resealed by writers (2026-10-19)

Each populated card copy carries a 16-byte integrity header at its start
(`copy+0x00..0x10`), followed by eight zero bytes and the copy's sequence counter
at `+0x18`. The guard is **MD5 over the copy's 0x7DC0 bytes with the header range
itself zeroed**; the loader rejects a copy whose header lags its data. Evidence:

- Zeroing only the 16 header bytes of a working save booted to "there is no save
  data" (`test6_header_zeroed.sav`).
- Removing a record slot without touching the header reproduced the same failure
  (`test2_slot_zeroed.sav`), as did pasting a game-native empty record into the
  slot with a stale header (`test3_game_style_empty.sav`).
- The identical record removal with the recomputed MD5 header loaded the save and
  the removal was visible in game (`test7_fixed_header.sav`); the recomputation
  also reproduces both copies' headers of the pristine sample byte-exactly.
- A failed boot ends with drastic erasing the card to all-0xFF automatically
  (no user prompt), which is why the first crash produced an empty file.

`GetCRC16` in the ARM9 (0x02000796) is a dead SDK stub with no callers and is not
the mechanism. `writeSaveSignature` writes the player name at `+0x10` of the save
region, not the header. The game's writer recomputes nothing: it carries the
header inside the save region and only bumps the sequence counter.

`WorldDsPartyEdits` reseals every populated copy it edits and restores the exact
source header bytes on undo, so the game accepts both edits and returns. The
boot-verified recompute makes the header a solved field; sequence-vs-copy
selection (loader picks the maximum valid sequence) matches all three observed
saves (sequences 2+3 pristine, 4 + erased second copy mid-play).

The app now applies that same rule when a linked save refreshes: `WorldDsCopySelection`
reads each populated copy's counter at `+0x18`, ignores a copy whose MD5 header does
not match its own bytes, and yields a copy only when one valid copy has the unique
maximum sequence. `AutomaticSaveReconciler` refreshes only already-bound native
identities from that copy, inserts a new snapshot when the record changed, and then
applies the returned species/progress to the app card. Unbound records still require
the reviewed import flow; a tie, missing counter or invalid header never triggers an
automatic guess.
