# Tamer Rank — implementação (offline)

Implementação completa das fases M0–M6 do plano (`analysis/rank-system/PLAN.md`), em uma passada.

## O que foi entregue

### Domínio (`app/src/main/java/com/tamerslab/rank/domain/`)
- `RankModels.kt`: `TxpSource` (fontes + caps), `RankConfig` (curva de nível `60 + 3·(l−1)`,
  cap 100, marcos de rank 1/4/8/…/96, tabela de recompensas), `RankCalculator` (puro: nível, rank,
  progresso, clamp), `TamerProfileSnapshot`, `RankRewardState`, `RankActivityEntry`, `TxpAward`,
  `RankNotification`, `RankEventSink`.
- `RankRepository.kt`: contrato local (Flows + award/claim).

### Dados (`app/src/main/java/com/tamerslab/rank/data/`)
- `RankEntities.kt`: `tamer_profile`, `tamer_txp_event` (idempotência UNIQUE), `tamer_daily_cap`,
  `tamer_reward`, `tamer_activity`.
- `RankDao.kt`, `RankSchema.kt` (migração 57→58), `RankSettingsStore.kt` (DataStore: card na Home,
  notificações), `RankEvents.kt` (sink global usado pelos hooks), `RankRepositoryImpl.kt`
  (transação, caps diários, idempotência, recompensas, feed, notificações).
- `AppDatabase`: versão **58**, 5 entidades novas, `rankDao()`, `MIGRATION_57_58`.
- DI: `AppContainer.rankRepository/rankSettings`, `DefaultAppContainer` (DataStore `rank_settings`,
  instala o sink), `AppServices` expõe ambos.

### UI (`app/src/main/java/com/tamerslab/rank/ui/RankScreen.kt`)
- Tela Rank: header + emblema original (16 sprites `vb_rank_00..15`, já copiados para
  `res/drawable-nodpi`), rank/level/EXP, barra de progresso, próximo marco, recompensas pendentes
  (claim/claim-all), galeria 4×4 com bloqueio por nível, atividade recente, diálogo de
  rank-up/level-up (respeita o toggle de notificações e reduce-motion não anima).
- `RankHomeCard`: card compacto na Home (emblema + nível + barra).
- Navegação: `AppRoute.Rank`, `NavigationItems.Rank` (overflow “Tamer Rank”),
  `composable<AppRoute.Rank>` no `AppNavigation`.
- Home: `FarmHomeScreen` mostra o card (abaixo do HUD, com `zIndex`), controlado por
  `rankHomeCardEnabled`.
- Configurações: seção **Tamer Rank** com dois switches (`Exibir card na Home`,
  `Mostrar avisos de rank`), persistidos em DataStore.
- Strings: `strings_rank.xml` em `values`, `values-pt-rBR`, `values-ja`.

### Hooks de progressão
- **NFC** (`transfer/ui/scan/ScanScreenControllerImpl`): leitura NFC-A e HCE, escrita NFC-A e HCE
  → `NFC_READ`/`NFC_WRITE` (após sucesso/ack).
- **Batalha** (`world/ecosystem/WorldInteractionRepository`): `BATTLE_WIN`/`BATTLE_LOSS` após
  `recordBattleResult`.
- **Quests** (`quests/QuestRepository`): `QUEST` no claim de recompensa.
- **Dex** (`species/data/DigimonScanRepository`): `DEX_NEW` ao registrar espécie.
- **Itens** (`items/data/GameplayRepository`): `ITEM_USE` após consumir item.
- Demais fontes (`STORAGE`, `WORLD`, `ADVENTURE`, `DAILY`, `STREAK`, `NFC_MISSION`,
  `NFC_TRANSFER`) já existem no domínio/`RankEvents`; podem ser ligadas a novos pontos sem mudar o
  modelo (os caps e chaves já estão prontos).

### Regras
- `TXP → nível` (curva linear), `nível → rank` (16 marcos; fiel à ajuda oficial do VB Lab).
- Caps diários por fonte, chave de idempotência UNIQUE, transação única por evento, clock-aware
  (sem regressão: total nunca diminui).
- Recompensas: moedas (creditadas via `CurrencyRepository`) + cosméticos/títulos/desbloqueios
  (registrados/claimáveis). Itens usam o gancho `labelKey = "item:<id>"` quando um catálogo
  confiável for definido.
- Sem rede, sem ranking online: a aba é “histórico pessoal + metas” (decisão do usuário).

## Arquivos novos/alterados (principais)
```
rank/domain/{RankModels,RankRepository}.kt
rank/data/{RankEntities,RankDao,RankSchema,RankSettingsStore,RankEvents,RankRepositoryImpl}.kt
rank/ui/RankScreen.kt
data/database/AppDatabase.kt        (+5 entidades, versão 58, migração)
di/{AppContainer,DefaultAppContainer,AppServices}.kt
navigation/{AppRoute,NavigationItems,BottomNavigationBar,AppNavigation}.kt
farm/ui/home/FarmHomeScreen.kt      (card)
settings/ui/{SettingsScreen,SettingsScreenControllerImpl}.kt (switches)
res/values*/strings_rank.xml
app/src/test/java/com/tamerslab/rank/RankCalculatorTest.kt
+ hooks: ScanScreenControllerImpl, GameplayRepository, QuestRepository,
  WorldInteractionRepository, DigimonScanRepository
```

## Overhaul visual (Impeccable)
A tela Tamer foi refeita para parecer uma página nativa do Tamers Lab (TopBanner, `VitalSectionBar`,
`VitalButton`, `cyberFrame`, espaçamento de instrumento) mantendo a anatomia da aba Tamer do VB Lab
(emblema grande + Tamer Level + EXP restante, status offline, linhas de registro, Win Rate, troféus,
atividade e galeria de emblemas). O card flutuante da Home foi substituído por um readout minimalista
no HUD (emblema pequeno + nível), controlado pelo mesmo switch. Auditoria em `AUDIT.md`.

## Verificação (executada depois de tudo implementado)
- `:app:compileDebugKotlin` → **BUILD SUCCESSFUL**.
- `:app:compileIntegrityCheckKotlin` (variante de teste) → **BUILD SUCCESSFUL**.
- `:app:testIntegrityCheckUnitTest --tests "com.tamerslab.rank.RankCalculatorTest"` → **8/8 testes verdes**
  (curva de nível, marcos de rank, progresso, caps).
- Migração Room 57→58 aplicada via `MIGRATION_57_58` (tabelas criadas; dados existentes preservados).
- Build/test executados com JDK 25 + Gradle 9.6.1 offline; `gradle-daemon-jvm.properties` restaurado.

## Correção pós-build (crash na abertura)
O primeiro APK crashava ao abrir: a migração 57→58 criava `occurredAt`/`claimedAt`, mas as entidades
Room usam `occurred_at`/`claimed_at`; a validação do schema falhava na abertura do banco. SQL corrigido
e schema exportado (58.json) conferido coluna a coluna. APK debug reconstruído com sucesso.

## Limitações conhecidas
- Não há nomes oficiais de rank (não existem no cliente VB Lab): UI mostra “Tamer Rank N” + emblema.
- “Ranking” é histórico pessoal/metas; não há leaderboard online.
- Ainda não testado em aparelho (build/JVM tests executados no fim da implementação).
- Recompensas de **itens** aguardam catálogo de itens estável; moedas/cosméticos/títulos já funcionam.
