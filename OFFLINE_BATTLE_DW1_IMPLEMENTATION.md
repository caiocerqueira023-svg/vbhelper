# Attribute-only DW1-inspired offline combat

Ruleset **3**, catalog **3**. Radar, training, and renderer-free NPC fights use the
shared simulator. Virus → Data → Vaccine → Virus remains the only type matchup;
there is no elemental resistance table or added elemental identity.

## Implemented behavior

- `BattleAiProfile` separates encounter preferences from persisted personality
  and the trainer's current strategy. Neutral wild templates use stable species
  identity, three ordinary moves, weighted selection, targeting policies, bounded
  opening buff preference, and automatic enemy specials.
  Training-arena asset participants use the same stable neutral loadout templates,
  with the arena's existing trainer-driven special policy.
- Decisions read targets/startups together. Movement intents are committed after
  every fighter has read the same positions. A selected move remains committed
  while approaching its range. Initial targets use distance rather than an ID prefix.
- Target switching uses a margin and personality stickiness. Trainer focus takes
  precedence. Vulnerability and status policies read the candidate's actual state.
- Readiness starts at 100, bottoms out at -155, and regenerates independently of
  MP, animation phases, cooldowns, and observation delays. Cost is total move power,
  limited to 15–255 (40 for zero-power support), with explicit overrides available.
  Pressure waits for positive readiness; balanced waits for the move's cost capped
  at 100; full waits for 100. Conservative strategies maintain extra separation.
  Bracelet-derived tempo sets regeneration; shock halves it.
- Positioning uses entry/retention bands. A 1.5-second no-progress window or a
  six-second total positioning deadline replans instead of spending resources or
  attacking outside physical range. Explicit orders fail and release reservations.
- Status effects have proc chance, periodic percent-max-HP ticks, refresh policy,
  exclusivity groups, and protection. Multi-hit techniques roll statuses once per
  contacted target, rather than once per hit. Control expiration grants a one-second
  recovery window. Cleanse removes ailments while retaining buffs.
- Poison and burn tick for a seeded 1–3% maximum HP each second. Burn subtracts
  `damage / 4`; freeze then adds `damage / 4`. They share a persistent-ailment group
  with shock. These conditions do not imply elemental attack types.
- Buff allowance prevents endless self-buffing and active buffs are not repeatedly
  refreshed autonomously. Combined attack/defense/movement bonuses are bounded;
  trainer-requested buffs remain available. The first damaging loadout move is the signature
  technique, receiving a bounded additive 5% power bonus (1–12 compact units).
- Counter is a separate, non-selectable reactive technique. An in-range melee
  impact while guarding can trigger it if MP/readiness/cooldown allow. It consumes
  25 energy, has a 3.5-second cooldown, uses reduced defense, and cannot recursively
  trigger another Counter. Both sides use the same mechanical eligibility rules.
- Special readiness combines existing attack/received-hit gains with slow passive
  progress. Enemy profiles can automatically prepare a ready special; partner
  specials stay trainer commands. Existing startup/active/recovery states separate
  preparation from readiness and resolution.
- Damage variance, criticals, status rolls, accuracy, Counter, AI choices, and
  movement use seeded purpose-specific streams. Technique accuracy is supported
  independently of geometric contact; catalog defaults remain 100% accuracy.

## Damage profiles

`BattleDamageResolver` is the central attribute-only resolver. The active adapted
profile retains the compact-stat ratio curve, uses integer 90–110% variance,
attributes, criticals, signature power, guarded reduction, and ordered burn/freeze
rounding. Damage caps at 9999; zero-power effects do not inflict minimum damage.

`Dw1DamageFormula` provides assembly-derived comparison arithmetic:

```text
difference = clamp(offense - defense, -500, 500)
base = power + truncate(difference * power / 500)
normalDamage = clamp(truncate(base * variance / 100), 1, 9999)
finisherBase = offense + power
if mashCharge > 40: finisherBase = truncate(finisherBase * mashCharge / 40)
finisherDamage = clamp(truncate(finisherBase * variance / 100), 1, 9999)
```

Reference mode expects PS1-scaled inputs and assumes neutral specialty factor.
The active bracelet profile is an authored adaptation, not a claim of full PS1 or
latest-Vice emulation. Timings, proc chances, encounter templates, and stat budgets
are explicitly VBHelper values. No PS1 command-delay bug or weakness-score bug is ported.

## Checkpoint compatibility

`NpcBattleAdapter.encode` stores an envelope inside the existing `definitionsJson`
column: format version, catalog version, complete battle configuration, and immutable
combatant definitions. It needs no Room schema migration. Recovery validates the
versions/seed and replays exactly the recorded number of 34ms steps.

Existing array-format checkpoints use the frozen v2 catalog and legacy simulator
branches. Missing newly added Gson fields are normalized before constructing a
combatant. Future/unknown versions are rejected instead of silently using current rules.

## Diagnostics and verification

Snapshots expose readiness/required readiness, remaining buffs, encounter profile,
target rationale, positioning replans, technique use, guard/Counter counts, target
changes, incapacitated time, waiting time, and average distance/readiness. The debug
balance runner exports these alongside ruleset/catalog/formula and paired-seed results.

Focused command:

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.NpcBattle*Test" --tests "com.github.nacabaro.vbhelper.world.RadarBattleFlowTest"
```

The matrix runs 1,000 seeds with both side assignments at each of six stages
(12,000 battles). Acceptance checks mirror win rates, side differences, timeouts,
duration tails, and incapacitation. Double KOs are reported separately from timeouts.
Device-renderer validation remains a separate runtime check.

### Verified on 2026-10-03

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=2
```

- Full app suite: 429 cases, 426 successful executions, three existing private-fixture
  skips, zero failures. NFC/DIM reader tests also passed through the root `test` task.
- Debug assembly, lint (with the existing baseline), and Android-test compilation passed.
- Six-stage mirror matrix: 12,000 battles; first-fighter win rate 47.53–51.58% among
  decided battles; paired side-swap difference 0 percentage points at every stage;
  no timeouts. Median duration 38.28–40.70 seconds; p95 48.65–51.41 seconds.
- Double KOs account for 2.5–5.0% by stage. These are genuine draws, not timeouts;
  the matrix records them without changing simultaneous-hit resolution to force a winner.
- Existing +5% HP/BP/AP regression: stronger profile won 102/190 decided battles
  (53.68%). The tank/striker regression measured tank wins at 86/190 (45.26%).
- Android renderer and physical input tests were compiled, not executed.

## Research sources

- [Original damage assembly](https://github.com/Vicen04/Dw1DataAndPatches/blob/6003d3bba3ecc2c86956dcf25dc5eb9f36abae87/Digimon%20Code/BTL/CalculateMovementDamage.asm)
- [Early Vice 2.2 changes](https://github.com/Vicen04/Dw1DataAndPatches/blob/6003d3bba3ecc2c86956dcf25dc5eb9f36abae87/Hack%20data/2.2%20changes%20%28still%20in%20progress%29.asm)
- [Original readiness](https://github.com/SydMontague/DW1-Code/blob/313a90116b8bb9060c382228abf48ecae6c06c67/BTL_REL.BIN/increaseSpeedBuffer.asm)
- [Original battle AI/movement](https://github.com/SydMontague/DW1-Code/blob/313a90116b8bb9060c382228abf48ecae6c06c67/BTL_REL.BIN/battle_rel.asm)

Post-battle aging, permanent stat progression, and technique learning are governed
by existing world/training progression, rather than being inferred from PS1 values.
