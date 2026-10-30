# 02 — Performance audit: does VBHelper have a CPU-bound problem a Rust rewrite would fix?

**Audit type:** read-only evidence review + two purpose-built microbenchmarks.
**Repo:** `C:\Users\julye\IdeaProjects\vbhelper` (VBHelper, Kotlin/Compose Android app, Alpha 0.6.5, minSdk 28 / targetSdk 37).
**Scope:** every hot path the brief named (image/pixel work, binary save parsing, battle simulator, Room/repositories, Filament viewports, chat/network, NFC) plus the artifacts offered as performance evidence.
**Write scope:** this file only. No project source, build script, or artifact was modified.
**Counting convention:** all LOC below are **non-blank lines in main source sets only**, excluding anything under `\build\`, `\.gradle\`, `\.kotlin\`, `\.git\`, `node_modules`. Blank-inclusive totals are ~8–10 % higher.

---

## 0. Verdict (read this first)

| Question | Answer | Confidence |
|---|---|---|
| **Is there a *measured*, user-visible performance problem?** | **No.** There is not a single runtime measurement, profile, trace, or frame-timing capture anywhere in the repository. The only measured resource event in the tree is a **Gradle build-daemon OOM on the developer's laptop** — a build-environment incident, not app behaviour. The two artifacts held up as performance evidence (`hs_err_pid20620.log`, `replay_pid20620.log`) are a JVM crash log and a HotSpot **compile-replay** dump; neither contains a single VBHelper sample. | **High (0.9)** for "no measurement exists". **Medium (0.6)** for "therefore no user-visible problem exists" — absence of measurement is not proof of absence, and §6 lists exactly the two paths a real measurement should target. |
| **Would a whole-project Rust rewrite fix it?** | **No.** ~73 % of the hot-path inventory is bound by things the language cannot touch (GPU/Filament, Compose layout, network/LLM, SQLite/flash I/O, NFC hardware). The CPU-tight remainder has measured costs of **6–15 ms per generated 3D model**, **~45 ms per 13.3 MB JSON parse**, and **microseconds per simulation step**. Best realistic Rust payoff is on ~2 % of the inventory, worth on the order of **0.1–0.4 s once per battle/farm load**, and it would be paid for with a new NDK/JNI build, per-call marshalling, and a second toolchain — while the app's release build is still **unminified with no baseline profile** (`app/build.gradle.kts:31`). | **High (0.85)** on "the rewrite is not the fix". |

**One-line answer:** the premise "rewrite it in Rust for optimization" is not supported by any evidence in this repository; the app is I/O-, GPU- and framework-bound, and the single biggest *sustained* cost (60 Hz battle-state → Compose) is precisely the kind of cost Rust cannot address.

---

## 1. Method

1. Read the artifacts offered as performance evidence (`hs_err_pid20620.log`, `replay_pid20620.log`, `build-logs/*`, `TESTING.md`, `DIGIFARM_25D_PLAN.md`, `.plan/assemble*.log`) and separated **what they prove** from what they are routinely assumed to prove (§2).
2. Read the real implementations of every performance-sensitive subsystem, with line numbers (§4).
3. Because no profile exists, I **measured the two CPU-tight algorithms myself** with faithful desktop ports (§3): the procedural sprite→GLB extruder, and the actual 13.3 MB JSON asset through the actual Gson version the app ships (2.14.0). Both harnesses live outside the repo (in `%TEMP%`); see Appendix B.
4. Classified each hot path by **dominant bound**, then quantified how much of the inventory Rust cannot move (§5).
5. Ranked the *real* levers, which are Kotlin/build-config side (§6).

**Anti-overclaim rule applied throughout:** no bottleneck is asserted without (a) a measurement made for this audit, (b) a quoted artifact line, or (c) quoted code plus an operation count.

---

## 2. What the offered artifacts actually prove — and do not prove

### 2.1 `hs_err_pid20620.log` (735,136 bytes) — a **Gradle build-daemon** OOM, not an app problem

| Evidence | Line |
|---|---|
| `There is insufficient memory for the Java Runtime Environment to continue.` | 2 |
| `Native memory allocation (malloc) failed to allocate 1438552 bytes. Error detail: Chunk::new` | 3 |
| `Out of Memory Error (arena.cpp:186), pid=20620, tid=29092` | 21 |
| `JRE version: ... JBR-25.0.4.1+1-583.48-jcef` | 23 |
| Command line contains `-Xmx2048m ... org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1` | 30 |
| `Host: AMD Ryzen 7 7730U ..., 16 cores, 15G, Windows 11` | 32 |
| `elapsed time: 8854.654254 seconds (0d 2h 27m 34s)` | 33 |
| `Current CompileTask: C2:8854656 275148 4 kotlin.text.Regex::matches (18 bytes)` | 40–41 |
| `_java_thread_list=..., length=131` | 74 |
| `Memory: 4k page, system-wide physical 15772M (343M free)` / `TotalPageFile size 64924M (AvailPageFile size 2M)` | 6253–6254 |
| `working set ... peak: 2477M`, `commit charge ... peak: 3059M` | 6255–6256 |
| `garbage-first heap total reserved 2097152K, committed 1930240K, used 1388315K` | 307 |

**What it proves, precisely:**

* The failing process is the **Gradle 9.6.1 daemon** (`pid 20620`), running with `-Xmx2048m` — which is exactly this project's configured daemon heap (`gradle.properties:9`: `org.gradle.jvmargs=-Xmx2048m`). It is **not** the VBHelper app and not an Android runtime.
* The failure is **native memory, not Java heap**: only 1.32 GB of the 2.0 GB heap was in use (line 307) when the C2 compiler's assembly arena (`Chunk::new`, `arena.cpp:186`) could not get 1.4 MB. The host had **343 MB of 15.7 GB RAM free and 2 MB of pagefile available** (line 6253–6254) — the machine was out of commit charge, not out of JVM heap.
* It happened **2 h 27 m into a session** (line 33) with 131 daemon threads alive (line 74).
* The virtual machine died mid-optimisation of `kotlin.text.Regex.matches` (line 40–41) — i.e. it died **compiling Gradle/Kotlin tooling code**, not running app logic.

**What it does NOT prove (and must not be used to argue):**

* Anything about VBHelper's runtime memory behaviour, leaks, GC pressure, or CPU cost.
* That the app or its tests are "too heavy". The most likely contributors to host memory pressure were Android Studio plus an emulator plus the daemon (`.plan/` contains uiautomator dumps `ui_*.xml` and emulator screenshots, so a device/emulator was in use), but **the log contains no process inventory**, so even that is inference, not evidence.
* Any relationship to the app's own performance. A defensible reading is only: *the dev machine ran out of RAM/pagefile during a long Gradle build at `-Xmx2048m`.*

### 2.2 `replay_pid20620.log` (4,376,884 bytes) — a HotSpot **JIT compile-replay** file, **not a profile**

The brief asked me to say so if true. It is true, and it is worse than "not a profile":

| Evidence | Value |
|---|---|
| Format marker | `version 2` (line 1) — the `ciReplay` compile-replay format |
| Record types present | `instanceKlass` ×35,006, `#` comments ×6,024, `ciInstanceKlass` ×166, `staticfield` ×157, `ciMethod` ×16, `ciMethodData` ×11, `compile` ×**1**, `version` ×1 |
| The single `compile` record | line 41,385 (last line): `compile kotlin/text/Regex matches (Ljava/lang/CharSequence;)Z ... inline 11 ... java/util/regex/Matcher matches (II)Z` |
| Occurrences of `vbhelper` in the whole file | **0** |
| What its counters describe | `ciMethodData java/util/regex/Matcher matches ()Z ... 170414` (line 41,379) and `kotlin/jvm/internal/Intrinsics.checkNotNullParameter ... 6118383` — **Gradle/Android-test-tooling classes** (`com.android.tools.utp.*`, `com.google.testing.platform.*`), i.e. the daemon's own work, not VBHelper |

This file is the **input HotSpot dumps so a failed C2 compilation can be reproduced offline** — it is produced alongside the crash, and it exists for exactly one reason: the compiler died compiling `kotlin.text.Regex.matches`. It contains:

* **no samples**, **no timestamps**, **no wall-clock or CPU time**, **no call stacks**, **no per-method cost**;
* **zero** application classes.

Conclusion: it cannot support any claim about VBHelper hot paths. Any "the JIT log shows Kotlin's Regex is hot" reading is measuring *Gradle's* Regex usage. Treating it as a profile would be a methodological error.

### 2.3 `build-logs/` (2 files, 36,512 + 42,350 bytes) — failed Kotlin compiles, no resource data

* Both are **UTF-16LE** (BOM `FF FE`) PowerShell captures whose lines are hard-truncated at **119 characters** (e.g. `e: file:///C:/Users/julye/IdeaProjects/vbhelper/app/src/main/java/com/github/nacabaro/vbhelper/digifarm/FarmWorldPrefer` — the diagnostic is cut off mid-path).
* Both end in `> Task :app:compileDebugKotlin FAILED` / `BUILD FAILED in 30s` (`phase0-compile2.log`), with Kotlin errors concentrated in digifarm/screens sources.
* They prove only that the `:app` module was mid-refactor and not compiling at capture time. A 30 s failed incremental compile is unremarkable; **no timing, memory, GC or CPU data is present**.

### 2.4 `TESTING.md` (200,505 bytes) and the plan docs — explicit admissions that nothing was measured

This is the strongest documentary evidence in the tree, and it points the opposite way from the premise:

* `TESTING.md:1316-1317`: *"Native tests remain compile-only; `adb devices -l` reports no connected device. **Actual phone loading times, native screenshots and TalkBack behavior have not been measured.**"*
* `TESTING.md:1304-1306`: perf-adjacent work that *was* done is described in **code** terms, not measurements: *"Preparation batches archive reads and database writes, skips installed artwork, caches immutable frames, and runs outside Radar mutation transactions. Arena indexing no longer reads all image BLOBs just to check renderability."*
* `DIGIFARM_25D_PLAN.md:347`: the 60 fps target is stated as a **goal**: *"Meta: desenho estável próximo de 60 fps onde viável … **não tratar a meta como medição já obtida**."* The same document (line 234) still lists *"conferir o resultado em aparelho … custo de GPU com 12 moradores"* as outstanding work.
* `TESTING.md:1063`: *"timing remain unverified. The production verified-profile registry is still empty"*.

**No profiler capture exists at all:** a recursive search for `*.trace`, `*.perfetto-trace`, `*.hprof`, `*.alloc` outside build outputs returns **nothing**.

**No measurement harness exists either:** there is no `macrobenchmark` module, no `androidx.benchmark`, no `profileinstaller`, no baseline-profile module anywhere in the build files, and `settings.gradle.kts` includes only `:app`, `:vb-nfc-reader`, `:vb-dim-reader`, `:game-save-core`, `:companion-protocol`, `:pc-save-companion`.

### 2.5 `.plan/assemble*.log` — the only repeated timing data in the repo, and it is build-side

21 captured `assemble` runs: `5s, 15s, 20s, 20s, 26s, 27s, 27s, 28s, 29s, 34s, 35s, 38s, 40s, 50s, 1m1s, 1m5s, 1m12s, 1m57s`, plus `BUILD FAILED in 10s` and `.plan/baseline_build.log` `BUILD FAILED in 37s`.

So the **only** thing in this repository with repeated, comparable timing data is the **developer build loop** — not app runtime. If the user's felt pain is "the project is slow", the evidence points at the Gradle/Android build (single-module 79 kLOC app, `-Xmx2048m` daemon, `org.gradle.parallel` commented out at `gradle.properties:13`, R8 disabled), and a Rust rewrite would make that *worse* by adding a Cargo/NDK stage.

---

## 3. Measurements performed for this audit

No profile existed, so the two candidate CPU-tight algorithms were measured directly. Harness: separate JVM processes (`Temurin 25.0.4.1`), best-of-5..10 after warm-up, faithful ports of the Kotlin/Java source (same loops, same boxing, same `Deflater`/`CRC32`/`MessageDigest` calls). Harness sources: Appendix B.

### 3.1 Procedural sprite → 3D mesh → GLB (`SpriteExtrusionGlb.build`)

Port covers `outline()` (2× supersample + 1 px border), the **two** SHA-256 passes (`contentKey()` over all pixels + `frameContentKey()` per unique frame), `buildPoseGeometry()` (one quad per solid pixel edge, with the code's `ArrayList<Float>` autoboxing), the accessor min/max scans, and PNG encoding (per-byte `ByteArrayOutputStream` writes, `DeflaterOutputStream`, `CRC32` chunks). `org.json` tree building is **not** ported — so these numbers are a **lower bound**.

Inputs: 9 poses (`idle, idle2, sleep, train, train2, happy, walk, walk2, run`), synthetic silhouettes at **43 % pixel coverage**; sprite sizes are the real ones from the bundled catalog (`species_catalog.json`: 48×48 ×2,625 sets, 36×36 ×494, 72×72 ×247 → mean 48.0×48.0).

| Phase | 9 poses @ 48×48 | 9 poses @ 72×72 |
|---|---|---|
| `outline()` | **0.22 ms** | 0.34 ms |
| `contentKey()` + per-frame `frameContentKey()` | **2.44 ms** | 2.96 ms |
| `buildPoseGeometry()` + accessor min/max | **0.88 ms** | 0.60 ms |
| PNG encode (deflate + CRC32) | **2.08 ms** | 3.31 ms |
| **Sum of phases** | **≈ 5.6 ms** | ≈ 7.2 ms |
| End-to-end harness run (includes a duplicate PNG encode in my port; **not** the real cost) | 15.2 ms | 24.4 ms |

Operation counts (48×48, 9 poses): 20,736 pixels; 8,883 solid pixels; 1,296 boundary quads; **25,968 boxed `Float`s + 7,776 boxed `Integer`s** allocated by the geometry builder; 165,888 `MessageDigest.update(byte)` calls by the two hash passes.

**Interpretation.** The real end-to-end cost is roughly the phase sum, **6–8 ms per model on this laptop's single core** at 48×48. A mid-range phone core is typically 2–4× slower on allocation-heavy integer/JSON-style code, so **≈ 15–30 ms per model**. Nothing here is a crisis — and crucially this runs **off the main thread** on `Dispatchers.IO` (`TrainingBattlePresentation.kt:131`, model built at `:245`) or on `Dispatchers.Default` with per-resident `async` (`FarmHomeWorld.kt:311-315`, `DigifarmScreen.kt:373-391`), and is **LRU-cached** (`SpriteExtrusionGlb.kt:19,25-26,190-199`; cache reuse is asserted by `SpriteExtrusionGlbTest.kt:124-134`).

### 3.2 The real 13.3 MB JSON asset through the shipped Gson (2.14.0)

`BundledDotSpriteLibrary.catalogBlocking()` (`species/BundledDotSpriteLibrary.kt:40-44`) Gson-parses `app/src/main/assets/species_catalog.json` (**13,319,315 bytes**) on first use per process. Measured with `gson-2.14.0` (the version pinned in `gradle/libs.versions.toml`), warm-up ×3 then best-of-5:

| Operation | Desktop time |
|---|---|
| Gson DOM parse (`JsonParser` → `JsonObject` tree), 13.3 MB | **45 ms** |
| Gson reflective bind → `Map<String,Object>`, 13.3 MB | **44 ms** |
| Gson DOM parse `blast_evolution.json` (2.25 MB) | **9 ms** |
| SHA-256 over `dot_sprites.zip` (12.99 MB) | **8 ms** |
| Raw file read of the 13.3 MB asset (OS cache warm) | **7 ms** |

**Interpretation.** Call it **150–500 ms once per process on a phone** (the app binds a concrete DTO tree — 3,368 sets × 12 frames + 1,662 identities — which is slower than the `Map` bind and allocates heavily). This is the **largest single CPU burst found anywhere in the app**, and it is still sub-second, one-shot, cached in memory (`cachedCatalog`, line 26) and run on `Dispatchers.IO`. A Rust JSON parser might make it 2–3× faster — saving ~0.1–0.3 s once per process — but the correct fix is a **binary/SQLite asset**, not a new language (§6).

### 3.3 What I did **not** measure, and will not pretend to

* Android runtime behaviour: cold start, frame times, GC pauses, `Bitmap` decode, Filament GPU time. **No device or emulator was attached for this audit** (`adb devices -l` is not available in this environment), so all phone figures above are explicitly **extrapolations** from desktop numbers, labelled as such.
* Compose recomposition cost (no Compose runtime on the desktop harness). §4.2 argues it from code structure only.

---

## 4. Hot-path inventory

### 4.1 The table

`Bound` vocabulary: **CPU-tight** = arithmetic/branch work; **CPU-memory** = allocation, GC, hashing, parse trees; **IO-network**; **IO-disk**; **GPU**; **Android-framework** = Compose/Room-AppWidget/NFC stack; **Hardware** = tag/radio latency.

| # | Path (line refs) | What it does | LOC | Dominant bound | Realistic Rust upside | Evidence |
|---|---|---|---|---|---|---|
| 1 | `app/src/main/java/com/github/nacabaro/vbhelper/rendering/sprite3d/SpriteExtrusionGlb.kt:66-486` | Turns each pixel-art pose into a 3D solid: 2× supersample outline, **two** SHA-256 pixel passes, per-pixel edge quads, hand-built PNGs, GLB pack | 453 | **CPU-tight / CPU-memory** | **Moderate** (6–8 ms desktop, ~15–30 ms phone, per model) | §3.1 measurement; call sites `TrainingBattlePresentation.kt:131,245,260`, `FarmHomeWorld.kt:311-315,483`, `DigifarmScreen.kt:391`; LRU cache `:19,25-26,190-199` |
| 2 | `app/.../battle/offline/core/BattleSimulator.kt:151-165,490-2195` (+ `BattleModel.kt`) | Fixed-step real-time battle sim: `advance()` accumulates frame delta, runs `STEP = 34 ms` steps (`BattleRules.STEP_MILLIS`, `BattleAiProfile.kt:21-24`) over **≤4 combatants** (1×1, 1×2, 2×2 — `TrainingBattleFactory.kt:65-67`) | 2,114 + 495 | **CPU-tight, but micro-scale** | **None** | `while (accumulatedMillis >= STEP ...)` `:161`; per step it iterates combatants, ≤8 impacts (`MAX_ACTIVE_IMPACTS`, `:2184`), projectiles and small maps — O(actors²)≈16 pairs × 29.4 steps/s |
| 3 | `app/.../battle/offline/session/BattleSessionController.kt:38-58` + `app/.../screens/offlineBattle/OfflineBattleSessionViewModel.kt:224-258` | 16 ms tick loop → `simulator.snapshot()` → `MutableStateFlow` → `_state.value = _state.value.copy(snapshot = …)` (deep `equals` on every emission) | 100 + 362 | **Android-framework (StateFlow/Compose)** | **None** | `FRAME_INTERVAL_MILLIS = 16L` `:106`; `_snapshot.value = simulator.snapshot()` `:53`; state copy `:251`; further publishes at `:70,80,89,102` |
| 4 | `app/.../screens/offlineBattle/OfflineTrainingBattleScreen.kt:211-213,431,789,1492-1555,1748-1761` | Reads `state.snapshot` in composition 20+ times, allocates `alliedMembers + opposingMembers` **inside** composition, animates projectiles/impacts per frame | 2,553 | **Android-framework (Compose layout/recomposition)** | **None** | `val snapshot = state.snapshot` `:213`; `(snapshot.alliedMembers + snapshot.opposingMembers)` `:1492,1748`; `snapshot.projectiles.forEach` inside composition `:1529` |
| 5 | `app/.../screens/offlineBattle/OfflineBattleScene.kt:292-1428` | Filament arena: `Engine.create()`, `ModelViewer`, per-frame scene updates, layer-mask swaps, shadow passes | 1,381 | **GPU** | **None** | `Engine.create()` `:293`; `ModelViewer(...)` `:294`; `viewer.render` loop; `setLayerMask` churn `:398,1005,1181-1196,1302` |
| 6 | `app/.../screens/digifarmScreen/Digifarm3dViewport.kt:407-427,753-1532` + `screens/worldScreen/RadarFirstPersonViewport.kt` | Choreographer-driven Filament diorama, 30 fps idle / 60 fps active, resident motion + follow + floating blocks | 1,542 + 301 | **GPU** (paced) | **None** | `Choreographer.FrameCallback.doFrame` `:407-427`; `SceneFrameBudget.tier`/`pacer.claim` `:411-415`; `viewer.render(frameTimeNanos)` `:425`; tiers in `rendering/FrameBudget.kt:8-15` |
| 7 | `app/.../species/BundledDotSpriteLibrary.kt:40-44,103-127` | Gson-parse of the 13.3 MB `species_catalog.json`; copy + **double** SHA-256 of the 13 MB `dot_sprites.zip` into cacheDir (re-hashed **every process start**, `:108`) | 170 | **CPU-memory (parse) / IO-disk (pack)** | **Moderate, but wrong lever** (see §6) | §3.2 measurements; `cachedCatalog` `:26`; `hash(target)` `:108`; `hash(temporary)` `:122` |
| 8 | `app/.../species/OfficialSpriteMatcher.kt:93-106,145-165` + `app/.../utils/BitmapData.kt:64-106` | Builds a lazy first-frame index over the bundled catalog (per-set PNG/frame decode + SHA-256 mask key), then exact-mask + RGB565-tolerance matching; per-sprite `IntArray` expansion for `Bitmap` | 166 + 91 | **CPU-tight, one-shot** | **None** | `firstFrameIndex by lazy` `:93-106`; `maskKey()` SHA-256 `:145-147`; tolerance loop `:159-163`; `createARGBIntArray()` `:92-106` |
| 9 | `app/.../chat/ChatRepository.kt` + `chat/OpenRouterClient.kt:15-32` + `chat/OpenRouterService.kt:8` | LLM chat/quest generation over HTTPS; the app waits on a remote model | 742 + 36 + 11 | **IO-network** | **None** | `@POST("chat/completions")` `OpenRouterService.kt:8`; `OkHttpClient.Builder()` `OpenRouterClient.kt:15`; `Retrofit.Builder()` `:32`; suspend calls in `ChatRepository` throughout |
| 10 | `app/.../database/AppDatabase.kt:95-99` + `app/.../daos/*.kt` (41 files) | Room/SQLite: 60+ entities, schema v55, joins that select sprite BLOBs inline with rows | 987 + 2,494 | **IO-disk** | **None** | `@Database(version = 55, …)` `:95-96`; e.g. `CharacterDao.kt:29,42` select `sprite.spriteIdle1` with each character row; no `allowMainThreadQueries` anywhere in `app/src/main`; 37 entity files declare `indices` |
| 11 | `game-save-core/src/main/kotlin/.../gamesave/*.kt` (`NdsStorySaveReader.kt:94-201`, `NdsStoryRecordCodec.kt:296-299`, `WorldDsPartyEdits.kt`, `NdsStoryCopyIntegrity.kt:19,31`) | Binary save parsing/writing: little-endian bit extraction, record codecs, **SHA-256 over whole save copies**, byte-preserving move journals | 3,487 (module, main) | **IO-disk + CPU-small** | **None/low** | `value or ((bytes[at+i].toLong() and 255) shl (8*i))` `NdsStorySaveReader.kt:201`; `MessageDigest.getInstance(profile.copyHeader.digest).digest(copy)` `NdsStoryCopyIntegrity.kt:19`; payloads are one-shot save files |
| 12 | `vb-nfc-reader/src/main/java/com/github/cfogrady/vbnfc/*` (`TagCommunicator.kt:1-201`, `CryptographicTransformer.kt:1-95`, `ChecksumCalculator.kt:1-36`) | NFC tag read/write, per-16-byte-page XOR checksums, substitution-cipher transform | 1,853 (module, main) | **Hardware (NFC) + CPU-trivial** | **None** | `PagesWithChecksum` page loop `ChecksumCalculator.kt:7,26-33`; 16-byte page arithmetic; latency is dominated by tag/radio round trips, not by Kotlin |
| 13 | `vb-dim-reader/src/main/java/com/github/cfogrady/vb/dim/**` (Java; e.g. `sprite/SpriteData.java:41-83`, `sprite/UnorderedSpriteChecksumHacker.java`) | DIM-card binary format reader + RGB565→RGB888/BGRA per-pixel expansion, sprite checksum areas | 2,625 (module, main) | **CPU-tight, tiny** | **None/low** | `getBGRA()`/`get24BitRGB()` loops `SpriteData.java:41-83`; inputs are 36–72 px sprites, executed on card import only |
| 14 | `app/.../widget/DigimonWidgetProvider.kt:284-418` | AppWidget bitmap compositing + `RemoteViews` updates from `Dispatchers.Default` | 407 | **Android-framework** | **None** | `ioScope.launch(Dispatchers.Default)` `:397`; `Canvas(buffer).drawBitmap` `:411`; `views.setImageViewBitmap` `:418` |
| 15 | `app/src/main/assets/battle_sprites/**` (13,810 PNGs) + `dot_sprites.zip` | Shipping and decoding art at runtime | — (assets) | **IO-disk / APK size** | **None** | Measured: **13,810 PNGs = 61.9 MB**, total assets **113.7 MB**; `assets/battle_sprites/extracted_assets/sprites/dim000_mon01/…` |

### 4.2 Judgements the brief specifically asked for

**`battle/offline/core/BattleSimulator.kt` — turn-based discrete simulator (cheap) or Monte-Carlo/ML (expensive)?**
**Neither: it is a real-time fixed-step simulator, and it is cheap.** Evidence: `advance()` clips the frame delta to 250 ms and then drains the accumulator in `STEP` (34 ms) slices (`:151-165`); `step()` advances movement, status ticks, projectiles, resources and AI decisions once per 34 ms (`:490+`). There is no search, no rollouts, no learning, no iteration-to-convergence, and no large numeric arrays — the "AI" is scalar scoring (`targetSwitchThreshold`, `lowHpRiskThreshold`, weights summed in `:1047-1077`). Team sizes are capped at **2 v 2** (`TrainingBattleFactory.kt:65-67`). Per step the work is bounded by ~4 combatants, ≤8 impacts (`BattleSimulator.kt:2184`), a handful of statuses per actor, and small maps — **microseconds**. Its Rust upside is therefore **none**: even a 10× speed-up of something that costs microseconds per step is invisible.

**`Database`/repositories — N+1, missing indices, main-thread work?**
No systemic defect found. There is **no `allowMainThreadQueries`** anywhere in `app/src/main`, and DB access is consistently wrapped (`withContext(Dispatchers.IO)` appears at 100+ call sites across the repositories/screens; e.g. `SpeciesRepository.kt:73`, `QuestRepository.kt:100`, `LinkedGameSaveRepository.kt:96`). 37 entity files declare `indices`, and hot paths batch explicitly (`BundledDotSpriteLibrary.installAll` chunks 32 sets per Room transaction, `:83-99`; comment at `:74`). The genuine cost is **SQLite + flash I/O**, and the earlier optimisations recorded in `TESTING.md:1304-1306` (batch reads/writes, skip installed artwork, cache immutable frames, stop reading all image BLOBs to test renderability) were already done **in Kotlin**. Rust would sit on the same SQLite and the same flash.

**`Digifarm3dViewport.kt` / `OfflineBattleScene.kt` — GPU-bound or CPU-bound?**
**GPU-bound, and already paced.** Both drive `ModelViewer`/`Engine` (Filament 1.76.1, a C++ engine already); the per-frame Java work is a small amount of JNI (`setPosition`, `setLayerMask`, `viewer.render`) over ~12 residents. The team has already built a **frame budget** (`rendering/FrameBudget.kt`, 30 fps idle / 60 fps active) and fixed the classic churn bug — `DIGIFARM_25D_PLAN.md:135` records *"texturas das 6 poses sobem uma única vez por morador e o tick de animação só troca o bind (setParameter), eliminando o churn de create/destroy de GL a cada 900ms"*. Rust cannot enter the GPU driver, and reimplementing a renderer in Rust would be strictly worse than calling Filament.

**`ChatRepository` / `DigimonPersonaBuilder` — network/LLM-bound?**
**Yes, purely.** `OpenRouterService.kt:8` is `@POST("chat/completions")` over OkHttp+Retrofit (`OpenRouterClient.kt:15-32`); the PVP side talks to `http://battle.io-void.com:8080/` (`battle/RetrofitHelper.kt:45`). End-to-end latency is server-side model generation — **seconds** — with the Kotlin side contributing prompt assembly and JSON decoding of the reply. The upside of rewriting this in Rust is exactly zero; the wall-clock time is spent in someone else's datacentre.

**`vb-dim-reader` — is this the image/vision hot path?**
**No.** It is a third-party-style **DIM card file format** library (`com.github.cfogrady.vb.dim`), 2,625 non-blank LOC of main Java, and its per-pixel work is RGB565 expansion for 36–72 px sprites on card import (`SpriteData.java:41-83`). The app's actual on-screen pixel work lives in `species/`, `battle/` and `utils/BitmapData.kt`. There is **no camera pipeline at all** — scanning is NFC tap, not vision (`screens/scanScreen/ScanScreenControllerImpl.kt:310 characterToNfc`), and there is **no ML/CV dependency** in any Gradle file (no TFLite/MLKit/ONNX/OpenCV/MediaPipe/PyTorch).

**Is there any heavy numeric/ML workload anywhere?**
No. A sweep for `DoubleArray`/`FloatArray`/`matrix`/`convolve`/`FFT`/`gradient`/`histogram` across all four main source sets returns only scalar `Float` battle-AI thresholds (e.g. `BattleAiProfile.kt:16`, `PersonalityBattleProfile.kt:27`). There is no dense math to accelerate.

### 4.3 The single biggest real bottleneck

Two different answers, because "biggest" splits into sustained vs one-shot — and the distinction matters, because **only one of them is even in principle Rust-shaped**:

* **Biggest *sustained* cost (what actually decides whether the screen feels smooth): the 60 Hz snapshot → `StateFlow` → Compose handoff in offline battle.** Every 16 ms the simulator is snapshotted into a fresh object graph (`BattleSimulator.snapshot()`, `:420-483`: ~11 collections at snapshot level plus ~7 maps/lists **per combatant** — `techniqueScores.toMap()`, `techniqueScoreComponents.mapValues{toMap()}`, `threat.toMap()`, `techniqueUses.toMap()`, cooldown `filterValues{}.mapValues{}` …), published through `MutableStateFlow` (which **deep-`equals`s** on every assignment), copied into an immutable `OfflineBattleSessionState` (`OfflineBattleSessionViewModel.kt:251`), and then read 20+ times inside a 2,553-line composable that even allocates `alliedMembers + opposingMembers` **during composition** (`OfflineTrainingBattleScreen.kt:1492,1748`). That is the largest recurring consumer of CPU on the battle screen. It is **Android-framework/Compose-bound**: Rust cannot make `MutableStateFlow.equals`, `data class deepEquals`, or Compose invalidation faster. The fix is architectural Kotlin (split the scene channel from UI state, drive the Filament view directly, `derivedStateOf`, avoid list concatenation in composition) — a few days of Kotlin work, no new language.
* **Biggest *one-shot CPU-tight* cost: procedural sprite→GLB generation** (row 1), measured at **6–8 ms/model desktop, ~15–30 ms on a phone**, times 4–12 models (battle or farm load) → **~0.1–0.4 s** of background work, partially parallelised, LRU-cached. This *is* the one place Rust would show up in a benchmark — and at that magnitude it is a loading-screen polish item, not an optimisation project.

---

## 5. What Rust cannot speed up — quantified

Using the 14 LOC-bearing rows above (LOC total **22,370**; the asset row carries no LOC):

| Dominant bound | Rows | LOC | Share | Can Rust help? |
|---|---|---|---|---|
| GPU / Filament | 5, 6 | 3,224 | 14.4 % | **No** — engine is C++ already; GPU driver/queue time |
| Android-framework (Compose, StateFlow, AppWidget) | 3, 4, 14 | 3,422 | 15.3 % | **No** — language runtime + framework, not app arithmetic |
| IO-network (LLM, PVP) | 9 | 789 | 3.5 % | **No** — server latency, seconds |
| Hardware (NFC) | 12 | 1,853 | 8.3 % | **No** — tag/radio round trips |
| IO-disk (Room/SQLite, assets, save files, checksums) | 7, 10, 11 | 7,138 | 31.9 % | **Effectively no** — same SQLite, same flash; only hashing gains a few ms |
| **Subtotal Rust cannot realistically move** | **10 of 14** | **16,426** | **73.4 %** | |
| CPU-tight (arithmetic/pixel/parse) | 1, 2, 8, 13 | 5,944 | 26.6 % | partly — see below |
| **of which realistic payoff** (procedural mesh/GLB) | 1 | **453** | **2.0 %** | moderate |

Cross-check by row count (less sensitive to file granularity): **10 of 14 rows (71 %)** are bound by GPU/Compose/network/hardware/disk; only 4 are CPU-tight, and of those 3 are micro-scale (`BattleSimulator` microseconds/step, `OfficialSpriteMatcher` lazy one-shot, `vb-dim-reader` 36–72 px conversions).

LOC-weighting is a crude proxy and is offered only as a cross-check to the row count. The stronger argument is per-path: the only measured CPU-tight cost in the app large enough to exceed ~50 ms is the **13.3 MB JSON parse (≈45 ms desktop, ≈150–500 ms phone, once per process)** and it is dominated by parsing/allocating a huge asset — a *data-format* problem, not a *language* problem.

**The hidden cost a rewrite would add, not remove:**
* The project currently has **no native code at all** — no `externalNativeBuild`, no `ndkVersion`, no CMake, no Rust anywhere in any Gradle file. Introducing Rust means adding the NDK/CMake pipeline, a second toolchain to CI, per-toolchain ABI builds (4 ABIs), and a **JNI boundary**. Every hot path above would have to cross that boundary per call: for the 60 Hz battle path that is thousands of marshalling operations per second — usually slower than doing the work in Kotlin.
* The app's release build has **the biggest free lever untouched**: `app/build.gradle.kts:31` sets `isMinifyEnabled = false` for `release`, and there is no `profileinstaller`/baseline-profile artifact in the build. R8 + a baseline profile improve ART-compiled code paths and startup **without touching a single source file** — a build-config change, versus a multi-month rewrite that would leave ART compiling the same UI code.

---

## 6. Where the leverage actually is (ranked, Kotlin-side, cheap)

1. **Cut the 60 Hz state delivery** (§4.3). Send simulation snapshots to the Filament scene through a non-invalidating channel / mutable holder; keep Compose state at UI cadence (orders, HP labels) instead of per frame; hoist `alliedMembers + opposingMembers` out of composition; wrap per-frame reads in `derivedStateOf`. Highest per-hour payoff on the battle screen, and it is the only *sustained* cost found.
2. **Enable R8/`isMinifyEnabled = true` for release + add a baseline profile** (`app/build.gradle.kts:31`; no profileinstaller today). Free wins on startup and every ART-compiled path; also shrinks the APK that currently carries 113.7 MB of assets.
3. **Replace the 13.3 MB JSON with a binary/SQLite asset.** It is the largest one-shot CPU burst measured (§3.2). Loading it into Room at first run (the pattern already used for Dot frames via `dot_sprites.zip` + Room) removes 150–500 ms from the first-use path per process and cuts install size.
4. **Asset diet.** 13,810 individual PNGs (61.9 MB) under `assets/battle_sprites/`; the Dot pack alone is 12.99 MB for 27,419 frames. A packed/atlas format (as already done for Dot) removes per-file I/O and APK bloat; Rust is irrelevant here.
5. **Build-loop throughput** (the only thing in the repo with repeated timings, §2.5): raise `org.gradle.jvmargs` above `-Xmx2048m` on a 15.7 GB machine (`gradle.properties:9` — the very value in the crash log), enable `org.gradle.parallel` (`:13`, commented), enable `android.dependency.excludeLibraryComponentsFromConstraints` (warned 6× per sync in `build-logs/phase0-compile.log`). This is the fix for the *actual* measured resource incident.
6. **Measure before optimising further**: add a `macrobenchmark` module + baseline profile + a Perfetto trace of the battle screen and of first-run `catalogBlocking()`. Right now the project cannot distinguish "smooth" from "janky".

**If after step 6 a specific kernel is still hot and CPU-tight** — most plausibly `SpriteExtrusionGlb` — then the proportionate move is a *targeted* optimisation **inside Kotlin** (reuse `FloatArray`/`IntArray` instead of boxed `ArrayList<Float>`/`Integer`, one `SHA-256` pass instead of two, preallocate the outline buffer, cache the PNG bytes), which the measurements suggest converts 6–8 ms into ~2–3 ms without a new language. A Rust module for that function alone would be defensible; a **whole-project** Rust rewrite is not.

---

## 7. Confidence, and what would change this verdict

**Confidence:**
* "No runtime measurement exists in this repository" — **0.95** (verified exhaustively: no trace/hprof/perfetto files, no benchmark module, no baseline profiles, explicit `TESTING.md:1317` admission).
* "The premise's artifacts do not support a hotspot claim" — **0.95** (`hs_err` = Gradle daemon OOM at 1.32/2.0 GB heap; `replay` = compile-replay with 0 `vbhelper` classes and exactly 1 `compile` record).
* "No CPU-bound problem that a rewrite would meaningfully fix" — **0.85**. The remaining 0.15 is honest uncertainty: nothing has been profiled on a real device, so an unmeasured frame-level problem could exist — but if it does, it is almost certainly in the Compose/Filament delivery path (§4.3), not in arithmetic.
* "Desktop→phone extrapolations in §3" — **0.6**; they are labelled estimates, not measurements. Even at 4× they do not change any conclusion.

**What would falsify this audit** (any one of these would reopen the question):
1. A Perfetto/systrace capture of the battle screen showing >16 ms frames dominated by **app-thread CPU** (not GPU, not Compose measure/layout) — e.g. `snapshot()` or `SpriteExtrusionGlb` on the main thread.
2. A cold-start trace showing the 13.3 MB JSON parse or asset decode on the **main thread**, or >1 s in `catalogBlocking()`.
3. A macrobenchmark showing startup/frame p95 regressions that survive enabling R8 + baseline profiles.
4. Evidence of OOM/GC thrash **on device** (logcat `Low on memory`, ART GC logs, `onTrimMemory` telemetry) — none exists today; the only OOM in the tree is the build daemon's.

Absent those, the evidence-based answer to "should VBHelper be rewritten in Rust for optimization?" is **no** — and if any single component ever justifies native code, it is the 453-LOC procedural mesh generator, as an isolated module, behind a measured regression budget.

---

## Appendix A — evidence index (file:line)

| Claim | Citation |
|---|---|
| Build daemon OOM, native arena, `-Xmx2048m` | `hs_err_pid20620.log:2,3,21,30,40-41` |
| Host memory exhausted, not JVM heap | `hs_err_pid20620.log:307,6253-6256` |
| 131 threads, 2 h 27 m elapsed | `hs_err_pid20620.log:74,33` |
| Replay file is `ciReplay` format, 0 app classes, 1 compile record | `replay_pid20620.log:1,41379,41385` (+ counts: 35,006 `instanceKlass`, 0 `vbhelper`) |
| Build logs are truncated failed compiles | `build-logs/phase0-compile.log`, `build-logs/phase0-compile2.log` (`BUILD FAILED in 30s`) |
| Device loading times never measured | `TESTING.md:1317`; profile registry empty `TESTING.md:1063` |
| 60 fps is a goal, not a measurement | `DIGIFARM_25D_PLAN.md:234,347`; GL churn already fixed `:135` |
| Build-loop timings only | `.plan/assemble*.log` (21 runs, 5 s–1 m 57 s) |
| Release not minified; no baseline profile | `app/build.gradle.kts:31`; no `profileinstaller`/`macrobenchmark` in any `.kts`/`.toml` |
| Gradle heap / parallel | `gradle.properties:9,13` |
| No native code today | no `externalNativeBuild`/`ndkVersion`/CMake/Rust in any Gradle file |
| Sim step 34 ms, ≤4 combatants | `battle/offline/core/BattleAiProfile.kt:21-24`; `battle/offline/data/TrainingBattleFactory.kt:65-67` |
| 16 ms snapshot→state copy | `battle/offline/session/BattleSessionController.kt:53,106`; `screens/offlineBattle/OfflineBattleSessionViewModel.kt:251` |
| Compose reads + in-composition allocation | `screens/offlineBattle/OfflineTrainingBattleScreen.kt:213,1492,1529,1748` |
| GPU 30/60 fps pacing | `rendering/FrameBudget.kt:8-15`; `screens/digifarmScreen/Digifarm3dViewport.kt:407-427` |
| 13.3 MB JSON parse + 13 MB pack hashing | `species/BundledDotSpriteLibrary.kt:26,40-44,103-127`; `assets/species_catalog.json` = 13,319,315 B |
| LLM/PVP network | `chat/OpenRouterService.kt:8`; `chat/OpenRouterClient.kt:15-32`; `battle/RetrofitHelper.kt:45` |
| No main-thread DB queries | no `allowMainThreadQueries` in `app/src/main`; `withContext(Dispatchers.IO)` at 100+ sites |
| NFC is hardware-bound | `vb-nfc-reader/.../ChecksumCalculator.kt:7,26-33`; `TagCommunicator.kt`; scan path is NFC (`ScanScreenControllerImpl.kt:310`) |
| Assets: 13,810 PNG / 61.9 MB / 113.7 MB total | measured inventory of `app/src/main/assets` |
| Sprite sizes in bundled catalog | `species_catalog.json`: 48×48 ×2,625, 36×36 ×494, 72×72 ×247 (mean 48.03) |
| No ML/CV dependencies | grep of all Gradle files for tflite/mlkit/onnx/opencv/mediapipe/pytorch → none |

## Appendix B — measurement harness

Both harnesses were written to `%TEMP%\vbperf\` (outside the repository) and compiled with `javac 25.0.4.1` / run on `Temurin 25.0.4.1`, 16-thread Ryzen 7 7730U, machine otherwise idle.

* `SpriteBench.java` — faithful port of `SpriteExtrusionGlb`'s CPU phases (`outline`, `contentKey`+`frameContentKey`, `buildPoseGeometry` with identical `ArrayList<Float>`/`ArrayList<Integer>` boxing, accessor min/max scan, `png`/`pngBytes`/`pngChunk` with `DeflaterOutputStream` + `CRC32`). `org.json` tree building is **not** ported → reported numbers are a lower bound. Inputs: 9 poses, 43 % pixel coverage, 48×48 and 72×72. Timed best-of-10 after 8 warm-up builds; sub-phases best-of-10.
* `JsonBench.java` — uses the app's pinned `gson-2.14.0` jar against the real `app/src/main/assets/species_catalog.json` and `blast_evolution.json`, plus `dot_sprites.zip` hashed with `MessageDigest("SHA-256")`. Warm-up ×3, best-of-5.
* Both are reproducible from the snippets described here; no repository file was created or modified by either run.