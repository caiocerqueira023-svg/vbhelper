---
version: 1
slug: "storage-scan-collection"
primary_target: "app/src/main/java/com/github/nacabaro/vbhelper/screens/storageScreen/StorageScreen.kt"
related_targets: ["app/src/main/java/com/github/nacabaro/vbhelper/screens/storageScreen/StorageScanCollectionDialog.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/dialogs/DigimonScanConversionDialog.kt"]
---

# Storage scan collection

Mode: Operate. Precisely requested extension of the existing Storage surface.

## Direction contract

THESIS: One top-left Scan data action connects every partially or fully scanned species to Storage conversion.
OWN-WORLD: Inherit native Material controls, the existing header chips, cut-corner tonal dialogs, cyan progress, and imported pixel art.
STORY: Open scans, identify the species and card version, read its percentage, select a completed scan, optionally name it, and convert into Storage.
FIRST VIEWPORT: Header Scan data action opposite Adventure; a titled, scrollable list with completed scans first. Each entry shows portrait, imported name, card, percentage, and a conversion action enabled only at 100%. Close and operation feedback remain outside the scrolling list.
FORM: Local code-led extension using the existing conversion transaction and a shared preview dialog; no new visual world or raster assets.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Loading, empty, read failure/retry, conversion failure, and successful removal of a consumed scan are explicit. The action remains available when Storage is empty. English, Brazilian Portuguese, and Japanese strings follow existing localization. Native visual/runtime verification retains the existing build-check-only scope.

## Implemented surface contract

- Storage's top-left Scan data icon chip preserves the opposite Adventure action
  and remains available with empty Storage.
- `DigimonScanDao.observeCollection()` includes every positive scan across local
  imported `cardCharacterId` entries. It sorts percentage descending, then card
  name case-insensitively, character index, and local ID; completed entries lead.
- Rows reuse imported pixel portraits and name art, with canonical species name
  or fallback, card name, percentage, and cyan progress. Local character sprite
  and profile joins preserve imported-card identity. Partial entries remain
  visible; only 100% entries enable Convert. Button readiness remains percentage-based;
  the conversion transaction separately revalidates current-mode eligibility.
- The collection uses a medium cut-corner tonal Material Card on
  `surfaceContainerHigh`, capped at 480dp/92% width and 88% window height.
  The `LazyColumn` scrolls rows; operation feedback and Close sit outside it.
  Loading, loaded-empty, and read-failure/Retry states are explicit.
- Storage and existing Dex details share the extracted
  `DigimonScanConversionDialog`, with preview and optional nickname, using the
  existing guarded backend. Consumption makes the zero-percent entry disappear
  through the Room flow; a retained selected snapshot keeps its preview alive
  through that emission until completion. Conversion feedback remains available
  after the final row disappears.
- English, Brazilian Portuguese, and Japanese strings are supplied. The extension
  originally introduced no schema change, new raster assets, or global design-token
  changes; the participation follow-up below uses additive schema 43.

## Original scan-collection finish disposition and evidence

The original scan-collection source finish review returned **ship**, with no
material introduced bugs. This is a source-level disposition, **not visual or
Android runtime approval**.

The implementation pass supplied the following successful checks; this documenter
pass records them without rerunning them:

- `py -3 -B scripts/test-digimon-scan-db.py`: **eight passes**, comprising three
  new actual-production collection SQL checks (filtering/order, local art/profile
  identity, consumed-row disappearance) and five existing migration invariants.
- `:app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.source.DigimonScanPolicyTest"`,
  `:app:compileIntegrityCheckAndroidTestKotlin`, `:app:lintDebug`, and
  `:app:assembleDebug`, run offline with plain console and two workers:
  **BUILD SUCCESSFUL**, **nine focused JVM passes**, Android tests compiled,
  lint with **174 existing warnings/seven hints and no errors**.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `StorageScanCollectionTest`: three **compile-only** cases for the header action
  and Adventure preservation, partial/full conversion controls, and simulated
  reactive removal with empty-state feedback/Close. No device execution or
  screenshots; compilation does not verify actual Room flow delivery or geometry.

The earlier 438-case full-unit result remains historical evidence in `TESTING.md`;
it was not rerun for this extension. Existing `PRODUCT.md`, `DESIGN.md`, tokens,
and other surface briefs remain the incumbent records. No new system rule or
unverified visual measurement is canonized by this finish pass.

## Deferred native acceptance

- Actual Scan chip corner geometry, physical hit area, and empty-Storage entry;
  Adventure placement and destination.
- Populated-list scrolling, large fonts, long card/species names, localized
  EN/PT-BR/JA content, crisp imported pixels, and pinned feedback/Close.
- Partial-disabled/full-enabled Convert and ready-first local-card identity.
- Shared Storage/Dex preview cancel, system Back, optional nickname, IME clearance,
  focus/return focus, and guarded controls during conversion.
- One live conversion with immediate Storage update, correct inactive individual
  and VB/BE profile, consumed-row removal, snapshot retention until completion,
  and last-row empty feedback/Close.
- Rotation/restart, persisted state, and no duplicate conversion.
- Loading, loaded-empty, read failure/Retry, and conversion-failure recovery.

See `TESTING.md` for the exact current commands, historical verification records,
and the distinction between host SQLite, JVM, compiled Android, and native evidence.

## Source-mode participation follow-up (milestone 3 step 2)

- The database-owned `PartnerParticipationGate` shared through `DefaultAppContainer`
  guards scan conversion and partner gameplay. Live current-form binding precedes
  the individual acquisition/last-known species snapshot; aliases/catalog IDs share
  loaded access, and unidentified imported slots retain VB support.
- Startup mode/DB reconciliation preserves the collection and records, clearing
  only ineligible active partners. Rejected activation retains a valid selection.
  Storage badges inactive partners, selectors exclude them, `StorageDialog` keeps
  history while disabling gameplay, and `ChatScreen` disables sending while inactive.
- Item use, activation and watch export cannot bypass eligibility; watch import
  retains source measurements without unavailable autoactivation. Farm/Adventure
  pause intervals survive restart through `ParticipationPause` and **42 → 43**;
  Adventure deadlines rebase at resume. Foreground battles and separate pending
  settlements hold mode-switch leases. The **41 → 42** nullable `DEFAULT NULL`
  correction preserves compatibility through the migration chain; no destructive reset.
- This is an enforcement increment, not completed card-independent play.
  Card-independent representation, full-catalog encounters, first-partner onboarding,
  source-mode default art, real external-game/VPet adapters, permanent universal
  evolution and compatible non-VB-to-watch conversion remain implementation work.
- Supplied checkpoint: **125 targeted JVM passes, zero failures/skips**, **four
  actual Python sqlite3 passes in <0.1s**, debug assembly and integrity Android-test
  compilation. The final small recruitment-guard signature change and added native
  regression are still recompiling at handoff; no new final result is recorded.
  `PartnerParticipationPersistenceTest` has **seven compile-only cases**.
- All six material source-review findings have fixes recorded; the final narrow
  recruitment verdict is pending. The original scan-collection **ship** disposition
  does not approve this follow-up. No new lint/full-suite result or native approval
  is claimed. Material/branding are inherited, with no new rasters or global tokens.

## Appended participation native acceptance boundary

### Current presentation update

Unavailable Storage partners now use grayscale sprites and neutral gray frames,
vitals rings and favorite markers instead of visible availability messages. Their
cards still open details/history; gameplay remains gated, and TalkBack receives
the inactive state description. Detail portraits use the same grayscale state.
VB-only saved normalization and Expanded Dot defaults remain separate. Native
captures for this update still require a connected device.

No ADB device is connected; no new screenshots, TalkBack or large-font acceptance
is recorded. On-device acceptance must cover mode/card-change reconciliation,
inactive badges and excluded selectors, readable history with gameplay/sending
disabled, successful eligible full-scan conversion and unavailable-mode rejection
without consuming a scan, and rejected activation preserving a valid selection.
Execute the seven Room persistence cases, chain upgrades and restart/resume
pauses; verify stale
quest/recruitment requests, fresh/frozen Arena and pending-bracket eligibility,
watch-round-trip individual mapping, separate battle/settlement leases and watch
import/export gating. Inspect inherited palettes and EN/PT-BR/JA with large text,
TalkBack and native Back/IME/focus behavior. See `TESTING.md` for the exact boundary.
