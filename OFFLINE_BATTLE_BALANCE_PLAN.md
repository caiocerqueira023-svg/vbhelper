# Plano de balanceamento da batalha offline — v2 (personalidades + HP/BP/AP)

> **Status:** proposta de design, ainda não implementada.
> **Escopo:** batalha offline (`app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/**`).
> **Restrição central:** usar apenas o que já existe no app — as 16 personalidades já persistidas por indivíduo e o trio HP / BP / AP lido do cartão e do relógio. Nenhum stat, atributo ou progressão nova.
> **Data:** 25/09/2026.

---

## 1. Resumo executivo

Hoje o problema tem duas causas separadas:

1. **Pouca variedade intraestágio.** O poder efetivo é quase uma função do estágio. Os Digimons do mesmo estágio chegam à arena com números quase iguais e usam exatamente o mesmo kit de quatro técnicas.
2. **Pouca variedade individual.** A personalidade é gerada e persistida por indivíduo, mas nunca chega ao simulador. Dentro da batalha, todo mundo se comporta como `BALANCED` com o mesmo ruído aleatório.

A proposta v2 inverte a ênfase: **o trio HP/BP/AP do relógio define o poder; a personalidade define como esse poder é interpretado.** O equilíbrio passa a vir do pareamento e da simetria do motor, e não de achatar os números de todas as espécies até ficarem iguais.

Três decisões de design:

- As 16 personalidades do app são as mesmas 16 de **Digimon World Time Stranger**, na mesma ordem. Os quatro quadrantes podem ser derivados do campo `number` que já existe, sem migração de banco.
- A personalidade **redistribui** o trio HP/BP/AP e nunca o aumenta. A mesma ficha com Adoring e com Daring tem a mesma força e estilos diferentes.
- A battle rating compara **somente** o trio puro. Personalidade nunca vira nerf, banimento de matchmaking ou dano secreto.

---

## 2. Achado principal: o app já tem a taxonomia de Time Stranger

O enum `DigimonPersonalityType` guarda um `number` canônico de 1 a 16. A ordem divide-se naturalmente em quatro blocos de quatro, exatamente como em Time Stranger:

| Núcleo | Faixa | Subtipos do app |
|---|---|---|
| Philanthropy (博愛) | 1–4 | Adoring, Devoted, Tolerant, Overprotective |
| Valor (勇壮) | 5–8 | Zealous, Brave, Reckless, Daring |
| Wisdom (知啓) | 9–12 | Enlightened, Sly, Astute, Strategic |
| Amicability (友好) | 13–16 | Opportunistic, Friendly, Sociable, Compassionate |

Isso significa:

- `core = (number - 1) / 4` resolve o quadrante sem coluna nova, sem migração e sem tocar no prompt.
- O esquema Room atual já é suficiente: `individualId`, `personalityType`, `generatedAt`, `systemVersion`.
- A taxonomia dos quatro núcleos e das 16 personalidade é consistente em várias fontes de pesquisa; os **números exatos** de bônus e as passivas são de guia comunitária e valem como direção de design, não como verdade canônica.

O modelo antigo `temperament` / `socialStyle` / `speechQuirk` foi removido pela migração 19→20 e não deve voltar.

---

## 3. Princípios da versão 2

1. **Personalidade redistribui, não adiciona.** Deslocar o poder entre HP, BP e AP; nunca criar poder novo.
2. **Os stats vêm do relógio e do cartão.** `baseHp` / `baseBp` / `baseAp` da linha do cartão, `trainingHp` / `trainingBp` / `trainingAp` do Vital Bracer em cartões BE, `mood` e `vitalPoints` do relógio.
3. **SP e SPD viram comportamento.** Time Stranger usa sete stats; o relógio dá três. SP vira eficiência de recurso e custo de técnica; SPD vira iniciativa e latência de decisão. Assim as 16 personalidades mantêm sentido sem inventar coluna.
4. **Equilíbrio vem do pareamento, não do achatamento.** Stats mais expressivos, matchmaking por índice e motor simétrico. Nenhum buff oculto em runtime.

---

## 4. O que já existe e onde está falhando

| Eixo | O que já existe | Onde está falhando | Referência |
|---|---|---|---|
| Personalidade | 16 tipos persistidos por indivíduo e injetados no prompt | Nunca chega a `TrainingParticipantInput`, `CombatantDefinition` ou `BattleSimulator` | `domain/personality/DigimonPersonalityTraits.kt:18-206`, `screens/offlineBattle/TrainingBattlePresentation.kt:126-140` |
| Quadrantes | A ordem 1–16 já forma quatro blocos de quatro | Nenhum quadrante, eixo ou bucket existe no código ou no schema | Derivável de `number`; nenhuma coluna nova necessária |
| HP / BP / AP | `baseHp/baseBp/baseAp` do cartão + `trainingHp/Bp/Ap` do Vital Bracer | Estágios 0 e 1 ignoram o trio; `baseBp` igual a zero descarta o perfil; DIM ignora treino | `battle/offline/data/TrainingBattleStats.kt:97-121`, `screens/OfflineBattleEntryPanel.kt:60-70` |
| Normalização | Ratio por eixo contra a média da fase, com clamp 0.65–1.75 | Comprime a variação real e distorce o resultado porque cada eixo é limitado isoladamente | `TrainingBattleStats.kt:38-39,155-158` |
| Participantes de asset | `MonoBehaviour_CharacterData.json` tem 1410 registros com HP/AP/BP por Digimon | Entra no fluxo com HP/AP fixos e **sem** `vitalStats`, caindo no baseline do estágio | `screens/BattlesScreen.kt:2028-2036` |
| Gerador | Pondera por atributo e estágio do cartão | Usa `Random.Default`; a mesma falha de importação pode gerar personalidades diferentes | `domain/personality/DigimonPersonalityGenerator.kt:54-78` |
| Fórmulas de combate | `TrainingBattleStats.forParticipant` recebe o trio e já mapeia HP→vida, AP→ataque, BP→defesa | A personalidade não participa e a curva de defesa dá pouco peso ao BP | `TrainingBattleStats.kt:101-120` |

### 4.1 Observação sobre a origem dos três stats

Para evitar uma premissa errada:

- O trio **base** (HP, BP, AP) vem da linha da forma no **cartão** DiM/BEM, lido por `CardImportController` e armazenado em `CardCharacter`.
- O **treino** (`trainingHp`, `trainingBp`, `trainingAp`) vem do **relógio** Vital Bracer e só existe em cartões BE.
- `mood` e `vitalPoints` vêm do relógio em qualquer dispositivo.
- O campo `baseBp` é, no fundo, o campo `dp` do cartão renomeado — vale registrar isso para não confundir BP de combate com BP de VitalWear.

---

## 5. Os quatro quadrantes em termos de relógio

Em Time Stranger, Philanthropy cresce SPI, Valor cresce ATK, Wisdom cresce INT e Amicability cresce DEF. O relógio não tem SPI, INT nem SPD. A adaptação proposta é:

| Núcleo | Eixo numérico | Identidade | Vies de IA |
|---|---|---|---|
| Philanthropy | BP (defesa e resistência) | Sustain, proteção e cura | Mantém o alvo, defende o aliado ferido, segura a técnica para punir o startup |
| Valor | AP (ataque e potência de técnica) | Pressão e finalização | Abre o combate, avança com HP baixo, aceita risco para fechar o alvo |
| Wisdom | AP com eficiência de recurso | Leitura, timing e punição | Espera o startup, lê cooldown, administra energia, fecha no momento certo |
| Amicability | BP (mitigação e resistência a interrupção) | Controle, defesa e sustain de equipe | Mantém posição, prolonga debuff, troca de alvo quando a troca é melhor |

Os dois eixos defensivos de Time Stranger (DEF e SPI) colapsam em BP; os dois ofensivos (ATK e INT) colapsam em AP. Quando o secundário de uma subclasse cai no mesmo eixo do primário, o secundário numérico passa para o terceiro eixo, preservando o sabor do tipo.

---

## 6. As 16 personalidades como contrato de batalha

As porcentagens abaixo são a **direção do desvio** em HP, BP e AP. Elas não são bônus de poder: a implementação resolve um escalar para manter o índice de combate constante (seção 7).

| # | Personalidade | Núcleo | Desvio HP/BP/AP | Leitura de IA | Passivas candidatas |
|---|---|---|---|---|---|
| 1 | Adoring | Philanthropy | +2 / +5 / −7 | Prioriza aliado ferido; evita pressa; age por último | Follow Up, Prayer for Aid |
| 2 | Devoted | Philanthropy | −7 / +4 / +3 | Não abandona o alvo; cumpre a ordem sob pressão | First Aid, Stout Spirit |
| 3 | Tolerant | Philanthropy | −2 / +5 / −3 | Espera o erro do oponente; defende em janela tardia | Steadfast Heart, Rally Blessing |
| 4 | Overprotective | Philanthropy | +5 / +3 / −8 | Primeiro a reagir a ameaça; protege o aliado mais frágil | First in Line, Great Embrace |
| 5 | Zealous | Valor | −2 / −3 / +5 | Abre com pressão; busca decisão rápida | Fast Break, First in Line |
| 6 | Brave | Valor | −5 / 0 / +5 | Avança mesmo machucado; não recua por medo | Vitality Theft, Extra Strikes |
| 7 | Reckless | Valor | +4 / −8 / +4 | Aceita risco; contra-ataca; ignora limiar de HP | Counter, Steadfast Might |
| 8 | Daring | Valor | −8 / +4 / +4 | Risco calculado; troca de plano no meio da luta | Stout Defense, Strategic Order |
| 9 | Enlightened | Wisdom | −6 / +2 / +4 | Lê o início do golpe; não entra em janela perdida | Intense Focus, Haymaker |
| 10 | Sly | Wisdom | +4 / −7 / +3 | Evita a linha de frente; espera; finta com retarget | Meditation, Combo |
| 11 | Astute | Wisdom | −2 / −2 / +4 | Pune cooldown e stagger; melhor leitura de padrão | Steadfast Emotion, Magic Theft |
| 12 | Strategic | Wisdom | −6 / +3 / +3 | Age por último; maximiza eficiência de energia | Follow Up, Soothing Song |
| 13 | Opportunistic | Amicability | −7 / +4 / +3 | Troca de alvo quando a troca é melhor; slow starter | Slow Starter, Planning Ahead |
| 14 | Friendly | Amicability | −7 / +4 / +3 | Mantém o alvo; coopera; ganho de crítico | Strategic Order, Fortifying Charge |
| 15 | Sociable | Amicability | −4 / +4 / 0 | Foca no inimigo que o aliado tamborilou | Weak Point Blitz, Booing |
| 16 | Compassionate | Amicability | +2 / +5 / −7 | Sustenta, prolonga efeitos e protege | Stout Strength, Hustle Cry |

---

## 7. CombatIndex e a afinidade zero-sum

### 7.1 O índice já está implícito na fórmula de dano

A fórmula de dano do simulador é, na sua essência:

```
damage ≈ techniquePower × AP / 100 × attributeMultiplier × guard × 100 / (100 + DEF)
```

Logo, o tempo para zerar a vida do outro é proporcional a:

```
TTK ≈ HP × (100 + DEF) / AP_do_atacante
```

Comparando dois combatentes com o mesmo kit, quem vence é quem tem o **maior** valor de:

```
CombatIndex = HP × (100 + k × BP) × AP
```

O termo `k` é apenas um coeficiente do ruleset que controla o quanto o BP pesa. Hoje `k = 1` e o BP quase não influencia: com BP prático entre 38 e 101, o fator `(100 + BP)` varia cerca de 1,46×, contra 2,7× de HP e de AP. Sugestão inicial: testar `k` entre 1,5 e 2,5 para o BP responder por 30–40% da variação do índice, e confirmar por simulação.

O mesmo índice é a resposta para a segunda metade do problema: o teste existente `TrainingBattleBalanceTest` só verifica que as 72 sessões terminam, duram menos de 150 s e causaram dano nos dois lados. Ele nunca verifica **quem** venceu, então uma distorção de 100% passa sem falhar.

### 7.2 A personalidade não altera o índice

Para cada personalidade, define-se a direção desejada `d = (dHP, dBP, dAP)` a partir da tabela da seção 6 e procura-se o menor escalar `t ≥ 0` tal que:

```
CombatIndex(stats × (1 + t·d)) = CombatIndex(stats)
```

É uma equação, uma incógnita. Nenhuma tabela manual de centenas de números por estágio é necessária.

Efeito prático: Daring ganha AP e BP e paga em HP; Reckless ganha HP e AP e paga em BP; Compassionate ganha HP e BP e paga em AP. A força total é a mesma; o estilo muda.

O índice é um **prior**, não uma verdade. Crítico, atributo, guarda, alcance e o próprio poder do kit entram depois, medidos por simulação.

---

## 8. Pipeline de stats v2

| # | Passo | Regra | Por que |
|---|---|---|---|
| 1 | Fonte de verdade | `baseHp` / `baseBp` / `baseAp` do cartão; `trainingHp` / `trainingBp` / `trainingAp` do Vital Bracer em BE; `mood` e `vitalPoints` do relógio | Uma única origem de verdade, já presente no app |
| 2 | Referência por estágio | Calcular mediana e percentis p10/p50/p90 de HP, BP e AP por escala e por estágio usando cartões reais e os 1410 registros de `MonoBehaviour_CharacterData.json` | Substitui a clamp 0.65–1.75 por uma distribuição observada |
| 3 | Conversão | Converter cada stat bruto em percentil `p` dentro do estágio e esticar: `ratio = 0.80 + 0.40 × p`, com teto de outlier em 0.78 e 1.25 | Amplia a variação real sem inventar poder; calibrar até 90% da fase cair entre 0.85 e 1.18 |
| 4 | Treino | Somar o treino apenas no eixo correspondente, limitado a 8% por eixo e 12% no total | O treino passa a ter efeito visível e ainda gera candidatos pareáveis |
| 5 | Personalidade | Aplicar o desvio da personalidade e resolver `t` para manter o `CombatIndex` | Redistribui, não adiciona |
| 6 | Estado do relógio | `mood` e `vitalPoints` como modificador pequeno, com teto total de ±4%, exibido na tela de preparação | Mantém o vínculo com o relógio sem virar sorteio de poder |
| 7 | Recurso e iniciativa | Energia continua derivada do estágio; a personalidade altera custo de técnica em até ±8% e latência de decisão em até ±15% | Traduz SP e SPD de Time Stranger sem criar stat |

### 8.1 Correções que destravam a variação

São duas, e ambas são problemas de modelagem, não pontos de equilíbrio:

1. **Estágios 0 e 1 ignoram o trio inteiro.** `TrainingBattleStats.forParticipant` retorna o baseline quando `stage < 2`, então Baby I e Baby II são idênticos por construção.
2. **Participantes de asset não têm `vitalStats`.** `BattlesScreen` monta oponentes com HP/AP fixos e sem perfil de relógio, então eles entram no baseline do estágio.

Só corrigir esses dois pontos já devolve diferença entre Digimons do mesmo estágio. O terceiro ajuste é **não descartar o perfil quando `baseBp == 0`**: usar a mediana da fase no lugar do zero, em vez de mandar o indivíduo inteiro para o fallback.

---

## 9. Gerador de personalidade v2

O gerador atual já pondera por atributo e estágio do cartão. A v2 acrescenta o prior da forma de stats e remove o `Random.Default`:

```
weight = 1 + 2 × shapeMatch + 1 × attributeMatch + 1 × stageMatch
sorteio = seed derivada de individualId
```

`shapeMatch` olha qual eixo do relógio é mais alto: BP alto sugere Amicability, AP alto sugere Valor, HP alto sugere Philanthropy. É um **prior**, não um destino — o sorteio continua circulando dentro dos 16 tipos.

Consequência de persistência: como o sorteio passa a ser determinístico por `individualId`, subir `CURRENT_PERSONALITY_SYSTEM_VERSION` de 3 para 4 e regenerar uma única vez. Os chats já gravados continuam referenciando o `personalityType` salvo, então o histórico não quebra.

### 9.1 Propriedades computadas no enum

Adicionar ao enum, sem coluna e sem migração:

- `core: PersonalityCore` — derivado de `number`.
- `primaryAxis` e `secondaryAxis` — `HP`, `BP`, `AP` ou `NONE` (quando o eixo de Time Stranger virou comportamento).
- `aiBias: AiBias` — agressividade, risco, distância, leitura, paciência, alvo.
- `passiveCandidates: List<PersonalityPassive>` — duas do núcleo e duas da subclasse.

---

## 10. Passivas de personalidade

Cada Digimon tem **uma** passiva ativa, sorteada entre quatro candidatas: duas do núcleo e duas da subclasse, como em Time Stranger.

Tradução para o tempo real:

| Padrão de Time Stranger | Tradução no simulador |
|---|---|
| Always acts first / last | Deslocamento do `decisionDelay` dentro da faixa, sem virar iniciativa absoluta |
| Boost ATK/DEF/INT at low HP | Multiplicador condicional pequeno, ancorado na fração de HP |
| Counter | Janela de contra-ataque habilitada por fração de HP |
| Revive once per battle | Um único revive por batalha, com limite de uso por sessão |
| Lifesteal / resource theft | Cura ou energia proporcional ao dano, com teto |
| Debuff duration +2 | Extensão de status dentro do limite da infraestrutura genérica |
| CRT +5% | +1% a +2% de crítico, para não virar stat paralelo |

Regra de balanceamento: a **expectativa de poder das quatro candidatas de um mesmo tipo** precisa ficar dentro de ±2%. A passiva muda o risco e o momento, não o teto.

---

## 11. IA, motor e pareamento

| Mudança | Regra | Efeito |
|---|---|---|
| RNG por combatente | Derivar `timing`, `utility` e `variance` de `seed + combatantId`, em vez de um `Random` compartilhado | Trocar de lado não consome a sequência do adversário |
| Ordem de resolução | Resolver por timestamp e usar hash estável da seed no empate técnico, nunca ordem lexical de ID | Elimina viés de posição e de ID |
| Personality na IA | `core` e `aiBias` entram como pesos na utility AI, no target stickiness e no limiar de risco | Dois indivíduos com os mesmos números se comportam diferente |
| Ruído de score | Reduzir o `U(0, 72)` atual, que hoje pode pesar mais que a diferença tática | A personalidade passa a importar mais que a sorte |
| Matchmaking | Mesmo estágio e `CombatIndex` dentro de ±5%; personalidade livre | Equilibra sem nerf dinâmico e sem excluir arquétipo |
| Sem rubber band | Nunca alterar HP, BP ou AP em runtime por estar perdendo | O caráter vem da decisão, não de número secreto |
| 1×2 | Ranking apenas em 1×1 e 2×2 simétricos até existir orçamento de equipe | O 100% do 1×2 é estrutural e não serve de medida |

### 11.1 Sobre o 100%

Uma reprodução local do núcleo, sem ordens, estratégia `BALANCED`, sem vitals e seeds 1–100, deu:

| Formato | Estágio 0 | Estágio 3 | Estágio 5 |
|---|---|---|---|
| 1×1 | 51 / 48 / 1 | 51 / 48 / 1 | 40 / 60 / 0 |
| 2×2 | 49 / 51 / 0 | 48 / 52 / 0 | 45 / 55 / 0 |
| 1×2 | 0 / 100 / 0 | 0 / 100 / 0 | 0 / 100 / 0 |

Ou seja: o 100% **não** é uma lei do 1×1 atual. Ele é estrutural no 1×2, onde o lado de dois simplesmente tem o dobro de HP e de ações sem orçamento de equipe. Se o par que falha é 1×1, as três causas prováveis são:

1. Todo mundo usa o mesmo kit universal, então qualquer diferença pequena vira o mesmo matchup.
2. Um único `Random` compartilhado, com consumo dependente da ordem de ID, correlaciona as escolhas.
3. Controle acumulativo: stagger, knockback e cancelamento dão vantagem a quem age primeiro.

As três são atacadas por personalidade diversificada, RNG stream por combatante e side-swap tests.

---

## 12. Acceptance gates

| Gate | Cenário | Critério inicial | Teste |
|---|---|---|---|
| Espelho de personalidade | Mesma espécie, mesmo estágio, mesmos stats brutos, só a personalidade muda | 40–60% por personalidade em 400 seeds | `PersonalityMirrorMatrixTest` |
| Quadrante no pool | Cada um dos 4 núcleos contra o pool do mesmo estágio | 45–55% de vitória | `CorePersonalityBalanceTest` |
| Distribuição de stats | Percentis de HP, BP e AP por estágio | 90% entre 0.85 e 1.18 da mediana | `StageSpreadTest` |
| Formas distintas | Razão HP:BP:AP dentro de um estágio | Nenhuma forma acima de 35% do pool; ao menos 8 formas | `StatShapeDiversityTest` |
| Viés de lado | Mesma seed, lados trocados | Diferença média ≤2 pontos | `SideSwapSymmetryTest` |
| Duração | Batalhas autônomas | p50 30–60 s, p90 <90 s, timeout <1% | `HeadlessBattleRunnerTest` |
| Passivas | Uma passiva por Digimon, quatro candidatas | Expectativa de poder dentro de ±2% entre candidatas | `PassivePowerBudgetTest` |
| Comportamento | Histograma de ações por personalidade | 16 assinaturas distinguíveis; nenhuma colapsa na média | `BehaviorSignatureTest` |
| Determinismo | Mesma seed, mesma versão, mesmas ordens | Resultado idêntico | `GoldenSeedTest` |

---

## 13. Rollout

| Fase | Pacote | Escopo | Gate |
|---|---|---|---|
| 1 | Observabilidade | Telemetria por combatente, `PersonalityCore` derivado do `number`, `HeadlessBattleRunner` | Medir antes de mudar |
| 2 | Stats v2 | Percentis por estágio, estágios 0 e 1 sem bypass, `baseBp == 0` com fallback de mediana, assets com trio real | Variância real aparece |
| 3 | Personality Core | `core`, eixos, viés de IA e passivas candidatas no enum; gerador com seed por `individualId` | A mesma criatura mantém a mesma personalidade |
| 4 | Afinidade zero-sum | Desvio HP/BP/AP resolvido para manter o `CombatIndex` | Personalidade muda o estilo, não o poder |
| 5 | Passivas e IA | Uma passiva entre quatro candidatas; utility AI usa núcleo e subclasse | Indivíduos com comportamento próprio |
| 6 | Simetria e pareamento | RNG por combatente, side swap, empates por seed, matchmaking por `CombatIndex` | Sem 100% artificial |
| 7 | Deriva opcional | Conversas e treino deslocam o `core`, como em Time Stranger | P2, atrás de flag |

---

## 14. Arquivos envolvidos

**Personalidade**
- `app/src/main/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityTraits.kt` — `core`, eixos, viés de IA e passivas como propriedades computadas.
- `app/src/main/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityGenerator.kt` — seed por `individualId` e prior da forma HP:BP:AP.
- `app/src/main/java/com/github/nacabaro/vbhelper/database/PersonalityConverters.kt` — sem mudança; o enum continua o mesmo.

**Batalha**
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleStats.kt` — percentis por estágio, estágios 0 e 1, fallback de mediana, coeficiente `k` da defesa e afinidade zero-sum.
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleFactory.kt` — aceitar `personality` e remover os inputs mortos `maxHealth` / `attack`.
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleModel.kt` — `personality` e `passive` em `CombatantDefinition`, telemetria por combatente.
- `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleSimulator.kt` — RNG por combatente, utility com viés de personalidade, passiva, ordem por timestamp, tie-break por seed.

**Entrada de dados**
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/BattlesScreen.kt` — enviar o trio real e a personalidade do indivíduo.
- `app/src/main/java/com/github/nacabaro/vbhelper/screens/OfflineBattleEntryPanel.kt` — perfil com `baseBp == 0` e `personalityId`.

**Testes**
- `app/src/test/java/com/github/nacabaro/vbhelper/battle/offline/TrainingBattleBalanceTest.kt` — trocar "terminou" por matriz de personalidade, side swap e win rate.
- `app/src/test/java/com/github/nacabaro/vbhelper/battle/offline/BattleSimulatorTest.kt` — RNG isolado, ordem, passiva, determinismo.
- `app/src/test/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityGeneratorTest.kt` — mesma `individualId` gera a mesma personalidade; o prior da forma não determina o tipo.

---

## 15. Comandos de validação

```powershell
# Suíte de batalha offline
.\gradlew.bat :app:testIntegrityCheckUnitTest `
  --tests "com.github.nacabaro.vbhelper.battle.offline.*" `
  --offline --console=plain

# Personalidade
.\gradlew.bat :app:testIntegrityCheckUnitTest `
  --tests "com.github.nacabaro.vbhelper.domain.personality.*" `
  --offline --console=plain

# Typecheck e lint
.\gradlew.bat :app:compileDebugKotlin `
  :app:compileIntegrityCheckUnitTestKotlin `
  :app:lintDebug --offline --console=plain
```

---

## 16. Riscos e decisões em aberto

- **`baseBp == 0`:** tratar como zero ou substituir pela mediana da fase? A proposta usa mediana, mas isso é decisão de produto.
- **Coeficiente `k` da defesa:** aumentar o peso do BP torna Amicability relevante, mas muda o TTK de tudo. Precisa de varredura por simulação, não de palpite.
- **Regeneração de personalidade:** subir a versão do sistema regenera as personalities e pode mudar a personalidade já refletida em chats antigos. Aceitável porque o histórico guarda o texto, e não uma promessa semântica sobre o futuro do Digimon.
- **Deriva de personalidade por conversa:** é a feature mais marcante de Time Stranger, mas torna o comportamento não-óbvio. Fica atrás de flag em P2.
- **1×2:** enquanto não houver orçamento de equipe, ranking e balanceamento usam apenas formatos simétricos.

---

## 17. Fontes

### 17.1 Locais

| Fonte | Uso |
|---|---|
| `app/src/main/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityTraits.kt` | 16 tipos, números 1–16, instruções em três idiomas |
| `app/src/main/java/com/github/nacabaro/vbhelper/domain/personality/DigimonPersonalityGenerator.kt` | Ponderação atual e ausência de seed |
| `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleStats.kt` | Conversão DIM/BEM, clamp, baselines por estágio |
| `app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleSimulator.kt` | Fórmula de dano e IA atual |
| `app/src/main/assets/battle_sprites/extracted_digimon_stats/character_data/MonoBehaviour_CharacterData.json` | 1410 registros com HP/AP/BP por Digimon para calibrar os percentis |
| `DigimonWorldBattleEspecifications.txt` | Princípios de batalha, IA, personalidade e regras versionadas |

### 17.2 Digimon World Time Stranger

| Fonte | Confiança | Uso |
|---|---|---|
| [Steam · Personalities Explained](https://steamcommunity.com/app/1984270/discussions/0/581649137613828339) | Média-alta | Tabela dos 16 tipos com os dois eixos de crescimento |
| [PCGamesN · personality chart](https://www.pcgamesn.com/digimon-story-time-stranger/personality) | Média | Bônus numéricos e passivas por núcleo e subclasse |
| [Game8 JP · 性格スキル一覧](https://game8.jp/digimonstory-ts/725109) | Média | Efeito das passivas e a regra de quatro candidatas |
| [GameFAQs · Personality Guide](https://gamefaqs.gamespot.com/ps5/513530-digimon-story-time-stranger/faqs/82355/personality-guide) | Média | Confirma os quatro núcleos e o modelo de grade |
| [Grindosaur · personalities](https://www.grindosaur.com/en/games/digimon-story-time-stranger/personalities/zealous) | Média | Bônus por tipo e chances de conversão |

Time Stranger não publica fórmula oficial de balanceamento. A taxonomia dos quatro núcleos e das 16 personalidade é consistente entre as fontes; os números exatos de bônus, as chances de passiva e as porcentagens de conversão são de guia comunitária e servem como direção de design, não como verdade canônica.

---

## 18. Resumo em uma frase

Duas mudanças de código: o trio **HP/BP/AP** do relógio define o poder, e a personalidade de 1 a 16 define **como** esse poder é interpretado. A battle rating compara só o trio, a personalidade nunca concede dano extra, e o comportamento da IA é lido da mesma personalidade que já aparece no prompt.
