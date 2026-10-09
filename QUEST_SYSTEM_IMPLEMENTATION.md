# Quest system — implementation passes 1–5 (verified host checks)

Status: **implemented and host-verified; on-device execution deferred per owner
instruction not to use adb**. Unit, migration, lint, assembly and Android-test
compilation passed. Real Room instrumentation was compiled but not executed.
Manual Bugbot-style review completed with two correctness fixes; no open
critical findings remain in the reviewed quest paths.

## Gameplay contract

- Unlocked wild Digiline contacts offer Normal quests before maximum trust.
- Normal rewards can increase trust. Maximum trust creates a persistent Recruitment
  invitation instead of automatically moving the Digimon into Storage.
- Every recruitment entry point requires that individual's completed requirements.
  The previous flat 5,000-vitals gate has been replaced.
- A recruitment reward preserves the giver's permanent identity and existing
  personality/chat. Quest completion and recruitment share the Room transaction.
- Offers freeze the template/version, seed, giver, objectives, targets and rewards.
  Acceptance can select a stored partner through the existing Storage picker or an
  explicit natural-language choice. The binding uses permanent identity. Starting a
  Radar battle automatically fields the bound partner (switching the stored active
  selection with a toast notice) when the battle targets that quest's marked opponent,
  or when exactly one active quest needs battle victories or item use; ambiguous
  multi-quest rosters never guess. The switched partner stays active afterwards until
  the player changes it in Storage.
- New accomplishments count only after acceptance and from their registered source.
  Explicit stage/vitals thresholds may use existing local state. Item deliveries
  consume inventory; prose and player claims cannot award progress.
- Up to three Normal quests and one Recruitment quest may be active/ready at once.
  Normal offers have a six-hour per-giver cooldown after declining/finishing them.
  Recruitment can be resumed with the original partner and verified progress.
- Objectives within a phase run in parallel; phases run in strictly increasing order.
  The journal exposes current and locked steps. Earlier battles, watch sessions and
  already-consumed item events cannot be replayed into a later phase.
- New Normal offers can save bounded follow-up branches. A child is a separate
  unaccepted quest, not another turn-in of its completed parent.
- Champion+ recruitment can use watch preparation followed by a challenge, supply
  service or rescue. All routes keep the stage-scaled watch effort bands.

## Initial authored catalogue

`quests/QuestTemplates.kt` retains source quest names, URLs and a rules version.
The generator selects seeded authored templates and binds available imported species
and inventory rows. Selection is weighted by the giver's persisted social profile:
challenge favors contests, training favors demonstrations, patience favors careful
victories, empathy favors supply/help requests, loyalty favors courier work, and
curiosity favors finding/recovery. Every eligible template retains a base weight.
The LLM expresses the saved task in the giver's voice; it does not create executable
requirements or change saved terms.

- Supply favor: adapted from Next Order's Palmon request; hand over existing app items.
- Rival: adapted from Cyber Sleuth's Dream of the Sky; defeat a generated radar individual.
- Patrol: adapted from Search Wanted Hacker; win new radar battles with the bound partner.
- Watch training: adapted from KaiserGreymon's stat trial; earn new watch battles.
- Medicine trial: adapted from Dr. Datamon's Medicine Trial; successfully use an inventory
  recovery pack in a radar battle involving the bound partner.
- Recruitment chain: adapted from KaiserGreymon's recruitment structure, with app
  victories, a partner-stage minimum, and mandatory watch milestones for Champion+.
- Find someone: adapted from Cyber Sleuth's Find the Missing; meet the generated
  individual through an in-range Radar quest interaction.
- Lost property: adapted from Digimon's Property; recover the keepsake from its
  finder, then return the held quest object to the giver through Digiline.
- Courier: adapted from Stingmon's letter to MegaKabuterimon; collect the giver's
  letter, deliver it to the recipient on Radar, receive a reply, then return that reply.
- Rescue: adapted from Time Stranger's A Missing Friend; meet the friend, defeat
  its registered threat with the bound partner, then check back with the same friend.
- Supply round: adapted from Dr. Datamon's Medicine Trial; deliver allocated existing
  inventory supplies to two or three distinct generated recipients. The generator
  spreads recipients across loaded attributes when available and does not allocate
  more units than the offered inventory contains. This is a supply adaptation, not
  a claim that the original type-specific medicines exist in VBHelper.
- Time trial: adapted from MachGaogamon's recruitment challenge; win a marked 1v1
  radar battle within 60 seconds of simulation time, excluding pauses.
- Careful victory: adapted from Paildramon's constrained battle; win a marked 1v1
  battle without any battle-item use and with the bound partner at 40% HP or higher.
  It does not reduce actual watch stats or reproduce the original starting-HP rule.
- Technique trial: adapted from Darkdramon's capability trials; have a registered
  selectable skill equipped, then win a new marked 1v1 match while landing at least
  two damaging hits with that skill using the bound partner. App catalogue techniques
  replace original visual/weapon requirements; no invented elemental or learning
  requirement is used.
- Recruitment challenge: complete watch preparation, then win the stage-scaled
  number of marked 1v1 trials. Patient givers can add item-use and ending-HP conditions.
- Recruitment service: complete watch preparation, then deliver allocated supplies
  to stage-appropriate generated recipients, with delivery count scaled by stage.
- Recruitment rescue: complete watch preparation, meet the friend, defeat the marked
  threat the required number of times, and check back with that same friend.

The new recruitment routes retain the original watch battle/win budgets and compatible
VB trophy milestones. Authored choices use the giver's personality and available
participants/supplies. Lower stages and unavailable route pools use the base template,
so maximum trust still creates an invitation without inventing unsupported tasks.

The ordered story templates use version 2, and the new supply/battle trials use
version 3. Existing saved version-1 objectives retain
their terms and default to phase zero. New multi-step offers have larger registered
rewards than a single favor; battle rewards select recovery, energy or status-remedy
stock. Existing offers are not rerolled when the catalogue changes.
New generation uses version 4 for chain metadata and recruitment variations. Saved
version-1/2/3 instances retain their original objectives, rewards and ordering.

Watch milestones are VBHelper adaptations, not claims about the original game quests.
Difficulty bands are provisional balancing values informed by the Humulos evolution
guide. Raw DIM/BEM stages are zero-based: Champion=3, Perfect/English Ultimate=4,
Ultimate/English Mega=5. Higher stages receive larger budgets.

## Follow-up chains

`QuestChains` defines an authored graph bounded to three chapters. Finding someone
can lead to rescuing that exact individual; courier work can branch into supplies
or technique trials; contests can lead to timed or careful victories. The edge is
saved with its parent offer and is never selected by LLM narration.

The scheduler requires completed parent objectives and a reward receipt. It creates
the child in the reward transaction when eligible, or queues the saved edge with a
reason when a partner, target card, supply stock or counter capacity is unavailable.
A deterministic child ID and unique parent index limit every parent to one child.
Refresh/retry prepares the original planned task instead of rerolling it.

Optional follow-ups can coexist with a maximum-trust Recruitment invitation. They
do not bypass or auto-complete recruitment. The journal shows planned chapters,
next-request links, queued reasons, retry and explicit skip actions. Skipping/declining
has no trust penalty and cannot grant completed rewards again. Recruitment ends
unmaterialized wild-contact continuations.

Missing→rescue reuses the saved friend's permanent identity. A friend already in
the collection, including one on the watch, closes that optional edge rather than
creating another copy. Acceptance rejects participants that left the wild representation.

## Persistence and economic changes

Room schema **34** introduced quest instances/objectives, source-deduplicated evidence,
reward receipts, token-bound watch baselines, a wallet, battle stock and reservations.
Schema **35** adds phase/activation metadata, the bound partner's display snapshot,
quest-only objects and per-source phase receipts. Schema **36** adds optional battle
conditions and independent terminal reports, participant-health facts, damaging-hit
counts and trial-attempt reasons. Schema **37** adds parent/child links, bounded depth,
queued/closed continuation metadata and the selected partner's device-family snapshot.
The 33→34, 34→35, 35→36 and 36→37 migrations are authored and registered. Generated
Room schema exports and validation remain deferred with the other checks.

Recruitment provisions a complete VB or BE record shape. BEM species use BE data;
DIM species can use the selected quest partner's saved BE family, otherwise VB.
BE defaults follow scan conversion, including zero training gains and 6,000 minutes
of training time. VB data includes four mission slots and a positive export timer.
The device-family snapshot survives normal export/removal of the partner's Storage row.

`CurrencyRepository` imports the legacy DataStore balance into the Room wallet on
first use with INSERT IGNORE. UI currency flows then observe Room. Quest rewards,
shop purchases and degeneration use transactional wallet updates; ordinary existing
currency rewards use additive updates instead of replacing a previously read balance.

Quest item handover and reward grants use inventory row IDs, not icon/effect IDs.
Item application is enclosed in a transaction so it cannot race a quest delivery
between applying the effect and consuming stock.

Letters, replies and keepsakes are isolated `QuestToken` rows. Their transitions are
LOCKED → HELD → DELIVERED; abandoned/declined quests void remaining objects. They
cannot be traded, sold, applied to a watch or substituted across quest instances.
Object consumption/creation, evidence and step advancement share the action transaction.

## Watch evidence

- The physical export path records the exact outgoing wire counters before sending.
- A new identity-matched NFC import receipt credits its one-time baseline in the
  import transaction. Exact retry imports bypass quest credit.
- Lifetime battle/win deltas exclude app activity already included in the export.
- A watch session's export baseline must be at or after activation of the current
  phase. A return spanning the activation boundary does not guess when its activity
  occurred; the player starts a new session after the step unlocks.
- VB lifetime trophies are used for trophy objectives.
- BE recruitment uses watch battles/wins, avoiding unobservable lifetime PP across
  evolution. The reducer supports conservative same-form trophy/PP deltas, but no
  BE PP-earning template is offered in this first catalogue.
- Counter/form discontinuities preserve previously credited evidence and expose a
  journal notice instead of guessing missing activity.
- VitalWear imports do not restore a physical-watch transfer identity and do not
  credit physical-watch quests.
- Watch adventure-clear, repeated-clear, and cumulative-earned-vitals objectives
  are not enabled. Existing card-wide adventure progress is not individual proof.
- Starting a Radar battle automatically fields the bound quest partner: battles
  against a quest's marked target use that quest's partner, and other battles
  switch only when exactly one active quest needs battle victories or item use.
  The switch is a stored active-partner change with a toast notice; it stays until
  the player changes it in Storage. Covered by `QuestPersistenceTest` cases for
  targeted, single-candidate, ambiguous, missing-partner, and already-active cases.

## Radar and battle integration

Current-step targets materialize their already allocated permanent individual
when a fresh radar population command runs. Targets remain outside normal expiry
and eviction. Unclaimed targets outside the current visible region can be relocated
around the fresh player fix; they are not teleported during a claimed interaction.
Retention includes later references to the same friend, so a rescue friend remains
available for the final visit. Later-step new targets appear after their step unlocks.
Retention ends after the objective/quest no longer needs the target.

`QUEST_INTERACTION` uses the existing Radar command gate, with a fresh fix, current
snapshot, unclaimed target and interaction-range enforcement. Meetings, property
recovery and recipient deliveries are explicit local actions on the encounter sheet.
Remote Digiline claims cannot substitute for them. A completed physical interaction
unlocks that individual's contact without inventing additional trust. The giver's
conversation receives the committed progress fact and optional LLM follow-up.

Player battle results apply quest evidence in the existing result transaction,
before opposing wilds can be removed. Autonomous battles do not count. The roster
must include the bound partner; recruitment victory objectives also enforce the
registered partner-stage minimum from the battle's species snapshot.

The simulator accumulates positive damaging hits by actor, target and technique
independently of its bounded 96-event presentation tail. It adds telemetry, not new
damage, AI or random-draw rules. Terminal snapshot facts are mapped to the reserved
roster's permanent identities and persisted before the view model publishes its
terminal state. Result application also accepts that exact terminal snapshot, so
completion/exit paths can record facts before applying progress.

Conditional wins require all registered constraints: actual victory, team sizes,
simulation-time limit, total item-use limit, bound-partner ending HP, and required
technique hits on the bound opponent. Missing reports do not count as zero item use
or a qualifying victory. Healing, misses, teammate attacks, equipped-but-unused
skills, practice sessions and watch counters cannot substitute for damaging-hit
demonstrations. Each eligible trial attempt retains a concrete failure reason.

Reports and trial evidence are independent of prunable interaction history. Restart
recovery can apply an already recorded terminal outcome when the owned roster still
matches; a genuinely unknown/interrupted battle retains the conservative interruption
policy. Required opponents are retained after a nonqualifying victory so the player
can retry with a new interaction/result.

Recovery, energy and status-remedy rewards are persistent stock, separate from the
existing disposable practice supplies. Radar commitment reserves up to three per
type. Successful consumption is tracked from cumulative remaining counts, not the
bounded recent-event feed. Normal termination returns unused stock exactly once.
Interrupted processes without a recorded terminal outcome have no trustworthy final
simulator snapshot; held stock is conservatively consumed rather than refunded.
Recorded terminal outcomes can settle from their saved use counters. Practice does
not reserve real stock.

## Digiline and UI

- Quests live in a standalone Quests tab under More (also in the navigation rail),
  with Normal/Recruitment filters and current/history views.
- Every quest offer is announced by an intro chat message from the giver. That
  message carries a View quest button deep-linking to the quest's details on the
  Quests tab (`quest-focus` handle). An offline fallback intro is used when the LLM
  is unavailable, so the message and button always appear.
- Private wild chat has no inline quest panel; all accept/track/turn-in actions
  live on the Quests tab. The recruits screen links there as well.
- The journal shows ordered steps, held quest objects and the selected partner.
- Completed parents with queued continuations remain in the current view with
  giver-scoped retry/skip controls. Next-request links choose current or history
  according to the saved child's state.
- Trial requirements and latest attempt reasons are shown in the journal. The Radar
  encounter sheet also shows a marked opponent's current trial conditions.
- Every current recipient has an individually named tracking action, and physical
  supply handovers show the exact item/quantity and current inventory eligibility.
- Technique objectives provide a shortcut to the existing bound-partner loadout
  screen. Existing loadout membership can satisfy the explicit possession step;
  the subsequent victory/hit demonstration must occur after that step unlocks.
- Participant/recipient contacts can read their role and navigate back to the giver;
  they cannot accept, abandon or claim another individual's quest.
- Partner-dependent acceptance reuses the Storage selector, including its imported
  sprites, search and filters. Resumption is scoped to the original individual, and
  VB-only trophy requirements restrict the family selection. The app validates
  remaining counter capacity at acceptance.
- The pending-recruit screen is contact-backed, including invitations whose spawn expired.
- Tracking a rival opens Radar and selects its permanent identity when materialized.
- Items has a persistent Battle items view.
- New controls and status copy have English, Brazilian Portuguese and Japanese resources.
- Offer messages are generated from already persisted facts when opening the contact.
  The saved announcement message ID prevents narration itself from becoming a new offer.
- Structured quest actions must reference a saved giver-scoped quest, its revision,
  and the current player message. Actions execute before their outcome is narrated.
  Models cannot directly modify objectives, progress or rewards.
- Every chat sees the quests it is involved in: givers see their offers with live
  progress, targets/recipients see their explicit recorded role per quest, and bound
  partners see their assigned quests with progress in both private chats and farm
  banter (private partner chats also refresh local stage/vitals/loadout objectives
  first). Quest blocks (missing cards, joined participants, watch discontinuities)
  are explained in plain language with what the player must do; blocked quests are
  never promised and completed quests are never re-accepted or re-rewarded.

## Deferred expansion

BE PP earning/reach and watch adventure advancement are implemented with
identity-matched NFC evidence, same-form continuity for earned PP, absolute
milestones for reach tasks, and per-card next-area baselines. Changed-card
collections use explicit availability states that preserve progress, block
unsafe actions, and keep status/cancellation available. Remaining catalogue
work is further authored content/availability behavior for changed collections.

## Verified evidence (host, no adb)

- `:app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.quests.*"`:
  BUILD SUCCESSFUL, including `QuestWatchEvidenceTest`,
  `QuestBattleConditionsTest`, `QuestRulesTest`, and `QuestDialogueCodecTest`.
- Full `:app:testIntegrityCheckUnitTest` and root `test`: BUILD SUCCESSFUL.
- `:app:lintDebug :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin`:
  BUILD SUCCESSFUL; lint retains advisory/baselined findings only.
- Host SQLite: `scripts/test-quests-db.py` 9/9 OK (33→38 migrations match Room
  38 export, child uniqueness, object isolation, current-step targets, wallet/
  inventory rollback, receipt idempotency, hit scoping, follow-up gating,
  serialization); `test-world-social-db.py` 7/7 OK; `test-digimon-scan-db.py`
  8/8 OK; `test-debug-spawn-db.py` 3/3 OK; `test-evolution-history-db.py`,
  `test-offline-battle-art-db.py`, and `test-dex-evolution-db.py` OK;
  `scripts/test-watch-identity.ps1` OK (69 tests).
- Android instrumentation (`IndividualPersistenceTest`, `QuestPersistenceTest`,
  world persistence): compiled via `compileIntegrityCheckAndroidTestKotlin`
  but not executed per owner instruction not to use adb. `QuestPersistenceTest`
  covers atomic rewards/follow-ups, multi-item rollback, cross-quest object
  rejection, phase-source isolation, duplicate watch receipts, legacy gate
  replacement, BE provisioning, missing-card preservation, and reopen persistence.
- Manual Bugbot-style review: fixed rescue follow-up null handling to queue
  `RELATED_TARGET_UNAVAILABLE` instead of failing the offer transaction, and
  fixed missing recruitment-quest error handling to return a clear quest
  message instead of a generic collection error. No open critical findings.

## Remaining native acceptance (requires device when owner allows)

- Execute `IndividualPersistenceTest`, `QuestPersistenceTest`,
  `WorldInteractionPersistenceTest`, and `WorldEcosystemPersistenceTest` on
  device/emulator.
- Exercise Digiline quest flows, Radar target materialization/tracking,
  NFC VB/BE round trips, locale/theme/accessibility, and TalkBack/manual UI flows.
