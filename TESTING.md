# Testing and verification

This is the current testing guide for Tamers Lab. The old 200 KB chronological
regression log was archived at
[docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md);
it is historical evidence, not the current result. Performance evidence lives in
[docs/performance/BASELINE.md](docs/performance/BASELINE.md); quality-tooling decisions
live in [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md).

## Home ↔ place transitions (2026-11-06, uncommitted tree)

This pass gives the farm home an authored transition to and from every place instead of a plain route
crossfade. The farm is a shell-level world (not a `NavHost` destination), so the motion is split: the
route transition animates the pages, and the shell animates the world layer underneath them. Evidence
for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 982 tests, 0 failures, 0 errors, 3 skipped; the new `NavigationMotionTest` (3 tests) passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no issue in `AppNavigation.kt`) |
| Native capture | **Pending.** No ADB device and no AVD were available in this pass (`adb devices` empty), so the transition feel, reduced motion and a screen recording of Home → place → Home are not captured yet |

### Motion contract

- **Home → place:** the outgoing chrome fades in 150 ms; the arriving place fades in over 220 ms while
  lifting ~2% of the window height over 320 ms; the farm layer keeps full opacity and pushes in
  1 → 1.05 over 280 ms, then the surface is dropped - after the place is opaque, so the world is never
  cut out from under a half-transparent screen. The farm's frame loop pauses as soon as Home is left,
  so no second Filament scene renders during the change.
- **Place → Home:** the place sinks ~2% while dissolving (fade 220 ms / travel 240 ms); the farm
  settles 1.05 → 1 over 360 ms; Home's chrome enters with a 40 ms delay so the world lands first.
- **Place → place:** unchanged short shared-axis horizontal slide (280 ms enter / 220 ms exit); routes
  outside the primary row keep the 200/140 ms crossfade.
- **Reduced motion** (system animator scale off): all of the above become ~90 ms crossfades with no
  scale and no travel; the world still holds through the fade instead of cutting.
- **Where:** `AppNavigation.kt` (`routeEnterMotion` / `routeExitMotion`, `FARM_HANDOFF_MS`,
  `FARM_DEPTH`, the farm layer's `graphicsLayer`); design rules in `DESIGN.md` **Farm home shell** →
  Transitions and **Navigation** → Motion.
- **Boundaries:** the AR stage, the partner sheet's own detents, the Radar place and the battle screens
  are untouched; this pass only decides how the shell swaps them.

## Top-header alignment (2026-11-06, same tree)

Owner request: every screen whose header does not use the same type as Home, the Radar place and the
battle surface. Audit of every header entry point in `app/src/main` (`TopBanner` call sites, the
`TopAppBar`/`CenterAlignedTopAppBar` variants, and the clusters that pad the status bar themselves):

| Surface | Header before this pass | Resolution |
|---|---|---|
| Home (`FarmHomeScreen` → `HomeTopHud`) | floating status cluster over the farm (scrim, HUD ink, 48dp keys, hairline) | reference — unchanged |
| Radar place (`RadarPlaceScreen`) and World place (`WorldScreen`) | `RadarTopChrome`: floating cluster, same scrim/keys/hairline | reference — unchanged (WorldScreen only had a dead `TopBanner` import, removed) |
| Battle surfaces (`OfflineTrainingBattleScreen`, Arena sessions → `BattleTopCluster`) | floating cluster over the arena | reference — unchanged |
| Every other screen / dialog header | `TopBanner`: gradient shelf, centered `titleLarge`, filled purple/cyan chips, surface-to-background gradient | rebuilt as the reference type |

`TopBanner` is now the page form of the same top-cluster type: one leading key, a left-aligned
`titleMedium` title carrying a single `labelMedium` context line, 48dp cut-corner keys with the
cluster's `SurfaceElevatedPurple` fill and hairline stroke (cyan only while pressed), one junction
line, and a palette-resolved scrim instead of the shelf. Shared primitives are in
`components/HudChrome.kt` (`hudTopScrim`, `hudInkShadow`/`hudInk`, `HudKey`, `HudHairline`). The
public API, the leading/trailing action priority, labels, `importEnabled`, the `trailingAction` slot
and every semantic are unchanged, so the ~38 call sites across 34 files were aligned without edits.

Screens aligned through that single component: Adventure, Items, Storage and its character picker,
Dex (`CardSelectionScaffold`, `CardViewScreen`, `CardAdventureScreen`, `ChooseCard`), DigiLine and farm
group, Quests, Settings, Credits, Lorebook, Chat and the world chat/conversation/recruits screens,
Arena lobby (`TamerArenaLobby`), battle hub (`BattlesScreen`), training preparation, Technique loadout,
Transfer hub and the transfer/scan flows, Sprite viewer, and the bracelet-conversion, species-sprite
and species-catalog dialogs. Legacy `home/ui/HomeScreen.kt` and `DigifarmScreen.kt` are not routed but
use the same component.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; `StorageScanCollectionTest` still finds both header keys by content description |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate) |
| Native captures | **Pending.** No ADB device and no AVD in this pass, so no portrait/landscape, light-palette or font-scale captures of the aligned header exist yet |

Boundaries: the Home/Radar/Battle clusters were not refactored onto the new primitives in this pass
(their behaviour is unchanged and previously device-verified); only the shared page header adopted the
type. 48dp targets, heading semantics, content descriptions and back behaviour are preserved, but
TalkBack and 1.3× font-scale acceptance for the new header are still unrecorded.

## Place-switch performance pass (2026-11-06, same tree)

The owner reported place switching feeling clunky and low-framerate on the SM-A346M, both before and
after the transition motion pass. This pass removes work from the switch instead of changing the
motion. **It is not measured**: no ADB device and no AVD were available (`adb devices` empty), so the
evidence is code-level plus the standing gates; the on-device procedure is in
`docs/performance/BASELINE.md` §4.8 and the result is still owed.

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped; the new `RouteArrivalTest` passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no new finding in the changed files) |
| On-device frame timing | **Not measured.** No device/AVD attached; a debug build is not representative, so compare a `-PnonDebuggable` debug build or a release build with §4.8 |

What was removed from the switch:

- **Repeated sprite decodes.** `CharacterEntry` rebuilds its bitmap whenever the idle frame swaps (the
  `remember` key changes with the frame) and on every arrival at a collection screen. Each rebuild was
  a per-pixel RGB loop plus a hardware-bitmap allocation on the main thread. `BitmapData.getBitmap()` /
  `getObscuredBitmap()` now serve a 16 MiB content-keyed `LruCache` (`utils/BitmapData.kt`), so a frame
  swap or a revisit is a cache hit.
- **Stacked entrances.** Every visible grid card ran its own 260 ms alpha+scale entrance on arrival, on
  top of the page transition. The shell now raises a process-wide, non-observable `RouteArrival` flag
  during composition of a route change and lowers it once the enter motion settles; arriving cards
  start fully visible, while items that appear later (scrolling, later pages, filters) keep the
  entrance. `DESIGN.md` **Farm home shell** → Transitions records the rule.
- **Persistent surface relayout.** The farm surface sat inside the inset-padded container, so every
  Home↔place change re-measured it and resized the `TextureView` that owns the Filament swapchain in the
  same frames as the transition. The shell is now two layers: the world keeps the window's full bounds
  for the shell's lifetime and only the `NavHost` takes the system-bar/IME insets and padding; the world
  also gets a stable modifier, so a route change no longer recomposes that subtree.

Known remaining hotspots, left alone because ranking them needs a device:

- Entering a full-bleed 3D place (`Radar`, `World`) constructs a fresh Filament `Engine` in the view
  constructor (`RadarIsometricViewport`, `Digifarm3dViewport`, `OfflineBattleScene`) and uploads the GLB
  on the main thread; moving that off the transition frame was not attempted unverified.
- The battle/Arena hub composes its whole animated surface on entry.

## Battle screen full-bleed redesign (2026-11-05, uncommitted tree)

This pass replaced the framed battle page (16dp gutter, bordered 1:1 viewport, half-screen command
deck) with one full-bleed battle surface shared by Arena/training battles and Radar encounters, and
stopped the Radar battle from reverting to the square window. A Radar-battle crash found after the
first gate is fixed in the same tree (see the crash-fix subsection below). Evidence for this working
tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed; debug APK rebuilt after the crash-fix cleanup (`app/build/outputs/apk/debug/app-debug.apk`, 2026-11-05 20:40) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 979 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; the androidTest source set compiles again after the stale `radarControls` parameter, and `OfflineTrainingBattleScreenTest` asserts the full-bleed invariants |
| `:app:lint` | 0 errors, 468 warnings, 38 hints (25 baseline entries are now stale/no longer found; warnings never gate) |
| Radar battle on device (crash fix) | Two consecutive Radar encounters on the SM-A346M ran the countdown → full fight → "Radar victory" (49 s and a shorter win), with one Filament engine creation per battle, the battle scene composed once, an empty `logcat -b crash` buffer and the app process alive throughout. The earlier instrumented build reproduced the native crash every time at the countdown (~3–5 s); hoisting the scene is the structural difference. |
| Arena battle on device | The earlier pass verified the full-bleed arena live on the same device (scene fill, enemy plate, HP/MP/ESP strip, projectiles); the crash fix does not touch that layout. |
| Native captures (this tree) | `.impeccable/review/battle-live-radar-fixed.png` (Radar victory state before the settled-chrome trim) and `.impeccable/review/battle-radar-second.png`. Portrait captures of the final shell are still pending. |

### Radar battle crash fix (2026-11-05, same tree)

- **Symptom.** Starting a Radar encounter battle crashed natively ~3–5 s in, at the countdown, with
  `SIGSEGV` in `libfilament-jni.so` (fault address `0x0`). Arena/training battles never crashed.
- **Diagnosis.** Temporary lifecycle logging showed the battle scene subtree being disposed and
  re-composed mid-battle and a second `scene engine created` inside one fight: Compose was replacing
  the `AndroidView` that owns the Filament engine. The native crash is the engine's component managers
  being destroyed/recreated out of order (use-after-free), not a bad asset or a decal precondition.
- **Fix.** The battle `OfflineBattleScene` was hoisted out of the radar/battle layout branch to the root
  of `WorldRadarBattleContent` (`OfflineTrainingBattleScreen.kt`), so it is composed exactly once per
  encounter; renderer-ownership changes only pause its frame loop. The settled-battle chrome trim
  (result panel owns the surface, live top cluster / command band / projected HUD step aside) landed in
  the same change; see `DESIGN.md` **Battle screen** → States.
- **Cleanup and re-verification.** The diagnostic logs, engine stack traces, `debugTag` parameter,
  deferred engine creation and the 250 ms handoff delay were removed before the final rebuild. The
  device pass above ran on the instrumented build; the cleaned APK (20:40) is
  compile/unit-test/lint verified, and a final on-device pass on that exact APK is pending because the
  SM-A346M's wireless ADB dropped and could not be reconnected in this session.

Boundaries and remaining acceptance work:

- The AR stage deliberately keeps the established square scanning/battle layout and its transparency
  contract; it is the legacy exception, not part of the new shell. The Blast overlay it shares was
  dimmed for legibility.
- Arena lobby/briefing/team setup, the Battle hub, legacy online PvP and the Radar encounter sheet are
  untouched in this pass.
- The finisher movie/camera sequence is kept as authored; only its captions and surrounding chrome were
  re-fitted.
- No TalkBack or large-font (1.3×) capture is recorded yet; the new chrome keeps 48dp targets and the
  existing accessibility actions.

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
