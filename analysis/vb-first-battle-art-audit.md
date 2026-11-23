# Audit — battle/arena artwork resolution: VB-first vs Dot fallback

Owner bug: *"even if you are on VB first mode and a digimon is one of the digimon that has VB sprite baked into the app, if this digimon is not on your DIM gallery it is shown as a dot digimon and not a VB one."*

Auditor: teammate `battle-art-path` (task-1). Read-only: no source file was modified; only this report was created. No gradle/build/device command was run.

Snapshot facts:

| Item | Value |
|---|---|
| HEAD | `89dbe649` ("VBHLLMVer.58 more ui changes") |
| Working tree | **contains an uncommitted, in-progress implementation of this fix** (see §4). Relevant files last written 2026-11-20 16:56–16:58 local. |
| Report snapshot | 2026-11-20 ~17:05 America/Bahia |
| Working-tree files touched by the in-progress fix | `species/BundledVbSprites.kt` (new), `species/CanonicalSpeciesCatalog.kt`, `species/BundledDotSpriteLibrary.kt`, `species/SpeciesAppearanceRepository.kt`, `battle/IndividualSpriteManager.kt`, `battle/ui/offline/TrainingBattlePresentation.kt` |

Because the fix is already partly applied in the working tree, this report separates:
- **§1–§3: baseline (HEAD) behaviour and exact root causes** — what the bug report describes;
- **§4: review of the working-tree fix** — whether it actually fixes every battle/arena surface;
- **§5–§9: remaining/suggested edits, risks, helpers, tests.**

---

## 1. Executive answer (baseline HEAD)

In `SpritePreference.VB_FIRST`, for an **un-owned species** (no imported DIM/BEM card, no VB sprite choice) that **does have baked VB frames** and dot art, the **3D battle/arena models and equipped result-form models were built from Dot artwork**, while roster/picker previews of the same species showed the baked VB art. All of those battle models flow through one helper: `TrainingBattlePresentationFactory.posesForSpecies(name)`, which called `SpeciesAppearanceRepository.spriteFor(name)` — i.e. `resolve(name)` **without a `vbAssetId`**. With `asset == false`, `SpeciesArtworkPolicy` falls through to the `dot` branch, and the Dot sprite wins before the existing `assetPoses(...)` fallback is ever tried.

The policy itself was already correct: `SpeciesArtworkPolicyTest.vbFirstUsesLoadedChoiceThenNativeArtThenVbAssetBeforeDotFallback` (app/src/test/java/com/tamerslab/species/SpeciesArtworkPolicyTest.kt:11-16, line 14) asserts `VB_ASSET` before `DOT`. The defect is that the battle call site never supplied the asset flag/data.

**Baseline verdict per surface** (details and evidence in §2):

| User-visible surface | Un-owned species with baked VB + dot, VB_FIRST → | Verdict |
|---|---|---|
| Offline training battle — hand-picked packaged opponents (`Pulsemon`, `Agumon`, `Dorumon`) | Dot model | **BUG: yes** |
| Offline training battle — hand-picked `Arena challenger` (name not in catalog) | baked VB (via legacy `assetPoses`) | no (inconsistent with the row above) |
| Offline training battle — user's own un-owned catalog partner (species not in gallery) | Dot model | **BUG: yes** |
| Tamer Arena exhibition/tournament fighters (bundled tamer teams) | Dot model | **BUG: yes** |
| Tamer Arena equipped Blast/Jogress result forms (bundled result species) | Dot model | **BUG: yes** |
| World/Radar wild-spawn battle — spawn from a catalog definition (un-owned) | Dot model | **BUG: yes** |
| World/Radar wild-spawn battle — spawn from an imported card | imported VB (via `VB_IMPORTED`) | no |
| Arena lobby/briefing roster preview (`ArenaSprite`) | baked VB | no — *preview was correct while the battle was Dot* |
| Training picker tile (`AssetParticipantPreview`) | baked VB | no — *preview was correct while the battle was Dot* |
| Radar map 2D markers / first-person & isometric 3D actors | Dot (DisplaySprite bytes) | **BUG: yes, but outside battle presentation** (DB/view surfaces, task-2 scope) |
| Farm / Home / Dex / Widget (all DisplaySprite-driven) | Dot | outside this task's code paths (task-2) |

---

## 2. Baseline call path, end to end (HEAD)

### 2.1 How a tamer-arena fighter is assembled

1. `ArenaSpeciesIndex.load` builds un-owned `ArenaSpecies` from `assets/species.json`; the character id is derived from `card:slot` and only kept when the frame folder exists:
   `app/src/main/java/com/tamerslab/battle/offline/tamers/ArenaSpeciesIndex.kt:28-47` (id formatting at line 33, `assetId` at line 41, `nativeArtwork` at line 45).
2. Tamer team resolves to those `ArenaSpecies` (`TamerTeamResolver.kt:7-20`, lookup at `:45-50`; DiM 124 is filtered as last-resort art at `:47`).
3. `TamerBattleLoadouts.participants` freezes each fighter with `assetCharacterId = externalCharacterId = species.assetId` (a real `dim###_mon##` when baked art exists) and `speciesName = species.name`:
   `app/src/main/java/com/tamerslab/battle/offline/tamers/TamerBattleLoadouts.kt:21-56` (lines 26-28, 35-36).
4. `TamerArenaViewModel.startExhibition/startTournament` → `ArenaMatchSpec` → `TamerArenaScreen.kt:32` → `OfflineBattleSessionViewModel.startArena` (`OfflineBattleSessionViewModel.kt:102-123`) → `TrainingBattlePresentationFactory.create` (`OfflineBattleSessionViewModel.kt:164-171`).
5. In `create(...)`, the fighter pose source is:
   - `app/src/main/java/com/tamerslab/battle/ui/offline/TrainingBattlePresentation.kt:205-210` (HEAD): `posesForSpecies(name) = appearances.spriteFor(name)?.let(::spritePoses)`; `spriteFor` = `resolve(name)` with `vbAssetId = null` (`SpeciesAppearanceRepository.kt:44` HEAD);
   - chain at HEAD `TrainingBattlePresentation.kt:238-244`: `posesForSpecies(name) ?: participant.spriteSet ?: posesForCharacter(cardCharacterId) ?: databasePoses(character) ?: assetPoses(spriteManager, artId)`.
6. `appearances.resolve(name)` (HEAD `SpeciesAppearanceRepository.kt:19-42`):
   - `dot` = default dot set when the canonical species is expanded-ready (line 24);
   - `chosen` VB/dot sprite choice (lines 27-31), `imported` = any imported card sprite of the species (lines 32-34);
   - `asset` = **`vbAssetId != null` only** (line 35) — for `spriteFor` this is always `false`;
   - `SpeciesArtworkPolicy.choose(VB_FIRST, dot=true, choice=false, imported=false, asset=false)` returns `DOT` (`SpeciesArtworkPolicy.kt:6-13`, dot branch line 11) → `Artwork(sprite = dot sprite)` (line 39).
7. `spritePoses(...)` converts that Dot sprite into the 7 resident poses and `SpriteExtrusionGlb.build` bakes the model (`TrainingBattlePresentation.kt:239-249` HEAD). Because `posesForSpecies` returned non-null, **the later `assetPoses(spriteManager, artId)` — which would have loaded the correctly identified baked VB frames from `dim###_mon##` — is unreachable** (`?:` short-circuit).

Result: an arena Agumon whose roster preview showed baked VB (`dim012_mon03`/`dim124_mon03` art) fought as the Dot sprite.

### 2.2 Equipped result forms

`buildPreparedForms` (same file, HEAD lines 329-420) uses the same helper: `val preferred = posesForSpecies(target)` at HEAD line 355; when non-null it builds the FORM/Jogress model from it (HEAD 356-368). So an equipped result species that is un-owned but has baked art also became Dot. The pinned-result fallback (`pinned.cardCharacterId ?: pinned.assetId` → `assetPoses`, HEAD 369-381) correctly used the dim asset, but only when `posesForSpecies` returned null — the Dot result pre-empted it.

### 2.3 Training battles (packaged opponents / un-owned partner)

- Packaged opponents are literal `assetOfflineBattleParticipant("dim000_mon03", "Pulsemon", …)` etc.:
  `app/src/main/java/com/tamerslab/battle/ui/BattlesScreen.kt:2087-2096`; `OfflineBattleEntryPanel.kt:232-275` sets `assetCharacterId = externalCharacterId = characterId` (line 261) and `speciesName = displayName` (line 267).
- They are passed to `TrainingBattlePreparation` (`BattlesScreen.kt:2398-2413`) → `offlineBattleViewModel.start(...)` (`BattlesScreen.kt:2408`).
- Same `posesForSpecies` pre-empts the valid `artId`, so `Pulsemon`/`Agumon`/`Dorumon` fought as Dot while the picker tile `AssetParticipantPreview` (`TrainingBattlePreparation.kt:407-428`, via `IndividualSpriteManager.loadSpriteFrame`, which **does** pass the character id as `vbAssetId`, `IndividualSpriteManager.kt:30-40` HEAD) showed the baked VB frames.
- `Arena challenger` (display name that does not resolve to a canonical species) made `posesForSpecies` return null and fell through to `assetPoses(spriteManager, artId)` → showed `dim014_mon05` VB art. That is the natural control showing the intended path already existed and worked.
- An **owned-but-un-owned-species** partner (ExpandedCatalog catalog definition; species not in the DIM gallery) has `externalCharacterId = "app_mon######"` (`storage/data/UserCharacterDao.kt:141-143`), so even the legacy `assetPoses(artId)` could not help; the chain stopped at `posesForCharacter`/`databasePoses` (DisplaySprite dot art) — Dot in battle.

### 2.4 World / Radar wild battles

- `worldRadarBattleParticipant` copies `spawn.externalCharacterId` (line 54) and builds `spriteSet` from the spawn's DisplaySprite bytes (`app/src/main/java/com/tamerslab/world/RadarBattleFlow.kt:45-95`, lines 57-60 and 79-88).
- `WorldSpawnDao` produces `externalCharacterId` as `dim###_mon##` **only when the spawn has a card**, else `app_mon######` (`app/src/main/java/com/tamerslab/world/data/WorldSpawnDao.kt:102-103, 147-148, 191-192`), and joins `SpeciesProfile.speciesName` (line 117).
- Un-owned catalog-definition species **can spawn** (`world/WorldRepository.kt:260-266`) and their `speciesName` is filled from the DB profile at `TrainingBattlePresentation.kt:174-182`. `posesForSpecies(name)` therefore found dot art → Dot model. Falling through would not have helped: `participant.spriteSet` is the DisplaySprite of the catalog definition, i.e. **also Dot** (`data/database/SpritePreferenceSchema.kt:7-35` view, VB_FIRST branch lines 31-35 — it has no baked-VB source).
- Imported-card spawns resolve `imported != null` → `VB_IMPORTED`, so they already showed VB (same art as their card).

### 2.5 Why the previews were already right (contrast / natural experiment)

- Arena preview `ArenaSprite` passes the species' `assetId` into `resolve`: `app/src/main/java/com/tamerslab/battle/ui/arena/ArenaWidgets.kt:73-83` (resolve call line 76; `resolved.assetId → loadBundledArenaArtwork` lines 78-80; loader lines 125-136). In VB_FIRST that makes `asset == true` → `VB_ASSET` → it loads `BattleAssetPaths.characterFrame(assetId, 1/2)` (`battle/BattleAssetPaths.kt:31-34`).
- Training picker `AssetParticipantPreview` calls `IndividualSpriteManager.loadSpriteFrame(assetId, 1)` → `resolve(name, characterId)` (`IndividualSpriteManager.kt:27-47` HEAD) → VB_FIRST gets `VB_ASSET` → then decodes the real PNG at `BattleAssetPaths.characterFrame(...)` (line 38).
- So the app could show VB in the roster/checklist and Dot in the arena for the same Digimon — exactly the owner report.

### 2.6 Baseline table with evidence

| # | Surface / entry point | Baseline source actually used | Un-owned + baked VB in VB_FIRST shows Dot? | Evidence |
|---|---|---|---|---|
| 1 | Training battle, packaged opponents | `posesForSpecies` → `resolve(name)` no asset → DOT sprite | **Yes** (Pulsemon/Agumon/Dorumon; `Arena challenger` is the exception because the name is not canonical) | `BattlesScreen.kt:2087-2096`; `OfflineBattleEntryPanel.kt:254-274`; `TrainingBattlePresentation.kt:205-210,238-247` (HEAD); `SpeciesAppearanceRepository.kt:19-44` (HEAD) |
| 2 | Training battle, user's catalog partner (un-owned species) | same Dot sprite; `app_mon` id cannot rescue via `assetPoses` | **Yes** | `TrainingBattlePresentation.kt:238-247` (HEAD); `UserCharacterDao.kt:141-143`; `OfflineBattleEntryPanel.kt:159,166` |
| 3 | Tamer Arena fighters | same Dot sprite | **Yes** | `TamerBattleLoadouts.kt:21-56`; `ArenaSpeciesIndex.kt:28-47`; `TrainingBattlePresentation.kt:205-210,238-247` (HEAD) |
| 4 | Tamer Arena / equipped result forms (FORM, Jogress) | `posesForSpecies(target)` → Dot sprite | **Yes** | `TrainingBattlePresentation.kt:270-272,329-420` (HEAD, preferred at 355) |
| 5 | World/Radar wild battle, catalog-definition spawn | `posesForSpecies` Dot; `spriteSet` is DisplaySprite dot too | **Yes** | `WorldRepository.kt:260-266`; `WorldSpawnDao.kt:100-121`; `RadarBattleFlow.kt:45-95`; `TrainingBattlePresentation.kt:205-210,238-247` (HEAD) |
| 6 | World/Radar wild battle, imported-card spawn | `VB_IMPORTED` (same art as card) | No | `SpeciesAppearanceRepository.kt:34,37` (HEAD) |
| 7 | Arena roster/briefing previews | `VB_ASSET` via supplied `assetId` | No (correct) | `ArenaWidgets.kt:73-80,125-136`; `ArenaBriefing.kt:46,66` |
| 8 | Training picker tile | `VB_ASSET` via supplied character id | No (correct) | `TrainingBattlePreparation.kt:407-428`; `IndividualSpriteManager.kt:27-47` (HEAD) |
| 9 | Radar map 2D marker / 3D world actors | DisplaySprite bytes (dot) | **Yes** (non-battle) | `WorldScreen.kt:618-630`; `RadarPresentation.kt:12-19`; `RadarFirstPersonViewport.kt:189`; `RadarIsometricViewport.kt:1503`; `SpritePreferenceSchema.kt:7-35` |
| 10 | Farm/Home/Dex/Widget (DisplaySprite view) | dot | outside battle paths (task-2) | `app/src/main/java/com/tamerslab/farm/ui/digifarm/FarmRosterArtwork.kt:16,31,154` etc. |

---

## 3. Exact causes (ranked)

1. **The battle call site blanked the only baked-VB input.** `posesForSpecies` called `appearances.spriteFor(name)`, which is `resolve(name)` with `vbAssetId = null` (`TrainingBattlePresentation.kt:205-210,239` HEAD; `SpeciesAppearanceRepository.kt:44` HEAD). `resolve` computed the `asset` flag **solely** from that argument (HEAD line 35), so for every un-owned species `asset == false`. Because the species has dot art, `SpeciesArtworkPolicy.choose` returns `DOT` (dot branch `SpeciesArtworkPolicy.kt:11`) and the model is built from dot poses before the chain can reach `assetPoses(...)`.
2. **No name → baked-asset mapping existed in Kotlin.** The build-time binding `CanonicalSpeciesDto.cardSlots` (“card:slot” → `dim###_mon##`) was parsed but never consumed (`species/CanonicalSpeciesCatalog.kt:33`), and `BundledDotSpriteLibrary` had no VB asset index. Therefore no caller could cheaply derive the baked asset id from a species name for participants whose id is `app_mon######`/`null` (catalog partner, wild catalog spawn). `AssetSpeciesNames` only maps in the opposite direction (asset id → name; `species/AssetSpeciesNames.kt:11-24`, and it skips card 0).
3. **`Artwork.assetId` was discarded by the presentation layer even when supplied.** `posesForSpecies` only ever consumed `Artwork.sprite`; `resolve` can return `Artwork(sprite = null, assetId = …)` for `VB_ASSET`, so any future/other caller passing a `vbAssetId` would silently fall through to the older fallbacks (which in the wild-spawn case are dot DisplaySprite bytes, and for `app_mon` ids are unusable asset paths). Result forms shared the same helper, so FORM/Jogress targets had the same defect.

Contributing evidence that the priority is intended: `SpeciesArtworkPolicyTest.kt:8` (DOT_FIRST + all sources → DOT), `:11-16` (VB_FIRST → choice/imported/VB_ASSET before DOT), `:18-22` (DOT_FIRST falls back to VB sources when no dot). The policy object was never the bug; the missing data was.

---

## 4. Working-tree fix already present (uncommitted) — review

Files and what they do (all read; not written by this auditor):

### 4.1 `species/BundledVbSprites.kt` (new)

- `frames(assetId)` lists/parses `battle_sprites/extracted_assets/sprites/<id>/<id>_NN.png` (lines 45-53), cached.
- `bitmap(assetId, frame)` decodes a PNG through `BattleAssetPaths.characterFrame` with an 8 MB `LruCache` (lines 56-66).
- `assetIdFor(species)` = first `catalog().bundledVbAssetIds(species)` whose folder actually ships frames (lines 39-42).
- `portrait(assetId)` = frame 1, falls back to frame 2 (lines 69-71).
- `VB_BATTLE_POSES` (lines 79-82) is the same pose→frame mapping used by `spritePoses`/`assetPoses` (idle 1, idle2 2, walk 3, walk2 4, attack 11, guard 12, defeated 10), and `vbCharacterId(card, mon)` formats `dim%03d_mon%02d` (line 85).

### 4.2 `species/CanonicalSpeciesCatalog.kt`

- `bundledVbAssetIds(species)` (lines 96-114) parses `cardSlots`, drops non-positive card/slot (so the `0` encyclopedia bucket is not a source), de-duplicates, sorts **non-124 first**, then by card/slot, and formats ids. This consumes the previously unused `cardSlots` field and encodes the documented “DiM 124 is tintable last-resort art” rule (see `docs/testing/HISTORICAL_REGRESSION_LOG.md:1619-1625`).
- `LAST_RESORT_VB_CARD = 124` in the companion.

### 4.3 `species/BundledDotSpriteLibrary.kt`

- Lazy `val bundledVbSprites: BundledVbSprites` (lines 24-30) shared process-wide; catalog provider is the existing cached `catalogBlocking()`.

### 4.4 `species/SpeciesAppearanceRepository.kt` (current)

- `resolve`: `val vbAsset = vbAssetId ?: library.bundledVbSprites.assetIdFor(species)` (line 37) and `VB_ASSET → Artwork(assetId = vbAsset)` (line 41), so VB_FIRST + baked + dot now returns the asset instead of the dot sprite — exactly the `asset` flag the policy test expected.
- `portrait` was rewritten to delegate to the same policy object (lines 50-70); `VB_ASSET → library.bundledVbSprites.portrait(...)` (line 64). `spriteFor` is unchanged (line 47) but now returns `null` for `VB_ASSET` by design (asset lives in `Artwork.assetId`).

### 4.5 `battle/IndividualSpriteManager.kt` (current)

- `loadSpriteFrame` now keeps the whole `Artwork`: `vbAsset = artwork.assetId`, `assetId = bundledAsset ?: characterId` (lines 33-46), and uses `bundledVbSprites.frames(...)` to choose a frame that actually exists (line 45-46) — cropped-frame sets may not contain the requested pose.
- `getAvailableDisplayFrames` returns the shipped frames for `VB_ASSET` (lines 86-94).

### 4.6 `battle/ui/offline/TrainingBattlePresentation.kt` (current)

- `bundledVbSprites` obtained from the application container (line 187).
- `posesForSpecies(name)` now calls `preferredSpeciesPoses(name, appearances, bundledVbSprites)` (lines 209-214).
- `preferredSpeciesPoses` (lines 430-450): `appearances.resolve(name)` → sprite? `spritePoses` → else `Artwork.assetId` → `bundledVbSprites.frames(assetId)`, map `VB_BATTLE_POSES` to `bitmap(...).toResidentFrame()`; returns null if no shipped frames, so the old fallback chain (spriteSet/card/db/asset) still applies when nothing is available.
- Both call sites automatically benefit: fighters (line 243) and equipped result forms (`buildPreparedForms`, `preferred` at current line 359; same `::posesForSpecies` reference).

### 4.7 Verification of the fix against the reported surfaces

| Surface | Under current working tree | Notes |
|---|---|---|
| Arena/training fighters (bundled tamer teams, packaged opponents) | baked VB poses | `resolve` derives the canonical slot; `preferredSpeciesPoses` loads it |
| User's un-owned catalog partner (`app_mon…`) | baked VB poses | derivation is name-based, so the invalid `app_mon` id is irrelevant |
| World/Radar battle, catalog-definition spawn (`app_mon…`) | baked VB poses | no longer stops at the dot `spriteSet` |
| Equipped FORM/Jogress result forms | baked VB poses | same helper; pinned-arena fallback still intact |
| Imported/owned VB art | unchanged (`VB_CHOICE`/`VB_IMPORTED` sprite path) | policy order preserved |
| DOT_FIRST | unchanged (dot first) | `resolve` returns the dot sprite; `preferredSpeciesPoses` takes the sprite path |
| Placeholder/no-art species | unchanged | `resolve` returns `NONE` → fallback chain → existing placeholder handling |
| Arena previews / training picker / portraits | already VB, now consistent | `ArenaWidgets`, `IndividualSpriteManager`, `portrait` |

No remaining **battle** surface that still resolves to Dot for an un-owned species with baked VB was found in this working-tree state. The fix is the minimal design predicted by the code structure: derive the asset id from the canonical catalog, let the existing policy choose it, and only then load frames.

### 4.8 Residual issues in the current fix

1. **Slot parity (cosmetic, low priority).** `preferredSpeciesPoses` derives the slot purely from the canonical catalog and ignores the participant’s `externalCharacterId`. For multi-slot species the battle art can be a different physical DiM slot than the roster/preview art. Measured from the shipped assets:
   - 660 canonical species have baked art (861 slot bindings, all 861 have frame folders);
   - 160 species have >1 slot, 27 have >2; 11 differ between “raw `species.json` first slot” and “canonical catalog order”, but 10 of those are DiM-124 cases that `TamerTeamResolver` already filters (`TamerTeamResolver.kt:45-48`), so effectively **1 species** differs in an arena fight: `Dokugumon` (arena/JSON order → `dim034_mon08`; derived order → `dim013_mon06`);
   - the hard-coded packaged opponent `Pulsemon` uses `dim000_mon03` (`BattlesScreen.kt:2088`) while the derived canonical id is `dim138_mon03`; `Agumon` and `Dorumon` match.
   Optional refinement: pass the participant’s already-validated `externalCharacterId` as a *preferred* id (only when `bundledVbSprites.frames(id).isNotEmpty()`), which also preserves the hard-coded dim000 art. This needs `posesForSpecies(name, vbAssetId)`, threading the parameter through `buildPreparedForms` (pinned result `assetId`) and a cache key that includes the id. Purely cosmetic; not needed for the owner bug.
2. **Explicit Dot choice in VB_FIRST is now overridden by baked VB.** `chosenDot` is folded into the `dot` flag; the policy order puts `VB_ASSET` before `dot` (`SpeciesArtworkPolicy.kt:10-11`), so a user who explicitly picked a negative/dot sprite for that species while in VB_FIRST now gets baked VB instead of their chosen dot row. This matches the policy object/test (`SpeciesArtworkPolicyTest.kt:14`) but is a product‑visible priority change vs. HEAD; confirm it is intended.
3. **New derivation runs on every `resolve`.** `assetIdFor`/`frames` are cached, but `bundledVbAssetIds` allocates/sorts per call and `resolve` is hot (`ArenaSprite`, portraits, picker). Acceptable at this scale; if profiling complains, cache the id list per species id in `CanonicalSpeciesCatalog`.
4. **Cross-surface mismatch remains outside battle.** DisplaySprite-driven surfaces still render Dot for the same un-owned species (radar map/3D world actors, farm, home, Dex, widget — `SpritePreferenceSchema.kt:7-35`). After this battle fix the battle renders VB while the Radar map/actor still renders Dot; this is the task-2 O1/O2/O3 decision, not a battle regression.
5. **No automated coverage of the new wiring yet.** `SpeciesArtworkPolicyTest` only tests the policy object; the repository derivation (`resolve` returns `assetId`) and the battle pose source (`preferredSpeciesPoses`) are untested. Add the tests in §8 before trusting the patch.
6. **Frame-shape caveat (already inherent).** Baked frames are individually cropped RGBA PNGs with different per-frame sizes (e.g. `dim012_mon03`: `39x37`, `39x33`, `44x35`…; `dim124_mon03`: `49x47`, `49x44`…; 1136/1137 folders have 12 frames, `dim000_mon01` has 11 and lacks frame 1). `SpriteExtrusionGlb.build` normalizes each pose to unit height with a 0.5–1.8 aspect clamp (`rendering/sprite3d/SpriteExtrusionGlb.kt:272-283`), so the model renders, but pose-to-pose proportions come from the crop rather than a uniform sheet. The same path is already used for the 42 baked-but-no-dot species and the hard-coded `Arena challenger`, so this is proven machinery, not a new risk.

---

## 5. Minimal patch spec (baseline → fixed), for reference

The working-tree change follows this spec; it is reproduced so the Lead can diff intent vs implementation.

Data / helpers:
1. New `BundledVbSprites(context, catalogProvider)` in `com.tamerslab.species` (or equivalent): `assetIdFor(species)`, `frames(id)`, `bitmap(id, frame)`, `portrait(id)`, plus `VB_BATTLE_POSES` and `vbCharacterId(card, mon)`.
2. `CanonicalSpeciesCatalog.bundledVbAssetIds(species): List<String>` — parse `CanonicalSpeciesDto.cardSlots` (`CanonicalSpeciesCatalog.kt:33`), drop non-positive card/slot, de-duplicate, sort non-124 first / card asc / slot asc, format `dim%03d_mon%02d`.
3. `BundledDotSpriteLibrary.bundledVbSprites` lazy singleton (it already owns the cached catalog).

Resolution:
4. `SpeciesAppearanceRepository.resolve(name, vbAssetId)` — replace `asset = vbAssetId != null` with `val vbAsset = vbAssetId ?: library.bundledVbSprites.assetIdFor(species)`; pass `vbAsset` to `choose` and return it in `Artwork.assetId`. No signature change required, so `ArenaWidgets`/`IndividualSpriteManager` keep working, and the policy (choice → imported → asset → dot) is preserved.
5. `IndividualSpriteManager.loadSpriteFrame` / `getAvailableDisplayFrames` — consume `Artwork.assetId` (frame-existence fallback needed because crops are not guaranteed to cover all 12 numbers).

Presentation:
6. `TrainingBattlePresentationFactory.posesForSpecies(name)` (HEAD `:205-210`) — resolve once and dispatch on the `Artwork`:
   `sprite != null → spritePoses(sprite)`; `assetId != null → bundle frames (VB_BATTLE_POSES) → ResidentFrameImage`; return null if neither.
   This automatically fixes both fighters (HEAD `:239`) and result forms (HEAD `:355`). No signature change is strictly required.
7. Optional consistency: add `vbAssetId` to `posesForSpecies` and pass `profile.externalCharacterId` (only when it is a shipped VB id) / `pinned?.assetId`; include the id in the `preferredPoses` cache key. Optional; see §4.8.1.

Fallback order after the patch (VB_FIRST, un-owned): **VB choice → imported VB → baked VB frames → Dot**; when the preferred helper yields nothing, the legacy chain still runs: `participant.spriteSet → DB `DisplaySprite` for cardCharacterId → character sprites → asset frames at external id → error if truly empty`. DOT_FIRST is untouched (dot wins first).

---

## 6. Risks / edge cases to preserve (checklist)

- **DOT_FIRST must keep Dot priority** (`SpeciesArtworkPolicy.kt:7`; test `SpeciesArtworkPolicyTest.kt:8,18-21`). Deriving the asset id must not change `choose` order; it does not.
- **User-loaded `VB_CHOICE`/`VB_IMPORTED` art** must win over baked frames (`SpeciesArtworkPolicy.kt:8-9`; HEAD tests `BundledDotSpritePersistenceTest.kt:82-98`). The helper returns the sprite first if present.
- **`artId` is not always an asset id** (`app_mon######`, `storage:…`, `card:<id>`, DB ids). The working-tree design derives from the species name instead of trusting `externalCharacterId`, so bad ids cannot poison resolution; only the optional §4.8.1 refinement needs an explicit `frames(id).isNotEmpty()` guard.
- **Placeholder handling**: `PlaceholderSprite.ID = -1` is reserved and excluded from bundled DOT sets (`species/PlaceholderSprite.kt:6-21`, `CanonicalSpeciesCatalog.kt:74-78`); no-art species must still return `NONE`/null so display placeholders and spawn filtering (`WorldSpawnDao.deletePlaceholderSpawns` lines 74-83; `RadarPresentation.isPlaceholderArtwork` lines 33-39) are unaffected.
- **Result forms**: same helper is intentionally shared; `preparedForms` keyed by `setKey` and `canReuseBattleRenderAssets` compare `setKey`s (`TrainingBattlePresentation.kt:111-119`), so changing the model source only changes art bytes, not reuse logic; pinned-arena target attack visuals are still taken from `pinned.cardCharacterId`/`pinned.assetId` (lines 362-365 in current file, 358-365 HEAD).
- **Per-frame cropped PNGs**: never assume one width/height for a VB character. `AssetPoses`/`preferredSpeciesPoses` build one `ResidentFrameImage` per frame; `getAvailableDisplayFrames`/`frames` filter the numbers that exist; missing pose falls back to the shipped idle.
- **Performance/memory**: VB frames are decoded from assets (7 poses per fighter), cached in `BundledVbSprites.bitmap` (8 MB LRU) and the GLB in `SpriteExtrusionGlb.modelCache` (8 MB, `SpriteExtrusionGlb.kt:18-26`); `IndividualSpriteManager.clearCache()` is still called in the `finally` of `create` (`TrainingBattlePresentation.kt:322-326`). This is the same cost the existing asset fallback already paid for 42 species.
- **Do not turn "asset exists" into "replaces choice/imported"**: the `choose` call must still receive `choice`/`imported` booleans before `asset`.
- **Cache**: `preferredPoses` is keyed by `BattleSpeciesIdentity.normalize(name)` only (line 211). With name-only derivation that is deterministic; if §4.8.1 adds a per-participant preferred id, the cache key must include it.
- **Explicit dot choice in VB_FIRST** now loses to baked VB — decide product intent (§4.8.2).

---

## 7. Existing helpers suitable for reuse (what to build on)

| Helper | Location | Use |
|---|---|---|
| `BundledVbSprites` (new, working tree) | `app/src/main/java/com/tamerslab/species/BundledVbSprites.kt:24-85` | asset-id derivation, frame listing, PNG decode, portrait, `VB_BATTLE_POSES`, `vbCharacterId` |
| `CanonicalSpeciesCatalog.bundledVbAssetIds` (new) | `species/CanonicalSpeciesCatalog.kt:96-114` | canonical species → shipped slot ids (non-124 first) |
| `CanonicalSpeciesDto.cardSlots` | `species/CanonicalSpeciesCatalog.kt:24-38` | build-time `card:slot` binding (previously unused) |
| `CanonicalSpeciesCatalog.resolve` / `isExpandedReady` | `species/CanonicalSpeciesCatalog.kt:88-94` | name/alias canonicalization and dot readiness |
| `BundledDotSpriteLibrary.catalog()/catalogBlocking()` | `species/BundledDotSpriteLibrary.kt:40-44` | cached catalog singleton |
| `BattleAssetPaths.characterFrame` / `CHARACTER_SPRITES` | `battle/BattleAssetPaths.kt:18,31-34` | frame paths |
| `IndividualSpriteManager.getAvailableFrames/hasCharacterSprites/loadSpriteFrame` | `battle/IndividualSpriteManager.kt:74-84,122-124,28-67` | frame existence + decode (used by previews) |
| `ArenaSpeciesIndex` name→dim mapping | `battle/offline/tamers/ArenaSpeciesIndex.kt:28-47` | consistency reference for which slot the tamer roster/multi-slot resolver uses (too heavy to reuse at render time) |
| `AssetSpeciesNames.nameFor` | `species/AssetSpeciesNames.kt:11-24` | existing `species.json` id→name index; could back a reverse map if one wanted to skip the canonical catalog |
| `SpriteExtrusionGlb.build` | `rendering/sprite3d/SpriteExtrusionGlb.kt:66-74,272-283` | per-pose geometry (handles unequal frame sizes) |
| `SpeciesArtworkPolicy` | `species/SpeciesArtworkPolicy.kt:5-14` | the correct priority order already exists; feed it `asset` |
| `assetPoses`/`spritePoses` | `battle/ui/offline/TrainingBattlePresentation.kt:540-550,463-471` (current) | existing pose builders |
| `BundledOfficialSprites` / `OfficialSpriteMatcher` | `species/BundledOfficialSprites.kt`, `species/OfficialSpriteMatcher.kt` | reverse-match DB sprite→card identity (very heavy; not needed here) |

---

## 8. Validation plan (do not run in this audit; for the implementing session)

### 8.1 New tests worth adding

1. **JVM — canonical slots map to real shipped folders** (`app/src/test/java/com/tamerslab/species/CanonicalSpeciesCatalogTest.kt`, alongside `shippedCatalogIsReadableAndEveryReadyIdentityReferencesTheShippedPack` at lines 72-107):
   - load `src/main/assets/species_catalog.json`; collect all species with `cardSlots`;
   - assert 660 species / 861 ids (or ≥ 650/≥ 800), each id formatted `dim\d{3}_mon\d{2}`, each `app/src/main/assets/battle_sprites/extracted_assets/sprites/<id>/<id>_01.png` exists;
   - order assertions: `bundledVbAssetIds(resolve("Agumon")) == ["dim012_mon03","dim124_mon03"]`, `Pulsemon == ["dim138_mon03","dim124_mon20"]`, `Dokugumon == ["dim013_mon06","dim034_mon08"]`; card 0 never emitted (`cardSlots` has none anyway).
2. **Device (androidTest) — repository priority with no imported card** (`app/src/androidTest/java/com/tamerslab/BundledDotSpritePersistenceTest.kt`, after `spritePrioritySwitchesArtworkWithoutOverwritingEitherSource` lines 82-98): fresh DB, VB_FIRST, no card/choice → `appearances.resolve("Agumon")` returns `Artwork(sprite = null, assetId = "dim012_mon03")`; DOT_FIRST → `sprite` is the dot set (`spriteId == catalog.resolve("Agumon").dotSprites.defaultSetId`'s sprite id); `resolve("Agumon", "dim124_mon03")` honors the caller id; and with a negative choice VB_FIRST still yields the baked asset (or the chosen dot — whichever product decision §4.8.2 lands on, assert it).
3. **Device — battle presentation picks VB for an un-owned species**: hardest layer to unit-test (needs Context/AssetManager). Either a small Robolectric-free instrumentation test around `preferredSpeciesPoses` (currently private) or assert through `resolve` (test 2) and add a `TrainingRematch`-style structural test only if the helper is made internal. At minimum assert `BundledVbSprites.frames("dim012_mon03") == (1..12)` and every `VB_BATTLE_POSES` frame exists for Agumon.
4. **Regression**: keep `SpeciesArtworkPolicyTest`, `TamerRosterAssetsTest`, `TamerArenaTest`, `ArenaPresentationTest`, `TrainingRematchTest`, `PreparedFinisherTransitionTest` green.

### 8.2 Commands (not executed here)

Unit (JVM):
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.tamerslab.species.CanonicalSpeciesCatalogTest" --tests "com.tamerslab.species.SpeciesArtworkPolicyTest" --tests "com.tamerslab.battle.offline.TamerRosterAssetsTest" --tests "com.tamerslab.battle.offline.TamerArenaTest" --tests "com.tamerslab.battle.ui.offline.TrainingRematchTest" --tests "com.tamerslab.battle.ui.arena.ArenaPresentationTest"
```
Full unit suite: `.\gradlew.bat :app:testDebugUnitTest`
Device (build type for androidTest is `integrityCheck`, app id suffix `.integritycheck` — `app/build.gradle.kts:66-70,82`):
```
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.BundledDotSpritePersistenceTest
```
Also worth assembling: `.\gradlew.bat :app:assembleDebug` (compile check of the new helper/wiring) — the uncommitted changes have not been compiled in this audit.

Relevant test files:
- `app/src/test/java/com/tamerslab/species/SpeciesArtworkPolicyTest.kt`
- `app/src/test/java/com/tamerslab/species/CanonicalSpeciesCatalogTest.kt`
- `app/src/androidTest/java/com/tamerslab/BundledDotSpritePersistenceTest.kt`
- `app/src/test/java/com/tamerslab/battle/offline/TamerRosterAssetsTest.kt`
- `app/src/test/java/com/tamerslab/battle/offline/TamerArenaTest.kt`, `.../TamerTeamConstructionSweepTest.kt`
- `app/src/test/java/com/tamerslab/battle/ui/arena/ArenaPresentationTest.kt`
- `app/src/test/java/com/tamerslab/battle/ui/offline/TrainingRematchTest.kt`, `PreparedFinisherTransitionTest.kt`, `BattleFighterScaleTest.kt`
- `app/src/test/java/com/tamerslab/rendering/sprite3d/SpriteExtrusionGlbTest.kt`

---

## 9. Asset/data facts used (measured from the working tree)

- `species_catalog.json`: 1 662 species (1 650 `DIGIMON`); **660 species have `cardSlots`** (650 `DIGIMON` + 10 references). **861 slot bindings total**, max card number 138.
- `species.json`: 1 702 entries; **861 entries with card > 0**, exactly the same `dim###_mon##` set as the canonical `cardSlots` (0 differences either way); 663 distinct names, all present as canonical names or aliases (0 unresolved); card `0` entries are the encyclopedia bucket and are intentionally skipped by `AssetSpeciesNames`/`ArenaSpeciesIndex`.
- Baked sprites: **1 137 folders**, 13 643 PNGs; all 861 card>0 slot folders exist; 1 136 folders have 12 frames, `dim000_mon01` has 11 (no `_01`).
- Expanded-ready dot species: 1 211 total; **608 also have baked VB** (the affected set in VB_FIRST); 603 are dot-only (correctly stay Dot); **42 `DIGIMON` have baked VB but no dot** (they already got VB through the legacy `assetPoses` fallback).
- Multi-slot: 160 species >1 slot, 27 >2, 21 include DiM 124, 2 have only DiM 124 (`Betamon`, `Bancho Mamemon`).
- `species.json` insertion order (used by `ArenaSpeciesIndex`/`TamerTeamResolver`) is `34, 12, 0, 1, 2, …, 124, …`; the canonical helper orders by non-124/card/slot. The only arena-visible difference after the resolver’s DiM-124 fallback filter is `Dokugumon` (see §4.8.1).

---

## 10. One-line summary

Baseline: the correct priority existed (`choice → imported → VB asset → dot`), but the 3D battle/result-form pose path resolved species **without a baked asset id**, so VB_FIRST chose the dot sprite before the valid `assetPoses(artId)` fallback — for arena fighters, packaged training opponents, un-owned catalog partners, wild catalog-definition spawns, and FORM/Jogress result models. The working tree already contains the minimal fix (canonical `cardSlots` → `BundledVbSprites` → `resolve` `VB_ASSET` → `preferredSpeciesPoses`), which covers every battle/arena surface reviewed here without changing DOT_FIRST, choice/imported priority, or placeholder fallbacks. Remaining work: automated tests for the new wiring, optional battle-vs-preview slot parity, and the separate DisplaySprite/DB surfaces (radar map/actors, farm, home, Dex, widget) tracked as task-2.
