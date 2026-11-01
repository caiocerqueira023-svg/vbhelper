# game-save-core (`:game-save-core`)

Pure Kotlin/JVM library for reading, validating, planning, and (in limited, opt-in
paths) writing Digimon game saves. The Android app, the desktop `SaveInspector` CLI, and
the `pc-save-companion` distribution all use the same readers and planners from this
module.

| Property | Value |
|----------|-------|
| Gradle path | `:game-save-core` |
| Plugins | `org.jetbrains.kotlin.jvm`, `application`, `jacoco` |
| Package | `com.tamerslab.gamesave` |
| Java/Kotlin target | Java 11 |
| Dependencies | Kotlin stdlib only (JUnit 4 for tests); no Android, Room, Gson, or app imports |
| CLI main class | `com.tamerslab.gamesave.SaveInspectorKt` |

## Structure

| File / area | Role |
|-------------|------|
| `SaveReaderRegistry.kt` | Reader selection and the bounded read boundary; `read(InputStream)` and `capture(InputStream)` produce a `SaveObservation` plus optional source bytes and a SHA-256 `contentRevision`. |
| `SaveModels.kt` | `SaveObservation`, `ObservedDigimon`, `SaveCapabilities`, `SaveContainer`, `RecordMembership`, `RecordIssue`, `ObservationNotice`, `SaveReadFailure`, `CapturedSave`. |
| `TimeStrangerSaveReader.kt` | Time Stranger reader (encrypted document format). |
| `NdsStorySaveReader.kt`, `NdsStoryProfile.kt`, `NdsStoryRecordCodec.kt`, `NdsStorySaveEditor.kt` | NDS Story engine-family readers/codecs/editor; World DS is the first registered profile. |
| `WorldDsSaveReader.kt`, `WorldDsRecordCodec.kt`, `WorldDsCopySelection.kt`, `WorldDsPartyEdits.kt` | Digimon World DS facades and the audited party-edit byte windows. |
| `DestinationPreviewPlanner.kt`, `SnapshotImportPlanner.kt` | Destination evaluation and snapshot-copy planning. |
| `TransferCommit.kt`, `UniversalProgress.kt` | Commit journal/rehearsal and the lossless native-progress model. |
| `NativeProgressReview.kt`, `NativeRefreshPlanner.kt`, `IdentityEvidenceComparison.kt` | Read-only native-change review and identity evidence comparison. |
| `WorldDsResearchInspector.kt` | Structural research inspector (`game` counts only, no roster names). |

Reads are content-led and tolerant; writes are independent and revision-bound. The
binding rules are in [SAVE_READING_STANDARD.md](SAVE_READING_STANDARD.md). A structurally
recognizable save is reported with review notices rather than rejected, while a write
path re-derives its own observations and refuses records with review issues.

## `SaveInspector` CLI

`SaveInspector.kt` is the desktop entry point (`application` plugin). It prints counts
and capability flags, not private names or candidate ids.

```powershell
.\gradlew.bat :game-save-core:run --args="C:\path\to\save.bin"
.\gradlew.bat :game-save-core:run --args="--inspect-world-ds C:\path\to\world-ds.sav"
.\gradlew.bat :game-save-core:run --args="--compare before.bin after.bin"
.\gradlew.bat :game-save-core:run --args="--preview-destination origin.bin destination.bin"
```

Additional research/rehearsal modes: `--inspect-nds-story`, `--write-rehearsal`,
`--edit-rename`, `--edit-activate`. They operate on copies and never address the source
file in place. See [FORMAT_RESEARCH.md](FORMAT_RESEARCH.md) and
[WORLD_DS_RESEARCH.md](WORLD_DS_RESEARCH.md) for the evidence behind them.

## Build and test

```powershell
.\gradlew.bat :game-save-core:test
.\gradlew.bat :game-save-core:jacocoTestReport
# build/reports/jacoco/jacocoTestReport/html/index.html
```

`test` is finalized by `jacocoTestReport` in the module build; the standalone task is
useful when collecting coverage in CI. Coverage is configured but **not measured or
threshold-enforced** for Alpha 0.7.0. The budget target is 80% on the named domain
classes (`WorldDsRecordCodec`, `NdsStoryRecordCodec`, `WorldDsPartyEdits`,
`BattleSimulator`, `TamerTrainerController`, `PersonalityBattleProfile`); see
[ADR 0005](../docs/adr/0005-performance-budgets.md).

## Fixtures

Tests use synthetic saves. Real game saves are private native data and are not
committed. The World DS research notes record which observations came from a
user-supplied save without committing its bytes. Do not add a save, ROM, or captured
native record to the repository as a fixture.

## Status

The module compiles independently of Android and is the shared save layer for the app
and the PC companion. The current test commands and verified state are in
[TESTING.md](../TESTING.md); older suite counts in the archived
[HISTORICAL_REGRESSION_LOG.md](../docs/testing/HISTORICAL_REGRESSION_LOG.md) and
[TARGETS.md](../docs/refactor/TARGETS.md) are historical.
