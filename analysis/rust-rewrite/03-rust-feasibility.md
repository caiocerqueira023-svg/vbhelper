# 03 — Rust Feasibility: Can VBHelper Be Rewritten in Rust?

**Status:** analysis, read-only. No project source file was modified.
**Subject:** VBHelper (`com.github.nacabaro.vbhelper`), Alpha 0.6.5, Kotlin + Jetpack Compose, `minSdk 28`, `targetSdk 37`.
**Question:** what would it take to replace this app with a Rust implementation, and is the result worth it?

---

## 0. Verdict (read this first)

**A full Rust rewrite of VBHelper is not technically blocked, but it is economically indefensible. Do not do it.**

| | Recommendation |
|---|---|
| **Recommended verdict** | **Reject the full rewrite.** Keep Kotlin + Compose as the product surface. Spend the rewrite budget on (1) algorithmic/I-O fixes, (2) R8 + Baseline Profiles + startup work, (3) profile-guided Kotlin optimization. Optionally extract exactly one hot module to Rust behind UniFFI/JNI **if and only if** a Perfetto/Macrobenchmark trace proves a Kotlin hot spot that JNI transfer cost cannot eat. |
| **Engineer-months for a full parity rewrite** | **75 / 125 / 210 EM** (optimistic / realistic / pessimistic), of which **~45 EM realistic is UI parity alone** and **~20 EM realistic is brand-new JNI/platform-bridge code that has no Kotlin equivalent to port** (i.e. it is not "translation", it is new construction). |
| **Expected user-visible performance gain** | **≈ 0–5%**, with a meaningful regression risk. The workload is I/O-bound (NFC, disk, network), GPU-bound (Filament is already native C++), and framework-bound (Compose recomposition, Room/SQLite, Binder IPC to Play services). Almost none of it is the JVM's fault. |
| **Top 3 blockers** | **B1 — UI:** no Rust toolkit can reproduce a 46k-line Compose/Material3 consumer UI (see §4). **B2 — Platform APIs:** NFC, Wear OS Data Layer, WorkManager, SAF, permissions, notifications, Android lifecycle have *no* Rust surface; every one forces a Kotlin shim and a JNI boundary that must be maintained forever (see §3). **B3 — Economics:** 75–210 EM for ≈0 user-visible gain, against a codebase with **zero** existing Rust/C/C++ and no NDK toolchain, plus a hardware QA matrix (NFC tags/devices, Wear watch, 3D farm, 16 KB-page devices) that is the real schedule risk. |

The rest of this document is the evidence and the re-runnable model behind those three lines.

---

## 1. The thing being reasoned about (measured, not remembered)

All numbers below were measured in the workspace on 2026-10-30, **excluding `build/` and `.gradle/` outputs**, over `app/src/main/java` and each module's `src/main`.

### 1.1 Code shape

| Subsystem | Files | Lines | Notes |
|---|---:|---:|---|
| `screens/` | 199 | 40,758 | all Compose |
| `components/` | 29 | 3,243 | all Compose |
| `navigation/` | 6 | 990 | Compose + Navigation |
| `ui/` (theme, palette) | 9 | 499 | Material3 theme system |
| **UI subtotal** | **243** | **45,490** | |
| `battle/` | 60 | 8,253 | simulation + game rules + a few sprite composables |
| `source/` | 66 | 5,604 | save/DIM parsing, converters |
| `world/` | 34 | 4,111 | ecosystem, radar, encounters, AFK |
| `domain/` | 70 | 2,965 | entities/value types |
| `daos/` | 41 | 2,893 | Room |
| `chat/` | 16 | 2,454 | AI chat (Retrofit + Gson) |
| `database/` | 31 | 2,245 | Room DB, converters, migrations |
| `species/` | 24 | 1,977 | |
| `digifarm/` | 14 | 1,544 | farm world model |
| `quests/` | 12 | 1,478 | |
| `companion/` | 17 | 1,247 | Wear OS companion, card validation |
| `rendering/` | 8 | 944 | Filament scene/profile code |
| `dtos/` | 9 | 881 | |
| `transfer/` | 6 | 807 | cross-game transfer |
| `widget/` | 1 | 439 | AppWidget |
| `di/` | 3 | 400 | manual DI container |
| `MainActivity.kt` | 1 | 240 | |
| `utils/`, `audio/`, `ui` misc, `ActivityLifecycleListener` | 5 | 292 | |
| **`app/src/main/java` total** | **664** | **84,371** | measured |

Other production modules (`src/main`, excluding tests):

| Module | Files | Lines | Language |
|---|---:|---:|---|
| `game-save-core` | 27 | 3,788 | Kotlin/JVM (pure logic) |
| `vb-dim-reader` | 72 | 2,987 | **Java 11** (`java-library`, vendored upstream) |
| `vb-nfc-reader` | 35 | 2,087 | Kotlin/Android library (protocol + crypto) |
| `pc-save-companion` | 4 | 372 | Kotlin/JVM |
| `companion-protocol` | 1 | 103 | Kotlin/JVM |
| **modules total** | **139** | **9,337** | |

**Model base used throughout §5: 93,708 lines of production Kotlin/Java.**
(Cross-check: the Lead's canonical figure is 107,055 Kotlin lines / 962 files across the repo, and the app module is 97,633 lines including `res/` XML — measured `app/src/main/res` = 113 XML files / 6,733 lines, of which three translated `strings.xml` alone are ~237 KB — plus the `debug`/`integrityCheck` source sets. The difference changes nothing structural; §5.5 scales the model by the ratio as a sensitivity row.)

### 1.2 The single most important structural fact

Split by whether a file touches Compose at all:

| Bucket | Files | Lines | Share |
|---|---:|---:|---:|
| Compose-touching (`@Composable` or `androidx.compose` import) | 190 | 41,026 | 48.6% |
| No Compose reference (logic, data, protocol, Android glue) | 474 | 43,345 | 51.4% |
| **Total (app)** | **664** | **84,371** | 100% |

(461 `@Composable` annotations in `app/src/main`; 190 files import `androidx.compose`; 192 files use `kotlinx.coroutines`.)

So: **roughly half the app is UI you cannot port without abandoning Compose, and half is logic you could port — at full rewrite prices.**

### 1.3 Dependency inventory (from `gradle/libs.versions.toml`, `app/build.gradle.kts`, module build files)

Kotlin 2.4.0, AGP 9.2.1, Compose BOM 2026.06.00, Compose Material3 + Material (M2) + `material-icons-extended`, Navigation-Compose 2.9.8, Lifecycle 2.11.0, Room 2.8.4 + KSP 2.3.9, DataStore 1.2.1, protobuf-gradle-plugin 0.10.0 / protobuf-javalite 4.35.1 / protoc 4.27.0, Retrofit 3.0.0 + Gson 2.14.0 + OkHttp 5.4.0, WorkManager 2.10.0, Play Services Wearable 20.0.1 + coroutines-play-services 1.11.0, Play Services Location 21.3.0, Filament 1.76.1 (`filament-android`, `gltfio-android`, `filament-utils-android`), Timber 5.0.1, tinylog 2.7.0, AppCompat 1.7.1.

Usage counted by file (a file counts once even if it imports several symbols):

| API / library | Files |
|---|---:|
| `androidx.room` | 155 |
| `kotlinx.coroutines` | 192 |
| `androidx.compose` | 190 |
| Gson | 39 |
| Room `@Dao` / `@Entity` / `RoomDatabase` | 42 / 61 / 4 |
| `ActivityResultContracts` | 11 |
| `androidx.datastore` | 10 |
| `retrofit2` | 9 |
| `com.google.android.filament` | 6 |
| `android.nfc` | 4 |
| `com.google.android.gms.wearable` | 4 |
| `com.google.android.gms.location` | 3 |
| `androidx.work` | 2 |
| protobuf runtime | 1 (2 `.proto` files) |
| SAF / `DocumentFile` | 1 |
| `SensorManager` | 1 |
| AppWidget / `RemoteViews` | 1 |
| `NotificationCompat` / `NotificationManager` | **0** |
| HCE (`HostApduService` / `processCommandApdu`) | **0** |

`AndroidManifest.xml` (main) declares: `NFC` (+ `uses-feature android.hardware.nfc required="true"`), `INTERNET`, `ACCESS_LOCAL_NETWORK`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ/WRITE_EXTERNAL_STORAGE`, `VIBRATE`, `MANAGE_EXTERNAL_STORAGE`; one `Activity` (`MainActivity`, plus deep links `vbhelper://auth`, `localhost:8080/authenticate`, and a SEND intent for `application/x-vitalwear-character`), a `FileProvider`, and one `AppWidgetProvider` receiver. **No** `POST_NOTIFICATIONS`, **no** foreground service, and **no** `<service>` declaration for the `WearableListenerService` subclass or the other companion services that exist in source — i.e. the Wear OS data-layer code paths exist but are not registered in the current build (repo inference, not an external claim).

### 1.4 Build/performance facts that matter for §6 and §7

- `release { isMinifyEnabled = false }` — **R8/ProGuard is off in release.** No `isShrinkResources`, no `isCrunchPngs`, no `resConfigs`.
- **No Baseline Profile** anywhere: no `baseline-prof.txt`, no `androidx.profileinstaller` dependency, no `baselineProfile` Gradle plugin.
- `compileOptions`: Java 11 source/target.
- No `externalNativeBuild`, no `ndk { }` block, no `jniLibs` directory; **zero `.rs`, `.c`, `.cpp`, `.h` files in the repo.**
- Existing APKs in `app/build/outputs`: `app-release-unsigned.apk` **183 MB**, debug **227 MB**; `app/src/main` is **125 MB** (13,886 PNGs, 4 `.glb`, plus `.dae/.fbx/.smd`).
- The 3D path is **already native**: Filament ships as prebuilt `.so` AARs with a Java/JNI API ([Filament README](https://github.com/google/filament)).
- The team already does frame-budget work: `rendering/FrameBudget.kt` implements a paused/30fps/60fps tier pacer driven by `Choreographer`; `DIGIFARM_25D_PLAN.md` records a jank fix where per-frame camera notifications to Compose were throttled to ~8 Hz because per-frame updates caused stutters. **That is framework-bound jank being fixed in app code, not JVM jank.**

---

## 2. Rust on Android in 2026: the mechanics, precisely

This section answers "what does it actually take to put Rust in this app", independent of whether it's wise.

### 2.1 Toolchain and Gradle integration

- Rust Android targets are added with `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android` ([cargo-ndk README](https://github.com/bbqsrc/cargo-ndk)).
- [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk) wraps `cargo` with the NDK environment and produces the Android `jniLibs/<abi>/lib*.so` layout (`cargo ndk -t arm64-v8a -t armeabi-v7a -o ./jniLibs build --release`). Its README states MSRV **1.86**, auto-detects the newest installed NDK (overridable via `ANDROID_NDK_HOME`), and provides `--link-builtins` / `--link-libcxx-shared` knobs plus `cargo ndk-test` / `cargo ndk-env` helpers. Supported hosts include Windows.
- Gradle integration: [`mozilla/rust-android-gradle`](https://github.com/mozilla/rust-android-gradle) (Kotlin-implemented plugin, **1,283 stars, last push 2026-09-24** — actively maintained) is the mature option; [`willir/cargo-ndk-android-gradle`](https://github.com/willir/cargo-ndk-android-gradle) is a `cargo-ndk` wrapper whose **last push was 2024-07-08** (stale; treat as unsupported).
- Pairing with the existing build is non-trivial here specifically because this repo is AGP **9.2.1** + Kotlin **2.4.0** + KSP + protobuf plugins: a Rust plugin must be added to the root build, `jniLibs.srcDirs` wired per variant, and the `integrityCheck` variant (`testBuildType = "integrityCheck"`, applicationIdSuffix `.integritycheck`) must also get the `.so`s or instrumented tests break.

### 2.2 The call boundary: `jni` crate vs UniFFI

- [`jni` crate](https://crates.io/crates/jni): current **0.22.4** (published 2026-03-16, MSRV 1.85, edition 2024, 228M lifetime downloads). Raw, hand-written JNI: maximum control, maximum boilerplate, and you own local/global reference management, exception checking, and every type conversion.
- [UniFFI](https://github.com/mozilla/uniffi-rs): generates Kotlin/Swift/Python/Ruby bindings from a UDL or proc-macro interface. Mozilla states it is **"used extensively … in Firefox mobile and desktop"** and that bindings are generated for **Kotlin (Android)** and Swift; it explicitly warns it is **"a long way from a 1.0 release"** and that "more advanced things might break as you upgrade". Kotlin Multiplatform support is available through third-party plugins (Gobley, uniffi-kotlin-multiplatform-bindings) per the same README.
- The structural consequence nobody can engineer away: a Rust-hosted Android app runs inside a **`NativeActivity` or `GameActivity`**, not a normal `Activity` — that is literally what the [`rust-mobile/android-activity`](https://github.com/rust-mobile/android-activity) crate is ("Glue for building Rust applications on Android with NativeActivity or GameActivity"; 408 stars, last push 2026-06-22). Compose cannot host a `NativeActivity`'s content; you either (a) keep a normal Kotlin `Activity` and call Rust as a library (recommended, and then Rust is a component, not a rewrite), or (b) hand the whole window to a Rust toolkit and lose Compose (§4).

### 2.3 ABI splits

Four Android ABIs exist; `minSdk 28` means 64-bit ARM (`arm64-v8a`) and 32-bit ARM (`armeabi-v7a`) are both in scope, plus `x86_64` for emulators. A Rust `cdylib` must be built per ABI ([cargo-ndk README](https://github.com/bbqsrc/cargo-ndk)), and Android App Bundles/splits then deliver one `.so` per device. Practically: **4 build targets × CI time**, 4 symbolication/strip steps, 4 QA matrix rows, and `--link-builtins`/`libc++_shared` decisions that can crash on device if wrong.

### 2.4 The Android 16 KB page-size requirement (a *new* tax on "make it native")

Google Play requires **16 KB page-size support** for new apps and updates that target Android 15+ on 64-bit devices, effective **November 1, 2025** — announced in the [Android Developers Blog (May 2025)](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html), restated with the deadline in the [July 2025 post](https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html) and documented at [developer.android.com/guide/practices/page-sizes](https://developer.android.com/guide/practices/page-sizes). A later Play deadline (updates that do not comply after **February 1, 2027**) is reported by third parties ([primetestlab](https://primetestlab.com/blog/google-play-16-kb-page-size-error), [rapidlabs](https://rapidlabs.dev/articles/android-16-16-kb-page-size/)) — **UNVERIFIED against the primary Play policy page** (the devsite page is JS-rendered and only its navigation could be retrieved).

Mechanics, per the [AOSP 16 KB page size docs](https://source.android.com/docs/core/architecture/16kb-page-size/16kb) and a 2026 Rust-on-Android practitioner write-up ([davthecoder](https://www.davthecoder.com/blog/rust-binary-size-android-16kb-page-size)):

- A `.so` records the LOAD-segment alignment it was linked for; `0x1000` (4 KB) fails on a 16 KB kernel and `System.loadLibrary` throws. `0x4000` passes.
- **NDK r28+ links 16 KB-aligned by default**; on r27 or older Rust needs `-C link-arg=-Wl,-z,max-page-size=16384` (+ `common-page-size`) in `.cargo/config.toml` per 64-bit target.
- AGP ≥ 8.5.1 zipaligns uncompressed native libraries to 16 KB; verification is `llvm-readelf -lW libfoo.so | grep LOAD` and `zipalign -v -c -P 16 4 app-release.apk`.
- **Relevance to VBHelper:** a Compose-only APK is currently not subject to this (it ships no self-built `.so`; the `.so`s inside AARs are the vendors' problem). **The moment the app becomes a Rust app, this requirement becomes yours** — on top of 4 ABIs. This is a recurring, non-optional compliance cost of the "native first" direction, not a one-off.

### 2.5 Binary size and startup

- Rust `cdylib` baseline: a minimal Rust `.so` is hundreds of KB and a realistic business-logic crate with `serde`/`prost`/`reqwest` lands in the low single-digit MB per ABI before shrinking; the standard levers are `opt-level="z"`, `lto="fat"`, `codegen-units=1`, `panic="abort"`, `strip` ([min-sized-rust](https://github.com/johnthagen/min-sized-rust), which documents 30%+ debug-vs-release gaps and the panic/unwind removal trade), and **there is no R8 for native code** — LLVM and the linker are your only shrinker ([davthecoder](https://www.davthecoder.com/blog/rust-binary-size-android-16kb-page-size)).
- For this app, APK size is *not* the decisive issue: the release APK is already **183 MB** and dominated by 13,886 PNGs and 3D assets. A few MB of `.so` per ABI is noise against that. (It still matters for download size and for Play's per-artifact limits — **UNVERIFIED** for current Play limits.)
- Startup *is* affected: adding a native library adds `System.loadLibrary` + `JNI_OnLoad` + (with UniFFI) binding-library class loading, and on Android the JVM still has to start the process; the JNI boundary itself is real cost — Android documents **25 ns for a `@CriticalNative` call** ([CriticalNative reference](https://developer.android.com/reference/dalvik/annotation/optimization/CriticalNative)), while ordinary JNI calls with object marshaling are substantially more expensive (exact multiples **UNVERIFIED**; no primary benchmark found that matches this codebase's calling patterns).
- The untapped startup lever is not Rust at all: the app currently has **minify off and no Baseline Profiles**, which Google documents as improving startup **by up to 40%** ([Android Developers Blog, 2022](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html); [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview); a published case study is the [Android Calendar team](https://developer.android.com/topic/performance/baselineprofiles/case-study-android-calendar)).

### 2.6 What the Rust toolchain does *not* give you

There is no Rust equivalent of: `Activity`/`Application` lifecycle hosting, `Service`/`BroadcastReceiver`/`ContentProvider` components, Jetpack Compose, the Android `View` system, Google Play services (Wearable, Location, Fitness), `WorkManager`, the Storage Access Framework, `NotificationManager`, or `ActivityResultContracts`. Every one of these is reached from Rust only by calling Java/Kotlin through JNI — which means the Kotlin code still exists, still ships, and still has to be maintained alongside the Rust code. §3 quantifies that per component.

---

## 3. Component-by-component replacement analysis

Verdict legend:

- **[R] Real Rust path** — the work can genuinely live in Rust, and Rust is a reasonable (sometimes better) choice.
- **[J] JNI-bridge-only** — the platform API has no Rust surface; Kotlin (or Java) must remain the owner and Rust can at most be called from it / call into it.
- **[X] No viable path** — replacing this with Rust means replacing the platform; the only "Rust" version is a different product.

"Multiplier" = how much *more* work the Rust version is versus porting the Kotlin logic directly (1× = same difficulty as a straightforward port). It is applied to the bucket estimate in §5.

| # | Component | What VBHelper actually uses (measured) | Verdict | Rust approach that exists | Multiplier |
|---:|---|---|---|---|---:|
| 1 | **Compose / Material3 UI** | 190 files, 41,026 lines; Material3 + Material2 + `material-icons-extended`; custom theme/palette (`ui/theme/Color.kt` has ~50 palette tokens); custom animated surfaces (`TechBackground`, `CyberUi`, `InstrumentUi`); 11 locales in `strings.xml`; RTL; AppWidget | **X** | None. A "Rust UI" means a different UI toolkit (§4), i.e. redesigning the product, not porting it | **3.0–5.0×** on top of UI rebuild cost |
| 2 | **Room** | 155 files import Room; 61 `@Entity`, 42 `@Dao`, 4 `RoomDatabase`; KSP codegen; `room.schemaLocation` schemas checked into `app/schemas`; hand-written `TypeConverter`s; migrations | **X** (Room itself) / **[R]+[J]** hybrid if Rust owns SQLite directly | `rusqlite`/`sqlx` can own the same `.db`, but Room's schema validation, KSP-generated DAO code, `Flow` invalidation tracking and migrations are Android-only. Moving the writer to Rust while Room reads means owning migration ordering and the `room_master_table` hash contract yourself | **3.0–4.0×** |
| 3 | **DataStore** | 10 files; `preferencesDataStore` with `Preferences` keys; also a `dataStore` (proto/typed) instance | **J** | No Rust equivalent; it is a Kotlin-coroutines/Flow API. Rust can only be handed values | **2.5–3.5×** |
| 4 | **protobuf-lite** | protobuf-gradle-plugin 0.10.0, `protoc 4.27.0`, `javalite`; 2 `.proto` files (`Character.proto` from the VitalWear wearable protocol, `Secrets.proto`) | **[R]** | `prost` is a mature Rust protobuf implementation and the natural replacement; the wire format is the contract, and the wearable peer does not care which language parses it | **1.0–1.2×** |
| 5 | **Retrofit / Gson / OkHttp** | Retrofit in 9 files, Gson in 39; endpoints include OpenRouter/UniRouter AI chat, Overpass (`overpass-api.de`), auth (`auth.nacatech.es`), PvP (`battle.io-void.com`) | **[R] partial** | `reqwest` + `serde` covers HTTP/JSON well; but call sites, error mapping, retry/backoff and Flow integration stay in Kotlin unless the whole repository moves. `rustls`/`ring` add cross-compile friction and bytes; the practitioner series documents `ring`/RustCrypto on Android as its own topic ([davthecoder](https://www.davthecoder.com/blog/rust-binary-size-android-16kb-page-size)) | **1.2–1.6×** |
| 6 | **WorkManager** | 2 files (`WorldAfkWorker`, `WorldAfkScheduler`); periodic work with network constraints | **X** | No Rust path. WorkManager is a Jetpack scheduler with a `Worker` lifecycle; Rust could only be the *body* of a Kotlin `CoroutineWorker` | **n/a (cannot move)** |
| 7 | **Play Services Wearable Data Layer (Wear OS)** | 4 files: `WearableListenerService` subclass, `ChannelClient`, `CapabilityClient`, `MessageClient` usage; wearable `Character.proto` handoff; `CompanionValidateCardActivity` reader-mode NFC hand-off | **X** | The Data Layer API is Google Play services, reachable only from Java/Kotlin; a `WearableListenerService` must be a Kotlin `Service` registered in the manifest (it currently is **not** registered). Rust cannot host it | **n/a (cannot move)** |
| 8 | **Play Services Location** | 3 files; `LocationServices` in `WorldAfkWorker`; FINE/COARSE permissions | **J** | Fused location is a Play services API; Rust can only consume the location handed to it | **n/a (cannot move)** |
| 9 | **Android NFC** | 4 files: `NfcAdapter.enableReaderMode` + `IsoDep` transceive (`ScanScreenControllerImpl`), `MifareUltralight` (`VBNfcData`, `CompanionValidateCardActivity`); protocol core in `vb-nfc-reader` (2,087 lines: `TagCommunicator`, `CryptographicTransformation`, `ChecksumCalculator`, block translators); **no** `HostApduService`/HCE implementation | **[J] for the transport / [R] for the protocol** | Reader-mode callbacks, tag dispatch, `IsoDep`, and NFC permission are Android-only. The *crypto/checksum/block-translation* logic (≈2.1k lines) is pure and portable. This is the best "real Rust path" in the app — but it is also the part that least needs Rust | **1.5–2.5×** |
| 10 | **Filament 3D** | 6 files import Filament; `rendering/` 944 lines (`EnvironmentThemeController` drives `Engine`/`MaterialInstance`/`Texture`/`TextureSampler`/`FilamentAsset`); glTF assets (`Colosseum.glb` 2.76 MB, `radar_grid.glb` 2.21 MB, `digi_farm_3d.glb`); `frame pacing` already tuned | **X** | Filament *is* native C++ (Vulkan/GLES) with a Java/JNI API ([README](https://github.com/google/filament)). A Rust binding does not exist in maintained form: [`filament-bindings` v0.2.2](https://crates.io/crates/filament-bindings) was **last published 2022-04-10**. Reimplementing the renderer is a multi-year product on its own | **n/a (already native)** |
| 11 | **SAF / file access** | 1 file uses `DocumentFile`; 11 files use `ActivityResultContracts` (open document tree, create document, etc.); `FileProvider` + `MANAGE_EXTERNAL_STORAGE` in manifest | **J** | The picker, URI permissions and content-resolver plumbing are Android APIs; Rust can only open a file descriptor handed to it | **2.0–3.0×** |
| 12 | **Permissions / notifications / background work** | Runtime permission requests via `ActivityResultContracts`; **zero** notification code; background = WorkManager + (implicitly) app process | **X** (permissions & components) | There is no Rust permissions API; `ActivityResultContracts.RequestPermission` is Kotlin-only. Notifications (`NotificationCompat`, channels, `POST_NOTIFICATIONS` on 33+) are Kotlin-only — irrelevant today (0 uses) but would be needed by any "foreground service" Rust design | **n/a (cannot move)** |
| 13 | **Android app lifecycle** | `MainActivity` + `Application` subclass (`di.VBHelper`), `ActivityLifecycleListener`, Compose lifecycle-aware state | **X** | Rust can be a library inside the app, never the host, unless you adopt `android-activity` (NativeActivity/GameActivity), which forfeits Compose entirely | **n/a (cannot move)** |

### 3.1 What that table means arithmetically

Of the app's 13 subsystems, **3 have a genuine Rust path** (protobuf/prost, HTTP/serde, NFC protocol core), **4 are JNI-bridge-only** (DataStore, Location, SAF, NFC transport, and partly Room), and **6 have no viable path at all** (Compose UI, WorkManager, Wearable, Filament, permissions/notifications, app lifecycle). The portable core — save codecs, battle rules, world simulation, NFC crypto — is roughly **40k lines**, i.e. **~43% of production code**. A "Rust rewrite" is therefore really *"a Kotlin/Compose app with a Rust backend 43% of its size"* — which is exactly alternative (i) in §7, at a small fraction of the cost.

---

## 4. UI toolkit reality check: is any Rust GUI a credible Compose replacement?

The app's UI is not "some screens". It is 45,998 lines of Compose across 243 files, with a custom design system, Material3 theming, ~50 named palette tokens driving a Cyber/Tech visual identity, animated backgrounds, accessibility (`contentDescription`s, semantics), RTL support, 11 locale resource sets, density/font scaling, an AppWidget, and Compose instrumentation tests (`ui-test-junit4`, `ui-test-manifest`).

A credible replacement must supply: **Material3 component set and theming**, text/font handling with 11 locales, accessibility bridge to Android's accessibility services (TalkBack), correct IME/insets behavior (`windowSoftInputMode="adjustResize"`), predictive back, Compose-grade animation, screenshot/UI test tooling, and Play-quality polish. Here is the state of each candidate **as of 2026**:

| Toolkit | Android status (sourced) | Material3-quality UI? | Accessibility on Android | Verdict for this app |
|---|---|---|---|---|
| **Slint** | Real Android backend via the `android-activity` crate; entry point is `slint_main`/`android_main`; project is a `cdylib` build ([Slint Android docs](https://docs.slint.dev/latest/docs/slint/guide/platforms/mobile/android/)); stable 1.x API; royalty-free license option for proprietary mobile apps ([README](https://github.com/slint-ui/slint)) | No — it is a declarative toolkit; you design and build your own widget set and Material3 look; no Material3 component library | AccessKit integration exists, but Android accessibility support is **UNVERIFIED** | Closest thing to a real option, still a **from-scratch design system** and a different app architecture (native entry point) |
| **egui / eframe** | `eframe` officially supports Android ([README](https://github.com/emilk/egui)) | No — immediate-mode, non-native look is an explicit **non-goal** ("Native looking interface"; "If you want a GUI that looks native, egui is not for you") | AccessKit "currently implements the native accessibility APIs on Windows and macOS" ([README](https://github.com/emilk/egui)) — not Android | Not viable for a consumer Play app |
| **Dioxus** | "First-class Android and iOS support"; mobile renders via **Webview** or **experimentally** WGPU/Skia ([README](https://github.com/DioxusLabs/dioxus)) | No — HTML/CSS styling, not Material3; the non-Webview renderers are explicitly experimental | Depends on the Webview/Blitz path — **UNVERIFIED** | A Webview UI is a different product (and slower); experimental renderer is a schedule risk |
| **Vello** | A **2D vector renderer**, not a UI toolkit: "high-performance 2D vector rendering … renderer implementations" with `vello_cpu`, `vello_gpu`, experimental compute renderer ([README](https://github.com/linebender/vello)) | No — no widgets, no text input, no focus/IME, no accessibility | None (it renders) | Not a replacement at all; at best an ingredient for someone else's toolkit |
| **Xilem / Masonry** (adjacent, worth naming) | "experimental high-level architecture for writing GUI apps in Rust" ([README](https://github.com/linebender/xilem)); MSRV 1.96 | No — experimental, and its backends are winit/web, with no Android story documented | AccessKit (Win/mac) | Not viable in 2026 |
| **Qt (C++/QML, not Rust)** | Qt 6.12 supports Android 9 (API 28) → **Android 16 (API 36)**, `arm64-v8a`/`x86_64`/`x86`/`armeabi-v7a`, NDK r27c, JDK 21, Gradle 9.5.1/AGP 9.2.1; Qt Quick Controls ships a **Material style**; Qt Quick 3D exists; "You cannot use Android Fragments"; license is GPL/LGPL-or-commercial ([Qt for Android](https://doc.qt.io/qt-6/android.html)) | **Closest to yes** — Material style plus a mature toolkit | Qt has its own accessibility bridge (quality on Android **UNVERIFIED**) | Not Rust; adds a large C++/QML runtime and a licensing decision; and **Qt's supported ceiling is API 36 while this repo targets API 37** — you would be behind the platform you currently target |
| **GTK 4** | A GDK Android backend exists, but GNOME community describes it as the **"experimental android backend"**, with APKs built via external tooling ("pixiewood") plus patches to GLib and Meson ([GNOME Discourse](https://discourse.gnome.org/t/how-do-we-use-the-experimental-android-backend/28689)); official docs list Wayland/X11/Broadway backends ([docs.gtk.org](https://docs.gtk.org/gtk4/running.html)) | No — desktop widget toolkit; mobile polish is not its target | **UNVERIFIED** | Not a shipping path; and no Rust-native GTK audience on Android |

**Blunt conclusion:** **no Rust UI toolkit is a credible replacement for this Compose UI in 2026** — not because Rust cannot draw rectangles, but because the product's value is *Material3-grade consumer polish plus Android accessibility/IME/RTL/widget integration*, and every candidate above either lacks the component library (Slint, egui, Dioxus, Vello), is not a UI toolkit (Vello), is experimental (Xilem/Dioxus-native/GTK-Android), or is a different language with a licensing decision and a lower target-SDK ceiling (Qt). The credible-looking option — Qt — is not Rust, which is the point of the exercise.

**Practical consequence:** the only Rust-in-this-app architecture that preserves the product is *"Compose UI stays, Rust core behind UniFFI"*. That is not a rewrite; it is §7 alternative (iv).

---

## 5. Bottom-up effort model (every assumption is a number)

### 5.1 Formula

```
EM_total = Σ_buckets ( LOC_bucket × r / (p × 21) )                 # logic port
         + EM_bridges                                            # NEW work, no Kotlin reference
         + EM_ui_parity                                          # new toolkit, not a port
         + EM_tests + EM_hw_qa + EM_toolchain_learning
```

where:
- `LOC_bucket` = measured production Kotlin/Java lines (§1.1).
- `r` = **Rust LOC per Kotlin LOC** (syntactic ratio after idioms).
- `p` = **Rust LOC written per engineer-day** when a working reference implementation exists *and* the engineer knows Rust.
- `21` = engineer-days per engineer-month.
- `EM_ui_parity`, `EM_bridges` etc. are estimated directly (they are not translations).

### 5.2 Assumption register (re-runnable)

| ID | Assumption | Optimistic | **Realistic** | Pessimistic | Basis |
|---|---|---:|---:|---:|---|
| A1 | Kotlin→Rust LOC ratio `r` | 1.00 | **1.15** | 1.50 | Rust is more verbose around ownership/error plumbing, terser around data modelling; a mid-point assumption, no primary citation — **UNVERIFIED, assume ±20%** |
| A2 | Logic porting productivity `p` (Rust LOC/engineer-day, Rust-fluent) | 120 | **80** | 45 | Porting with a live reference is faster than greenfield; 80 LOC/day ≈ 1,680 LOC/EM, consistent with "100k LOC ≈ 3–4 dev-years" industry reality once reviews/tests are included |
| A3 | Productivity penalty while learning Rust (first 3 months) | 0% | **30%** | 50% | Applies to A2 for the first 3 calendar months; folded into A11 to avoid double counting |
| A4 | UI parity (new toolkit, all 243 Compose files / 45,998 lines) | 25 EM | **45 EM** | 70 EM | Implied ~66 / 49 / 31 LOC/day; includes rebuilding the design system, accessibility, 11 locales, RTL, widget |
| A5 | Platform bridges: NFC transport + reader-mode/HCE shims, Wearable shims, Location/WorkManager shims, SAF/ActivityResult, permissions, notifications, lifecycle host, protobuf/array marshaling at the boundary | 12 EM | **20 EM** | 34 EM | **Entirely new work** — there is no Kotlin code to "port"; every bridge is designed, written, tested and maintained twice (§3) |
| A6 | Tests: rewrite/port 21,688 lines / 227 files (app unit 146 f / 12,864 l; app androidTest 48 f / 7,018 l; modules 33 f / 3,955 l) | 7 EM | **12 EM** | 20 EM | Port at ~90 test-LOC/day + a new cross-language test harness + on-device Rust tests (`cargo ndk-test`) |
| A7 | Real-hardware QA: NFC tags × VB/BE/VB-Special devices + DIM cards, Wear OS watch pairing, Filament farm on low/mid devices, 4 ABIs, Android 9→17, a 16 KB-page device, Play submission | 5 EM | **9 EM** | 16 EM | This is the risk that does not compress with more engineers (limited devices/people) |
| A8 | Toolchain/CI/learning: NDK + cargo-ndk + Gradle plugin + UniFFI codegen in CI, symbolication, cache, reproducible builds, docs | 2.5 EM | **5 EM** | 9 EM | Greenfield: repo has zero Rust/NDK today |
| A9 | Coordination factor at 5 engineers | — | **×1.2** | ×1.4 | Brooks: parallel efficiency ≈0.8 at 3, ≈0.7 at 5 |
| A10 | Fully-loaded engineer-month cost (for money statements only) | — | **$10k** | $18k | Assumption, not sourced |
| A11 | Learning-curve surcharge (A3 × 3 months × team) | — | **+3 EM** | +6 EM | Team of 2–3 ramping |

### 5.3 Bucket-by-bucket

| Bucket | LOC | Rust LOC @r=1.15 | Days @p=80 | **EM opt** | **EM real** | **EM pess** | Notes |
|---|---:|---:|---:|---:|---:|---:|---|
| 1. UI + navigation (Compose) | 45,998 | — | — | 25 | **45** | 70 | Not a port; see A4/§4 |
| 2. Persistence (Room+DAOs+converters) | 5,138 | 5,909 | 74 | 2.5 | **4.0** | 8.0 | 61 entities + migrations + schema hash contract |
| 3. Save codecs & formats (core + DIM reader + PC companion + protocol) | 10,620 | 12,213 | 153 | 5.0 | **7.5** | 12.0 | Best-fit Rust code; heavily fixture-tested |
| 4. NFC protocol core (crypto/checksums/block translation) | 2,787 | 3,205 | 40 | 1.5 | **2.5** | 5.0 | Hardware-validated; small |
| 5. Battle simulator & rules | 7,750 | 8,913 | 111 | 3.5 | **5.5** | 9.0 | 2,195-line fixed-step sim + AI/technique catalogues |
| 6. World / ecosystem / AI chat / quests / species / farm logic | 19,521 | 22,449 | 281 | 9.0 | **13.5** | 22.0 | Largest portable logic block |
| 7. Misc glue (DI, utils, audio, widget, transfer, companion) | 1,894 | 2,178 | 27 | 1.0 | **1.5** | 3.0 | |
| **Logic subtotal** | **47,710** | **54,867** | **686** | **22.5** | **34.5** | **59.0** | |
| 8. Platform bridges (A5) | — | — | — | 12 | **20** | 34 | new work |
| 9. Tests (A6) | 21,688 test LOC | — | — | 7 | **12** | 20 | |
| 10. Hardware QA (A7) | — | — | — | 5 | **9** | 16 | |
| 11. Toolchain/CI/learning (A8+A11) | — | — | — | 2.5 | **8** | 15 | |
| **TOTAL** | **93,708 prod** | | | **≈74** | **≈126** | **≈208** | |

### 5.4 The range, expressed three ways

| Framing | Optimistic | **Realistic** | Pessimistic |
|---|---:|---:|---:|
| Engineer-months (full parity) | **75 EM** | **125 EM** | **210 EM** |
| Calendar, solo engineer | 6.3 yr | 10.4 yr | 17.5 yr |
| Calendar, 3 engineers (÷3, ×1.0 coordination) | 25 mo | 42 mo | 70 mo |
| Calendar, 5 engineers (A9) | 15 mo | 25 mo | 42 mo |
| Cost at A10 | $0.75M | **$1.25M** | $2.1M (pess × $18k ⇒ $3.8M) |

For scale: the whole app is an **Alpha**, and this estimate is for **feature parity only** — it contains zero product work, zero migration of the 13,886 image assets' pipeline, zero new features, and zero time for whatever the app does next while the rewrite is happening.

### 5.5 Sensitivity table (each row changes one driver from the realistic 126 EM)

| Change | ΔEM | New total | Comment |
|---|---:|---:|---|
| `r` = 1.00 (Rust as terse as Kotlin) | −13% | ≈110 EM | logic buckets ÷1.15 |
| `r` = 1.50 (Rust much more verbose) | +30% | ≈165 EM | ownership/error plumbing |
| `p` = 120 (fast senior Rust team) | −24% | ≈96 EM | |
| `p` = 45 (unfamiliar team, reviews slow) | +51% | ≈190 EM | |
| **Keep Compose UI (no UI rewrite)** — i.e. the "extraction" alternative, for comparison | −45 | **≈81 EM** | This is the *entire* honest case for Rust-in-this-app, and it still costs 6.75 engineer-years |
| UI toolkit magically acceptable out of the box (unrealistic) | −25 | ≈101 EM | no such toolkit exists (§4) |
| Tests discarded instead of ported | −10 | ≈116 EM | risk ↑↑ (hardware/format regressions are silent) |
| Team already Rust-fluent from day 1 | −8 | ≈118 EM | A3/A11 → 0 |
| Bridges larger: HCE + full Wear OS app + 16 KB + ABI QA hardening | +8 | ≈134 EM | |
| Model scaled to the Lead's 107,055 Kotlin LOC (+14% logic) | +9 | ≈135 EM | reconciles §1.1 |
| `p`=45 **and** `r`=1.5 **and** bridges=34 (combined pessimistic) | — | **≈208 EM** | matches the pessimistic column |

### 5.6 Calibration check ("does 125 EM smell right?")

97k lines of production code at 2.5–4× the original build cost: if the current app represents ~40–55 EM of accretion (a defensible order of magnitude for an alpha of this breadth built by a small team), a rewrite in a **new language**, with a **new UI toolkit**, a **new platform-bridge layer**, **re-validated hardware I/O** and **rewritten tests** multiplying by 2.5–4× lands at **100–220 EM**. The bottom-up 75/125/210 is consistent with that independent estimate. Neither number is precise; both point the same way.

---

## 6. Expected performance benefit: what Rust can and cannot buy here

Rust's performance story is real, but it is a *CPU-bound* story, and this app's cost centres are elsewhere.

| Workload class | What actually dominates in VBHelper | Rust's realistic gain | Evidence / reasoning |
|---|---|---|---|
| **CPU-bound** (tight numeric loops, parsing) | `BattleSimulator` (2,195 lines, fixed 34 ms step, 5-min max battle), save parsing/checksums, DIM block translation | **1.5–3×** on the hot loop, vs ART | The Benchmarks Game publishes per-workload Rust and Java programs side by side, but explicitly warns these are "micro benchmarks … far from realistic" ([site](https://benchmarksgame-team.pages.debian.net/benchmarksgame/index.html), [methodology](https://benchmarksgame-team.pages.debian.net/benchmarksgame/how-programs-are-measured.html)) — use per-workload pages, not a blanket multiplier. Discord's Go→Rust service is the clearest production evidence of a **non-GC** rewrite beating a hand-tuned GC-language version ([Discord engineering](https://discord.com/blog/why-discord-is-switching-from-go-to-rust)) — note it is Go, not ART |
| **I/O-bound** | NFC ISO-DEP transceive (tag/EEPROM latency; NFC-A at 106–848 kbps), Room disk I/O, HTTPS to AI/map/PvP endpoints, file import/export of multi-MB saves | **≈0** | The app spends its hardware time waiting on the tag and the network; language choice does not change link speed. The NFC protocol layer is ~2.1k portable lines that already run in microseconds-to-milliseconds |
| **GPU-bound** | Filament scene rendering (Vulkan/GLES), glTF assets, farm/radar 3D | **0** | Already native C++ with a Java/JNI API ([Filament README](https://github.com/google/filament)). Rust cannot make a GPU faster; it can only call the same engine |
| **Framework-bound** | Compose recomposition/layout/draw, Binder IPC to Play services (Wearable/Location), ART GC, Activity lifecycle, Room's `Flow` invalidation | **≈0–5%** | Rust does not replace Compose, Binder, or ART. The repo's own history shows framework-bound jank fixed by *throttling notifications into Compose* (`DIGIFARM_25D_PLAN.md`) and by *frame pacing* (`rendering/FrameBudget.kt`) — both app-code fixes. Discord's GC-pause win applies to a service with a giant LRU cache; whether ART GC is even measurable in this app is **UNVERIFIED** (no profiling data in the repo) |
| **Startup / cold start** | Kotlin object graph init, Room/DataStore open, Play services connection, Compose first frame | **negative to slightly positive** | Rust adds a `loadLibrary` + `JNI_OnLoad` + binding init; the *available* win is R8 + Baseline Profiles (up to 40% startup per [Google](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html)), which is untouched today |
| **Binary size / download** | 183 MB release APK, 125 MB of `app/src/main` assets | **worse** | 4 ABIs × a few MB of Rust, on top of an already asset-dominated APK (§2.5). `opt-level="z"`/LTO/`panic="abort"` recover some of it ([min-sized-rust](https://github.com/johnthagen/min-sized-rust)) |

### 6.1 The specific hot loop people will point at — and the cheap fix

`battle/offline/tamers/ArenaRepository.kt` simulates a full battle by:

```kotlin
repeat(60_000) { step ->
    if (step % 128 == 0) currentCoroutineContext().ensureActive()
    simulator.advance(BattleRules.STEP_MILLIS)   // 34 ms per step
    val snapshot = simulator.snapshot()          // full immutable snapshot EVERY step
    if (snapshot.result != null) return snapshot
}
```

That is up to **60,000 snapshot allocations** per simulated match (the sim's `maxDurationMillis` is 300,000 ms ⇒ 8,823 steps for a 5-minute battle; the arena loop allows 34 minutes of simulated time). Rust would make each step cheaper; **taking the snapshot every N steps or only on state change makes the loop cheaper without leaving Kotlin**, and is an afternoon of work. This is the pattern the whole analysis hinges on: the wins are algorithmic, and they are available today.

**Bottom line for §6:** the honest expected user-visible benefit of a full Rust rewrite is **≈0–5%**, concentrated in "arena battles simulate a bit faster" and "save import is a bit snappier", with **no** improvement to the NFC experience, the Wear OS experience, the 3D farm, Compose scrolling, cold start (probably a small regression), or APK size (a small regression).

---

## 7. Alternatives, ranked

Ranked by expected value (gain ÷ effort), not by novelty. Effort and risk are engineer-months and probability-of-schedule-loss respectively.

| Rank | Alternative | Effort | Risk | Expected gain | Why it ranks here |
|---:|---|---|---|---|---|
| **1** | **Algorithmic / I-O fixes, off-main-thread work, caching, query/index tuning, streaming** | **0.5–2 EM** | **Low** | **High / measurable** | Concrete, already-located wins: `ArenaRepository`'s 60k snapshots (§6.1); the already-proven "throttle UI notifications" pattern from `DIGIFARM_25D_PLAN.md`; Room indices/queries (155 Room files, 61 entities) reviewed with `EXPLAIN QUERY PLAN`; multi-MB save import/export streamed instead of buffered; AI-chat and Overpass calls kept off the main dispatcher. Zero platform risk, no new toolchain, reversible |
| **2** | **R8/minify + Baseline Profiles + startup tuning** | **0.5–1.5 EM** | **Low** | **High / measurable** | Currently *absent*: `isMinifyEnabled = false`, no `profileinstaller`, no `baseline-prof.txt` (§1.4). Google documents up to **40%** startup improvement from Baseline Profiles ([blog](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html), [overview](https://developer.android.com/topic/performance/baselineprofiles/overview)) and R8 shrinks/optimizes DEX. Add `isShrinkResources`, PNG crunching, `resConfigs` for the 11 locales, and `Startup Profiles`. This is the single largest *available* performance delta in the project and it costs one engineer-month |
| **3** | **Profile-guided Kotlin optimization (Macrobenchmark + Perfetto first, then fix)** | **1–3 EM** | **Low-Medium** | **Medium / measurable** | Measure before choosing: cold start, frame timing on the farm/radar, arena battle wall time, save-import time. Then apply allocation reduction, `derivedStateOf`/`remember` fixes in the 190 Compose files, `Immutable`/`Stable` annotations, `LazyColumn` keys. Standard Compose performance practice; keeps the UI |
| **4** | **Targeted Rust extraction of ONE hot module behind UniFFI/JNI** (best candidate: NFC crypto/block protocol or save codecs; second: battle simulator) | **3–8 EM** | **Medium** | **Low-Medium** | The only architecture that preserves Compose (§4) and still uses Rust. Real work: NDK+cargo-ndk+UniFFI wiring (1–2 EM), port the module (1–3 EM for 2–8k lines), boundary design to avoid per-element JNI copies, on-device tests, 4-ABI CI, 16 KB alignment (§2.4). Gain is bounded by §6 — only worth it if a trace shows >20% of a user-visible operation inside that module |
| **5** | **NDK C++ for one specific kernel** | **2–5 EM** | **Medium** | **Low-Medium** | Same shape as (4) with a worse language for new code (manual memory safety) and slightly lower toolchain friction (CMake is first-class in AGP). Precedent exists in the app (Filament). Only justified for something like a SIMD checksum/hash kernel that profiles hot; porting 47k lines of Kotlin logic to C++ is *strictly* dominated by (4) |
| **6** | **Full rewrite in Rust** | **75–210 EM** | **Extreme** | **≈0–5%, possibly negative** | §2–§6. Adds a permanent dual-language maintenance burden (Kotlin shims stay for every platform API in §3), a 16 KB/4-ABI compliance obligation, a rewritten 21.7k-line test suite, and a hardware QA matrix, in exchange for performance in the one place users cannot feel |
| **7** | **Status quo (do nothing)** | **0 EM** | **None** | **0** | Perfectly acceptable *as a baseline*, but it leaves alternatives 1–3 on the table. Note that "status quo" and "alternatives 1–3" are not mutually exclusive — they are the same codebase |

### 7.1 Recommended programme

1. **Sprint 0 (2–3 weeks):** enable Macrobenchmark + Perfetto; record cold start, farm frame times, radar frame times, arena battle wall time, save-import wall time. Publish the numbers.
2. **Sprint 1:** land alternative (2) — R8/minify + Baseline/Startup Profiles + resource shrinking. Re-measure. Expect the biggest single delta.
3. **Sprint 2–3:** land alternative (1) — starting with the `ArenaRepository` snapshot loop, then Room/query/index review, then streaming I/O.
4. **Sprint 4+:** profile-guided Kotlin work (3) driven by the Sprint-0 traces.
5. **Gate for (4)/(5):** only if a trace shows a single Kotlin computation consuming **>20%** of a user-visible operation **and** the data crossing the boundary is small (a seed + result, not per-element arrays). Then extract exactly that, behind UniFFI, with the Kotlin app unchanged.

### 7.2 Kill criteria for the Rust-track

Abandon the extraction if any of these is true after the spike (1–2 EM):
- The candidate module is <20% of any user-visible operation.
- The boundary needs per-element/multi-MB marshaling (JNI/UniFFI copies eat the win).
- The module depends on Android types (then it is a bridge, not a port).
- The module is the NFC transport, Filament rendering, or anything lifecycle-bound — those are [X] or [J] by construction (§3).

---

## 8. Sources

Every external claim above, with the URL used. Fetch date 2026-10-30. "UNVERIFIED" means the claim is stated but not established from a primary source I could retrieve.

**Rust on Android**

1. cargo-ndk — build Rust for Android, MSRV 1.86, `jniLibs` layout, NDK auto-detect, `--link-builtins`/`--link-libcxx-shared`: https://github.com/bbqsrc/cargo-ndk
2. `mozilla/rust-android-gradle` — Gradle plugin, 1,283 stars, last push 2026-09-24: https://github.com/mozilla/rust-android-gradle
3. `willir/cargo-ndk-android-gradle` — cargo-ndk Gradle wrapper, last push 2024-07-08 (stale): https://github.com/willir/cargo-ndk-android-gradle
4. `jni` crate — v0.22.4 (2026-03-16), MSRV 1.85, edition 2024, 228M downloads: https://crates.io/crates/jni · docs: https://docs.rs/jni
5. UniFFI — Kotlin/Swift bindings, used in Firefox mobile, "a long way from a 1.0 release", KMP via Gobley: https://github.com/mozilla/uniffi-rs
6. `android-activity` — Rust apps on Android via NativeActivity/GameActivity, 408 stars, last push 2026-06-22: https://github.com/rust-mobile/android-activity
7. Android 16 KB page sizes (primary doc): https://developer.android.com/guide/practices/page-sizes
8. Android Developers Blog — Play 16 KB requirement, Nov 1 2025 (May 2025): https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html
9. Android Developers Blog — transition to 16 KB, Nov 1 2025 restated (Jul 2025): https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html
10. AOSP — 16 KB page size, `max-page-size=16384`, page-size tradeoffs: https://source.android.com/docs/core/architecture/16kb-page-size/16kb · https://source.android.com/docs/core/architecture/16kb-page-size/optimize
11. Feb 1 2027 Play deadline for non-compliant updates — third-party reporting only: https://primetestlab.com/blog/google-play-16-kb-page-size-error · https://rapidlabs.dev/articles/android-16-16-kb-page-size/ — **UNVERIFIED** against the primary policy page
12. Rust + Android + 16 KB + binary-size practitioner series (NDK r28 default alignment, `readelf`/`zipalign` checks, "no R8 for native code", four size levers, launch-time figures quoted from Android docs): https://www.davthecoder.com/blog/rust-binary-size-android-16kb-page-size — the "3.16% average / up to 30%" launch numbers are **UNVERIFIED** against the primary Android page (JS-rendered; only nav retrievable)
13. `min-sized-rust` — strip/LTO/`opt-level=z`/`panic="abort"`/codegen-units: https://github.com/johnthagen/min-sized-rust
14. `@CriticalNative` — "cost in nanoseconds: 25" (measured 2016): https://developer.android.com/reference/dalvik/annotation/optimization/CriticalNative

**Platform APIs with no Rust surface (verdict [X]/[J] in §3)**

15. Compose: https://developer.android.com/develop/ui/compose · Material3: https://developer.android.com/develop/ui/compose/designsystems/material3
16. Room: https://developer.android.com/training/data-storage/room
17. DataStore: https://developer.android.com/topic/libraries/architecture/datastore
18. WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
19. Wear OS Data Layer API: https://developer.android.com/training/wearables/data/overview
20. Location: https://developer.android.com/develop/sensors-and-location/location
21. NFC: https://developer.android.com/develop/connectivity/nfc · HCE: https://developer.android.com/develop/connectivity/nfc/hce
22. Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
23. Runtime permissions: https://developer.android.com/training/permissions/requesting · Notifications: https://developer.android.com/develop/ui/views/notifications
24. Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle
25. Filament — native C++ engine, Vulkan/GLES backends, Java/JNI API for Android: https://github.com/google/filament
26. `filament-bindings` (Rust) — v0.2.2, last published 2022-04-10: https://crates.io/crates/filament-bindings

**UI toolkits**

27. Slint — README (declarative, 1.x, royalty-free license): https://github.com/slint-ui/slint · Android docs (`android-activity`, `slint_main`): https://docs.slint.dev/latest/docs/slint/guide/platforms/mobile/android/
28. egui — Android support via eframe; "Native looking interface" is a non-goal; AccessKit covers Windows/macOS only: https://github.com/emilk/egui
29. Dioxus — "first-class Android/iOS", Webview or **experimental** WGPU/Skia: https://github.com/DioxusLabs/dioxus
30. Vello — 2D renderer (CPU/GPU/compute), not a UI toolkit: https://github.com/linebender/vello
31. Xilem — "experimental high-level architecture for writing GUI apps in Rust": https://github.com/linebender/xilem
32. Qt for Android — supported Android 9 (API 28) → 16 (API 36), NDK r27c, AGP 9.2.1, Qt Quick Controls Material style, `QtQuickView`, no Fragments: https://doc.qt.io/qt-6/android.html
33. GTK 4 on Android — community "experimental android backend", external APK tooling: https://discourse.gnome.org/t/how-do-we-use-the-experimental-android-backend/28689 · GDK backends reference: https://docs.gtk.org/gtk4/running.html

**Performance evidence**

34. Benchmarks Game — index (with its own "far from realistic" caveat): https://benchmarksgame-team.pages.debian.net/benchmarksgame/index.html · methodology: https://benchmarksgame-team.pages.debian.net/benchmarksgame/how-programs-are-measured.html · per-language fastest/measurements (e.g.): https://benchmarksgame-team.pages.debian.net/benchmarksgame/fastest/rust.html · https://benchmarksgame-team.pages.debian.net/benchmarksgame/measurements/rust.html
35. Discord — Go→Rust Read States: GC-pause elimination, matched then beat hand-tuned Go: https://discord.com/blog/why-discord-is-switching-from-go-to-rust
36. Baseline Profiles — up to 40% startup improvement: https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html · overview: https://developer.android.com/topic/performance/baselineprofiles/overview · case study: https://developer.android.com/topic/performance/baselineprofiles/case-study-android-calendar

**Repo-internal evidence (measured or read, not external claims)**

37. `app/build.gradle.kts` — `minSdk 28`, `targetSdk/compileSdk 37`, Java 11, `isMinifyEnabled = false`, protobuf `java { option("lite") }`, Filament/Room/Wearable/Location/WorkManager/Retrofit dependencies.
38. `gradle/libs.versions.toml` — exact versions (§1.3).
39. `app/src/main/AndroidManifest.xml` — permissions, single Activity, FileProvider, AppWidget receiver, **no** notification permission/service declarations.
40. `app/src/main/java/.../battle/offline/tamers/ArenaRepository.kt` — `repeat(60_000)` × `advance(34ms)` × `snapshot()`.
41. `app/src/main/java/.../battle/offline/core/BattleSimulator.kt` — 2,195 lines; `STEP_MILLIS = 34`; `maxDurationMillis = 300_000`.
42. `app/src/main/java/.../rendering/FrameBudget.kt` — paused/30/60 fps Choreographer pacer.
43. `DIGIFARM_25D_PLAN.md` — Compose camera notifications throttled to ~8 Hz to stop stutter.
44. Measured LOC/file counts (§1.1) — commands reproduced in Appendix A.

---

## Appendix A — reproducible measurements

Run from the repo root (read-only). Excludes `build/`/`.gradle/` by targeting `src/` only.

```powershell
# app production code
$root = (Resolve-Path 'app\src\main\java').Path
$files = Get-ChildItem -Recurse -File -Filter *.kt -Path $root
$files.Count
($files | ForEach-Object { (Get-Content -LiteralPath $_.FullName).Count } | Measure-Object -Sum).Sum

# Compose vs non-Compose split
$ui = $files | Where-Object { (Select-String -Path $_.FullName -Pattern '@Composable|androidx\.compose' -Quiet) }
'compose files={0} lines={1}' -f $ui.Count, (($ui | ForEach-Object { (Get-Content -LiteralPath $_.FullName).Count } | Measure-Object -Sum).Sum)

# per-package LOC
$files | ForEach-Object {
  $rel = $_.FullName.Substring($root.Length+1)
  [pscustomobject]@{ pkg = ($rel -split '\\')[0..5] -join '/'; lines = (Get-Content -LiteralPath $_.FullName).Count }
} | Group-Object pkg | ForEach-Object {
  [pscustomobject]@{ pkg=$_.Name; files=$_.Count; lines=($_.Group | Measure-Object lines -Sum).Sum }
} | Sort-Object lines -Descending

# module production LOC
'game-save-core','vb-nfc-reader','pc-save-companion','companion-protocol','vb-dim-reader' | ForEach-Object {
  $m = $_; $f = Get-ChildItem -Recurse -File -Include *.kt,*.java -Path "$m\src\main" -ErrorAction SilentlyContinue
  [pscustomobject]@{ module=$m; files=$f.Count; lines=(($f | ForEach-Object { (Get-Content -LiteralPath $_.FullName).Count } | Measure-Object -Sum).Sum) }
}

# tests
Get-ChildItem -Recurse -File -Include *.kt,*.java -Path app\src\test,app\src\androidTest | Group-Object {($_.FullName -split '\\src\\')[1].Split('\')[0]} |
  ForEach-Object { [pscustomobject]@{ suite=$_.Name; files=$_.Count; lines=(($_.Group | ForEach-Object { (Get-Content -LiteralPath $_.FullName).Count } | Measure-Object -Sum).Sum) } }
```

## Appendix B — one-page summary for the Lead

- **Base:** 93,708 production Kotlin/Java LOC measured (app 84,371 in 664 files + modules 9,337; tests excluded); 48.6% of app code is Compose-touching; **zero** Rust/C/C++ and no NDK today.
- **Workload classification:** I/O-bound (NFC/disk/network), GPU-bound (Filament already native C++), framework-bound (Compose/Binder/ART). Only a thin CPU-bound layer (battle sim, codecs, crypto) is addressable — and the biggest measured waste in that layer is a **Kotlin** allocation pattern (`snapshot()` every one of up to 60,000 sim steps).
- **Effort:** **75 / 125 / 210 EM** (optimistic / realistic / pessimistic) for full parity, of which UI parity 25/45/70 and new platform bridges 12/20/34. A UI-preserving "Rust core only" variant is **≈81 EM** — still 6.75 engineer-years, for a §6 gain of ≈0–5%.
- **Verdict:** reject the rewrite. Do (1) algorithmic/IO fixes, (2) R8 + Baseline Profiles (currently completely absent — the largest untapped win, up to 40% startup per Google), (3) profile-guided Kotlin. Keep (4) targeted UniFFI extraction as a *conditional*, gated on trace evidence.
- **Top 3 blockers:** UI toolkit gap (§4), platform-API JNI border (§3), economics/QA (§5).