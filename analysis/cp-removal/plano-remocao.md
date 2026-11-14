# Plano de remoção do sistema de Command Points (CP)

> **Repo:** `C:\Users\julye\IdeaProjects\vbhelper` · **Data:** 07/11/2026
> **Base:** código real + [`pesquisa-jogos.md`](pesquisa-jogos.md), [`inventario-core.md`](inventario-core.md),
> [`inventario-ui-testes.md`](inventario-ui-testes.md). Nenhum código de produção foi alterado.
> **Revisão adversarial:** [`revisao-plano.md`](revisao-plano.md) — veredito "ajustes necessários";
> os 8 pontos P1–P8 foram incorporados abaixo.
> **Enquadramento:** os arquivos `OFFLINE_BATTLE_DW1*` são fonte de informação (o DW1 não tem CP);
> o plano é justificado pelo código/produto, não por fidelidade histórica.

## 0. Fato que decide o plano

O CP **não tem efeito de gameplay em produção**:

- `TechniqueDefinition.commandPointCost` default = `0`; o único valor explícito de produção é `0`
  (`GenericTechniqueCatalog.kt:170`). Nenhuma das 57 técnicas/`counter`/`BASIC` custa CP.
- A única entrada é `TrainerAction.Support` (+`min(8, teto − saldo)`), disparado por janela de 1 s
  aberta a cada acerto de dano (`BattleSimulator.kt:1377-1381`).
- Logo, no app real o saldo de CP só sobe; gate de custo, reserva de CP e pool inimigo são no-ops.
- Só testes exercitam custo de CP.

Consequência: remover o CP é uma **limpeza de código/UX**, não uma mudança de balanceamento. O único
efeito observável é a perda do **incentivo** e dos textos/estatísticas associados.

## 1. Decisão de arquitetura e destino do Support

**Recomendado: remover CP + comando Support/incentivo juntos.**

| Opção | O que acontece | Custo | Veredito |
|---|---|---|---|
| **A. CP + Support juntos** | Remove recurso, janela, botão, eventos, estatística, strings, testes e docs. Band 9→8 teclas. | Médio; maior parte mecânica, guiada pelo compilador | **Recomendado** |
| B. Manter Support "cosmético" | Botão existe sem efeito; pior UX e código morto | Baixo, mas pior produto | Rejeitado |
| C. Transformar Support em outro efeito (ex.: buff de prontidão, recurso pessoal tipo OP do Next Order) | Nova mecânica, design e balanceamento | Alto; escopo separado | Possível **depois**, sobre a remoção já feita |
| D. Remover só o CP mantendo Support com outro efeito mínimo | Efeito improvisado, sem design; ainda mexe em testes/string | Médio-alto | Não recomendado |

**Mantêm-se intactos (não são CP):** `BattleStrategy.SUPPORT`, `TamerStyle.SUPPORT`,
`TechniqueKind.SUPPORT/HEAL`, `isSupport()` (cura/suporte), `ui_battle_strategy_support`,
`arena_plan_support`, e as ordens foco/defender/técnica/item/estratégia/aproximar/recuar/especial/Blast.

**Modelo final do combate:** energia individual + prontidão/cooldown + gauge de especial + ordens do
treinador (sem recurso de comando). É o que já roda hoje, menos o CP decorativo.

## 2. Fases (commits pequenos por fase; Fases 1–3 são UMA unidade de compilação)

> O build só volta a ficar verde ao fim da Fase 4 (UI). A Fase 1 é intencionalmente destructive e o
> compilador vira o inventário de call sites. Cada fase termina em **commit**, não necessariamente em
> build verde isolado.

### Fase 0 — Preparação (0,5 h)

1. Criar branch/worktree isolada (a árvore tem alterações não commitadas, incluindo `BattleChrome.kt`
   e docs; não commitar o trabalho alheio).
2. Baseline: `.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2`
   (esperado hoje: ≈1.016 testes, 0 falhas). Guardar a contagem.
3. Confirmar `:app:compileIntegrityCheckKotlin` existe para a Fase 1.

### Fase 1 — Modelo/domínio (1 h) — quebra o build de propósito

`app/src/main/java/com/tamerslab/battle/domain/BattleDefinitions.kt`
- remover `TechniqueDefinition.commandPointCost` (`:142`).

`app/src/main/java/com/tamerslab/battle/offline/core/BattleModel.kt`
- `BattleConfiguration`: remover `commandPoints`, `maxCommandPoints` (`:107-108`).
- `BattleStatistics`: remover `supportCommands` (`:89`).
- `TrainerAction`: remover `data object Support` (`:139`).
- `OrderFailure`: remover `SUPPORT_PAUSED`, `SUPPORT_NO_WINDOW`, `SUPPORT_WINDOW_EXPIRED` (`:166-168`).
- `BattleSnapshot`: remover `commandPoints`, `maxCommandPoints`, `pendingSupportCombatantIds`,
  `reservedCommandPoints`, `opposingCommandPoints` (`:283-284`, `:287`, `:292`, `:305`).
- `BattleEvent`: remover `SupportWindowOpened` e `SupportSucceeded` (`:338-339`).

`app/src/main/java/com/tamerslab/battle/offline/data/GenericTechniqueCatalog.kt`
- remover a linha `commandPointCost = 0` (`:170`).

*Critério da fase:* `:app:compileIntegrityCheckKotlin` falha listando **todos** os consumidores —
é o inventário automático. Contar as falhas esperadas (≈10 arquivos).

### Fase 2 — Simulador (2 h)

`app/src/main/java/com/tamerslab/battle/offline/core/BattleSimulator.kt`
- remover `pendingSupport` (`:56`) e os saldos `commandPoints`/`opposingCommandPoints` (`:72-73`);
- remover `require(maxCommandPoints …)` (`:84`);
- remover o branch `TrainerAction.Support` (`:210-221`);
- `issueSideOrder`/`UseTechnique`: condição só energia e `Reservation(actorId, energy, specialCharge)`
  (`:280`, `:283`);
- `prepareDuoAttack`: remover gate/gasto de CP (`:357-358`, `:362`);
- `snapshot()` (**4 posicionais + 2 nomeados** de CP/incentivo): remover `commandPoints`,
  `configuration.maxCommandPoints`, `pendingSupport.keys.toSet()`,
  `commandPoints - availableCommandPoints()` (`:486-488`) e `opposingCommandPoints = …` (`:503`);
- `step()`: remover expiração de `pendingSupport` (`:520`);
- plano autônomo: remover `special.commandPointCost == 0` (`:903`) e `it.commandPointCost == 0`
  (`:1111`);
- `approachAndExecute`: condição sem CP e sem `spendCommandPoints`, mantendo
  `reservations.remove(orderId)` (energia/special charge) (`:1157`, `:1227-1228`);
- remover bloco de abertura da janela no acerto (`:1377-1381`);
- remover helpers `commandPointsFor`/`spendCommandPoints`/`availableCommandPoints` (`:1820-1825`);
- `defeat`: remover `pendingSupport.remove` (`:1970`); `finish`: remover `pendingSupport.clear()` (`:1995`);
- `validateTechnique`: remover cláusula `commandPointCost >= 0` (`:2032`);
- `Reservation`: remover campo `commandPoints` (`:2177-2182`).

*Critério (grep sem falso positivo):* em `battle/offline/core`, `commandPoint|pendingSupport|SupportWindow|SupportSucceeded`
→ vazio; `isSupport|TechniqueKind.SUPPORT|BattleStrategy.SUPPORT` continuam de propósito.

### Fase 3 — IA e sessão (0,5 h)

- `battle/offline/core/TamerTrainerController.kt:97-99`: remover auto-Support.
- `battle/offline/session/BattleSceneChannel.kt:57`: remover gatilho de `pendingSupportCombatantIds`.

### Fase 4 — UI (3 h) — build volta a verde

`battle/ui/offline/BattleCommandDeckModel.kt`
- remover `BattleCommandSlot.SUPPORT` (`:13`), `BattleCommandAvailability.supportReady` (`:24`) e o
  item do deck (`:39`). O arquivo **não** contém "3×3/9"; atualizar o comentário só se ficar impreciso
  (o "3×3/Nine" está nos **nomes de teste**: `BattleCommandDeckModelTest.kt:10`
  `...ThreeByThreeOrder` e `OfflineTrainingBattleScreenTest.kt:49` `...KeepsNine…`).

`battle/ui/offline/BattleChrome.kt` (`BattleCommandBand`)
- remover `availableCommandPoints`/`maxCommandPoints` (`:566-567`), `supportReady`/`onSupport`
  (`:570`, `:575`); ajustar `battleCommandDeck(...)` (`:600`); remover instrumento CP
  (`:658-674`); remover `BattleCommandSlot.SUPPORT` da **lista de ordem** (`:547`), do label e do
  clique (`:721`, `:746`); atualizar KDoc "nine"→"eight" (`:561`) **e o comentário** "the nine command
  stations…" (`:690-693`); colunas do wide landscape 9→8 (`:703`); resultado:
  `ui_battle_result_stats` sem `statistics.supportCommands` (`:1104`).

`battle/ui/offline/OfflineTrainingBattleScreen.kt` (3 bandas + debug + falhas + feed + gate)
- banda treino/Arena (`:599-630`): remover CP/support/onSupport;
- debug (`:657`): texto sem `CP … reservado`;
- AR: `BattleCommandDeckHeader` (`:1247-1256`) perde CP + instrumento (`:2155-2191`); `BattleCommandBar`
  (`:1257-1291`) perde CP/support (`:2229-2230`, `:2257`, `:2273`, `:2294`), colunas 9→8 (`:2264`);
- Radar (`:1449-1491`): idem treino;
- `orderFailureText` (`:2570-2572`): remover 3 branches `SUPPORT_*`;
- `TechniquesDialog` (`:2721-2723`): elegibilidade só energia;
- `eventSummary` (`:2864-2865`): remover 2 branches de evento.
- manter `BattleStrategy.SUPPORT` (`:2900`).

`battle/ui/offline/TechniqueLoadoutScreen.kt:292`: `ui_battle_loadout_cost` com 1 argumento.

*Critério:* `:app:assembleDebug` compila; HUD sem CP; 8 teclas; revisar layout portrait (3 col),
wide (5 col) e landscape (8 col ou spacer), alturas/insets medidos por `onSizeChanged`, TalkBack
(labels/semântica).

### Fase 5 — i18n (0,5 h) — 3 locales: `values` (en), `values-ja`, `values-pt-rBR`

Remover (6 chaves): `ui_battle_cmd_support`, `ui_battle_feed_support`,
`ui_battle_feed_support_window`, `ui_battle_order_err_support_paused`,
`ui_battle_order_err_support_no_window`, `ui_battle_order_err_support_window_expired`.

Reformatar (3 chaves) — **atenção P1: renumerar placeholders, não só apagar o `%3$d`**:
- `ui_battle_result_stats`: reescrever as 3 strings com `%1$d..%5$d` sequenciais
  (heals, orders, items, projectiles hit, projectiles missed) e ajustar o call site para 5 args na
  mesma ordem; apagar só `cheers/%3$d` e manter `%4..%6` causaria `MissingFormatArgumentException`.
- `ui_battle_loadout_cost`: 2→1 placeholder (`%1$d EN · campo inteiro`); call site 1 arg.
- `ui_battle_no_resources`: texto só energia ("Not enough energy" / "Energia insuficiente" /
  "エナジーが不足"); chave permanece (usada por `RESOURCES_MISSING` e pela técnica sem energia).

*Critério:* `:app:lintDebug` sem chaves órfãs/format args inválidos; nenhuma chave de CP no res.

### Fase 6 — Testes (2,5 h)

Remover (3): `supportIsImmediateAndOnlyRewardsOncePerWindow`,
`supportIsRejectedWhilePausedAndWindowRemainsAvailable`,
`enemyAutomationCannotSpendTrainersCommandPoints` (`BattleSimulatorTest.kt`).
Reescrever: `reservationPreventsTwoAlliesSpendingTheSameCommandPoints` →
`reservationPreventsTwoAlliesSpendingTheSameEnergy`; `expiryReleasesResourcesEvenWhilePartnerIsDefending`
sem CP; `innateSpecialRequiresItsOwnFullGauge…` sem args CP; remover asserção
`maxCommandPoints = -1`.
Limpar fixtures: `BattleFinisherTest` (arg de técnica, helper `commandPoints`, 3 asserts de CP, caso
"command" do DUO), `BattleCommandDeckModelTest` (8 slots; remover `supportReady`/`SUPPORT`; renomear
`...ThreeByThreeOrder`), `BattleVfxCueTest`, `DuoBlastTest`, `BattleSceneChannelTest`,
`GenericTechniqueCatalogTest`, `BattleSimulatorTest` (setups).
androidTest (3): `OfflineTrainingBattleScreenTest` 2× `assertEquals(9→8)` + renomear
`commandBandKeepsNineStableCutCornerKeys`; `OfflineBattleFinisherPresentationTest:54` 9→8;
`ArenaPersistenceTest:58` migrar `BattleSnapshot` para argumentos **nomeados** sem os 5 campos.
Adicionar:
- teste JVM de **tolerância a envelope legado**: injetar `"commandPoints": 7, "maxCommandPoints": 42`
  no objeto `configuration` de um `definitionsJson` antigo, reabrir e garantir mesmo HP/energia/outcome;
- teste do deck com 8 slots e ordem estável.

Comandos:
```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.tamerslab.battle.*"
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=2
.\gradlew.bat :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```
Execução instrumentada em aparelho/emulador (`:app:connectedIntegrityCheckAndroidTest`) fica
**declarada fora do escopo obrigatório**, mas os testes de 8 teclas devem rodar antes de fechar.

### Fase 7 — Docs/CHANGELOG (1 h)

- `CHANGELOG.md`: **nova** entrada em `[Unreleased] — Alpha 0.7.0` ("Removed: Command Points…"),
  sem reescrever a entrada histórica (`:51-52`).
- `DESIGN.md:734-736`: band sem instrumento de CP, "eight" teclas, orders (Defend, Special) e movement;
  revalidar a frase de colunas (`:738-739`) com o layout final.
- `OFFLINE_BATTLE_3D_PLAN.md`: marcar CP/Etapa 7/incentivo como **superseded** (`:83`, `:88`, `:96`,
  `:124`, `:160`, `:162`, `:178`, `:222-226`, `:259`, `:273`).
- `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:121`: "não há CP; técnicas custam apenas EN".
- `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:458`: remover CP/incentivo da lista de apresentação.
- `TESTING.md`: evidência da remoção; atualizar contagem de testes e as menções "nine/979"
  (`:535-582`, `:720`) coerentes com o número novo.
- `DigimonWorldBattleEspecifications.txt`: documento de pesquisa/design histórico — **anotar** que o CP
  foi removido; não reescrever sem ordem explícita.
- **Anotar como snapshot pré-remoção** (data + commit) este `README.md` e os dois inventários, para não
  virarem documentação enganosa depois da execução.

## 3. Checkpoint e compatibilidade (decisão)

- **Manter `formatVersion = 1`** e **não** alterar `CATALOG_VERSION`/Room.
- Gson default ignora campos JSON desconhecidos; após remover os campos, registros antigos continuam
  carregando e os valores de CP são descartados. `BattleConfiguration` tem todos os parâmetros com
  default (construtor sem args), então campos ausentes também não quebram.
- Replay **não muda**: batalhas NPC nunca emitem ordens, custos de produção são 0, RNG não deriva de CP.
  O que muda é só `eventCount`/`recentEvents`/`statistics` — nada persistido (`NpcBattleSummary` só
  guarda elapsed, membros HP/EN e outcome).
- Único risco residual: downgrade do app (não previsto). O teste de envelope legado da Fase 6 documenta
  a tolerância; a infra de `NpcBattleVersionTest.kt:56-64` serve de base.

## 4. Riscos priorizados

| # | Risco | Mitigação |
|---|---|---|
| 1 | Construtor **posicional** de `BattleSnapshot` (produção + androidTest) desloca 4 args | migrar para nomeados antes de remover campos |
| 2 | Esquecer uma das **3 bandas de UI** (treino/Arena, Radar, AR) ou o slot `:547`/comentário `:690-693` | checklist por layout; teste androidTest 8 teclas nos 3 pontos |
| 3 | Layout 9→8 (portrait 3 col, wide 5, landscape 8; altura/insets; TalkBack) | revisão visual + ajuste `onSizeChanged`; decidir 4×2 vs spacer |
| 4 | **Placeholders i18n desalinhados** (`result_stats` 6→5 precisa renumerar; `loadout_cost` 2→1) | lint/build + conferência dos 3 locales |
| 5 | `when` exaustivos de `TrainerAction`/`BattleEvent`/`OrderFailure` | compilador aponta; remover branches em UI/IA |
| 6 | Docs históricos desatualizados | marcar superseded, não apagar; CHANGELOG nova entrada |
| 7 | Suíte grande (≈1.016 testes) com fixtures | rodar suíte completa; contagem esperada 1.014 (−3 removidos, +1 novo) |

**Esforço estimado por fase:** 0,5 + 1 + 2 + 0,5 + 3 + 0,5 + 2,5 + 1 ≈ **11 h** (~1,5 dia focado),
sendo ~3 h de UI/layout e ~2,5 h de testes. Quanto mais cedo a decisão do layout de 8 teclas, menor o
retrabalho.

## 5. Definição de pronto

- `rg -n "commandPoint|CommandPoint|pendingSupport|SupportWindowOpened|SupportSucceeded|TrainerAction\.Support|SUPPORT_PAUSED|SUPPORT_NO_WINDOW|SUPPORT_WINDOW_EXPIRED|supportCommands|reservedCommandPoints|opposingCommandPoints|supportReady|onSupport|availableCommandPoints" app/src`
  → **vazio** (fora do histórico em docs; `isSupport|TechniqueKind.SUPPORT|BattleStrategy.SUPPORT` são exceções conscientes).
- `rg -n "commandPointCost" app/src` → **vazio**.
- Grep de strings `CP|cheer|incentivo|応援` em `values*/strings.xml` → só termos não relacionados.
- `:app:testIntegrityCheckUnitTest` (≈1.014), `:app:lintDebug`, `:app:assembleDebug`,
  `:app:compileIntegrityCheckAndroidTestKotlin` e `:app:assembleIntegrityCheckAndroidTest` verdes;
  execução em device dos testes de 8 teclas registrada.
- Save NPC anterior ao update recupera a batalha normalmente (mesmo HP/energia/outcome).
- CHANGELOG/DESIGN/docs atualizados; README/inventários anotados como snapshot pré-remoção.

## 6. "Se eu fosse fazer só o mínimo"

É tecnicamente possível remover **apenas o CP** e manter `TrainerAction.Support` mudando o prêmio
(ex.: conceder 8 de prontidão/energia ou um buff de 1 s). Porém:

- não é "mínimo" de verdade: troca o corpo do branch, mexe em eventos, UI, estatística, strings e
  testes de qualquer forma;
- cria um efeito novo sem design/balanceamento (e o incentivo atual nem sequer é usado pela IA do
  jogador fora da Arena);
- deixa o botão "Apoiar" com semântica improvisada.

**Recomendação:** não fazer o mínimo; executar as 7 fases em **um PR com commits pequenos por fase**
(modelo → núcleo → UI → testes → docs). Se o objetivo for apenas "tirar o CP da tela/HUD", a Fase 4
sozinha já remove o instrumento e a tecla, mas o código morto continuaria — não resolve a dívida.
