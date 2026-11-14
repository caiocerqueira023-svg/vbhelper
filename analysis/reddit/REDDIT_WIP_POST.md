# Tamers Lab — Reddit W.I.P. post draft

Draft for posting Tamers Lab as a work-in-progress, with the features that exist in
Tamers Lab but **not** in upstream VBHelper. Evidence: code inventory
[local-features.md](local-features.md), docs extraction [docs-status.md](docs-status.md),
upstream baseline [upstream-baseline.md](upstream-baseline.md).

If you post, adjust the voice (this draft uses "I") and replace the `[[…]]` placeholders.

---

## 1. Title options

**Recommended (covers the four headline features in the user's order):**

> **Tamers Lab [W.I.P] — a Digimon companion app where you chat with your Digimon via LLM, find wild ones in the real world like Pokémon GO, fight real-time Digimon World-style battles, and transfer Digimon between games**

(~233 characters, under Reddit's 300 limit)

**Alternates:**

- Short and punchy:
  > **Tamers Lab [W.I.P] — LLM chat with your Digimon, Pokémon GO-style wild encounters, real-time Digimon World-style battles, and Digimon transfers between games**
- Hook-first (for generic r/digimon):
  > **[W.I.P] Your Digimon can talk now — Tamers Lab: AI chat, real-world wild hunts, Digimon World-style 3D battles, a 3D farm, and cross-game transfers**
- Question-style:
  > **What if your Digimon could talk to you, appear around your neighborhood, and follow you between games? Tamers Lab [W.I.P], an Android Digimon companion app**
- Fork-forward (for the VBHelper / Vital Bracelet community):
  > **Tamers Lab [W.I.P] — a fork of VBHelper with LLM chat, Pokémon GO-style wild Digimon, Digimon World-style real-time battles, and multi-game save transfers**

**Suggested flair / subreddits:** `W.I.P` / `Fan Art`-style flair where allowed;
r/digimon, r/DigimonVitalBracelet (fork context), r/AndroidGaming (if an APK is shared).
Attach a short GIF first — Reddit mobile truncates long text posts behind the media.

---

## 2. Ready-to-paste post body

**TL;DR —** Tamers Lab is a fan-made Android companion app, and a fork/expansion of
[VBHelper](https://github.com/nacabaro/vbhelper). It keeps all the Vital Bracelet tooling
and adds an in-world Tamer layer: **LLM chat with your Digimon, a Pokémon GO-style radar
that finds wild Digimon around you, real-time 3D Digimon World-style battles, a 3D
Digifarm where your partners live, quests and recruitment, and a Storage vault for moving
Digimon between game saves.** It's an early alpha — I'm posting to get feedback and find
out what people want next.

### What is Tamers Lab?

Tamers Lab started as a fork of VBHelper (the excellent community app for the Vital
Bracelet family) and grew into a bigger "one home for all your Digimon" project. Everything
VBHelper does is still here: NFC read/write for VB / VH / VBC / VBBE, DiM/BEM card import,
Dex with evolution and Jogress/fusion info, Storage, items and the shop, per-card adventure
missions, database export/import, and the VitalWear watch-transfer hooks. (Its unfinished
online-PvP client is also unchanged — it still points at the old external server.) On top
of that, I've been building the features below — none of them exist in VBHelper.

### 1) Chat with your Digimon (LLM) — new

- Every stored Digimon can be talked to in **DigiLine**, a chat inbox that keeps one
  conversation per individual. Your partner has its **own personality (16 types), mood and
  trust level**, remembers the conversation, and knows its species profile, its real
  evolution history and your Tamer name — so two Agumon can feel like two different
  characters.
- **Bring your own model:** the app supports **7 OpenAI-compatible gateways** (OpenRouter,
  Api.Airforce, UnoRouter, UniteRouter, OpenCode Zen, LiteLLM, or any custom
  OpenAI-compatible endpoint), with per-provider API key, model and temperature. Free
  OpenRouter roleplay models work.
- **Wild Digimon talk too.** When you meet a wild Digimon in the world, you can chat with
  it in its own voice; it can react to you, offer quests, accept or decline a duel. The
  model's reply is validated in code, so it can suggest things but can't invent mechanics
  or force a battle.
- Small extras: your partner writes a **weekly diary entry** based on what actually
  happened to it (vitals, evolutions, wins/losses), and your personal **research notes
  (lorebook)** are fed into the conversation as context.

### 2) Find Digimon in the real world (Radar) — new

- The Radar is a **GPS + compass-driven, full-bleed isometric 3D world** — not a list. Your
  active partner stands at the centre of the plate and wild Digimon stand at their **real
  bearing and distance** around you. Walk and the grid world scrolls under you; turn and the
  digivice-style compass ring follows; pinch to zoom from 50 m out to 400 m.
- The wild population is **persistent and simulated**: spawns are stage-weighted, expire
  after about 30 minutes, and the app resolves your surroundings from OpenStreetMap
  (park, urban, water, grassland, industrial…) and **biases which Digimon appear** by
  biome.
- Get within **~40 m** of a wild Digimon to interact: chat with it, battle it, or pick up
  its quest; long-press for its Dex entry. Raise a wild Digimon's trust, finish its
  recruitment quest, and it joins your Storage as a real partner with its own identity,
  personality and chat history.
- Honest note: this is a radar, not a map (no map tiles), and the autonomous
  wild-vs-wild life sim is currently **paused** while the player-facing encounters are
  polished.

### 3) Real-time 3D battles, Digimon World style — new

- Fights are **real-time battles in a 3D arena** (Filament), not a sprite slideshow. Your
  Digimon moves and attacks on its own while you issue orders — the **Digimon World /
  Re:Digitize** feel — with a DW1-derived damage formula, Virus → Data → Vaccine
  interactions, statuses and counters.
- The command deck is a fixed **3×3 instrument inspired by Re:Digitize Decode**: your three
  equipped techniques, Items / Special / Strategy, and Focus / Defend / Move-to-point.
  Strategies actually change the AI's behaviour.
- **Battle Blast** opens a timing minigame mid-fight — hit the window and your Digimon
  performs a **Blast Evolution** form change or a **Jogress** fusion finisher with a
  cinematic camera pass.
- Modes: training battles, wild duels straight from the Radar, and the **Tamer Arena** —
  a roster of canonical tamers from the anime/manga/games with 1×1 and 2×2 formats, 8- or
  16-entrant single-elimination tournaments, three difficulties, persistent brackets,
  records and bit rewards.
- **AR battles:** point the phone at a table or the floor and the fight anchors to your real
  surface, auto-scaled from tabletop (~0.9 m) to open space (~8 m), with the camera feed as
  the backdrop. ARCore-only; it falls back to the normal arena on unsupported devices.

### 4) Transfer your Digimon between games (Storage vault) — W.I.P.

- **Storage is the universal vault.** Read a supported game save, copy individuals into
  Storage with their identity, nickname and progress, preview where they would land in a
  destination save, review native changes, then send them back or into another supported
  game. The Vital Bracelet sits in the same origin list, so watch Digimon can be stored
  alongside your game saves.
- Working today: **Digimon World DS (USA, DraStic raw 64 KiB `.sav`)** — read the roster and
  party directly on the phone, copy party Digimon into Storage provisionally, and an
  **experimental native move/send** that writes with pre-flight backups, a durable journal,
  readback and recovery. **Time Stranger** supports inspection and provisional party/bank
  snapshot copies (writes are paused while DS moves are finished).
- A **read-only Windows PC companion** exposes your save folders to the phone over your
  local network (pairing + pinned TLS), so no USB shuffling is needed.
- This is the most experimental pillar and I want to be upfront: **no writer is validated
  against the retail game yet** (load/checksum acceptance is unverified), Dawn/Dusk and
  later games are planned but not implemented, and a "copy" is not yet a fully managed
  cross-game move. Cross-game stat conversion is being built as a lossless native-progress
  track.

### 5) A 3D Digifarm home — new

- Home is a persistent **3D island** where up to 12 stored partners live with their full VB
  pose sets: they breathe, stroll, jog, work out, nap, and occasionally walk up to each
  other for a greeting — with body collision and a slow cinematic camera that drifts and
  cuts shots around them.
- The island stays composed behind every screen, so transitions between Home and the other
  places (Radar, Arena, Dex, Shop, Partners) are animated rather than cut.
- Honest note: right now the island is a cosmetic living diorama — the old farm
  needs/activities/autonomous farm dialogue are retired while the new version is built.

### 6) Quests, taming and recruitment — new

- **20 authored quest templates** (supply runs, rival duels, patrols, training requests,
  medicine, couriers, missing persons, lost property, rescues, timed/constrained battles,
  recruitment trials…) with multi-phase objectives, quest tokens, battle constraints
  (time limit, item limit, minimum health, team size) and follow-up chains.
- Quests come from **wild Digimon you meet in the world**, in their own voice, and finishing
  a recruitment chain is how a wild Digimon becomes your partner. The Radar tracks quest
  targets near you.

### 7) Extras that came with the fork

- **Bundled offline Dot-sprite library**: 1,662 species identities, 824 with confirmed Dot
  artwork, a custom species catalog editor and a VB-first / Dot-first artwork priority — so
  the app is usable without a card.
- A **home-screen widget** where your active Digimon walks around, idles and opens the app
  when tapped.
- **Lorebook** research notes, **English / Português (BR) / 日本語** UI, themes, a
  soundtrack, and a modular codebase (save-core, PC companion protocol and hardware
  modules are separate from the app).
- A large automated test suite (unit + instrumented) guarding the refactor.

### Status: what's W.I.P. / not ready yet

- **Alpha 0.7.0, unreleased.** No public APK yet — I'm still doing device passes. Expect
  rough edges, and don't run it on data you can't lose yet.
- **No retail-game validation yet for save writes.** World DS send is structurally
  tested and journaled, but not accepted by the retail game; Time Stranger writes are
  paused; Dawn/Dusk are planned.
- **Newest visual features are still being verified on hardware** (AR anchored fights,
  radar/biome readout, battle and farm captures) — compilation is not device acceptance.
- **LLM chat needs your own API key** and model quality depends on the model you pick;
  the wild-vs-wild ecosystem is paused, there's no day/night cycle yet, and permanent
  universal evolution is planned.
- **Fan project:** not affiliated with Bandai; you still import your own cards and watch
  secrets, and nothing here redistributes card data.

### What I'd like from you

1. **Priorities:** of the pillars above (AI chat, real-world Radar, 3D battles, Digifarm,
   cross-game transfer), which one should I polish first?
2. **Testers:** especially people with spare Android devices, ARCore phones, and DS save
   files (World DS / Dawn / Dusk) who don't mind reviewing read-only flows.
3. **Feedback** on the battle feel, the radar, and the transfer flow — what feels wrong,
   what's missing?

Repo / build instructions: `[[your repo link]]`
Upstream VBHelper: https://github.com/nacabaro/vbhelper

**Credits:** nacabaro and everyone behind VBHelper (lightheel, cfogrady, b13st, 457R0 and
all contributors/translators), the Digimon.net reference material, and the artists behind
the bundled Dot sprite credits. This is a community fan project built on their work.

---

## 3. Compact summary (for replies / crossposts)

> **What's in Tamers Lab that isn't in VBHelper:**
> 1. **LLM chat** with every Digimon (personality, mood, trust, memory, diary, 7
>    OpenAI-compatible gateways, wild Digimon chat with quest/battle intents).
> 2. **Real-world Radar** (GPS + compass 3D isometric radar, OpenStreetMap biomes, wild
>    spawns at real distance, 40 m interactions, taming/recruitment).
> 3. **Real-time 3D battles** (Digimon World/Re:Digitize-style orders, Re:Digitize Decode
>    3×3 command deck, Battle Blast → Blast Evolution/Jogress, Tamer Arena cups, ARCore AR
>    battles).
> 4. **Cross-game transfer vault** (read World DS saves, provisional copies, experimental
>    native move/send, Time Stranger inspection, read-only Windows PC save companion).
> 5. **3D Digifarm home** (12 partners living on a persistent island), **quests and
>    recruitment chains**, **offline Dot sprite library + species editor**, lorebook.

---

## 4. Media & posting checklist — do NOT post

Recommended media (GIF first — Reddit mobile collapses long text behind media):

1. **Radar** — the isometric plate with the compass ring and wild Digimon around you.
2. **Chat** — a short DigiLine exchange with the personality/mood panel visible.
3. **Battle** — the 3×3 command band mid-fight; a second clip of a Blast/Jogress finisher.
4. **Tamer Arena** — the tournament bracket or the tamer roster.
5. **Digifarm** — the island with several residents walking/greeting.
6. **Transfer** — reading a World DS save and the Storage vault side by side.
7. **AR** — an anchored AR battle on a table (only if you have an ARCore phone clip).

Before posting:

- Replace `[[your repo link]]` with the repo/APK destination (the GitHub repo is currently
  private/404, so decide whether to publish it, share an APK, or ask people to build).
- Decide how you want bugs reported (GitHub issues / Discord / DM) and add one line.
- Do not title it "release" or "available now" — this is a W.I.P feedback post.
- If a mod asks about assets: card data and watch secrets are user-imported, Dot sprites
  ship with credits, and the project is a non-commercial fan project.

---

## 5. Internal fact-check appendix — do NOT post

Status labels from the code audit ([local-features.md](local-features.md)) and docs
([docs-status.md](docs-status.md)). Keep the post's caveats if you edit it.

| Claim used in the draft | Status | Notes |
|---|---|---|
| LLM chat with partner/wild Digimon | LIVE (wired) | `AppRoute.Chat`, `world/ui/WorldChatScreen.kt`; single-shot replies (no streaming), last-20-message context, BYO key |
| 16 personality types, mood, trust | LIVE | `domain/personality/`; trust > 75 unlocks Wild Ones contacts; quests are the route to max trust |
| 7 OpenAI-compatible gateways | LIVE | `chat/ChatApiProvider.kt` (OpenRouter, Api.Airforce, UnoRouter, UniteRouter, OpenCode Zen, LiteLLM, custom) |
| Weekly diary | LIVE | `chat/DigimonDiaryService.kt` (7-day gate) |
| GPS isometric radar + 40 m interactions | LIVE | `world/ui/RadarPlaceScreen.kt`, `RadarWorldGeometry.INTERACTION_RANGE_METERS = 40.0`; Fused location every 2 s, stale fixes rejected |
| OSM/Overpass biome bias | LIVE (device readout pending) | `world/OpenStreetMapBiomeDetector.kt`; degrades to `NULL` (no bias) when unavailable |
| Persistent wild spawns (~30 min, stage weighted) | LIVE | `WorldRepository` / `WorldSpawnSelector`; 60 active cap, 350 m / 1 km targets |
| Wild-vs-wild autonomous sim | **PAUSED** — do not advertise as live | `WildSocialInteractionGate.ENABLED = false`; player-directed encounters unaffected |
| Real-time 3D battle + 3×3 Decode deck | LIVE | `battle/ui/offline/*`; Filament arenas; 60 Hz simulator; DW1 damage formula |
| Blast Evolution / Jogress | LIVE | `blast_evolution.json`, timing minigame, cinematic finishers |
| Tamer Arena (1×1/2×2, 8/16 cups, records) | LIVE | `battle/ui/arena/*`, `tamers.tsv`; settlement only touches records/bits, not owned stats |
| AR battles | GATED (preference + ARCore device) | `ar/*`; falls back to normal arena; anchored fight still needs a hands-on device pass |
| World DS read + provisional copies | LIVE | `game-save-core` `WorldDsSaveReader`; USA/DraStic raw `.sav` |
| World DS experimental move/send | LIVE (experimental) | `ExperimentalWorldDsMoveRepository` / `SendRepository`; retail load/checksum acceptance **unverified** |
| Time Stranger inspection + snapshots | LIVE (writes paused) | `TimeStrangerSaveReader`; transfer dev paused |
| Dawn/Dusk/Lost Evolution/Xros Wars | **PLANNED** — catalog entries only | `TransferGameCatalog`, `NdsStoryProfile.all = listOf(WORLD_DS)` |
| PC save companion | LIVE, read-only | `pc-save-companion/`, `companion-protocol/`; pinned TLS, no write endpoint |
| 3D Digifarm with 12 residents | LIVE | `farm/ui/digifarm/*`; cosmetic diorama (needs/activities/farm LLM dialogue retired/WIP) |
| 20 quest templates | LIVE (host-verified) | `quests/QuestTemplates.kt`; on-device execution deferred |
| Dot sprite library (1,662 species / 824 art) | LIVE | `species_catalog.json`, `dot_sprites.zip` |
| Custom species editor, artwork priority | LIVE | `components/SpeciesCatalogEditorDialog.kt`, `Settings` |
| Home-screen widget (active Digimon) | LIVE, local-only | `widget/DigimonWidgetProvider.kt`; upstream has no widget |
| EN/PT-BR/JA UI, themes, soundtrack | LIVE | `res/values-*`, `AppThemeSettings`, `audio/AppMusicController` |
| ~1,150 unit tests | Repo claim (README, 2026-10-31) | Not re-run for this draft; drop the number if you want to avoid a stale claim |
| Online PVP | **INHERITED from VBHelper, unchanged** — do not claim as new | upstream `battle/online/*` (lightheel); hard-coded `http://battle.io-void.com:8080` + `auth.nacatech.es` SSO; likely dead |
| VitalWear transfer hooks | **INHERITED** | upstream HCE/ISO-DEP transport; upstream companion screens are actually unreachable in stock 0.6.5.1 (manifest entries stripped), so don't promise them |
| Database export/import (`.vbhelper`) | **INHERITED** (exists upstream and here) | Settings → DB management; can be listed as "still here", not as new |
| Upstream "mood" | Exists upstream as a plain VB character stat | Our **personality + mood + trust** system is new; don't claim the word "mood" itself is new |
| Day/night cycle | **Does not exist** | No code found |
| "Retreat" battle command | **Does not exist** | Exit ends in `BattleOutcome.ABANDONED` |
| Permanent universal evolution screen | **PLANNED** | In-battle Blast/Jogress only; README/doc calls permanent evolution planned |

**Upstream release context (for honest framing):** nacabaro/vbhelper's last release is
`v0.6.5.1-pre` (2026-07-01); its README still lists online battles, VitalWear and
localization as in-progress. Tamers Lab is 142 commits past that point and no release has
been published from the fork yet.

**Claims to avoid in the post:** "works offline" for chat (it needs a provider), "all
games supported", "verified game writes", "living autonomous wild society" (paused),
"streaming chat", "day/night cycle", "retreat command", "online multiplayer is new".

**Safe one-liners for comment replies:**

- "Yes, chat is bring-your-own-API-key — any OpenAI-compatible endpoint, and free OpenRouter
  models work."
- "No, it doesn't need a card to be usable — there's a bundled offline Dot-sprite library;
  cards are needed for exact watch/DIM association."
- "Transfers are alpha: World DS reads and experimental writes are in, but retail checksum
  acceptance is still being validated."
- "AR needs an ARCore-capable phone; otherwise battles run in the normal 3D arena."
