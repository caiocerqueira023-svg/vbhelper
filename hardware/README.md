# hardware (`:hardware`)

Android library for the NFC side of Tamers Lab: reading and writing Vital Bracelet /
Vital Hero / Vital Bracelet BE characters and cards over NFC, plus the protocol
translation layer that turns tag pages into typed Kotlin models. The module owns the
watch protocol layer; the app still owns the Android NFC session (`NfcAdapter`, `Tag`)
in the scan and companion flows.

| Property | Value |
|----------|-------|
| Gradle path | `:hardware` |
| Plugin | `com.android.library` |
| Namespace | `com.tamerslab.hardware` |
| SDKs | `compileSdk 37`, `minSdk 28` |
| Java/Kotlin target | Java 11 |
| DIM facade | `api(project(":hardware-dim"))` |

## Public API surface

All public types are under `hardware/src/main/java/com/tamerslab/hardware/nfc/`.

| Type | Role |
|------|------|
| `TagCommunicator` | Main entry point. Wraps an `NfcA` tag (or a supplied `NfcTransport`), performs password auth, reads/decrypts a character, verifies checksums, and writes characters back with write-ack and read-back checks. |
| `TagCommunicator.getInstance(NfcA, Map<UShort, CryptographicTransformer>)` | Builds the communicator from the app's imported per-device secrets and registers the VB/BE translators. |
| `TagCommunicator.receiveCharacter`, `sendCharacter`, `prepareDIMForCharacter`, `passwordAuth`, `addDataTranslator`, `deviceKey` | The supported operations; `deviceKey` returns the tag id as lowercase hex. |
| `NfcTransport` | Small transport boundary (`tagId`, `transceive`) used to exercise the protocol with fault injection in JVM tests. |
| `NfcTransferSafety` | `requireWriteAck` and `requireReadBack` guards; a transfer that is not acknowledged or does not read back identically fails loudly. |
| `NfcDataTranslator<OUT_CHARACTER_TYPE>` | Abstract per-product translator: header parse, character parse, byte-array write, checksum finalization, and operation command. |
| `NfcDataTranslatorFactory` | Maps device type ids to translators; unknown ids throw `UnsupportedOperationException`. |
| `CryptographicTransformer` | HMAC-SHA256/AES password and payload transform; constructed from secrets supplied by the app, never from literals in this module. |
| `ChecksumCalculator` | Validates and recalculates the page checksums the watches require. |
| `data.NfcCharacter` | Base character model (identity, stats, mood, vitals, items, battle record, reserved app bytes); subclasses `VBNfcCharacter` and `BENfcCharacter` live in `nfc/vb/` and `nfc/be/`. |
| `data.NfcHeader`, `data.NfcData`, `data.NfcDevice`, `data.DeviceType`, `data.DeviceSubType` | Header/device metadata and the supported device id constants. |
| `data.block.*`, `vb.*`, `be.*` | Block translators and product-specific command/format code. |
| `vb.SpecialMission` | VB special-mission payload. |

The caller owns the `NfcA` tag and the Android NFC session; this module only performs
tag I/O and parsing. It has no UI, no database, and no app imports.

## DIM facade

`:hardware` exposes the pure-JVM card parser through
`api(project(":hardware-dim"))`. App code can use `com.tamerslab.hardware.dim.card.*`
(`DimReader`, `BemCardReader`, `Card`, `DimCard`, `BemCard`) without depending on a
separate Gradle module, while `hardware-dim` stays a plain Java/Kotlin library. See
[hardware-dim/README.md](../hardware-dim/README.md) and
[ADR 0002](../docs/adr/0002-hardware-module-merge.md) for the split rationale.

## Build and test

```powershell
.\gradlew.bat :hardware:testDebugUnitTest
.\gradlew.bat :hardware:assembleDebug
.\gradlew.bat :hardware:connectedDebugAndroidTest   # device required
```

JVM tests under `hardware/src/test/` run the real protocol code without a watch; a
test-only `android/util/Log` stub keeps the source compilable on the JVM. Tests cover
cryptographic transforms, transfer protocol integrity, and VB/BE translation fixtures.

## Rules

- Dependencies come from `gradle/libs.versions.toml`; do not hardcode versions.
- No Lombok in this module. The DIM dependency is Java and still uses Lombok at compile
  time; that is a `hardware-dim` concern, not a reason to add Lombok here.
- Do not log NFC payloads, tag ids, decrypted character stats, passwords, or full
  transfer tokens. Phase 4 removed those logs; keep it that way
  ([PHASE4_DECISIONS.md](../docs/refactor/PHASE4_DECISIONS.md)).
- The library has an empty `consumer-rules.pro`. The app's `proguard-rules.pro` carries
  the `-dontwarn lombok.Generated` / `org.slf4j.impl.StaticLoggerBinder` entries needed
  by the DIM dependency; add a consumer rule only for a real reflective contract.
- Protocol behavior is covered by characterization tests. Change a fixture value only
  for an intentional protocol change and explain it in the PR.

## Status

Build and unit-test verification is **not yet re-verified after the Phase 6
restructure**. The last recorded suite counts in
[docs/refactor/TARGETS.md](../docs/refactor/TARGETS.md) are historical and must not be
cited as current.
