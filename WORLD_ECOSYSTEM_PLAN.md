# World Ecosystem Plan — Living Digimon Society for the Radar

**Status:** Plan only (not implemented)
**Scope:** Radar tab of the World hub (`WorldScreen.kt`)

## 1. Concept

Turn the radar from static pins into a **society of anchored agents**: each wild Digimon has a *home territory*, mostly stays there, occasionally wanders, and forms *pairwise bonds* with neighbors. Bonds + personality + mood drive social context, which decides what happens: friendly chat, rivalry → battle, curiosity toward the player. All NPC↔NPC dialogue is LLM-generated; battles resolve abstractly over time so the player can butt in mid-interaction. The world only ticks while the radar is open, then fast-forwards deterministically on return.

### Design decisions (confirmed)

| Topic | Decision |
|---|---|
| NPC↔NPC dialogue | **Always LLM** (templated lines only as offline fallback) |
| NPC battles | **Abstract resolution over time**, player can join mid-battle: side with one digimon (2v1) or fight both (2v2), decided by social context |
| Interruption UX | **Observe-then-join** — spectate the live interaction, then jump in |
| Background simulation | **Only while radar is open + deterministic catch-up** on reopen |

## 2. Data model (Room, requires migration)

### `WorldSpawn` additions

- `homeLat`, `homeLon` — permanent anchor (current lat/lon becomes the anchor; lat/lon stays as the *live* position)
- `wanderTargetLat`, `wanderTargetLon` — current wander destination
- `moveState` — `HOME | WANDERING | RETURNING | IN_EVENT`
- `anchorRadiusMeters` — how far from home it may roam
- `groupTag: String?` — herd/tribe/den membership

### New entity `WildPairBond`

- PK `(individualA, individualB)`
- `affinity: Int` (−100..100)
- `groupTag` (same den / different den)
- Counters: `chats`, `duelsWon`, `duelsLost`
- `lastInteractionAt`
- Pre-seeded when spawns are clustered so neighbors already know each other

### New entity `WorldInteraction` (the event)

- `id`
- `type: CHAT | BATTLE`
- `participantIds: List<String>` (2, extensible for player join)
- `phase: PROPOSED | ACTIVE | JOINABLE | RESOLVING | ENDED`
- `socialContext: FRIENDLY | RIVAL | CURIOUS_PLAYER | HOSTILE_PLAYER`
- `seed: Long` — deterministic RNG seed
- `initiatedAt`, `expiresAt`
- `turnIndex` (chat) / `roundIndex` (battle)
- `teamPolicy` (battle team composition rule)
- `transcript` (chat JSON)
- `outcome`

**Invariants**

- Max 1 active event per spawn.
- Global on-screen cap: e.g. 2 chats + 1 battle.
- Events extend `WorldSpawn.expiresAt` while active so nobody despawns mid-fight.

**Migration:** DB version bump (#25) + schema export (repo already exports `schemas/`).

## 3. Engine: `WorldEcosystemEngine`

Modeled on `FarmSessionCoordinator` (ref-counted, `acquire()`/`release()` from WorldScreen lifecycle, `SupervisorJob + Dispatchers.IO`).

**Tick interval: ~1.5 s.** All randomness from `Random(seed = individualId ^ interactionSeed ^ tickIndex)` → fully deterministic (required for catch-up).

### Per-tick phases

1. **Movement**
   - Agents with no event lerp toward `wanderTarget` (picked within `anchorRadius`, biased by biome/den), then return home.
   - `IN_EVENT` agents freeze at the event location.
   - Speeds capped (like Digifarm's `0.14 w/s`).
   - DB lat/lon flushed only every ~10 s / on event end — avoids Room-Flow recomposition churn. Rendering merges engine positions over DB rows.

2. **Decision rolls** (per agent, low probability per tick, decision horizon ~30–90 s)
   - Neighbor + same group + affinity ≥ 30 → **friendly chat**
   - Neighbor + affinity ≤ −30 → **rival battle**
   - Player within ~60 m → curiosity/approach (stage/personality-weighted) or hostility (low mood) → **player chat/battle**; otherwise avoid
   - Biases from `DigimonPersonalityTraits` (bold/shy/gregarious) and `WorldSpawn.mood`

3. **Event lifecycle advance**
   - Chat: turn timer → LLM line request → append transcript.
   - Battle: abstract rounds (pressure/damage from `PersonalityBattleProfile` + stats + affinity) until `RESOLVING` → outcome updates bonds/mood.

4. **Emit** `StateFlow<EcosystemSnapshot>` (positions + active events) for UI.

### Suspension

Engine pauses while `battleActive` (matches the existing radar suspension contract at `WorldScreen.kt:166-175`).

## 4. LLM pipeline for NPC dialogue

- New `NpcDialogueService` modeled on `ChatRepository.generateFarmReply` (stateless one-shot).
- Reuses `DigimonPersonaBuilder` for **both** speakers + a social-context preamble.
- One call returns the exchange (both speakers' lines) with `[[MOOD:+N/-N]]` markers parsed by existing `MoodDirectiveParser` / `WildMoodAnalyzer`.

**Guardrails**

- Global mutex + rate limiter (`acquireCallSlot` pattern from `FarmConversationOrchestrator`).
- Max ~1 NPC exchange / 15 s.
- Max 6 turns per session (mirrors `MAX_SESSION_TURNS`).
- 5 min event expiry (mirrors `SESSION_EXPIRY_MILLIS`).

**Fallback:** no API key / network → templated line pool from personality (degradation path only, not the default).

## 5. NPC battles — abstract, joinable

### Timeline

- `PROPOSED` — standoff, ~5 s warning marker on radar
- `ACTIVE` — rounds every ~4–6 s; radar shows two sprites clashing + round ticker
- `RESOLVING` → outcome

### Joining (any time before resolution)

`teamPolicy` derived from `socialContext` decides composition, then hands off to the existing `WorldRadarBattleContent` / `OfflineBattle` (already accepts `List<Fighter>` on both sides):

| Context | Player side | Enemy side | Shape |
|---|---|---|---|
| Side with one (friendly/rival) | `[playerActive, alliedSpawn]` | `[enemySpawn]` | **2v1** |
| Fight both | `[playerActive]` | `[spawnA, spawnB]` | **1v2** ⚠️ see open question |

- Abstract → concrete conversion: wilds' HP seeded from damage already taken in abstract rounds.
- Extend `worldRadarBattleParticipant` to multi-fighter sides.
- Outcome feeds `recordRadarBattleResult` (both wilds) + bond updates.

### If not joined

Abstract outcome is recorded (win/loss affects bonds, mood, maybe despawn of loser). No vitals/XP farming without joining.

## 6. Player interruption UX (observe-then-join)

- **Radar markers** gain event states: speech-bubble icon for chats, clash marker for battles, both with participant tint. Wandering visible as slow sprite drift.
- **Tapping an event → observe overlay** (reuse `WorldEncounterActionSheet` shell):
  - Chat: live LLM transcript streaming as turns resolve.
  - Battle: round ticker / spectator view.
  - No effect on the simulation while spectating.
- **Join button** available any time:
  - Chat → player (or active Digimon) inserts into the 3-way conversation. Extends `WorldChatScreen` to attach to an existing `WorldInteraction` instead of always creating 1:1.
  - Battle → team prompt per §5 → existing battle morph flow.
- Existing 40 m interaction rule kept for *initiating*; event observation works at any range.

## 7. Catch-up (screen-only persistence)

- Persist `lastSimulatedAt`.
- On radar open, if elapsed > 60 s → `fastForward()` on IO.
  - Cap: 30 min elapsed / ~600 ticks.
  - Uses the **same seeded decisions but with LLM generation disabled** — positions, event start/end, bond/mood changes still advance.
  - Missed chat turns get a single lazily-generated **LLM "recap" line** when the player opens the event → cost stays bounded.
- Cap work so the first frame isn't blocked: render immediately, fast-forward in background.

## 8. Supporting changes

- `WorldRepository.ensureSpawnsLocked`: cluster new spawns into **dens** (2–4 spawns, `anchorRadius` apart, same `groupTag`) instead of uniform random; seed `WildPairBond`s between denmates.
- `WorldScreen.kt`:
  - Collect engine snapshot, merge with spawn Flow.
  - Render movement, event markers, overlay.
- i18n: new strings in `res/values*/strings.xml` (PT-BR / EN / JA) — no hardcoded text.

## 9. New files

| File | Role |
|---|---|
| `world/ecosystem/WorldEcosystemEngine.kt` | tick loop, ref-counting, fast-forward |
| `world/ecosystem/EcosystemDecisionPlanner.kt` | seeded decision rolls |
| `world/ecosystem/InteractionOrchestrator.kt` | event lifecycle, joins, caps |
| `world/ecosystem/WorldBattleResolution.kt` | abstract round timeline |
| `chat/NpcDialogueService.kt` | 2-speaker LLM calls + fallback |
| `domain/world/WorldInteraction.kt` | event entity |
| `domain/world/WildPairBond.kt` | pairwise bond entity |
| `daos/WorldInteractionDao.kt` | event persistence |
| `daos/WorldPairBondDao.kt` | bond persistence |
| `screens/worldScreen/WorldInteractionOverlay.kt` | observe/join UI |

## 10. Phasing

1. **Movement + dens** — engine skeleton, home anchors, visible drift
2. **Bonds + NPC chat** — LLM dialogue, observe overlay
3. **NPC battles + joining** — abstract timeline, team composition, battle handoff
4. **Catch-up fast-forward + polish**

## 11. Testing

- Engine determinism: same seed → same state.
- Catch-up ≡ continuous run (fast-forwarded state equals real-time state).
- Bond evolution after chat/battle outcomes.
- Event cap / one-event-per-spawn dedup guards.
- Migration #25 upgrade path (schema export).
- Extended `RadarBattleFlowTest` for multi-fighter teams.

## 12. Open questions

1. **"Fight both = 2v2":** should the player pick a **second owned Digimon** for that mode, or is 1v2 (player + active Digimon vs two wilds) acceptable?
2. **Freeze policy:** freeze the whole ecosystem during player battles (current plan, matches existing suspension contract), or keep NPC-only events ticking in parallel?
