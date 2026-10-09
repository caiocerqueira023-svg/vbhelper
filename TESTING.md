# Regression checks

## Radar social behavior (schema 33 / ecosystem rules 3)

See `RADAR_SOCIAL_IMPLEMENTATION.md` for implementation and verification details.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.domain.personality.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" --tests "com.github.nacabaro.vbhelper.digifarm.*" --tests "com.github.nacabaro.vbhelper.chat.*"
py -3 -B scripts/test-world-social-db.py
```

Latest full app evidence: **466 cases, zero failures, three existing fixture-dependent
skips**. Root reader tests, debug APK assembly, lint with the existing baseline, and
Android-test compilation passed. Social/interaction/living-world/chat-memory host
SQLite suites passed **31 checks**. The schema-33 migration is compared with Room's
generated export and preserves existing identity, personality labels/timestamps,
private history, player trust, and logical clock.

New regressions verify non-cosmetic personality choices, balanced attacks, neutral
meetings, explicit activity acceptance/refusal, locale-aware opening diversity,
private/public memory separation, persistent social state, need-decay equivalence,
and stale conversation epochs. Real Room approach/handoff, greeting-expiry, and
activity-discussion/refusal cases are compile-only Android coverage. Live GPS,
renderer behavior, and provider response quality still require device execution.

Bugbot could not run because its agent type is unavailable; this change received
a manual code review and corresponding corrective regressions.

Run from the repository root in PowerShell:

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug --offline --console=plain
.\scripts\test-watch-identity.ps1
python scripts/test-evolution-history-db.py
python scripts/test-offline-battle-art-db.py
```

The app unit-test variant is `integrityCheck`; its explicit task is
`:app:testIntegrityCheckUnitTest`. The root `test` task also checks both NFC
library variants and the DIM reader. The first run may require online dependency
resolution (omit `--offline`).

The offline battle art check executes the production storage/radar DAO queries
against SQLite with local card keys that differ from DiM/BEM numbers. It verifies
catalog identities, custom-card attack isolation, embedded BEM pixels, and the
additive 28→29 migration. `ImportedAttackArtTest` checks the actual parsed attack
IDs, including zero, missing IDs, and BEM-specific sprite boundaries.
`CustomCardAttackArtTest` exercises import/re-import against real Room on Android;
re-importing a matching file restores legacy assignments without recreating owned
individuals. `BattleImpactVisualTest` checks the 180ms flash lifetime (including
paused simulation), per-victim multi-hit deduplication, and scene placement;
billboard GLB tests verify alpha-preserving art without outlines or ground shadows.
Actual depth occlusion and flash timing require Android renderer verification.
`RadarTabNavigationTest` exercises the actual World/Radar composition while
switching to Home, Storage, Dex, Battle, and Settings during animated transitions.
The World destination's entry owns its saved state throughout the outgoing frame;
the test requires Android execution to verify the former missing-back-stack crash.

## Attribute-only DW1-inspired offline battle (ruleset 3)

Mechanics and source provenance are documented in `OFFLINE_BATTLE_DW1_IMPLEMENTATION.md`.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.NpcBattle*Test" --tests "com.github.nacabaro.vbhelper.world.RadarBattleFlowTest"
.\gradlew.bat test :app:lintDebug :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=2
```

`Dw1BattleMechanicsTest` checks reference damage/rounding/caps, the attribute triangle,
power-dependent readiness, weighted eligibility, stable arena profiles, defensive
reaction during observation, percent-HP ticks, protection/exclusivity, bounded buffs,
replanning without out-of-range damage/resource spending, reactive Counter, accuracy,
purpose-specific RNG, and control recovery. `NpcBattleVersionTest` verifies exact
fixed-step replay of new envelopes and legacy arrays with absent new fields, pinned
configuration/catalog/ruleset, seed validation, and unsupported-version rejection.

`Dw1BattleBalanceMatrixTest` runs 1,000 paired seeds per stage (12,000 battles total),
checking mirror win rates, side swaps, timeouts, duration tails, and incapacitation.
Latest full-app evidence: **429 cases, zero failures, three existing private-fixture
skips**; root reader tests, lint, debug assembly, and Android-test compilation passed.
The mirror matrix measured 47.53–51.58% decided win rates, zero paired side difference,
zero timeouts, p50 38.28–40.70s, and p95 48.65–51.41s. Double KOs were 2.5–5.0% by
stage and remain reported separately. Android runtime/renderer acceptance is not
established by compilation.

## Selectable color themes

Settings → Appearance exposes VB Helper (the existing dark purple/cyan palette),
VB Lab (charcoal/cyan), and VB Arena (white/silver/green). The choice is saved in
`app_preferences` and applied immediately, independently of the phone's night mode.
Typography, geometry, sprites, and motion remain shared.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.ui.theme.*" :app:compileIntegrityCheckAndroidTestKotlin :app:assembleDebug --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=2
```

`AppThemeTest` checks saved-value fallback, all three choices, retained Helper colors,
reference color families, text contrast, and primary action-label contrast.
`ThemeSurfaceContrastTest` checks all five Material tonal surfaces in each palette.
`AppThemeSelectionTest` exercises the real picker and preference store, restoring
each choice and checking composition colors, typography, shapes, and light/dark
configuration independence. Its execution requires a device/emulator; compilation
alone does not establish runtime or screenshot acceptance.

## Card evolution chart (build checks only)

Focused commands, from the repository root in PowerShell:

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 `
  --tests "com.github.nacabaro.vbhelper.screens.cardScreen.*" `
  --tests "com.github.nacabaro.vbhelper.source.Card*Test" `
  --tests "com.github.nacabaro.vbhelper.source.JogressImportReaderTest" `
  --tests "com.github.nacabaro.vbhelper.source.SpeciesOriginQueueTest" `
  --tests "com.github.nacabaro.vbhelper.ui.theme.ThemeSurfaceContrastTest"
py -3 -B scripts/test-dex-evolution-db.py
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

`CardEvolutionLayoutTest` covers branches/merges, disconnected and fusion-only
characters, deterministic placement, route deduplication/conditions, and bounds;
shared overlapping buses, including mixed adjacent/skipped routes in the same gap;
and presentation-only final sections for terminal evolution/Jogress results.
Cycles retain imported stages while acyclic downstream terminal chains advance;
results also reached from earlier stages and explicit BEM stages retain their
imported placement rules. Toggling Jogress changes connectors, not node positions.
`CardChartViewportTest` covers minimum 48dp node bounds, centroid-anchored zoom,
bounded pan, fit behavior, and accessible scroll positions.

`JogressImportReaderTest` covers DiM/BEM attribute and partner-specific tables,
deduplication, absent tables, sentinels, and foreign-card endpoint isolation.
`CardReimportPolicyTest` covers named variants, unique content matches, duplicate
names, ambiguous candidates, and unambiguous legacy attack-art recovery. The eight
host SQLite/migration checks execute production SQL for local-card route isolation,
ownership/discovery, progress/order, correct destination art via
`CardCharacter.spriteId`, both same-card Jogress partner edges, foreign partners as
requirements only, automatic/manual name rules, and additive schema 30→31 retention.

The 18 new JVM cases comprise seven `CardBatchImporterTest`, four
`SpeciesOriginQueueTest`, four `CardDocumentReadTest`, two `CardImportRunGateTest`,
and one `ThemeSurfaceContrastTest`. They cover mixed/new/re-import batches,
duplicate URIs and aliases, 1,001-file selection without an app count cap,
independent failures, committed progress on Stop, origin reservations/recovery,
queued-name refresh by ID, close-before-persist (including close failure and late
open), stalled-stream cancellation, and stale-run admission/publication ownership.
The contrast test calculates luminance from all three selectable color schemes and
requires `onSurface` against all five `surfaceContainer*` roles to reach 4.5:1.

### Import and requirement contract

Dex selection uses `CardSelectionScaffold` with zero nested system insets;
`TopBanner` places Import at top-left opposite Edit. Dex and Settings share
`OpenMultipleDocuments` with no app-imposed file-count cap. `CardBatchImportPanel`
shows preparation/progress, Stop/Stopping, added/updated/failed totals, dismissible
results, and safe per-file issues (display name or file number, no raw exception/URI).
Origin-bookkeeping issues remain distinct from failed imports of saved cards.

Files run sequentially, with each new import or re-import committed atomically and
returning `CardImportResult(cardId, cardName, isNew)`. Dex queues an origin choice
only for a NEW card after commit; Settings respects its existing prompt toggle.
Duplicate URIs are read once; matching aliases/re-imports add no new choices, but
refresh an already-pending label by card ID. A choice is reserved by ID, its status
saved before the queue entry is cleared, and failed saves release the reservation.
Recovery runs once per retained model, filtering the existing queue to still-existing
UNKNOWN cards; it does not enqueue every unknown card or replay classified choices.

The retained `CardImportViewModel` uses Application services and `SavedStateHandle`
for picker origin policy, so configuration changes retain the job/results without an
Activity reference. `CardImportRunGate` guards admission and publication until the
owning job and cleanup complete; completion supplies a terminal fallback even if
cancellation precedes the lazy body. Provider name queries and opens receive a
`CancellationSignal`; cancellation also closes the active stream. Parsing and close
finish BEFORE persistence, including cleanup when open returns late, so close errors
cannot turn an already-committed file into a failure.

Schema 31 adds `CardSpecificJogress` and `Card.nameIsUserEdited`. DiM and BEM imports
store both attribute and partner-specific Jogress; same-card partners contribute
both local source edges, while foreign partner references remain requirements and
never become false local nodes. Migration adds storage, not missing source routes:
re-import the original card file to recover previously discarded Jogress and
ambiguous legacy BEM adventure requirements.

An unambiguous re-import refreshes ordinary evolution definitions, both Jogress
tables, and attack art for one matching local card, retaining card/character IDs,
owned individual IDs and state, discovery, and card progress. Same-body custom
variants must not receive bulk refreshes; an unresolved match follows the new-card
import path and leaves existing variants intact. A supplied filename refreshes the
automatic card name unless `nameIsUserEdited` is set. Legacy name provenance was
absent: the user explicitly chose to refresh legacy names on re-import, including
names that may have been manually assigned before the flag existed. Future explicit
manual renames set the flag and are protected from filename refresh.

An absent adventure requirement is stored as `-1`. Valid BEM adventure index `0`
displays stage `1`; nonnegative indices display index + 1. Migration normalizes only known
legacy DiM zero values to `-1`, preserves BEM zero, and leaves source recovery to
re-import. The chart's final sections are presentation-only and never rewrite
imported character stages or stats.

### Recorded evidence and limits

- XML reports in `app/build/test-results/testIntegrityCheckUnitTest/` establish
  **18 new tests passing**, with zero failures/errors/skips, in the five suites above.
  Earlier chart evidence records 17 layout, seven viewport, three Jogress-reader,
  and five re-import-policy tests passing. Main verification reports all **eight
  host SQLite/migration checks passing**.
- The latest full-app report at
  `app/build/reports/tests/testIntegrityCheckUnitTest/index.html` records **407
  tests, zero failures, and three private-fixture skips** (404 successful executions).
  An earlier run exposed a pre-existing personality determinism test comparing
  different clocks; both calls now use explicit `now = 1L` (application behavior
  unchanged). Its latest three-case XML report has zero failures/errors/skips.
- Final Android-test compilation, debug assembly, and lint passed. Current checks
  use `--max-workers=2`; lint retains advisory/baselined findings.
  APK output: `app/build/outputs/apk/debug/app-debug.apk`. The living-world results
  below predate this extension.
- Main's latest code review scored all four findings resolved: light-scheme contrast,
  stale cancelled-run ownership, stream close after commit, and blocking-provider
  Stop, including the pre-start terminal fallback. Earlier fixes for ambiguous
  re-import, shared skipped-gap buses, cycle descendants, and BEM index zero remain.
- `DexEvolutionPersistenceTest`, `CustomCardAttackArtTest`,
  `CardEvolutionChartTest`, `DexCharacterDetailsTest`, and
  `CardSelectionImportUiTest` remain compile-only Android coverage.
  Their Room/Compose cases cover reactive ownership, in-place re-import, variant
  isolation, name protection, BEM index zero, selection/navigation/scroll callbacks,
  the disabled Jogress chip and overlay placement, readable requirements, and
  obscured stats with an available Close action. New cases add two selection
  header/body-bottom/Stop checks and two Room atomic-result/rollback checks;
  compilation does not execute these assertions.

**User-selected scope: BUILD CHECKS ONLY.** No installation, Android runtime tests,
screenshots, font-scale checks, or TalkBack evidence. Compilation and semantic-click
test coverage do not establish physical hit-testing or visual approval.

### Deferred native acceptance

- Select mixed DiM/BEM new cards and re-imports, large batches, and aliases from local
  and multiple document providers through Dex and Settings. Check top-left Import
  opposite Edit, disabled admission during a run, preparation/progress/Stop/results,
  and no app count cap. Confirm Dex asks only for committed NEW cards; Settings keeps
  its prior toggle behavior. Re-imports/aliases must not add choices, pending labels
  must follow card IDs, and saving a choice must not consume the next card's prompt.
- Mix valid, invalid, unreadable, and close-error files: later files continue, failed
  files leave no partial rows/prompts, and saved cards retain their success counts
  when origin bookkeeping fails. Stop during provider query/open/read and immediately
  after starting; try a fast restart and rotate during picker/import/choice handling.
  Require terminal Stopped state, cleanup before replacement admission, no stale
  progress/results, retained completed cards, and recovery only of queued existing
  UNKNOWN cards. Provider cancellation responsiveness still needs native evidence.
- In both system appearances, check tonal dialogs and all container levels for
  readable text; the light scheme maps every container role to incumbent purple.
  Info/Jogress dialogs use medium cut-corner Material Cards on `surfaceContainerHigh`,
  capped at 480dp/92% width and 88% height, following `StorageDialog` and
  `TransformationHistoryCard`; info Close/Jogress text actions stay outside scrolling.
- Inspect compact selection cards, logo/name/status/progress and edit controls, plus
  the actual selection-body and chart-footer alignment with bottom navigation in
  both system navigation modes and rotation. Require no duplicate inset gap; source
  geometry and compile-only assertions do not establish rendered bottom alignment.
- Inspect branches/merges with mixed adjacent and stage-skipping routes: every
  shared gap uses an aligned overlapping bus, with shared segments drawn once and
  skipped/backward routes outside unrelated nodes. Shared-path ambiguity is an
  accepted user choice; require neither separate per-route tracks nor arrowheads.
- Tap actual displayed nodes after sustained pan and pinch/button zoom; check
  correct details and usable minimum 48dp node bounds. Check the subtle Fit/zoom
  overlay's Material hit areas, zoom-limit disabled states, and taps near/under the
  overlay so controls and chart gestures do not select unintended characters.
  There is no drag/pinch hint text or separate controls strip.
- Check chart-to-footer continuity; previous/next controls and logo/name remain
  readable at the bottom-navigation boundary.
- Start with Jogress off; keep its labeled chip visible but disabled when no stored
  routes exist (including loading). Toggle dashed yellow Jogress visibility while
  ordinary routes stay solid and nodes stay fixed, including fusion-only results,
  both same-card partners, and foreign partners shown only as requirements in details.
- Check terminal-only ordinary evolution and Jogress results in their final
  presentation section even with Jogress off; acyclic downstream chains advance,
  actual cycle members stay at imported stage, and descendants of cycles advance.
  Earlier-stage incoming routes and explicit BEM stages retain their rules;
  details/stored stats must still show the imported stage.
- Check Official and Custom DiMs have active cyan technical frames, while Unknown
  cards keep their distinct status/origin action; imported logo pixels remain crisp.
- Check previous/next on first, last, and single-card catalogs, with matching
  logo/title and Adventure destination; navigation must stay bounded.
- Return from Adventure and rotate while zoomed/panned; check displayed-card,
  selection, Jogress preference, and viewport restoration. Exercise loading, empty,
  failure/retry, and reactive discovery/availability changes.
- At large fonts and with long localized names/species descriptions/requirements,
  inspect info and Jogress dialogs: clear HP/BP/AP hierarchy, imported name pixels,
  species level/type/profile/moves, correct evolution destination on tap, attribute
  and partner-specific requirements, scrollable content, and pinned Close/Jogress
  actions. Verify the Custom species picker and its unavailable-data state, obscured
  name/stats, absent stats/profile/routes, native Back/outside dismissal, and focus
  return after nested Jogress/species dialogs. Adventure index `0` must visibly say
  stage `1`, and `-1` must show no adventure requirement.
- Check grayscale/static-color/animated ownership and reduced-motion behavior;
  with TalkBack check node index/imported stage/ownership/selection, labeled
  toggle/controls, route-card actions, pinned dialog actions, and consecutive
  two-axis scrolling. Native readability, physical hit areas, and focus remain
  acceptance work rather than inferred passes.
- Re-import DiM/BEM fixtures with changed evolution/Jogress/attack definitions,
  missing legacy data, filename changes, and an explicitly manually renamed card.
  Exercise same-number/same-body custom variants, duplicate names, ambiguous content,
  and missing-art recovery: only an unambiguous existing card is refreshed. Confirm
  the chosen legacy-name refresh and future manual-name lock, retained local
  card/character/individual IDs, owned stats, discovery/progress, and unaffected
  other variants; verify repaired adventure requirements after reopen.

## Digimon scan conversion (build checks only)

### Behavior contract

- Scan belongs to the local `cardCharacterId`: each distinct opposing wild awards
  **+20 percentage points** only for a player `ALLIED_VICTORY`, capped at **100%**.
  Friendly and autonomous encounters, losses, draws, and abandonment award nothing;
  entries on other local cards remain isolated.
- Scan rewards and the result receipt persist atomically. Victory feedback shows
  committed reward receipts, so replay cannot award or announce an uncommitted result.
- Conversion is manual in Dex, with a form/card preview and optional nickname. It
  consumes **100%** into a fresh, inactive Storage individual with the matching VB/BE
  profile: **VB countdown 1; BE countdown 0**. Conversion and Close actions stay
  pinned outside scrolling content. UI strings cover English, Portuguese, and Japanese.
- The persisted Settings debug switch awards **100 percentage points per eligible
  defeat** only in a debuggable application; release ignores the saved switch.
- Schema **32** is generated, with the additive **31→32** migration registered.

### Recorded verification

Historical scan-conversion implementation verification (before the Storage collection extension):

```powershell
.\gradlew.bat test :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
py -3 -B scripts/test-digimon-scan-db.py
```

- The final Gradle command passed with **BUILD SUCCESSFUL**. Full-app HTML at
  `app/build/reports/tests/testIntegrityCheckUnitTest/index.html` records **438
  cases, zero failures, three existing private-fixture skips** (435 executions),
  including **nine new `DigimonScanPolicyTest` passes**. Earlier counts elsewhere
  in this document remain records of their respective verification runs.
- The host SQLite script passed **five tests** covering migration/retention,
  per-entry isolation, guarded consumption, rollback, and receipt uniqueness.
- `DigimonScanPersistenceTest` and the modified `WorldInteractionPersistenceTest`
  and `DexCharacterDetailsTest` compiled only. Actual Room and Compose assertions
  were **not executed**; host SQLite checks do not establish genuine Room migration passes.
- Lint passed against the existing baseline with **174 warnings and seven hints**,
  with no new errors. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Initial source review required a VB countdown fix; the reviewer subsequently
  scored it **resolved**. The ship verdict covers that source fix only and gives
  **no native approval**. No screenshots, device/runtime evidence, or new shipping
  rasters are claimed.

### Deferred native acceptance

- Complete end-to-end wild victories with normal rewards and the persisted debug
  switch off/on; verify eligibility exclusions, local-entry isolation, the cap,
  exactly-once committed feedback, and release ignoring the debug preference.
- Convert, restart, and inspect nickname and inactive Storage state; check VB/BE
  export profiles/countdowns and conversion while the farm is full.
- Inspect English/Portuguese/Japanese UI at large fonts and with reduced motion;
  verify pinned Convert/Close, system Back, IME clearance, and TalkBack focus/actions.
- Execute genuine Room upgrades and persistence/reopen/rollback assertions on Android.

## Storage scan collection (build checks only)

### Behavior contract

- Storage exposes a top-left **Scan data** icon chip opposite the existing
  Adventure action. Scan data remains available when Storage has no individuals.
- The collection observes all positive scan percentages across imported local
  `cardCharacterId` entries, not just completed scans or currently owned species.
  Production SQL orders by percentage descending (ready entries first), then
  case-insensitive card name, character index, and local character ID.
- Each row shows imported pixel portrait and name art, canonical species name
  (or the existing fallback), card name, percentage, and cyan progress. Partial
  entries remain visible with Convert disabled; Convert is enabled at **100%**.
  Art and profile identity follow the local imported character rather than a
  shared card number.
- Storage and Dex reuse the extracted `DigimonScanConversionDialog`: form/card
  preview, optional nickname, live readiness, and the existing guarded backend
  conversion transaction. This extension adds no schema change or raster assets.
- Consumption resets the scan to zero; the positive-only Room flow removes that
  entry from the collection. The selected preview snapshot is retained through
  that emission until conversion completes, so removal cannot prematurely
  dismiss the preview. Feedback and Close remain outside the `LazyColumn`,
  including when the last entry disappears.
- Loading, loaded-empty, and read-failure/Retry states are explicit. Conversion
  outcomes are reported separately. Strings cover English, Brazilian Portuguese,
  and Japanese.

### Current focused verification

Evidence supplied by the implementation pass for this documentation update:

```powershell
py -3 -B scripts/test-digimon-scan-db.py
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.source.DigimonScanPolicyTest" :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

- Host SQLite: **eight passes**. Three newly added checks execute the actual
  production collection SQL for positive-only filtering/order, local imported
  art and profile identity, and consumed-entry disappearance. The five existing
  migration/retention, isolation, guarded-consumption, rollback, and receipt
  invariants also pass. Host SQLite does not execute Room flow delivery on Android.
- Gradle: **BUILD SUCCESSFUL**, with **nine focused `DigimonScanPolicyTest`
  passes**, Android-test Kotlin compilation, lint, and debug assembly passing.
  The historical **438-case** full-app run above was **not rerun in this pass**.
- Lint retains **174 existing warnings and seven hints**, with no errors.
  Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `StorageScanCollectionTest` has **three compile-only cases**: header Scan action
  with Adventure preserved; visible partial versus enabled complete conversion;
  and simulated reactive removal to empty with feedback and Close retained.
  These Compose assertions were not executed and do not establish a live Room
  conversion or actual button-corner geometry.
- Source finish review returned **ship**, with no material introduced bugs.
  That disposition is source-level only: **no visual/runtime approval**, device
  execution, installation, or screenshots are established. This documentation
  update records supplied evidence; it does not rerun the checks.

### Deferred native acceptance

- Tap the actual top-left Scan data chip and check its corner geometry, hit area,
  and placement opposite Adventure. Enter from empty Storage and confirm both
  actions remain usable and Adventure retains its destination.
- Scroll a populated collection at large font scales, with long card/species
  names and EN/PT-BR/JA strings. Inspect pixel art, local-card identity, ready-first
  order, percentages/progress, and feedback/Close outside the scrolling list.
  Confirm partial Convert is disabled and full Convert is enabled.
- Exercise the shared preview from Storage and Dex: cancel, system Back, optional
  nickname, IME clearance, focus/return focus, and guarded controls during conversion.
- Complete one conversion against live Room: verify the new inactive Storage
  individual and nickname, immediate Storage update, consumed-row disappearance,
  selected-preview retention until completion, and feedback/Close after the last
  scan disappears. Verify the existing VB/BE profile/countdown contract.
- Rotate and restart during collection/preview/conversion and after completion;
  verify safe state restoration, persisted scan/individual state, and no duplicate
  consumption or creation.
- Exercise loading, loaded-empty, read failure/Retry, and conversion failure on
  Android. Require usable dismissal and recovery, not merely compiled assertions.

## Android persistence checks

### Radar living-world foundation

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.world.*" --offline --console=plain
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.screens.worldScreen.RadarPresentationTest" --offline --console=plain
python scripts/test-radar-ecosystem-db.py
python scripts/test-world-interactions-db.py
python scripts/test-living-world-migrations.py
python scripts/test-world-chat-memory-db.py
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain
```

The host SQLite checks use the exported v24/v25 schemas and production 24→25
migration SQL to verify anchors, neutral NPC emotion, schema compatibility, and
preserved individual identity/private chat/player trust/recruitment data.
`WorldEcosystemPersistenceTest` additionally exercises real Room migration and
checkpoint reopen on Android. Compiling the Android tests does not execute them.

The interaction checks use the production 25→26 schema, database guards, and DAO
SQL to verify unique participation, protected expiry/recruitment, card/individual
deletion, transaction rollback, result deduplication, recovery, and retention.
`WorldInteractionPersistenceTest` covers the actual Room command repository,
including concurrent reservations, stale input, private-chat claims, and NPC
result isolation. It requires authorized Android execution for runtime evidence.

`RadarCommandGateTest` checks session lease/revision/readiness and transactional
rollback with a fake clock. `RadarPresentationTest` checks snapshot-authoritative
geometry/assets, pose selection, and status priority. Android cases additionally
cover atomic gated reservation/checkpoint failure and the status panel's recovery
controls and layout slot. Verify real GPS freshness/stationary updates, large
fonts, TalkBack, and pending-battle retry on an authorized device/emulator.

The Radar living-world additions occupy schemas 25–28 (26→27 movement/social inputs,
27→28 transcripts/intents/NPC checkpoints). `test-living-world-migrations.py`
compares the production additive SQL with the fresh Room schema and verifies
private history and prior clock/identity preservation. New JVM suites cover
geographic replay/bounds, FP compression/renderer generations, structured
dialogue validation, initial injuries, Radar 2v1, and complete NPC simulator replay.
Actual FP composition, engine-release ownership, group/challenge/team flows,
physical compass/range and workload budgets need authorized Android verification.

`WorldDialoguePolicyTest` verifies reciprocal, unexpired sparring consent, inherited
sparring stakes, attributed refusal, rejected evidence, and the trainer consent
boundary. Coordinator/command tests cover monotonic replay revisions, input tick
ordering, checkpoint rollback, and hidden/frozen input rejection. FP geometry
tests cover the shared close-range display/hit-target offset and missing-frame
fallback. Room cases also cover missed joined-preparation timeout and explicit
process recovery; host recovery checks execute the production database-open SQL.

`WorldWildInitiationPolicyTest` covers unrequested greetings/hostile attacks,
the required owned partner for combat, fresh/ranged/live presence, no offscreen
player encounters, public participant tap lookup, and expiring speech without
exposing private chat. FP cases cover lower/bounded pitch, close-range clearance,
observable handoff, participant spacing, and reduced-motion lunges. The new
`RadarRendererOwnershipTest` checks Compose updates after request/detach without
a second screen action. Room cases cover a single-wild chat, attack claim
transfer/restoration, exactly-once defense results, and active-partner changes
during preparation. Android cases remain compile-only.

The conversation-screen regressions verify public/ready/ranged eligibility,
read-only private/ended/remote contexts, one shared clock across Radar→chat lease
handoff, reversed vertical drag, measured label clearance/edge clamping, and
readable dialogue despite invalid intent metadata. Historical structured replies
are cleaned for display without rewriting stored history. New Android cases
check long labels at large font scale and single-wild sparring handoff; they are
compiled, not executed.

First-person Radar billboarding has geometry regressions for front normals
pointing toward the player's eye at varied positions/heights, projection corners
sharing the mesh's rotated basis, and finite coincident-coordinate handling.
The billboard helper is called only by the FP Radar viewport; the full unit suite
also retains the existing shared sprite-facing regressions.

Unified 1:1 chat and wager cases verify acceptance of the current user turn,
nonlethal agreed duels despite aggressive voice, invitation/refusal boundaries,
friendly opponent retention, saved stakes and winner-specific reaction prompts,
and a private-chat lease that keeps time while disabling unrelated autonomous
interruptions. Room cases cover greeting deduplication into the Talk history,
exactly-once friendly results/memories, and an existing-v29 upgrade to schema 30.
They remain compile-only. The five `test-world-chat-memory-db.py` checks execute
the production migration and verify durable memories after event cleanup,
import provenance, reaction rollback, and individual deletion.

### Latest living-world verification

- Full `:app:testIntegrityCheckUnitTest`: 357 tests reported, zero failures,
  three existing private-fixture skips (354 successful executions).
- `:app:assembleDebug`, `:app:compileIntegrityCheckAndroidTestKotlin`, and
  `:app:lintDebug` pass. Lint still reports advisory/baselined findings.
- Host suites: five Radar migration checks, 16 interaction checks, and three
  living-world migration checks, and five chat-memory checks pass (29 total).
- `git diff --check` passes.

**Device acceptance is deferred at the user's request.** The authorized isolated
emulator attempt could not boot: no connected device/AVD, no installed hypervisor
driver, and the available system-image folders lack the kernel/system image.
No APK was installed. Android instrumentation, first-person screenshots,
TalkBack/large-font checks, compass/range checks, and resource/workload measurements
have not run. These require a separately authorized, working runtime environment.

For the gameplay-feedback recheck, exercise FP↔2D↔battle and private-chat return
without another manual tab/screen tap; drag pitch at near/far distances; toggle
labels and World tabs; tap overlapping participants and speech/battle markers;
observe changing HP while the scene stays visible; and let local wilds greet or
attack without an Accept prompt. Check preparation failure, changed active
partners, repeated outcomes, stale/out-of-range fixes, and background/replay.
Friendly sparring and voluntary team intervention retain invitation/selection.
Conversation taps now open the normal full-screen chat layout, including group
authors/participants when applicable. Verify Radar→chat→Radar and chat→battle
return, GPS freshness/range changes while typing, reversed drag direction,
names/distances fully above sprite bounds at large font sizes, and the screenshot
case of a structured reply with invalid intent metadata: spoken prose should
remain visible, with no JSON envelope or executable invalid intent.
In FP, confirm sprites remain front-facing while moving/turning the view and
while chatting/battling, with labels and touch targets aligned to the billboard.
Verify wild-initiated 1:1 chat and Talk show the same greeting/history/trust,
then offer the screenshot's name/obedience wager. After the Digimon accepts,
combat should start without another acceptance prompt. Check both win branches:
friendly defeats retain the wild; a Hackmon win asks for the promised name, and
a player win acknowledges his promise. After combat, return to the same chat,
verify the result/reaction appears once, and verify later private conversations
can recall the battle without leaking its wager into other NPCs' public prompts.

### Device persistence suite

Connect an Android device through ADB, then run:

```powershell
.\scripts\test-individuals-android.ps1
```

The script verifies the APK package names and installs only
`com.github.nacabaro.vbhelper.integritycheck` and its test APK. It does not update
the primary application. Tests use a separate database and cover storage,
World recruitment, migration, replay protection, interrupted transfers,
VB/BE export profiles and the current VitalWear converter interfaces.

NFC protocol tests use an in-memory transport with the production encryption,
checksums and packet handling. They do not replace validation with a physical
Digivice V. The JVM Android logger shim is confined to test source sets.

Physical-watch identity protection allows only one outstanding exported individual
per watch UID. A second send is rejected before character data is written. Returning
the first individual releases that restriction; retrying the same transfer remains
allowed. Separate watches can have independent outstanding transfers.

Unknown/erased return tokens with an outstanding export, contradictory tokens and
ambiguous legacy backup occupants are rejected before import acknowledgement.
Neither choosing a DIM manually nor matching species/stats bypasses this check.
An exact already-committed receive can be retried without duplicating or resetting
the saved individual. Historical unresolved transfers are preserved for deliberate
recovery, not automatically reassigned to a similar-looking individual. This is a
conservative software safeguard, not a claim that backup-slot firmware behavior
has been verified or corrected.

## Debug Radar spawn picker

Tap the debug spawn button to keep its random behavior; hold it to choose any
loaded card character. The searchable picker includes undiscovered characters
and cards excluded from automatic World spawning, identifies variants by their
local character ID, and displays each sprite with its species and card name.
An explicit selection is revalidated inside the spawn transaction; a removed
character does not fall back to a random spawn.

```powershell
py -3 -B scripts/test-debug-spawn-db.py
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.world.WorldSpawnSelectorTest" --tests "com.github.nacabaro.vbhelper.screens.worldScreen.*" :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

The three host SQLite checks execute the production picker/automatic-spawn DAO
queries for disabled cards, absent discovery/profile rows, manual names, sprite
isolation, duplicate card slots, and card deletion. `DebugWorldSpawnTest` covers
real Room spawning, individual/personality/relationship creation, the 20 m radius,
random-pool eligibility, and stale selection rejection. `DebugSpawnControlsTest`
covers tap versus hold, disabled gestures, and searching/selecting a card variant.
Android tests require a device or emulator to execute; compilation alone does
not establish touch, layout, or persistence runtime acceptance.

## Optional private fixtures

Three official-APK integration tests are reported as skipped when these private
files are absent from `app/src/test/resources/com/github/nacabaro/vbhelper/source/`:

- `com.bandai.vitalbraceletarena.apk`
- `classes.dex` extracted from that APK

Synthetic negative-key validation and APK structure tests run without those files.
Do not commit the official APK or its extracted data.

DIM/BEM integration tests accept local images through environment variables:

```powershell
$env:VBHELPER_TEST_DIM = 'C:/path/to/custom-dim.bin'
$env:VBHELPER_TEST_BEM = 'C:/path/to/bem.bin'
.\gradlew.bat :vb-dim-reader:test --rerun-tasks --offline --console=plain
```

Without environment variables they look for `original.bin` and
`BEM_CARD_IMAGE.bin` in the DIM module, and report absent fixtures as skipped.
An explicitly configured path that does not exist fails the test. Sprite changes
are generated in memory; the source images are never modified.

## Lint scope

The pre-existing `app/lint-baseline.xml` remains unchanged. A successful lint run
means there are no errors outside that baseline; it does not mean all historical
warnings or baseline findings have been fixed. Dependency upgrades, resource
removal, and persistence changes should be evaluated separately rather than made
solely to remove advisory warnings.

## Blast Evolution / Jogress cinematics

The snapshot-owned finisher clock freezes ordinary combat while running focus,
transform, reveal, charge, release, impact, aftermath, and restoration. FORM lasts
7,400ms and JOGRESS 7,900ms. Core commits damage once at IMPACT; the renderer's
150ms hit-stop never changes damage or the combat clock. Killing blows defer the
terminal result until restoration. Jogress suspends both originals and presents
one retained result instance; their actions/queues resume afterward.

```powershell
.\gradlew.bat :app:compileIntegrityCheckKotlin :app:mergeIntegrityCheckResources :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.*" --tests "com.github.nacabaro.vbhelper.rendering.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" :app:compileIntegrityCheckAndroidTestKotlin :app:assembleIntegrityCheck :app:lintIntegrityCheck --offline --console=plain --max-workers=2
```

`BattleFinisherTest` covers clock isolation, exactly-once damage/resource accounting,
partner suspension, terminal aftermath, cancellation, later windows, and frame-chunk
determinism. `BattleCinematicTest` covers fusion visibility through the entire attack,
side-by-side staging, reduced motion, aspect-aware framing, restored camera composition,
and visual hit-stop. Profile/transition/UI tests cover both equipped slots, validated
move aliases, result titles, alpha-derived blend silhouettes, and compact layouts.

Latest combined check: **295 JVM cases, zero failures/errors/skips** across the four
filtered packages above. Kotlin/resource compilation, Android-test compilation,
integrity-check APK assembly, and lint passed with the existing baseline. Cleanup
regressions also verify that cinematic impacts/misses do not replay on combat resume.

Runtime effects are original procedural planes and imported DIM attack artwork.
Base/result/transition actors and effects are prepared before readiness and retained
through each sequence; transformations toggle visibility instead of recreating GLBs.
Sound/haptics honor system and saved audio settings, pause/background, and consumed
phase guards. No additional distributed character images or music are introduced.

Native acceptance is pending: ADB reports no connected device and no AVD is configured.
When available, capture training and Radar in portrait/landscape, testing FORM,
JOGRESS, non-fusion DUO, a killing blow, background/rotation during charge, reduced
motion, and repeated activations. Verify no source body/shadow remains after fusion,
result attack art stays visible through aftermath, camera bounds/occlusion, audio,
frame time, and stable GPU entity/material counts. Compilation/unit tests do not
establish those visual or hardware-performance results.
