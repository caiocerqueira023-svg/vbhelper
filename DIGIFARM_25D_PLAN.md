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

### Rodada de correção de meshes — 20/09/2026

- O enquadramento foi mantido exatamente como estava no aparelho. Não alterar câmera, alvo, distância, yaw, pitch ou composição para corrigir defeitos de malha.
- A causa do flicker na grama era `add_backer`: o exportador duplicava cada face superior alguns milímetros abaixo da original. As duas superfícies competiam no depth buffer. A função agora é um hook de compatibilidade sem criar faces; o GLB contém uma única camada de grama.
- A causa das linhas/rasgos laterais era a combinação de shells de tiles não soldados e bordas superiores sem parede. A ordem correta agora é: `clean_mesh`/weld antes de procurar furos, preencher somente loops internos pequenos, fechar o pé e criar quads apenas nas arestas de topo que continuam abertas; depois limpar novamente. Faces que já têm parede não recebem uma segunda cópia.
- A função `close_top_side_gaps` cria a parede lateral a partir da borda de topo e da cota mínima do objeto, orienta a normal para fora e deixa o `split_island` atribuir o material de cliff. Isso fecha o oco sem sobrepor a grama.
- O render offline após a rodada mostra topo contínuo, laterais sem fendas negras e sem a camada duplicada. Essa é uma verificação do asset, não uma confirmação de comportamento no aparelho.
- O manifesto foi atualizado para registrar `welded tile shells`, `continuous side-gap closure` e `no duplicate grass backer`. `Sea` e ajustes de câmera continuam fora desta rodada para respeitar a instrução de não mexer no enquadramento.

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
- Sétima rodada (modo wireframe toggável): `tools/digifarm/build_tron_textures.py` gera variantes Tron das 3 texturas (base quase-preta `#06040E` + 12% da luminância original, arestas via `FIND_EDGES` em ciano vital `#2DE1FC`, alpha preservado; guia `.impeccable/design.json`); `build_assets.py` ganha `--sea-color` (Tron usa roxo vital 0.55/0.36/0.97); `digi_farm_3d_tron.glb` (1.39MB, mesma geometria/bounds) + `manifest_tron.json` nos assets; preview `vbhelper-tron-out/preview.png` confere (ilha/estádios em wireframe ciano, água roxa). Toggle em Settings → Appearance (`settings_tron_title/desc` em EN/PT/JA, descrição em ciano como as demais), persistido em DataStore (`SpeciesSettingsRepository.digifarmTronWireframe`, default off).
- Oitava rodada (crash ao ativar): troca de cena passa a hot-swap no Engine vivo (`switchAsset` + `update` do `AndroidView`, billboards preservados, geração descarta loads obsoletos) em vez de recriar a GL view — teardown+rebuild em sequência era o vetor do crash.
- Nona rodada (modo normal como a referência): `tools/digifarm/build_reference_textures.py` gera `island_top.png` (512, verde vivo quadriculado + manchas tonais + areia esparsa) e `island_side.png` (256, borda clara no topo + gradiente areia com estratos); `build_assets.py` divide ilha/ilhotas em 2 materiais por normal (topo planar, lateral cilíndrica, UVs refeitos) e o mar vira teal da referência (0.30/0.65/0.64). Rebuild dos dois GLBs (mesma geometria/bounds; 5 materiais compartilhados, 4 imagens); previews `vbhelper-classic-out` e `vbhelper-tron-out2` conferem. `farm_base_tex01.png` não é mais usado. Nenhuma mudança em Kotlin necessária.
- Décima rodada (fundo oco): `cap_bottom` no `build_assets.py` fecha a ilha e as ilhotas com quilha de rocha — leque da borda aberta (ordenada por ângulo, robusto a bordas ramificadas) até centroide afundado (0.5× raio, 0.12–0.45), normais para baixo/fora, herdando material/UV do penhasco. Primeira tentativa com ordenação topológica deu serra; ordenação angular resolveu. Verificado com render por baixo (cone fechado limpo) e por cima (inalterado). Rebuild classic + tron instalados. Bounds jogáveis intactos (quilha só para baixo de y=0).
- Décima-primeira rodada (sem água, fade no escuro): `Sea` removido do `keep` (fundo agora é o void preto do app); quilha esticada (0.6× raio, 0.25–0.70); `island_side.png` redesenhado com fade longo (borda → areia → rocha escura → quase-preto) e `island_keel.png` (64×256, rocha→preto) com material próprio por normal (nz<-0.4); `add_backer` duplica faces de cima 0.025 abaixo em verde-escuro (`--backer-color`) pois o topo é patchwork com furos (1387 faces backer só na ilha). Descoberta crítica no caminho: o exportador glTF inverte V (FBX importa com flip e exporta com flip; sprites, que nunca passam pelo Blender, provam que o Filament usa v=0=file-top) — UVs autorados agora pré-invertidos; verificado por dump binário do GLB (ponta v=1.0, topo v=0.0). Sem mudanças em Kotlin.
- Décima-segunda rodada (quilha reta + flicker): cone trocado por extrusão reta (`build_skirt`: só a borda baixa, quads de seção constante + tampa plana; faces novas marcadas e resolvidas no split); `fill_top_holes` (flood-fill de furos altos + leque plano, 3184 faces na ilha) pois as ilhotas tinham falhas grandes sob os estádios; estádios elevados +0.035 (bases coplanares ~0.0001 com o topo causavam o flicker). Perfil de cor medido no render: fade liso até (9,7,14). Sem mudanças em Kotlin.
- Décima-terceira rodada (linha brilhante na captura): dump binário do GLB instalado revelou 1311 tris degenerados de área zero (duplicatas da borda costuradas pela saia) — em alguns GPUs rasterizam como filetes, incluindo um cortando a tela; `clean_mesh` (remove_doubles + deleta faces mortas) zerou os degenerados (restam 43 micro-slivers válidos no fundo da quilha). Bônus: escala da sombra corrigida para `(worldW, worldH, worldW)` (estava fina e funda) e piso da elevação 24°→30° contra ângulos extremos.
- Décima-quarta rodada (sem extensão, fade original, yaw travado): `build_skirt` removida por completo (incluía um comentário `//` inválido em Python); fade redesenhado na altura original (`island_side` com preto no pé, v0→1 no penhasco); yaw travado em ±0.65 rad (~±37°) no controle orbital — só o lado modelado aparece. Mantidos fill com guarda de vão, backer, lift dos estádios, `clean_mesh` e mar removido.
- Décima-quinta rodada (linha verde): dump do GLB achou faces verticais com material do topo (vão até 1.75, inclusive) — leques do fill sobre loops em C + undersides originais; topo agora single-sided (`strip_doublesided` no pipeline, winding 1388↑ vs 127↓ conferido) para retalhos de baixo/frestas não renderizarem. Verificação no aparelho pendente com o usuário.
- Décima-sexta rodada (cena não carrega): `strip_doublesided` escrevia o length no offset 4 (campo VERSION) em vez do offset 8 (LENGTH) — Filament rejeitava o GLB inteiro; corrigido, rebuild dos dois modos e validação de header (magic/version==2/length/overflow) agora é porta de instalação. Sem mudanças em Kotlin.
- Décima-sétima rodada (remesh revertido): voxel remesh destruiu a ilha (4319→120 vértices — flood fill vaza pelos furos do patchwork); pipeline voltou ao patchwork reparado verificado (fill/backer/cap/clean/split/lift/strip), que renderiza pixel-perfect nos previews. Sem mudanças em Kotlin.

### Arquivos tocados nesta sessão (20/09/2026)

- `screens/digifarmScreen/Digifarm3dViewport.kt` (novo): viewport Filament — `Digifarm3dSceneView` (câmera orbital travada, billboards `resident.glb` com 6 poses em cache, `projectWorld`, follow com interpolação, limpeza antes do Engine), `ResidentFrames`/`ResidentPose`/`ResidentFrameImage`.
- `screens/digifarmScreen/DigifarmScreen.kt`: overlay sem cópia 2D (só balões + alvos de toque invisíveis via `projectWorld`), envio de frames por `rosterKey`, poses por tick, `followId` ligado à câmera, câmera legada só persistida.
- `digifarm/map/Digifarm3dMap.kt`: `defaultPlayableBounds` alinhado ao `manifest.json` (-0.68/-0.43 a 0.68/0.43).
- `navigation/AppNavigation.kt`: `tabSwipeNavigation` ganha `enabled`, desligado em `World.route`.
- `tools/digifarm/build_assets.py`, `build_billboard.py`, `build_tron_textures.py`, `manifest.json`, `manifest_tron.json`, `digi_farm_3d.glb`, `digi_farm_3d_tron.glb`: cena canônica já estava pronta e inalterada (preview `build/digifarm-debug/map-preview.png` confere com a referência); variante Tron mesma geometria/bounds (preview `vbhelper-tron-out/preview.png`).
- `source/SpeciesSettingsRepository.kt` (+ controller + `SettingsScreen` + strings EN/PT/JA): flag `digifarmTronWireframe`.
- Nenhuma mudança em simulação, banco, conversas, vitais ou evolução; Digiline recebeu posteriormente apenas refinamentos de interface descritos no registro complementar abaixo.

## Registro complementar de implementação — 20/09/2026

Esta seção atualiza o plano 2.5D com o acabamento de UI e as integrações realizadas depois da execução do renderer. As alterações preservam a cena, as identidades, o banco, a simulação e o orquestrador social.

### Digifarm e World

- World/Digifarm passaram a usar subabas com `PrimaryTabRow` contínuo, sem divisor rígido e com indicador inferior ciano alinhado ao padrão da aba Itens.
- Os `Scaffold`s aninhados do Radar, Digifarm e grupo passaram a consumir apenas `WindowInsets.statusBars`, removendo o inset inferior duplicado da navegação principal.
- A viewport 3D da Digifarm foi mantida quadrada e visualmente equivalente ao Radar, com borda, recorte e fundo de cena consistentes.
- A camada escura adicional atrás dos cards foi removida para manter o fundo animado visível.
- Os botões visíveis de zoom e `Fit` foram retirados da interface. O controle por gesto de pan/pinça permanece na viewport e o estado da câmera continua sendo persistido.
- O `TextureView` da cena agora chama `requestDisallowInterceptTouchEvent(true)` ao iniciar toque dentro da viewport e libera a interceptação ao terminar/cancelar, evitando que o scroll externo roube orbit/pinça.
- A lista de moradores foi movida para baixo da viewport e recebeu altura explícita, corrigindo o estado em que a `LazyRow` ficava invisível e eliminando o espaço vazio associado.

Arquivos de UI envolvidos: `screens/worldScreen/WorldHubScreen.kt`, `screens/worldScreen/WorldScreen.kt`, `screens/digifarmScreen/DigifarmScreen.kt` e `screens/digifarmScreen/Digifarm3dViewport.kt`.

### Seletor baseado no Storage

- Criado `screens/storageScreen/StorageCharacterPicker.kt` para reutilizar a linguagem do Storage em ações que precisam escolher um Digimon.
- O seletor oferece busca, filtros `Todos/Favoritos/Ativo/VB/BE`, ordenação por recentes/nome/vitais/estágio, contador, grid quadrado, favoritos, estados de carregamento e estados vazios.
- `ChooseCharacterScreen` passou a usar o seletor completo ao aplicar item, mantendo a lista compatível com o tipo do item (BE, VB ou missão especial).
- Adicionar residente à Digifarm passou a abrir o seletor em tela cheia; Digimons já residentes são excluídos e a seleção fica protegida contra toques duplicados durante a gravação.
- Mensagens de estado vazio para item foram adicionadas em inglês, português do Brasil e japonês.

### Digiline e grupo

- As abas Storage/Wild Ones/Digifarm foram movidas para o topo junto do `TopBanner` e receberam o mesmo tratamento suave de Itens/World: fundo contínuo, sem divider padrão e indicador ciano desenhado no `Tab` ativo.
- `DigilineScreen` deixou de reaplicar o inset inferior e suas listas agora ocupam todo o espaço útil.
- Linhas de conversa receberam painéis angulares roxos, avatar/placeholder de tamanho fixo, preview truncado, cores secundárias consistentes e badge de não lidas em ciano.
- `FarmGroupScreen` perdeu o padding inferior extra; chips de moradores ficaram centralizados, bolhas ganharam superfícies angulares/bordas semânticas e o composer passou a ter uma linha e feedback de envio.
- Nenhuma regra de destinatários, leitura, geração autônoma, persistência ou envio foi alterada; essas mudanças são de composição, insets e feedback visual.

Arquivos: `screens/digilineScreen/DigilineScreen.kt` e `screens/digilineScreen/FarmGroupScreen.kt`.

### Ícone do aplicativo e localização

- A imagem fornecida pelo usuário foi preservada sem transformação em `app/src/main/res/mipmap-nodpi/vbhelper_app_icon.png`.
- `app/src/main/AndroidManifest.xml` agora aponta `android:icon` e `android:roundIcon` para `@mipmap/vbhelper_app_icon`, substituindo o launcher adaptativo anterior para usar a arte exatamente como enviada.
- O hash SHA-256 da cópia foi conferido contra o arquivo original e permaneceu idêntico.

### Validação desta atualização

- `./gradlew.bat :app:compileDebugKotlin` e `./gradlew.bat :app:assembleDebug` concluídos com sucesso.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check` não encontrou erro de whitespace; os avisos reportados são somente a normalização LF/CRLF do checkout Windows.
- A validação visual final destas alterações foi feita por inspeção de código e build, sem nova rodada de ADB, conforme orientação do usuário. As verificações anteriores da Digifarm no aparelho já haviam confirmado viewport quadrado, lista de moradores e bloqueio de scroll durante gesto dentro da cena.

## Correção da lateral oca e do fade — 20/09/2026

- A abertura não era uma peça solta isolada. Depois da solda, a origem mostra uma única borda aberta em forma de loop: um arco no topo da ilha, outro arco no pé do penhasco e duas escadas verticais de transição em cada extremidade. A tentativa anterior de projetar cada ponto para uma cota global criava a saia desconectada que aparecia sob o modelo.
- `close_top_side_gaps()` agora encontra esse loop, separa os dois arcos pela altura, preserva todos os vértices de transição e constrói uma faixa de triângulos/quads entre as bordas existentes. As faces usam as mesmas arestas da origem; não são criados centros abaixo da ilha nem uma segunda camada de grama. A verificação offline termina com zero bordas abertas na ilha principal.
- Os conectores recebem o material de cliff por um marcador temporário de face válido durante o ciclo BMesh → Mesh. Isso impede que um triângulo inclinado seja classificado como topo e mantém o UV cilíndrico de altura, com o mesmo alpha da lateral original.
- O fade foi retirado do RGB preto. `island_side.png` e `island_keel.png` são RGBA com alpha decrescendo até zero; o pós-processamento do GLB também copia essas imagens para `baseColorTexture` e define `alphaMode: BLEND`, porque o exportador de emissão sozinho só escrevia `emissiveTexture`. O Filament passa a receber transparência real no penhasco e na quilha.
- O enquadramento da câmera, o alvo, o zoom e a composição da viewport não foram alterados nesta rodada. O preview final está em `build/digifarm-debug/final-connected-preview.png`; ele é apenas a verificação offline da malha/exportação.
- A mesma geometria e o mesmo pós-processamento de alpha foram regenerados em `digi_farm_3d_tron.glb`/`manifest_tron.json`, para que o modo wireframe não reintroduza a lateral antiga quando for ativado.
- `:app:compileDebugKotlin` e `:app:assembleDebug` concluíram com sucesso. O APK final está em `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `D1FD1400F6EB75CB1E39DF8F347A17298DBC5E97FC66365B1FE9204804488286`). O GLB dentro do APK passou por checagem de header, tamanho e `alphaMode: BLEND`; não foi feita nova instalação via ADB.
