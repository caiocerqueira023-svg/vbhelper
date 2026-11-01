# Phase 7 Decisions — Quality, Coverage, and CI Gates

Recorded: 2026-10-31 (post Phase 6 package restructure)
Target: Alpha 0.7.0
Plan: [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md) Phase 7

## Summary

| Task | Status | Result |
|------|--------|--------|
| 7.1 Batch lint cleanup | **Complete for errors; quality plugins deferred** | 52 `MissingTranslation` errors fixed with real JA/PT-BR resources; final lint is 0 errors / 422 warnings / 38 hints; detekt/ktlint removed from the build after the aggregate quality run could not complete reliably |
| 7.2 Coverage & suite closure | **Complete for the measured target** | JaCoCo now produces a usable app report (AGP 9 class path fixed) and `game-save-core` report; all six named classes are above the 80% line target (81.0–95.8%). No threshold is enforced yet; suite closure remains incremental |
| 7.3 Public KDoc | **Partially complete** | Core domain contracts documented; full sweep + dokka gate deferred |
| 7.4 CI quality gate | **Complete for the available checks** | Required jobs: build + all unit suites + debug/release assemble + lint. No device or benchmark step (manual gate, see BASELINE.md) |

## 7.1 Static analysis

### Lint errors (fixed)

`:app:lint` failed before this pass with exactly 52 errors, all
`MissingTranslation`, for strings present in `values/` but missing from
`values-ja/` and/or `values-pt-rBR/`. This was a pre-existing backlog recorded in
[TARGETS.md](TARGETS.md) ("52 errors ... first failure nav_radar").

Fix (resources only, no behaviour change):

- Added Japanese translations for the missing strings in
  `app/src/main/res/values-ja/strings.xml`.
- Added Brazilian Portuguese translations for the missing strings in
  `app/src/main/res/values-pt-rBR/strings.xml`.
- Added locale-specific farm-home files
  `app/src/main/res/values-ja/strings_farm_home.xml` and
  `app/src/main/res/values-pt-rBR/strings_farm_home.xml` for the `place_*` /
  `home_*` family so the locale structure mirrors `values/strings_farm_home.xml`.
- Format specifiers (`%1$d`, `%2$s`, ...) are preserved verbatim; no new
  placeholders were introduced. A name-level completeness check over all
  `values/` string resources now reports 0 missing names in both locales.

The baseline file still contains pre-existing warning entries (and some entries
stale after the Phase 6 file move). Stale warning entries are harmless for the
build — lint still passes — but `app/lint-baseline.xml` should be re-recorded
with `:app:lint` once a clean run is available; that is the recommended follow-up
to remove the `LintBaselineFixed` hint noise. No error is suppressed by the
baseline.

### detekt / ktlint (removed from the build, deferred)

Phase 7.1 scaffolding added detekt `2.0.0-alpha.6` and the
`org.jlleitschuh.gradle.ktlint` `14.2.0` plugin, per-module ktlint baselines, and
a `config/detekt` configuration. The aggregate quality run did not complete: the
Gradle invocation stalled while analysing the module graph under the project's
constrained memory settings (`org.gradle.workers.max=1`, 2 GB daemon heap) and
had to be killed; no detekt report was produced. Leaving that wiring in place
would make ordinary builds unpredictable and could hang CI for every contributor.

Decision: **remove the detekt/ktlint plugin application from the build** and
delete their configs/baselines (`config/detekt`, `config/ktlint`, the two plugin
aliases and versions in `gradle/libs.versions.toml`). Keep `.editorconfig` as the
documented style anchor (charset/EOL/indent and Kotlin line-length policy).

Rationale: the plan explicitly allows simplifying or removing quality scaffolding
that cannot run reliably rather than leaving a broken or hanging build. Lint
remains the enforced static-analysis gate, and it now passes on errors.

Follow-up (post-0.7.0): reintroduce formatting/analysis as a **separate,
non-blocking CI job** with a newer stable detekt/ktlint pair, measured wall-clock
time, and a repo-wide formatting PR. Do not wire it into `check` until it is
proven to finish within CI timeouts on this codebase. The previous ktlint app
baseline was ~990 KB, which is itself a signal that a full-repo formatting pass
(not a baseline) is the right migration.

## 7.2 Coverage

- `game-save-core` has the JaCoCo plugin; `:game-save-core:test` finalizes
  `jacocoTestReport` (XML + HTML). This module is pure JVM and cheap to cover.
- The app has the JaCoCo plugin and an opt-in `:app:jacocoTestReport` task
  covering `integrityCheck` unit tests. It is **not** wired into `check`: the
  report builds over the whole app and is slow. Run it on demand:
  `.\gradlew.bat :app:jacocoTestReport`.
- The AGP 9/Kotlin class output is under
  `build/intermediates/built_in_kotlinc/integrityCheck/compileIntegrityCheckKotlin/classes`
  (the older `tmp/kotlin-classes` fallback is kept). The first report run exposed the
  stale path and produced an almost-empty report; the path was fixed and the report now
  contains the app classes.
- Measured line coverage on the final tree (2026-10-31):
  `BattleSimulator` 95.8% (1,565/1,634), `TamerTrainerController` 95.5% (64/67),
  `PersonalityBattleProfile` 93.3% (14/15), `NdsStoryRecordCodec` 93.0% (185/199),
  `WorldDsPartyEdits` 92.6% (401/433), `WorldDsRecordCodec` 81.0% (17/21).
  Every named class is above the 80% plan target. Whole-app line coverage is 18.5%
  (the app is mostly UI), while `game-save-core` is 90.3% line overall.
- No threshold is enforced yet; adding one is a follow-up. Evidence:
  [docs/performance/BASELINE.md](../performance/BASELINE.md).

## 7.3 KDoc / dokka — partially complete, gate deferred

No dokka plugin is configured and no warning gate exists. The core public contracts now
carry KDoc: `UiState`/`RequestState`/`StreamingUiState`/`OneShotEventQueue`,
`UseCaseResult`, the five use cases, and the feature repository/gateway interfaces.
The full "all public/internal domain, usecase, and repository APIs documented" sweep
(192 declarations across 73 domain files) and the dokka warning gate remain a follow-up;
they should be introduced together with dokka in a separate PR after 0.7.0.

## 7.4 CI gate

`.github/workflows/ci.yml` now runs as required checks:

1. `:game-save-core:test :companion-protocol:test :pc-save-companion:test
   :hardware-dim:test :hardware:testDebugUnitTest :app:testIntegrityCheckUnitTest`
2. `:app:assembleDebug :app:assembleRelease` (release exercises the R8 keep rules)
3. `:app:lint` (blocking now that the 52 errors are fixed)

`:app:jacocoTestReport` is advisory (`continue-on-error`) until a coverage
policy exists. detekt/ktlint are deliberately not invoked (see 7.1).

Not in CI: device instrumentation and macrobenchmarks. Those stay a documented
manual gate (device SM-A346M, API 35) because no emulator/Firebase Test Lab
runner is configured; see BASELINE.md.

## Follow-ups owned outside this pass

- Re-record `app/lint-baseline.xml` after a clean lint run (warnings only; 25 now-unmatched
  entries are hygiene).
- Restore detekt/ktlint as a bounded, non-blocking CI job after a formatting PR.
- Generate app coverage, pick named-class targets, then add a threshold.
- Introduce dokka + a full public-API KDoc sweep and warning gate.
- Add UI screenshot testing (Roborazzi/Paparazzi). Phase 3.3 is currently satisfied by the
  Compose UI smoke tests in `app/src/androidTest`.
- Pay down the Phase 2.2 mapping debt: a few `domain/repository` and use-case signatures
  still expose app data-layer types (`ArenaRunEntity`/`ChatMessageEntity`,
  `transfer.data.gameSave.*`) instead of pure domain models. Architecture tests now
  forbid Room annotations in those interfaces, but the explicit domain mapping is a
  follow-up.
- Add device/macrobenchmark CI (Firebase Test Lab or self-hosted runner) once the harness
  exists; the `baselineprofile` module now has the startup/frame harness, but CI has no
  device and no credentials.
