# Phase 0 Spike Decisions

Recorded: 2026-10-31
Related plan: [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md)
Baseline: [TARGETS.md](TARGETS.md)

These are timeboxed paper/static spikes. No production code was changed and no
new Gradle build was run for them. Each decision must be rechecked when its
owning phase starts.

## 1. Hardware module merge

**Question:** Can `hardware` and `hardware-dim` become one Android library
without breaking DIM parsing or bloating the APK?

**Evidence:**

- `hardware` is an Android library with hardcoded AndroidX/Material versions.
- `hardware-dim` is a standalone `java-library` using Java 11, Lombok 1.18.42
  (`compileOnly` plus `annotationProcessor`), commons-io 2.11.0, slf4j 1.7.32,
  `at.favre.lib:bytes` 1.5.0, and JUnit 5.
- DIM sources do not depend on Android, but Lombok and slf4j are avoidable
  packaging risks; no Android-specific proof exists yet.

**Decision:** Conditional go, fallback first.

- Merge NFC into `hardware` now.
- Keep DIM as a JVM `hardware-dim` module behind a `hardware` facade until a
  compile/package spike proves the Android-library path.
- Do not remove Lombok or rewrite DIM as part of the initial merge.
- Revisit full source merge only after the DIM unit tests pass from the Android
  module and the APK dependency report shows no unwanted runtime libraries.

**Next action:** Phase 1.1 creates the facade and runs the first merge build;
record the result and choose `hardware` vs `hardware-dim`.

**Phase 1 result:** the fallback was implemented. `hardware` and `hardware-dim`
compile, their unit tests pass, and the app consumes DIM through the `hardware` API.

## 2. Filament 1.76.1 KTX2 support

**Question:** Can the current Filament/gltfio stack load KTX2 textures?

**Evidence:**

- Filament, gltfio, and utils are all 1.76.1.
- The gltfio AAR exposes `AssetLoader`, `MaterialProvider`, and
  `ResourceLoader`; no Java KTX2-specific loader class is visible in the AAR.
- Current packaged scenes are `.glb` files; no KTX2 asset or device render test
  exists in the repository.

**Decision:** No-go as a default optimization until proven on a device.

- Keep PNG/WebP and `gltfpack`/meshopt as the fallback in Phase 5.1.
- Add KTX2 only if a sample asset renders correctly on the reference device with
  the current gltfio version and the measured memory win is real.
- Do not assume the 50% VRAM reduction from the old plan; require measurement.

**Next action:** Phase 5.1 creates a tiny KTX2 sample and a device A/B check
after Phase 5.0 budgets exist.

## 3. Battle 60 Hz state delivery

**Question:** Does the current battle path publish a full snapshot to Compose on
every simulation tick?

**Evidence:**

- `BattleSessionController` uses `FRAME_INTERVAL_MILLIS = 16` and writes
  `_snapshot.value = simulator.snapshot()` each tick.
- `OfflineBattleSessionViewModel` collects that `StateFlow` and copies
  `OfflineBattleSessionState(snapshot = snapshot)`.
- `OfflineBattleScene` consumes the latest snapshot for Filament and UI.

**Decision:** Go to a prototype; do not ship a full-snapshot Compose update at
60 Hz.

- Split the render channel from UI state: pass the latest snapshot to Filament
  directly and publish a lower-frequency/derived UI state.
- Prototype `derivedStateOf` and list-concatenation hoisting; keep the old path
  behind a flag until Perfetto and characterization tests validate it.
- No gameplay behavior change is allowed during the optimization.

**Next action:** Phase 5.4 follows the prototype from this spike; Phase 5.0
records the baseline first.

**Phase 5.4 result:** the split is implemented behind
`BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` (default `false`): the full-rate snapshot
goes to the Filament view through `OfflineBattleSessionViewModel.sceneSnapshot`, while
Compose UI state is throttled by `BattleSceneChannel` (100 ms, with immediate
pause/finisher/result transitions) and a monotonic guard prevents the throttled UI
snapshot from rewinding the scene. Unit tests cover the policy; the Perfetto/p95
validation is still pending, which is why the flag stays off. See
[docs/performance/PHASE5_DECISIONS.md](../performance/PHASE5_DECISIONS.md) §5.4.

## 4. Koin module graph and startup

**Question:** Can the manual container move to Koin without duplicating the
database or changing startup behavior?

**Evidence:**

- `TamersLabApplication.onCreate()` constructs `DefaultAppContainer`.
- `AppContainer` exposes a large lazy graph (database, repositories, services,
  coordinators, workers).
- `WorldAfkWorker` constructs a second `DefaultAppContainer(applicationContext)`
  instead of reusing the application-owned graph.
- Koin is not currently a dependency.

**Decision:** Go, with a pre-refactor.

- Replace the worker's direct container construction with an application-owned
  graph or a Koin-aware Worker factory before declaring the migration complete.
- Define `AppModule`, `DatabaseModule`, `NetworkModule`, and `HardwareModule`;
  keep lazy initialization and add a module-graph verification test.
- Use Koin's Kotlin-first API; no annotation processing.
- Measure startup before/after; a startup regression is a no-go for the hard
  cutover.

**Next action:** Phase 2.1 performs the cutover; Phase 1.8 provides the CI
baseline needed to compare startup builds.

## 5. R8 release keep rules

**Question:** Is the app ready to enable R8 and resource shrinking?

**Evidence:**

- `app/build.gradle.kts` has `isMinifyEnabled = false`.
- `app/proguard-rules.pro` contains only the generated comment template.
- The app uses reflection-heavy Gson/`TypeToken`, Retrofit, protobuf-lite, Room,
  Filament, WorkManager, DataStore, and future Koin/kotlinx.serialization APIs.
- `integrityCheck` is a separate test build type and must keep working.

**Decision:** Go, but treat it as a high-risk release gate.

- Add explicit keep rules for Gson models/type tokens, Retrofit interfaces,
  protobuf-lite, Room, Filament/gltfio, WorkManager, and the future DI/serialization
  stack as those dependencies arrive.
- Build a minified release, retain the mapping file, install it on a device, and
  run startup, scan, battle, farm, transfer, and chat smoke flows.
- Target the plan's 15-35% size range; do not claim a number before measuring.

**Next action:** Phase 1.3 owns the first minified build and smoke test; if it
fails, fix keeps and retry rather than disabling minification.

**Phase 1 result:** the minified release builds; APK size fell 23.2% to 140.51 MiB.
A debug-key-signed copy installed over the debug build and launched on the
SM-A346M; production signing and an older-version upgrade remain pending.

## Cross-spike risks

| Risk | Owner phase | Mitigation |
|------|-------------|------------|
| DIM merge pulls Lombok/commons-io/slf4j into the APK | 1.1 | Keep `hardware-dim` fallback; inspect dependencies |
| KTX2 support exists natively but not documented | 5.1 | Device/asset proof; keep fallback |
| Battle split changes timing or AI behavior | 5.4 | Characterization tests plus feature flag |
| Koin and worker create duplicate databases | 2.1 | One app-owned graph; graph test |
| R8 breaks reflection | 1.3 | Keep rules, mapping, device smoke, rollback build type |
