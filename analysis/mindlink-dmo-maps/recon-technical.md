# Mindlink × DMO maps — repository technical recon (Tamers Lab)

**Scope:** the repo-side technical envelope only. No DMO asset was opened, downloaded or
analysed here; the DMO side and rights are out of scope. Everything below is from the
working tree or measured on it.

| Field | Value |
|---|---|
| Date | 2026-11-14 |
| Tree | `397ef846` + 110 uncommitted/untracked entries (git status); nothing was built or committed |
| Method | Read source/docs, parsed bundled GLBs, measured `app/src/main/assets`, measured the existing release APK as a zip |
| Confidence | **high** = read directly / measured; **medium** = derived arithmetic or repo audit not re-tested; **low** = estimate |
| Legend | `[REPO]` observed, `[EST]` estimate, numbers in bytes unless MiB stated (1 MiB = 1,048,576 B) |

---

## 0. Bottom line

1. **Rendering is not the risk.** A GLB scene with a manifest, a themed material pass, a
   `Choreographer` loop and camera/projection already runs in four places. A fifth
   "place" is a repeat of an existing pattern, not new engine work `[REPO]`.
2. **The shipped renderer is far more minimal than "Filament" suggests.** Every bundled
   scene is unlit/baked (emissive or `KHR_materials_unlit`, **no lights, no IBL, no
   shadows**), 1,590–4,159 triangles, 2–4 PNG textures. Lit maps, lightmaps and KTX2 have
   never been exercised in this app `[REPO]`.
3. **The APK budget is the hard wall.** Recorded release APK 149,019,234 B against a
   163,053,568 B budget = **14,034,334 B (13.38 MiB) headroom**, and that APK predates
   assets that are already in the tree (+5,950,108 B raw, ≈+3.21 MiB compressed), so HEAD
   headroom is an estimated **~10.2 MiB**. One faithful DMO map is tens of MiB: it cannot
   be bundled `[REPO]`+`[EST]`.
4. **There is no on-device content-download pipeline.** Networking exists only for JSON
   APIs (online battles, chat, species catalog) and the OSM biome lookup; art is built on
   a desktop and checked in — `tools/hatchery_sprites.py:4` says it outright: "the app
   never downloads art" `[REPO]`.
5. **Rendering a "DMO map" faithfully needs four new capability families**, none present:
   Gamebryo/NIF+DDS conversion, lit/lightmap rendering (or a bake pipeline), large-scene
   memory/LOD management, and player locomotion/collision/navmesh `[REPO]`.
6. **A bundled, own-art Mindlink zone is feasible now** within ~3–4 MiB of APK if it is
   authored into the proven envelope (one GLB ≤3 MiB, ≤4 textures, baked materials). The
   spike should gate fidelity/delivery, not the viewer.

---

## 1. Fact table

### 1.1 Toolchain, packaging, platform

| Claim | Evidence | Conf. |
|---|---|---|
| Rendering stack is Filament **1.76.1** with `gltfio` and `filament-utils` | `gradle/libs.versions.toml:30`, `:103–105`; wired at `app/build.gradle.kts:266–268` | high |
| compileSdk **37**, minSdk **28**, targetSdk **37** | `app/build.gradle.kts:24`, `:28`, `:29` | high |
| Release is R8-minified with resource shrinking | `app/build.gradle.kts:59–66` | high |
| Room **2.8.4**, navigation-compose **2.9.8**, WorkManager **2.10.0**, Retrofit **3.0.0**, OkHttp **5.4.0** logging interceptor, ARCore **1.57.0**, Gson | `gradle/libs.versions.toml:19,15,22,26,28,31`; deps `app/build.gradle.kts:220,237,238,260–268,271` | high |
| Single universal APK: native libs for **4 ABIs** shipped (arm64-v8a, armeabi-v7a, x86, x86_64) | measured in `app/build/outputs/apk/release/app-release-unsigned.apk` (`lib/*`) | high |
| Optional per-ABI split exists (`-PabiSplits`) and the log calls an App Bundle the largest size lever | `app/build.gradle.kts:80–91`; `docs/performance/OPTIMIZATION_LOG.md:103–110` | high |
| `INTERNET` permission is declared; no `largeHeap`, no asset-pack/Play-Asset-Delivery dependency anywhere | `app/src/main/AndroidManifest.xml:7,25–37`; repo grep for `asset-pack`/`AssetPackManager`: none | high |
| No Asset Delivery / OBB / external content store in use: assets live only in `app/src/main/assets` | build files + grep; `docs/INDEX.md:109–110` asset conventions | high |

### 1.2 Renderer and scene stack

| Claim | Evidence | Conf. |
|---|---|---|
| Scene kinds are a closed enum `BATTLE, DIGIFARM, RADAR_FP, RADAR_ISO`; each gets a post-processing profile | `app/src/main/java/com/tamerslab/rendering/HybridSceneProfile.kt:5`, `:16–35` | high |
| Profile = post-processing on, FXAA, temporal dithering, bloom; **AO is disabled in all four kinds**; TAA off | `rendering/HybridSceneProfile.kt:41–48`, profiles `:17–34` with `ambientOcclusionEnabled = false` throughout | high |
| No Filament lights and no IBL are ever created by the app; backdrops are a solid-colour `Skybox` | grep for `LightManager`/`IndirectLight`/`DirectionalLight`: no matches; skyboxes at `farm/ui/digifarm/Digifarm3dViewport.kt:608–615`, `battle/ui/offline/OfflineBattleScene.kt:401–411`, `world/ui/RadarIsometricViewport.kt:325–332` | high |
| GLB loading = read whole file from assets on a worker, copy to a **direct ByteBuffer**, then `viewer.loadModelGlb(buffer)` on the main thread | `farm/ui/digifarm/Digifarm3dViewport.kt:695–765` (read `:712`, copy `:725–728`, load `:734`); same in `battle/ui/offline/OfflineBattleScene.kt:432–474` | high |
| There is no streaming/chunked loading API in use: the loader takes the entire GLB bytes at once | same call sites; `ModelViewer.loadModelGlb(ByteBuffer)` is the only model path | high |
| Scenes are hosted in a `TextureView` inside Compose `AndroidView`; one Filament `Engine` + `ModelViewer` per viewport | class decls: `Digifarm3dViewport.kt:286`, `battle/ui/offline/OfflineBattleScene.kt:166`, `world/ui/RadarIsometricViewport.kt:186`, `world/ui/RadarFirstPersonViewport.kt:76` | high |
| Scenes can be hot-swapped inside a live Engine (no engine teardown) | `Digifarm3dViewport.kt:671–690` | high |
| Frame pacing exists: `Paused` / `Idle 30` / `Active 60` / `Reduced 45` fps, 800 ms interaction grace, thermal downgrade | `rendering/FrameBudget.kt:8–17`, `:39–56`, pacer `:66–77` | high |
| Radar renderers cap themselves at 30 fps | `world/ui/RadarIsometricViewport.kt:307`, `:1533`; `world/ui/RadarFirstPersonViewport.kt:118` | high |
| Picking is projection-based (world→screen), not physics raycast | `Digifarm3dViewport.kt:1060–1083`, `:1338`; `RadarIsometricViewport.kt:1319–1428` | high |
| The app has no physics engine, no raycast collider, no navmesh; all "collision" is analytic 2D XZ circle math | grep (`physics|navmesh|raycast|bullet|jolt`): only circle-radius code, e.g. `battle/offline/core/BattleSimulator.kt:1545`, `battle/offline/core/BattleMovement.kt:12–62`, `farm/map/DigifarmGround.kt:22,103–130` | high |

### 1.3 Existing 3D scenes (what the repo actually renders)

| Claim | Evidence | Conf. |
|---|---|---|
| Digifarm: one authored GLB (`digifarm/3d/digi_farm_3d.glb`), 4 baked materials, residents are separate runtime-generated extruded-sprite assets | `Digifarm3dViewport.kt:178` (default asset), `:952–1010` (resident stream), `:964–965` (separate `AssetLoader`/`ResourceLoader`); `rendering/sprite3d/SpriteExtrusionGlb.kt:17,66–80` | high |
| Offline battle: arena = `Colosseum.glb` + `arena.json` manifest; fighters/effects are runtime extruded sprites with pools | `battle/ui/offline/OfflineBattleScene.kt:432–474`; `OfflineArenaManifest.kt:6–23`; plan `OFFLINE_BATTLE_3D_PLAN.md:60–64` | high |
| Arena manifest is a versioned, validated camera/scene contract (`schemaVersion == 1`, Y-up, XZ plane) | `battle/ui/offline/OfflineArenaManifest.kt:41–76` (`:53–56` validation) | high |
| Radar isometric: one authored plate GLB (`Arena/Radar/radar_plate.glb`) + runtime-extruded "cardboard" sprites; fixed 34° camera, zoom 0.5×–4× | `world/ui/RadarIsometricViewport.kt:335–358` (load), `:544–608` (frame), `:1496–1512` (sprite GLB); `DESIGN.md:477–578` (`:560–562` camera) | high |
| Radar first-person: Debug-only legacy renderer of the same arena/manifest, billboard sprites | `world/ui/RadarFirstPersonViewport.kt:142–149`, `:223`; selectable only with Debug master switch `DESIGN.md:543–546` | high |
| "World map" today is a **GPS radar**: spawns are generated around the real location, biome comes from OSM, nothing is an authored explorable map | `world/WorldRepository.kt:41,74–105`; `world/ui/WorldScreen.kt:194–200`; constants `world/RadarWorldGeometry.kt:49–52`; `DESIGN.md:469–470` (legacy `World` alias opens Radar) | high |
| Camera modes that exist: orbit+pinch (farm), fixed-pitch iso with zoom (radar), debug pitch-drag (radar FP), cinematic/orbit (battle), ARCore pose (AR battle). There is **no walk/free-look player controller** | `Digifarm3dViewport.kt:1201–1520`; `RadarIsometricViewport.kt:610–668`; `OfflineBattleScene.kt:900–930`; `ar/ArBattlePresentation` imports in `OfflineBattleScene.kt:42–47` | high |
| Only one Filament engine may be alive at a time by design | `DESIGN.md:557–559` | high |

### 1.4 Theming / "biomes and palettes"

| Claim | Evidence | Conf. |
|---|---|---|
| A manifest-driven material allowlist re-textures three scenes (`farm`, `colosseum`, `radar`) across the four saved themes | `rendering/EnvironmentThemeCatalog.kt:24,36–60`; `app/src/main/assets/environment_themes/manifest.json:51–1186` | high |
| Theming swaps **pre-baked PNG per theme**; there is no runtime hue shift. Digifarm/Colosseum textures are ARGB_8888 decodes (10–16 MiB per theme bind, measured) | `rendering/EnvironmentThemeController.kt:105–116`, `:156–165`; measured decoded sizes in §2.5 | high |
| Binding asserts every material instance of the asset is in the catalog — an unknown material set fails the theme bind | `EnvironmentThemeController.kt:149–152` | high |
| Derived assets alias the authored scene's materials (e.g. `radar_plate.glb` → `radar_grid.glb`) | `EnvironmentThemeCatalog.kt:32–34` | high |
| Theme provenance/regeneration is documented and hash-checked (`tools/build_environment_themes.py`, `--check`) | `tools/ENVIRONMENT_THEMES.md:8–38`; generator SHA-256 of source GLBs in manifest | high |
| Design contract: changing theme must not change scene topology/interaction | `DESIGN.md:117–132` | high |

### 1.5 Navigation, data, persistence

| Claim | Evidence | Conf. |
|---|---|---|
| Routes are a type-safe `@Serializable sealed interface AppRoute` + `NavHost` `composable<AppRoute.X>` blocks; new screens follow that | `navigation/AppRoute.kt:10–63`; `navigation/AppNavigation.kt:281–283,360–397` | high |
| Places return to the farm; the Radar is the exception (second full-bleed world with floating chrome) | `DESIGN.md:472–475` | high |
| Room schema version is **56** (`exportSchema = true`); migrations per version, schema JSONs checked in; a schema change needs bump + migration test + new schema JSON | `data/database/AppDatabase.kt:95–98`, `:266–1049`; `docs/INDEX.md:109–110` | high |
| Per-session/state precedent uses a raw-SQL schema object + `stateJson` column (`ArenaRun`/`ArenaMatch`) | `data/database/ArenaSchema.kt:5–18`; wired at `AppDatabase.kt:324–326` | high |
| DAO surface is one abstract accessor per table group in `AppDatabase` | `AppDatabase.kt:222–263` | high |

### 1.6 Offline-first / network (the "is there any fetch pipeline?" question)

| Claim | Evidence | Conf. |
|---|---|---|
| **No mechanism exists to fetch, stream or download remote binary content.** No `DownloadManager`, no asset-pack API, no remote-asset cache, no pack/version store. | greps over `app/src/main/java` for `DownloadManager|asset-pack|AssetPackManager|PlayAssetDelivery`: none; no writer of HTTP bodies to `filesDir`/`cacheDir` | high |
| Networking is text/JSON only: online battle Retrofit API, OpenRouter chat, species catalog from GitHub raw, Overpass OSM biome query, pinned-TLS local companion | `battle/online/RetrofitHelper.kt:43–45`; `chat/OpenRouterClient.kt:15–32`; `species/SpeciesRepository.kt:34,490–497`; `world/OpenStreetMapBiomeDetector.kt:4–6,89`; `transfer/data/gameSave/LocalCompanionClient.kt:22,94` | high |
| WorkManager is used only for the AFK world simulation, not for content downloads | `world/WorldAfkWorker.kt:10–11`; `world/WorldAfkScheduler.kt:16` | high |
| Assets are authored offline and checked in; the app never downloads art | `tools/hatchery_sprites.py:4`; conversion scripts under `tools/` | high |
| Offline independence is a stated product/build rule | `OFFLINE_BATTLE_3D_PLAN.md:46–50`; `PRODUCT.md:50` | high |
| The only on-device asset "packs" are bundled zips extracted to cache (dot sprites), sha256-validated — a precedent for integrity checks, not for delivery | `species/BundledDotSpriteLibrary.kt:95–119`; `docs/performance/PHASE5_DECISIONS.md:175–185` | high |

### 1.7 Performance budgets and evidence status

| Claim | Evidence | Conf. |
|---|---|---|
| Budgets: battle p95 ≤16.7 ms; farm 60 fps active / 30 idle; release APK ≤155.5 MiB (163,053,568 B); no OOM + 30-min soak | `docs/adr/0005-performance-budgets.md:20–29` | high |
| Reference device = Samsung SM-A346M (`a34xub`), Android API 35; emulator never closes a budget | `docs/performance/BASELINE.md:29–33,44–45` | high |
| Battle p95 **13.3 ms** recorded as a CPU-frame-duration proxy (not present-to-present) | `docs/performance/BASELINE.md:70,313` | high |
| Farm measured only as a 3-second post-startup window (≈64 fps but p95 29.8 ms, includes asset loading) — active/idle split still missing | `docs/performance/BASELINE.md:71,314` | high |
| No 30-minute soak and no on-device OOM evidence exist | `docs/performance/BASELINE.md:76,222–226,324–325` | high |
| KTX2 support on Filament 1.76.1 is **unproven**; no KTX2 Java loader proof, no KTX2 asset in repo | `docs/refactor/SPIKES.md:40–60`; `docs/performance/BASELINE.md:326–328` | medium (repo audit, not re-tested here) |
| There is no gltfpack/meshopt/KTX2 build step anywhere; adding one is new pipeline work | `docs/performance/PHASE5_DECISIONS.md:65–67` | high |
| Benchmark harness covers startup + farm/battle frame timing only, device-gated, not in CI | `docs/INDEX.md:53–54`; `docs/performance/BASELINE.md:112–125,279–286` | high |

---

## 2. Asset-size math (measured, not quoted)

### 2.1 Measured inputs

| Item | Bytes | Size | Files |
|---|---|---|---|
| `app/src/main/assets` total (working tree) | 125,302,503 | **119.50 MiB** | **13,891** |
| `battle_sprites/` | 41,730,546 | 39.80 MiB | 13,811 |
| `environment_themes/` (43 PNGs + manifest) | 23,491,046 | 22.40 MiB | 44 |
| `audio/` (4 WAV files) | 15,626,492 | 14.90 MiB | 4 |
| `species_catalog.json` | 13,319,315 | 12.70 MiB | 1 |
| `dot_sprites.zip` | 12,995,584 | 12.39 MiB | 1 |
| `Arena/` total (**includes unconverted sources**) | 10,705,202 | 10.21 MiB | 11 |
| — `Colosseum.glb` | **2,893,360** | **2.76 MiB** | 1 |
| — `radar_grid.glb` / `radar_plate.glb` | 2,315,979 / 2,311,372 | 2.21 / 2.20 MiB | 2 |
| — source `BG_Battle_PVP_Base_01.{dae,fbx,smd}` + 2 PNGs | 3,182,424 | 3.04 MiB | 5 |
| All bundled GLBs, 5 files (3 in `Arena/`, 2 in `digifarm/`) | **8,069,591** | 7.70 MiB | 5 |

**Correction to the team brief:** the Colosseum GLB is **2.76 MiB, not 10.2 MB**.
10.21 MiB is the *whole `Arena/` folder*, of which 3.04 MiB is the unconverted DAE/FBX/SMD
source that also ships in the APK. Likewise, `PHASE5_DECISIONS.md:59–62` listed "four
GLBs ≈5.72 MB"; the tree now has five GLBs (7.70 MiB) because `radar_plate.glb` was added.

### 2.2 Existing release APK composition (measured zip, 2026-10-31 build)

| Component | Uncompressed | Compressed | Notes |
|---|---|---|---|
| `assets/` (13,884 entries) | 113.82 MiB | **95.69 MiB** | → assets are ~67% of the APK |
| `lib/` (20 `.so`, 4 ABIs) | 28.88 MiB | 28.88 MiB | arm64 alone = **7.32 MiB** |
| `classes*.dex` (2 files) | 12.08 MiB | 12.08 MiB | |
| `resources.arsc` + `res/` | 2.27 MiB | 2.07 MiB | |
| other (META-INF, kotlin, google, manifest) | 0.27 MiB | 0.08 MiB | |
| zip headers/signing | — | ~3.3 MiB | total file 142.12 MiB |
| **APK file** | — | **149,019,234 B = 142.12 MiB** | matches `BASELINE.md:72,301` |

Compression is effectively nil for the art types that matter:

| Payload | Raw | In APK | Ratio |
|---|---|---|---|
| `Colosseum.glb` | 2,893,360 | 2,785,152 | **96.3%** |
| `environment_themes/*.png` (43 textures + manifest) | 22,531,724 | 22,499,616 | **99.86%** |
| `species_catalog.json` | 13,319,315 | 3,469,000 | 26.0% |

**Rule of thumb (high):** 1 MiB of GLB/PNG-authored map content ≈ 0.96–1.0 MiB of APK;
only JSON can be compressed away. Map content is bought with raw APK bytes.

### 2.3 Headroom arithmetic

| Line | Bytes | MiB |
|---|---|---|
| Budget (ADR 0005) | 163,053,568 | 155.50 |
| Recorded release APK (2026-10-31) | 149,019,234 | 142.12 |
| **Recorded headroom** | **14,034,334** | **13.38** |
| Tree-vs-that-APK asset delta, raw | **+5,950,108** | +5.67 |
| Same delta, projected as APK compressed bytes | **+3,371,094** | +3.21 |
| **Estimated HEAD headroom** (APK not rebuilt) | **10,663,240** | **10.17** |

The delta is exact and measured: 32 asset files exist in the tree but not in the APK
(+17,423,943 B raw: `Arena` 2 files, `digiline` 8, `environment_themes` 22), and 25 files in
the APK no longer exist (−11,473,910 B raw, −11,383,657 B compressed). The compressed
projection was computed method-by-method: deflate the 32 added files (PNG = stored,
`digiline` JSON compresses to 6.9%, GLB to 96%) and subtract the 25 measured compressed
entries. The APK was deliberately **not** rebuilt (no build artifacts touched by this recon).

Second lever, if delivery changes: shipping an App Bundle / per-ABI APK drops the three
foreign ABIs from the install, freeing ≈ **21.56 MiB** (28.88 − 7.32 MiB). That changes the
budget's basis (universal APK), so it is a plan change requiring owner approval
(`docs/adr/0005-performance-budgets.md:81–82`), not free headroom.

### 2.4 What fits, and what does not

| Map payload | APK cost ≈ | vs recorded 13.38 MiB | vs estimated HEAD 10.17 MiB |
|---|---|---|---|
| One scene in the proven class (≤3 MiB GLB, ≤4 textures) | 3–4 MiB | fits | fits, ~6 MiB margin |
| One 8 MiB GLB + 2 MiB textures | ~10 MiB | fits barely | **does not fit** |
| One average DMO map (naive 10 GiB Pack01 ÷ 245 maps ≈ 39–40 MiB `[ASSUME]`) | ~39 MiB | **2.9× over** | **~3.8× over** |
| All ~245 maps | ~9.5 GiB | ~60× the whole budget | n/a |

`[ASSUME]`: the "~10 GB 3D assets / ~245 maps" figure comes from the team brief and is not
verified here (Pack01 also contains characters, effects and UI). Even if the true per-map
average is 5× smaller, **one bundled map still exceeds the remaining budget**; only
on-demand delivery (new capability) or severe downscoping fits. Note the team README's own
spike target "zone bundle ≤40 MB" (`analysis/mindlink-dmo-maps/README.md:493`) is by
construction a *download*, not a bundle.

### 2.5 Texture-memory note (measured from the theme manifest + PNG headers)

A single theme bind decodes whole PNGs to ARGB_8888 (`EnvironmentThemeController.kt:105–116`):

| Scene / theme | Unique PNGs | Decoded ARGB_8888 |
|---|---|---|
| farm, any theme | 4 (128²–512²) | 1.4 MiB |
| colosseum, `vb_helper` | 2 (1024², 1254²) | 10.0 MiB |
| colosseum, light themes | 3 | 16.0 MiB |
| radar, any theme | 4 (1254², 128²) | 12.1 MiB |

That is the largest texture set the app has ever decoded. A DMO-style map with dozens of
1K–2K textures is a different class of object (see §4.2).

---

## 3. Corrections / cautions for the viability analysis

1. **Colosseum GLB = 2.76 MiB**, not "10.2 MB"; `Arena/` folder = 10.21 MiB including
   shipped source files (`app/src/main/assets/Arena/`). Use 2.76 MiB as the proven
   per-scene geometry+texture budget reference.
2. **Room is at schema 56 now** (`AppDatabase.kt:95–96` with `MIGRATION_55_56` at `:266`),
   not "55 shipped, 56 pending". A Mindlink table is a **56 → 57** bump.
3. **Headroom is ~10.2 MiB, not ~15 MiB**, once the working tree is accounted for
   (14.03 MiB recorded, minus ~3.2 MiB of already-unpacked asset growth). Any "one bundled
   zone" budget must be authored against ~10 MiB until a HEAD release APK is rebuilt and
   recorded.
4. **"Filament is already there" is true but weaker than it sounds.** It has never rendered
   a single lit pixel in this app: no lights, no IBL, no shadows, no lightmaps, no KTX2.
   All four scenes are baked/unlit. Lit-map fidelity is new work (§4.2).
5. **"Add a screen" is cheap; "add explorable movement" is not.** Routes + scene host are
   pattern work (§5). A walkable world needs collision/navmesh + a locomotion controller,
   which do not exist (§6).
6. **`environment_themes` is 43 baked PNGs for 4 themes × 3 scenes.** Adding a themed map
   multiplies its texture payload by the theme count unless the scene opts out of the
   catalog — a real content-budget decision, not a config flag.
7. **No download pipeline exists** — confirmed explicitly, not "probably none". The
   nearest precedents are a JSON catalog fetched from GitHub raw and sha256-validated
   bundled zips; neither is a delivery mechanism for binary scene packs.

---

## 4. Renderer feasibility: proven vs needs a spike

### 4.1 Proven in-repo (high confidence, existing code + assets)

| Capability | Evidence |
|---|---|
| Full GLB scene load/render from APK assets, incl. multi-material and multi-mesh | Colosseum (`Ground` + `Sphere001`, 3,676 tris, 2 textures) `OfflineArenaManifest.kt:41–76`, measured GLB §2.1 |
| Hot-swap/replace the map asset in a live engine without engine recreation | `Digifarm3dViewport.kt:671–690` |
| Versioned scene manifest validated before render (scale, bounds, spawns, camera limits, plane/up-axis) | `OfflineArenaManifest.kt:47–76`; `farm/map/Digifarm3dAssetCatalog.kt:13–25` |
| Per-theme material/texture override after gltfio resource load, with generator + hash provenance | `rendering/EnvironmentThemeController.kt:62–188`; `tools/ENVIRONMENT_THEMES.md:8–38` |
| Runtime GLB synthesis (alpha-extruded sprite solids, programmatic PNG material) | `rendering/sprite3d/SpriteExtrusionGlb.kt:17–80`, cache bound `:19` |
| Frame tiers + thermal downgrade + Choreographer pacing, incl. 30 fps caps for radar views | `rendering/FrameBudget.kt`; `RadarIsometricViewport.kt:304–313,1533` |
| Camera modes: orbit+pinch, fixed-pitch iso zoom, cinematic, ARCore pose; projection-based tap targets | §1.3 rows; `RadarIsometricViewport.kt:610–668`; `Digifarm3dViewport.kt:1060–1083` |
| Asset release/teardown ordering around ModelViewer's detach listener; retained pools | `docs/performance/PHASE5_DECISIONS.md:80–109`; `RadarIsometricViewport.kt:178–185,1428–1480` |
| 30 fps radar scene with 1 plate + runtime meshes + ground decals + themed skybox | `RadarIsometricViewport.kt` (whole), `DESIGN.md:477–578` |
| Existing device reference for scene rendering: SM-A346M, API 35; theme validation activity on that device | `docs/performance/BASELINE.md:31–33`; `tools/ENVIRONMENT_THEMES.md:98–115` |

### 4.2 Not proven — needs a spike

| Capability | Why it is not proven here | Where it would be new |
|---|---|---|
| Lit/lightmapped/IBL/shadow rendering | App never creates a Filament light or IBL; bundled GLBs have **no** `KHR_lights_punctual`; materials are unlit/emissive (measured: Colosseum `Ground` is `KHR_materials_unlit`, `Sphere001` is emissive-only; farm uses baseColor 0 + emissive maps, no extensions) | renderer config, material translation, GPU budget |
| KTX2 / GPU-compressed textures | Repo audit says no KTX2 loader proof and no sample; no build step; only PNG→ARGB_8888 path used | texture pipeline + device A/B (`docs/refactor/SPIKES.md:40–60`) |
| Scenes beyond the current class (≫4k tris, ≫4 textures, many nodes, terrain) | Largest bundled scene is 3,676 tris / 2 textures; all GLBs are static with no skins/animations; node counts 1–43, ≤15 meshes | memory, draw-call, LOD and culling work |
| Large-scene memory / OOM safety | Whole-file `ByteArray` + `DirectByteBuffer` double copy; no `largeHeap`; header says copy exists so "Filament retain the source bytes" (2× transient); no memory measurement or 30-min soak recorded | load strategy + soak evidence (`BASELINE.md:76,222–226`) |
| Transparent foliage / alpha-sorted vegetation at scale | Only sprite billboards with `alphaMode BLEND` (one quad per actor); no vegetation, no sorting strategy evidence | material strategy + perf |
| Terrain/heightfield, walkable surfaces, obstacle extraction from scene geometry | App has no heightfield, no raycast; collision is analytic circles on XZ | collision proxy + height query |
| Navmesh / walkable graph / pathfinding | None in repo (grep) | content bake + runtime lookup |
| Player locomotion + free-look controller | No joystick/first-person walk; movement is GPS (radar) or scripted/cinematic (battle/farm) | input + camera + collision integration |
| Scene streaming / LOD / chunked load | No streaming API in use; one GLB at a time | new subsystem |
| Gamebryo (`.nif`) / DDS import, lightmap bake | Repo tooling handles Collada/FBX/OBJ/SMD via Blender/PowerShell only; no NIF/DDS code anywhere in `tools/` or `app/` | external conversion tooling + bake pipeline |

---

## 5. Reusable components (concrete, with paths)

| Component | Path | How Mindlink reuses it |
|---|---|---|
| Scene host pattern (Compose `AndroidView` → `TextureView` → Engine/ModelViewer) | `farm/ui/digifarm/Digifarm3dViewport.kt:175–330`, class `:286` | Copy for `MindlinkSceneViewport`; same lifecycle/`onRelease` discipline |
| Safe teardown around ModelViewer detach | `world/ui/RadarIsometricViewport.kt:178–185,1428–1480`; `docs/performance/PHASE5_DECISIONS.md:90–95` | Mandatory for a new viewport |
| Manifest-driven scene contract | `battle/ui/offline/OfflineArenaManifest.kt` (78 lines, `schemaVersion`, camera/bounds validation) | Generalize to a Zone manifest (`assetPath` + bounds + spawn + POIs + camera) |
| Manifest validation object | `farm/map/Digifarm3dAssetCatalog.kt:13–25` | Same `Result<Manifest>` + `require(...)` style |
| GLB load helper pattern | `Digifarm3dViewport.kt:695–765`; `OfflineBattleScene.kt:432–474`; `RadarIsometricViewport.kt:335–358` | Reuse verbatim (worker read → main-thread upload, generation guard) |
| Environment theme controller/catalog (allowlisted material overrides) | `rendering/EnvironmentThemeController.kt`, `EnvironmentThemeCatalog.kt`, `assets/environment_themes/manifest.json` | Optional for v1; required if the new place must follow `DESIGN.md:117–132` |
| Post-processing profile per scene | `rendering/HybridSceneProfile.kt:5,16–35` | Add `MINDLINK` kind rather than a new renderer |
| Frame budget tiers/pacer | `rendering/FrameBudget.kt` | Reuse for 60 active / 30 idle on the new scene |
| Runtime GLB generation (programmatic mesh + PNG) | `rendering/sprite3d/SpriteExtrusionGlb.kt`, `ResidentPoseAnimation.kt`, `SpriteFacing.kt` | Partner token/POI markers without new asset files |
| Type-safe route + NavHost block | `navigation/AppRoute.kt:10–63`; `AppNavigation.kt:281–283,388` | Add `AppRoute.Mindlink` (and a zone id argument if needed) |
| Room schema object + migration + DAO + schema JSON | `data/database/ArenaSchema.kt`; `AppDatabase.kt:95–98,222–263,266`; `docs/INDEX.md:109–110` | `MindlinkProgress` table in schema 57 |
| Offline asset build pipeline + provenance checks | `tools/build_environment_themes.py`, `tools/build_radar_plate.py`, `tools/arena/convert_colosseum.ps1`, `tools/digifarm/*.py`, `tools/ENVIRONMENT_THEMES.md` | Blueprint for a `tools/mindlink/` authoring/validation script |
| Device perf harness | `benchmark/` (`FrameTimingBenchmark` farm/battle), `docs/performance/BASELINE.md` §4 | Extend with a Mindlink walk scenario rather than a new harness |

---

## 6. Missing capabilities for a genuine "explorable DMO map"

| # | Capability | Today | Needed by |
|---|---|---|---|
| 1 | On-device content fetch/store/update for scene packs | absent (§1.6) | any non-bundled map |
| 2 | Asset-pack / App Bundle delivery | absent (single universal APK) | size relief, large zones |
| 3 | NIF/DDS/Gamebryo import + lightmap bake/conversion tooling | absent (Collada/FBX/OBJ/SMD only, desktop Blender/PowerShell) | real DMO geometry/textures |
| 4 | KTX2/ASTC texture pipeline | unproven / no-go without device proof | texture memory + APK size |
| 5 | Lit rendering path (lightmaps or dynamic lights/IBL/shadows) | absent by design (baked/unlit only) | faithful DMO look |
| 6 | Large-scene streaming/LOD/culling | absent (whole GLB in memory) | zones above the proven class |
| 7 | Collision/heightfield/navmesh/walkable graph | absent (2D circle math only) | walking, camera collision |
| 8 | Player locomotion + free-look controller (touch/joystick) | absent | "explore" |
| 9 | Map/POI persistence model | absent (Room 56 has no map tables) | visits, progress, spawns |
| 10 | Content QA/perf gate for new scenes (visual + frame + memory) | only theme validation + farm/battle benchmarks | repeatable acceptance |
| 11 | Content provenance/versioning for imported third-party assets | only repo-local generator hashes | any external source |

---

## 7. Recommended minimal architecture (matches repo conventions)

Scope this as **one authored, own-art scene per release**, inside the proven envelope —
not as a DMO importer. Sizes and durations below are `[EST]` unless cited.

**Packages (no new Gradle module; `:app` already owns scenes):**

- `world/mindlink/MindlinkZoneManifest.kt` — @Serializable model: `schemaVersion = 1`,
  `assetPath`, `bounds`, `spawn`, `pois[]`, `camera{...}`, `materialAllowlist[]`; reader
  mirrors `OfflineArenaManifest.read` (assets + `require` validation) or Gson like
  `Digifarm3dAssetCatalog` for a richer model.
- `world/mindlink/MindlinkZoneCatalog.kt` — lists checked-in zones from
  `assets/mindlink/zones.json`, validates each manifest at load (fail to a readable
  error, never a crash — same contract as `Digifarm3dAssetCatalog`).
- `world/ui/mindlink/MindlinkSceneViewport.kt` — copy of the RadarIsometric scene-host
  skeleton (Engine + ModelViewer + gltfio loaders + scene `Skybox` + Choreographer loop +
  release path), driven by the manifest instead of the plate; add
  `HybridSceneKind.MINDLINK` to `HybridSceneProfile.kt`.
- `world/ui/mindlink/MindlinkScreen.kt` + `AppRoute.Mindlink` + one `composable<...>` block
  + a place entry; follow the Radar "second full-bleed world with floating chrome"
  precedent (`DESIGN.md:472–482`) or the standard place/banner pattern if it is a
  sub-place.
- Persistence: `MindlinkZoneVisit` (+ `MindlinkPoiState` if needed) via a
  `MindlinkSchema` raw-SQL object, `AppDatabase` version **57**, migration + migration test
  + new schema JSON (repo rule, `docs/INDEX.md:109–110`).
- Content: `assets/mindlink/<zoneId>/zone.glb` + `zone.json`; authored offline by
  `tools/mindlink/` (Blender bpy script modeled on `tools/digifarm/build_assets.py`, plus
  a `--check` validator like `build_environment_themes.py`) with hashes recorded in
  `zones.json`; assets checked in (the repo's established pattern).

**Budget the new content against:**

| Constraint | Target | Basis |
|---|---|---|
| Zone GLB | ≤ 3 MiB | proven class = 2.76 MiB Colosseum, 7.70 MiB all GLBs |
| Textures per zone | ≤ 4 PNG ≤ 1024² (≈ ≤6 MiB decoded) | proven class: 2–4 textures, ≤10 MiB decode |
| Triangles per zone | ≤ ~5k | proven class: 1,590–4,159 |
| Materials | unlit/emissive-baked or standard PBR with one fixed light rig **only after spike** | measured GLB/theme evidence |
| APK delta | ≤ 4 MiB | estimated HEAD headroom ~10.2 MiB |
| Frame target | 60 fps interactive / 30 fps idle tier, p95 frame ≤33.3 ms while walking `[EST]` | ADR 0005 farm tiers + 16.7 ms battle proxy |
| Memory/load | no OOM in a 30-min soak; cold load ≤8 s `[EST]` (matches the team spike gate) | ADR 0005 stability budget; no repo measurement exists |

**Explicitly out of scope for v1:** KTX2, runtime lights/IBL, chunk streaming, navmesh,
on-device download, DMO format import, >1 bundled zone.

**If the answer becomes "real DMO fidelity":** the required stack is §6 items 1–9 plus a
content pipeline; that is a multi-month program (`analysis/mindlink-dmo-maps/README.md:434–445`
already estimates 4–8 eng-weeks per zone `[EST]`), and the two-week spike below is its
feasibility gate, not its implementation plan.

---

## 8. Two-week spike questions (falsifiable)

Numbers marked `[EST]` are proposed gates, not repo-derived budgets; each must be recorded
with device, build and command to count as evidence (`docs/adr/0005-performance-budgets.md:34–45`).

| ID | Question | Pass | Fail |
|---|---|---|---|
| S1 | Can one authorized zone be converted to a single runtime GLB + manifest? | One GLB ≤8 MiB, ≤8 textures ≤2048², no runtime lights needed; loads and renders via `ModelViewer` on SM-A346M with no fatal material/geometry artifact | Any manual undocumented step; GLB >8 MiB after cleanup; missing textures/materials; load crash |
| S2 | Does the scene hold the repo's frame budgets on device? | Walking + camera motion: p95 frame CPU ≤33.3 ms `[EST]`; idle ≥30 fps / interactive ≥60 fps tier respected; no visible hitching in 3× 30-s captures | p95 >33.3 ms or sustained jank; frame pacing tier violated |
| S3 | Does it fit the APK budget? | Rebuilt HEAD release APK ≤155.5 MiB (163,053,568 B) with the zone bundled; bundled delta ≤4 MiB `[EST]`; if delivery changes, a measured per-device install size is recorded | APK >155.5 MiB; bundled delta >4 MiB with no measured delivery plan |
| S4 | Is it memory-safe? | 30-min soak with the zone active: no `OutOfMemoryError`, no low-memory kill, PSS delta <100 MiB `[EST]` | OOM/kill; unbounded PSS growth |
| S5 | Do compressed textures (KTX2) work on Filament 1.76.1/gltfio? | Sample KTX2 asset renders identically (visual A/B) on SM-A346M; measured memory/size win recorded | Loader absent/throws; visual regression; no measurable win |
| S6 | Can the user actually walk it? | Bounded walk on the authored floor with analytic collision: stays on floor, camera never exits the scene, ≥30 fps during traversal; partner token projects correctly | Falls through/off floor, camera clips out, frame rate below idle tier |
| S7 | Is the pipeline reproducible and provenance-clean? | A second person regenerates the exact bytes from recorded hashes with `tools/mindlink/ --check`; zero external binaries; no network during build | Manual fixing required; hash mismatch; any community binary used |
| S8 | Is delivery decidable on evidence? | Written comparison of "bundle 1 zone" vs "download/asset-delivery" with measured APK/AAB sizes and the owner's budget decision attached | No measurement; assumption used as fact |

Kill rule: **any of S2/S3/S4 failing with no credible fix parks the faithful route** and
keeps Mindlink at the own-art/small-scene tier.

---

## 9. What I could not verify

- **No DMO artifact was inspected.** Map sizes, texture counts, NIF/DDS contents, pack
  layout, encryption and extraction feasibility are all outside this repo-only recon. The
  "~10 GB Pack01 / ~245 maps" figures are the team brief's, not confirmed here.
- **HEAD release APK size was not rebuilt** (no build artifacts modified). All HEAD
  headroom numbers are derived from the 2026-10-31 APK plus a measured asset delta.
- **No device measurement was run**: no FPS, load time, memory or OOM data for any new
  scene; the SM-A346M budgets in §7 are proposals.
- **KTX2 support**: I did not inspect the Filament AAR; this is the repo's own no-go audit
  (`SPIKES.md:40–60`), not an independent test.
- **No soak / PSS / thermal data exists in the repo** for the existing scenes either, so
  "proven" for the renderer means "renders on device", never "measured stable".
- **No visual-fidelity analysis**: whether a converted DMO map would look acceptable under
  the app's unlit/baked pipeline is untested and cannot be answered from code.
- **Play policy / Asset Delivery limits** are not repo evidence and were not checked.
- **The uncommitted working tree was not built or tested**; the schema/route/tooling
  recommendations are conventions extracted from existing code, not compiled changes.
- **`assets/` vs APK drift**: the release APK predates 32 current asset files, so
  per-file APK numbers in §2.2 are exact for that build, not for HEAD.

---

## Appendix — measurements run (reproducible, no build)

```powershell
# asset totals and top-level folders
Get-ChildItem app\src\main\assets -Recurse -File -Force | Measure-Object Length -Sum
# GLB feature inspection (parse GLB JSON chunk: extensionsUsed, meshes, images, materials)
# APK composition and per-file compressed sizes
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::OpenRead("app\build\outputs\apk\release\app-release-unsigned.apk")
# PNG decode math: dimensions from IHDR bytes of each environment_themes PNG (w*h*4)
# theme bindings: count unique texture assets per scene/variant in environment_themes/manifest.json
# tree-vs-APK asset diff: compare relative paths and byte lengths of assets on disk vs APK entries
# network/asset-pack search: rg -i "DownloadManager|asset-pack|AssetPackManager|PlayAssetDelivery|openConnection" app/src/main/java
```

---

## Post-review corrections (Lead, 2026-11-14)

Independent review of the viability analysis (`REVIEW.md`, F12) found six small errors or
stale cross-references in this note. The body above is left as written for auditability;
these are the corrections to apply when quoting it.

| Location | Correction |
|---|---|
| §4.2 row "Scenes beyond the current class" | The *largest triangle count* is **4,159 tris / 4 textures** (`digi_farm_3d.glb`); the Colosseum is 3,676 tris / 2 textures. So the "≫4k tris" threshold is already at the top of the proven class, not beyond it. |
| §7 budget table, "Textures per zone" | 4 × 1024² ARGB_8888 decodes to **≈16 MiB** before mipmaps, not ≤6 MiB; that sits *at* the proven 10–16 MiB theme-bind ceiling. The analysis therefore recommends ≤2 × 1024² or 4 × 512² (≈4 MiB decoded) for v1. |
| §1.7, reference-device row | The "emulator never closes a budget" rule is at `BASELINE.md:31–33`; `:44–45` is the Gradle memory setting. |
| §2.3, tree-vs-APK delta | The delta has **32 added / 1 size-changed (+75 B) / 25 removed** files; the net +5,950,108 B is still correct. |
| §2.2, "other" row | Measured 0.28 MiB / 0.09 MiB (rounding only). |
| §2.4 and §7 cross-references | The references to `README.md:493` ("zone bundle ≤40 MB") and `README.md:434–445` ("4–8 eng-weeks per zone") point at an earlier draft that no longer exists. Cite README **§10.2** (Spike B) and **§8.2** (canonical effort model) instead. |

No other figure in this note is affected: the asset totals, APK composition, headroom
arithmetic, Room 56, scene-envelope and capability-gap findings were independently
re-measured during review and matched.
