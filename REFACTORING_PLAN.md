# Tamers Lab Refactoring Plan

**Generated:** 2026-10-30
**Revised:** 2026-10-31 - Phase 0 offline artifacts complete; Phase 1 implemented and verified
**Status:** Phases 0–7 implemented; 5.1/5.2/5.5, an enforced coverage threshold, and the full dokka/KDoc gate remain documented follow-ups. The 2026-10-31 verification is green for 1,157 unit tests, debug/release assembly, lint (0 errors), 281/281 device tests, install/cold-launch smoke, on-device baseline-profile regeneration, named-class coverage ≥80% line, and device benchmarks (cold-start 875 ms median; battle frame-CPU P95 13.3 ms). Room query plans, farm steady-state/thermal validation, soak, owner security sign-off, and Play-ready signing/versionCode remain open (see [refactor/TARGETS.md](docs/refactor/TARGETS.md#post-phase-6-verification-2026-10-31)).
**Target:** Alpha 0.7.0

---

## Verification Notes (added during review)

The plan was re-checked against the repository. These are the corrections that change scope:

- **Upstream is app-only and already merged.** `lightheel/vbhelper` is a fork with only an `app/` module. 264 of its 277 `app/` files are already present here; all upstream main Kotlin sources exist locally. The 13 absent files are 2 tests, 1 legacy drawable, and 10 launcher `.webp` variants replaced by the current PNG/adaptive icons. There is no upstream `SpriteFileManager` port to do.
- **`SpriteFileManager.kt` is not a DIM parser.** It is a 325-line battle-sprite copier (`app/.../battle/SpriteFileManager.kt`) and is byte-identical to upstream. It cannot replace `hardware-dim`'s Java sprite classes.
- **Companion validation is already ported.** `ValidatedCardEntity/Dao/Manager` and `CompanionValidatedDatabase` already exist and are byte-identical to upstream.
- **Companion transport is JSON, not protobuf.** The `companion-protocol` module is Kotlin/JVM using Gson (`CompanionProtocol.kt`); protobuf-lite is used separately by the app (`app/src/main/proto`).
- **Room indices are not greenfield.** 37 entity files already declare `indices`. Phase 1.5 is an audit, not an "add indices everywhere" pass.
- **The crash log is not Filament.** `hs_err_pid20620.log` is a Gradle 9.6.1 build-daemon native-memory failure on the dev machine (`-Xmx2048m`, 1.32/2.0 GB heap used, 343 MB free RAM, 2 MB pagefile). `replay_pid20620.log` is a HotSpot compile-replay file, not a profile. There is no on-device OOM evidence.
- **Frame pacing already exists.** `SceneFrameBudget` and `SceneFramePacer` are implemented in `rendering/FrameBudget.kt` and wired into `Digifarm3dViewport`.
- **The root `domain/` directory is stale.** It is tracked but not in `settings.gradle.kts`; `app/.../domain/identity/IndividualIdentity.kt` is the built copy. The root copy is deleted in Phase 1.6.
- **`hardware-dim` is a standalone JVM Java library.** It uses `java-library`, Lombok (46 files), commons-io, and slf4j, and carries its own wrapper/settings files. Merging it into an Android library is conditional on removing or isolating those dependencies.

---

## Success Metrics (Phase 0)

Baselines are unmeasured today; Phase 0 records them before any refactor. Targets are proposals and should be confirmed with the project owner.

| Metric | Baseline | 0.7.0 target | How measured |
|--------|----------|--------------|--------------|
| Cold start (release build) | Measure in Phase 0 | â‰¥15% faster than baseline | Macrobenchmark, 5 runs, same device |
| Release APK size | Measure in Phase 0 | â‰¥15% smaller | `apkanalyzer` / file size before and after |
| Battle frame time | Measure in Phase 0 | p95 â‰¤16.7 ms on reference mid-range device | Perfetto / macrobenchmark |
| Farm rendering | Measure in Phase 0 | 60 fps active, 30 fps idle | `FrameMetrics` / Perfetto |
| Room query plans | Current `EXPLAIN QUERY PLAN` | No unexpected `SCAN` for top 20 queries | Query-plan log |
| Static analysis | Current lint/detekt/ktlint counts | Zero errors (baseline entries only where justified) | CI |
| Test coverage | Measure in Phase 0 | 80%+ on named domain classes | JaCoCo |
| Developer onboarding | Measure in Phase 0 | Build + unit tests from root README in <30 min | Timed dry run |
| Stability | No on-device OOM evidence today | No on-device OOM; stable 30-minute soak | `logcat`, Perfetto, `dumpsys meminfo` |
| Security | No audit yet | No secrets in logs; auth/token storage reviewed; cleartext endpoints documented | Phase 4.6 checklist |

---

## Scope Triage (Phase 0)

The plan has more work than a single Alpha release should block on. Phase 0 finalizes this table after the spikes; the rule is that only **Must-ship** tasks gate 0.7.0.

| Class | Tasks | Release rule |
|-------|-------|--------------|
| **Must-ship** | 1.1–1.8, 4.1, 4.6, 5.0, 5.3, 5.4, 7.1, 7.4, 8.1–8.3 | Gates Alpha 0.7.0 |
| **Enabler** | 2.1–2.5, 3.1–3.3, 4.2–4.5, 6.1–6.4 | Merge incrementally when ready; may slip without blocking release |
| **Follow-up** | 5.1, 5.2, 5.5, 7.2 beyond named classes, 7.3 | After 0.7.0 unless Phase 5 data justifies earlier work |

---

## Decisions Summary

| Area | Decision | Rationale |
|------|----------|-----------|
| Root package | Rename package/namespace to `com.tamerslab`, including `com.github.cfogrady.*` in app and library modules; keep `applicationId` unchanged unless a clean-install release is accepted | Removes `github`/`nacabaro` source nesting without breaking existing installs or Play upgrades |
| Product name | Rename the app label, root project, application class, and Compose theme to Tamers Lab / `TamersLabApplication`; keep the existing `applicationId` for upgrades | Rebrands the fork without forcing a clean install |
| Hardware modules | Replace `vb-nfc-reader` with `hardware`; keep DIM as `hardware-dim` behind the `hardware` API until Android packaging is proven | Both are watch communication; the JVM/Android split is the real constraint |
| Package structure | Feature-based: `battle/`, `farm/`, `transfer/`, `chat/`, `species/`, `world/`, `hardware/` | Scales with features; matches mental model |
| DI | Koin, started in `TamersLabApplication.onCreate()`; hard cutover with a temporary `DefaultAppContainer` adapter | Lightweight, Kotlin-first, no annotation processing; avoids two DI systems drifting indefinitely |
| UseCases | All complex flows: transfer, battle, save-import, chat, farm; pure Kotlin under `app/.../domain/usecase/`; typed outcomes rather than bare `Result<T>` | Consistent architecture; testable business logic; no extra Gradle module for one file |
| UI State | Sealed state for request/response screens; separate persistent state from one-shot events for streaming screens (battle/farm/world) | Unidirectional data flow without forcing Loading/Success/Error onto continuous state |
| Architecture boundaries | `app` depends on libraries, never the reverse; no Android/Room types in domain APIs; enforced by Konsist/ArchUnit or detekt forbidden imports | Prevents UseCases/repositories from becoming thin Room wrappers |
| Perf priority | R8 + Baseline Profiles (Phase 1) â†’ baseline measurement (5.0) â†’ battle 60 Hz state delivery â†’ rendering/asset memory â†’ Room query audit â†’ build-loop memory | Only measured/bounded levers; no evidence for a Filament OOM |
| Performance gates | Numeric budgets + CI macrobenchmark on release builds and a low-end device | Prevents the plan from regressing after Phase 5 |
| Rendering memory | No confirmed runtime OOM; optimize only after profiling; pool assets/textures, not `Entity` IDs | `hs_err` is the Gradle daemon; existing code already destroys Filament assets/providers and uploads pose textures once |
| Online PVP | Move the existing online battle files into `battle/online/` and extract shared models; feature-flag behavior changes | Files already exist; upstream is already present, so this is unification, not porting |
| Sprite parsing | Kotlinize `hardware-dim` sprite classes in `hardware/dim/` as an independent rewrite; keep Java until golden tests pass | No upstream Kotlin replacement exists; upstream `SpriteFileManager` is unrelated |
| Wear OS sync | Skip | `pc-save-companion` covers PC sync only; different use case |
| Lint | Batch cleanup PR; add detekt/ktlint plugins first | Fix all violations in one focused PR; update baseline |
| Test coverage | 80%+ on domain logic (`game-save-core`, `battle`); add JaCoCo and measure baseline first; tests land with each phase | High confidence for save parsing, battle math, transfer |
| Testing safety net | Characterization/golden tests, Room migration tests, and UI screenshot/smoke tests | Refactors must prove behavior preservation, not just compile |
| Security | Review auth/token storage, cleartext endpoints, logging, and exported components while touching online battle | Refactor touches sensitive code; do not ship new exposure |
| Delivery | Trunk-based, task-sized PRs, release after each phase; no phase-long branches | Avoids branch rot and conflicts with active feature work |
| Module READMEs | All product modules (5 after merge; 6 if the `hardware-dim` fallback is retained) | Onboarding; architecture decisions; public API surface |
| Documentation | ADRs for key decisions + a docs index; reduce reliance on the 200 KB `TESTING.md` | Future developers need the "why", not only the history |
| applicationId | Kept `com.github.nacabaro.vbhelper` for upgrade continuity during the refactor; **superseded 2026-11-14 by [ADR 0006](docs/adr/0006-own-application-id.md)**: current value is `com.tamerslab.vbhelper` (separate app, no VBHelper update coupling) | `applicationId` changes break updates and existing installs; namespace rename is internal |
| Estimation | Base estimate + 20–30% contingency + spike budget; re-estimate after Phase 1 | Hardware/device work is the most likely underestimate |

---

## Phase-by-Phase Task Breakdown

### Phase 0: Pre-flight (Week 0, 5 days)
**Goal:** Define success, cut scope, retire unknowns, and create a safety net before changing code

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **0.1 Success metrics & scope triage** | Record baselines for the Success Metrics table; confirm Must-ship / Enabler / Follow-up split | Baselines recorded; triage table finalized; release gate agreed | `REFACTORING_PLAN.md`, `docs/refactor/TARGETS.md` |
| **0.2 Spikes** | Timeboxed spikes (half a day each, 2.5 days total): hardware merge (Lombok/Android packaging), KTX2 support in Filament 1.76.1 gltfio, battle 60 Hz state delivery prototype, Koin module graph + startup cost, R8 release keep rules | Each spike has a go/no-go decision and revised estimate; unresolved unknowns move into the owning phase rather than extending Phase 0 | Spike branches/notes; no production code merged |
| **0.3 Delivery rules** | Trunk-based workflow, task-sized PRs, feature flags for behavior changes, release a build after each phase | Rules documented; CI required checks identified; no phase-long branches | `CONTRIBUTING.md` |
| **0.4 Seed characterization suite** | Add the highest-risk characterization/golden tests first: `game-save-core` codecs, `BattleSimulator`, transfer previews, and current Room schema | Seed tests pass on current `main` and stay green through every phase; remaining characterization gaps are tracked and land with their owning phase; fixtures are synthetic or properly licensed | `game-save-core/src/test`, app unit tests, test fixtures |

**Phase 0 artifacts:** `docs/refactor/TARGETS.md`, `docs/refactor/SPIKES.md`, `CONTRIBUTING.md`, and the seed characterization tests.

---

### Phase 1: Foundation (Week 1–2)
**Goal:** Clean namespace, build hygiene, baseline performance, and build config

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **1.1 Merge hardware modules** | Create `hardware` Android library for NFC; keep DIM as `hardware-dim` JVM library behind the `hardware` API; move both source trees and delete the old nested settings/wrappers; migrate hardcoded dependency versions to the version catalog | `:hardware` builds, app depends on it, and unit tests pass; old module dirs deleted; DIM classes package correctly; no Lombok runtime dependency leaks into the APK; dependency versions come from `libs.versions.toml` | `settings.gradle.kts`, `hardware/build.gradle.kts`, `hardware-dim/build.gradle.kts`, moved sources, `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| **1.2 Package & product rename** | Rename `com.github.nacabaro.vbhelper` to `com.tamerslab`; `com.github.cfogrady.{vitalwear, vb.dim, vbnfc}` to `com.tamerslab.*`; rename app label, root project, `TamersLabApplication`, and `TamersLabTheme`; update namespace, manifests, resources, tests, and reflective/persisted names | Build passes; no old imports or source brand strings remain; label is `Tamers Lab`; `applicationId` remains `com.github.nacabaro.vbhelper`; scheduled `WorkManager` jobs, manifest components, deep links, Gson/type tokens, protobuf descriptors, `BuildConfig`, and ProGuard references still work after an upgrade | All `build.gradle*`, `settings.gradle*`, `AndroidManifest.xml`, package declarations, resources, `proguard-rules.pro`, tests |
| **1.3 Enable R8 + minify** | Enable `isMinifyEnabled` and `isShrinkResources`; add keep rules for Gson/Retrofit/protobuf-lite/Room/Filament/Koin/kotlinx.serialization/reflection; keep `integrityCheck` test build working | Release APK installs and passes smoke flows; size measured before/after (expect roughly 15–35%); no missing-class/reflection crashes; mapping file retained; release smoke test runs on a minified build | `app/build.gradle.kts`, `app/proguard-rules.pro`, `gradle.properties` |
| **1.4 Baseline Profiles** | Add `androidx.baselineprofile` plugin + `androidx.profileinstaller` runtime dependency; create `:baselineprofile` generator module; cover app start, scan, battle, farm, transfer | `./gradlew :app:generateBaselineProfile` succeeds on a connected device/emulator; generated profile committed under `app/src/release/generated/baselineProfiles/`; profile regenerated after R8 enablement and again after Phase 6; startup measured before/after | `gradle/libs.versions.toml`, root `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, new `baselineprofile/`, generated profile |
| **1.5 Room indices audit** | Audit the 37 entity files that already declare `indices`; add only missing indices for the top 20 slow queries/unindexed joins; if indices change, bump `AppDatabase` version and add a migration; remove duplicate indices; add `MigrationTestHelper` tests; measure index write cost | `EXPLAIN QUERY PLAN` shows `SEARCH` where expected; migration from schema v55 tested only if the schema changes; no duplicate indices; schema JSON updated; write-path regression measured | Entity files under `domain/`, `world/`, `quests/`, `battle/offline/tamers/`; `database/AppDatabase.kt`; `daos/*.kt`; `app/schemas/`; migration tests |
| **1.6 Repo hygiene** | Delete tracked `hs_err_pid20620.log` and `replay_pid20620.log`; add ignore patterns; delete empty root `screens/` and stale root `domain/identity/IndividualIdentity.kt`; remove nested Gradle wrappers from vendored modules when merged | No crash/replay logs tracked; `git status` clean; no duplicate `IndividualIdentity`; `.gitignore` covers `hs_err_pid*.log`, `replay_pid*.log`, `*.hprof` | `.gitignore`, root log files, `domain/`, `screens/`, vendored module wrappers |
| **1.7 Build memory fix** | Fix the actual Gradle daemon OOM: ensure Windows pagefile/commit headroom, tune `org.gradle.jvmargs`, cap `org.gradle.workers.max`, optionally enable parallel; document the setup | A clean `assembleDebug` and `lint` complete without native OOM on the target dev machine; no new `hs_err` file; settings documented for new developers | `gradle.properties`, `README.md` (developer setup) |
| **1.8 CI baseline** | Add a CI pipeline for build, unit tests, and lint; cache Gradle; pin the JDK/toolchain; keep required checks small; keep full lint advisory until Phase 7.1 | Every PR runs build + unit tests + lint; build/unit checks are required; lint is advisory while the 52 pre-existing errors remain; pipeline documents the JDK/Gradle version | `.github/workflows/`, `gradle.properties`, `README.md` |

**Phase 1 implementation note (2026-10-31):** 1.1–1.3 and 1.5–1.8 are implemented
and verified. The minified release APK is 147,335,418 bytes (140.51 MiB), a 23.2%
reduction from the unminified baseline. The [Room index audit](docs/refactor/ROOM_INDEX_AUDIT.md)
found no justified schema change. 1.4 generated startup profiles on the connected
SM-A346M. Debug install/launch and a local debug-key release upgrade passed. The
instrumentation suite ran 280 tests with 25 failures before triage; 20 were fixed
(rename/schema, scan fixtures, chat/picker UI, rematch timing, and radar
navigation), leaving five isolated data-path failures
(`ExperimentalWorldDsMoveTest` 3, `IndividualPersistenceTest` 1,
`NativeMaterializationTest` 1). The scan/battle/farm/transfer profile journeys,
production signing/older-version upgrade smoke, and post-Phase-1 re-estimation
remain. Full `:app:lint` remains a Phase 7.1 item.

**Note:** raising `-Xmx` alone will not fix the logged incident; the JVM had heap headroom but the host had almost no free commit charge. Do not attribute this log to Filament or the app.

---

### Phase 2: Architecture Migration (Week 3–5)
**Goal:** Koin DI, Repository interfaces, UseCase extraction, enforceable boundaries

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **2.1 Koin setup** | Add `koin-android`, `koin-androidx-compose`; create `AppModule`, `DatabaseModule`, `NetworkModule`, `HardwareModule`; start Koin in `TamersLabApplication.onCreate()` | App starts with Koin; `DefaultAppContainer` is only a temporary adapter and receives no new dependencies; all constructor dependencies resolve; add a Koin module-graph verification test; lazy modules avoid startup regressions | `di/`, `TamersLabApplication.kt`, `MainActivity.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, Koin test |
| **2.2 Repository interfaces** | Define feature-facing interfaces for transfer, battle, save-import, chat, farm, storage; keep Room implementations internal; do not create a 1:1 wrapper for all 41 AppDatabase DAOs; expose pure domain models where practical | ViewModels/UseCases depend on interfaces, not DAOs; Room imports are confined to implementations; interfaces do not leak Room annotations; mapping is explicit; interfaces are scoped to real features | `*Repository.kt`, `*RepositoryImpl.kt`, domain models |
| **2.3 UseCase extraction** | Create `app/src/main/java/com/tamerslab/domain/usecase/` per feature: `TransferUseCase`, `BattleUseCase`, `SaveImportUseCase`, `ChatUseCase`, `FarmUseCase`; inject repository interfaces; return typed outcomes | Each UseCase has one responsibility, no Android framework imports, injectable clock/dispatcher/ID generator, and JVM unit tests | `app/.../domain/usecase/*.kt` |
| **2.4 Migrate ViewModels** | Inject UseCases; remove direct Repository/DAO calls; map `UiState`; keep line count as a guideline, not a gate | ViewModels contain state orchestration only; no DAO imports; logic that moved out is covered by JVM tests; lifecycle-aware screen collection lands in 3.3 with `lifecycle-runtime-compose` | `*ViewModel.kt`, `screens/**/*ViewModel.kt` |
| **2.5 Enforce architecture boundaries** | Add Konsist/ArchUnit or detekt forbidden-import rules for dependency direction and prohibited Android/Room imports in domain APIs | CI fails when `app` is imported by a library, when domain/UseCases import Android/Room, or when repository interfaces expose Room entities; rules documented | `build.gradle.kts`, `config/detekt`, architecture tests |

**Phase 2 implementation note (2026-10-31):** Koin starts in
`TamersLabApplication`; `AppModule`, `DatabaseModule`, `NetworkModule`,
`HardwareModule`, feature repository adapters, five typed use cases, a Koin
module-graph test, and architecture boundary tests are in place.
`TamerArenaViewModel` uses `BattleUseCase`; the remaining ViewModels/controllers
resolve through the Koin-backed `AppServices` bridge instead of constructing or
holding `AppContainer`. Controller/ViewModel DAO access now goes through typed
gameplay, card, storage, sprite, chat, world-chat, and arena-roster repositories;
the container remains the Koin-owned adapter. The World DS/extraction
data-path tests pass on the target device.

**Phase 3 implementation note (2026-10-31):** `AppRoute` now models the whole
graph with `@Serializable` destinations and a typed `NavHost`; legacy serial
names preserve existing route patterns and the auth deep links are declared on
the Battle destination. `MainActivity` resolves deep links to typed destinations
and forwards warm intents through the navigation graph. `ui/state/` adds
`UiState`, `RequestState`, `StreamingUiState`, `UiEvent`, `UiEffect`, and a
channel-backed `OneShotEventQueue`. Screen and component state now uses
`collectAsStateWithLifecycle`; typed navigation smoke and one-shot/state tests
cover the new contracts.

**Phase 4 implementation note (2026-10-31):** shared battle definitions now live
in `battle/domain`, while snapshots/events/configuration remain in
`battle/offline/core`; online auth/PVP/DTO/network files live in `battle/online`
and are rollback-gated by `OnlineBattleFeature`. All twelve DIM sprite parser
classes are Kotlin, with synthetic parity tests and the Java versions removed.
`CompanionValidatedDatabase` and the duplicate Koin manager binding were removed.
Security hardening adds callback/NFC log redaction, backup exclusions, and a
cleartext allowlist; residual HTTP/Keystore/App-Link risks are tracked in
`docs/refactor/PHASE4_DECISIONS.md`.

**Note:** every phase lands with its own tests; Phase 7 closes gaps rather than introducing the first test suite.

---

### Phase 3: UI State & Navigation (Week 6–7)
**Goal:** Predictable UI state, one-shot events, type-safe routes

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **3.1 UI state model** | Define base `UiState<out T>`, `UiEvent`, and a one-shot event mechanism; use sealed request states for request/response screens and separate persistent state for streaming screens (battle/farm/world) | Each screen has an explicit state model; navigation/actions are one-shot and do not replay on rotation; streaming screens do not force `Loading/Success/Error` onto continuous state | `ui/state/`, `screens/**/*.kt` |
| **3.2 Type-safe navigation** | Add the `kotlinx-serialization` plugin and `kotlinx-serialization-json`; replace string routes with `@Serializable` route classes and a `NavController` extension; build `NavGraph` from a sealed route hierarchy; migrate existing deep links | Deep links work; no string-route typos; compiler catches route argument mismatches; existing external deep links are covered by tests | `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`, `navigation/`, `screens/**/*.kt` |
| **3.3 Screen migration** | Migrate 20+ screens: `BattlesScreen`, `DigifarmScreen`, `WorldScreen`, `StorageScreen`, `TransferScreen`, `ChatScreen`, `SettingsScreen`, `ScanScreen`, `HomeScreen`; add `lifecycle-runtime-compose` for `collectAsStateWithLifecycle`; add screenshot/smoke tests for critical screens | Each screen collects a lifecycle-aware state and dispatches events; critical screens have screenshot or Compose UI smoke tests; state extraction happens before splitting large (2,000+ line) composables | `screens/**/*.kt`, `components/`, `app/build.gradle.kts`, androidTest |

---

### Phase 4: Feature Unification & Hardware Boundary (Week 8–9)
**Goal:** Unify battle domain, port the few genuinely missing upstream files, finish the DIM boundary, and review security-sensitive code

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **4.1 Unify battle domain** | Extract shared `TechniqueDefinition`, `BattleTeam`, `CombatantDefinition`/`Fighter`, `BattleRules` from `battle/offline/core/BattleModel.kt`; move existing online files (`ArenaBattleSystem`, `PVPService`, `AuthInterceptor`, `AuthService`, `RetrofitHelper`, models) into `battle/online/`; keep simulator in `battle/offline/`; feature-flag behavior changes | Single source of truth for battle models; offline/online share models without duplicating DTOs; characterization + offline battle tests still pass; online PVP can be enabled/disabled by flag; no upstream port required | `battle/domain/`, `battle/offline/`, `battle/online/` |
| **4.2 Port the remaining upstream files** | Upstream audit found 13 absent `app/` paths: 2 tests (`ToNfcConverterProfileSelectionTest.kt`, `VitalWearCharacterExporterTest.kt`), 1 legacy drawable, and 10 launcher `.webp` variants already replaced by current PNG/adaptive icons; port the tests if still relevant; do not re-add replaced icons or the unused drawable | Decision recorded per file; ported tests pass; no obsolete resources added | `app/src/main/res/drawable/`, `app/src/test/...` |
| **4.3 Kotlinize DIM sprite parsing** | Rewrite `hardware-dim/src/main/java/com/tamerslab/hardware/dim/sprite/*.java` in Kotlin under `hardware/dim/sprite`; keep the Java implementation as reference until golden-byte tests pass; do not touch the unrelated battle `SpriteFileManager.kt`; use synthetic or licensed fixtures | Identical sprite bytes/checksums for fixture card dumps; Java sprite classes deleted only after parity; no Lombok in new code; fixture licensing documented | `hardware/dim/sprite/`, test fixtures, `TESTING.md` |
| **4.4 Validate companion card integration** | `ValidatedCardEntity/Dao/Manager` and `CompanionValidatedDatabase` already exist byte-identical to upstream; verify the scan/transfer integration, then move to `hardware/validation/` only if the module boundary requires it | Validated card DB works; scan and transfer flows pass; no re-port or duplicate classes | `companion/validation/`, `hardware/validation/` |
| **4.5 Remove legacy DIM sprite Java** | After 4.3 parity, delete the legacy Java sprite classes; keep non-sprite Java until it is Kotlinized; remove nested wrappers/settings | Only Kotlin sprite parsing remains; module slimmed; all tests pass | `hardware/dim/` |
| **4.6 Security & privacy review** | Review auth/token storage (`AuthInterceptor`, `BattleAuthContainer`, `DataStoreSecretsRepository`), cleartext HTTP endpoints, logging/`println`, exported manifest components, and backup rules in the touched code | No secrets in logs; token storage reviewed; cleartext endpoints documented/allowlisted; findings tracked; release checklist includes security sign-off | `battle/online/`, `source/`, `AndroidManifest.xml`, network security config |

---

### Phase 5: Rendering & Performance (Week 10–12)
**Goal:** Measurement-driven rendering and performance work with budgets and gates; verify memory claims on device

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **5.0 Baseline measurement & budgets** | Use the Phase 0 device/benchmark setup; capture cold start, first farm frame, a 30-second battle, and transfer import on a release build and a low-end device; commit budgets from Success Metrics | Baseline numbers and traces committed; budgets are explicit; measurements are repeatable on the reference device; macrobenchmark runs in CI or a documented manual gate | `baselineprofile/`, benchmark module, `TESTING.md` |
| **5.1 Texture/mesh pipeline** | Measure GPU/native memory first; use the Phase 0 KTX2 spike result; if supported, add KTX2/gltfpack; otherwise use downscaled PNG/WebP + meshopt/gltfpack; keep an uncompressed fallback | Measured VRAM/first-load improvement (target â‰¥30%, not an assumed 50%); no visual regression; fallback verified; asset pipeline deterministic | `rendering/`, `digifarm/`, asset pipeline (`build.gradle.kts` for asset processing) |
| **5.2 Filament asset lifecycle** | Audit `Digifarm3dViewport` and `OfflineBattleScene` destroy paths (they already destroy asset/resource/provider loaders); pool/reuse `FilamentAsset`/`MaterialInstance`/`Texture`, not `Entity` IDs; avoid per-frame create/destroy | 30-minute soak shows stable native/GPU memory; no per-frame create/destroy; no behavior change | `rendering/`, `digifarm/`, `screens/offlineBattle/` |
| **5.3 Frame budget extension** | `SceneFrameBudget`/`SceneFramePacer` already exist and are wired into `Digifarm3dViewport`; add quality tiers (low/med/high) and thermal downgrade via `PowerManager.currentThermalStatus` on API 29+ with a fallback for API 28 | Measured 60 fps active / 30 fps idle on a mid-range device; thermal downgrade tested; no battery regression | `rendering/FrameBudget.kt`, `Digifarm3dViewport.kt`, `OfflineBattleScene.kt` |
| **5.4 Battle state delivery** | Prototype from the Phase 0 spike: the 60 Hz `BattleSimulator.snapshot()` â†’ `MutableStateFlow` â†’ Compose path is the largest sustained cost (deep equals + recomposition). Split the scene channel from UI state; drive Filament directly; use `derivedStateOf`; hoist list concatenation out of composition; keep behavior behind a flag until validated | Perfetto shows reduced app-thread time; p95 frame time meets the Phase 5.0 budget; simulator and characterization tests pass; no gameplay behavior change; flag rollback works | `battle/offline/session/`, `screens/offlineBattle/`, `rendering/` |
| **5.5 Asset/catalog load** | Measure first-use cost of the 13.3 MB `species_catalog.json` and the 13,810 PNG battle sprites; precompute the catalog into SQLite/binary (the pattern already used for Dot sprites) and atlas/pack battle sprites if measurements justify it | Measured first-use reduction; APK size reduction; no missing assets; skipped if 5.0 shows the cost is not user-visible | `species/`, `assets/`, build scripts |

---

### Phase 6: Package Restructure (Week 13–14)
**Goal:** Feature-based layout, clean imports, documentation

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **6.1 Create feature packages** | `com.tamerslab.{battle,farm,transfer,chat,species,world,hardware,companion,di,navigation,ui}`; use valid package names (`transfer/multigame/`, `transfer/pc/`, not hyphenated directories) | All source files moved; no top-level `screens/`, `daos/`, `database/`, or `source/`; root `screens/` deleted; no hyphenated package directories | All `*.kt` (move + import fix) |
| **6.2 Update imports & dependencies** | Fix all internal imports; remove unused; migrate remaining hardcoded dependency versions to the version catalog; remove redundant nested build files | Build passes; `./gradlew :app:lint` clean apart from an explicitly updated baseline; no hardcoded versions remain in module build files | All `*.kt`, module build files, `gradle/libs.versions.toml` |
| **6.3 Module READMEs** | Each product module: `README.md` with purpose, public API, dependency graph, build/test commands | New developer can understand a module in under 5 minutes; 5 product modules documented, plus `hardware-dim` if the fallback is retained | `README.md` in `app`, `hardware`, `game-save-core`, `companion-protocol`, `pc-save-companion` (and `hardware-dim` if used) |
| **6.4 ADRs & docs index** | Add `docs/adr/` for key decisions (applicationId, hardware merge, Koin, battle unification, performance budgets); add a root docs index with build/test/lint commands; split or archive historical sections of `TESTING.md` | Every major decision has an ADR; root README links architecture, modules, testing, and ADRs; new developer can build and run unit tests from the root README in <30 minutes | `docs/adr/`, `README.md`, `CONTRIBUTING.md`, `TESTING.md` |

---

### Phase 7: Quality & Testing (Week 15–16)
**Goal:** Lint clean, test coverage, documentation, CI gate

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **7.1 Batch lint cleanup** | Add detekt and ktlint Gradle plugins to the version catalog/root build; add configs; run and fix all violations; update `lint-baseline.xml` | `./gradlew detekt ktlintCheck :app:lint` completes with zero errors (baseline entries only where justified) | `gradle/libs.versions.toml`, root `build.gradle.kts`, `config/detekt/detekt.yml`, `.editorconfig`/ktlint config, `lint-baseline.xml` |
| **7.2 Test coverage & test-suite closure** | Add JaCoCo to `game-save-core` and app unit tests; measure current coverage; exclude Room/KSP generated code; close gaps left by phases: characterization, Room migration, UI screenshot/smoke; target `WorldDsRecordCodec`, `NdsStoryRecordCodec`, `WorldDsPartyEdits`, `BattleSimulator`, `TamerTrainerController`, `PersonalityBattleProfile` | Coverage targets met for named classes; migration and screenshot suites run in CI; no behavior-preservation gaps remain | `*Test.kt` in domain modules, androidTest, Gradle build files |
| **7.3 KDoc public APIs** | All `public`/`internal` classes/functions in domain, usecase, and repository interfaces | `dokka` generates without warnings for the documented modules | KDoc comments across domain modules |
| **7.4 CI quality & performance gate** | Expand CI: build, unit tests, lint, detekt/ktlint, R8 release build, Room migration tests, macrobenchmark on a dedicated device or Firebase Test Lab; cache Gradle; pin JDK/toolchain | Required checks block merge; performance results are recorded and compared against budgets; failures are actionable and not flaky | `.github/workflows/`, Gradle config, benchmark module |

---

### Phase 8: Polish & Release Prep (Week 17–18)
**Goal:** Final validation, performance verification, release

| Task | Scope | Acceptance Criteria | Files Touched |
|------|-------|---------------------|---------------|
| **8.1 Performance validation** | Re-run the Phase 0/5.0 benchmarks on release builds and a low-end device: cold start, scan latency, battle p95 frame time, farm FPS, transfer throughput | All metrics meet or beat the Phase 0 budgets; no on-device OOM or GC-thrash evidence; no unverified Filament-OOM claims | Benchmark module, `TESTING.md` |
| **8.2 Integration test suite** | End-to-end: scan DIM â†’ save â†’ transfer â†’ battle â†’ farm â†’ chat | All flows pass on device (API 28, 31, 34) | `androidTest/` |
| **8.3 Release checklist** | Version bump, changelog, `applicationId` decision verified, ProGuard mapping retained, baseline profile regenerated after Phase 6, Room migration test, rename/`WorkManager` upgrade test, security sign-off, Play Console upload | Alpha 0.7.0 ready | `CHANGELOG.md`, `app/build.gradle.kts`, release docs |

---

## Implementation Status After Phase 6/7 (2026-10-31)

This section records what actually landed after the Phase 6 package restructure,
including deliberate deferrals. Detailed evidence lives in
[docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md),
[docs/performance/PHASE5_DECISIONS.md](docs/performance/PHASE5_DECISIONS.md), and
[docs/performance/BASELINE.md](docs/performance/BASELINE.md).

| Task | Status | Notes |
|------|--------|-------|
| 5.0 Baseline measurement & budgets | Done for the available harness | Budgets + procedures in `docs/performance/BASELINE.md`; cold-start benchmark (875.0 ms median TTID), battle and farm frame-timing proxies recorded on the reference device; Room query plans and soak still unmeasured |
| 5.1 Texture/mesh pipeline | Deferred | KTX2 spike remains no-go without device proof; no measured first-use/VRAM baseline to justify a pipeline change |
| 5.2 Filament asset lifecycle | Audited, deferred soak | Destroy/pool paths already present and documented; 30-minute soak still required |
| 5.3 Frame budget extension | Done | Quality tiers + API 29+ thermal downgrade with API 28 fallback wired into the farm viewport; farm/battle frame proxies measured, thermal behaviour not exercised |
| 5.4 Battle state delivery | Done behind flag | `BattleSceneChannel` split with `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED = false`; unit-tested policy; flag-off battle frame baseline recorded, flag-on A/B still pending before enabling |
| 5.5 Asset/catalog load | Deferred | Catalog/dot-sprite caches already exist; battle-sprite packing/precompute needs measured first-use cost |
| 6.1/6.2 Package restructure | Done | Feature packages under `com.tamerslab.*`; old `screens/daos/database/source` packages removed |
| 6.3 Module READMEs | Done | `app/`, `hardware/`, `hardware-dim/`, `game-save-core/`, `companion-protocol/`, `pc-save-companion/` |
| 6.4 ADRs & docs index | Done | `docs/adr/0001-0005`, `docs/INDEX.md`, root README/CONTRIBUTING, and the archived 200 KB TESTING log split into a current guide + `docs/testing/HISTORICAL_REGRESSION_LOG.md` |
| 7.1 Batch lint cleanup | Done (detekt/ktlint deferred) | 52 `MissingTranslation` errors fixed with JA/PT-BR resources; lint baseline pruned; detekt/ktlint removed after the aggregate run hung |
| 7.2 Coverage & suite closure | Done for the measured target | JaCoCo reports for app and `game-save-core`; all six named classes above the 80% line target (81.0–95.8%); no enforced threshold yet |
| 7.3 KDoc public APIs | Partial | Core domain/use-case/repository contracts documented; full sweep + dokka gate deferred |
| 7.4 CI quality gate | Done for available checks | CI requires build + all unit suites + debug/release assemble + lint; device benchmarks remain a manual gate |
| 8.1 Performance validation | Partial | Cold start, battle and farm frame proxies recorded on the reference device; Room query plans, farm steady active/idle, thermal and soak remain unmeasured |
| 8.2 Integration test suite | Partial | 281/281 device instrumentation tests pass on the connected SM-A346M (API 35); the API 28/31/34 matrix is unavailable |
| 8.3 Release checklist | Partial | Version bumped to Alpha 0.7.0, changelog and release checklist written, profiles regenerated on device, R8 release built with a regenerated mapping, coverage/benchmarks recorded; no production keystore, no Play upload, `versionCode` decision open |

The 2026-10-31 verification record (commands, counts, sizes, and the skipped checks) is
in [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md#post-phase-6-verification-2026-10-31).
It covers the uncommitted working tree: unit tests 1,157 (7 skipped), debug + R8 release
assembly, `:app:lint` 0 errors, 281/281 device tests, install/cold-launch smoke,
baseline-profile regeneration on the device, named-class coverage above the 80% line
target, and device benchmarks (cold-start median 875 ms; battle frame-CPU P95 13.3 ms).
Room query plans, the 30-minute soak, farm steady active/idle, and thermal validation
remain unmeasured.

---

## Module Structure After Refactor

```
TamersLab/
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ app/                          # Application module
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ src/main/java/com/tamerslab/
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ battle/               # Unified battle domain
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ domain/           # TechniqueDefinition, BattleTeam, CombatantDefinition, BattleRules
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ offline/          # BattleSimulator, TamerTrainerController, PersonalityBattleProfile
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ online/           # ArenaBattleSystem, PVPService, AuthInterceptor (existing files moved)
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ ui/               # Battle screens, components
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ farm/
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ digifarm/         # DigifarmScreen, FarmHomeWorld, Digifarm3dViewport
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ rendering/        # SceneFrameBudget/Pacer, HybridSceneKind, EnvironmentThemeController
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ transfer/
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ storage/          # StorageScreen, StorageDialog
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ multigame/        # WorldDsPartyEdits, planners (valid package name)
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ pc/               # PC sync integration
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ chat/
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ ai/               # DigimonPersonaBuilder, ChatRepository
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ ui/               # Chat screens
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ species/              # SpeciesRepository, catalog JSON
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ world/                # WorldSpawnSelector, WorldInteractionOrchestrator
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ companion/            # Logs, firmware, PC companion UI
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ hardware/             # NFC/DIM integration (thin adapters)
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ di/                   # Koin modules
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ navigation/           # Type-safe routes, AppNavigation
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ domain/usecase/       # Pure-Kotlin use cases (no Android deps)
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡       ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ ui/                   # Shared components, theme, widgets, state
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ game-save-core/               # Save parsing, conversion (pure Kotlin)
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ hardware/                     # NFC Android library
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ nfc/                      # VBNfcReader, protocol parsing
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬Å¡   ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ validation/               # ValidatedCardEntity/Dao/Manager (already present)
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ hardware-dim/                 # DIM JVM library; Kotlin sprite rewrite target
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ companion-protocol/           # Companion pairing/listing JSON protocol (Gson)
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ pc-save-companion/            # Local-network PC sync
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ baselineprofile/              # Baseline-profile / macrobenchmark generator (not shipped)
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ docs/                         # ADRs, targets, architecture notes
```

**Removed:** root `domain/` (stale duplicate), root `screens/` (empty), `vb-nfc-reader/`, and `vb-dim-reader/` after the hardware merge.

### Dependency Rules

- `app` may depend on `game-save-core`, `hardware` (or `hardware-dim`), `companion-protocol`, and `pc-save-companion`; libraries never depend on `app`.
- `game-save-core` is pure Kotlin/JVM: no Android, Room, or app imports.
- `hardware` owns NFC/DIM protocols; app-level `hardware/` contains thin adapters only.
- Repository interfaces expose pure domain models where practical; Room entities stay inside implementations.
- Use cases and domain models have no Android framework imports.
- Enforce with Konsist/ArchUnit or detekt forbidden-import rules (task 2.5); CI fails on violations.

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Package rename breaks git history | Use `git mv`; tag the pre-rename commit; rely on rename detection; do not rewrite history unless necessary |
| Package rename breaks persisted/reflective names | Run the rename checklist (WorkManager workers, manifest components, deep links, Gson/type tokens, protobuf, `BuildConfig`, ProGuard); test an upgrade install |
| `applicationId` change breaks upgrades | Keep `com.github.nacabaro.vbhelper` by default; treat any change as a clean-install boundary with explicit approval. **Boundary crossed 2026-11-14:** owner approved a separate identity, `com.tamerslab.vbhelper` ([ADR 0006](docs/adr/0006-own-application-id.md)) |
| Hardware merge blocked by Lombok/commons-io/slf4j | Spike first; if Android packaging fails, keep `hardware-dim` as a JVM module behind a `hardware` API facade; do not force one module |
| Koin migration breaks runtime DI | Hard cutover: no new dependencies in `DefaultAppContainer`; module-graph test; migrate module-by-module |
| Two DI systems drift | Remove or freeze `DefaultAppContainer` by the end of Phase 2; track any remaining adapter as a follow-up |
| DIM Kotlin rewrite changes card parsing | Keep the Java implementation until golden-byte/checksum tests pass against real card dumps; no "port from upstream" shortcut exists |
| DIM fixtures have copyright constraints | Use synthetic or user-provided fixtures that are not committed; document fixture provenance |
| R8 breaks reflection | Keep Gson/Retrofit/protobuf/Room/Filament/Koin/serialization rules; test the release build on a device; retain the mapping file |
| Room index changes require migrations | Bump schema version; add and test migrations with `MigrationTestHelper`; measure write cost; never use destructive fallback |
| Baseline profile needs a device | Require a connected device/emulator or Firebase Test Lab; profile generation is not a CI-only unit test |
| Build daemon OOM misread as app/Filament OOM | Fix build memory separately (Phase 1.7); require on-device Perfetto/logcat evidence before claiming a runtime memory problem |
| Battle unification breaks online PVP | Feature-flag online PVP; run both paths in parallel for one release |
| Battle state delivery changes gameplay | Prototype in Phase 0; validate with Perfetto and characterization tests; keep the old path behind a flag until approved |
| Texture compression artifacts | A/B test original vs compressed; keep an uncompressed fallback; verify gltfio KTX2 support before committing |
| Long-lived branches conflict with feature work | Trunk-based delivery; task-sized PRs; feature flags; release after each phase |
| Architecture drift (UseCases become wrappers) | Enforce dependency/import rules (task 2.5); pure models; review repository interfaces |
| CI cannot run device benchmarks | Use Firebase Test Lab or a dedicated device; until then, document a manual benchmark gate and record results |
| Estimate optimism | Add 20–30% contingency; timebox spikes; re-estimate after Phase 1 |

---

## Estimated Effort

| Phase | Weeks | Engineer-Days |
|-------|-------|---------------|
| 0. Pre-flight | 1 | 5 |
| 1. Foundation | 2 | 13 |
| 2. Architecture | 3 | 20 |
| 3. UI State/Nav | 2 | 12 |
| 4. Unification/Hardware | 2 | 18 |
| 5. Rendering/Performance | 3 | 20 |
| 6. Package Restructure | 2 | 12 |
| 7. Quality/Tests | 2 | 14 |
| 8. Polish/Release | 2 | 8 |
| **Base total** | **19** | **122** |
| **With 20–30% contingency** | **~23–25** | **~146–159** |

The week ranges above are a sequential calendar at roughly 1.3 FTE on average (Phase 4 peaks at ~1.8 FTE): Phase 0 W0, Phase 1 W1–2, Phase 2 W3–5, Phase 3 W6–7, Phase 4 W8–9, Phase 5 W10–12, Phase 6 W13–14, Phase 7 W15–16, Phase 8 W17–18. At 1.0 FTE, the 122 engineer-days imply ~24–25 weeks base and ~29–32 weeks with contingency. Re-estimate after Phase 1, when the hardware merge and rename spikes are done.

---

## Branch Strategy

```
main                         # always releasable; tagged after each phase
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ task/<id>-short-name      # hoursÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“days; one task/PR
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ mechanical/rename         # short-lived; rename-only commit
ÃƒÂ¢Ã¢â‚¬ÂÃ¢â‚¬ÂÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ mechanical/hardware-merge # short-lived; no feature work
```

No phase-long branches. Merge task-sized PRs to `main` behind feature flags, release or tag after each phase, and keep `main` green. Large mechanical changes (rename, hardware merge) get their own short-lived PR with no behavior changes.

---

## Deliverables to Produce Next

1. **Phase 0 record** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â baselines, scope triage, spike decisions, and the confirmed success metrics
2. **`CONTRIBUTING.md`** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â trunk-based workflow, build/test/lint commands, PR expectations
3. **Phase 1 task PRs** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â short-lived `mechanical/hardware-merge`, `mechanical/rename`, and `task/1.3`ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“`task/1.8` PRs for R8, baseline profiles, Room audit, hygiene, build memory, and CI baseline
4. **`detekt`/`ktlint` configs** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â config for batch lint cleanup
5. **`baselineprofile` module + profileinstaller wiring** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â critical user journeys for startup optimization
6. **Koin modules** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â `di/AppModule.kt`, `DatabaseModule.kt`, `NetworkModule.kt`, `HardwareModule.kt`
7. **ADRs + docs index** ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the "why" behind package, module, DI, battle, and performance decisions

**Start here:** Phases 0–7 are implemented, with the Phase 5.1/5.2/5.5, coverage, and
KDoc items explicitly deferred as recorded in
[docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md) and
[docs/performance/PHASE5_DECISIONS.md](docs/performance/PHASE5_DECISIONS.md). The
remaining release work is the Alpha 0.7.0 device verification and performance gate in
[refactor/TARGETS.md](docs/refactor/TARGETS.md) and
[release/RELEASE_CHECKLIST_0.7.0.md](docs/release/RELEASE_CHECKLIST_0.7.0.md).

---

## Original Prompt Context

This plan was generated from the following user request:

> "before continuing with the rust thing i want you to plan a refactoring of the app, focused on making it more optmized, and having a better human readabilty for future devs, also take a look at this: https://github.com/lightheel/vbhelper project that this current project you are working on is a fork of, see what you think was better on the original project and could be ported without changing the current project, only making it more eficient(i also dont like how the folders are structured an have "github" and "nacabaro"s name). Plan making me as much questions as possible to keep everything on track and know what i prefer. Remember this is just a plan"

**Key constraints from prompt:**
- Focus: optimization + human readability for future developers
- Compare with upstream fork (lightheel/vbhelper) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â port improvements without changing current project scope
- Fix folder structure: remove `github`/`nacabaro` nesting
- Ask clarifying questions to align with preferences
- Output: a plan (not implementation)



