# Performance Baseline, Budgets, and Measurement Procedures

Status: living document, owned by Phase 5.0 (baseline) and Phase 8.1 (validation)
Target: Alpha 0.7.0 · Last updated: 2026-10-31
Sources of truth: [TARGETS.md](../refactor/TARGETS.md),
[REFACTORING_PLAN.md](../../REFACTORING_PLAN.md), [SPIKES.md](../refactor/SPIKES.md),
[ROOM_INDEX_AUDIT.md](../refactor/ROOM_INDEX_AUDIT.md)

## 1. Purpose and rules

This file is the single home of performance budgets and the procedures that produce their
evidence.

1. No performance claim ships without an on-device measurement recorded in section 6. The
   existence of `SceneFrameBudget`, thermal downgrade, R8, or a baseline profile is not
   evidence; none of them is a measured result.
2. Budgets are copied from the plan documents, never invented here; changing one is a plan
   change needing owner approval (TARGETS.md scope triage).
3. Every result states commit, build type, device, exact command, date, and artifact path.
4. Never cite a stale run as current: a result is valid only for the commit and build it was
   captured on, and is invalidated by changes to `app/build.gradle.kts`,
   `proguard-rules.pro`, the generated baseline profiles, `rendering/`, the farm/battle
   scene code, or the asset pipeline.
5. "Not measured" is the honest default. One `am start -W` launch is a smoke check, never a
   benchmark.
6. Do not measure while a Gradle build runs on the same machine, and never run two benchmark
   sessions at once.

## 2. Reference environment

Device: Samsung SM-A346M (`a34xub`), Android API 35, ADB-authorized (TARGETS.md, Phase 0
device verification). Only physical-device results count; emulator runs are labelled
`emulator` and never close a budget. Dev machine: Windows 11, amd64, PowerShell.

| Item | Value | Source |
|------|-------|--------|
| Gradle | 9.6.1 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP / Kotlin / KSP | 9.2.1 / 2.4.0 / 2.3.9 | `gradle/libs.versions.toml` |
| compileSdk / minSdk / targetSdk | 37 / 28 / 37 | `app/build.gradle.kts` |
| benchmark / baselineprofile / profileinstaller | 1.5.0 / 1.5.0 / 1.4.1 | `gradle/libs.versions.toml` |
| Gradle memory | `-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8`, `workers.max=1`, `parallel=false`, `caching=true` | `gradle.properties` |
| Kotlin daemon | `-Xmx2560m -XX:MaxMetaspaceSize=768m` | `gradle.properties` |

Build-memory constraint: the Gradle daemon gets a 2 GB heap and one worker by design. The
Phase 0 `hs_err_pid20620.log` was a Gradle daemon native-memory failure on this machine
(1.32/2.0 GB heap used, 343 MB free RAM, 2 MB pagefile), not an app or Filament OOM
(REFACTORING_PLAN.md verification notes). Build and measure in separate steps.

SDK layout here: `$env:LOCALAPPDATA\Android\Sdk` has `build-tools\36.0.0` and
`platform-tools`, but neither is on `PATH` and `cmdline-tools` is not installed. Identity:
`applicationId` is `com.tamerslab.vbhelper` while the Kotlin namespace is
`com.tamerslab`; the launcher is `com.tamerslab.MainActivity`. Resolve the component rather
than hardcoding it, and never measure the `integrityCheck` variant (it appends
`.integritycheck` → `com.tamerslab.vbhelper.integritycheck`).

> The values recorded in this document before 2026-11-14 were measured under the previous
> `applicationId` (`com.github.nacabaro.vbhelper`). Only the installed package id changed
> (ADR 0006); build type, device and workload are otherwise the same, so those numbers stay
> the baseline. The commands below use the current id.

```powershell
$env:PATH += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices -l
adb shell cmd package resolve-activity --brief com.tamerslab.vbhelper
```

## 3. Budgets and current status

Quoted from TARGETS.md / REFACTORING_PLAN.md. Statuses are honest as of this document;
rows with a recorded value cite the section-6 entry.

| # | Metric | Budget | Current status |
|---|--------|--------|----------------|
| 1 | Cold start (release) | >=15% faster than the Phase 0 release baseline; macrobenchmark, 5 runs, same device | **Measured baseline exists, relative target undefined.** `benchmarkRelease` (R8, profileable) cold-start TTID median **875.0 ms** (min 830.5, max 1,848.8) over 5 iterations on 2026-10-31. The Phase 0 release baseline was never captured, so ">=15% faster" cannot be evaluated; this run becomes the new reference for future comparisons. |
| 2 | Battle frame time | p95 <=16.7 ms on the reference mid-range device | **Measured proxy.** `frameDurationCpuMs` P50 7.6 / P90 11.5 / **P95 13.3** / P99 20.9 ms over 3 iterations on the `benchmarkRelease` build; frame-overrun P95 1.3 ms. This is CPU frame duration, not full present-to-present time; treat it as a proxy until the Perfetto/gfxinfo cross-check. |
| 3 | Farm rendering | 60 fps active, 30 fps idle | **Partially measured.** The 3-second post-startup farm window produced a median of 194 frames (≈64 fps) but also `frameDurationCpuMs` P90 19.4 / P95 29.8 ms, because the window includes 3D asset loading. A steady active-vs-idle farm measurement is still missing. |
| 4 | Release APK size | <=155.5 MiB (163,053,568 bytes), >=15% smaller than the Phase 0 182.95 MiB | **Met on 2026-10-31.** Post-Phase-6 minified release: 149,019,234 bytes (**142.12 MiB**), 22.3% below Phase 0 (uncommitted working tree; see section 6). Phase 1 value was 147,335,418 bytes (140.51 MiB). |
| 5 | Room query plans | No unexpected `SCAN` for the top 20 queries | **Not measured.** Needs a runtime DB copy plus an authored top-20 list (ROOM_INDEX_AUDIT.md follow-up). |
| 6 | Static analysis | Zero errors; baseline entries only where justified | **Met on 2026-10-31.** Post-Phase-6 `:app:lint`: 0 errors, 422 warnings, 38 hints (32 warnings + 5 hints filtered by the pruned baseline). 25 baseline entries are now unmatched (hygiene only). Phase 0 was 52 errors. |
| 7 | Test coverage | 80%+ on `WorldDsRecordCodec`, `NdsStoryRecordCodec`, `WorldDsPartyEdits`, `BattleSimulator`, `TamerTrainerController`, `PersonalityBattleProfile` | **Met on line coverage on 2026-10-31.** Named classes: 81.0–95.8% line coverage (details in section 6). No threshold is enforced yet, and branch coverage is lower. |
| 8 | Stability | No on-device OOM; stable 30-minute soak | **Not measured.** No soak run exists; no on-device OOM has ever been observed. |
| 9 | Developer onboarding | Build + unit tests from the root README in <30 min | **Not measured.** Timed dry run pending (Phase 6.4). |

Derived arithmetic, not measurements: 155.5 MiB x 1,048,576 = 163,053,568 bytes. Non-budget
context: `battle_sprites` 13,810 PNGs / 41,355,076 bytes (39.44 MiB), `species_catalog.json`
13,319,315 bytes (12.70 MiB) (TARGETS.md, 2026-10-31).

## 4. Repeatable measurement procedures

Run from the repository root. Release has no production signing config, so
`assembleRelease` yields `app-release-unsigned.apk`; the Phase 1 device run used a copy
signed with the local debug key (TARGETS.md). Never report debug numbers as release results.

### 4.1 Cold start (release, manual)

```powershell
$pkg = "com.tamerslab.vbhelper"
.\gradlew.bat :app:assembleRelease
$apk = "app/build/outputs/apk/release/app-release-unsigned.apk"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" sign `
  --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android `
  --out app/build/outputs/apk/release/app-release-debugkey.apk $apk
adb install -r app/build/outputs/apk/release/app-release-debugkey.apk
$cmp = (adb shell cmd package resolve-activity --brief $pkg).Trim()
$runs = 1..5 | ForEach-Object {
    adb shell am force-stop $pkg | Out-Null; Start-Sleep -Seconds 2
    $out = adb shell am start -W -n $cmp; Start-Sleep -Seconds 3
    [int](($out | Select-String 'TotalTime:\s+(\d+)').Matches.Groups[1].Value)
}
$runs | Select-Object -Skip 1 | Measure-Object -Average -Minimum -Maximum
```

Screen unlocked, device idle, one launch per `force-stop`; discard run 1 and record runs
2-5. Report `TotalTime` (process start to first idle frame) and mention `WaitTime` if it
diverges.

Macrobenchmark: two device-only Gradle modules are involved. `:baselineprofile` is the
`BaselineProfileRule` generator with **separate startup and journey tests** (the startup
profile is no longer identical to the journey profile); it contains no benchmark tests, so
`:app:generateBaselineProfile` does not run macrobenchmark launch loops. `:benchmark` is a
separate `com.android.test` module with `StartupBenchmark` (cold-start
`StartupTimingMetric` + `TraceSectionMetric` for `startup:*` sections, 5 iterations) and
`FrameTimingBenchmark` (`farmIdleFrameTiming`, `farmActiveFrameTiming`,
`battleFrameTiming`). `:app` applies the baselineprofile plugin, consumes `:baselineprofile`,
and ships `profileinstaller`. Profile regeneration is device-gated:

```powershell
.\gradlew.bat :app:generateBaselineProfile
Get-ChildItem app/src/release/generated/baselineProfiles | Select-Object Name,Length,LastWriteTime
```

The startup/journey split, the benchmark-module separation, and the new benchmark windows
are documented in [OPTIMIZATION_LOG.md](OPTIMIZATION_LOG.md).

The benchmark tests are device-gated too and are **not** part of CI. Run them with the
`:benchmark` connected benchmark task on the reference device, then record the exact
command, device, build and result JSON path in section 6. Do not quote a benchmark run
that was not executed on the recorded build.

For the Phase 5.4 battle-channel A/B, build both variants with the same command plus
`-PbattleSceneChannel=true` (default is `false`) and compare the two `frameDurationCpuMs`
results; only change the default after recording the comparison here.

### 4.2 Battle frame time (p95)

Perfetto capture while a 30-second offline battle plays (API 35 supports on-device perfetto).
If the trace is truncated, use a textproto config with a larger `buffers.size_kb` and the
same data sources after checking `adb shell perfetto --help` for the flags this image
supports.

```powershell
New-Item -ItemType Directory -Force build-logs\perf | Out-Null
adb shell perfetto -o /data/misc/perfetto-traces/battle.perfetto-trace -t 40s sched freq gfx view binder_driver am wm
adb pull /data/misc/perfetto-traces/battle.perfetto-trace build-logs/perf/battle.perfetto-trace
adb shell rm /data/misc/perfetto-traces/battle.perfetto-trace
```

Open the trace at <https://ui.perfetto.dev>, read the frame timeline / `Choreographer#doFrame`
slices over the battle window, and report p95 frame duration plus jank count, keeping the
trace as the artifact. Faster first pass without the Perfetto UI:

```powershell
adb shell dumpsys gfxinfo $pkg reset
# play a 30-second battle
adb shell dumpsys gfxinfo $pkg framestats > build-logs\perf\gfxinfo-battle.txt
```

Compute p95 by column name from the `---PROFILEDATA---` block (column sets differ by API
level, so read them from the header row):

```powershell
$lines = ((Get-Content build-logs\perf\gfxinfo-battle.txt -Raw) -split '---PROFILEDATA---')[1] -split "\r?\n" |
    Where-Object { $_.Trim() -ne '' }
$h = $lines[0] -split ','
$i0 = [array]::IndexOf($h, 'INTENDED_VSYNC'); $i1 = [array]::IndexOf($h, 'FRAME_COMPLETED')
$f = $lines[1..($lines.Count - 1)] | ForEach-Object {
    $c = $_ -split ','
    if ($c[$i0] -match '^\d+$' -and $c[$i1] -match '^\d+$') { ([double]$c[$i1] - [double]$c[$i0]) / 1e6 }
} | Sort-Object
"frames={0} p95={1:N2} ms" -f $f.Count, $f[[math]::Floor($f.Count * 0.95)]
```

Report frame count and p95; if rows whose `FLAGS` says "no GPU work"/"no composition work"
were dropped, say so. Three 30-second runs, median of the three p95 values.

### 4.3 Farm FPS (active vs idle)

Same framestats or Perfetto flow on the farm screen: reset, 30 s of continuous pan/orbit
touching, dump to `build-logs\perf\gfxinfo-farm-active.txt`; reset, 30 s with no input and no
resident walking or asset streaming, dump to `gfxinfo-farm-idle.txt`.

What the code should do (implemented, unmeasured): `SceneFrameBudget` tiers are `Paused` 0,
`Idle` 30, `Active` 60, `Reduced` 45 fps with an 800 ms interaction grace
(`app/src/main/java/com/tamerslab/rendering/FrameBudget.kt`). `Digifarm3dViewport` derives
the tier from world/lifecycle/window/surface flags, touch, and animation, and downgrades via
`PowerManager.currentThermalStatus` on API 29+ (HIGH below MODERATE, Reduced at MODERATE,
Idle at SEVERE/CRITICAL) with a `SceneQuality.HIGH` fallback on API 28. "Idle" is meaningful
only after the grace window and once residents stop walking; until then the tier legitimately
stays `Active`, so compare against the tier the app selected. Thermal downgrade itself is
unbenchmarked and has no defined procedure here.

### 4.4 APK size

```powershell
.\gradlew.bat :app:assembleRelease
Get-Item app/build/outputs/apk/release/*.apk | Select-Object Name,Length,LastWriteTime
# optional, needs cmdline-tools (not installed here; verify the path first)
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat" apk summary `
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Quote `Length` in bytes, convert with /1,048,576, name the file (debug-key-signed vs
unsigned), and confirm release keeps `isMinifyEnabled`/`isShrinkResources` true with the
mapping at `app/build/outputs/mapping/release/mapping.txt`.

### 4.5 Soak / stability (30 minutes)

```powershell
adb logcat -c
adb shell dumpsys meminfo $pkg > build-logs\perf\meminfo-00min.txt
# 30-minute loop: farm active -> battle -> farm idle -> transfer preview -> chat -> farm
adb shell dumpsys meminfo $pkg > build-logs\perf\meminfo-30min.txt
adb shell dumpsys meminfo $pkg -d > build-logs\perf\meminfo-30min-detail.txt
adb logcat -b crash -d > build-logs\perf\crash-30min.txt
```

Watch `TOTAL PSS`, `Native Heap`, and `Graphics` deltas plus any `OutOfMemoryError` or
low-memory kill for the package in `logcat -b crash` and `adb shell dmesg`. Report "no OOM
observed" or "OOM observed"; a missing crash file alone is not proof of stability. Note that
`hs_err_pid20620.log` (removed in Phase 1.6) was a Gradle daemon failure, not an app or
Filament OOM.

### 4.6 Room query plans

Needs a runtime database and an authored top-20 query list. The databases are `internalDb`
(`DefaultAppContainer`) and `shared_transfer.db` (`SharedDatabaseFactory`). `run-as` works
only on debuggable builds, so pull from a debug build or use Android Studio's Database
Inspector; otherwise run each statement with an `EXPLAIN QUERY PLAN` prefix in a SQLite shell
close to the device version.

```powershell
adb exec-out run-as $pkg cat databases/internalDb > build-logs\perf\internalDb
adb shell which sqlite3   # if present, plans can be read on device as the app uid
adb shell run-as $pkg sqlite3 databases/internalDb "EXPLAIN QUERY PLAN SELECT ...;"
```

Record the query list with its provenance, the plan text, and the verdict ("unexpected
`SCAN`" vs "index used"), interpreting plans for the device SQLite version. No index change
follows from this evidence alone: it needs an `AppDatabase` version bump, a migration test,
and a write-path measurement (ROOM_INDEX_AUDIT.md).

### 4.7 Coverage and onboarding timing

```powershell
.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport
# report: app/build/reports/jacoco/jacocoTestReport/html/index.html
Measure-Command { .\gradlew.bat :app:testIntegrityCheckUnitTest } | Select-Object TotalMinutes
```

Read coverage per named class from the HTML report. Onboarding timing starts from a fresh
clone with no Gradle cache if the number is quoted as README onboarding time; say which.

### 4.8 Route / place-switch frame timing (not measured)

Owner-reported clunky, low-framerate switching between Home and its places on the SM-A346M. Reset the
framestats, switch places a fixed number of times, then compute exactly as in §4.2:

```powershell
$pkg = "com.tamerslab.vbhelper"
adb shell dumpsys gfxinfo $pkg reset
# Home -> Radar -> back -> Home -> Partners -> back -> Home -> Dex -> back -> Home -> Shop -> back
adb shell dumpsys gfxinfo $pkg framestats > build-logs\perf\gfxinfo-place-switch.txt
adb shell dumpsys gfxinfo $pkg | Select-String "Janky frames"
```

Report frame count, janky-frame count and p95 frame duration from the `---PROFILEDATA---` block, with
the exact switching sequence. Build type is part of the result: a debuggable build is not
representative of shipped performance, so repeat on `.\gradlew.bat :app:assembleDebug -PnonDebuggable`
or a release build before quoting a number. No result is recorded yet; the 2026-11-06 optimization
pass is described in `TESTING.md` and shipped unmeasured because no device was attached.

## 5. Gate policy: CI vs manual

`.github/workflows/ci.yml` runs all unit-test suites, `:app:assembleDebug`,
`:app:assembleRelease` (exercises R8), and `:app:lint` as required checks on
`ubuntu-latest` with JDK 25; `:app:jacocoTestReport` is advisory. It has no Android
device and no benchmark step, so **no performance metric is gated in CI today**. Device
benchmarks are a manual gate, with Firebase Test Lab as the only documented alternative
(REFACTORING_PLAN.md risk table, task 7.4). A release-candidate claim is accepted only
when section 6 holds a dated entry with command, artifact, and device for that metric on
the same build. A missing or stale entry means "Not measured", not "passed".

## 6. Recording template

One row per measurement; artifact paths are relative to the repository root.

| Metric | Device | Build (commit / type / versionName) | Command | Result | Verdict vs budget | Artifact path |
|--------|--------|--------------------------------------|---------|--------|-------------------|---------------|
| | | | | | | |

Recorded history:

| Date | Metric | Device | Build | Result | Verdict | Provenance |
|------|--------|--------|-------|--------|---------|------------|
| 2026-10-31 | Release APK size | n/a (host) | Phase 1 minified release, `5f98ca85` | 147,335,418 bytes (140.51 MiB), -23.2% vs Phase 0 | Meets <=155.5 MiB | TARGETS.md size context |
| 2026-10-31 | Release APK size | n/a (host) | Post-Phase-6 R8 release, uncommitted tree, versionName Alpha 0.7.0 | 149,019,234 bytes (142.12 MiB), -22.3% vs Phase 0, +1.1% vs Phase 1 | Meets <=155.5 MiB | `app/build/outputs/apk/release/app-release-unsigned.apk`; `build-logs/final-verification-2.log` |
| 2026-10-31 | Cold start, debug | SM-A346M, API 35 | debug, one launch | 4,909 ms | Smoke only, not a benchmark | TARGETS.md device verification |
| 2026-10-31 | Cold start, debug-key release | SM-A346M, API 35 | minified release, debug key, one launch | 1,562 ms | Smoke only, not a benchmark | TARGETS.md device verification |
| 2026-10-31 | Cold start, post-Phase-6 debug | SM-A346M, API 35 | debug, `am start -W`, one launch | 3,233 ms | Smoke only, not a benchmark | `build-logs/final-verification.log` and install-smoke output |
| 2026-10-31 | Cold start, post-Phase-6 debug-key release | SM-A346M, API 35 | R8 release + debug key, `am start -W`, one launch | 724 ms | Smoke only, not a benchmark | same run; versionName confirmed `Alpha 0.7.0`, no crash entries |
| 2026-10-31 | Instrumentation suite (not a budget) | SM-A346M, API 35 | `:app:connectedIntegrityCheckAndroidTest`, post-Phase-6 | 281/281 passed, 0 failed, 0 skipped | n/a | `app/build/outputs/androidTest-results/connected/integrityCheck/`; `build-logs/final-device-tests.log` |
| 2026-10-31 | Baseline profiles | SM-A346M, API 35 | Phase 1 release | 2,585,038 bytes each for `baseline-prof.txt` and `startup-prof.txt` | Startup journey only | `app/src/release/generated/baselineProfiles/` |
| 2026-10-31 | Baseline profile regeneration | SM-A346M, API 35 | Post-Phase-6 non-minified release, `:app:generateBaselineProfile` | 2,723,442 bytes each; 24,408 rules (3,858 added / 2,139 removed vs the re-mapped files) | Startup + best-effort scan/battle/farm/transfer journeys; not a performance measurement | `build-logs/baseline-profile-generation.log`; `app/src/release/generated/baselineProfiles/` |
| 2026-10-31 | Baseline profile re-map | n/a (host) | post-Phase-6 tree | 22,689 lines per file; 2,134 app-class lines rewritten, 11 unresolved synthetic lines dropped | Superseded by the device regeneration above | `scripts/remap-baseline-profiles.py` |
| 2026-10-31 | Static analysis | n/a (host) | Phase 0 `:app:lint` | 52 errors, 374 warnings, 11 hints | Failed zero-error budget | TARGETS.md static analysis baseline |
| 2026-10-31 | Static analysis | n/a (host) | Post-Phase-6 `:app:lint` | 0 errors, 422 warnings, 38 hints; 25 baseline entries unmatched | Meets zero-error budget | `app/build/reports/lint-results-integrityCheck.txt`; `build-logs/final-verification-2.log` |
| 2026-10-31 | Cold start, `benchmarkRelease` (benchmark, not smoke) | SM-A346M, API 35 | R8 minified profileable benchmark build, `:baselineprofile:connectedBenchmarkReleaseAndroidTest` (StartupBenchmark) | `timeToInitialDisplayMs` **median 875.0 ms**, min 830.5, max 1,848.8, 5 iterations | Relative budget still undefined (no Phase 0 release baseline); recorded as the new reference | `baselineprofile/build/outputs/connected_android_test_additional_output/benchmarkRelease/.../com.tamerslab.baselineprofile-benchmarkData.json`; `build-logs/startup-benchmark.log`; 5 Perfetto traces |
| 2026-10-31 | Battle frame timing (proxy metric) | SM-A346M, API 35 | `benchmarkRelease`, FrameTimingBenchmark (3 iterations) | `frameDurationCpuMs` P50 7.6 / P90 11.5 / **P95 13.3** / P99 20.9 ms; frame-overrun P95 1.3 ms | Meets the ≤16.7 ms budget as a CPU-frame-duration proxy; not a full present-time measurement | same benchmark data dir; 3 Perfetto traces; `build-logs/frame-benchmark.log` |
| 2026-10-31 | Farm frame timing (partial) | SM-A346M, API 35 | `benchmarkRelease`, FrameTimingBenchmark (3 iterations) | 3-second post-startup window: median 194 frames (≈64 fps) but `frameDurationCpuMs` P90 19.4 / **P95 29.8** ms (window includes asset loading) | Does not yet prove the 60-active/30-idle budget; steady-state split missing | same benchmark data dir; 3 Perfetto traces |
| 2026-10-31 | Named-class line coverage | n/a (host) | Post-Phase-6 unit tests, `:app:jacocoTestReport` + `:game-save-core:jacocoTestReport` | BattleSimulator 95.8% (1565/1634), TamerTrainerController 95.5% (64/67), PersonalityBattleProfile 93.3% (14/15), NdsStoryRecordCodec 93.0% (185/199), WorldDsPartyEdits 92.6% (401/433), WorldDsRecordCodec 81.0% (17/21); app total 18.5% line, game-save-core total 90.3% line | All named classes meet the 80% line target; no threshold enforced | `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`; `game-save-core/build/reports/jacoco/test/jacocoTestReport.xml`; `build-logs/app-coverage-2.log` |

Room query plans and the 30-minute soak are still **"Not measured"**; the onboarding
timed dry run is also pending. The launch figures in the smoke rows are single
`am start -W` launches: never quote them as benchmarks or as the Phase 0 baseline. The
`benchmarkRelease` rows above are the benchmark evidence for this tree.

## 7. Explicit non-claims

- There is no confirmed on-device OOM, GC-thrash, or Filament memory evidence; the historical
  `hs_err_pid20620.log` was a Gradle daemon build failure on the dev machine.
- KTX2 support is unproven on the Filament 1.76.1 / gltfio stack (SPIKES.md spike 2). The
  KTX2/texture pipeline and the `species_catalog.json`/sprite precompute work are deferred
  until the measurements above exist; the old plan's 50% VRAM figure is not assumed.
- The battle 60 Hz state-delivery split (SPIKES.md spike 3) is implemented behind
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` (default `false`) with unit-tested policy,
  but it is not a measured optimization: accepting/enabling it needs Perfetto evidence of
  reduced app-thread time against the budget in section 3.
- Frame-pacing tiers, quality tiers, and thermal downgrade are implemented; the battle
  and farm frame-timing harness now records CPU frame duration, but thermal downgrade and
  the 60-active/30-idle farm split are still not device-verified. R8 and resource shrinking
  are proven for size (Phase 1) and the current release, not for runtime speed, and
  the generator profiles startup only, so no other journey is profile-optimized yet.
- Nothing here comes from CI: every number is either a budget quoted from the plan documents
  or a recorded value with provenance.
