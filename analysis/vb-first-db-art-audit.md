# VB-first baked-sprite fallback — SQL `DisplaySprite` / World-Radar / Transfer audit

Task: `task-2` ("Audit SQL DisplaySprite + world/transfer surfaces for VB-first baked-sprite fallback").
Mode: read-only audit. The only file written is this report. No gradle/build was run.
Repo: `C:\Users\julye\IdeaProjects\vbhelper` (app "Tamers Lab").
Snapshot: **2026-11-20 17:05 (America/Bahia)**, working tree (uncommitted WIP included). `AppDatabase` in the
working tree is already at **version 58** (`AppDatabase.kt:96`), `DisplaySprite` view definition is schema 54
(`SpritePreferenceSchema.kt`), and `git status` shows unrelated in-flight work (transfer/rank/freechat/…).

> ⚠️ The tree is moving while this audit was produced. Between 16:56 and 16:57 another session added the
> *first half* of the Kotlin fix this report recommends (`BundledVbSprites.kt` is untracked; four existing files
> were modified). Section 1.4 and all line numbers reflect the tree as of the snapshot above; re-read the files
> before applying anything from this report.

---

## 0. TL;DR — the recommendation the Lead asked for

**The SQL layer can and should be left untouched.** `DisplaySprite` can only return `Sprite` rows — uniform
`width*height*2` little-endian RGB565 blobs with green-screen transparency (`SpritePreferenceSchema.kt:7-18`,
`BitmapData.createARGBIntArray()` at `BitmapData.kt:140-153`). Baked VB art is 13,643 individually cropped RGBA
PNGs with per-pose sizes (`assets/battle_sprites/extracted_assets/sprites/`), so it cannot be represented by the
view without a large DB install + view migration. The existing architecture already resolves "baked VB" in
Kotlin for Arena portraits (`ArenaWidgets.kt:66-83`) and the correct fix is to extend that same Kotlin resolver —
**O2, no schema migration, no `DisplaySprite` change, DOT_FIRST untouched.**

Smallest *complete* fix for the owner's bug (un-owned species shown as Dot in VB_FIRST):

1. **Keep the in-flight O2 core** (already in the tree): `cardSlots` → asset-id mapping + shipped-VB frame
   provider + `SpeciesAppearanceRepository.resolve/portrait` preferring `VB_ASSET` over Dot. It already fixes
   **battle fighters/result forms** and **transfer/native-save portraits** (see §1.4, §2).
2. **Add the missing piece: World/Radar spawn artwork.** Wild spawns still render the `DisplaySprite` Dot bytes
   (`WorldSpawnDao.kt:109-116` etc. → `RadarPresentation.spriteFrame()` → 2D/FP/iso renderers and the encounter
   sheet). Map `SpawnWithDetails` in `WorldRepository.observeSpawns()/observePendingRecruits()` using the same
   `resolve()` decision and the new shipped-VB frame loader, converting frames to RGB565 for the existing
   renderers. Exact files/edits in §4.
3. **Leave Dot (documented, not a regression)** on surfaces that show *owned* catalog definitions
   (Home/Storage/widget/farm/Adventure) and on the world encounter-detail dialog/radar player token; those are
   Phase 2 (also Kotlin-only, no migration) and are not "un-owned wild species" surfaces.

`O1` (DB sprite install + mapping table + view change) and `O3` (build-time RGB565 pack) are technically possible
but materially worse for this cosmetic fallback: O1 needs a 58→59 migration + ~7,800 inserted `Sprite` rows
(~42 MB, measured) + losing per-frame dimensions; O3 has the same DB semantics plus ~40 MB of new APK assets.
Details and measured costs in §3.

---

## 1. Ground truth in the current tree

### 1.1 The `DisplaySprite` view (schema 54) — exact VB_FIRST order

`app/src/main/java/com/tamerslab/data/database/SpritePreferenceSchema.kt:24-35`:

```
JOIN Sprite s ON s.id = CASE WHEN <mode> = 'DOT_FIRST'
    THEN COALESCE(choice.spriteId < -1, dot.spriteId, bundled.catalogSpeciesId sprite, choice.spriteId >= 0,
                  c.cardId IS NOT NULL -> c.spriteId, native imported same-species, c.spriteId)
    ELSE COALESCE(choice.spriteId >= 0,                       -- 1. user VB choice
                  CASE WHEN c.cardId IS NOT NULL THEN c.spriteId END, -- 2. imported card art
                  (native.spriteId … native.speciesId = c.speciesId), -- 3. imported same-species art
                  choice.spriteId < -1,                       -- 4. explicit Dot choice
                  dot.spriteId,                               -- 5. SpeciesDefaultSprite (automatic Dot)
                  c.spriteId)                                 -- 6. row sprite
    END
```

* The view is declared once as a Room `@DatabaseView` (`domain/species/SpeciesCustomization.kt:29`) and is
  re-created by `MIGRATION_53_54` (`AppDatabase.kt:297-301`). The same constant is the only current definition;
  historical schema objects (`SpeciesCustomizationSchema.kt:7-28`, `ModeSpriteSchema.kt:7-30`,
  `SourceIndependentSchema.kt:40`) are replay-only migration history.
* There is **no column and no candidate for APK-bundled PNG art** anywhere in the view. Baked VB art is only
  reachable from Kotlin.

### 1.2 Why an un-owned species resolves to Dot today

Un-owned species are represented by `ExpandedCatalogRepository` "catalog definitions":
`CardCharacter(cardId = null, catalogSpeciesId = species.id, speciesId = species.id, spriteId = dot set sprite)`
(`ExpandedCatalogRepository.kt:94-101`), plus a `SpeciesDefaultSprite` row
(`ExpandedCatalogRepository.kt:46-48, 137-139`). Therefore in `DisplaySprite`:

* branch 1 (`choice >= 0`) — only if the owner explicitly chose a VB sprite;
* branch 2 — never (`c.cardId IS NULL`);
* branch 3 — only if the owner imported a DiM/BEM containing the same species (bindings are backfilled by
  `syncImportedBindings`, `ExpandedCatalogRepository.kt:108-126`);
* branch 4 — explicit Dot choice;
* **branch 5 wins for every catalog definition that has a bundled Dot set** → Dot. That is the reported bug.

For the 42 baked-VB-only species (no Dot set) the definition keeps `PlaceholderSprite.ID` (32×32 diamond), and
`WorldSpawnSelector`/`WorldRepository` filter placeholder characters out of wild spawns
(`WorldSpawnSelector.kt:38-41`, `WorldRepository.kt:194-195, 260-263`, `WorldSpawnDao.kt:74-83`).
DOT_FIRST order is unchanged by anything recommended here.

### 1.3 Measured catalog/asset facts (python inspection of `app/src/main/assets`)

| Fact | Value |
|---|---|
| `species_catalog.json` species / sprite sets | 1662 / 3368 (1650 `kind="DIGIMON"`) |
| DIGIMON with `cardSlots` (baked VB available) | **650** (861 card-slot refs overall) |
| DIGIMON with `dotSprites.setIds` | 1211 |
| baked VB **and** Dot | **608** ← population affected by the bug in wild/radar |
| baked VB only (no Dot) | 42 (placeholder today, `-1`) |
| Dot only (no baked VB) | 603 (correctly Dot) |
| neither artwork | 397 (placeholder, not spawnable) |
| card-slot refs whose asset dir exists with exactly 12 `_01.._12.png` | **851/851** |
| sprite asset dirs / PNG frames / bytes | 1137 dirs, 13,643 frames, 41.7 MB |
| PNG frame size distribution | mostly 64×56 (2232); min 20×11, max 132×160 |
| avg RGB565 bytes per frame | ~5,422 B → ~65 KB/species × 12 poses; **~42.3 MB for 650 species** |
| existing Dot DB install (1211 ready default sets, 12 poses) | **~65.5 MB** of `Sprite` rows (dims 36/48/72/100) |
| `dot_sprites.zip` | 12,995,584 B (13.0 MB) |
| species with >1 cardSlot | 160 (first slot ordering may start with tintable DiM 124, e.g. Agumon `['124:3','12:3']`) |

`cardSlots` → asset id is exactly `dim%03d_mon%02d` (same convention as `ArenaSpeciesIndex.kt:33`,
`AssetSpeciesNames.kt:18`, `BundledVbSprites.vbCharacterId()` line 85). `cardSlots` was unused in Kotlin before
the in-flight patch — the only prior use of the JSON mapping was the runtime name index in `AssetSpeciesNames`.

### 1.4 In-flight O2 core already in the working tree (uncommitted/untracked)

Added between 16:56–16:57 (not committed; `BundledVbSprites.kt` is untracked):

* `species/CanonicalSpeciesCatalog.kt:96-114` — `bundledVbAssetIds(species)`: parses `cardSlots`, sorts
  deterministically (lowest card/mon; DiM 124 as last resort, `:135-136`).
* `species/BundledVbSprites.kt:24-85` — shipped-VB provider: `assetIdFor(species)` (first candidate whose asset
  folder exists, `:39-42`), `frames(assetId)` (`:45-53`), `bitmap(assetId, frame)` (`:56-66`),
  `portrait(assetId)` (`:69-71`), shared `VB_BATTLE_POSES` (`:79-82`) and `vbCharacterId` (`:85`).
* `species/BundledDotSpriteLibrary.kt:24-30` — `bundledVbSprites` lazy singleton shared by consumers.
* `species/SpeciesAppearanceRepository.kt:19-45` — `resolve()` now derives `vbAsset = vbAssetId ?: library.
  bundledVbSprites.assetIdFor(species)` (`:37`) and passes it to `SpeciesArtworkPolicy` (`:38`), so in VB_FIRST
  the order becomes choice → imported → **shipped VB** → Dot; `portrait()` (`:50-70`) was rewritten onto the same
  policy and `VB_ASSET` now decodes `BundledVbSprites.portrait` (`:64`), fixing transfer portraits.
* `battle/IndividualSpriteManager.kt:20, 28-67, 86-94` — frame loading understands `Artwork.assetId`.
* `battle/ui/offline/TrainingBattlePresentation.kt:187, 209-214, 435-449` — `preferredSpeciesPoses()` prefers
  chosen/imported VB rows, then shipped VB frames, then Dot; used by fighters and finisher/result forms
  (`:243, :276, :359`). This fixes wild/arena **battle** artwork even when `externalCharacterId` is `app_mon…`.

`SpeciesArtworkPolicy` itself is unchanged (`SpeciesArtworkPolicy.kt:6-13`; DOT_FIRST-first branch intact), and
`spriteFor()` (`SpeciesAppearanceRepository.kt:47`) now returns `null` for `VB_ASSET` — it has no external callers
after the patch (`grep`), but it is an API trap for future code.

---

## 2. Surface-by-surface map (what reads art where, and what a non-DIM species shows)

"non-DIM species" = catalog definition (`catalogSpeciesId != null, cardId = null`) that is not represented in the
owner's imported gallery. Status is *as of the snapshot* (pre-remaining-radar-work; in-flight O2 already applied).

| # | User-visible surface | Art path (file:line) | Non-DIM species possible? | VB_FIRST today | Status after in-flight O2 |
|---|---|---|---|---|---|
| 1 | **World/Radar entity art** (2D marker, first-person, isometric; also encounter bottom sheet art) | `WorldSpawnDao.kt:109-116` (bytes) → `WorldScreen.kt:618-630` (2D), `RadarFirstPersonViewport.kt:182-201`, `RadarIsometricViewport.kt:427-445`; sheet `WorldScreen.kt:2075-2083` | **Yes** — wild pool = imported-enabled cards + *all* catalog definitions (`WorldRepository.kt:260-263, 290-336`); guaranteed first partner is a catalog definition (`WorldRepository.kt:106-135`) | **Dot** (DisplaySprite branch 5) | ❌ **still Dot — main remaining gap** |
| 2 | World encounter long-press details dialog | `WorldScreen.kt:1538-1545` → `DexDao.getCharacterProgress` (`DexDao.kt:61-87`, `DisplaySprite` join `:81`) → `DexCharaDetailsDialog` (`DexCharaDetailsDialog.kt:48`) | Yes (catalog spawn) | Dot | ❌ still Dot (secondary dialog) |
| 3 | **Wild/Radar battle fighters + result forms** | `RadarBattleFlow.kt:45-95` → `OfflineBattleParticipant` → `TrainingBattlePresentation.kt:243, 359` (poses by `speciesName`) | Yes | Dot before patch | ✅ fixed by `preferredSpeciesPoses` (`TrainingBattlePresentation.kt:435-449`); `externalCharacterId` still `app_mon…` (only affects attack-sprite pick, see risks) |
| 4 | Arena tamer rosters/opponent portraits + arena battles | `ArenaSpeciesIndex.kt:28-66`, `ArenaWidgets.kt:66-83`; battle via `TrainingBattlePresentation` | Yes | already VB via assetId, Kotlin fallback returned Dot for catalog-def entries | ✅ VB asset (derived or explicit) |
| 5 | Radar **player token** (active partner) | `WorldScreen.kt:395-413` ← `UserCharacterDao.getActiveCharacter()` (`UserCharacterDao.kt:251-282`) | Owned catalog definition (world recruit / native materialization) | Dot | ❌ Dot (Phase 2) |
| 6 | **Transfer / native game-save previews** (workspace tiles, away records, archive tiles, move dialogs) | `TransferIndividualInformation.kt:41-59` (`TransferSpeciesPortrait`) and `NativeArchiveStorageEntry.kt:41-80` → `SpeciesAppearanceRepository.portrait` (`SpeciesAppearanceRepository.kt:50-70`) | **Yes** — native save records are never in the DIM gallery | Dot (portrait ignored VB asset) | ✅ fixed: `VB_ASSET` portrait (`:64`) |
| 7 | Home active-partner core art | `home/ui/screens/UnifiedHomeScreen.kt:60-75` (other home layouts similar) ← `UserCharacterDao.getAllCharacters()` (`UserCharacterDao.kt:105-134`) | Owned catalog definition | Dot | ❌ Dot (Phase 2) |
| 8 | Storage grid / storage details | `StorageScreen.kt:542-552`, `StorageDialog.kt:145,206` ← `UserCharacterDao` | Owned catalog definition | Dot | ❌ Dot (Phase 2) |
| 9 | Home-screen Digimon widget | `DigimonWidgetProvider.kt:254-309` ← `UserCharacterDao.getActiveCharacterWidgetSprites()` (`UserCharacterDao.kt:428-447`) | Owned catalog definition | Dot | ❌ Dot (Phase 2, widget needs bitmap path) |
| 10 | Farm diorama | `FarmRosterArtwork.kt:110-151` + `DigifarmDao.kt:78-97` (`DisplaySprite`), Dot path installs from catalog | Owned catalog definition | Dot | ❌ Dot (Phase 2) |
| 11 | Adventure roster | `AdventureDao.kt:24-43` (`DisplaySprite`) | Owned catalog definition | Dot | ❌ Dot (Phase 2) |
| 12 | Dex main screens (per card) | `DexDao.kt:52-59, 81` ↔ `WHERE c.cardId = :cardId` | **No** — Dex is per imported Card; catalog definitions only appear via `getCharacterProgress(id)` used by #2 and Home details | n/a | not affected |
| 13 | Scan collection / fusions / evolution requirements | `DigimonScanDao.kt:39-65` (JOIN Card), `CardFusionsDao.kt:32-50` (destination.cardId = source.cardId) | No (imported cards only) | n/a | not affected |
| 14 | Wild NPC chat/social avatar | `CharacterDao.getWildCharacterInfo()` (`CharacterDao.kt:69-86`) is used for prompt/personality (`ChatRepository.kt:671`, `WorldSocialRepository.kt:19`), not for rendering art | n/a | n/a | no user-visible art |
| 15 | World Dim settings/debug pickers | `WorldScreen.kt:2297-2315`, `CharacterDao.kt:40-50` | imported/debug only | n/a | not affected |

Notes:
* `DisplaySprite` is also read by `SpriteDao.getRenderableCharacterIds()`/`getForCharacter()` (`SpriteDao.kt:28-30,
  72-74`), `CharacterDao.getDebugCatalogCharacters()` (`:40-50`), `CharacterDao.getWildCharacterInfo()` (`:69-86`),
  `DigimonScanDao` (imported), `DexDao`, `CardFusionsDao` (imported), `AdventureDao`, `DigifarmDao`,
  `UserCharacterDao` — all rows above are covered; Dex/scan/fusion are imported-only.
* `WorldSpawnDao`'s `externalCharacterId` (`10-103, 147-148, 191-192`) uses `COALESCE('dim…', 'app_mon'||id)` —
  for catalog definitions it is `app_mon…`, which is why the battle code cannot load baked frames from it and why
  the `speciesName`-based Kotlin resolve matters.

---

## 3. Options analysis

### O1 — install baked VB frames as `Sprite` rows + mapping table, extend the view

What it would take:
* Convert 650 species × 12 cropped RGBA PNGs into uniform `Sprite` frames: `Sprite` has one `width`/`height` for
  all 12 poses and the view validates `length(frame) == width*height*2` (`SpritePreferenceSchema.kt:8-18`);
  baked frames differ per pose (64×56 typical, 20×11…132×160), so every pose must be padded/cropped to a common
  canvas. Format is internal RGB565 little-endian with green (0x07E0) transparency (`BitmapData.kt:140-153`,
  `SpriteData.kt:85-127`).
* Reserve negative IDs disjoint from Dot sets and `PlaceholderSprite.ID == -1`; add a mapping table
  (`speciesId → assetId → spriteId`), install ~7,800 rows (~**42 MB** measured, more with padding) during
  `ExpandedCatalogRepository.ensureReadyDefinitions()` (which already installs ~65.5 MB of Dot rows by decoding
  1,211×12 pre-baked `.bin` frames — `ExpandedCatalogRepository.kt:28-29`, `BundledDotSpriteLibrary.kt:75-101`).
* Change `DISPLAY_SPRITE_QUERY` (insert baked between imported/native and the automatic Dot default) → the
  `@DatabaseView` identity hash changes → **version 58→59 + MIGRATION_58_59** (drop/recreate view + table),
  `app/schemas/.../59.json`, and every migration-open path; historical schema constants are replay-only but the
  parity must be verified. The view's `JOIN Sprite s` is INNER: a partially installed mapping row would make the
  whole `CardCharacter` lose its display row, so install must be atomic/validated.
* All SQL consumers (§2 #1,#2,#5,#7-#11) would light up at once; DOT_FIRST would stay as-is if the baked candidate
  is only inserted in the VB branch.

Cost/risk: high (startup PNG decode for 7,800 frames, +42 MB DB, frame-size/aspect loss, migration + view
consistency + host SQLite parity tests, all schema/DAO tests). **Not justified for a cosmetic fallback**, and the
owner explicitly asked not to destabilize migrations unless justified.

### O2 — keep SQL untouched, finish Kotlin resolution (recommended)

* Battle/arena/result-form art: already fixed by in-flight `preferredSpeciesPoses`
  (`TrainingBattlePresentation.kt:435-449`) + `SpeciesAppearanceRepository` VB_ASSET; `IndividualSpriteManager`
  handles asset ids.
* Transfer/native-save portraits: already fixed by `portrait()` VB_ASSET path
  (`SpeciesAppearanceRepository.kt:50-70`); callers are unchanged because they call `portrait(species.id)`.
* **Remaining: world/radar spawn DTO bytes.** One choke point feeds all three radar renderers and the encounter
  sheet: `WorldRepository.observeSpawns()` (`WorldRepository.kt:61-66`), `observePendingRecruits()` (`:68-69`),
  `getSpawn()` (`:581`). Map each row: if it is a catalog definition (`cardId == null`) and
  `appearances.resolve(row.speciesName)` returns `Artwork.assetId != null` (VB_ASSET), replace
  `spriteIdle/spriteIdle2/spriteWalk/spriteWalk2/spriteRun/spriteRun2` with shipped-VB frames converted to
  RGB565 (uniform canvas = max dims of the used poses, centering the crop) and update `spriteWidth/Height`;
  leave `externalCharacterId` **unchanged** so wild AI/technique selection
  (`EncounterBattleProfiles.wild`, `EncounterBattleProfiles.kt:10-33`) and `RadarBattleFlowTest.kt:84-88` stay
  stable. DOT_FIRST rows take the existing SQL Dot bytes.
  * Add `BundledVbSprites.rgb565Frames(assetId)`/`radarFrames(assetId)` (new code in the untracked file) with an
    LRU cache; conversion can copy the proven packing in
    `NativeArchiveStorageEntry.kt:117-133` (`toSpriteData`) — transparent pixels must pack to the green key that
    `BitmapData.createARGBIntArray()` decodes (`BitmapData.kt:140-153`).
  * Wire a `SpeciesAppearanceRepository` instance into `WorldRepository` (currently constructed with only `db`
    plus lambdas, `di/DefaultAppContainer.kt:272-275`); keep a constructor default so existing unit tests that
    build `WorldRepository` directly keep compiling.
* Effort: helper ~80-120 LOC, mapper ~50-70 LOC, DI ~5 LOC, tests ~100-200 LOC. Medium-low risk, no migration.
* The in-flight patch is still worth completing even if the Lead prefers O1 later: O2 is the minimum shippable
  fix and is compatible with a later DB change.

### O3 — build-time generated RGB565 pack + small catalog (like `dot_sprites.zip`)

* Pre-convert baked PNGs to normalized 12-pose RGB565 frames at build time (`tools/build_dot_sprites.py` is the
  precedent; `tools/test_build_dot_sprites.py` tests it) and ship `vb_sprites.zip` + manifest, then either
  install `Sprite` rows at runtime (same DB + view + migration cost as O1, only startup decode moved to build
  time) or add a second library like `BundledDotSpriteLibrary` (then consumers still need a DTO/view equivalent
  for SQL surfaces, or the same Kotlin mapper as O2).
* Cost: +~40 MB of effectively incompressible APK assets, new tooling + validation, and O1's DB/migration cost
  if the SQL consumers are to be fixed through the view. **Worse than O2** for this bug; only interesting if the
  team wants DB-normalized VB art for other reasons (export/import, widget process, etc.).

### Recommendation (smallest complete, consistent with architecture)

**O2.** Keep `DISPLAY_SPRITE_QUERY`, schema v54 view, `AppDatabase` v58 and `SpeciesArtworkPolicy` untouched.
Finish the in-flight O2 core with the World/Radar mapper, then treat Home/Storage/widget/farm as explicit Phase 2
(Kotlin-only, optional) if the owner wants their recruited/materialized catalog partners to show VB art too.

Priority rule to make explicit (and document):
`VB choice > imported/native VB art > shipped VB asset > automatic Dot > placeholder`, with **explicit Dot choice**
decided once. Current SQL view ranks explicit Dot choice above the automatic Dot default; the in-flight Kotlin
policy ranks shipped VB above Dot (including `chosenDot`), because `SpeciesArtworkPolicy.choose` only knows "dot".
That means a user who picked a Dot set for a species can get shipped VB in battle/portrait but Dot on the radar.
Recommended: in Kotlin, pass `vbAsset` only when `chosen == null` (preserve the explicit user override, matching
the view's intent), or consciously accept "VB first wins" and note the radar/battle divergence; do not change the
view for this (migration not worth it).

---

## 4. Exact files/functions to touch for the recommended remaining work (O2 Phase 1)

| File | Change |
|---|---|
| `app/src/main/java/com/tamerslab/species/BundledVbSprites.kt` (untracked, new) | Add `radarFrames(assetId)` / `rgb565Frames(assetId, poses)` + uniform-canvas packing + LRU; keep `frames`/`bitmap` semantics. |
| `app/src/main/java/com/tamerslab/world/WorldRepository.kt` | Constructor: optional `appearanceResolver: (suspend (String) -> SpeciesAppearanceRepository.Artwork?)? = null` (or a small `SpawnArtworkPresenter`); map `observeSpawns()` (`:61-66`), `observePendingRecruits()` (`:68-69`) and `getSpawn()` (`:581`) on `Dispatchers.IO`, replacing DTO frames only for catalog definitions resolving to `VB_ASSET`; never touch `externalCharacterId`, stats or gameplay fields. Keep `DOT_FIRST` rows untouched. |
| `app/src/main/java/com/tamerslab/di/DefaultAppContainer.kt` | Wire `WorldRepository` (`:272-275`) with `SpeciesAppearanceRepository(db, dotSpriteLibrary) { spritePreferenceRepository.current }` (pattern already used at `IndividualSpriteManager.kt:17-19`, `ArenaWidgets.kt:73-77`). |
| Optional Phase 1.5: `app/src/main/java/com/tamerslab/world/ui/WorldScreen.kt` | Encounter dialog (`:1538-1545`, `:1968-1980`) and radar player token (`:395-413`) baked-art override; otherwise they stay Dot (documented). |
| Optional: `app/src/main/java/com/tamerslab/species/SpeciesAppearanceRepository.kt` | Decide/document explicit-Dot-choice rank; consider deprecating `spriteFor()` (`:47`) or making callers use `resolve().sprite/assetId`. |
| Tests | See §5. |

No changes needed to: `SpritePreferenceSchema.kt`, `SpeciesCustomization.kt`, `AppDatabase.kt` migrations,
`WorldSpawnDao.kt` SQL, all `UserCharacterDao`/`DexDao`/`FarmRosterArtwork`/widget queries (Phase 2 only).

Phase 2 if required (owned, catalog-definition characters): add `c.speciesId`/`c.catalogSpeciesId` (existing
columns — pure query/DTO change, **no migration**) to `CharacterWithSprites` projections and route UI art through
the same `resolve()`; widget needs a `BundledVbSprites.bitmap()` path in `DigimonWidgetProvider.kt:254-309`; farm
can use `farmArtworkFromSprite` after converting frames; Home/Storage just swap `BitmapData(spriteIdle…)` inputs.
Effort ~1-2 days, medium risk (many call sites), no schema risk.

### Risks / caveats for the recommended patch

1. **RGB565 conversion correctness**: must match the green-key decode in `BitmapData.createARGBIntArray()`; reuse
   the `NativeArchiveStorageEntry.toSpriteData()` packing and add a device test that converts back.
2. **Per-pose sizes**: radar/FP/iso renderers assume one `width/height` per spawn (`RadarFirstPersonViewport.kt:186-194`,
   `RadarIsometricViewport.kt:427-445`); pad all four poses to a common canvas (max dims) and keep the aspect
   ratio sane (renderers clamp the ratio to 0.5…1.8, `RadarFirstPersonViewport.kt:271`).
3. **Performance**: PNG decode + RGB565 conversion on first sight of each species; cache converted frames and
   decoded bitmaps (`BundledVbSprites` already caches bitmaps; ~650 species worst case, but a radar region only
   shows ≤60 spawns). Measure 3D GLB rebuilds (key is content-hash based, so swapping bytes once is safe).
4. **Flow stability/equality**: `SpawnWithDetails.equals/hashCode` compares sprite bytes (`WorldDtos.kt:41-62`);
   deterministic conversion keeps equality stable so Compose `remember` keys do not thrash. Do not introduce
   random/time values into the frames.
5. **Do not change `externalCharacterId`**: it drives wild encounter technique templates (cosmetic but game
   behaviour), battle-sprite lookups and the `RadarBattleFlowTest` expectations.
6. **Explicit Dot choice vs shipped VB ordering** (see §3); decide explicitly and document; DOT_FIRST must remain
   byte-identical to the view's Dot result.
7. **In-flight WIP**: `NativeSpeciesPresentationRepository.kt` and several transfer/game-save files are being
   edited by another session; do not rebase onto them or touch them for this fix. `BundledVbSprites.kt` is
   untracked — it must be added deliberately (it is part of the fix, not unrelated WIP).
8. `BundledVbSprites.assetIdFor()` trusts `catalog()` mappings and checks folders via `AssetManager.list`; this is
   per-asset cached, but do not call it on the main thread (document says "Call on IO", `BundledVbSprites.kt:19-23`).

---

## 5. Tests (existing + what to add; none were run — no gradle, per task)

Existing relevant tests:
* `app/src/test/java/com/tamerslab/species/SpeciesArtworkPolicyTest.kt:6-28` — priority matrix; still passes
  (policy untouched). Extend with "VB_FIRST + shipped asset beats Dot" and "explicit dot choice" cases.
* `app/src/test/java/com/tamerslab/species/CanonicalSpeciesCatalogTest.kt:72-107` — shipped `species_catalog.json`
  + dot pack integrity; add `bundledVbAssetIds` assertions (deterministic order, DiM-124 last-resort, every
  returned id has a `battle_sprites/extracted_assets/sprites/<id>` folder; this test already reads assets from
  disk via `File("src/main/assets")` fallback at `:73`).
* `app/src/test/java/com/tamerslab/world/RadarBattleFlowTest.kt:44-97` — constructs `SpawnWithDetails`, asserts
  `externalCharacterId`/`assetCharacterId` and fallback `spriteSet`; must keep passing after the world mapper
  (keep `externalCharacterId` untouched).
* `app/src/test/java/com/tamerslab/battle/offline/TamerRosterAssetsTest.kt`, `TamerCanonicalPartnersTest.kt`,
  `TrainingBattleBalanceTest.kt` — arena/tamer pixel-source contract; check for regressions.
* `app/src/test/java/com/tamerslab/species/NormalizedSpriteFramesTest.kt`,
  `app/src/test/java/com/tamerslab/ui/SpritePreviewPolicyTest.kt` — frame-size validity assumptions (uniform
  `width/height`); relevant if radar frames are packed on a common canvas.
* `app/src/androidTest/java/com/tamerslab/BundledDotSpritePersistenceTest.kt:39-124` — device checks for Dot
  install/priority/portrait; best template for a new `BundledVbSpritePersistenceTest`: un-imported species
  `resolve()` returns `VB_ASSET` in VB_FIRST and Dot in DOT_FIRST, `portrait()` decodes shipped PNG, catalog
  definitions with shipped VB produce non-empty RGB565 radar frames that decode back with transparency.
* `app/src/androidTest/java/com/tamerslab/SpeciesCustomizationPersistenceTest.kt:81-93` (choice/DisplaySprite
  semantics), `SpeciesBindingMigrationTest.kt:99-102` (view query) — must stay green because the SQL is unchanged.
  Note the working tree already bumps `AppDatabase` to 58 while these WIP tests still assert version 56
  (`SpeciesCustomizationPersistenceTest.kt:194`, `SpeciesBindingMigrationTest.kt:111`) — pre-existing uncommitted
  drift, not caused by this fix.
* `app/src/androidTest/java/com/tamerslab/WorldEcosystemPersistenceTest.kt`,
  `PartnerParticipationPersistenceTest.kt`, `IndividualPersistenceTest.kt` — migration lists (§ only if O1).
* `app/src/test/java/com/tamerslab/data/database/RoomSchemaCharacterizationTest.kt:11-32` — pins schema 55 snapshot
  and identity hash; unchanged for O2, must be extended for O1.
* `app/src/androidTest/java/com/tamerslab/battle/ui/offline/TrainingBattlePreparationTest.kt`,
  `app/src/androidTest/java/com/tamerslab/battle/ui/arena/TamerArenaLayoutTest.kt`,
  `app/src/androidTest/java/com/tamerslab/world/ui/RadarOverheadAnnotationTest.kt`,
  `DebugWorldSpawnTest.kt` — regression candidates for the battle/radar change.
* Host schema tests: `tools/test_sprite_preference_schema.py:43-85` executes the shipped
  `DISPLAY_SPRITE_QUERY` against real SQLite and asserts the current VB/DOT fallback order (including "explicit
  Dot choice does not defeat VB native art"). **Pre-existing breakage**: `tools/test_participation_schema.py:10-11`
  points at the old package path (`com.github.nacabaro.vbhelper.database`, absent in this tree), so
  `python -m unittest tools.test_sprite_preference_schema` fails in fixture setup before touching SQL
  (verified by running it). It is untouched by O2 but must be repaired before relying on it for O1.
* `tools/test_build_dot_sprites.py` / `tools/build_dot_sprites.py` — precedent for O3 only.

Relevant commands (do not run builds in this audit; list for the implementer):
`.\gradlew.bat :app:testDebugUnitTest --tests "com.tamerslab.species.*"` (unit),
`:app:connectedDebugAndroidTest` selective classes above, `python -m unittest tools.test_sprite_preference_schema`
after repairing the stale path.

---

## 6. Answers to the four requested bullets (condensed)

1. **Surfaces that actually display un-owned species art via the `DisplaySprite` SQL view**: World/Radar wild
   spawns and first partner (incl. all three radar renderers and the encounter sheet), world encounter detail
   dialog, and wild-battle fallback sprite sets. Dex main screens, scan and fusions are per imported card and are
   *not* affected. Transfer/native-save previews are un-owned species art too, but they use the Kotlin
   `SpeciesAppearanceRepository.portrait()` path, not the view.
2. **How VB_FIRST resolves them today**: view order at `SpritePreferenceSchema.kt:24-35` = user VB choice →
   imported card art → imported same-species art → explicit Dot choice → `SpeciesDefaultSprite` Dot →
   `c.spriteId`. A catalog definition has `cardId = null` and a `SpeciesDefaultSprite` Dot row
   (`ExpandedCatalogRepository.kt:46-48, 94-101, 137-139`), so with no imported card/choice it stops at branch 5
   → **Dot**. Baked PNG art was not part of resolution at all before the in-flight O2 patch; transfer portraits
   resolved Dot via `portrait()` (`SpeciesAppearanceRepository.kt`, old logic, now replaced).
3. **Smallest complete option**: **O2 — leave SQL/view untouched; finish the Kotlin resolver** (in-flight core
   already fixes battle/result forms/arena/transfer; add the `WorldRepository` spawn-art mapper +
   `BundledVbSprites` RGB565 frame helper + DI wiring; exact files in §4). No schema migration, DOT_FIRST
   unchanged, explicit-Dot-choice ranking decided/documented. O1/O3 rejected for cost/risk (§3). Remaining Dot
   after Phase 1 (documented, not un-owned wild species): Home partner art, Storage grid/details, home-screen
   widget, farm diorama, Adventure roster, radar player token, encounter-detail dialog — all owned catalog
   definitions; Phase 2 is Kotlin-only, no migration.
4. **Tests**: list and gaps in §5; no migration test changes are needed for O2; the O1 path would require a new
   `MIGRATION_58_59`, `app/schemas/.../59.json`, updated Room/migration tests and repair of the stale host SQLite
   test paths.
