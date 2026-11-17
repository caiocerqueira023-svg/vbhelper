# Implementation result — VPet support in Tamers Lab

Status: **implemented (all phases in one pass)** on 2026-11-16. English-only code, per the
repo conventions. Decisions D1–D8 from [PLAN.md](PLAN.md) were applied.

## Delivered

### `:hardware-vpet` (new Android library module, `com.tamerslab.hardware.vpet`)

- Protocol ported 1:1 from the recovered `lector.js`: `VpetModel`, `VpetDigiRom`
  (DM20/DMX/Pen20/PenZ/DMC/PenC commands + nibble/sum checksums + color block host XOR),
  `VpetDecoders` (all six decoders, version learning), `VpetModelSpec`, `VpetProtocol`,
  `VpetTables` (17.5 KB list resource `vpet-lists.txt` generated from the APK's `LISTAS`),
  `VpetReadSession` (30 s request timeout, 1.5 s re-arm, progress, version-learn re-request,
  8 attempts, Color resend rules) with `TimerVpetScheduler`.
- Transports: `AcomUsbLink` (usb-serial-for-android, 9600 8N1, DTR/RTS, prober + CDC/ACM
  fallback, USB permission PendingIntent), `BtComLink` (BLE NUS UUIDs, chunked write queue,
  no-response writes, 60 ms retry/watchdog), `BtSerieLink` (SPP), `VpetBluetoothDiscovery`
  (paired SPP + BLE scan), `ScriptedVpetLink` + `VpetSimulator` for hardware-free runs.
- Generator: `tools/vpet/extract-lists.mjs` (extracts lists and golden vectors from
  `recovered/lector.js`; idempotent). Golden data: 96 ROM vectors, decoder and
  simulator vectors.

### App layer

- Domain: `domain/vpet/VpetModels.kt` (`VpetReadSnapshot`, `VpetPendingRead`,
  `VpetPendingReason`, `VpetSpeciesChoice`, `VpetImportOutcome`, `VpetRepository`).
- Data: `transfer/vpet/VpetImportService.kt` (canonical species resolution with aliases →
  catalog definition → participation gate → `AppPartnerFactory.createLocked` with
  `origin = IMPORTED`, **no Room migration**; unknown/blocked reads become pending),
  `VpetPendingReadStore` (DataStore JSON), `VpetImportPolicy`, `VpetReadSnapshotMapper`;
  DI: `ContainerVpetRepository` + Koin binding + `DefaultAppContainer` lazy fields.
- UI: `transfer/ui/vpet/` (`VpetSectionScreen`, `VpetReadCoordinator`,
  `VpetDevicePicker`, `VpetResultCard`, `VpetPendingReads`, `VpetPreferences`), a new
  **“Virtual pet”** section in the Transfer hub (menu + page + title + back handling),
  103 new strings in `values` (English) and `values-pt-rBR` (Portuguese; `values-ja` got
  English fallbacks so the lint translation gate stays green).
- Manifest: USB host + BLE features (optional) and Bluetooth permissions
  (`BLUETOOTH_SCAN`/`CONNECT`, legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` ≤ API 30).
- Gradle: `settings.gradle.kts` include, `app` dependency, version-catalog entry for
  `usb-serial-for-android` 3.8.1.

### Behaviour

1. Transfer → ⋮ → **Virtual pet** → choose model (remembered) → connect A-Com (USB) or
   BTCom (BLE/SPP) → **Read** → progress/log → decoded result card.
2. **Import to Storage** creates a new APP partner (`DeviceType.APP`, `originKind =
   IMPORTED`, nickname = decoded name). Every read creates a new individual (no dedupe).
3. Unknown species or blocked participation → read is kept in **pending reads** with full
   provenance and can be mapped later (catalog search) or discarded.
4. Debug builds expose **Simulate read** (no hardware needed); the session/decoder path is
   the real one.

## Verification (run after all implementation landed)

| Command | Result |
|---|---|
| `.\gradlew.bat :hardware-vpet:test` | **42 tests, 0 failures** (22 decoders, 6 ROMs, 14 session) |
| `.\gradlew.bat :app:testIntegrityCheckUnitTest` | **full app JVM suite passes**, including 20 new VPet tests (`VpetImportPolicyTest` 7, `VpetPendingReadCodecTest` 7, `VpetPendingReadStoreTest` 6), `ArchitectureBoundaryTest` (4) and `KoinModuleGraphTest` (1) |
| `.\gradlew.bat :hardware-vpet:lintDebug` | **0 errors** |
| `.\gradlew.bat :app:lintIntegrityCheck` | **0 errors**, 562 warnings (baseline-filtered; blocking gate green) |
| `.\gradlew.bat :app:assembleIntegrityCheck` | **BUILD SUCCESSFUL** (`app-integrityCheck.apk`, 234.4 MB) |
| `.\gradlew.bat :app:assembleRelease` | **BUILD SUCCESSFUL** (R8; `app-release-unsigned.apk`, 146.8 MB) |

All of the above were re-run after the final resource-string fix; the last full run finished
green (lint `integrityCheck` 0 errors, module lint 0 errors, both APKs rebuilt).

Packaging checks: `vpet-lists.txt` is present in the APK; R8 `mapping.txt` keeps
`com.tamerslab.hardware.vpet.*` classes and `com.hoho.android.usbserial.driver.UsbSerialProber`.

## Incidental fixes required for the adopted gates

- `ItemCatalogue.kt` had a pre-existing lint error (`@StringRes` on a drawable resource id)
  and two pre-existing untranslated strings (`settings_debug_legacy_llm_*`). They were fixed/
  translated (pt-BR + English fallback in ja) so `:app:lintIntegrityCheck` can pass; no
  behaviour change.

## Known limitations / not verified

- **No hardware available** (D8): USB A-Com, BLE and SPP transports compile and are covered
  by the scripted/simulated path, but were not exercised on a physical VPet adapter. A
  manual checklist is in PLAN.md; first real session may need transport tuning (especially
  BLE write callbacks on some stacks).
- No device was attached during verification (`adb devices` empty), so the app was not
  installed/launched; Compose UI is compile/lint-verified only.
- Writing to the VPet is out of scope (D1); the protocol layer is prepared for a future
  write session but no write commands exist.
- `values-ja` uses English fallbacks for the new strings (the app's Japanese translation is
  not maintained for this feature yet).
