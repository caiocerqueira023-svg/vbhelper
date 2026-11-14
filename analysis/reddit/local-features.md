# Tamers Lab — features that exist locally and not in upstream vbhelper

Code-side evidence inventory for the Reddit W.I.P post.

- **Working tree:** HEAD `397ef846` ("VBHLLMVer.53 ui redesign 2").
- **Upstream baseline:** `99d309adc1b62af1f2665597e854cc53ea7e864ba` — `git merge-base --is-ancestor` confirms it is an ancestor of HEAD.
- **Method:** source reads/greps only (no Gradle run). Plans/docs were not accepted as evidence; code or assets are cited.
- **Scale:** upstream has **192** Kotlin files in one `:app` module and no Filament/ARCore/Koin/location dependency. This tree has 702 (`app`) + 27 (`game-save-core`) + 35 (`hardware`) + 12 (`hardware-dim`) + 4 (`pc-save-companion`) + 1 (`companion-protocol`) Kotlin files.
- **Status vocabulary:** **LIVE** = reachable in the shipping UI; **GATED** = behind a setting/debug flag (or an optional device capability); **PAUSED** = code exists, switched off on purpose; **PLANNED/WIP** = incomplete or not wired.
- Upstream features that were only refactored (NFC bracelet read/write, storage, dex/cards, items/shop, adventure missions, online PVP, companion, VitalWear) are listed in the "Inherited" section instead of being claimed as local.

## Summary table

| Feature | What it is | Status | Key evidence |
|---|---|---|---|
| Chat with a partner Digimon | One-to-one roleplay chat with the active/stored partner | LIVE | `chat/Ui/ChatScreen.kt`, route `AppRoute.Chat` in `navigation/AppRoute.kt` |
| Multi-provider LLM setup | 7 OpenAI-compatible gateways, per-provider key/model/URL/temperature | LIVE | `chat/ChatApiProvider.kt`, `settings/data/LlmSettingsRepository.kt`, `settings/ui/dialogs/LlmSettingsDialog.kt` |
| Personality, mood and trust | Generated per-individual personality; 0–100 mood panel; trust gates | LIVE | `domain/personality/*`, `chat/DigimonPersonaBuilder.kt`, `world/WildMoodAnalyzer.kt` |
| Lorebook grounding | Bundled Digimon-world lore + custom trigger entries injected into prompts | LIVE | `chat/lorebook/LorebookRepository.kt`, `chat/ui/lorebook/LorebookScreen.kt` |
| Wild Digimon chat | Proximity chat with wild Digimon; structured JSON battle/quest intents | LIVE | `chat/ChatRepository.kt` (`sendMessageForWildEncounter`), `world/ui/WorldChatScreen.kt` |
| Autonomous wild↔wild chat/battles | Social planner + NPC battle path for two wild Digimon | PAUSED | `world/ecosystem/WildSocialInteractionGate.kt` (`ENABLED = false`) |
| Isometric GPS radar | Filament isometric radar place, compass, OSM surroundings line | LIVE | `world/ui/RadarPlaceScreen.kt`, `world/ui/RadarIsometricViewport.kt`, `world/ui/WorldScreen.kt` |
| Legacy radar renderers | Classic 2D and first-person radar | GATED | `world/ui/WorldScreen.kt` (`effectiveLegacy` forced to `NONE` unless Debug) |
| OpenStreetMap biomes | Overpass lookup → biome that biases spawn attributes | LIVE | `world/OpenStreetMapBiomeDetector.kt`, `world/WorldBiome.kt` |
| Wild spawn ecosystem | Simulated wild population, 30-min spawns, stage-weighted species | LIVE | `world/WorldRepository.kt`, `world/WorldSpawnSelector.kt`, `world/ecosystem/*` |
| Proximity encounters | Interaction only within 40 m of a fresh GPS fix | LIVE | `world/RadarWorldGeometry.kt` (`INTERACTION_RANGE_METERS = 40.0`), `world/ecosystem/WorldInteractionRepository.kt` |
| Recruitment / taming | Raise trust, meet quest requirements, move wild Digimon into Storage | LIVE | `world/WorldRepository.kt` (`recruitSpawn`/`recruitIndividual`), `world/ui/WorldRecruitsScreen.kt` |
| Quest targets on the radar | Quest target individuals materialized near the player and tracked | LIVE | `world/WorldRepository.kt` (`materializeQuestTargetsLocked`), `farm/ui/digiline/DigilineScreen.kt` (`trackQuestTarget`) |
| ARCore AR battles | Camera passthrough + surface placement of the live battle | GATED | `ar/*`, `world/ui/WorldScreen.kt` (`beginRadarBattle`, "AR battles" radar toggle) |
| Offline 3D real-time battle | Filament arena, 60 Hz simulator, DW-style command band | LIVE | `battle/ui/offline/OfflineBattleScene.kt`, `battle/offline/core/BattleSimulator.kt` |
| DW1/Re:Digitize combat model | 3 techniques + items/special/strategy/focus/defend/move, orders, AI profiles | LIVE | `battle/ui/offline/BattleCommandDeckModel.kt`, `battle/offline/core/BattleModel.kt`, `battle/domain/BattleDefinitions.kt` |
| Blast Evolution / Jogress | Solo FORM/POWER transforms and duo Jogress with timing minigame | LIVE | `battle/offline/data/BlastEvolutionRepository.kt`, assets `blast_evolution.json`, `battle/ui/offline/BattleFinisherOverlay.kt` |
| Tamer Arena | Tamers, exhibition/tournament brackets, records, bits rewards | LIVE | `battle/ui/arena/*`, `battle/offline/tamers/*`, assets `tamers.tsv` |
| Training battles | Practice fight against generated opponents | LIVE | `battle/ui/offline/TrainingBattlePreparation.kt`, `battle/offline/data/TrainingBattleFactory.kt` |
| 3D Digifarm home | Persistent Filament island with extruded residents | LIVE | `farm/ui/digifarm/FarmHomeWorld.kt`, `farm/ui/digifarm/Digifarm3dViewport.kt`, `AppRoute.Home` → `FarmHomeScreen` |
| Resident behaviour AI | Needs/activity rolls, walking, greetings, collision, cinematic camera | LIVE | `farm/FarmBehaviorPolicy.kt`, `farm/diorama/FarmDioramaSimulation.kt`, `farm/FarmCameraState.kt` |
| DigiLine | Chat-thread list (Storage + wild contacts) with pinned partner | LIVE | `farm/ui/digiline/DigilineScreen.kt`, route `AppRoute.Digiline` |
| Farm residents talk to each other (LLM) | Bounded autonomous farm conversations | PLANNED/WIP | `farm/social/FarmConversationOrchestrator.kt` — entry points have **no caller** |
| Quest system | 20 authored quest templates, phases, tokens, battle constraints, rewards | LIVE | `quests/QuestTemplates.kt`, `quests/QuestRepository.kt`, `quests/ui/QuestScreen.kt`, route `AppRoute.Quests` |
| Game-save readers | Inspect Time Stranger and Digimon World DS saves | LIVE | `game-save-core/.../*SaveReader.kt`, `transfer/data/gameSave/*` |
| Snapshot copy into Storage | Provisional app copies of native party/bank Digimon | LIVE | `transfer/data/gameSave/GameImportAcquisitionRepository.kt` (`productionProfiles`) |
| World DS native move/send | Real writes into a linked World DS save | LIVE (experimental) | `ExperimentalWorldDsMoveRepository.kt`, `ExperimentalWorldDsSendRepository.kt`, `transfer/ui/TransferWorkspaceScreen.kt` |
| Dawn/Dusk, Lost Evolution, Xros Wars transfer | Listed in the catalog, no reader/writer profile | PLANNED/WIP | `navigation/TransferGameCatalog.kt` (`nativeGameId = null`), `game-save-core/.../NdsStoryProfile.kt` (`all = listOf(WORLD_DS)`) |
| Read-only PC save companion | Desktop LAN server exposing save files to the phone | LIVE (separate module) | `pc-save-companion/`, `companion-protocol/`, `transfer/data/gameSave/LocalCompanionClient.kt` |
| Save previews/reviews/receipts | Destination previews, native-change review, import receipts | LIVE | `transfer/data/gameSave/DestinationPreviewRepository.kt`, `NativeProgressReviewRepository.kt` |
| Bundled Dot sprites + artwork priority | 12-pose offline sprite sets, VB-first/Dot-first priority | LIVE | `species/BundledDotSpriteLibrary.kt`, assets `dot_sprites.zip`, `settings/data/SpritePreferenceRepository.kt` |
| Custom species catalog editor | Edit species name/stage/type/moves/sprite from Settings | LIVE | `components/SpeciesCatalogEditorDialog.kt`, `species/SpeciesCustomizationRepository.kt` |
| Rendering tiers + extruded sprites | Frame pacing/thermal quality tiers; sprite extrusion GLB | LIVE | `rendering/FrameBudget.kt`, `rendering/HybridSceneProfile.kt`, `rendering/sprite3d/SpriteExtrusionGlb.kt` |
| App soundtrack + battle SFX | One looping home track, battle track, hit/attack sounds | LIVE | `audio/AppMusicController.kt`, `battle/BattleAssetPaths.kt`, assets `audio/game_sounds` |
| Debug-only AR/validation hosts | AR smoke test, AR stage preview, battle balance runner, integrity checks | GATED | `app/src/debug/.../ar/ArDiagnosticsActivity.kt`, `ArStagePreviewActivity.kt`, `app/src/integrityCheck/.../OfflineBattleValidationActivity.kt` |

## Chat with Digimon via LLM

- Providers are real and configurable: `ChatApiProvider` lists OpenRouter, Api.Airforce, UnoRouter, UniteRouter, OpenCode Zen, LiteLLM and a manual OpenAI-compatible endpoint; `LlmSettingsRepository` stores per-provider key/model/base-URL/temperature in DataStore. Requests are `POST {baseUrl}chat/completions` through Retrofit/OkHttp (`chat/OpenRouterService.kt`, `chat/OpenRouterClient.kt`), HTTPS-only.
- The chat UI is `AppRoute.Chat(characterId)`, opened from the Home partner deck, Storage, DigiLine and the farm resident sheet — not from a hidden dev screen (`chat/ui/ChatScreen.kt`, `farm/ui/digifarm/FarmHomeScreen.kt` line ~230).
- Personality and mood feed the prompt: `DigimonPersonaBuilder.buildSystemPrompt` injects mood description, personality type, evolution history, species block, per-language system prompt; `character.mood` is shown as a 0–100 `ChatStatusPanel` and updated by `WildMoodAnalyzer`/`SocialEventAppraisal`.
- Wild encounters use a different path: `ChatRepository.sendMessageForWildEncounter` asks for one strict JSON object (`lines` + optional battle `intent` + optional `questAction`), validates it with `WorldDialogueCodec`, and `BattleTalkDetector` decides when a follow-up call may recover a dropped battle intent. The model can never start a fight; the intent still passes codec + policy checks.
- Extras that ship with chat: lorebook matches injected as a size-budgeted context block, a partner diary generator (`chat/DigimonDiaryService.kt`, called from `home/ui/HomeScreenControllerImpl.kt`), and reaction snapshots taken before sending a Digimon to the watch (`chat/ReactionRepository.kt`).
- **Honest limits:** completions are single-shot, **not** streaming (`ChatCompletionRequest` has no `stream`; no SSE code exists). History is the last 20 messages, `max_tokens` 400. There is no chat-wide on-device model.
- **vs upstream:** upstream has no `chat` package, no LLM settings, no prompts and no network AI dependency at all. This whole area is new.

## World — finding Digimon in the real world

- Location is real: `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` in `app/src/main/AndroidManifest.xml`; the radar requests `PRIORITY_HIGH_ACCURACY` fixes every 2 s via Fused Location in `world/ui/WorldScreen.kt` (~line 648) and the conversation screen, and the ecosystem rejects stale fixes (`WorldInteractionRepository.validateFix`).
- Map data is OpenStreetMap: `OpenStreetMapBiomeDetector` POSTs an Overpass query (250 m radius) for water/park/grassland/farm/industrial/entertainment/residential and caches per ~220 m cell (15 min success / 30 s failure). It degrades to `WorldBiome.NULL`, which deliberately has no spawn bias.
- Spawning is a persisted Room ecosystem, not a wallpaper: stage-weighted species selection (`WorldSpawnSelector`), 350 m nearby/1 km outer target counts, max 3 new spawns per call, 60 active-spawn cap, 30 min expiry, placeholder-art cards excluded (`world/WorldRepository.kt`).
- Proximity is enforced: the shared interaction radius is **40 m** (`RadarWorldGeometry.INTERACTION_RANGE_METERS`), used by radar commands, wild initiation and interaction validation; the only bypass is the Debug "allow any distance" switch (`DebugSettingsRepository`).
- Taming is a workflow, not a button: chat raises `WildRelationship.trust` (soft cap 75), recruitment requirements are checked in `QuestRepository.recruitmentReadyLocked`, then `recruitSpawn`/`recruitIndividual` convert the wild individual into a Stored partner. Quest givers, quest targets and the guaranteed first-partner encounter are also spawned around the player.
- Wild↔wild autonomous chat and battles are **paused by an explicit one-line gate** (`WildSocialInteractionGate.ENABLED = false`); player-directed wild chat, duels and radar/AR battles still work. The ecosystem clock is continuous but there is **no day/night cycle** anywhere in the code.
- **vs upstream:** upstream has no world/radar feature, no location permission, no OSM/Overpass code and no Filament radar. New isometric radar, legacy radar (Debug-only), shared `CombatantDefinition`-backed wild battles and the spawn ecosystem are local additions.

## AR battles (ARCore)

- `ArBattleStage` runs one ARCore session with a camera background layer, asks for the runtime install if missing, requests CAMERA once, and shows a fallback action on every failure.
- Placement is hands-free: `ArPlacementOverlay` hit-tests detected planes plus ARCore depth/instant hits, applies quality gates (plane can lock almost instantly, depth needs ~1.2 s of scanning, instant ~2.5 s), auto-picks a scale plan from surface size and confirms once the pose is stable.
- Scale is derived from the detected surface: `ArBattleScale` maps the 16-unit radar arena to 0.9 m (tabletop), 2.5 m (room) or 8 m (open space), AUTO = 75 % of the surface's short side, clamped 0.5–12 m; pinch can scale 60–160 %.
- The same Filament battle is reused: `OfflineBattleScene` supports an AR presentation with a transparent swap chain (camera as backdrop) and hides floor/grid/dome (`ar/ArTransparentSwapChain.kt`, `battle/ui/offline/OfflineBattleScene.kt`).
- Reachability: `beginRadarBattle` only enters AR when the "AR battles" radar preference is on and `checkArAvailability` is not `UNSUPPORTED`; otherwise (or on deny/cancel) it fights the normal Coliseum arena. Debug builds add `ArDiagnosticsActivity` and `ArStagePreviewActivity` via `app/src/debug/AndroidManifest.xml`.
- **vs upstream:** upstream has no ARCore dependency, no camera use and no AR activities. AR is new and device/preference-gated, not the default battle presentation.

## Offline 3D real-time battle (Digimon World style)

- The battle is a Filament scene (`OfflineBattleScene`, 1,800+ lines) over authored GLB arenas: `Arena/Colosseum/Colosseum.glb` and the Radar plate (`Arena/Radar/*.glb`), with manifest-driven bounds/spawns/camera (`battle/ui/offline/OfflineArenaManifest.kt`).
- Simulation is a deterministic fixed-step `BattleSimulator` owned by a session controller; the UI submits orders and consumes snapshots (`battle/offline/core/BattleSimulator.kt`, `battle/offline/session/BattleSessionController.kt`, `BattleSceneChannel.kt`). Damage uses the DW1 arithmetic (`Dw1DamageFormula` in `BattleDamageResolver.kt`).
- The command band is the Re:Digitize Decode 3×3 instrument: three equipped techniques, Items/Special/Strategy, Focus/Defend/Move with an armed one-tap move. Availability states are modelled, keys never disappear (`battle/ui/offline/BattleCommandDeckModel.kt`, CHANGELOG lines ~180–188).
- Techniques come from `GenericTechniqueCatalog` and a per-individual loadout (`AppRoute.TechniqueLoadout`, `battle/offline/data/GenericTechniqueLoadout.kt`); items are a **session-only** inventory with cooldowns (`BattleItemDefinition`, `battle/offline/data/TrainingBattleFactory.kt`); strategy is a real enum used by the AI (`BattleStrategy`, `TamerTrainerController`).
- Evolution in battle is Blast/Jogress, not a permanent evolution screen: finisher kinds POWER/FORM/JOGRESS/DUO with phased cinematic camera (`battle/offline/core/BattleFinisher.kt`, `battle/ui/offline/BattleCinematicRenderer.kt`).
- **Retreat/flee does not exist as a command.** Leaving a fight is an exit flow that ends the battle as `BattleOutcome.ABANDONED` (`BattleSimulator.abandon`, `OfflineTrainingBattleScreen` exit dialog). Claiming a "retreat" move would be wrong.
- **vs upstream:** upstream's `battle` package is the online sprite/PVP client only (no 3D scene, no simulator, no Filament). The offline 3D simulator, command grammar, techniques, items, strategy, finishers and AI are new.

## Blast Evolution and Jogress

- The shipped asset `blast_evolution.json` (~2.25 MB) is a universal solo-form + Jogress table, not restricted to the loaded DIM: `BlastEvolutionRepository.availableForms/availableJogress` only offer targets whose species (and Jogress partner) exist in the imported cards.
- Card Dex Jogress routes are resolved into species-level options (`battle/offline/data/DexJogressResolver.kt`), and the arena/AI uses canonical permitted Jogress instead of fabricating chains (`TamerCanonicalPartners.permittedJogress`).
- In battle: a Blast opens a timing minigame on the command dial; the confirmed grade decides whether the form/Jogress happens (`BattleSnapshot.pendingBlastTiming`, `BlastTimingOpened/Resolved`, `BattleFinisherOverlay`).
- **vs upstream:** upstream has no Blast, Jogress, finisher, or evolution-battle code; its battle was turn-based sprite PVP.

## Tamer Arena

- Tamer roster is authored data (`assets/tamers.tsv`, `tamer_nicknames.tsv`, `TamerCatalog`, `TamerCanonicalPartners`) with canonical partner histories and Jogress permissions.
- `TamerArenaLobby` exposes Tamers / Tournaments / Records, 1×1 or 2×2 formats, three difficulties, series filters and a partner picker; battles reuse the offline arena (`battle/ui/arena/TamerArenaLobby.kt`).
- Tournaments are persisted single-elimination brackets of 8 or 16 entrants with seeded matches, resume/withdraw and champion tracking (`ArenaTournament`, `ArenaRepository`, tables `ArenaRun`/`ArenaMatch`/`ArenaRewardReceipt`).
- Settlement writes only records/brackets/wallet bits — never owned Digimon or item stock (`battle/offline/tamers/ArenaRepository.kt` header comment).
- **vs upstream:** no arena, tamers, tournament or records exist upstream.

## Digifarm (home island) and DigiLine

- Home is a persistent 3D island: `FarmHomeWorld` is composed once at the shell level and kept alive under every place, so returning never rebuilds the Filament engine (`navigation/AppNavigation.kt` lines ~142–280). Island asset: `digifarm/3d/digi_farm_3d.glb`.
- Residents are extruded cardboard sprites with a 12-pose set (idle x2, walk x2, run x2, train x2, happy, sleep) created as per-resident GLBs, loaded one per frame through a budgeted queue (`farm/ui/digifarm/Digifarm3dViewport.kt`, `rendering/sprite3d/SpriteExtrusionGlb.kt`).
- Behaviour is probabilistic, not scripted: needs decay and activity selection come from each resident's `DigimonSocialProfile` (`FarmBehaviorPolicy`), and the diorama simulation handles walking strides, jogs, workouts, naps, greetings, and body collision (`FarmDioramaSimulation.kt`).
- The camera is cinematic: slow orbit around the followed partner, hard cuts every 14–26 s, paused on touch, disabled under reduced motion (`Digifarm3dViewport.dynamicCamera`, `farm/FarmCameraState.kt`); roster membership/limit (1–12) is stored in DataStore.
- DigiLine lists Storage and wild chat threads, supports pinning the partner you were just viewing, and routes to `AppRoute.Chat` / `AppRoute.WildContact`.
- The five "places" on the dock are typed routes: Radar, Arena, Dex, Shop, Partners (`farm/ui/home/PlaceDestination.kt`) with a shared Home↔place transition.
- **Honest limit:** the farm has an LLM conversation orchestrator (`farm/social/FarmConversationOrchestrator.kt`, 6-turn bounded, intent whitelist), but `sendTamerMessage`/`maybeGenerateAutonomous` are **never called**; the DigiLine screen reads Storage/wild threads, not farm messages. Treat resident-to-resident generated dialogue as WIP, not shipped.
- **vs upstream:** upstream's Home is a 2D card/partner screen; there is no farm, no 3D island, no resident simulation, no DigiLine and no Filament.

## Quests (and adventure missions)

- Quests are authored templates, not model output: 20 templates in `quests/QuestTemplates.kt` (supplies, rival duel, patrol, training, medicine, recruitment, missing person, lost property, courier, rescue, timed/constrained battles…), generated into instances with phases and difficulty scaling.
- Objective model is deep: multi-phase objectives, quest tokens (letter/reply/lost property), watch-evidence baselines, battle reports with constraints (time limit, item limit, min health %, min technique hits, team sizes), follow-up quests and idempotent reward receipts (`quests/QuestEntities.kt`).
- All accept/track/turn-in actions live in the Quests tab (`AppRoute.Quests` → `QuestScreen`/`QuestJournal`), reachable from the "More" sheet; chat may propose a `questAction`, but the action is executed and validated by app code, and radar tracking is a first-class action.
- Radar quest targets are materialized around the player when a quest needs a world target (`WorldRepository.materializeQuestTargetsLocked`) and released when the quest finishes.
- Adventure missions are the **inherited** upstream system (send a watch character away, claim item/bits afterwards) and are now also powered by the offline battle system through battle constraints; note that the "adventure" tab itself is not a local-only feature.
- **vs upstream:** upstream has adventure missions but no quest system, no quest journal, no quest targets, tokens, battle-constrained objectives or chat-driven quest offers.

## Transfer Digimon between games / PC companion

- **Reads** (verified): `SaveReaderRegistry` registers `TimeStrangerSaveReader` plus an `NdsStorySaveReader` for each registered profile. Only **World DS (USA)** is registered as an `NdsStoryProfile` today (`game-save-core/.../NdsStoryProfile.kt`, `all = listOf(WORLD_DS)`). Time Stranger and World DS rosters expose stored names, level, native HP/MP/combat stats and 8 separate EXP channels.
- **Catalog vs capability:** `TransferGame` lists World DS, Dawn, Dusk, Lost Evolution, Super Xros Wars Blue/Red and Time Stranger, but only WORLD_DS and TIME_STRANGER carry a `nativeGameId`; the others are presentation-only and filename hints never enable a reader (`navigation/TransferGameCatalog.kt`).
- **Copies into Tamers Lab Storage** (provisional app representation, source untouched): Time Stranger party/bank snapshots and World DS party snapshots are registered in `GameImportAcquisitionRepository.productionProfiles` with provisional identity + review; the save file keeps its records.
- **Writes** (verified, World DS only): `ExperimentalWorldDsMoveRepository` (remove from save → acquire into vault, plus storage→party admission) and `ExperimentalWorldDsSendRepository` (send a retained native record back into a save) write through SAF with pre-flight backups, a durable journal, readback and recovery, surfaced in `TransferWorkspaceScreen` / `TransferGameLibraryScreen` and a "move backups" screen.
- **Honest caveat:** the repositories are named "Experimental" and their own comments state *"retail game-load/checksum acceptance is unverified"*; README says native checksums, global identity and game writes remain unvalidated. Time Stranger game writes are not wired at all and its transfer development is described as paused. Farm/boundary/tail records are review-only. So: reads previews/copies are shipped; only World DS has a working, structurally verified writer, and no writer is proven against the retail game.
- **PC companion:** a separate desktop distribution (`pc-save-companion/`, `companion-protocol/`) that serves explicitly supplied save folders over pinned-TLS HTTPS on port 7428 with a pairing token; the phone calls `/v1/sources` and `/v1/saves/{id}` (`LocalCompanionClient`). It is read-only by design — "No game-save write endpoint exists" (`pc-save-companion/README.md`) — and also ships the World DS research inspector and Time Stranger capture/compare modes.
- **vs upstream:** upstream has the NFC bracelet flow and Storage but no game-save readers/writers, no save model, no companion-protocol, no desktop companion and no game library.

## Species, dex, artwork

- The Dex/Cards tab is the upstream card/dex surface heavily extended: card chart/list, evolution chart, Jogress/fusion dialogs, card details, card origin prompt, batch card import and Digimon scans (`species/ui/card/*`, route `AppRoute.Dex`).
- Offline art: `assets/dot_sprites.zip` + `species_catalog.json` (~13 MB) and `species.json` back a bundled Dot-sprite library; Settings has VB-first/Dot-first artwork priority and a per-species sprite picker (`species/BundledDotSpriteLibrary.kt`, `species/SpeciesArtworkPolicy.kt`, `settings/ui/SettingsScreen.kt` "Digimon graphics" section).
- A custom species catalog editor ships in Settings: `SpeciesCatalogEditorDialog` can edit species name/stage/type/moves and choose a sprite (reachable from "Species and sprites" with `editOnSelect = true`); `SpeciesCustomizationRepository` persists the overrides used by Arena and Dex.
- **vs upstream:** upstream has a card/dex viewer (card import, sprite viewer) but no custom species catalog editor, no bundled Dot sprite library/artwork priority and no scan/Jogress/card-chart surfaces in this form.

## Settings, localization and diagnostics

- Settings exposes LLM chat configuration, lorebook, species/sprites, sound, language, Tamer name, themes/fonts, data export/import and a Debug switch (`settings/ui/SettingsScreen.kt`).
- Prompt template editing is Debug-gated: `LlmSettingsRepository.setSystemPromptTemplate`/`setWildSystemPromptTemplate` no-op unless Debug is enabled, and the dialogs only render when `debugSettings.enabled` (SettingsScreen ~338–352).
- Language selection: System / English / Português (Brasil) / 日本語 (`settings/ui/dialogs/LanguageDialog.kt`); prompts and persona blocks are localized in `PromptLocalization` and `DigimonPersonaBuilder`.
- `diagnostics/StartupTrace.kt` provides always-on atrace startup slices used by the macrobenchmark module; debug/integrityCheck source sets host the AR smoke tests, AR stage preview, battle balance runner and validation activities.
- **vs upstream:** upstream had settings, language import and credits only; the LLM/prompt/lorebook/species sections, debug switch and diagnostics are new.

## Inherited from upstream (do NOT claim as local-only)

- **NFC bracelet read/write and HCE** — `transfer/ui/scan/*` (read, write card/character, transfer animation), `transfer/hce/*`; upstream has the same screens and HCE path.
- **Online PVP** — legacy upstream client (`battle/online/*`, `BattlesScreen` auth + PVP match flow). `BuildConfig.ONLINE_PVP_ENABLED` is hard-coded `true` in `defaultConfig` and `OnlineBattleFeature.ENABLED` is **never read by any UI or gate** (only imported), so the "gated" claim in CHANGELOG/docs is nominal; the screen depends on the external `auth.nacatech.es` service. Do not present online PVP as a Tamers Lab feature or as verified.
- **Storage/vault, items/shop/bits, adventure missions, cards/dex, sprite viewer, companion activities, VitalWear shared database, secrets import** — upstream features that were refactored (Koin, type-safe routes, new UI) rather than invented here.
- **Online-vs-offline battle split** — the domain models were unified locally, but the online half is upstream code, not new gameplay.

## Claims I could not verify (stated honestly)

- **Nothing was executed.** No APK was built or run; "LIVE" means reachable from `AppNavigation`/`AppRoute` and wired to UI, not device-tested. README/CHANGELOG device claims were not re-run here.
- **World DS writer against a real game** — code writes a journaled candidate, but retail load/checksum acceptance is explicitly unverified in code comments and README.
- **Dawn/Dusk/Lost Evolution/Xros Wars** — only enum/filename hints; no reader, species mapping or writer exists (`NdsStoryProfiles.all` has one entry).
- **Time Stranger transfer** — inspection/provisional snapshot copies exist; game writes do not, and the README calls its transfer development paused.
- **Day/night cycle** — no code found (`dayNight`, `isNight`, `nocturnal`, night strings all absent). The ecosystem has a logical clock only.
- **Farm residents conversing via LLM** — orchestrator exists but is unwired; present only as WIP.
- **Online PVP reachability/health** — depends on an external auth/PVP server that cannot be verified from this repo.
- **AR on hardware** — placement/scale/session code is present and unit-tested at the math level, but actual AR tracking quality on a device was not measured here.
