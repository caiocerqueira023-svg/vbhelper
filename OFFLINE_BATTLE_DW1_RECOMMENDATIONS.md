# Recomendações para aproximar a batalha offline do VBHelper de Digimon World 1

Data: 12/10/2026. Base: [diagnóstico com 34 diferenças](OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md), conferido no ruleset 3, catálogo 3 e movimento 2 do código atual.

Este documento contém **50 propostas**, com prós, contras e dependências. Elas são recomendações de evolução; não descrevem funcionalidades já implementadas por esta análise.

## Direção recomendada

Começar pela **sensação de movimento, identidade das espécies e decisões da IA**. Depois aproximar cálculo de dano, precisão e economia de MP. Aprendizado, envelhecimento, perdas e torneios vêm por último, porque alteram progressão e economia além da batalha.

O VBHelper já possui motor autônomo, prontidão, Counter, status, defesa, especiais, steering, replay e testes de balanceamento. A recomendação é modificar regras e dados dessas estruturas onde necessário, aproveitando essa base.

### Critérios

- **P1:** maior impacto na identidade do combate ou fundamento necessário para as próximas mudanças.
- **P2:** fidelidade sistêmica, aprofundamento tático ou conteúdo que depende dos fundamentos.
- **P3:** criação, economia, conteúdo e mudanças de produto de escopo maior.
- **DW1:** inspirado diretamente no comportamento original auditado.
- **Vice:** melhoria documentada do mod; não deve ser atribuída ao jogo original.
- **Adaptação:** solução proposta para o VBHelper/mobile, sem alegação de ser código histórico.

Complexidade é relativa ao código atual: **baixa**, **média** ou **alta**. Não é uma estimativa de prazo. Uma alteração numérica pequena pode exigir muita calibração.

## A. Fundamentos, atributos e identidade

### 01. Criar um perfil de regras DW1 versionado

**Prioridade:** P1 · **Complexidade:** média · **Referência:** adaptação.

- **Implementar:** um perfil explícito para fórmula, recursos, IA, movimento e conteúdo DW1. Registrar sua versão na configuração/checkpoint, com parâmetros próprios; evitar trocar silenciosamente a semântica do ruleset atual.
- **Prós:** permite comparar experiências com as mesmas seeds; facilita calibração e separa alterações históricas das regras autorais.
- **Contras:** aumenta a matriz de configurações, testes e compatibilidade; perfis pouco distintos podem confundir a interface.
- **Depende:** baseline reproduzível e contratos atuais de `BattleConfiguration`/`NpcBattleAdapter`.
- **Ponto atual:** `BattleConfiguration`, `OfflineBattleRuleset`, `NpcBattleAdapter`.

### 02. Criar um adaptador explícito para os seis atributos do DW1

**Prioridade:** P1 para fidelidade numérica · **Complexidade:** alta · **Referência:** DW1 + adaptação.

- **Implementar:** representar HP, MP, Offense, Defense, Speed e Brains no perfil offline, com origem e escala declaradas. HP/AP/BP do relógio podem alimentar uma conversão, mas os atributos secundários precisam de regras próprias.
- **Prós:** permite port coerente das fórmulas e perfis de tanque, atacante, rápido e inteligente.
- **Contras:** o dispositivo não fornece todos esses atributos; será necessário definir valores virtuais, sua progressão e a apresentação ao usuário. Conversões inadequadas criam falsa fidelidade.
- **Depende:** 01; decisão sobre atributos derivados versus progressão virtual independente.
- **Ponto atual:** `TrainingBattleStats`, `CombatantDefinition`, `BattleParticipantProfile`.

### 03. Separar Speed de potência, massa e estágio

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** um Speed de combate usado em prontidão, acerto e finisher, distinguindo-o da velocidade de locomoção e do AP. Definir explicitamente seu teto e conversão.
- **Prós:** permite um Digimon veloz com ataque baixo e diferencia ritmo de dano bruto.
- **Contras:** adiciona uma dimensão de balanceamento e pode quebrar as bandas de estágio atuais.
- **Depende:** 02; testes de monotonicidade e especialização.
- **Ponto atual:** `TrainingBattleStats`, `BattleAiProfile`, `BattleDamageResolver`.

### 04. Introduzir Brains virtual e treinável

**Prioridade:** P2 · **Complexidade:** alta · **Referência:** DW1 + adaptação.

- **Implementar:** inteligência própria da experiência offline, persistida por indivíduo e usada em decisões, comandos, buffs e eficiência de MP. Distinguir esse valor de personalidade.
- **Prós:** o jogador percebe que treinar inteligência melhora o comportamento, não apenas o dano.
- **Contras:** exige origem, ganho, limites e UI; inteligência baixa pode tornar início e novos parceiros menos agradáveis.
- **Depende:** 01–02; desenho da progressão virtual.
- **Ponto atual:** `PersonalityBattleProfile`, preparação da batalha e persistência do indivíduo.

### 05. Modelar disciplina/obediência para o combate

**Prioridade:** P2 · **Complexidade:** alta · **Referência:** DW1 + adaptação.

- **Implementar:** uma relação comportamental que possa influenciar hesitação e resposta a ordens. Explicar a diferença entre personalidade permanente, estratégia momentânea e obediência adquirida.
- **Prós:** aproxima o vínculo treinador/criatura e conecta criação a comportamento observável.
- **Contras:** ordens não atendidas podem parecer bug ou retirar agência; exige feedback e limites claros de frustração.
- **Depende:** 04 e uma fonte legítima para o valor; não assumir que mood do relógio significa disciplina.
- **Ponto atual:** `TrainerAction`, `BattleOrder`, `PersonalityBattleProfile`.

### 06. Criar catálogo de técnicas por espécie com compatibilidade

**Prioridade:** P1 · **Complexidade:** alta · **Referência:** DW1/Vice.

- **Implementar:** movesets nomeados, dados de potência/MP/precisão/alcance/status e técnicas utilizáveis por espécie. Vincular cada técnica a um template visual compatível; manter um fallback documentado quando os dados faltarem.
- **Prós:** espécies deixam de ser apenas stats e arte diferentes; chefes e parceiros ganham identidade real.
- **Contras:** é trabalho de conteúdo contínuo; nomes do Dex não fornecem sozinhos propriedades mecânicas ou animações.
- **Depende:** 01–02 e validação de referências/compatibilidades.
- **Ponto atual:** `GenericTechniqueCatalog`, `GenericTechniqueLoadout`, `TamerBattleLoadouts`.

### 07. Definir técnica favorecida por espécie

**Prioridade:** P1 · **Complexidade:** média · **Referência:** Vice.

- **Implementar:** tabela de técnica favorecida e bônus por espécie, em lugar da regra atual de primeira técnica ofensiva do loadout. Permitir explicitamente variantes e exceções.
- **Prós:** Agumon, Greymon e outros têm assinatura reconhecível mesmo ao reorganizar os slots.
- **Contras:** um bônus excessivo pode tornar os outros golpes irrelevantes; aproxima o Vice, não o DW1 original puro.
- **Depende:** 06 e calibração de dano/custo.
- **Ponto atual:** `TrainingBattleFactory.toDefinition`, `signatureTechniqueId`, `signaturePowerBonus`.

### 08. Acrescentar afinidades de técnica e especialidades do defensor

**Prioridade:** P2, opcional · **Complexidade:** alta · **Referência:** DW1/Vice.

- **Implementar:** tipo do golpe separado de Virus/Data/Vaccine e até três especialidades por espécie, com matriz versionada original ou rebalanceada. Tornar explícito como os dois sistemas interagem.
- **Prós:** dá significado tático a trocar golpes e alvos; reproduz um componente importante do dano do DW1.
- **Contras:** muda a atual proposta element-free; aumenta dados necessários, combinações de balanceamento e carga de leitura do jogador. Empilhar dois sistemas de vantagem pode exagerar matchups.
- **Depende:** 02 e 06; definição da identidade elemental das espécies.
- **Ponto atual:** `BattleAttribute`, `TechniqueDefinition.element`, `BattleDamageResolver`.

## B. Movimento, animação e posicionamento

### 09. Dar locomoção própria a cada espécie

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** perfis de passada, velocidade espacial e tamanho derivados da identidade corporal/arte, separados de Speed e do multiplicador genérico por estágio.
- **Prós:** um corpo pesado, um corredor e uma criatura pequena passam a ocupar o campo de maneiras distintas.
- **Contras:** velocidade espacial também é poder tático; exige balancear alcance, perseguição e kiting, não só aparência.
- **Depende:** 03 e uma política de dados/fallback por espécie.
- **Ponto atual:** `CombatantDefinition.movementSpeed`, `TrainingBattleStats`, `TrainingBattlePresentation`.

### 10. Implementar root motion virtual ou curvas de passada

**Prioridade:** P1 · **Complexidade:** alta · **Referência:** DW1 + adaptação.

- **Implementar:** curvas de deslocamento por ciclo de caminhada/corrida aplicadas no simulador, sincronizadas à pose. A posição continua tendo um único escritor; o renderer apenas acompanha a curva/estado publicado.
- **Prós:** reduz sensação de deslizar e aproxima a relação original entre passada e deslocamento, mesmo usando sprites.
- **Contras:** exige curvas para diferentes corpos, transições e colisões; os sprites atuais não contêm automaticamente os dados de root motion dos modelos MMD.
- **Depende:** 09, timeline de movimento e testes determinísticos de colisão.
- **Ponto atual:** `BattleSimulator.move`, `BattleMovement`, `OfflineBattleScene.animatedPose`.

### 11. Introduzir velocidade de giro e compromisso de direção

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** orientação lógica com limite de giro, direção de avanço/recuo e transições próprias. Usar os passos angulares do original como referência, convertidos para o relógio do app.
- **Prós:** remove mudanças instantâneas de rumo e dá peso físico às criaturas.
- **Contras:** perseguição fica menos eficiente; pode causar círculos, atrasos e erros de alcance se a IA ignorar o custo de virar.
- **Depende:** 09–10; não implementar a rotação apenas visualmente.
- **Ponto atual:** `BattlePosition`, `BattleSimulator.move`, orientação em `OfflineBattleScene`.

### 12. Diferenciar caminhada e corrida por condição

**Prioridade:** P1 · **Complexidade:** baixa/média · **Referência:** DW1; extensões do Vice opcionais.

- **Implementar:** postura lenta com HP ≤ 20% ou Poison no perfil clássico, postura rápida em condição normal e variantes para efeitos do Vice. Trocar a regra de animação e a passada juntas.
- **Prós:** torna desgaste visível e aproxima a movimentação auditada do original.
- **Contras:** pode reforçar uma vantagem já acumulada contra o lado com pouco HP; requer distinguir dificuldade desejada de efeito bola de neve.
- **Depende:** 09–10 e seleção de quais status alteram movimento.
- **Ponto atual:** `BattleStatusEffect`, `movementMultiplier`, seleção de poses do renderer.

### 13. Usar distância de contato baseada nos corpos

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** raios de espécie e alcance melee relativo à soma dos corpos, com tolerância pequena e coerência entre animação, contato e dano.
- **Prós:** ataques corpo a corpo parecem realmente alcançar o adversário; corpos grandes ocupam mais espaço.
- **Contras:** afeta separação, câmera, AoE e equilíbrio entre tamanhos; escala visual não é automaticamente um raio físico correto.
- **Depende:** 09 e limites de colisão consistentes.
- **Ponto atual:** `collisionRadius`, `tacticalRangeWindow`, `separateCombatants`.

### 14. Recriar a faixa de posicionamento dos golpes à distância

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** uma distância ideal por técnica e comportamento aproximar/parar/recuar equivalente ao original. Converter corretamente os valores de distância ao quadrado e sua tolerância; não aplicar ±30% diretamente à distância linear.
- **Prós:** gera o padrão característico de buscar distância, preparar e lançar, reduzindo movimento arbitrário.
- **Contras:** pode aumentar reposicionamentos e criar impasses entre dois perfis de longo alcance.
- **Depende:** 06 e 13; testes com alvo móvel e bordas.
- **Ponto atual:** `TechniqueRangeProfile`, `tacticalRangeWindow`, `approachAndExecute`.

### 15. Reproduzir posturas durante carga e cooldown

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** regras claras para manter distância, ficar parado mirando ou recuar conforme o modo de carga, em vez de usar uma observação espacial genérica em todas as esperas.
- **Prós:** altera imediatamente a sensação de combate e conecta estratégia ao movimento visível.
- **Contras:** menos movimento constante pode parecer menos dinâmico; permanência parado precisa de uma boa animação de preparação.
- **Depende:** 14 e integração com prontidão.
- **Ponto atual:** `scheduleAutonomousRead`, `continueAutonomousRead`, `readinessFootwork`.

### 16. Tornar os movimentos orbitais condicionais

**Prioridade:** P1 · **Complexidade:** baixa/média · **Referência:** adaptação para fidelidade ao DW1.

- **Implementar:** reduzir os arcos laterais no perfil clássico e reservá-los para obstáculos, confusão ou perfis especificamente móveis. A técnica escolhida e o modo de carga devem determinar a maior parte da locomoção.
- **Prós:** evita a sensação de passeio constante e aproxima a intenção do movimento original.
- **Contras:** pode reduzir expressividade das personalidades; remover movimento sem substituir sua função pode causar sobreposição ou espera sem feedback.
- **Depende:** 15; preservar o desvio necessário para colisão.
- **Ponto atual:** `tacticalRoamDestination`, `lateralReadDestination`, `readinessFootwork`.

### 17. Acrescentar topologia e obstáculos do cenário

**Prioridade:** P2 · **Complexidade:** alta · **Referência:** DW1 + adaptação.

- **Implementar:** uma malha lógica leve de áreas bloqueadas/tiles para batalhas em campo, ligada ao manifesto da arena e usada pelo movimento. Começar com obstáculos simples; decidir pathfinding global somente quando os mapas exigirem.
- **Prós:** terreno passa a afetar aproximação, linha de combate e desvio, como no original.
- **Contras:** mapas, renderer, física e IA precisam concordar; obstáculos mal desenhados provocam batalhas intermináveis.
- **Depende:** 13–14 e dados de navegação do cenário.
- **Ponto atual:** `OfflineArenaManifest`, `BattleMovementObstacle`, `steerBattlePosition`.

### 18. Ajustar desvio local e falha de posicionamento ao perfil clássico

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** testes de direções e recuo com padrão inspirado no original, mantendo detecção de falta de progresso. Separar timeout de aproximação de tempo de preparo e definir se a criatura tenta outro golpe ou aguarda.
- **Prós:** preserva trajetórias reconhecíveis com recuperação robusta quando bloqueada.
- **Contras:** pode ser menos eficiente que o steering atual; copiar a tentativa original de atacar após 80 ticks sem revalidar geometria pode produzir ataques incoerentes.
- **Depende:** 11 e 17; relógio lógico versionado.
- **Ponto atual:** `BattleMovement`, `POSITIONING_STALL_MILLIS`, `POSITIONING_TIMEOUT_MILLIS`.

## C. IA, inteligência e comandos

### 19. Desbloquear comandos conforme Brains

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** faixas de inteligência para acesso a Attack, Moderate, Change, Distance, Defensive e golpes específicos, com explicação na preparação e na interface.
- **Prós:** recria a progressão de controle do treinador e dá valor visível ao treino de inteligência.
- **Contras:** jogadores perdem opções disponíveis hoje; isso pode ser frustrante em um modo de treino/sandbox.
- **Depende:** 04; política explícita de acesso no perfil clássico e no treino.
- **Ponto atual:** `TrainerAction`, HUD de técnicas/estratégias e preparação.

### 20. Implementar uma seleção Your Call por faixas de inteligência

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** ramos e sorteios ponderados que considerem potência, custo, área e efetividade conforme Brains/modo, em um seletor de IA próprio. Personalidade pode aplicar ajustes limitados, se o perfil permitir.
- **Prós:** autonomia fica mais próxima do original e o jogador distingue inteligência de temperamento.
- **Contras:** escolhas menos ótimas são intencionais nas faixas baixas; manter dois seletores aumenta complexidade de manutenção.
- **Depende:** 04 e 06; 08 quando houver avaliação elemental.
- **Ponto atual:** `planAutonomousAction`, `chooseSoftmax`, `chooseEncounterWeighted`.

### 21. Dar semântica própria a Attack e Moderate

**Prioridade:** P1 · **Complexidade:** baixa/média · **Referência:** DW1.

- **Implementar:** comandos que priorizem potência ou economia de MP e mudem postura/carga de forma reconhecível. Não tratá-los apenas como pequenos deltas no mesmo score geral.
- **Prós:** cada ordem produz uma resposta observável; recria a linguagem tática do DW1.
- **Contras:** pode tornar a variedade de estratégias atual redundante e exigir uma reorganização dos comandos.
- **Depende:** 15 e 20; critérios de desempate para golpes equivalentes.
- **Ponto atual:** `BattleStrategy`, `chargeMode`, `TrainerAction.ChangeStrategy`.

### 22. Selecionar alvos conforme Brains e contexto

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1 com correções.

- **Implementar:** proximidade nas faixas baixas, avaliação de vulnerabilidade a partir de 150 e status/reavaliação a partir de 300, usando pontuação correta do candidato e margem de troca.
- **Prós:** o ganho de inteligência muda decisões visíveis; evita troca errática de alvos.
- **Contras:** aumenta complexidade no 2×2 e exige conciliar foco do treinador, coordenação e políticas atuais.
- **Depende:** 04; 24 para estimativas táticas corrigidas. Não reproduzir o erro histórico de pontuação de fraqueza.
- **Ponto atual:** `chooseEnemyByPolicy`, `targetSwitchThreshold`, foco do treinador.

### 23. Criar perfis específicos de NPCs e chefes

**Prioridade:** P1 · **Complexidade:** alta · **Referência:** DW1/Vice.

- **Implementar:** moveset, pesos, uso de buffs, postura, alvo e comportamento de especial por espécie/encontro. Chefes podem ter fases e exceções declaradas. Os quatro templates atuais continuam úteis como fallback de conteúdo.
- **Prós:** encontros passam a ser aprendidos pelo jogador; chefes ganham identidade além de mais HP.
- **Contras:** demanda curadoria e testes por perfil; padrões excessivamente fixos ficam exploráveis.
- **Depende:** 06 e seletores de IA; fases de chefes são uma expansão proposta, não uma regra universal auditada do DW1.
- **Ponto atual:** `EncounterBattleProfiles`, `BattleAiProfile`, `TamerBattleLoadouts`.

### 24. Fazer a IA estimar dano e utilidade reais por alvo

**Prioridade:** P1 para a experiência corrigida · **Complexidade:** média/alta · **Referência:** Vice + adaptação.

- **Implementar:** estimativa sem consumir RNG do resultado esperado da técnica: assinatura, defesa, atributos/afinidades, múltiplos hits, precisão, duração e custo. Comparar esse valor com buffs/controle, sem usar informação futura.
- **Prós:** a IA escolhe melhor o que o motor realmente resolve; reduz discrepância entre potência nominal e dano útil.
- **Contras:** uma IA otimizada pode ficar mais forte que a original; falta de limitação por inteligência pode dar conhecimento perfeito a todos os Digimon.
- **Depende:** 06–08, 20 e 29–30; preservar seletores históricos quando a intenção for distribuição original.
- **Ponto atual:** scores de `planAutonomousAction` e `BattleDamageResolver`.

### 25. Introduzir hesitação e obediência com feedback

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** estado de hesitação limitado em perfis de baixa inteligência/disciplina, com indicação visível e recuperação definida. Distinguir hesitação de falta de MP, alcance ou cooldown.
- **Prós:** torna a criatura menos mecânica e dá consequência ao vínculo/treinamento.
- **Contras:** pode interromper decisões legítimas e parecer input perdido. O atraso calculado e depois zerado no executável original não deve ser copiado como uma regra ativa ideal.
- **Depende:** 04–05 e UI de motivos de espera.
- **Ponto atual:** `CombatantState`, fila de ordens, `CombatantDebugSnapshot`.

### 26. Fazer Confusion alterar a IA de verdade

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** seleção desorientada de técnica, períodos sem alvo definido e mudanças de direção compatíveis com o comportamento auditado. Configurar como ordens interagem com esse estado; não inferir fogo amigo como regra obrigatória.
- **Prós:** confusão ganha identidade distinta de redução de ataque e atordoamento.
- **Contras:** pode alongar a batalha ou levar a decisões sem utilidade; movimento/confusão precisa respeitar colisões e término do efeito.
- **Depende:** 20–22 e regras de status.
- **Ponto atual:** `decode_confusion`, seleção de alvos e técnicas no simulador.

### 27. Ajustar defesa e leitura do adversário à inteligência

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** diferenciar comando defensivo, decisão de antecipar um startup e chance de bloquear no impacto. Limitar antecipação por perfil/inteligência e usar precisão/Speed na resolução quando o perfil clássico estiver ativo.
- **Prós:** evita que toda criatura leia ataques com habilidade equivalente; cria estilos defensivos mais claros.
- **Contras:** duas camadas defensivas podem reduzir demais o dano se seus efeitos forem empilhados sem orçamento.
- **Depende:** 03–04 e 30; calibrar com Counter e recuperação de impacto.
- **Ponto atual:** `tryAutonomousGuard`, `damageFor`, `BattleDamageResolver`.

### 28. Ligar preparo de buffs a Brains e à situação

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** cota e prioridade inicial de buffs baseadas em Brains, com queda de prioridade ao longo da luta. A IA deve considerar benefício restante, MP e risco de interrupção.
- **Prós:** diferencia criatura inteligente/preparada de uma que apenas pressiona; reaproveita a infraestrutura de quota atual.
- **Contras:** pode favorecer excessivamente alta inteligência; buffs e anti-refresh precisam funcionar juntos para evitar ciclos inúteis.
- **Depende:** 04 e 35.
- **Ponto atual:** `buffLimit`, `preparationPriority`, `buffsRemaining` e filtro de buffs ativos.

## D. Dano, recursos e efeitos

### 29. Ativar uma fórmula DW1 calibrada para o perfil correto

**Prioridade:** P1 para fidelidade numérica · **Complexidade:** média/alta · **Referência:** DW1.

- **Implementar:** usar a curva por diferença Offense–Defense, aritmética inteira, variância e finisher separados, com entradas na escala correta. Integrar a escolha na factory de cada fluxo e não apenas na ferramenta de comparação.
- **Prós:** defesa, Counter e potência passam a produzir relações próximas das históricas.
- **Contras:** ativar `DW1_REFERENCE` sobre stats compactos sem conversão muda duração e matchup de modo imprevisível; reequilíbrio é obrigatório.
- **Depende:** 01–03 e 06; 08 se a meta incluir o fator de especialidade completo.
- **Ponto atual:** `BattleDamageResolver`, `Dw1DamageFormula`, `TrainingBattlePresentationFactory`.

### 30. Implementar acerto/bloqueio com precisão e Speed

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1/Vice.

- **Implementar:** precisões variadas por técnica e resolução dependente de Speed, prontidão e estado do defensor. Diferenciar ausência de contato geométrico de erro/bloqueio probabilístico.
- **Prós:** golpes ganham riscos distintos; velocidade passa a ter utilidade além de atacar mais cedo.
- **Contras:** erros frequentes podem frustrar; movimentos e chance de acerto podem penalizar a mesma técnica duas vezes.
- **Depende:** 03, 06 e 27; manter identidade dos motivos de miss.
- **Ponto atual:** `accuracyPercent`, `resolveTechnique`, eventos de miss.

### 31. Aproximar energia da economia de MP do DW1

**Prioridade:** P2 · **Complexidade:** média/alta · **Referência:** DW1.

- **Implementar:** custos de MP por técnica, descontos por Brains e penalidade de doença/ferimento. Reduzir ou desativar regeneração automática no perfil clássico e rever o ataque básico gratuito.
- **Prós:** recuperadores e escolhas de eficiência ficam relevantes; a pressão não pode ser sustentada indefinidamente.
- **Contras:** pode produzir tempos mortos e depender demais de itens; partidas precisam de saída válida quando não existe ação útil.
- **Depende:** 02, 04, 06 e uma política de itens por sessão; o kit de treino atual pode atender a primeira versão. Revisar a IA de economia e os limites de duração.
- **Ponto atual:** regeneração de energia, `autonomousTechniqueCandidates`, custos do catálogo.

### 32. Calibrar prontidão e cooldown pelo relógio lógico original

**Prioridade:** P1 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** regeneração ligada a Speed, consumo por técnica, modo de carga e recuperação após ação. Converter frames/ticks históricos para o relógio do perfil; impedir que cooldown, observação e prontidão tripliquem a espera sem intenção.
- **Prós:** aproxima ritmo e escolha entre ataque rápido e espera completa; a estrutura 100/-155 já existe.
- **Contras:** pequenas diferenças de cadência mudam todo o equilíbrio. Igualar tempos não significa obrigar o renderer a rodar no FPS do PS1.
- **Depende:** 03, 15 e dados de duração das técnicas.
- **Ponto atual:** `BattleRules`, `readinessRequired`, `cooldownDuration`, pausas autônomas.

### 33. Dar identidade às reações de impacto e recuperação

**Prioridade:** P1 · **Complexidade:** média/alta · **Referência:** DW1 + adaptação.

- **Implementar:** reação de golpe, defesa, knockback, continuidade de ação e recuperação com durações/poses coerentes. Diferenciar tempo de reação ao impacto da imunidade atual contra encadeamento de controle.
- **Prós:** golpes parecem ter peso e interrupções ficam legíveis; conecta decisão a animação.
- **Contras:** controle pode virar bola de neve; o sistema precisa conservar uma recuperação saudável mesmo quando aproxima timings históricos.
- **Depende:** 10–11 e 27; validar trocas simultâneas e efeitos de múltiplos hits.
- **Ponto atual:** startup/active/recovery, `applyKnockback`, `controlImmuneUntil`, poses do renderer.

### 34. Tornar Counter dependente de compatibilidade/equipamento

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** exigir que a espécie/loadout tenha a técnica compatível, definir seu papel no slot e usar as condições de reação do perfil. Preservar a resolução própria de defesa reduzida e a prevenção de recursão.
- **Prós:** Counter passa a ser uma escolha tática de construção, não uma reação universal concedida pela factory.
- **Contras:** consome espaço do loadout e muda poder defensivo de espécies que hoje contam com a reação genérica.
- **Depende:** 06, 27 e 30.
- **Ponto atual:** `counterTechniqueId`, `reactionOnly`, `queueCounter`.

### 35. Aproximar buffs numéricos e seus tetos

**Prioridade:** P2 · **Complexidade:** média/alta · **Referência:** DW1; rompimento de teto é Vice.

- **Implementar:** dados próprios de War Cry/Muscle Charge/Full Potential e similares, limite por atributo e feedback do aumento realmente aplicado. Um perfil Vice pode permitir ultrapassar o teto por técnica favorecida, com limites explícitos.
- **Prós:** buffs recuperam identidade e interação com stats; evita mostrar aumento inexistente ao atingir o teto.
- **Contras:** altera a contenção atual de multiplicadores a 1,3; rompimento de teto pode dominar o metagame.
- **Depende:** 02, 07, 24 e 28.
- **Ponto atual:** `BattleStatusEffect`, limites em `damageFor`, score de suporte.

### 36. Implementar os estados originais e suas durações

**Prioridade:** P2 · **Complexidade:** alta · **Referência:** DW1.

- **Implementar:** Poison com cadência lógica adequada, Stun com timer específico e Flatten com transição, comportamento e ataque próprios. Confusion depende do comportamento de IA da proposta 26.
- **Prós:** efeitos deixam de ser traduções genéricas e passam a ter comportamento histórico reconhecível.
- **Contras:** Flatten exige arte/transições e novas interações; cadência e duração podem tornar o veneno mais ou menos forte que a configuração atual.
- **Depende:** 26, 32–33 e templates de apresentação.
- **Ponto atual:** `BattleStatusEffect`, `tickStatuses`, `applyStatuses`, estados do motor.

### 37. Completar as consequências dos efeitos do Vice

**Prioridade:** P2, opcional · **Complexidade:** média · **Referência:** Vice.

- **Implementar:** além do -25%/+25% já existente, configurar movimentação sob Freeze, efeitos de Shock sobre bloqueio e prontidão, cadências, exclusividades, cura e bônus de status da técnica favorecida.
- **Prós:** Burn/Freeze/Shock passam a ter diferenças táticas completas e aproveitam muita infraestrutura já pronta.
- **Contras:** aproxima o Vice, não o original; Shock/controle podem impedir ações por muito tempo se consumo e regeneração forem ambos penalizados.
- **Depende:** 07, 12, 27, 30 e 32; usar uma versão documentada como alvo, não presumir fórmula de todas as releases.
- **Ponto atual:** `BattleStatusMechanic`, grupo `persistent_ailment`, catálogo e cálculo de dano.

## E. Finishers, recuperação e papel do treinador

### 38. Recriar a carga de finisher ligada a Speed

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** meta/progresso equivalente a `3000 - Speed`, com ganhos passivos e por eventos convertidos ao perfil. Distinguir disponibilidade do finisher da preparação da técnica e da carga extra do jogador.
- **Prós:** Speed ganha outra utilidade; o momento do especial fica mais próximo do original.
- **Contras:** altera os ganhos atuais 18/6 e afeta vitórias, IA adversária e sincronização de especiais em dupla.
- **Depende:** 03 e 32.
- **Ponto atual:** `specialCharge`, `addSpecialCharge`, `SPECIAL_CHARGE_MAX`.

### 39. Adicionar carregamento manual contínuo do finisher

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1 + adaptação mobile.

- **Implementar:** uma janela com carga 0–80 e ganho por input, ligada ao multiplicador de dano original. Para celular, definir uma interação equivalente e uma opção de acessibilidade previsível.
- **Prós:** aproxima a participação L1/R1 do PS1 e permite resultado graduado em vez de HIT/MISS.
- **Contras:** repetição rápida pode cansar e favorecer tipos de input; exige orçamento consistente entre toque, controle e acessibilidade.
- **Depende:** 29, 38 e relógio único da janela.
- **Ponto atual:** `pendingBlastTiming`, `ConfirmBlastTiming`, UI de finisher.

### 40. Dar dados e execução próprios aos finishers de cada espécie

**Prioridade:** P1 · **Complexidade:** alta · **Referência:** DW1/Vice.

- **Implementar:** potência, alcance, efeitos, sequência de preparação e comportamento visual próprios. Reaproveitar templates onde possível, mas separar o nome canônico da definição mecânica efetiva.
- **Prós:** um finisher passa a ser parte da identidade da espécie; melhora o principal momento audiovisual do combate.
- **Contras:** alto custo de conteúdo e validação; nomes de técnicas não fornecem os frames, modelos ou efeitos necessários.
- **Depende:** 06, 10, 29 e 38–39 conforme o perfil.
- **Ponto atual:** `specialDisplayNameOverride`, `TamerBattleLoadouts`, `BattleFinisherTimeline`.

### 41. Implementar queda e janela de recuperação antes da derrota

**Prioridade:** P2 · **Complexidade:** alta · **Referência:** DW1.

- **Implementar:** estado separado de queda, contagem e item de recuperação para uma criatura caída. Revalidar alvo, efeitos, fila e término da equipe quando a janela acabar.
- **Prós:** aproxima a tensão do Restore no original e permite salvar o parceiro.
- **Contras:** aumenta muito as transições e as possibilidades de prolongar a luta; em 2×2, exige regras de quem usa item e quem pode ser alvo.
- **Depende:** 33 e política de inventário; definir interação com AoE, empate e timeout.
- **Ponto atual:** `defeat`, `checkBattleEnd`, `BattleItemKind`, validação de alvo vivo.

### 42. Implementar Run/fuga como ação de combate

**Prioridade:** P2 · **Complexidade:** média/alta · **Referência:** DW1 + adaptação.

- **Implementar:** ordem de fugir, deslocamento até região de saída, ataques/interrupções durante a fuga e resultado próprio. Algumas batalhas roteirizadas podem proibir a ação.
- **Prós:** fugir ganha risco e presença física; distingue fuga bem-sucedida de abandono direto da sessão.
- **Contras:** exige região de saída, tratamento de aliados e consequências específicas por fluxo; bordas ruins podem impedir a fuga.
- **Depende:** 11 e 17; 46 para consequências de criação.
- **Ponto atual:** `TrainerAction`, `BattleOutcome`, `abandon`, adaptadores de resultado.

### 43. Oferecer uma apresentação de treinador orientada ao perfil clássico

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1 + adaptação.

- **Implementar:** interface centrada em Your Call, Attack, Moderate, Distance, Defensive, Change, itens e finisher. Definir como Blast, FORM, DUO e Jogress são exibidos ou habilitados nesse perfil (CP e incentivo foram removidos do app).
- **Prós:** reduz a distância de linguagem e fluxo em relação ao DW1, mesmo mantendo o motor capaz de recursos adicionais.
- **Contras:** reduz destaque das funcionalidades próprias do VBHelper; opções inconsistentes entre modos podem exigir onboarding.
- **Depende:** 01, 19, 21 e 38–39; a regra de pausa tática deve ser uma escolha explícita da experiência.
- **Ponto atual:** `OfflineTrainingBattleScreen`, menus e comandos do treinador.

## F. Aprendizado, progressão, formatos e torneios

### 44. Introduzir biblioteca de técnicas aprendidas por indivíduo

**Prioridade:** P3 · **Complexidade:** alta · **Referência:** DW1; aprendizado múltiplo opcional é Vice.

- **Implementar:** progresso de técnicas conhecidas, compatibilidade da forma atual e chance de aprender técnicas observadas em batalha/treino. Equipar consulta essa biblioteca; sandbox pode usar uma política de acesso própria.
- **Prós:** enfrentar certos inimigos tem propósito além de vencer; aprendizado e evolução modificam o leque de opções.
- **Contras:** restringe o catálogo livre disponível hoje e pode criar grind; exige persistência, migração e feedback de aquisição.
- **Depende:** 06 e regras idempotentes de resultado; decidir uma técnica por batalha ou todas as aprovadas pelo sorteio.
- **Ponto atual:** `GenericTechniqueLoadout`, tela de loadout e adaptadores de resultado.

### 45. Acrescentar ganhos permanentes e drops por confronto

**Prioridade:** P3 · **Complexidade:** alta · **Referência:** DW1/Vice + adaptação.

- **Implementar:** ganhos de atributos virtuais, Bits e itens conforme inimigo, ações e resultado; distinguir treino, Radar e Arena. Reaproveitar transações/recibos já usados para resultados, sem duplicar pagamentos.
- **Prós:** a batalha passa a integrar criação e exploração; inimigos fracos/fortes podem ter recompensas distintas.
- **Contras:** muda economia e incentiva farm; ganhos de poder podem desbalancear a Arena e não correspondem automaticamente aos stats gravados no relógio.
- **Depende:** 02 e 44; definir exatamente o que cada fluxo pode conceder.
- **Ponto atual:** `ArenaRepository`, `WorldInteractionRepository`, recompensas de quests e inventário.

### 46. Integrar tempo de batalha, cansaço e condição do parceiro

**Prioridade:** P3 · **Complexidade:** alta · **Referência:** DW1 com correções do Vice.

- **Implementar:** passagem de tempo virtual de criação após a batalha, avanço correto de evolução/eventos diários, cansaço e consequências de condição. Doença/ferimento podem alterar stats e MP no snapshot de entrada.
- **Prós:** aproxima a experiência de criar uma criatura, não apenas competir com uma ficha de combate.
- **Contras:** é uma expansão de produto além do motor; pode conflitar com dados e relógios do dispositivo se suas origens não forem distinguidas. Penalidades podem desencorajar treino.
- **Depende:** 02, 05 e 45; definir persistência virtual e consequências separadas por modo.
- **Ponto atual:** preparação de stats, dados de condição do indivíduo e resultado do World.

### 47. Acrescentar o formato 1×3

**Prioridade:** P2 · **Complexidade:** média · **Referência:** DW1.

- **Implementar:** permitir um parceiro contra até três inimigos nas factories, seleção de equipes, HUD e critérios de fim. Recalibrar pressão, alvo, AoE e geração de especial.
- **Prós:** reproduz um formato característico das batalhas de campo do original, mantendo quatro combatentes no total.
- **Contras:** número total igual ao 2×2 não implica dificuldade igual; três atacantes podem gerar controle e dano concentrado excessivos.
- **Depende:** 22, 30 e 33; templates de encontro e revisão de interface.
- **Ponto atual:** limites de equipe em `TrainingBattleFactory`/`TrainingBattlePresentationFactory` e HUD.

### 48. Criar copas com requisitos e recuperação entre rodadas

**Prioridade:** P3 · **Complexidade:** alta · **Referência:** DW1; Royal Cup restaurada é Vice.

- **Implementar:** copas por estágio/especialidade/requisitos e calendário virtual, regras de participação, recompensas e regeneração parcial entre rodadas. Usar dados próprios para o conteúdo DW1 em vez de tratar todos os campeonatos como iguais à Arena de Tamers.
- **Prós:** dá metas de criação e valor a formas menos poderosas; torneios ganham identidade.
- **Contras:** calendários/restrições podem bloquear participação; demanda conteúdo e reequilíbrio das equipes atuais.
- **Depende:** 06, 45–46; 08 se os requisitos usarem especialidades.
- **Ponto atual:** `ArenaTournament`, `ArenaRepository`, catálogo de torneios e participantes.

## G. Feedback e verificação

### 49. Mostrar efetividade, carga e mudanças de condição no HUD

**Prioridade:** P1 para feedback básico; P2 para efetividade · **Complexidade:** média · **Referência:** Vice + adaptação.

- **Implementar:** comunicar vantagem/resistência do golpe, motivo de espera, passo lento por condição, MP efetivamente gasto e buff realmente aplicado. Usar texto/ícone junto da cor, distinguindo crítico, especial e efetividade.
- **Prós:** o jogador entende por que o Digimon anda, espera, erra ou usa um golpe; reduz sensação de IA arbitrária.
- **Contras:** excesso de informação pode cobrir a arena; exige selecionar sinais úteis e manter diagnóstico detalhado fora do HUD comum.
- **Depende:** metadados dos eventos/snapshots; 08 para cores de afinidade reais, 30 e 35 para valores novos.
- **Ponto atual:** `BattleEvent`, `BattleImpactSnapshot`, `CombatantDebugSnapshot`, `DamageNumberOverlay`.

### 50. Expandir a validação para comportamento e sensação de DW1

**Prioridade:** P1, transversal · **Complexidade:** média/alta · **Referência:** adaptação.

- **Implementar:** estender o runner/testes existentes com cenários de passada/giro, contato, recuo, saída de colisão, prioridades por Brains, MP esgotado, precisão, revive, 1×3 e finisher. Medir tempo em idle/carga, frequência de reposicionamento, uso de cada técnica e cumprimento de ordens.
- **Prós:** evita aproximar aparência enquanto IA/geometria continuam incoerentes; detecta mudanças de duração, dominância e loops.
- **Contras:** bons cenários demandam manutenção; taxas de vitória sozinhas não provam fidelidade histórica e benchmarks amplos não devem tornar toda verificação lenta.
- **Depende:** 01 e telemetria atual. Começar com fixtures comportamentais pequenas; ampliar matrizes quando regras novas justificarem.
- **Ponto atual:** runner de balanceamento, testes de domínio, snapshots e testes Android já existentes.

## Sequência recomendada de implementação

### Etapa 0 — Perfil e baseline

- Propostas 01 e 50.
- Definir também os contratos e a origem dos atributos das propostas 02–03 que movimento e IA irão consumir; a calibração numérica completa fica na etapa 3.
- Registrar tempos, escolhas, distâncias, consumo de energia e resultados do perfil atual.
- **Critério:** mesma configuração/seed/ordens reproduz o mesmo comportamento; checkpoints identificam o perfil utilizado.

### Etapa 1 — Movimento reconhecível

- Propostas 09–16 e feedback básico da 49.
- Implementar primeiro postura, giro, alcance e restrição de orbitais; depois curvas de passada mais completas.
- **Critério:** aproximação/recuo têm intenção legível, passos e posições são coerentes, e o Digimon não circula sem relação com técnica/modo.

### Etapa 2 — Identidade e decisões

- Propostas 06–07, 20–24 e 26; inteligência da 04 conforme a progressão escolhida.
- **Critério:** espécies/perfis têm uso de técnicas e alvos distinguíveis; decisões não usam resultados aleatórios futuros.

### Etapa 3 — Mecânica numérica e ritmo

- Calibrar as propostas 02–03 e implementar 27–36; afinidades da 08 se fizerem parte do objetivo.
- **Critério:** custo, acerto, defesa e cooldown produzem a duração pretendida e não geram espera/controle infinito.

### Etapa 4 — Finishers e conteúdo de batalha

- Propostas 38–43 e 47; efeitos do Vice da 37 quando desejados.
- **Critério:** especial preparado, carga do jogador, impacto e término de luta são estados distintos e recuperáveis; 1×3 termina corretamente.

### Etapa 5 — Criação e economia

- Propostas 05, 19, 25 e 44–48 conforme o escopo de progressão.
- **Critério:** recompensas e efeitos são aplicados uma vez, o jogador entende restrições/perdas e cada fluxo mantém sua semântica declarada.

## O que eu priorizaria primeiro

1. **15–16:** postura de espera/recuo e menos orbitais genéricos.
2. **09–11 e 13–14:** locomoção por espécie, giro, passada e alcance físico.
3. **06–07 e 23:** técnicas, assinatura e inimigos com identidade.
4. **20–24 e 26:** decisões reconhecíveis e confusão comportamental.
5. **29–32:** fórmula, precisão, MP e prontidão coerentes com o perfil.

Envelhecimento, perdas de criação, calendários e restrições por inteligência têm custo de experiência alto e não são necessários para que o combate já pareça mais World 1. Para o primeiro resultado perceptível, movimento e IA oferecem melhor relação entre impacto e escopo.

## Referências para executar as propostas

- [Comparação detalhada e referências locais](OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md).
- [Implementação atual inspirada em DW1](OFFLINE_BATTLE_DW1_IMPLEMENTATION.md).
- [DW1: decisões, movimentação, dano e estados](https://github.com/jype0/dw_decomp/blob/main/src/btl/battle_main.c).
- [DW1: Brains, comandos e inicialização](https://github.com/jype0/dw_decomp/blob/main/src/btl/battle_setup.c).
- [DW1: animações e root motion](https://github.com/jype0/dw_decomp/blob/main/src/main/anim.c).
- [DW1: colisão do mapa](https://github.com/jype0/dw_decomp/blob/main/src/main/map_collision.c).
- [Syd: consulta de colisões](https://github.com/SydMontague/DW1-Code/blob/master/entityCheckCollision.asm).
- [Vice: Tech Boost](https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Hack%20data/Digimon%20World%20moves%20boost.asm).
- [Vice: mudanças 2.2 documentadas](https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Hack%20data/2.2%20changes%20%28still%20in%20progress%29.asm).
- [Vice: changelog](https://github.com/Vicen04/Dw1DataAndPatches/blob/main/Changelog.txt).
- [Vice: dados de técnicas e espécies](https://docs.google.com/spreadsheets/d/1lG3aLJsLiCwcZXo5-OS18o21GngTVuyAiKA0liV_kpM/edit).

Dados históricos incompletos devem ter sua origem/versão e fallback registrados. Não se deve apresentar um parâmetro autoral como valor extraído do DW1 ou do Vice. As propostas de IA otimizada, acessibilidade mobile e navegação expandida são adaptações explicitamente indicadas acima.
