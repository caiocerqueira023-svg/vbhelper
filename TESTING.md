# Testing and verification

This is the current testing guide for Tamers Lab. The old 200 KB chronological
regression log was archived at
[docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md);
it is historical evidence, not the current result. Performance evidence lives in
[docs/performance/BASELINE.md](docs/performance/BASELINE.md); quality-tooling decisions
live in [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md).

## Radar place redesign (2026-11-04, uncommitted tree)

This pass replaced the Radar place's 2D/first-person split with a full-bleed isometric radar world and
fixed the encounter-asset starvation that made nearby Digimon slow or absent. Evidence for this
working tree only:

| Check | Result |
|---|---|
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 984 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleDebug` | Passed; installed on Samsung SM-A346M (wireless ADB), API 35 |
| Radar flow on device | Home → Radar opens the isometric plate with the partner token, wild cardboard sprites and the digivice-bezel compass (tick ring, cardinal beacons, nearest-Digimon needle; no distance rings); zoom, vertical tilt, labels, options sheet and the Debug-only legacy views exercised |
| Encounter loading | Spawn assets appear immediately after the remembered Room flow emits (empty-list restarts removed); shared GLB cache + parallel builders + nearest-first uploads cover a 16–20 Digimon roster; expired-region recovery repopulates sprite rows on entry and stale placeholder encounters are removed |
| Screen captures (this tree) | `.impeccable/review/radar-18-final100.png`, `radar-19-spread.png`, `radar-17-wide.png`, `radar-20-options.png`, `radar-21-labels.png` (light palette); `radar-11-ready.png` (dark palette). The label-collision pass that followed these captures is compile/unit-test verified only. |
| `:app:lint` | 0 errors, 471 warnings, 38 hints (32 warnings and 5 hints filtered by `lint-baseline.xml`). A first lint run crashed inside the K2 UAST resolver before reporting; the clean rerun above supersedes it. |

Boundaries and remaining acceptance work:

- The battle handoff and AR stage were not re-exercised on device in this pass. Their code paths are
  unchanged except for the `WorldRadarBattleContent` full-bleed radar branch, which keeps the
  established battle/AR layout.
- The legacy 2D and first-person renderers are untouched and remain reachable only with the master
  Debug switch on (Radar options → Legacy view). A native capture of the legacy 2D view is pending.
- No TalkBack or large-font (1.3×) capture of the new radar was made in this pass; the chrome keeps
  48dp targets and the existing semantics contract.
- Sprite settle time was measured during development (first sprite ~1 s, a 20-Digimon roster ~3.7 s
  on a cold process, including plate load and GLB build) with temporary logging that has been
  removed; no benchmark entry was recorded.
- **Wild↔wild social pause (2026-11-04).** `WildSocialInteractionGate` is off, so autonomous wild
  pair chat/battles are not created, activated or shown; the retained planner/dialogue/combat code
  stays compiled. Covered by `WildSocialInteractionGateTest` (family matching, player encounters
  unaffected, re-enable path). Wild → player and player-directed encounters were not behaviourally
  re-exercised on device after this change; the gate keeps them out of scope by construction.
- **Radar cardboard orientation (2026-11-04).** Travel is derived from real-world positions and the
  display orientation from a pure helper (`radarCardboardOrientation`), so compass/zoom motion cannot
  flip sprites. Covered by three JVM tests: standing Digimon ignore compass motion, the four travel
  directions match the farm' camera-assisted profile/mirror behaviour, and a turning compass rotates
  the sprite with the plate without chatter at the mirror latch.

## Current verified state (2026-10-31, uncommitted tree)

| Check | Result |
|---|---|
| JVM unit tests (all modules) | 1,157 tests, 0 failures, 0 errors, 7 skipped |
| Debug + R8 release assembly | Passed (minified APK 149,019,234 bytes / 142.12 MiB, regenerated profiles packaged) |
| `:app:lint` | 0 errors (422 warnings, 38 hints; warnings never gate) |
| Connected instrumentation (`:app:connectedIntegrityCheckAndroidTest`) | 281/281 passed on Samsung SM-A346M, API 35 |
| Install + cold launch smoke | Debug and debug-signed R8 release installed and launched; no crash-buffer entries |
| Coverage (named classes) | 81.0–95.8% line coverage; see [BASELINE.md](docs/performance/BASELINE.md) |
| Benchmarks (device-gated) | Cold-start TTID median 875 ms; battle frame CPU P95 13.3 ms; farm 3-second window P95 29.8 ms (includes loading) |

These are current for this working tree only. Do not quote older suite counts from the
archive or [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md) as current.

## Commands (Windows PowerShell, repository root)

```powershell
# JVM/library module suites
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test `
    :hardware-dim:test :hardware:testDebugUnitTest

# App JVM unit tests (integrityCheck test build type)
.\gradlew.bat :app:testIntegrityCheckUnitTest

# Static analysis (blocking gate; must stay at zero errors)
.\gradlew.bat :app:lint

# Builds
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease        # R8; mapping at app/build/outputs/mapping/release/

# Device instrumentation (device required)
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest

# Baseline-profile regeneration (device required; startup + best-effort journeys)
.\gradlew.bat :app:generateBaselineProfile

# Macrobenchmarks (device required; not part of CI). These live in :benchmark, a separate
# test module, so :app:generateBaselineProfile only runs the profile generator.
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.StartupBenchmark"
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.FrameTimingBenchmark"

# Optional optimization instrumentation (no behaviour change by default)
.\gradlew.bat :app:assembleRelease -PcomposeCompilerMetrics   # Compose compiler reports
.\gradlew.bat :app:assembleRelease -PabiSplits                 # per-ABI APKs for size checks
.\gradlew.bat :app:assembleRelease -PbattleSceneChannel=true   # battle channel A/B build

# Coverage (opt-in; no threshold is enforced yet)
.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport
```

The device-visible tasks require an authorized ADB device. On this machine the SDK is
`%LOCALAPPDATA%\Android\Sdk` and ADB is not on `PATH`; wireless debugging can drop
during long builds — verify with `adb devices` before a device task and reconnect with
`adb connect`/`adb mdns services` if it disappeared.

## Suite layout

| Area | Location | Notes |
|---|---|---|
| Save parsing/conversion | `game-save-core/src/test` | pure JVM; golden/characterization tests for DS record codecs |
| DIM/BEM card parsing | `hardware-dim/src/test` | pure JVM; synthetic R5G6B5/checksum fixtures only |
| NFC protocol | `hardware/src/test` | JVM with a fake `NfcTransport` |
| Companion protocol | `companion-protocol/src/test` | pure JVM JSON protocol |
| PC companion | `pc-save-companion/src/test` | pure JVM |
| App JVM | `app/src/test/java/com/tamerslab` | use cases, Koin graph, architecture boundaries, state/navigation contracts, battle/transfer/save logic |
| Device/instrumentation | `app/src/androidTest/java/com/tamerslab` | Room persistence + migration checks, Compose UI smoke tests, battle/radar/transfer/quest flows |

Architecture boundaries are enforced by
`app/src/test/java/com/tamerslab/architecture/ArchitectureBoundaryTest.kt` (domain APIs
must stay Android/Room-free, library modules must not import the app shell, repository
interfaces must not import Room). Koin graph verification is in
`app/src/test/java/com/tamerslab/di/KoinModuleGraphTest.kt`.

## Fixtures and private data

- Never commit real game saves, ROMs, retail DIM/BEM card dumps, pairing links, tokens,
  `secrets.pb`, keystores, or mapping files with private symbols.
- Use synthetic fixtures. Licensed local card fixtures are read only through the
  `VBHELPER_TEST_DIM` / `VBHELPER_TEST_BEM` environment variables; tests skip when they
  are absent.
- The archived regression log documents the provenance of earlier fixtures; the policy
  itself is restated in [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

## Performance and benchmark tests

Performance is a separate manual gate: budgets, procedures, and recorded
measurements/status are in [docs/performance/BASELINE.md](docs/performance/BASELINE.md).
The `baselineprofile` module contains the device-gated baseline-profile generator and
the macrobenchmark harness; generation is not a unit test and must not run in CI without
a device. The battle channel optimization
(`BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED`) stays disabled until a measured Perfetto
run justifies it.

## CI gates

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs module unit tests,
`:app:assembleDebug`, `:app:assembleRelease`, and `:app:lint` as required checks.
`:app:jacocoTestReport` is advisory and no coverage threshold exists yet. CI has no
Android device; instrumentation and benchmark runs are a documented manual gate.

## Adding tests

- Every behavior change/refactor lands with its own tests; do not rely on the historical
  log as a regression suite.
- Characterization/golden values are behavior contracts: change one only for an
  intentional behavior change and explain why in the PR.
- Keep JVM tests deterministic (inject clocks, dispatchers, seeds).
- Room schema changes need a version bump, a `MigrationTestHelper` test, and a new JSON
  under `app/schemas/com.tamerslab.data.database.AppDatabase/`; never use destructive
  migrations.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for PR expectations and device-gated checks.
