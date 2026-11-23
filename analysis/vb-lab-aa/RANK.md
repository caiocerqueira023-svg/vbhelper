# Rank de Tamer — evidências com o conteúdo completo

## O que existe no cliente (assets locais agora)

- **16 ícones de rank** (`tamerrankicon_0` … `tamerrankicon_15`), sprites brancos/alpha extraídos do
  bundle `03cfc5dc0ea9c67645c5847849bca460` → `assets/rank_icons/` (contact sheet
  `contacts/rank_icons.png`). São escudos com marcas crescentes (base → topo), ou seja, o cliente
  conhece **16 tiers visuais** de rank.
- **UI de rank/ranking**: sprites e prefabs nos bundles de Home/log:
  `131a58d36b6c8d5fe4d838de87266536` (`rank`, `RankNum`, `rank_123_parts`, `rank_p_parts`,
  `button_rank`), `17b2782a71113fd61f1fc31bcdc8c066` (versão EN), `25b7124fe6d9fec6259ef62ee0a20e20`,
  e `2711b38851d2cf6e8ad1ffe98783ba19` (`ranking`, `ranks`, `Text_ranking`, `anatanoranking`).
- **Animações de rank**: `rankup_anime` (`rankup_anime.prefab`), `rank_reward_anime_arena`,
  `rank_reward_anime_raid` (e variantes `_en`). O patcher edita o bundle `2711b388…` para
  “Offline - no ranking” — confirmando que **ranking/placement é server-side** e o modo offline
  remove isso.
- **Strings/metadata**: `TamerRank`, `GetRanking`, `NewMyRanking`, `BRankingMain:rankList.count over`,
  `popup_window/content/ranking`, campos `DigimonApp.Home.RankRewardDialog`, `DigimonApp.Home.ExpMcParts.rankupf`
  (dumps antigos: `data/strings.tsv`, `data/fields.tsv`).
- **Ranking de Arena/Raid**: as mesmas telas de ranking têm abas/props separadas para arena e raid
  (rank rewards específicos: `rank_reward_anime_arena`, `rank_reward_anime_raid`).

## Novas evidências (assets completos + páginas estáticas)

- Ajuda oficial do app (dentro de `vb_lab_static.zip`):
  - `page/help/help_spmission_deck[_en].html`: “ganhando EXP o **Tamer Level** sobe; ao atingir certos
    níveis, o **Tamer Rank** sobe também” → **rank é derivado de marcos de nível**, não uma segunda
    escada de EXP.
  - `page/help/help_user[_en].html`: “Tamer Rank — exibe seu Tamer Rank atual”.
- **Nomes de ranks não existem no cliente**: nem nos 5.972 literais do metadata, nem nas páginas
  estáticas, nem no catálogo. Só existem os 16 ícones `tamerrankicon_0..15`; a UI oficial mostra
  emblema + “Tamer Rank”. Qualquer nome de tier seria invenção — no plano do Tamers Lab usamos apenas
  “Tamer Rank” + índice visual.
- Assets de apoio extraídos: `tamerrank_bg_0` (fundo/faixa), `rank_ui` (sprites de ranking/arena),
  `rank_123_parts`/`rank_p_parts`/`RankNum` (números/badges), animações `rankup_anime`,
  `rank_reward_anime_arena/raid`.

## O que NÃO está no cliente

- Pontos, thresholds de EXP/troféus, temporadas e placement são calculados no servidor
  (`GetRanking`/`NewMyRanking`); o APK só tem a apresentação. A doc anterior (`RANK_SYSTEM.md`, na
  pasta de análise original) detalha o fluxo; o conteúdo novo confirma os 16 tiers e a UI.

## Sugestão para o Tamers Lab

`TamerRank` offline pode mapear 16 tiers nos mesmos ícones (copiar `assets/rank_icons/` para
`drawable-nodpi`), com animação de rank-up no padrão do `rankup_anime` (fade/scale + badge).
