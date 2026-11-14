# Upstream baseline — nacabaro/vbhelper @ `99d309a`

Purpose: document exactly what the upstream project does at the last commit present in this
repo's history, so the Tamers Lab feature summary can exclude overlaps and claim only new work.
Everything below was verified from the local git object store with `git show`,
`git ls-tree -r 99d309adc1b62af1f2665597e854cc53ea7e864ba`, and
`git grep -i -E "<pattern>" 99d309a -- app/src`; release context from the GitHub API.

## 1. Upstream identity

- Commit: `99d309adc1b62af1f2665597e854cc53ea7e864ba` — 2026-07-01 17:24:49 +0200,
  "Merge pull request #60 from b13st/fix/hce-send-crash". It is an ancestor of Tamers Lab
  HEAD `397ef846cd166bdd58870ef8d594a51194167720`; Tamers Lab is 142 commits ahead.
- Release context (GitHub API): latest release `v0.6.5.1-pre` "Small fixes", published
  2026-07-01 (APK `vbhelper_v0.6.5.1-alpha.apk`, body: fixes "crashing when assigning missions in
  the VitalWear component"). `v0.6.4-pre` (2026-06-18) merged lightheel's battles and
  cfogrady/457R0's VitalWear work and added PT-BR + Japanese translations.
- Version in `app/build.gradle.kts`: `versionName = "Alpha 0.6.5"`, `versionCode = 1`,
  `applicationId = com.github.nacabaro.vbhelper`, `compileSdk/targetSdk = 37`, `minSdk = 28`.
- Modules: **single Gradle module** — `settings.gradle.kts` is
  `rootProject.name = "VBHelper"` + `include(":app")` only. Root dirs: `app/`, `gradle/`,
  `build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`, `README.md`, `LICENSE`.
  No `hardware/`, `hardware-dim/`, `game-save-core/`, `companion-protocol/`,
  `pc-save-companion/`, baseline-profile or benchmark modules (all of those are Tamers Lab's).
- Tech stack (from `app/build.gradle.kts` + `gradle/libs.versions.toml`): Kotlin 2.4.0,
  AGP 9.2.1, Jetpack Compose (BOM 2026.06.00, Material3, Navigation Compose,
  material-icons-extended), Room 2.8.4 + KSP, Proto DataStore + protobuf-lite + protobuf Gradle
  plugin, Retrofit 3.0.0 + Gson + OkHttp logging interceptor, Play Services Wearable,
  Timber + tinylog, manual DI (`di/AppContainer`, `di/DefaultAppContainer`, `di/VBHelper`).
  No Koin/Hilt/Dagger (grep: 0), no Filament/ARCore/maps/LLM dependencies.
- NFC/hardware libs are external JitPack artifacts, not a local module:
  `com.github.cfogrady:vb-nfc-reader:0.2.0-SNAPSHOT`, `com.github.cfogrady:vb-dim-reader:2.1.0`.
  Embedded cfogrady code: `com/github/cfogrady/vitalwear/` (4 files: `SharedDatabaseFactory`,
  `SharedTransferDatabase`, `SharedTransferSeenDao/Entity`) + protos
  `Character.proto` (vitalwear) and `Secrets.proto`.
- README "Features" (verbatim-ish): read characters, view stats, send them back to the watch;
  apply items to characters such as special missions or change timers; store characters in
  Storage; earn items when a special mission (VB only) or a character adventure completes;
  a dex that updates as characters are added and shows evolution requirements and the current
  adventure stage on the watch.
- README "Planned features" (verbatim): "Online battles, undegoing development by `lightheel`";
  "VitalWear compatibility, undergoing development by `cfogrady`"; "Support for multiple
  languages, not yet started"; "Database backup/restore". The README is stale vs. this commit
  (JA/PT-BR translations, DB export/import and the online-battle UI are already in code).

## 2. Feature catalog with evidence at `99d309a` (`app/src/main/java/...`)

| Feature | Evidence | Notes |
|---|---|---|
| NFC watch read/write | `screens/scanScreen/{ReadCharacterScreen,WriteCharacterScreen,WritingScreen,ReadingScreen}.kt`, `ScanScreenControllerImpl.kt` (NfcA `TagCommunicator` for real VB; IsoDep/HCE branch for VitalWear), `converters/{FromNfcConverter,ToNfcConverter}.kt`; dep `vb-nfc-reader` | Both directions ("Watch to VBH" / "VBH to Watch"); `ChooseConnectionScreen` labels device support "VB, VH, VBBE" |
| DiM/BEM card import | Settings "Import card" → `screens/settingsScreen/controllers/CardImportController.kt` (`DimReader`, `DimCard`/`BemCard`); imports card header, characters, evo data, adventure missions, fusions; `WriteCardScreen.kt` writes DIM to watch | Release notes v0.5 "support for custom cards"; v0.6 imports trophies/vitals/win-rate |
| Dex / evolution / fusions | `screens/cardScreen/{CardsScreen,CardViewScreen}.kt` (bottom-nav label "Dex"), `dialogs/DexCharaDetailsDialog.kt` (Tr/Bt/Vr/Wr/Ct requirements), `dialogs/DexCharaFusionsDialog.kt`, `domain/characters/Dex.kt`, `domain/Evolutions.kt`, `domain/card/{CardFusions,PossibleTransformations}.kt`, `CardFusionsDao` | Fusions viewer added in v0.6.2 |
| Storage | `screens/storageScreen/{StorageScreen,StorageDialog,StorageAdventureTimeDialog}.kt`, `StorageRepository`; actions "Send to watch", "Send to VitalWear", "Set active", "Send on adventure", "Delete character" | v0.6.2 "Working store" |
| Items / special missions | `screens/itemsScreen/{ItemsScreen,ItemsStore,MyItems,ItemDialog,ChooseCharacterScreen}.kt`, `daos/{ItemDao,SpecialMissionDao}.kt`, `domain/{items/Items,device_data/SpecialMissions}.kt`, `assets/items.db`; strings `special_mission_{steps,battles,wins,vitals}`, `home_vbdim_special_missions`, `DeleteSpecialMissionDialog` | Credit-based store (10,000 starting credits), item apply to a character |
| Adventure (per-card) | `screens/adventureScreen/AdventureScreen.kt`, `screens/cardScreen/CardAdventureScreen.kt` ("Adventure missions"), `CardAdventureDao` / `domain/card/CardAdventure.kt` (steps required + hidden boss HP/AP/DP/BP), `StorageAdventureTimeDialog.kt`, `source/VitalWearTransformationCountdown.kt` | "Send on adventure" timers, "AdvLvl" in dex; v0.6.1 added adventure-mission progress |
| Settings / card import / DB management | `screens/settingsScreen/SettingsScreen.kt` + controllers: import secrets from official VB Arena APK (`ApkSecretsImporter`, `DexFileSecretsImporter`), import card, credits/about, **export DB / import DB** (`DatabaseManagementController` copies Room `internalDb`; import requires `.vbhelper` extension) | DB backup/restore is implemented despite README "planned" |
| Companion module | `companion/`: `card/CompanionValidateCardActivity` (NFC reader-mode card validation on VB), `card/CompanionImportCardActivity`, `firmware/CompanionFirmwareImportActivity` (Wearable Data Layer), `logs/CompanionWatchLogsActivity` + `CompanionLogService` + `WatchCommunicationService`, `transfer/CompanionCharacterImportActivity` (explicitly "Transfer is currently disabled"), `validation/CompanionValidatedDatabase`+`ValidatedCard*` | **Not wired at this commit**: the single `AndroidManifest.xml` declares only `MainActivity` + `FileProvider`. Commit `6c257b20` "HCE Savepoint" (ancestor) removed all companion `<activity>`/`<service>` entries and the `CardImportProvider`/`SharedTransferSeenProvider` declarations. Staged code, unreachable in a stock install here |
| Online PVP battle client (only battle code upstream) | `battle/` (22 files): `ArenaBattleSystem.kt`, `APIBattleCharacter`, `PVPDataModel`, `OpponentsDataModel`, `PVPService` (`GET api/pvp`), `OpponentService` (`GET api/opponents`), `AuthService` (`POST api/auth/login`/`validate`), `AuthInterceptor`, `RetrofitHelper`, sprite/animation managers (`BattleSpriteManager`, `IndividualSpriteManager`, `AttackSpriteManager`, `HitEffectSpriteManager`, `AnimatedSpriteImage`), animated battle backgrounds | Wired as bottom-nav "Battle" → `screens/BattlesScreen.kt`. Hard-coded plain-HTTP base `http://battle.io-void.com:8080/` and browser auth redirect `http://auth.nacatech.es/begin?...` (`vbhelper://auth` deep link). README credits lightheel "developing the battling part ... including server. Still in the works." This is the **old third-party online PVP client**, not a local battle system; external server domains are very likely dead now (gated/broken) |
| transfer package (actual purpose) | `transfer/TransferRoute.kt` (transport `NFCA`/`HCE`, target `REAL_BRACELET`/`VITAL_WEAR`, export format `VB`/`BE`), `hce/VitalWearHceReaderClient.kt` (ISO-DEP APDU client, AID `F0 56 49 54 41 4C 57 45 41 52` = "VITALWEAR"; negotiate/read-chunk/write-chunk/commit/status), `hce/VitalWearHceSession.kt`, `CharacterTransferPolicyResolver.kt`, `CardImportProvider.kt`, `SharedTransferSeenProvider.kt`; `source/VitalWearCharacter{Importer,Exporter}.kt` | **Character transport to/from the VitalWear watch app**, not game-save import/export. NFC-A ↔ watch HCE via `ScanScreenControllerImpl`; shared-proto share (`application/x-vitalwear-character` SEND intent) in `MainActivity` + Home/Storage "Send to VitalWear"; per-character transfer policy persisted in Room. The two ContentProviders (signature-perm card import / seen-transfer bookkeeping for the companion) are unwired here (declarations removed in `6c257b20`) |
| Sprite viewer | `screens/spriteViewer/{SpriteViewer,SpriteViewerController,SpriteViewerControllerImpl}.kt` (nav "Viewer"), `SpriteDao`, `domain/characters/Sprite.kt`, `utils/BitmapData.kt` | Views loaded card sprites |
| Languages | `res/values/` (English), `res/values-ja/`, `res/values-pt-rBR/` | 3 locales; v0.6.4 release notes credit Japanese + Portuguese translations |
| Hardware/device support | `utils/DeviceType.kt` (`VBDevice`, `BEDevice`, `VitalWear`; `detectDeviceType` by tag name), `DataStoreSecretsRepository` (VBC HMAC keys / VB ciphers), `homeScreens/screens/{VBDiMHomeScreen,BEDiMHomeScreen,BEBEmHomeScreen,UnifiedHomeScreen}.kt`, device data `domain/device_data/*` | VB/VH/VBC/VBBE family + VitalWear watch; no separate hardware module |

## 3. ABSENCE TABLE — Tamers Lab headline patterns vs. upstream `app/src`

Method: `git grep -i -E "<pattern>" 99d309a -- app/src` (matching lines) and
`git grep -i -l -E ...` (files). Naive substring counts are shown separately because several
patterns produce false positives in unrelated identifiers/UI/hex colors.

| Pattern group | Naive count | Real feature hits | Verdict |
|---|---|---|---|
| `llm\|openai\|gemini\|chat completion\|prompt` | 169 lines / 38 files | all `llm` hits are inside `fillMaxSize` etc.; `prompt` = 3 lines of generic Compose/UI copy (`CompanionConfirmation(prompt=...)`). Word-boundary `\bllm\b` = 0; openai / gemini / chat completion / anthropic / ollama / gpt = 0 each | **ABSENT** (no LLM/AI/chat integration) |
| `gps\|location\|fusedlocation\|radar\|osm\|openstreetmap\|geofence` | **0 lines / 0 files** | — | **ABSENT** (no location stack, no location permissions in manifest) |
| `arcore\|camera\|filament\|3d\|opengl` | 6 lines / 5 files | all false positives: `3d` inside launcher hex colors (`#3DDC84`, `#FF03DAC5`) and binary icon/webp name matches; arcore / camera / filament / opengl / `\b3d\b` = 0 each | **ABSENT** (no 3D/AR/camera rendering) |
| `farm\|digifarm` | 0 lines / 0 files | — | **ABSENT** |
| `quest` | 76 lines / 25 files | entirely `request`/`requested`/`Requires` substrings; `\bquest` = 0 | **ABSENT** |
| `tamer arena\|tournament` | 0 lines / 0 files | `tamer` = 1 (`UserHealthData.tamerRank`, "Old VB thingy" comment); `arena` = 9 lines but only `ArenaBattleSystem` (old online PVP client) | **ABSENT as a feature** |
| `jogress\|blast\|personality\|taming` | 0 lines / 0 files each | — | **ABSENT** |
| `lorebook` | 0 lines / 0 files | — | **ABSENT** |
| `companion-protocol` (`companion[-_ ]protocol`) | 0 lines / 0 files | — | **ABSENT** (Tamers Lab module) |
| `\bsave` (game saves) | 16 lines / 9 files | every hit is Android `savedInstanceState` / `saveState` / `saveable`, or a "Save updated character to database" comment. `save file`, `savegame`, `save game`, `game save`, `pc-save`, `\bsav` = 0; `savestate` = 1 (Compose nav `saveState = true`) | **ABSENT** (no game-save reader/writer) |
| `dawn\|dusk\|time stranger\|world ds\|pc-save` | 0 lines / 0 files | — | **ABSENT** (no Digimon game save support) |
| `species editor\|custom sprite\|dot sprite` | 0 lines / 0 files | `species` = 0 anywhere upstream (upstream says "character"); `sprite editor`, `custom sprite`, `dot[- ]sprite`, `pixel art` = 0 | **ABSENT** |
| `brave` / local server | 0 lines / 0 files for `brave`; `server` = 13 lines / 3 files (PVP `PVPDataModel`, battle logging, README-credits text) | no bundled/server code; only the external online-PVP client | **ABSENT** (no local server, no Brave) |
| Extra headline concepts: `chat`, `dialogue`, `npc`, `story`, `raid`, `egg`, `habit`, `encounter`, `catalog`, `recruit`, `vault`, `living`, `party`, `checksum` | 0 lines / 0 files each | — | **ABSENT** |
| `koin` / `hilt\|dagger` (fork stack) | 0 / 0 | manual DI only | **ABSENT** (upstream stack differs) |

**Patterns that surprisingly DO exist upstream** (must not be claimed as new):
`mood` (22 lines / 13 files — a real VB character stat: `UserCharacter.mood`,
`UnifiedCharacter.mood`, home screen "Mood" label; not an emotion/taming sim);
`arena` (only `ArenaBattleSystem`, part of the old online PVP client);
`boss` (30 lines / 4 files — hidden-boss HP/AP/DP/BP stats for card adventure missions,
`CardAdventureDao`); `sleep` (10 lines — PVP battle status effect in `ArenaBattleSystem`);
`server` (external PVP endpoint strings only); `online battles` (wired UI, external server);
DB **export/import** (backup/restore exists in Settings); `transfer` (watch character
transport, HCE/ISO-DEP); `vibration`, `Play Services Wearable`.
Also note binary/string false positives worth avoiding in future greps: `llm` ⊂
`fillMaxSize`, `quest` ⊂ `request`, `3d` ⊂ hex colors/icon names, `osm` ⊂ nothing (0), and
`git grep` reports binary `assets/items.db` / webp icons when the pattern hits their bytes.

## 4. What upstream is (context)

Upstream `nacabaro/vbhelper` is the classic community "Vital Bracelet helper": an Android app
that reads characters off a physical VB/VH/VBC/VBBE watch over NFC, imports DiM/BEM card dumps
to unlock the dex (characters, evolutions, fusions, adventure missions), lets you view stats,
apply items and special missions, change adventure timers, store characters, and write them back
to the watch. It also has a partial VitalWear watch-companion story (HCE character transfer,
character share intents, staged companion screens for card validation/firmware/logs) and the
unfinished lightheel online-PVP client (battle/ package, external `battle.io-void.com` server,
browser SSO redirect). It is a single-module Compose + Room app with manual DI, no game-save
support, no 3D/AR/camera/location, no LLM, no farm/quests/tournaments/lorebook/personality
systems, and no local battle engine — everything else in the Tamers Lab summary is new work.

---
Source of release/activity context (fetched 2026-11-07, treated as data):
https://api.github.com/repos/nacabaro/vbhelper/releases (latest `v0.6.5.1-pre`, 2026-07-01).
