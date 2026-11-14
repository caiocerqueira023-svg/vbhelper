# Application module (`:app`)

The Android application shell for **Tamers Lab**. This module owns the Compose UI,
the Room database and its migrations, protobuf secret storage, the Koin graph,
type-safe navigation, and the thin adapters that connect the UI to the library
modules. Feature code lives under `app/src/main/java/com/tamerslab/`.

| Property | Value |
|----------|-------|
| Gradle path | `:app` |
| Namespace | `com.tamerslab` |
| `applicationId` | `com.tamerslab.vbhelper` (own app identity; see [ADR 0006](../docs/adr/0006-own-application-id.md)) |
| Launcher label | `Tamers Lab` (`@string/app_name`) |
| `versionName` | `Alpha 0.7.0` target |
| SDKs | `compileSdk 37`, `minSdk 28`, `targetSdk 37` |
| Test build type | `integrityCheck` (`testBuildType`), used by all Android unit tests |

## Feature package map

| Package (`com.tamerslab.*`) | Responsibility | Representative entry points |
|-----------------------------|----------------|-----------------------------|
| `battle` | Offline battle, arena, online PVP | `battle/ui/BattlesScreen.kt`, `battle/domain/BattleDefinitions.kt`, `battle/offline/core/BattleSimulator.kt`, `battle/online/OnlineBattleFeature.kt` |
| `farm` | Digifarm, Digiline, farm 3D rendering | `farm/ui/digifarm/DigifarmScreen.kt`, `farm/ui/digifarm/Digifarm3dViewport.kt` |
| `transfer` | Watch scan/transfer, game saves, PC companion client | `transfer/ui/TransferScreen.kt`, `transfer/ui/scan/ScanScreen.kt`, `transfer/data/gameSave/` |
| `chat` | Digimon chat and lorebook | `chat/ui/ChatScreen.kt`, `chat/DigimonPersonaBuilder` consumers |
| `species` | Dex, cards, catalog, sprites | `species/ui/card/CardsScreen.kt`, `species/SpeciesRepository` consumers |
| `world` | World hub, radar, encounters, chat | `world/ui/WorldScreen.kt`, `world/WorldSpawnSelector.kt` |
| `storage` | Stored individuals and storage tools | `storage/ui/StorageScreen.kt` |
| `home` | Shared DIM-style Home/partner surface | `home/ui/HomeScreen.kt` |
| `items` | Inventory and item application | `items/ui/ItemsScreen.kt` |
| `settings` | App settings, theme, locale, secrets import | `settings/ui/SettingsScreen.kt`, `settings/ui/controllers/CardImportController.kt` |
| `adventure` | Adventure missions and rewards | `adventure/ui/AdventureScreen.kt` |
| `quests` | Quest rules, dialogue, watch evidence | `quests/ui/QuestScreen.kt` |
| `data` | Shared Room database and DAOs | `data/database/AppDatabase.kt` (schema version 55) |
| `dtos` | Cross-feature transport/transfer data holders | `dtos/` |
| `domain` | Domain models, repository interfaces, use cases | `domain/usecase/FeatureUseCases.kt`, `domain/repository/` |
| `ui` | Shared state contracts and theme | `ui/state/UiState.kt`, `ui/theme/Theme.kt` (`TamersLabTheme`) |
| `rendering` | Frame budget/pacing, 3D sprite helpers | `rendering/FrameBudget.kt`, `rendering/sprite3d/` |
| `companion` | Firmware, logs, card validation, PC companion UI | `companion/validation/ValidatedCardManager.kt`, `companion/logs/CompanionLogService.kt` |
| `di` | Koin modules and the container adapter | `di/AppModule.kt`, `di/DefaultAppContainer.kt`, `di/TamersLabApplication.kt` |
| `navigation` | `@Serializable` routes and the typed `NavHost` | `navigation/AppRoute.kt`, `navigation/AppNavigation.kt` |
| `security` | Baked-in watch connection keys, optional APK secrets import, protobuf secret handling | `security/BundledSecrets.kt`, `security/ApkSecretsImporter.kt`, `security/` |
| `vitalwear`, `utils`, `widget`, `components`, `audio` | Import/export formats, widgets, shared UI, music | `widget/DigimonWidgetProvider.kt`, `components/`, `audio/AppMusicController.kt` |

## Key entry points

- **`di/TamersLabApplication.kt`** — application class named in the manifest. Starts
  Koin (`startKoin { androidContext(...); modules(appModules) }`), takes the
  `DefaultAppContainer` from the Koin graph, launches the app-scoped startup work,
  and schedules `WorldAfkScheduler`.
- **`MainActivity.kt`** (package `com.tamerslab`) — creates the screen controllers,
  applies the saved theme/locale, resolves deep links through
  `navigation/AppRoute.kt`, and hosts the Compose tree via `AppNavigation`.
- **`di/AppModule.kt`** — the Koin module list: `appModule`, `databaseModule`,
  `networkModule`, `hardwareModule`, `repositoryModule`, `useCaseModule`,
  `viewModelModule`. Repository adapters live in `di/repository/`.
- **`navigation/`** — `AppRoute` is the single navigation contract; serial names
  intentionally preserve the pre-Phase-3 route strings so existing deep links and
  restored back stacks keep resolving.

## Dependency direction

`app` depends on `:hardware`, `:game-save-core`, and `:companion-protocol`, and
consumes `:baselineprofile` only as a baseline-profile generator. The libraries never
depend on `:app`; `ArchitectureBoundaryTest` enforces that, along with keeping Android
and Room imports out of `domain/usecase` and `domain/repository`. `:pc-save-companion`
is a desktop distribution and is not an app dependency. See the module READMEs for
[hardware](../hardware/README.md), [game-save-core](../game-save-core/README.md), and
[companion-protocol](../companion-protocol/README.md).

## Build and test

Run from the repository root (Windows PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testIntegrityCheckUnitTest
.\gradlew.bat :app:lint
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest   # device required
```

Coverage is opt-in and not wired into `check`:

```powershell
.\gradlew.bat :app:jacocoTestReport
# app/build/reports/jacoco/jacocoTestReport/html/index.html
```

## Schemas and protobuf

- Room exports schemas to `app/schemas/com.tamerslab.data.database.AppDatabase/`
  (current `55.json`); KSP is configured with `room.schemaLocation` and
  `room.generateKotlin=true`. The same directory is added to the `androidTest`
  assets for migration tests.
- Protobuf sources live in `app/src/main/proto/com/tamerslab/security/Secrets.proto`
  and `app/src/main/proto/com/tamerslab/vitalwear/protos/Character.proto`; they are
  generated as protobuf-lite.
- A Room schema change requires a version bump, a `MigrationTestHelper` test, and a
  new exported schema. Never use destructive migrations.

## R8 / ProGuard

Release builds use `isMinifyEnabled = true` and `isShrinkResources = true`. Rules are
in `app/proguard-rules.pro` and cover Gson/`TypeToken`, Retrofit, protobuf-lite, Room,
WorkManager, Koin, kotlinx.serialization, and reflective app packages; `-dontwarn`
entries absorb the Lombok/slf4j annotations carried by the DIM dependency. Retain
`app/build/outputs/mapping/release/mapping.txt` for every release candidate. Release
output is currently unsigned (there is no production keystore). Do not disable
minification to work around a missing keep rule — fix the rule and re-run the device
smoke.

## Status

Phases 0–7 are implemented, with documented deferrals for Phase 5.1/5.2/5.5, a
coverage threshold, and the full public-KDoc sweep. Post-Phase-6 verification on
2026-10-31 passed: 1,157 unit tests (7 skipped, 0 failures), debug + R8 release
assembly, `:app:lint` 0 errors, 281/281 connected instrumentation tests on SM-A346M
(API 35), and debug/debug-signed-release install+launch smoke. Baseline profiles were
regenerated on the device; named-class coverage is above 80% line; device benchmarks
recorded a 875 ms cold-start median and battle frame-CPU P95 13.3 ms. Room query plans,
soak, and thermal validation remain unmeasured. See [docs/INDEX.md](../docs/INDEX.md),
[docs/refactor/TARGETS.md](../docs/refactor/TARGETS.md) and
[docs/performance/BASELINE.md](../docs/performance/BASELINE.md).
