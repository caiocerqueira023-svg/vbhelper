# Plano de balanceamento da batalha offline — v3

> **Status:** implementação parcial; pipeline de fonte, fallback por eixo, seed de revanche e dinâmica derivada de HP/BP/AP já iniciados. O mapeamento das 16 personalidades, seleção softmax e streams individuais da IA/dano agora estão no código; os gates de balanceamento e a simetria completa do motor continuam pendentes.
> **Escopo:** batalha offline em tempo real, principalmente 1×1 autônomo, preservando a arquitetura de treinador + criatura autônoma de `DigimonWorldBattleEspecifications.txt`.
> **Fontes centrais:** personalidade persistida por indivíduo e HP/BP/AP provenientes do cartão/relógio.
> **Decisão recomendada:** stats definem capacidade; personalidade define decisão. Personalidade não concede poder bruto no primeiro rollout.

---

## 1. Resultado do diagnóstico

O problema percebido como “um Digimon sempre vence” não tem uma única causa. O diagnóstico original encontrou cinco efeitos; o estado de implementação aparece em cada item:

1. **A revanche repetia a mesma luta — corrigido.** `OfflineBattleSessionViewModel.retry` agora cria uma seed nova; a repetição determinística continua disponível apenas ao fornecer explicitamente a mesma seed.
2. **Uma vantagem pequena nos três stats se multiplica.** HP aumenta sobrevivência, AP reduz o tempo para derrotar e BP reduz dano. Cinco por cento em cada eixo não é uma vantagem total de cinco por cento.
3. **O perfil de stats era tudo-ou-nada — corrigido.** HP, BP e AP agora resolvem fallback por eixo; um BP ausente não descarta HP e AP válidos.
4. **A personalidade chega ao combatente e agora altera decisões.** Ela é persistida por `individualId` e atravessa `OfflineBattleParticipant`, `TrainingParticipantInput` e `CombatantDefinition`. `PersonalityBattleProfile` deriva núcleo e pesos comportamentais dos 16 tipos, mantendo `BattleStrategy` como camada separada; o simulador usa esses pesos em escolha de técnica, guarda, reserva de energia, distância, persistência de alvo e ritmo de abertura.
5. **Todos usam o mesmo kit e o controle é cumulativo.** Os combatentes recebem as mesmas três técnicas. Stagger/knockback podem cancelar uma ação e favorecer quem começou a pressionar, tornando uma pequena vantagem inicial mais estável.

### 1.1 Sonda headless realizada

Foi executado um teste descartável contra o simulador atual; o arquivo de teste foi removido depois da medição.

| Cenário 1×1 autônomo | Seeds | Resultado |
|---|---:|---|
| Espelho perfeito | 400 | aliado 195, oponente 202, empate 3 |
| Stats médios contra +5% em HP/BP/AP | 400 | o lado +5% venceu 315 (78,75%) |
| +5% em HP/BP/AP com os lados trocados | 400 | o lado +5% venceu 307 (76,75%) |

Conclusão: não há evidência de um viés universal de quase 100% quando cada início recebe uma seed nova e os perfis são idênticos. A sensação relatada é compatível com a combinação de **seed repetida na revanche**, vantagem efetiva multiplicativa, kit universal e controle cumulativo.

### 1.2 Dados de HP/BP/AP disponíveis

O catálogo empacotado `MonoBehaviour_CharacterData.json` possui 1.410 registros:

| Fase da fonte | Registros | Trios HP/AP/BP distintos | Observação |
|---:|---:|---:|---|
| 1 | 82 | 0 | HP/AP/BP são zero na fonte |
| 2 | 82 | 0 | HP/AP/BP são zero na fonte |
| 3 | 141 | 60 | há variação real, mas várias espécies compartilham o mesmo trio |
| 4 | 337 | 109 | há variação real |
| 5 | 422 | 155 | há variação real |
| 6 | 346 | 158 | há variação real |

Portanto, não é correto prometer stats de espécie diferentes para todas as formas. Nas duas primeiras fases a fonte não fornece diferença numérica; nas demais, valores repetidos também são dados legítimos. A individualidade faltante deve vir da personalidade, não de bônus inventados.

---

## 2. Objetivos e limites

### 2.1 Objetivos

- Fazer HP, BP e AP originais influenciarem a batalha sem transformar diferenças pequenas em resultados quase determinados.
- Fazer a personalidade persistida do indivíduo produzir um comportamento reconhecível em 1×1 e em equipe.
- Preservar a escolha estratégica do treinador: personalidade é a tendência; `BattleStrategy` é a ordem tática atual.
- Eliminar viés de lado, de ordem de ID e de consumo compartilhado de RNG.
- Manter a simulação reprodutível quando a mesma seed é solicitada, mas gerar seed nova numa revanche comum.
- Medir balanceamento por matrizes de centenas/milhares de batalhas, não por sensação ou uma seed isolada.

### 2.2 Não objetivos do primeiro rollout

- Copiar literalmente as passivas de *Time Stranger* para um combate em tempo real.
- Dar bônus secretos para quem está perdendo (`rubber band`).
- Forçar qualquer par escolhido manualmente a ter 50% de chance.
- Criar novos stats persistidos, classes, equipamentos ou progressão.
- Inventar HP/BP/AP de espécie para fases cuja fonte possui zero.
- Rerrolar as personalidades já persistidas.

---

## 3. O que aproveitar de Digimon Story: Time Stranger

A fonte oficial da Bandai confirma que personalidade altera crescimento de stats e caminho evolutivo. Guias baseados no jogo documentam 16 personalidades distribuídas em quatro núcleos, cada um associado a um stat principal e a habilidades próprias.

O VBHelper já usa exatamente os 16 nomes em quatro blocos canônicos:

| Núcleo | Números | Personalidades |
|---|---:|---|
| Philanthropy | 1–4 | Adoring, Devoted, Tolerant, Overprotective |
| Valor | 5–8 | Zealous, Brave, Reckless, Daring |
| Wisdom | 9–12 | Enlightened, Sly, Astute, Strategic |
| Amicability | 13–16 | Opportunistic, Friendly, Sociable, Compassionate |

Não foi encontrada fonte oficial que prove que essas personalidades comandam uma IA autônoma em *Time Stranger*. Aquele jogo é por turnos; transformar os quatro núcleos e 16 subtipos em pesos de IA de tempo real é uma **adaptação original do VBHelper**.

Consequências de design:

- `PersonalityCore` pode ser derivado de `DigimonPersonalityType.number`; não exige coluna nem migração Room.
- O `personalityType` persistido continua sendo a única identidade de personalidade.
- Não aumentar `CURRENT_PERSONALITY_SYSTEM_VERSION` apenas para integrar batalha. Isso rerrolaria indivíduos sem necessidade.
- Uma personalidade ausente deve ser criada e persistida uma vez antes da preparação da batalha, nunca dentro do tick do simulador.

---

## 4. Contrato de dados da batalha

O fluxo recomendado é:

```text
UserCharacter ──individualId──> DigimonPersonalityTraits
      │
      ├── CardCharacter: baseHp/baseBp/baseAp
      ├── BECharacterData: trainingHp/trainingBp/trainingAp
      └── UserCharacter: mood/vitalPoints
                         │
                         ▼
               BattleParticipantProfile
                         │
                         ▼
                TrainingParticipantInput
                         │
                         ▼
                 CombatantDefinition
```

Criar uma projeção própria, por exemplo `BattleParticipantProfile`, para evitar consultas N+1 e não depender de `CharacterWithSprites`, que hoje omite `individualId`.

Campos mínimos do perfil:

```text
sourceCharacterId
individualId
stableRngKey
externalCharacterId
displayName
stage
attribute
personalityType
baseHp/baseBp/baseAp
trainingHp/trainingBp/trainingAp
vitalPoints/mood
statSourceScale
```

`stableRngKey` deve ser o `individualId` para Digimons persistidos. Para um oponente empacotado sem identidade individual, usar uma chave sintética versionada, como `asset:<characterId>:<profileVersion>`.

### 4.1 Oponentes empacotados

Os quatro oponentes atuais recebem números no construtor, mas entram sem `vitalStats`; esses números não chegam à definição final. O novo fluxo deve ler HP/AP/BP do registro correspondente no `MonoBehaviour_CharacterData.json` por `charaId`.

Não tratar silenciosamente essa fonte como DIM ou BEM. Criar escalas explícitas:

```text
CARD_DIM
CARD_BEM
ARENA_EXTRACTED
STAGE_FALLBACK
```

O parser de `AttackSpriteManager` já lê o mesmo arquivo. Stats e nomes de VFX devem compartilhar um catálogo cacheado, em vez de analisar o JSON inteiro em componentes separados.

---

## 5. Pipeline de HP/BP/AP v3

### 5.1 Validade por eixo

Substituir o teste tudo-ou-nada por resolução independente:

```text
resolvedHp = baseHp válido ? baseHp : medianaHpDaFonteEFase
resolvedBp = baseBp válido ? baseBp : medianaBpDaFonteEFase
resolvedAp = baseAp válido ? baseAp : medianaApDaFonteEFase
```

- `65535`, valor negativo e valor não finito são desconhecidos.
- Zero em fase 1/2 não vira um stat artificial; usa o baseline documentado do estágio.
- Um BP ausente não pode apagar HP e AP válidos.
- O snapshot/debug deve registrar qual eixo usou fallback.

### 5.2 Conversão por fonte

- **CARD_DIM:** manter uma conversão DIM→escala canônica versionada, validada por fase.
- **CARD_BEM:** base + treino do eixo correspondente, com o limite real do dispositivo.
- **ARENA_EXTRACTED:** usar os números do catálogo de arena com uma tabela própria; não passar pelo conversor DIM.
- **STAGE_FALLBACK:** baseline somente quando a fonte não oferece aquele eixo.

As tabelas de referência devem ser congeladas num asset versionado do `BattleRuleset`. Não calcular medianas a partir apenas dos cartões instalados pelo usuário, porque instalar/remover um cartão não pode mudar retrospectivamente a força de todo o elenco.

### 5.3 Compressão sem achatamento

O clamp atual de `0.65..1.75` é muito largo quando HP, AP e BP variam juntos. Usar compressão monotônica em log, calibrada por simulação:

```text
rawRatio_i        = rawStat_i / referenceMedian_i
winsorizedRatio_i = clamp(rawRatio_i, referenceP05_i, referenceP95_i)
battleRatio_i     = exp(lambda_i * ln(winsorizedRatio_i))
battleStat_i      = stageBaseline_i * battleRatio_i
```

Propriedades:

- mantém a ordem das espécies;
- mantém o formato “tanque / frágil / ofensivo”;
- reduz a multiplicação de três vantagens pequenas;
- permite calibrar HP, BP e AP separadamente;
- não cria pontos de corte bruscos.

`lambda_i` não deve ser escolhido por palpite. A primeira calibração implementada usa `0.45`, depois de `0.55` produzir 71,5% de vitórias para +5% nos três eixos. Com `0.45`, a mesma amostra pareada de 200 lutas caiu para 68%, dentro do gate inicial de 70%. O valor pertence ao `BattleRuleset` e ainda deve ser ampliado para a matriz completa de estágios e fontes.

### 5.4 Condição do relógio

`trainingHp/Bp/Ap`, `vitalPoints` e `mood` continuam válidos porque já existem no app, mas precisam ser:

- aplicados depois da conversão base;
- limitados por orçamento explícito;
- exibidos na preparação;
- incluídos no Battle Rating;
- testados para não quebrar o pareamento.

Recomendação inicial: treino continua específico ao eixo; mood/vitals juntos não devem deslocar o Battle Rating em mais de uma banda de pareamento.

### 5.5 Dinâmica de batalha derivada dos três stats

O relógio não fornece MP, Speed ou Brains como em *Re:Digitize Decode*. Portanto, o VBHelper deriva os atributos secundários somente de HP/BP/AP normalizados, sem fingir que energia é um quarto valor extraído:

```text
energyRatio = HP^0.25 × BP^0.40 × AP^0.35
regenRatio  = HP^-0.25 × BP^0.45 × AP^0.30
tempo       = normalize(0.55 ln(AP) + 0.25 ln(BP) - 0.65 ln(HP))
```

- HP aumenta a capacidade de energia, mas massa alta reduz regeneração, movimento e ritmo de técnicas.
- BP sustenta energia, regeneração e estabilidade de ritmo.
- AP aumenta capacidade e favorece mobilidade/recarga, além do ataque primário.
- movimento e cooldown usam bandas não sobrepostas por estágio; o Digimon mais lento de um estágio superior ainda fica acima do mais rápido do estágio anterior.
- o mesmo tempo derivado ajusta a janela de decisão autônoma; personalidade continuará sendo uma camada separada.
- os multiplicadores são limitados e versionados para que a especialização de papel não vire aumento bruto ilimitado.

A inspiração em Decode é estrutural: HP/MP/Speed separados, combate em tempo real e peso alto sacrificando movimento/velocidade de ataque. As fórmulas acima são regras autorais do VBHelper, não fórmulas históricas atribuídas ao jogo.

### 5.6 Battle Rating

Adicionar um índice apenas para comparação, telemetria e aviso de matchup:

```text
effectiveHp  = health * (100 + defense) / 100
expectedDps  = attack * canonicalKitFactor
battleRating = sqrt(effectiveHp * expectedDps)
```

- A fórmula deve usar exatamente a mesma curva de defesa do ruleset.
- Atributo Data/Vaccine/Virus entra no **rating do confronto**, não no rating intrínseco.
- Personalidade não entra como bônus bruto no primeiro rollout; seus perfis são calibrados para expectativa semelhante.
- O rating não modifica stats durante a luta.

---

## 6. Personalidade como IA persistente

Não converter personalidade diretamente para `BattleStrategy`. Manter duas camadas:

```text
PersonalityBattleProfile = tendência permanente do indivíduo
BattleStrategy           = intenção atual do treinador
```

Uma ordem `CHANGE_STRATEGY(DEFENSIVE)` deve tornar qualquer Digimon mais defensivo, mas um Reckless e um Strategic ainda interpretam essa ordem de formas diferentes dentro de limites seguros.

### 6.1 Dimensões derivadas

`PersonalityBattleProfile` pode ser imutável e calculado do enum:

```text
core
offenseWeight
safetyWeight
efficiencyWeight
teamWeight
preferredRangeBias
energyReserve
guardReadBias
targetStickiness
lowHpRiskThreshold
actionTemperature
openingBias
```

Nenhum campo precisa ser persistido. A versão do mapeamento pertence ao `BattleRuleset`.

### 6.2 Assinatura das 16 personalidades

| Personalidade | Núcleo | Assinatura autônoma proposta |
|---|---|---|
| Adoring | Philanthropy | protege o aliado mais ameaçado; em 1×1 prefere troca segura e guarda |
| Devoted | Philanthropy | alta persistência de alvo e de ordem; evita abandonar uma execução viável |
| Tolerant | Philanthropy | paciente; espera uma janela limpa e reduz trocas simultâneas arriscadas |
| Overprotective | Philanthropy | reação defensiva mais rápida a startup inimigo e ameaça ao aliado |
| Zealous | Valor | pressão de abertura e preferência por converter energia em iniciativa |
| Brave | Valor | mantém pressão com HP baixo sem aumentar dano bruto |
| Reckless | Valor | aceita risco, reserva pouca energia e usa maior temperatura de escolha |
| Daring | Valor | busca burst quando o payoff é alto; recua se a janela não compensa |
| Enlightened | Wisdom | baixa aleatoriedade, eficiência e espera por alcance/cooldown favorável |
| Sly | Wisdom | reposiciona, varia distância e troca alvo quando há abertura real |
| Astute | Wisdom | lê startup/recovery, pune cooldown e evita atacar numa defesa evidente |
| Strategic | Wisdom | planeja sequência, guarda recurso e mantém distância estável |
| Opportunistic | Amicability | começo contido; acelera contra alvo vulnerável ou fora de posição |
| Friendly | Amicability | acompanha o alvo do aliado; em 1×1 usa perfil equilibrado cooperativo |
| Sociable | Amicability | prioriza follow-up, controle e efeitos aplicados pelo time |
| Compassionate | Amicability | sustenta/protege o aliado mais frágil; em 1×1 prioriza sobrevivência |

Cada assinatura precisa produzir efeito também com o kit atual. Heals, buffs e múltiplos alvos podem enriquecer Philanthropy/Amicability depois, mas não podem ser pré-requisito para perceber a personalidade em 1×1.

### 6.3 Escolha de ação

Substituir o ruído uniforme atual `U(0,72)`, que pode superar diferenças táticas, por seleção estocástica controlada:

```text
score = situação + técnica + personalidade + estratégia + risco
ação  = softmax(scores / temperature)
```

- Técnicas claramente inválidas nunca entram no sorteio.
- A personalidade altera pesos e `temperature`, não dano.
- A estratégia do treinador aplica um delta temporário maior que o viés de personalidade.
- O log de debug registra os componentes do score, não apenas o total.

### 6.3.1 Estado implementado nesta continuação

- `PersonalityBattleProfile.kt` deriva os quatro núcleos pela numeração existente e mapeia os 16 subtipos; nenhuma coluna ou migração é necessária.
- A tendência fica limitada a prioridades (delta próprio até ±12 pontos), temperatura, guarda reativa, distância, reserva de energia, coordenação de alvo, aproveitamento de aberturas e pequeno ajuste da primeira leitura. A estratégia do treinador tem deltas maiores nos contextos correspondentes; nenhum dos dois altera dano ou atributos de combate.
- A escolha usa softmax reproduzível; streams de timing, posicionamento, reação, escolha e dano são derivados de `randomSeed + stableRngKey + streamTag` por hash estável.
- O diagnóstico da batalha expõe personalidade/núcleo e a decomposição técnica, posição, repetição, suporte, personalidade, estratégia e risco de cada técnica.
- Ainda falta medir a matriz de personalidades e side swaps. Os valores são uma primeira calibração autoral, não um gate quantitativo aprovado.

### 6.4 Passivas

As skills de personalidade de *Time Stranger* são boa inspiração, mas ficam para uma segunda etapa opcional. Counter, revive, roubo de vida, “age primeiro/último” e bônus de crítico têm valores de poder muito diferentes num motor em tempo real.

Se forem adicionadas:

- uma passiva por indivíduo;
- seleção persistida ou determinística, nunca rerrolada por batalha;
- orçamento de poder medido por simulação;
- expectativa de vitória de cada passiva dentro de ±3 pontos percentuais do grupo;
- Battle Rating inclui o valor esperado da passiva.

---

## 7. Simetria e aleatoriedade

### 7.1 Seed de sessão e revanche

- Nova batalha: seed nova.
- Revanche comum: seed nova.
- “Reproduzir seed” existe apenas em ferramenta/debug.
- Snapshot de diagnóstico guarda seed + versão do ruleset.

Isso mantém depuração determinística sem transformar “Revanche” em replay invisível.

### 7.2 Streams independentes

O único `Random` compartilhado deve ser substituído por streams derivadas de:

```text
battleSeed + stableRngKey + streamTag
```

Tags mínimas:

```text
decisionTiming
actionChoice
movementChoice
damageVariance
critical
```

Usar hash estável definido pelo projeto, não depender de `String.hashCode` ou da ordem de iteração. Trocar um indivíduo de lado não pode trocar sua sequência aleatória.

### 7.3 Decisão em duas fases

Hoje os atores são processados em ordem de `combatantId`; o segundo pode observar uma ação iniciada pelo primeiro no mesmo tick. Corrigir para:

1. capturar estado de leitura no começo do tick de decisão;
2. calcular intenções de todos os atores elegíveis;
3. ordenar por timestamp lógico;
4. confirmar as intenções juntas;
5. resolver impactos simultâneos como o motor já faz.

Isso remove vantagens de prefixo `ally:`/`opponent:` e preserva reação apenas a sinais que já existiam no estado anterior.

### 7.4 Stagger e knockback

O kit atual permite que stagger/knockback mantenham vantagem acumulativa. Regras propostas:

- stagger usa medidor/poise e decai, em vez de comparar cada golpe isoladamente com `defense * 0.25`;
- após stagger, pequena imunidade evita cadeia infinita;
- knockback físico não cancela automaticamente uma fase não-interrompível;
- guarda aumenta resistência a stagger;
- interrupção, tempo incapacitado e cadeias de controle entram na telemetria.

Não adicionar chance secreta de “escapar” quando estiver perdendo.

---

## 8. UX de preparação

Exibir em cada participante:

- personalidade e núcleo;
- HP/AP/BP finais e origem/fallback;
- Battle Rating;
- atributo;
- modificadores de treino/mood/vitals;
- rótulo do confronto: `EQUILIBRADO`, `VANTAGEM LEVE`, `VANTAGEM ALTA` ou `DESAFIO`.

O usuário continua livre para iniciar um confronto desigual. O app deve explicar a diferença, não corrigi-la escondido.

Formatos:

- 1×1 e 2×2 simétricos recebem gates de balanceamento.
- 1×2 é marcado como desafio enquanto não existir orçamento de equipe; não entra em métricas de justiça 1×1.
- Em 2×2, comparar a soma/combinação do rating de equipe e testar foco coordenado.

---

## 9. Observabilidade e runner headless

Antes de ajustar números, criar um runner sem UI que exporte por batalha:

```text
rulesetVersion, seed, formato, duração, vencedor
rawStats, resolvedStats, fallbackPorEixo, battleStats, battleRating
personalityType, core, strategy
contagem por técnica, guarda, troca de alvo, distância média
energia média ao agir, dano, crítico, stagger, interrupção
primeiro atacante e tempo incapacitado
```

O relatório deve agregar por:

- lado;
- espécie/perfil de stats;
- personalidade;
- núcleo;
- atributo;
- faixa de Battle Rating;
- seed e side swap.

Não persistir essa telemetria como histórico de usuário no primeiro rollout; gerar em testes/diagnóstico de desenvolvimento.

---

## 10. Gates de aceitação

| Gate | Amostra | Critério inicial |
|---|---:|---|
| Espelho perfeito | ≥1.000 seeds por estágio | cada lado entre 47–53%; empate <2% |
| Side swap | ≥500 pares × 2 lados | diferença de win rate da identidade ≤3 p.p. |
| Par equivalente | rating dentro de ±3%, sem vantagem de atributo | cada combatente entre 40–60% em ≥500 seeds |
| Pool por núcleo | cada núcleo contra o pool | 45–55% |
| Pool por personalidade | cada uma das 16 contra o pool | 42–58%; nenhuma domina todos os núcleos |
| Pequena vantagem | +5% nos três stats brutos | não exceder 70% após compressão inicial; manter vantagem >50% |
| Monotonicidade | variar um eixo por vez | aumentar HP/AP/BP bruto nunca reduz o eixo de batalha correspondente |
| Fallback isolado | um eixo desconhecido | apenas aquele eixo usa mediana |
| Fases 1–2 | fontes zeradas | nenhuma diferença de espécie fabricada; personalidade ainda distinguível |
| Duração | batalhas autônomas pareadas | p50 30–70 s; p95 <120 s; timeout <1% |
| Controle | todas as lutas | incapacidade <30% da duração; sem cadeia ilimitada de stagger |
| Determinismo | mesma seed/ruleset/ordens | snapshot e resultado idênticos |
| Revanche | ação de UI | seed nova por revanche comum |
| Assinatura | histograma de ações | os quatro núcleos diferem em pelo menos duas métricas comportamentais |

Os intervalos são gates de partida e devem ser versionados. Não afrouxar um gate apenas para acomodar uma personalidade dominante; corrigir o perfil ou seu poder implícito.

---

## 11. Ordem de implementação proposta

### Fase 0 — Baseline reproduzível

- Headless runner e relatório.
- Testes de mirror, side swap e rating bands.
- Seed nova na revanche; replay da mesma seed apenas em debug.
- Registrar o baseline atual antes de mudar regras.

### Fase 1 — Dados corretos

- Projeção `BattleParticipantProfile` com `individualId` e personalidade persistida.
- Validade/fallback por eixo.
- Catálogo compartilhado para stats dos assets.
- Identificar explicitamente `CARD_DIM`, `CARD_BEM`, `ARENA_EXTRACTED` e fallback.
- Mostrar origem dos números na preparação.

### Fase 2 — Motor simétrico

- RNG por combatente e por finalidade (timing, posição, reação, escolha e dano) — implementado para a IA e dano; demais fontes/empates ainda precisam de auditoria.
- Decisão em duas fases.
- Tie-break side-neutral.
- Regras de stagger/knockback com poise e imunidade curta.

### Fase 3 — Quatro núcleos

- `PersonalityCore` derivado — implementado.
- Perfis Philanthropy, Valor, Wisdom e Amicability — implementados como base dos perfis individuais.
- `BattleStrategy` como camada separada.
- Gates 45–55% por núcleo e assinatura comportamental.

### Fase 4 — Dezesseis indivíduos

- Modificadores de subtipo, softmax/temperature e explicação de decisão — implementados; calibração estatística pendente.
- Matriz 16×16 com side swap.
- Ajuste para que cada tipo seja reconhecível sem ganhar poder bruto.

### Fase 5 — Stats e pareamento calibrados

- Referências congeladas por fonte/fase.
- Varredura de `lambdaHp/lambdaBp/lambdaAp`.
- Battle Rating e rótulo de confronto.
- Gates de pequena vantagem e TTK.

### Fase 6 — Opcional

- Passivas inspiradas em *Time Stranger*.
- Cooperação 2×2 mais rica.
- Deriva de personalidade por conversa/treino somente com decisão explícita de produto.

Cada fase deve aterrissar com seus testes. Não implementar as 16 personalidades e depois tentar descobrir se o motor tinha viés de lado.

---

## 12. Arquivos previstos

### Entrada e persistência

- `app/src/main/java/com/github/nacabaro/vbhelper/daos/UserCharacterDao.kt`
- novo DTO/projeção de participante de batalha
- `app/src/main/java/com/github/nacabaro/vbhelper/source/StorageRepository.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/BattlesScreen.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/OfflineBattleEntryPanel.kt`

### Domínio

- `app/src/main/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityTraits.kt`
- novo `battle/offline/core/PersonalityBattleProfile.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleStats.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleFactory.kt`
- novo catálogo compartilhado de dados extraídos

### Simulador e sessão

- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleModel.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleSimulator.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/TrainingBattlePresentation.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/OfflineBattleSessionViewModel.kt`
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/TrainingBattlePreparation.kt`

### Testes

- `TrainingBattleStatsTest` ou expansão de `TrainingBattleBalanceTest`
- `BattleSeedPolicyTest`
- `BattleSideSwapTest`
- `PersonalityBattleProfileTest`
- `PersonalityMirrorMatrixTest`
- `BattleControlLockTest`
- `BattleRatingTest`
- runner headless de calibração fora da suíte curta

---

## 13. Decisões recomendadas para autorização

1. **Personalidade muda IA, não HP/BP/AP no MVP.** Isso preserva os stats originais e reduz o risco de poder oculto.
2. **Fases 1–2 permanecem numericamente no baseline.** A fonte possui zeros; a personalidade fornece individualidade comportamental.
3. **Revanche recebe seed nova.** Replay idêntico fica restrito ao modo de diagnóstico.
4. **Battle Rating informa, não corrige.** Sem buff dinâmico ou nerf secreto.
5. **Passivas ficam depois dos 16 perfis passarem nos gates.**
6. **1×2 é desafio, não referência de balanceamento competitivo.**

---

## 14. Fontes

### Código e dados locais

- `DigimonWorldBattleEspecifications.txt` — autonomia, utility AI, estratégias, dano e auditoria do sistema atual.
- `DigimonPersonalityTraits.kt` — 16 personalidades persistidas e numeração 1–16.
- `DigimonPersonalityGenerator.kt` — geração atual ponderada por atributo/estágio.
- `TrainingBattleStats.kt` — conversão atual HP/BP/AP e fallback.
- `TrainingBattleFactory.kt` — criação do perfil e estratégia `BALANCED` padrão.
- `BattleSimulator.kt` — RNG compartilhado, utility score, dano, stagger e ordem dos atores.
- `OfflineBattleSessionViewModel.kt` — política atual de seed/revanche.
- `MonoBehaviour_CharacterData.json` — 1.410 registros de stats/asset.

### Pesquisa externa

- Bandai Namco, [Digimon Story Time Stranger — What You Need to Know](https://www.bandainamcoent.com/news/digimon-story-time-stranger-what-you-need-to-know) — confirmação oficial de que personalidade altera crescimento e evolução.
- GameFAQs, [Personality Guide](https://gamefaqs.gamespot.com/ps5/513530-digimon-story-time-stranger/faqs/82355/personality-guide) — quatro núcleos, 16 tipos e stats priorizados.
- Digimon Evolution Tree, [Personalities](https://digimon.jsuk.cloud/en/guide/personalities/) — tabela de núcleo, stat secundário, equipamento e skills; fonte comunitária baseada nos dados do jogo.
- PCGamesN, [Personality chart](https://www.pcgamesn.com/digimon-story-time-stranger/personality) — números e descrições comunitárias usados apenas como inspiração, não como regra canônica do VBHelper.

---

## 15. Resumo em uma frase

O HP/BP/AP original determina **o que o Digimon consegue fazer**; a personalidade persistida determina **como ele tenta vencer**; seed, scheduler e gates estatísticos garantem que essa individualidade não vire vantagem invisível ou revanche predeterminada.
