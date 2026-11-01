# Alpha 0.7.0 release checklist

Status: **build/test verification passed on 2026-10-31; not ready for release.** The
post-Phase-6 verification passed (1,157 unit tests, debug + R8 release assembly,
`:app:lint` 0 errors, 281/281 device tests, install/launch smoke), baseline profiles were
regenerated on device, named-class coverage is above 80% line, and device benchmarks
recorded a cold-start baseline. The release remains blocked on `versionCode`/production
signing, the remaining unmeasured performance items (Room query plans, soak, farm
steady-state/thermal), multi-API instrumentation, and owner security sign-off. This
checklist is the honest record of what remains.

Related: [CHANGELOG.md](../../CHANGELOG.md),
[docs/INDEX.md](../INDEX.md), [BASELINE.md](../performance/BASELINE.md),
[ADR 0005](../adr/0005-performance-budgets.md),
[PHASE4_DECISIONS.md](../refactor/PHASE4_DECISIONS.md).

## 1. Version, identity, and branding

- [x] `versionName = "Alpha 0.7.0"` is set in [`app/build.gradle.kts`](../../app/build.gradle.kts).
- [ ] Decide and set the release `versionCode` (currently `1`, matching earlier alpha
      builds; confirm before tagging — Play would require an increment, a local
      `adb install -r` does not).
- [x] `namespace = "com.tamerslab"` and `applicationId =
      "com.github.nacabaro.vbhelper"` in the tree; the split is intentional
      ([ADR 0001](../adr/0001-keep-application-id.md)).
- [x] Launcher label is `Tamers Lab` (`@string/app_name`); application class is
      `com.tamerslab.di.TamersLabApplication`; root project is `TamersLab`.
- [ ] Verify the installed package still upgrades in place over the previous **released**
      build (`adb install -r`) and that the label/launcher icon update without losing data.
      (Smoke substitution: debug â†’ debug-key-signed release â†’ debug `adb install -r` on the
      same `applicationId` succeeded with data preserved and launched; an actual
      older-version upgrade is still untested.)
- [x] Confirm no old `com.github.nacabaro` or `com.github.cfogrady` package names remain
      in source, manifest, resources, tests, or ProGuard references. (Only the intentional
      `applicationId` strings in `ExampleInstrumentedTest` and the benchmark package name
      remain.)

## 2. Changelog and documentation

- [x] [CHANGELOG.md](../../CHANGELOG.md) has an Alpha 0.7.0 entry covering the package
      restructure, rebrand, R8, Koin, typed navigation/UI state, battle domain, hardware
      split, performance work, quality work, and release notes.
- [x] Confirm the changelog contains no fabricated metrics or stale test counts; only
      measured numbers recorded in [BASELINE.md](../performance/BASELINE.md) may be
      cited. (Post-Phase-6 numbers in the changelog are from the recorded verification
      run; performance budgets are marked unmeasured.)
- [x] Confirm the module READMEs and [docs/INDEX.md](../INDEX.md) still match the tree.
      (Relative-link check passed; referenced module classes/files verified.)
- [ ] Confirm [PHASE7_DECISIONS.md](../refactor/PHASE7_DECISIONS.md) is committed if the
      quality-tooling deferral is part of the release notes.

## 3. Build, tests, and static analysis

Run against the release candidate and record the exact commands/results:

- [x] `.\gradlew.bat :app:assembleDebug` — passed.
- [x] `.\gradlew.bat :app:testIntegrityCheckUnitTest` — 900 tests, 0 failures, 3 skipped.
- [x] `.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test :hardware-dim:test :hardware:testDebugUnitTest`
      — 257 tests, 0 failures, 4 skipped (library total).
- [x] `.\gradlew.bat :app:lint` — post-Phase-6: **0 errors**, 422 warnings, 38 hints; no
      error is suppressed by the baseline (25 stale baseline entries are a hygiene
      follow-up only).
- [x] `.\gradlew.bat :app:connectedIntegrityCheckAndroidTest` — 281/281 passed on the
      connected SM-A346M (API 35). The plan's API 28/31/34 matrix is still missing.
- [x] Coverage: `.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport` —
      all six named classes are above the 80% line target (81.0–95.8%); values are
      recorded in BASELINE.md. No threshold is enforced yet.
- [x] Do not tag from a stale or partially run verification. State skipped checks
      explicitly. (Final verification: 1,157 unit tests, 0 lint errors, 281/281 device
      tests, install/launch smoke, profile regeneration and benchmarks. An earlier batch's
      device step aborted before running tests when the wireless-ADB link dropped; the
      device-only step was then run once. The benchmark/profile runs are separate
      device-gated commands and their results are recorded in BASELINE.md.)

## 4. R8 and release artifact

- [x] Build the minified release (`.\gradlew.bat :app:assembleRelease`) and confirm
      `isMinifyEnabled = true` / `isShrinkResources = true` — passed.
- [x] Review `app/proguard-rules.pro` for the current dependency set (Gson/Retrofit,
      protobuf-lite, Room, Filament/gltfio, WorkManager, Koin, kotlinx.serialization,
      hardware-dim Lombok/slf4j warnings); added keeps for the Gson-serialized
      `com.tamerslab.gamesave` and `com.tamerslab.dtos` packages after the package move.
- [x] Regenerate and archive `app/build/outputs/mapping/release/mapping.txt` for this
      candidate — regenerated by the R8 build (101,223,636 bytes).
- [ ] Smoke-test the minified build on a device through the full flows: startup done
      (cold launch OK, no crash entries); scan, battle, farm, transfer, chat and
      settings/import are still untested on the minified build.
- [x] Record the APK size in BASELINE.md — post-Phase-6 R8 release: 149,019,234 bytes
      (142.12 MiB), 22.3% below the Phase 0 baseline and within the <=155.5 MiB budget.
- [x] **Signing:** there is no production keystore. The release artifact is unsigned or
      debug-key-signed for smoke only. Do not describe it as a production-signed release.

## 5. Baseline profiles after the package move

- [x] The committed profiles were re-mapped deterministically to current class names
      with `scripts/remap-baseline-profiles.py` (framework lines untouched; 2,134
      app-class lines rewritten per file; 11 unresolved synthetic lines dropped). This is
      recorded in [CHANGELOG.md](../../CHANGELOG.md) and
      [BASELINE.md](../performance/BASELINE.md).
- [ ] Regenerate on a device (`.\gradlew.bat :app:generateBaselineProfile`) — the
      generator now covers startup plus best-effort scan/battle/farm/transfer journeys.
      The regeneration result for this candidate is recorded with the final verification
      pass; the deterministic re-map remains the fallback if generation cannot run.
- [x] Confirm `profileinstaller` is still an app dependency and the generated files under
      `app/src/release/generated/baselineProfiles/` are the ones shipped — dependency is
      present and `mergeReleaseArtProfile` consumed the re-mapped profiles in the R8 build.
- [ ] Do not claim a startup improvement without a macrobenchmark entry in BASELINE.md.

## 6. Room and migrations

- [x] `AppDatabase` is version 55 and schemas are exported under
      `app/schemas/com.tamerslab.data.database.AppDatabase/` (including `55.json`).
- [x] Confirm no schema change landed after version 55 — only the schema directory moved;
      the exported version/content is unchanged (the git diff is the old directory
      deletion plus the new path).
- [x] Run the migration test suite and confirm no destructive fallback is configured —
      the instrumentation suite (281/281) covers the migration/persistence tests and
      `AppDatabase` has no `fallbackToDestructiveMigration`.
- [x] Confirm the `androidTest` schema asset path still resolves after the package move —
      covered by the passing instrumentation suite and the `schemas` asset wiring in
      `app/build.gradle.kts`.

## 7. Rename / WorkManager / upgrade smoke

- [ ] Upgrade install over the previous **released** build with the same `applicationId`;
      verify the collection, linked saves, imported secrets, settings, and theme survive.
      (Only the same-tree debug â†’ debug-signed release â†’ debug `adb install -r` smoke has
      been run; the older-version upgrade is still untested.)
- [ ] Verify scheduled `WorldAfkWorker`/`WorkManager` jobs still run after the upgrade and
      after a reboot; the rename must not orphan persisted worker class names.
- [ ] Verify deep links: `vbhelper://auth`, the localhost `:8080/authenticate` callback,
      and the VitalWear `ACTION_SEND` import.
- [ ] Verify the `FileProvider` authority still uses `${applicationId}.provider` and the
      widget receiver (`DigimonWidgetProvider`) still updates.
- [ ] Verify backup/restore behavior and the data-extraction exclusions for secrets,
      tokens, LLM settings, and logs.

## 8. Security sign-off

- [x] Record the technical review evidence: see
      [docs/security/SECURITY_REVIEW_0.7.0.md](../security/SECURITY_REVIEW_0.7.0.md)
      (cleartext config, backup exclusions, exported components, no committed
      secrets/keystores, no secret-bearing logs; residual risks restated).
- [ ] Re-review the residual risks in
      [PHASE4_DECISIONS.md](../refactor/PHASE4_DECISIONS.md): cleartext legacy
      battle/auth hosts, plaintext token/secret storage, the non-App-Link `vbhelper://`
      callback, the exported VitalWear import, and undeclared companion compatibility
      activities. (No new security-sensitive code landed in this pass beyond the R8 keep
      additions; the review and sign-off are still required.)
- [ ] Confirm no new secrets, tokens, save bytes, or roster names are logged.
- [ ] Confirm `network_security_config.xml` still denies cleartext by default and the
      allowlist is unchanged or justified.
- [ ] Obtain explicit security sign-off from the owner/reviewer before publishing
      ([sign-off table](../security/SECURITY_REVIEW_0.7.0.md#sign-off) is pending).

## 9. Performance gate

- [ ] Check each Alpha 0.7.0 budget in
      [BASELINE.md](../performance/BASELINE.md): cold start, battle p95, farm FPS, APK
      size, Room query plans, zero lint errors, named-class coverage, and 30-minute soak.
      Recorded on 2026-10-31: **APK size** 149,019,234 bytes / 142.12 MiB (meets),
      **lint** 0 errors (meets), **coverage** all six named classes 81.0–95.8% line
      (meets), **cold-start benchmark** 875.0 ms median TTID (absolute baseline; relative
      target undefined because the Phase 0 release baseline was never captured), **battle
      frame CPU** P95 13.3 ms (meets the ≤16.7 ms budget as a CPU-frame proxy), **farm**
      only a 3-second post-startup window (194 median frames, P95 29.8 ms, includes asset
      loading). Still unmeasured: Room query plans, farm steady active/idle, thermal
      downgrade, 30-minute soak.
- [ ] Record missing or stale metrics as **"not measured"**, not as passed. The battle
      channel flag stays off until a flag-on/flag-off Perfetto comparison.
- [ ] If a budget is waived for this release, record the owner approval and the reason in
      BASELINE.md and the release notes.

## 10. Distribution

- [ ] **Play Console upload: NOT done.** No production-signed bundle/APK has been
      uploaded, no store listing was updated for the rebrand, and no staged rollout
      exists.
- [ ] No production keystore or signing configuration exists in the repository.
- [ ] Do not publish the unsigned/debug-key artifact as a production release.

## Exit criteria

Alpha 0.7.0 can be tagged only when sections 1–8 have an explicit result, section 9 has a
recorded verdict per budget (including "not measured" with owner approval), and the
distribution status in section 10 is stated accurately. As of 2026-10-31 the build, unit
test, lint, device-instrumentation, install/launch, R8 mapping and APK-size rows are
green; the measured performance gate, multi-API matrix, owner security sign-off,
`versionCode`/production signing, and Play upload are still open. Until then, the release
remains **in preparation**.
