# Contributing

Tamers Lab uses a trunk-based workflow. `main` stays releasable; work lands in small,
task-sized PRs and releases are tagged after each phase. Read the
[documentation index](docs/INDEX.md) first if you are new to the repository.

## Branches and PRs

- Branch from `main`: `task/<id>-short-name` for feature/refactor work.
- Use `mechanical/<name>` for rename-only or module-move work.
- Keep one task or one mechanical change per PR. Do not mix a package rename with
  behavior changes.
- No phase-long branches. Rebase or merge `main` promptly; do not let a task branch live
  for weeks.
- Prefer PRs even for solo work; direct pushes are reserved for release metadata and
  emergencies.
- State the verification you actually ran in the PR description. If a check was not run,
  say which one and why. Do not claim a green build from stale output.

## Before opening a PR

Run the checks relevant to your change from the repository root (Windows PowerShell):

```powershell
# JVM/library modules
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test :hardware-dim:test :hardware:testDebugUnitTest

# Android app unit tests (integrityCheck test build type)
.\gradlew.bat :app:testIntegrityCheckUnitTest

# Android lint
.\gradlew.bat :app:lint

# Build the debug APK when the change affects Android
.\gradlew.bat :app:assembleDebug
```

Device-gated checks are not expected on every PR, but run them when you touch NFC,
navigation/deep links, Room migrations, baseline profiles, or release behavior:

```powershell
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest
.\gradlew.bat :app:generateBaselineProfile
```

Coverage reports are opt-in: `.\gradlew.bat :app:jacocoTestReport` and
`.\gradlew.bat :game-save-core:jacocoTestReport`. A coverage number is not a substitute
for a behavior test.

Environment: JDK 25 and Android SDK 37. On Windows, keep pagefile/commit headroom and
use the bounded settings in [gradle.properties](gradle.properties); see the
[README](README.md#how-to-set-up) setup section.

## Feature-flag rule

A behavior change that can be independently disabled must land behind a build-time or
runtime flag, with the old behavior as the default, unless the owner explicitly accepts
the risk. Document in the PR:

- the flag name and where it is read (for example
  `BuildConfig.ONLINE_PVP_ENABLED` via `battle/online/OnlineBattleFeature`,
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` via `battle/offline/session/BattleSceneChannel`);
- its default value and why that default is safe;
- how to roll it back (normally one field + rebuild);
- the test or device evidence that justifies enabling it;
- when the flag will be removed.

Pure refactors, formatting, and documentation do not need a flag. A flag is not a
substitute for a test; cover both paths where practical.

## Test expectations

- Every phase lands with its own tests; quality work closes gaps, it does not introduce
  the first suite.
- Characterization/golden tests are the behavior-preservation contract for the refactor.
  Update a golden value only for an intentional behavior change, and explain why in the
  PR.
- Keep JVM tests deterministic: inject clocks, dispatchers, and random seeds.
- Room schema changes need an `AppDatabase` version bump, a migration test, and an
  updated JSON under `app/schemas/com.tamerslab.data.database.AppDatabase/`.
- Do not use destructive migrations.
- The historical suite counts in
  [docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md)
  and [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md) are not current results. Do
  not quote them as evidence for your branch; the current guide is
  [TESTING.md](TESTING.md).

## Code and architecture

- Kotlin style: official, as configured in `gradle.properties`.
- Packages are feature-based (`battle/`, `farm/`, `transfer/`, ...) with shared
  `data/`, `domain/`, `ui/`, `di/`, and `navigation/`. Use valid package names
  (`transfer/multigame/`, not `transfer/multigame-transfer/`).
- `app` may depend on library modules; libraries must not depend on `app`.
- Domain/use-case APIs must not import Android or Room types. Repository interfaces
  expose domain models, not Room entities.
- Dependencies are resolved through Koin modules in `app/.../di/AppModule.kt`; add a
  binding and constructor injection rather than a new field on `DefaultAppContainer`.
- Keep `applicationId = com.tamerslab.vbhelper` unless a new identity change is explicitly
  approved; it is what makes this a separate app instead of a VBHelper update
  ([ADR 0006](docs/adr/0006-own-application-id.md)).
- Add dependencies only through `gradle/libs.versions.toml`, and consider APK size and
  startup impact.

## Static analysis and quality tooling

`:app:lint` is the static-analysis command and a blocking CI step; it currently reports
**0 errors** (the historical 52 `MissingTranslation` errors are fixed). `app/lint-baseline.xml`
still has 25 unmatched pre-existing warning entries; do not add new lint errors and do not
regenerate the baseline to hide warnings. Record lint changes in the PR.

detekt and ktlint were scaffolded during Phase 7.1 but the aggregate quality run could
not be completed reliably under the constrained build memory settings. They are
**deferred to a follow-up quality pass**, and the plugins are not part of the current
build; do not re-enable them ad hoc. The Lead-owned plan and rationale are in
[PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md). Coverage is measured with
JaCoCo (`:app:jacocoTestReport`, `:game-save-core:jacocoTestReport`) but no threshold is
enforced yet; the current numbers are in
[docs/performance/BASELINE.md](docs/performance/BASELINE.md).

## Secrets, fixtures, and private data

Never commit:

- `secrets.pb`, imported NFC/LLM secrets, battle auth tokens, DataStore contents,
  keystores, signing passwords, or mapping files containing private symbols. The one
  sanctioned exception is the reviewed, test-pinned derivation in
  `app/src/main/java/com/tamerslab/security/BundledSecrets.kt`, which ships the watch
  connection keys so a fresh install does not have to import them; do not add further
  raw key material, `classes.dex`, or Arena APK contents to the tree;
- companion pairing links/tokens, controlled-capture manifests, or captured save files;
- retail DiM/BEM card dumps, real game saves/ROMs, or native record dumps;
- Gradle daemon logs, `hs_err_pid*.log`, `replay_pid*.log`, `*.hprof`, APKs, or local
  SDK paths.

Use synthetic fixtures. If you have licensed local card fixtures, use the
`VBHELPER_TEST_DIM` / `VBHELPER_TEST_BEM` environment variables; tests skip when they
are absent. Keep measurement artifacts under `build-logs/` (ignored) and record only the
provenance in [docs/performance/BASELINE.md](docs/performance/BASELINE.md).

## Commits and releases

- Write short, imperative commit messages that state the change.
- Do not commit generated APKs or IDE/build output.
- Tag/release after each completed phase, not after every task.
- Release notes should state migrations, behavior flags, known gaps, and the verification
  that was actually performed.
- Update the owning module README/ADR and [CHANGELOG.md](CHANGELOG.md) when a boundary,
  command, flag, or release gate changes.

## Review expectations

- A reviewer should be able to see the task boundary, the tests, and the rollback path
  from the PR description alone.
- UI changes need a screenshot or a short screen recording; navigation/deep-link changes
  need a test.
- Security-sensitive changes (auth, token/secret storage, logging, exported components,
  cleartext endpoints, backup rules) need an explicit note and a second reviewer.
- Performance claims need a dated entry in
  [docs/performance/BASELINE.md](docs/performance/BASELINE.md); code presence is not
  evidence.
- Request owner review for architecture changes, new modules, dependency injections,
  and anything that changes the `applicationId` or release process.

## References

- [Refactoring plan](REFACTORING_PLAN.md)
- [Targets and baseline](docs/refactor/TARGETS.md)
- [Spike decisions](docs/refactor/SPIKES.md)
- [Phase 4 decisions and residual risks](docs/refactor/PHASE4_DECISIONS.md)
- [Documentation index](docs/INDEX.md)
