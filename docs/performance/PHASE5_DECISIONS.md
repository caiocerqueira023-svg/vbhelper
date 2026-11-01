# Phase 5 Decisions and Audit Record

Recorded: 2026-10-31 (post Phase 6 package restructure)
Target: Alpha 0.7.0
Budgets and measurement procedures: [docs/performance/BASELINE.md](../performance/BASELINE.md)
Plan: [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md) Phase 5

This record separates three things deliberately:

1. work that is **implemented** (code exists and is covered by JVM tests/compile),
2. work that is **measured** (on-device numbers exist and are repeatable), and
3. work that is **deferred** (with the measurement or proof that would unblock it).

No performance number in this document is invented. Everything unmeasured is
labelled as such. The last full on-device performance validation predates the
Phase 6 package move; on 2026-10-31 the refactor tree was benchmarked on the
reference device for cold start and battle/farm frame timing, and those results are
recorded in [BASELINE.md](../performance/BASELINE.md) section 6.

## Status summary

| Task | Plan class | Status | Basis |
|------|------------|--------|-------|
| 5.0 Baseline measurement & budgets | Must-ship | **Complete for the available harness** — budgets + procedures recorded; cold-start and frame timing measured on the reference device | BASELINE.md section 6; Room query plans/soak remain unmeasured |
| 5.1 Texture/mesh pipeline | Follow-up | **Deferred** | KTX2 spike was no-go without device proof; no texture pipeline change can be justified without first-use/VRAM measurements |
| 5.2 Filament asset lifecycle | Follow-up | **Audited, no code change needed; soak measurement deferred** | Destroy/pooling paths already implemented (evidence below); 30-minute soak not run after Phase 6 |
| 5.3 Frame budget extension | Must-ship | **Implemented** (quality tiers + thermal downgrade); frame timing proxy measured, thermal behavior untested | `SceneFrameTier`/`SceneQuality`/`qualityForThermalStatus` + `Digifarm3dViewport` wiring; BASELINE farm/battle rows |
| 5.4 Battle state delivery | Must-ship | **Implemented behind a flag; baseline captured; flag defaults OFF until A/B validation** | `BattleSceneChannel`, `sceneSnapshot`, unit tests; battle frame-duration baseline in BASELINE |
| 5.5 Asset/catalog load | Follow-up | **Audited, explicitly deferred** | Catalog/sprite caches already exist; repacking needs first-use measurements |

## 5.0 Budgets, gates, and repeatable measurement

- Budgets, device/environment details, and copy-pasteable measure commands live
  in [docs/performance/BASELINE.md](../performance/BASELINE.md). That file is the
  single source of truth; budgets are copied from
  [docs/refactor/TARGETS.md](../refactor/TARGETS.md) and the plan's Success Metrics.
- Only a measured number may be cited as a result. The Phase 0/TARGETS smoke
  launches (debug 4,909 ms; debug-key-signed release 1,562 ms cold start) are
  single-launch smoke checks, not benchmarks, and are not current results.
- Device benchmarks are a **manual gate** today: CI has build, unit tests, and
  lint, but no device or macrobenchmark step. The `baselineprofile` module contains
  the harness: a startup+journeys `BaselineProfileRule` generator, `StartupBenchmark`
  (`StartupTimingMetric`, 5 cold iterations), and `FrameTimingBenchmark`
  (`FrameTimingMetric` for farm and battle). On 2026-10-31 the startup benchmark
  measured a **875.0 ms median TTID** on the reference device and the frame benchmark
  measured battle `frameDurationCpuMs` **P95 13.3 ms**; the full evidence is in
  BASELINE.md section 6. The relative cold-start budget is still undefined because the
  Phase 0 release baseline was never captured.
- The final 2026-10-31 verification pass covered unit tests, assemble, lint, and device
  instrumentation/install; the separate device benchmark runs afterwards produced the
  cold-start and frame-timing rows recorded in BASELINE.md. Room query plans and the
  30-minute soak were not measured.

## 5.1 Texture/mesh pipeline — deferred

Evidence gathered from the tree (no measurement):

- Only four `.glb` files are bundled: `Arena/Colosseum/Colosseum.glb`
  (2,893,360 bytes), `Arena/Radar/radar_grid.glb` (2,315,979 bytes),
  `digifarm/3d/digi_farm_3d.glb` (546,328 bytes), `digifarm/3d/resident.glb`
  (2,552 bytes). Total â‰ˆ 5.72 MB.
- `environment_themes/` adds 44 files / 21.5 MB and `Arena/` 9 files / 8.0 MB;
  these dominate native asset I/O more than the meshes.
- There is **no** `gltfpack`/meshopt/KTX2 build step in the repository
  (nothing under `scripts/` or `tools/` processes `.glb`/textures), so adding one
  is new pipeline work, not a configuration flip.
- The Phase 0 KTX2 spike recorded no Java KTX2 loader proof and no KTX2 asset in
  the repo; it is a **no-go as a default optimization until proven on device**
  ([SPIKES.md](../refactor/SPIKES.md) §2).
- Scene material/texture handling already avoids per-frame churn (see 5.2).

**Decision:** defer. Unblock conditions: a 5.0 baseline of GLB load time and
platform GPU/native memory for the farm and one arena; an A/B visual check; and a
device proof that the current glTFIO build renders the chosen compression path.
Cheapest safe candidates when that happens: gltfpack/meshopt on the arena GLBs
first (highest bytes), then KTX2 only if a sample renders correctly with
Filament 1.76.1. Do **not** assume the old plan's 50% VRAM claim.

## 5.2 Filament asset lifecycle — audited, no code change required

The plan's premise was that destroy paths and pooling were missing. The audit
found them already implemented in the current tree:

- `battle/ui/offline/OfflineBattleScene.kt`
  - Retained pools: `fighterPool` (line 201) and per-victim impact pools
    (line 1285 comment: "no textures/GLBs are built on a hit").
  - Model replacement keeps `setKey` assets and only loads a new GLB when the
    key changes (`if (presentation.setKey !in fighterPool)`, line 952).
  - Full teardown releases assets through the loaders and recycles providers:
    `releaseScene()` (line 1383) â†’ `releaseFighters()` â†’ `assetLoader.destroyAsset`,
    `viewer.destroyModel()`, `assetLoader.destroy()`, `resourceLoader.destroy()`,
    `provider.destroyMaterials()`, `provider.destroy()`.
  - Arena load/release is generation-guarded; the unattached/attached teardown
    ordering around ModelViewer's detach listener is handled explicitly.
- `farm/ui/digifarm/Digifarm3dViewport.kt`
  - Materials/textures are created once and reused; the file comment at line 64
    states no create/destroy per animation tick.
  - Resident `AssetLoader`/provider/resources are created once (line 888) and
    released in `destroyResidents()` (line 1546) before the Engine can be torn
    down; `releaseScene()` (line 1081) and the detached path (line 1104) both go
    through it.

**Residual risk:** the 30-minute soak that proves stable native/GPU memory has
not been run after the Phase 6 move. The historical `hs_err_pid20620.log` was a
Gradle daemon native-memory failure, not an app/Filament OOM
([SPIKES.md](../refactor/SPIKES.md) / [TARGETS.md](../refactor/TARGETS.md)); there is still no on-device
OOM evidence. **Decision:** no pooling rewrite without soak data; record the soak
in BASELINE.md as part of the 5.0 gate.

## 5.3 Frame budget tiers + thermal downgrade — implemented

- `rendering/FrameBudget.kt` defines `SceneFrameTier` (Paused 0 / Idle 30 /
  Active 60 / Reduced 45 fps), `SceneQuality` (LOW/MEDIUM/HIGH),
  `SceneFrameBudget.tier(...)`, `qualityForThermalStatus(...)`, and
  `SceneFramePacer`.
- `farm/ui/digifarm/Digifarm3dViewport.kt` wires the tier from visibility,
  lifecycle, surface, interaction and animation state (line 436), reads
  `PowerManager.currentThermalStatus` on API 29+ with an API 28 fallback of
  `SceneQuality.HIGH` (lines 446-449), and drives `Choreographer` from the pacer.
- Unit coverage lives in the existing frame-budget tests. The device acceptance
  ("measured 60 fps active / 30 fps idle, thermal downgrade tested") is still not fully
  met: the 3-second farm benchmark window recorded 194 median frames (≈64 fps) but its
  P95 frame CPU duration was 29.8 ms because the window includes 3D asset loading, and
  thermal-downgrade behaviour was not exercised. These gaps are tracked in BASELINE.md.

## 5.4 Battle state delivery — implemented behind a feature flag (default OFF)

Problem (confirmed by the Phase 0 spike and the code): `BattleSessionController`
ticks every 16 ms, writes `MutableStateFlow<BattleSnapshot>`, and the ViewModel
copied that whole snapshot into the single Compose UI state object on every tick,
including a deep `data class` equality check and restarting every
`LaunchedEffect(snapshot)` in `OfflineTrainingBattleScreen`.

Implemented split:

- `battle/offline/session/BattleSceneChannel.kt` (new):
  - `UI_SNAPSHOT_INTERVAL_MILLIS = 100` throttle policy.
  - `shouldPublishUiSnapshot(previous, next, elapsed, channelEnabled)` publishes
    immediately on first snapshot, pause/pause-reason change, finisher sequence
    change, blast/support window changes, and terminal result; otherwise at most
    every 100 ms.
  - `enabled` comes from `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED`
    (`app/build.gradle.kts`, default `false`). Flipping the field is the complete
    rollback switch.
- `battle/ui/offline/OfflineBattleSessionViewModel.kt`: keeps the full-rate
  stream in a new `sceneSnapshot: StateFlow<BattleSnapshot?>` and only copies
  snapshots into `OfflineBattleSessionState` through the throttle policy. Radar
  quest recording still observes every snapshot, so no gameplay bookkeeping is
  lost.
- `battle/ui/offline/OfflineBattleScene.kt`: optional `sceneSnapshotFlow`
  parameter; when provided, a `LaunchedEffect` feeds the Filament view directly
  at simulation rate (no Compose state read). A monotonic guard prevents the
  throttled UI snapshot (which can be up to 100 ms behind) from rewinding the
  scene; session changes bypass the guard.
- `battle/ui/offline/OfflineTrainingBattleScreen.kt`: both scene call sites pass
  the flow only when the flag is enabled.
- Unit tests: `app/src/test/.../BattleSceneChannelTest.kt` covers the disabled
  pass-through, first-snapshot publication, steady-state throttling, and the
  immediate pause/finisher/result transitions.

**Why default OFF:** the acceptance criterion is a Perfetto app-thread/p95
improvement with no gameplay change. The 2026-10-31 frame-timing run recorded the
current (flag-off) battle baseline — `frameDurationCpuMs` P95 13.3 ms — but did not
run the flag-on A/B, so the optimization is still unproven. Gameplay behaviour is
unchanged while the flag is off (the old per-tick path is byte-for-byte the previous
logic). Enablement is a follow-up after a flag-on/flag-off Perfetto comparison; if it
regresses, the single `buildConfigField` flip restores the old path. No performance win
is claimed for this pass.

## 5.5 Asset/catalog load — audited, deferred

Measured repository facts (sizes only; no device timings):

- `species_catalog.json` is 13,319,315 bytes; it is parsed once with Gson and
  memoized in `species/BundledDotSpriteLibrary.kt` (`cachedCatalog`, line 26;
  `catalogBlocking()`, line 40). All consumers share the catalog object.
- `dot_sprites.zip` is 12,995,584 bytes, content-addressed (SHA-256 validated) and
  extracted only once; portraits and installed sprites use bounded `LruCache`s
  (4 MB / 8 MB) and batched Room transactions
  (`installAll`, line 75). This is already the "precomputed pack" pattern the
  plan proposed for the catalog.
- `species.json` (1,546,672 bytes) is read once and cached (`AssetSpeciesNames`).
- `battle_sprites/` is still 13,811 loose PNG files / 39.8 MB (plus the
  separately handled `dot_sprites.zip`).

**Decision:** defer. The obvious candidates (packing battle sprites into an
atlas/zip, precomputing the catalog into SQLite/binary) change the asset contract
and need a measured first-use/user-visible cost before they are worth the risk;
the existing dot-sprite implementation already demonstrates the pack pattern and
the catalog is memoized. Revisit only if 5.0 shows first-use cost is user-visible
or APK size blocks the release budget.
