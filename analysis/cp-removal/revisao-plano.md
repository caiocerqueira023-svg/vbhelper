# Revisão adversarial do plano de remoção de CP

> **Revisor:** `revisor-plano` · **Data:** 07/11/2026 · **Task:** `task-4`
> **Alvo:** [`plano-remocao.md`](plano-remocao.md), confrontado com os inventários
> [`inventario-core.md`](inventario-core.md) e [`inventario-ui-testes.md`](inventario-ui-testes.md)
> e com o código real (greps/reads re-executados; nenhum código de produção alterado).
> **Timebox:** revisão focada (grep + trechos críticos), não auditoria integral arquivo a arquivo.

## Veredito: **AJUSTES NECESSÁRIOS** (não bloqueiam iniciar Fases 0–3; P1/P2 precisam entrar antes da Fase 4/5)

O plano está tecnicamente correto na arquitetura (CP + Support juntos, checkpoint sem bump, replay
inalterado) e a cobertura de ocorrências é quase total. Há **1 risco de crash em runtime não
explicitado** (P1), **2 ocorrências reais fora do checklist de UI** (P2) e imprecisões menores de
contagem/critério/comandos (P3–P8).

---

## 1. Cobertura das ocorrências — greps re-executados

Padrão principal em `app/src`: `commandPoint(s)|commandPointCost|reservedCommandPoints|opposingCommandPoints|pendingSupport|SupportWindowOpened|SupportSucceeded|TrainerAction.Support|SUPPORT_PAUSED|SUPPORT_NO_WINDOW|SUPPORT_WINDOW_EXPIRED|supportCommands|supportReady|onSupport`; mais chaves `ui_battle_*support*`,
`ui_battle_no_resources`, `ui_battle_loadout_cost`, `ui_battle_result_stats` nos 3 `strings.xml` e
`assertEquals(9`/`Nine`/`commandSlots` em `androidTest`. Resultado: **160 matches**, compatível com os
inventários (≈102 linhas em 10 arquivos de produção; 7 arquivos JVM + 3 androidTest; 27 entradas i18n).

| Arquivo / alvo | Ocorrências reais | Cobertura do plano |
|---|---|---|
| `BattleSimulator.kt` (36 linhas: 56–2180) | todas as linhas citadas conferem | **OK** |
| `BattleModel.kt` (89,107-108,139,166-168,283-287,292,305,338-339) | confirmadas | **OK** |
| `BattleDefinitions.kt:142`, `GenericTechniqueCatalog.kt:170` | confirmadas | **OK** |
| `OfflineTrainingBattleScreen.kt` (40 linhas: 600–2865) | 599–613, 657, 1247–1267, 1449–1463, 2155–2191, 2229–2303, 2570–2572, 2723, 2864–2865 | **OK** |
| `BattleChrome.kt` | 12 linhas; plano cita 10 | **FURO PARCIAL** — `:547` e `:691-693` (P2) |
| `BattleCommandDeckModel.kt:13,24,39` | confirmadas | **OK** |
| `TechniqueLoadoutScreen.kt:292` | confirmada | **OK** |
| `BattleSceneChannel.kt:57`, `TamerTrainerController.kt:97-98` | confirmadas | **OK** |
| Testes JVM (7 arquivos: `BattleSimulatorTest`, `BattleFinisherTest`, `DuoBlastTest`, `BattleVfxCueTest`, `BattleSceneChannelTest`, `BattleCommandDeckModelTest`, `GenericTechniqueCatalogTest`) | 50 linhas | **OK** |
| androidTest: `OfflineTrainingBattleScreenTest:49/67-71/107-111`, `OfflineBattleFinisherPresentationTest:54`, `ArenaPersistenceTest:58` | 3 arquivos | **OK** |
| i18n: 9 chaves × `values`/`values-ja`/`values-pt-rBR` | 6 remover + 3 reformatar | **OK** (com ressalva P1) |
| Docs (`DESIGN`, `CHANGELOG`, `3D_PLAN`, `VICE`, `RECOMMENDATIONS`, `TESTING`, spec `.txt`) | grep de `Command Points|commandPoints|CommandPoints|incentiv` em `*.md` só acha esses + os próprios `analysis/cp-removal/*`; `docs/`, `scripts/`, `tools/` = 0 | **OK** |

**Falsos positivos** (não são CP, não remover): `onSupportContentChanged` em
`app/src/release/generated/baselineProfiles/*.txt`; testes `...Supports...` em nomes de teste.

---

## 2. Furos e correções priorizadas (máx. 8)

### P1 — ALTO: `ui_battle_result_stats` 6→5 exige **renumerar** placeholders (risco de crash)
`res/values/strings.xml:848`, `values-ja/strings.xml:758`, `values-pt-rBR/strings.xml:827` e call site
`BattleChrome.kt:1100-1108`. O plano diz só "remover cheers" e "call site 6→5 args". Se a remoção for
apenas do `%3$d` mantendo `%4$d/%5$d/%6$d`, o `%6$d` passa a estourar
`MissingFormatArgumentException` em runtime (5 args passados, índice 6 referenciado). Nenhum teste JVM
cobre esse texto. **Sugestão:** reescrever as 3 strings com `%1$d..%5$d` sequenciais
(`heals/orders/items/projectiles hit/missed`) e ajustar a ordem dos 5 args no call site. Mesma atenção
em `ui_battle_loadout_cost` (só `%1$d` resta — sem renumeração necessária).

### P2 — MÉDIO: duas ocorrências reais em `BattleChrome.kt` fora do checklist
- `BattleChrome.kt:542-552` (`battleCommandOrder`): `BattleCommandSlot.SUPPORT` em **:547**. O plano cita
  apenas label/clique (`:721`, `:746`). O compilador pega, mas o inventário do plano ("~12 pontos") fica
  incompleto e o critério "guiado pelo compilador" esconde a lista.
- `BattleChrome.kt:690-693`: comentário "the **nine** command stations never shift under the player's
  thumb" — não citado (o plano só atualiza o KDoc em `:561`). Fica texto obsoleto "nine".
**Sugestão:** adicionar `:547` e `:690-693` à Fase 4 e ao checklist da Fase 7/DESIGN.

### P3 — MÉDIO: comandos não cobrem os testes de device que o DoD promete
O DoD (plano:219) exige "testes de device de 8 teclas passam", mas a Fase 6 só lista
`compileIntegrityCheckAndroidTestKotlin`. `TESTING.md`/histórico usam adicionalmente
`:app:assembleIntegrityCheckAndroidTest` e a execução instrumentada em aparelho. **Sugestão:** incluir
`.\gradlew.bat :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=2` e
registrar a execução em device/emulador (ou declarar explicitamente fora do escopo). Padronizar
`:app:lint` (plano:139) para `:app:lintDebug` (usado em 167/218 e em `TESTING.md`).

### P4 — MÉDIO: contradição "cada fase compila" × "Fase 1 quebra o build de propósito"
Plano:41 ("cada uma compila") vs plano:50/67 ("quebra o build de propósito"; critério = compilação
falhar). Na prática Fases 1–3 formam **uma** unidade de compilação; só Fase 4/6 restaura o verde.
**Sugestão:** reescrever o cabeçalho da §2 para "cada fase termina em commit; o build só volta a verde
ao fim da Fase 4" e trocar "Baseline verde" por contagem de falhas esperadas após Fase 1.

### P5 — BAIXO: contagem errada no `snapshot()`
Plano:79 fala "sem os 3 argumentos posicionais". São **4** argumentos posicionais ligados a CP/incentivo
em `BattleSimulator.kt:486-488`: `commandPoints`, `configuration.maxCommandPoints`,
`pendingSupport.keys.toSet()` e a expressão `commandPoints - availableCommandPoints()` (esse último na
posição de `reservedCommandPoints`); os 2 nomeados são `opposingCommandPoints` (`:503`). **Sugestão:**
corrigir o texto para 4+2 e alertar que os 4 testes com `BattleSnapshot(...)` nomeado
(`BattleVfxCueTest:10`, `DuoBlastTest:268`, `BattleSceneChannelTest:15`, `ArenaPersistenceTest:58`)
precisam remover exatamente os 5 campos.

### P6 — BAIXO: critério de grep da Fase 2 e DoD são frouxos/contraditórios
- Plano:91: "nenhuma referência a CP/support em `battle/offline/core`" colide com o que o plano manda
  manter: `isSupport()`/`TechniqueKind.SUPPORT`/`BattleStrategy.SUPPORT` aparecem **18×** em
  `BattleSimulator.kt` (1817-1818, 275, 570, 726-727, ...). O critério literal nunca ficaria "vazio".
- Plano:214 (DoD) não inclui `SUPPORT_PAUSED|SUPPORT_NO_WINDOW|SUPPORT_WINDOW_EXPIRED`, `onSupport`,
  `BattleCommandSlot.SUPPORT`, `availableCommandPoints`. Um resíduo de `onSupport` passaria no grep do
  DoD (o token não casa com `supportReady`/`commandPoint`).
**Sugestão:** usar o grep de 398 da `README` do diretório (que inclui `SUPPORT_*`), limitar a
`core` a `commandPoint|pendingSupport|SupportWindow|SupportSucceeded` e documentar a exceção
`isSupport|TechniqueKind.SUPPORT|BattleStrategy.SUPPORT`.

### P7 — BAIXO: KDoc do deck não contém "3×3/9"; renomear testes explicitamente
`BattleCommandDeckModel.kt:3-8` diz "Decode's two-screen layout..."; não há "3×3" nem "9" no arquivo. O
"3×3/Nine" está nos **nomes de teste**: `BattleCommandDeckModelTest.kt:10`
(`...ThreeByThreeOrder`) e `OfflineTrainingBattleScreenTest.kt:49`
(`commandBandKeepsNineStableCutCornerKeys`). O plano cita o rename do teste androidTest, mas não o nome
do teste JVM. **Sugestão:** corrigir a referência do plano e listar o rename do teste JVM para 8 slots
(ou "TwoByFour"/ordem estável).

### P8 — BAIXO: artefatos de análise ficam desatualizados após a execução
`analysis/cp-removal/README.md` (linhas 22, 116, 128) e os dois inventários descrevem CP como existente;
após as 7 fases viram documentação enganosa. O plano (Fase 7) não prevê marcá-los como históricos.
**Sugestão:** adicionar à Fase 7 "anotar README/inventários como snapshot pré-remoção (data + commit)",
e atualizar as menções "nine/979" em `TESTING.md:535-582`/`:720` junto da evidência nova.

---

## 3. Pontos verificados como corretos (não são furos)

- **Modelo/snapshot:** `BattleSnapshot` (`BattleModel.kt:279-308`) tem `commandPoints`/`maxCommandPoints`
  como 4º/5º **posicionais sem default** e `pendingSupportCombatantIds` posicional; os outros (292/305)
  são nomeados com default. Só 2 call sites posicionais reais: `BattleSimulator.kt:486-505` e
  `ArenaPersistenceTest.kt:58` — risco #1 do plano é legítimo e bem priorizado.
- **Support é folha isolada:** o branch `TrainerAction.Support` (`BattleSimulator.kt:210-221`) retorna
  antes da fila (ação imediata); janela só nasce em `:1377-1381`; `pendingSupport` só é lido ali,
  em `step():520`, `defeat:1970` e `finish:1995`. Removê-lo não altera posições/energia/RNG — a
  conclusão "replay não muda" confere (`WorldInteractionOrchestrator` só faz `advance`).
- **Reservas:** `Reservation` tem 1 único construtor de produção (`:283`); remover o campo `commandPoints`
  + ajustar `:283` e os gates `:280/:358/:1157/:1227/:362` mantém `reservations.remove(orderId)` — plano
  correto. `BattleStatistics` só é construída sem args (`BattleSimulator.kt:75`, `BattleModel:273/295`),
  então remover `supportCommands` é compile-safe.
- **UI/Band/AR:** call sites de produção de `BattleCommandBand` = `OfflineTrainingBattleScreen.kt:599`
  (treino/Arena) e `:1449` (Radar); AR = `BattleCommandDeckHeader` `:1247`/`:2154` e `BattleCommandBar`
  `:1257`/`:2225`. `BattleCommandAvailability` só em `BattleChrome.kt:597`, `OfflineTrainingBattleScreen:2254`
  e no teste do deck. Plano cobre as 3 bandas + 2 implementações (exceto P2). `onSizeChanged`/insets e
  TalkBack estão previstos no critério da Fase 4.
- **Deck 8 slots:** `BattleCommandDeckModel` perde `SUPPORT` e `supportReady`; `BattleCommandDeckModelTest`
  fixa 9 e índice/`supportReady`; androidTest fixa 9 em 2 pontos (`:67-71`, `:107-111`); finisher fixa
  bounds dos 9 (`OfflineBattleFinisherPresentationTest:54`). Plano cobre.
- **i18n:** exatamente 9 chaves por locale (6 remover + 3 reformatar); `ui_battle_strategy_support`
  (`values:805`, `-ja:715`, `-pt:784`), `arena_plan_support` (`tamer_arena.xml:103`) e
  `ui_battle_deck_commands`/`hint` **não** são citadas para remoção — correto.
- **Homônimos preservados:** o plano mantém `BattleStrategy.SUPPORT` (mapeado em
  `OfflineTrainingBattleScreen.kt:2900`, usado em `ArenaWidgets.kt:188`), `TamerStyle.SUPPORT`
  (`TamerBattleLoadouts.kt:15,63,101`), `TechniqueKind.SUPPORT/HEAL`/`isSupport()` e `ui_battle_strategy_support`.
  Nenhuma remoção indevida identificada.
- **Checkpoint:** `NpcBattleAdapter.encode` grava `Gson()` default (`NpcBattleAdapter.kt:22`), leitura usa
  `Gson().fromJson` e `require(formatVersion == 1)` (`:38`); `BattleConfiguration` tem todos os parâmetros
  com default (construtor sem args) → campos antigos (presentes no JSON) são ignorados e ausentes caem em
  default. Manter `formatVersion = 1`/`CATALOG_VERSION` e o pin de `movementRulesVersion` (`:44-49`) é a
  política certa; o teste de envelope legado é viável na infra de `NpcBattleVersionTest.kt:56-64`.
- **Gradle/TESTING:** `:app:testIntegrityCheckUnitTest`, `:app:compileIntegrityCheckAndroidTestKotlin`,
  `:app:lintDebug`, `:app:assembleDebug` existem e são os usados em `TESTING.md`; `:app:compileIntegrityCheckKotlin`
  (Fase 1) também existe (citado em `docs/testing/HISTORICAL_REGRESSION_LOG.md:2664` e
  `docs/refactor/PHASE7_DECISIONS.md:81`). Contagem atual de `TESTING.md` = **1.016** JVM
  (`TESTING.md:108/138/178`), coerente com "≈1.016" do plano; o "979" do inventário é leitura antiga
  (`TESTING.md:546`). Conta esperada: 1.016 − 3 removidos + 1 novo = 1.014.

## 4. Riscos residuais / observações finais

1. O plano não trata o **rebuild do APK instrumentado** nem a execução em device (P3) — único ponto em
   que "Definição de pronto" excede os comandos propostos.
2. `BattleSceneChannelTest` não testa a transição removida; remover o gatilho (`:57`) não deixa lacuna de
   cobertura relevante (o throttle continua coberto) — ok.
3. Nenhum consumidor de CP além dos inventariados: `ArenaResult`/`WorldScreen`/`ArBattleScene`/debug
   `BattleBalanceRunner` não leem CP (grep confirmou 0 ocorrências).
4. A spec histórica `DigimonWorldBattleEspecifications.txt` (CommandPoints/OrderPoints) fica em conflito
   com o produto; o plano acerta em anotar, não reescrever, mas a decisão editorial é do lead.
