# Digimon World / Vice versus a batalha offline atual do VBHelper

Análise do código em 12/10/2026. Base Git local: `42cdb22f`, incluindo o conteúdo atual do working tree. Ruleset de batalha **3**, catálogo **3** e regras de movimento **2**.

## Escopo e método

- Comparação do DW1 de PS1, principalmente versão USA, com as mudanças documentadas do Vice e com o motor Kotlin atualmente usado pelo treino, Radar e Arena de Tamers.
- As regras locais foram conferidas nos arquivos de implementação. Planos antigos e o grafo existente apontam para versões anteriores do offline; não são a fonte para afirmar que uma funcionalidade existe ou está ausente hoje.
- O Vice é uma família de versões e patches opcionais. Mudanças recentes de IA/dano têm documentação parcial no arquivo `2.2 changes (still in progress).asm`; quando a evidência é apenas o changelog, isso é indicado.
- Esta é uma comparação estática de comportamento, dados e arquitetura. Não foram executadas novas simulações, testes Android ou benchmarks para esta lista.

**Conclusão:** o VBHelper já tem combate autônomo, prontidão, estados, Counter, Burn/Freeze/Shock e especiais. As maiores diferenças estão no significado dos atributos, cálculo de dano/acerto, identidade das técnicas, inteligência/obediência e na forma como animação e locomoção se relacionam.

## A. Regras de combate

### 1. Atributos independentes versus derivados do relógio

- **DW1:** HP, MP, Offense, Defense, Speed e Brains são valores distintos. Speed influencia prontidão e acerto; Brains influencia decisões, comandos e custo de MP.
- **VBHelper:** HP/AP/BP de DiM, BEM ou assets são convertidos e comprimidos. Energia, regeneração, movimento, cooldown e intervalo de decisão são derivados desses três eixos e do estágio. Não há Speed ou Brains independente no perfil do combatente.
- **Impacto:** aumentar AP/BP/HP também altera o ritmo e os recursos; não reproduz a especialização em seis atributos do DW1.
- **Código:** [V-stats], [V-model]; DW1 `BaseStats` em [D-entity].

### 2. Fórmula efetivamente utilizada

- **DW1:** para golpes comuns, usa a diferença `clamp(Offense - Defense, -500, 500)`, potência da técnica, afinidades e variação inteira de 90–110%. Finishers têm aritmética própria.
- **VBHelper:** o padrão é `ADAPTED`: potência × ataque, redução proporcional `100 / (100 + defesa)`, atributo, variação e crítico. `DW1_REFERENCE` existe, mas espera unidades de PS1 e assume fator neutro de especialidade; não é o padrão das sessões criadas pela apresentação.
- **Impacto:** importar as potências ou stats do DW1 sem recalibrar não preserva o dano original. A curva de defesa é diferente.
- **Código:** [V-damage], [V-model] `BattleConfiguration`, [V-presentation] linhas 282–305; DW1 `BTL_calculateDamage` em [D-battle].

### 3. Afinidade de técnica versus atributo Virus/Data/Vaccine

- **DW1/Vice:** o dano consulta o tipo da técnica contra até três especialidades do defensor. O Vice modifica a matriz Fire/Battle/Air/Earth/Water-Ice/Mech/Filth.
- **VBHelper:** aplica Virus → Data → Vaccine → Virus, com multiplicadores 1,10/0,90. As famílias genéricas são neutras; `element` permanece nulo no catálogo e não entra no resolvedor.
- **Impacto:** trocar duas técnicas não muda sua vantagem elemental contra um alvo. A matriz rebalanceada do Vice não está representada.
- **Código:** [V-model] linhas 13–33, [V-catalog], [V-damage]; [D-battle] e [P-sheet], aba `Type damage changes`.

### 4. Precisão e bloqueio

- **DW1:** a chance de acerto depende da precisão da técnica, Speed dos dois lados, animação/estado do defensor e prontidão. Há situações de 100% de acerto e regras específicas para bloqueio/finisher.
- **Vice:** aumenta precisões de vários golpes e adiciona a penalidade de bloqueio de Shock.
- **VBHelper:** o teste usa diretamente `accuracyPercent`; não calcula oposição de velocidades. O catálogo atual mantém o padrão 100%, embora a infraestrutura aceite outras precisões. A guarda reduz o dano a 30% do valor sem guarda.
- **Impacto:** existem erros geométricos e alvos fora de alcance, mas não a mesma disputa de precisão/bloqueio do PS1.
- **Código:** [V-simulator] linhas 1320–1326, [V-damage] linhas 54–59, [V-model] linha 199; `BTL_calculateHitChance` em [D-battle].

### 5. Economia de MP/energia

- **DW1:** o consumo de golpes comuns parte de `mpCost × 3`; Brains alto reduz o custo do parceiro, e doença/ferimento aumenta esse custo. O código também contém exceções de consumo para NPCs em certas batalhas.
- **VBHelper:** debita o custo normalizado da técnica e regenera energia continuamente. Há um ataque básico gratuito usado como fallback quando as técnicas equipadas não podem executar.
- **Impacto:** a falta de energia é temporária e não produz o mesmo esgotamento de MP e dependência de recuperação do DW1.
- **Código:** [V-simulator] linhas 504–507, 1084–1104 e 1193–1205; [V-catalog] linha 295; `BTL_setupMoveExecution` e `BTL_isMoveUsable` em [D-battle].

### 6. Prontidão e cadência

- **DW1:** `speedBuffer` cresce com `Speed / 100 + 1` a cada dois frames, chega a 100 e cai conforme a potência utilizada, com piso -155. Modos de carga mudam o quanto deve esperar.
- **VBHelper:** já preserva a ideia de prontidão 100/-155 e três modos. A regeneração é `45 / cooldownMultiplier` por segundo, o custo considera potência total de multi-hit e há clamps/overrides. Também existem cooldowns por técnica e pausas de observação.
- **Impacto:** o conceito foi adaptado, mas a cadência não é a fórmula temporal original de Speed.
- **Código:** [V-rules], [V-factory] linhas 138–143, [V-simulator] linhas 508–519; `BTL_increaseSpeedBuffer` e `BTL_applyChargeRequirement` em [D-battle].

### 7. Catálogo e compatibilidade das técnicas

- **DW1:** técnicas têm IDs, tipos e animações compatíveis com cada espécie, além de três slots comuns e um finisher. O Vice amplia movesets e compatibilidades específicas.
- **VBHelper:** o catálogo genérico adapta uma matriz de oito famílias × sete ranks de Re:Digitize Decode. Os três slots regulares continuam existindo, mas nomes, poderes, tempos e custos são valores do VBHelper. Os loadouts de Tamers usam templates por estágio/estilo, nomes da espécie e algumas exceções.
- **Impacto:** o app tem um sistema de técnicas completo, mas não o catálogo/moveset histórico de cada Digimon do DW1/Vice.
- **Código:** [V-catalog], [V-loadout], [V-tamer-loadout]; [P-sheet], abas `Tech expansion` e `New techs/tech changes`.

### 8. Tech Boost por espécie versus assinatura do loadout

- **Vice:** a espécie define a técnica favorecida, o bônus de potência e, na série 2.2, alterações de efeito/chance de status.
- **VBHelper:** a primeira técnica equipada com potência positiva vira a assinatura; recebe 5% de potência adicional, limitado a 1–12 unidades. A regra também é aplicada às definições dos oponentes construídas pela mesma factory.
- **Impacto:** reordenar o loadout muda a assinatura; a identidade não é uma tabela de bônus exclusivos por espécie como no Vice.
- **Código:** [V-factory] linhas 103 e 140–143, [V-simulator] linhas 1413–1415; [P-boost], [P-sheet] aba `Tech boost`.

### 9. Efeitos de status: semântica e frequência

- **DW1:** Poison, Confusion, Stun e Flatten têm estados e timers próprios. Poison aplica 1–3% do HP máximo a cada 100 ticks da rotina de batalha.
- **Vice:** adiciona Burn, Freeze e Shock. O código documentado reduz dano causado sob Burn, aumenta dano recebido sob Freeze e altera o bloqueio e consumo de prontidão sob Shock.
- **VBHelper:** Poison/Burn aplicam 1–3% do HP máximo a cada segundo e têm duração finita. Burn/Freeze já aplicam -25%/+25% com arredondamento inteiro. Shock reduz pela metade a regeneração de prontidão, sem implementar a mesma penalidade de bloqueio. Freeze, no catálogo corrente, não acrescenta lentidão de locomoção.
- **Impacto:** os nomes e parte da aritmética coincidem, mas duração, cadência e consequências não são equivalentes.
- **Código:** [V-catalog] linhas 195–224, [V-simulator] linhas 508–513 e 1835–1889, [V-damage]; [D-battle] `BTL_tickStatusEffects`, [P-22].

### 10. Controle, confusão e transformação em Flatten

- **DW1:** Confusion altera a escolha de movimento/golpe/alvo; Flatten possui transição visual, estado de ação e ataque próprio. Há também o estado `SENILE` ligado a Brains/disciplina.
- **VBHelper:** `decode_confusion` reduz ataque para 0,82, sem trocar o algoritmo de alvos/golpes. Paralysis e Liquid Crystalization impedem ações; não reproduzem o ciclo de Flatten. O motor concede um segundo de imunidade a controle ao terminar um efeito incapacitante.
- **Impacto:** controle de dano, desorientação de IA e transformação corporal são comportamentos distintos e hoje não têm correspondência um-para-um.
- **Código:** [V-catalog] linhas 44–50, [V-simulator] linhas 1863–1895; [D-battle] `BTL_selectConfusedMove`, `BTL_tickConfusedState` e `BTL_tickFlatState`.

### 11. Buffs: magnitude e teto

- **DW1:** buffs alteram stats numéricos; a cota de buffs depende de Brains. O Vice permite romper o limite normal com certas técnicas favorecidas e modifica o limite de NPCs por dificuldade.
- **VBHelper:** buffs são multiplicadores temporários e o resolvedor limita combinações de ataque/defesa a 0,5–1,3. O catálogo não tem a regra de Tech Boost para romper o teto dos atributos.
- **Impacto:** a utilidade e a escala dos buffs não equivalem às de War Cry/Muscle Charge/Full Potential no Vice.
- **Código:** [V-catalog], [V-simulator] linhas 1388–1392; [P-22] `CheckIfBuffs`, [P-log].

### 12. Counter, críticos e especiais

- **DW1:** Counter é excluído da seleção comum e possui regra própria que usa 30% da defesa do alvo. A fórmula auditada de dano não contém o multiplicador de crítico genérico do VBHelper. O finisher acumula progresso com meta `3000 - Speed` e carga adicional por L1/R1.
- **VBHelper:** Counter já é uma reação não selecionável, mas sua chance vem do perfil, com custo 25 e cooldown 3,5 s. Técnicas comuns têm crítico padrão de 5%/1,5×. Especial usa barra 0–100, ganhos 18/6 por atacar/receber acerto e progresso passivo. A confirmação de Blast usa HIT/MISS e bônus autorais.
- **Impacto:** há equivalentes funcionais, com probabilidades e economia diferentes. O app também acrescenta POWER/FORM/DUO/JOGRESS, inclusive transformação visual temporária.
- **Código:** [V-catalog] linhas 186–193, [V-model] linhas 194–195, [V-simulator] linhas 285–355, 1262–1279, 1419–1451 e 2185–2190; [V-finisher]; [D-battle] e [D-setup].

## B. Diferenças na IA dos Digimon

### 13. Progressão da inteligência

- **DW1:** Brains muda a seleção de alvo, as prioridades de golpes, limites de buffs e eficiência de MP. O comportamento melhora conforme um atributo treinável cresce.
- **VBHelper:** essas tendências vêm de `PersonalityBattleProfile`, `BattleAiProfile` e estratégia. Há 16 personalidades persistidas, mas não um Brains treinável que desbloqueie capacidades da IA.
- **Impacto:** a individualidade já existe; falta a progressão de inteligência equivalente ao DW1 se ela for um objetivo.
- **Código:** [V-personality], [V-profile]; [D-battle] `BTL_selectPartnerMove` e `BTL_selectFighterTarget`.

### 14. Obediência e períodos de hesitação

- **DW1:** com Brains abaixo de 301, a rotina do parceiro pode entrar em `SENILE` por 100 ticks, dependendo da disciplina. O comando fica em buffers. No executável USA auditado, `handleCommands` calcula um atraso e em seguida o zera; não se deve descrever esse atraso como sempre ativo.
- **VBHelper:** ordens são validadas, reservadas e enfileiradas; uma ordem válida é executada quando estado/alcance/recursos permitem. Não há desobediência por disciplina, e personalidade modifica preferências em vez de invalidar ordens.
- **Impacto:** há espera operacional, mas não a mesma hesitação comportamental do PS1.
- **Código:** [V-simulator] linhas 167–283 e 678–715; [D-battle] `BTL_tickPartnerAI`, [D-commands] `handleCommands`.

### 15. Desbloqueio de comandos

- **DW1:** Brains libera Attack, Moderate, Change, Distance, Defensive e seleção explícita dos golpes em faixas sucessivas.
- **VBHelper:** oferece estratégias, foco, técnicas, defesa, aproximação/recuo e incentivo pela interface, sem portas de acesso por Brains. Não há CP nem incentivo: as técnicas custam apenas EN e as ordens são foco, defesa, movimento por toque na arena, itens, estratégia e especial.
- **Impacto:** o treinador tem controle amplo desde o começo; não há o mesmo crescimento gradual de controle tático.
- **Código:** [V-model] `TrainerAction`, [V-simulator] linhas 194–205, [V-catalog]; [D-setup].

### 16. Escolha de técnica do parceiro

- **DW1:** usa ramos por comando/modo e rankings ponderados de potência, MP, efetividade e área, com limiares de Brains. Attack e Moderate direcionam escolhas próprias.
- **VBHelper:** calcula utilidade por potência, precisão nominal, custo, startup/recovery, distância, repetição, suporte, risco, personalidade e estratégia. Sorteia por softmax; determinados encontros usam ponderação configurada combinada ao score.
- **Impacto:** a IA é mais parametrizada, mas não reproduz as distribuições históricas de `Your Call` ou as regras exatas de Attack/Moderate.
- **Código:** [V-simulator] linhas 866–1104; [D-battle] `BTL_selectPartnerMove` e `BTL_tickPartnerAI`.

### 17. Avaliação de dano e afinidades pela IA

- **DW1:** escolha de golpes/alvos consulta `TYPE_FACTORS`. O Vice registra revisões na avaliação de potência, efetividade, prioridade de alvos e pontuação de fraqueza; o algoritmo mais recente não está integralmente explicado no arquivo público de mudanças.
- **VBHelper:** o score usa potência nominal, custo e tempo, mas não chama `BattleDamageResolver` para estimar dano final no candidato. Não inclui a matriz de afinidades do DW1 nem um bônus explícito de matchup Virus/Data/Vaccine na seleção de alvo.
- **Impacto:** um golpe com melhor dano real após assinatura, defesa, atributos ou múltiplos hits não necessariamente recebe o melhor score.
- **Código:** [V-simulator] linhas 908–993 e 1678–1711; [D-battle] `BTL_calculateTargetScore`; [P-22] notas finais, [P-log].

### 18. Identidade dos inimigos

- **DW1:** NPCs têm movesets e prioridades `movesPrio[4]` próprios. O Vice modifica NPCs, chefes e combinações de golpes para seus modos de dificuldade.
- **VBHelper:** selvagens escolhem um de quatro templates neutros pelo hash da identidade de espécie/asset. Esses templates têm pesos 1,15/1/0,85 e políticas diferentes. Tamers têm outro construtor por estilo, espécie e estágio.
- **Impacto:** encontros são estáveis e variados, mas quatro templates compartilhados não equivalem a um moveset histórico específico para cada espécie/chefe.
- **Código:** [V-encounters], [V-tamer-loadout]; [D-battle] `BTL_selectEnemyMove`, [P-sheet].

### 19. Escolha e troca de alvo

- **DW1:** abaixo de 150 Brains, o parceiro escolhe o inimigo mais próximo; a partir de 150 usa avaliação de fraqueza; a partir de 300 considera status e reavalia com margem. NPCs normalmente focam o parceiro, com exceções previstas em suas rotinas.
- **VBHelper:** CLOSEST/THREAT/VULNERABLE/STATUS são políticas de perfil. O score combina distância, dano/ameaça acumulada, fração de HP e status, além de coordenação entre aliados. Foco do treinador tem precedência por oito segundos.
- **Impacto:** a política independe de inteligência treinada e foi generalizada para duas equipes.
- **Código:** [V-simulator] linhas 236–246 e 1678–1711; [D-battle] `BTL_selectFighterTarget`.

### 20. Defesa e leitura de risco

- **DW1:** bloqueio/acerto depende de Speed, prontidão e estado/animação do defensor, além do comando defensivo.
- **VBHelper:** `tryAutonomousGuard` lê um startup inimigo direcionado ao combatente e em alcance. Personalidade, HP baixo e potência influenciam a chance; DEFENSIVE força essa decisão quando os critérios e o intervalo permitem. O score também penaliza comprometer-se diante de um ataque em preparação.
- **Impacto:** a reação antecipada é uma regra explícita do app e não reproduz a probabilidade original de bloqueio no impacto.
- **Código:** [V-simulator] linhas 969–977 e 1023–1043; [D-battle] `BTL_calculateHitChance`.

### 21. Preparação com buffs

- **DW1:** `buffPrioTimer` começa em `Brains / 10 + 5`, decai e pesa na escolha de buffs; o total permitido também vem de Brains.
- **VBHelper:** prioridade de preparação vem do perfil e decai durante os primeiros 20 s. A cota típica é dois/três buffs, e a IA filtra buffs já ativos para não renová-los repetidamente.
- **Impacto:** já existe preparo de abertura; seus pesos e limites são autorais, sem dependência de Brains.
- **Código:** [V-profile], [V-encounters], [V-simulator] linhas 921–926 e 1094–1098; [D-setup], [D-battle] `BTL_selectEnemyMove`.

### 22. Inimigo selvagem versus treinador adversário

- **DW1:** a rotina normal de inimigos considera finisher pronto e prepara seu uso automaticamente. Comandos do jogador e IA de NPC têm caminhos distintos.
- **VBHelper:** selvagens usam `autonomousSpecial=true`; Tamers usam `autonomousSpecial=false` e um `TamerTrainerController` separado decide itens, estratégia, foco e especial, inclusive confirmação de Blast.
- **Impacto:** o app modela uma segunda camada de decisão: a criatura e seu treinador. Não se deve atribuir o uso de item do treinador à IA interna do Digimon.
- **Código:** [V-simulator] linhas 877–885, [V-encounters], [V-tamer-loadout] linhas 36–43, [V-trainer]; [D-battle] `BTL_tickEnemyAI`.

### 23. Dificuldade e ordem de atualização

- **Vice:** os pacotes documentados alteram stats/movesets de NPCs e regras de bônus, além das revisões de IA. Não há equivalência direta entre suas dificuldades e as do app.
- **VBHelper:** as dificuldades verificadas de Tamers mudam reação (1.500/900/510 ms) e chance de acertar Blast (55/80/94%). No motor atual, leituras de alvos/startups e intenções de movimento são feitas antes da confirmação conjunta; RNG por finalidade/indivíduo permite replay.
- **Impacto:** dificuldade inclui a habilidade do treinador, e a atualização busca reduzir vantagem da ordem dos atores. Isso não implica equivalência frame-a-frame à ordem parceiro → inimigos do DW1.
- **Código:** [V-tamer-loadout] linhas 11–18, [V-simulator] linhas 490–592 e `randomFor`; [D-battle], [P-log].

## C. Como o movimento original é produzido

O caminho verificado é: **IA escolhe técnica/estado e orientação → seleciona animação → lê instruções de movimento dos bones/raiz → prevê colisão → aplica o deslocamento permitido**.

### 24. Root motion versus velocidade contínua

- **DW1:** `startAnimation` inicializa frames, instruções e momentum; `tickAnimation`/`tickMomentum` interpretam os dados e `applyRootMomentum` converte o delta da raiz em deslocamento mundial. As coordenadas acumuladas usam 15 bits fracionários.
- **VBHelper:** a simulação calcula `movementSpeed × deltaMillis / 1000`, aplica direção e steering e publica a posição. O renderer traduz o sólido para essa posição e alterna poses `walk/walk2`.
- **Impacto:** no DW1, o passo depende da animação da espécie; no app, animação acompanha a posição e não determina a distância percorrida. A cadência visual pode divergir da passada física.
- **Código:** [D-anim] `startAnimation`, `tickMomentum`, `applyRootMomentum`; [V-simulator] linhas 1589–1610, [V-scene] linhas 1176–1241.

### 25. Speed e animação de caminhar/correr

- **DW1:** a rotina de batalha escolhe animação 0x23 quando envenenado ou com HP ≤ 20%; caso contrário usa 0x24. O delta espacial vem do momentum dessas animações, enquanto Speed controla prontidão/acerto nas funções auditadas.
- **Vice:** adapta as rotinas de caminhada para os novos efeitos.
- **VBHelper:** a velocidade espacial vem dos derivados HP/BP/AP/estágio e de multiplicadores/slow. Movimento, recuo, posicionamento e knockback compartilham pose de caminhada; não há nessa seleção uma regra automática HP baixo → caminhada lenta.
- **Impacto:** duas espécies com a mesma configuração de batalha têm a mesma velocidade lógica, mesmo com artes diferentes.
- **Código:** [D-battle] `BTL_startWalkingAnimation`, [P-22]; [V-stats], [V-scene] linhas 1182–1188.

### 26. Aproximação para melee e tiro

- **DW1:** melee aproxima até a distância ao quadrado caber no quadrado da soma dos raios das espécies. Tiros buscam uma faixa baseada em `MOVE_DATA.distance + (raio1 + raio2)²`, com tolerância de ±30% nesse valor quadrático.
- **VBHelper:** usa intervalos lineares em unidades de arena, perfis CLOSE/CLOSE_MEDIUM/MEDIUM_LONG e janela tática dentro do alcance. Para vários perfis, prefere a região 45–85% da faixa válida.
- **Impacto:** a distância de combate não deve ser copiada convertendo uma porcentagem de distância quadrática diretamente em porcentagem de distância linear.
- **Código:** [D-battle] `BTL_tickMeleeAttack`, `BTL_tickRangedAttack`; [V-simulator] linhas 1754–1781.

### 27. Recuo e rotação

- **DW1:** aponta para o alvo e soma 0x800 para obter a direção oposta. Após colisão, testa direções laterais e pode corrigir o ângulo em passos de 20 unidades, dentro de um círculo 0–4095. A direção da entidade determina o sentido do root motion.
- **VBHelper:** calcula um destino geométrico no entorno do alvo e escolhe o próximo ponto de steering. A orientação visual é derivada separadamente para manter o sprite extrudado legível.
- **Impacto:** o original tem movimento dependente de giro/passada; o app pode mudar a direção lógica sem a mesma curva de rotação física.
- **Código:** [D-battle] `BTL_backAwayFromTarget`, [D-anim]; [V-simulator] `move`, [V-scene] linhas 1206–1225.

### 28. Movimento entre ataques

- **DW1:** em charge/cooldown mantém distância, fica em idle mirando o alvo ou recua, conforme o modo. Os desvios aleatórios verificados aparecem em colisão/confusão; isso não estabelece um passeio orbital genérico entre todas as ações.
- **VBHelper:** `scheduleAutonomousRead`, `tacticalRoamDestination`, `lateralReadDestination` e `readinessFootwork` acrescentam destinos laterais e arcos de observação dependentes de estratégia/personalidade, inclusive durante espera de prontidão.
- **Impacto:** parte do comportamento de circular/reposicionar do app é uma adição autoral, não uma reprodução da lógica original de cooldown.
- **Código:** [D-battle] `BTL_tickChargeState`, `BTL_tickCooldownState`; [V-simulator] linhas 716–864.

### 29. Colisão com mapa e limite de combate

- **DW1:** prevê o passo, restaura a posição durante a consulta e verifica limite de combate, tiles do mapa e outras entidades. O mapa usa grade 100×100, unidades de tile e flags de bloqueio; o limite de combate inclui checagem projetada em tela. Raios vêm de dados da espécie.
- **VBHelper:** faz steering em um círculo de arena e contra os círculos dos outros combatentes vivos. Os obstáculos fornecidos a essa rotina são os combatentes, não os tiles do cenário. Também separa sobreposições por correção de posições.
- **Impacto:** há colisão e desvio hoje, mas não a topologia dos mapas/batalhas em campo do PS1.
- **Código:** [D-collision], [D-map-collision]; [V-movement], [V-simulator] linhas 831–835 e 1613–1636.

### 30. Desvio local e desistência do posicionamento

- **DW1:** `BTL_findUnblockedRotation` testa direções cardinais/diagonais, direção oposta e tentativa aleatória, com fallback `collisionGrace`. A rotina de tiro tenta iniciar o golpe após contador superior a 80 ticks de reposicionamento.
- **VBHelper:** testa desvios ±30/60/90/120/150 graus com score de progresso/espaço livre. Após 1,5 s sem progresso ou 6 s de posicionamento, falha/replaneja, libera a ordem e evita a técnica por 2 s. A resolução revalida alcance.
- **Impacto:** ambos têm navegação local no trecho auditado, mas heurísticas e tratamento de falha são diferentes; não foi encontrado A*/navmesh nessas rotinas de combate.
- **Código:** [D-battle] `BTL_findUnblockedRotation`, `BTL_tickRangedAttack`; [V-movement], [V-simulator] linhas 1139–1171 e 1281–1287.

## D. Progressão, formatos e apresentação

### 31. Aprendizado e consequências da batalha

- **DW1:** batalha participa de ganhos permanentes, drops, aprendizado de técnicas, felicidade/disciplina, cansaço e passagem do tempo de criação. O Vice corrige o relógio/evolução e oferece aprendizado múltiplo e ganhos/drops opcionais.
- **VBHelper:** treino é uma sessão descartável. Radar possui registros, scan, missões e consequências sociais; Arena credita Bits/histórico/bracket sem alterar stats dos Digimon ou inventário real. A seleção de técnicas genéricas não consulta uma lista de técnicas aprendidas nem sorteia aquisição pós-batalha.
- **Impacto:** seria incorreto dizer que todo offline é sem recompensa. A progressão existe por fluxo, mas não como treinamento de seis stats e aquisição de golpes do DW1.
- **Código:** [V-loadout-screen], [V-arena-repo] linhas 81–139, [V-world-result] linhas 210–253; [P-learning], [P-log], [S-battle].

### 32. Formatos e controle do treinador

- **DW1:** um parceiro enfrenta até três inimigos em batalhas de campo; o treinador físico participa da cena. O Vice expande conteúdo/torneios mantendo essa estrutura base.
- **VBHelper:** treino oferece 1×1, 1×2 e 2×2; Arena de Tamers usa equipes iguais de um/dois membros. O jogador escolhe qual aliado recebe cada ordem e pode usar pausa tática padrão nos menus.
- **Impacto:** coordenação entre dois aliados e identidade de equipe são capacidades adicionais do app; 1×3 não é um formato aceito pelas factories atuais.
- **Código:** [V-factory] linhas 65–67, [V-tamer-factory] linhas 94–113, [V-battle-screen] linhas 293–296; [D-entity], [D-setup], [S-battle].

### 33. Derrota, recuperação e saída

- **DW1:** há fase de queda/contagem antes de confirmar a derrota do parceiro e retorno por recuperação; Run movimenta treinador/parceiro e tem condição própria de término.
- **VBHelper:** `defeat` cancela ações e limpa estados; itens só aceitam aliados vivos e o catálogo não tem item de revive. A luta termina quando uma equipe inteira cai, ou em empate/timeout. `abandon` é um resultado direto, sem simular a fuga original.
- **Impacto:** em 2×2 o aliado sobrevivente continua, mas não existe a mesma janela de recuperação pós-queda nem fuga física do DW1.
- **Código:** [V-simulator] linhas 595–604, 269–276 e 1942–1950, [V-model] `BattleItemKind`; [D-setup] `BTL_isBattleFinished`, [D-commands] `handleFleeing`.

### 34. Feedback de dano e animações dos golpes

- **Vice:** números de dano coloridos comunicam efetividade de tipo. O DW1 usa animações esqueléticas e efeitos associados a técnicas/modelos; dados de animação também movimentam o corpo.
- **VBHelper:** o número é amarelo para especial/crítico e branco para os demais, sem categoria de efetividade. Os personagens são sólidos extrudados a partir de poses de sprite; VFX pequeno/grande são mapeados a partir da arte de cartão/asset, sem reproduzir cada animação MMD histórica.
- **Impacto:** o nome do golpe pode coincidir com uma espécie, mas isso não comprova equivalência de animação, timing ou efeito visual ao DW1/Vice.
- **Código:** [V-battle-screen] linhas 1652–1684, [V-presentation] linhas 201–249, [V-scene]; [D-anim], [P-log].

## O que já está próximo e não deve ser listado como ausente

- Combate em tempo real, criatura autônoma e intervenção do treinador.
- Três técnicas regulares e um especial.
- Prontidão separada da energia, limite 100/-155 e modos de carga.
- Compromisso com uma técnica durante aproximação, revalidação de alvo/alcance e desvio local de colisões.
- Seleção ponderada, prioridade inicial de buffs e especiais automáticos em perfis inimigos.
- Counter não selecionável e defesa reduzida para sua fórmula.
- Poison, Burn, Freeze, Shock, chances de status, proteção e cleanse.
- Variação inteira 90–110%, teto 9999 e ordem Burn → Freeze no cálculo.

## Diferenças mais importantes para aproximar a experiência

1. **Movimento:** separar passo/rotação/pose por espécie e decidir quanto do root motion original deve ser adaptado aos sprites atuais; reduzir ou configurar footwork orbital quando a meta for comportamento DW1.
2. **IA:** decidir se inteligência/obediência será progressão própria ou se continuará sendo representada por personalidade/perfil; são conceitos diferentes.
3. **Escolha tática:** comparar dano esperado por alvo, precisão, custo e estado usando as regras realmente ativas, em vez de apenas potência nominal.
4. **Identidade:** dar espécie/chefe um moveset e uma técnica favorecida próprios, com dados de compatibilidade, em vez de depender apenas de templates e primeiro slot.
5. **Fidelidade numérica:** escolher explicitamente fórmula/escala e regra de acerto. A curva ADAPTED e a precisão padrão 100% explicam boa parte da diferença de combate.

As afinidades elementais foram deliberadamente excluídas do perfil atual; incorporá-las mudaria essa decisão de design. Esta lista descreve o estado e as diferenças, sem implementar mudanças no motor.

## Referências

### Código atual do VBHelper

[V-model]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleModel.kt
[V-damage]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleDamageResolver.kt
[V-simulator]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleSimulator.kt
[V-stats]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleStats.kt
[V-rules]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleAiProfile.kt
[V-profile]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleAiProfile.kt
[V-factory]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/TrainingBattleFactory.kt
[V-catalog]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/GenericTechniqueCatalog.kt
[V-loadout]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/GenericTechniqueLoadout.kt
[V-personality]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/PersonalityBattleProfile.kt
[V-encounters]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/data/EncounterBattleProfiles.kt
[V-finisher]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleFinisher.kt
[V-movement]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/BattleMovement.kt
[V-trainer]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/core/TamerTrainerController.kt
[V-tamer-loadout]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/tamers/TamerBattleLoadouts.kt
[V-tamer-factory]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/tamers/TamerBattleFactory.kt
[V-arena-repo]: app/src/main/java/com/github/nacabaro/vbhelper/battle/offline/tamers/ArenaRepository.kt
[V-world-result]: app/src/main/java/com/github/nacabaro/vbhelper/world/ecosystem/WorldInteractionRepository.kt
[V-presentation]: app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/TrainingBattlePresentation.kt
[V-loadout-screen]: app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/TechniqueLoadoutScreen.kt
[V-battle-screen]: app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/OfflineTrainingBattleScreen.kt
[V-scene]: app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/OfflineBattleScene.kt

- Perfil ativo: [OFFLINE_BATTLE_DW1_IMPLEMENTATION.md](OFFLINE_BATTLE_DW1_IMPLEMENTATION.md), conferido contra a implementação acima.

### Código e documentação externa

[D-battle]: https://github.com/jype0/dw_decomp/blob/main/src/btl/battle_main.c
[D-setup]: https://github.com/jype0/dw_decomp/blob/main/src/btl/battle_setup.c
[D-entity]: https://github.com/jype0/dw_decomp/blob/main/include/dw/entity.h
[D-commands]: https://github.com/jype0/dw_decomp/blob/main/src/main/battle_tick.c
[D-anim]: https://github.com/jype0/dw_decomp/blob/main/src/main/anim.c
[D-map-collision]: https://github.com/jype0/dw_decomp/blob/main/src/main/map_collision.c
[D-collision]: https://github.com/SydMontague/DW1-Code/blob/master/entityCheckCollision.asm
[S-battle]: https://github.com/SydMontague/DW1-SydPatches/blob/main/src/Battle.cpp
[P-22]: https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Hack%20data/2.2%20changes%20%28still%20in%20progress%29.asm
[P-boost]: https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Hack%20data/Digimon%20World%20moves%20boost.asm
[P-log]: https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Changelog.txt
[P-learning]: https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Digimon%20patches/Learn%20multiple%20techs.asm
[P-sheet]: https://docs.google.com/spreadsheets/d/1lG3aLJsLiCwcZXo5-OS18o21GngTVuyAiKA0liV_kpM/edit

- [Syd: previsão de deslocamento](https://github.com/SydMontague/DW1-Code/blob/master/entityMoveForward.asm).
- [Vice: explicação das correções](https://github.com/Vicen04/Dw1DataAndPatches/wiki/Bugs,-glitches,-exploits-and-improvements).
- [Vice: calculadora de dano em Godot/C#](https://github.com/Vicen04/Dw1DataAndPatches/blob/main/New%20installer/DataCheck/DamageCalculator.cs).
- [Vice: metadados e multiplicador de afinidades](https://github.com/Vicen04/Dw1DataAndPatches/blob/main/New%20installer/DataCheck/TechDamage.cs).
