# Auditoria Impeccable — Tela Tamer Rank

Alvo: `app/src/main/java/com/tamerslab/rank/ui/RankScreen.kt` (Android/Compose, modo **Operate**).
Referências: `DESIGN.md` (Tamers Lab), `reference/android.md`, `reference/audit.native.md`.
Estrutura de referência funcional: aba **Tamer** do VB Lab 1.4.6 (emblema grande + Tamer Level +
EXP restante, faixa Offline, linhas de V-Coins/Digimon/Digidex, Win Rate, troféus, Milhas, Dim Cards).

## Placar da auditoria (antes)

| # | Dimensão | Score | Achado principal |
|---|---|---|---|
| 1 | Acessibilidade | 2/4 | Barras de progresso sem semântica, alvos < 48dp nos claims, textos auxiliares em cor de baixo contraste |
| 2 | Performance | 3/4 | `Column` rolável simples, sem lista longa; `RankConfig()` recriado fora de `remember` em alguns pontos |
| 3 | Aparência & Tema | 3/4 | Tokens corretos, mas emblema sempre ciano e painéis sem a moldura técnica do app |
| 4 | Conformance Android | 1/4 | Header próprio no lugar do `TopBanner` (regra vinculante do DESIGN), botão de texto sem 48dp, hierarquia não-Material |
| 5 | Adaptabilidade | 2/4 | Hero em linha fixa quebra em fonte grande/tela estreita; galeria sem estados de lock acessíveis |
| **Total** | | **11/20** | **Acceptable — trabalho significativo** |

Conformance verdict: a tela **não passava** como superfície nativa do Tamers Lab: header fora do
vocabulário `TopBanner`, painéis sem moldura, métricas VB Lab ausentes (bits/Digimon/Dex/vitórias/
troféus), e conteúdo sem a ordem de leitura do app.

## Achados por severidade (antes)

- **[P0] Header fora do contrato do app.** `RankHeader` customizado substituía `TopBanner`. O DESIGN
  exige `TopBanner` em todas as telas. → **Corrigido** com `TopBanner(text, subtitle, back, gear →
  Settings)`, que também dá acesso à edição de perfil (equivalente ao “Edit” do VB Lab).
- **[P1] Estrutura não correspondia à aba Tamer.** Faltavam o herói com emblema grande + nível + EXP
  restante, a faixa Offline, as linhas de registro com ícones e o bloco de Win Rate. → **Corrigido**
  replicando a anatomia do VB Lab com dados reais do Tamers Lab.
- **[P1] Dados fictícios/ausentes.** A tela não mostrava moedas, Digimon guardados, Digidex,
  vitórias/derrotas e troféus. → **Corrigido** com consultas agregadas reais
  (`UserCharacterDao.observeCharacterCount/observeTotalWins/observeTotalLosses/observeTotalTrophies`,
  `DexDao.observeDiscoveredCount/observeCatalogCount`, `CurrencyRepository.currencyValue`).
- **[P1] Progresso sem semântica.** Barras desenhadas em `Box` não anunciavam progresso. →
  **Corrigido** com `ProgressBarRangeInfo` + descrições localizadas (`rank_progress_*_a11y`).
- **[P1] Alvos de toque pequenos.** `TextButton` de claim e card da Home abaixo de 48dp. →
  **Corrigido** com `VitalButton` (min 48dp) e card inteiro clicável com 48dp.
- **[P1] Contraste de texto auxiliar.** `TextMutedOnDark` em corpo. → **Corrigido** usando
  `TextSecondaryOnDark` para corpo e `TextMutedOnDark` só em metadados/bloqueios.
- **[P2] Adaptabilidade.** Hero em `Row` fixa. → **Corrigido** com `BoxWithConstraints` +
  `fontScale > 1.3`: hero empilha em tela estreita/fonte grande; textos sem largura fixa.
- **[P2] Moldura ausente.** Emblema e ícones sem `cyberFrame`. → **Corrigido** com `cyberFrame`
  (moldura técnica) e `CyberPanel` ativo no herói.
- **[P2] Galeria sem estado acessível.** → cada tile tem `contentDescription` com rank/level de
  desbloqueio e o atual marcado com moldura ativa.
- **[P3] Separadores.** → divisores pontilhados do VB Lab substituem linhas vazias.

## Estrutura final (mapeamento VB Lab → Tamers Lab)

| VB Lab | Tamers Lab (implementado) |
|---|---|
| Emblema hexagonal + Tamer Level + Until Next Level | `CyberPanel` ativo: emblema emoldurado + Tamer Level grande + `X TXP` restante + barras de nível/rank |
| Faixa “Offline” | removida a pedido do dono do produto (offline é implícito no fluxo local) |
| V-Coins / Digimon Trained / Digidex / Win Rate / Trophies | Bits / Digimon guardados / Digidex descobertos/total / Win Rate com vitórias-derrotas / Troféus / TXP de Tamer |
| Miles (linhas pontilhadas com data) | Atividade de Tamer (últimos 8, data + XP, divisores pontilhados) |
| Dim Cards | Galeria de emblemas (16, com lock por nível) |
| Edit (rodapé) | Ação de engrenagem no `TopBanner` → Configurações (perfil/nome do Tamer) |
| — (novo) | Recompensas de rank (claim/claim-all, `VitalButton` 48dp) |

## Pós-correção (esperado)

| # | Dimensão | Estimativa |
|---|---|---|
| 1 | Acessibilidade | 3/4 |
| 2 | Performance | 4/4 |
| 3 | Aparência & Tema | 4/4 |
| 4 | Conformance Android | 3/4 |
| 5 | Adaptabilidade | 3/4 |
| **Total** | | **17/20 (Good)** |

Verificação renderizada (emulador x86_64) pendente de captura; build/teste serão reexecutados após a
mudança e as evidências ficam registradas no `IMPLEMENTATION.md`.
