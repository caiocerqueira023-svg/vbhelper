# 04 — Post-refactor reassessment: does the Rust verdict change?

**Date:** 2026-10-31
**Tree:** uncommitted post-Phase-6/7 refactor (Alpha 0.7.0), after the package/module
restructure and the quality/performance work.
**Supersedes:** the **input measurements and build facts** in
[01-platform-coupling.md](01-platform-coupling.md),
[02-performance-audit.md](02-performance-audit.md), and
[03-rust-feasibility.md](03-rust-feasibility.md). The verdict in 03 is unchanged; this
file re-baselines its inputs against the current tree and re-derives the effort model.
**Method:** read-only static inventory of the current `src/main` trees (PowerShell +
Python), plus the verification results recorded in
[../../docs/refactor/TARGETS.md](../../docs/refactor/TARGETS.md) and
[../../docs/performance/BASELINE.md](../../docs/performance/BASELINE.md). No build or
test was run for this file.

---

## 0. Verdict (read first)

| Question | Answer |
|---|---|
| Does the refactor make a full Rust rewrite more viable? | **No.** The refactor changed names, module boundaries and quality gates, not the language boundary. Compose/Android platform remain the app. |
| Is the performance case for Rust stronger today? | **No, it is weaker.** R8 + resource shrinking + regenerated Baseline Profiles are now in place and measured (cold start 875 ms median TTID, battle frame CPU P95 13.3 ms). The largest "untapped" lever the earlier audit identified has been taken. |
| Is a UI-preserving "Rust core only" variant cheaper now? | **Slightly.** The pure-JVM modules are cleanly separated and test-covered, which reduces extraction *boundaries* work by a small amount. It remains a multi-year project (≈82–84 EM realistic) for an expected ≈0–5% user-visible gain. |
| Recommendation | **Unchanged: reject the full rewrite.** Keep Kotlin/Compose; if Rust is strategically wanted, extract one proven hot module behind UniFFI (3–8 EM) after trace evidence. |

One-line answer: the refactor strengthened the *arguments against* the rewrite (quality
gates and startup work already done, more tests to re-validate) and mildly strengthened
the *targeted extraction* option (cleaner module seams).

---

## 1. What changed since 01–03 (deltas that matter)

| Item | 01–03 assumption (2026-10-30, Alpha 0.6.5) | Current tree (2026-10-31) |
|---|---|---|
| Package layout | layer-based `screens/`, `daos/`, `database/`, `source/` | **26 feature packages** under `com.tamerslab.*`; old layer packages gone |
| Modules | `:app` + `:vb-nfc-reader` + `:vb-dim-reader` + utils | **7 modules**: `:app`, `:hardware`, `:hardware-dim`, `:game-save-core`, `:companion-protocol`, `:pc-save-companion`, `:baselineprofile` |
| R8 / shrinking | `isMinifyEnabled = false`, no shrinking | **`isMinifyEnabled = true`, `isShrinkResources = true`** |
| Baseline Profiles | absent | **present and device-regenerated** (2,723,442 bytes each, 24,408 rules) |
| Release APK | 183 MB unsigned | **149,019,234 bytes (142.12 MiB)** |
| Tests | 1,149 unit / 281 device (historical table) | **1,157 unit (7 skipped, 0 failures) / 281 device (0 failures)** |
| Performance evidence | none; `hs_err` was a Gradle daemon OOM | measured on SM-A346M: **cold start 875.0 ms median TTID**; **battle frame CPU P95 13.3 ms**; farm 3-second window P95 29.8 ms; coverage 81.0–95.8% line on named classes |
| DIM parser language | Java sprite classes being Kotlinized (Phase 4.3) | sprite parser is **Kotlin (12 files)**; **60 non-sprite Java files remain** in `hardware-dim` |
| Native code / NDK | zero Rust/C/C++; no NDK | **still zero**; no `externalNativeBuild`, `ndk`, or `jniLibs` in the app build |
| applicationId / schema | `com.github.nacabaro.vbhelper`, schema v55 | **unchanged** (upgrade-compatible) |

The two stale claims that mattered most in 02/03 were "R8 absent" and "no Baseline
Profiles"; both are now false, and the measured startup/frame numbers above replace the
"nothing was measured" premise. The *structural* verdict was never based on those
numbers alone — it is based on the UI/platform boundary — which is why it survives.

---

## 2. Re-measured code shape (current tree)

Production code = `src/main` of all modules, Kotlin + Java, all lines, excluding
`build/`/generated. Tests excluded.

### 2.1 Modules

| Module | Files | Lines | Java files | Notes |
|---|---:|---:|---:|---|
| `:app` | 676 | 85,152 | 0 | Compose UI + feature logic + Room |
| `:game-save-core` | 27 | 3,788 | 0 | pure Kotlin/JVM save codecs |
| `:hardware` | 35 | 2,083 | 0 | NFC protocol layer |
| `:hardware-dim` | 72 | 3,247 | **60** | DIM/BEM parser; sprite part Kotlin, non-sprite Java remains |
| `:companion-protocol` | 1 | 103 | 0 | JSON/Gson protocol |
| `:pc-save-companion` | 4 | 372 | 0 | desktop companion |
| **Total** | **815** | **94,745** | **60** | 03 model base was 93,708 → **+1.1%** |

### 2.2 Language-boundary classifier (same categories as 01)

| Category | Files | Lines | Share | Pre-refactor (01) |
|---|---:|---:|---:|---:|
| **Compose UI** (`androidx.compose` / `@Composable`) | 190 | 41,193 | **43.5%** | 245 / 43,487 (48.4%) |
| **Filament** (3D, already native C++) | 3 | 671 | 0.7% | 2 / 261 (0.3%) |
| **Android-platform-bound** (AndroidX non-Compose, Play services, WorkManager, Room, DataStore, NFC transport) | 252 | 26,491 | **28.0%** | 226 / 19,979 (22.2%) |
| **Portable logic** (no Android/Compose/Filament; incl. portable libs) | 370 | 26,390 | **27.9%** | 356 / 26,190 (29.1%) |

The percentages moved by a few points because the classifier runs on a new layout and a
slightly larger tree (+1.1% production LOC); the structural conclusion is unchanged:
**~43–48% of the app is Compose UI; ~28% is Android platform code; ~28% is portable.**
No amount of package renaming moves that boundary.

### 2.3 App feature packages (lines)

`battle` 21,257 · `transfer` 10,268 · `world` 8,671 · `farm` 7,385 · `species` 5,357 ·
`storage` 4,075 · `chat` 3,389 · `home` 3,338 · `components` 3,208 · `domain` 3,155 ·
`settings` 2,518 · `data` 2,245 · `quests` 1,781 · `items` 1,259 · `companion` 1,236 ·
`navigation` 1,044 · `rendering` 959 · `dtos` 881 · `di` 632 · `adventure` 571.
The old `screens/` (40.7k lines) is now distributed across feature `*/ui/` packages; it
is still Compose.

### 2.4 API usage (files importing each)

Kotlin/Compose stack: coroutines 200, Compose 190, Gson 44, Room 151, Retrofit 9,
OkHttp 4, Filament 6, DataStore 10, NFC 5, Wearable 4, Location 3, WorkManager 2,
AppWidget 1, Sensor 1. These counts are essentially unchanged from 01 §1.3 (Room 155→151,
Compose 190→190, coroutines 192→200), i.e. the refactor did not reduce platform coupling.

### 2.5 Tests (the parity oracle for any rewrite)

| Suite | Files | Lines |
|---|---:|---:|
| app unit | 156 | 13,354 |
| app androidTest | 49 | 7,111 |
| game-save-core | 22 | 2,950 |
| hardware | 8 | 775 |
| hardware-dim | 16 | 1,214 |
| companion-protocol | 1 | 26 |
| pc-save-companion | 3 | 238 |
| **Total** | **255** | **25,668** |

Current result: **1,157 unit tests (7 skipped, 0 failures)** + **281/281 device tests**.
The refactor added tests (+18% test LOC vs 03's 21,688/227) — good for extraction safety,
but it also **raises the cost of a full rewrite's re-validation**.

### 2.6 Build/toolchain facts (current)

- Kotlin 2.4.0, AGP 9.2.1, Compose BOM 2026.06.00, Room 2.8.4, KSP 2.3.9, Filament 1.76.1,
  Koin 4.1.1, Retrofit 3.0.0, Gson 2.14.0; `compileSdk 37`, `minSdk 28`, `targetSdk 37`.
- R8 + resource shrinking enabled; Baseline Profile plugin + `profileinstaller` wired.
- No `externalNativeBuild`, `ndk { }`, `jniLibs`, and **zero `.rs`/`.c`/`.cpp`/`.h` files**
  in the repository: a Rust path is still greenfield toolchain work.
- Release 149,019,234 bytes; debug 236,064,876 bytes; baseline profiles 2,723,442 bytes
  each (24,408 rules).

---

## 3. Effort model refresh (same assumptions as 03 §5)

03's model is linear in logic LOC; UI parity, platform bridges, test rewrite, hardware
QA and toolchain costs are direct estimates. With +1.1% production LOC and the current
test suite:

| Bucket | 03 realistic EM | Refreshed realistic EM | Why |
|---|---:|---:|---|
| Logic port (save/DIM/battle/world/etc.) | 34.5 | **≈34.9** | +1.1% LOC |
| UI parity (Compose rewrite) | 45 | **45** | Compose LOC essentially unchanged (41.2k vs 41.0k) |
| NEW platform bridges (NFC transport, Room contract, DataStore, Wearable, Location, WorkManager, SAF, permissions, lifecycle) | 20 | **20** | usage counts unchanged |
| Test rewrite / cross-language harness | 12 | **≈13** | 25.7k test LOC vs 21.7k |
| Real-hardware QA (tags/cards, Wear, 4 ABIs, 16 KB, API matrix) | 9 | **9** | unchanged |
| Toolchain/CI/learning | 8 | **8** | still greenfield |
| **Full rewrite (realistic)** | **≈126** | **≈127–130** | within noise of 03's 125 |
| **UI-preserving "Rust core only"** | **≈81** | **≈82–84** | logic + bridges + tests + toolchain minus UI |
| **Targeted single-module extraction** | **3–8** | **3–8** | module seams are cleaner; JNI/NDK cost unchanged |

Range: roughly **76–212 EM** (optimistic–pessimistic), i.e. 6–17 engineer-years solo,
~25–42 months with 5 engineers. For scale, the app is still an **Alpha**, and this is
feature parity with zero product work.

---

## 4. Performance case: weaker than in 02/03

| Workload | Current evidence | Rust's realistic gain |
|---|---|---|
| Cold start | **875.0 ms TTID median** (5 iterations, R8 `benchmarkRelease`); Baseline Profiles + R8 already applied | **negative to ~0** (adds `loadLibrary`/JNI init); the profile/R8 lever is already taken |
| Battle frame time | `frameDurationCpuMs` P95 **13.3 ms** (P90 11.5); scene/UI channel split exists behind a flag | Small CPU-only upside; the sustained cost is framework/Compose-bound |
| Save/DIM parsing | pure JVM; fixture-tested; multi-MB inputs | 1.5–3× on tight parser loops only |
| NFC | hardware I/O latency dominates; protocol ~2.1k portable lines | ≈0 |
| Filament farm/radar | already native C++ | 0 |
| Chat/network | I/O-bound | ≈0 |
| APK size | 142.12 MiB, asset-dominated | slightly worse (4 ABIs of `.so`; no R8 for native) |

The earlier audit's one concrete Kotlin hot spot — `ArenaRepository` taking a full
snapshot on every simulated step — is still a **Kotlin** fix (an afternoon), not a
language problem.

---

## 5. What “works the same” now includes (parity contracts)

A rewrite would have to preserve, among others: `applicationId`
`com.github.nacabaro.vbhelper` (upgrade continuity), Room **schema v55** + migrations +
`room_master_table` contract, DataStore keys/files, protobuf encodings
(`Secrets.proto`, `Character.proto`), Gson JSON shapes used in Room columns and
network DTOs, `WorkManager` worker class names, deep-link URIs (`vbhelper://auth`,
localhost callback, VitalWear `ACTION_SEND`), baseline-profile/profileinstaller
packaging, R8 keep rules, the 26 feature packages' behaviour, and the 1,157+281 test
oracle. The refactor did not make this smaller — it made the contracts **explicit**
(ADRs, module boundaries, tests), which is exactly why they are expensive to reproduce.

---

## 6. Extraction options, updated to the post-refactor tree

| Candidate | Current size | Why it fits Rust | Realistic cost (gated) |
|---|---:|---|---|
| `:game-save-core` (save readers/codecs/planners) | 3,788 lines, 192 tests | pure Kotlin/JVM, fixture-tested, shared by app + PC companion; best cross-platform play | 4–6 EM incl. UniFFI/Gradle/4-ABI/16 KB + parity |
| DIM/BEM parsing (`:hardware-dim`) | 3,247 lines (60 Java + 12 Kotlin) | pure JVM parsing, golden/checksum tests, no Android | 4–6 EM; Java mix is a maintenance argument for a future core |
| NFC protocol core (`:hardware`) | 2,083 lines | mostly protocol/crypto; Android only at the transport edge | 4–5 EM + Kotlin NFC transport bridge |
| Battle simulator | inside `battle` (largest feature, 21.3k incl. UI) | deterministic, test-heavy | 5–8 EM; only if a trace shows >20% of a user-visible operation |

The Module split makes the **boundaries** cheaper (no more layer soup, fewer cycles,
JVM tests as oracle), but it does not reduce the NDK/UniFFI/ABI/16 KB/JNI cost or the
dual-language maintenance burden.

---

## 7. Decision and kill criteria (unchanged)

- **Reject the full rewrite.**
- Keep Kotlin/Compose and continue measurement-driven Kotlin work (Room query plans, I/O
  streaming, Compose stability, farm steady-state/thermal).
- Only consider **one** module extraction behind UniFFI if a Perfetto/Macrobenchmark trace
  shows it consumes **>20%** of a user-visible operation and the boundary payload is
  small (seed/result, not per-element arrays).
- Kill the spike if the candidate depends on Android types, needs multi-MB marshalling,
  or is the UI/3D/lifecycle/NFC-transport layer.
- **What would change this verdict:** a measured Kotlin hot spot that profiling cannot fix
  in Kotlin; or a strategic decision to ship a shared native save/DIM core for Android +
  desktop where the Kotlin/Compose UI stays and Rust is a *library*, not the app.

---

## Appendix — reproducible commands used

```powershell
# production LOC per module (exclude build/generated)
Get-ChildItem app\src\main,game-save-core\src\main,hardware\src\main,hardware-dim\src\main,companion-protocol\src\main,pc-save-companion\src\main `
  -Recurse -File -Include *.kt,*.java | Where-Object FullName -notmatch '\\build\\' |
  Group-Object { ($_.FullName -split '\\src\\')[0] } |
  ForEach-Object { [pscustomobject]@{ module=$_.Name; files=$_.Count; lines=($_.Group | ForEach-Object { (Get-Content $_.FullName).Count } | Measure-Object -Sum).Sum } }

# Compose / Android / portable split: same classifier logic as 01, run over the current src/main trees
# Build knobs, APK sizes, profile sizes, test results: see docs/refactor/TARGETS.md §Post-Phase-6 verification
```
