# Digimon World Dawn/Dusk transfer plan — the second NDS Story adapter

**Status:** planning step opened 2026-10-24; **read, acquisition and the native editor are
implemented 2026-11-19** from the user-supplied USA ROMs and retail saves (see
`game-save-core/DS2_RESEARCH.md`). Dawn/Dusk saves now decode on the shared reader, party
records are imported into Storage through the normal reviewed flow, and the core party-record
editor is verified on the real saves. The app-level move/send repository wiring is the remaining
writer step (W6/W7). This document keeps the evidence gates and implementation sequence that
produced the adapter.

**Parent plan:** [MULTI_GAME_TRANSFER_PLAN.md](MULTI_GAME_TRANSFER_PLAN.md), section 20
item 5 ("Extend to Dawn/Dusk…") and section 21 checkpoint added with this plan.
**Current code state:** schema 55; World DS read/acquire/move/send implemented, measured by
the latest recorded offline verification in [TESTING.md](TESTING.md) (137 core /
337 filtered app JVM / 11 companion tests at the last checkpoint).
**Existing World DS evidence:** [game-save-core/WORLD_DS_RESEARCH.md](game-save-core/WORLD_DS_RESEARCH.md),
[game-save-core/SAVE_READING_STANDARD.md](game-save-core/SAVE_READING_STANDARD.md),
[game-save-core/WORLD_DS_STAT_CONVERSION.md](game-save-core/WORLD_DS_STAT_CONVERSION.md).

---

## 0. Summary

The user's working assumption is right about the engine and wrong about the save layout.
Dawn and Dusk are the second *Digimon Story* entry and are documented as running on the
**Digimon World DS engine**, but their persisted save is a **different layout**: same
architecture (64 KiB image, two MD5-headed copies, counter at `+0x18`, one live copy chosen
by counter), different geometry and a very different individual-record region. Copying World
DS offsets would fail. What transfers is the *architecture and the code*, not the constants.

Therefore the plan is:

1. Refactor the World DS adapter into a shared **NDS Story engine family** layer
   (behavior-preserving) so a second layout is a profile rather than a fork.
2. Add a **Dawn/Dusk profile** whose constants are verified against real saves before the
   reader is registered, as the binding save-reading standard requires.
3. Land the same feature ladder World DS already has, in the same order and with the same
   guarantees: read → reviewed copies → Storage acquisition/Home → destination preview →
   exact retained move/send → generated sends → UI/docs/PC parity.
4. Ship nothing as "working transfer" until each title passes its own in-game load
   acceptance, exactly like World DS.

Work can start immediately on item 1; item 2 needs save files and ROMs that only the user
can supply (section 6).

---

## 1. Scope

**In scope**

- Digimon World Dawn (USA, `A3VE`) and Digimon World Dusk (USA, `A6RE`), raw 64 KiB
  `.sav` from DraStic / DeSmuME / melonDS and their `.dsv` containers.
- Full UX parity with World DS: origin list entry, linked save, roster with native
  values, reviewed save copy, provisional Storage copies, shared DIM-style Home,
  species auto-identification, reviewed mapping, destination preview/progress,
  experimental native move out, experimental send back, presence/journal/recovery/backups,
  PC companion and desktop inspectors.
- Both directions: `Dawn/Dusk → Storage` and `Storage → Dawn/Dusk`, including a stored
  individual arriving from another supported origin.

**Out of scope (unchanged decisions)**

- Farm/DigiFarm transfer. Dawn/Dusk keep non-party Digimon in the DigiFarm; as with
  World DS's farm, DigiFarm residents stay inspection-only until separately decided.
  Party-scope transfer is the parity target.
- Japanese Sunburst/Moonlight (`A3VJ`/`A6RJ`), other regions, save states, flashcart-only
  containers, and no$GBA wrappers. Later adapters, not this step.
- Lost Evolution / Super Xros Wars / Re:Digitize etc.; each keeps its own future step.
- Direct save-to-save transfer; everything still goes through Storage.
- Shipping ROMs, ROM-derived binaries, or private saves in the repository.

---

## 2. What "work exactly like it currently does for World DS" means

Parity is defined per capability, not per file. Each row is independently accepted.

| # | Capability | World DS today | Dawn/Dusk target | Evidence dependency |
|---|---|---|---|---|
| 1 | Read a raw save on the phone, any file name/folder, content-led | `WorldDsSaveReader`, registered, discovery by size family | same UX, DS2 geometry + title binding | real saves (G1) |
| 2 | Roster with stored name, level, native HP/MP/combat stats, aptitude, separate EXP channels | native values shown; no fabricated personality | same for fields verified per title | record field map (G2) |
| 3 | Save integrity/copy review | MD5 header verified structurally, newest valid `+0x18` copy auto-selected, manual copy review bound to revision | same rule if DS2 reseal verified; DS2 has copies at `0x0`/`0x7B80` | G1 + boot test |
| 4 | Provisional copy into Storage | party records only, per-item receipts, source-local IDs | same; party is the native transfer scope | G2/G4 |
| 5 | Species identification | 426-entry ROM name table asset; automatic identity + reviewed mapping | 398-entry DS2 table asset; same flow | ROM extraction (G4) |
| 6 | Shared Home / materialization | app-only personality, projection, refresh continuity | same | #4/#5 |
| 7 | Destination preview and progress report | revision-bound, non-executable, profile-aware | same, with DS2 profile and blockers | #1/#4 |
| 8 | Native move out (`Move to Storage`) | removes exact 384-byte record, reseals, journals backup, readback-verifies, source-removal gated | same, DS2 record bytes/geometry | G2 + G5 |
| 9 | Send back (`Move to game`) | inserts exact retained record, presence marked away, recovery path | same, including cross-title later | G2 + G5 |
| 10 | Generated record from app state | synthesized from app stats + species defaults | later phase (DS2 fields and defaults first) | G2/G4 |
| 11 | Desktop/PC parity | `--inspect-world-ds`, preview, rehearsal, README | `--inspect-ds2`, preview/rehearsal, README | each phase |
| 12 | Localization + capability honesty | EN/PT-BR/JA, experimental labels, flags false until accepted | same, fix JA mojibake while touching strings | each phase |

Definition of "done for a title": rows 1–9 and 11–12 accepted for that title, with the
in-game boot acceptance in section 8; row 10 accepted for at least one title before
advertising "any supported game that supports its species".

---

## 3. Evidence about Dawn/Dusk (2026-10-24)

Confidence labels: **V** = read from a primary source or cross-checked numerically here;
**P** = published claim, not yet checked against a real save; **U** = unknown, must be
measured.

### 3.1 Confirmed

- **Titles and codes (V).** Dawn USA `A3VE`, Dusk USA `A6RE`; JP Sunburst `A3VJ`, Moonlight
  `A6RJ`. Source: `DigimonNDSRomEditor` `constants.py` header table at the pinned commit
  (the repo's own art credits already carry A3VE/A6RE).
- **Same engine (P but strong).** Wikipedia's infobox lists the engine as "Digimon World DS
  engine"; the release is 2007-03-29 JP / 2007-09-18 NA. Treat "same engine" as true for
  architecture, false for layout (below).
- **Save architecture (P with numeric cross-checks).** From the Dawn/Dusk research notes
  `save_data_editing.txt` (same author as the pinned ROM editor):
  - 64 KiB image; two copies: `0x0000–0x7B2C` and `0x7B80–0xF6AC`; per-copy size
    **`0x7B2C`**; second copy at **`0x7B80`**; unused tail after `0xF6AC`.
  - 16-byte MD5 at `copy+0x00` over the whole `0x7B2C` copy with `[0x00,0x10)` zeroed.
  - `copy+0x10–0x18` = internal ID string **`DSDIG2`**.
  - Live copy = higher single byte at **`copy+0x18`** (second copy's counter at `0x7B98`).
  - Player name originally `copy+0x238` (5 glyphs), money `0x244`, tamer rank `0x3A28`,
    tamer points `0x3A2C`, consumable inventory `0x4530–0x46F8`.
  - **Seven Digimon slots at `0x288`, stride `0x164`** (`0x288, 0x3EC, 0x550, 0x6B4,
    0x818, 0x97C, 0xAE0`), i.e. the party region.
  - Two independent cross-checks agree: GBAtemp AR codes place Dawn Digimon #1 moves at
    `0x2135AA4/0x2135AA8` and #2 at `0x2135C08/0x2135C0C` (a `0x164` step), and the
    `0x1C0` Dawn/Dusk RAM delta matches the Dawn name address in the same notes.
  - DeSmuME's lead developer describes a Dawn `.duc` as a raw save with a 164-byte header
    to import as **512 kbit = 64 KiB**, matching the geometry above.
- **Record fields known so far (P, narrow):** two move-related u16 at record `+0x54` and
  `+0x58` (AR codes). Everything else inside the `0x164` record is **unverified**.
- **Species ID space differs (P, measured from pinned constants):** 398 entries IDs
  `0x41–0x1F4` for Dawn/Dusk versus World DS's 426 entries `0..425`. DS IDs cannot be
  pasted into a DS2 record; a per-title crosswalk is mandatory.
- **Text encoding differs from World DS (P):** the pinned ROM editor's `strings.py` uses
  A–Z `0x20–0x39`, a–z `0x3A–0x53`, terminator `0xFFFF` for DS2 ROM text, while the World DS
  reader decodes A–Z `0x50–0x69`, a–z `0x6A–0x83`. Record names still need a real-save
  check (ROM text and save text can share the table but this must be proven).
- **No public save editor exists for Dawn/Dusk (V by search):** GitHub/GBAtemp searches
  return only cheat codes and save requests, so there is no third-party record layout to
  copy and no independent writer to compare against.

### 3.2 Why this changes the plan versus the user's assumption

| Assumption | Reality | Consequence |
|---|---|---|
| "Both use the exact same engine" | true (Story engine lineage) | shared container/commit/UI architecture is reusable |
| "…must have a similar saving system" | same *architecture*, different *constants and record region* | no offset reuse; a second layout profile is required |
| "World DS work can just be pointed at Dawn/Dusk" | code is ~90% reusable, layout is not | refactor to a family profile first, then a DS2 adapter |
| "Dawn and Dusk are probably identical" | likely byte-identical layout; the save carries no documented version marker | Dawn-vs-Dusk title binding is required (reviewed, revision-bound) |

### 3.3 Unknown until measured (U)

- Record fields beyond moves: species ID, name, level, EXP channels, stats, aptitude,
  history, equipment/held item, flags; whether the 7 slots are party (6) plus one extra.
- Where the DigiFarm roster lives, whether any "unassigned storage record" region exists
  (World DS move support includes such records; DS2 may have none — party-only is acceptable
  for parity because farm transfer is excluded).
- Empty-slot representation, identity/allocator semantics, maximum party size in the game.
- Whether any in-save byte distinguishes Dawn from Dusk.
- Whether both copies are always written together, and whether the MD5 reseal rule boots.
- `.dsv`/footer behavior for DS2 (expected to share the 40-byte DeSmuME/DraStic trailer).
- Whether the record name uses the same glyph table as ROM text.

---

## 4. Target architecture — one NDS Story family, two layouts, three titles

### 4.1 Why a single family reader

`SaveReaderRegistry` runs every reader whose `acceptsLength` passes and throws
`AMBIGUOUS_FORMAT` if more than one decodes the same bytes. World DS and Dawn/Dusk saves are
both 64 KiB images, so three sibling `GameSaveReader`s would collide. The family must be
**one reader** that resolves the layout from content, or mutually exclusive gates that no
future title can satisfy honestly. Content-led resolution is the correct design.

### 4.2 Profile model

Two separations, both persisted as plain strings so no per-game table is needed:

- **Layout profile** — what the bytes look like:
  ```kotlin
  data class NdsStoryLayout(
      val id: String,                       // "nds-story-ds1-usa-raw-v1", "nds-story-ds2-usa-raw-v1"
      val fileBytes: Int,                   // 65536
      val saveBytes: Int,                   // 0x7DC0 / 0x7B2C
      val copyStride: Int,                  // 0x7E00 / 0x7B80
      val copyCount: Int,                   // 2
      val wrapperSlackBytes: Int,           // 0x400
      val copyHeader: CopyHeaderRule,       // 16-byte MD5 over zeroed header, sequence offset/width
      val regions: List<RecordRegion>,      // party/bank/farm tables: base, stride, count, issues
      val references: List<ReferenceArray>, // party ids, farm residents, items, misc refs
      val lastIdOffset: Int?, val idReserved: Long,
      val signature: ByteArray?,            // optional in-save magic (never a hard requirement)
      val recordLayout: NdsStoryRecordLayout // field offsets/widths/valid ranges
  )
  ```
- **Title profile** — how to interpret the bytes' game data:
  ```kotlin
  data class NdsStoryTitle(
      val gameId: String,                  // digimon_world_ds_nds / digimon_world_dawn_nds / digimon_world_dusk_nds
      val formatProfile: String,           // world-ds-usa-drastic-raw-v1 / world-dawn-usa-raw-v1 / …
      val codecVersion: Int,
      val layout: NdsStoryLayout,
      val speciesAsset: String,            // app asset key, e.g. ds2_species.json
      val speciesCount: Int,
      val glyphCodec: NdsStoryGlyphCodec,  // DS1 map vs DS2 map
      val statPolicyId: String,            // world-ds-stat-v1 / ds2-stat-v1 (declared, versioned)
      val titleBinding: TitleBinding,      // NONE for DS; REVIEWED for Dawn/Dusk
      val romEvidence: RomEvidence?         // pinned commit/table addresses, never ROM bytes
  )
  ```

World DS becomes the first `NdsStoryTitle`; `WorldDsSaveReader`, `WorldDsRecordCodec`,
`WorldDsPartyEdits`, `WorldDsCopySelection`, `WorldDsStatConversion` keep their public
surface as thin delegates/facades so the 58 app call sites and 137 core tests stay valid.
New code goes in the `gamesave/nds/` package.

### 4.3 Reader, layout detection, and the `DSDIG2` signature

`NdsStorySaveReader` derives each copy's base from the candidate image base and profile
geometry, validates the copy header (MD5) and decodes the profile's regions. Layout
resolution strategy:

1. Try each registered layout's container geometry against the file size/wrapper families;
   decode only candidates whose geometry fits (DS1 `0x7E00` stride vs DS2 `0x7B80` never
   overlap for a 64 KiB image, so World DS ↔ Dawn/Dusk is unambiguous).
2. A layout is accepted only when its own identification invariants hold (header valid or
   degenerate magic, at least one live record, at least one bookkeeping reference as the
   World DS reader already requires). Signature bytes are corroboration, never the rule.
3. If no layout decodes, the usual granular `SaveReadFailure` reasons apply.

This keeps the save-reading standard: any name, any folder, any progress state; anomalies
become review issues.

### 4.4 Copy authority

Generalize `WorldDsCopySelection` to `NdsStoryCopySelection`, keyed by the registered
profile triple instead of the `WorldDsSaveReader.GAME_ID` literal, and reading
`copyStride`/`saveBytes`/sequence offset/width from the profile. Behavior for World DS
must not change; DS2 gets the same newest-valid-copy rule (single-byte counter) plus the
existing manual review, revision-bound.

### 4.5 Dawn versus Dusk: reviewed title binding (schema 56)

Unless evidence finds an in-save discriminator, a DS2 save cannot prove which retail version
wrote it. Silent guessing would mislabel species/defaults, so the plan uses the same
authority pattern already used for save copies:

- Detection: layout `ds2` is automatic; the version is a **reviewed binding**.
- Persistence: add two nullable columns to `LinkedGameSave`
  (`reviewedTitleGameId TEXT`, `reviewedTitleRevision TEXT`) and a `bindTitle()` method in
  `LinkedGameSaveRepository`. This is schema **56** (additive, no table rebuild, preserving
  migration tested).
- Selection: a binding applies only while the file's `contentRevision` matches; a new save
  revision expires it and the source shows "confirm Dawn/Dusk" again.
- First link: the link flow already has a modal step for the DS launcher icon
  (`NdsLauncherImportPrompt`); reuse that pattern for a version prompt. A generic file link
  produces a family observation with `ObservationNotice.TITLE_VARIANT_REQUIRED`; browsing
  shows native data, acquisition/send stay blocked. Library grouping shows an "unresolved
  Dawn/Dusk" row with a generic icon until bound.
- If evidence later finds a reliable discriminator (e.g. a version byte or
  version-exclusive species in the roster), automatic binding is added behind a confidence
  rule and the manual prompt remains the fallback.

Title binding must never silently rewrite an existing binding on a changed revision.

### 4.6 Writers: editor interface instead of DS literals

The move/send repositories are already ~90% game-agnostic (policy objects work on
`ObservedDigimon` + `SaveContainer`). The coupling is the reader/editor call sites. Introduce:

```kotlin
interface NdsStorySaveEditor {
    fun read(bytes: ByteArray): SaveObservation
    fun removeRecord(source: ByteArray, revision: String, recordKey: String): NdsStoryEdit
    fun insertRetained(destination: ByteArray, revision: String, recordBase64: String): NdsStoryEdit
    fun replaceRetained(destination: ByteArray, revision: String, recordBase64: String): NdsStoryEdit
    fun admitToParty(source: ByteArray, revision: String, recordKey: String): NdsStoryEdit
    fun restore(candidate: ByteArray, edit: NdsStoryEdit): ByteArray
}
```

- `Ds1SaveEditor` wraps the existing `WorldDsPartyEdits` unchanged.
- `Ds2SaveEditor` is implemented only after section 6 gates pass; until then the profile
  declares no writer and every writer gate reports `FORMAT`/unsupported (honest UI).
- Both `ExperimentalWorldDsMoveRepository` and `...SendRepository` are renamed to
  `ExperimentalNdsStoryMoveRepository`/`...SendRepository` (or gain a
  `profileResolver`/`editorResolver`); the DS classes stay as delegating wrappers during
  migration so their tests remain meaningful.
- `SaveDocumentWrite.FILE_BYTES` and `ExperimentalEditJournal`'s `ByteArray(FILE_BYTES)`
  move to the profile's `fileBytes`.

### 4.7 Species, text and conversion registries

- Replace the concrete `WorldDsSpeciesTable` type in six constructor parameters
  (`DefaultAppContainer`, `TransferWorkspaceScreen`, `NativeArchiveStorageEntry`,
  `NativeGameArchivePanel`, `NativeSpeciesPresentationRepository`,
  `NativeMaterializationRepository`) with `NdsStorySpeciesTables` resolved by `gameId`.
  `tableIdentity` already takes profile+codec; keep that contract.
- Per-title glyph codec: `NdsStoryGlyphCodec` with `Ds1Glyphs` (current map) and `Ds2Glyphs`
  (verified against a real save name before shipping).
- Conversion: keep `WorldDsStatConversion` as DS1's policy (`world-ds-stat-v1`); DS2 gets its
  own declared policy id once its record fields exist. **No DS2 field may borrow a DS1
  window.** Until a DS2 policy is verified, DS2 records keep native values only and app
  projection supplies baseline display, exactly as World DS did before its policy existed.

### 4.8 Registry and discovery

`SaveReaderRegistry(profiles)` builds the family reader for the registered layouts.
`SaveDiscoveryPolicy` derives its size families from the same registry instead of the
hardcoded `worldDsRanges`; the DS2 size family is coincidentally the same 64 KiB, so
discovery already lists these files — registration and structure decide support. The
`AMBIGUOUS_FORMAT` rule stays.

### 4.9 App capability plumbing

Replace the ten `== WorldDsSaveReader.GAME_ID` equality sites and the enum-literal UI gates
(`TransferGameLibraryScreen` send rows/destination filter, `TransferLauncherIconsScreen`,
`TransferHubScreen`, `TransferScreen` game labels, `SaveCopyCollapse`, `AutomaticSaveReconciler`,
`NativeMaterializationRepository`, `LinkedGameSaveRepository` copy hooks) with profile capability lookups:

- `TransferGame` gains real native ids (`DAWN`, `DUSK`) and a
  `capabilitiesOf(gameId)` resolver: canRead, canReviewCopy, canSnapshotCopy, canMove,
  canSend, writerState.
- Send destinations are all games with a writer profile, not `WORLD_DS` literals.
- `TransferGameCatalog.identify` keeps filename hints for grouping only; decoded ids win
  (existing test stays green; new tests cover Dawn/Dusk decoded ids).
- `TransferDetails` native-stat labels become profile-supplied strings, not `world_ds_*`
  keys; a new `values*/ds2_saves.xml` holds DS2 copy/review text and generic move/send
  strings move to `transfer_workspace.xml`.
- Fix the existing JA mojibake at `values-ja/transfer_workspace.xml` (`transfer_game_dawn`)
  while touching the file.

---

## 5. Dawn/Dusk layout profile — hypothesis to verify

Everything here is a **verification target**, not an implementation fact. A field only
becomes a constant after gate G1/G2.

| Element | Working value | Confidence | How it is verified |
|---|---|---|---|
| Container | 64 KiB raw, `.dsv` via shared 40-byte trailer | P | open user's `.sav`/`.dsv`; footer parser reuse |
| Copy count / size | 2 × `0x7B2C` | P | file geometry + both headers valid |
| Copy 2 base | `0x7B80` | P | MD5 header check at both bases |
| Copy sequence | u8 at `+0x18` / `0x7B98` | P | two saves from real play; boot behavior |
| Copy reseal | MD5 over copy with `[0,0x10)` zeroed | P | recompute on pristine save; boot test after edit |
| Signature | `DSDIG2` at `+0x10` | P | inspect real saves (may be absent like DS's `DSDIGI`) |
| Party region | 7 slots at `0x288`, stride `0x164` | P (two independent checks) | decode real party of 1–6; match in-game names/levels |
| Party roster mapping | slot ↔ active-3 + reserve-3 layout | U | controlled reorder sample |
| DigiFarm region | unknown, likely `0xC44–0x3A28` | U (inference) | excluded from transfer; inspect to avoid misclassification only |
| Record fields | moves at `+0x54/+0x58` only known | U | controlled action samples (rename/evolve/level/equip) |
| Name encoding | DS2 glyph table (`A–Z 0x20…`) | P | decode a known player/party name |
| Species ids | `0x41–0x1F4` (398) | P (measured per pinned constants) | ROM extraction + save cross-check |
| Identity/allocator | unknown, DS1-style `lastId` not assumed | U | controlled recruit/delete samples |
| Empty slot | unknown (zeroed? sentinel?) | U | controlled storage sample |
| Title marker | unknown | U | diff a Dawn and a Dusk save |
| Max party | 6 (Wikipedia), 7 save slots | P/U | in-game + save observation |

**Structural caution:** Dawn/Dusk's only documented individual table is the party. If the
DigiFarm roster or a world/bank table uses a different record layout, the reader must model
regions separately; the shared `ObservedDigimon`/`SaveContainer` model already supports
multiple regions (Time Stranger has party/bank/farm), so this is a profile-per-region
decision, not a contract change.

---

## 6. Evidence gates and the samples needed

The save-reading standard forbids enabling a production reader from guesswork. These gates
are ordered and each has a concrete artifact.

**G0 — Planning (this document).** Signed-off scope, architecture, parity matrix.

**G1 — Container and integrity evidence.** Needs one Dawn and one Dusk USA raw `.sav` (or
`.dsv`) written by real play, ideally one fresh and one progressed per title.
Checks: file size family; both copy bases and headers; MD5 recomputation reproducing the
game's own headers; counter behavior across two saves from the same file; `DSDIG2`
presence; whether both copies are written together; `.dsv` footer slicing.
Artifact: aggregate receipt in `TESTING.md` + `game-save-core/DS2_RESEARCH.md`; no private
bytes, names or IDs published.

**G2 — Record and region mapping.** Needs the same saves plus controlled before/after pairs
(details in G3) and the in-game knowledge of party state. Checks: party table boundary and
stride; record field offsets for species, name, level, EXP, stats, aptitude, equipment,
flags; empty-slot representation; identity semantics; DigiFarm boundary (inspection only);
RAM↔save correspondence. Method: the existing PC companion `--capture`/`--compare-captures`
workflow (private state, immutable manifests, aggregate reports) plus `--inspect-nds-story`
on copies. No synthetic edits are counted as evidence.

**G3 — Controlled samples.** The user performs one known action per pair and copies the save
out (or captures it through the companion): unchanged resave; party reorder; deposit to
DigiFarm; withdraw from DigiFarm; rename; evolve/de-evolve; level up / gain EXP; equip/unequip;
recruit or create a Digimon; delete/release if the game allows. Each pair answers specific
field questions; only the pairs needed for transfer scope (party record fields, identity,
resave behavior) are required.

**G4 — ROM data extraction.** Needs the user's own USA Dawn (`A3VE`, MD5
`61a532b01d97392bb2a2d68955486d03`) and Dusk (`A6RE`, MD5
`35b6209cf0a69b68a5f78c07d9433c27`) ROMs — the hashes recorded by the pinned research
notes, validated by the tool. A generalized `tools/extract_nds_story_species.py` verifies
the hash and game code, reads the species/base-data/evolution tables at the pinned
ROM-editor addresses (independently implemented, no vendored code), sanity-checks first/last
names and stage bands, and writes `app/src/main/assets/ds2_species.json` with provenance
(ROM hash, game code, table addresses, pinned commit). No ROM bytes ship. If the user
prefers not to share ROMs, they run the tool and provide only the JSON.

**G5 — In-game writer acceptance (per title).** Candidate saves are built on copies, never
on the source; the user swaps a candidate into the emulator and boots the retail game.
Acceptance requires: game loads (no "no save data"), roster/party shows the expected
result, unrelated state intact, the game saves again normally, and a re-read of the
post-save file still matches. This gate is title-and-capability specific (read, remove,
insert are separate rungs) exactly as World DS.

**G6 — Title binding evidence (or fallback).** Compare a Dawn and a Dusk save for in-save
version markers; if none is reliable, the reviewed binding in section 4.5 is the accepted
design and needs no further evidence.

Nothing after G1 blocks G0/W1; W1 (refactor) and the generic inspector can be built before
any sample arrives.

---

## 7. Work breakdown

Sizes are relative (S/M/L), not time estimates. Each work item ends with green tests and a
`TESTING.md` checkpoint entry.

### W0 — Planning and master-plan sync (S) — **this step**

- This document; `MULTI_GAME_TRANSFER_PLAN.md` section 20 item 5 + section 21 checkpoint +
  reference/risk updates; README pointer when implementation starts.
- **Acceptance:** plan review; no code/capability change; user confirms scope decisions in
  section 10.

### W1 — Behavior-preserving NDS Story family refactor (L) — **delivered 2026-10-25**

**Delivery note (2026-10-25).** The family seams exist and World DS behavior is unchanged
(core 191, filtered app JVM 155, companion 11, SQLite 22 checks passed; debug assembly and
androidTest compilation passed; schema stays 55). Shipped: `NdsStoryProfile`/
`NdsStoryRecordLayout`/`NdsStorySpeciesData`/`NdsStoryTextCodec`/`NdsStoryCopyHeaderRule`/
`NdsStoryFarmLayout`/`NdsStoryItemLayout`; `NdsStoryProfiles` with the verified DS1 profile;
`NdsStorySaveReader`; `NdsStoryRecordCodec`; `NdsStoryCopyIntegrity`; `NdsStoryCopySelection`;
`NdsStorySaveEditor` + `Ds1SaveEditor`; `NdsStoryConversionPolicies`; profile-driven
`SaveReaderRegistry`/`SaveDiscoveryPolicy`; profile-aware research inspector and
`--inspect-nds-story`; app `TransferGameCapabilities` replacing the ten game-id equality
sites and the library/send gates. Deviations kept deliberately small: new types live in the
existing `gamesave` package (not a `gamesave/nds` subpackage); the ~700-line
`WorldDsPartyEdits` body still owns the audited DS1 byte windows behind `Ds1SaveEditor`
instead of being rewritten onto the profile object; glyph encode/decode was extracted to
`Ds1TextCodec` but records' `384`/`425` literals inside `WorldDsPartyEdits` remain; the
`NdsStoryPartyEdits` result classes keep their `WorldDs*` names behind the
`NdsStoryPendingEdit` interface. These are mechanical follow-ups for W3/W6, not blockers.

Tasks (original scope):

1. Add `gamesave/nds/` profile types (`NdsStoryLayout`, `NdsStoryRecordLayout`,
   `CopyHeaderRule`, `RecordRegion`, `ReferenceArray`, `NdsStoryTitle`, glyph codec).
2. Move `WorldDsSaveReader` parsing internals into `NdsStorySaveReader(layout)` and keep
   `WorldDsSaveReader` as a facade exposing today's constants.
3. Generalize `WorldDsResearchInspector.readBlock(bytes, base, layout)` and make
   `desmumePayloadStart`/`hasSaveSignature` shared; remove the duplicated
   `SAVE_BLOCK_BYTES`; the inspector CLI gains a generic `--inspect-nds-story`.
4. Generalize `WorldDsRecordCodec` to `NdsStoryRecordCodec(recordLayout)`; keep delegates;
   extract glyph encode/decode to `Ds1Glyphs`.
5. Generalize party edits to `NdsStoryPartyEdits(profile)` with an `NdsStorySaveEditor`
   interface; DS1 editor wraps existing logic byte-for-byte; result classes lose the
   `WorldDs` prefix but keep aliases.
6. `NdsStoryCopySelection` replaces the `isWorldDs` literal; identical DS1 behavior.
7. `NdsStoryStatConversion` keeps `world-ds-stat-v1` math and exposes a policy registry.
8. `SaveReaderRegistry` and `SaveDiscoveryPolicy` take the registered titles; DS1 ranges
   derived, not hardcoded.
9. Collapse duplications found in the inventory: `previousSpeciesIds` twice, MD5 reseal
   twice (`resealHeaders` + `copyHeaderValid`), `384`/`425` literals.
10. App plumbing: replace the ten `== WorldDsSaveReader.GAME_ID` equality sites and the
    enum-literal UI gates with profile capability lookups; `TransferGame.DAWN/DUSK` still
    have no nativeGameId until W3 (UI must not regress while they are empty).

Tests: every existing core/app test stays green, plus new `NdsStoryProfileTest` (registry,
geometry, discovery ranges, byte-identical facade equivalence) and `TransferGameCapabilitiesTest`.

**Acceptance:** no behavior change for World DS; debug assembly; no schema change; plan
checkpoint recorded. **Met** (see delivery note; recorded in `TESTING.md`).

### W2 — Dawn/Dusk evidence pack (M) — **delivered 2026-11-19 (ROM-first)**

Delivery: the layout was recovered by static analysis of the user's USA ROMs (ARM9 + overlays,
Ghidra headless) and verified on two retail saves. Container `0x10000` with `0x7B2C` copies at
`0x0`/`0x7B80`; MD5 header over the copy with the header zeroed; u32 sequence at `+0x18`;
`DSDIG2` magic at `+0x10`; 40 records of `0x164` at `0x288`; party array of ten ids at `0x260`;
last id at `0x25C`; record fields through `0x124` mapped, remainder preserved verbatim. Species
names, base data, lineage and the three 99-level EXP curves were extracted from both ROMs and are
identical between the titles; the extracted Coronamon record reproduces a real save's record
windows exactly. Full offsets, evidence and reproduction commands: `game-save-core/DS2_RESEARCH.md`
(tooling under `analysis/ds2/`). Controlled before/after sample pairs were **not needed** — the
engine code plus two real saves settled every field the adapter interprets. Remaining unknowns:
the meaning of record `0x120` state bits and of `0x128..0x164` (preserved, never interpreted), and
the DigiFarm table (farm transfer stays out of scope).

Original task list (kept for traceability):

- `--inspect-nds-story` on the user's Dawn/Dusk files; `game-save-core/DS2_RESEARCH.md`
  documenting every verified offset/width with method and confidence, and the still-unknown
  list.
- Title-marker study (Dawn vs Dusk), `.dsv` behavior, copy-counter/boot behavior.
- Controlled-sample mapping for party record fields and identity; DigiFarm boundary marked
  inspection-only.
- `tools/extract_nds_story_species.py` + `ds2_species.json` (names, record defaults, lineage,
  stage bands, provenance). Cross-check the extraction against the real save roster.
- Desktop `--inspect-ds2` / generic inspector; PC companion command.
- **Acceptance:** both user saves decode into party records whose names/levels/species match
  the in-game party; MD5 headers re-derived byte-exactly; evidence doc reviewed; no reader
  registered in production yet.

### W3 — DS2 read-only phone parity (M) — **delivered 2026-11-19**

Delivered: `NdsStoryProfiles.DAWN_DUSK` (game id `digimon_world_ds2_nds`, profile
`ds2-usa-raw-v1`, codec 1) is registered, so the shared reader, discovery, copy review and the
error granularity apply unchanged; the reader requires the `DSDIG2` signature so the families
cannot decode each other's bytes. The app resolves the retail title from the file name
(`TransferGameCatalog.titleHint`; Dawn/Dusk both name the same family, a generic name falls back
to Dawn) and the library shows the two entries. DS2 rows use the existing `WorldDsCopyDialog`
(profile-based copy authority) and the native stat rows. JA `transfer_game_dawn` mojibake fixed
(`デジモンストーリー サンバースト`). Not implemented from the original list: a reviewed,
revision-bound title binding (schema 56) and a first-link title prompt — the file name covers the
supported titles for now and the binding remains the documented follow-up. Physical-phone
acceptance was not run.

Original task list (kept for traceability):

- `Ds2Layout` constants and `NdsStoryTitle` entries for Dawn/Dusk; `codecVersion = 1` for the
  DS2 layout; identity scheme `ds2-source-local-v1`.
- Family reader registered; discovery; observations carry `TITLE_VARIANT_REQUIRED` until
  bound.
- Schema 56: `reviewedTitleGameId`, `reviewedTitleRevision`; `bindTitle()`; revision expiry;
  SQLite migration test `tools/test_ds2_title_binding_schema.py`; no per-game tables.
- Link flow version prompt; library "unresolved Dawn/Dusk" row; decoded ids in
  `TransferGameCatalog`; native stat rows through profile strings; copy review/collapse
  gated by capability, not DS literal.
- New localized `ds2_saves.xml` (EN/PT-BR/JA) + generic move/send wording; fix JA mojibake.
- **Acceptance:** link/refresh both titles on a phone; roster matches the game; copy review
  and integrity notices work; any-name/any-folder discovery; distinct error reasons.
  Physical-phone acceptance may be deferred by user request, but desktop/JVM evidence is not
  a substitute.

### W4 — Acquisition, Storage and Home parity (M) — **delivered 2026-11-19**

Delivered: `productionProfiles` gains the Dawn/Dusk entry (party-only, `appPersonalityOnly`,
provisional identity, level range 1..99, identity scheme `ds2-source-local-v1`).
`NdsStorySpeciesTables` resolves each game's own table by (gameId, profile, codec); the DS2
asset (`app/src/main/assets/ds2_species.json`, 436 species with names, base data, lineage and
EXP curves, extracted from the user's ROMs) serves Dawn and Dusk identically, and auto-identity
runs before any review through the normal path. `NativeMaterializationRepository` seeds the first
materialization and applies returns through `Ds2StatConversion` (authored `ds2-stat-v1`: level,
HP, ATK, DEF and max-level/aptitude project; the nine native EXP channels are retained and only
the three training counters are interpreted). Pending: the destination preview title wording for
DS2 (`W5`) and phone acceptance.

Original task list (kept for traceability):

- `GameImportAcquisitionRepository.productionProfiles` gains Dawn and Dusk entries
  (`appPersonalityOnly`, party-only, provisional identity, level range verified).
- Species tables resolved by `gameId`; auto identity through the DS2 ROM table; reviewed
  mapping wins; unresolved names stay explicit.
- Materialization, `applyWorldDsInitialStats`-equivalent, refresh continuity, presence,
  reconciliation gates generalized from DS literals to profile lookups.
- `NdsStoryStatConversion` DS2 policy declared: native values retained exactly; no invented
  app growth until field mapping (mirroring DS1's pre-policy state).
- **Acceptance:** party Digimon → Storage → shared Home → refresh; no duplicate receipts; no
  fabricated native personality; World DS flows unchanged.

### W5 — Destination preview and progress parity (S/M) — depends W4

- `DestinationPreviewPlanner`/`DestinationPreviewRepository` profile-aware format verdicts,
  identity/capacity blockers, progress assessment for DS2; report remains non-executable
  until W6.
- Dialog labels use the destination title; batch selection unchanged.
- **Acceptance:** previews for Dawn/Dusk are honest (copy vs conversion, blockers, progress)
  and flag every unvalidated field.

### W6 — Writer parity A: exact retained round trip (L) — **core editor delivered 2026-11-19**
**App repository wiring still pending.**

Delivered in core: `Ds2SaveEdits` / `Ds2SaveEditor` (registered in `NdsStorySaveEditors`) with
revision-bound removal (party reference + record slot cleared to the game's own empty shape,
last-party-member refusal), exact retained insertion (first free record slot, first free party
slot by the game's own rule, last-id high-water raise only when needed), replacement, party
admission and byte-exact restore. Every edit edits only the authoritative copy, refuses a copy
whose MD5 header does not match, bumps the copy sequence by one, reseals the header, and re-reads
the candidate requiring every unrelated record unchanged. Verified on the two retail saves
(`Ds2RealSaveValidationTest`: removal + restore rebuilds the exact real bytes; the candidate
header validates). Also verified by synthetic unit tests (`Ds2SaveEditsTest`).

App wiring delivered too: `ExperimentalWorldDsMoveRepository` and
`ExperimentalWorldDsSendRepository` now resolve the import profile and the native editor per
observation instead of a single World DS profile, so Move to Storage / Move to game work for
Dawn/Dusk party records through the same journal, durable backup, fresh-read preflight, readback
verification and presence machinery. Record synthesis is implemented for these titles too: app-only partners (and stored partners
whose app growth exceeded the archived record) are projected into a `0x164` record from the game's
own species defaults plus the app stats, with the level capped by the species max level, the
history block rebuilt exactly like the engine's creation path, and the nine native EXP counters
left zero because their semantics are not established. A retained record is used as the seed, so
its identity and unknown bytes survive. A cross-layout send is supported the simple way: the individual is already in Storage, so a
destination title that differs from its last native layout receives a freshly synthesized record
for its own layout (the retained bytes are only reused inside their own layout). In-game boot
acceptance of a written Dawn/Dusk save is still unperformed.

Original task list (kept for traceability):

- `Ds2SaveEditor`: remove a party record (clear slot, adjust roster/reference data), reseal
  both copies, insert the exact retained `0x164` record into an evidenced empty slot, admit
  to party, restore candidate byte-exactly; refuse ambiguous/unknown shapes.
- Generalized move/send repositories, blockers, journal/recovery/backups/presence,
  profile-sized write buffers and readback re-parse; desktop rehearsal for DS2.
- Blocker for DS2 until `lastId`/empty-slot/party-cap semantics are observed: only paths
  the evidence supports.
- **Acceptance:** desk rehearsal on copies; user in-game boot test per title; only then
  flags move from experimental to supported-for-that-rung; source removal only after
  readback+acknowledgement exactly as DS1.

### W7 — Writer parity B: generated records and cross-title sends (L) — **DS2 record synthesis delivered 2026-11-19**

Delivered: `Ds2RecordCodec` (core) builds a `0x164` record exactly like the engine's creation path
(species fields, abilities/skills/resistances, stage flags, five stage-indexed forms, the species
history bitmap, stage byte, EXP-to-next from the ARM9 curves); `Ds2SaveEdits.insertGeneratedRecord`
assigns the next identity from the destination high-water mark and inserts it; the app's
`Ds2OutboundConversion` projects app stats through `Ds2StatConversion.toNative` (level capped by
the species max level) and feeds it from `Ds2SpeciesCrosswalk.ds2IdsByCanonical`; the send
repository chooses retained vs generated per destination and seeds generation with the retained
record so identity and unknown windows survive. Remaining from this work item: direct
cross-title conversion (a World DS record into Dawn or the reverse) — it stays refused and belongs
to the Storage-conversion path; and in-game acceptance of a synthesized record.

Original task list (kept for traceability):

- DS2 record generator from app state: species defaults, moves/traits/equipment mapping,
  EXP/level windows, stage/history; every unmapped field blocks rather than guesses.
- App species → DS2 species id crosswalk from `ds2_species.json`; version differences between
  Dawn and Dusk handled by the bound title.
- `Storage → Dawn/Dusk` for individuals from any origin; presence and return flow applies.
- **Acceptance:** app-created partner sent into each title, boots, round-trips; no invented
  fields; conversion policy documented and versioned.

### W8 — UI, docs and PC parity (M)

- Origin-list rows fully driven by profile capabilities; send destination list includes
  Dawn/Dusk when writers exist; blocker strings per profile in EN/PT-BR/JA.
- README, `pc-save-companion/README.md`, `WORLD_DS_RESEARCH.md` cross-link,
  `game-save-core/DS2_RESEARCH.md`, art/research credits (facts only, no code/assets).
- Backups screen labels per profile (remove `world-ds-original.sav` hardcode).
- **Acceptance:** docs match capability flags; no claim exceeds accepted evidence.

### W9 — Optional follow-ups (not this step)

- JP Sunburst/Moonlight (`A3VJ`/`A6RJ`), PAL/other regions, no$GBA/container variants.
- DigiFarm transfer if separately requested.
- Lost Evolution and later Story titles as their own plans.

---

## 8. Verification and acceptance

### Offline (every work item)

```powershell
.\gradlew.bat :game-save-core:test :app:testIntegrityCheckUnitTest `
  --tests "*source.gameSave.*" --tests "*navigation.*" --tests "*species.*" `
  --tests "*screens.transferScreen.*" :app:assembleDebug `
  :app:compileIntegrityCheckAndroidTestKotlin :pc-save-companion:test `
  :pc-save-companion:distZip --offline --console=plain --max-workers=1 `
  "-Pkotlin.daemon.jvmargs=-Xmx4g"

py -3 -B -m unittest tools.test_participation_schema tools.test_transfer_continuity `
  tools.test_world_ds_acquisition_schema tools.test_ds2_title_binding_schema
```

### Test matrix (new cases)

| Layer | Cases |
|---|---|
| Core profile/reader | DS1 golden observations unchanged through the family reader; DS2 container/geometry/glyph/decode from anonymous fixtures shaped by G1/G2; raw/`.dsv`; any-name discovery; granular failures; layout resolution never ambiguous with DS1 |
| Core copy/editor | DS2 newest-valid-copy with single-byte counter; broken header ignored; revision-bound review; removal/insertion windows; both-copy reseal; byte-identical restore; refusals for unmapped/ambiguous shapes |
| Core title binding | binding applies only to its revision; expiry on new revision; wrong-title binding does not silently decode as another title; acquisition/send gated until bound |
| App | profile registry and capability gating; species tables per game; auto identity/reviewed mapping; outbound/receive conversion policy versioning; schema 56 migration; send destination list; library grouping; JA/PT-BR/EN string parity |
| Companion/desktop | `--inspect-ds2`, generic inspector, preview, rehearsal; no writes to source |
| androidTest | link → bind → roster → copy review → snapshot import → Storage → Home → send/move on temporary documents; presence/recovery |

### Real-save and in-game acceptance (per title, user-assisted)

1. Copy the save off the device/emulator; record SHA-256; keep the original untouched.
2. Read with the family reader; verify copy headers and reported roster against the game.
3. Build any candidate strictly into a separate output directory (never the save folder).
4. Verify the candidate: header integrity recomputed, diff confined to the expected windows,
   full re-parse equivalence, source revision unchanged.
5. User swaps the candidate into the emulator save folder and boots the retail game:
   - no "no save data" / no automatic card erase,
   - expected party/roster change visible,
   - unrelated state (money, tamer rank, items, DigiFarm) intact,
   - the game saves normally afterwards.
6. Copy the post-save file back; re-read; confirm the tool still parses it and the change
   persisted. Only then mark that capability accepted for that title/region/profile.
7. Record the aggregate receipt in `TESTING.md`; keep private bytes out of the repo.

Because a failed DS boot has been observed to make DraStic erase the card silently, every
test uses a backup and the tool never writes the source.

---

## 9. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| 1 | DS2 record layout beyond moves is undocumented; DigiFarm/bank regions unknown | reader/roster/send may stall on fields | G2/G3 controlled samples; ship read-only party scope first; farm excluded; no guessed writes |
| 2 | "Same engine" misread as "same layout" | corrupted saves if DS offsets are reused | layout profiles; constants only after G1/G2; DS1 tests pin unchanged behavior |
| 3 | No in-save Dawn/Dusk discriminator | mislabelled version, wrong defaults | reviewed revision-bound binding (schema 56); optional auto-detect only with confidence evidence |
| 4 | Species space difference (426 vs 398, DS2 IDs `0x41–0x1F4`) | wrong identities/sends | per-title tables + crosswalk; never reuse DS ids; extraction tool verifies ROM hash/game code |
| 5 | Text glyph table differs | garbled names / unreviewable identity | DS2 glyph codec verified against a real name; unknown glyphs stay explicit |
| 6 | MD5 reseal/copy policy unverified for DS2; boot failure erases card | data loss in testing | use copies+backups; G5 boot protocol; keep flags false until pass |
| 7 | Refactor regresses World DS | existing users lose a working path | W1 is behavior-preserving, golden tests, checkpointed tests/app build; app facades retained |
| 8 | Schema 56 migration bug | DB damage | additive columns only, preserving factory, SQLite migration test seeded at 55 |
| 9 | Licensing: research notes are "educational use only" (no license grant); pinned ROM editor is GPL-3.0 | contamination if copied | facts-only reuse, independent implementations, citations, no vendored code/assets/ROMs; user supplies own ROMs/saves |
| 10 | Scope inflation (two titles + family refactor) | half-finished features | ordered WBS; each capability accepted per title; no farm/JP/other titles in this step |
| 11 | "Works exactly like DS" interpreted as UI clone while DS still has open rungs | false parity claims | parity matrix + capability flags + TESTING evidence rule; send/generate rows explicitly later |
| 12 | Phone acceptance deferred | platform bugs found late | keep androidTest coverage and desktop parity; mark acceptance boundary honestly |

---

## 10. Decisions and inputs needed from the user

1. **Saves.** One Dawn USA raw `.sav`/`.dsv` and one Dusk USA raw `.sav`/`.dsv` written by
   real play (fresh and/or progressed). More progress states and multiple party members make
   G2 much faster. Controlled before/after pairs (section 6, G3) only for the fields needed.
2. **ROMs.** USA Dawn (`A3VE`) and Dusk (`A6RE`) ROMs for species/base-data extraction, or
   run `tools/extract_nds_story_species.py` yourself and share only the JSON. No ROM or save
   bytes will be committed or published.
3. **Order.** Recommendation: build the family refactor and DS2 evidence pack, then accept
   Dawn first and repeat the acceptance ladder for Dusk; the layout code is shared, so Dusk
   mainly re-verifies its own save. Alternative: both in lockstep, slower to first acceptance.
4. **Title binding UX.** Approve the one-time "Is this Dawn or Dusk?" prompt for DS2 saves,
   repeated only when the file's content revision changes, if no reliable in-save marker is
   found.
5. **Scope confirmations.** Farm/DigiFarm transfer stays excluded; USA first; no JP/other
   regions in this step; direct save-to-save still out of scope.
6. **In-game testing availability.** Who performs the swaps/boots (as with World DS) and when.

---

## 11. References

- Pinned ROM-format reference (GPL-3.0, evidence only, no code vendored):
  [joaomlsantos/DigimonNDSRomEditor @ cc3ff499](https://github.com/joaomlsantos/DigimonNDSRomEditor/tree/cc3ff499edfd86e382e724d1da7f155b6b830e5f) —
  `digimon_core/rom.py`, `constants.py` (header table, per-title ROM table addresses,
  398-entry ID list), `loaders.py`, `model.py`, `strings.py`.
- Dawn/Dusk research notes (facts only; the repository is provided "for educational and
  community use only", which is not a license grant):
  [joaomlsantos/Digimon-World-Dawn-Dusk-Research-Notes](https://github.com/joaomlsantos/Digimon-World-Dawn-Dusk-Research-Notes) —
  `save_data_editing.txt`, `nickname_size_change.txt`, `data locations_{dawn,dusk}.txt`,
  `base_data_digimon_table.txt`, target-ROM MD5s.
- DeSmuME forum, 512 kbit / 164-byte `.duc` header:
  <https://forums.desmume.org/viewtopic.php?id=9573>.
- GBAtemp Dawn AR-code thread (record stride corroboration):
  <https://gbatemp.net/threads/digimon-world-dawn-u.516124/>.
- Wikipedia, engine/release/party-size/DigiFarm background:
  <https://en.wikipedia.org/wiki/Digimon_World:_Dawn_and_Dusk>.
- GameTDB cover provenance and game codes already in
  [app/src/main/assets/transfer_game_art_credits.md](app/src/main/assets/transfer_game_art_credits.md).
- Internal: [MULTI_GAME_TRANSFER_PLAN.md](MULTI_GAME_TRANSFER_PLAN.md),
  [TESTING.md](TESTING.md),
  [game-save-core/SAVE_READING_STANDARD.md](game-save-core/SAVE_READING_STANDARD.md),
  [game-save-core/WORLD_DS_RESEARCH.md](game-save-core/WORLD_DS_RESEARCH.md),
  [game-save-core/WORLD_DS_STAT_CONVERSION.md](game-save-core/WORLD_DS_STAT_CONVERSION.md),
  [pc-save-companion/README.md](pc-save-companion/README.md).

---

## Appendix A — Concrete touch map (current code)

Core (`game-save-core/src/main/kotlin/com/github/nacabaro/vbhelper/gamesave/`):

- `WorldDsSaveReader.kt` — container/geometry/identification; becomes a facade over
  `NdsStorySaveReader`.
- `WorldDsResearchInspector.kt` — block decoder + footer unwrap; generalize layout.
- `WorldDsRecordCodec.kt` — 384-byte record fields/generation; generalize record layout.
- `WorldDsPartyEdits.kt` — remove/insert/replace/admit/restore + MD5 reseal; becomes
  `Ds1SaveEditor` behind `NdsStorySaveEditor`.
- `WorldDsCopySelection.kt` — copy authority; generalize profile match.
- `WorldDsStatConversion.kt` — DS1 stat policy; keep as policy.
- `SaveModels.kt` — `SaveObservation`, `SaveCapabilities`, `SaveDiscoveryPolicy`.
- `SaveReaderRegistry.kt` — reader registration and `AMBIGUOUS_FORMAT` contract.
- `WorldDsSpeciesDefaults.kt` — stage bands/EXP curves; becomes per-title species data.
- `SaveInspector.kt` — desktop CLI; add generic `--inspect-nds-story`/`--inspect-ds2`.

App (`app/src/main/java/com/github/nacabaro/vbhelper/`):

- `navigation/TransferGameCatalog.kt` — enum ids; already has DAWN/DUSK shells.
- `source/gameSave/GameImportAcquisitionRepository.kt` — `productionProfiles` gate.
- `source/gameSave/WorldDsSpeciesTable.kt`, `WorldDsSpeciesCrosswalk.kt`,
  `NativeSpeciesResolution.kt`, `NativeSpeciesPresentationRepository.kt`,
  `NativeMaterializationRepository.kt` — species/mapping/materialization; replace single
  table type with per-game registry.
- `source/gameSave/ExperimentalWorldDsMoveRepository.kt`,
  `ExperimentalWorldDsSendRepository.kt`, `ExperimentalMoveJournal.kt` — generalize editor
  and profile lookups; journal sizing from profile.
- `source/gameSave/LinkedGameSaveRepository.kt`, `AutomaticSaveReconciler.kt`,
  `SaveDocumentWrite.kt` — copy selection/title binding/revision checks, write size.
- `screens/transferScreen/*` — library/workspace/send/details/copy dialogs; capability and
  string generalization; new version prompt.
- `database/AppDatabase.kt`, new `Ds2TitleSchema.kt` (schema 56) — additive columns.
- `di/DefaultAppContainer.kt` — profile/species/editor wiring.
- `res/values*/` — `ds2_saves.xml`, `transfer_workspace.xml`, JA mojibake fix.

Tools/assets/tests:

- `tools/extract_nds_story_species.py` (new), `app/src/main/assets/ds2_species.json` (new).
- `tools/test_ds2_title_binding_schema.py` (new).
- `pc-save-companion/README.md`, `game-save-core/DS2_RESEARCH.md` (new).
- New/parameterized tests listed in section 8.

## Appendix B — Rollout and capability honesty

- World DS stays exactly as accepted; refactor must be invisible to users.
- Dawn/Dusk capability flags: `canView` once W3 passes; `provisionalSnapshotCopy` once W4
  passes; `canRemove`/`canInsert` only per title after W6/W7 in-game acceptance. Until then
  the UI shows the real blocker, never a fake "ready".
- Title binding and copy review are persisted authorities bound to content revisions; new
  game saves always force re-review, never silent continuation.
- Every send/move keeps a durable pre-write backup, journals phases, readback-verifies and
  marks presence only after verification, as World DS does today.
- `TESTING.md` is the evidence ledger: each checkpoint records command, counts and the
  explicit boundary between offline evidence and real-game acceptance.