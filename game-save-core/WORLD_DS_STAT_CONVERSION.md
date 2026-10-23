# World DS Stat Conversion Policy

Authored policy id `world-ds-stat-v1` (integer `VERSION = 1`), implemented in
`WorldDsStatConversion.kt` and applied by `WorldDsRecordCodec.kt`,
`WorldDsPartyEdits.insertGeneratedRecord`, and the app send path. Both the native record and the
app state remain stored exactly; this table only defines the projection between them.

## Bands

| Meaning | App side | World DS side |
| --- | --- | --- |
| Health / base+training | 1..1000 | `maxHp`/`hp` 10..999999 |
| Combat (attack/defense) | 1..1000 | 5..999 |
| MP | derived energy | `maxMp`/`mp` 5..9999 |
| Spirit | derived, mood-scaled | 5..999 (input 1..1250) |
| Speed | derived | 5..999 |
| Vital points | 0..9999 | level 1..99 |
| Aptitude | derived | 0..99 |
| Mood | 0..100 | spirit factor 75..125% |

`scale(v, a..b, c..d) = c + round((v-a) * (d-c) / (b-a))`, clamped to the target band.

## Forward (app -> record)

| Record field | Source |
| --- | --- |
| `level` | `1 + round(vitalPoints * 98 / 9999)` |
| `maxLevel` (new records) | `clamp(0..99, 10 + stage*10 + min(55, experience/25) + min(10, generation))`, `experience = trophies + wins + losses + ageInDays + rank*10`; retained records preserve native `maxLevel` in `1..999` |
| `maxHp`/`hp` | `scale(baseHp + trainingHp, 1..1000 -> 10..999999)`; generated `hp = maxHp` |
| `attack` | `scale(baseAp + trainingAp, 1..1000 -> 5..999)` |
| `defense` | `scale(baseBp + trainingBp, 1..1000 -> 5..999)` |
| `maxMp`/`mp` | `scale(energy, 1..1000 -> 5..9999)`, `energy = weighted(hp 30%, bp 45%, ap 25%)`; generated `mp = maxMp` |
| `spirit` | `scale(weighted(hp 20%, bp 30%, ap 50%) * moodFactor, 1..1250 -> 5..999)` |
| `speed` | `scale(weighted(hp 20%, bp 10%, ap 60%), 1..1000 -> 5..999)` |
| `exp[0..2]` | `trainingHp`, `trainingBp`, `trainingAp` |
| `exp[3]` | `vitalPoints` |
| `exp[4..6]` | `trophies`, `wins`, `losses` |
| `exp[7]` | `activityLevel` (each channel capped at 99999) |

`ageInDays`, `generation` and `rank` feed `aptitude` only and are not persisted into DS channels.

## Reverse (record -> app)

| App field | Source |
| --- | --- |
| `trainingHp/Ap/Bp` | `unscale(maxHp/attack/defense) - base`, clamped 0..999 |
| `vitalPoints` | `round((level-1) * 9999 / 98)` |
| `mood` | inverted from `spirit`/derived spirit base, clamped 0..100 |
| `trophies`, `wins`, `losses` | `exp[4]`, `exp[5]`, `exp[6]` |
| `activityLevel` | `exp[7]` |

`derivedMp`, `derivedSpeed` and `aptitude` are reported for display but have no independent app
field; the exact native value stays in the archived snapshot.

## Return delta (existing individual)

`applyDelta` adds native deltas only to direct app fields: level -> `vitalPoints`, `maxHp` ->
`trainingHp`, `attack` -> `trainingAp`, `defense` -> `trainingBp`, `exp[4..7]` -> trophies / wins /
losses / activity. Mood, MP, spirit, speed and aptitude are never back-written from derived values.

## Send selection

A send uses the exact retained bytes when the app partner still matches its `NativeAppProjection`
checkpoint and the retained record has a complete generated species payload. Otherwise it synthesizes
the stat windows from current app state, reusing the retained 384-byte record as a seed so unknown
bytes (flags, ancestry, existing history) survive.

- Pre-default records are detected by `WorldDsRecordCodec.needsSpeciesRepair` (missing species fields,
  resistances or current species history). Sending one regenerates the record and replaces the same
  native identity in every populated save copy instead of treating it as already away.
- A retained seed keeps its local identity and native species. This prevents an app send from
  overwriting a DS evolution; `insertRetainedRecord` inserts the same identity only when the
  destination no longer contains it.
- A record with no retained seed uses the app species and receives a fresh identity from
  `insertGeneratedRecord`.
- Party insertion and storage admission use only visible slots 0..7. Table slots 8 and 9 are
  reserved and never receive an ordinary app-sent Digimon, so a sent individual cannot disappear
  from the game's party screen.
- Missing species payload fields are filled from the ROM-derived `WorldDsRecordDefaults` asset
  (extracted from `/dat/dm`): `unk1A`, record `unk50`, first ability and source, four skills,
  seven history resistances, history `unk50`, and the species stage/history stage.
- History flags at `history+0x00` are written exactly by stage: `0` -> `0x1C`, `1` -> `0x18`,
  `2` -> `0x10`, and armor/ultimate/mega -> `0`.
- History forms are stage-indexed at `history+0x04`. A new record sets the current stage slot to
  the species and fills earlier slots from the `/dat/sk` previous-species lineage (earliest first).
  A matching seeded record keeps its existing forms. The current-stage marker is also written at
  `history+0x4E` and `history+0x4F` is cleared.
- Seeded records keep real `maxLevel` up to 999. New records use the app-derived value at `0x52`;
  `expToNext` comes from the three extracted level curves.
- `unk70=33` and `unk108` bit `0x40` are written when absent; `history+0x50` comes from the
  species default for generated records and is preserved otherwise.

## Receive

- First materialization of a World DS record seeds the new app partner from `toApp` (mood, vitals,
  trophies, wins/losses, activity and training), guarded so the seed is not counted as app progress.
- A return (`applyWorldDsReturn`) adds `applyDelta` of the newest snapshot against the projection's
  snapshot to the existing app fields, then moves the projection baseline to the new snapshot.
  Re-applying the same snapshot is a no-op.
- A return also resolves the native species through the reviewed mapping or the game's species table,
  updating the existing card's definition, the individual's species name, transformation history,
  canonical mapping, receipt and projection stage/base. `materializeRetainedMappings` reconciles the
  latest snapshot for previously returned individuals at startup; `USER_CONFIRMED` mappings win.

## Limits and policy

- Duplicate World DS species names resolve to the lowest species id (`WorldDsSpeciesCrosswalk`); a
  name absent from the game's table is blocked, never substituted with another species.
- Generated records are structurally validated and read back, but in-game acceptance of a
  synthesized record is not yet verified on hardware.
- Unknown native bytes outside the write windows are never modified.