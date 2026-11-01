# Refactor Targets and Phase 0 Baseline

Recorded: 2026-10-31
Repository: `C:\Users\julye\IdeaProjects\vbhelper`
Commit: `5f98ca85`
Target: Alpha 0.7.0
Plan: [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md)

## Environment

- Windows 11, amd64
- Gradle 9.6.1, launcher and daemon JVM 25.0.4.1
- AGP 9.2.1, Kotlin 2.4.0, KSP 2.3.9
- `compileSdk = 37`, `minSdk = 28`, `targetSdk = 37`
- `org.gradle.jvmargs = -Xmx2048m -XX:MaxMetaspaceSize=768m`
- `org.gradle.workers.max = 1`; `kotlin.daemon.jvmargs = -Xmx2560m`
- Baseline Profile module and `profileinstaller` wiring are present; generation is device-gated
- Koin and JaCoCo are configured; detekt/ktlint are deferred (Phase 7.1 removal, see
  [PHASE7_DECISIONS.md](PHASE7_DECISIONS.md))

## Success Metrics

| Metric | Phase 0 baseline | 0.7.0 target | Status |
|--------|------------------|--------------|--------|
| Cold start (release) | Not measured | >=15% faster than baseline | Blocked: no connected device or macrobenchmark harness |
| Release APK size | 191,840,962 bytes (182.95 MiB), unsigned, unminified | >=15% smaller; target <=155.5 MiB | Phase 1 minified: 147,335,418 bytes (140.51 MiB), -23.2% |
| Battle frame time | Not measured | p95 <=16.7 ms on reference mid-range device | Blocked: needs Perfetto/macrobenchmark |
| Farm rendering | Not measured | 60 fps active, 30 fps idle | Blocked: needs device frame metrics |
| Room query plans | Not measured; schema v55, 106 entities, 41 AppDatabase DAOs | No unexpected `SCAN` for top 20 queries | Needs runtime database and query-plan capture |
| Static analysis | 52 errors, 374 warnings, 11 hints; 52 errors are `MissingTranslation` | Zero errors; baseline entries only where justified | `:app:lint` currently fails |
| Test coverage | Not measured; JaCoCo absent | 80%+ on named domain classes | Blocked: no coverage instrumentation |
| Developer onboarding | Not measured | Build + unit tests from root README in <30 min | Build commands added to README; timed dry run pending |
| Stability | No on-device OOM evidence; no soak run | No on-device OOM; stable 30-minute soak | Blocked: needs device |
| Security | Phase 4.6 review | No secrets in logs; backup exclusions; cleartext allowlist; residual token-storage/App-Link risks tracked | Reviewed; HTTPS/Keystore follow-up tracked |

### Size context

| Item | Size |
|------|------|
| Release APK (Phase 0) | 191,840,962 bytes (182.95 MiB) |
| Release APK (Phase 1, minified) | 147,335,418 bytes (140.51 MiB) |
| Debug APK (Phase 1) | 234,502,482 bytes (223.64 MiB) |
| Integrity-check APK | 238,469,120 bytes (227.42 MiB) |
| Release mapping file | 101,542,000 bytes (96.84 MiB) |
| `battle_sprites` PNGs | 13,810 files, 41,355,076 bytes (39.44 MiB) |
| `species_catalog.json` | 13,319,315 bytes (12.70 MiB) |

The Phase 1 release enables R8 and resource shrinking and keeps
`app/build/outputs/mapping/release/mapping.txt`. Baseline profiles were generated
on the connected SM-A346M (API 35) into
`app/src/release/generated/baselineProfiles/`; both generated files are
2,585,038 bytes and currently identical. The generator covers startup only; the
remaining journeys are still Phase 5/8 work.

## Test Baseline

This table is the Phase 1 module-split baseline (recorded before the Phase 6 package
restructure); it is historical. The post-Phase-6 results are in
[Post-Phase-6 verification](#post-phase-6-verification-2026-10-31).

| Suite | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| `:app:testIntegrityCheckUnitTest` | 892 | 0 | 0 | 3 |
| `:game-save-core:test` | 192 | 0 | 0 | 0 |
| `:companion-protocol:test` | 4 | 0 | 0 | 0 |
| `:pc-save-companion:test` | 11 | 0 | 0 | 0 |
| `:hardware:testDebugUnitTest` | 22 | 0 | 0 | 0 |
| `:hardware-dim:test` | 28 | 0 | 0 | 4 |
| **Total** | **1,149** | **0** | **0** | **7** |

All suites were executed on 2026-10-31 after the Phase 1 package/module rename.
The app suite now includes the Phase 2 Koin/architecture tests, Phase 3 UI-state/typed-navigation tests, and Phase 4 domain/companion tests.
Four `hardware-dim` tests are intentionally skipped.

## Device Verification

Device: Samsung SM-A346M (`a34xub`), API 35, authorized over ADB.

- Debug APK installed over the existing package and launched successfully.
  Single cold launch: 4,909 ms; this is a smoke measurement, not a benchmark.
- The minified release APK was signed locally with the debug key and installed
  over the debug build. It launched as `Tamers Lab` with a 1,562 ms cold start.
  Production signing and a true older-version upgrade remain pending.
- `:app:connectedIntegrityCheckAndroidTest` executed 281 tests with 0 failures.
  Triage fixed 25 initial failures: nine rename/schema assertions, four
  Digimon-scan fixtures, two chat UI cases, three training-picker cases, one
  rematch timing case, one radar navigation case, and five data-path integrity
  cases (`ExperimentalWorldDsMoveTest` 3, `IndividualPersistenceTest` 1,
  `NativeMaterializationTest` 1). The finisher presentation test now allows the
  slower device to charge its special before the layout assertions.
- `:app:generateBaselineProfile` succeeded and wrote
  `app/src/release/generated/baselineProfiles/baseline-prof.txt` and
  `startup-prof.txt` (2,585,038 bytes each).

## Characterization Coverage

- **World DS record codec:** existing field-level tests plus the new
  `WorldDsRecordCodecGoldenTest`, which locks the exact 384-byte/512-character
  Base64 fixture for one fixed record.
- **Battle simulation:** `BattleSimulatorTest` covers deterministic combat
  rules; `BattleSessionControllerTest` covers ticking, fractional frame time,
  pause composition, and Blast/finisher sequencing.
- **Transfer preview:** `DestinationPreviewPlannerTest` covers same-format
  blocking, cross-game unknowns, fingerprint invalidation, and field
  dispositions; `DestinationBatchPreviewTest` covers batch selection.
- **Room schema:** the new `RoomSchemaCharacterizationTest` locks schema v55,
  its identity hash, 106 tables, and the presence of the core tables.

These are seeds, not full coverage. Remaining gaps land with their owning phase,
as required by the plan.

## Static Analysis Baseline

`:app:lint` failed the build on 2026-10-31 after 11m30s:

- 52 errors, 374 warnings, 11 hints
- All 52 errors are `MissingTranslation`; first failure:
  `app/src/main/res/values/strings.xml:74` (`nav_radar`)
- Top warning groups: `UnusedResources` 145, `LogNotTimber` 83,
  `PluralsCandidate` 48, `UseKtx` 28
- `app/lint-baseline.xml` has 163 entries; 66 entries were not found in the
  current project, so the baseline is stale
- Phase 1 `lintVitalRelease` completed with no errors or warnings; full `:app:lint`
  remains a Phase 7.1 cleanup item, not a Phase 1 blocker

### Phase 7.1 cleanup (2026-10-31)

- All 52 `MissingTranslation` errors were fixed with real JA and PT-BR resources
  (`values-ja`/`values-pt-rBR` strings plus locale farm-home files). No error is
  suppressed by the baseline; locale string-name coverage is complete.
- `app/lint-baseline.xml` was pruned to 62 entries, all pre-existing warnings whose
  source files still exist; stale entries (including the old `MissingTranslation`
  ones) were removed. Warnings never failed the build, so this is hygiene.
- Final `:app:lint` verification is part of the post-Phase-6 verification pass;
  see the status section below.

## Scope Triage

This is the Phase 0 default. The release gate is Must-ship only; reclassifying
an item changes the 0.7.0 scope and needs owner approval. Owner confirmation of
this split is the remaining Phase 0 gate; device-dependent metrics are deferred
to Phase 5.0.

| Class | Tasks | Release rule |
|-------|-------|--------------|
| **Must-ship** | 1.1-1.8, 4.1, 4.6, 5.0, 5.3, 5.4, 7.1, 7.4, 8.1-8.3 | Gates Alpha 0.7.0 |
| **Enabler** | 2.1-2.5, 3.1-3.3, 4.2-4.5, 6.1-6.4 | Merge incrementally when ready; may slip without blocking release |
| **Follow-up** | 5.1, 5.2, 5.5, 7.2 beyond named classes, 7.3 | After 0.7.0 unless Phase 5 data justifies earlier work |

## Baseline Commands

```powershell
# Unit tests
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test
.\gradlew.bat :app:testIntegrityCheckUnitTest

# Lint (currently known to fail on pre-existing MissingTranslation errors)
.\gradlew.bat :app:lint

# APK size (after a build)
Get-Item app/build/outputs/apk/release/app-release-unsigned.apk | Select-Object Length
```

## Known Constraints

- No connected Android device was used in this Phase 0 pass.
- Cold start was smoke-checked once on debug (4,909 ms after install), but
  battle frame time, farm FPS, Room query plans, soak behavior, and security
  checks remain unmeasured.
- Debug install/launch passed on SM-A346M (API 35). The local debug-key release
  upgrade passed; production signing and an older-version upgrade remain pending.
- CI is defined in `.github/workflows/ci.yml`; its lint step is now blocking (the old
  52-error backlog was fixed in the post-Phase-6 pass; see the status section below).
- `hs_err_pid20620.log`, `replay_pid20620.log`, root `domain/`, and root
  `screens/` were removed in Phase 1.6.
- Lint errors must not grow before Phase 7.1; do not add new violations.

## Post-Phase-6 verification (2026-10-31)

Executed on the uncommitted working tree after the Phase 5/6/7 edits, from the
repository root with `--no-daemon` and the bounded settings in `gradle.properties`.
No commit was created. This section supersedes an earlier pass in the same session
(1,155 tests, APK 149,002,850 bytes); the numbers below include the final Phase 1/5/7
gap work and are the current record.

### Compile-only pass

One compile-only invocation (debug/release/integrityCheck Kotlin, integrityCheck unit
test + instrumentation sources, all library modules, and the benchmark-module variants)
completed **BUILD SUCCESSFUL** with warnings only (`10m 14s`).

### Final verification

| Check | Command | Result |
|-------|---------|--------|
| Library unit tests | `:game-save-core:test :companion-protocol:test :pc-save-companion:test :hardware-dim:test :hardware:testDebugUnitTest` | 257 tests, 0 failures, 0 errors, 4 skipped |
| App unit tests | `:app:testIntegrityCheckUnitTest` | 900 tests, 0 failures, 0 errors, 3 skipped |
| **Unit total** | all of the above | **1,157 tests, 0 failures, 0 errors, 7 skipped** |
| Debug + release build | `:app:assembleDebug :app:assembleRelease` | Success; R8 minification and resource shrinking ran with the regenerated profiles |
| Lint | `:app:lint` | **0 errors**, 422 warnings, 38 hints; 32 warnings + 5 hints filtered by the pruned baseline |
| Device instrumentation | `:app:connectedIntegrityCheckAndroidTest` | **281/281 passed**, 0 failed, 0 skipped, on SM-A346M / API 35 |
| App coverage | `:app:jacocoTestReport` | Report generated; named app classes: BattleSimulator 95.8%, TamerTrainerController 95.5%, PersonalityBattleProfile 93.3% line |
| game-save-core coverage | `:game-save-core:test` (finalizes report) | Report generated; WorldDsRecordCodec 81.0%, NdsStoryRecordCodec 93.0%, WorldDsPartyEdits 92.6% line |
| Baseline profiles | `:app:generateBaselineProfile` | Regenerated on device: 2,723,442 bytes each; 24,408 rules (3,858 added / 2,139 removed vs the re-mapped files) |
| Cold-start benchmark | `:baselineprofile:connectedBenchmarkReleaseAndroidTest` (StartupBenchmark) | R8 `benchmarkRelease`, 5 cold iterations: TTID **median 875.0 ms** (min 830.5 / max 1,848.8), 5 Perfetto traces |
| Frame-timing benchmarks | same task (FrameTimingBenchmark) | Battle `frameDurationCpuMs` P95 **13.3 ms** (P90 11.5); farm 3-second post-startup window P95 29.8 ms with median 194 frames (â‰ˆ64 fps, includes asset loading) |
| Install + cold launch smoke | `adb install -r`, `am start -W` | Debug: install OK, cold launch 4,241 ms; debug-key-signed release: install OK (versionName `Alpha 0.7.0`), cold launch 820 ms; no crash-buffer entries; debug build restored afterwards |

Notes and caveats:

- Earlier in the same session, the first final-verification batch's device step aborted
  **before running any test** with `No connected devices!` because the wireless-ADB link
  dropped during a 44-minute batch; the device was reconnected and the device-only step
  was run once. The batch above is the complete final pass and was not re-run after it.
- Launch timings are single `am start -W` **smoke** values, not benchmarks. The
  `benchmarkRelease` rows are the benchmark evidence; they are recorded in
  [docs/performance/BASELINE.md](../performance/BASELINE.md).
- The cold-start budget is relative to a Phase 0 release baseline that was never
  captured, so ">=15% faster" cannot be evaluated; the 875 ms median is the new
  reference. The battle 13.3 ms is a CPU-frame-duration proxy, not full present time. The
  farm window includes 3D asset loading, so 60-active/30-idle is still not proven.
- Lint: 25 unmatched baseline entries are hygiene only (warnings/hints, never errors);
  re-record the baseline with a clean lint run in a follow-up. No error is suppressed.
- No production signing was attempted; the release APK is unsigned and the smoke copy
  was debug-key signed locally. The previous debug build was restored on the device
  afterward and no app data was cleared or restored.

### Release artifact (same run)

- Minified unsigned release APK: **149,019,234 bytes (142.12 MiB)**, i.e. 22.3% smaller
  than the 182.95 MiB Phase 0 baseline and within the <=155.5 MiB budget; this is
  1.1% larger than the Phase 1 minified build (147,335,418 bytes).
- R8 mapping regenerated: `app/build/outputs/mapping/release/mapping.txt`
  (101,223,636 bytes).
- Debug-signed smoke copy: 150,110,629 bytes, verified with the Android Debug
  certificate; installed over the debug build with data preserved and launched.

### Still open after this pass

- Room query plans and the 30-minute stability soak are unmeasured; onboarding timing
  (<30 min) is unmeasured because it needs a clean-cache dry run.
- Farm steady-state active/idle frame rates and thermal-downgrade behavior are not
  device-verified; the battle channel flag stays OFF until a Perfetto A/B run.
- The device matrix is API 35 only; API 28/31/34 instrumentation was not available.
- Coverage has no enforced threshold; detekt/ktlint, dokka, and UI screenshot testing
  remain documented follow-ups.
- `versionCode` remains `1` (no Play upload; a Play release needs an increment).
- Security sign-off still needs owner review of the residual risks in
  [PHASE4_DECISIONS.md](PHASE4_DECISIONS.md) and
  [docs/security/SECURITY_REVIEW_0.7.0.md](../security/SECURITY_REVIEW_0.7.0.md).
