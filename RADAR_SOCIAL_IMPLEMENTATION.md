# Personality-driven Radar interactions

## Implementation status

Source implementation and authorized automated verification are complete. The full
app suite reports **466 cases, zero failures, and three existing fixture-dependent
skips** (463 successful executions). Debug assembly, lint with the existing baseline,
and Android-test compilation passed. Host SQLite verification passed 31 checks across
social migration, interaction integrity, living-world migration, and chat memory.

Bugbot is unavailable in this harness (`Unknown agent type: bugbot`), so the code
review was performed manually. Android runtime, renderer, live GPS, and actual model
response quality have not been exercised on a device.

## Radar behavior

- `DigimonSocialProfile` derives semantic priorities for all 16 persisted types,
  plus small identity-stable variation. Initiative, curiosity, warmth, playfulness,
  challenge willingness, territoriality, patience, loyalty, empathy, boundaries,
  and training preference influence decisions. Stage/attribute changes do not
  reroll the individual's baseline voice.
- `WorldSocialPlanner` chooses a motive before generating dialogue. Player and
  peer selection use seeded weighted priorities rather than a fixed first-ID
  winner or an absolute-affinity-first ordering.
- Available motives include curious approaches, recognition, company, teasing,
  observations, shared activities, check-ins, reconciliation, keeping distance,
  territorial warnings, friendly practice invitations, and hostile confrontation.
  Attributes are not treated as moral alignments.
- Neutral neighboring pairs can get acquainted. Familiarity, attachment, novelty,
  actual emotion, previous meetings, and per-individual/pair cooldowns affect the
  next interaction. Quiet participation still counts as a recorded meeting.
- Player attacks are not restricted to Reckless individuals or deeply negative
  emotion. They remain situational and weighted against social alternatives.
  An active owned partner and actual geographic proximity are required for a
  player battle handoff. An injured recorded partner increases check-in interest.
- A nearby individual can physically approach within its home territory. Pending
  approaches activate only after the actor reaches interaction range; they do not
  teleport or become unlimited player-following behavior.
- Unanswered player greetings expire after 24 logical ticks (36 seconds). The
  global initiation cooldown is 40 ticks (60 seconds), with longer individual
  cooldowns derived from personality. Pending approaches have a finite deadline.
- Keeping distance ends the invitation and sets a bounded movement destination.
  Territorial warnings stop the approach rather than continuing to crowd the player.
- NPC invitations can be declined. Accepted shared exploration uses a target in
  both territories. Friendly NPC sparring requires a recorded invitation and a
  reciprocal acceptance, including when localized authored fallback dialogue is used.
  Shared exploration requires the invited participant's explicit typed acceptance;
  speaking or discussing the idea alone does not start an activity. A stated refusal
  overrides contradictory acceptance metadata.
- Final public speech remains displayable briefly after an encounter closes;
  terminated events do not remain interactable simply to preserve their bubbles.

## Speech and memory

- Localized opening banks use the actual motive, personality, variation, and recent
  opening keys. Authored openings are immediately available in English, Portuguese,
  and Japanese. Provider failures no longer collapse the roster into three styles.
  Peer openings are also delivered when a live physical meeting activates, while
  replay records meeting facts without inventing offline speech.
- Public generation receives the actual audience, encounter motive, recipient
  relationship, current emotion, prior public meetings, and relevant public memories.
  Reserved participants may observe without a compulsory line.
- Structured Radar exchange contracts are also supplied as the final response
  instruction, preventing the generic single-reply reminder from overriding the
  multi-participant JSON format. Recaps use their own factual response instruction.
- `WorldSocialMemory` stores bounded public encounter facts, outcomes, partner
  identity/name, and opening keys. It never copies private chat transcripts or
  private wager terms. Player-private context and peer-public context have different
  retrieval paths.
- Hostile/friendly battles produce individual outcome perspectives. Winners and
  losers can react differently; same-team cooperation is not recorded as rivalry.
  Player battles also contribute public outcome facts to the individual record;
  private conversation and wager terms remain outside that public memory.
- Trust, temporary emotion, and familiarity are distinct inputs. In player chat
  turns every message moves trust coherently: agreement/engagement up,
  refusals slightly down, insults strongly down, praise/care scaled by
  personality, and a same-sign model marker is honored without ever zeroing a
  real signal. Generated cheerful wording alone still cannot manufacture gains.
  Past the 75 contact threshold, positive chat gains drop to a quarter
  (minimum +1) while losses stay full, so quests remain the compelling path to
  maximum trust. Peer-to-peer appraisal is unchanged.
  In peer conversations, attributed insults, teasing, and actual invitations can
  have different appraisals for the two participants rather than universally
  strengthening their relationship.

## Changes outside Radar

### Shared chat and individual voice

- The individual voice hash uses permanent identity and personality, while current
  species/stage remain factual context. The profile gives voice energy, register,
  playfulness, and initiative a personality-based semantic baseline.
- Owned and wild prompts use actual relationship/history context. Returning wild
  visitors are not universally described as first-time strangers.
- Reactions to losses, injuries, recovery, milestones, and recruitment no longer
  prescribe the same emotional response for every individual.
- Shared reply reminders allow restrained answers, disagreement, refusal, and
  natural endings instead of advancing every turn or repeatedly asking questions.

### Digifarm

- `FarmBehaviorPolicy` replaces the universal rotating activity sequence with
  personality/need-based choices and commitment windows.
- Need decay uses differences of absolute time buckets, preserving fractional
  decay across short foreground updates. Offline needs remain bounded, while
  position advances by a visible step rather than teleporting.
- Socializing has a persisted partner target. Partner selection considers directed
  relationships, familiarity, curiosity, proximity, availability, and recent contact.
- Sleeping/eating or committed residents are not automatically forced into an approach.
- Farm map and group share one conversation owner. Resumed lifecycle leases control
  generation; suspension cancels pending jobs, and membership/epoch checks prevent
  obsolete replies after transfer, removal, or departure.
  Request registration checks the original epoch, and publication rechecks it inside
  the database transaction. An old request cannot start after the farm is reopened.
- Approach/no-op attempts do not consume API-generation slots. Requests are bounded,
  with localized fallback speech when generation is unavailable.
- Responder selection, invitation receptivity, public memory relevance, and directed
  relationship appraisal use the shared social profiles.
- Accepted shared NPC activities revalidate both residents' membership, proximity,
  needs, and availability before starting play or training.
- Assistant-initiated owned conversations can appear in Digiline before the first
  user message; this makes existing individual reactions visible as threads.

## Persistence

Room schema **33** includes the additive **32 → 33** migration:

- Nullable `WorldInteraction.socialContextJson` for the application-selected motive.
- `WorldSocialMemory` and `IndividualSocialState`, tied to permanent identity.
- Nullable `FarmResident.socialTargetId`.
- Existing personality labels/timestamps are retained; legacy version metadata is
  adopted without generating replacement personality assignments.

Ecosystem rules **3** rebase earlier logical checkpoints without inventing retroactive
dialogue. The generated Room schema is in
`app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/33.json`.

## Verification commands and results

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.domain.personality.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" --tests "com.github.nacabaro.vbhelper.digifarm.*" --tests "com.github.nacabaro.vbhelper.chat.*"
.\gradlew.bat test :app:lintDebug :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=2
py -3 -B scripts/test-world-social-db.py
py -3 -B scripts/test-world-interactions-db.py
py -3 -B scripts/test-living-world-migrations.py
py -3 -B scripts/test-world-chat-memory-db.py
```

The initial combined build exceeded its terminal time limit after unit tests and
APK assembly. Remaining lint/assembly/Android-compilation tasks were rerun with a
longer limit and finished successfully. Android fixture compilation also exposed
and resolved a mixed-number SQL argument inference error.

- Pure tests cover semantic initiative/challenge distributions, balanced hostile
  choices, no-partner behavior, order independence, neutral neighbors, recognition,
  cooldowns, localized opening diversity, and stable voices across evolution.
- Dialogue tests cover explicit recipient acceptance, refusal precedence, preserved
  readable speech with invalid optional metadata, and separation from battle consent.
- Farm tests cover foreground/batched need equivalence, personality-sensitive
  activities, immediate needs, overlapping leases, request cancellation, and stale
  epochs across reopening.
- Seven social SQLite cases execute production migration/DAO SQL and compare the
  result with Room's exported schema. They cover retained types/timestamps/history/
  trust/clock, scoped retrieval, retention, foreign-key behavior, final-speech privacy,
  and connection-restored social state.
- Existing host suites passed 16 interaction checks, three living-world migration
  checks, and five chat-memory checks.
- Android cases for physical approach → automatic battle handoff, short greeting
  expiry, and discussion versus explicit refusal were added and compiled. They were
  not executed. Existing migration-test builders include the 32 → 33 migration.

Manual review fixes include explicit activity acceptance, lifecycle request/publication
fencing, the farm social-target DTO mapping, and migration registration. Build artifact:
`app/build/outputs/apk/debug/app-debug.apk`.
