# Inventário CP — UI, i18n, testes e documentação

**Task:** `task-5` — "Inventário CP — UI, i18n, testes e documentação"
**Escopo deste arquivo:** metade UI/I18N/TESTES/DOCS do sistema de Command Points (CP). O núcleo
(`battle/offline/core`, checkpoint, replay) é coberto por `inventariador-core`.
**Método:** leitura somente (`glob`/`grep`/`read`), sem edição de código de produção. Contagens por
`Select-String` case-sensitive com o padrão:
`commandPoint|CommandPoint|pendingSupport|PendingSupport|SupportSucceeded|SupportWindowOpened|supportReady|onSupport|supportCommands|OrderFailure\.SUPPORT|TrainerAction\.Support|\bCP\b|ui_battle_cmd_support|ui_battle_feed_support|ui_battle_order_err_support|ui_battle_loadout_cost|ui_battle_no_resources|ui_battle_result_stats`
(linhas, não ocorrências; falsos positivos de "Supports" em nomes de teste foram descartados).

## 0. Fora de escopo — NÃO remover

Ficam, porque são conceitos homônimos independentes de CP:

| Símbolo | Onde | Motivo |
|---|---|---|
| `BattleStrategy.SUPPORT` | `OfflineTrainingBattleScreen.kt:790,966,2900`, `ArenaWidgets.kt:188` | estratégia de IA, não comando de CP |
| `R.string.ui_battle_strategy_support` | `values:805`, `values-ja:715`, `values-pt-rBR:784` | rótulo da estratégia acima |
| `TamerStyle.SUPPORT` | `TamerBattleLoadouts.kt:15,63,101` | estilo de tamer |
| `R.string.arena_plan_support` | `values/tamer_arena.xml:103`, `values-ja/...:103`, `values-pt-rBR/...:103`; ref. `ArenaBriefing.kt:98` | descrição do estilo de tamer |
| `TechniqueKind.SUPPORT/HEAL` | `battle/domain`, `BattleCinematicRenderer.kt:131` | tipo de técnica |
| `accessibility`/`support` geométrico | `BattleImpactVisual.kt:26,41,44` | plano de colisão |
| `supportingLabel` | `TechniqueLoadoutScreen.kt:326,487,521` | rótulo visual do loadout |

## 1. Mapa de renderização (quem usa o quê)

| Fluxo | Composable | Arquivo:linha | Usa CP/Support? |
|---|---|---|---|
| Treino offline (`BattlesScreen` rota `offline-battle`) | `OfflineTrainingBattleScreen` → `BattleCommandBand` + `BattleResultPanel` | `BattlesScreen.kt:2424`; `OfflineTrainingBattleScreen.kt:599`; `BattleChrome.kt:565,1050` | **sim** |
| Arena (`TamerArenaScreen`) | reusa `OfflineTrainingBattleScreen` (`resultContent = ArenaResult`) | `TamerArenaScreen.kt:36-50` | **sim** (band); painel de resultado próprio **não** mostra CP |
| Radar battle (`WorldScreen`) | `WorldRadarBattleContent` → branch radar `BattleCommandBand` | `WorldScreen.kt:1039`; `OfflineTrainingBattleScreen.kt:1449` | **sim** |
| AR battle (`arStage != null`) | branch AR: `BattleCommandDeckHeader` + `BattleCommandBar` (privados) + `WorldRadarBattleHud` | `OfflineTrainingBattleScreen.kt:1096,1197,1247,1257,1693` | **sim** |
| Debug de batalha | `AlertDialog` "Diagnóstico da IA" | `OfflineTrainingBattleScreen.kt:653-657` | **sim** (texto CP) |
| Loadout de técnicas | `TechniqueLoadoutScreen` | `TechniqueLoadoutScreen.kt:292` | **sim** (custo EN·CP) |

## 2. Referências em produção/UI

### 2.1 `app/src/main/java/com/tamerslab/battle/ui/offline/BattleChrome.kt` (12 linhas; +2 comentários)

| file:line | símbolo/trecho | classificação | ação |
|---|---|---|---|
| `BattleChrome.kt:542-552` (SUPPORT em 547) | `battleCommandOrder = listOf(... BattleCommandSlot.SUPPORT, ...)` — 9 posições | remover | remover `SUPPORT`; lista fica com 8 |
| `BattleChrome.kt:560-563` | KDoc `BattleCommandBand`: "command points, ... and nine stable cut-corner keys" | atualizar | "eight"; remover "command points" |
| `BattleChrome.kt:566-567` | params `availableCommandPoints: Int`, `maxCommandPoints: Int` | remover | remover ambos e todos os call sites |
| `BattleChrome.kt:570,575` | params `supportReady: Boolean`, `onSupport: (String) -> Unit` | remover | remover ambos |
| `BattleChrome.kt:596-603` (600) | `battleCommandDeck(BattleCommandAvailability(..., supportReady = supportReady && commandsEnabled, ...))` | remover | remover argumento `supportReady` |
| `BattleChrome.kt:658-664` | `Text("CP $availableCommandPoints/${maxCommandPoints.coerceAtLeast(0)}", ...)` | remover | instrumento CP sai do header do band |
| `BattleChrome.kt:666-674` | `LinearProgressIndicator(progress = available/max, ...)` | remover | barra de CP; reduz a altura medida do band |
| `BattleChrome.kt:690-693` | comentário "the nine command stations never shift..." | atualizar | "eight" |
| `BattleChrome.kt:701-707` | `columns = when { landscape && maxWidth >= 560.dp -> 9; ... }` | atualizar | 9→8 colunas no wide landscape (ou manter 9 com spacer; ver Riscos) |
| `BattleChrome.kt:718-733` (721) | `BattleCommandSlot.SUPPORT -> stringResource(R.string.ui_battle_cmd_support, availableCommandPoints)` | remover | branch + string |
| `BattleChrome.kt:742-753` (746) | `BattleCommandSlot.SUPPORT -> allyId?.let(onSupport)` | remover | branch do `when` de clique |
| `BattleChrome.kt:1099-1108` (1101/1104) | `ui_battle_result_stats(healingDone, ordersCompleted, supportCommands, itemsUsed, projectilesHit, projectilesHit+missed)` | atualizar | remover `statistics.supportCommands` (6→5 args); ajustar string nos 3 locales |

Não mexer: `menuCommandSlots` (554-558), imports `BattleSnapshot/BattleStatistics` (83-84), demais usos do snapshot.

### 2.2 `app/src/main/java/com/tamerslab/battle/ui/offline/OfflineTrainingBattleScreen.kt` (40 linhas)

| file:line | símbolo/trecho | classificação | ação |
|---|---|---|---|
| `OfflineTrainingBattleScreen.kt:599-630` (600-602,605-606,613) | chamada do `BattleCommandBand` (treino/Arena): `availableCommandPoints`, `maxCommandPoints`, `supportReady = ... pendingSupportCombatantIds::contains`, `onSupport = { issue(it, TrainerAction.Support) }` | remover/atualizar | remover os 4 argumentos e o callback |
| `OfflineTrainingBattleScreen.kt:647-658` (657) | debug: `Text("CP ${snapshot.commandPoints}/${snapshot.maxCommandPoints} · reservado ${snapshot.reservedCommandPoints} · ...")` | remover | remover só o trecho CP/reservado do texto |
| `OfflineTrainingBattleScreen.kt:1247-1256` (1248-1250) | branch **AR**: `BattleCommandDeckHeader(availableCommandPoints=..., maxCommandPoints=...)` | remover/atualizar | header perde params e instrumento CP (mantém pause/tactics) |
| `OfflineTrainingBattleScreen.kt:1257-1291` (1260-1263,1267) | branch **AR**: `BattleCommandBar(availableCommandPoints, supportReady, onSupport=...)` | remover/atualizar | remover 4 argumentos |
| `OfflineTrainingBattleScreen.kt:1449-1491` (1450-1452,1455-1456,1463) | branch **Radar**: `BattleCommandBand(...)` com CP/support | remover/atualizar | idem 599-613 |
| `OfflineTrainingBattleScreen.kt:2153-2222` (2155-2156,2176-2181,2183-2191) | `BattleCommandDeckHeader` (AR): params CP + `Text("CP ...")` + `LinearProgressIndicator` | remover/atualizar | remover params/instrumento; preservar botões Pause/Tactics |
| `OfflineTrainingBattleScreen.kt:2224-2310` (2229-2230,2234,2257,2264,2273,2294) | `BattleCommandBar` (AR): params CP/support, `battleCommandDeck(... supportReady ...)`, `columns = if (maxWidth >= 560.dp) 9 else 3`, labels e clique `SUPPORT` | remover/atualizar | remover params/branches; colunas 9→8 |
| `OfflineTrainingBattleScreen.kt:2558-2598` (2570-2572) | `orderFailureText`: `SUPPORT_PAUSED/SUPPORT_NO_WINDOW/SUPPORT_WINDOW_EXPIRED -> ui_battle_order_err_*` | remover | remover 3 branches (códigos vêm do core) |
| `OfflineTrainingBattleScreen.kt:2563,2739` | `RESOURCES_MISSING -> ui_battle_no_resources`; `!hasResources -> ui_battle_no_resources` | manter chave/atualizar texto | mensagem passa a ser só energia (EN/energia) |
| `OfflineTrainingBattleScreen.kt:2721-2723` | `TechniquesDialog`: `((snapshot?.commandPoints ?: 0) - reservedCommandPoints) >= technique.commandPointCost` | atualizar | elegibilidade só energia (remover 2ª condição) |
| `OfflineTrainingBattleScreen.kt:2864-2865` | `eventSummary`: `SupportSucceeded -> ui_battle_feed_support`, `SupportWindowOpened -> ui_battle_feed_support_window` | remover | remover 2 branches do `when` |
| `OfflineTrainingBattleScreen.kt:2894-2901` (2900) | `BattleStrategy.SUPPORT -> ui_battle_strategy_support` | **manter** | não é CP |

O arquivo tem dois `fun issue(...)` (linhas 268 e 895) e quatro call sites de `TrainerAction.Support`
(613, 1267, 1463; o de 613 cobre treino/Arena); o tipo de ação em si é do core.

### 2.3 Demais arquivos de UI/sessão

| file:line | símbolo/trecho | classificação | ação |
|---|---|---|---|
| `TechniqueLoadoutScreen.kt:292` | `Text(stringResource(R.string.ui_battle_loadout_cost, special.energyCost, special.commandPointCost))` | atualizar | remover `special.commandPointCost`; string passa a 1 placeholder |
| `BattleCommandDeckModel.kt:9-19` (13) | `enum BattleCommandSlot { ..., SUPPORT, ... }` — 9 slots | remover | remover `SUPPORT`; comentário "Decode two-screen/3×3" fica desatualizado (atualizar) |
| `BattleCommandDeckModel.kt:21-26` (24) | `BattleCommandAvailability.supportReady` | remover | remover campo |
| `BattleCommandDeckModel.kt:33-48` (39) | `BattleCommandDeckItem(BattleCommandSlot.SUPPORT, availability.supportReady)` | remover | remover item |
| `battle/offline/session/BattleSceneChannel.kt:57` | `if (next.pendingSupportCombatantIds != previous.pendingSupportCombatantIds) return true` | remover | transição de janela de incentivo deixa de existir; KDoc 14-22 é genérico, sem mudança |
| `battle/offline/core/TamerTrainerController.kt:97-98` | `team.firstOrNull { it.combatantId in snapshot.pendingSupportCombatantIds }?.let { decisions += TrainerDecision(it.combatantId, TrainerAction.Support) }` | remover | IA deixa de emitir Support automático |

### 2.4 Consumidores indiretos (Arena/Radar/AR/debug)

| file:line | consumidor | o que consome | ação |
|---|---|---|---|
| `battle/ui/arena/TamerArenaScreen.kt:36-50` | Arena | reusa `OfflineTrainingBattleScreen` (band CP); `resultContent = ArenaResult` | herda remoção do band; **nenhuma** ação própria |
| `battle/ui/arena/ArenaResult.kt:21-49` | Resultado da Arena | só `damageDealt/damageReceived/itemsUsed/opposingItems` — **não** usa `supportCommands`/CP | sem alteração |
| `world/ui/WorldScreen.kt:1039` | Radar/AR | `WorldRadarBattleContent`; radar usa `BattleCommandBand` (linha 1449), AR usa header/bar privados (1247/1257) | herda remoção nos dois branches |
| `ar/ArBattleScene.kt:33,39` | AR | só `snapshot`/`sceneSnapshotFlow`; não lê CP | sem alteração |
| `battle/ui/offline/BattleAccessibility.kt:12-35` | a11y | semântica de seleção; não cita CP | sem alteração de código; labels do band mudam (TalkBack) |
| `app/src/debug/java/.../diagnostics/BattleBalanceRunner.kt:96,148` | debug harness | cria `BattleConfiguration()` sem CP | sem alteração |
| `BattlesScreen.kt:2424` | rota `offline-battle` | sem CP direto | sem alteração |
| `OfflineBattleSessionViewModel.kt` / `BattleSessionController.kt` | sessão | repassam snapshot; não leem CP | sem alteração |

## 3. Contagens por arquivo

| Arquivo | refs CP/Support | natureza |
|---|---:|---|
| `OfflineTrainingBattleScreen.kt` | **40** | 3 bandas (treino/Arena, Radar, AR) + debug + falhas + elegibilidade + feed |
| `BattleChrome.kt` | **12** | `BattleCommandBand` + `BattleResultPanel` |
| `BattleCommandDeckModel.kt` | **2** (+ enum SUPPORT l.13 = 3 pontos) | modelo dos slots |
| `TechniqueLoadoutScreen.kt` | **1** | custo EN·CP |
| `BattleSceneChannel.kt` | **1** | throttle de `pendingSupportCombatantIds` |
| `TamerTrainerController.kt` | **2** | auto-Support da IA |
| **Total UI/sessão** | **58** | |
| *Núcleo (fora deste inventário, `inventariador-core`)* | *49* | `BattleSimulator` 37, `BattleModel` 10, `BattleDefinitions` 1, `GenericTechniqueCatalog` 1 |
| `res/values/strings.xml` | **9 chaves** | ver §4 |
| `res/values-ja/strings.xml` | **9 chaves** | ver §4 |
| `res/values-pt-rBR/strings.xml` | **9 chaves** | ver §4 |
| Testes JVM (7 arquivos) | **50** | ver §5 |
| androidTest (3 arquivos) | **4 asserts/linhas** | ver §5 |
| Docs (5 arquivos) | **16 refs de CP + 1 "Support" + 1 "nine"** | ver §6 |

## 4. i18n

### 4.1 Locales existentes

`glob app/src/main/res/values*/strings.xml` → **3 locales**:

| Diretório | Papel | strings.xml |
|---|---|---|
| `values/` | default (inglês) | 1136 linhas |
| `values-ja/` | japonês | 1058 linhas |
| `values-pt-rBR/` | português (Brasil) | 1126 linhas |
| `values-v29/` | qualificador de API — **só `themes.xml`**, sem strings | — |

Strings de CP existem **apenas** nos 3 `strings.xml` (varredura em todos os `res/**/*.xml`: nenhuma
outra chave/arquivo contém CP/incentiv/応援/pontos de comando). Os demais `values*/<arquivo>.xml`
(`tamer_arena.xml`, `immersion.xml`, `quests.xml`, etc.) não têm chaves de CP.

### 4.2 Chaves afetadas (todas as 9 existem nos 3 locales)

| chave | values (en) | values-ja | values-pt-rBR | referências no código | ação |
|---|---|---|---|---|---|
| `ui_battle_cmd_support` | `Support · %1$d` (821) | `応援 · %1$d` (731) | `Apoiar · %1$d` (800) | `BattleChrome.kt:721`; `OfflineTrainingBattleScreen.kt:2273` | **REMOVER** |
| `ui_battle_feed_support` | `%1$s got cheered · +%2$d CP` (854) | `%1$sを応援 · +%2$dCP` (764) | `%1$s recebeu incentivo · +%2$d CP` (833) | `OfflineTrainingBattleScreen.kt:2864` | **REMOVER** |
| `ui_battle_feed_support_window` | `%1$s can be cheered for %2$d s` (855) | `%1$sへの応援可能 · あと%2$d秒` (765) | `%1$s pode receber incentivo por %2$d s` (834) | `OfflineTrainingBattleScreen.kt:2865` | **REMOVER** |
| `ui_battle_order_err_support_paused` | `Cheer unavailable while paused.` (987) | `一時停止中は応援不可。` (897) | `Incentivo indisponível durante a pausa.` (966) | `OfflineTrainingBattleScreen.kt:2570` | **REMOVER** |
| `ui_battle_order_err_support_no_window` | `No cheer window active.` (988) | `応援ウィンドウがありません。` (898) | `Não há janela de incentivo ativa.` (967) | `OfflineTrainingBattleScreen.kt:2571` | **REMOVER** |
| `ui_battle_order_err_support_window_expired` | `Cheer window closed.` (989) | `応援ウィンドウが閉じました。` (899) | `A janela de incentivo terminou.` (968) | `OfflineTrainingBattleScreen.kt:2572` | **REMOVER** |
| `ui_battle_result_stats` | `Heals %1$d · orders %2$d · cheers %3$d · items %4$d · projectiles %5$d/%6$d` (848) | `回復%1$d · 命令%2$d · 応援%3$d · アイテム%4$d · 弾%5$d/%6$d` (758) | `Curas %1$d · ordens %2$d · incentivos %3$d · itens %4$d · projéteis %5$d/%6$d` (827) | `BattleChrome.kt:1101-1108` | **ATUALIZAR**: 6→5 placeholders (remover cheers/incentivos/応援); call site 6→5 args |
| `ui_battle_loadout_cost` | `%1$d EN · %2$d CP · full field` (906) | `EN%1$d · CP%2$d · 全体攻撃` (816) | `%1$d EN · %2$d CP · campo inteiro` (885) | `TechniqueLoadoutScreen.kt:292` | **ATUALIZAR**: remover `%2$d CP` + arg `commandPointCost` |
| `ui_battle_no_resources` | `Not enough energy or command points` (837) | `エナジーかコマンドポイントが不足` (747) | `Energia ou pontos de comando insuficientes` (816) | `OfflineTrainingBattleScreen.kt:2563,2739` | **ATUALIZAR texto** (só energia); chave permanece |

Resumo: **6 chaves removidas + 3 atualizadas, × 3 locales = 27 entradas** a tocar.

### 4.3 Chaves relacionadas que permanecem

| chave | en | ja | pt-rBR | referência | motivo |
|---|---|---|---|---|---|
| `ui_battle_strategy_support` | `Support` (805) | `サポート` (715) | `Suporte` (784) | `OfflineTrainingBattleScreen.kt:2900`, `ArenaWidgets.kt:188` | `BattleStrategy.SUPPORT` (manter) |
| `arena_plan_support` (`tamer_arena.xml:103`) | `Supports the team...` | `チームを支援し...` | `Apoia a equipe...` | `ArenaBriefing.kt:98` | `TamerStyle.SUPPORT` (manter) |
| `ui_battle_deck_commands` | `COMMANDS` (828) | `コマンド` (738) | `COMANDOS` (807) | `BattleChrome.kt:640`, `OfflineTrainingBattleScreen.kt:2171` | rótulo genérico do band (manter) |
| `ui_battle_deck_hint` | `Battle commands` (944) | `バトルコマンド` (854) | `Comandos da luta` (923) | `OfflineTrainingBattleScreen.kt:610` | hint genérico (manter) |

## 5. Testes

### 5.1 Testes com acoplamento a CP (11 arquivos)

| arquivo:linha | teste | constrói/verifica | ação |
|---|---|---|---|
| `app/src/test/.../battle/ui/offline/BattleCommandDeckModelTest.kt:10-36` | `commandDeckAlwaysKeepsTheDecodeInspiredThreeByThreeOrder` | deck com 9 slots na ordem fixa (SUPPORT incluso) e `assertEquals(9, deck.size)` | atualizar: 8 slots, renomear teste (não é mais 3×3) |
| `.../BattleCommandDeckModelTest.kt:38-58` | `commandAvailabilityDoesNotRemoveOrReorderDisabledActions` | `assertTrue(deck[SUPPORT].enabled)` quando `supportReady=true` | remover asserções de SUPPORT |
| `.../BattleCommandDeckModelTest.kt:60-74` | `targetDependentActionsBecomeAvailableOnlyWithALiveTarget` | `assertFalse(deck[SUPPORT].enabled)` | remover asserção de SUPPORT |
| `.../battle/offline/BattleSimulatorTest.kt:166-174` | `supportIsImmediateAndOnlyRewardsOncePerWindow` | janela `pendingSupportCombatantIds`, +8 CP, segunda ordem falha | **remover teste** |
| `.../BattleSimulatorTest.kt:512-524` | `reservationPreventsTwoAlliesSpendingTheSameCommandPoints` | `commandPointCost=20`, `BattleConfiguration(commandPoints=25)`, `reservedCommandPoints` 20→0, `commandPoints` 5 | reescrever para reserva só de energia **ou** remover |
| `.../BattleSimulatorTest.kt:526-546` | `innateSpecialRequiresItsOwnFullGaugeInsteadOfCommandPointsOrEnergy` | `commandPointCost=0`, `commandPoints=0` | remover args CP; manter semântica de especial |
| `.../BattleSimulatorTest.kt:649-661` | `expiryReleasesResourcesEvenWhilePartnerIsDefending` | `commandPointCost=20`, `commandPoints=30`, `reservedCommandPoints` | remover trechos CP; manter energia |
| `.../BattleSimulatorTest.kt:674-684` | `supportIsRejectedWhilePausedAndWindowRemainsAvailable` | pausa/retomada da janela, +8 CP | **remover teste** |
| `.../BattleSimulatorTest.kt:686-693` | `enemyAutomationCannotSpendTrainersCommandPoints` | `commandPointCost=10`, `commandPoints=50` inalterado | **remover teste** (não há mais CP) |
| `.../BattleSimulatorTest.kt:729-734` | `invalidCostsAndTeamsAreRejectedAtConstruction` | `BattleConfiguration(maxCommandPoints = -1)` lança | remover essa asserção |
| `.../BattleSimulatorTest.kt:452,593` (+ helper l.24) | setups | `BattleConfiguration(commandPoints=100)`, `commandPointCost=0`, `BattleConfiguration()` | remover args CP (defaults deixam de existir) |
| `.../battle/offline/BattleFinisherTest.kt:10,24-27` | helper `simulator(commandPoints=...)` e `special.commandPointCost=5` | configuração/ técnica com CP | remover params/arg |
| `.../BattleFinisherTest.kt:102-156` (108,115,148) | `launchConsumesWindowButNeverResolvesOrdinaryStartupBeforeImpact` | `assertEquals(95, commandPoints)` antes/depois do finisher | remover 3 asserções de CP |
| `.../BattleFinisherTest.kt:158-188` (165) | `allOrdinaryTimersProjectilesDotAndReservationsAreFrozen` | `BattleConfiguration(commandPoints = 100)` | remover arg |
| `.../BattleFinisherTest.kt:336-362` (338) | `duoPresentationDoesNotGrantMissingEnergyCommandPointsOrReadiness` | matriz `"energy"/"command"/"readiness"`; caso `command` usa `commandPoints=5` | remover caso `command` e renomear teste |
| `.../battle/offline/DuoBlastTest.kt:268-273` | helper `snapshotWith(form)` (teste de `resolveVisibleFighters`) | `BattleSnapshot(... commandPoints=0, maxCommandPoints=100, pendingSupportCombatantIds=emptySet())` | remover os 3 campos |
| `.../battle/ui/offline/BattleVfxCueTest.kt:10-22` | helper `snapshot(events)` | idem (3 campos de CP/support) | remover os 3 campos |
| `.../battle/offline/session/BattleSceneChannelTest.kt:15-27` | helper `snapshot(...)` | idem (3 campos); nenhum teste cobre a transição de support explicitamente | remover os 3 campos; avaliar teste substituto (não há o que testar) |
| `.../battle/offline/GenericTechniqueCatalogTest.kt:73` | `trainingFactoryUsesARangeCompleteNeutralLoadoutInsteadOfPracticePlaceholders` | `assertEquals(0, special.commandPointCost)` | remover asserção |
| `app/src/androidTest/.../battle/ui/offline/OfflineTrainingBattleScreenTest.kt:49-81` | `commandBandKeepsNineStableCutCornerKeys` | `assertEquals(9, commandSlots.size)`; todas visíveis, 48dp | atualizar 9→8 e renomear |
| `.../OfflineTrainingBattleScreenTest.kt:84-112` (107-111) | `radarBattleKeepsTheFullBleedSurfaceAndCommandBand` | `assertEquals(9, ...)` no Radar | atualizar 9→8 |
| `app/src/androidTest/.../OfflineBattleFinisherPresentationTest.kt:29,53-54,111-117` | `repeatedFinishersKeepArenaAndEveryCommandStationaryInBothOrientations` | `assertEquals(9, commands.size)` + comparação de bounds de todos os 9 slots | atualizar 9→8 |
| `app/src/androidTest/.../ArenaPersistenceTest.kt:58` | helper de snapshot | `BattleSnapshot(34, false, null, 0, 100, members(...), ...)` — args **posicionais** (commandPoints, maxCommandPoints) | trocar por argumentos nomeados sem CP (quebra de compilação garantida) |

### 5.2 Impacto de compilação (além dos testes acima)

- Helpers que instanciam `BattleSnapshot(...)` com CP: `BattleVfxCueTest.kt:10`, `DuoBlastTest.kt:268`,
  `BattleSceneChannelTest.kt:15`, `ArenaPersistenceTest.kt:58`. Só o último usa posicionais.
- `BattleConfiguration()` com defaults continua compilando em ~25 testes; aquecedores que passam
  `commandPoints=`/`maxCommandPoints=` quebram (listados acima).
- `BattleSnapshot` posicional em produção: `BattleSimulator.kt:486` (núcleo, `inventariador-core`).
- Não há teste para `ui_battle_result_stats`, `ui_battle_loadout_cost` ou para o texto de
  `ui_battle_no_resources`; mudanças de formato de string não são pegas por teste JVM (só lint/build).

## 6. Documentação e afins

Varredura: `*.md` raiz + `docs/` + `analysis/` + `benchmark/` + `scripts/` + `tools/`.
**Ocorrências literais de CP/Command Points/incentiv: 16** (17º match é falso positivo: "incentiva farm",
`OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:482`). `docs/`, `analysis/`, `benchmark/`, `scripts/`, `tools/`:
**zero** ocorrências.

| file:line | trecho | classificação | ação |
|---|---|---|---|
| `CHANGELOG.md:47-53` (51 CP, 52 "nine 48dp cut-corner keys") | entrada histórica "Battle screen is now a full-bleed arena" descreve o CP instrument e 9 teclas | histórico | **não editar o histórico**; adicionar nova entrada de remoção do CP (com "8 teclas", band sem instrumento) |
| `DESIGN.md:734` | "holds the CP instrument, the selected partner's name..." | atualizar | remover o instrumento CP da descrição do band |
| `DESIGN.md:735` | "one polite live event/feedback line and nine 48dp cut-corner keys" | atualizar | "eight" |
| `DESIGN.md:736` | "orders (Defend, Support, Special) and movement..." | atualizar | "orders (Defend, Special) and movement..." |
| `DESIGN.md:738-739` | "Portrait uses three columns; wide windows and landscape use five columns in two rows." | revalidar | código atual usa 9 colunas no wide landscape (`BattleChrome.kt:703`); ajustar texto junto da troca 9→8 |
| `OFFLINE_BATTLE_3D_PLAN.md:83` | "Comandos diretos: **Incentivar**, **Defender** e **Especial**" | atualizar | remover "Incentivar" dos comandos diretos |
| `OFFLINE_BATTLE_3D_PLAN.md:88` | "janela de incentivo possui sinal visível, opção de som/háptica..." | atualizar/remover | remover janela de incentivo |
| `OFFLINE_BATTLE_3D_PLAN.md:96` | "ordens, incentivo e ameaça" (camadas do core) | atualizar | remover "incentivo" |
| `OFFLINE_BATTLE_3D_PLAN.md:124` | "Pausa congela ... janelas de incentivo. Incentivo não aceita ganhos enquanto pausada." | atualizar | remover referências a incentivo/CP na pausa |
| `OFFLINE_BATTLE_3D_PLAN.md:160` | "um saldo de Command Points do treinador compartilhado entre os parceiros" | remover/nota | marcar superseded (regra nunca mais existirá) |
| `OFFLINE_BATTLE_3D_PLAN.md:162` | "Incentivo abre uma janela ... Especial consome energia/CP" | atualizar | remover janela/CP; especial só energia |
| `OFFLINE_BATTLE_3D_PLAN.md:178` | "Resultado apresenta duração, dano, defesas, incentivos, ordens..." | atualizar | remover "incentivos" |
| `OFFLINE_BATTLE_3D_PLAN.md:222` | "### Etapa 7 — Incentivo, CP e especiais — implementada..." | atualizar | marcar Etapa 7 substituída pela remoção de CP |
| `OFFLINE_BATTLE_3D_PLAN.md:224` | "Adicionar janelas de incentivo ... saldo/reservas do treinador..." | atualizar | idem |
| `OFFLINE_BATTLE_3D_PLAN.md:226` | "observar → incentivar → ganhar CP → defender/ordenar especial..." | atualizar | critério de aceite não se aplica mais |
| `OFFLINE_BATTLE_3D_PLAN.md:259` | debug: "energia, CP/reservas, cooldowns..." | atualizar | remover CP/reservas do painel (espelha `OfflineTrainingBattleScreen.kt:657`) |
| `OFFLINE_BATTLE_3D_PLAN.md:273` | "- CP compartilhado pelo treinador, energia e ordens por parceiro." | remover | regra extinta |
| `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:458` | "Definir como CP, incentivo, Blast, FORM, DUO e Jogress são exibidos..." | atualizar | remover CP/incentivo da lista |
| `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:121` | "Existe infraestrutura de CP e incentivo (+8), mas ... custo de CP padrão zero" | atualizar | trocar por "não há CP; técnicas custam apenas EN" |
| `TESTING.md:535-582` (547, 564) | seção do battle screen cita `OfflineTrainingBattleScreenTest`, `command band` e contagens de teste (979 JVM) | atualizar | sem CP literal; adicionar seção/evidência da remoção, renomear testes "Nine"→"Eight" e atualizar contagens |
| `AR_BATTLE_IMPLEMENTATION.md:70,542,638` | "command deck/command band" (layout AR/Radar), **sem CP** | manter | nada a remover; opcional atualizar nomenclatura deck→band se o layout AR mudar |
| `README.md:342` | "Japanese and Brazilian Portuguese string resources" | manter | sem mudança (locales continuam iguais) |
| `docs/testing/HISTORICAL_REGRESSION_LOG.md:2758` | "command deck opens Radar..." | manter | log histórico, sem CP |

## 7. Riscos e pontos de atenção

1. **Band de 9→8 teclas — duas implementações.** `BattleChrome.BattleCommandBand` (treino/Arena/Radar)
   e os privados `BattleCommandDeckHeader`/`BattleCommandBar` (AR) duplicam a lógica. Ambas precisam
   perder `SUPPORT` e o instrumento CP; esquecer a AR deixa o CP "meio removido" (compilação falha
   porque os call sites passam CP, mas a regra visual ficaria inconsistente).
2. **Layout/colunas.** Hoje: `BattleChrome`: 9 colunas se landscape e `maxWidth>=560dp`; 5 se
   landscape `>=420dp`; 5 se `>=560dp`; senão 3. Com 8 teclas: 8 col em uma linha (wide landscape),
   5+3 (landscape/wide) e 3+3+2 (portrait). `OfflineTrainingBattleScreen:2264` (AR) usa `9 else 3` —
   em portrait AR o último row passa a ter 2 teclas + spacer. Validar espaçamento das teclas
   (peso/`repeat(columns-rowSlots.size){ Spacer(Modifier.weight(1f)) }`).
3. **Testes instrumentados que fixam 9.** Três pontos: `OfflineTrainingBattleScreenTest.kt:67-71`,
   `:107-111` e `OfflineBattleFinisherPresentationTest.kt:54`; os nomes dos testes ("Nine") e o teste
   de bounds dos 9 slots precisam acompanhar. O teste de layout init (`battleSurfaceFillsTheWindow...`)
   não depende de CP.
4. **Altura/insets do band.** O instrumento CP (texto + `LinearProgressIndicator` 3dp) é medido por
   `onSizeChanged` (`OfflineTrainingBattleScreen.kt:629,1489` → `commandBandHeightPx`/`radarBandInset`,
   usados pelo HUD de combatentes e overlays). Remover a barra encolhe o band: revalidar overlap
   HP/MP/ESP, placas de inimigo, banners e overlays de Blast.
5. **Estatísticas de resultado (format args).** `ui_battle_result_stats` tem 6 placeholders e o call
   site passa 6 valores; após remover `supportCommands` a string precisa ter 5 placeholders **nos 3
   locales** e o call site 5 args. Divergência = `MissingFormatArgumentException` em runtime (nenhum
   teste JVM cobre esse texto).
6. **Strings órfãs / paridade de locales.** As 6 chaves removidas existem nos 3 locales: remover das 3
   fontes (sem sobras em `values-ja`/`values-pt-rBR`). As 3 atualizadas precisam ser reescritas nos 3
   locales (fallback silencioso do default esconde tradução faltante). `values-v29` não tem strings.
7. **Acessibilidade.** As teclas do band são `Surface`+`Text`, então o TalkBack lê o label; remover a
   tecla "Support · N" e a linha "CP x/y" muda a ordem de foco e a leitura. Manter 48dp, grupos
   (menus/ordens/movimento) e o `liveRegion` do feedback. Nenhum `contentDescription` específico de CP
   existe (`BattleAccessibility.kt` só cobre seleção de alvo).
8. **Elegibilidade de técnica.** `TechniquesDialog` (`OfflineTrainingBattleScreen.kt:2722-2723`) usa CP
   como segundo recurso; passa a energia-only. A mensagem `ui_battle_no_resources` (também usada em
   `orderFailureText` para `RESOURCES_MISSING`) muda de texto nos 3 locales.
9. **Debug.** Linha 657 do painel "Diagnóstico da IA" mostra `CP x/y · reservado z`; remover sem quebrar
   o restante do texto (`projéteis`/`eventos`) e o harness `BattleBalanceRunner` (debug) não usa CP.
10. **Arena vs. treino/radar.** Arena herda o band (CP) mas usa `ArenaResult` próprio, que não mostra
    `supportCommands`; portanto o único painel de resultado afetado é `BattleResultPanel`
    (`BattleChrome.kt:1050`) — usado por treino offline e Radar. Não "corrigir" `ArenaResult` à toa.
11. **`pendingSupportCombatantIds` no canal de UI.** `BattleSceneChannel.kt:57` publica snapshot
    imediato na troca de janela de suporte; removido o campo, o throttle continua correto sem regra
    extra. Teste `BattleSceneChannelTest` só constrói o snapshot (não testa a transição).
12. **Strings "cheer"/"Apoiar" fora de CP.** Não confundir com `ui_battle_strategy_support` /
    `arena_plan_support` (estratégias) — ficam.

## 8. Checklist de aceite sugerido (UI/i18n/testes/docs)

- [ ] `BattleCommandBand`: sem params CP/`supportReady`/`onSupport`; sem instrumento CP; 8 teclas; KDoc
      e comentários "nine" atualizados.
- [ ] `BattleCommandBar`/`BattleCommandDeckHeader` (AR): sem CP/support; instrumento removido; colunas
      ajustadas para 8 teclas.
- [ ] `BattleCommandDeckModel`: `SUPPORT` removido de enum/availability/deck; teste JVM atualizado (8).
- [ ] `orderFailureText` sem branches `SUPPORT_*`; `TechniquesDialog` energia-only; `eventSummary` sem
      eventos de support; debug sem CP/reservado.
- [ ] `BattleResultPanel`: `ui_battle_result_stats` com 5 args; `TechniqueLoadoutScreen` sem
      `commandPointCost`; `TamerTrainerController` sem auto-Support; `BattleSceneChannel` sem
      `pendingSupportCombatantIds`.
- [ ] 6 chaves removidas + 3 atualizadas nos 3 locales; nenhuma chave órfã; strings de estratégia
      intactas.
- [ ] Testes: 11 arquivos ajustados; `:app:testIntegrityCheckUnitTest` e
      `:app:assembleIntegrityCheckAndroidTest` verdes; asserts 9→8 nos 3 pontos; `ArenaPersistenceTest`
      com `BattleSnapshot` nomeado.
- [ ] Docs: DESIGN/3D_PLAN/RECOMMENDATIONS/VICE atualizados; CHANGELOG ganha entrada nova (histórico
      antigo preservado); TESTING com evidência; AR/README/docs/analysis sem mudanças.
