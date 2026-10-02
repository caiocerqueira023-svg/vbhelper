# Radar Living World — Unified Implementation Plan

**Status:** Main implementation paths for Phases 1–5 are connected. The current integrated hardening pass and automated checks pass. Device acceptance is deferred at the user's request; the feature is not yet runtime-accepted.
**Scope:** World → Radar, including its existing encounter, chat, and battle integration.
**Inputs:** [First-person Radar](radar-first-person.md) and [World ecosystem](../WORLD_ECOSYSTEM_PLAN.md).
**Evidence:** Reviewed against the current working tree on 2026-09-30, including existing uncommitted changes. Recheck affected interfaces before implementation.

This document combines and reviews both proposals. It is the proposed implementation sequence; the two source documents remain unchanged. Decisions called **retained** come from the source plans. Decisions called **proposed** resolve gaps or conflicts and are not presented as additional user confirmations.

## Implementation progress — 2026-10-01

### Completed initial Phase 1 slice

- Added pure `world/RadarWorldGeometry.kt`: validated coordinates, geographic distance/bearing, meter-based offsets, date-line wrapping, polar stability, and heading-aware 2D projection. Radar marker culling and 40 m eligibility now use the live player fix. Spawn placement/counting uses the same geometry and retains the existing population targets.
- Extended `WorldSpawn` with home coordinates, optional wander target, `HOME` movement state, 25 m initial anchor radius, optional den identity, and neutral ecosystem emotion separate from player trust.
- Registered Room **24→25**, exported schema 25, and added durable `WorldEcosystemSession` checkpoints. Migration backfills homes from existing positions and preserves individual identity, private history, trust, contacts, and recruitment state.
- Added the application-scoped `WorldEcosystemCoordinator` and immutable geographic snapshot contract. Idempotent surface leases share one clock; region admission uses 1 km plus a 50 m exit margin and stable individual ordering. Checkpoints flush on suspension and batch approximately every 10 s while running.
- Added the 1.5 s fixed-step clock contract, fractional tick preservation, 30-minute/1,200-tick cap, one-time dormancy rebasing, clock-rollback high-water timestamp, explicit rejection of unsupported rules, and a documented/pinned v1 seed mixer.
- Wired Radar's lease to the resumed lifecycle, including compass/GPS and decorative frame-loop suspension. The battle owner persists a freeze before handoff, rejects a failed freeze checkpoint, and unwinds failed/cancelled preparation. The saved freeze is excluded from return catch-up, including process recreation.

### Completed transactional Phase 1 slice

- Registered additive **25→26** and exported schema 26 for `WorldInteraction`, archived participant rows, unique per-wild participation claims, and a unique result ledger. The 24→25 migration and schema 25 remain available in the upgrade chain.
- Added `WorldInteractionRepository`: commands share Room transactions with conflicting World writes. Direct battles reserve for at most 30 s, then commit against the expected event revision after rechecking a location fix no older than 30 s, 40 m range, current claims, and owned identity. Direct battle ownership is bounded to 30 minutes; private chat requests and NPC proposal scaffolding are bounded to five minutes.
- Added NPC proposal/activation contracts with stable participant ordering and two-chat/one-battle caps. Activation requires participants to have actually met within 3 m and remained inside their anchors. These APIs have no scheduled autonomous caller yet; the fixed-step engine must own their scheduling and timing before they are enabled.
- Guarded expiry and eviction SQL retains claimed spawns, including expired pinned encounters in population counts. Population admission/eviction and debug spawning now commit in transactions; biome lookup remains outside the transaction. Recruitment/pending/removal validate claims in the same transaction as their effects, and recruitment rechecks active vitals at commit.
- Added database guards for raw spawn deletion, recruitment-state changes, and invalid claims. Card/species, individual, or owned-character deletion cancels affected interactions and releases claims before cascades. Archived participant identity and ordinary private history survive encounter expiry/card deletion.
- Replaced deletion-based battle deduplication with the event-ID ledger. Owned win/loss updates, opposing-wild deletion on victory, claim release, terminal state, and the applied marker commit atomically. Repeated defeats now record once; draw/abandonment and autonomous outcomes award no owned result. Role-based effects retain allied wilds.
- Wired existing Radar battles and private 1:1 message requests to these claims. Provider I/O occurs outside transactions; late private replies cannot apply trust/recruitment/removal effects after ownership expires. Failed recruitment is surfaced instead of reporting a successful recruit. Busy/unavailable/stale-location errors are localized in English, Brazilian Portuguese, and Japanese.
- Persisted Radar encounter seeds are passed into battle preparation and preserved on loading retries. Outcome callbacks validate the current session, and exit can retry the same terminal commit without duplicating effects.
- Cold database open cancels stale direct reservations/chat requests and interrupts unrecoverable player-owned battles without awards. Completed result commits remain terminal. Ended-event retention keeps the latest 100 for up to seven days, with a bounded open-child exception for source context.
- Population snapshots now include interaction summaries, retain claimed participants outside the region/normal expiry until release, and advance revision when membership/event summaries change.

### Completed command/readiness and 2D snapshot slice

- Added lease-epoch/revision/status tickets for Radar encounter, debug/population, card-setting, reservation, and handoff commands. The coordinator validates the submitting lease, readiness, current membership/claims, accepted fix age, and geographic range, then rechecks database-backed state before applying a command.
- Room command effects and the session checkpoint now share one transaction. Storage failure/cancellation restores in-memory state too; failed readiness is retained until explicit recovery. The repository spawn mutex was removed in favor of this shared Room transaction contract, avoiding opposite lock ordering between gated and legacy callers.
- GPS callbacks carry their attachment epoch; detached/old-lease callbacks cannot update the session. Fix timestamps refresh even at unchanged coordinates, future timestamps are rejected, and a new valid sample can replace a future-dated old fix after clock rollback. The 2 s location request now permits stationary updates; measure its device power/accuracy behavior in Phase 6.
- Biome lookup is resolved outside the simulation mutex/transaction. A delayed refresh rechecks its epoch, the current accepted fix, and a 60 m source-location fence before population mutation; leaving Radar cancels the request and its old ticket cannot commit after resume.
- Added a localized Radar status/recovery surface for reconciliation, storage/rules failure, missing/stale/approximate location, card/region emptiness, population refresh, and pending battle finalization. Recovery buttons use 48 dp minimum targets and a reserved layout slot; status text uses a polite accessibility live region. Zoom/Dex inspection remains available while mutation controls are blocked.
- The 2D map now obtains marker positions, player origin/culling, membership/counts, and movement pose from the coherent ecosystem snapshot. DAO DTOs provide identity-checked assets only. Selection is saved by individual ID; an expired/claimed/out-of-range selection shows a reason and disables encounter actions. Walking poses choose existing walk frames, and reduced motion retains a static sprite/pulse.
- Pending battle event/outcome values remain saved for explicit retry if finalization/checkpoint persistence fails. The ecosystem freeze is released only after successful finalization. Preparation failure uses the same bounded cleanup path. Owned species/identity changes during preparation are rejected before an obsolete roster can commit.

### Connected living-world implementation — 2026-10-01

- Added rules-v2 geographic fixed-step movement at 0.5 m/s, persisted movement/decision ticks, stable seed decisions, per-personality horizons, territory validation, home return, claimed-participant pinning, 30-minute bounded replay with 32-step yielding batches, and display-only geographic interpolation. Rules-v1 clock-only checkpoints rebase once without inventing retrospective movement; seed/identity/history remain intact.
- Registered additive **26→27→28** for dens, canonical pair bonds, accepted input records, normalized public messages, contextual intent records, logical deadlines, and immutable-definition NPC battle checkpoints. Fresh schema 28 and the upgrade chain remain exported.
- New population placement groups compatible individuals into small local dens without increasing the existing spawn targets/cap. Nearby denmates/neighbors seed sparse pair bonds; NPC bond/emotion changes are separate from player trust. Location and command inputs are recorded durably.
- Added first-person Radar using the existing Radar GLB/manifest, north −Z/east +X, bounded linear-near/log-far projection, fixed eye-height/downward pitch, the existing compass, north-facing unavailable-compass preview, extruded idle/walk frames, one sprite GL upload per frame, bounded CPU model caching, 30 FPS scheduling, projection-backed Compose hit targets, and a shared accessible nearby/event list.
- Added generation-stamped renderer release ownership, including a latched releasing state for rapid toggle-back, loading placeholders, forward battle handoff and reverse restoration. Audited pinned Filament **1.76.1** ModelViewer: its detach listener synchronously destroys the Engine; acknowledgments post after detach. Unattached viewers have a separate explicit teardown path. View preference is lifted to World scope across Radar/Digifarm tab changes.
- Added observable NPC proposals/approach/activation, two-chat/one-battle caps, normalized transcripts, remote observation/local joining, player-authored group conversation, speaker/evidence validation, 15 s autonomous provider budget, serialized requests with an eight-player-request cap, player priority, personality-authored fallback, revision/generation checks for late replies, and read-only on-demand recaps. Replay generates no LLM dialogue or contextual intentions.
- Added structured contextual intent to both public exchanges and existing private Radar chat. Challenges/refusals/de-escalation are persisted and validated; NPC mutual acceptance can transfer claims into a linked battle. Owned participation goes through explicit Accept/Decline and team preview. Private source messages stay in private history; a public battle receives only a generic suitable challenge reason.
- Added renderer-free battle execution using the existing simulator/stat/personality/technique mappings. Recovery replays immutable definitions/seed/elapsed fixed steps, not HP alone. A compact public round/HP summary is persisted separately; each resumable combat step commits with clock/movement, and whole cache state invalidates on transaction failure.
- Added initial HP/energy through participant, presentation, factory and simulator while retaining effective maximum HP. Radar permits true 2v1 and 2v2 formation with distinct eligible owned identities. Joining checks current HP/identity/claims/range/revision; preparation cancellation restores the original NPC seed/checkpoint/claims. Committed joined interruption/abandonment does not resurrect the prior fight. Result effects are participant-role based and idempotent; allied wilds are retained and trainer commands reject autonomous wild allies.
- Added English/Brazilian Portuguese/Japanese mode, observation, group-chat, challenge/team, progress, recap, and autonomy labels. History follows incoming lines only while the reader is at the bottom. Existing Digifarm lint blockers were repaired minimally (resource access and unused constraint scope), with no baseline update.

### Integrated hardening — consent, input ordering, recovery, and FP

- Added a pure dialogue policy and regression coverage: only validated speaker/target/evidence proposals affect challenge-specific social outcomes. Acceptance must reciprocate an unexpired NPC challenge, inherits its sparring stakes, and cannot consent for the trainer. Refusal/de-escalation affects only related challenges; expired or invented evidence cannot create hostility. Authored fallback and recaps cannot submit model-generated intent.
- Group player messages now pass through the same reconciled-clock transaction as provider replies before network work. Pending challenge context accompanies group prompts. Gameplay eligibility rejection preserves readiness rather than becoming a storage error; failed checkpoints roll back input effects and logical time. Hidden/frozen sessions reject external inputs.
- Replay keeps all intermediate event-reconciliation revision increments. A bounded replay regression verifies durable revisions never rewind when the final clock checkpoint is published.
- Joined preparation expiry restores the original NPC event even after a missed full timeout; explicit process recovery handles both direct and joined ownership. A committed joined fight remains interrupted without resurrecting its parent. Direct and joined reservations share the one-player-battle guard.
- Trainer Decline controls apply only to trainer-addressed challenges. A private refusal cannot cancel another public NPC conversation's challenge.
- FP now reads the existing Radar manifest and applies its visual scale. Mesh placement and hit-target projection share the near-camera display offset; incomplete idle/walk animation assets fall back to an available pose. Load tracking is pruned with membership, and suspended views do not enqueue new sprite builds.

### Gameplay feedback revision — scene switching, camera, and wild initiative

The user's runtime feedback supersedes the initial fixed-pitch, always-visible-label/list, and mandatory target-agreement assumptions below.

- Renderer ownership now publishes immutable `StateFlow` state collected by Compose. Requests and detach acknowledgments update the viewport without an unrelated manual tap. FP and battle release acknowledgments use the main-thread Handler, since posting through a detached host view can strand the callback. Permissions for a renderer that never created an engine also unwind. Incoming private attack payloads select Radar automatically.
- Lowered FP eye height from 1.35 to **0.85** scene units, doubled ordinary sprite scale (0.7→1.4; early-stage scales 0.85/1.05), and added bearing-preserving **1.8-unit** close-range presentation clearance. Meshes and projected body-sized touch targets share placement; eligibility still uses geographic coordinates.
- Added vertical drag and accessible Look up / Center / Look down controls. Pitch defaults to **−12°**, bounded to **−60°…+45°**; compass yaw remains authoritative. Names/distances are togglable, off initially, with preference retained across World tabs.
- Replaced the default nearby/event button lists and modal observer with participant-attached conversation/battle indicators and a scene-adjacent spectator panel. Live attributed speech appears over its speaker; NPC battles show health and bounded alternating visual lunges. Meeting participants have display spacing in both views. Tapping a participant/marker immediately spectates the event. History is on demand; reduced motion disables decorative lunges/pulses.
- Eligible local wilds can open a chat themselves or initiate an attack based on personality/emotion. These decisions require resumed visible Radar, fresh physical proximity, and no conflicting claim; they never target the player during replay. Scheduling has a two-minute global/four-minute individual cooldown and retains event caps.
- Hostile contextual challenges can transfer to NPC combat without target acceptance. Trainer-directed hostile attacks transfer to the existing 1v1 owner after a six-second windup, without an Accept prompt. Private hostile intents return to Radar automatically. Friendly sparring and voluntary 2v1/2v2 intervention retain invitation/team selection. Model identity/evidence validation still applies.
- Single-wild attack handoff includes transactional claim transfer, preparation restoration, source linkage, active-owned revalidation, and idempotent ordinary results. There is no renderer-free battle with a missing opponent for a player-directed attack.
- Latest verification: **336 tests reported, zero failures, three fixture skips**; debug build, Android test compilation, and lint pass (170 advisory warnings/seven hints outside the historical baseline). All 24 host SQLite checks pass. Added camera/observable-handoff/spacing cases and four initiative/presence/privacy cases; new Room and Compose cases are compiled, not executed. The reported scene stall and revised visual framing still need device confirmation.

### Conversation-screen and label feedback revision

- Conversation participant/marker taps now open `WorldConversation/{interactionId}` using the existing regular/group chat components: TopBanner, full-height ChatHistoryPanel, attributed ChatMessageBubble rows, and ChatComposer. Single-wild conversations use the regular visual layout; multi-wild conversations show group participants/authors. Radar's boxed panel is restricted to battle spectating.
- The public event transcript stays separate from private 1:1 history. The resumed chat route acquires a shared ecosystem lease and refreshes location, retaining one clock while Radar detaches. First-send participation is transactionally joined and revalidates event revision, claims, freshness, and each wild's range. Remote/ended/private/unready contexts remain read-only. Battle transitions return to the Radar owner through a stable back-stack payload.
- Reversed vertical drag direction. Look up/down buttons use a shared bounded pitch-step helper. Replaced name/distance text inside sprite hit boxes with measured overhead annotations in both views: separately wrapped name and distance, viewport-edge clamping, text-aware vertical clearance, and no label placement over the sprite when overhead space is unavailable. Activity/speech cues share the measured stack.
- Structured speech is decoded independently of intent metadata. Invalid intentions cannot execute, while valid attributed prose remains readable. Private history rendering also cleans old envelope messages without rewriting stored records; wrong-speaker or broken envelopes show a localized readable fallback rather than raw JSON. Valid private friendly acceptance can offer player-confirmed sparring; single-wild public sparring returns through the existing player battle handoff.
- Full verification passes: **348 tests reported, zero failures, three fixture skips** (345 successful executions), debug assembly, Android test compilation, and lint (173 advisory warnings/seven hints outside the unchanged baseline). Added conversation eligibility/lease handoff, reversed pitch, measured label placement, and malformed-envelope regressions. Large-font label fitting and single-wild sparring Room cases are compiled Android tests; device confirmation remains deferred.

### First-person-only billboard revision

- FP Radar now uses a point-facing billboard for every Digimon: the sprite front rotates toward the player's eye position, including idle, wandering, conversation, and battle poses. Rotation is about the sprite center, and projected label/hit-target corners use the same rotated basis.
- The helper and both call sites are confined to the first-person Radar renderer. The shared camera-assisted facing strategy remains available to the other 3D scenes.
- Full app unit tests, debug assembly, and lint pass: **352 tests reported, zero failures, three fixture skips** (349 successful executions). New cases cover player-facing normals across cardinal directions/heights, matching projected corners, and finite coincident-coordinate behavior. Device visual confirmation remains deferred.

### Unified 1:1 chat, accepted wagers, and battle memories

- Wild-initiated one-Digimon chats and the Radar Talk action now use the same regular `WildContact`/`WorldChatScreen` controller, private history, trust, and persona. Greetings and older single-wild public exchanges import once, with durable provenance preventing duplicates after retries or history editing. Genuine multi-wild chats retain their separate group transcript.
- A structured acceptance of the current player's offered duel/wager now initiates combat automatically after the Digimon answers. It is nonlethal regardless of an aggressive speaking style; a new unaccepted friendly invitation remains distinct. The private response contract is the final transient system instruction and requests the actual stakes for both sides, current user-turn evidence, and an acceptance intent.
- Friendly results keep the wild encounter even when the owned side wins. Standard owned win/loss bookkeeping remains idempotent. Battle context captures the attributed private conversation and agreement before handoff; the battle result transaction stores participant-specific memories, outcome perspective, and a pending private follow-up before any hostile removal effects.
- Registered additive schema **29→30** for battle contexts, permanent-individual battle memories, and private-message import provenance. Memory rows survive retained-event cleanup and are included in future private prompts; private wager excerpts are excluded from public NPC/Farm prompts. Existing identity, conversations, trust, card art, and prior migrations remain preserved.
- Battle exit returns to the same regular 1:1 chat. Its result reaction uses the saved conversation, agreement and actual winner: a Hackmon win in the example should ask for the trainer's promised name; his loss should acknowledge the promised obedience. The model is instructed not to invent fulfillment or treat a configured profile name as the completed wager. Draw/abandonment has no winner's stakes. Reactions commit once and remain pending on generation failure; they do not execute fresh battle intents.
- Regular chat holds a resumed, renewed private participation claim and the shared clock. Autonomous player interruptions and offscreen NPC generation are disabled when only chat is visible. This prevents accepted offers from expiring during a long conversation or a neighbor taking the participant mid-chat.
- Latest checks pass: **357 tests reported, zero failures, three fixture skips** (354 successful executions); debug build, Android test compilation, lint, and **29 host SQLite checks**, including five new migration/memory/provenance/rollback cases. New Room cases for unified greetings, nonlethal victories with preserved wager memories, and real-v29 upgrade compile but have not executed. Live model reactions and the complete phone flow still require device confirmation.

### Remaining acceptance work — user-deferred

- Run the complete Room migration/UI/renderer instrumentation on an authorized device/emulator; exercise natural contextual challenges, refusal, expiry/recruitment/deletion races, team preparation failure, interruption, and repeated renderer/tab/lifecycle transitions.
- Capture actual first-person/observer/team states in normal and large-font/reduced-motion configurations; validate TalkBack, real compass alignment/wrap/calibration, physical range changes and approximate location.
- Measure frame time, renderer/resource counts, repeated-transition memory, stationary GPS workload, request budgets, thermal/battery cost, and worst-case 1,200-tick catch-up. Tune only from measured runtime evidence.

The user explicitly deferred device acceptance after the authorized isolated emulator attempt could not boot: no connected device/AVD, no installed emulator hypervisor driver, and the advertised API 37 system-image directories contain installer metadata without `kernel-ranchu`. No APK was installed and no instrumentation, new device capture, or device performance measurement ran. SDK/image installation is not part of the current authorized work.

### Verification recorded for this slice

- Passed the full `:app:testIntegrityCheckUnitTest` suite, including 23 new geometry/clock/coordinator cases and existing Radar/compass regressions.
- Passed `scripts/test-radar-ecosystem-db.py`: five host SQLite checks executing production migration SQL against the exported v24 schema, comparing migrated/fresh v25 columns/defaults/indexes/foreign keys, and preserving private history/trust/recruits.
- Passed `:app:kspDebugKotlin`, `:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:compileIntegrityCheckAndroidTestKotlin`. Added real-v24 Room migration and fresh-creation instrumentation tests; compilation does not execute them.
- `:app:lintDebug` is blocked by four existing errors in unchanged `screens/digifarmScreen/DigifarmScreen.kt`: `UnusedBoxWithConstraintsScope` at line 410 and `LocalContextGetResourceValueCall` at lines 716, 718, and 722.
- Passed `git diff --check`. Runtime/device evidence remains pending.

### Verification recorded for the transactional slice

- Passed the full `:app:testIntegrityCheckUnitTest` suite with five new interaction-policy cases and a new claimed-participant snapshot case (29 new foundation unit cases across both slices).
- Passed 14 `scripts/test-world-interactions-db.py` checks: real host SQLite validates production 25→26 schema SQL against fresh schema 26, claim uniqueness/identity, expiry/recruitment guards, deletion cascades, atomic rollback, duplicate-result protection, process-open recovery, and retention. The previous five 24→25 checks also pass.
- Passed `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`. Added 15 Room interaction integration cases and a real-v25 upgrade case alongside v24 upgrade/fresh creation. These Android cases are compiled, not executed.
- Re-ran lint: the same four existing `DigifarmScreen.kt` errors remain the blocker. No device installation/runtime checks have been performed.

### Verification recorded for command/readiness integration

- Passed the full app unit suite with 11 command-gate and eight presentation/state cases added for this slice (48 new foundation unit cases across the implementation so far). Cases cover loading tickets, old leases/epochs, stale fixes, clock rollback, duplicate commands, changed claims, transaction failure/cancellation, retry, frozen finalization, stale asset coordinates, identity mismatches, reduced motion, and status priority.
- Passed `:app:assembleDebug` and Android test compilation. Added Room cases for command/checkpoint atomic rollback and changed owned species, plus three Compose status-panel cases for retry invocation, 48 dp targets, slot stability, and unsupported-rule recovery behavior. Instrumentation has not run.
- Both host SQLite suites remain green (19 checks). Lint still reports the same four existing Digifarm errors; its baseline was not changed. `git diff --check` passes.
- Inspected the existing Radar screenshot fixture against current theme/source; the new status panel has not been captured on a device. Large-font/TalkBack behavior, real location freshness, stationary GPS workload, and finalization recovery require authorized runtime verification.

### Current whole-plan build evidence

- Full `:app:testIntegrityCheckUnitTest`, `:app:assembleDebug`, and `:app:compileIntegrityCheckAndroidTestKotlin` pass. Added movement replay/bounds/personality tests, compression/cardinal/release-generation tests, structured-intent validation cases, injured-start/2v1 tests, and complete seeded NPC simulator replay comparisons.
- `:app:lintDebug` passes outside the existing baseline after the four Digifarm errors were fixed. Baseline findings/advisory warnings are still reported by lint; the baseline file is untouched.
- Host SQLite checks pass for 24→25, 25→26, and 26→28 production schema/guard SQL. The new migration check caught and fixed the required `WorldNpcBattle.interactionId` index. Normalized-message request deduplication and cascaded cleanup preserve private chat.
- Added Android coverage for real-v26 upgrade, original NPC checkpoint/claim restoration after failed team preparation, allied-wild retention, and distinct owned 2v2 result application. Compilation does not execute these tests.
- Prior integrated-hardening app run: **319 tests reported, zero failures, three existing private-fixture skips** (316 executed successfully). Assembly, Android test compilation, and lint also passed. The gameplay-feedback section above records the newer run.
- Added six dialogue-policy cases, three coordinator input/revision cases, two external-input rollback/ownership cases, and two FP display/pose cases. Added Room cases for missed joined-preparation timeout and explicit process recovery; those remain compile-only.
- All **24 host SQLite checks** pass: five anchor/identity migration cases, 16 interaction cases including idempotent uncommitted-join restoration and no committed-parent resurrection, and three living-world migration/transcript cases. `git diff --check` passes.

## 1. Product outcome

Make Radar a living local Digimon world that can be viewed from above or at eye level. Wild individuals inhabit small territories, wander, recognize neighbors, converse, and occasionally battle. The player can observe or intervene in NPC interactions; nearby wilds can also start conversations or attack directly.

**One world, two views:** Radar 2D and First person consume the same individuals, positions, events, distances, eligibility rules, and persistence. Switching views changes presentation only. It never restarts simulation, generates a different population, or rerolls outcomes.

The experience remains centered on the player's Vital Bracelet Digimon. Existing HP/BP/AP, DiM/BEM mappings, individual identity, personality, and battle rules remain authoritative. This work does not create an unrelated stat system.

### Retained requirements

- A 2D / First person toggle inside the Radar tab; no additional World tab.
- Reuse `Arena/Radar/radar_grid.glb` and its manifest. No new environment assets.
- First-person camera uses existing compass heading and a lower fixed eye height. Vertical drag and Look controls adjust pitch; the initial slight downward pitch is the reset position.
- Anchored wild individuals with limited wandering, neighboring bonds, and personality-driven social behavior.
- LLM-generated NPC dialogue as the normal path; authored fallback only when generation is unavailable or fails.
- NPC battles advance without the player and can be joined before resolution.
- Observe first, then join; rendering an event does not reserve or pause it.
- Simulation while Radar is visible and resumed, with bounded deterministic catch-up on return. No background service or background LLM activity.
- Preserve existing location, compass, encounter, Dex, recruitment, and battle behavior except where this plan explicitly extends it.
- Names/distances are optional overhead labels. Interactions appear on their participants; tapping opens full-screen conversation for chat or starts spectating for battle.
- Wilds can initiate chats and hostile attacks without target agreement. Player attacks use the active owned Digimon and physical range checks.

### Additional confirmed requirement

**Conversations can trigger battles from their actual context.** This applies to NPC↔NPC conversations, player-joined group conversations, and the existing player↔wild Radar chat. A quarrel, insult, territorial dispute, rivalry, or mutually agreed sparring challenge can lead into combat. Mentioning battles, quoting an insult, joking, or declining a challenge must not automatically start one.

### Proposed resolutions

- **Fight alongside one wild:** active owned Digimon + selected wild versus the other wild, a true **2v1**.
- **Fight both wilds:** active owned Digimon + a second eligible owned Digimon versus both wilds, a true **2v2**. The human trainer is not a combatant. Do not silently substitute 1v2 when a second owned individual is unavailable.
- Freeze the whole ecosystem from committed battle handoff through battle exit. Exclude that interval from catch-up so the freeze is real.
- Observation is allowed at any distance within the loaded Radar region, including outside the current camera FOV. Joining requires every original wild participant to be within the existing 40 m interaction range at commit time.
- Keep NPC-to-NPC affinity and temporary emotional state separate from player trust and recruitment eligibility.
- Retain current fullscreen active-battle behavior. The square exploration viewport and its surrounding controls remain stable during ordinary exploration and observation.

These defaults make the plan executable without further discovery. Product choices that can still be changed are collected in section 12.

## 2. Review findings and corrections

### 2.1 First-person geometry needs correction

The original curve, `40 * sqrt(distanceM / 1000)`, maps 40 m to **8 units**, not 2.5; 350 m maps to approximately **23.66 units**. Its examples cannot be used as written.

The arena manifest's `playableRadius = 8` describes battle movement. The generator builds a floor extending to ±200 units. Exploration does not need to compress the whole 1 km region into the battle movement circle. Use a separate presentation radius, with the existing floor, and hide the battle boundary in FP mode.

The current 2D implementation projects markers relative to a stored origin and subtracts player displacement, but its initial culling checks distance from that origin. A shared geometry layer should calculate distance, culling, and range eligibility from the live player location in both views. Walking far from the initial fix must not hide nearby individuals.

`approachSceneAxis()` is linear interpolation, not angle-aware interpolation. Applying it directly to 359° → 1° would take the long route. Preserve existing compass filtering and use shortest-arc interpolation only if additional camera smoothing is necessary.

### 2.2 Renderer disposal cannot depend on one Boolean

`WorldRadarBattleContent` uses `AnimatedContent`, which can retain outgoing and incoming content during a transition. Changing `fpViewEnabled && !battleActive` does not prove that FP resources have finished detaching before the battle view is created.

Existing scene code distinguishes releasing scene resources from `ModelViewer`'s detach-time Engine destruction. Add an explicit ownership/handoff protocol, including the reverse transition. Treat one live 3D renderer for this Radar flow as a resource-management requirement, not a claim that Filament universally forbids multiple engines.

The original assertion that battery load is unchanged is unsupported: compass reuse avoids another sensor pipeline, but rendering, movement, and LLM requests add work. Measure the combined cost.

### 2.3 Current battle chrome differs from older plans

`RadarScreen` reports fullscreen battle state, hides its top banner/tabs, and expands the battle viewport. Older guidance describing an always-visible World shell and always-square battle viewport is stale. Preserve the current behavior; do not restore that older layout during this feature.

### 2.4 NPC relationships must not accidentally raise player trust

`WorldRepository.observeMood()` currently reads `WildRelationship.trust`. `applyWildMoodDelta()` updates that trust and mirrors it into `WorldSpawn.mood`. Feeding NPC conversations through this method would improve or damage a wild individual's relationship with the player even when the player did nothing.

Use dedicated pair affinity and a separate ecosystem emotional value. Keep existing trust/contact/recruitment rules intact. Direct player participation may update player trust only through an explicit, participant-specific outcome policy.

### 2.5 Deterministic decisions do not make LLM output deterministic

Live LLM responses are external inputs, not reproducible seeded decisions. Seeds alone do not establish replayability: tick index, stable participant ordering, input history, rules version, and committed outcomes matter. Context-driven battle proposals therefore need to be validated and persisted as inputs before they change simulation state.

Let dialogue influence social intent, including a battle challenge; let deterministic rules validate that intent and apply its mechanical consequences. Catch-up and live execution can match mechanical state for the same recorded inputs, including accepted dialogue intents. They cannot promise the same events as an uninterrupted live run that generated additional, unrecorded conversations, identical generated prose, or reconstruction of the player's unknown movement while the app was closed.

At a 1.5 s tick, 30 minutes is **1,200 ticks**, not approximately 600. Catch-up must address gaps shorter than 60 s too, otherwise repeated brief closes change simulation behavior.

### 2.6 Existing battle and dialogue seams need real extensions

- `OfflineBattleSessionViewModel.start()` already accepts `List<OfflineBattleParticipant>` for allies and opponents. The Radar caller still constructs one of each; there is no public `List<Fighter>` integration contract to reuse.
- `worldRadarBattleParticipant()` builds one wild participant and should remain a reusable per-individual adapter. Generalize orchestration and persistence around it.
- `OfflineBattleParticipant` has maximum HP but no starting-HP field; `BattleSimulator` initializes fighters at maximum health. Injured NPC handoff requires an explicit initial-condition path through presentation, factory, and simulator.
- `recordRadarBattleResult()` records one owned character versus one spawn. Calling it once per enemy would risk duplicate player results and incorrect allied-wild deletion. Existing deletion-based duplicate protection also does not establish exactly-once handling for every outcome.
- `generateFarmReply()` takes an owned character ID and returns one speaker's public turn. It is a useful provider/persona pattern, not a drop-in wild multi-speaker API.
- Room is currently version **24**. A 24→25 migration is appropriate only if still current when implementation starts; migration registration in `DefaultAppContainer` and schema export are both required.
- Japanese resources belong in `res/values-ja/strings.xml`, not `values/ja/strings.xml`.

## 3. Shared architecture and ownership

### Authoritative world session

Introduce an application-scoped `WorldEcosystemCoordinator` following the ownership pattern of `FarmSessionCoordinator`, with a single writer for a Radar session. UI renderers observe immutable snapshots and submit commands; they never mutate world state independently.

Responsibilities:

1. Own lifecycle leases, pause reasons, checkpointing, and catch-up.
2. Serialize movement, spawn admission/expiry, event transitions, player joins, and result application.
3. Publish `StateFlow<EcosystemSnapshot>` with revision, simulation tick, individual positions, movement/pose state, event summaries, and eligibility inputs.
4. Persist significant transitions transactionally; batch movement checkpoints approximately every 10 s and on suspension.
5. Coordinate with `WorldRepository` for existing spawn generation, recruitment, and card deletion. A second mutex in an unrelated repository is not sufficient protection: all conflicting writes need one transaction/command contract.

`EcosystemSnapshot` uses stable `individualId` and `eventId`. It carries geographic state, not pixels or Filament entities. Position interpolation belongs in presentation and never changes eligibility.

### Simulation scope

Use the same approximately 1 km local region regardless of 2D zoom, FP heading, visible marker count, or selected overlay. Reconcile region membership on accepted location updates, with a small boundary margin to prevent repeated admission/removal near the edge. Retain event participants until their event ends.

Current population targets are 8 nearby plus 12 outer individuals, with a repository-wide active cap of 60. Preserve those limits initially. Dens change placement and neighbor relationships, not total population. Apply the proposed cap of two autonomous chats and one autonomous battle to the simulation region, not to whichever entities happen to be on screen.

### Lifecycle contract

- Radar selected and app resumed: tick and render normally.
- 2D ↔ FP: keep the same session lease and snapshot; only renderer ownership changes.
- Observe overlay: continue simulation. No extra lease or new engine.
- Switch to Digifarm, leave Radar, or background the app: stop ticking/new LLM requests and checkpoint. Release FP resources when leaving; stop frame callbacks when backgrounded.
- One-wild player chat: use the same regular private conversation/history/controller as the Talk button, importing the initiating greeting once. Multi-wild chat: use the full-screen group layout with its public transcript. Resumed chat leases retain the shared clock while suspending unrelated autonomous dialogue/player interruptions.
- Player battle or preparation after event reservation: pause the ecosystem with a recorded reason. Sensor/location refresh and all Radar mutation controls follow the existing battle suspension behavior.
- Back/close overlay: return to the same view, heading, and selection where still valid. Battle exit restores the selected exploration mode after renderer handoff.

## 4. Persistence, identity, and invariants

### WorldSpawn extensions

Add home latitude/longitude, wander target latitude/longitude, movement state, anchor radius in meters, den ID, and a separate ecosystem emotion value. Retain `latitude`/`longitude` as live geographic coordinates.

Existing rows start at their current coordinates as home, with `HOME` movement, no target/den, and neutral ecosystem emotion. Existing `individualId`, player trust, private chat, and recruitment state remain unchanged. A home lasts for the life of its wild spawn; persistent cross-visit territorial populations are outside the initial scope.

### Pair bonds

Add `WildPairBond`, keyed by canonically sorted `(individualA, individualB)`. Disallow self-pairs and index both identities. Store affinity in −100..100, chat count, A-wins/B-wins/draw counts, last interaction tick/time, and cooldown information. Generic `duelsWon/duelsLost` fields on an unordered pair are ambiguous.

Den membership belongs to individuals/dens. Derive “same den” instead of maintaining a second contradictory membership label in every bond. Seed bonds only for denmates/nearby neighbors, not every pair of retained individuals.

### Events and participation

Persist `WorldInteraction` with ID, type, lifecycle state, seed, rules version, logical start/next-action/end ticks, revision, social context, allowed join policies, and outcome/application status.

Use participant rows with individual identity, spawn reference where applicable, role, side, and battle initial-state/checkpoint data. Use normalized message rows with speaker identity, sequence, text source, and request ID rather than rewriting a growing transcript JSON blob on every line.

Persist dialogue intent records with source conversation/event ID, source message IDs/revision, initiator, target individuals, intent type, reason, proposed stakes, and accepted/rejected/expired status. Link a battle to its originating conversation through `sourceConversationId`/`parentInteractionId`; keep the original transcript instead of overwriting a CHAT row into a BATTLE and losing its context.

Persist a unique active-participation claim per wild individual. This enforces the one-event-per-individual rule transactionally, including direct player encounters. Conversation membership and combat teams are related but distinct: the player is a chat author/trainer, while only Digimon occupy combat slots.

Recommended event lifecycle:

`PROPOSED → ACTIVE → RESOLVING → ENDED`

Additional transitions: `ACTIVE → RESERVED → PLAYER_CONTROLLED → RESOLVING`, and cancellation from an appropriate nonterminal state. Joinability is derived eligibility, not a competing `JOINABLE` phase. A reservation has a timeout and expected revision; a failed handoff returns to the saved event state without rerolling.

### Checkpoints and durable result commits

Persist session seed, rules version, tick index, last checkpoint timestamp, fractional tick remainder, simulated region, and pause reasons/intervals. Record external commands/accepted inputs needed to reproduce a bounded replay.

Persist a battle/event result ledger keyed by session/event ID. Apply participant updates, removals, bond deltas, owned-character battle records, and the applied marker in one transaction. Repeated UI callbacks, rotation, or retries must not award or apply anything twice.

### Expiry and cleanup

- Existing expiry deletion, cap eviction, recruitment, debug spawning, and card deletion must respect active claims.
- Pin participants for bounded event duration; never extend expiry forever through a stuck event or repeated retries.
- On expiry/cancellation, release claims and apply a finite grace policy. Recruitment removes the individual from wild simulation and cancels any stale pending commands.
- Deleting a card/individual cancels affected events and cleans invalid references. Ordinary spawn expiry must not delete that individual's existing player chat/contact history.
- Bound retained ended events/messages and stale pair bonds; proposed initial retention is the latest 100 local ended events for at most 7 days. This is a storage policy, not a promise of permanent event history.

### Migration acceptance

Migration backfills anchors and neutral new fields, preserves identities/history/recruits, creates required indexes/foreign keys, and registers the upgrade path. Test an existing version-24 database and fresh creation. Do not use a destructive reset to implement this feature.

## 5. Movement and social simulation

### Fixed-step engine

Start with a **1.5 s logical tick**. Use a documented stable seed mixer over session seed, individual/event ID, decision purpose, and tick. Sort candidate IDs before decisions. Do not depend on collection iteration order, renderer frame rate, wall-clock scheduling, or a String XOR expression.

Persist or derive all random choices from that contract. A rules-version change must have an explicit resume/migration policy instead of silently replaying old checkpoints with new formulas.

### Movement and dens

- Group compatible new spawns into small dens of 2–4 where current biome/card filters and population limits allow; isolated spawns remain valid.
- Spawn selection continues to use existing allowed-card/species rules. Cluster members must still fit the existing nearby/outer population regions.
- Start with proposed anchor radii around 15–40 m and walking speed around 0.5 m/s, then tune visually. These are real-world meters/seconds, not Digifarm scene units.
- Wander within the home radius and return home. Personality affects the frequency and choice of movement, not arbitrary stat bonuses.
- NPC events are proposed only when participants can meet within their territory limits. They approach a shared meeting point before activation; they do not teleport across the map. Cancel a proposal if participants become ineligible or cannot meet within its timeout.
- Curiosity may produce a short local approach toward the latest valid player position. It must stay inside the territory and does not restore the deprecated follow-player feature.
- Wilds can open conversation directly. Hostile player-directed encounters show an attack windup and can hand off automatically into a local 1v1; voluntary NPC-team intervention still uses team selection.

### Decision policy

Evaluate social opportunities on roughly 30–90 s horizons with per-pair cooldowns. Use existing persisted personality, distance, pair affinity, den familiarity, and ecosystem emotion. Suggested starting thresholds remain affinity ≥30 for friendly chat and ≤−30 for rivalry; they are tunable defaults.

These thresholds are starting opportunities, not the only battle trigger. A conversation may create a validated hostile challenge even before affinity reaches −30, or a friendly sparring challenge at high affinity. Reconciliation or refusal can cancel an uncommitted challenge. Apply intent-derived bond/emotion changes through bounded rules, not arbitrary model-generated numeric deltas.

Repeated duels need cooldowns and diminishing bond changes. NPC outcomes never grant owned-character vitals, XP, battle count, or recruitment progress merely because the player watched or reopened Radar.

Both views render the same motion and event state. As explicitly requested, every FP Radar sprite billboards toward the player regardless of activity; this is a presentation rule independent of simulated travel or social targets. Other 3D scenes retain their existing facing strategies.

## 6. First-person presentation

### Shared coordinates and compression

Extract pure geometry shared by both views: accepted player fix → north/east offsets in meters → true distance/bearing → presentation-specific projection. Handle zero distance, longitude wrapping, invalid inputs, and high-latitude stability. Eligibility uses geographic distance before visual compression.

Use a proposed bounded, linear-near/logarithmic-far curve:

```text
d = clamp(distanceM, 0, 1000)
u(d) = d / 10                                      for d <= 40
u(d) = 4 + 36 * ln(1 + (d - 40) / 160) / ln(7)    for d > 40
```

This maps 0 m → 0 units, 40 m → 4 units, approximately 350 m → 23.94 units, and 1,000 m → 40 units. It is continuous, monotonic, and avoids the original square-root curve's steep slope near zero. The constants remain presentation tuning values; the 40 m interaction rule does not change with them.

Choose a single coordinate convention: north = −Z, east = +X, up = +Y; camera forward at heading θ is `(sin θ, 0, −cos θ)`. A spawn at bearing β is placed at `(u * sin β, groundY, −u * cos β)`. Apply heading to the camera once, not to both the camera and the world. Verify the reused asset alignment against this convention.

Projected offsets/labels, hit testing, edge indicators, and culling use one camera projection. Offscreen entities remain simulated. At zero/near-zero distance, use a safe display offset or selected-item card to avoid clipping into the camera without modifying the entity's actual distance.

### Environment and camera

- Reuse the current arena asset with FP-only node visibility/root configuration. Hide `Sphere001` and the battle boundary; keep a clear floor/grid and selectively retain distant voxel scenery if it reads well from eye level.
- Do not modify the shared GLB/manifest to satisfy FP composition. Its battle appearance remains intact.
- Use a lower fixed eye height (0.85 scene units) and a −12° initial pitch; vertical drag/Look controls allow −60°…+45°. Choose FOV/near/far planes from viewport aspect and filtered bounds; device framing remains part of acceptance.
- Reuse `rememberWorldCompass()` and its status/declination behavior. Avoid unnecessary additional smoothing; if needed, interpolate shortest angular deltas.
- On missing compass heading, show a clearly labeled north-facing preview and retain 2D access. On missing/stale location, show a locating state and disable joining until a valid fix is available.
- On renderer/asset failure, release resources and return to usable 2D with a retry action. Do not silently introduce a new procedural environment as a scope expansion.

### Sprites, HUD, and performance

Reuse sprite extrusion, idle/walk frames, stage scaling, scene profile, and contact-shadow conventions. Use a bounded CPU cache and load at most one new sprite GL asset per frame. Compass changes and movement ticks must not rebuild sprite GLBs.

GPU handles belong to their owning Engine and cannot be reused after teardown. Decode/build assets off the UI thread where supported, upload/mutate scene objects on the renderer's owning thread, and reject stale asynchronous results after release.

Keep the existing purple surfaces, cyan active states, and angular framing. Show compass status, real distance, selected individual/event, and concise activity markers. Prioritize selected/nearby labels; group colliding edge indicators and provide an accessible nearby/event list for crowded or offscreen selections.

Do not publish every projected coordinate as independent Compose state every frame. Bound HUD projection updates and publish one coherent projection snapshot; scene transforms can interpolate at render rate. Initial performance target: stable 30 FPS on the representative supported phone with approximately 20 local individuals, then measure whether higher frame rates fit the power budget.

### Renderer handoff

Use explicit states such as `EXPLORING → PREPARING → RELEASING → BATTLE_LOADING → BATTLE → RESTORING`:

1. Validate and reserve the encounter/event; capture selected exploration mode and committed battle inputs.
2. Pause ecosystem updates and block duplicate taps.
3. Stop FP frame callbacks/uploads, release dependent resources, detach the old view, and acknowledge Engine ownership release.
4. Display a lightweight Compose/loading transition while the battle renderer is created. Keep the simulator paused until assets/scene are ready.
5. At battle exit, release/detach the battle renderer before creating FP again.
6. On failure/cancel, unwind reservation and pause reasons exactly once; restore 2D or FP without losing world state.

Crossfade a placeholder or retained static frame if desired, not two live scenes. Cover rapid toggles, app backgrounding during either release, and switching to/from Digifarm. Audit actual teardown behavior for the pinned Filament dependency before adding an acknowledgment; copying `releaseScene()` alone is insufficient evidence.

## 7. Events, dialogue, and joining

### Shared interaction surface

Tap an ordinary eligible individual → existing encounter actions. Long-press → Dex. Tap a chat participant/marker → the full-screen regular/group conversation keyed by event ID. Tap a battle participant/marker → spectate in the scene with the battle panel.

The observer shows participants, real distance, event state, transcript or battle HP/round summary, and a fixed-position Join action with a clear reason when unavailable. Observing never creates a second 3D renderer. In FP, show social poses in the existing scene; in 2D, show coordinated markers/animation.

Observation range is the loaded region, independent of zoom/FOV. Joining rechecks current event revision, claims, participant state, player location freshness, 40 m range to each wild, owned-character eligibility, and selected team. A stale observation can show “interaction ended” instead of launching an obsolete fight.

### NPC dialogue

Build `NpcDialogueService` using existing provider settings, localization, persona/lorebook construction, and public-chat isolation. Resolve wild personas by persisted individual identity and card/species data; do not fabricate owned-character rows to call `generateFarmReply()`.

- One request returns one short exchange: one attributed line from each wild speaker. Limit an autonomous conversation to six NPC utterances total, initially three exchanges, within a five-minute event lifetime.
- Request a validated structure with allowed speaker IDs, ordered lines, bounded text lengths, and an optional contextual intent (`NONE`, `CHALLENGE_BATTLE`, `ACCEPT_CHALLENGE`, `DECLINE_CHALLENGE`, or `DEESCALATE`). Intents identify their speaker, targets, and supporting message IDs. The model proposes intent; the orchestrator validates eligibility and owns the transition. The model cannot invent participants, award rewards, or assign combat teams.
- Prefer a shared provider budget where available; at minimum serialize Radar autonomous requests and enforce at most one NPC exchange every 15 s across Radar, not per event. Give explicit player messages priority and use a bounded queue.
- Snapshot event ID/revision/request sequence before sending. Accept a reply only if that event and speakers are still valid; discard late/stale replies after joining, cancellation, suspension, or expiry.
- Commit messages once using request IDs. Do not hold the simulation mutex during network I/O.
- Handle no key, offline, timeout, provider failure/rate limit, and malformed output with bounded retries and the authored personality fallback. Mark text provenance internally.
- Autonomous NPC emotion/bond outcomes come from bounded deterministic rules applied to accepted intents. Strip hidden mood markers if returned; they cannot directly alter outcomes or the player's trust. Persist each accepted intent before applying it so replay never needs to ask the model the same question again.

### Joined chat

Reserve a conversation on join, cancel/ignore any outstanding autonomous exchange for the old revision, then attach a player composer to its existing transcript. The two original wild speakers retain identities and history.

Proposed default: the human player authors messages; an active Digimon may be a clearly identified third Digimon speaker when explicitly selected, never text impersonating the human. Budget and validate each responding speaker. Persist event conversation separately from private 1:1 history, and never inject private player conversations into NPC prompts.

Joining requires proximity once; leaving the accepted range later disables further new participation with an explicit state while leaving observation/history available. New input revalidates eligibility. Recruitment/contact effects, if any, must identify which wild is affected and use existing player-interaction rules, never the autonomous NPC bond updater.

### Context-driven chat → battle

This is a core feature, not a later optional enhancement. Keep one transition policy shared by autonomous NPC chat, joined group chat, and existing 1:1 player↔wild chat.

1. **Understand the exchange:** provide the recent attributed transcript, existing challenge state, personalities, relationships, and any already-stated agreement/refusal to the dialogue request. Return an optional structured intent together with the response, avoiding a second LLM classifier call per message. Existing 1:1 chat generation needs this structured result too, while keeping user-visible text clean.
2. **Validate the context:** require existing speaker/target identities and a current transcript revision. Distinguish friendly sparring, a direct challenge, and an escalating dispute from hypothetical/quoted speech. Text is untrusted narrative, not an executable command; validate structured proposals, ignore invented participants, and never rely on keyword matching alone.
3. **Show the challenge:** append a persisted system event and display who challenged whom and why. Explain whether it is sparring or hostility. Allow refusal/de-escalation before commitment. NPC response intent plus deterministic personality/relationship rules decide whether an NPC challenge becomes a standoff; an explicit refusal must not be mislabeled as agreement.
4. **Commit the correct transition:** transfer NPC claims atomically into a linked proposed battle, respecting cap, range, cooldowns, and expiry. A hostile attack does not require target acceptance; sparring remains an invitation. Show the standoff and advance automatically. If capacity is unavailable, keep a short-lived pending proposal and revalidate; stale dialogue cannot launch a much later fight.
5. **Distinguish wild attack from voluntary participation:** a local hostile attack hands off automatically to the active owned Digimon's 1v1, through the same proximity, roster, reservation, renderer-release, and freeze gates. Friendly sparring and voluntary NPC intervention use Accept/Decline and team preview; the two-wild options remain explicit 2v1/2v2. A model does not impersonate the human's agreement. Sparring does not introduce another reward system or a hostile affinity penalty.
6. **Carry context forward:** battle introduction and post-battle dialogue reference the accepted reason and outcome. Preserve transcript/participant identity and apply consequences once. Cancellation/refusal stays in the conversation; battle completion returns to an available follow-up conversation/summary with the previous exploration mode. If a defeated spawn was removed, its archived context remains readable without allowing another encounter with that expired spawn.

For existing `WorldChatScreen`, add an event-aware challenge controller and a navigation payload containing the persisted challenge ID. On acceptance, return to/activate the existing Radar battle owner, which revalidates the challenge; do not construct a second battle engine inside the chat screen. A remote Digiline contact or expired/out-of-range wild can discuss a challenge but cannot bypass Radar's physical encounter requirements. Keep private 1:1 source messages private; a public battle marker gets only a suitable brief challenge reason, not the private transcript.

Offline authored dialogue can offer an explicit, context-authored sparring/challenge action. Do not pretend to understand arbitrary free-text escalation without a functioning model; keep the normal manual Battle action available. Recaps are read-only and can never produce fresh challenge intents.

Examples for acceptance: a territorial dispute between two NPCs becomes an observable fight; friendly NPCs agree to spar without requiring negative affinity; a wild challenges the player after an argument and the player accepts a 1v1; a joke about fighting produces no challenge; “I do not want to fight” keeps the chat out of combat; the same returned intent cannot start two battles.

### NPC battle and player handoff

Use an approximately five-second visible standoff, then an abstract round display at roughly 4.5–6 s intervals. Fix the schedule in logical ticks so live and catch-up use identical timing.

**Proposed resolution strategy:** run the existing battle simulator without a renderer for autonomous NPC combat, advancing bounded fixed steps and sampling a coarse round/HP summary for Radar. This retains the current stat mappings, personality behavior, and damage rules instead of adding a separate affinity-based damage formula. Affinity chooses whether/whom to fight, not hidden combat buffs. Profile this before promising the full catch-up budget; resource pressure must reduce scheduled event density or batch work, not silently substitute different results.

Persist enough to recover that bounded battle: seed, immutable participant definitions/rules version, elapsed simulation time, and required external inputs; either replay from those inputs to the last committed round or provide a validated simulator checkpoint. A few HP values alone are not a full resumable simulator state.

The player join is an explicit transition into a new concrete team battle:

- Side with A: `[active owned, wild A]` versus `[wild B]`.
- Side with B: `[active owned, wild B]` versus `[wild A]`.
- Challenge both: `[active owned, selected second owned]` versus `[wild A, wild B]`.
- Social context determines which actions are offered and suggests an ally; the player sees teams before committing. Two distinct owned individuals are mandatory for 2v2.
- Carry each wild's remaining HP into the new battle, with the same effective maximum and identity. Add an initial-condition API throughout the battle pipeline; never fake injury by reducing maximum HP.
- Only living, nonresolving participants can be joined. Preserve remaining energy where available. Proposed initial scope resets tactical positions, targets, cooldowns, and transient statuses at the team-formation transition; document this as a new encounter phase, not seamless restoration of the prior simulator.
- Owned participants enter using normal current preparation rules. Allied wilds remain autonomous; selecting them for observation must not unlock owned inventory/vitals operations or persistence paths that require a `UserCharacter`.
- Preserve manual camera focus, a stable opening target, and fixed battle action positions for larger teams.

### Results and interruption policy

One completed joined battle records one result for each eligible owned participant, regardless of the number of enemies. Apply the existing win/loss policy, with no records for draw/abandonment, through the new idempotent transaction.

Proposed wild outcome policy: allied victory removes defeated enemy wild encounters; allied wilds remain if still eligible, including defeated allies. Owned defeat/draw/abandonment preserves wild encounters, matching the conservative existing retention policy. Apply social deltas once and return retained wilds home with a cooldown; encounter damage is temporary rather than a new permanent wild-health/recovery system.

An unjoined NPC battle changes only NPC bonds/emotion/cooldowns; retain both wilds until normal expiry. No passive owned rewards. A player abandonment cancels the joined event without resuming the old autonomous fight. A preparation failure before battle ownership commits restores the reserved NPC event unchanged.

On process death, do not claim the current in-memory player simulator can resume. If a durable terminal outcome exists, apply it once; otherwise mark the committed joined event interrupted, grant no result, release claims, and return surviving encounters to the explicit interruption policy. Preserve committed transcripts and existing private chat.

## 8. Catch-up and reproducibility

Catch-up is a foundation of the engine, not a final polish task. Implement its clock/persistence contract before adding autonomous outcomes.

1. Immediately render the last checkpoint with an “updating world” state. Disable mutation/join actions until reconciliation finishes; static observation is safe.
2. Compute nonnegative eligible elapsed time, excluding recorded player-battle freezes and preserving sub-tick remainder. Handle device clock rollback without negative steps or duplicate outcomes.
3. For gaps up to 30 minutes, advance every logical tick on a worker in bounded/yielding batches; 30 minutes requires 1,200 ecosystem ticks. Use exactly the same decision and battle advancement functions as live mode.
4. Do not issue LLM calls during replay. Replay already-committed contextual intents exactly once; do not invent new dialogue-driven escalation for conversations that were never generated. NPC social/battle decisions that do not require new dialogue may still advance under the seeded rules. Store event facts and dialogue placeholders. Lazily request a single bounded, read-only recap only when the player opens a retained event, using the same rate budget and deduplication keys. Label it as a recap, not a verbatim conversation that occurred offscreen.
5. Player-nearby decision input is absent while away. Continue NPC-only behavior; do not infer a travel path from two GPS samples. Reconcile the new valid fix and local region after catch-up.
6. For gaps over 30 minutes, simulate at most the first capped interval, then apply a documented dormancy boundary: close stale events, expire spawns against wall time, release claims, and reconcile population at the new location. Do not grant outcomes for unsimulated time. Rebase the checkpoint so repeated reopenings cannot replay the same backlog.
7. Commit reconciled state and publish a new coherent revision before enabling interactions or regular ticks. Queue/revalidate late location inputs and reject late UI commands against the old revision.

**Determinism guarantee:** same persisted seed, rules version, ordered inputs (including accepted contextual intents), and eligible elapsed ticks yield the same positions, events, HP/outcomes, bonds, and emotion, whether processed live or in batches. Newly generated text/intents and unknown offscreen player actions are outside that guarantee. A run with live conversations may develop differently from a run with no generated conversations. Capped dormancy intentionally differs from an uninterrupted multi-hour run.

## 9. UX states and accessibility

- Keep mode controls, event actions, and battle commands at stable positions; do not let incoming dialogue or sprite loading move primary buttons.
- Save view preference at World/session scope so toggling the Digifarm tab and restoring the Activity does not unintentionally reset it. Save selection by ID; never serialize renderer handles.
- Retain 2D pinch zoom only in 2D. FP supports tap/long-press and vertical look drag, with accessible pitch controls.
- Provide explicit states for loading world, no imported/eligible cards, no local spawns, denied location, approximate/stale fix, compass calibration/unavailable, asset failure, missing active Digimon, missing second teammate, expired event, out-of-range join, and unavailable LLM.
- Ensure 48 dp touch targets, readable large-font layouts, semantic labels for entities/events, and a Compose-accessible selection list. Do not rely on raw `TextureView` hit tests or color alone for access.
- Use `motionEnabled()` for transitions, pulse, and decorative motion. Reduced motion preserves meaningful compass direction and world state while removing unnecessary easing/bobbing/flashes.
- Localize new controls, statuses, team summaries, fallback lines, and accessibility text in default English, `values-pt-rBR`, and `values-ja`.
- Chat starts at the latest line, follows only while the reader is already at the bottom, and does not pull someone away from older text.

## 10. Implementation sequence and file boundaries

Paths below are relative to `app/src/main/java/com/github/nacabaro/vbhelper/` unless explicitly qualified. Names for new files are proposed boundaries, not mandatory class proliferation.

### Phase 1 — Shared contracts, migration, and lifecycle

Extract location geometry and snapshot/command contracts. Define identities, event/result state machines, pause reasons, deterministic clock/seeds, persistence, and migration. Integrate spawn expiry/cap/recruitment guards and catch-up ordering before movement begins.

Likely touchpoints: `world/WorldRepository.kt`, `world/RadarBattleFlow.kt`, `domain/world/WorldSpawn.kt`, `daos/WorldSpawnDao.kt`, `dtos/WorldDtos.kt`, `database/AppDatabase.kt`, `di/DefaultAppContainer.kt`, schema exports, and new `world/ecosystem/` coordinator/model/DAO code.

**Exit criteria:** migration preserves existing data; duplicate leases do not duplicate ticks; 2D retains current encounters and spawn targets; expiry cannot remove a claimed participant; suspended intervals replay once under fake-clock tests.

### Phase 2 — Movement and dens in 2D

Add bounded wandering, local den placement, persisted pair bonds, interpolation, and shared live-location culling. Validate movement/replay without introducing network requests or new battle behavior yet.

Likely new components: `WorldEcosystemEngine`, `EcosystemDecisionPlanner`, and `RadarWorldGeometry`; wire snapshots into `screens/worldScreen/WorldScreen.kt`.

**Exit criteria:** anchors remain stable, movement stays in meters within bounds, mode-independent snapshots replay correctly, and no NPC-only changes alter player trust.

### Phase 3 — FP renderer and ordinary encounter parity

Add `RadarFirstPersonViewport.kt`, `RadarFirstPersonSceneView.kt`, and pure FP projection/compression helpers; add `HybridSceneKind.RADAR_FP` as needed. Implement explicit scene ownership with existing Radar battle UI before exposing the toggle.

Reuse `rendering/HybridSceneProfile.kt`, sprite extrusion/facing, and motion helpers. Limit refactoring of Digifarm/battle rendering to lifecycle code genuinely needed for safe ownership; do not turn this into a general renderer rewrite.

**Exit criteria:** same nearby individuals/ranges in both views, correct cardinal bearings, ordinary 1v1 encounter works from FP, safe handoff in both directions, accessible selection, 2D recovery on FP failure, and no duplicated scene ownership during transitions.

### Phase 4 — NPC chat, observation, and joined conversation

Add `InteractionOrchestrator`, `chat/NpcDialogueService`, interaction/message/intent persistence, and `WorldInteractionOverlay.kt`. Reuse current chat UI components and persona/provider settings with an event-aware controller. Extend existing `WorldChatScreen` and its controller with structured challenge handling while retaining their 1:1 routes/history. Define the shared chat-to-battle contract and refusal/de-escalation UX now; activate battle transitions when Phase 5's ownership/result handling is complete.

**Exit criteria:** NPC conversations appear in both views, player can observe remotely and join locally, transcripts survive view changes, all replies/intents have valid speaker identity, challenges are grounded in their source conversation, late LLM responses are harmless, and offline fallback/catch-up issue no background LLM requests or invented contextual intents. Do not expose an actionable battle challenge until the complete transition is available.

### Phase 5 — Autonomous battle and multi-participant joining

Add a renderer-free NPC battle adapter, bounded persistence/replay, initial-condition support through `OfflineBattleParticipant`, `TrainingBattlePresentation`, `TrainingBattleFactory`, and `BattleSimulator`, plus event-aware team preparation and result commit.

Reuse `OfflineBattleSessionViewModel`, `WorldRadarBattleContent`, existing participant picker patterns, and `RadarBattleFlow` adapters. Replace Radar's single spawn/owned-character result bookkeeping with explicit participant roles. This phase depends on the renderer handoff and event claims already passing.

**Exit criteria:** direct/context-triggered 1v1 and true 2v1/2v2 teams; NPC chat can become an observable battle; owned Digimon enter only after player acceptance; conversation reasons and history survive the transition; preserved wild HP; no duplicate owned results or friendly-wild deletion by enemy-result logic; reliable rejection of stale events; defined cancellation/process-death recovery.

### Phase 6 — Integrated hardening and device verification

Complete localization, accessibility, retention cleanup, workload budgets, long-gap catch-up, error states, and regression coverage across all phases. Tune motion, label density, event frequency, camera composition, and performance from device evidence.

**Exit criteria:** the complete acceptance checklist below passes; build evidence and actual device evidence are reported separately. Any device installation requires the user's authorization at implementation time.

## 11. Acceptance and verification plan

### Automated behavior checks

- Geometry: cardinal bearings, shortest heading wrap, zero distance, longitude boundary, compression continuity/monotonicity, 40 m boundary, camera projection, behind-camera clipping, and live-location culling after walking from initial origin.
- Determinism: compare live and batched runs with identical input logs, fixed seeds, and absent-player intervals. Include 1 s, 59 s, 60 s, 30 min, over-cap gaps, clock rollback, process recreation, and rules-version mismatch policy.
- Persistence: migration from the current schema; preserved player history/trust/recruitment; participant constraints; cleanup and card deletion; one atomic outcome across duplicate callbacks and crashes around commit.
- Lifecycle: two observers/rapid recompositions still mean one loop; toggle does not reset ticks/events; background cancels new LLM work; player-battle freeze is excluded from later catch-up.
- Social: movement bounds, pair ordering, event caps independent of FOV/zoom, cooldowns, failed proposals, no simultaneous events per individual, and no passive player trust/reward changes.
- Dialogue: invalid speakers, malformed/oversized output, no key, timeouts, 429s, cancellation, stale replies, request deduplication, bounded queue/rate, and recap generation only on demand.
- Contextual escalation: hostile dispute, friendly sparring, direct challenge, refusal, de-escalation, joking/quoted/hypothetical violence, expired proposals, full battle capacity, remote/private chat, and duplicate intents. Replay accepted intents without generation, preserve source transcript links, and verify recaps cannot trigger combat.
- Battle: existing 1v1 regression; 2v1 and 2v2 role mapping; no duplicate owned identity; injured HP survives preparation; zero-HP NPCs cannot join; allied wilds never access owned-character operations; win/loss/draw/abandon/interruption apply the specified effects once.
- Race cases: join versus expiry, join versus last damage round, recruit versus active event, card deletion versus asset load, location update versus catch-up, and failure while switching renderers.

Retain and extend relevant existing suites: `CompassHeadingTest`, `RadarBattleFlowTest`, `RadarArenaAssetTest`, `OfflineArenaGeometryTest`, and `OfflineTrainingBattleScreenTest` (including `world-radar-battle-viewport`). Add focused ecosystem, migration, initial-condition, and renderer-handoff coverage as those seams are implemented.

### Build checks

Run focused tests first, then the affected module checks:

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest
.\gradlew.bat :app:kspDebugKotlin :app:compileDebugKotlin :app:assembleDebug
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin
python scripts/test-radar-ecosystem-db.py
python scripts/test-world-interactions-db.py
python scripts/test-living-world-migrations.py
git diff --check
```

Compile-only Android test checks do not execute instrumentation. Execute migration/UI tests on an authorized device/emulator, and use applicable lint tasks during implementation.

### Required runtime evidence

- Real magnetometer device: rotate through cardinal directions and 359°/0°, verify labels and FP alignment, calibration/unavailable status, and approximate-location behavior.
- Walk beyond the initial map origin and across the 40 m boundary; check both views against the same live positions.
- Observe/join chat and battle from each mode, including overlaps, offscreen events, and denied joins. Trigger a battle naturally from NPC conversation and from player↔wild chat; accept, decline, and de-escalate challenges, and verify the reason is visible before and after combat.
- Repeat FP ↔ 2D ↔ battle and Radar ↔ Digifarm transitions; background/foreground during loading; rotate/recreate/kill the process. Instrument renderer counts and ensure frame callbacks/resources stop as intended.
- Verify dark theme, large fonts, TalkBack selection, reduced motion, stable controls, and multi-participant camera focus.
- Measure frame time, memory after repeated transitions, battery/thermal behavior, network call count, and worst-case catch-up with the population/event caps. Record actual device, workload, and results rather than claiming unchanged battery load.

The original planning review used source/document checks only. Implementation/build evidence for the initial Phase 1 slice is recorded above; runtime/device acceptance is still pending.

## 12. Remaining product choices and scope limits

The proposed defaults above resolve the source plans' open questions. Before implementing the relevant phase, revisit only if the desired experience differs:

1. **2v2 roster:** this plan requires a second owned Digimon; explicit 1v2 could be added later as a separate option.
2. **Battle freeze:** this plan freezes all NPC activity during a player battle, including the corresponding catch-up interval.
3. **Joined chat voice:** human-authored messages are the default; including the active Digimon as another autonomous speaker is optional and must be explicit in the UI.
4. **Unjoined losers:** retain them with social consequences/cooldown rather than despawning them merely because of an unseen fight.
5. **Team handoff fidelity:** preserve HP/energy while resetting temporary tactical state at team formation. Seamless continuation with every projectile/status/cooldown is a larger follow-up.

Deferred: permanent territories across spawn lifetimes, inter-region migration, background services, offscreen LLM conversations, multiplayer/server simulation, new art assets, phone-camera AR, tilt/orbit controls, and a new battle balance ruleset.

The finished feature is complete when the player can discover the same living individuals in either Radar view, observe their interactions, see conversations lead naturally into context-driven battles, join eligible chats or correctly composed battles, and return without lost identity, duplicated outcomes, renderer overlap, or unbounded background work.
