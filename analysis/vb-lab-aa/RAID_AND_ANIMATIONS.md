# Raid battles + animações (conteúdo completo)

## Raid — assets

- **Bosses/ilustrações**: `assets/raid/` — sprites `raid001…raid015` (JP) e `raidxxx_en` (EN),
  incluindo `raid001_old`/`raid002_old` (variantes). Contact sheet `contacts/raid_sample.png`.
- **UI de raid**: prefabs nos bundles `0df0e2667ddb05d42013560dafbc4b8e` / `3321a555981172afb2ce763940bc4fae`:
  `Raid3DAnim`, `Raid_farstATK`, `VS_opning_Raid`, `ch_start_Raid`, `raid_ticket`, `raid_ticket_no`,
  `raid_free`, `raid_battle`, `raid_defeat`, `raid_close`, `Text_Raid`…
- **Páginas estáticas** (`vb_lab_static.zip`): `page/battle_raid/` (134 arquivos) com artes de cada
  raid (`img/raid001.jpg…`, `title_raidbattle.png`, `raid_hint`), páginas de info/reward em JP/EN;
  `page/battle_arena/` (33 arquivos) com artes de arena.
- **Animações de raid** (114 clips nos bundles de raid): `Raid_op_atk`, `Raid_op_atk_start(2/3/4)`,
  `VSstart_Raid`, controller `VS_opning_Raid` (opening/VS/attack).
- **Tickets/limites**: UI com `raid_ticket`/`raid_ticket_no`/`raid_free` indica o sistema de tickets
  e raids gratuitos; recompensas têm rank (`rank_reward_anime_raid`).

## Animações — visão geral

- `data/animations.csv`: **2.207 clips** com bundle e contagem de curvas/eventos.
- Destaques: `title_anime1/2/3` (+ `_en`), `Raid_op_atk*`, `VSstart_Raid`, `BG_logo_loop`,
  `BG_logo_start`, `rankup_anime`, `idle`, `fadein`, `faedout`, `loadchara`, `loadingtext`.
- Observação: nos bundles Addressables o typetree do clip não traz `m_AnimationClipSettings`, então
  `duration` fica vazia nesse CSV; as durações locais (title_anime 10 s, homebg_anime 20 s, etc.)
  já estão documentadas na análise local.

## Sugestões para o Tamers Lab

- Reutilizar os sprites de raid como arte de bosses de raid offline; a abertura `VS_opning_Raid` +
  `Raid_op_atk` dá uma sequência pronta para adaptar (intro → VS → ataque).
