# Implementation contract — VPet in Tamers Lab

This is the binding contract for the parallel implementation of
[PLAN.md](PLAN.md) decisions D1–D8. Code is **English-only** (identifiers, comments,
strings.xml default values). Do **not** run any Gradle build/test: the Lead runs the full
build after all implementation lands. You may run Node scripts for data generation and read
any repository file.

## Ownership / write scopes (do not edit other scopes)

| Owner | Write scope |
|---|---|
| `vpet-protocol` | `hardware-vpet/**`, `tools/vpet/**` |
| `vpet-data` | `app/src/main/java/com/tamerslab/domain/vpet/**`, `app/src/main/java/com/tamerslab/transfer/vpet/**`, `app/src/main/java/com/tamerslab/di/repository/RepositoryAdapters.kt`, `app/src/main/java/com/tamerslab/di/AppModule.kt`, `app/src/main/java/com/tamerslab/di/DefaultAppContainer.kt` |
| `vpet-ui` | `app/src/main/java/com/tamerslab/transfer/ui/vpet/**`, `app/src/main/java/com/tamerslab/navigation/TransferNavigation.kt`, `app/src/main/java/com/tamerslab/transfer/ui/TransferHubScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml` |
| Lead | `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, this contract, final integration, build/test |

## 1. `:hardware-vpet` module (owner: vpet-protocol)

Android library module at `hardware-vpet/`, namespace `com.tamerslab.hardware.vpet`.
Mirror `hardware/build.gradle.kts` structure (AGP built-in Kotlin; `android { namespace,
compileSdk = 37, defaultConfig { minSdk = 28 }, compileOptions/kotlin jvmTarget 11 }`).
Dependencies: `implementation(libs.androidx.core.ktx)`,
`implementation("com.github.mik3y:usb-serial-for-android:3.8.1")`,
`testImplementation(libs.junit)`. The Lead adds the Gradle include and the version-catalog
entry is not needed if the dependency is declared inline.

### Public API (exact names and signatures)

```kotlin
package com.tamerslab.hardware.vpet

enum class VpetModel(val wireKey: String, val displayName: String, val colorProtocol: Boolean) {
    DM20("dm20", "Digital Monster Ver.20th", false),
    DMX("dmx", "Digimon X", false),
    PEN20("pen20", "Pendulum Ver.20th", false),
    PENZ("penz", "Pendulum Z", false),
    DMC("dmc", "Digital Monster Color", true),
    PENC("penc", "Pendulum Color", true);
    companion object { fun fromKey(key: String): VpetModel? }
}

data class VpetReadResult(
    val model: VpetModel,
    val version: Int,
    val index: Int,
    val name: String?,          // decoded from the RECOVERED lists
    val stage: Int?,            // 0 Baby I … 5 Ultimate (device table value)
    val attribute: Int?,        // 0 Vaccine, 1 Data, 2 Virus, 3 Free
    val power: Int?,
    val payloadHex: String,     // packets joined or color block hex
    val checksumValid: Boolean,
)

sealed class VpetReadFailure {
    data class NoResponse(val attempts: Int) : VpetReadFailure()
    data class InvalidData(val reason: String) : VpetReadFailure()
    data class UnsupportedModel(val wireKey: String) : VpetReadFailure()
}

interface VpetScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): VpetScheduled
    fun cancelAll()
}
interface VpetScheduled { fun cancel() }
class TimerVpetScheduler : VpetScheduler      // daemon java.util.Timer

class VpetReadSession(
    val model: VpetModel,
    initialVersion: Int,
    private val send: (String) -> Unit,
    private val scheduler: VpetScheduler,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(line: String) {}                       // protocol monitor lines
        fun onProgress(percent: Int) {}
        fun onFinished(result: VpetReadResult)
        fun onFailed(failure: VpetReadFailure)
    }
    fun start()
    fun receiveLine(line: String)
    fun stop()
    fun learnedVersion(): Int
}

object VpetDigiRom {
    const val STOP_COMMAND = "0"
    fun commandFor(model: VpetModel, version: Int): String
}

object VpetDecoders {
    /** Null when the collected lines are not yet a complete valid read for the model. */
    fun decode(model: VpetModel, lines: List<String>, version: Int): VpetReadResult?
}

// USB A-Com (owner of the permission dialog + serial IO)
interface VpetLink {
    fun send(text: String)
    fun close()
}
class AcomUsbLink(
    context: Context,                                  // application context is enough
    private val onLine: (String) -> Unit,
    private val onStatus: (connected: Boolean, message: String) -> Unit,
) : VpetLink {
    fun connect()          // finds device; requests permission if needed
    override fun send(text: String)
    override fun close()
    fun isConnected(): Boolean
}

// Bluetooth (D6 says not required for first release, but PLAN phase 3 implements it)
data class VpetDeviceInfo(
    val name: String,
    val address: String,
    val transport: VpetDeviceTransport,   // USB_ACM, BLE_UART, BLUETOOTH_SPP
    val bluetoothDevice: BluetoothDevice?,// null for USB
)
enum class VpetDeviceTransport { USB_ACM, BLE_UART, BLUETOOTH_SPP }

class BtComLink(context: Context, device: BluetoothDevice, onLine: (String) -> Unit,
    onStatus: (connected: Boolean, message: String) -> Unit) : VpetLink { fun connect(); override fun send(...); override fun close() }
class BtSerieLink(context: Context, device: BluetoothDevice, onLine: (String) -> Unit,
    onStatus: (connected: Boolean, message: String) -> Unit) : VpetLink { fun connect(); override fun send(...); override fun close() }
class VpetBluetoothDiscovery(context: Context) {
    fun pairedSppDevices(): List<VpetDeviceInfo>
    /** Requires BLUETOOTH_SCAN (API 31+) or BLUETOOTH+location; caller handles permissions. */
    fun startBleScan(onFound: (VpetDeviceInfo) -> Unit, onFinished: () -> Unit)
    fun stopScan()
}

// Test/debug simulation (D8: no hardware available)
class ScriptedVpetLink(
    private val responder: (command: String) -> List<String>,
    private val onLine: (String) -> Unit,
    private val scheduler: VpetScheduler,
) : VpetLink { override fun send(text: String); override fun close() }

object VpetSimulator {
    /** Builds a valid response script for a model/version/index so the UI can be exercised without hardware. */
    fun responsesFor(model: VpetModel, version: Int, index: Int): (command: String) -> List<String>
}
```

### Behaviour (port `lector.js` exactly, see `analysis/vpet-rea/README.md`)

- `VpetDigiRom.commandFor`: the exact strings from `romDM20/romDMX/romPen20/romPenZ/romDMC/romPenC`
  (`V2-…` words, `C1-…`/`C2-…` 16-byte blocks with the intentional XOR `0xFFFF` on the last
  4 hex digits).
- `VpetDecoders.decode`: the exact `decDM20/decDMX/decPen20/decPenZ/decDMC/decPenC` rules
  (packet counts 10/6/10/6, low-nibble `0xE` filters, nibble checksum mod 16 == 0/12/8,
  color blocks 8 words: `C1` header `47444C43`, `C2` header `47444470`, PenC sum check
  `w0..w6 == w7`, DMC VIP version mapping, PenC `w[2]==18` ignore).
- `VpetReadSession`: replicate `empezar/pedir/armar/linea/cerrar` state machine: 30 s request
  timeout, 1.5 s re-arm per received packet, progress 10–95 %, color resend every 2.5 s
  (DMC) / once (PenC), `s:`/`t` handling, version mismatch → learn version and re-request
  (index 0 special case), max 8 attempts, then `NoResponse`.
- Lists: generate `hardware-vpet/src/main/resources/vpet-lists.txt` from
  `analysis/vpet-rea/recovered/lector.js` with `tools/vpet/extract-lists.mjs`.
  Format (stable, testable): first line `#vpet-lists-v1`, then lines `name<TAB>value` for
  `dm20`, `pen20`, `z1`, `z2`, `x0`, `x1`, `x2`, `dmc`, `penc` (values copied verbatim from
  the JS `LISTAS` object; `x` array indices become `x0..x2`). Parser loads via
  `javaClass.getResourceAsStream("/vpet-lists.txt")` so it works in JVM tests and in the AAR.
- The same script emits test golden vectors to
  `hardware-vpet/src/test/resources/vpet-golden.txt` (format of your choice, documented in
  the script header): every `commandFor(model, version)` for versions 0..15 and decoded
  results for synthetic valid packets per model, so the Kotlin tests pin parity with the JS.
- Unit tests (JUnit4, `hardware-vpet/src/test/java/...`): golden ROM strings, decoder
  success/invalid cases per model, session success/timeout/version-learn/8-attempts using a
  fake scheduler and fake `send`.

## 2. App data layer (owner: vpet-data)

New package files:

```
app/src/main/java/com/tamerslab/domain/vpet/VpetModels.kt        (pure Kotlin, no Android/Room)
app/src/main/java/com/tamerslab/transfer/vpet/VpetPendingReadStore.kt
app/src/main/java/com/tamerslab/transfer/vpet/VpetImportService.kt
app/src/main/java/com/tamerslab/transfer/vpet/VpetReadSnapshotMapper.kt   (optional helper)
```

```kotlin
package com.tamerslab.domain.vpet

data class VpetReadSnapshot(
    val modelKey: String, val modelLabel: String, val version: Int, val index: Int,
    val name: String?, val stage: Int?, val attribute: Int?, val power: Int?,
    val payloadHex: String, val valid: Boolean, val readAtMillis: Long,
)

enum class VpetPendingReason { UNKNOWN_SPECIES, PARTICIPATION_BLOCKED }

data class VpetPendingRead(
    val id: String, val snapshot: VpetReadSnapshot,
    val reason: VpetPendingReason, val blockedSpeciesName: String,
)

data class VpetSpeciesChoice(val canonicalId: String, val name: String, val level: String?)

sealed class VpetImportOutcome {
    data class Imported(val individualId: String, val characterId: Long, val speciesName: String?) : VpetImportOutcome()
    data class Pending(val pending: VpetPendingRead) : VpetImportOutcome()
    data class Failed(val message: String) : VpetImportOutcome()
}

interface VpetRepository {
    val pendingReads: Flow<List<VpetPendingRead>>
    suspend fun import(snapshot: VpetReadSnapshot): VpetImportOutcome
    suspend fun mapPending(id: String, canonicalId: String): VpetImportOutcome
    suspend fun discardPending(id: String)
    suspend fun searchSpecies(query: String, limit: Int = 20): List<VpetSpeciesChoice>
}
```

Implementation `ContainerVpetRepository` in `di/repository/RepositoryAdapters.kt` (constructor
receives `AppContainer`) and bind it as `single<VpetRepository> { ContainerVpetRepository(get()) }`
in `repositoryModule`. Add `val vpetRepository: VpetRepository by lazy { ... }` to
`DefaultAppContainer` **or** have the adapter build the service from container fields; keep the
implementation in `transfer/vpet/VpetImportService.kt` so the UI/data boundary is testable.

Requirements:
- Import known species with no Room schema change:
  `speciesBinding.canonicalIdentityFor(name)` (or catalog direct: `container.speciesRepository.getCanonicalCatalog()`
  + `CanonicalSpeciesCatalog.resolve`) → `db.expandedCatalog.definitionFor(canonicalId)` →
  `db.withTransaction { AppPartnerFactory.createLocked(db, definition, IndividualIdentity.generate(),
  now, nickname = read.name, activate = false, origin = IndividualOrigin.IMPORTED) }`.
  Check the participation gate (`db.participation?.snapshot()?.requireCard(definition.id)`);
  on `PartnerUnavailableException` return `Pending(PARTICIPATION_BLOCKED)`.
- Unknown species → persist `VpetPendingRead` (DataStore preferences JSON, key
  `vpet_pending_reads`) and return `Pending(UNKNOWN_SPECIES)`.
- `mapPending` resolves the canonical id, imports, removes the pending row.
- No dedupe: every import creates a new individual (D4).
- Pending store uses `androidx.datastore.preferences` (already a dependency) with
  kotlinx.serialization JSON; expose a `Flow`.
- Add a pure unit test `app/src/test/java/com/tamerslab/transfer/vpet/VpetPendingReadStoreTest.kt`
  only if it can run without Android; otherwise a pure mapper/policy test
  (`VpetPendingReadCodecTest`) for encode/decode and `VpetImportPolicyTest` for reason
  selection. Do not add DB/Robolectric tests.

## 3. UI / navigation / strings (owner: vpet-ui)

Files:

```
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetSectionScreen.kt
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetReadCoordinator.kt
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetDevicePicker.kt
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetResultCard.kt
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetPendingReads.kt
app/src/main/java/com/tamerslab/transfer/ui/vpet/VpetPreferences.kt   (last model/version, SharedPreferences)
```

- Add `VPET("vpet")` to `TransferSection` and a menu item + page branch `"vpet"` in
  `TransferHubScreen` (title string). Route: `TransferNavigation.route(TransferSection.VPET)`.
- `VpetSectionScreen(initialCharacterId: Long? = null, showHeader: Boolean = false)`:
  model selector (FilterChip/Chip row) with models from `VpetModel.entries`, persistence via
  `VpetPreferences`; connection area for USB (A-Com): "Connect" → `AcomUsbLink.connect()`;
  Bluetooth: call `VpetBluetoothDiscovery` to list `pairStreams` and BLE scan (runtime
  permission requests via `rememberLauncherForActivityResult` with `BLUETOOTH_SCAN/CONNECT`
  on API 31+ and `ACCESS_FINE_LOCATION` legacy; USB needs no runtime permission but shows the
  system dialog via `UsbManager.requestPermission`); "Read" starts
  `VpetReadSession` with the link's `send`; progress bar; result card; import button calls
  `application.container.vpetRepository.import(snapshot)` (suspend in `rememberCoroutineScope`);
  pending list with mapping dialog (`searchSpecies` + choices) and discard; debug builds
  expose "Simulate read" using `ScriptedVpetLink`+`VpetSimulator` (`BuildConfig.DEBUG`).
- Language: add all strings to `values/strings.xml` (English) and `values-pt-rBR/strings.xml`
  (Portuguese) with `vpet_` key prefix. Do not edit `values-ja`.
- Style: follow existing Compose screens (`TopBanner`, `CyberPanel`, `VitalButton`,
  `ChatContextPanel`, material3); screens access services through
  `LocalContext.current.applicationContext as TamersLabApplication` + `application.container`
  like `TransferHubScreen`/`ScanScreen`.
- Do not edit data-layer files; use the contract API above. If something is missing, note it
  in your final report instead of editing outside scope.
- No Gradle builds/tests.

## 4. Lead integration

- `settings.gradle.kts`: `include(":hardware-vpet")`.
- `app/build.gradle.kts`: `implementation(project(":hardware-vpet"))`.
- `app/src/main/AndroidManifest.xml`: `<uses-feature android:name="android.hardware.usb.host"
  android:required="false"/>`, `<uses-feature android:name="android.hardware.bluetooth_le"
  android:required="false"/>`, permissions `BLUETOOTH_SCAN` (`neverForLocation`),
  `BLUETOOTH_CONNECT`, legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` with `maxSdkVersion="30"`.
- Final full build/tests after all files land; fix integration issues across scopes.
- Update `analysis/vpet-rea/PLAN.md` status/decisions if implementation deviates.
