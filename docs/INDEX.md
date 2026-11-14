# Documentation index

Tamers Lab, target release **Alpha 0.7.0**. This page is the entry point for the
repository's architecture, refactor, performance, testing, and release records.

> **Verification status (2026-10-31):** Phases 0–7 are implemented, with documented
> deferrals for Phase 5.1/5.2/5.5, a coverage threshold, dokka, and screenshot testing.
> The post-Phase-6 verification passed: 1,157 unit tests (7 skipped, 0 failures), debug +
> R8 release assembly, `:app:lint` with 0 errors, 281/281 connected instrumentation tests
> on the SM-A346M (API 35), and debug/debug-signed-release install+launch smoke. Baseline
> profiles were regenerated on the device; named-class coverage is above 80% line; device
> benchmarks recorded a 875 ms cold-start TTID median and battle frame-CPU P95 13.3 ms.
> Room query plans, the 30-minute soak, and thermal validation remain unmeasured.
> Details: [docs/refactor/TARGETS.md](refactor/TARGETS.md) and
> [performance/BASELINE.md](performance/BASELINE.md).

## Quick start

Prerequisites: JDK 25, Android SDK 37 (`platforms;android-37.0`,
`build-tools;36.0.0`), and the Gradle wrapper. Run from the repository root:

```powershell
# Build the debug app
.\gradlew.bat :app:assembleDebug

# All unit-test modules
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test :hardware-dim:test :hardware:testDebugUnitTest :app:testIntegrityCheckUnitTest

# Android lint
.\gradlew.bat :app:lint

# Device-only checks
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest
.\gradlew.bat :app:generateBaselineProfile
```

On Windows, keep at least 8 GB of pagefile/commit headroom and use the bounded settings
in [gradle.properties](../gradle.properties) (`org.gradle.workers.max=1`,
`org.gradle.parallel=false`, explicit daemon metaspace limits). See the
[README](../README.md) for setup details and the [CONTRIBUTING](../CONTRIBUTING.md) guide
for PR expectations.

## Architecture and modules

| Module | README | Role |
|--------|--------|------|
| `:app` | [app/README.md](../app/README.md) | Android application shell, Compose UI, Room, Koin, navigation |
| `:hardware` | [hardware/README.md](../hardware/README.md) | NFC Android library; watch transport and protocol parsing |
| `:hardware-dim` | [hardware-dim/README.md](../hardware-dim/README.md) | Pure Kotlin/JVM DiM/BEM card parser and writer |
| `:game-save-core` | [game-save-core/README.md](../game-save-core/README.md) | Save reading/planning/conversion; desktop `SaveInspector` |
| `:companion-protocol` | [companion-protocol/README.md](../companion-protocol/README.md) | Pairing and pinned-TLS listing protocol (JSON/Gson) |
| `:pc-save-companion` | [pc-save-companion/README.md](../pc-save-companion/README.md) | Read-only local-network desktop companion |
| `:baselineprofile` | — | Device-gated Baseline Profile generator (not shipped) |
| `:benchmark` | — | Device-gated macrobenchmark harness: startup trace metrics and farm/battle frame timing (not shipped) |

Dependency direction: `app` depends on the three runtime libraries (`hardware`,
`game-save-core`, `companion-protocol`), never the reverse; `game-save-core`,
`hardware-dim`, and `companion-protocol` have no Android dependency. The rules are
restated in [REFACTORING_PLAN.md](../REFACTORING_PLAN.md) and enforced by
`ArchitectureBoundaryTest` in the app unit tests.

## Architecture decisions (ADRs)

- [ADR 0001: Keep `applicationId` `com.github.nacabaro.vbhelper`](adr/0001-keep-application-id.md) — *superseded by ADR 0006*
- [ADR 0002: Keep `hardware` and `hardware-dim` separate](adr/0002-hardware-module-merge.md)
- [ADR 0003: Hard cutover to Koin](adr/0003-koin-dependency-injection.md)
- [ADR 0004: Unified battle domain with offline/online split](adr/0004-unified-battle-domain.md)
- [ADR 0005: Performance budgets and release gating](adr/0005-performance-budgets.md)
- [ADR 0006: Own `applicationId` `com.tamerslab.vbhelper`](adr/0006-own-application-id.md)

## Refactor records

| Document | Contents |
|----------|----------|
| [REFACTORING_PLAN.md](../REFACTORING_PLAN.md) | Full phase plan, module structure, dependency rules, decisions summary |
| [refactor/TARGETS.md](refactor/TARGETS.md) | Phase 0 baseline, size context, historical test/device records |
| [refactor/SPIKES.md](refactor/SPIKES.md) | Hardware merge, KTX2, battle delivery, Koin, R8 spike decisions |
| [refactor/PHASE4_DECISIONS.md](refactor/PHASE4_DECISIONS.md) | Upstream port decisions, sprite fixtures, security findings and residual risks |
| [refactor/PHASE7_DECISIONS.md](refactor/PHASE7_DECISIONS.md) | Lint cleanup, detekt/ktlint deferral, JaCoCo/coverage status, CI gates |
| [performance/PHASE5_DECISIONS.md](performance/PHASE5_DECISIONS.md) | Phase 5 implementation/audit/deferral record (budgets, Filament lifecycle, battle channel, asset load) |
| [refactor/ROOM_INDEX_AUDIT.md](refactor/ROOM_INDEX_AUDIT.md) | Static Room index audit; no schema change justified |

Phase 7 quality-tooling decisions are recorded in
[refactor/PHASE7_DECISIONS.md](refactor/PHASE7_DECISIONS.md): detekt and ktlint were
removed from the build after the aggregate run could not complete reliably.

## Performance

- [performance/BASELINE.md](performance/BASELINE.md) — budgets, repeatable measurement
  procedures, recording template, and the honest current status.
- [performance/OPTIMIZATION_LOG.md](performance/OPTIMIZATION_LOG.md) — post-0.7.0
  optimization pass: implemented changes, measurement commands, and the deferred list.
- [ADR 0005](adr/0005-performance-budgets.md) — why budgets gate releases and what counts
  as evidence.

Do not cite a number that is not recorded in BASELINE.md with commit, build type,
device, command, date, and artifact.

## Testing

- [TESTING.md](../TESTING.md) — current testing guide: commands, suite layout, fixtures,
  CI gates, and how to add tests.
- [testing/HISTORICAL_REGRESSION_LOG.md](testing/HISTORICAL_REGRESSION_LOG.md) — the
  archived 200 KB chronological regression log. It is useful history, not the current
  test result; do not quote its counts as current.
- [CONTRIBUTING.md](../CONTRIBUTING.md) — commands, test expectations, feature-flag and
  fixture rules, review expectations.
- [game-save-core/SAVE_READING_STANDARD.md](../game-save-core/SAVE_READING_STANDARD.md) —
  binding save-reader rules.
- Room schemas live in `app/schemas/com.tamerslab.data.database.AppDatabase/`; any schema
  change needs a version bump, a migration test, and a new schema JSON.

## Release

- [CHANGELOG.md](../CHANGELOG.md) — Keep-a-Changelog entry for Alpha 0.7.0.
- [release/RELEASE_CHECKLIST_0.7.0.md](release/RELEASE_CHECKLIST_0.7.0.md) — version,
  signing, R8 mapping, baseline profile, Room, upgrade smoke, security, and Play status.
- [security/SECURITY_REVIEW_0.7.0.md](security/SECURITY_REVIEW_0.7.0.md) — verified
  security controls, residual risks, and the pending owner sign-off.
- [ADR 0001](adr/0001-keep-application-id.md) — the `applicationId` freeze and the
  clean-install boundary for revisiting it.

## CI

[.github/workflows/ci.yml](../.github/workflows/ci.yml) runs on JDK 25 and the Android
SDK 37 image as required checks: all module unit-test suites, `:app:assembleDebug`,
`:app:assembleRelease` (exercises the R8 keep rules), and `:app:lint` (blocking; the 52
`MissingTranslation` errors are fixed). `:app:jacocoTestReport` is advisory. detekt and
ktlint are deliberately not invoked (deferred, see
[PHASE7_DECISIONS.md](refactor/PHASE7_DECISIONS.md)). CI has no Android device and no
benchmark step; device/instrumentation/benchmark checks are a documented manual gate.

## Product, transfer, and format research

- [MULTI_GAME_TRANSFER_PLAN.md](../MULTI_GAME_TRANSFER_PLAN.md) — multi-game transfer
  plan and current transfer contract.
- [DAWN_DUSK_TRANSFER_PLAN.md](../DAWN_DUSK_TRANSFER_PLAN.md) — Dawn/Dusk evidence plan.
- [EXPANDED_MODE_IMPLEMENTATION.md](../EXPANDED_MODE_IMPLEMENTATION.md) — historical
  expanded-mode implementation notes.
- [game-save-core/WORLD_DS_RESEARCH.md](../game-save-core/WORLD_DS_RESEARCH.md) and
  [FORMAT_RESEARCH.md](../game-save-core/FORMAT_RESEARCH.md) — save-format evidence.
- [game-save-core/WORLD_DS_STAT_CONVERSION.md](../game-save-core/WORLD_DS_STAT_CONVERSION.md)
  — stat conversion notes.

## Keeping these docs current

When a change affects a module boundary, a build command, a flag, or a release gate,
update the owning README/ADR in the same PR. Record measurement evidence only in
BASELINE.md. Do not rewrite history in the archived regression log; append new entries to
[TESTING.md](../TESTING.md) or link the relevant archive entry instead.
