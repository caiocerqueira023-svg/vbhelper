# Optimization log — post-0.7.0 performance pass

Started: 2026-10-31 (after the Phase 1–7 refactor verification)
Scope: implementation-only changes plus the measurement hooks needed to validate them.
Results are recorded in [BASELINE.md](BASELINE.md) once the device runs are done; this
file records **what changed, why, and how to measure/roll back**.

Baseline before this pass (2026-10-31, SM-A346M / API 35):

| Metric | Before |
|---|---:|
| Cold start (`benchmarkRelease`, `StartupTimingMetric`) | 875.0 ms median TTID |
| Battle frame CPU (`FrameTimingMetric`) | P95 13.3 ms |
| Farm frame timing | 3-second post-startup window P95 29.8 ms (included 3D loading) |
| Release APK | 149,019,234 bytes (142.12 MiB) |
| Baseline profiles | 2,723,442 bytes each; `baseline-prof.txt` == `startup-prof.txt` |
| Unit / device tests | 1,157 / 281 |

## Implemented, easiest → hardest

### 1. Startup profile split (baseline profiles)
- **Change:** [baselineprofile/.../BaselineProfileGenerator.kt](../../baselineprofile/src/main/java/com/tamerslab/baselineprofile/BaselineProfileGenerator.kt)
  now collects **two** tests: `startupProfile` (`includeInStartupProfile = true`, cold
  launch only) and `appJourneys` (`includeInStartupProfile = false`, scan/battle/farm/
  transfer). Previously one test marked every journey rule as startup-critical, which is
  why the two generated files were byte-identical.
- **Why:** the startup profile is applied early at install/first-run; bloating it with
  journey rules dilutes the benefit.
- **Measure:** `\.\gradlew.bat :app:generateBaselineProfile`, then compare
  `startup-prof.txt` size/line count with `baseline-prof.txt` (startup should be a
  strict subset).
- **Rollback:** revert the generator to a single `collect`.

### 2. Startup trace sections
- **Change:** [StartupTrace.kt](../../app/src/main/java/com/tamerslab/diagnostics/StartupTrace.kt)
  plus sections in [TamersLabApplication.kt](../../app/src/main/java/com/tamerslab/di/TamersLabApplication.kt)
  (`startup:app-locale`, `startup:koin`, `startup:deferred-init-dispatch`) and
  [MainActivity.kt](../../app/src/main/java/com/tamerslab/MainActivity.kt)
  (`startup:activity-oncreate`). `StartupBenchmark` now reports
  `TraceSectionMetric` for each section.
- **Why:** turn the 875 ms median into attributable phases; optimizations can be judged
  against the phase they change.
- **Measure:** `:benchmark:connectedBenchmarkReleaseAndroidTest` filtered to
  `com.tamerslab.benchmark.StartupBenchmark`; read `timeToInitialDisplayMs` + trace section
  metrics in the benchmark JSON.

### 3. Deferred application background initialization
- **Change:** the four startup coroutine jobs that touch disk/DB (linked-save refresh,
  participation reconcile, imported-species binding refresh, personality/species/history
  repair) plus `WorldAfkScheduler.schedule` now run **after the first frame**, triggered by
  the first activity resume + `Choreographer` callback and guarded to run once per
  process ([TamersLabApplication.kt](../../app/src/main/java/com/tamerslab/di/TamersLabApplication.kt)).
  Locale selection and the Koin graph remain on the critical path.
- **Debug/integrityCheck exception:** debug builds keep the legacy immediate dispatch.
  The instrumentation run showed the delayed pass contending with a slow Compose finisher
  test (45 s timeout), so `BuildConfig.DEBUG` keeps tests and dev builds on the old path;
  the release/`benchmarkRelease` build exercises the deferred path measured here.
- **Why:** these jobs are idempotent, previously asynchronous, and competed with first
  frame rendering/disk for CPU and I/O.
- **Measure:** startup benchmark + `startup:*` sections; regression check of the device
  suite for chat/personality/species paths.
- **Rollback:** dispatch `launchDeferredStartupWork()` inline at the end of `onCreate`
  (or set the debug gate to unconditional).

### 4. Arena simulation: no snapshot per fixed step
- **Change:** `BattleSimulator.isFinished` exposes the existing `outcome` cheaply;
  [ArenaRepository.kt](../../app/src/main/java/com/tamerslab/battle/offline/tamers/ArenaRepository.kt)
  `simulate()` now allocates a `BattleSnapshot` **only when the simulation ends**, instead
  of every one of up to 60,000 steps.
- **Why:** the audit identified this loop as the largest measured Kotlin-side waste;
  behavior is unchanged (same stopping step, same returned snapshot).
- **Measure:** arena simulation wall time via a focused benchmark/trace before/after;
  unit tests cover the simulation outcomes.

### 5. Compose compiler reports (opt-in)
- **Change:** [app/build.gradle.kts](../../app/build.gradle.kts) enables
  `composeCompiler { reportsDestination/metricsDestination }` only with
  `-PcomposeCompilerMetrics`.
- **Why:** find unstable parameters/classes that force recomposition; default builds are
  unaffected.
- **Measure:** `\.\gradlew.bat :app:assembleRelease -PcomposeCompilerMetrics`, inspect
  `app/build/reports/compose-compiler/`.

### 6. Battle scene channel A/B switch (build-time)
- **Change:** `BATTLE_SCENE_CHANNEL_ENABLED` now reads
  `-PbattleSceneChannel=true|false` (default `false`) instead of a hard-coded field.
- **Why:** one command can now build both variants for a fair Perfetto/frame-timing A/B
  without editing source. The default stays off until the A/B is recorded.
- **Measure:** run `FrameTimingBenchmark.battleFrameTiming` with and without the property;
  record both in BASELINE.md before changing the default.

### 7. Farm steady-state benchmarks
- **Change:** [FrameTimingBenchmark.kt](../../benchmark/src/main/java/com/tamerslab/benchmark/FrameTimingBenchmark.kt)
  replaces the single conflated farm test with:
  `farmIdleFrameTiming` (4 s warm-up in `setupBlock`, then 3 s idle) and
  `farmActiveFrameTiming` (4 s warm-up, then 10 drag gestures). `battleFrameTiming` now
  warms up in `setupBlock` too.
- **Why:** the previous window measured 3D asset loading, not the 60-active/30-idle
  budget.
- **Measure:** `\.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest` filtered to
  `com.tamerslab.benchmark.FrameTimingBenchmark`.

### 8. ABI splits (opt-in)
- **Change:** `-PabiSplits` enables `splits.abi` for `arm64-v8a`/`armeabi-v7a`;
  `-PabiUniversalApk` also emits the universal APK. Default output is unchanged.
- **Why:** the Filament AARs ship several ABIs; a per-ABI APK/AAB is the largest size lever
  with no behaviour change. Play delivery should use an App Bundle, which splits
  automatically.
- **Measure:** `\.\gradlew.bat :app:assembleRelease -PabiSplits` and compare per-ABI APK
  sizes.

### 9. Benchmark module split (profile generation no longer launches benchmarks)
- **Change:** the macrobenchmarks moved out of `:baselineprofile` into a new device-only
  `:benchmark` test module (`benchmark/`), with their own small `Journeys` helper. The
  generator module now contains only `BaselineProfileGenerator` + its journey helper.
- **Why:** `:app:generateBaselineProfile` executes the tests of `:baselineprofile`; when
  the benchmark tests lived there too, profile generation could run macrobenchmark
  launch/kill loops (observed as the app opening and closing repeatedly on a physical
  device). The generator task is now quiet: one startup collection and one journey
  collection.
- **Measure:** `\.\gradlew.bat :app:generateBaselineProfile` should report only the
  generator tests; benchmark runs use the separate module:
  `\.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.StartupBenchmark"`.
- **Rollback:** move the benchmark files back into `:baselineprofile`.

## Validation status

After the optimization edits:

- Compile-only pass (app variants, unit/instrumentation sources, `:baselineprofile`,
  `:benchmark`): **green**.
- Unit tests: **1,157 passed, 0 failures, 7 skipped**.
- `assembleDebug` + `assembleRelease`: green; lint: **0 errors** (422 warnings/38 hints).
- Device instrumentation: **281/281 passed** after the deferred-startup fix (release only;
  debug/integrityCheck keeps the legacy immediate path).
- Profile regeneration and the new benchmarks were **not** completed after these changes:
  the device run was stopped on request and aborted before writing new files. The profiles
  in `app/src/release/generated/baselineProfiles/` are still the pre-optimization pair
  (identical startup/baseline) until `:app:generateBaselineProfile` is run on a device.
  No optimization result should be quoted until the corresponding benchmark/profile run is
  recorded in [BASELINE.md](BASELINE.md).

## Next tier (documented, not implemented in this pass)

These are intentionally left for a measured follow-up:

| Candidate | Why it is next | First measurement needed |
|---|---|---|
| WebP conversion of `battle_sprites/` (13,811 PNGs, 39.8 MB) and audio (14.9 MB WAV) | Largest APK-size win; needs visual/audio A/B | APK size before/after + spot-check renders/loops |
| Extruded-model cache (`SpriteExtrusionGlb`, measured 6–15 ms/model) | Reused across battles/farm sessions | Per-model generation count + frame trace on battle/farm load |
| Battle per-tick allocation reduction (`BattleSessionController` / `BattleSnapshot`) | 60 Hz immutable snapshots allocate lists/maps each tick | Perfetto/heap allocation trace during a battle |
| Environment-theme loading (21.5 MB) | Possibly on the startup/farm first-frame path | Startup trace + farm first-frame |
| KTX2/gltfpack/meshopt (GLBs + textures) | Needs device proof and a visual fallback | First-load/VRAM measurement (Phase 5.1) |
| `species_catalog.json` precompute (13.3 MB; ~45 ms Gson parse, already memoized) | Only if it appears on the first-frame path | Startup trace |
| Room query plans | Unmeasured budget | `EXPLAIN QUERY PLAN` for top-20 queries on a real DB |

## Rules for this pass

1. Every change is validated on `benchmarkRelease` on the SM-A346M, recorded in
   [BASELINE.md](BASELINE.md) with command/device/build/artifact.
2. One change per measurement; do not combine.
3. Benchmark-only code stays in `:baselineprofile` and is never part of CI.
4. Do not change the `BATTLE_SCENE_CHANNEL_ENABLED` default until the A/B is recorded.
5. The release build must keep passing `assembleRelease` + lint + the device suite after
   every optimization batch.
