# Tamer Arena

Battles → Tamer Arena opens a local franchise roster, preparation, cups and records.
Fights use the training Colosseum and the existing command deck/cinematics.

## Content and availability

- `app/src/main/assets/tamers.tsv` is a versioned, source-linked roster across anime,
  manga and games. Original/dub aliases are searchable; distinct continuities have
  distinct IDs. The catalog includes long-tail characters even when their art is
  not currently available.
- `TamerCanonicalPartners` records character-specific stage forms with source URLs.
  Generic species trees and the old whole-line "canon" hint are not used. For a
  genuinely undocumented stage, a documented form is retained with scaled stats,
  attacks and supplies; its original visual form/size is preserved and labeled.
  A documented form whose artwork is missing is unavailable, not replaced by an
  assumed evolution. Associated Digimon and guest owners remain explicit.
- `ArenaSpeciesIndex` joins physical card-slot metadata with existing sprite poses
  and extracted stats, then adds imported species with usable local sprites. The
  encyclopedia in species bucket `0` is deliberately not treated as DIM slot art.
- Available opponents sort first. Reload refreshes newly imported/deleted cards.
  Missing-stage/secondary artwork prevents starting that particular team; no
  unrelated sprite or duplicate form of the same partner fills the slot.
- `TamerBattleLoadouts` supplies three ordinary moves, an innate special, stage
  forms and curated eligible fusions. Species names do not imply elemental damage
  matchups; the shared attribute triangle remains the battle matchup model.

## Trainer and item rules

`TamerTrainerController` is simulation-owned and seeded. It changes strategy,
coordinates focus, uses specials, decides Blast timing and chooses items based on
actual HP/energy/ailments. Casual/Normal/Expert alter reaction/timing quality.

Each side owns 2 recovery, 2 energy and 1 remedy, shared by its two Digimon in 2×2.
Healing/restoration scale with stage. The team item cooldown is five seconds;
reservations and execution revalidation prevent overlapping stock consumption.
Player commands cannot control opponents. Enemy finishers use the shared timeline
and report actual committed damage independently of the player's statistics.

Trainer policies, item cooldown and strict fusion eligibility are opt-in. Existing
training and Radar defaults/checkpoint behavior retain their former rule paths.

## Safe matches and cups

- 1×1 and 2×2 exhibitions; 8- and 16-entry single-elimination cups with franchise
  filters. Registration freezes participants, stage, loadout, result-art identities,
  rules and seeds. No match writes owned Digimon stats or persistent item stock.
- NPC bracket bouts use the same simulator, technique definitions and trainer
  controllers on a background dispatcher.
- Teams recover and session supplies reset each round. A drawn player bout has one
  30-second, quarter-HP, item-free tiebreak. Remaining proportional team HP decides
  a further draw; exact equality uses a fixed seeded draw.
- Brackets, records and reward receipts persist in Room schema 40. Activity
  recreation retains the live battle ViewModel. A process restart resumes the cup
  and restarts an unfinished bout with its original frozen setup/seed.
- Results, bracket advancement and Bit credits settle in one transaction. Repeated
  settlement returns the existing receipt. Championship trophies are represented
  by completed player-won cups, with a separate idempotent championship bonus.

## Canonical corrections (policy revision 2)

The roster-wide audit is documented in `TAMER_CANON_AUDIT.md`. Tsurugi uses Victory
Greymon, Next's Yuu uses Z'd Garurumon, Takumi Hiiragi uses Gaioumon, Erika uses
Hudiemon, and other ReArise/Survive/Liberator/Seekers continuities have their own
documented histories. Mirei, Nokia and Hideto field their canonical combined
partner at Mega tier and receive independent guests for 2×2. Their absorbed
components cannot reappear in another slot, including mixed-stage teams.

Legacy unfinished cups refresh NPC teams under the corrected policy before their
next bout. The registered player, bracket, match seeds and settled rewards are
preserved; missing canonical artwork blocks that bout with an actionable message.

## Verification

Checks are batched after integration. `TamerArenaTest` covers team selection,
aliases, item isolation, opposing finishers and replayable trainer decisions;
`TamerRosterAssetsTest` exercises existing asset-backed teams and combat definitions.
`ArenaPersistenceTest` covers concurrent reward settlement and transaction rollback
against actual Room on Android. Native rendering/touch acceptance requires Android
execution and is reported separately from compilation.
