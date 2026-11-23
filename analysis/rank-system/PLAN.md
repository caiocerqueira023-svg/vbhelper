# Plano — Sistema de Rank de Tamer no Tamers Lab

> Objetivo: recriar no Tamers Lab um sistema de **Tamer Rank** completo, funcional e offline,
> adaptado à identidade visual e à arquitetura do app, funcionando com **qualquer Digimon**
> (de qualquer save: Vital Bracelet, Vital Battle, DW DS/Dusk, Lost Evolution, storage, cards…)
> e **interconectado com o relógio** via NFC, sem depender de servidor.
>
> Referência de mecânica: VB Lab 1.4.6 (`net.digimon.digibb`). Assets originais extraídos do APK
> completo (`analysis/vb-lab-aa/`). Uso pessoal de preservação; o usuário assume a responsabilidade
> pelo uso dos assets de terceiros.

---

## 1. Sumário executivo

- **Rank é do Tamer, não do Digimon.** O perfil (rank, nível, TXP) é global/local e acumula pontos
  vindos de *qualquer* atividade com *qualquer* Digimon. Espécie/origem só influencia **modificadores
  opcionais e limitados**, nunca cria dados por espécie.
- **Duas escalas ligadas**, como no VB Lab: **Tamer Level** (sobe com EXP) e **Tamer Rank**
  (16 tiers visuais). O app oficial diz na ajuda: ao ganhar EXP o Tamer Level sobe e, ao atingir
  certos níveis, o **Tamer Rank** sobe também. Ou seja: **rank = marcos de nível** (não uma segunda
  escada de EXP). Tudo deriva do acumulador **TXP** por fórmula determinística e configurável.
- **Rank offline não é leaderboard.** O VB Lab obtinha posições/exp do servidor e os nomes dos ranks
  eram server-side (o cliente tem **só os 16 ícones**, sem nomes — confirmado nas páginas de ajuda e
  nos 5.972 literais de string). Aqui: **histórico pessoal + metas** (sem posição fictícia), sem
  download nem "GET_RANKING". Exibição fiel ao original: emblema + "Tamer Rank" + nível.
- **NFC é fonte de TXP, não gate de funcionamento.** Ler/escrever/enviar do relógio pontua; falha de
  NFC nunca consome TXP; rank **não altera bytes do relógio** (firmware não tem campo de rank). Rank
  libera **conteúdo opcional** do app (batalhas elite, missões especiais, itens) e **recompensas**.
- **Reuso máximo**: 16 emblemas originais (`tamerrankicon_0..15`), fundo `tamerrank_bg`, sprites de
  UI/arena/raid para recompensas/banners, animação de rank-up recriada em Compose no estilo do app
  (`rankup_anime` como referência de timing), tema/tipografia/paleta existentes.
- **Zero rede, zero backup obrigatório**: Room + DataStore; export/import compatível com os backups
  atuais (opcional).

**Esforço estimado:** 8–12 dev-days (1 dev), em 7 marcos (M0–M6) abaixo.

> **Status:** implementado (M0–M6). Ver `IMPLEMENTATION.md` para arquivos, hooks e resultados de build/teste.

---

## 2. Escopo

### 2.1 Dentro do escopo
- Modelo de dados local (Room), migração do banco e repositório de rank.
- Fórmula TXP → nível (rank derivado de marcos de nível), 16 tiers, recompensas, gates configuráveis,
  anti-farm/idempotência, histórico/metas pessoais.
- Tela de Rank (perfil, progresso de nível, próximo marco de rank, estatísticas, galeria dos 16
  emblemas, atividade recente), com card na Home ligável/desligável nas configurações.
- Card de rank na Home, entrada no menu/navegação, diálogos de level-up/rank-up/recompensa/gate.
- Integração NFC: eventos de leitura/escrita/transferência/missão especial → TXP.
- Integração com batalhas, quests, dex, itens, storage e mundo como fontes de TXP.
- i18n (en, pt-BR, ja), temas claro/escuro, reduce-motion, acessibilidade e testes.
- Assets e identidade visual adaptados ao estilo Tamers Lab.

### 2.2 Fora do escopo (não-objetivo)
- Servidor, contas, ranking global real, temporadas online, anti-cheat de rede.
- Escrever rank/EXP dentro do relógio (não existe campo; risco de corromper dados).
- Substituir o sistema existente de batalhas/quests; apenas hooks de eventos.
- Baralho de arena/raid completo (fica preparado por gates/rewards, implementação futura).
- Nomes oficiais de ranks do VB Lab (eram server-side; usaremos nomes próprios do Tamers Lab).

---

## 3. Base de referência (VB Lab) — fatos e decisões

Evidências completas em `C:\Users\julye\Downloads\vbhelper-analysis\digibb-1.4.6\RANK_SYSTEM.md`
(metadata IL2CPP) e nos assets em `analysis/vb-lab-aa/`.

| Fato observado (VB Lab) | Implicação para o Tamers Lab |
|---|---|
| `TamerRank` (tier, índice de ícone `tamerrankicon_<n>`), `TamerLevel`, `expRemain`/`expRemainPrev` são coisas distintas de **posição** em boards (`RankingData.rank/point`) | Modelar `rank` (tier), `level` e `totalTxp`; **não** misturar com posição em board |
| Escada/pontos/nomes são **server-side** (nenhum literal de nome; nada no cliente) | Fórmula e nomes são **decisão de design** do Tamers Lab (documentada e configurável) |
| 16 ícones `tamerrankicon_0..15` no cliente | Usar os 16 emblemas como tiers visuais |
| Única trava de rank no código: `BattleLimitType.TAMER_RANK` (EQUAL/MORE/LESS) em `BattleLimitData` → `EntryCheck.LIMIT_NG` | Replicar como **RankGate** em conteúdo opcional (entrada de batalhas elite/missões) |
| Rank **não** controla NFC/scan (conteúdo de `ScanContent` sem rank) | NFC é fonte de TXP; rank não bloqueia transferência |
| UI: `ExpMcParts` (`texp`, `startp`, `endp`, `lvlupf`, `rankupf`), `RankUpDialog`, `RankRewardDialog`, `BRankResultDialog`, ranking screens | Tela própria + barra de progresso + diálogos equivalentes |
| Aviso: batalha retorna `TamerRank/NewTamerRank`, `TamerLevel/NewTamerLevel` | Eventos de batalha podem render TXP e disparar level/rank up |
| `rankup_anime`, `rank_reward_anime_arena`, `rank_reward_anime_raid` | Referência de intenção/timing para a animação Compose |
| Assets: `tamerrank_bg`, `rank_123_parts`, `rank_p_parts`, `RankNum`, `Text_ranking`, ícones de arena/raid | Base visual de recompensas/banners/galeria |

---

## 4. Decisões de design

### 4.1 Dois eixos, um acumulador
- **TXP (Tamer EXP):** inteiro acumulado (64-bit), nunca diminui (exceto migração/correção manual).
- **Tamer Level:** 1..100, fórmula `custoNivel(l) = 60 + 3·(l−1)` TXP para o próximo nível.
- **Tamer Rank:** 0..15, **derivado do nível** por marcos (tabela §6.2), como no app oficial. Cada rank
  tem só o emblema `vb_rank_XX` — **sem nome inventado**: a UI mostra "Tamer Rank" + numeral do índice
  (a11y) e o nível, fiel ao original.
- A **barra principal** mostra o progresso de **EXP do nível atual** (como `ExpMcParts.texp/startp/
  endp`); abaixo, "próximo rank no nível N". No rank-up o nível continua; no level-up a barra reinicia.
- Level e rank são calculados *a partir do total* (funções puras) para evitar drift; banco guarda
  apenas `total_txp` + cache denormalizado (`level`, `rank`, `last_rank_up_at`).

### 4.2 "Qualquer Digimon"
- Eventos de TXP são **agnósticos de espécie**: carregam opcionalmente `digimonId` (individual) e
  `origin` (VB/BE/DIM/VPET/DWDS/DUSK/LOST/STORAGE/CARD), usados só para:
  - **modificador de estágio** opcional e limitado (ex.: ×1.0 Rookie … ×1.3 Mega, cap 1.3);
  - apresentação no feed de atividade;
  - deduplicação (ex.: "primeira vez deste individual").
- Nenhuma tabela por espécie, nenhum requisito de "Digimon específico". Funciona com saves de
  qualquer origem mapeada no `game-save-core`/`storage`.
- **Regra de ouro:** se o app consegue exibir/transferir o Digimon, ele pode pontuar.

### 4.3 Offline-first, sem servidor
- Toda a progressão é local; timestamps usam relógio do device com tolerância e *monotonic guard*
  (`last_updated` nunca anda para trás; se o relógio voltar, não regride o estado).
- "Ranking" = **histórico pessoal + metas** (sem board de rivais): recordes de TXP/dia, sequência,
  marcos de rank/level e "faltam X TXP". Sem posição fictícia; nunca sugere ranking online.
- Sem chamadas de rede, sem permissão de internet.

### 4.4 Princípios de produto
- Recompensa por **variedade**, não por grind repetitivo (caps diários + bônus de primeira vez).
- Progressão visível em qualquer sessão curta (1 scan já move a barra de nível; rank é meta longa).
- Nunca punir falha do relógio; nunca bloquear funções core por rank.
- Tudo configurável em um único objeto `RankConfig` (defaults no código, override por DataStore
  somente para debug).

---

## 5. Assets e identidade visual

Disponíveis em `analysis/vb-lab-aa/` (originais do APK, branco/alpha para tint por tema):

| Arquivo origem | Destino no app | Uso |
|---|---|---|
| `assets/rank_icons/tamerrankicon_0.png` … `_15.png` | `drawable-nodpi/vb_rank_00.png` … `vb_rank_15.png` | Emblema do rank atual, galeria, gate, diálogos, card da Home |
| `assets/rank_bg/tamerrank_bg_0.png` | `drawable-nodpi/vb_rank_bg.png` | Faixa/fundo do cabeçalho de rank (opcional, baixo alpha) |
| `assets/rank_ui/Background.png`, `UIMask.png`, `dot.png`, `animeline_*` | conforme uso | Detalhes/linhas de UI (opcional) |
| `assets/rank_ui/arena000…016.png`, `boss_*.png`, `battle_menu_*` | banners de recompensa/conquista | Banners de rank reward e gates de conteúdo |
| `assets/raid/raid001…015` (JP/EN), `title_raids*` | futuro modo raid | Arte de bosses e telas de raid |
| `assets/title_gears/title_circle1..3.png` | já usado no `TechBackground` | Consistência do fundo tech |
| `contacts/rank_icons.png` | doc | Contact sheet dos 16 tiers |

Regras visuais:
- Emblemas são **silhueta branca**: tint com `VitalCyan`/`markTint` (tema escuro) e `TextPrimary`/
  accent escuro (tema claro). Criar helper `rankEmblemTint()` com contraste ≥ 3:1.
- Tamanhos: 96 dp (header), 64 dp (card Home), 44 dp (lista/galeria), 28 dp (chips). Sem bordas duras;
  usar `MaterialTheme` shapes do app.
- Animação de rank-up: replicar a intenção de `rankup_anime` com Compose (`animateFloatAsState` +
  `graphicsLayer`): troca do emblema com scale/fade, sweep de luz radial, texto letter-spaced.
  Duração ~900–1200 ms, **skippable**, e desligada com reduce-motion (troca instantânea).
- Fundo: seguir `TechBackground` (grid/circuito removidos conforme decisão recente) — o rank screen
  usa `Box` com `TechBackground` + conteúdo; evitar competir com os anéis.
- Copy/estilo: técnico, curto, monospace-ish nos números (usar `AppFont` existente); sem "gamificação"
  infantil.

**Passo de asset (M0):**
```powershell
Copy-Item analysis\vb-lab-aa\assets\rank_icons\tamerrankicon_0.png app\src\main\res\drawable-nodpi\vb_rank_00.png
# … repetir 1..15
Copy-Item analysis\vb-lab-aa\assets\rank_bg\tamerrank_bg_0.png app\src\main\res\drawable-nodpi\vb_rank_bg.png
```

---

## 6. Regras, fórmulas e configuração

### 6.1 Fontes de TXP (defaults)

| Fonte | Evento | TXP | Cap diário | Idempotência |
|---|---|---|---|---|
| NFC leitura | leitura concluída de personagem | 8 | 40 | `nfc:read:<tagId>:<charId>:<dia>` |
| NFC escrita | envio concluído (ack + read-back) | 15 | 60 | `nfc:write:<tagId>:<charId>:<dia>` |
| Missão especial escrita | `SpecialMission` gravada | 20 | 60 | `nfc:mission:<tagId>:<slot>:<dia>` |
| Batalha | vitória | 30 | 200 | `battle:<battleId>` |
| Batalha | derrota | 8 | 80 | `battle:<battleId>` |
| Batalha | 1ª vitória do dia vs oponente | +15 | 45 | `battle:first:<opponentId>:<dia>` |
| Quest | clear tiers | 25/40/60 | 250 | `quest:<questId>:<instanceId>` |
| Dex | espécie nova | 40 | — (única) | `dex:new:<speciesId>` |
| Evolução | nova linha registrada | 20 | — (única) | `dex:evo:<lineId>` |
| Itens | uso de item | 2 | 40 | `item:use:<instanceId>` |
| Storage/transfer | organização/entrega | 5 | 30 | `storage:<opId>` |
| Mundo/adventure | interação/participação | 10 | 100 | `world:<interactionId>` |
| Login diário | 1º acesso do dia | 20 | 1/dia | `daily:<dia>` |
| Streak | 7 dias consecutivos | 100 | semanal | `week:<isoWeek>` |
| Card/adventure | clear de nó | 15 | 90 | `card:<nodeId>:<runId>` |

Modificadores (aplicados após o valor-base, antes do cap):
- Estágio do Digimon envolvido: Rookie ×1.0 · Champion ×1.05 · Ultimate ×1.10 · Mega ×1.15 ·
  Ultra ×1.2 · Armor/Hybrid ×1.1 · cap absoluto ×1.3.
- Primeira atividade do dia: +10 % (uma vez).
- Modo "foco" (opcional, settings): nada de multiplicador — evitar pressão.

### 6.2 Rank por nível (marcos) — fiel ao original

No VB Lab, a ajuda oficial (`help_spmission_deck`): “ganhando EXP o Tamer Level sobe; ao atingir certos
níveis o Tamer Rank sobe também”. Portanto o rank é **função do nível**, não uma segunda escada de EXP.
Os 16 emblemas `tamerrankicon_0..15` correspondem aos 16 ranks; **não há nomes no cliente**.

| Rank (ícone) | Nível de entrada | RECOMPENSA (confirmada para o plano) |
|---:|---:|---|
| 0 | 1 | — |
| 1 | 4 | 2× item consumível |
| 2 | 8 | 100 moedas |
| 3 | 12 | ticket de batalha |
| 4 | 17 | 3× itens |
| 5 | 22 | ticket de missão especial |
| 6 | 28 | 150 moedas |
| 7 | 34 | item raro |
| 8 | 41 | ticket especial + título de perfil |
| 9 | 48 | 250 moedas |
| 10 | 56 | moldura de emblema (cosmético) |
| 11 | 64 | bundle de itens |
| 12 | 72 | skin/variação visual |
| 13 | 80 | 400 moedas |
| 14 | 88 | ticket raro + missão especial |
| 15 | 96 | moldura dourada + título exclusivo |

Calibração de ritmo (escolha do usuário): **~8 semanas** de uso normal até o rank 15. Com
`custoNivel(l)=60+3·(l−1)`, chegar ao nível 96 custa ≈ **19 440 TXP** (≈ 350 TXP/dia em 56 dias).
Todos os valores ficam em `RankConfig`, fáceis de rebalancear.

### 6.3 Nível
- `custoNivel(l) = 60 + 3·(l−1)` TXP para ir de `l` para `l+1`; começa no nível 1; **cap 100**.
- Exibição: cabeçalho "Lv. 37" + barra de EXP do nível; abaixo, "próximo rank no nível 41".
- Level-up não bloqueia nada; diálogo/toast curto. Rank-up dispara no nível-marco (`RankConfig`).

### 6.4 Anti-farm e integridade
- Caps diários por fonte (§6.1) em `tamer_daily_cap` (transação atômica).
- `idempotency_key` UNIQUE em `tamer_txp_event` — nunca pontua o mesmo evento 2× (retry de NFC,
  recomposição, rotação de tela, etc.).
- Eventos NFC só após `requireWriteAck` + `requireReadBack` (já existentes em `:hardware`).
- Sanidade: 1 evento ≤ 500 TXP; total do dia ≤ 20 000 (limite de configuração); em caso de estouro,
  registra evento "suspeito" e não pontua (log interno, sem UI punitiva).
- Sem rollback destrutivo: correção manual via debug settings (export/import).

---

## 7. Modelo de dados

### 7.1 Room (novo schema, versão seguinte do `AppDatabase`)

```sql
CREATE TABLE tamer_profile (
  id INTEGER NOT NULL PRIMARY KEY CHECK (id = 0),
  total_txp INTEGER NOT NULL DEFAULT 0,
  level INTEGER NOT NULL DEFAULT 1,
  rank INTEGER NOT NULL DEFAULT 0,
  last_level_up_at INTEGER,
  last_rank_up_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE tamer_txp_event (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,            -- NFC_READ / NFC_WRITE / BATTLE_WIN / ...
  amount INTEGER NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  digimon_ref TEXT,                -- opcional (individual/card id)
  origin TEXT,                     -- VB / BE / DIM / VPET / DWDS / DUSK / LOST / STORAGE
  metadata TEXT,                   -- JSON (batalha, quest, tag)
  occurred_at INTEGER NOT NULL
);
CREATE INDEX idx_txp_event_time ON tamer_txp_event(occurred_at);

CREATE TABLE tamer_daily_cap (
  day TEXT NOT NULL,               -- yyyy-MM-dd (fuso local)
  source TEXT NOT NULL,
  total INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(day, source)
);

CREATE TABLE tamer_reward (
  rank INTEGER NOT NULL,
  slot INTEGER NOT NULL,
  kind TEXT NOT NULL,              -- ITEM / CURRENCY / COSMETIC / TITLE / UNLOCK
  payload TEXT NOT NULL,           -- JSON
  claimed_at INTEGER,
  PRIMARY KEY(rank, slot)
);

CREATE TABLE tamer_activity (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at INTEGER NOT NULL,
  source TEXT NOT NULL,
  delta INTEGER NOT NULL,
  summary TEXT NOT NULL,
  digest TEXT NOT NULL UNIQUE      -- dedup de feed
);
```

Entidades Kotlin correspondentes em `rank/data/RankEntities.kt`; DAOs em `rank/data/RankDao.kt`
(`@Transaction` para "aplicar evento": checar idempotência → atualizar cap → inserir evento →
atualizar profile → enfileirar recompensas → inserir atividade).

Migração: `AppDatabase` versão +1 com `PreservingMigrationFactory` (padrão já existente no projeto);
teste de migração em `rank/data/RankMigrationTest` (vN→vN+1 preserva tabelas atuais).

### 7.2 Preferências (DataStore)
- `rank_enabled` (feature flag default off até M2 pronto), `rank_notifications`,
  `rank_show_activity`, `rank_board_style` (LOCAL/HISTORY/OFF), `rank_reduce_motion_override`
  (respeita setting global).

### 7.3 Domain models (puros, testáveis)
```kotlin
data class TamerProfile(val totalTxp: Long, val level: Int, val rank: Int, ...)
data class TamerRank(val index: Int, val nameRes: Int, val threshold: Long, val emblemRes: Int)
data class TxpAward(val source: TxpSource, val amount: Int, val idempotencyKey: String, ...)
data class RankReward(val rank: Int, val kind: RewardKind, val itemId: String?, val qty: Int)
sealed interface RankGate { data class MinRank(val rank: Int) : RankGate }
```
`RankCalculator` (puro): `levelFor(total)`, `rankFor(total)`, `progressToNextRank(total)`,
`progressToNextLevel(total)`, `isRankUp(old, new)`.

---

## 8. Recompensas e gates

### 8.1 Recompensas
- No rank-up: gera `tamer_reward` para o novo rank (idempotente) e mostra `RankRewardDialog`.
- Tipos: `ITEM` (via `ItemDao`/`items` existente), `CURRENCY` (via `CurrencyRepository`),
  `COSMETIC`/`TITLE`/`UNLOCK` (skin/perk — armazenar e exibir; efeitos futuros).
- Claim: manual ("Resgatar") para permitir a animação; "Resgatar tudo" no card da tela.
- Nunca duplicar: `PRIMARY KEY(rank, slot)` + `claimed_at`.
- Recompensas que viram item do relógio: entregues no inventário do app e o usuário escreve pelo fluxo
  normal de transferência (marcar "pronto para enviar ao relógio" quando aplicável).

### 8.2 Gates (`RankGate`)
Adaptação de `BattleLimitType.TAMER_RANK`, apenas em conteúdo opcional:
- Batalha elite/boss do `battles` package: `MinRank`.
- Missões especiais (roteiros do relógio) — apenas **exibição/compra** de missão especial, nunca leitura.
- Tiers de loja de itens e variações de skin.
- NUNCA: scan, leitura, escrita básica, transferência, storage, dex.
- Diálogo `RankGateDialog`: emblema atual vs necessário, barra de progresso, CTA "Ir para Rank".

---

## 9. Integração com o relógio (NFC)

### 9.1 Fluxo de pontos
1. Fluxo existente de scan/transferência chama `TagCommunicator.receiveCharacter` / `sendCharacter`
   (VB/BE) ou `hardware-vpet` (VPET) e obtém sucesso com ack/read-back.
2. Emite `WatchRankEvent(type, watchKind, tagId, individualId, result)` **somente em sucesso**.
3. `RankAwarder.award(event)` roda em `Dispatchers.IO`, valida idempotência/cap e grava em transação.
4. UI observa `flow` de profile e dispara level-up/rank-up se cruzou threshold (fila de diálogos).
5. Falha/timeout/cancelamento: nenhum TXP; log técnico sanitizado (sem payload/tag).

### 9.2 O que o rank faz no relógio
- **Nada é escrito no relógio** pelo sistema de rank (evita corromper dados e é semanticamente falso:
  firmware não tem campo de rank).
- Rank concede **direitos no app**: mais espaço de armazenamento? (não existe no relógio), tickets,
  missões especiais, itens — todos transferidos pelos fluxos existentes.
- Compatibilidade: VB, BE, DIM, VPET — o evento é normalizado; taxa de TXP igual por tipo de
  operação (sem favorecer aparelho).
- Segurança/privacidade: não logar tag id/payload; hashes de idempotência derivados de SHA-256
  truncado de `tagId|sessionNonce|day`; dados existentes em `NfcTransferSafety` preservados.

### 9.3 Pontos de gancho concretos
- `app/.../transfer/**` (fluxos de envio/recebimento) e `transfer/hce/VitalWearHceReaderClient`
  para leitura HCE — emitir evento após confirmação.
- `storage`/`vitalwear` quando o resultado é "aplicado ao relógio" (missão especial).
- `battles`/`world/ecosystem` ao finalizar batalha com resultado confirmado.
- `quests`, `species` (dex), `items` (uso/consumo), `adventure` (clear de nó).
- Todos emitem via `RankEventSink` (interface no domínio) para não criar dependência circular.

---

## 10. UI / UX

### 10.1 Navegação e superfícies
- Nova rota `AppRoute.TamerRank` + `NavigationItems.Rank` (ícone: `vb_rank_15` tintado; label
  "Tamer Rank"/"Rank"/"ランク"), no menu overflow (não roubar slot primário da bottom bar).
- **Card na Home** (`UnifiedHomeScreen`/`PartnerModules`): emblema 64 dp + "Tamer Rank" + numeral
  + barra de EXP do nível + "próximo rank no nível 41"; tap → tela; long-press → galeria.
  **Ligável/desligável nas configurações** (`rank_home_card_enabled`, default ligado).
- **Tela Rank** (scroll):
  1. Header: emblema grande, "Tamer Rank" + numeral, nível, total TXP, última promoção.
  2. Progresso: barra de EXP do nível (como no original) + próximo marco de rank ("próximo rank no
     nível N") + "faltam X TXP".
  3. Estatísticas: vitórias/derrotas/% , quests, scans, transferências, espécies registradas,
     sequência diária.
  4. Recompensas: pendentes (claim) + histórico.
  5. Metas/próximos desbloqueios (gates e recompensas do próximo rank).
  6. Atividade recente (feed com caps e dedupe).
  7. Galeria dos 16 emblemas (grid 4×4; atual destacado, futuros com lock e nível de entrada).
  8. Atividade do relógio (últimas sessões; sem payload).
- Diálogos: `RankUpDialog` (animação), `LevelUpDialog` (toast/dialog curto), `RankRewardDialog`,
  `RankGateDialog`.
- Estados vazios: "Comece escaneando seu relógio" com CTA para Scan.

### 10.2 Estilo
- Usar `TechBackground`, `DeepPurpleBg`, `SurfaceStroke`, `VitalCyan`, `TextPrimaryOnDark`,
  `HelperPalette` atual; sem novas cores hardcoded.
- Números em `AppFont` monospace-ish; emblemas tintados; halos/linhas finas (1 dp) no estilo do app.
- Tema claro: inverter tint dos emblemas e aumentar contraste (mesma lógica do boost de presença já
  usada no `TechBackground`).
- Animações: progresso animado (`animateFloatAsState`, 400 ms), rank-up 1.0–1.2 s, shimmer curto;
  `motionEnabled()` desliga tudo (estado estático + troca instantânea).
- Acessibilidade: `contentDescription` "Tamer Rank, nível 5; nível de Tamer 37; 240 de 348 TXP para o
  próximo nível"; barra com `progressBarRangeInfo`; diálogos com foco inicial no título; fontScale 200 %.

### 10.3 i18n
- `values/strings_rank.xml`, `values-pt-rBR/strings_rank.xml`, `values-ja/strings_rank.xml`.
- Chaves: `rank_title`, `rank_level`, `rank_txp`, `rank_progress`, `rank_up_title`,
  `rank_reward_*`, `rank_gate_*`, `rank_board_*`, `rank_source_*` (fontes exibidas no feed).
- **Sem nomes inventados**: o original não tem nomes de rank no cliente (server-side). A UI mostra
  "Tamer Rank" + numeral (ex.: "Tamer Rank 5") e o emblema; o numeral é apenas exibição/index, não lore.
  Se no futuro quiser nomes, entram como strings opcionais sem mudar o modelo. Ver §16.

---

## 11. Arquitetura de código

### 11.1 Pacotes novos
```
app/src/main/java/com/tamerslab/rank/
  data/        RankEntities.kt · RankDao.kt · RankConverters.kt · RankRepositoryImpl.kt · RankConfigStore.kt
  domain/      TamerProfile.kt · TamerRank.kt · RankThresholds.kt · RankCalculator.kt
               TxpSource.kt · TxpAward.kt · RankReward.kt · RankGate.kt · RankRepository.kt · RankEventSink.kt
  ui/          RankScreen.kt · RankController.kt · RankState.kt · RankEmblem.kt · RankProgressCard.kt
               RankDialogs.kt · RankGallery.kt · RankHomeCard.kt · RankActivityList.kt
  di/          RankModule.kt (ou fábrica em DefaultAppContainer)
```
Padrão de controller/estado igual ao restante do app (`*Controller` interface + Impl, `StateFlow`).

### 11.2 Integrações a alterar
| Arquivo | Mudança |
|---|---|
| `data/database/AppDatabase.kt` | +5 entidades, +DAOs, versão+1, migração |
| `di/AppContainer.kt` / `DefaultAppContainer.kt` | `val rankRepository: RankRepository` + construção |
| `navigation/NavigationItems.kt` | `object Rank` |
| grafo de navegação (`MainApplication`/rotas) | composable `RankScreen` |
| `home/ui/UnifiedHomeScreen.kt` / `PartnerModules.kt` | card de rank |
| `battle/**` result handling | emitir BATLE_* |
| `quests/**`, `species/**` (dex), `items/**`, `storage/**`, `adventure/**` | emitir TXP |
| `transfer/**`, `storage/**` (missão especial), `hardware` flows | emitir NFC_* após ack/read-back |
| `res/values*/strings_rank.xml`, `drawable-nodpi/vb_rank_*.png` | i18n + assets |

### 11.3 Contratos
```kotlin
interface RankRepository {
  val profile: Flow<TamerProfile>
  val rewards: Flow<List<RankRewardState>>
  val activity: Flow<List<RankActivityEntry>>
  suspend fun award(events: List<TxpAward>): AwardResult
  suspend fun claimReward(rank: Int, slot: Int)
  suspend fun claimAll()
}

interface RankEventSink { fun emit(event: RankEvent) }
sealed interface RankEvent {
  data class Nfc(val kind: NfcRankKind, val tagHash: String, val digimonRef: String?, val origin: Origin, val ok: Boolean) : RankEvent
  data class Battle(val battleId: String, val won: Boolean, val opponentId: String?) : RankEvent
  data class Quest(val questId: String, val tier: Int, val instanceId: String) : RankEvent
  // Dex, Item, Storage, World, Daily…
}
```
`RankEventBus` (SharedFlow) + `RankAwarder` (debounce/queue) desacopla o app.

---

## 12. Migração, backup e versionamento

- Room: migração testada (vAtual→vAtual+1) com `PreservingMigrationFactory`; dados novos não afetam
  saves existentes.
- Backup export/import (se já existir): incluir `tamer_*` no JSON/ZIP; manter compatibilidade
  (chaves novas opcionais; import antigo cria profile zero).
- Sem alteração de schema de saves/transfer existentes.
- Feature flag `rank_enabled` permite desligar sem remover dados (rollback seguro).

---

## 13. Testes e critérios de aceitação

### 13.1 Unit
- `RankCalculator`: marcos de nível exatos (1,4,8,…,96), bordas (nível 3→4 sobe rank; nível 95→96
  sobe para 15), cap 100, progresso de nível 0–1.
- `RankRepositoryImpl`: idempotência (mesmo evento 2× = 1 linha), caps diários, transação,
  rank-up único por rank, recompensas não duplicadas, clock rollback.
- Migração: schema vAtual→vNovo.
- `RankConfig` parse/default.

### 13.2 UI/Robolectric
- Tela: estados loading/empty/full, 16 galeria, reduce-motion, fontScale 2.0.
- Diálogos: rank-up/level-up/reward/gate; sem sobreposição (fila).
- Temas claro/escuro: contraste e tint.
- i18n: en/pt/ja sem strings faltando.

### 13.3 Integração
- Fake NFC (`NfcTransport` + fixtures): sucesso pontua; falha não pontua; retry não duplica.
- Batalha/quest/dex emitem e aplicam; cap respeitado.
- Backup/import preserva total.

### 13.4 Critérios de aceitação
1. Um scan de qualquer relógio suportado (ou VPET) gera TXP visível na Home e na tela de Rank.
2. 16 ranks atingíveis por atividades normais; próximo rank sempre alcançável; fórmula documentada.
3. Rank/level update em < 100 ms após o evento; UI não bloqueia NFC.
4. Nenhuma função core (scan/transfer/leitura) bloqueada por rank; gates só em conteúdo opcional.
5. Nenhum TXP duplicado em retries; caps diários funcionam; relógio do device recuado não regride.
6. Funciona com qualquer origem de Digimon (testes com 3 origens diferentes).
7. Tema claro/escuro e 3 idiomas; reduce-motion desliga animações.
8. Migração sem perder dados e feature flag desliga o sistema.
9. Docs atualizadas + previews/contact sheets.

---

## 14. Fases de implementação

| Marco | Entrega | Estimativa | Depende |
|---|---|---|---|
| **M0 Fundação** | assets copiados (`vb_rank_*`, `vb_rank_bg`), `RankConfig` + domínio puro + `RankCalculator` + testes; flag desligada | 0,5–1 d | — |
| **M1 Dados** | entidades/DAOs/migração/repositório + idempotência/caps + testes unitários | 1–1,5 d | M0 |
| **M2 Tela de Rank** | rota/menu, `RankScreen` (header/progresso/galeria/atividade), strings en/pt/ja, card na Home | 2–3 d | M1 |
| **M3 Recompensas** | `RankRewardDialog`, claim/claim-all, integração `ItemDao`/`CurrencyRepository`; gates + `RankGateDialog` | 1–2 d | M2 |
| **M4 NFC** | `RankEventSink` nos fluxos de scan/transfer/missão; idempotência; log de atividade; testes com `NfcTransport` fake | 1–2 d | M1 |
| **M5 Progressão ampla** | hooks de batalha/quests/dex/itens/world/adventure; stats; metas/próximos desbloqueios | 1–2 d | M4 |
| **M6 Polimento** | animação rank-up, acessibilidade, temas, reduce-motion, backup/import, QA, docs | 1–2 d | M3–M5 |

Total: **8–12 dev-days**. Ordem recomendada: M0→M1→M2→M4→M3→M5→M6 (NFC cedo para validar UX).

---

## 15. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Inflação de rank por grind repetitivo | caps diários + bônus de variedade + marcos de nível; rebalance via `RankConfig` |
| Idempotência de NFC (retry/recomposição) | chave única por tag/dia/op + transação; testes com fake transport |
| Migração de banco falhar | migração testada + `PreservingMigrationFactory` + flag off |
| Expectativa de leaderboard online | UI deixa claro: **histórico pessoal + metas**, sem posição; textos revisados |
| Contraste em tema claro | tint com `isDark` do palette + testes de screenshot |
| Performance no scan | award em `Dispatchers.IO` pós-sucesso; sem bloquear NFC; debounce |
| Escopo crescer (arena/raid) | gates/recompensas deixam pronto; implementação em fase futura |
| IP dos assets | uso pessoal; assets de `analysis/` não entram em release público; nota legal |

---

## 16. Decisões confirmadas (alinhadas com o usuário)

1. **Nomes dos ranks:** “como no app original do VB Lab”. Investigação: o cliente **não tem nomes**
   (server-side; só 16 ícones + “Tamer Rank”). Portanto: **não inventar nomes** — UI mostra
   “Tamer Rank” + emblema + nível, com numeral apenas como índice/exibição e para acessibilidade.
2. **Ranking offline:** **histórico pessoal + metas** (sem board de rivais fictício).
3. **Gates de rank:** batalhas elite/boss + **missões especiais** + **loja de itens e skins**
   (nunca scan/leitura/escrita/transferência).
4. **Ritmo:** **~8 semanas** de uso normal até o rank 15 (nível 96; ≈19 440 TXP).
5. **Recompensas:** **itens + moedas** do Tamers Lab, **tickets e missões especiais**, **cosméticos/
   títulos de perfil** (sem skins de UI por enquanto; deixar gancho para depois).
6. **Card na Home:** ligável/desligável nas configurações.
7. **Visual:** emblemas originais contraídos pelo tema (claro/escuro), sem recriar arte.
8. **Notificação de TXP:** mantida discreta (snackbar/toast) ao voltar de scan; ligável nas settings.

---

## 17. Anexos

### A. Evidência VB Lab (resumo)
- `TamerRank` = índice de `tamerrankicon_<n>`; 16 sprites presentes no cliente.
- **Ajuda oficial** (`vb_static/page/help/help_spmission_deck[_en].html`): “ganhando EXP o Tamer Level
  sobe; ao atingir certos níveis o **Tamer Rank** sobe também” → rank é função do nível.
  `help_user[_en].html`: “Tamer Rank — exibe seu Tamer Rank atual”.
- Escada/pontos/nomes **server-side** (sem literais no cliente; busca por nomes de tier não encontrou
  nada) → fórmula/marcos próprios documentados acima, sem inventar nomes.
- `BattleLimitType.TAMER_RANK` → gate de entrada de batalha (única regra de rank no metadata).
- UI: `ExpMcParts` (texp/startp/endp/lvlupf/rankupf), `RankUpDialog`, `RankRewardDialog`,
  `BRankResultDialog`; ranking boards em `Home.BattleMenu.*`.
- Assets extraídos: `analysis/vb-lab-aa/` (README, RANK.md, contatos).
- APIs observadas (não usadas neste plano): `GetRanking`, `NewMyRanking`, `GetBattleInfo`,
  `getarenaranking`, `getraidinf`, `ViewOtherProfile`.

### B. Config default (exemplo)
```json
{
  "enabled": false,
  "levelCap": 100,
  "levelStepBase": 60,
  "levelStepGrowth": 3,
  "rankLevelThresholds": [1,4,8,12,17,22,28,34,41,48,56,64,72,80,88,96],
  "dailyCaps": {"NFC_READ":40,"NFC_WRITE":60,"BATTLE_WIN":200,"QUEST":250,"DEX":1000,"ITEM_USE":40,"STORAGE":30,"WORLD":100,"DAILY":20},
  "stageMultiplier": {"ROOKIE":1.0,"CHAMPION":1.05,"ULTIMATE":1.10,"MEGA":1.15,"ULTRA":1.20,"ARMOR":1.10,"HYBRID":1.10,"UNKNOWN":1.0},
  "homeCardEnabled": true,
  "boardStyle": "HISTORY"
}
```

### C. Assets já disponíveis
- Emblemas: `analysis/vb-lab-aa/assets/rank_icons/` (16), preview `contacts/rank_icons.png`.
- Fundo: `assets/rank_bg/tamerrank_bg_0.png`.
- UI/arena/raid: `assets/rank_ui/`, `assets/rank_bg/`, `assets/raid/`.
- Ícones de nav originais já aplicados: `app/src/main/res/drawable-nodpi/vb_icon_*.png`.
- Docs de referência: `analysis/vb-lab-aa/RANK.md`, `analysis/vb-lab-aa/README.md`,
  `C:\Users\julye\Downloads\vbhelper-analysis\digibb-1.4.6\RANK_SYSTEM.md`.

### D. Checklist de entrega
- [ ] Assets copiados e tintados por tema
- [ ] `RankConfig` + domínio + testes
- [ ] Room + migração testada
- [ ] Tela + card Home + galeria + i18n
- [ ] Rank-up/reward/gate dialogs + animação
- [ ] Hooks NFC (ack/read-back) + idempotência
- [ ] Hooks batalha/quest/dex/itens/storage/world
- [ ] Estatísticas + feed + metas
- [ ] Backup/import + feature flag
- [ ] QA: temas, fontScale, reduce-motion, 3 idiomas, 3 origens de save
