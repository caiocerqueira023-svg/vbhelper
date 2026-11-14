# Remoção do sistema de Command Points (CP) — pesquisa, inventário e recomendação

> **Status (07/11/2026): snapshot pré-remoção.** O CP e o Support/incentivo descritos aqui foram
> removidos do app na mesma data. Este diretório permanece como registro de pesquisa/inventário e base
> do plano; `analysis/cp-removal/plano-remocao.md` documenta a execução. Leia as descrições de "CP
> atual" como estado histórico, não como o comportamento do código atual.

> **Pergunta do usuário:** o sistema de combate tem um sistema de Command Points que ele não gosta.
> Quais jogos da série têm e quais não têm? Qual a melhor forma de removê-lo do projeto?
> Base usada: arquivos `OFFLINE_BATTLE_DW1*` (fonte de informação) + código real do repositório.
>
> **Data:** 07/11/2026 · **Repo:** `C:\Users\julye\IdeaProjects\vbhelper` (Android/Kotlin — Tamer's Lab)
> **Status:** pesquisa + inventário + plano. Nenhum arquivo de produção foi alterado por este trabalho.

## TL;DR

1. **O CP atual é um recurso "write-only" em produção.** Nenhuma das 57 técnicas do catálogo de produção
   custa CP (todas usam o default `0`; o único valor explícito de produção é `0`). A única entrada é o
   comando **Support/incentivo**, que abre uma janela de 1 s quando o aliado causa dano e concede até
   **+8** CP. Ou seja: o saldo mostrado no HUD sobe e nunca desce. O subsistema inteiro (gate de custo,
   reserva de CP, pool inimigo) é estruturalmente inerte fora dos testes.
2. **Nenhum jogo da série World usa um CP do treinador compartilhado.** O único parente próximo é
   **Digimon World: Next Order**, com **OP (Order Points)** — mas individual por parceiro, ligado ao
   Bond e resetado a cada batalha. **DW1 não tem CP**: HP, MP (×3, reduzido por Brains), Brains,
   Speed/prontidão e finisher. O CP do VBHelper nasceu como **proposta** do próprio
   `OFFLINE_BATTLE_3D_PLAN.md:160` ("recomendação do plano, não decisão confirmada"), não do DW1.
3. **Recomendação:** remover **CP + Support/incentivo juntos**, em 7 fases compiláveis, guiadas pelo
   compilador (modelo → núcleo → IA/sessão → UI → i18n → testes → docs). Não há migração de dados:
   checkpoints continuam `formatVersion = 1`, pois o Gson ignora campos antigos e o replay não muda
   (batalhas NPC nunca emitem ordens; custos de produção são 0). O CP não é campo serializado de
   técnica — `CATALOG_VERSION` não muda.
4. **Impacto estimado:** ~10 arquivos de produção (102 linhas acopladas), 3 locales (9 chaves → 6
   removidas + 3 reformatadas = 27 entradas), 11 arquivos de teste (7 JVM + 3 androidTest, com três
   `assertEquals(9)` de teclas e um `BattleSnapshot` posicional), 5 documentos + 16 referências
   literais a CP. Esforço estimado: **~1 a 2 dias de trabalho focado** (ver fases).

## Jogos da série: quais têm e quais não têm recurso tipo CP

Critério: pool numérico **do treinador** (não da criatura), consumido para emitir ordens e recarregável.

| Jogo | Modelo de combate | Recurso | Tipo CP? |
|---|---|---|---|
| **Digimon World** (PS1 1999) | Tempo real, parceiro autônomo | HP, MP, **Brains** (libera comandos), Speed/prontidão, finisher | **Não** |
| **Digimon World 2** (PS1 2000) | Turnos, 3 Digimon | HP/MP, DP, DNA Digivolution | **Não** |
| **Digimon World 3** (PS1 2002) | Turnos 1×1 com party | HP/MP, 6 atributos, elementos | **Não** |
| **Digimon World 4** (PS2/GC/Xbox 2005) | Ação (hack and slash) | HP/MP, arma/magia, ranks | **Não** |
| **Digimon World DS** (NDS 2006) | Turnos | HP/MP por Digimon | **Não** |
| **Digimon World: Dawn/Dusk** (NDS 2007) | Turnos, zonas | HP/MP, atributos, equipamento | **Não** |
| **Digimon World Championship** (NDS 2008) | Auto-batalha (criação) | Atributos/cuidado; **sem comandos em luta** | **Não** |
| **Digimon World Re:Digitize / Decode** (PSP 2012 / 3DS 2013) | Tempo real, comandante | MP, Brains, Finisher Power, Attack/Defense Gauges | **Não** |
| **Digimon World: Next Order** (Vita 2016 / PS4 2017 / PC-Switch 2023) | Tempo real, 2 parceiros | HP, MP, **OP (Order Points) individual**, Bond, Order Ring | **Sim (OP)**, mas individual e resetado — não é CP compartilhado |
| **Digimon Masters Online** (MMO) | Tempo real | HP, Digi-soul | **Não** |
| **Digimon RPG / Battle Online** (MMO) | Turnos com barra de tempo | HP, cartas limitadas por batalha | **Parcial** (limite de cartas, não pool) |
| "Digimon World Online" | — | Não existe título oficial com esse nome; provável confusão com *Digimon Online* (MMO fictício do DW3), *Masters* ou *RPG* | — |

Detalhe e fontes (Wikipedia/Fandom/site oficial Bandai + docs locais): [`pesquisa-jogos.md`](pesquisa-jogos.md).

**Conclusão:** remover o CP não afasta o projeto de nenhum jogo da série; o argumento de "fidelidade"
não se aplica ao CP. O OP do Next Order é a inspiração conceitual mais próxima, mas é outra mecânica.

## Situação atual no código (evidência)

- **Núcleo:** `BattleConfiguration.commandPoints/maxCommandPoints`, `BattleSnapshot` com 5 campos de
  CP/incentivo, `TrainerAction.Support`, janela `pendingSupport` (+8 em cada acerto), eventos
  `SupportWindowOpened/SupportSucceeded`, `BattleStatistics.supportCommands`, reservas com custo de CP,
  pool inimigo `opposingCommandPoints`, `TechniqueDefinition.commandPointCost` (produção = 0).
- **UI:** instrumento "CP x/100" + barra no command band; tecla SUPPORT (9 teclas); custo "EN · CP" no
  loadout; "cheers/incentivos" nas estatísticas de resultado; textos de erro de incentivo; feed de
  eventos "+8 CP"; elegibilidade de técnica checando CP; diagnóstico de debug mostrando CP/reservado.
  Existem **duas implementações** do band: `BattleCommandBand` (treino/Arena/Radar) e
  `BattleCommandDeckHeader`/`BattleCommandBar` (AR/Radar antigo).
- **IA:** `TamerTrainerController` emite `Support` automaticamente quando há janela.
- **i18n:** 3 locales (`values`, `values-ja`, `values-pt-rBR`), 9 chaves afetadas.
- **Testes:** 11 arquivos (7 JVM + 3 androidTest), incluindo três `assertEquals(9)` de teclas e um
  construtor **posicional** de `BattleSnapshot` (`ArenaPersistenceTest.kt:58`).
- **Checkpoint:** `BattleConfiguration` viaja dentro do JSON `definitionsJson`
  (`NpcBattleAdapter`, `formatVersion = 1`). Gson default ignora campos desconhecidos; o replay é
  re-derivado de seed/definições e não lê CP. **Sem migração Room; não bumpar versão.**

Inventários completos: [`inventario-core.md`](inventario-core.md) (núcleo/checkpoint) e
[`inventario-ui-testes.md`](inventario-ui-testes.md) (UI/i18n/testes/docs).

## Recomendação: como remover

**Decisão de produto:** remover **CP + comando Support/incentivo** na mesma entrega.

- Sem CP, o Support não tem efeito nenhum — mantê-lo criaria um botão morto. As ordens que restam
  cobrem o repertório do projeto: foco, defender, técnicas, itens, estratégia, aproximar, recuar,
  especial e Blast. O band fica com **8 teclas** (9→8) e perde o instrumento de CP.
- Alternativas consideradas: (a) manter Support "cosmético" — pior UX, rejeitado; (b) transformar
  Support em outro recurso/efeito (ex.: buff temporário de prontidão ou recurso pessoal estilo OP do
  Next Order) — é uma mecânica nova, exige design/balanceamento e pode entrar depois, sobre a remoção
  já feita; (c) remover só o CP e deixar Support devolvendo algo — gera trabalho extra e um efeito
  improvisado, não recomendado.
- Não confundir com conceitos homônimos que **permanecem**: `BattleStrategy.SUPPORT`,
  `TamerStyle.SUPPORT`, `TechniqueKind.SUPPORT/HEAL`, `ui_battle_strategy_support`,
  `arena_plan_support`.

**Fases (cada uma deve compilar; o compilador lista os call sites):**

1. **Preparação:** branch/worktree isolada (a árvore tem mudanças não commitadas, incl.
   `BattleChrome.kt`/docs); rodar a baseline: `.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2`.
2. **Modelo/domínio:** remover `TechniqueDefinition.commandPointCost`; em `BattleModel.kt`, remover os
   campos de CP de `BattleConfiguration`/`BattleSnapshot`, `BattleStatistics.supportCommands`,
   `TrainerAction.Support`, `OrderFailure.SUPPORT_*`, eventos `Support*`. Corrigir o que o compilador
   apontar (inclui `BattleSimulator.snapshot()` posicional).
3. **Simulador:** remover saldos/require, branch de Support, janela (`pendingSupport`), helpers
   `commandPointsFor/spendCommandPoints/availableCommandPoints`, campo CP de `Reservation`, gates e
   `spendCommandPoints` (issue, DUO, approachAndExecute, plano autônomo), limpezas no
   `step/defeat/finish`, validação do catálogo.
4. **IA/sessão:** remover auto-Support de `TamerTrainerController` e o gatilho de
   `pendingSupportCombatantIds` em `BattleSceneChannel`.
5. **UI:** remove `SUPPORT`/`supportReady` do `BattleCommandDeckModel`; `BattleCommandBand` perde
   instrumento de CP + 2 props + tecla (9→8, revalidar colunas 3/5/8 e landscape); as **três** bandas
   (treino/Arena, Radar, AR) perdem CP/onSupport; debug, erros, feed e elegibilidade (energia-only);
   `TechniqueLoadoutScreen` sem CP; resultado com 5 argumentos.
6. **i18n:** remover 6 chaves (`ui_battle_cmd_support`, `ui_battle_feed_support`,
   `ui_battle_feed_support_window`, 3× `ui_battle_order_err_support_*`) e reformatar 3
   (`ui_battle_result_stats` 6→5, `ui_battle_loadout_cost` 2→1, `ui_battle_no_resources` só energia),
   nos 3 locales. Rodar `:app:lint`.
7. **Testes:** remover os 3 testes de incentivo; reescrever reserva/expiração só com energia; limpar
   fixtures (`BattleFinisherTest`, `BattleSimulatorTest`, `BattleVfxCueTest`, `DuoBlastTest`,
   `BattleSceneChannelTest`, `GenericTechniqueCatalogTest`, `BattleCommandDeckModelTest`); atualizar
   `assertTrue(9→8)` nos 3 pontos androidTest; trocar `ArenaPersistenceTest` para argumentos nomeados;
   **adicionar** teste de envelope legado com `commandPoints` no JSON (deve carregar e reproduzir os
   mesmos HP/energia/outcome). Rodar suíte JVM completa + `assembleDebug` + compilação do androidTest.
8. **Docs:** nova entrada no `CHANGELOG.md` (sem reescrever a histórica), `DESIGN.md:734-736`,
   `OFFLINE_BATTLE_3D_PLAN.md` (marcar Etapa 7/CP como substituída), `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:121`,
   `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:458`, `TESTING.md`. `DigimonWorldBattleEspecifications.txt`
   é documento de pesquisa/design histórico — anotar, não reescrever sem ordem.

**Verificação (definição de pronto):**
`rg -n "commandPoint|pendingSupport|SupportWindowOpened|SupportSucceeded|TrainerAction\.Support|supportCommands|reservedCommandPoints|opposingCommandPoints|supportReady" app/src` sem resultados;
`rg -n "commandPointCost" app/src` vazio; lint + assembleDebug + `:app:testIntegrityCheckUnitTest`
verdes; contagem de teclas 8 nos testes de device; save NPC antigo recuperando normalmente.

## Riscos principais

- **Construtor posicional de `BattleSnapshot`** (produção + `ArenaPersistenceTest`) — remover campos
  desloca argumentos; trocar por nomeados.
- **Layout/UX do band:** 9→8 teclas, colunas 3/5/8, altura/insets medidos por `onSizeChanged`,
  acessibilidade/TalkBack e memória muscular; decidir se reflui 8 em 4×2 ou mantém spacer no wide.
- **Strings com placeholders:** `ui_battle_result_stats` 6→5 e `ui_battle_loadout_cost` 2→1 precisam
  casar com os call sites nos 3 locales (erro de formatação).
- **Checkpoint antigo:** não é preciso migrar (Gson ignora campos extras; replay não lê CP), mas vale
  teste de regressão; **não** bumpar `formatVersion`/`CATALOG_VERSION`.
- **Esquecer uma das bandas (AR)** deixa a remoção inconsistente.
- **Docs históricos** (`OFFLINE_BATTLE_3D_PLAN`, spec) continuarão descrevendo CP; marcar como
  superseded em vez de apagar história.

## Arquivos deste trabalho

| Arquivo | Conteúdo |
|---|---|
| [`pesquisa-jogos.md`](pesquisa-jogos.md) | Série World: recursos/comandos por jogo, DW1 real, origem do CP, fontes externas |
| [`inventario-core.md`](inventario-core.md) | Inventário do núcleo/modelo/checkpoint/eventos/reservas/IA/testes, compat Gson, riscos, sequência |
| [`inventario-ui-testes.md`](inventario-ui-testes.md) | Inventário de UI/i18n/testes/docs, 3 locales, 11 arquivos de teste, riscos de layout |
| [`plano-remocao.md`](plano-remocao.md) | Plano executável detalhado (fases, assinaturas, comandos, esforço, DoD) |
| [`revisao-plano.md`](revisao-plano.md) | Revisão adversarial do plano: veredito "ajustes necessários"; P1–P8 já incorporados ao plano |
| `README.md` (este) | Síntese: resposta direta, tabela de jogos, recomendação e fases |

> Nenhum arquivo de código de produção foi alterado por esta pesquisa.
