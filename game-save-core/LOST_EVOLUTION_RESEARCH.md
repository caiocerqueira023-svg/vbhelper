# Digimon Story: Lost Evolution — reverse-engineering and transfer notes

Everything below was recovered from the user's own ROM (`Digimon Story - Lost Evolution (Japan)
[T-En by Operation Decoded v1.1] [AP fix].nds`, BLEJ, MD5 `9c3a0795d4b7037eabb5fa522ef43e89`,
60,598,900 bytes) and their own save (64 KiB, `B57D0BCFA2B90C4E11255D52F3F04EEF`). Nothing from the
game image is committed; only tooling and this document.

Status: **core reader and editor implemented and validated on the user's real save; app wiring
pending.** `LostEvolution.kt` (layout/profile/codecs), `LostEvolutionSaveEdits.kt`
(`LostEvolutionSaveReader`, `LostEvolutionSaveEdits`, `LostEvolutionSaveEditor`) are registered in
`NdsStoryProfiles` / `NdsStorySaveEditors` / `SaveReaderRegistry`. Core tests: 9 in
`LostEvolutionSaveTest` (synthetic save: read, newest-copy selection, removal with reference
cleanup and byte-exact restore, generated insert with identity allocation and placement, farm
fallback when the party is full, replace, last-individual refusal, admission, tag refusal; plus a
real-save validation gated by `LE_SAVE_DIR`).

Real-save validation output (newest copy, user's own file):
`77:Ａｎｄｒｅ:lv4:PARTY:168; 82:Gabumon:lv2:FARM:134; 90:ToyAgumon:lv1:FARM:144;
80:Dorumon:lv2:PARTY:141; 101:Falcomon:lv2:PARTY:136` — party/farm membership matches the identity
references found by byte search, and levels/stats match the species parameter blocks scaled by the
engine's growth code. This cross-validates the record layout independently of the byte-diff used to
find it.

## 1. ROM image

| Part | Stored | In RAM | Notes |
|---|---|---|---|
| ARM9 | 874,868 B | 1,427,128 B | first 0x4000 kept verbatim (boot + decompressor) |
| ARM7 | 156,528 B | 156,528 B | not needed |
| overlay 0 | 97,024 B | 163,104 B | RAM 0x0223BC00 |
| overlay 1 | 97,720 B | 155,200 B | RAM 0x0223BC00 (bank with 0/2) |
| overlay 2 | 88,020 B | 146,176 B | RAM 0x0223BC00 |
| overlay 3 | 41,352 B | 71,872 B | RAM 0x02264100 |
| overlay 4 | 4,740 B | 7,584 B | RAM 0x02264100 |
| overlay 5 | 88 B | 87 B decoded (table says 0x60) | RAM 0x02264100, tiny stub |

NitroFS: 32 files (FAT/FNT). Data lives in `BPLAYER.BIN` (277,824 B), `BPDATA.BIN` (29,348 B),
`P2D_DATA.PAK`, `SPR_*/BG_*`, and `MSG/MESPAK0[0-5].PAK` (text). The ARM9 is **compressed**, and all
overlays are compressed with the same custom scheme.

### Custom compression (verified byte-exact)

`analysis/dsle/tools/decompress_le.py` ports the in-ROM routine at ARM9 `0x02000950` and matches a
public reference vector exactly. Layout: data block + 8-byte trailer
`{u32 (input_end_skip << 24) | input_start_offset, i32 output_delta}`; input is consumed backwards,
output written backwards from `EOF + delta`, control byte MSB-first, literal or back-reference with
`offset = ((a << 8 | b) & 0xFFF) + 2`, `length = (a >> 4) + 3`. ARM9 output = first 0x4000 bytes +
decoded body. `prepare_le.py` regenerates decompressed images + ELF wrappers for Ghidra.

## 2. Save container (verified against the real save)

64 KiB EEPROM image, **two 0x8000-byte slots**:

| Field | Offset (per slot) | Value |
|---|---|---|
| MD5 header | +0x00 (16 B) | `md5(16 zero bytes + slot[0x10..0x8000))` — verified for both slots |
| signature | +0x10 | `"DM3_000"` + NUL (8 B) |
| sequence | +0x18 | u32; real save: slot0 = 8, slot1 = 9 (slot1 is newest) |
| constant tail of header | +0x1C.. | identical in both slots: 0, 0x2717, 0, 0, 5, 0x2FF1F, 0x3292BA03 |

The real save's two slots contain the same body except the sequence byte.

### Save manager (ARM9)

- Load state machine: `FUN_0209c3fc` (7 states, dispatch table of branches at `0x0209c418`).
- Save state machine: `FUN_0209c670` (11 states, dispatch at `0x0209c68c`).
- Save context pointer from literal `0x0209c818` / `0x0209c664`; fields observed:
  `+0x18` card read/write callback (called with `blx`), `+0x1c` save buffer pointer,
  `+0x20` slot size, `+0x28` MD5 length, `+0x34`/`+0xc0`/`+0xc4` slot address math,
  `+0x38` sequence/retry slot value, `+0x50` state, `+0x54` next state, `+0x00` result (-1 error).
- Saving zeroes `buffer[0..0x10)`, bumps the sequence (`[buffer+0x18]`), then MD5 over
  `buffer[0..length)` and writes the buffer to the card at the computed address.
- MD5 routines: init `0x02019c18`, update `0x02019c5c`, final `0x02019d24`.
- Section framework: signature tag descriptor at RAM `0x02153050`; callback `FUN_0209df58`
  (`0x0215305C`); handler pointer array from `0x02153074`; a schema/metadata table occupies
  `0x02152E00..0x0215304C`.

### Save buffer layout = 0x20-byte header + RAM player state

The roster management code in the game copies records with
`memcpy(dest = section + 0x18 + index * 0x78, src = getRecord(index), 0x78)` and the record
accessor resolves to `globalPlayerState + 0x1830 + index * 0x78` (via the indirect container
accessor `DAT_020ade80/020ade84`, called with `(base, index)`). The same array sits at save
offset `0x1850`, i.e. **save offset = 0x20 + RAM offset** for the player state. The header is
therefore `[MD5 16][signature 8][sequence 4][pad 4]` and the serialized player state starts at
save offset 0x20. Roster array: RAM 0x1830, 40 records × 0x78; the next system starts at the
player-state offset 0x2AF0 (= 0x1830 + 40*0x78).

Roster management API (ARM9, all reachable from the overlay code):
`FUN_020acd34` (reset/init player state), `FUN_020acff0`, `FUN_020ad01c`, `FUN_020ad0dc(fieldIndex,...)`
(expanded field getter used by the save-section callbacks), `FUN_020ade64(index)` (record accessor
through `DAT_020ade80/020ade84`), `FUN_020aff40` (roster init), `FUN_020b1720` (next system init),
`FUN_020ac7d8(species)` (species data lookup, returns `table + species*8`).
Overlay 000 `FUN_0223cf64` converts between the RAM roster and the save-side array and reads
record fields `+0x0C` (species) and `+0x24` (flags, tests bit 0x200).

## 3. Save data (real save, newest slot)

- **Digimon roster**: 40 records × `0x78` bytes at slot+`0x1850`. Used slots in the real save: 5.

| + | field |
|---|---|
| 0x00 | u32 index (0-based) |
| 0x04 | u32 `0x030405F0` (seen in every record) |
| 0x08 | u32 `0x030405F0` |
| 0x0C | u16 species id |
| 0x0E | name: 16 bytes, NUL-terminated; ASCII (translated names) or SJIS (player-entered names) |
| rest | stats/flags, not yet decoded (level/exp/stat fields are the next RE step) |

Real records: `Agumon` (id 77, name "andre" in SJIS), `Gabumon` (82), `ToyAgumon` (90),
`Dorumon` (80), `Falcomon` (101). The first u16 stat group in a record matches the species base
value in `BPLAYER.BIN` (e.g. Gabumon HP 134), which anchors the stat layout and the base-data file.

## 4. Record layout (recovered from the engine's creation code)

`FUN_020b0100` creates a record; `FUN_020b0788` merges two records; the roster accessor is
`FUN_020ade64(index)` (through the container hooks `DAT_020ade80/84`); `FUN_020ade88(record)` says
whether a slot is occupied (identity != 0); `FUN_020aff40` initializes the 40 x 0x78 roster.

| offset | type | meaning |
|---|---|---|
| 0x00 | u64 | identity: low32 = unique id, high32 = 0x030405F0 (type marker) |
| 0x0C | u16 | species id |
| 0x0E | char[18] | name, NUL-terminated; ASCII (translated names) or full-width SJIS (player-entered) |
| 0x20 | u8 | variant/form (0..5) chosen from the species bitmask |
| 0x21 | u8 | pending-skill flags (one bit per skill slot) |
| 0x24 | u32 | status flags (bit 0x200 tested by the list UI; party/team bits in low 5 bits) |
| 0x28/0x2A | u16 | HP / max HP |
| 0x2C/0x2E | u16 | MP / max MP |
| 0x30/0x32/0x34 | u16 | attack / defense / spirit |
| 0x36 | u16 | unused (0 at creation) |
| 0x38 | u16 | friendship (creation default 100, cap 255) |
| 0x3A | u8 | 100 in the real save (creation writes rng+5; likely aptitude/cap) |
| 0x3B | u8 | 5 in the real save and at creation (role/rank?) |
| 0x3C | u8 | **level** (verified: base stats are level-1 values; ToyAgumon lv1 stats equal the
species table, Agumon lv4/Gabumon lv2 scale up accordingly) |
| 0x3D | u8 | **speed** (creation copies species speed; growth-capped at 99) |
| 0x40 | u32 | experience toward next level (0/4/26 in the real save, matches levels 1/2/4) |
| 0x44 | u16 | unused |
| 0x46..0x50 | u16 x6 | skill ids |
| 0x54/0x58 | u32 x2 | identity copy (low/high) |
| 0x5C..0x67 | u16 x6 | 6-slot sub-structure initialized to 0xFFFF |
| 0x68 | u8 | 0 |
| 0x6A/0x6C | u16 | 0 |

Species tables (static in ARM9): id table at RAM 0x02154278 (743 x 8: {species-parameter index,
name-record index}); parameter blocks at 0x021325EC (376 x 0x38: hp@+6, mp@+8, atk@+0xA, def@+0xC,
spi@+0xE, spd@+0x11, growth/stage@+0x12, six skills at +0x1E); name records at 0x02129FC8
(0x14 each, name message id at +0xE). These match the save records exactly (level-1 stats equal the
parameter blocks).

## 5. Player state, party and storage

The save body is the serialized player state (`0x20` header + RAM image). Roster management and
containers:

- player state base pointer is a global (BSS); the save image starts at 0x20.
- **party container**: player-state +0x3AC, 8 slots, element i = {u32 idLow, u32 idHigh} at
  +0x1C + i*8 (accessors `FUN_020afbc8`/`FUN_020afc54`/`FUN_020afc90`). In the save: 0x3E8 + i*8.
  Real save party: Agumon (id 0), Falcomon (id 4), Dorumon (id 3).
- tamer profile around save 0x3C8: player name "Ｃａｉｏ" (full-width) at 0x3D4 and the current
  partner identity at 0x3C8 (Agumon).
- **farm groups**: player-state +0x11D0 (farm struct, name at +0x28 -> save 0x11F8 "Farm 1");
  member identities appear at save 0x120C/0x1214 (Gabumon id 1, ToyAgumon id 2).
- further containers initialized by `FUN_020acd34`: +0xA8 (0x304 bytes), +0x1020 (54 x 8 entries),
  +0x157C (8 x 8 ids), +0x1830 (roster), +0x2AF0 (0x41C bytes); player state size 0x407C.

Identity references found in the real save (search for `[u32 id][u32 0x030405F0]`):
record 0 -> 0x3C8 (partner), 0x3E8 (party 0); record 1 -> 0x1214 (farm); record 2 -> 0x120C (farm);
record 3 -> 0x3F8 (party 2); record 4 -> 0x3F0 (party 1); plus each record's own identity copy at
record+0x54. This is the checklist for a safe move-out/send.

## 6. Species identity

`MSG/MESPAK00.PAK` contains an **id-ordered Digimon name list** (first entries are eggs, then species
from index 54 = `Chicchimon`). Index == species id, validated with all five records
(Gabumon 82, ToyAgumon 90, Dorumon 80, Falcomon 101). Ids 0..53 are eggs; 447..450 are
`Julia`/`Glare`/`Guard Tamer`/`Dummy Name`; a second copy of the list starts around index 451 (a
different message block, not ids). Names exactly as the game spells them (no truncation problem).

`BPLAYER.BIN`: container `{u32 count=506}{"2.01"}` then 506 × `{u32 offset, u32 size, u32 size, u32
flags}`; block index == species id. Each block begins with the base HP (`u16`/`u32`) and continues
with tables still to decode (candidate: level-up stats, evolutions). `BPDATA.BIN` and `P2D_DATA.PAK`
are further per-species data sources.

## 7. Tooling in this repo

- `analysis/dsle/tools/unpack_nds.py` (reused from Dawn/Dusk): header, ARM9/7, overlays, NitroFS.
- `decompress_le.py`: ARM9/overlay decompressor (validated) + `prepare_le.py` (images + ELFs).
- `save_recon.py`, `container_rules.py`, `save_structure.py`, `roster_parse.py`: save forensics.
- `species_full_list.py`, `extract_nitrofs.py`, `parse_bplayer.py`: data extraction.
- `ovl5_dump.py`, `disasm_decompressor.py`, `disasm_save.py`, `dump_jump_tables.py`,
  `dump_save_table.py`, `show_pseudo.py`: code analysis helpers.
- Ghidra: project `analysis/dsle/ghidra/project-le`, exports `out-arm9` (6,079 functions),
  `out-ovl000` (496), `out-ovl001` (981), `out-ovl002` (347), `out-ovl003` (314).
  Scripts: reuse `analysis/ds2/ghidra/ExportNdsArm9.java`; `analysis/dsle/ghidra/FindSaveCode.java`.

## 8. App wiring (implemented 2026-11-20)

- `app/src/main/assets/le_species.json` is generated by
  `analysis/dsle/tools/build_le_species_asset.py` from the id-ordered MESPAK name run and the ARM9
  id/parameter tables: 447 names (ids 0..446), 376 parameter blocks, stage bands
  (in-training 54..76, rookie 77..116, champion 117..176, ultimate 177..300, mega 301..367) and
  51 localization aliases for the ROM's abbreviated names (`WereGarurumonB`, `MKabuterimonR`,
  `IPDramonDM`, `SGreymon BM`, ...). Audit: every id in 54..367 resolves to a bundled canonical
  species; eggs (0..53) and scripted battle units (368..375) stay non-Digimon.
- `LeSpeciesTable` (app) exposes names/defaults/stage/canonical ids; `NdsStorySpeciesTables`,
  `WorldDsSpeciesCrosswalk.leIdsByCanonical` and `NativeSpeciesResolution` now include it.
- `LeStatConversion` (`le-stat-v1`) projects app stats to the engine's record bands (HP 9999,
  combat 999, speed 99, level 99) and back; `LeOutboundConversion` synthesizes records (with the
  retained record as seed) for sends.
- `TransferGameCatalog` identifies `BLEJ` saves as LOST_EVOLUTION; capabilities expose it as a
  readable, wired writer; `GameImportAcquisitionRepository` imports party **and** farm residents;
  `NativeMaterializationRepository` materializes/returns native stats with the LE policy;
  `ExperimentalWorldDsSendRepository` synthesizes/inserts LE records (cross-title included).
- Farm residents are part of the transfer scope: the import profile accepts FARM, and the move
  policy admits farm records only where the profile opts in (`farmRecordsMovable`, true only for
  this layout) because the editor clears the farm reference on removal.
- Tests: `LostEvolutionSaveTest` (core, incl. real save + real move-out plan/restore),
  `LeTransferConversionTest`,
  updated catalog/capability/import-profile tests. Full suites green (see TESTING.md "Verify v59").

## 9. Next steps (ordered)

1. Core — **done**: profile, reader, editor (remove / insert-retained / insert-generated / replace /
   admit) with MD5 reseal, sequence bump, readback verification and byte-exact restore.
2. App species asset: build `le_species.json` from the ARM9 parameter blocks (376 x 0x38), the
   id->parameter/name mapping table (743 x 8) and the MESPAK00 id-ordered name list; include base
   stats, six skills, growth bits and evolution lineage where evidenced.
3. App: `LeSpeciesTable` + catalog 4th title, canonical crosswalk, stat conversion policy
   (`le-stat-v1`), acquisition/import profile (`ds2`-style), materialisation/move/send branches and
   destination UI (catalog + capabilities + writer wiring).
4. Remaining engine facts if needed for accuracy: exp-to-next curve for level scaling (currently
   synthesis writes explicit stats with exp 0), `+0x3A` aptitude semantics, team container meaning.
5. Tests (app JVM + core), docs (TESTING, CHANGELOG, plan) and final verification; in-game boot
   acceptance remains the honest external boundary.
