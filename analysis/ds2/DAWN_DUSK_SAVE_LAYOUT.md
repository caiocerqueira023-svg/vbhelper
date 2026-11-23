# Digimon World Dawn / Dusk save layout (verified)

Status: **verified on two retail saves and the shared ARM9 engine code**, 2026-11-19.
Dawn USA `A3VE` (MD5 `61a532b01d97392bb2a2d68955486d03`) and Dusk USA `A6RE`
(MD5 `35b6209cf0a69b68a5f78c07d9433c27`) persist the **same** layout, species table and EXP
curves; one adapter serves both titles.

Everything below was recovered from the user's own ROMs by static analysis (Ghidra headless,
decompilation of the ARM9 and the Dawn/Dusk overlays) and then cross-checked byte-for-byte
against two user-supplied retail saves. No ROM or save bytes are committed; the app ships only
derived species names/values with provenance.

## Container

| Fact | Value | Evidence |
|---|---|---|
| Image size | `0x10000` (64 KiB) | both real saves |
| Save region per copy | `0x7B2C` | `memset` size in the new-game initialiser `FUN_0202e8d8`; real header length |
| Copy 2 base | `0x7B80` | real saves: one populated copy at 0, second region erased |
| Gap | `0x54` bytes, zeroed | real saves |
| Copy header | 16-byte **MD5 over the copy with `[0x00,0x10)` zeroed**, stored at copy+0 | real saves: `md5_ok=true`; loader verifies exactly this (`FUN_02029e30` case 6) |
| Write sequence | **u32** at copy+`0x18`, `+1` per save; higher wins | `FUN_0202a0e8` reads `*(u32 *)(buf+0x18)` and writes `value+1`; real saves store `0` |
| Signature | `DSDIG2\0\0` at copy+`0x10` | `writeSaveSignature` call in `FUN_0202e8d8` writes the literal at `0x0211DA08`; real saves carry it |
| Save region base in RAM | `0x021357C8` | `FUN_0202e8d8`, `takeNextId` (`0x02135A24` = base+0x25C) |

The initialiser (`FUN_0202e8d8`) clears `0x7B2C` bytes, writes the signature, sets money to 100,
day to 1 and a random id base; `FUN_02030880` marks every record empty by storing `-1` at
`record+4`.

## Save structure (offsets inside a copy)

| Offset | Size | Meaning | Evidence |
|---|---|---|---|
| `0x000` | 16 | MD5 copy header | `FUN_0202a0e8` / `FUN_02029e30` |
| `0x010` | 8 | `DSDIG2` magic | initialiser |
| `0x018` | 4 | write sequence (u32) | `FUN_0202a0e8` |
| `0x230` | 4 | constant `1000` | initialiser |
| `0x234` | 4 | random id base | initialiser |
| `0x238` | 12 | player name, 5 glyphs + `0xFFFF` | initialiser; real save "Fafa" |
| `0x244` | 4 | money (100 at new game) | initialiser; real save |
| `0x248` | 4 | food | same field order as the shared save struct |
| `0x24C` | 4 | play time ticks | real save ≈ 17 s at new game |
| `0x258` | 2 | day (1 at new game) | initialiser |
| `0x25C` | 4 | last id handed out (`takeNextId`) | `FUN_0202e9d0` reads `*(u32 *)(0x02135A24)`; real saves match the max record id |
| `0x260` | 10×4 | party references (record ids, 0 = free) | real saves contain their three members; `FUN_02033000` adds |
| `0x288` | 40 × `0x164` | individual records; empty when id word is 0 | `FUN_02030c64` iterates 40 slots of `0x59` words |
| `0x3A28` | 1 | tamer rank | initialiser; follow-up tamer data at `0x3A2C` |

The 40 records end exactly at `0x3A28` (`0x288 + 40 * 0x164`), which is why one table serves both
party and storage: a record is a party member when its id appears in the `0x260` array. The game's
free-party-slot rule (`FUN_02032f74`) uses party slots `0..4` while fewer than three are occupied,
then `5..7`; slots 8 and 9 are never assigned by the add path.

## Individual record (`0x164` bytes)

| Offset | Field | Evidence |
|---|---|---|
| `0x000` | id (u32, 0 = empty) | `FUN_02030c64` |
| `0x004` | species id (u32; valid `0x41..0x1F4`) | `FUN_0203109c` rejects `<0x41` and `>500` |
| `0x008` | name, nine 2-byte glyph codes, `0xFFFF`-terminated | record initialiser copies 16 bytes + terminator |
| `0x01A` | species `unk3` | initialiser |
| `0x01B` | EXP curve index (0..2) | initialiser; `FUN_02055810` indexes three 99-entry rows |
| `0x01C` | growth row | initialiser |
| `0x01D` | derived from species `+0x27` | initialiser |
| `0x01E` | stage (0..4) | `FUN_02030714` bands |
| `0x022` | four ability ids (`0xFFFF` = none) | initialiser; real save `[1, 0x24]` |
| `0x02C` | four ability source species ids (`-1` unused) | initialiser (`record+0x2C = species`) |
| `0x03C` / `0x040` | HP / max HP (s32) | initialiser from species `+0x04` |
| `0x044` / `0x046` | MP / max MP (s16) | species `+0x08` |
| `0x048`..`0x04E` | attack, defense, spirit, speed (s16) | species `+0x0A..+0x10` |
| `0x050` | species `unk12` (s16) | species `+0x12`; real save `5` for Coronamon (base data `5`) |
| `0x052` | max level / aptitude (s16, 0..99) | species `+0x14`; real save `32` |
| `0x054` | 14 skill slots (`0xFFFF` ends) | initialiser writes four + ten `0xFFFF` |
| `0x070` | `0x21` (33) | initialiser |
| `0x074` | zero at creation | initialiser |
| `0x076` | **level** (s16) | initialiser stores species `+0x02`; real saves 20/35 |
| `0x078` | nine 32-bit counters, zero at creation | initialiser loop |
| `0x09C` | EXP to next level (u32) | `FUN_02055810(record, level)`; real save 646 at level 20 |
| `0x0A0` | eight zeroed u16 | initialiser |
| `0x0B0` | history flags by stage (`0x1C`/`0x18`/`0x10`) | `FUN_02030f78` |
| `0x0B4` | five stage-indexed previous species ids (`-1` unused) | `FUN_02030f78` fills from the `/dat/sk` chain |
| `0x0C8` | history tail copied from the species record | `FUN_0202bd2c` |
| `0x107` | history stage byte | `FUN_02030f78` |
| `0x108` | eight species resistances (s16) | initialiser from species `+0x16..+0x24` |
| `0x120` | state flags (u16, 0 for ordinary records) | `FUN_02030b7c`/`FUN_02030bb0`; real saves 0 |
| `0x124` | zeroed u32 at creation | initialiser |
| `0x128..0x164` | not written by creation; preserved verbatim | byte comparison of real records |

Field meanings beyond the ones listed are **not established**; the app keeps the exact 356 bytes
and only interprets the documented windows.

## Species and text data

- Species name table: ARM9 offset table at `0x02112998` (Dawn) / `0x02112A94` (Dusk), indexed by
  species id, each entry an `0xFFFF`-terminated 2-byte glyph string. 436 species ids
  (`0x41..0x1F4`), **identical between titles**.
- Base data: NitroFS `/dat/dm/<species/8>` with eight u32 offsets then the records initialised
  earlier (level, stats, abilities, skills, resistances, growth, EXP curve, max level).
- Lineage: NitroFS `/dat/sk/<species/8>`, first s32 of each record is the previous species.
- EXP curves: ARM9 `0x0211E440`, three rows of 99 u32; values identical to the World DS curves.
- Text glyphs: `0x00` space, `0x01..0x0A` digits, `0x0B..0x19` punctuation, `0x20..0x39` A–Z,
  `0x3A..0x53` a–z, `0x54..0x6B` symbols, `0x80..0xBC` accented letters, `0xFFFF` terminator.

Validation: the extracted Coronamon base record reproduces the real save's record windows exactly
(`maxLevel` 32, `unk50` 5, skills `[32,52,111,59]`, abilities `[1,36]`, resistances
`[1000,200,900,700,700,500,400,500]`).

## Writer rules (implemented in `Ds2SaveEdits`)

1. Re-read the exact source; edit only the copy whose sequence is the unique newest valid one.
2. Refuse to edit a copy whose MD5 header does not match its bytes.
3. Removal: clear the party references to the id, zero the record slot and write `-1` at `+4`
   (the shape the game itself leaves), refusing the last party member.
4. Insertion: exact 0x164 bytes into the first free record slot, first free party slot per the
   game's own rule, raising `0x25C` only when the id is higher.
5. Every edit bumps the copy sequence by one and recomputes the copy's MD5 header.
6. Re-read the candidate and require every record outside the edited windows to be unchanged
   (copy sequence normalised), and byte-exact byte windows otherwise.
7. `restore` returns the exact original image; the app additionally keeps a durable backup.
8. In-game load acceptance of a written Dawn/Dusk save is **not yet performed**; the loader
   verification logic above is decompiled, and the World DS experience is the reference.

## Reproduction

```powershell
py -3 analysis/ds2/tools/unpack_nds.py "<rom.nds>" analysis/ds2/dawn
py -3 analysis/ds2/tools/scan_patterns.py analysis/ds2/dawn
py -3 analysis/ds2/tools/extract_ds2_species.py "<rom.nds>" analysis/ds2/dawn/species_dawn.json
py -3 analysis/ds2/tools/validate_layout.py "<save.sav>"
$env:DS2_SAVE_DIR = "<folder with the retail saves>"
.\gradlew.bat :game-save-core:test --tests "*Ds2RealSaveValidationTest*"
```

Ghidra headless project: `analysis/ds2/ghidra/project` (ARM9 at `0x02000000`, Dawn overlays at
their RAM bases), pseudocode exports under `analysis/ds2/ghidra/out-dawn` and `out-ovl012`.

> The canonical copy of this document lives at game-save-core/DS2_RESEARCH.md.

