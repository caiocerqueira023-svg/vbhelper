# Inventário CP — núcleo do simulador, modelo e checkpoint

> **Task:** task-2 · "Inventário CP — núcleo do simulador, modelo e checkpoint"
> **Escopo:** metade NÚCLEO/CHECKPOINT do sistema de Command Points (CP) do VBHelper.
> **Método:** leitura somente (glob/grep/read). Nenhum arquivo de produção editado; este md é o único artefato.
> **Base:** workspace `C:\Users\julye\IdeaProjects\vbhelper` · Android/Kotlin · Gson 2.14.0 (`gradle/libs.versions.toml:27`) · `BattleSimulator.kt` = 2.218 linhas · `BattleModel.kt` = 368 linhas.
> **Classificações usadas:** `estado do treinador` · `custo de técnica` · `incentivo-Support` · `reserva` · `evento-snapshot` · `checkpoint` · `teste` · `doc` · `outro`.

---

## 0. Sumário executivo

1. **O custo de CP de toda técnica de produção é zero.** O catálogo (`GenericTechniqueCatalog`) nunca define `commandPointCost`; só existe um `copy(commandPointCost = 0)` explícito e redundante (`GenericTechniqueCatalog.kt:170`). Logo, **em gameplay real o CP nunca é debitado**: as checagens de saldo (`BattleSimulator.kt:280,358,1157`), o gasto (`:1227,362`), as filtragens (`:903,1111`) e as reservas de CP (`Reservation.commandPoints`) são **no-ops estruturais**. O CP só é "gasto" em testes.
2. **A única fonte real de CP é o incentivo (`TrainerAction.Support`)**: cada acerto de dano abre uma janela de 1 s por parceiro (`BattleSimulator.kt:1377-1381`) e o aceite concede `min(8, maxCP − CP do lado)` (`:215-216`), contabiliza `statistics.supportCommands` (só lado ALLIED, `:217`) e emite `SupportSucceeded` (`:220`). Sem técnica custando CP, **o CP é um recurso write-only/decorativo em produção** (o HUD mostra o saldo, nada consome).
3. **Três conceitos "Support" não podem ser confundidos na remoção:** (a) `TrainerAction.Support` = incentivo/cheer (sair junto com CP); (b) `TechniqueKind.SUPPORT` + `isSupport()` = classificação de técnica de suporte/cura (`BattleSimulator.kt:1817-1818`, mantém); (c) `BattleCommandSlot.SUPPORT` = tecla do HUD de comandos (sai só a tecla de incentivo).
4. **Reservas:** o data class `Reservation` (`BattleSimulator.kt:2177-2182`) carrega `energy`, `commandPoints` e `specialCharge`. Remover CP = remover **apenas** o campo `commandPoints` e os dois argumentos nos call sites; a máquina de reservas de energia/special charge permanece.
5. **Snapshot expõe 5 campos de CP/incentivo** (`BattleModel.kt:283,284,287,292,305`) + 2 eventos (`:338-339`). O `BattleSnapshot` é **somente em memória** (nunca serializado; o checkpoint NPC persiste `NpcBattleSummary`, que não tem CP) — então não há migração de snapshot.
6. **Checkpoint NPC:** envelope JSON `Checkpoint(formatVersion=1, catalogVersion, configuration, definitions)` gravado com `Gson()` default (`NpcBattleAdapter.kt:12-23`). O Gson default **ignora campos JSON desconhecidos** na leitura e, como `BattleConfiguration` é data class com todos os parâmetros default (construtor sem args gerado pelo Kotlin), campos ausentes caem nos defaults. **Remover `commandPoints`/`maxCommandPoints` de `BattleConfiguration` é retrocompatível na leitura**: registros antigos com esses campos continuam carregando, os valores são descartados.
7. **O replay não muda.** `recover()` não lê CP (reconstrói o simulador a partir de `configuration` + definições e avança N steps fixos). Batalhas do mundo/NPC **nunca emitem ordens** (orchestrator só chama `advance`), portanto incentivo jamais ocorre nelas; custo de técnica é 0; o RNG deriva de `randomSeed:stableKey:streamTag` (`:1136-1143`), não de CP nem de order ids. Só mudam `eventCount`, `recentEvents` e `statistics` — e o resumo persistido não os guarda.
8. **Política recomendada: NÃO bumpar `formatVersion` (manter 1) e tolerar o legado** (campos antigos ignorados). Bump forçaria `require(checkpoint.formatVersion == 1)` (`NpcBattleAdapter.kt:38`) a rejeitar batalhas NPC ativas salvas antes do update, sem ganho. Um teste barato de regressão ("envelope legado com commandPoints/maxCommandPoints é aceito e o replay é idêntico") é recomendado para documentar a compat.
9. **Riscos de compilação/layout:** construtores **posicionais** de `BattleSnapshot` (`BattleSimulator.kt:486-505`, `ArenaPersistenceTest.kt:58`) e de `BattleConfiguration` em testes precisam de ajuste manual; a remoção da tecla `SUPPORT` deixa o deck 3×3 com 8 slots (decisão de produto/UX pendente); strings em **3 locales** (27 chaves) e docs/spec precisam ser atualizados.

---

## 1. Tabela mestra (file:line · símbolo/trecho · classificação · ação)

### 1.1 `app/src/main/java/com/tamerslab/battle/offline/core/BattleModel.kt`

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `BattleModel.kt:83-98` (campo em `:89`) | `BattleStatistics(... val supportCommands: Int = 0, ...)` — contador de incentivos aceitos | evento-snapshot | Remover o campo; ajustar quem o preenche (`BattleSimulator.kt:217`) e a string de resultado (`BattleChrome.kt:1104`, `ui_battle_result_stats` 6→5 args em 3 locales). |
| `BattleModel.kt:100-120` (`:107-108`) | `BattleConfiguration(... val commandPoints: Int = 0, val maxCommandPoints: Int = 100, ...)` | estado do treinador | Remover os 2 campos. `commandPoints` é o **saldo inicial** (default 0) e `maxCommandPoints` o **teto** (100). Nenhum caller de produção define esses campos (só testes). |
| `BattleModel.kt:131-145` (`:139`) | `data object Support : TrainerAction` | incentivo-Support | Remover o caso. `TrainerAction` é `sealed interface`; o `when` de `issueSideOrder` tem `else -> Unit` (`BattleSimulator.kt:264`), mas o branch específico precisa sair. |
| `BattleModel.kt:160-197` (`:166-168`) | `OrderFailure.SUPPORT_PAUSED = "support_paused"`, `SUPPORT_NO_WINDOW`, `SUPPORT_WINDOW_EXPIRED` | incentivo-Support | Remover as 3 constantes + mapping de UI (`OfflineTrainingBattleScreen.kt:2570-2572`) + strings dos 3 locales. |
| `BattleModel.kt:269-274` | `BattleResult(outcome, elapsedMillis, eventCount, statistics)` | evento-snapshot | Sem campo CP próprio; herda apenas `statistics` — nenhuma alteração além do item anterior. |
| `BattleModel.kt:279-308` (`:283,284,287,292,305`) | `BattleSnapshot(commandPoints, maxCommandPoints, pendingSupportCombatantIds, reservedCommandPoints, opposingCommandPoints, ...)` | evento-snapshot | Remover os 5 campos. `reservedCommandPoints` hoje é computado como `commandPoints - availableCommandPoints()` (`BattleSimulator.kt:488`); `opposingCommandPoints` é o pool do lado inimigo (`:503`). Ajustar construtores posicionais (ver §6). |
| `BattleModel.kt:310-347` (`:338-339`) | `BattleEvent.SupportWindowOpened(combatantId, expiresAtMillis)` / `SupportSucceeded(combatantId, commandPointsGained)` | incentivo-Support | Remover as 2 subclasses; remover branches de UI (`OfflineTrainingBattleScreen.kt:2864-2865`) e strings (`ui_battle_feed_support*`). |
| `BattleModel.kt:199-238` | `CombatantSnapshot` (sem CP) | outro | Intocado. |

**Observação de modelagem:** `BattleConfiguration.commandPoints` inicializa **os dois lados com o mesmo valor** (`BattleSimulator.kt:72-73`), apesar do nome "commandPoints" sugerir pool só do treinador; `opposingCommandPoints` existe para o pool do treinador adversário.

### 1.2 `app/src/main/java/com/tamerslab/battle/offline/core/BattleSimulator.kt`

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `BattleSimulator.kt:56` | `private val pendingSupport = linkedMapOf<String, Long>()` — combatantId → deadline da janela | incentivo-Support | Remover o campo. |
| `BattleSimulator.kt:62` | `private val reservations = mutableMapOf<Long, Reservation>()` | reserva | Manter (energia/special charge/habilidade de finalizar); reduzir o `Reservation` em `:2177-2182`. |
| `BattleSimulator.kt:72-73` | `private var commandPoints = configuration.commandPoints`<br>`private var opposingCommandPoints = configuration.commandPoints` | estado do treinador | Remover as 2 variáveis. |
| `BattleSimulator.kt:84` | `require(configuration.maxCommandPoints >= 0 && commandPoints in 0..configuration.maxCommandPoints)` | estado do treinador | Remover o `require`. |
| `BattleSimulator.kt:210-221` (bloco `TrainerAction.Support`) | `if (paused) return reject(... SUPPORT_PAUSED)` · `pendingSupport[actorId] ?: reject(... SUPPORT_NO_WINDOW)` · `if (window <= elapsedMillis) reject(... SUPPORT_WINDOW_EXPIRED)` · `pendingSupport.remove(actorId)` · `val gained = min(8, configuration.maxCommandPoints - commandPointsFor(controllingSide))` · `spendCommandPoints(controllingSide, -gained)` · `statistics.copy(supportCommands = ... + 1)` · `emit(SupportSucceeded(...))` | incentivo-Support | Remover o branch inteiro. Note que `Support` retorna **antes** do limite de fila (`:266`): é ação imediata de treinador (não ocupa ação/fila do parceiro, não avança a simulação). |
| `BattleSimulator.kt:268-284` (`:280`, `:283`) | `if (availableEnergy(actor) < technique.energyCost \|\| availableCommandPoints(controllingSide) < technique.commandPointCost) reject(RESOURCES_MISSING)`<br>`reservations[id] = Reservation(actorId, technique.energyCost, technique.commandPointCost, specialChargeCost)` | custo de técnica + reserva | Remover o termo de CP da condição e o 3º argumento do `Reservation`; recalibrar mensagem de `RESOURCES_MISSING` (hoje cita "pontos de comando"). |
| `BattleSimulator.kt:351-371` (`:357-358`, `:362`) | `prepareDuoAttack`: `availableCommandPoints(partner.definition.side) < special.commandPointCost \|\| ...` e `spendCommandPoints(partner.definition.side, special.commandPointCost)` | custo de técnica | Remover o gate e o gasto de CP; manter consumo de energia/special charge/readiness. |
| `BattleSimulator.kt:443-506` (`:486-488`, `:503`) | `snapshot()`: `BattleSnapshot(elapsedMillis, paused, pauseReason, commandPoints, configuration.maxCommandPoints, ... pendingSupport.keys.toSet(), ..., commandPoints - availableCommandPoints(), ...)` e `opposingCommandPoints = opposingCommandPoints` | evento-snapshot | Reescrever a chamada sem os 3 argumentos posicionais e sem os 2 nomeados; `snapshot()` é chamado a 60 Hz e alimenta HUD/`StateFlow` — não muda o custo, só o shape. |
| `BattleSimulator.kt:513-521` (`:520`) | `pendingSupport.entries.removeAll { it.value <= elapsedMillis }` em `step()` | incentivo-Support | Remover a linha. |
| `BattleSimulator.kt:545-553` | `trainerControllers.forEach { controller.decide(observed, techniques) → issueSideOrder(side, ...) }` | outro (IA de treinador) | Manter o laço; remover apenas a decisão de Support do controller (§1.5). |
| `BattleSimulator.kt:889-909` (`:903`) | `planAutonomousAction`: `if (ready(...) && availableEnergy(...) >= ... && special.commandPointCost == 0)` para especial autônomo | custo de técnica | Remover o predicado `special.commandPointCost == 0` (hoje sempre verdadeiro em produção; era o que impedia a IA de "gastar" CP do treinador). |
| `BattleSimulator.kt:1105-1128` (`:1111`) | `autonomousTechniqueCandidates`: `ready(actor, it) && it.energyCost <= availableEnergy(actor) && it.commandPointCost == 0 && it.kind != SPECIAL` | custo de técnica | Remover o predicado `it.commandPointCost == 0`. |
| `BattleSimulator.kt:1145-1245` (`:1157`, `:1227-1228`) | `approachAndExecute`: `explicit && commandPointsFor(actor.definition.side) < technique.commandPointCost` → `RESOURCES_MISSING`<br>`if (explicit) { spendCommandPoints(..., technique.commandPointCost); reservations.remove(actor.currentOrder!!.orderId) }` | custo de técnica + reserva | Remover o termo CP da condição e o `spendCommandPoints`; **manter** `reservations.remove(orderId)` (libera energia/special charge). |
| `BattleSimulator.kt:1377-1381` | Abertura da janela em acerto: `if (damage > 0 && actor.health > 0 && (side == ALLIED \|\| side in trainerControllers)) { val expiry = elapsedMillis + 1_000L; pendingSupport[actor.definition.combatantId] = expiry; emit(SupportWindowOpened(...)) }` | incentivo-Support | Remover o bloco inteiro. É a **única origem** das janelas de incentivo; roda dentro do loop de acertos (a cada hit de dano > 0). |
| `BattleSimulator.kt:1816-1830` (`:1820-1825`) | `commandPointsFor(side)`, `spendCommandPoints(side, amount)`, `availableCommandPoints(side = ALLIED) = commandPointsFor(side) - Σ reservations.commandPoints` | estado do treinador + reserva | Remover os 3 helpers. |
| `BattleSimulator.kt:1965-1974` (`:1970`) | `defeat(actor)`: `pendingSupport.remove(actor.definition.combatantId)` | incentivo-Support | Remover a linha. |
| `BattleSimulator.kt:1985-2002` (`:1995`) | `finish(result)`: `pendingSupport.clear()` | incentivo-Support | Remover a linha. |
| `BattleSimulator.kt:2030-2058` (`:2032`) | `validateTechnique`: `require(... && technique.commandPointCost >= 0)` | custo de técnica | Remover a cláusula. |
| `BattleSimulator.kt:2177-2182` | `private data class Reservation(actorId, energy, commandPoints, specialCharge = 0)` | reserva | Remover o campo `commandPoints`; ajustar os 2 construtores (`:283`, e eventualmente DUO/trainer). |
| `BattleSimulator.kt:2016-2028` | `emit(event)`: incrementa `eventCount`, mantém tail de 96 `recentEvents`; indexa `TechniqueHit` | outro | Sem CP; porém remover `SupportWindowOpened/Succeeded` **altera a contagem/ordem de eventos** visível em `snapshot()`, `BattleResult.eventCount` e no feed de UI. Não existe consumidor de checkpoint para isso. |
| `BattleSimulator.kt:2203-2217` | companion (`SPECIAL_CHARGE_*`, `BASIC = TechniqueDefinition("basic_attack", ...)`) | outro | Intocado (BASIC não declara CP). |

### 1.3 Domínio de técnicas

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `battle/domain/BattleDefinitions.kt:136-169` (`:142`) | `TechniqueDefinition(... val energyCost: Int = 0, val commandPointCost: Int = 0, ...)` | custo de técnica | Remover o campo. Todos os call sites usam argumentos **nomeados** após `id/displayName/kind/power`, então a remoção desloca apenas nomes (compile-safe); os únicos que citam `commandPointCost` por nome são testes (`BattleFinisherTest.kt:10`, `BattleSimulatorTest.kt:530,593` e `.copy(commandPointCost=...)` em `:513,650,687`). |
| `battle/offline/data/GenericTechniqueCatalog.kt:164-184` (`:170`) | `legacyBattleDefinitions`: `definition.copy(kind = SPECIAL, ..., commandPointCost = 0, ...)` | custo de técnica (redundante) | Remover a linha — é o **único** `commandPointCost` explícito de produção, e vale 0. |
| `battle/offline/data/GenericTechniqueCatalog.kt:245-328` (`:304-326`) | `entry(...) { TechniqueDefinition(techniqueId = ..., energyCost = runtimeCost, ...) }` — não passa CP ⇒ default 0 em todas as 57 técnicas | custo de técnica | Nenhum ajuste além da remoção do campo no modelo. |
| `battle/offline/data/GenericTechniqueCatalog.kt:226-230` | `definitionsForVersion(version) = when (version) { 2 -> legacyBattleDefinitions; CATALOG_VERSION -> battleDefinitions; ... }` | checkpoint | **Não** mudar versões de catálogo por causa de CP: `TechniqueDefinition` não é serializada em checkpoint (o envelope persiste `CombatantDefinition`), então o custo CP nunca aparece em dados antigos. `CATALOG_VERSION = 3` (`OfflineBattleRuleset.kt:6`) permanece. |
| `battle/offline/data/GenericTechniqueCatalog.kt:188-193` | `counter = TechniqueDefinition(counterTechniqueId, ..., energyCost = 25, ...)` | outro | Intocado (sem CP). |

### 1.4 Checkpoint (envelope Gson, replay, compatibilidade)

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `world/ecosystem/NpcBattleAdapter.kt:12-17` | `private data class Checkpoint(val formatVersion: Int, val catalogVersion: Int, val configuration: BattleConfiguration, val definitions: List<CombatantDefinition>)` | checkpoint | Manter a estrutura/versão. O CP viaja **dentro de `configuration`** (é uma propriedade de `BattleConfiguration`). |
| `world/ecosystem/NpcBattleAdapter.kt:19-23` | `encode(...)` → `Gson().toJson(Checkpoint(1, catalog, configuration, definitions))`; `require(rulesetVersion in ...)` | checkpoint | Manter `formatVersion = 1`; após a remoção o gravador simplesmente deixa de emitir as chaves de CP. |
| `world/ecosystem/NpcBattleAdapter.kt:22` | `Gson()` **default** (sem `GsonBuilder`, sem `TypeAdapter`, sem `@SerializedName`) | checkpoint | Confirmado por grep: nenhum `GsonBuilder`/`setIgnoreUnknown` no código-fonte (só artefatos de baseline profile). Gson default **ignora membros JSON desconhecidos** ao desserializar; não há modo estrito. |
| `world/ecosystem/NpcBattleAdapter.kt:25-29` | `definitions(record)`: aceita `definitionsJson` como **array legado** (`Array<CombatantDefinition>`) ou envelope `Checkpoint` | checkpoint | Intocado; não contém CP. |
| `world/ecosystem/NpcBattleAdapter.kt:31-64` | `recover(event, record)`: parse do envelope/array → `require(formatVersion == 1)` (`:38`) → valida `rulesetVersion`/`catalogVersion`/`randomSeed` → monta `BattleSimulator` → `while (elapsed < record.elapsedMillis) advance(STEP)` | checkpoint | Replay **re-derivado**: nunca lê CP do JSON; reconstrói do zero. Remover CP não altera trajetória (§5). |
| `world/ecosystem/NpcBattleAdapter.kt:44-49` | `movementVersionPresent = root.asJsonObject.getAsJsonObject("configuration")?.has("movementRulesVersion")` → se ausente, `copy(movementRulesVersion = LEGACY)`; comentário: "BattleConfiguration has a no-arg default constructor, so Gson alone would silently apply new movement to old envelopes" | checkpoint | **Precedente de compatibilidade** para campos ausentes. Para CP **não é necessário** pin: o efeito de um default (0/100) é nulo após a remoção; os campos antigos presentes são simplesmente ignorados. |
| `world/ecosystem/NpcBattleAdapter.kt:50-52` | `require(record.elapsedMillis >= 0 && elapsedMillis % STEP_MILLIS == 0)` | checkpoint | Intocado. |
| `world/ecosystem/WorldDialogue.kt:31-34` | `@Entity WorldNpcBattle(@PrimaryKey interactionId, definitionsJson, elapsedMillis, startTick, nextRoundTick, snapshotJson)` | checkpoint | Não há coluna/estado de CP; nada a migrar no Room. |
| `world/ecosystem/WorldDialogue.kt:45-52` | `NpcBattleSummary.from(snapshot)`: `elapsedMillis` + membros (hp/maxHp/energy/maxEnergy) + outcome | checkpoint | Resumo persistido **não carrega CP nem estatísticas/eventos** — confirma que a remoção não exige migração de `snapshotJson`. |
| `data/database/WorldDialogueSchema.kt:27-33` | DDL `CREATE TABLE WorldNpcBattle(interactionId, definitionsJson, elapsedMillis, startTick, nextRoundTick, snapshotJson, ...)` | checkpoint | Sem alteração de schema / sem migration. |
| `world/ecosystem/WorldInteractionOrchestrator.kt:193-207` | Battles do mundo: `NpcBattleAdapter.recover(...)` → `repeat(6) { simulator.advance(250) }` → `saveNpcBattle(record.copy(elapsedMillis = state.elapsedMillis, snapshotJson = ...))` | checkpoint | O estado vivo é descartado a cada tick; o avanço é replay puro. **Nenhuma ordem de treinador é emitida** em batalha NPC → incentivo nunca ocorre; CP nunca é lido/escrito. |
| `world/ecosystem/WorldInteractionOrchestrator.kt:323-327` | `createBattle`: `NpcBattleAdapter.encode(definitions, BattleConfiguration(randomSeed=event.seed, defaultPaused=false, arenaRadius=8f))` + `NpcBattleSummary.from(recover(...))` | checkpoint | Intocado (após remoção o encode omite CP). |
| `world/ui/WorldScreen.kt:756` | `NpcBattleAdapter.recover(source, record).snapshot()` → mapa `health` por combatantId para transplantar HP/EN ao vivo | checkpoint | Consome apenas HP/EN (não CP) — intocado. |
| `battle/offline/data/OfflineBattleRuleset.kt:5-6` | `VERSION = BattleRules.CURRENT_VERSION`, `CATALOG_VERSION = 3` | checkpoint | **Não** bumpar por CP. |

### 1.5 Consumidores fora do núcleo (código de produção)

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `battle/offline/core/TamerTrainerController.kt:87-99` (`:97-98`) | IA de treinador (arena): `team.firstOrNull { it.combatantId in snapshot.pendingSupportCombatantIds }?.let { decisions += TrainerDecision(it.combatantId, TrainerAction.Support) }` | incentivo-Support | Remover o bloco `:97-99`. Sem isso a IA continua tentando `TrainerAction.Support` (não compila após remover a ação). Note que a IA escolhe especial (`:87-95`) usando só `energyCost`/cooldown — já ignora CP. |
| `battle/offline/session/BattleSceneChannel.kt:57` | `if (next.pendingSupportCombatantIds != previous.pendingSupportCombatantIds) return true` (força publicação imediata do snapshot à UI) | incentivo-Support | Remover a linha; throttle volta a depender só de pausa/resultado/finisher/blast/intervalo. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:599-613` | `BattleCommandBand(availableCommandPoints = ((current?.commandPoints ?: 0) - (current?.reservedCommandPoints ?: 0)).coerceAtLeast(0), maxCommandPoints = current?.maxCommandPoints ?: 0, ..., supportReady = current != null && !current.isPaused && selectedAlly?.combatantId?.let(current.pendingSupportCombatantIds::contains) == true, ..., onSupport = { ally -> issue(ally, TrainerAction.Support) })` | incentivo-Support + estado do treinador | Remover as props de CP/support e o `onSupport` desta chamada. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:647-657` | `Text("CP ${snapshot.commandPoints}/${snapshot.maxCommandPoints} · reservado ${snapshot.reservedCommandPoints} · projéteis ...")` (diálogo de diagnóstico) | estado do treinador | Remover o trecho de CP do diagnóstico. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:1247-1267` | Layout "world-radar": `BattleCommandDeckHeader(availableCommandPoints = ..., maxCommandPoints = ...)`, `BattleCommandBar(..., supportReady = ..., onSupport = { ... TrainerAction.Support })` | incentivo-Support + estado do treinador | Remover props/handlers (idem `:1449-1463`, terceiro layout). |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:2153-2186` | `BattleCommandDeckHeader(availableCommandPoints: Int, maxCommandPoints: Int, ...)`; `Text("CP $availableCommandPoints/${maxCommandPoints.coerceAtLeast(0)}")`; `LinearProgressIndicator(progress = available/max)` | estado do treinador | Remover params/trecho/progress (o header pode ficar só com pausa/título). |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:2225-2301` (`:2229-2230,2257,2273,2294`) | `BattleCommandBar(..., availableCommandPoints, supportReady, ...)`; `BattleCommandSlot.SUPPORT -> stringResource(R.string.ui_battle_cmd_support, availableCommandPoints)`; `BattleCommandSlot.SUPPORT -> allyId?.let(onSupport)` | incentivo-Support + estado do treinador | Remover params/keys/handlers. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:2716-2723` | Habilitação de técnica: `((snapshot?.commandPoints ?: 0) - (snapshot?.reservedCommandPoints ?: 0)) >= technique.commandPointCost` | custo de técnica | Remover o termo CP (só energia). |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:2570-2572` | `OrderFailure.SUPPORT_PAUSED/NO_WINDOW/WINDOW_EXPIRED -> R.string.ui_battle_order_err_support_*` | incentivo-Support | Remover as 3 linhas do `when`. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt:2864-2865` | `is BattleEvent.SupportSucceeded -> ... +%2$d CP`; `is BattleEvent.SupportWindowOpened -> ... pode receber incentivo por %2$d s` | incentivo-Support | Remover branches do feed. |
| `battle/ui/offline/BattleChrome.kt:543-558` (`:547`) | Lista de slots da command band inclui `BattleCommandSlot.SUPPORT` | incentivo-Support | Remover o slot das duas listas. |
| `battle/ui/offline/BattleChrome.kt:565-600` (`:566-567`, `:600`) | `BattleCommandBand(availableCommandPoints, maxCommandPoints, ..., supportReady, ...)`; `supportReady = supportReady && commandsEnabled` | estado do treinador + incentivo-Support | Remover params e uso. |
| `battle/ui/offline/BattleChrome.kt:658-674` | `Text("CP $availableCommandPoints/${maxCommandPoints...}")` + `LinearProgressIndicator(progress = available/max)` | estado do treinador | Remover o instrumento de CP do header. |
| `battle/ui/offline/BattleChrome.kt:718-721` (`:721`) | `BattleCommandSlot.SUPPORT -> stringResource(R.string.ui_battle_cmd_support, availableCommandPoints)` | incentivo-Support | Remover branch. |
| `battle/ui/offline/BattleChrome.kt:1099-1111` (`:1104`) | Resultado: `statistics.supportCommands` como 3º argumento de `ui_battle_result_stats` ("cheers %3$d") | evento-snapshot | Remover argumento e reformatar string (6→5) em 3 locales. |
| `battle/ui/offline/BattleCommandDeckModel.kt:9-19` (`:13`) | `enum class BattleCommandSlot { ..., SUPPORT, ... }` | incentivo-Support | Remover `SUPPORT` do enum; ver decisão de layout 8 slots (§6). |
| `battle/ui/offline/BattleCommandDeckModel.kt:21-26,33-48` (`:24`, `:39`) | `BattleCommandAvailability.supportReady`; `BattleCommandDeckItem(BattleCommandSlot.SUPPORT, availability.supportReady)` | incentivo-Support | Remover campo e item do deck. |
| `battle/ui/offline/TechniqueLoadoutScreen.kt:292` | `stringResource(R.string.ui_battle_loadout_cost, special.energyCost, special.commandPointCost)` | custo de técnica | Remover o 2º argumento; reformatar `ui_battle_loadout_cost` (remover `%2$d CP`) em 3 locales. |

### 1.6 Testes (referências que quebram ou precisam de reescrita)

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `test/.../BattleSimulatorTest.kt:166-174` | `supportIsImmediateAndOnlyRewardsOncePerWindow`: espera `pendingSupportCombatantIds`, `TrainerAction.Support` COMPLETED, `commandPoints == 8`, 2ª tentativa FAILED | teste | Remover o teste (comportamento eliminado). |
| `BattleSimulatorTest.kt:452` | `BattleConfiguration(commandPoints = 100, defaultPaused = true)` | teste | Remover o argumento. |
| `BattleSimulatorTest.kt:512-524` | `reservationPreventsTwoAlliesSpendingTheSameCommandPoints`: custo CP 20, `commandPoints = 25`, asserts `reservedCommandPoints` 20→0 e saldo 5 | teste | Reescrever como teste de reserva **de energia** (o mecanismo permanece) ou remover a parte CP. |
| `BattleSimulatorTest.kt:526-546` | `innateSpecialRequiresItsOwnFullGaugeInsteadOfCommandPointsOrEnergy` (copia especial com `commandPointCost = 0`) | teste | Renomear/ajustar: só gauge/energia; remover `commandPointCost`. |
| `BattleSimulatorTest.kt:580-596` (`:593`) | especial com `commandPointCost = 0` | teste | Remover a linha. |
| `BattleSimulatorTest.kt:649-661` | `expiryReleasesResourcesEvenWhilePartnerIsDefending` (custo CP 20, `commandPoints = 30`, assert `reservedCommandPoints == 0`) | teste | Manter só a parte de energia/expiração; remover asserts de CP. |
| `BattleSimulatorTest.kt:674-684` | `supportIsRejectedWhilePausedAndWindowRemainsAvailable` | teste | Remover (incentivo eliminado). |
| `BattleSimulatorTest.kt:686-693` | `enemyAutomationCannotSpendTrainersCommandPoints` (custo CP 10, `commandPoints = 50`) | teste | Remover (não há mais CP para proteger). |
| `BattleSimulatorTest.kt:729-734` (`:733`) | `battle(config = BattleConfiguration(maxCommandPoints = -1))` | teste | Remover essa asserção (campo deixa de existir). |
| `BattleFinisherTest.kt:9-11` (`:10`) | `TechniqueDefinition(... energyCost = 30, commandPointCost = 5, ...)` | teste | Remover o argumento. |
| `BattleFinisherTest.kt:24-33` (`:24`, `:27`) | helper `simulator(commandPoints: Int = 100, ...)`; `BattleConfiguration(commandPoints = commandPoints, randomSeed = 903)` | teste | Remover o parâmetro/argumento; simplificar o helper. |
| `BattleFinisherTest.kt:102-156` (`:108`, `:115`, `:148`) | asserts `assertEquals(95, startup.commandPoints)` / `startup.commandPoints == launched.commandPoints` / `95, restored.commandPoints` | teste | Remover os asserts de CP; o teste continua válido para energia/finisher. |
| `BattleFinisherTest.kt:165` | `BattleSimulator(BattleConfiguration(commandPoints = 100), ...)` | teste | Remover o argumento. |
| `BattleFinisherTest.kt:336-359` | `duoPresentationDoesNotGrantMissingEnergyCommandPointsOrReadiness` — cenário `"command"` (`commandPoints = if (missing == "command") 5 else 100`) | teste | Remover o cenário "command" e a menção a CP no nome. |
| `GenericTechniqueCatalogTest.kt:73` | `assertEquals(0, special.commandPointCost)` | teste | Remover o assert (campo eliminado). |
| `test/.../BattleVfxCueTest.kt:10-22` (`:14-15`, `:18`) | `BattleSnapshot(..., commandPoints = 0, maxCommandPoints = 100, ..., pendingSupportCombatantIds = emptySet(), ...)` | teste | Remover os 3 argumentos nomeados. |
| `test/.../session/BattleSceneChannelTest.kt:15-27` (`:19-20`, `:23`) | idem `BattleSnapshot(...)` | teste | Remover os 3 argumentos. |
| `test/.../DuoBlastTest.kt:268-273` (`:269-271`) | idem `BattleSnapshot(...)` | teste | Remover os 3 argumentos. |
| `test/.../BattleCommandDeckModelTest.kt:9-75` (`:15,25,44,52,66,73`) | espera 9 slots incluindo `BattleCommandSlot.SUPPORT` e usa `supportReady` | teste | Ajustar para o novo deck (8 slots) / remover `supportReady`. |
| `androidTest/.../ArenaPersistenceTest.kt:58-63` | `BattleSnapshot(34, false, null, 0, 100, members(...), members(...), emptySet(), ...)` — **construtor posicional** | teste | Remover os argumentos posicionais `0, 100` (contagens) e `emptySet()` (pendingSupport) — a assinatura desloca. |
| `test/.../world/ecosystem/NpcBattleVersionTest.kt` | Suíte de replay/versão (sem CP explícito) | teste | **Nenhuma mudança**; serve de guarda de regressão. Recomendado adicionar um caso "envelope legado com `commandPoints`/`maxCommandPoints`" (ver §5). |

### 1.7 Strings i18n e documentação

| file:line | símbolo / trecho | classificação | ação |
|---|---|---|---|
| `res/values/strings.xml:821` · `values-pt-rBR:800` · `values-ja:731` | `ui_battle_cmd_support` = "Support · %1$d" / "Apoiar · %1$d" / "応援 · %1$d" | doc/i18n (incentivo) | Remover a chave nos 3 locales. |
| `values/strings.xml:848` · `pt-rBR:827` · `ja:758` | `ui_battle_result_stats` = "Heals %1$d · orders %2$d · **cheers %3$d** · items %4$d · projectiles %5$d/%6$d" | doc/i18n (evento) | Reformar para 5 argumentos (remover `%3$d` cheers) nos 3 locales. |
| `values/strings.xml:854-855` · `pt-rBR:833-834` · `ja:764-765` | `ui_battle_feed_support` ("got cheered · +%2$d CP"), `ui_battle_feed_support_window` | doc/i18n (incentivo) | Remover as 2 chaves nos 3 locales (branches de evento eliminados). |
| `values/strings.xml:906` · `pt-rBR:885` · `ja:816` | `ui_battle_loadout_cost` = "%1$d EN · **%2$d CP** · full field" | doc/i18n (custo) | Reformar para 1 argumento (EN) nos 3 locales. |
| `values/strings.xml:987-989` · `pt-rBR:966-968` · `ja:897-899` | `ui_battle_order_err_support_paused/no_window/window_expired` | doc/i18n (incentivo) | Remover as 3 chaves nos 3 locales. |
| `values/strings.xml:837` · `pt-rBR:816` · `ja:747` | `ui_battle_no_resources` = "Not enough energy **or command points**" / "Energia **ou pontos de comando** insuficientes" / "エナジーか**コマンドポイント**が不足" | doc/i18n (custo) | Reescrever mencionando só energia (o `OrderFailure.RESOURCES_MISSING` deixa de ter CP). |
| `DESIGN.md:734` | "Command band. One bottom band over its own scrim holds the **CP instrument**, ..." | doc | Atualizar descrição do HUD (sem instrumento de CP). |
| `CHANGELOG.md:51` | "...one bottom command band holds the **CP**..." | doc | Entrada histórica — não reescrever; registrar a remoção em nova entrada. |
| `OFFLINE_BATTLE_3D_PLAN.md:88,96,124,162,178,222-226,259,273` | Etapas 7 ("Incentivo, CP e especiais"), pausa de janelas, "CP compartilhado pelo treinador", debug "CP/reservas", resultado com "incentivos" | doc | Plano histórico: anotar como superseded pela remoção (ou atualizar status), sem exigir código. |
| `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:121` | "Existe infraestrutura de CP e incentivo (+8), mas o catálogo genérico atual mantém custo de CP padrão zero para técnicas." | doc | Atualizar: CP/incentivo removidos. |
| `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:458` | "Definir como **CP**, incentivo, Blast, FORM, DUO e Jogress são exibidos..." | doc | Atualizar recomendação (CP removido; manter incentivo? decisão de produto). |
| `DigimonWorldBattleEspecifications.txt:771,820,1689,2360,3289` | Especificação externa com `CommandPoints`/`OrderPoints` (ex.: "`CommandPoints`, `OrderPoints` ou equivalente", "Para o sandbox, `Energy` e `CommandPoints` podem continuar existindo") | doc (fonte de design) | Não é gerado pelo app; **não editar sem ordem do lead** — apenas registrar o conflito: a spec prevê CP, o código de produção nunca o usou de fato. |

---

## 2. Contagens por arquivo

Contagem = linhas que casam com o padrão CP/incentivo `commandPoints|commandPointCost|CommandPoints|pendingSupport|SupportWindowOpened|SupportSucceeded|TrainerAction.Support|SUPPORT_*|supportCommands|supportReady|reservedCommandPoints|opposingCommandPoints|pendingSupportCombatantIds` (grep em `app/src`).

| Área | Arquivo | Linhas | Intervalo | Papel dominante |
|---|---|---:|---|---|
| núcleo | `battle/offline/core/BattleSimulator.kt` | **36** | 56–2180 | estado do treinador + incentivo + custo + reserva + snapshot |
| UI | `battle/ui/offline/OfflineTrainingBattleScreen.kt` | **36** | 600–2865 | exposição (CP band, supportReady, erros, feed, gate de custo) |
| núcleo | `battle/offline/core/BattleModel.kt` | **13** | 89–339 | modelo (config, snapshot, eventos, failures, estatística) |
| UI | `battle/ui/offline/BattleChrome.kt` | **9** | 566–1104 | exposição (band, progress, resultado) |
| núcleo IA | `battle/offline/core/TamerTrainerController.kt` | **2** | 97–98 | incentivo (decisão de IA) |
| UI | `battle/ui/offline/BattleCommandDeckModel.kt` | **2** | 24–39 | exposição (slot/tecla) |
| domínio | `battle/domain/BattleDefinitions.kt` | **1** | 142 | custo de técnica (campo) |
| catálogo | `battle/offline/data/GenericTechniqueCatalog.kt` | **1** | 170 | custo de técnica (redundante, =0) |
| UI | `battle/ui/offline/TechniqueLoadoutScreen.kt` | **1** | 292 | exposição (custo no loadout) |
| sessão | `battle/offline/session/BattleSceneChannel.kt` | **1** | 57 | incentivo (throttle de snapshot) |
| **main total** | **10 arquivos** | **102** | — | |
| teste | `test/.../BattleSimulatorTest.kt` | **28** | 169–733 | teste |
| teste | `test/.../BattleFinisherTest.kt` | **9** | 10–338 | teste |
| teste | `test/.../session/BattleSceneChannelTest.kt` | **3** | 19–23 | teste |
| teste | `test/.../ui/offline/BattleCommandDeckModelTest.kt` | **3** | 15–66 | teste |
| teste | `test/.../DuoBlastTest.kt` | **3** | 269–271 | teste |
| teste | `test/.../ui/offline/BattleVfxCueTest.kt` | **3** | 14–18 | teste |
| teste | `test/.../GenericTechniqueCatalogTest.kt` | **1** | 73 | teste |
| **test total** | **7 arquivos** | **50** | — | |
| teste device | `androidTest/.../ArenaPersistenceTest.kt` | **0 por nome** (2 args posicionais: `:58`) | 58 | teste (construtor posicional) |
| checkpoint | `world/ecosystem/NpcBattleAdapter.kt` | **0 por nome** (envelope) | 12–64 | checkpoint |
| checkpoint | `world/ecosystem/WorldDialogue.kt` | 0 | 31–52 | checkpoint |
| checkpoint | `data/database/WorldDialogueSchema.kt` | 0 | 27–33 | checkpoint |
| i18n | 3 × `strings.xml` (values, pt-rBR, ja) | **27 chaves** (9 por locale) | — | doc/i18n |
| docs | `DESIGN.md`, `CHANGELOG.md`, `OFFLINE_BATTLE_3D_PLAN.md`, `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md`, `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md`, `DigimonWorldBattleEspecifications.txt` | **17 referências** | — | doc |

**Produção não-CP que menciona "Support" e deve permanecer:** `TechniqueKind.SUPPORT`, `isSupport()` (`BattleSimulator.kt:1817-1818`), `TechniqueKind.HEAL`, técnicas de suporte/cura do catálogo, `BattleStatusEffect` de buff, `TrainerAction` demais casos.

---

## 3. Mapa de dependências CP ↔ incentivo ↔ reservas ↔ snapshot

```
                         BattleConfiguration.commandPoints (default 0)
                         └─ BattleConfiguration.maxCommandPoints (default 100)
                                   │ init (mesmo valor p/ os 2 lados)
                                   ▼
     ┌───────────────────► commandPoints (ALLIED) / opposingCommandPoints (OPPOSING)
     │                             │
     │                             │ commandPointsFor(side)  ◄──────────────┐
     │                             │ availableCommandPoints(side) =          │
     │                             │     commandPointsFor(side)              │
     │                             │     − Σ Reservation.commandPoints       │
     │                             ▼                                         │
     │            TechniqueDefinition.commandPointCost (PRODUÇÃO = 0)        │
     │                             │                                         │
     │        ┌────────────────────┼──────────────────────────┐              │
     │        ▼                    ▼                          ▼              │
     │  gate issueOrder      gate approachAndExecute     prepareDuoAttack     │
     │  (:280)               (:1157)                     (:357-362)           │
     │        │                    │                          │              │
     │        └─────► Reservation(orderId, energy, commandPoints, charge) ───┘
     │                                   (criado :283; liberado :1953/:1228)
     │                             │
     │                             ▼
     │                     spendCommandPoints(side, cost)  (:1227, :362)
     │
     │   janela de incentivo (independe de custo):
     │   TechniqueHit com damage > 0 e side ALLIED|trainerControllers
     │        └─► pendingSupport[combatantId] = elapsed + 1000 ms   (:1377-1381)
     │                                                                  │
     └──── TrainerAction.Support (:210-221) — ordem imediata, sem fila:  │
           ├─ rejected se paused / sem janela / janela expirada          │
           ├─ pendingSupport.remove(actorId)  ──────────────────────────┘
           ├─ gained = min(8, maxCommandPoints − commandPointsFor(side))
           ├─ spendCommandPoints(side, −gained)      → saldo += gained
           ├─ statistics.supportCommands++ (só ALLIED) (:217)
           └─ emit(SupportSucceeded) (:220)
                                     │
                                     ▼
        BattleSnapshot.commandPoints / maxCommandPoints / reservedCommandPoints /
        opposingCommandPoints / pendingSupportCombatantIds        (:486-505)
                                     │
        ┌────────────────────────────┼─────────────────────────────────────────┐
        ▼                            ▼                                         ▼
  HUD CP band + progress      supportReady / tecla SUPPORT           throttle BattleSceneChannel
  (BattleChrome:658-674;      (BattleChrome:600;                     (:57)
   OfflineScreen:2153-2186)    OfflineScreen:605-606,1262,1456)
                                     │
                                     ▼
                    TrainerAction.Support (UI :613,1267,1463 /
                    IA TamerTrainerController:97-98)
                                     │
                                     ▼
                    BattleEvent.SupportWindowOpened / SupportSucceeded
                    → feed de UI (:2864-2865) e strings (+CP)
                                     │
                                     ▼
                    statistics.supportCommands → BattleResult →
                    string de resultado ("cheers/%;d") (BattleChrome:1104)

  CHECKPOINT (paralelo, sem CP explícito):
  BattleConfiguration (com commandPoints/maxCommandPoints) ──Gson──► definitionsJson
        └─ recover(): Gson desserializa config (campos desconhecidos ignorados)
           + BattleSimulator(config, definitions) → replay de N steps fixos
           (NpcBattleAdapter:31-64; Orchestrator:193-207)
        └─ NpcBattleSummary (persistido) NÃO carrega CP/estatística (WorldDialogue:45-52)
```

**Fatos-chave do mapa:**

* CP **entra** no jogo só por `Support` (+`min(8, …)`, com cap no teto) e **sai** só por custo de técnica/reserva — que é **0 em produção**. Consequência: em produção o CP é monotônico crescente e nunca consumido.
* `opposingCommandPoints` só é incrementado se o treinador adversário (arena, `trainerPolicies`) aceitar uma janela; nunca é decrementado em produção. Não há HUD para ele.
* O incentivo **não** consome ação do parceiro e **não** avança a simulação: é um branch que retorna antes da fila (`:222`/`:266`). Portanto removê-lo não altera posições/estados/energia/RNG de quem quer que fosse — só eventos, estatística e o saldo de CP.
* A janela reabre a cada hit com dano > 0 (sobrescreve expiração) e expira por tempo de simulação; pausa **não** expira janela (o `step()` não roda), só impede o aceite.
* `BattleSnapshot` → UI é o único consumidor dos campos de CP; nenhum deles é persistido.

---

## 4. Catálogo: confirmação de custo 0 em produção

Grep de `commandPointCost` em todo `app/src` (20 ocorrências):

| Ocorrência | Custo | Contexto |
|---|---|---|
| `BattleDefinitions.kt:142` | default `0` | declaração do campo |
| `GenericTechniqueCatalog.kt:170` | **0** | `legacyBattleDefinitions` (catálogo v2 do checkpoint array) — único valor explícito de produção |
| `GenericTechniqueCatalog.kt:245-328` (`entry`) | **não passa** ⇒ 0 | 57 técnicas `generic_*` do catálogo v3 |
| `GenericTechniqueCatalog.kt:188` (`counter`) | **não passa** ⇒ 0 | contra-ataque |
| `BattleSimulator.kt` (`BASIC:2214`) | **não passa** ⇒ 0 | ataque básico de fallback |
| `TamerBattleLoadouts.kt:92` | **não passa** ⇒ 0 | "Ocean Love" (Tamer Arena) |
| Testes: `BattleFinisherTest.kt:10` | **5** | especial do fixture de finisher |
| Testes: `BattleSimulatorTest.kt:513` | **20** | teste de reserva de CP |
| Testes: `BattleSimulatorTest.kt:650` | **20** | teste de expiração/reserva |
| Testes: `BattleSimulatorTest.kt:687` | **10** | teste "enemy automation cannot spend CP" |
| Testes: `BattleSimulatorTest.kt:530,593` / `GenericTechniqueCatalogTest.kt:73` | 0 | asserts/copies de custo zero |

**Conclusão:** não existe nenhum caso **não-zero em produção**. Os 4 casos não-zero são exclusivamente fixtures de teste do próprio subsistema CP; podem ser removidos/reescritos sem perda de cobertura válida (a cobertura útil deles é reserva de energia e expiração de ordem, que continua existindo).

---

## 5. Checkpoint e compatibilidade (análise)

### 5.1 Como o envelope é construído

`NpcBattleAdapter.encode()` (`world/ecosystem/NpcBattleAdapter.kt:19-23`) grava **uma string JSON** em `WorldNpcBattle.definitionsJson`:

```json
{
  "formatVersion": 1,
  "catalogVersion": 2 | 3,
  "configuration": { "alliedTeamId": "...", "arenaRadius": 8.0, ...,
                     "commandPoints": 0, "maxCommandPoints": 100, ... },
  "definitions": [ { ... CombatantDefinition ... } ]
}
```

* `formatVersion` é literal `1`; `catalogVersion` = `2` (ruleset legado) ou `OfflineBattleRuleset.CATALOG_VERSION = 3`.
* `configuration` é serializada por Gson **puro** — sem `GsonBuilder`, sem `TypeAdapter`, sem `@SerializedName` (grep confirmou ausência de customização no código-fonte; Gson 2.14.0 em `gradle/libs.versions.toml:27`).
* Formatos aceitos na leitura (`:25-29`, `:32-37`): envelope objeto (v1) **ou** array JSON legado de `CombatantDefinition` (v2, gera `Checkpoint(1, 2, BattleConfiguration(randomSeed = event.seed, defaultPaused = false, arenaRadius = 8f, rulesetVersion = LEGACY), definitions)`).
* `BattleSnapshot` **não** é serializado em nenhum checkpoint: o que vai para o banco como `snapshotJson` é `NpcBattleSummary` (elapsed + membros + outcome, `WorldDialogue.kt:45-52`). `BattleResult/statistics` também não são persistidos em batalha NPC.

### 5.2 Gson ignora campos desconhecidos? Sim

* Na leitura (`Gson().fromJson(root, Checkpoint::class.java)`), o Gson default **salta membros JSON que não existem na classe alvo**. Não há `FAIL_ON_UNKNOWN_PROPERTIES` (isso é Jackson) nem `GsonBuilder` estrito no repositório. Portanto, após remover `commandPoints`/`maxCommandPoints` de `BattleConfiguration`, um envelope antigo que **ainda os contenha** desserializa normalmente e os valores são descartados.
* Sentido inverso (registro novo lido por app antiga) não é necessário. Mesmo se fosse, a app antiga encontraria campos ausentes: `BattleConfiguration` tem **todos os parâmetros com default**, o que faz o Kotlin gerar um construtor sem argumentos; o Gson o utiliza e mantém os defaults Kotlin (`commandPoints = 0`, `maxCommandPoints = 100`) nos campos ausentes. Esse é exatamente o comportamento que motivou o pin manual de `movementRulesVersion` (`NpcBattleAdapter.kt:44-49`) — mas, para CP, o default é **inofensivo**, então não é preciso replicar o pin.

### 5.3 O replay muda? Não

`recover()` (`NpcBattleAdapter.kt:31-64`) **nunca lê CP**: reconstrói `BattleSimulator(configuration, teams, catálogo da versão)` e reexecuta `advance(STEP)` até `elapsedMillis`. Argumentos:

1. **Batalhas NPC/mundo nunca emitem ordens** — o orchestrator apenas chama `advance` (`WorldInteractionOrchestrator.kt:193-207`); `TrainerAction.Support` jamais ocorre; o saldo nunca muda; `pendingSupport` nunca é consultado por ordem alguma.
2. **Custo de técnica = 0 em produção** ⇒ nenhum `spendCommandPoints`/reserva de CP acontece; remover essas linhas não altera nenhum saldo que alguém leia.
3. **RNG não depende de CP nem de order ids** — `randomFor(stableKey, streamTag)` deriva de `configuration.randomSeed:stableKey:streamTag` (`BattleSimulator.kt:1136-1143`). Remover ordens de incentivo muda `nextOrderId` apenas em batalhas com `trainerPolicies` (arena), que não são objeto de checkpoint; e order ids são opacos.
4. **O que muda em memória:** `eventCount`, o tail `recentEvents` (≤96) e `statistics` (campo `supportCommands` eliminado). Nada disso é persistido em batalha NPC; o `NpcBattleSummary` guarda apenas posição temporal + membros/hp/energia + outcome. Logo, um save antigo recuperado após o update reproduz **mesmas HP/energia/posições/outcome**.
5. **Arena/treino (não checkpoint):** o replay é live×replay na **mesma build** (ex.: `NpcBattleVersionTest`); os testes continuam válidos, apenas com menos eventos e sem `supportCommands`.

### 5.4 Política de compatibilidade recomendada

| Opção | Veredito | Justificativa |
|---|---|---|
| **Manter `formatVersion = 1` e tolerar legado** | **Recomendado** | Mudança retrocompatível na leitura (campos desconhecidos ignorados), replay inalterado, zero migração de dados. O `require(checkpoint.formatVersion == 1)` (`:38`) continua aceitando todos os registros existentes — inclusive batalhas NPC **ativas** salvas antes do update. |
| Bump `formatVersion` para 2 + `require in 1..2` | Desnecessário | Custaria aceitar 2 formatos e um teste de migração; nenhum dado semântico novo é introduzido. Só se justifica se outro campo ganhar semântica histórica. |
| Bump para 2 + `require == 2` | **Não fazer** | Rejeitaria registros antigos e quebraria batalhas NPC em andamento (perda de progresso) sem ganho. |
| Migração explícita (`commandPointsRemoved: true`) | Não necessária | Nada no replay consome CP; o gravador novo já omite as chaves. |

**Ações de checkpoint recomendadas:** (i) manter `formatVersion`/`catalogVersion` como estão; (ii) **não** alterar `CATALOG_VERSION` (o CP não é campo de `TechniqueDefinition` serializada — o envelope persiste `CombatantDefinition`); (iii) manter o pin de `movementRulesVersion` intocado; (iv) adicionar teste de regressão barato: serializar um envelope, injetar `"commandPoints": 7, "maxCommandPoints": 42` no objeto `configuration`, reabrir e comparar `recover().snapshot()` live×replay (ou simplesmente garantir que decode não lança). Esse teste documenta a tolerância ao legado.

---

## 6. Riscos e lacunas

**Compilação / construtores posicionais (alto risco, mecânico)**
1. `BattleSnapshot` é construído **posicionalmente** em `BattleSimulator.kt:486-505` e em `androidTest/.../ArenaPersistenceTest.kt:58-63`. Remover `commandPoints` e `maxCommandPoints` (posições 4 e 5) desloca todos os argumentos seguintes → atualizar os 2 call sites. Os demais testes usam `named arguments` (`BattleVfxCueTest`, `BattleSceneChannelTest`, `DuoBlastTest`) e são triviais.
2. `BattleConfiguration(commandPoints = …)`/`(maxCommandPoints = …)` aparece só em testes (`BattleSimulatorTest.kt:452,515,537,652,689,733`; `BattleFinisherTest.kt:24-33,165`). Produção nunca define CP — o default 0/100 é o que roda de verdade.
3. `TechniqueDefinition` tem todos os call sites com **named args** após as 4 primeiras posições → remoção do campo é compile-safe exceto nos testes que o citam por nome (`copy(commandPointCost=…)`, asserts).
4. **`when` exaustivos**: remover subclasses de `BattleEvent`/`TrainerAction` obriga a apagar os branches correspondentes em UI e controller; verificar `when` sem `else` (ex.: feed de eventos em `OfflineTrainingBattleScreen.kt:2843+`, mapping de `OrderFailure`). `TrainerAction` em `issueSideOrder` usa `else -> Unit` (`:264`), mas o bloco `TrainerAction.Support` é um branch dedicado.

**Produto / UX (decisão pendente, não só mecânica)**
5. **Layout do command deck**: 9 teclas em 3×3 incluem `SUPPORT` (`BattleChrome.kt:543-558`, `OfflineTrainingBattleScreen.kt:2225-2301`, `BattleCommandDeckModel.kt:9-19`). Remover deixa **8 slots**. `BattleCommandDeckModelTest` fixa a ordem/quantidade (9) e "unavailable actions do not disappear" (memória muscular). Precisa de decisão: deixar 8 (reflow) ou manter 9 com outra ação. **Sinalizar ao lead/owner de UI.**
6. Instrumento de CP no HUD (barra "CP x/100" + `LinearProgressIndicator`) some; o `BattleCommandBand` perde 2 props e o header pode ficar visualmente vazio — revisar layout (`BattleChrome.kt:558-674`).
7. `ui_battle_result_stats` perde "cheers" (6→5 placeholders) e `ui_battle_loadout_cost` perde CP (2→1); strings precisam ser reescritas **nos 3 locales** (values, pt-rBR, ja) — não deixar `%3$d` órfão (crash/lint de formatação).
8. `ui_battle_no_resources` ("Energia ou pontos de comando insuficientes") deve virar só energia; caso contrário a mensagem de falha fica incorreta (o `OrderFailure.RESOURCES_MISSING` continua existindo para energia/special charge).

**Comportamento / testes**
9. **IA de arena** deixa de emitir ordens de incentivo (`TamerTrainerController.kt:97-98`). Efeito colateral: menos ordens → `nextOrderId` menor; nenhum teste atual fixa order ids numéricos (usam os retornados), mas testes que contam eventos/`OrderChanged` podem precisar de ajuste.
10. `BattleSceneChannel.shouldPublishUiSnapshot` perde o gatilho de janela de incentivo (`:57`); a UI deixa de publicar imediatamente na abertura/fechamento de janela — como a tecla de incentivo some, não há regressão funcional, mas o teste de throttle (`BattleSceneChannelTest`) pode precisar de caso substituto.
11. `statistics.supportCommands` removido altera a igualdade de `BattleStatistics` → testes que comparam snapshots live×replay continuam ok (mesma build), mas fixtures que constroem o data class por posição/nome podem quebrar (grep: não há construções explícitas de `BattleStatistics` fora do simulador/tests de snapshot — checar no build).
12. **Docs/design em conflito:** `DigimonWorldBattleEspecifications.txt` (§ CommandPoints/OrderPoints) e planos `OFFLINE_BATTLE_3D_PLAN.md` descrevem CP como pilar. A remoção deixa a spec desalinhada; decisão editorial do lead (não editar a spec sem ordem).

**Lacunas do inventário (o que este arquivo não cobre / precisa de outro dono)**
13. **Strings/resources**: listei as chaves e linhas nos 3 locales, mas não fiz inventário de `values-v29` (não contém chaves de batalha) nem de ContentDescription/testTags associados (`offline-battle-command-header` etc.) — podem precisar de ajuste de testes de UI device.
14. **AR battle**: `ArBattleScene.kt` recebe `BattleSnapshot`, mas não referencia CP diretamente; se o overlay AR reusar `BattleCommandBand`, herda a remoção. Não localizei uso de CP em `app/src/main/java/com/tamerslab/ar`.
15. **Testes device de UI**: não executei build/testes (somente leitura). A lista de testes acima é por grep; pode haver testes que quebrem por compilação de fixtures posicionais não listadas (ex.: construtores posicionais de `CombatantSnapshot`/`BattleResult`, que **não** mudam).
16. **Persistência de arena/quests**: `ArenaRepository.kt:119` usa `snapshot.statistics.itemsUsed`; `QuestBattleFacts`/`QuestBattleInventory` não usam `supportCommands` (grep). Sem migração de dados além do envelope NPC.
17. **`CATALOG_VERSION`**: confirmei que `TechniqueDefinition` não é serializada em checkpoint; se algum dia for, a remoção do campo exigiria política de versão — hoje não é o caso.

---

## 7. Sequência de remoção sugerida (dependente de ordem)

1. **Modelo primeiro** (`BattleDefinitions.kt:142`, `BattleModel.kt:107-108,139,166-168,283-292,305,338-339,89`) — quebra a compilação e revela **todos** os call sites por erro do compilador (é a forma mais segura de inventariar consumidores).
2. **Núcleo** (`BattleSimulator.kt`): remover `pendingSupport`, os 2 campos de saldo, require, branch `Support`, janela (`:1377-1381`), helpers, expirações/limpezas, cláusulas de custo e campo de `Reservation`; ajustar `snapshot()`.
3. **IA** (`TamerTrainerController.kt:97-99`).
4. **Sessão** (`BattleSceneChannel.kt:57`).
5. **UI** (`BattleCommandDeckModel.kt`, `BattleChrome.kt`, `OfflineTrainingBattleScreen.kt`, `TechniqueLoadoutScreen.kt`) — decidir o layout de 8 slots antes/durante este passo.
6. **i18n** (27 chaves nos 3 locales) + `ui_battle_no_resources`.
7. **Testes**: remover os 2 testes de incentivo; reescrever reserva/expiração só com energia; limpar fixtures; ajustar deck/resultado; adicionar teste de tolerância a envelope legado.
8. **Docs**: atualizar `DESIGN.md`, `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md`, `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md`, status em `OFFLINE_BATTLE_3D_PLAN.md`; nova entrada em `CHANGELOG.md`; spec `DigimonWorldBattleEspecifications.txt` só com ordem explícita.
9. **Checkpoint**: nenhuma mudança de código necessária (`formatVersion`, `catalogVersion`, schema Room e `movementRulesVersion` ficam); apenas o teste de compat opcional.

---

## 8. Checklist de verificação (após a remoção)

```powershell
# 1) Nenhum símbolo CP/incentivo no código de produção
rg -n "commandPoint|CommandPoint|pendingSupport|SupportWindowOpened|SupportSucceeded|TrainerAction\.Support|SUPPORT_PAUSED|SUPPORT_NO_WINDOW|SUPPORT_WINDOW_EXPIRED|supportCommands|supportReady|reservedCommandPoints|opposingCommandPoints|pendingSupportCombatantIds" app/src/main
# 2) Nenhum custo CP no catálogo
rg -n "commandPointCost" app/src
# 3) "Support" restante deve ser só técnica/tecla não-incentivo (revisar manualmente)
rg -n "Support|SUPPORT" app/src/main/java/com/tamerslab/battle | rg -v "TechniqueKind.SUPPORT|isSupport|BattleCommandSlot"
# 4) Strings órfãs de CP/cheer
rg -n "CP|cheer|incentivo|応援" app/src/main/res/values*/strings.xml
# 5) Checkpoints antigos: rodar NpcBattleVersionTest + (novo) teste de envelope legado com commandPoints
# 6) Snapshots posicionais atualizados
rg -n "BattleSnapshot\(" app/src
```

**Critérios de aceite do inventário:** todo `file:line` acima foi confirmado por leitura do arquivo; custos de produção confirmados = 0; política de checkpoint justificada por leitura de `NpcBattleAdapter.kt` (Gson default, `formatVersion == 1`, replay por steps fixos) e pela ausência de serialização de `BattleSnapshot`.
