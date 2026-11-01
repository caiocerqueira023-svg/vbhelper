# Changelog

All notable changes to Tamers Lab are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are project
milestones rather than strict SemVer.

## [Unreleased] — Alpha 0.7.0

Target: Alpha 0.7.0. This entry summarizes the refactor work that is implemented in the
tree; it is not a release announcement and does not claim a verified green build.

### Added

- **Koin dependency injection.** `appModule`, `databaseModule`, `networkModule`,
  `hardwareModule`, `repositoryModule`, `useCaseModule`, and `viewModelModule` start from
  `TamersLabApplication`; typed use cases (`TransferUseCase`, `BattleUseCase`,
  `SaveImportUseCase`, `ChatUseCase`, `FarmUseCase`) and repository adapters resolve from
  the graph. Covered by a Koin module-graph test and architecture-boundary tests.
- **Type-safe navigation and UI state.** `@Serializable` `AppRoute` destinations, a
  typed `NavHost`, deep-link resolution in `MainActivity`, and shared state contracts
  (`UiState`, `RequestState`, `StreamingUiState`, `UiEvent`, `UiEffect`,
  `OneShotEventQueue`).
- **Unified battle domain.** Shared `TechniqueDefinition`, `CombatantDefinition`,
  `BattleTeam`, `BattleStatusEffect`, and `BattleRules` in `battle/domain`; the offline
  simulator/session layer stays separate and the legacy online PVP/auth client lives in
  `battle/online` behind a flag.
- **Hardware module split.** `:hardware` is the Android NFC library; `:hardware-dim` is
  the pure Kotlin/JVM DIM/BEM card parser exposed through the `hardware` API. The DIM
  sprite parser is now Kotlin.
- **R8 and resource shrinking** for the release build, with explicit keep rules for
  Gson, Retrofit, protobuf-lite, Room, WorkManager, Koin, serialization, and Filament.
- **Baseline profiles.** `:baselineprofile` generator module, `profileinstaller`
  runtime dependency, and committed startup profile artifacts.
- **JaCoCo setup** for `:app` and `:game-save-core` (standalone report tasks; coverage
  not yet measured or threshold-enforced).
- **Performance scaffolding.** `SceneFrameTier` (Paused/Idle/Active/Reduced) and
  `SceneQuality` frame pacing, thermal downgrade via `PowerManager.currentThermalStatus`
  on API 29+, and the `BattleSceneChannel` scene/UI state split behind
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` (default off).
- **Documentation set.** Module READMEs, ADRs 0001–0005, a root documentation index,
  Phase 5/7 decision records, and the Alpha 0.7.0 release checklist.
- **Translations.** Japanese and Brazilian Portuguese string resources for the previously
  missing keys (including a locale farm-home file), removing the 52 lint
  `MissingTranslation` errors.

### Changed

- **Package and product rebrand.** Kotlin packages and namespace are `com.tamerslab`; the
  launcher label is **Tamers Lab**, the root Gradle project is `TamersLab`, and the
  application class/theme are `TamersLabApplication` / `TamersLabTheme`. The
  `applicationId` remains `com.github.nacabaro.vbhelper` for upgrade continuity (see
  [ADR 0001](docs/adr/0001-keep-application-id.md)).
- **Feature-based package layout.** `battle`, `farm`, `transfer`, `chat`, `species`,
  `world`, `storage`, `home`, `items`, `settings`, `adventure`, and `quests`, plus shared
  `data`, `domain`, `ui`, `rendering`, `companion`, `di`, `navigation`, `security`,
  `vitalwear`, `utils`, `widget`, `components`, and `audio`. The old top-level
  `screens/`, `daos/`, `database/`, and `source/` packages are gone.
- **Room schema location.** Exported schemas now live under
  `app/schemas/com.tamerslab.data.database.AppDatabase/`; schema version remains 55 with
  preserving migrations.
- **DI cutover.** The manual `DefaultAppContainer` is now a Koin-managed adapter rather
  than the composition root ([ADR 0003](docs/adr/0003-koin-dependency-injection.md)).
- **Battle delivery.** The offline battle screen can publish the full-rate snapshot
  directly to the scene while throttling Compose UI state; old behavior remains the
  default until device validation.
- **Baseline-profile work.** Existing generated profiles were produced before the Phase 6
  package move. They were deterministically re-mapped to the current class names with
  `scripts/remap-baseline-profiles.py` (framework entries untouched, unresolved synthetic
  entries dropped); on-device regeneration with the extended journeys is still pending,
  so the files are not a post-restructure measurement.

### Fixed

- NFC/auth logging no longer prints callback URIs, decrypted card bytes, tag ids,
  passwords, or full transfer tokens.
- Backup/device-transfer rules exclude `secrets.pb`, battle auth tokens, LLM settings,
  and logs; cleartext traffic is denied by default with a narrow allowlist.
- Removed the unused `CompanionValidatedDatabase` and a duplicate Koin binding for
  `ValidatedCardManager`.
- Lint baseline cleanup and missing-translation work landed; the zero-error state is not
  yet re-verified after the Phase 6 restructure.

### Security

- Online PVP is gated by `BuildConfig.ONLINE_PVP_ENABLED` /
  `OnlineBattleFeature`; the residual HTTPS, Keystore, and App-Link risks are tracked in
  [PHASE4_DECISIONS.md](docs/refactor/PHASE4_DECISIONS.md).
- No secrets, retail card dumps, real saves, pairing links, or controlled captures are
  committed. See [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

### Quality and tooling

- detekt and ktlint were scaffolded for Phase 7.1 but the aggregate run could not be
  completed reliably under the constrained build memory settings. They were **removed
  from the build and deferred to a follow-up quality pass** (see
  [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md)); the build no
  longer applies those plugins.
- `:app:lint` remains the static-analysis command and is a blocking CI step. The 52
  `MissingTranslation` errors were fixed with real JA/PT-BR resources and
  `app/lint-baseline.xml` was pruned to 62 justified pre-existing warnings; the clean-run
  result is recorded with the final verification.

### Release preparation

- No production signing keystore exists; release smoke builds are unsigned or signed
  with the local debug key only.
- The R8 mapping file must be regenerated and retained for each release candidate.
- Baseline profiles must be regenerated or explicitly re-mapped after the package move.
- Play Console upload has **not** been done.

### Verification

Post-Phase-6 verification ran on the uncommitted working tree on 2026-10-31
(all Gradle commands with `--no-daemon`; the final numbers include the Phase 1/5/7
gap-closing work — an earlier pass in the same session with 1,155 tests and a
149,002,850-byte APK is superseded):

- **Unit tests:** 1,157 tests, 0 failures, 0 errors, 7 skipped across
  `:app:testIntegrityCheckUnitTest` (900), `:game-save-core:test` (192),
  `:companion-protocol:test` (4), `:pc-save-companion:test` (11),
  `:hardware-dim:test` (28, 4 intentional skips), `:hardware:testDebugUnitTest` (22).
- **Build:** `:app:assembleDebug` and `:app:assembleRelease` (R8 minify + resource
  shrinking) succeeded with the regenerated profiles; the unsigned release APK is
  149,019,234 bytes (142.12 MiB) and the R8 mapping was regenerated.
- **Lint:** `:app:lint` reports 0 errors, 422 warnings, 38 hints (warnings/hints only;
  25 stale baseline entries remain a hygiene follow-up).
- **Device:** 281/281 connected instrumentation tests passed on the SM-A346M (API 35);
  debug and debug-key-signed release builds installed with `adb install -r` and cold
  launched without crash entries (single `am start -W` smoke launches, not benchmarks).
- **Baseline profiles:** regenerated on the device with startup + best-effort
  scan/battle/farm/transfer journeys (2,723,442 bytes each; 24,408 rules).
- **Coverage:** JaCoCo reports for app and game-save-core; all six named classes are
  above the 80% line target (81.0–95.8%). No threshold is enforced yet.
- **Benchmarks (device-gated):** cold-start TTID median 875.0 ms over 5 iterations;
  battle frame CPU duration P95 13.3 ms; the farm 3-second window recorded P95 29.8 ms
  and includes 3D asset loading (steady active/idle and thermal behavior unverified).
- **Not verified:** Room query plans, 30-minute soak, onboarding timing, multi-API
  (28/31/34) instrumentation, production signing, and Play upload. The battle-channel
  flag stays off until a flag-on/flag-off Perfetto A/B run. No commit was created and
  nothing was published.

Useful references: [documentation index](docs/INDEX.md),
[performance baseline and budgets](docs/performance/BASELINE.md),
[Alpha 0.7.0 release checklist](docs/release/RELEASE_CHECKLIST_0.7.0.md).
