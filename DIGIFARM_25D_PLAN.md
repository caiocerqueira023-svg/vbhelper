# Digifarm 2.5D — plano de implementação

**Status:** fundação 3D em implementação local; o aparelho será validado pelo usuário.

## Decisão de produto

A imagem anexada é uma referência visual de composição: terreno isométrico, grade, sprites de Digimon e leitura de profundidade. Ela não é uma instrução de código nem será usada como mapa em tempo de execução. O mapa PNG usado pela primeira versão fica substituído por uma cena 3D carregada dos assets de `DigifarmAssets`.

Radar continua sendo a aba de encontros do World. Digifarm continua sendo a aba vizinha de World, com múltiplas fazendas, até 12 moradores por fazenda, atividades, deslocamento, colisões, conversas espontâneas e balões. Digiline continua em Mais, com Storage, Wild Ones e uma conversa de grupo por fazenda. O trabalho 3D troca o renderer e a representação espacial; não cria uma segunda identidade, um segundo chat ou uma segunda simulação.

As respostas já confirmadas para a primeira versão permanecem: a vida passa por simulação local resumida enquanto a fazenda está fechada; LLM só é acionado com a fazenda/Digiline em uso; atividades alteram convivência e estados próprios da fazenda, sem vitais/evolução inicialmente; confiança acima de 75 libera contato permanente com Wild Ones.

## O que foi encontrado no repositório e nos assets

- O domínio já tem `Farm`, `FarmResident`, mensagens, destinatários, relações, memórias, `DigifarmRepository`, `FarmSessionCoordinator`, Digiline e a separação Radar/Digifarm. Esses contratos serão preservados.
- O renderer atual ainda desenha `bird_digifarm.png` e usa coordenadas 2D de 512×736. Essa referência será removida do caminho da Digifarm quando o viewport 3D estiver ligado; `BirdFarmMap` será mantido somente como compatibilidade temporária durante a migração da simulação.
- `DigifarmAssets` contém o mapa `farm_01` em DAE/FBX/SMD, malhas de colisão/oclusão, texturas e dez famílias de instalações (Chip Factory, Dojo, Exchange, Gym, House, Lab, Meat Field, Recruitment Notice, Restaurant e Warehouse).
- O FBX binário do mapa abre no Blender 5.0. O objeto `Cube` é um proxy de autoria e deve ser excluído da cena visível; `Island`, `Sea` e `FarmColosseum_O` são a base visual. Alguns FBX das instalações são ASCII e não são aceitos pelo importador do Blender; o pipeline deve escolher DAE/SMD ou uma conversão offline equivalente, sem fazer parsing desses arquivos no APK.
- A conversão para GLB é uma etapa de build determinística. O APK recebe GLB otimizado, texturas empacotadas e um manifesto; os ZIPs de origem continuam como fonte/proveniência e não são carregados pelo app.

## Arquitetura técnica escolhida

### Runtime 3D

Usar Filament Android com glTF/GLB. `filament-android` fornece engine, cena, câmera, renderer e buffers; `gltfio-android` carrega GLB; `filament-utils-android` fornece `ModelViewer`/`UiHelper` para integrar o renderizador a `SurfaceView`. A versão fica fixada no catálogo Gradle para que o formato do asset não varie entre builds.

`Digifarm3dViewport` será um `AndroidView` que possui uma única sessão Filament por tela. O ciclo de vida cria `Engine`, `Renderer`, `View`, `Scene`, `Camera`, `AssetLoader` e `ResourceLoader` no attach, inicia o frame callback, pausa ao sair e destrói todos os recursos no detach. Nenhum renderer fica ativo em background. A cena começa com câmera oblíqua com perspectiva baixa; distância, alvo, yaw e pitch são persistidos por fazenda. O gesto de arrastar orbita/pan a câmera e a pinça altera distância, mantendo o enquadramento de cima e uma pequena parede lateral para leitura 2.5D.

### Cena, instalações e materiais

O mapa e as instalações convertidas formam uma cena GLB versionada (`digi_farm_3d`, versão 1). A primeira composição coloca as instalações em pontos seguros do platô e mantém nomes de nós no GLB para futura interação. A luz usa uma fonte direcional suave, ambiente/skybox econômico e sombras limitadas aos objetos próximos. Materiais PBR são mantidos quando presentes; texturas são reduzidas somente no pipeline, nunca na fonte.

`assets/digifarm/3d/manifest.json` descreve o GLB, escala, origem, bounds jogáveis, células bloqueadas, pontos de atividade e versão. O manifesto é validado antes de abrir a fazenda. Falha ou asset ausente produz estado de erro recuperável no card, sem apagar moradores ou histórico.

### Digimon 2D em um mundo 3D

Os sprites existentes (`idle`, `walk`, `train`, `happy`, `sleep`) continuam sendo a fonte visual do Digimon. Cada morador será um billboard voltado para a câmera no plano XZ, com textura alpha e sombra elíptica no chão. Para dar espessura, a implementação usa uma pequena extrusão: frente texturizada, verso escurecido e uma faixa lateral de poucos milímetros; a direção da câmera atualiza o billboard, mas o ponto de contato permanece no chão. A pose troca conforme `activity` e o frame da simulação.

Enquanto a malha de billboard é introduzida, a camada Compose acima do `SurfaceView` pode desenhar balões e os alvos acessíveis. Ela recebe a mesma projeção de mundo da cena e não desenha o mapa; portanto não há retorno à imagem 2D. Cada sprite tem semântica de nome, espécie, atividade e confiança para TalkBack, e a lista de moradores continua disponível para telas pequenas.

### Espaço, colisão e navegação

O mundo lógico passa a usar `WorldPoint(x, y, z)`, com `y` reservado à altura e movimento principal em XZ. Os pontos dos residentes atuais são convertidos uma vez do mapa lógico anterior para o retângulo jogável do manifesto. O componente `DigifarmNavMesh` usa uma grade de baixa resolução derivada dos objetos `FarmColosseum_C`/`FarmColosseum_O` e de bounds das instalações. Células têm altura, raio de agente e flags de passagem; A* proíbe diagonal através de cantos, reserva uma célula por morador e suaviza somente segmentos que continuam livres.

Ray picking do Filament converte toque em XZ para seleção de Digimon/instalação. O mesmo conversor é usado para seguir, aproximar-se antes de conversar e colocar um morador. Se um asset não traz malha de colisão confiável, o build exige um `collision.json` revisado; cor ou transparência da textura nunca é usada como colisão.

### Persistência e compatibilidade

`Farm.mapId` passa a `digi_farm_3d`. Os campos de câmera legados (`cameraScale`, `cameraX`, `cameraY`) não serão reinterpretados silenciosamente: uma migração acrescenta `cameraYaw`, `cameraPitch`, `cameraDistance`, `cameraTargetX` e `cameraTargetZ`, preservando os valores antigos para rollback e usando defaults oblíquos para fazendas existentes. `mapVersion` impede abrir uma fazenda com manifesto incompatível.

O banco continua garantindo uma residência por `individualId`, capacidade, transferências atômicas, snapshots de identidade, histórico e contatos permanentes. O renderer consulta `FarmResident`; não cria `UserCharacter`, não altera vitais, evolução, inventário ou transferências para relógio. O coordenador mantém uma única sessão detalhada e `summarizeReturn` continua sendo local, factual e limitado a oito horas.

### Conversas e Digiline

O orquestrador existente continua decidindo oportunidade, cooldown, destinatário e memória. Uma fala manual ou autônoma recebe IDs de destinatários (`um`, `vários` ou `ALL`), persiste uma vez e aparece no balão de cada autor. O grupo da fazenda lê `FarmMessage` paginado; Storage lê chats privados já existentes; Wild Ones lista somente relações com `contactUnlockedAt` e confiança que já passou de 75. Renderização 3D não chama LLM por frame nem bloqueia movimento quando o provedor falha.

## Pipeline de assets

1. Descompactar os ZIPs fora do APK, localizar o mapa/instalações e conferir a textura relativa.
2. Importar em Blender através do script versionado em `tools/digifarm`, eliminar câmeras, luzes, proxies de colisão e helpers de autoria da camada visual, aplicar escala e exportar GLB.
3. Montar a cena em coordenadas documentadas, gerar `manifest.json` e `collision.json`, calcular bounds/triângulos e registrar hash dos arquivos de origem.
4. Copiar somente GLBs, manifesto, colisões e texturas necessárias para `app/src/main/assets/digifarm/3d/`.
5. Validar que o GLB abre, que os materiais têm texturas, que a escala não contém NaN/inf e que todos os nós de instalação estão nomeados. O CI falha antes do APK se o manifesto não corresponder ao asset.

Os arquivos vêm de uma coleção externa de assets. A origem e a conversão ficam documentadas em `DigifarmAssets/README.md`; não redistribuir os ZIPs em outro pacote nem substituir a fonte por uma imagem gerada.

## Fases de implementação

1. **Fundação e inventário:** catálogo Filament, script de conversão, GLB do mapa, manifesto, colisão e teste de leitura. Critério: o mapa abre sem o PNG e sem o `Cube` de autoria.
2. **Viewport 2.5D:** `Digifarm3dViewport`, câmera oblíqua, pan/orbit/zoom, ciclo de vida e erro recuperável. Critério: a fazenda permanece usável ao recompor e ao pausar.
3. **Instalações e navegação:** cena composta, nós selecionáveis, `WorldPoint`, bounds e A*. Critério: caminho entre pontos seguros respeita bordas, água, instalações e agentes.
4. **Moradores:** billboards extrudados, projeção, sombra, seleção, atividades e balões. Critério: até 12 moradores podem andar/ocluir-se pela profundidade sem duplicar identidade.
5. **Integração social:** manter o orquestrador, grupo, destinatários, Digiline e resumo offline; registrar mudanças de posição/câmera. Critério: uma sessão detalhada, nenhum LLM em background e falha de provedor sem travar a cena.
6. **Migração e acabamento:** Room, Storage, Radar, acessibilidade PT/EN/JA, movimento reduzido, memória/perfil e limpeza da referência PNG. Critério: build local, testes de domínio e checklist visual prontos para teste manual do usuário.

## Validação local nesta etapa

- Compilar Kotlin e testes unitários/integração local após cada lote de mudança.
- Validar manifesto, hashes, bounds, projeção/inversa, colisão e câmera com relógio/seed falsos.
- Não executar ADB nem afirmar comportamento no aparelho nesta entrega; o teste real da Digifarm fica para o usuário.
- Medir tamanho dos GLBs e registrar qualquer limitação de importação de instalações ASCII antes de prometer todos os prédios na primeira cena.

## Saída esperada

O resultado é uma Digifarm 2.5D: terreno e instalações com profundidade e oclusão reais, câmera manipulável, Digimon sprite com espessura leve e sombra, simulação/LLM/Digiline preservados e uma rota clara para substituir ou acrescentar instalações sem tocar no banco de identidades. O plano anterior continua sendo a referência dos contratos sociais e de persistência; este documento substitui apenas a decisão de renderer e mapa visual.

## Revisão visual de 20/09/2026 — referências do usuário

### Diagnóstico confirmado no código e na origem

- A captura atual comprova que o GLB passou a carregar após remover `TextureView.setBackgroundColor`; ela NÃO comprova correção dos materiais, geometria ou integração dos moradores.
- `compose_scene.py` adicionava dez famílias de prédios com escala arbitrária 0.035 e altura 0.50. Isso não corresponde ao platô vazio da imagem 3. A cena base deve conter ilha, água, ilhota e estádios; os assets de instalações continuam disponíveis para futura colocação explícita.
- O FBX possui materiais importados que conectam a mesma textura de cor tanto à cor quanto ao normal map. Não há iluminação ambiente no ModelViewer atual, apenas uma luz solar: combinação incompatível com as texturas estilizadas do asset e responsável pelo aspecto escuro. Reconstruir materiais de cor unlit, opacos para terreno, sem normal/metallic importados.
- O pipeline anterior aplica transformações locais antes de apagar pais EMPTY, mas não preserva explicitamente `matrix_world`. A nova conversão deve guardar cada matriz mundial, remover parentesco, reaplicar a matriz e só então eliminar helpers.
- Medidas originais em Blender: Island x [-0.5824, 0.6057], y [-0.8048, 0.8015], z [-0.3080, -0.0021]; Sea x [-2.0679, 2.0415], y [-2.0945, 2.0376], z [-0.1004, -0.0082]. A ilhota fica em x [-1.1527, -0.6997]. São medidas de origem, não coordenadas finais do runtime.
- `FarmColosseum_C` e `FarmColosseum_O.001` têm praticamente os mesmos bounds e ocupam a mesma posição na fonte. Existe textura `stadium_close.png`. O sufixo C sozinho não é evidência suficiente de malha de colisão: inspecionar como variante fechada e separar os dois estádios na composição.
- `transformToUnitCube()` enquadra todos os objetos, inclusive água, e muda a escala sem aplicar a mesma transformação aos moradores. Remover essa normalização de runtime e exportar coordenadas canônicas compartilhadas.
- Os moradores atuais são imagens Compose posicionadas por `legacyToViewport`, sem projeção da câmera, teste de profundidade nem contato com o terreno. A cópia escura deslocada em pixels não é extrusão 3D.
- A navegação continua usando BirdFarmMap, com pontes e obstáculos do mapa PNG antigo. A ilha nova deve usar uma área conservadora medida no platô, sem reutilizar essas pontes.
- O ModelViewer 1.76.1 foi inspecionado diretamente na fonte oficial: cria luz direcional sem IBL, carrega recursos assincronamente, controla a câmera apenas quando recebe Manipulator e destrói Engine no listener de detach. Recursos adicionais precisam ser liberados ANTES desse listener.

### Plano de correção autorizado

1. Reconstruir o pipeline determinístico: preservar matrizes mundiais, orientar a ilha larga com estádios ao fundo, colocar topo em y=0 no runtime, recuperar texturas originais e exportar água ciano opaca. Não inserir instalações arbitrariamente.
2. Exportar GLB em escala fixa e gerar manifesto correspondente. Manter uma área central conservadora de circulação no platô, com margem das bordas. Validar coordenadas e registrar imagens de inspeção offline, sem confundi-las com captura do app.
3. Usar câmera oblíqua ortográfica, pan e pinça com limites; os botões devem controlar a mesma câmera. Enquadrar a ilha, não os bounds da água. Evitar controles cortados pelo viewport quadrado em telas baixas.
4. Criar billboards reais no Filament: planos verticais ancorados nos pés, material unlit com alpha MASK, filtro nearest, profundidade habilitada e sombra de contato no chão. A espessura é opcional na solicitação; priorizar silhueta correta e oclusão, sem retângulos opacos.
5. Reaproveitar animações existentes e atualizar texturas somente quando a pose mudar; interpolar deslocamento entre passos da simulação. Limitar recursos aos moradores presentes e liberar texturas/malhas ao sair.
6. Usar a mesma matriz da câmera para balões e seleção. Compose conserva apenas UI e balões; não desenha uma segunda cópia do Digimon. Manter lista acessível para seleção.
7. Preservar identidades, banco, Digiline, conversas e coordenação da simulação. Trocar apenas a geometria lógica necessária à ilha nova; não alterar vitais/evolução.
8. Compilar APK, testar conversão/limites espaciais, inspecionar uma renderização offline do GLB e registrar limitações. O teste visual no Android fica com o usuário, conforme preferência já informada.

### Critérios de conclusão desta revisão

Ilha completa e colorida, água visível, topo livre, estádios ao fundo; moradores apoiados no terreno e sujeitos à câmera/profundidade; balões seguindo seus autores; controles acessíveis; nenhum retorno ao PNG antigo. Build sozinho não certifica renderização no telefone.

### Execução 20/09/2026

- O preview Blender do GLB atual (`build/digifarm-debug/map-preview.png`) já mostra a ilha larga verde, água ciano e os dois estádios ao fundo: nenhuma reconstrução de asset foi necessária.
- `Digifarm3dViewport`: removido `transformToUnitCube()` (enquadrava a água e dessincronizava os moradores); câmera oblíqua fixa na ilha (alvo 0/0.05/-0.1, home 0/2.35/3.0, lente 50mm), gestos de toque encaminhados ao `ModelViewer`, `defaultPlayableBounds` alinhado ao `manifest.json` (-0.68/-0.43 a 0.68/0.43).
- Moradores viraram billboards Filament de verdade a partir de `resident.glb`: plano vertical ancorado nos pés com textura unlit alpha-MASK e filtro nearest, sombra de contato no chão, Y-billboarding por frame, textura reenviada só na troca de pose, tudo liberado antes da destruição do Engine.
- `DigifarmScreen`: removidas as cópias 2D (imagem + cópia escura deslocada + borda de seleção). O Compose mantém só balões e alvos de toque invisíveis, ambos posicionados por `projectWorld()` com a mesma câmera da cena; seleção vira escala 0.20→0.23 no billboard; lista de moradores segue como alternativa acessível.
- Validação: `:app:compileDebugKotlin` OK; `:app:testIntegrityCheckUnitTest` (BirdFarmMap, Digifarm3dMap, FarmConversation) 13 testes, 0 falhas. Renderização no aparelho fica com o usuário.
- Correção pós-teste no aparelho: o `TextureSampler` dos billboards era um `val` ansioso, criado antes de `Filament.init()` carregar o JNI, o que derrubava a cena com `UnsatisfiedLinkError ...TextureSampler.nCreat...`. Virou `by lazy` (criado só no primeiro upload de sprite, com o JNI já carregado); demais inicializadores da view já eram seguros.
- Segunda rodada (ilha OK, giro sensível, sprites brancos): `orbitSpeed` 0.35/0.25 → 0.08/0.06 e `zoomSpeed` 0.25 → 0.15. Branco confirmado como placeholder do template: `baseColorMap` existe no `libgltfio-jni.so`, então o bind passou a (1) localizar o material pela entidade `"sprite"` em vez da ordem do array, (2) usar `SAMPLEABLE or UPLOADABLE`, (3) manter o buffer vivo com referência + callback de conclusão no `PixelBufferDescriptor`, (4) registrar logs de criação/upload para diagnóstico via logcat.
- Terceira rodada (sprites OK, giro ainda sensível, tamanho, crash): `Manipulator` removido e trocado por controle orbital próprio com travas — alvo fixo na ilha, elevação 24°–63° (parte oca de baixo nunca aparece), distância 2.4–5.0, ganho 0.0016/0.0013 rad/px, pinça para zoom; botões +/- e Fit preservados. Altura do morador 0.20→0.13 (selecionado 0.23→0.15), aspecto 0.5–1.8, bolhas/alvos reajustados. Contra o crash: texturas das 6 poses subem uma única vez por morador e o tick de animação só troca o bind (`setParameter`), eliminando o churn de create/destroy de GL a cada 900ms; buffers reaproveitados por frame.
- Quarta rodada: zoom mínimo 2.4→1.2 (aproxima bem mais do Digimon); seguir morador (botões da lista ou Follow no diálogo) agora centraliza a câmera de verdade — `followId` empurrado para a view, alvo orbita com interpolação (0.08/frame) e acompanha o morador andando; Fit/Stop voltam ao centro; follow limpo se o morador sair da fazenda.
- Quinta rodada: (1) facing passa a ser resolvido da velocidade em espaço de câmera a cada frame (`mirror` por morador; flag legada só semeia o valor inicial) — antes o `facingLeft` do mapa 2D invertia ao orbitar e o Digimon "andava de costas"; (2) notificações de câmera para o Compose limitadas a ~8Hz (era por frame e causava engasgos durante o gesto); (3) swipe global de troca de aba (`tabSwipeNavigation`, 96dp) desativado na rota World — Radar/Digifarm donos de todos os gestos horizontais.
- Sexta rodada (sprites ainda invertidos): causa raiz encontrada no `DigimonWidgetProvider` ("VB Digimon sprites face left by default") — a arte olha para a ESQUERDA sem espelho, então toda a lógica anterior (herdada do overlay 2D antigo, que também estava invertido sem ninguém notar) espelhava ao contrário. Inversão corrigida nos dois lugares: inicial (`mirror = !facingLeft`) e por velocidade (espelha só ao andar para a direita da tela).

### Arquivos tocados nesta sessão (20/09/2026)

- `screens/digifarmScreen/Digifarm3dViewport.kt` (novo): viewport Filament — `Digifarm3dSceneView` (câmera orbital travada, billboards `resident.glb` com 6 poses em cache, `projectWorld`, follow com interpolação, limpeza antes do Engine), `ResidentFrames`/`ResidentPose`/`ResidentFrameImage`.
- `screens/digifarmScreen/DigifarmScreen.kt`: overlay sem cópia 2D (só balões + alvos de toque invisíveis via `projectWorld`), envio de frames por `rosterKey`, poses por tick, `followId` ligado à câmera, câmera legada só persistida.
- `digifarm/map/Digifarm3dMap.kt`: `defaultPlayableBounds` alinhado ao `manifest.json` (-0.68/-0.43 a 0.68/0.43).
- `navigation/AppNavigation.kt`: `tabSwipeNavigation` ganha `enabled`, desligado em `World.route`.
- `tools/digifarm/build_assets.py`, `build_billboard.py`, `manifest.json`, `digi_farm_3d.glb`, `resident.glb`: cena canônica já estava pronta e inalterada (preview `build/digifarm-debug/map-preview.png` confere com a referência).
- Nenhuma mudança em simulação, banco, Digiline, conversas, vitais ou evolução.
