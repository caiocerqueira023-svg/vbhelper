# Batalha offline 3D — plano de implementação

Data: 23–24/09/2026. Estado: **overhaul implementado; compilação, testes JVM e integração Android aprovados**.

O usuário autorizou a implementação, a revisão e o overhaul após relatar câmera fora da arena e combates de um segundo.

## Revisão das falhas encontradas

- A escala antiga deixava a cúpula com raio próximo de 6 e a câmera a 13,5 unidades, mostrando a superfície externa. O manifesto agora usa escala visual 0,7, posições em unidades de mundo, piso jogável de raio 8 e câmera limitada a 10–22, dentro da cúpula. O conversor também corrige a origem das UVs e a ordem das matrizes; materiais preservam a iluminação pintada nas texturas.
- HP/AP de DiM/BEM e valores online tinham unidades diferentes. `TrainingBattleStats` cria perfis de treino comuns aos dois lados, proporcionais ao estágio (0–5). Valores brutos não entram no cálculo de dano. A preparação informa essa regra e mostra o HP efetivamente utilizado.
- Carregamento assíncrono agora mantém todas as razões de pausa. O combate começa somente depois da carga GPU e da primeira renderização, seguido de contagem de três segundos. Menus sobrevivem à recriação; simulação e renderização param em segundo plano; voltar à tela aguarda a nova cena ficar pronta.
- Corrigidas as poses de ataque (frame 11 nos assets e `Sprite.spriteAttack` do banco), defesa e derrota. O ID de ataque usa o ID oficial do cartão, e os sprites se orientam para o alvo em coordenadas da câmera. Nomes se separam quando os combatentes se aproximam.
- HUD com técnicas, itens e defesa visíveis; feedback de comandos, motivos de indisponibilidade, pausa manual e ações de aproximar/afastar. A IA defensiva pode antecipar o início de um ataque. O modo de treino continua sem persistir gastos ou resultados.

## Evidências da revisão

- `:app:compileDebugKotlin`, `:app:testIntegrityCheckUnitTest`, `:app:assembleDebug`, `:app:assembleIntegrityCheck` e `:app:assembleIntegrityCheckAndroidTest` passaram. Última execução da suíte JVM do workspace: **166 testes, nenhuma falha, 3 ignorados**.
- Regressão de balanceamento: **72 sessões**, combinando 1×1, 1×2, 2×2, estágios 0/3/5 e oito seeds. Duração: **31.076–85.884 ms**, média 61.699 ms. Primeira derrota nunca antes de 12 segundos nesses cenários. Também foram testados dados brutos extremos e disparidade de estágio 0 contra dois estágio 5.
- Teste de geometria lê o GLB empacotado e verifica que câmera e área de combate cabem dentro da arena. Uma inspeção WebGL auxiliar confirmou piso/texturas/enquadramento; ela não equivale à execução Android.
- O SDK possui ADB em `C:/Users/julye/AppData/Local/Android/Sdk/platform-tools/adb.exe`. A afirmação anterior de indisponibilidade por ausência no PATH estava incorreta.
- Instalada apenas a variante separada `.integritycheck` e seu APK de instrumentação no Galaxy A34. A primeira inspeção real mostrou o Coliseu, os Digimon e combate ativo acima de 50 segundos. Ela revelou inversão de orientação, sobreposição de nomes e botões truncados, corrigidos na revisão seguinte. O teste inicial também precisou estabilizar o relógio independente para interagir com os matchers Compose.
- `OfflineBattleRuntimeTest` passou no Galaxy A34 (SM-A346M): renderer real com 1×1, 1×2 e 2×2, dano nos dois lados, pausa tática, menu preservado na recriação, relógio congelado em segundo plano e retomada, além de rotação para paisagem. Capturas e logs ficam em `build/offline-battle-review`. O host isolado abre a tela real de combate diretamente; não comprova o percurso completo de seleção pelo Storage.
- Ainda não foi feita uma sessão prolongada para medir aquecimento/FPS, nem uma regressão visual completa do online e da Digifarm. Essas limitações não são substituídas por compilação.

## 1. Escopo confirmado

- Combate em tempo real, com criaturas autônomas e intervenção tática do treinador, conforme as 45 seções da especificação fornecida.
- Primeira versão pública com **1×1, 1×2 e 2×2**. Uma prova intermediária 1×1 não encerra essa entrega.
- Arena fornecida em `app/src/main/assets/Arena/Colosseum`.
- Digimon com o mesmo tratamento 3D da Digifarm: sprites convertidos em sólidos finos, com volume lateral, contorno, poses e sombra.
- Ataques pequeno e grande com as mesmas imagens e o mesmo mapeamento usados no online.
- **Pausa tática como padrão** nos menus de técnicas e itens; modo de seleção em tempo real também configurável.
- **Treino inicialmente**: HP, energia, itens de batalha, vitórias e derrotas pertencem à sessão. Nenhuma alteração de inventário, progressão, NFC, vitais, troféus ou histórico real por resultado do treino.
- Iniciar pela entrada de batalha offline existente. Encontros por exploração/Radar ficam para uma integração futura.

## 2. Linha de base observada antes da implementação

### Entrada e combate offline

Na linha de base, `screens/BattlesScreen.kt` preparava o parceiro ativo, oponentes do Storage e desafiantes de assets. `OfflineBattleEntryPanel` abria `LocalBattleScreen`; essa rota foi substituída pela preparação e pela nova tela 3D.

`screens/LocalBattleScreen.kt` guarda HP e resultado em Compose e executa ataque + contra-ataque com corrotinas e atrasos. Não existe ali uma simulação espacial autônoma reutilizável. A substituição precisa de um novo núcleo de combate, e não apenas outra apresentação do botão Attack.

A entrada offline já aparece antes do painel de autenticação. A implementação deve preservar essa independência e garantir que autenticação ou falhas de rede do online não interrompam o treino.

### Digifarm

`screens/digifarmScreen/Digifarm3dViewport.kt` integra Compose/AndroidView ao Filament, carrega GLB, mantém entidades e projeta posições para a UI. `ResidentExtrusionGlb.kt` gera geometria a partir do alpha dos sprites. O renderer limita a rotação visual e usa espelhamento para os sólidos finos não desaparecerem de perfil.

O projeto usa diretamente Filament/gltfio/utils; não precisa de Unity, Godot ou outro motor. A simulação da fazenda e suas posições persistidas em Room não são adequadas ao loop de combate.

Há alterações locais anteriores à elaboração deste plano em `Digifarm3dViewport.kt` e `ResidentExtrusionGlb.kt`. A futura extração de componentes deve partir do conteúdo atual, preservar essas alterações e verificar regressão na fazenda.

### Arena

Foram encontrados FBX, DAE, SMD e as texturas `Battle_stadium.png` e `Battle_stadium_sphere.png`. A conversão agora gera `Colosseum.glb` e `arena.json` nessa pasta. O DAE contém duas geometrias, `Sphere001` e `Ground`, com 760 e 2.916 triângulos declarados, respectivamente, além de controllers/joints e transforms.

A extensão total do cenário não define o piso de luta: `Ground` inclui alturas diferentes e a esfera é cenário. O manifesto fornece limite lógico circular, escala e câmera. A revisão mediu o disco central do GLB e confirmou piso, texturas, quatro personagens e enquadramento no Android.

### Personagens e ataques

- `domain/characters/Sprite.kt` possui idle, caminhada, corrida, treino, felicidade, sono, ataque e esquiva. `CharacterWithSprites` não expõe todas essas poses; será necessário um adaptador de leitura para o combate.
- `battle/IndividualSpriteManager.kt` permite carregar poses dos desafiantes empacotados sem exigir um personagem no banco.
- `battle/AttackSpriteManager.kt` resolve `charaId` para `smalefilename` e `laugeFileName` no JSON de personagens. Os nomes originais dessas propriedades devem ser preservados na leitura.
- O manager já trata registros ausentes com `atk_s_02` / `atk_l_04`, e o valor `"0"` significa ausência de sprite. O novo renderer deve respeitar essa distinção.
- `CardCharacter` fornece HP/AP/BP e atributo, mas não um conjunto completo de MP, defesa, velocidade, alcance e stagger. Esses valores novos precisam de perfis de batalha explícitos.
- O ID de asset vem do identificador do cartão e `charaIndex + 1`; o ID da linha do banco não é o identificador externo do cartão. `CharacterDao.getCharacterInfo` já distingue esses valores.
- `AppMusicController` já seleciona música de batalha para `offline-battle`; deve continuar sendo o proprietário da trilha.

## 3. Experiência proposta

Fluxo: **Batalhas → Offline → formato/equipes → carregar arena → apresentação breve → combate autônomo → resultado do treino → revanche ou voltar**.

A preparação mostra os formatos 1×1, 1×2 e 2×2. O parceiro ativo preenche o primeiro slot. Em 2×2 o jogador escolhe um segundo indivíduo disponível no Storage, reutilizando a seleção existente quando compatível. Sem dois parceiros elegíveis, mostrar o motivo e permitir outro formato. Cada slot inimigo recebe um oponente válido do catálogo local ou dos desafiantes com assets existentes.

Mesmo quando um oponente é baseado num Digimon do Storage, ele é uma cópia para a sessão. Cada instância recebe `combatantId` próprio; `individualId` e `userCharacterId` são referências de origem, nunca a chave de todas as instâncias em combate. Não permitir selecionar o mesmo indivíduo em dois slots aliados.

Proposta de interface, preservando DESIGN.md:

- Arena ocupa a maior área útil; enquadramento oblíquo elevado mostra todos os participantes e distâncias.
- Topo: inimigos, HP, status e alvo selecionado. Em 1×2/2×2, tocar no inimigo ou em seu indicador define foco para o parceiro selecionado.
- Base: um ou dois painéis de parceiros, HP, energia, estado resumido e seleção do parceiro que receberá ordens.
- Comandos diretos: **Incentivar**, **Defender** e **Especial**; acesso a **Técnicas**, **Estratégia**, **Itens** e **Alvo**. Não haverá comando básico que precise ser pressionado repetidamente para a luta acontecer.
- Técnicas/itens abrem painel tático com indicação explícita de pausa. Confirmar ou fechar retoma, salvo outra razão de pausa ativa.
- Comandos informam recebido, aguardando, executando, concluído ou motivo de falha. Fora do alcance significa que o parceiro precisa se posicionar; não é necessariamente uma ordem inválida.
- Câmera automática inicial, zoom/órbita limitados e ação de recentralizar. Gestos de câmera não movem criaturas nem emitem ordens acidentalmente.
- Em tela compacta, comandos em painel inferior sem cobrir os combatentes. Em paisagem/larguras amplas, painel lateral. Respeitar fonte ampliada, áreas de toque de 48dp, insets e navegação Voltar.
- Texto/ícone acompanha cor; janela de incentivo possui sinal visível, opção de som/háptica conforme preferências. Reduzir animações cosméticas não altera a simulação.

Não há necessidade de um avatar 3D do treinador nesta entrega. O jogador ocupa esse papel pela interface; um avatar seria escopo visual adicional.

## 4. Arquitetura proposta

Separar cinco áreas, com nomes finais ajustáveis às convenções do projeto:

1. **`battle/offline/core`**: Kotlin puro, sem Compose, Filament, Room ou rede. Modelos, relógio, batalha, equipes, combate, alvo, movimento, técnicas, dano, status, ordens, incentivo e ameaça.
2. **`battle/offline/data`**: catálogo versionado de técnicas/perfis; adaptadores de Storage, sprites e assets; criação de snapshots de entrada.
3. **`battle/offline/session`**: ViewModel/controlador de sessão, ciclo de vida, configurações, comandos e publicação de snapshots/eventos.
4. **`battle/offline/render`**: arena, câmera, personagens, projéteis, impactos e projeção para HUD. Consome estado; não decide dano nem vitória.
5. **`screens/offlineBattle`**: preparação, HUD, menus e resultado. Envia intenções; não executa regras de batalha.

Extrair para um pacote neutro, como `rendering/sprite3d`, apenas a geração de sólidos/poses e utilitários comprovadamente compartilháveis. O renderer de batalha deve ser independente da tela e do repositório da Digifarm. Evitar transformar a grande classe da fazenda em uma classe universal com flags para cada modo.

Contratos centrais:

- `BattleConfig`: formato, treino, política de pausa, seed, regras e versão do catálogo.
- `Team` com coleção de `Combatant`; nenhuma regra depende de variáveis globais player/enemy.
- `Combatant`: IDs, atributos, HP/energia, posição XZ, direção lógica, colisão, alvo, estado, estratégia, técnicas, cooldowns e status.
- `SkillDefinition`: tipo de entrega (corpo a corpo/projétil/área/self), afinidade, poder, custos de energia e comando, alcances, tempos, cooldown, cancelamento, stagger, efeitos e referências VFX/SFX/pose.
- `BattleOrder`: ID, sequência, parceiro(s), alvo/técnica, prioridade, criação, validade e política de interrupção.
- `BattleSnapshot`: estado imutável para UI/render. `BattleEvent`: evento identificado por sequência para animação, som, feedback e debug.
- `BattleResult`: vitória, derrota, empate, abandono ou interrupção, acompanhado de estatísticas da sessão.

Estado e eventos se complementam: HP, cooldown e resultado sempre são recuperáveis no snapshot; não dependem de um evento transitório ter sido observado pela UI.

## 5. Regras técnicas e de combate

### Tempo, execução e pausa

Proposta inicial: simulação fixa a 30 Hz, IA a 5 Hz por combatente e renderização interpolada com alvo de 60 FPS. São parâmetros de partida, a medir no Android; não uma promessa de desempenho.

Um único escritor atualiza o estado. A UI entrega ordens a uma caixa de entrada; carregamento/decodificação ocorre fora do loop. Relógio monotônico e RNG com seed tornam os cenários reproduzíveis. Limitar recuperação de ticks após travamentos; nunca simular minutos acumulados ao voltar do segundo plano.

Pausa congela movimento, startup/active/recovery, cooldowns, status, expiração de ordens e janelas de incentivo. A interface e câmera podem continuar responsivas. Incentivo não aceita ganhos enquanto a batalha está pausada. Manter razões de pausa separadas: menu, background, diálogo de saída e sessão suspensa.

Rotação/recriação da tela mantém a sessão no ViewModel; segundo plano pausa e interrompe sons. Para a primeira versão de treino, morte do processo encerra o treino sem consequências e informa isso na volta. Não persistir posições a cada tick no Room. Retomada após morte do processo exigiria snapshot serializado e é evolução posterior.

### Máquina de estados, IA e movimento

Implementar estados explícitos para seleção, aproximação, afastamento, posicionamento, ataque com fases, defesa, stun, knockback, especial, efeito de item, espera, recuperação e derrota. Estados opcionais (fuga/confusão/provocação etc.) serão adicionados por comportamento concreto, não como enums sem funcionalidade.

Prioridade: derrota/incapacitação → reação obrigatória → ordem válida → reação defensiva → objetivo tático autônomo. Utility AI considera técnicas elegíveis e deslocamentos necessários para usá-las. Cooldown, alvo, recursos e impedimentos são filtros; estratégia, distância, perigo e eficiência alteram pontuação.

Uma técnica fora de alcance pode motivar aproximação/afastamento, mas nunca causar dano antes do alcance válido. Usar histerese de distância, tempo mínimo de decisão e compromisso com a ação para evitar oscilações. A direção lógica de mira é independente da rotação visual limitada dos sólidos.

Começar com limite jogável convexo, raio de colisão por combatente, separação local e destinos ao redor do alvo. Arquibancadas e cenário não são chão. Se a inspeção identificar obstáculos internos, fornecer grafo de navegação/A*; não adotar navmesh pesado sem necessidade. Detectar ausência de progresso, recalcular destino e expirar ordens impossíveis com motivo.

### Técnicas, projéteis e dano

Técnicas são dados, com validação de referências e números. Kit inicial proposto: ataque simples autônomo sem custo de energia, projétil pequeno, ataque forte com efeito grande e especial. Perfis variam alcance, velocidade e preferências; o catálogo poderá crescer sem condicionais por espécie no núcleo.

O mesmo sprite grande pode servir ao ataque forte e ao especial, com diferenças de duração, escala e preparação claramente configuradas. Não afirmar que a imagem contém uma animação ou identidade de técnica que não existe nos assets.

Pipeline: selecionar → mover até alcance quando necessário → validar execução → debitar custos uma vez → STARTUP → ACTIVE → RECOVERY → autonomia. Após lançamento, um projétil pode continuar existindo enquanto o autor recupera; velocidade, raio, duração máxima e política de colisão pertencem ao projétil.

Usar colisão contínua do segmento percorrido pelo projétil contra volumes lógicos para evitar atravessar alvos entre ticks. Resolver impacto uma única vez por projétil/alvo; miss, bloqueio e expiração também produzem eventos. Ataques de área recebem regras explícitas de alcance e equipes afetadas. Sem fogo amigo por padrão proposto.

A função central de dano aplica poder/ataque, defesa, vantagem de atributo/elemento quando houver dados válidos, buffs, defesa ativa e crítico. BP não será silenciosamente convertido em defesa. Atributo do dispositivo e elemento de técnica serão campos diferentes. Defesa/MP/velocidade inicialmente vêm de perfis normalizados de treino; preservar stats e semântica do relógio.

Sem energia, o combatente continua capaz de lutar com o ataque simples. Regeneração, custos e fórmulas ficam em dados de balanceamento, com limites e proteção contra valores inválidos.

### Ordens e recursos do treinador

Ordens têm ciclo QUEUED → EXECUTING → COMPLETED e terminais FAILED/CANCELLED/EXPIRED. Uma ordem de ataque mantém a responsabilidade durante a aproximação; não é substituída pela IA a cada tick.

Proposta: uma ordem em execução e fila curta por parceiro; ordens de foco/estratégia substituem a preferência anterior. Defender pode cancelar startup cancelável, mas aguarda quando a fase é irrevogável. Derrota/stun, alvo inválido, falta de recurso, cooldown e término da batalha têm motivos distintos.

Para evitar gasto duplicado com dois aliados, reservar custos de comandos aceitos e revalidar ao iniciar. Ordem cancelada/expirada antes da execução libera reserva; após início não há reembolso por erro ou interrupção, salvo regra específica declarada. UI mostra disponível versus reservado. Itens seguem a mesma disciplina de confirmação.

Proposta de primeira versão: **um saldo de Command Points do treinador compartilhado entre os parceiros**, energia individual e filas individuais. O jogador seleciona o parceiro destinatário. Essa regra é recomendação do plano, não uma decisão já confirmada pelo usuário.

Incentivo abre uma janela por evento elegível e por parceiro. Cada janela concede pontos no máximo uma vez, usa tempo de simulação e tem proteção contra spam. Eventos simultâneos ficam claramente associados ao parceiro selecionado. Especial consome energia/CP conforme catálogo e retorna à IA depois de terminar.

Defesa possui duração, multiplicador de dano e política de resistência a stagger definidos em dados. Estratégias AGGRESSIVE, BALANCED, CONSERVATIVE, DEFENSIVE, RANGED e SUPPORT são pesos; SUPPORT precisa de pelo menos uma técnica útil de cura/buff para ser oferecida.

### Vários participantes desde a primeira versão

1×2 e 2×2 exigem manutenção/revalidação de alvos, distribuição espacial, seleção do destinatário de ordens, aliados curáveis e fim por equipe inteira derrotada. Um aliado derrotado não termina o treino se o outro ainda puder lutar.

Incluir ameaça simples já nessa entrega: dano, suporte/cura e proximidade, com decaimento e histerese para não trocar alvo incessantemente. Todos os inimigos usam a mesma infraestrutura de IA e regras de recurso, com perfis configuráveis.

Resolver eventos simultâneos de maneira estável e detectar empate se ambas as equipes forem derrotadas no mesmo passo. Detectar ausência prolongada de progresso e permitir encerrar como empate técnico; o combate não pode ficar preso para sempre por geometria, alvo inacessível ou perfis puramente defensivos.

### Itens e treino

Disponibilizar um kit de itens virtuais por sessão, com cura de HP, energia e remoção de status. Quantidades são limitadas para o treino, reiniciadas na revanche e claramente identificadas como itens de treino. Não chamar `ItemDao.useItem` nessa versão.

Resultado apresenta duração, dano, defesas, incentivos, ordens e resultado das equipes. Não chama os endpoints online de resultado e não aumenta contadores persistentes. Futura economia real usará outro adaptador, transações e resultado idempotente por `battleId`, em trabalho posterior aprovado.

## 6. Etapas implementadas

### Etapa 1 — Contratos, dados de entrada e cenários de teste — implementada

Criar estrutura de pacotes, modelos de equipes e combatentes, configuração de treino, IDs de instância, relógio/RNG controláveis e leitor de snapshots de personagens. Definir perfis iniciais e fixtures 1×1, 1×2 e 2×2. Começar os testes pelas regras de elegibilidade, identidade, recursos e conclusão por equipe.

**Concluída quando:** os três formatos podem ser construídos sem UI/rede e duas instâncias da mesma espécie mantêm identidades e estados independentes.

### Etapa 2 — Preparar o Coliseu para Filament — implementada e inspecionada no Galaxy A34

Converter preferencialmente FBX/DAE por ferramenta que preserve UVs, materiais, transforms e bind pose; exportar GLB estático. Verificar necessidade de aplicar os controllers antes de remover rig. Se houver diferenças entre formatos, comparar visualmente antes de escolher a fonte. Manter originais intactos durante validação.

Gerar GLB de runtime e manifesto com eixo Y para cima, escala, chão, limite jogável, câmera, spawns para quatro participantes e regiões bloqueadas se houver. Texturas devem ser incorporadas ou ter caminhos resolvidos. Separar esfera de fundo e piso; conferir faces vistas de dentro da esfera, culling, transparência e iluminação.

**Concluída quando:** a arena abre no renderer Android, texturas e escala estão corretas e os quatro spawns ficam no piso, dentro da área navegável. Essa prova usa uma cena de desenvolvimento e ainda não troca a entrada pública offline.

### Etapa 3 — Reaproveitar os personagens 3D com regressão controlada — implementada; regressão visual em aparelho pendente

Extrair geração de sólidos/poses, cachear por conteúdo de sprite/tamanho/versão e carregar antes do combate. Adaptar sprites de banco e de assets para idle, deslocamento, ataque, defesa/esquiva, dano e derrota. Onde falta pose exclusiva, definir fallback visual explícito usando poses existentes e transformações leves.

Manter contorno, espessura, sombra de contato e legibilidade da Digifarm. Evitar gerar GLB ou decodificar PNG a cada frame. Instâncias têm transform próprio; materiais/geometria podem ser compartilhados com propriedade de memória clara.

**Concluída quando:** quatro personagens podem ser posicionados e animados no Coliseu, sem sumir de perfil, e a Digifarm mantém aparência e comportamento anteriores.

### Etapa 4 — Combate autônomo completo — implementada e coberta por testes JVM

Implementar relógio fixo, estados, alvo, movimento/colisão, ataque básico, dano e derrota. Validar inicialmente 1×1; imediatamente repetir com 1×2 e 2×2. Separar simulação de renderização desde esse ponto.

**Concluída quando:** os três formatos terminam sem qualquer input, com alcance correto, sem sobreposição persistente, sem loops de alvo morto e com vitória/derrota/empate emitidos uma vez.

### Etapa 5 — Técnicas, recursos, Utility AI e efeitos online em 3D — implementada; comparação visual em aparelho pendente

Adicionar catálogo de técnicas, energia, cooldown individual e fases STARTUP/ACTIVE/RECOVERY. Integrar o resolvedor atual de ataques pequenos/grandes a texturas de projéteis e impactos em coordenadas da arena. Acrescentar perfis de distância e estratégias à Utility AI.

**Concluída quando:** a IA se aproxima ou recua conforme técnica, não ataca fora de alcance, não gasta recursos indisponíveis e o impacto visual corresponde ao evento de colisão. Comparar pequeno/grande de um mesmo Digimon com o online; testar mapeamento ausente e valor `"0"`.

### Etapa 6 — Ordens, alvos, defesa e pausa tática — implementada e coberta por testes JVM

Implementar filas por parceiro, prioridade/interrupção, reserva/revalidação de custos, foco, mudança de estratégia e comando de técnica. Acrescentar seleção de aliado/alvo, menus e pausa configurável. Integrar ciclo de vida, Voltar e saída do treino.

**Concluída quando:** uma ordem válida assume temporariamente a ação e a autonomia retorna; comando irrevogavelmente bloqueado aguarda, expirado falha com motivo, abrir/fechar menu não avança relógios e os dois aliados obedecem apenas às próprias ordens.

### Etapa 7 — Incentivo, CP e especiais — implementada e coberta por testes JVM

Adicionar janelas de incentivo por parceiro, ganhos com consumo único, saldo/reservas do treinador, defesa e especiais com feedback. Validar concorrência de duas ordens e situações de acertos simultâneos.

**Concluída quando:** observar → incentivar → ganhar CP → defender/ordenar especial funciona nos três formatos, e pressionar incentivo repetidamente ou durante a pausa não explora o sistema.

### Etapa 8 — Status, stagger, itens e ameaça — implementada e coberta por testes JVM

Implementar infraestrutura genérica de status (duração, magnitude, origem, empilhamento/renovação), dano periódico, stun, slow, modificadores e cura. Acrescentar interrupções/knockback com retorno válido à IA, ameaça entre múltiplos participantes e kit virtual de itens. Fornecer técnicas/perfis que exercitem essas capacidades.

**Concluída quando:** suporte influencia alvo inimigo, cura respeita equipe/alvo, item não é gasto duas vezes, stun impede iniciar ações, knockback respeita arena, status termina corretamente e nenhum dado real é alterado.

### Etapa 9 — Preparação, HUD e substituição da entrada offline — implementada; combate validado em Android, percurso completo pelo Storage pendente

Integrar seleção de formato/equipes na entrada existente, carregar assets com progresso/erro/repetir e abrir a nova tela. Conectar resultado/revanche/voltar e música existente. Remover a seleção de fundo 2D somente desse fluxo e retirar a antiga tela de combate offline após a nova estar validada. Não reutilizar o motor de rodadas do online.

**Concluída quando:** acessar Offline conduz ao novo sistema nos três formatos, em modo avião e sem login; falha de asset não abre uma batalha invisível; online continua funcionando com o mesmo visual e protocolo.

### Etapa 10 — Validação e ajuste da primeira versão — JVM/build e integração Android aprovados; desempenho prolongado e regressões externas pendentes

Executar testes de domínio, compilação e APK, inspeção dos assets empacotados e testes reais no Android. Ajustar distâncias, cadência de ataques, HP, custos, câmera, legibilidade e consumo de memória com base em resultados medidos. Corrigir bloqueios e regressões antes de considerar concluída a substituição.

**Concluída quando:** todos os critérios da seção seguinte possuem evidência. Aprovação de compilação sozinha não comprova comportamento 3D ou IA.

## 7. Critérios de aceitação e testes

- Os 17 critérios do MVP fornecido são cumpridos; incluem autonomia, alcance, cooldown, ordem priorizada com retorno à IA, defesa, itens, morte e conclusão.
- **1×1, 1×2 e 2×2 são obrigatórios**, tanto sem comandos quanto com intervenção do treinador.
- Testes determinísticos: invalidar alvo durante aproximação/startup; morte durante ordem; fila expirada; stun durante ação cancelável; custo reservado por dois parceiros; falta de energia; suporte consumido uma vez; cooldown/efeitos/janela congelados em pausa; empate simultâneo; efeito de item em aliado inválido; projétil rápido sem atravessar alvo; conclusão idempotente.
- Testes espaciais: bordas, separação, afastamento bloqueado, perseguição, destinos inacessíveis e ausência de progresso. Nenhum combatente usa arquibancada/esfera como chão.
- Testes de assets: GLB válido, UVs/texturas/bind pose conferidos visualmente, poses válidas, efeito pequeno/grande correto, fallback de asset, quatro instâncias distintas.
- Testes de treino: comparar campos relevantes de inventário/progressão/histórico antes e depois de vitória, derrota, abandono e revanche; confirmar que não houve consumo nem recompensa reais. Evitar comparação cega do banco inteiro porque outros sistemas têm atividade própria.
- Testes Android: abrir/fechar menus, trocar aliado/alvo, Back, background/foreground, rotação, falha de carregamento e repetidas entradas/saídas. Verificar liberação de Engine/entidades/texturas e ausência de música duplicada.
- Testes de regressão: Digifarm (poses, direção, câmera e descarte de recursos) e batalhas online (ataques pequeno/grande, autenticação e música).
- Medir tempo de frame, duração dos ticks, pausas de GC e memória com quatro combatentes e múltiplos projéteis. Meta inicial 60 FPS; fallback visual de 30 FPS sem alterar o tempo de combate. Metas finais dependem do aparelho de referência.
- Comandos previstos: `.\gradlew.bat :app:testIntegrityCheckUnitTest`, `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:assembleDebug` e inspeção do APK. Testes em aparelho/instalação devem ser combinados conforme autorização; não são parte da etapa inicial de planejamento.

Debug desde o núcleo: estado, alvo, decisão, ordem, distâncias, técnica, pontuações candidatas, energia, CP/reservas, cooldowns, threat e motivo de rejeição. Log circular com limites; painel debug fora do HUD normal e sem registrar estado em disco a cada frame.

## 8. Evoluções posteriores, sem bloquear a primeira versão acordada

1. **Ataques combinados:** pedidos com dois parceiros, reservas atômicas, aproximação coordenada, tempo limite e cancelamento simétrico se um participante morrer ou ficar impedido. Liberar ambas as IAs em qualquer saída. 2×2 comum já existe na primeira versão; combo sincronizado é uma capacidade adicional.
2. **Personalidade, Bond, Intelligence e Obedience:** ajustar pesos, latência de ordem e ganhos com feedback; não usar LLM no tick de combate. Não transformar atributos do chat em regras de combate sem mapear explicitamente os significados.
3. **Economia e progressão:** definir recompensas, inventário real, persistência de energia/HP, regras de elegibilidade, derrota e integração com contadores do dispositivo. Requer decisões próprias; treino não antecipa essas consequências.
4. **Encontros no World e mais participantes:** iniciar sessões pelo mundo e ampliar os limites das equipes a partir dos mesmos contratos, com novo orçamento de desempenho e interface.
5. **Aprendizado de técnicas e progressão do treinador:** catálogo e perfis já preparados, mas aquisição, desbloqueios e balanceamento persistente ficam para a fase de progressão.

## 9. Recomendações ainda ajustáveis

O usuário confirmou formatos, pausa padrão e treino. As seguintes escolhas são propostas técnicas/de produto do plano, não respostas atribuídas ao usuário:

- CP compartilhado pelo treinador, energia e ordens por parceiro.
- Segundo parceiro escolhido no Storage e inimigos escolhidos por slot; parceiro ativo é o primeiro padrão.
- Estratégias iniciais, kit de técnicas e quantidades virtuais de itens.
- Encerrar treino após morte do processo; manter sessão durante recriação de tela.
- Câmera oblíqua com controle limitado; sem avatar visível do treinador.
- Valores exatos de HP normalizado, energia, alcance, custos, velocidade, duração de defesa e janelas de suporte serão balanceados em dados. Os números ilustrativos da especificação não são constantes obrigatórias.

## 10. Referências e limites desta análise

Fonte normativa: especificação de 45 seções fornecida pelo usuário e esclarecimentos desta conversa.

O [índice de FAQs de Re:Digitize Decode](https://gamefaqs.gamespot.com/3ds/705638-digimon-world-redigitize-decode/faqs) inclui materiais do PSP junto aos de Decode. O [Skill Guide de Charjake08](https://gamefaqs.gamespot.com/3ds/705638-digimon-world-redigitize-decode/faqs/78979/skill-acquisition-methods), identificado como 3DS, apresenta técnicas com poder, MP, alcance e efeitos; serve de referência conceitual para o catálogo. O acesso direto aos guias foi parcial, complementado pelo conteúdo indexado. Não foi feita auditoria integral das regras dos três jogos, nem copiadas suas fórmulas/tabelas.

Na etapa inicial de planejamento, a inspeção local abrangeu código, metadados e XML dos assets, DESIGN.md/PRODUCT.md e uma captura existente da Digifarm como referência visual histórica. Na revisão de 24/09, a arena e o combate foram executados e capturados no Galaxy A34 com a variante isolada `.integritycheck`. O APK principal foi gerado, mas não instalado. Continuam pendentes o percurso completo pelo Storage, a medição prolongada de desempenho e a regressão visual completa de Digifarm/online.
