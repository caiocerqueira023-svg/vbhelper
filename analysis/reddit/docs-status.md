# Tamers Lab — feature status claimed by the project's own docs

Docs-side extraction for a Reddit W.I.P. post: what the repo's design/implementation docs
claim is built, partial, planned or paused — not a code audit and not an upstream diff.

**Honesty frame.** This is the uncommitted Alpha 0.7.0 working tree; its own docs say
"**Nothing has been committed or published**" (README.md, CHANGELOG.md), no production
signing keystore exists and Play upload "has **not** been done". "Implemented" below means
*the docs claim the behaviour exists in this tree with the cited automated checks* — not
device acceptance or release; the newest TESTING.md entries repeatedly say "Native capture:
**Pending**" because no phone was attached.

**Legend:** **Implemented** = docs claim source + checks; **Partially implemented** = built
with a documented acceptance gap/scope cut; **Planned** = docs say plan/not implemented;
**Paused** = built in code, disabled/unaccepted by owner decision.

## Summary table

| Feature | One-line description | Status | Source doc |
|---|---|---|---|
| LLM chat + personality/trust loop | Talk to your Digimon and wild ones; one persistent voice, memory and relationship per individual | Implemented | PRODUCT.md; RADAR_SOCIAL_IMPLEMENTATION.md |
| Real-world Geo Radar | Pokémon-GO-like 3D radar: partner at centre, wilds at real bearing/distance | Implemented | DESIGN.md §"Radar place"; CHANGELOG.md |
| Biomes → spawns | Map surroundings (park/urban/rural…) bias which wild Digimon appear | Partially implemented | TESTING.md §"Radar biome readout" |
| Living wild ecosystem | Wild territories, dens, bonds, LLM chats, joinable wild-vs-wild fights | Paused | .plan/radar-living-world.md; DESIGN.md; CHANGELOG.md |
| Real-world encounters | Tap a nearby wild (≈40 m) to chat, battle or quest; long-press for Dex | Implemented | RADAR_SOCIAL_IMPLEMENTATION.md; QUEST_SYSTEM_IMPLEMENTATION.md |
| 3D real-time battle + Decode deck | Filament autonomous fights, trainer orders, 1×1/1×2/2×2, 3×3 command band, Blast dial | Implemented | OFFLINE_BATTLE_3D_PLAN.md; OFFLINE_BATTLE_DW1_IMPLEMENTATION.md; DESIGN.md |
| AR battles | Point the phone at a surface; the fight anchors there through the camera | Partially implemented | AR_BATTLE_IMPLEMENTATION.md |
| Tamer Arena / tournaments | Franchise roster, 1×1 & 2×2 exhibitions, 8/16-entry cups, records, bits | Implemented | TAMER_ARENA_IMPLEMENTATION.md |
| Blast Evolution / Jogress | Fighter form-change and fusion finishers; Dex evolution/Jogress charts | Implemented (universal evolution: Planned) | DESIGN.md; CHANGELOG.md; EXPANDED_MODE_IMPLEMENTATION.md |
| Digifarm living island | Home is a 3D island where up to 12 stored partners walk, nap, greet | Partially implemented | DESIGN.md §"Farm home shell"; CHANGELOG.md; DIGIFARM_25D_PLAN.md |
| Quests + recruitment chains | Authored contact quests (supply, courier, rescue, trials…) leading to recruitment | Implemented (host-verified) | QUEST_SYSTEM_IMPLEMENTATION.md |
| Multi-game Transfer / Storage vault / PC companion | Link saves, read rosters, copy (partially move) individuals between games via Storage | Partially implemented | MULTI_GAME_TRANSFER_PLAN.md; README.md; pc-save-companion/README.md |
| Offline Dot art + species catalog | 1,662 species identities, 824 with bundled Dot art; cosmetic VB-first/Dot-first | Implemented | EXPANDED_MODE_IMPLEMENTATION.md; PRODUCT.md |
| Online PvP, VitalWear, full localization, DB backup/restore | Docs list these as still-planned | Planned | README.md §"Planned features" |

## Feature notes

### 1. LLM chat + personality and the trust/taming loop
Plain: every stored Digimon can be chatted with in DigiLine (Storage/Wild Ones) through an
LLM, keeping one persistent personality, memory and relationship; chat, battles and quests
move trust, and trust above 75 permanently unlocks Wild Ones contacts.
Mechanics per docs: "The individual voice hash uses permanent identity and personality,
while current species/stage remain factual context"; 16 persisted personality types feed
prompts; public memories are bounded and private transcripts are not leaked into public
context; "In player chat turns every message moves trust coherently: agreement/engagement
up, refusals slightly down, insults strongly down, praise/care scaled by personality…
Generated cheerful wording alone still cannot manufacture gains"; past 75, positive chat
gains drop to a quarter "so quests remain the compelling path to maximum trust"
(RADAR_SOCIAL_IMPLEMENTATION.md §"Speech and memory"; DESIGN.md §"Condition details").
Status evidence: PRODUCT.md ("AI chat and real-world Digimon encounters"); CHANGELOG.md
(`ChatUseCase`, assistant-initiated DigiLine threads). Caveats: "actual model response
quality" was not exercised on a device; a provider key/network is required (offline fallback
speech); the docs treat chat as an existing surface and only claim the voice/memory layer.

### 2. Real-world Geo Radar, biomes and encounters
Plain: the Radar place is a full-bleed 3D isometric world where the active partner stands at
the centre and wild Digimon stand at their real bearing/distance as extruded sprites; a
minimal digivice compass ring turns with the phone and pinch-zoom spans 50–400 m. Tapping a
wild within ≈40 m opens the encounter sheet (chat, battle, quest), long-press opens its Dex.
Mechanics per docs: GPS + compass drive it, the plate slides opposite real walking and
rotates north-up, the distance curve keeps far Digimon legible, one quiet line carries
locating/empty/approximate/frozen states, and `OpenStreetMapBiomeDetector` (Overpass, 250 m,
cached) biases spawns and shows "Surroundings: Park" (CHANGELOG.md "Radar place is now a
full-bleed isometric world"; TESTING.md §"Radar biome readout"; DESIGN.md §"Radar place").
Caveats: it is a radar, not a map (no map tiles); legacy 2D and first-person views exist only
behind the master Debug switch; biome readout "On-device: **Pending**" and honestly absent
while Overpass is unavailable; several radar polish captures pending.

### 3. Living wild ecosystem — implemented, currently paused
Plain: a small Digimon society on the radar — wilds hold home territories, wander in dens,
form pairwise bonds, start friendly chats, rivalries or hostile confrontations, fight each
other abstractly over time (player can spectate or join 2v1/2v2), LLM writes the dialogue,
and time fast-forwards deterministically when the radar reopens.
Status: **Paused** by owner decision. CHANGELOG.md: "Wild↔wild chat and battles are paused
for current versions (owner decision 2026-11-04)… the social planner, dialogue service, NPC
battle simulation, persistence, history and Radar rendering paths all stay in the codebase.
Wild → player greetings/challenges and every player-directed or joined encounter are
unaffected." DESIGN.md repeats it behind `WildSocialInteractionGate`.
Caveats: .plan/radar-living-world.md says implementation paths are connected but "the feature
is not yet runtime-accepted"; WORLD_ECOSYSTEM_PLAN.md still says "Plan only (not
implemented)" — stale, superseded by the later docs above.

### 4. 3D real-time battle, Decode command deck and Blast timing
Plain: trainer-vs-trainer real-time fights in Filament, Digimon World style: the creature
acts autonomously while you order it, in 1×1, 1×2 and 2×2. The ruleset is DW1-inspired
(Virus → Data → Vaccine → Virus, readiness/tempo, Counter, statuses, a DW1-derived damage
formula). The bottom bar is a fixed 3×3 Re:Digitize Decode deck (three techniques / Items,
Special, Strategy / Focus, Defend, Move-to-point) and the special/Blast window is a command
dial with a yellow progress arc, red target arc and needle.
Status evidence: OFFLINE_BATTLE_3D_PLAN.md ("overhaul implemented; compilation, JVM tests and
Android integration approved"); OFFLINE_BATTLE_DW1_IMPLEMENTATION.md (implemented behaviour,
12,000-battle mirror matrix); CHANGELOG.md 0.7.0 Changed; TESTING.md 2026-11-07 (Command
Points removed, deck rebuilt). DigimonWorldBattleEspecifications.txt is the design brief,
not proof by itself. Caveats: "The active bracelet profile is an authored adaptation, not a
claim of full PS1 or latest-Vice emulation"; prolonged FPS/thermal sessions were not measured;
portrait/landscape, TalkBack and font-scale acceptance are "Pending by owner request".

### 5. AR battles
Plain: point the phone at a table or the floor and the two Digimon fight on that real
surface; the camera feed is the background, the arena auto-scales (tabletop ~0.9 m, room
~2.5 m, open space ~8 m) and stays anchored while you walk around it.
Status evidence: AR_BATTLE_IMPLEMENTATION.md — "Source implementation is complete on the
uncommitted working tree"; ARCore is optional and every failure degrades to the normal
battle; 42 JVM maths tests; a device run verified transparent Filament over the live camera
and the hands-free scanner. Caveats: "actual horizontal-plane tap-to-place and an anchored
battle… still needs a hands-on pass"; no re-place action; light estimation not applied;
automatic hand-off battles keep the normal renderer; AR is opt-in and ARCore-dependent, has
no instrumentation tests, and tracking can stay `PAUSED` in dim/low-texture rooms.

### 6. Tamer Arena and tournaments
Plain: a local franchise roster (anime, manga, games, source-linked canonical partners) with
1×1/2×2 exhibitions and 8- or 16-entry single-elimination cups, franchise filters,
persistent brackets, records and Bit rewards.
Status evidence: TAMER_ARENA_IMPLEMENTATION.md — seeded trainer AI changes strategy, uses
specials, decides Blast timing and items; each side has 2 recovery/2 energy/1 remedy;
brackets and rewards persist in Room schema 40; no match writes owned Digimon stats.
Caveats: "Native rendering/touch acceptance requires Android execution and is reported
separately from compilation"; canonical corrections are audited in TAMER_CANON_AUDIT.md.

### 7. Blast Evolution / Jogress and evolution data
Plain: in battle the special slot can play a Blast Evolution form change or a Jogress fusion
cinematic; the Dex shows imported evolution requirements and a Jogress chart, and Jogress
route data is imported from DiM/BEM cards.
Status evidence: DESIGN.md §"Battle finisher motion" — "Blast Evolution shows the imported
result; Jogress stages both sources, hides their bodies and shadows, presents one result, and
then restores both originals"; CHANGELOG.md; the archived regression log records FORM
6,500 ms / JOGRESS 7,000 ms lifecycles and the Jogress import reader.
Caveats: "Permanent universal evolution with a separately researched graph and rules" remains
**Planned** (EXPANDED_MODE_IMPLEMENTATION.md); catalog partners keep their recruited form;
evolution history is "not… sufficient permanent history" for transfer continuity.

### 8. Digifarm living island
Plain: Home is one 3D island diorama where up to 12 stored partners live with full VB pose
sets: they breathe, walk, jog, work out, nap, and occasionally walk up to each other for a
happy greeting; body collision prevents overlap and a cinematic camera drifts and cuts shots.
Status evidence: CHANGELOG.md 0.7.0 Added (full resident-life pass) and DESIGN.md §"Farm home
shell" ("the diorama is a living tableau, not a simulation"). Caveats: the same section says
"Farm creation, switching, needs, activities, grouping and autonomous farm dialogue remain
retired from the home surface", and the multi-farm UI was deleted (CHANGELOG.md; TESTING.md
2026-11-07) — so this is a cosmetic diorama, not the full farm sim from
DIGIFARM_DIGILINE_PLAN.md; nothing on the island writes progress or asks for care; latest
gait/timing captures pending.

### 9. Quests and recruitment chains
Plain: wild Digimon contacts offer authored quests in their own voice; finishing them raises
trust toward a Recruitment invitation that adds the Digimon to Storage while preserving
identity, personality and chat.
Status evidence: QUEST_SYSTEM_IMPLEMENTATION.md — "implemented and host-verified"; 17
authored templates (supply, rival, patrol, watch training, medicine, find/courier/rescue/lost
property, time trial, careful victory, technique trial, recruitment challenge/service/rescue);
up to 3 Normal + 1 Recruitment active; parallel objectives in ordered phases; follow-up
chains bounded to 3 chapters; watch evidence via NFC receipts and VB trophies; the LLM
narrates saved terms but "does not create executable requirements".
Caveats: "on-device execution deferred per owner instruction not to use adb"; Android
persistence tests compiled but not executed; "Watch adventure-clear… objectives are not
enabled"; difficulty bands are "provisional balancing values".

### 10. Multi-game Transfer, Storage vault and PC companion
Plain: one origin list (games + Vital Bracelet + other origins) where a Digimon can travel
game → Tamers Lab Storage → another supported game while keeping identity, nickname,
personality and progress; Storage/Home is the universal vault; a read-only Windows companion
exposes PC save folders; Time Stranger was the proof adapter and Digimon World DS is the
active native-move target.
What docs claim works today: linked local saves/folders and paired LAN PC companion; Time
Stranger inspection + **provisional** party/bank snapshot copies; World DS **USA/DraStic raw
64 KiB** read on the phone (native stats/aptitude/eight EXP channels), provisional party
copies, reviewed species/form mapping into shared Home; read-only destination preview;
reviewed native-change review; universal progress vectors/checkpoints with a synthetic
zero-progress A/B/C/A invariant. Top status: "Storage → World DS send now exists end to end
for exact retained records: the core inserts the original 384 bytes into a zeroed destination
slot, the writer journals backup/readback phases with recovery…"
Caveats (explicit): "Game-load/checksum acceptance, the `lastId` high-water-mark behavior and
the game-side party cap remain unverified; Time Stranger and other send origins remain
incomplete, as does real cross-product growth conversion"; Dawn/Dusk have "no reader
registration, no acquisition profile, no writer and no UI enablement yet" (planning only);
the PC companion is read-only ("No game-save write endpoint exists"); snapshots are not
managed moves; farm transfer is excluded; Time Stranger transfer development is paused.
Future game order named by the docs: DS Story (World DS, Dawn/Dusk, Lost Evolution/Super Xros
Wars), Re:Digitize/Decode, PS World games, Cyber Sleuth/Next Order.

### 11. Offline Dot art and species catalog
Plain: a bundled, offline sprite library so Digimon art works without a card: 1,662 canonical
species identities, 824 with confirmed Dot artwork, a user-editable species catalog,
per-species sprite choice, and only a cosmetic VB-first/Dot-first artwork priority.
Status evidence: EXPANDED_MODE_IMPLEMENTATION.md Milestones 1–3; PRODUCT.md. Caveats: "Full
card-free gameplay and native upgrade acceptance remain pending"; the Expanded-mode gameplay
restrictions in that document's body were removed on 2026-10-17 (its own superseded-policy
banner).

## Known limitations / still pending (keep these in the W.I.P. post)

- **No release / native acceptance is the biggest gap:** "Nothing has been committed or
  published"; unsigned release, no production keystore, Play upload not done; latest
  TESTING.md entries repeatedly say "Native capture: **Pending**… No ADB device/AVD here",
  and battle portrait/landscape/TalkBack/font-scale acceptance "remain owed before native
  acceptance — compilation is not native approval."
- **AR:** the full "scan → place → anchored fight" path "still needs a hands-on pass"
  (AR_BATTLE_IMPLEMENTATION.md); no AR instrumentation tests; no re-place; no light estimation.
- **Wild ecosystem:** "the feature is not yet runtime-accepted"; wild↔wild chat/battles
  paused (owner decision 2026-11-04).
- **Chat/LLM:** actual model response quality not exercised on a device; provider key/network
  required; offline fallback speech only.
- **Battle/Arena:** authored values, "not a claim of full PS1 or latest-Vice emulation";
  prolonged FPS/thermal measurement not done; Arena native rendering/touch acceptance is
  separate from compilation.
- **Quests:** on-device execution deferred; Room instrumentation compiled but not executed;
  adventure-clear objectives not enabled; difficulty bands provisional.
- **Transfer:** game-load/checksum acceptance and native identity/authority unverified; real
  cross-product growth conversion incomplete; Time Stranger paused; Dawn/Dusk planning-only;
  PC companion read-only; farm transfer excluded; snapshot copies must not be sold as moves.
- **Digifarm / evolution:** needs, activities and autonomous dialogue are retired; island life
  is cosmetic; universal app-side evolution remains planned, and catalog partners keep their
  recruited form.
- **Not shipped features:** DigimonWorldBattleEspecifications.txt is a design brief and
  OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md's 50 proposals "do not describe functionality already
  implemented by this analysis."
- **Planned-only:** online PvP (upstream `lightheel` work behind a BuildConfig flag), VitalWear,
  full localization (JA/PT-BR UI strings exist; "full localization is not complete"), DB
  backup/restore (README.md §"Planned features").

## Doc conflicts found (and which statement I trusted)

1. **WORLD_ECOSYSTEM_PLAN.md** says "**Status:** Plan only (not implemented)", but
   RADAR_SOCIAL_IMPLEMENTATION.md, .plan/radar-living-world.md and CHANGELOG/DESIGN record a
   later implementation (then a pause). *Trusted the newer docs; the plan's status is stale.*
2. **.plan/radar-first-person.md** says "plan only, not implemented", but README/CHANGELOG/
   DESIGN say first-person Radar was later implemented and is now Debug-only legacy. *Stale.*
3. **EXPANDED_MODE_IMPLEMENTATION.md** body describes VB/Expanded gameplay modes and
   participation gates; PRODUCT.md, README.md and MULTI_GAME_TRANSFER_PLAN.md state those
   modes were removed on 2026-10-17 in favour of cosmetic artwork priority. *Trusted the newer
   contract; the Expanded doc's own banner calls its body history.*
4. **.plan/battle-surface-brief.md / battle-screen-plan.md** still mention the CP instrument;
   CHANGELOG.md and TESTING.md (2026-11-07) say CP and the Support/cheer command were removed.
   *Trusted the newer removal record.*
5. **MULTI_GAME_TRANSFER_PLAN.md** section 14 (schema 51) and section 20's "Current gap" say no
   Storage-to-game execution/writer exists, while its top status (schema 55, 2026-10-25) says
   World DS send exists end-to-end for exact retained records. *Trusted the newer top status +
   section 21; the in-game acceptance caveat is unchanged.*
6. **README.md** calls the fork "a fork of `lightheel/vbhelper`", while the project context and
   the old `applicationId` (`com.github.nacabaro.vbhelper`) point at nacabaro's app. *Resolved
   2026-11-14: the app now uses its own `applicationId` (`com.tamerslab.vbhelper`, ADR 0006) and
   is no longer installed as a VBHelper update; README's upstream lineage wording is unchanged
   and still refers to the upstream projects.*
