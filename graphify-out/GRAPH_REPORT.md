# Graph Report - vbhelper  (2026-09-21)

## Corpus Check
- 316 files · ~131,425 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2716 nodes · 7671 edges · 146 communities (121 shown, 25 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 127 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Battle Adventure Cards
- Farm Storage Screens
- Shared UI Components
- Digifarm Persistence
- App Shell Screens
- Transfer Secrets Storage
- Cyber UI Library
- Battle Arena Core
- Battle Engine Methods
- Lorebook System
- Digifarm 3D Viewport
- World Compass Radar
- Card Character Domain
- Card DTOs
- Character DTOs
- Secrets Import
- Watch HCE Transfer
- Shared Transfer Database
- Item Store
- Character Persistence
- App DAOs
- Screen Controllers
- Navigation Graph
- Design System
- Validation Snapshots
- Route Navigation
- Companion Logs Service
- Watch Transfers
- Diary Reactions
- NFC Scan Import
- Settings Controls
- Card Import Flow
- Chat Providers
- Background Music
- Digifarm Simulation
- Auth Settings
- Character Cards UI
- Card Persistence
- Chat History
- Chat Prompt Engine
- Card Validation
- Personality System
- Battle Screens
- World Spawns
- Battle API Auth
- Wild Recruitment Chat
- Farm Pathfinding
- Personality Storage
- Species Database
- Battle Assets
- Prompt Localization
- NFC Conversion
- Sprite Storage
- Profile Converters
- World Spawns Logic
- App Entry
- Battle Sprites
- Reaction Species
- Digifarm Map Data
- Item Types
- Scan UI
- World Hub Radar
- Firmware Import
- API Callbacks
- Farm Sessions
- Evolution History
- Character Import
- Battle Auth Models
- Farm Conversations
- Character Queries
- Scan Controller
- Active Digimon UI
- Attack Sprites
- App Logging
- Card Importer
- App Fonts
- NFC Export
- Card Provider
- Home Widget
- Tech Background
- Battle Sprite Cache
- Persona Prompts
- Individual Records
- Chat Controller
- Home Controllers
- Biome Detection
- Sprite Files
- Vitals UI
- Species Profiles
- Wild Relations
- Evolution Repair
- Transfer Haptics
- Battle Animation
- API Services
- Digiline Threads
- LLM API Models
- Byte Utilities
- Special Missions
- AFK Scheduler
- Reaction Types
- Digifarm UI
- Theme Widget
- Bitmap Utilities
- World Biomes
- Sprite Managers
- Item Controllers
- NFC History
- Battle Match UI
- Viewport Lifecycle
- Storage Controllers
- NFC Data
- Lifecycle Listener
- Adventure Controller
- Card Progress
- Dex Records
- Identity Codec
- App Navigation
- Farm Build Plans
- PVP Models
- Species Dialogues
- Import States
- Wear Channel
- Adventure Records
- Chat Sending
- Card Adventures
- Item Spawns
- Database Tools
- Storage Filters
- Secrets Store
- Auth Interceptor
- Watch Logs UI
- Transfer Import
- Database Integrity
- NFC Transports
- Storage Sorting
- App Typography
- Unified Character
- NFC Reading UI
- NFC Writing UI
- Spawn DTOs
- Farm Navmesh
- Wild Trust Rules
- Test Strategy
- Filament R String
- DIM License

## God Nodes (most connected - your core abstractions)
1. `CharacterDtos` - 102 edges
2. `Card` - 79 edges
3. `VitalButton()` - 76 edges
4. `BitmapData` - 64 edges
5. `TopBanner()` - 59 edges
6. `AppDatabase` - 57 edges
7. `ArenaBattleSystem` - 51 edges
8. `VBHelper` - 46 edges
9. `UserCharacterDao` - 41 edges
10. `SettingsScreenControllerImpl` - 40 edges

## Surprising Connections (you probably didn't know these)
- `Guided NFC transfer state machine` --semantically_similar_to--> `NFC character handling`  [INFERRED] [semantically similar]
  .impeccable/critique/2026-09-13T17-28-13Z__app-src-main-java-com-github-nacabaro-vbhelper.md → README.md
- `Bird Digi-Farm map` --semantically_similar_to--> `digi_farm_3d manifest`  [INFERRED] [semantically similar]
  DIGIFARM_DIGILINE_PLAN.md → DIGIFARM_25D_PLAN.md
- `Compact navigation rebuild` --semantically_similar_to--> `Radar encounters tab`  [INFERRED] [semantically similar]
  .impeccable/critique/2026-09-13T17-28-13Z__app-src-main-java-com-github-nacabaro-vbhelper.md → DIGIFARM_DIGILINE_PLAN.md
- `Compact navigation rebuild` --conceptually_related_to--> `VBHelper Design System`  [INFERRED]
  .impeccable/critique/2026-09-13T17-28-13Z__app-src-main-java-com-github-nacabaro-vbhelper.md → DESIGN.md
- `Vital Bracelet devices` --conceptually_related_to--> `NFC character handling`  [INFERRED]
  PRODUCT.md → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Digifarm social simulation loop** — digifarm_25d_plan_farm, digifarm_25d_plan_farmresident, digifarm_digiline_plan_farmmessage, digifarm_digiline_plan_orchestrator, digifarm_digiline_plan_session_coordinator [EXTRACTED 0.95]
- **Vital signal design system** — design_vital_signal_rule, design_dark_habitat_rule, design_violet_cyan_palette, design_cyber_frame [EXTRACTED 0.90]
- **World Radar Digifarm Digiline navigation** — digifarm_digiline_plan_radar, digifarm_25d_plan_digifarm_25d, digifarm_digiline_plan_digiline [INFERRED 0.75]

## Communities (146 total, 25 thin omitted)

### Community 0 - "Battle Adventure Cards"
Cohesion: 0.16
Nodes (39): alpha, BackgroundSet, getBitmap(), getImageBitmap(), asimagebitmap, backhandler, box, button (+31 more)

### Community 1 - "Farm Storage Screens"
Cohesion: 0.07
Nodes (53): abs, animatedvisibility, FarmGroupScreen(), NavController, StorageCharacterPickerDialog(), badge, border, carddefaults (+45 more)

### Community 2 - "Shared UI Components"
Cohesion: 0.05
Nodes (51): animatecolorasstate, animatefloatasstate, animateintasstate, Shape, Modifier, TopBannerIconChip(), ChooseConnectOption(), Modifier (+43 more)

### Community 3 - "Digifarm Persistence"
Cohesion: 0.06
Nodes (16): DigifarmDao, Flow, Farm, FarmMemory, FarmMessage, FarmMessageRecipient, FarmReadState, FarmRelationship (+8 more)

### Community 4 - "App Shell Screens"
Cohesion: 0.10
Nodes (41): add, Alignment, TopBanner(), VBHelper, ChatScreen(), NavController, SpeciesManualEditDialog(), SpeciesManualEditResult (+33 more)

### Community 5 - "Transfer Secrets Storage"
Cohesion: 0.07
Nodes (30): CharacterTransferPolicyDao, CharacterTransferPolicy, getCryptographicTransformerMap(), getHmacKeys(), isMissingKey(), isMissingSecrets(), CryptographicTransformer, HmacKeys (+22 more)

### Community 6 - "Cyber UI Library"
Cohesion: 0.07
Nodes (48): CompanionConfirmation(), CharacterEntry(), CyberEmptyState(), cyberFrame(), Color, Modifier, PaddingValues, Shape (+40 more)

### Community 7 - "Battle Arena Core"
Cohesion: 0.15
Nodes (30): alertdialog, AnimationState, DeleteSpecialMissionDialog(), NfcCharacter, ScanScreenController, WritingScreen(), PromptTemplateDialog(), composable (+22 more)

### Community 9 - "Lorebook System"
Cohesion: 0.07
Nodes (17): DigimonWorldLore, WorldLoreEntry, Flow, LorebookMatch, LorebookRepository, Flow, LorebookEntryDao, LorebookEntry (+9 more)

### Community 10 - "Digifarm 3D Viewport"
Cohesion: 0.07
Nodes (24): androidview, Digifarm3dSceneView, ByteArray, ResidentEntry, ResidentFrameImage, ResidentFrames, ResidentPose, AssetLoader (+16 more)

### Community 11 - "World Compass Radar"
Cohesion: 0.06
Nodes (35): compassBearing(), compassDegrees(), compassDelta(), CompassHeading, CompassReading, CompassStatus, APPROXIMATE, CALIBRATE (+27 more)

### Community 12 - "Card Character Domain"
Cohesion: 0.13
Nodes (12): DigimonIndividual, Evolutions, User, UserHealthData, UserMonsters, UserMonstersSpecialMissions, UserStepsData, entity (+4 more)

### Community 13 - "Card DTOs"
Cohesion: 0.07
Nodes (14): OfficialStatus, CUSTOM, OFFICIAL, UNKNOWN, CardAdventureWithSprites, CardDtos, CardIcon, CardProgress (+6 more)

### Community 14 - "Character DTOs"
Cohesion: 0.07
Nodes (15): AdventureCharacterWithSprites, CardCharacterInfo, CardCharaProgress, CardProgress, CharacterDtos, CharacterWithSprites, EvolutionHistoryPromptEntry, EvolutionRequirementsWithSpritesAndObtained (+7 more)

### Community 15 - "Secrets Import"
Cohesion: 0.09
Nodes (23): ApkSecretsImporter, Secrets, DexFileSecretsImporter, ByteArray, CryptographicTransformer, HmacKeys, IntArray, Secrets (+15 more)

### Community 16 - "Watch HCE Transfer"
Cohesion: 0.15
Nodes (9): HceTransferMetrics, ByteArray, ReadPayloadResult, VitalWearHceReaderClient, VitalWearHceSession, VitalWearHceSessionInfo, VitalWearHceTransferDirection, PHONE_TO_WATCH (+1 more)

### Community 17 - "Shared Transfer Database"
Cohesion: 0.09
Nodes (16): Context, SharedDatabaseFactory, RoomDatabase, SharedTransferDatabase, SharedTransferSeenDao, SharedTransferSeenEntity, CompanionValidatedDatabase, RoomDatabase (+8 more)

### Community 18 - "Item Store"
Cohesion: 0.09
Nodes (20): ItemDao, Flow, ItemDtos, ItemsWithQuantities, PurchasedItem, AdventureScreenController, ItemDialog(), ItemElement() (+12 more)

### Community 19 - "Character Persistence"
Cohesion: 0.08
Nodes (5): Flow, UserCharacterDao, UserCharacter, VBCharacterData, TransformationHistory

### Community 20 - "App DAOs"
Cohesion: 0.14
Nodes (11): CardFusionsDao, Flow, NfcCharacter, Flow, dao, insert, onconflictstrategy, query (+3 more)

### Community 21 - "Screen Controllers"
Cohesion: 0.12
Nodes (23): MediaPlayer, Flow, ActivityResultLauncher, Flow, StateFlow, asstateflow, cancel, ceil (+15 more)

### Community 22 - "Navigation Graph"
Cohesion: 0.06
Nodes (33): BottomNavigationBar(), Modifier, NavController, navigatePrimary(), OverflowDestination(), VitalNavigationRail(), VitalNavItem(), Adventure (+25 more)

### Community 23 - "Design System"
Cohesion: 0.06
Nodes (34): Companheiro Digital North Star, Cyber Frame component, Dark Habitat Rule, Oxanium typography, Quiet Elevation Rule, Signal-First Type Rule, VBHelper Design System, Violet cyan palette (+26 more)

### Community 24 - "Validation Snapshots"
Cohesion: 0.07
Nodes (17): ValidatedCardDao, ValidatedCardEntity, DigimonStateSnapshotDao, VitalWearSettingsDao, RoomDatabase, SupportSQLiteDatabase, Background, CardAdventure (+9 more)

### Community 25 - "Route Navigation"
Cohesion: 0.07
Nodes (28): animatable, CardAdventureEntry(), CardAdventureScreen(), NavController, boxwithconstraints, contextcompat, currentbackstackentryasstate, detecthorizontaldraggestures (+20 more)

### Community 26 - "Companion Logs Service"
Cohesion: 0.11
Nodes (19): Activity, CompanionLogService, ChannelCallback, ChannelClient, Context, WorldAfkWorker, await, capabilityclient (+11 more)

### Community 27 - "Watch Transfers"
Cohesion: 0.12
Nodes (9): WatchTransferDao, WatchImportReceipt, NfcCharacter, resolveReturningIndividual(), WatchTransfer, NfcCharacter, WatchTransferSafety, WatchTransferRepository (+1 more)

### Community 28 - "Diary Reactions"
Cohesion: 0.15
Nodes (14): DigimonDiaryService, ReactionRepository, ValidatedCardManager, AppDatabase, AppContainer, com, DefaultAppContainer, com (+6 more)

### Community 29 - "NFC Scan Import"
Cohesion: 0.13
Nodes (15): ActivityLifecycleListener, NfcAdapter, NfcCharacter, ScanScreenController, Secrets, Tag, ScanScreenControllerImpl, atomicboolean (+7 more)

### Community 30 - "Settings Controls"
Cohesion: 0.08
Nodes (3): SettingsScreenController, Uri, SettingsScreenControllerImpl

### Community 31 - "Card Import Flow"
Cohesion: 0.14
Nodes (20): AdventureLevel, AdventureLevels, CompanionImportCardActivity, ActivityResultLauncher, Bundle, ComponentActivity, Intent, Uri (+12 more)

### Community 32 - "Chat Providers"
Cohesion: 0.14
Nodes (12): ChatApiProvider, AIRFORCE, CUSTOM, LITELLM, OPENCODE_ZEN, OPENROUTER, UNITEROUTER, UNO_ROUTER (+4 more)

### Community 33 - "Background Music"
Cohesion: 0.11
Nodes (15): AppMusicController, StateFlow, MusicSettings, AssetAudioPlayer, MediaPlayer, BackgroundMusicControlPanel(), Modifier, CreditsScreen() (+7 more)

### Community 35 - "Auth Settings"
Cohesion: 0.15
Nodes (16): AuthRepository, Flow, CardSettingsRepository, Flow, Flow, Flow, Flow, booleanpreferenceskey (+8 more)

### Community 36 - "Character Cards UI"
Cohesion: 0.14
Nodes (18): ItemDisplay(), Modifier, SpecialMissionsEntry(), NicknameDisplay(), Modifier, TransformationHistoryCard(), TransformationHistoryItem(), BECharacterData (+10 more)

### Community 37 - "Card Persistence"
Cohesion: 0.13
Nodes (6): CardDao, Flow, Card, WorldSpawnDimRow(), WorldSpawnDimSettingsDialog(), Flow

### Community 38 - "Chat History"
Cohesion: 0.13
Nodes (8): Flow, ChatDao, Flow, ChatMessageEntity, MoodDirectiveParser, experimentalcoroutinesapi, flatmaplatest, httpexception

### Community 39 - "Chat Prompt Engine"
Cohesion: 0.22
Nodes (6): ChatRepository, MissingApiKeyException, PromptContext, WildChatResult, ChatMessageDto, Exception

### Community 40 - "Card Validation"
Cohesion: 0.11
Nodes (16): CardValidationState, Success, ValidateCardOnVB, WaitingForVBConnect, CompanionValidateCardActivity, Bundle, ComponentActivity, NfcAdapter (+8 more)

### Community 41 - "Personality System"
Cohesion: 0.09
Nodes (18): PersonalityConverters, DigimonPersonalityType, ADORING, ASTUTE, BRAVE, COMPASSIONATE, DARING, DEVOTED (+10 more)

### Community 42 - "Battle Screens"
Cohesion: 0.19
Nodes (20): android, androidx, APIBattleCharacter, HitEffectOverlay(), Modifier, AnimatedBattleBackground(), AnimatedDamageNumber(), BattleScreen() (+12 more)

### Community 43 - "World Spawns"
Cohesion: 0.12
Nodes (4): Flow, WorldSpawnDao, WorldSpawn, WorldDtos

### Community 44 - "Battle API Auth"
Cohesion: 0.19
Nodes (10): BattleAuthContainer, Context, RetrofitHelper, OpenRouterClient, SpeciesDatabaseClient, gsonconverterfactory, httplogginginterceptor, OkHttpClient (+2 more)

### Community 45 - "Wild Recruitment Chat"
Cohesion: 0.14
Nodes (8): Flow, None, Pending, Recruited, Vanished, WildChatEvent, WorldChatScreenControllerImpl, WildMoodAnalyzer

### Community 46 - "Farm Pathfinding"
Cohesion: 0.24
Nodes (6): BirdFarmMap, IsoTile, MapManifest, MapPoint, Polygon, Portal

### Community 47 - "Personality Storage"
Cohesion: 0.14
Nodes (5): DigimonPersonalityGenerator, NfcCharacter, com, Flow, StorageRepository

### Community 48 - "Species Database"
Cohesion: 0.23
Nodes (3): SpeciesDatabaseDto, SpeciesEntryDto, SpeciesRepository

### Community 49 - "Battle Assets"
Cohesion: 0.14
Nodes (7): BattleAssetPaths, decodeBattleAsset(), Bitmap, HitEffectSpriteManager, Bitmap, Bitmap, bitmapfactory

### Community 50 - "Prompt Localization"
Cohesion: 0.15
Nodes (3): PromptLocalization, ReactionEvent, ReactionResult

### Community 51 - "NFC Conversion"
Cohesion: 0.26
Nodes (5): FromNfcConverter, BENfcCharacter, com, NfcCharacter, VBNfcCharacter

### Community 52 - "Sprite Storage"
Cohesion: 0.16
Nodes (6): SpriteDao, Sprite, Bitmap, SpriteViewerController, Bitmap, SpriteViewerControllerImpl

### Community 53 - "Profile Converters"
Cohesion: 0.14
Nodes (9): SpeciesProfileConverters, TypeToken, LorebookEntrySource, CUSTOM, RecruitmentState, PENDING_RECRUITMENT, RECRUITED, WILD (+1 more)

### Community 54 - "World Spawns Logic"
Cohesion: 0.18
Nodes (3): Result, Flow, WorldRepository

### Community 55 - "App Entry"
Cohesion: 0.20
Nodes (7): ActivityLifecycleListener, Bundle, Intent, Uri, MainActivity, AppCompatActivity, enableedgetoedge

### Community 56 - "Battle Sprites"
Cohesion: 0.14
Nodes (17): BattleCharacterImage(), DatabaseAnimatedSpriteImage(), ContentScale, Modifier, DigimonAnimationType, ATTACK, FLEE, HAPPY (+9 more)

### Community 57 - "Reaction Species"
Cohesion: 0.14
Nodes (9): DigimonReactionEngine, SnapshotMission, SpeciesSource, MANUAL, OFFICIAL_MATCHED, DeviceTypeConverter, gson, messagedigest (+1 more)

### Community 58 - "Digifarm Map Data"
Cohesion: 0.23
Nodes (7): Digifarm3dAssetCatalog, Context, Digifarm3dManifest, Digifarm3dMap, FarmWorldBounds, FarmWorldPoint, inputstreamreader

### Community 59 - "Item Types"
Cohesion: 0.12
Nodes (17): ItemTypes, AllTraining, APTraining, Battle20, Battle5, BPTraining, EvoTimer, HPTraining (+9 more)

### Community 60 - "Scan UI"
Cohesion: 0.17
Nodes (8): ActivityLifecycleListener, NavController, NfcCharacter, ScanScreenController, Secrets, ScanScreen(), ScanScreenPreview(), ScanScreenController

### Community 61 - "World Hub Radar"
Cohesion: 0.12
Nodes (16): NavController, WorldScreen(), cardinalDirection(), displayName(), frameFor(), ByteArray, NavController, PlayerMotion (+8 more)

### Community 62 - "Firmware Import"
Cohesion: 0.19
Nodes (11): activityresultcontracts, CompanionFirmwareImportActivity, FirmwareImportState, LoadFirmware, PickFirmware, ActivityResultLauncher, Bundle, ComponentActivity (+3 more)

### Community 63 - "API Callbacks"
Cohesion: 0.21
Nodes (8): Serializable, OpponentsDataModel, Call, Response, Callback, Callback, Callback, Callback

### Community 64 - "Farm Sessions"
Cohesion: 0.16
Nodes (9): FarmSessionCoordinator, appcompatdelegate, Application, cancellationexception, coroutinescope, isactive, Job, localelistcompat (+1 more)

### Community 65 - "Evolution History"
Cohesion: 0.20
Nodes (5): EvolutionHistoryDao, Entry, EvolutionHistoryRepair, Route, Species

### Community 66 - "Character Import"
Cohesion: 0.22
Nodes (8): ByteArray, TransferFingerprint, ImportResult, com, NfcCharacter, VitalWearCharacterImporter, callable, max

### Community 67 - "Battle Auth Models"
Cohesion: 0.18
Nodes (9): AuthenticateRequest, AdditionalInfo, AuthenticateResponse, UserInfo, AuthService, Call, body, header (+1 more)

### Community 68 - "Farm Conversations"
Cohesion: 0.23
Nodes (7): ConversationSession, FarmConversationOrchestrator, FarmUtterance, FarmUtteranceValidator, mutex, uuid, withlock

### Community 70 - "Scan Controller"
Cohesion: 0.23
Nodes (5): ActivityLifecycleListener, Flow, NfcCharacter, Secrets, ScanScreenController

### Community 71 - "Active Digimon UI"
Cohesion: 0.15
Nodes (13): animatedcontent, ActiveDigimonCard(), Modifier, customaccessibilityaction, customactions, entertransition, exittransition, fastoutslowineasing (+5 more)

### Community 72 - "Attack Sprites"
Cohesion: 0.24
Nodes (9): AttackSpriteManager, CharacterData, CharacterDataAttributes, CharacterDataResponse, Bitmap, AttackSpriteImage(), ContentScale, Modifier (+1 more)

### Community 73 - "App Logging"
Cohesion: 0.20
Nodes (7): Context, TinyLogTree, configuration, DebugTree, logger, providerregistry, suppresslint

### Community 74 - "Card Importer"
Cohesion: 0.25
Nodes (6): CardProgress, CardImportController, com, bemcard, dimcard, dimreader

### Community 75 - "App Fonts"
Cohesion: 0.18
Nodes (11): PreviewItemDialog(), AppFont, DEFAULT, EUROSTILE_EXTENDED, MICHROMA, OXANIUM, SQUARE_721_EXTENDED, appFontFamily() (+3 more)

### Community 76 - "NFC Export"
Cohesion: 0.29
Nodes (6): BENfcCharacter, NfcCharacter, UShort, VBNfcCharacter, ToNfcConverter, firmwareversion

### Community 77 - "Card Provider"
Cohesion: 0.26
Nodes (6): CardImportProvider, Bundle, ContentProvider, ContentValues, Cursor, Uri

### Community 78 - "Home Widget"
Cohesion: 0.35
Nodes (4): DigimonWidgetProvider, Context, IntArray, AppWidgetManager

### Community 79 - "Tech Background"
Cohesion: 0.22
Nodes (12): animatefloat, Modifier, TechBackground(), TechRotatingRings(), TechStaticPattern(), canvas, deeppurplebg, infiniterepeatable (+4 more)

### Community 80 - "Battle Sprite Cache"
Cohesion: 0.21
Nodes (7): BattleSpriteManager, Bitmap, SpriteData, SpriteMapping, TextureInfo, TextureRect, environment

### Community 83 - "Chat Controller"
Cohesion: 0.18
Nodes (3): ChatScreenController, Flow, SpeciesContext

### Community 84 - "Home Controllers"
Cohesion: 0.15
Nodes (4): HomeScreen(), NavController, HomeScreenController, HomeScreenControllerImpl

### Community 85 - "Biome Detection"
Cohesion: 0.24
Nodes (8): CachedBiome, Cell, OpenStreetMapBiomeDetector, concurrenthashmap, floor, httpurlconnection, JSONObject, urlencoder

### Community 87 - "Vitals UI"
Cohesion: 0.21
Nodes (10): cyberPulseAlpha(), InfoStatRow(), Color, Modifier, VitalsHeaderStat(), WeeklyVitalsChart(), VitalsHistory, PaddingValues (+2 more)

### Community 88 - "Species Profiles"
Cohesion: 0.26
Nodes (5): Flow, SpeciesProfileDao, SpeciesProfile, DigimonInfoEditDialog(), DigimonInfoEditResult

### Community 89 - "Wild Relations"
Cohesion: 0.20
Nodes (4): Flow, WildRelationshipDao, WildRelationship, DigilineWildThread

### Community 90 - "Evolution Repair"
Cohesion: 0.24
Nodes (7): TransformationHistory, EvolutionHistoryRepository, Graph, Report, log, runblocking, systemclock

### Community 91 - "Transfer Haptics"
Cohesion: 0.27
Nodes (5): TransferHaptics, build, VibrationEffect, Vibrator, vibratormanager

### Community 92 - "Battle Animation"
Cohesion: 0.22
Nodes (4): AnimatedSpriteImage(), ContentScale, Modifier, DigimonAnimationStateMachine

### Community 93 - "API Services"
Cohesion: 0.22
Nodes (6): Call, OpponentService, SpeciesDatabaseService, get, ResponseBody, url

### Community 94 - "Digiline Threads"
Cohesion: 0.25
Nodes (10): DigilineFarmThread, DigilineScreen(), DigilineTab(), EmptyList(), FarmThreadList(), ByteArray, NavController, ThreadList() (+2 more)

### Community 95 - "LLM API Models"
Cohesion: 0.22
Nodes (5): ChatCompletionChoice, ChatCompletionRequest, ChatCompletionResponse, ChatCompletionResponseMessage, OpenRouterService

### Community 96 - "Byte Utilities"
Cohesion: 0.27
Nodes (9): Endian, Big, Little, getUInt16(), getUInt32(), ByteArray, UShort, toByteArray() (+1 more)

### Community 97 - "Special Missions"
Cohesion: 0.24
Nodes (3): Flow, SpecialMissionDao, SpecialMissions

### Community 98 - "AFK Scheduler"
Cohesion: 0.22
Nodes (7): Context, WorldAfkScheduler, constraints, existingperiodicworkpolicy, networktype, periodicworkrequestbuilder, workmanager

### Community 99 - "Reaction Types"
Cohesion: 0.20
Nodes (10): ReactionType, BATTLE_LOSS, BATTLE_WIN, DEGENERATION, EVOLUTION, INJURY_GAINED, INJURY_HEALED, MILESTONE_TROPHIES (+2 more)

### Community 100 - "Digifarm UI"
Cohesion: 0.29
Nodes (10): DigifarmScreen(), EmptyFarmState(), farmActivityLabel(), FarmNameDialog(), FarmWorld(), ByteArray, NavController, ResidentDialog() (+2 more)

### Community 101 - "Theme Widget"
Cohesion: 0.20
Nodes (8): Bitmap, AppWidgetProvider, componentname, matrix, paint, pendingintent, remoteviews, typedvalue

### Community 102 - "Bitmap Utilities"
Cohesion: 0.27
Nodes (9): ARGBMasks, createARGBIntArray(), getBitmaps(), getObscuredBitmap(), Bitmap, Context, IntArray, imagebitmap (+1 more)

### Community 103 - "World Biomes"
Cohesion: 0.20
Nodes (9): WorldBiome, ENTERTAINMENT, GRASSLAND, INDUSTRIAL, NULL, PARK, RURAL, URBAN (+1 more)

### Community 104 - "Sprite Managers"
Cohesion: 0.31
Nodes (4): IndividualSpriteManager, ContentScale, Modifier, SpriteImage()

### Community 106 - "NFC History"
Cohesion: 0.31
Nodes (5): NfcCharacter, NfcEvolutionHistory, instant, localdate, zoneoffset

### Community 107 - "Battle Match UI"
Cohesion: 0.25
Nodes (9): BattlesScreen(), com, ResumeMatchDialog(), assetOfflineBattleParticipant(), BattleHealthBar(), Color, Modifier, OfflineBattleEntryPanel() (+1 more)

### Community 108 - "Viewport Lifecycle"
Cohesion: 0.28
Nodes (3): describeDigifarmFailure(), Digifarm3dViewport(), Modifier

### Community 110 - "NFC Data"
Cohesion: 0.36
Nodes (5): and, UShort, VBNfcData, MifareUltralight, or

### Community 113 - "Card Progress"
Cohesion: 0.25
Nodes (3): CardProgressDao, Flow, CardProgress

### Community 115 - "Identity Codec"
Cohesion: 0.32
Nodes (3): IndividualIdentity, ByteArray, securerandom

### Community 116 - "App Navigation"
Cohesion: 0.29
Nodes (7): AppNavigation(), AppNavigationHandlers, Modifier, primaryTabTransitionDirection(), tabSwipeNavigation(), ChooseCharacterScreen(), NavController

### Community 117 - "Farm Build Plans"
Cohesion: 0.25
Nodes (8): Deterministic GLB asset pipeline, digi_farm_3d manifest, Tron wireframe mode, Android WorkManager constraint, Bird Digi-Farm map, FarmSessionCoordinator, Digifarm ZIP asset source, build_assets.py conversion

### Community 118 - "PVP Models"
Cohesion: 0.33
Nodes (4): Serializable, PVPDataModel, Call, PVPService

### Community 119 - "Species Dialogues"
Cohesion: 0.33
Nodes (3): SpeciesConversationDatabase, SpeciesConversationEntry, SpeciesConversationExchange

### Community 120 - "Import States"
Cohesion: 0.29
Nodes (7): ImportState, ImportCard, LoadFile, NameOrUnique, PickFile, Success, UnlockCard

### Community 121 - "Wear Channel"
Cohesion: 0.38
Nodes (4): ChannelTypes, ChannelClient, WatchCommunicationService, WearableListenerService

### Community 125 - "Item Spawns"
Cohesion: 0.33
Nodes (5): ItemType, BEITEM, SPECIALMISSION, UNIVERSAL, VBITEM

### Community 127 - "Storage Filters"
Cohesion: 0.33
Nodes (6): StorageFilter, ACTIVE, ALL, BE, FAVORITES, VB

### Community 128 - "Secrets Store"
Cohesion: 0.60
Nodes (3): Flow, Secrets, SecretsRepository

### Community 129 - "Auth Interceptor"
Cohesion: 0.70
Nodes (3): AuthInterceptor, Response, Interceptor

### Community 130 - "Watch Logs UI"
Cohesion: 0.40
Nodes (4): CompanionWatchLogsActivity, Bundle, ComponentActivity, CompanionLoading()

### Community 131 - "Transfer Import"
Cohesion: 0.60
Nodes (3): CompanionCharacterImportActivity, Bundle, ComponentActivity

### Community 132 - "Database Integrity"
Cohesion: 0.50
Nodes (3): IndividualIntegrity, SupportSQLiteDatabase, roomdatabase

### Community 133 - "NFC Transports"
Cohesion: 0.40
Nodes (4): DetectedTransport, ISO_DEP, NFC_A, UNKNOWN

### Community 134 - "Storage Sorting"
Cohesion: 0.40
Nodes (5): StorageSort, NAME, RECENT, STAGE, VITALS

### Community 135 - "App Typography"
Cohesion: 0.50
Nodes (4): appTypography(), FontFamily, textstyle, typography

## Knowledge Gaps
- **201 isolated node(s):** `CharacterDataResponse`, `CharacterDataAttributes`, `AdditionalInfo`, `UserInfo`, `SpriteMapping` (+196 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 653 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CharacterDtos` connect `Character DTOs` to `Battle Adventure Cards`, `Farm Storage Screens`, `App Shell Screens`, `Transfer Secrets Storage`, `Cyber UI Library`, `Battle Arena Core`, `Card DTOs`, `Character Persistence`, `App DAOs`, `Screen Controllers`, `Route Navigation`, `Character Cards UI`, `Chat History`, `Battle Screens`, `Personality Storage`, `Battle Sprites`, `Evolution History`, `Character Queries`, `Active Digimon UI`, `NFC Export`, `Home Widget`, `Persona Prompts`, `Home Controllers`, `Vitals UI`, `Theme Widget`, `Battle Match UI`, `Dex Records`, `Species Dialogues`, `Adventure Records`?**
  _High betweenness centrality (0.093) - this node is a cross-community bridge._
- **Why does `VBHelper` connect `App Shell Screens` to `Battle Adventure Cards`, `Farm Storage Screens`, `Cyber UI Library`, `Battle Arena Core`, `Lorebook System`, `Card DTOs`, `Screen Controllers`, `Route Navigation`, `Companion Logs Service`, `Diary Reactions`, `NFC Scan Import`, `Card Import Flow`, `Wild Recruitment Chat`, `NFC Conversion`, `Sprite Storage`, `App Entry`, `Farm Sessions`, `NFC Export`, `Card Provider`, `AFK Scheduler`, `Theme Widget`, `Wear Channel`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Why does `Card` connect `Card Persistence` to `Battle Adventure Cards`, `App Shell Screens`, `Cyber UI Library`, `Battle Arena Core`, `Lorebook System`, `Item Store`, `App DAOs`, `Validation Snapshots`, `Route Navigation`, `NFC Scan Import`, `Card Import Flow`, `Chat Providers`, `Background Music`, `Character Cards UI`, `Battle Screens`, `NFC Conversion`, `Reaction Species`, `World Hub Radar`, `Scan Controller`, `Card Importer`, `Home Controllers`, `Vitals UI`, `Species Profiles`, `Battle Match UI`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Are the 39 inferred relationships involving `Card` (e.g. with `BackgroundMusicControlPanel()` and `CharacterEntry()`) actually correct?**
  _`Card` has 39 INFERRED edges - model-reasoned connections that need verification._
- **What connects `CharacterDataResponse`, `CharacterDataAttributes`, `AdditionalInfo` to the rest of the system?**
  _201 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Farm Storage Screens` be split into smaller, more focused modules?**
  _Cohesion score 0.073224043715847 - nodes in this community are weakly interconnected._
- **Should `Shared UI Components` be split into smaller, more focused modules?**
  _Cohesion score 0.05493863237872589 - nodes in this community are weakly interconnected._