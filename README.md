# Tamers Lab

Tamers Lab is a community Android app for interacting with the Vital Bracelet family
(VB, VH, VBC, VBBE) and for reading, reviewing, and carefully transferring Digimon game
saves. It is a fork of [`lightheel/vbhelper`](https://github.com/lightheel/vbhelper),
currently being refactored for Alpha 0.7.0.

> **Verification status (2026-10-31):** the post-Phase-6 verification passed for this
> working tree: **1,157 unit tests** (7 skipped, 0 failures), debug + R8 release
> assembly, `:app:lint` with **0 errors**, **281/281** connected instrumentation tests on
> the SM-A346M (API 35), and install/cold-launch smoke for the debug and debug-signed
> release builds. Baseline profiles were regenerated on the device, and the named
> coverage classes are above 80% line coverage. Device benchmarks now record a
> **875 ms cold-start TTID median** and a battle frame-CPU P95 of 13.3 ms; Room query
> plans, the 30-minute soak, and thermal-downgrade validation remain **unmeasured**.
> Nothing has been committed or published. Evidence:
> [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md) and
> [docs/performance/BASELINE.md](docs/performance/BASELINE.md).

## App identity

| Property | Value |
|----------|-------|
| Product name / launcher label | **Tamers Lab** |
| Kotlin namespace | `com.tamerslab` |
| `applicationId` | `com.tamerslab.vbhelper` (own app identity since Alpha 0.7.0 — [ADR 0006](docs/adr/0006-own-application-id.md), superseding [ADR 0001](docs/adr/0001-keep-application-id.md)) |
| Application class | `com.tamerslab.di.TamersLabApplication` |
| Compose theme | `TamersLabTheme` |
| Target release | Alpha 0.7.0 (`versionName` in [app/build.gradle.kts](app/build.gradle.kts)) |
| SDKs | `compileSdk 37`, `minSdk 28`, `targetSdk 37` |
| Room schema | 55 |

The namespace and the installed package id intentionally differ. This build is its own app: it
does not install as, or receive updates as, VBHelper, and VBHelper releases are not updates of
it. Data from an install that used the old `com.github.nacabaro.vbhelper` id is not inherited;
move it with `Settings > Export database` then `Import database` ([ADR 0006](docs/adr/0006-own-application-id.md)).

## Modules

| Module | Purpose |
|--------|---------|
| [`app/`](app/README.md) | Android application shell: Compose UI, Room, Koin, navigation, feature packages |
| [`hardware/`](hardware/README.md) | NFC Android library: watch transport, translators, character models |
| [`hardware-dim/`](hardware-dim/README.md) | Pure Kotlin/JVM DiM/BEM card parser and writer (Java 11) |
| [`game-save-core/`](game-save-core/README.md) | Pure Kotlin/JVM save readers, planners, conversion, `SaveInspector` CLI |
| [`companion-protocol/`](companion-protocol/README.md) | JSON/Gson pairing and pinned-TLS listing protocol |
| [`pc-save-companion/`](pc-save-companion/README.md) | Read-only local-network desktop companion |
| `baselineprofile/` | Device-gated Baseline Profile generator (not shipped) |
| `benchmark/` | Device-gated macrobenchmark harness (not shipped) |

`app` depends on `hardware`, `game-save-core`, and `companion-protocol`; libraries never
depend on `app`. `pc-save-companion` is a desktop distribution, not an app dependency.

## Documentation

- [Documentation index](docs/INDEX.md) — architecture, refactor, performance, testing,
  release, and research links
- [Architecture decisions](docs/adr/) — applicationId, hardware split, Koin, battle
  domain, performance budgets
- [Refactoring plan](REFACTORING_PLAN.md) — phases, module structure, dependency rules
- [Performance baseline](docs/performance/BASELINE.md) — budgets, measurement
  procedures, honest status
- [Contributing](CONTRIBUTING.md) — workflow, commands, flags, fixture policy
- [Changelog](CHANGELOG.md) — Alpha 0.7.0 entry

## Current state of the project

The project is still under development. The current implementation uses Room schema
**55**, with preserving migrations, a shared DIM-style Home for card-independent
partners, linked-save discovery, and a read-only local-network PC companion. See
[TESTING.md](TESTING.md) for the current testing guide and commands; the 200 KB
chronological regression log is archived at
[docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md)
and is history, not the current result.

### Multi-game Transfer and local PC companion

**Current development focus:** actual World DS / first Story moves between a save and
the vault. World DS, Dawn/Dusk and Lost Evolution now read, import, move out and send back;
Lost Evolution is the newest adapter (see [LOST_EVOLUTION_TRANSFER_PLAN.md](LOST_EVOLUTION_TRANSFER_PLAN.md)).
Time Stranger transfer development is paused.
The target Transfer flow uses PKForge's game-first organization, adapted into one
origin list with DS launcher icons, Vital Bracelet and other origins together.
Open a game, tap a sprite-matched individual, then use Move to Storage in its information
panel; the supported move must preserve nickname and progress without intermediate
review/mapping/materialization steps. This final flow is still being implemented.
DS format research uses
[DigimonNDSRomEditor](https://github.com/joaomlsantos/DigimonNDSRomEditor) as the
Dawn/Dusk reference; see [the DS notes](game-save-core/WORLD_DS_RESEARCH.md).

**Current prototype:** Home's top-left button opens a Games library. Open a game tile to
see its individuals, select Digimon in the grid, and use the selection action. **In
game** and **Tamers Lab Storage** switch the two sides on a phone; wider windows show
both. Selection is cleared when the observed save revision changes. Current imports are
explicitly labelled **Copy to Storage** because they leave the source intact.

Transfers go **game → Tamers Lab Storage** or **Tamers Lab Storage → game**. A stored
individual can go to any supported game that supports its species, independently of
its original game, once that game's conversion/writer capability is validated. Opening
a receiving game fixes the destination for its Storage actions; there is no redundant
destination chooser. Filename-based library grouping never enables an unsupported
reader or claims species compatibility. Game-cover credits are bundled in
[transfer_game_art_credits.md](app/src/main/assets/transfer_game_art_credits.md).

The latest origin-list, DS-launcher-icon and one-action move requirements supersede
this prototype's cover grid and copy-review UI; see sections 2, 4 and 20 of the
[implementation plan](MULTI_GAME_TRANSFER_PLAN.md).

The top-right menu keeps secondary workflows together:
- **Manage saves:** files/folders/PCs, access repair, copy review and saved import reviews.
- **Storage tools:** native mapping, app creation, source history, native-change review
  and destination previews. Stored tiles also open their individual actions directly.
- **Vital Bracelet:** outgoing-partner selection, bracelet preparation and NFC transfer.

Storage shortcuts open the Storage side with the relevant partner selected.

Transfer links local files/folders and paired PCs, refreshes persisted saves and
retains cached observations on failure. Start the Windows companion using
[these setup instructions](pc-save-companion/README.md), then choose **Transfer →
Connect local PC** on the phone. The distribution is built with
`:pc-save-companion:installDist` / `:pc-save-companion:distZip`.

Time Stranger supports inspection and **provisional party/bank snapshot copies** into
Storage. Copies receive app-owned permanent identities and receipts; the game save
stays intact. Possible matches to prior copies require identity review. Farm, boundary
and tail records remain review-only. This does not establish verified game-wide native
identity or enable game writes and managed moves. Game-specific progression conversion
still needs validation.

**Digimon World DS (USA), DraStic raw 64 KiB `.sav` files are readable directly on the
phone.** In **Transfer → Game saves → Link save file**, select the regular `.sav` from
an accessible phone folder. The roster opens after reading and shows stored names,
level, native HP/MP/combat stats, aptitude and eight separate EXP channels. Linked
folders can discover these `.sav` files too. The reader was checked against the
user-supplied newly created DraStic save. Eligible **party Digimon can be copied into
Storage provisionally**, then given a reviewed species/form and an APP representation
using the shared DIM-style Home. Choose the resulting app partner to use Home. Native
stats/EXP and original records remain separate from app starting values; native
personality stays absent and the app initializes its own personality once. Native
checksums, global identity and native writes remain unvalidated.

For saves with multiple populated copies, use **Review save copy** in Transfer. That
authority choice applies only to the exact source revision; a changed file requires
another review. Retrying an import does not create another individual; later native
observations require reviewed refresh. World DS IDs are source-local, so equal IDs from
independent linked saves are not automatically merged.

The companion also has an offline **Digimon World DS signature-research inspector** for
raw backup candidates and DeSmuME version-0 containers. It reports structural candidate
blocks and native roster-reference counts. The phone's observed DraStic profile does not
require that research signature; it uses the registered read-only reader. See
[the research notes](game-save-core/WORLD_DS_RESEARCH.md) for commands and evidence.

Transfer → Storage transfers → Game archives → **Preview game destination** evaluates a
selected linked save and Party/Bank placement using a fresh read. The planning report
distinguishes same-format native return from cross-format conversion, flags possible
existing records, and reports unvalidated/unmapped fields. It also shows recorded app
progress in its own units, whether a retained native baseline matches the destination
format, pending native-change reviews and destination records changed under the same
identity evidence. Up to 16 partners can be included for evaluation against the same
fresh destination read, with shared restrictions and one conflict warning when selected
partners share candidate identity evidence. It does not execute a transfer. Farm
transfer is outside scope and is not a prerequisite for party/bank support.

The desktop inspector can rehearse the planned commit pipeline on copies only:
`--write-rehearsal <save-file> <output-dir>` verifies a fresh backup, rechecks the
source right before writing, refuses output inside the save folder, and replays the
commit journal through readback and re-parse verification. Encrypted Time Stranger
saves must additionally re-encode byte-identically (verified for the reference save).
`--edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file>` builds one
stored-name candidate for the Time Stranger adapter with the exact semantics observed
in the game's own rename: the name bytes plus a terminator overwrite the record window
and nothing else changes. Its output was validated against the game's own rename sample
(identical except volatile time/live-state fields), and a candidate built from an
authorized slot loaded normally in the retail game (user-run swap, 2026-10-16).
`--edit-activate <save-file> <output-dir> <inactive:N>` is the staged experimental
counterpart: one stored flag byte on the first inactive bank-tail record, desk-verified
to differ by exactly that byte, testing whether the flag alone controls ownership
(in-game acceptance pending). The tool writes no game file and no writer capability is
enabled; see [the format-research notes](game-save-core/FORMAT_RESEARCH.md).

**Review native changes** compares archived native refreshes in their original units:
permanent parameter/EXP-channel changes, temporary HP/MP, personal edits, movement,
form/identity changes and unmapped bytes. This read-only review preserves pending
changes; it does not award growth or establish a game-to-game conversion policy.

The universal progression core retains native baselines and tracks actual changes
separately from app/DIM stat projections. Synthetic tests cover lossless zero-progress
A/B/C/A returns despite lossy intermediate scales; real-game round trips remain subject
to adapter/writer acceptance. See [the implementation plan](MULTI_GAME_TRANSFER_PLAN.md).

## Features

As of now, the project allows you to read characters, view character stats, and send
them back to your watch.

You can also apply items to the characters read, such as special missions, or change
timers, and store characters in the storage section.

You also earn new items every time an item such as a special mission (VB only) or a
character completes an in-app adventure mission.

The app also comes with a dex that updates every time a new character is added, and
allows you to see evolution requirements and the current adventure stage on the watch.

### Bundled Dot sprites

Open **Settings → Species and sprites**, select a species to edit, choose
**Choose sprite → Dot sprites**, select an appearance, then **Save** the species.
The alternatives work offline and include artist/device credits. The selected sprite
set is used within the chosen artwork priority, including portraits, Arena fighters
and result forms.

### Digimon graphics and universal participation

**Settings → Digimon graphics** offers **VB first** (loaded VB sprites, then the VB
frames bundled with the app, then Dot fallback) and **Dot first** (Dot sprites, then
loaded VB fallback). This choice is cosmetic. There are no VB/Expanded gameplay modes, source restrictions or artwork
participation requirements. Missing art uses a neutral placeholder. Artwork changes do
not clear the active partner, pause activities, change stats or interrupt play.
Existing source-mode preferences migrate to the corresponding artwork priority;
legacy mode pauses resume without replaying paused activity time.

The current source includes catalog-backed encounters, quest targets, recruitment
and a high-trust first-partner World encounter. Scripted, AI-independent onboarding and
complete card-free gameplay acceptance remain pending. Sending a catalog partner to a
physical watch requires assigning an exact loaded DIM/BEM slot of the same species and
form. That association enforces normal card stat rules, suppressing extra
app/cross-product bonuses without deleting growth. Removing it restores the balanced
app stats. Known incoming device fields and outgoing local representation state are
durably archived with the permanent individual; transfer receipt consumption does not
delete those baselines. Physical conversion/round-trip acceptance and product-specific
growth equivalence remain adapter work.

Additional game/VPet readers, verified progression mappings, writers and managed
multi-game moves remain format-specific work. See
[EXPANDED_MODE_IMPLEMENTATION.md](EXPANDED_MODE_IMPLEMENTATION.md).

## How to set up

1. *(Optional — only needed if a future VB Arena release rotates the connection keys.)*
   Download the latest version for VB Arena APK from a trustworthy source. If your
   download is a standalone APK, continue to step 2. Otherwise, if your download is an
   XAPK, do the following:

    1. Using your phone file manager, rename the XAPK file to ZIP, and extract its
       contents. You can also do this with any other device, such as Windows, macOS and
       Linux.

    2. Once the files are extracted, look for the VB arena APK. Copy it somewhere else,
       you will need it.

2. Install an APK release for Tamers Lab. Download the latest release from this project's
   GitHub releases page
   ([caiocerqueira023-svg/vbhelper releases](https://github.com/caiocerqueira023-svg/vbhelper/releases))
   and install its APK.

    This is its own app: it installs next to VBHelper instead of updating it, and VBHelper
    releases are not updates of it ([ADR 0006](docs/adr/0006-own-application-id.md)). For an
    existing installation of *this* app, upgrade in place to retain your collection;
    uninstalling or clearing application data discards local records.

    Coming from the old `com.github.nacabaro.vbhelper` build (VBHelper or an earlier Tamers
    Lab build), the new install starts empty. Open `Settings → Export database` in the old
    app, then `Settings → Import database` in this one. Cards live in that database, so they
    move with it; linked `.sav` saves are re-linked; watch secrets need no migration.

3. The connection secrets that let the app talk to the watch are **built in**. They were
   extracted from the official VB Arena 2.1.0 release and the unit tests keep them honest
   by regenerating the known watch test passwords, so a fresh install can scan a watch
   immediately — no VB Arena APK needs to be downloaded or imported.

    Re-importing connection data is a developer fallback for a later Arena release that
    rotates the keys: enable `Debug` in Settings, then use `Watch connection → Import watch
    connection data` and pick the APK obtained in step 1. Imported data still wins over the
    built-in values.

4. Import cards for bracelet interaction and optional exact card-stat association. App
   partners can participate without a card; the bundled Dot library is available
   offline. To import cards, do the following.

    1. Using your own DiM/BEm cards, dump the cards to your device. You can get an
       in-depth tutorial [here](http://mrblinky.net/digimon/vb/dimcardtool/dimcardtool.html).
       You can download the dump tool from
       [here](http://mrblinky.net/digimon/vb/dimcardtool/).

    2. Once installed the tool and drivers, open the tool, connect your DiM/BEm reader
       hardware to your computer and click on Read card.

    3. Transfer the resulting file to your mobile device. You can put them anywhere, as
       long as they are accessible. The recommendation is a folder called `Cards` in
       your `Internal storage` or `SD Card`.

    4. In the app, click on import card. Next choose the BIN file corresponding to the
       card you want to import.

    **Note: if you do not import the card, whenever you attempt to read a character from
    the watch, the character you read will get deleted.**

5. The app will now be ready to be used.

## Building, testing, and lint

Use JDK 25 and the bundled Gradle wrapper from the repository root:

```powershell
# Build the debug APK
.\gradlew.bat :app:assembleDebug

# Android app unit tests (integrityCheck test build type)
.\gradlew.bat :app:testIntegrityCheckUnitTest

# All JVM/library unit tests together
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test :hardware-dim:test :hardware:testDebugUnitTest :app:testIntegrityCheckUnitTest

# Lint
.\gradlew.bat :app:lint

# Device-gated checks
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest
.\gradlew.bat :app:generateBaselineProfile
```

Optional coverage and desktop distribution tasks:

```powershell
.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport
.\gradlew.bat :pc-save-companion:installDist
.\gradlew.bat :pc-save-companion:distZip
```

Coverage is recorded for the named classes and game-save-core, but no threshold is
enforced. Baseline-profile generation requires a connected device/emulator and lives in
`:baselineprofile`; the macrobenchmarks live in the separate `:benchmark` device-only
module so profile generation does not run benchmark launch loops (see
[TESTING.md](TESTING.md)). `:app:lint` passed with zero errors on 2026-10-31; the
historical baseline is in [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md).

### Developer setup

- **JDK 25** for the Gradle launcher/daemon (the Android and JVM modules still target
  Java 11 bytecode).
- **Android SDK 37** with `platforms;android-37.0` and `build-tools;36.0.0`.
- **Windows memory settings.** The Gradle daemon can fail with a native-memory error
  when system commit is exhausted even while the Java heap has free space. Keep at
  least 8 GB of pagefile/commit headroom, close unused Gradle/Kotlin daemons, and use the
  bounded settings in [gradle.properties](gradle.properties)
  (`org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8`,
  `org.gradle.workers.max=1`, `org.gradle.parallel=false`,
  `kotlin.daemon.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m`).
- The historical `hs_err_pid20620.log` was a Gradle daemon failure on the dev machine,
  not an app or Filament out-of-memory event. Do not repeat that claim.

## Planned features

- Online battles, undergoing development by `lightheel`.
- VitalWear compatibility, undergoing development by `cfogrady`.
- Support for multiple languages (Japanese and Brazilian Portuguese string resources
  are in place for the shipped UI; full localization is not complete).
- Database backup/restore.

## Credits

- `cyanic` for helping us understand more about the VB connection protocol.
- `cfogrady` for making both [`VB-DIM-Reader`](https://github.com/cfogrady/VB-DIM-Reader)
  and [`lib-vb-nfc`](https://github.com/cfogrady/lib-vb-nfc).
- `lightheel` for working on the online component in the application, both server and
  battle client.
- `shvstrz` for the app icon.
