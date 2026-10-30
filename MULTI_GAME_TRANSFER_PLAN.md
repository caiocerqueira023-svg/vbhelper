# Multi-Game Digimon Vault and Transfer Plan

## Binding product contract — clarified 2026-10-17

This contract supersedes earlier mode, archive-first presentation and base-only DIM
wording throughout this document. PKVault/PKForge are interaction references for a
universal Digimon home spanning games and V-Pets, not limits on the destination set.

- One continuing individual may travel through arbitrary supported routes such as
  `Game A -> Storage -> V-Pet -> Storage -> Game B -> Storage -> Game A`.
  Preserve identity, nickname, personality, history, every native representation and
  all information required to track and convert its growth. A destination lacking a
  field cannot delete that information from VBHelper.
- Storage/Home/care/battles use the complete DIM-style VBHelper presentation and
  balanced VBHelper parameters. Native levels, EXP, stats and product-specific facts
  belong in the individual's information under that product's Transfer entry.
- Actual stats and progression earned across products influence the balanced app
  representation through versioned conversion policies and durable checkpoints.
  Neither repeated transfer nor rounding, capping, sprite selection or card assignment
  creates progress. Unknown conversion data remains retained until supported.
- A matching loaded DIM/BEM is required for Vital Bracelet export. While associated,
  that card's species/stat rules and normal device modifiers apply exclusively;
  extra cross-product/app stat bonuses are suppressed, not erased. Removing the
  association restores the balanced app projection from preserved data.
- **There are no VB/Expanded gameplay modes or source/artwork participation gates.**
  Every stored Digimon remains usable regardless of origin, loaded card or artwork.
  Ordinary ownership/transfer locks and actual activity rules still apply.
- The sole replacement choice is cosmetic: **VB first** uses loaded VB artwork then
  Dot fallback; **Dot first** uses Dot artwork then loaded VB fallback. Missing art
  uses a neutral placeholder and cannot disable a Digimon. Changing this preference
  cannot clear the active partner, pause activities, alter stats or invalidate play.
- Native adapters still establish concrete read/write/species/conversion support.
  Destination support is independent of the individual's origin. Simple one-action
  moves must preserve data internally; unfinished writers are not working transfers.

**Active priority (2026-10-17, user-directed):** Time Stranger transfer development is
paused. The next native-move target is **Digimon World DS / the first Digimon Story**,
followed by Dawn/Dusk and individually validated later DS titles. Actual moves mean
durable vault retention followed by source removal, and verified native insertion on
return. Existing snapshot-copy support is distinct from that goal.

**Dawn/Dusk step opened (2026-10-24, user-directed):** the next step is planned in
[DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md): extend the existing World DS
feature to Digimon World Dawn (USA `A3VE`) and Dusk (USA `A6RE`) with the same flows and
guarantees, reusing World DS code through a shared NDS Story engine-family layer. Planning
evidence: Dawn/Dusk run on the World DS engine but persist a **different save layout**
(64 KiB image, two copies of `0x7B2C` at `0x0`/`0x7B80`, MD5 header rule, party records
`0x164` bytes at `0x288`), so World DS offsets are not reusable. No production reader or
writer for Dawn/Dusk exists yet; real-save and ROM evidence is required first.

**Definitive Transfer UX (2026-10-17, latest user clarification):** one **list of
origins** containing games, Vital Bracelet and every other supported Digimon origin.
DS games use the **icons shown by the DS launcher**, not cover artwork. Open a game
entry to see its saved individuals with automatically matched species and the app's
normal Digimon sprites. Tap an individual to open its information, then tap **Move to
Storage**: the ordinary supported operation completes directly, preserving nickname,
identity and progress, without an import-preview, mapping or materialization wizard.
The only routes are **origin → VBHelper Storage** and **VBHelper Storage → origin**.
A stored individual may enter **any supported game that supports its species**;
its original game does not restrict its destination. Detailed acceptance is in
section 4; it supersedes earlier game-cover grids and user-facing staged imports.
Home/Storage/Dex/World/More remains the bottom bar; Transfer opens from Home's
upper-left button.

**DS reference baseline:** [joaomlsantos/DigimonNDSRomEditor](https://github.com/joaomlsantos/DigimonNDSRomEditor),
revision `cc3ff499edfd86e382e724d1da7f155b6b830e5f` (GPL-3.0), supplies USA Dawn/Dusk
ROM species, stats, moves, evolution and text research. Those ROM offsets are title-
specific; World DS persisted-save structures additionally use the existing ReGame
Labs evidence. Save integrity and source-removal acceptance need their own validation.
The same author's [Dawn/Dusk research notes](https://github.com/joaomlsantos/Digimon-World-Dawn-Dusk-Research-Notes)
supply the first persisted-save evidence (copy geometry and party-record layout); they are
facts-only references, not a license to reuse code or assets, and every constant still
needs real-save verification. The resulting plan is
[DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md).

**Status:** Implementation in progress at schema 55. Linked sources, provisional Time
Stranger/World DS snapshots, reviewed native refresh, shared Home/materialization,
universal progress receipts/checkpoints and read-only destination planning exist.
Gameplay mode restrictions are removed in favor of artwork priority with mutual
fallback. Exact DIM/BEM association suppresses extra app bonuses without erasing
growth. Incoming/outgoing bracelet representations are retained independently of
consumed transfer receipts. Storage → World DS send now exists end to end for exact
retained records: the core inserts the original 384 bytes into a zeroed destination
slot, the writer journals backup/readback phases with recovery, a presence table marks
away individuals until a verified return, and the Transfer Send list enables the World
DS destination with localized blockers. Game-load/checksum acceptance, the `lastId`
high-water-mark behavior and the game-side party cap remain unverified; Time Stranger
and other send origins remain incomplete, as does real cross-product growth conversion.
See TESTING.md for exact evidence; older checkpoints below are historical.

**Save-reading standard (binding for every game implemented here):** the app must read
any save at any point of its game, named in any way, inside any folder, whenever the file
contains a structurally recognizable save. `game-save-core/SAVE_READING_STANDARD.md`
codifies the contract every current and future game reader must satisfy: content-led
reads with a fail-open `acceptsLength` default, bounded container-family unwrapping
(raw, header-prefixed, footer-annotated, combined), per-record review issues instead of
whole-file rejection for gameplay-state anomalies, write safety independent of read
tolerance (review-flagged records never move or send), revisions hashing the exact
source container, size-first discovery under any name, and one distinct user-visible
reason per `SaveReadFailure`/`RecordIssue` in EN/PT-BR/JA. Each new game's reader
checklist and its tests must demonstrate the standard before that game ships.

**Scope clarification:** Farm Digimon do not need to be transferable. Farm records remain
inspection-only; resolving farm identity/occupancy is not a dependency for party/bank
transfer work. References below to farm transfer experiments are optional research,
not required acceptance gates for the supported transfer scope.

**Transfer navigation target:** Home's upper-left button opens the origin list.
Games, Vital Bracelet and other supported sources are peer entries in this list.
Opening an entry exposes its individuals and their information/action flow. File,
folder and PC connection management, technical reports and recovery remain secondary
tools. The earlier grid/cover prototype and copy reviews are implementation history,
not acceptance of the final user experience. Copies remain labelled as copies while
actual DS writer acceptance is being established.
Storage shortcuts delegate actual transfer operations to this destination with context.
The hub is not a bottom-bar destination (the bottom bar carries Home, Storage, Dex and
World); it is reached through the Home button and contextual chips only (2026-10-16).
On-device verification (2026-10-17): the top-left button opens the hub, the More sheet
lists DigiLine/Quests/Items/Battle/Settings with no Transfer entry, and the Dex tab is
back in the bar; the same pass fixed the hub's empty-state crash (a vertical scroll was
measured under a lazy item's infinite height; `CyberEmptyState` now scrolls only with
bounded height) with the crash log empty through the whole flow.
**Updated:** 2026-10-25. Code-verified (offline compile/tests); see the verified state in section 14, the W1 refactor checkpoint in section 21 and the risk register in section 22.
**Primary surface:** The Android Transfer hub, reached from Home's upper-left button, connected to VBHelper Storage.
**First implemented adapter:** Digimon Story Time Stranger, PC version (further transfer work paused).
**Active native-move adapter:** Digimon World DS, USA/DraStic raw save profile.

## 1. Product vision

VBHelper should become a phone-first companion and central vault for a user's Digimon across supported games, emulators, and Vital Bracelet workflows.

It must also be a **universal intermediary and progress translator**. Progress actually earned in a supported game should become a meaningful, balanced equivalent in VBHelper Storage and subsequently in another supported game, while preserving the individual's original native values and continuing identity.

The user links the save files or save folders for games they are currently playing. The Transfer hub reads those sources, displays their latest persisted rosters, and explains which individuals can move into Storage or back into a selected game. Storage provides continuity of identity, personality, conversations, history, and app-earned progress between source representations.

Time Stranger is the first adapter used to prove this framework. Its PC save format must not dictate the entire product's architecture or require a Windows companion for saves already accessible on the phone.

The central principle is:

> One continuing Digimon individual, independently identified source-native representations, explicit transfer capabilities, and reviewed conversions.

### Example intended experience

1. Link a local DS emulator save folder and select a supported Digimon Story save.
2. Link another local save for Re:Digitize or another supported game.
3. Connect a local PC companion for Time Stranger.
4. Open Transfer, choose a game from the origin list and see the saved individuals
   using VBHelper sprites and automatically resolved species.
5. Tap a Digimon, inspect its information, then tap Move to Storage. A supported
   native move removes it from the source and makes that same individual available in
   ordinary Storage with its nickname and progress preserved.
6. Select that individual in Storage and send it to any supported origin/game that
   supports its species. If a receiving game is already open, use that destination
   directly; otherwise choose a compatible destination once.

This example is a target experience, not a claim that these game formats are currently supported.

## 2. Confirmed user decisions

### Platform and access

- Android is the primary interface.
- Users can link multiple saves and multiple emulator locations.
- Initial save locations are local phone emulator saves and local PC game saves.
- A Windows companion is accepted for PC-side access and eventual save writing.
- **Local-network connection** is the selected PC-companion transport. Android remains the main interface.
- Local phone saves should be processed on-device when their format and document access are supported.
- A source can be a selected file, an authorized folder containing saves, or a local companion connection.

### Transfer routes

- The current route is **Game -> VBHelper Storage -> Game**.
- Vital Bracelet and every other supported origin participate through that same
  Storage intermediary and appear in the same origin list.
- A receiving game must support the individual's species and have validated native
  insertion/conversion support. Originating in that game is **not** a requirement.
- Direct Game A -> Game B is not an operational route; use Storage between origins.
- Actual moves are the active development goal. Snapshot copies are existing
  groundwork and must never be relabelled as completed moves.
- A normal supported deposit uses the information panel's one-action Move to Storage
  control. Internal validation, backups and journaling run as part of that action.

### Definitive interaction and presentation

- One origin **list**, not a game-card grid. Include games, Vital Bracelet/loaded
  device origins and every other supported Digimon origin as peer rows.
- Use actual title/edition-appropriate **DS launcher icons** for DS entries. Use
  suitable native/app icons for other origins. Game cover thumbnails are superseded.
- Game entry -> saved individuals -> tap an individual -> information -> Move to
  Storage. The app resolves species and the shared app sprite automatically.
- Preserve the nickname as the individual's nickname, not just as a string buried in
  its native archive; preserve progress and continuing identity as well.
- No routine preview/confirmation sequence, manual species picker, source-name
  checkbox or separate APP-creation step between Move to Storage and ordinary Storage.
- Opening a receiving game sets the target for Storage-to-game actions. When sending
  directly from Storage, show compatible supported receiving origins once.
- Preserve the app's shared Home, Storage, sprites, saved palettes and Android
  accessibility/Back behavior. PKForge/PKVault supply interaction references, not
  another app's species rules or a separate Digimon identity model.

### Refresh behavior

- Refresh linked sources when entering Transfer and when the app resumes with Transfer active.
- Provide manual refresh and per-source retry.
- Show the last successful read and whether the displayed roster is cached, refreshing, unavailable, or changed.
- Reread the affected source and destination before constructing and executing a transfer.
- Continuous background watching is not part of the initial refresh policy.

### Individual data

- Main first-release priority: identity, current species/form, stored name/nickname, and personality.
- Preserve original game progression, stats, skills, equipment, and verified history for subsequent integration.
- Adapt source values into VBHelper's rules; retain the original values separately.
- Retain all individuals and make stored Digimon usable independently of source/card/artwork.
- A later save containing the same verified individual should offer a reviewed refresh.
- Reviewed refreshes preserve VBHelper chat, history, favorites, notes, and app-earned progress.
- Long-term returns should carry all supported personal changes, forms, training, progression, skills, and equipment.

### Storage/Home parity and stat basis

- A Digimon without an assigned DIM must use the same Home presentation and interactions as a DIM Digimon. A separate reduced card-independent Home is not an acceptable final experience.
- Every Digimon represented in Storage must have the complete applicable in-app stat and gameplay-parameter model available to DIM Digimon, regardless of origin. DIM assignment is not a prerequisite for normal Storage stats, care, progression or Home functionality.
- Preserve the distinction between in-app parameters and source/device provenance; sharing the complete gameplay model does not mean fabricating hardware credentials or claiming app values were measured by a bracelet.
- Origin influences the Digimon's in-app stats through an explicit, balanced and versioned projection. Merely giving every origin the same default stats does not meet the intended result.
- An explicitly assigned DIM/BEM enforces its normal card stat rules and suppresses
  extra app/cross-product bonuses while associated. This configuration choice preserves
  the individual, all earned growth and every native baseline.

### Universal progress translation and round-trip fidelity

- VBHelper is both the central Storage and the progress intermediary between games, not only a viewer or archive.
- Actual progress made in Game A or Game B must be recognized, retained, translated into meaningful Storage progress, and converted into an equivalent supported result in the next game through versioned, game-specific policies.
- Keep initial source competence/stat baselines, in-app display/balance projections and newly earned progress separate. Reimporting a baseline or changing a projection must not award that baseline again as new progress.
- Transfers, import retries, representation creation, Home rendering, DIM assignment and conversion rounding/clamping do not themselves constitute earned progress.
- **Required zero-progress invariant:** for `Game A -> VBHelper Storage -> Game B -> VBHelper Storage -> Game C -> VBHelper Storage -> Game A`, if no progress was made involving the individual during any step, it must be possible to return that individual to Game A with the **same native stats it had when it left Game A**.
- Intermediate games or the app may use different units, ranges, caps and balanced projections. These differences must not destroy the original Game A baseline, fabricate gains, compound rounding loss or prevent the zero-progress return.
- Real gains and supported losses must be applied once through the appropriate translation policy. Unmapped changes must remain preserved and explicitly unresolved rather than being silently treated as zero progress or discarded.

### Game priority

The active DS-first order, superseding the original Time Stranger-first schedule, is:

1. **DS Story games:** World DS, Dawn/Dusk, Lost Evolution, and/or Super Xros Wars.
2. **Re:Digitize / Decode:** PSP Re:Digitize and/or 3DS Decode.
3. **PlayStation World games:** World 1, World 2, and World 3.
4. **Cyber Sleuth / Next Order:** supported editions considered separately.

**Digimon World DS (USA), DraStic raw 64 KiB** is the first native-move target; the supplied fixture already supports reading and provisional party copies. Dawn/Dusk follow with their own save evidence, informed by the requested ROM-editor reference. Exact variants for subsequent titles remain to be selected. Support for a group does not imply support for every title or emulator container in it.

## 3. Reference projects and lessons

The user supplied [PKVault](https://github.com/Chnapy/PKVault) and [PKForge](https://github.com/sofianeelhor/PKForge) as product references.

### PKVault

Useful concepts:

- Multiple configured save locations.
- A centralized bank outside game saves.
- Source-native variants of a continuing individual.
- Explicit conversion between concrete game formats.
- Staged actions followed by saving changes.
- Source refresh and save/collection organization.

VBHelper should give each linked save an explicit identity and authority selection. Several slots, copies, or playthroughs must not be silently merged merely because their contents resemble one another. Filesystem modification time and in-game playtime are useful evidence, not universal definitions of the authoritative save.

### PKForge

Useful concepts:

- Android file/folder linking with persisted document grants.
- Emulator-oriented discovery and manual-file fallback.
- A domain layer separated from the game engine and Android I/O.
- On-device parsing and transfer previews.
- A bank retaining native bytes and exact format identifiers.
- Backup-before-write and operation recovery.

The inspected Android implementation also demonstrates an important boundary: an Android document write is not automatically an atomic filesystem replacement. VBHelper needs provider-aware writing and readback verification rather than assuming every authorized document behaves like a normal local file.

### Applying the references

Borrow the source-linking, vault, representation, conversion-preview, and recovery concepts. Digimon needs its own format codecs, identity rules, evolution semantics, conversion rules, and validation evidence. PKHeX's Pokemon-specific engines and legality data do not establish Digimon compatibility.

The referenced projects are GPLv3-family projects. Any later proposal to reuse their code or libraries needs to account for those licenses; the plan does not assume they are drop-in dependencies.

## 4. Transfer-tab experience

The current navigation has an existing `NavigationItems.Scan` route and watch-oriented `ScanScreen`. The planned Transfer hub should integrate those watch actions alongside linked game sources, with any route migration preserving character-specific watch-send navigation.

### 4.1 Unified origin list

This is the main Transfer screen. Render a vertical list of origins, with one row per
game/device/origin and its recognizable icon. Games and Vital Bracelet are siblings;
the bracelet must not be isolated in a separate main section or bottom action while
games alone occupy the list. Other supported origins join this same structure.

DS rows use their **Nintendo DS menu/banner launch icons**, rendered crisply with
their native proportions. Source icons from verified title/edition metadata or a
user-authorized ROM banner when available; do not require users to supply a ROM merely
to use an already-supported save. Retain icon provenance. A filename hint may help
organize a linked file but cannot establish game recognition or species compatibility.

Tapping a game opens its persisted roster. With one linked save, open it directly;
ask which save only when that game actually has several possible saves/playthroughs.
Keep connection repair, discovery and technical source metadata out of the default
individual-browsing path.

#### Secondary source management

Each source entry should show:

- User-chosen label, such as "Dusk - main playthrough".
- Detected game, edition/region when known, and format profile.
- Emulator or PC provider label when supplied or detected.
- Save slot/playthrough association.
- Last successful read time and refresh status.
- Observed roster count with any unresolved-record count distinguished.
- Supported operations: view, snapshot import, deposit/move-out, insert/return, or export-file fallback.
- A relink/retry action when access is unavailable.

Users should be able to add a file, add a folder, connect a PC source, refresh, rename a source, choose an authoritative slot, and unlink a source without deleting its imported individuals or their provenance.

One granted folder may discover several saves. A single logical save may depend on several files. A shared memory card may contain saves for several games. These relationships must be represented explicitly.

### 4.2 Source roster

Opening a source displays its observed native organization: party, bank/boxes, farm, or the equivalent verified containers for that game.

Display individuals with **the app's Digimon sprites**, matched automatically from
the verified source game's species/form IDs into the canonical app catalog. Matching
is scoped by game/edition/region/profile; another game's numeric ID or the nickname
is not a species crosswalk. Respect normal app sprite preferences and cache/decode
artwork without creating gameplay database rows just to browse a save.

Tap a Digimon to open its information directly. Multi-select or bulk actions may be
additional capabilities, but selecting a checkbox before opening information is not
the ordinary single-individual flow.

Each individual should expose:

- Current species/form and stored name.
- Native level/personality where decoded.
- Source location.
- Whether it is already linked to a known Storage individual.
- Transfer-in status and the reason for any restriction.
- Identity ambiguity or pending-transfer status.

Viewing a roster does not automatically create an owned Storage partner, generate a personality, award discoveries, or advance quests. Observation and acquisition are different operations.

The information surface shows the stored nickname, matched species/form, sprite,
native level and supported native parameters/progress in their own units. Its primary
supported deposit action is **Move to Storage**. One tap runs the native move pipeline
and makes the continuing individual usable through ordinary Storage/Home immediately
after verified persistence. Do not make users separately import an archive, map its
species, choose whether to keep the nickname, or create an APP representation.

If species/form, ownership, format or writer support cannot be established, preserve
the observation and show a concise relevant restriction in the information surface.
Do not silently guess a match or present routine manual mapping as the finished flow.
Farm individuals remain inspection-only by the existing scope decision.

### 4.3 Storage-to-game selection

Selecting a Storage individual and a destination should evaluate the actual pair, rather than marking that individual globally "transferable".

Every locally owned individual may be evaluated against **any supported receiving
game that supports its current species/form**, irrespective of original game or device.
Use a validated species availability/crosswalk plus per-game encoding, capacity and
conversion rules; matching a display name alone is insufficient. Retain all original
representations and app-earned progress with the same permanent individual.

When the user started inside a game's entry, that game/save already supplies the
destination. Otherwise choose a compatible receiving origin from Storage. Do not add
an arbitrary source/destination-type wizard or a direct game-to-game path.

The result should identify:

- Exact supported species/form mapping.
- Required destination version/DLC or other encoding constraints, if verified.
- Available destination capacity and valid insertion location.
- Supported, converted, retained-only-in-VBHelper, or blocked fields.
- Native snapshot/return support versus a conversion from another game.
- Any unresolved identity, stale-save, write-capability, or pending-operation issue.

Examples of useful statuses are "Ready to deposit", "Ready to return", "Conversion available", "View/import only", "Refresh needed", "Permission needed", and "Needs identity review". The wording should describe the actual state, not expose raw parser exceptions.

### 4.4 One-action native move and automatic preservation

The normal supported flow is **tap individual -> information -> Move to Storage**.
The action performs the actual move immediately from the user's perspective. It does
not open mandatory import previews, additional confirmations, species-mapping screens
or separate materialization steps. A short in-flight state and final feedback are
appropriate while real I/O completes; do not announce success before it does.

Preserve the exact source nickname and its native encoding/baseline, continuing app
identity, personality where present, progression, stats, skills, equipment and verified
history. Initialize only genuinely absent app-only fields once. Ordinary Storage/Home
must use the resolved species and nickname directly after the move. A transfer,
conversion or materialization cannot reroll the individual, reset its level/growth or
award duplicate progress. The existing zero-progress A/B/C/A invariant still applies.

The button internally binds a fresh versioned plan, rereads affected documents,
validates candidates, creates backups, journals the operation and verifies persistence
under section 11. If source state changed or execution cannot complete, retain the
recoverable state and present a concise result/retry; a simple UI does not justify
losing an individual or treating a copy as source removal.

Technical field reports and detailed conversion diagnostics remain optional secondary
tools. Their old preview-first interface is historical groundwork, not an extra step
required for every supported move.

### 4.5 History and recovery

Transfer should expose completed operations, pending acknowledgments, recoverable interruptions, and source conflicts. A previously successful roster may remain visible while its source is unavailable, clearly labelled with its last-read time.

Inherit existing Compose/Material components, saved palettes, accessibility, and localization. Refresh/progress states should retain usable cached content. Detailed parser diagnostics remain behind the existing Debug setting.

### 4.6 Acceptance checklist for the clarified experience

- Transfer opens a single list containing DS games, Vital Bracelet and other supported
  origins. DS entries show launcher icons rather than game covers.
- Opening a game's entry displays the actual save individuals with automatically
  resolved species and their normal app sprites.
- Tapping one individual opens its information and direct Move to Storage action.
- One supported action removes the native individual from the save and exposes that
  same individual in regular Storage/Home with its nickname and progress intact.
- No required preview, extra confirmation, manual species choice or APP creation
  intervenes in the normal supported flow.
- Storage-to-game evaluation permits any supported game with the species, independent
  of where the individual originated; incompatible species and unimplemented writers
  cannot be presented as working destinations.
- Retry, interruption and stale-save handling preserve exact native data, ownership,
  receipts and app progress; game-load validation verifies the modified save.

## 5. Meaning of current state

"Current state" means **the latest successfully read, persisted game save**, not the emulator's live RAM or an unflushed save state.

Refresh procedure:

1. Resolve each authorized source and its current capabilities.
2. Read its documents with adapter-specific size/time bounds.
3. Compute a content revision covering all participating files.
4. Decode and reconcile a new immutable roster observation.
5. Publish that source's completed observation together with its read time and revision.
6. Retain the last good observation with an explicit stale/error state if refresh fails.

For multi-file saves, detect changes during the read and retry or report an inconsistent observation. A timestamp or file-size match alone is insufficient evidence that a save is unchanged. Provider notifications are useful hints, not proof of freshness.

Refresh sources independently, with bounded concurrency, so one disconnected PC or inaccessible emulator folder does not prevent browsing the other sources. Deduplicate overlapping refresh requests and cancel obsolete work.

Before a transfer, reread affected documents and recheck identity, destination capacity, encoding support, and plan revisions. Starting an operation from cached content must not authorize overwriting a newer save.

## 6. Architecture boundaries

Names below are proposed contracts, not existing implementations. Where a functional equivalent already exists, it is recorded here so later work extends real code instead of introducing parallel abstractions. Paths are relative to `app/src/main/java/com/github/nacabaro/vbhelper/` unless they name a Gradle module (`game-save-core`, `companion-protocol`, `pc-save-companion`). Verified 2026-10-16.

| Proposed contract | Current functional equivalent |
|---|---|
| `SaveSourceTransport` | `source/gameSave/SaveDocumentAccess.kt` (`AndroidSaveDocuments`, read-only) and `source/gameSave/LocalCompanionClient.kt` (paired LAN companion, read-only) |
| `SaveDiscoveryAdapter` | `game-save-core` `SaveModels.kt` `SaveDiscoveryPolicy` plus bounded folder walks in both transports; Time Stranger-shaped today |
| `SaveContainerCodec` | Not implemented |
| `GameSaveAdapter` | `game-save-core` `SaveModels.kt` `GameSaveReader`; sole implementation `TimeStrangerSaveReader.kt` (read-only) |
| `IndividualIdentityReconciler` | `game-save-core` `IdentityEvidenceComparison.kt` plus binding/review logic in `source/gameSave/GameImportAcquisitionRepository.kt` |
| `TransferConversionPolicy` | Not implemented for game saves; `game-save-core` `UniversalProgress.kt` normalization-policy strings and `OriginStatInfluence` are the only precedents |
| `TransferPlanner` | `game-save-core` `SnapshotImportPlanner.kt` and `NativeRefreshPlanner.kt` cover read-only import and refresh planning only |
| `TransferCoordinator` | `source/gameSave/GameImportAcquisitionRepository.kt` `commit` (Room transaction and receipts). No production execution coordinator exists; `game-save-core` `TransferCommit.kt` provides only the section 11 plan/journal/recovery state machine and the desktop rehearsal |

### 6.1 Source transport

`SaveSourceTransport` owns byte access and capability reporting:

- Android document/file access.
- Authorized folder enumeration.
- Local Windows companion access.
- Exported-file replacement where in-place writing is unavailable.

It reports opaque locators, read/write capabilities, availability, and replacement/readback behavior. Android document URIs are not converted into guessed filesystem paths. A Windows path is resolved by the PC provider, not by the phone filesystem.

### 6.2 Discovery

`SaveDiscoveryAdapter` identifies candidate documents inside a selected location using bounded scans and emulator-specific conventions.

Names/extensions are discovery hints. Actual contents must pass format recognition. Keep discovery diagnostics and manual linking so a different emulator folder layout does not require a new game parser.

### 6.3 Emulator/container handling

`SaveContainerCodec` extracts and rewraps a game payload while retaining relevant container metadata and unrelated content.

Examples to research include raw battery saves, DS emulator wrappers, and PlayStation memory cards. These are research targets, not claims of current support. Emulator save states are a separate format class and should not be mistaken for ordinary game saves.

For a shared memory card, read and update the correct game entry while preserving the other games and the card filesystem's required metadata.

### 6.4 Game codec

`GameSaveAdapter` recognizes a concrete game/layout version and provides independently verified operations:

- Read source metadata and occupied rosters.
- Expose source identity evidence.
- Preserve native records and required context.
- Plan insertion/removal and validate the candidate save, when supported.
- Decode written output for readback verification.

A reader does not automatically gain writer capabilities. Decode, insert, remove, return, and conversion support must be enabled separately per tested format profile.

### 6.5 Identity reconciliation

`IndividualIdentityReconciler` distinguishes continuing individuals, new individuals, historical/branched observations, ambiguous matches, and conflicts.

It uses verified native identifiers and source-lineage evidence. Names, species, roster offsets, and file paths are not universal individual IDs.

### 6.6 Conversion

`TransferConversionPolicy` converts between source-native data and VBHelper's representation, and later into a selected destination format.

Conversion support is directional and field-specific. A game codec can be readable and writable while a particular foreign individual remains unencodable.

### 6.7 Planning and commit

`TransferPlanner` builds immutable, revision-bound plans with exact candidate outputs and per-individual results.

`TransferCoordinator` executes those plans using backups, a durable journal, destination verification, source release, and recovery.

The domain/codec layer should remain independent of Room, UI, Android document APIs, NFC, and LLMs. A shared Kotlin/JVM core is the recommended starting point for Android and Windows reuse; packaging and desktop UI choices can follow the first working vertical slice.

## 7. Identity, representations, and ownership

### 7.1 Permanent individual

Keep `DigimonIndividual.individualId` as VBHelper's permanent identity. Conversations, imported provenance, app progress, and permanent life events should follow that individual across representation changes.

Two same-species Digimon remain separate. An ordinary evolution can preserve individual identity where source evidence supports that interpretation. Rebirth, fusion, split, or replacement mechanics need game-specific identity semantics; they must not be generalized from Pokemon behavior.

### 7.2 Linked save instance and lineage

Give each linked logical save an app-owned `sourceId`. Separately represent a continuing playthrough/lineage and the documents/slots observed for it.

Several slots may belong to one playthrough, while an older snapshot may become a branch. Offer reviewed grouping and authority selection; do not silently collapse independent saves across emulators.

### 7.3 Native representations

An individual may have several archived or observed representations:

- A Time Stranger native record.
- A supported DS game record.
- A VBHelper APP representation.
- A bracelet-compatible representation when that path is validated.

Retain exact game/format/container versions and native values. Shared species definitions must not receive individual names, stats, or history.

Retain a native baseline for each visited game/representation and an independent origin foundation for the continuing individual. A lossy or normalized Game B/Storage representation must not replace Game A's native baseline. DIM-assigned app stats are another representation basis, not a rewrite of a game-native original.

### 7.4 Observation is not ownership transfer

External roster observations can be displayed before an individual is imported. Snapshot copies should be identified as copies. An actual move changes the managed presence/ownership state only through its committed transfer journal.

Keep these states separate:

- Present in a linked game.
- Owned in VBHelper Storage.
- Observed as a snapshot copy.
- Pending transfer/frozen for an operation.
- Away at a destination.
- Ambiguous/conflicting source state.

The Home active-selection flag is not an ownership ledger. Artwork preferences cannot
change ownership or produce a transfer-pending state.

## 8. Proposed persistence

Use preserving additive migrations. Schema 45 was the original design baseline; current implementation versions and verification receipts are tracked in `AppDatabase.kt` and `TESTING.md`.

### `LinkedSaveLocation`

- App-owned location ID and user label.
- Transport/provider kind and opaque locator.
- Persisted grant/connection reference.
- Discovery configuration and health.

### `LinkedGameSave`

- Stable source ID, location ID, game and verified format profile.
- Constituent documents, container entry, and slot association.
- Playthrough/lineage association and selected authority.
- Last successful observation and current operation capabilities.

### `SaveObservation` / `ObservedSourceIndividual`

- Immutable source revision, read time, codec version, and validation result.
- Native roster records, source locations, decoded display values, and identity evidence.
- Ambiguous/unresolved records distinguished from confirmed occupied records.

Observed rows do not have to become owned `UserCharacter` rows.

### `ExternalIndividualBinding`

- Permanent VBHelper individual ID.
- Game and source-lineage association.
- Native identifier/evidence and confidence.
- First/latest observations and any explicit reconciliation decision.

Enforce uniqueness only for identifiers whose semantics have been established. A weak or reused native hash requires conflict handling, not a blanket global uniqueness assumption.

### `SourceNativeSnapshot`

- Exact native representation and required export context.
- Original species, stored name, personality, progression, skills, equipment, and available history.
- Source revision, observation time, decoder version, and conversion version.
- Decoded, derived, unknown, and user-authored values distinguished.

### `ImportSession` / `ImportItemReceipt`

- Source revision and selected individuals.
- New/unchanged/refreshed/ambiguous result per item.
- Idempotent commit receipt and permanent binding.

Item-level receipts must allow later imports of additional individuals from the same file.

### `TransferPlan` / `TransferJournal` / `SourceBackup`

- Operation ID, direction, selected individuals, and source/destination revisions.
- Candidate output hashes and affected-file descriptions.
- Conversion results and confirmation binding.
- Prepared/committed/verified/acknowledged/recovery states.
- Verified backups and write/readback receipts.

### Permanent history and app deltas

Add a permanent-individual history/delta model where needed. Existing `TransformationHistory` belongs to a Storage representation and can cascade away on representation deletion; it is not sufficient as the sole lifetime history for cross-game transfers.

### Universal journey/progress ledger and representation checkpoints

- Associate the origin native snapshot, semantic progress ledger and all representation checkpoints with the permanent individual.
- Record the normalization/conversion policy and version, source revision, actual values encoded/read back when entering a representation, and the ledger amount already reflected in that native representation.
- Record actual observed progress separately, with per-observation/item receipts so polling, retries and repeated imports cannot award it twice.
- Use checkpoints based on the **actual encoded target values**, not an ideal pre-rounding projection. An unchanged rounded Game B record therefore contributes zero new progress.
- Observing an unchanged older representation must not acknowledge or swallow gains earned elsewhere. Pending progress remains pending for that destination until a verified conversion actually applies it.
- Preserve unresolved native changes and their snapshots until a validated translation policy can classify them. Keep projected/app values distinct from the original source values and unknown fields.
- Persist the selected origin/DIM stat basis independently of earned progress. Changing that basis does not increment the progress ledger.

## 9. Compatibility and participation policy

Evaluate four independent questions:

1. **Can this source be read and is the record confirmed owned?**
2. **Can this individual be represented or archived in VBHelper?**
3. **Can this selected destination encode it and commit the requested operation?**
4. **Is the stored individual locally owned and free of an actual transfer/activity lock?**

These answers must not be collapsed into one availability flag.

### Universal participation and artwork preference

- All stored Digimon participate regardless of origin, DIM/BEM association or artwork.
- VB first and Dot first select rendering priority only, with the opposite source as
  fallback and a neutral placeholder when neither exists.
- Missing cards, catalog art and old mode settings cannot disable an individual or
  clear the active selection. Retired mode-induced pauses are resumed preserving clocks.
- Ownership, an in-flight transfer and normal activity rules remain separate concerns.
- A missing sprite does not block an otherwise supported native transfer.

### Complete Storage stat model and shared Home

Card-independent and game-origin Digimon share the DIM-style Home composition, complete
VBHelper stats/parameters, interactions, care/progression, history and favorites. Source
transport, missing artwork and the absence of a DIM cannot select a reduced screen or
pause gameplay. Product-specific parameters are inspected under that product's Transfer
individual-information surface, not mixed into ordinary Home or Storage details.

The current in-app stat projection is balanced and reflects retained cross-product
progress. An associated exact DIM/BEM overrides combat stats under its normal card
rules, suppressing additional origin/app bonuses while retaining them in the ledger.
Shared species definitions never receive individual bonuses. Association/removal is
configuration, not a growth event, and never overwrites native values or earned changes.

### Missing species/artwork

- Known species with unavailable artwork: keep the usable partner with a neutral fallback.
- Confirmed owned but unmapped native species: retain the individual and native data
  without guessing a species; automatic mapping remains an adapter implementation task,
  not an artwork/mode restriction. Native IDs belong in Transfer information.
- Uncertain ownership: keep in review; do not manufacture a partner from an unproven roster row.

### Destination compatibility

Report unsupported species/forms, required fields that cannot be represented, unknown format versions, unavailable insertion capacity, write restrictions, identity conflicts, and conversion losses explicitly.

An adapted or approximate form must never silently replace the requested canonical form. Fields retained only in VBHelper remain available in source history even when a destination cannot encode them.

## 10. Conversion, personality, and memories

### Original values and app values

Preserve source values independently of VBHelper gameplay. Game level is not evolution stage; game EXP is not Vital Points; game bond is not automatically a care/mood value; game stats are not BE training measurements.

Record the source baseline and the initial adapted app representation. Later returns should apply supported app-earned deltas through a versioned policy, rather than reverse-engineering original game data from the current app stats.

### Balanced origin influence and bidirectional progress translation

1. Preserve the exact source-native baseline and required context before adapting values.
2. Establish the individual's origin foundation separately from progress earned after that baseline.
3. Project that foundation and retained progress into a bounded, balanced in-app model appropriate to the species/form. An associated DIM/BEM enforces the card's normal stat rules, suppressing extra app/cross-product bonuses without erasing the foundation or earned progress.
4. On entry into another game, use a directional, versioned adapter and record the actual encoded/read-back native values as that representation's checkpoint.
5. On return from that game, compare against its own checkpoint to identify actual gains/losses. Translate those changes into the shared semantic ledger and equivalent supported in-app values once.
6. For the next destination, apply the progress not yet represented there using that game's validated conversion policy; retain nonrepresentable information in VBHelper and expose restrictions/losses in the preview.

A common numeric value is not automatically a common unit. Translation must account for native ranges, species/form context, growth curves, caps and mechanics. Exact balance bounds and per-game formulas are versioned implementation policies to validate, not new user-confirmed numeric constants.

Unknown growth/skill/equipment mappings must remain preserved and visibly unresolved. Restore/branch/rebirth regressions require the appropriate reconciliation policy instead of automatic subtraction or another award of the original baseline.

### Personality

Import verified native personality into an explicit semantic mapping. Preserve its original ID and source. App-derived behavior traits can be generated from that mapped personality, but should remain distinguishable from source measurements.

For a game without a decoded native personality field, an app-derived personality must be labelled as such; leave native personality provenance unset rather than presenting a generated value as imported game data.

Reviewed refreshes must resolve conflicts if the game and app changed personality or names independently. Personality-system upgrades must preserve imported provenance and avoid silently rerandomizing the individual.

### Verified history

Represent partial native evolution history as partial source evidence. Keep observation time separate from unknown event time. Global story completion provides world/player context, not proof that every banked Digimon personally participated.

Chat should use verified individual facts and properly scoped game context. Import-time initialization must not be presented as a complete historical biography.

### Cross-game journeys through Storage

For Game A -> Storage -> Game B, preserve Game A's native snapshot and append Game B's representation/receipt to the same permanent individual when the transfer establishes continuity. Do not discard origin-only information merely because Game B cannot encode it.

The mandatory reference journey is:

`Game A -> Storage -> Game B -> Storage -> Game C -> Storage -> Game A`

- With no progress or relevant personal edits, return to Game A using its retained native baseline so its original stats are restored exactly; do not attempt to invert a lossy app/Game B/Game C projection.
- With actual progress, preserve that same baseline and apply the equivalent supported deltas once. Visiting another game must not reset progress to a generic default or multiply previously earned gains.
- DIM assignment, switching in-app stat bases and app-only balance adjustments must not change the zero-progress outcome.
- Restoration concerns the continuing individual's native record/fields. It must be integrated into the fresh destination save while preserving unrelated current game state, not by blindly replacing the whole save with an old archive.

## 11. Commit and recovery design

### Preflight

1. Refresh and fingerprint every affected logical save and its constituent documents.
2. Reconcile the selected individual against that revision.
3. Confirm adapter, conversion, capacity, and provider capabilities.
4. Construct and validate exact candidate outputs in isolation.
5. Bind the user's Move action to the fresh plan revision; evaluate preservation and
   conversion internally. Keep technical reports optional instead of inserting a
   mandatory preview/confirmation wizard into the ordinary supported flow.

Immediately before writing, recheck affected revisions and access. An external change requires a new plan.

### Deposit from a game into Storage

1. Persist the operation journal and recoverable native data.
2. Create a durable pending Storage acquisition and its receipt.
3. Back up fresh original source bytes.
4. Apply the adapter's verified source removal and required metadata updates.
5. Read back and validate the persisted source.
6. Finalize Storage ownership and its normal playable app representation; artwork/card
   availability cannot disable the stored Digimon.

If source removal is unsupported, the advertised operation is a snapshot import rather than a move.

### Send or return from Storage into a game

1. Reserve/freeze the selected app representation for the operation.
2. Back up the fresh destination and construct its candidate output.
3. Write, reread, and validate the destination and inserted individual.
4. Finalize the app representation as away only after destination verification.
5. Retain permanent identity, source bindings, conversations, snapshots, and receipts.

### Storage/provider behavior

Normal files and some PC providers may support staged replacement. Android document providers do not offer a universal rename-and-swap transaction. Select a tested write strategy per provider, with backup and readback; otherwise expose read/import-only or an explicit export-and-replace workflow.

Multi-file saves and memory cards require a journal spanning all affected documents. Known directory/checksum/compaction changes must be included in the planned write set. A validation rule that allows only one entity's bytes to change would incorrectly reject legitimate structural updates.

### Interruptions and conflicts

- Persist enough progress to resume or reconcile after process death/disconnection.
- Verify destination persistence before releasing the source representation.
- Do not blindly compensate by overwriting a destination that has changed since this operation wrote it.
- Detect repeated packages/receipts and double confirmation.
- Treat restored saves and divergent branches as reconciliation cases.
- Keep backups separate from the active emulator saves they protect.

A Room transaction does not make an external document write atomic. Normal managed retries should be idempotent; old backups or external edits cannot be assumed to participate in the same transaction system.

## 12. Android and PC access

### Android

Use the Storage Access Framework for selected files and directories, retaining persistable grants where provided. Recheck read and write access separately after restart.

Modern Android restricts access to other apps' private directories and selection of certain roots. Prefer emulator-exposed document providers, configurable accessible save folders, and supported export/import paths. Represent permission revocation, moved files, unavailable SD cards, and provider failures as source-health states with relink/retry actions.

Require an actual game save to be flushed before transfer writes. Emulator save states can contain older in-memory data and later overwrite a changed save; save-state support, if ever added, needs its own adapter and semantics.

### PC companion

The Windows companion provides access to PC-side saves while the phone remains the main Transfer UI. It should reuse the format/identity/transfer core and report source revisions, operation capabilities, busy/conflict states, and commit receipts.

The selected connection mechanism is **local network**, with explicit pairing and pinned peer identity. Companion packaging and actual phone/PC network acceptance still require verification. A copied package is a dated snapshot; only an accessible, refreshed PC source should be presented as current PC-save state.

The PC provider should not own the only copy of permanent individual identity. Provenance and transfer receipts must survive companion reinstall/relink according to the backup/recovery design.

## 13. Time Stranger as the first adapter

### Confirmed target

- PC version; user's main reference is `0001.bin`.
- Installation supplied by the user: `C:\Games\Digimon Story - Time Stranger`.
- Saves found under `gamedata\savedata\<profile>\`.
- Seven main slots and corresponding metadata files, plus two existing backups of `0001.bin`.
- First priority is actual individual identity, current form, name, and personality.
- Later support includes original progression, skills/equipment, and all representable return changes.

### Observations from read-only inspection

- Main saves/backups are 3,098,176 bytes.
- Independent in-memory AES-128-ECB decoding matched the researched container and plausible roster structures.
- The researched payload is 3,097,152 bytes plus a 1,024-byte decoded header.
- Slot sidecars are 720 bytes; the settings file is a separate 992-byte file.
- Selected roster records showed personality IDs in the documented 1-16 range.
- Candidate identity values recurred across some slots despite position/species differences.
- The executable's Windows file/product-version fields were blank, so an exact installed game patch was not established from those fields.

These are format/structure observations, not proof of writer correctness or game acceptance of modified saves.

### Identity research

The field labelled `creation_hash` is a promising candidate, but needs controlled verification of persistence, uniqueness, collision/reuse behavior, and playthrough branching.

Offsets, party/bank indexes, species IDs, and save-slot numbers are mutable/location-specific. ANAMNESIS save UUIDs and individual padding markers are tool-authored and must not be treated as proven native individual IDs.

### Occupancy research

The public parser's first inactive bank-boundary candidate can carry a zero or duplicate candidate hash. Your reference appears to contain six party records and 35 non-boundary bank candidates, with one additional disputed bank-boundary row. These are not confirmed in-game ownership counts.

The user has now confirmed reference in-game counts of **6 party, 35 bank and 5 farm**. The decoder also retains one additional inactive bank-boundary candidate for review. Aggregate count agreement does not establish native individual identity, uniqueness or every row's occupancy semantics; controlled movement/identity samples remain necessary. Do not silently import boundary/sentinel/story data as owned individuals or silently omit actual farm members.

### Native data

- Use a reviewed numeric game-species/form crosswalk to canonical VBHelper IDs.
- Preserve exact stored name bytes/text alongside nickname interpretation.
- Explicitly map native personality IDs; do not rely on Kotlin enum ordinals.
- Preserve raw progression fields before applying display scaling or rounding.
- Keep partial previous-species history separate from invented timestamps or a full lineage.
- Preserve region-specific native record boundaries; a generic fixed-length export from another tool is not assumed lossless for party/bank and farm records.

### Controlled samples still needed

1. Save twice without changes, then save the same state to another slot.
2. Rename one known individual, including short/localized names if supported.
3. Change personality independently.
4. Reorder party and move the same individual party -> bank -> farm -> bank.
5. Evolve and de-evolve it in separate steps.
6. Retain two same-species individuals with distinguishable names.
7. Trigger bank compaction by removing an expendable earlier individual, then recruit another.
8. Compare an older restored snapshot and its later continuation.

The first Time Stranger implementation must use the general source/observation/import contracts. It must not become an isolated "load PC package and create Debug Digimon" path.

## 14. Existing VBHelper foundations and gaps

Paths below are relative to `app/src/main/java/com/github/nacabaro/vbhelper/` unless stated otherwise.

### Reusable foundations

- `domain/device_data/DigimonIndividual.kt`: permanent identity and `IMPORTED` origin.
- `species/CanonicalSpeciesCatalog.kt`: canonical identity, approved aliases, forms, artwork metadata.
- `species/ExpandedCatalogRepository.kt`: shared card-independent definitions.
- `species/AppPartnerFactory.kt`: transactional APP-partner acquisition primitive.
- `species/PartnerParticipationGate.kt`: live source-mode policy and paused participation.
- `species/SpeciesAppearanceRepository.kt` and `database/ModeSpriteSchema.kt`: mode-aware artwork.
- `source/WatchTransferRepository.kt`: durable prepare/acknowledgment patterns for the existing watch workflow.
- `source/CardDocumentRead.kt` and card-import controllers: document lifetime, cancellation, retained operation-state patterns.
- `screens/storageScreen/`: collection, details, inactive presentation, and source-aware extension points.
- `database/AppDatabase.kt` / `di/DefaultAppContainer.kt`: registration and migrations.
- `navigation/NavigationItems.kt`, `navigation/AppNavigation.kt`, `screens/scanScreen/`: Transfer/watch integration points.

### Gaps affecting the plan

- The multi-game architecture is scaffolded for exactly one format: one read-only Time Stranger reader, Time Stranger-shaped discovery, no container codec and no second-format fixture. The linked-save registry, source-revision model, observations and import receipts exist (schema 51) but remain unproven as game-neutral contracts.
- No Storage-to-game execution, writer or recovery path exists. Destination selection/evaluation is implemented read-only and non-executable (`canExecute` false), and a core commit journal/recovery state machine, a byte-preserving rehearsal and a rename writer (desk-validated against the game's own rename sample) exist desktop-side only. `SaveCapabilities.canRemove`/`canInsert` are declared false and are unreferenced in production code.
- The verified acquisition profile registry is intentionally empty (`GameImportAcquisitionRepository.productionProfiles`), so production snapshot imports stay gated until the registry gates pass.
- Existing NFC/VitalWear importers have card/device assumptions and are not generic game importers.
- APP creation currently assumes a ready definition and mode eligibility; imported archival/inactive creation needs a validated, explicit acquisition policy.
- A general unresolved-species/placeholder Storage path needs work.
- APP training persistence and battle projections need integration before source-stat adaptation can be considered complete.
- Representation-owned evolution history is not sufficient permanent history.
- Imported personality must survive regeneration/migration policies.
- Chat labels need to distinguish source-game observations, app-derived readings, and actual historical events.
- Existing APP-to-bracelet/BEM/VitalWear round trips need their own validation before being advertised as a general destination.

Use a normal import service with validated acquisition context. Reusing Debug creation, requiring a recruitment quest, or routing a save through whole-database restore would give this feature the wrong semantics.

### Verified transfer-subsystem state (schema 51)

Code-verified against the repository on 2026-10-16. This records what actually exists so the phases below are not planned twice:

- **Implemented:** the Transfer hub (`screens/transferScreen/`), linked file/folder and paired LAN companion sources (`source/gameSave/`, `pc-save-companion/`), immutable observations with source revisions, cached/stale/error source health, roster display, snapshot-import preview and transactional commit with per-item receipts, reviewed species mapping and APP materialization, and the universal progress ledger with representation checkpoints and per-observation receipts (`game-save-core/.../UniversalProgress.kt`, `source/gameSave/UniversalJourneyRepository.kt`, `database/TransferContinuitySchema.kt`).
- **Read-only and gated:** transports are read-only on Android and PC; `SaveCapabilities.canRemove`/`canInsert` are false and unused in production; `GameImportAcquisitionRepository.productionProfiles` is empty, so production acquisition stays gated until the registry gates pass.
- **Not implemented:** Storage-to-game execution and transfer history/recovery UI (plan 4.5), `SaveContainerCodec`, per-game conversion policies, the production writer/planner/coordinator execution machinery (only the core section 11 journal/recovery rehearsal and the rename-writer groundwork exist), and the companion write path. Destination evaluation/preview (plan 4.3/4.4) and the World DS reader are implemented read-only and non-executable.
- **Contract naming:** section 6 names are proposals; the table at the start of section 6 maps them to the current functional equivalents.

## 15. Performance requirements

The user has already reported slow artwork preparation and delayed rosters. Save linking must not amplify that problem.

- Read/parse on background dispatchers, with bounded per-source work and concurrency.
- Show cached rosters and per-source progress while refreshing.
- Cache immutable parsed observations by verified content revision and codec version.
- Treat unknown provider timestamps as an instruction to verify bytes, not an unchanged-save guarantee.
- Bound directory depth, candidate count, file size, archive expansion, and read duration per supported format.
- Resolve artwork by canonical reference and load only visible portraits/selected import assets.
- Do not materialize every species' artwork in Room merely to browse a linked roster.
- Prepare selected trusted assets outside the acquisition transaction.
- Batch database changes and avoid a participation snapshot or large image query per roster row.
- Keep one source failure independent of the others and cancel obsolete refresh/preview jobs.

Native timing and memory targets should be measured on the user's phone once a vertical slice exists. Historical APK/JVM results are not performance evidence for this subsystem.

## 16. Delivery roadmap

Each phase is independently useful and has its own acceptance boundary.

### Phase A - Source and domain foundation

Deliver:

- Linked locations and logical saves.
- Opaque source locators and operation capabilities.
- Immutable observations and source revisions.
- Generic game/format/container/identity boundaries.
- Minimal additive persistence and migration fixtures.

Acceptance: multiple selected sources survive restart and remain distinct; unavailability/relinking does not erase imported individuals or falsely publish empty rosters.

### Phase B - Phone-first read-only Transfer hub

Deliver:

- Link file/folder and local PC source registration.
- Refresh-on-open/resume, manual refresh, cached/stale source states.
- Source list and native roster organization.
- Per-individual inbound/outbound capability explanations.
- Existing watch actions integrated as a distinct endpoint.

Use real format evidence to prove a read-only vertical slice; avoid a large speculative plugin framework. Time Stranger supplies the first real codec, with phone-accessible and PC-backed transport exercised through the same contracts.

Acceptance: the phone can show multiple linked save instances, refresh actual saved contents, and accurately distinguish supported operations from read-only or unimplemented ones.

### Phase C - Time Stranger inspector and identity verification

Deliver:

- Validated reader for the reference `0001.bin` layout.
- Party/bank/farm and ambiguous-record handling.
- Native species/name/personality and identity evidence.
- Cross-slot/backups comparison and controlled-sample fixtures.

Acceptance: confirmed owned counts agree with the game; no ghost imports; continuing individuals can be matched with a documented confidence policy.

### Phase D - Identity-first Storage imports and reviewed refresh

Deliver:

- Source bindings/snapshots and per-item receipts.
- Preview, selection, and transactional commit.
- Correct imported personality and source details.
- Inactive/placeholder handling for unavailable imports.
- Reviewed refresh/conflict handling preserving app history and progress.
- A complete in-Storage stat model and the shared DIM-style Home for card-independent partners.
- Reviewed species/form mapping and origin-versus-explicit-DIM stat-basis selection without replacing permanent identity/personality or native baselines.

Acceptance: selected actual individuals appear in Storage with correct identity, name, form, personality, source status and complete applicable in-app stats. A partner without a DIM uses the normal DIM-style Home. Retries do not duplicate individuals, additional unselected individuals can be imported later, and DIM assignment changes the stat basis without resetting progress or native data.

This is the first complete import release. Snapshot imports remain explicitly labelled until an adapter has verified removal support.

### Phase E - Conversion and permanent continuity

Deliver:

- Source versus app gameplay projections.
- Versioned stat/progression adaptation and app-delta recording.
- Supported skills/equipment mappings.
- Permanent history and source-aware chat context.
- Exact native-return reconstruction from preserved baselines.
- A universal semantic progress ledger with per-game/representation checkpoints and observation receipts.
- Balanced origin-based app projections, explicit DIM/BEM overrides, and equivalent bidirectional translation of actual progress.
- Zero-progress multi-game round-trip fidelity, independent of intermediate rounding, caps or app stat-basis selection.

Acceptance: no source values are lost through display rounding or app-field overloading; reviewed refreshes do not reapply growth repeatedly; unknown data remains preserved. A no-progress `A -> Storage -> B -> Storage -> C -> Storage -> A` journey restores A's original native stats exactly. Actual gains translate into a balanced Storage representation and an equivalent supported destination result once, without creating progression from transfers or configuration changes.

### Phase F - Verified writers and managed moves

Rung-1 groundwork is in place desktop-side: the section 11 plan/journal/recovery state
machine (`game-save-core` `TransferCommit.kt`) and the `--write-rehearsal` copy-only
pipeline. It enables no writer; per-title rungs still gate every capability.

Deliver per tested adapter/provider:

- Insertion/removal, required counts/metadata/checksums, and candidate validation.
- Revision-bound plans, fresh backups, journals, readback, and recovery.
- Game -> Storage moves and Storage -> original-game returns.
- Supported name/personality/form/progression return changes as each mapping is validated.

Acceptance: game-loaded output agrees with the plan; unrelated game state remains valid; interrupted operations can be reconciled; stale confirmations cannot overwrite newer saves.

### Phase G - Additional games in the confirmed order

Add DS Story, Re:Digitize/Decode, PlayStation World, then Cyber Sleuth/Next Order adapters. For each title, select concrete game/edition/region/emulator fixtures and progress independently from reader to importer to writer.

Use the first additional DS reader early enough to verify that shared contracts are genuinely game-neutral. A container or identity assumption that only works for Time Stranger must not become a universal requirement.

Acceptance: each advertised capability has format-specific evidence and the same Transfer UI can represent different native roster/identity structures without game-specific logic leaking into generic storage access.

### Phase H - Cross-game destinations through Storage

Introduce destination-specific conversions for Game A -> Storage -> Game B. Preserve original snapshots, expose conversion changes, and retain identity continuity established by managed receipts.

Acceptance: unsupported forms/required fields are blocked accurately; supported conversions and subsequent returns preserve the individual, archived native baselines and accumulated semantic progress. Exercise both zero-progress and earned-progress A/B/C journeys, including DIM assignment in Storage, without double awards, rounding drift or resetting native originals. Direct save-to-save transfers require a separate future decision.

## 17. Verification plan

### Pure codec and identity tests

- Golden decoder fixtures for each supported layout/container.
- Truncation, invalid lengths, corrupt records, unsupported versions, and bounded decoding.
- Occupancy/sentinel/tail handling and farm validation.
- Two same-species/same-name individuals; rename, reorder, evolve, compact, delete/recruit, and branch cases.
- Explicit native personality and numeric species/form mappings.
- Container rewrapping preserving required padding and unrelated save entries.
- Lossy intermediate representations with unchanged native progress contributing zero to the shared ledger.
- Game A -> Storage -> Game B -> Storage -> Game C -> Storage -> Game A with no progress, asserting exact original A stats/native record fields.
- Actual progress in B translating once into Storage and back to A/C, with retries, caps, rounding and per-destination pending deltas covered.
- Stat-basis/DIM changes leaving earned progress and original native baselines untouched.

### Import and persistence tests

- Additive migration/reopen preserving the existing collection and records.
- APP imports without fake watch rows.
- Inactive/unresolved imports remaining visible and correctly gated.
- Atomic selected-item commits, cancellation, double confirmation, retries, and concurrent attempts.
- Later selection from the same save and reviewed updates from later revisions.
- Permanent chat, personality provenance, history, and app progress surviving representation changes.
- Complete stat/parameter persistence and shared Home behavior for DIM, card-independent and game-origin partners.
- Explicit DIM assignment using the selected template's stats while retaining origin provenance and earned changes.

### Transfer and recovery tests

- Every selected individual included in preview/validation.
- Confirmation invalidated after source or destination changes.
- Backup/readback failure, disconnection, process death, and partial multi-file writes.
- Destination changes during compensation; source release failure.
- Duplicate receipts/packages and restored-save branches.
- Unchanged-return/native-round-trip checks plus actual game acceptance for writers.

### Android/PC integration tests

- Persisted document grants; permission revocation and relink.
- Emulator document providers, accessible folders, and export-file fallbacks.
- App resume/manual refresh/cancellation while several sources are linked.
- PC unavailable/reconnected, slot changed, and current-versus-snapshot display.
- No ownership mutations from merely browsing a roster.
- Native layout, large fonts, TalkBack, supported locales, and measured loading/memory behavior.

Use private or sanitized save fixtures and small documented controlled differences. Existing unit/build/device receipts do not validate these future codecs or writers.

## 18. Outstanding decisions and research gates

The following remain open; they do not change the confirmed phone-first, local, via-Storage direction:

- Region, emulator/container variant and fixture for the selected first additional title, **Digimon World DS**.
- Exact subsequent editions, regions, emulator wrappers, and memory-card formats.
- Companion packaging and native/network acceptance for the selected **local-network** connection.
- Controlled Time Stranger identity/occupancy samples; reference party/bank/farm counts are now user-confirmed.
- Reliable native identity semantics, including collision/reuse and playthrough branching.
- Game-specific rebirth/fusion/multi-form ownership semantics.
- Final gameplay conversion formulas, growth bounds, and skills/equipment return policies.
- Per-game normalization policies and fidelity evidence for equivalent earned-progress translation and the mandatory zero-progress multi-game round trip.
- Tested provider-specific write strategies and support boundaries.
- Placement of the new Transfer hub within existing watch-send navigation.
- Backup retention/storage limits and recovery UX after native write behavior is established.

## 19. Reference sources and evidence

### User-supplied product references

- [PKVault repository](https://github.com/Chnapy/PKVault).
- [PKVault configured save locations](https://github.com/Chnapy/PKVault/blob/4a023c35da0d6bb2a83e219d86e94023bfe50960/docs/functional/en/5-settings.md).
- [PKVault storage/variants](https://github.com/Chnapy/PKVault/blob/4a023c35da0d6bb2a83e219d86e94023bfe50960/docs/functional/en/3-storage.md).
- [PKVault sessions](https://github.com/Chnapy/PKVault/blob/4a023c35da0d6bb2a83e219d86e94023bfe50960/docs/functional/en/2-session.md).
- [PKForge repository](https://github.com/sofianeelhor/PKForge).
- [PKForge Android folder picker](https://github.com/sofianeelhor/PKForge/blob/94fff38ea7817a594db58f38722d6f2f008d3b9f/src/PKForge.App/Platforms/Android/AndroidFolderPicker.cs).
- [PKForge emulator discovery](https://github.com/sofianeelhor/PKForge/blob/94fff38ea7817a594db58f38722d6f2f008d3b9f/src/PKForge.App/Platforms/Android/AndroidEmulatorScanner.cs).
- [PKForge transfer previews](https://github.com/sofianeelhor/PKForge/blob/94fff38ea7817a594db58f38722d6f2f008d3b9f/src/PKForge.Engine/TransferPreviewService.cs).
- [PKForge writer](https://github.com/sofianeelhor/PKForge/blob/94fff38ea7817a594db58f38722d6f2f008d3b9f/src/PKForge.Infrastructure/SafeSaveWriter.cs).
- [PKForge Android document access](https://github.com/sofianeelhor/PKForge/blob/94fff38ea7817a594db58f38722d6f2f008d3b9f/src/PKForge.App/Platforms/Android/AndroidSafFileAccess.cs).

### Time Stranger format research

- [ANAMNESIS Save Editor research baseline](https://github.com/DotingDavid/DSTS-Save-Editor/tree/9447d574129d2d5d782e82447e4885b935afc909).
- [Save field layout](https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_layout.py).
- [Roster, identity, and individual export implementation](https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_data.py).
- [Container crypto implementation](https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_crypto.py).
- [PC/Switch converter research](https://github.com/TyohDev/Digimon-Story-Time-Stranger-Save-Converter/blob/ec418712266d797fbf871eca1cbf1d44c47f9a5c/main.js).

The converter documents untested conversion directions and is not evidence of native individual identity or writer correctness. The ANAMNESIS editor's README still says "not yet licensed", but the user reports the author has granted permission to reuse the project's code and research (personal communication; keep this line updated if written confirmation is obtained). It therefore serves as an adoptable baseline: its field-map and save-diff claims are starting hypotheses, validated against the user's own save corpus and controlled samples rather than trusted blindly, and reused code or constants keep attribution to this section. Correctness acceptance still requires the controlled tests above.

### Digimon World DS and Dawn/Dusk format research

- [ReGame-Labs/dwds_decomp @ 91efd17c](https://github.com/ReGame-Labs/dwds_decomp/tree/91efd17c4c05ce106ce0f973155149d23d9de519)
  — World DS save/record facts (MIT); see
  [WORLD_DS_RESEARCH.md](game-save-core/WORLD_DS_RESEARCH.md).
- [joaomlsantos/DigimonNDSRomEditor @ cc3ff499](https://github.com/joaomlsantos/DigimonNDSRomEditor/tree/cc3ff499edfd86e382e724d1da7f155b6b830e5f)
  (GPL-3.0) — Dawn/Dusk ROM header codes (`A3VE`/`A6RE`/`A3VJ`/`A6RJ`), per-title ROM
  table addresses, 398-entry species ID list, ROM text glyph table; ROM data only, never
  save offsets and no code/assets vendored.
- [joaomlsantos/Digimon-World-Dawn-Dusk-Research-Notes](https://github.com/joaomlsantos/Digimon-World-Dawn-Dusk-Research-Notes)
  — Dawn/Dusk persisted-save geometry (`0x7B2C` copies at `0x0`/`0x7B80`, `DSDIG2`,
  MD5 header, party records `0x164` bytes at `0x288`), player-name/items offsets and target
  ROM MD5s. The repository says content is provided "for educational and community use
  only", which is **not** a license grant: facts may be cited and independently
  reimplemented, but no code or assets are copied, and every constant is verified against
  the user's own saves before use.
- <https://forums.desmume.org/viewtopic.php?id=9573> — 512 kbit save size / 164-byte `.duc`
  header reference for Dawn.
- <https://gbatemp.net/threads/digimon-world-dawn-u.516124/> — Dawn AR-code addresses that
  independently corroborate the `0x164` record stride.
- <https://en.wikipedia.org/wiki/Digimon_World:_Dawn_and_Dusk> — engine attribution,
  release dates, six-Digimon party, Digifarm; background only, not format evidence.
- The full step plan and parity matrix: [DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md).

### Local project context

- [Expanded-mode implementation status](EXPANDED_MODE_IMPLEMENTATION.md).
- [Recorded verification boundaries](TESTING.md).
- [Product context](PRODUCT.md).
- [Design context](DESIGN.md).

## 20. Next implementation step

### Universal-journey correction (2026-10-17, latest decision)

1. Remove source modes and all mode/card/artwork participation restrictions. Replace
   their setting with VB-first/Dot-first sprite priority and migrate saved choices.
   Resume retired mode pauses without replaying inactive time or deleting records.
2. Enforce the card-only stat override independently of artwork, retaining growth and
   baselines. Keep ordinary Home/Storage information VBHelper-specific; inspect native
   product information through the corresponding Transfer entry.
3. Complete the DS vertical slice as an actual continuing journey: World DS -> usable
   Storage with balanced progress -> supported return into a fresh save. Extend the
   existing permanent identity, journal and ledger rather than building another vault.
4. Add each receiving product's species mapping and conversion policy against the
   arbitrary-route invariants above, including zero-progress exact return and applying
   genuine gains once. Icons and technical tools support this journey; they are not
   substitutes for conversion and move implementation.

### DS-first override (2026-10-17)

1. Replace the prototype's game-cover grid with the unified **origin list** specified
   in section 4: DS launcher icons, Vital Bracelet and all other supported origins in
   the same list. Preserve native Android controls and saved app palettes.
2. Establish verified automatic native species/form crosswalks and reuse normal app
   sprites in source rosters. Tap-to-information must lead to one-action Move to
   Storage, automatically preserving the nickname and creating the normal Storage/Home
   representation without a manual mapping or materialization wizard.
3. Build World DS party removal and exact native return on copies: retain the full
   384-byte individual, maintain roster references, preserve every unrelated field,
   reject the last usable party member and unresolved/foreign/farm records.
4. Establish native integrity and save-copy authority, then validate changed saves in
   game. Connect the accepted writer to durable backups, journal/recovery and the
   vault transaction before exposing an actual Move action.
5. Extend to Dawn/Dusk with the pinned DigimonNDSRomEditor ROM baseline **and the
   independently verified persisted-save layout**: the step is now planned in
   [DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md). Dawn/Dusk share the World DS
   engine architecture but not its layout (per-copy `0x7B2C` at `0x0`/`0x7B80`, MD5 header,
   7 party records of `0x164` bytes at `0x288`), so the World DS adapter is first refactored
   into a shared NDS Story family and Dawn/Dusk become a second layout profile. Continue
   subsequent DS titles per adapter rather than applying a shared guessed save layout.
6. Implement Storage-to-receiving-game moves using species availability and validated
   conversions for the selected destination. The original game's identity is retained
   as provenance, never used as an artificial destination lock.

**Current gap:** the origin list, tap-to-information, first-link ROM banner import for
verified DS titles and experimental World DS deposit writer are implemented. The app
stores only the imported 32x32 icon; no ROM or bundled launcher artwork is required.
Automatic native species crosswalks, game-load/checksum acceptance, actual native
growth conversion and Storage-to-game execution remain incomplete. The writer retains
backups and verifies its own readback; that is not yet real-game acceptance of an
end-to-end journey.

The remaining section below records the preceding schedule. Time Stranger experiments,
including the staged inactive-tail candidate, are paused rather than active prerequisites.

Sections 21 and 14 record delivered work. Home/stat parity, the ledger and the first read-only native-archive destination preview are implemented. Physical-phone testing is deferred at the user's request; farm transfer is excluded. Remaining work follows capability-specific dependencies:

1. **Device acceptance of the existing read-only slice.** Run the link/refresh/roster/snapshot-import flows on a physical Android device, including SAF permission revocation and relink, Keystore pairing, accessibility and measured timing/memory. This is the highest confidence-per-effort item and needs no new format evidence.
2. **Read-only destination evaluation and preview (plan 4.3/4.4) without writes — implemented for retained native archives.** The user chooses a linked save and Party/Bank, may include up to 16 partners evaluated against the same fresh destination read, prepares a fresh revision-bound report and can re-read it. Per-pair native-format compatibility, candidate conflicts, shared restrictions and field preservation/conversion limits are reported. Exact capacity, version/DLC support and actual projected conversion values remain unvalidated until their adapter policies exist. No execution confirmation is offered.
3. **World DS save → Storage → shared Home — implemented provisionally.** The observed USA/DraStic 64 KiB raw profile supports phone reading, revision-bound reviewed copy selection, PARTY-only acquisition, source-local identity checks and reviewed refresh. Missing native personality stays null; an app-only trait is initialized once. Reviewed species/form mapping reuses the existing APP materialization/shared Home and bounded origin influence, preserving native stats/EXP/records separately. Automatic checksum/sequence authority and broader variants remain unverified; human copy authority does not assert checksum proof.
4. **Game-progress normalizers only per validated adapter.** A versioned native-change review now separates raw native parameter/EXP changes, temporary state, personal edits, location, identity/form and unknown data inside the Transfer hub. It preserves the unresolved queue and awards no app progression. Actual semantic normalization/equivalence remains gated on adapter/game evidence (sections 11 and 16).
5. **Writers and managed moves last**, per title and provider, following the section 11 commit/recovery design. Writers are not scheduled until the section 13 identity policy passes controlled samples.

Implementation progress is recorded in `TESTING.md`. These targets do not enable unverified game writes or imply that an aggregate count confirmation proves stable native individual identity.

### Dawn/Dusk extension step (2026-10-24, user-directed)

The user asked to start the next step: make transfer into **Digimon World Dawn** and
**Digimon World Dusk** work exactly like World DS, reusing the World DS implementation.
Planning is complete in [DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md) (scope,
parity matrix, evidence gates, work breakdown W0–W9, risks, inputs).

Key evidence and decisions:

- Dawn/Dusk (USA `A3VE`/`A6RE`) are documented as running on the World DS engine, but their
  persisted save is a different layout: 64 KiB image, two copies of `0x7B2C` at `0x0`/`0x7B80`,
  16-byte MD5 header over the zeroed-header copy, single-byte counter at `+0x18`, `DSDIG2`
  magic at `+0x10`, and **7 party records of `0x164` bytes at `0x288`**. World DS offsets are
  therefore not reusable; the shared code must be reorganized around a layout profile.
- Record fields beyond two move words, DigiFarm placement, identity/allocator semantics and
  the Dawn-vs-Dusk version marker are **unverified** and are the first evidence gate.
- Work 1 (`W1`) is a behavior-preserving refactor of the World DS adapter into an NDS Story
  engine-family layer and can start before any sample arrives; the Dawn/Dusk profile,
  reader, acquisition and writers follow only after the plan's gates G1–G5.
- Nothing is registered in production and no capability changes: Dawn/Dusk remain unreadable
  by the app until their reader passes the save-reading standard on real saves.

## 21. Consolidated implementation checkpoint and remaining gates

The current implementation has reached schema 55. Detailed commands/results live in
`TESTING.md`; this section distinguishes delivered code from accepted real-game support.

- Linked local sources, persistent tree/PC discovery, source health/cache retention,
  unlink exclusions and a paired read-only local-network PC companion are implemented.
- The separate reduced APP Home has been removed. Card-independent partners share the
  DIM Home composition and persistent applicable in-app parameters. Exact DIM/BEM stat
  assignment is an explicit basis override; source/native baselines and progression
  are independently retained.
- Guarded acquisition, reviewed refresh, canonical reference mapping, first APP
  materialization, lifetime events, app deltas and source-aware context are implemented.
  Native records and captured original source documents are preserved separately.
- Universal progress vectors, representation checkpoints, per-observation receipts,
  pending native classification and projection suppression are implemented. Synthetic
  tests cover zero-progress A/B/C/A fidelity and real deltas without conversion gains.
  Bounded origin influence is an authored/versioned app policy; per-game equivalence
  and real-game writer acceptance are not implied by those tests.

The following cannot yet be accepted or enabled from the available evidence:

1. **Fully verified Time Stranger identity/occupancy/progress policies:** counts and
   controlled rename evidence are confirmed; existing-corpus correlations support
   provisional main-array copies. Global identity/branch behavior and
   native progression/writer policies still need capability-specific evidence.
2. **Broader World DS and subsequent adapters:** the supplied USA/DraStic 64 KiB raw
   save now supports a read-only phone profile. Other variants, native checksum/sequence
   authority, acquisition and conversion need further evidence. Later titles follow
   the confirmed order with their own concrete format samples.
3. **Native writers, return conversion and managed cross-game moves:** validated
   field/growth/skill/equipment mappings, capacity/checksum/sidecar behavior, journals
   and actual game-load/recovery acceptance are required per title/provider. The
   section 11 journal/recovery state machine and byte-preserving rewrite rehearsal now
   exist desktop-side (rung 1) without enabling any writer. The read-only PC transport
   does not grant writer support.
4. **Native platform acceptance:** a connected Android device and actual local-network
   pairing/save refresh are needed for UI, Keystore/permissions, accessibility and
   phone timing/memory acceptance. Compilation and loopback desktop TLS tests are
   separate evidence.

The production registry contains provisional Time Stranger PARTY/BANK and World DS
PARTY snapshot policies. World DS IDs are source-local: independent documents are not
automatically merged by a repeated local number. Binding confidence stays PROVISIONAL,
and later continuity requires review. Farm/uncertain records and native insertion/removal
remain blocked. Native personality can be absent without inventing a native value.
Remaining game-specific gates are not completed by generic or synthetic-test coverage.

### Controlled sample collection workflow

The PC companion now supports offline labelled capture and comparison commands for
the pending Time Stranger experiments. See `pc-save-companion/README.md` for exact
commands. Capture copies the game-written encrypted document into private state,
never edits the original, and retains immutable manifests with integrity hashes.
Comparison distinguishes ordinary candidates from research-only farm/review evidence,
flags decoder-version changes on reanalysis, and reports aggregate differences without
publishing individual names or native IDs.

Use one known game action per before/after pair: unchanged resave, cross-slot save,
rename, personality, party reorder, party/bank/farm movement, evolution/de-evolution,
progression, duplicate-species individuals and restored branches. The user must perform
and save those changes in the game; synthetic edits are not a substitute. Reports aid
verification but never automatically register an identity policy or enable writers.

### Received controlled evidence

- **Rename pair inspected:** the known renamed party member remained in position 1
  at level 86, with unchanged nonzero candidate identity, unique within each decoded
  observation. Only name-buffer bytes changed; species, personality and decoded
  progression fields were stable. See `TESTING.md` for the aggregate receipt.
- This completes the first actual rename experiment, not the full native identity
  acceptance gate. Existing slots/backups were then analyzed as the user's preferred
  lower-effort route, rather than requiring another manual reorder experiment.

### Existing-corpus route and usable provisional copies

Eleven snapshots (seven slots, two backups and the rename pair) were inspected read-only.
Occupied main-array candidate values were unique within each snapshot and correlated
across observed form, personality, progression and party/bank-position changes. Slot
ordering was not treated as chronological. Farm candidates collide with different main
records; their ownership/matching semantics remain unresolved.

The reference preview permits **41 eligible main-array candidates** with identity review,
blocks all **five farm rows**, and keeps source removal/insertion disabled. Additional
manual pairs are only needed for specific unresolved capabilities, not as a prerequisite
for inspection or provisional party/bank copying.

Supported flow: link a local save or paired PC, select eligible party/bank records,
review and copy snapshots into Storage, then use Game archives to confirm a canonical
species/form and create an eligible APP representation with the shared DIM-style Home.
The native snapshot and original source remain intact; later identity matches require
review. This is not a managed move or verified native return.

Latest verification: 35 core JVM, 289 filtered app JVM, 20 SQLite and 8 companion tests
passed; debug assembly and integrity Android-test compilation passed. Instrumentation
and physical phone/LAN acceptance remain pending. See `TESTING.md` for boundaries.

### Phone-independent destination planning checkpoint

- Farm transfer is excluded by user choice and does not block party/bank work.
- Game archives now exposes destination selection with Party/Bank placement. Preparation
  rereads the destination and rechecks that the native snapshot is still latest/local;
  archives with an away World/watch presence cannot be used as local planning origins.
- Planner version 1 fingerprints permanent identity/native anchor, exact record facts,
  destination source/revision/profile/codec, placement, capability and evaluation results.
  Re-reading rebuilds the report and distinguishes changed from unchanged previews.
- Field reporting covers native record, species/form, name, personality, progress and
  skills/equipment. Retained Storage bytes are not claimed to be losslessly writable.
  No validated conversion or writer exists yet; all previews remain non-executable.
- Desktop `--preview-destination` probes the same planner without importing individuals.
  Two existing slots produced 41 eligible main records, five excluded farm rows and
  37 origin records with possible destination matches. These are review candidates,
  not verified individual matches. No saves or collection/progress receipts were written.
- Verification: 45 core JVM, 293 filtered app JVM and 8 companion tests passed; debug
  assembly and integrity Android-test compilation passed. No phone tests were run.

### Historical World DS research checkpoint preceding phone support

The desktop companion and core inspector now provide `--inspect-world-ds <file>`.
Pinned public USA (`ADNE`) structures support an independently implemented, bounded
raw/DeSmuME-v0 structural probe: 0x7DC0 candidate blocks, forty 0x180 records, native
stats/aptitude, encoded names and eight separate EXP counters. Exact record bytes
and conflicting/guest/lent/egg/reference evidence remain separate. Farm references
are inspected solely to avoid misclassification; transfer is excluded.

This research path neither selects a current block nor claims verified region,
checksums, identity, ownership, import or progress conversion. It is not registered
as a production reader and does not expand automatic discovery. No authorized real
World DS save has been inspected yet. One existing save plus actual region/emulator
information can support the next validation pass without phone testing or a new
manual experiment checklist. Details: `game-save-core/WORLD_DS_RESEARCH.md`.

Verification: 58 core JVM, 293 filtered app JVM and 11 companion tests passed; debug
assembly, integrity Android-test compilation and companion distributions rebuilt.

### Phone-first World DS reading checkpoint

The user supplied a newly created USA save played on DraStic and prioritized opening
that persisted file in the phone app. The shared registered raw reader now decodes
the exact 65536-byte file as one party candidate in one populated sector; the other
sector is erased. No native bytes, names or local IDs are copied into public fixtures.

Transfer → Link save file accepts the regular `.sav` directly via Android SAF, opens
the roster after refresh and displays decoded supported name glyphs and native stats,
aptitude and separate EXP channels. Folder discovery also accepts these files. Local
phone reading requires no PC companion. Cached observations retain native parameters;
World DS remains view-only with no acquisition controls, fabricated native personality
or scalar-EXP normalization. Several populated copies are shown separately for review.

Native integrity/sequence authority, ownership/lineage and writers remain unverified.
The earlier DSDIGI signature probe is not the recognition rule for this observed raw
profile. Details, pinned name-code evidence and limits: `game-save-core/WORLD_DS_RESEARCH.md`.

Verification: 67 core JVM, 295 filtered app JVM and 11 companion tests passed. Debug
assembly and integrity Android-test compilation passed; schema remains 52. Physical
phone acceptance remains deferred as requested. The actual save was read through the
same shared registry used by the Android app, without modifying the source file.

### Schema-53 World DS acquisition/Home checkpoint

The phone now supports the requested World DS save → Storage → shared Home flow using
provisional party copies and reviewed species/form mapping. Multiple-copy authority is
persisted for an exact source revision/profile/codec; a new file revision expires it.
Unselected copies remain visible but ineligible. Fresh preview and acquisition checks
reject affected stale choices. Native checksums/automatic sequence rules are not claimed.

World DS native personality remains NULL. Its app-only personality is initialized once
from the permanent app UUID and preserved on refresh/materialization; native stat/EXP
data remains exact and separate from authored app starting values. Local-ID dedup is
source/document scoped, preventing false merges between independent saves. Retry receipts
and first-materialization receipts continue preventing duplicate/replayed app acquisition.

The 52→53 upgrade preserves native rows, child references, container bytes, triggers,
identity/history and autoincrement state while enabling null native personality. Desktop
verification: 70 core, 296 filtered app JVM, 11 companion and 21 SQLite checks passed;
debug assembly and integrity Android-test compilation passed. New native acquisition/
refresh/materialization cases compile but have not run on a phone. The supplied save
produces one eligible provisional party preview with source insert/remove both false.

### Unified transfer hub and progress-review checkpoint

Home's upper-left entry now opens a single Transfer hub with Game saves, Storage
transfers and Devices tabs. Archive species/form review, app creation, source history,
native changes and destination previews are hosted there; Storage's transfer actions
carry partner context into the same hub. Compatible bracelet preparation and existing
NFC operations remain under Devices with active-operation tab/selection guards.

Native change review v1 compares recorded Time Stranger/World DS native snapshots in
their original units. It distinguishes permanent parameter/EXP-channel changes from
temporary HP/MP, personal edits, location, form/history, identity and unknown bytes.
Viewing it never normalizes growth, acknowledges pending changes, rewrites game data
or creates progression receipts. That is the next evidence boundary before real
per-game normalization, not a claim that native growth equivalence is solved.

Verification: 77 core, 298 filtered app JVM, 11 companion and 22 SQLite checks passed;
debug assembly and integrity Android-test compilation passed. The controlled rename
pair produced 38 unchanged +1 personal-edit reports, zero changed native measures and
zero enabled normalization. Physical-phone/UI/NFC acceptance remains deferred.

### Progress-aware destination review checkpoint

Destination previews no longer report progress only as "unvalidated/unmapped". A new
core `DestinationProgressAssessment` accompanies each report with one of four statuses
(context unavailable, no recorded changes, recorded app changes without a validated
game mapping, unresolved native/destination changes) plus pending native-review count,
changed destination records under matching identity evidence, and whether a retained
native baseline matches the destination game/profile/codec.

The app repository supplies the context as a pure read: synchronized ledger progress
plus unsynchronized app deltas summed in canonical app units (no receipts, no journey
writes), owner-scoped pending native ids (bounded 32) and up to 16 retained anchors
with original-document/native-progress retention flags. Fingerprints now bind account
revision, latest app delta, latest native snapshot id, the recorded vector, sorted
pending ids and the anchor set, so later app growth or a native review invalidates
`isCurrent` and forces re-preparation. Five new blockers (context unavailable, anchor
missing, native unresolved, destination record changed, mapping unavailable) keep the
report non-executable, and the dialog renders them in a dedicated progress section.
Recorded units never convert: raw EXP channels remain native and `canExecute` stays
false for every status.

Verification: 83 core, 298 filtered app JVM, 11 companion and 22 SQLite checks passed;
debug assembly and integrity Android-test compilation passed. Six new core cases cover
the statuses, ordering-insensitive pending ids and foreign-anchor exclusion. The
desktop `--preview-destination` probe intentionally reports context unavailable (it runs
without app progress) and remains non-executable. No schema change. Phone acceptance
and any recorded-to-native mapping remain deferred.

### Batch destination planning checkpoint

Plan 4.4's "every selected individual" is now covered without weakening the read-only
boundary. `DestinationPreviewPlanner.batch` evaluates 1–16 unique origins (unique
individual and native anchor) against a single destination observation, reusing the
per-item preview exactly. The batch fingerprint hashes sorted item fingerprints, so
re-selecting partners in a different order never rebuilds the report, while any
per-item change — including recorded progress — does. A new `BATCH_IDENTITY_CONFLICT`
blocker flags two selected partners sharing identity evidence and changes nothing else;
shared restrictions are exposed as the `commonBlockers` intersection and `canExecute`
stays false for the whole batch.

`DestinationPreviewRepository.prepareBatch` reads the destination once, re-loads every
origin before and after that read, and rejects mid-read anchor/progress changes with
`DestinationOriginChangedException`. Duplicate snapshot ids and selections beyond the
bound are rejected before any file or database mutation could occur. The archive dialog
now offers an optional **Include other partners** checklist (single-partner layout
unchanged, fields table retained for one item only), a batch summary, shared
restrictions and per-partner status/blocker rows, localized in EN/PT-BR/JA.

Verification: 87 core, 301 filtered app JVM, 11 companion and 22 SQLite checks passed;
debug assembly and integrity Android-test compilation passed. Four new core cases cover
rejection, conflict flagging, fingerprint binding and item isolation; three new app
cases cover the single destination read, mid-read rejection and invalidation on
recorded-progress change. No schema change. Phone acceptance, capacity checks and any
execution confirmation remain deferred.

### Writer-ladder groundwork checkpoint

Plan item "native writers and managed moves" has begun with its safest rung. Core
`TransferCommit.kt` encodes the section 11 commitment order: a revision-bound commit
plan with candidate hashes and document descriptions, a strictly ordered journal
(PREPARED → BACKED_UP → CANDIDATE_READY → WRITTEN → VERIFIED → ACKNOWLEDGED, with
RECOVERY_REQUIRED only before verification and a mandatory backup revision before
candidate readiness), source release gated on acknowledgement, and recovery decisions
that resume an unwritten destination, re-verify an already-written candidate and demand
manual reconciliation for anything else — never a blind overwrite. Rung 1
(`NOOP_REWRITE`) is the only intent: the candidate must equal the planned source bytes.

`--write-rehearsal <save-file> <output-dir>` rehearses the whole pipeline on copies:
backup hash verification, a source recheck immediately before the write, refusal of
output locations inside the source folder, candidate copies written only to the
provided directory, then readback hash and full re-parse equivalence before
acknowledgement. Encrypted formats must additionally re-encode their decoded bytes
byte-identically first: the internal deterministic `TimeStrangerContainer`
(`TimeStrangerSaveReader.kt`) is the first adapter's rung-1 codec, and the real
authorized `0001.bin` completed `codec_roundtrip=verified` through the full journal.
The authorized DraStic World DS save completed the same rehearsal (raw copy path). Both
left their game files untouched. No capability changed: `canExecute`, source-remove and
insert all still read false, and no execution confirmation exists in the app.

Verification: 99 core, 301 filtered app JVM, 11 companion and 22 SQLite checks passed;
debug assembly and integrity Android-test compilation passed. Twelve new core cases
cover revision binding, transition legality, release gating, the recovery matrix, the
codec round-trip and the copy-only rehearsal. No schema change.

### Rung-2 rename writer checkpoint

The first real field edit exists and was validated against the game's own controlled
rename, still with zero game writes. Controlled samples (`name-test` Before/After)
established the exact game behavior: renaming overwrites the record's name window from
the name offset with the new UTF-8 bytes plus one terminator and leaves the rest of the
window untouched, so stale tail residue from previously longer names survives ("Gritaria"
becomes "Kuzuhamon" while an old trailing "a" remains); names resolve only up to the
first terminator. Seven further copies of the old name in a history-looking structure
near 0x226480 were not rewritten by the game. Digest searches found no stored
MD5/SHA-1/SHA-256/CRC32/Adler32 or per-block CRC tied to the rename; the only other
changed bytes were playtime digits, the save timestamp and a small live-state tail.

Core `TimeStrangerEdits.renameIndividual` (`TimeStrangerWrite.kt`) mirrors that
semantics exactly for one active party/bank record: strict name validation (1..31 UTF-8
bytes, no terminator, no control characters), farm and review-boundary records refused,
candidate re-decoded with a byte-for-byte outside-window equality check and a re-read
name check, and no file writing by the writer itself. The desktop-only
`--edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file>` mode resolves the
selector from a fresh observation, refuses output inside the source folder, takes exact
name bytes from a file (never the command line) and reports aggregate counters only.

Desk verification: applying the extracted 9-byte name to the `Before.bin` copy (party:1)
produced a candidate whose only differences from the game's own `After.bin` are volatile
fields (playtime, timestamp, live-state bytes at 0xfded0/0xfe1a4/0xfe1ac); the 10-byte
name difference present in the raw Before→After comparison vanished entirely. Source and
evidence hashes confirm no game save was modified. Five new core cases bring core to
**104 tests**; consolidated offline verification (core, filtered app JVM, companion,
SQLite checks, debug assembly, Android-test compilation, companion ZIP) passed, and
capability flags remain false.

Corpus validation over the seven authorized save slots confirmed the adopted layout at
scale (identical record phase, zero species-parity mismatches, unique nonzero creation
hashes, sequential farm slots, bounded scan table, talent ×1000 scale) and falsified one
baseline claim: the forward linked list is mostly maintained but not authoritative (two
stale non-species pointers in slot 0002), so the reader keeps ignoring it and future
writers must rebuild chains themselves. The corpus retires several pending
controlled-sample asks; only operations the seven slots cannot demonstrate (fresh
recruit/insert and removal) still need fresh samples.

In-game acceptance passed on 2026-10-16: the user swapped a desk-built slot-0000
candidate (party-position 1 rename, exactly 8 changed bytes in a single window run) into
the save folder and the retail game loaded it normally with the new name displayed — the
game accepts our container writing and this first field edit at the rename scope, with no
checksum or signature gate observed. The swap was user-performed; the tool and pipeline
never write to savedata.

A second experimental rung is staged from the same corpus analysis that mapped the
roster's inactive tail (contiguous active prefix, fully intact released records behind
it, chain running through the tail into a zero-hash terminator). Core
`TimeStrangerEdits.activateInactiveIndividual` and `--edit-activate` flip exactly the
stored flag of the first inactive bank record — one byte, desk-verified with an
independent diff and roster re-read — producing a candidate whose active prefix is
contiguous, the shape the game itself writes. It awaits user in-game acceptance on slot
0004; acceptance would settle whether the stored flag alone controls ownership.

Remaining after this checkpoint: insert/remove and non-name field edits, sidecars,
World DS checksum/sequence research and the plan 4.5 history/recovery UI.

### Dawn/Dusk extension planning checkpoint (2026-10-24)

- **Planning deliverable:** [DAWN_DUSK_TRANSFER_PLAN.md](DAWN_DUSK_TRANSFER_PLAN.md)
  defines the parity target ("exactly like World DS" per capability), the NDS Story
  engine-family architecture, Dawn/Dusk evidence gates, the W0–W9 work breakdown, the
  reviewed Dawn-vs-Dusk title-binding design (schema 56, additive) and the in-game
  acceptance protocol. No code or capability changed in this step.
- **Evidence established for planning (not yet accepted as app support):** Dawn/Dusk use
  the World DS engine but a distinct save layout — 64 KiB image, copies of `0x7B2C` at
  `0x0`/`0x7B80`, MD5 header with `[0x00,0x10)` zeroed, `DSDIG2` at `+0x10`, u8 counter at
  `+0x18`, player name `0x238`, tamer data `0x3A28`, party records `0x164` bytes at `0x288`
  (7 slots; moves at `+0x54/+0x58`). Independent corroboration: GBAtemp AR-code stride,
  DeSmuME's 512 kbit/164-byte-header statement, and the `0x1C0` Dawn/Dusk RAM delta. The
  published record beyond moves remains unverified and must be measured on the user's saves.
- **Species/text:** Dawn/Dusk expose 398 species IDs `0x41–0x1F4` (vs World DS 426) and a
  different ROM glyph table, so per-title species assets, crosswalks and a glyph codec are
  required; the World DS tables must never be reused for DS2.
- **Consequence for implementation order:** first refactor World DS into a shared NDS Story
  family without behavior change (W1); then verify the DS2 layout/record fields from real
  saves and ROMs (W2); then read-only phone parity (W3), acquisition/Home (W4), destination
  preview (W5), exact retained move/send with in-game acceptance (W6), generated sends (W7),
  and UI/docs/PC parity (W8). Every rung keeps the DS guarantee set: revision-bound copies,
  exact bytes, backups/journal/readback, and no writer flag until the game itself accepts.
- **Next external inputs:** one Dawn and one Dusk USA raw save (fresh and/or progressed),
  controlled before/after pairs for record-field mapping, and the USA `A3VE`/`A6RE` ROMs (or
  the JSON produced by the generalized extraction tool). Details and the exact artifact list:
  plan sections 6 and 10.
- **Status:** World DS remains the only accepted DS Story adapter; Dawn/Dusk have no reader
  registration, no acquisition profile, no writer and no UI enablement yet. Time Stranger
  transfer work stays paused.

### NDS Story family refactor checkpoint (W1 delivered 2026-10-25)

- The World DS adapter is now the first registered **NDS Story profile** rather than a
  standalone codec: `NdsStoryProfile`/`NdsStoryRecordLayout`/`NdsStorySpeciesData`/
  `NdsStoryTextCodec`/`NdsStoryCopyHeaderRule` plus `NdsStoryProfiles.WORLD_DS`;
  `NdsStorySaveReader`, `NdsStoryRecordCodec`, `NdsStoryCopyIntegrity`,
  `NdsStoryCopySelection`, `NdsStorySaveEditor` + `Ds1SaveEditor`, and
  `NdsStoryConversionPolicies`. All container sizes, copy geometry, integrity rule, record
  windows, species count and glyph codec are profile values.
- `WorldDsSaveReader`, `WorldDsRecordCodec`, `WorldDsCopySelection` and the audited
  `WorldDsPartyEdits` byte windows remain the stable World DS contract (facades/delegates),
  so app callers and the desktop evidence path are unchanged. `SaveReaderRegistry` builds one
  reader per registered profile and `SaveDiscoveryPolicy` derives sizes from the same
  registry, so a second layout does not require editing either. The research inspector is
  profile-driven and the core CLI gained `--inspect-nds-story`.
- App plumbing: the ten `== WorldDsSaveReader.GAME_ID` equality sites and the library/send
  destination gates resolve through `TransferGameCapabilities` (profile + editor registry);
  write/journal buffers size from the profile. UI behavior is unchanged while Dawn/Dusk have
  no registered profile.
- Verification: **191 core**, **155 filtered app JVM**, **11 companion** and **22 SQLite**
  checks passed, zero failures; integrity Android-test Kotlin compilation and debug assembly
  passed. Schema stays **55**; no migration. Fixtures remain synthetic: this is a refactor
  checkpoint, not new game support and not a Dawn/Dusk acceptance.
- Next gate unchanged: the Dawn/Dusk evidence pack (`DAWN_DUSK_TRANSFER_PLAN.md` W2) needs
  one real USA save per title, controlled before/after pairs and the USA ROMs (or extracted
  JSON) before a second profile, reader registration or any writer flag.

## 22. Risk register

Ordered by current blocking power. Each risk names the evidence that would close it.

| # | Risk | Impact | Mitigation and closing evidence |
|---|---|---|---|
| 1 | **Format-evidence coverage:** World DS has one observed USA/DraStic raw save; native writer/identity evidence remains incomplete | Broader variants and verified native moves remain blocked | Retain the working phone-local reader, gather capability-specific evidence only as needed and keep farm transfer excluded |
| 2 | **Single-codec validation:** every "game-neutral" contract is proven against one format only | A second format forces discovery/identity/ledger redesign | Build the World DS reader as the first structural gate (section 16, Phase G) and record each Time Stranger-shaped assumption it breaks |
| 3 | **Identity semantics unresolved:** `creation_hash` collision/reuse/branch behavior unknown | Wrong merges, double awards, dishonest history | Controlled samples (section 13); uniqueness only for established identifiers; conflicts stay reviewable (section 8) |
| 4 | **Writer feasibility:** sidecars, memory cards and multi-field game acceptance are unbuilt (Time Stranger rename shows no stored checksum; desk output matches the game's own rename, and a user-swapped renamed slot loaded normally in the retail game on 2026-10-16) | Remaining rungs (insert/remove, field edits beyond names) are unproven; saves could be corrupted | Section 11 commit/recovery design; per-title ladder: identical rewrite, no-op insert/remove, then real moves; capabilities stay declared false until each rung passes |
| 5 | **Verification lag:** desktop checks pass but this transfer increment has no device runs | Keystore, LAN pairing, SAF permissions, accessibility and performance fail late | Phone acceptance is deferred by user request; desktop receipts do not count as platform or game evidence (section 23) |
| 6 | **Scope inflation:** a ~950-line plan against a ~2k-line read-only slice | Maintenance overhang; speculative abstraction | Implement container codec, planner and coordinator only at first real use; delete unused contract names rather than keeping placeholders |
| 7 | **Reference-project licensing** (PKVault/PKForge GPLv3-family; DSTS-Save-Editor unlicensed README, author permission reported by user) | License contamination if code or libraries are reused without a grant | Sections 3 and 19 keep PKVault/PKForge as concepts only; ANAMNESIS reuse is allowed per the author's reported permission with attribution in section 19, and correctness is still validated independently |
| 8 | **Provisional-feature drift:** snapshot copying mistaken for verified identity or native moves | False expectations or unsafe identity merges | Persist provisional confidence, require continuity review and keep farm/writer gates explicit |
| 9 | **Dawn/Dusk "same engine" misread as "same layout":** the current save evidence shows different copy size/stride and a different record region | Copying World DS offsets would corrupt saves or garble identities | Shared NDS Story family with per-layout profiles; DS2 constants only after real-save verification (plan G1–G2); no DS2 writer flag until in-game acceptance |
| 10 | **DS2 record internals are undocumented beyond moves** (party table known, DigiFarm/identity/empty-slot semantics unknown) | Reader/roster/acquisition/send may stall or guess fields | First-class evidence gates and controlled samples; party-scope read-only first; farm excluded; block unmapped fields rather than inventing them |
| 11 | **Dawn vs Dusk indistinguishable in-save** (same layout expected) | Wrong title label, defaults or species interpretation | Reviewed, revision-bound title binding (schema 56); automatic detection only if evidence proves a reliable marker; never silently rebind |
| 12 | **Research-note licensing:** Dawn/Dusk notes say "educational and community use only" with no license grant; the ROM editor is GPL-3.0 | Contamination if code/assets are copied | Facts-only citation and independent reimplementation; no vendored code/assets/ROMs; user supplies own ROMs/saves; credits in section 19 |

## 23. Critical path and external inputs

The following external inputs are ordered by what they block. None can be replaced by more framework code or synthetic tests.

1. **Capability-specific Time Stranger evidence:** the rename pair and existing corpus already support provisional copies. Additional controlled evidence is needed only for unresolved native identity/branch/progression/writer claims, not as a blanket manual checklist. Farm transfer is excluded.
2. **Broader Digimon World DS evidence:** the USA/DraStic raw fixture and phone reader are present; other variants and later conversion/import policies need their own evidence. The save-copy integrity header and writer reseal are now solved for the observed profile (see `WORLD_DS_RESEARCH.md`).
3. **Dawn/Dusk evidence:** one real USA raw save per title (fresh and/or progressed), controlled before/after pairs for the party record fields/identity, and the USA ROMs (or extracted JSON) for species/defaults/lineage extraction. These block the Dawn/Dusk reader and every later step; plan G1–G4.
4. **A physical Android device with local-network access:** Transfer UI, Keystore pairing, SAF permission lifecycle, accessibility, timing and memory acceptance.
5. **Per-title decisions from section 18:** later editions, memory-card formats, companion packaging and network acceptance.
6. **Writer acceptance evidence:** per-title undefined-field behavior, checksums and an actual game-load/recovery test — for Dawn/Dusk the boot/swap protocol in the plan's section 8.

Work that proceeds in parallel while the inputs above are pending:

- Dawn/Dusk work 1: the behavior-preserving NDS Story engine-family refactor, no evidence required.
- Device acceptance of the existing slice when the user resumes phone testing.
- Destination evaluation and preview (plan 4.3/4.4) is implemented for native archives,
  now including recorded app growth, retained native anchors and unresolved-change
  counts; validated capacity/conversion details can be added when real adapter evidence exists.
- Contract consolidation and honest capability reporting using the mapping in section 6.
- Keeping the synthetic ledger invariants green, without presenting them as game evidence.

**Standing evidence rule:** a JVM, compile or loopback result is never accepted as evidence of game, device, writer or platform behavior. Phase acceptance uses the strongest available evidence class and labels it explicitly.
