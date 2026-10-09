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
  visible; only 100% entries enable Convert.
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
  introduces no schema change, new raster assets, or global design-token changes.

## Finish disposition and evidence

Source finish review returned **ship**, with no material introduced bugs. This is
a source-level disposition, **not visual or Android runtime approval**.

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
