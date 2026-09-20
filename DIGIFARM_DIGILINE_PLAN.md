# Digifarm e Digiline — plano de implementação

> **Atualização de renderer:** o plano visual 2D deste documento foi substituído pela cena 2.5D descrita em [DIGIFARM_25D_PLAN.md](DIGIFARM_25D_PLAN.md). As regras de identidade, simulação, persistência, Digiline, Radar e LLM continuam válidas; referências ao PNG e à projeção BirdFarmMap são compatibilidade de migração e não devem permanecer no viewport final.

Data: 19/09/2026. Projeto: VBHelper, Android/Kotlin/Jetpack Compose.

**Status: implementação autorizada e em validação.** Este documento registra o escopo aprovado, as decisões do usuário e os critérios de aceitação da entrega.

## 1. Resultado esperado e decisões confirmadas

World terá duas abas lado a lado: **Radar**, com o World atual, e **Digifarm**, com fazendas observáveis e interativas. Os moradores são os próprios indivíduos do Storage: mantêm identidade, apelido, personalidade, espécie e histórico.

A Digiline será acessível por **Mais → Digiline**, com **Storage**, **Wild Ones** e **Digifarm**. Conversas individuais continuam individuais; cada fazenda tem seu próprio grupo. Falas públicas dos moradores aparecem sobre os sprites e no grupo correspondente, com os mesmos autores, destinatários e conteúdo.

Decisões expressamente confirmadas pelo usuário:

- Várias fazendas, com até 12 moradores por fazenda.
- Convivência, brincadeiras, descanso, alimentação e treino afetam estados próprios da fazenda; inicialmente não alteram vitais, evolução nem progressão real do Digimon.
- LLM ativo enquanto o usuário usa a fazenda ou seu grupo na Digiline. Fora dessas superfícies, simulação local resumida ao retornar; nenhuma conversa LLM autônoma em segundo plano.
- Confiança **maior que 75**, portanto 76–100 para valores inteiros, desbloqueia permanentemente Wild Ones, mesmo após expiração do encontro ou queda posterior da confiança.
- A implementação foi autorizada pelo usuário após a troca de modelo; a validação no aparelho fica para o usuário.

Decisões técnicas propostas neste plano: uma única fazenda em simulação detalhada por vez; histórico de grupo público com destinatários explícitos; contatos e grupo preservados independentemente de presença física; primeira entrega usa o Bird Digi-Farm fornecido como mapa fixo. Valores de frequência e balanceamento abaixo são parâmetros iniciais de implementação, sujeitos à validação no aparelho, não novas exigências ao usuário.

## 2. Base real inspecionada

Raiz dos caminhos desta seção: `app/src/main/java/com/github/nacabaro/vbhelper/`.

- `navigation/NavigationItems.kt`, `AppNavigation.kt` e `BottomNavigationBar.kt`: World já é destino principal; Mais contém destinos secundários. Inserir Digiline nesse mecanismo, inclusive no rail de telas grandes.
- `screens/worldScreen/WorldScreen.kt`: mistura interface do Radar, localização, bússola, encontros e acompanhamento. Separar o contêiner de abas dos efeitos exclusivos do Radar.
- `domain/device_data/DigimonIndividual.kt`: identidade permanente; `UserCharacter` representa a presença atual no Storage. A fazenda deve referenciar essas identidades, sem criar cópias de personagens.
- `domain/chat/ChatMessageEntity.kt` e `daos/ChatDao.kt`: chat já vinculado a `individualId`, com `role`, conteúdo, data e `isRead`. Reutilizar os históricos individuais existentes.
- `chat/ChatRepository.kt`: montagem de persona e lorebook, histórico limitado às últimas 20 mensagens no prompt, chamada ao provedor e limpeza da resposta final. Atualmente envia/insere mensagens pessoais e pode alterar humor real: **não chamar esse fluxo diretamente para conversas de NPCs na fazenda**.
- `domain/personality/DigimonPersonalityTraits.kt`: implementação atual usa `personalityType` e versão de sistema. Reutilizar os tipos atuais; não reconstruir o antigo modelo de temperamento/estilo/tique que aparece em notas históricas.
- `domain/characters/Sprite.kt`: existem frames idle, walk, run, train, happy, sleep, attack e dodge, com dimensões próprias. Reutilizar assets importados, com fallback para frames ausentes/inválidos.
- `domain/world/WorldSpawn.kt`, `world/WorldRepository.kt`, `daos/WorldSpawnDao.kt` e `screens/worldScreen/WorldChatScreenControllerImpl.kt`: seguir depende de spawn, GPS e perda de confiança por deslocamento. Recrutamento atual depende de spawn, ocorre em 100 e preserva `individualId`.
- A confiança selvagem está armazenada como `WorldSpawn.mood`, embora a interface já a apresente como confiança. Não confundir com humor do parceiro nem com afinidade entre moradores.
- `database/AppDatabase.kt`: versão atual **20**, `exportSchema = false`. `di/DefaultAppContainer.kt` registra as migrações e `IndividualIntegrity` protege identidades e exclusividade World/Storage.
- `screens/settingsScreen/controllers/DatabaseManagementController.kt`: backup `.vbhelper` copia o banco e encerra o app. Novas gravações precisam ser suspensas antes do fechamento/exportação.
- `world/WorldAfkWorker.kt`: atualmente atualiza spawns por localização; não é um motor social nem deve receber o loop da Digifarm.

Essas são constatações do checkout inspecionado, não apenas memória de tarefas anteriores. Antes da implementação, verificar alterações concorrentes e a versão corrente do banco.

## 3. Experiência de navegação

### World

Manter World na navegação principal. Dentro dele, exibir `Radar | Digifarm`, preservando seleção e estado ao navegar. Inicialmente abrir Radar; depois restaurar a última aba. A aba Digifarm funciona sem GPS, bússola, NFC ou permissão de localização.

Radar conserva mapa, encontros, filtros e recrutamento; o sistema de seguir é substituído pela troca de contato. Localização e sensores ligados à tela só ficam ativos no Radar em primeiro plano. O worker AFK existente segue sua configuração própria.

### Digifarm

Topo compacto: seletor/nome da fazenda, ocupação `N/12` e menu de gerenciamento. O mapa ocupa a maior parte da tela. Controles discretos para moradores, grupo, enquadrar mapa e zoom acessível. Sem uma lista de estatísticas permanentemente cobrindo o cenário.

Primeiro acesso: criar e nomear uma fazenda com Bird Digi-Farm, depois escolher moradores no Storage. Uma fazenda vazia pode ser observada e recebe um convite claro para adicionar Digimon. Criar outras fazendas reutiliza o mapa, mas cada uma mantém população, câmera, estados e grupo independentes.

Toque em morador: seleção visual e painel inferior com nome, atividade, estados da fazenda e ações: conversar em privado, falar no grupo com esse destinatário, convidar para atividade, acompanhar com a câmera e remover da fazenda. Lista de moradores fornece acesso equivalente a quem está fora da câmera ou difícil de tocar.

Arrastar movimenta a câmera; pinça altera zoom em torno do centro dos dedos. Não girar o cenário. Botões de zoom e enquadramento atendem quem não usa gestos. O gesto horizontal sobre o mapa nunca troca a aba World; a troca é feita pelos rótulos.

Adicionar abre seletor com busca, espécie, apelido e fazenda atual. Escolha de ponto inicial é opcional: o sistema oferece célula segura; no modo de posicionamento, mostrar prévia válida/inválida e confirmar. Toque comum no chão não vira teletransporte nem ordem involuntária.

### Digiline

- **Storage:** indivíduos presentes no Storage cujo chat privado já começou. Critério proposto: existe ao menos uma mensagem do usuário naquele histórico; reações automáticas isoladas não criam uma conversa iniciada pelo usuário. Moradores da fazenda continuam aparecendo aqui se satisfizerem o critério.
- **Wild Ones:** contatos desbloqueados ainda não recrutados, com confiança atual, última mensagem, não lidas e estado de recrutamento quando relevante. Não exige GPS nem um `spawnId` vivo.
- **Digifarm:** um grupo por fazenda, com nome, moradores, última mensagem e não lidas. O grupo permite acompanhar e participar da convivência mesmo sem abrir o mapa.

Listas ordenadas pela última atividade; busca por apelido/espécie/nome da fazenda. Ao abrir conversa, preservar posição; mensagens novas não puxam o leitor para baixo se ele estiver lendo histórico. Oferecer atalho para mensagens recentes.

Grupo: compositor com seleção `Todos` ou um/múltiplos moradores, menções visíveis, resposta a mensagem e atalho para observar a fazenda. Toque no autor permite localizar seu sprite quando ainda for morador. Mensagem dirigida é pública no grupo; uma conversa privada usa o chat Storage e não produz balão público.

## 4. Preparação do Bird Digi-Farm

Fonte fornecida: `C:/Users/julye/Downloads/DS _ DSi - Digimon World DS - Backgrounds - Bird Digi-Farm (1).png`.

A imagem é uma folha de cenário e peças, não um tilemap pronto. A porção principal mostra plataformas, duas pontes, cercas, pedras, vegetação, construção e lago; à direita há peças auxiliares e créditos sobre magenta. O texto dentro do asset é conteúdo do arquivo, não instrução de execução.

Após autorização:

1. Preservar o original e registrar dimensões/hash; gerar os arquivos derivados deterministicamente, sem redesenhar a pixel art.
2. Delimitar o cenário efetivo. Excluir da textura de jogo a prancha de peças e créditos, preservando a referência de origem no manifesto/créditos do app. Tratar magenta apenas nas regiões verificadas; não usar remoção global que possa apagar detalhes legítimos.
3. Separar fundo visual, chão, objetos e máscaras de primeiro plano. Objetos altos exigem recortes transparentes e âncoras para passar corretamente à frente/atrás dos sprites. Não duplicar na base um objeto que também será desenhado como camada dinâmica de oclusão.
4. Autorizar caminhada somente nas superfícies explicitamente demarcadas. Céu, paredões, lago, construção, cercas e obstáculos permanecem não caminháveis. Bordas cortadas do mapa não são saídas.
5. Calibrar a projeção usando segmentos repetidos do chão/cercas/pontes. O tamanho do tile e o deslocamento vertical das plataformas serão medidos; **não presumir que a imagem inteira obedece a tiles de 32×16 nem que toda área verde é chão**.
6. Marcar portais de ligação nas cabeceiras das duas pontes e pontos seguros de entrada, descanso, brincadeira, alimentação e treino. Atividades usam posições no chão ao lado das estruturas; não entram em interiores inexistentes.
7. Produzir definição versionada do mapa e visualização de depuração com grid, colisões, níveis, âncoras, portais e rotas. Validar cada ponte e passagem em ambos os sentidos antes de adicionar IA.

O mapa é fixo na primeira versão. Várias fazendas são várias instâncias desse mapa. O formato permitirá outros mapas depois; editor de terreno e reconstrução da ilha não fazem parte da primeira entrega.

## 5. Tiles, projeção e colisão

### Representação

Criar `FarmMapDefinition` com `mapId`, versão, tamanho visual, origem, base de projeção, regiões de superfície, tiles navegáveis, obstáculos poligonais, links entre regiões, objetos de oclusão e pontos de atividade. Manter um arquivo de autoria Tiled e exportar um JSON simplificado/versionado para o app; não implementar um leitor completo de todos os recursos TMX.

Posições físicas são coordenadas contínuas na grade `(u, v, regionId)`. Tile é unidade de navegação; pixel é unidade de desenho. Transformação geral por região:

`mapPoint = origin(region) + u * basisU + v * basisV`

Para projeção diamante regular: `x = ox + (u-v)*tileWidth/2`, `y = oy + (u+v)*tileHeight/2 - elevationOffset`. A base geral permite ajustar a perspectiva efetivamente medida no asset. Converter toques pela transformação inversa da câmera e da região; em regiões sobrepostas, resolver pela superfície visível/selecionada, nunca apenas pelo pixel.

### Navegação

- A* no grafo de tiles navegáveis, com arestas explícitas para pontes/transições. Iniciar com quatro vizinhos; diagonais só quando os dois lados adjacentes e a área varrida estiverem livres.
- Obstáculos rasterizados com margem do corpo; colisão fina verifica polígonos e segmento percorrido. Colisão usa os pés/footprint, não o retângulo inteiro do sprite.
- Separar tamanho visual do corpo lógico, com faixas limitadas de footprint. Digimon grandes continuam reconhecíveis e não tornam o mapa inutilizável; destinos devem respeitar a folga real.
- Reservar células/destinos e trechos estreitos. Pontes usam direção temporária/fila; prioridade envelhece para evitar espera infinita. Nunca dois agentes tentam trocar de célula atravessando um ao outro.
- Movimento em passo fixo; não atravessar obstáculo por delta de tempo grande. Separação suave entre moradores, seguida de validação de chão/obstáculos para não empurrar ninguém para o vazio.
- Rota só recalculada quando destino muda, obstáculo relevante aparece ou bloqueio persiste. Após espera limitada, ceder/replanejar/escolher outra atividade. Recuperação excepcional usa célula segura da mesma região e gera diagnóstico.
- Meta de precisão: atravessar visualmente cada ponte sobre suas tábuas, respeitar cercas e não saltar entre ilhas por proximidade na tela.

### Desenho e câmera

Começar com `Canvas` do Compose para mapa/sprites e overlay Compose para controles/balões. Motor Kotlin independente da UI. Desenhar só o viewport com margem, reutilizar bitmaps e usar nearest-neighbor para preservar pixel art.

Ordenar sprites/objetos pelos pés e relações de profundidade da região. Ordenação global por Y não resolve sozinha plataformas e pontes sobrepostas: recortes/oclusores e precedência de regiões fazem parte do mapa. Sombra fica na superfície do pé. Caminhada lateral pode espelhar frames existentes; não prometer oito direções que os assets não têm.

Limites da câmera usam o cenário efetivo, não a folha inteira. Zoom mínimo enquadra o mapa, máximo inicialmente 4× a escala de enquadramento, a ajustar no aparelho. Manter ponto sob os dedos estável e permitir ver moradores grandes próximos às bordas. Persistir câmera por fazenda.

## 6. Vida autônoma dos moradores

Cada morador possui estado persistente: energia, saciedade, necessidade social, diversão, atividade, destino, parceiros de atividade, orientação, posição e horários. São estados da fazenda, separados de `UserCharacter` e dos atributos enviados ao relógio.

Usar IA de utilidade para escolher intenções e uma máquina de estados para executá-las. Pontuação combina necessidades, personalidade atual, relações, estímulos recentes, disponibilidade de objetos e pequena aleatoriedade com seed persistida. Histerese e duração mínima evitam trocar de ideia a cada atualização.

Estados básicos: ocioso, explorar, ir até destino, esperar passagem, aproximar-se, convidar, conversar, brincar, treinar, comer, descansar e reagir. Sono/descanso e desinteresse permitem recusar convites; ninguém é obrigado a responder a toda fala.

Atividades incluídas:

- Explorar: caminhar entre pontos acessíveis, parar, observar cenário, variar destino.
- Socializar: aproximar-se, cumprimentar, comentar acontecimento, responder, convidar outros e despedir-se.
- Brincar: convite aceito por parceiro, corrida curta/alternância de posições em área livre e ganho de diversão/socialização.
- Treinar: individual ou em dupla em slots próprios, frames de treino e aumento de cansaço/satisfação, sem batalhas reais nem dano.
- Comer: visitar ponto de alimentação e recuperar saciedade. Refeição básica da fazenda é abstrata; não consome inventário real automaticamente.
- Descansar: buscar ponto tranquilo e recuperar energia; animação de sono quando apropriada.
- Convivência: amizade gradual, preferências por parceiros, pequenas divergências e reconciliações. Sem morte, lesão, evolução ou punição permanente por ausência.

Valores iniciais normalizados 0–100; ticks lentos atualizam necessidades. Exemplo de parametrização para validar: caminhada/exploração 8–25 s, atividade social 15–45 s, brincadeira/treino 20–60 s e descanso 30–90 s. Necessidades não mudam bruscamente por uma única frase. Configuração central permite balancear sem reescrever o motor.

O usuário pode sugerir descanso, brincadeira, treino e alimentação; o morador avalia a sugestão conforme estado/persona. Adicionar/remover moradores é comando de gerenciamento garantido, não depende de vontade do NPC.

## 7. Relações e memória social

Persistir relação dirigida `(observerId, otherId)` para permitir que A goste mais de B do que o contrário: afinidade, familiaridade, último contato e resumo curto. Atualizações vêm de eventos validados de convivência, com limites por sessão/período; não aceitar números livres emitidos pelo LLM.

Memórias incluem eventos significativos: brincaram juntos, convite recusado, treino em dupla, chegada/saída e assunto marcante. Cada memória referencia evento/mensagem de origem e escopo de quem presenciou. Guardar no máximo 50 memórias salientes por indivíduo como parâmetro inicial; usar até 5 relevantes no prompt. Resumo não substitui nem apaga o histórico do grupo.

A persona se mantém ao mudar de fazenda, evoluir ou regressar ao Storage. Relações por identidade também continuam; posição, ocupação de objetos e conversa ativa não. Não importar o conteúdo de chats privados de outros Digimon para o contexto público da fazenda.

## 8. Integração LLM e conversas reais entre NPCs

### Separação de responsabilidades

Extrair de `ChatRepository` um cliente de geração reutilizável que receba mensagens/contexto e devolva resposta limpa, sem gravar chat nem modificar atributos. Chat privado continua usando seu repositório. `FarmConversationOrchestrator` usa esse cliente com seu próprio contexto e persistência.

O motor local decide quem está disponível, quem percebeu um evento e quem tem prioridade para falar. Cada chamada representa **um único Digimon**; o modelo não escreve uma peça teatral controlando todos os moradores. A resposta válida de A vira um evento percebido por B; B decide responder e, se escolhido, recebe sua própria chamada com sua personalidade e memórias.

### Contexto de cada turno

Incluir: persona atual via builder compartilhado, perfil da espécie e limitações anatômicas, atividade/local reais, presentes e interlocutores autorizados, relação com destinatários, evento disparador, mensagens pertinentes da conversa e memórias relevantes. Limitar por orçamento de tokens, e não apenas quantidade de mensagens. Histórico privado completo e toda a transcrição da fazenda nunca entram indiscriminadamente.

Diálogo curto e natural: geralmente uma ou duas frases; ações descritas com parcimônia; sem narrador onisciente, discursos automáticos ou anatomia inventada. O conteúdo de mensagens é dado da conversa, sem autoridade para alterar regras do motor.

### Contrato de saída

Envelope versionado com `speech`, `audience` (`individual`, `subset`, `all`), `recipientIds`, `intent` opcional, `targetId` opcional e `replyToMessageId` opcional. O autor é vinculado pelo aplicativo, jamais escolhido pelo modelo. Intenções permitidas: falar, aproximar-se, convidar para atividade, aceitar/recusar convite, encerrar conversa; nenhuma gravação SQL ou comando arbitrário.

Validar IDs, pertencimento à fazenda, disponibilidade, comprimento, limites e precondições. Structured output quando suportado; nos demais provedores, pedir JSON e validar localmente. Se só houver texto limpo válido, aproveitar como fala com os destinatários pré-autorizados e ignorar ações. Saída inválida não pode mover personagens nem afetar estados. Ocultar metadados, raciocínio e marcadores técnicos.

### Conversação espacial e grupo

Iniciativa espontânea entre moradores exige proximidade na mesma região conectada e parceiros acordados/disponíveis; aproximar-se antes de conversar. Uma sessão tem participantes, assunto/evento, máximo inicial de 6 turnos e expiração. Ao encerrar, liberar os moradores para outras atividades.

As falas são públicas: destinatário identifica a quem se dirigem, não um segredo. Percepção física local respeita proximidade; mensagens enviadas pelo Tamer pelo grupo são entregues digitalmente aos destinatários mesmo longe. Menção explícita cria oportunidade de resposta para todos os selecionados, mas o coordenador escalona e evita respostas simultâneas; `Todos` não obriga 12 respostas iguais.

No grupo da Digiline, todos os moradores são membros do canal, mas o construtor de contexto filtra mensagens pertinentes à conversa/percepção de cada um. O registro público não significa incluir tudo no prompt de todos. Chats privados nunca são espelhados nesse canal.

## 9. Frequência, consumo e falhas do provedor

Proposta inicial do modo equilibrado: até uma geração autônoma a cada 20 segundos no conjunto ativo, máximo 3 por minuto e 60 por hora de uso; cooldown individual de 60 segundos para iniciar nova conversa. Respostas na mesma sessão podem ocorrer antes desse cooldown, respeitando o limite global. Intervalos não são metas: silêncio e atividades físicas ocupam a maior parte do tempo.

Uma requisição autônoma em voo por vez; mensagens do usuário têm prioridade. Compartilhar limitador com chat privado, impedir filas ilimitadas e evitar bloqueio de chat manual por uma fila de NPCs. Reservar orçamento de resposta curta, medir tokens quando o provedor informar e contar requisições/retries no limite. Não prometer preço em dinheiro sem tabela de preços do provedor configurado.

Configurações da fazenda: diálogos autônomos ligados/desligados, frequência baixa/equilibrada/alta com teto explícito, limite de chamadas e indicador de consumo da sessão. Chamadas manuais ficam identificadas separadamente; limites do provedor se aplicam a ambas.

Sem chave, sem rede, erro 429, timeout ou limite atingido: movimento e atividades continuam. Mostrar estado discreto de diálogo indisponível/pausado, sem inventar conversa LLM. No máximo um retry transitório por turno, com backoff e respeito a `Retry-After`; erro de autenticação suspende novas tentativas automáticas até correção.

Revalidar antes de confirmar resposta: versão da fazenda, identidade, presença do autor, contexto da sessão e destinatários. Se autor saiu, foi transferido ou sessão foi invalidada, descartar resposta obsoleta. Não enviar novamente automaticamente após morte do processo se não for possível saber se a requisição chegou ao provedor; evitar duplicar gasto e mensagem local.

## 10. Balões e registro único de falas

Cada fala aceita gera um único `FarmMessage` persistido, com sequência por fazenda e identificador idempotente. Balão, grupo e memória consomem esse registro; não fazer duas chamadas LLM ou duas inserções para o mesmo enunciado.

Balões ancorados acima da cabeça usando câmera/projeção, com texto em tamanho legível independente do zoom. Mostrar autor e destinatários de modo compacto; texto completo fica acessível por toque na Digiline. Limitar inicialmente a três balões simultâneos visíveis, priorizando seleção, mensagem ao usuário e fala mais recente. Enfileirar somente o que ainda fizer sentido; falas antigas continuam no grupo sem serem reproduzidas ao abrir o mapa.

Duração inicial de 4–10 segundos conforme tamanho; pausa de leitura ao selecionar. Evitar sobreposição com controles e bordas. Morador fora da câmera mantém sua mensagem no grupo, sem deslocar câmera automaticamente. Indicador de elaboração é discreto e não congela o sprite.

Estado de leitura do grupo usa último número de sequência efetivamente visto no chat. Ver um balão não marca todo o grupo como lido. Chat Storage/Wild continua com a semântica de leitura individual existente.

## 11. Wild Ones e remoção completa de seguir

Criar estado persistente de relação selvagem por `individualId`, independente de `WorldSpawn`, contendo espécie de referência, confiança 0–100, estado de recrutamento e `contactUnlockedAt`. Para novos encontros, esse estado existe desde o início; desbloqueio ocorre atomicamente quando a confiança supera 75. Após desbloquear, o contato nunca desaparece por distância, expiração ou queda de confiança.

Novas conversas remotas usam `individualId` e essa relação. Radar permanece necessário para iniciar novos encontros; abrir um chat não pode dar acesso remoto a um selvagem ainda não desbloqueado. Quando um encontro bloqueado expirar ou ficar fora de alcance, impedir novos envios; preservar mensagens já gravadas e explicar o estado. Revalidar elegibilidade no envio, não apenas ao abrir a tela.

Remover: `startFollowing`, `stopFollowing`, `processFollowMovement`, callbacks de deslocamento, relocação junto ao usuário, punição por metros, observadores/DTOs/strings/botões/indicadores de seguir e a exceção de alcance para seguidores. Campos antigos podem permanecer inertes por uma migração para reduzir risco, mas nenhum código novo os consulta.

Confiança passa a ter uma fonte persistente única no estado selvagem. `WorldSpawn.mood` é migrado e deixa de comandar comportamento; leitura legada é removida dos DAOs/DTOs para evitar duas verdades.

Preservar recrutamento em 100 e requisitos atuais. Extrair criação do personagem para um serviço que aceite a identidade/candidatura persistente, com ou sem spawn. Em uma transação: validar requisitos, remover eventual WorldSpawn antes de inserir UserCharacter (respeitando os triggers), criar todos os registros auxiliares já exigidos e marcar relação como recrutada. Duplo clique ou concorrência não pode duplicar indivíduo. Pendências de recrutamento continuam disponíveis mesmo sem localização.

Após recrutar, retirar de Wild Ones e aparecer em Storage quando houver chat iniciado, com o mesmo histórico e apelido. Atingir zero após desbloqueio mantém o contato; muda a receptividade, sem apagar histórico nem revogar o desbloqueio. Excluir/arquivar conversa não refaz a confiança nem o recrutamento.

Migração de seguidores antigos: apenas confiança atual >75 garante desbloqueio; seguidores abaixo do limiar deixam de seguir e não recebem contato automaticamente. Não há registro confiável para provar que um encontro já expirado ultrapassou 75 no passado; não inventar esse dado.

## 12. Persistência proposta

Manter `ChatMessageEntity` para conversas privadas existentes. Criar repositório de índice da Digiline sobre esses chats, sem copiar mensagens para cada aba.

Novas entidades conceituais:

- `Farm`: UUID, nome, mapId/mapVersion, capacidade, createdAt, archivedAt, lastSimulatedAt, seed e versão de estado.
- `FarmResident`: individualId como chave única global, farmId, referência à presença atual no Storage, posição/região, orientação, atividade e timestamps. Um indivíduo pode estar em no máximo uma fazenda.
- `FarmResidentState`: individualId, necessidades, último update e cooldowns. Pode ser incorporada a FarmResident se não dificultar ciclo de vida; escolher um único armazenamento antes de escrever migração.
- `FarmObjectState`: fazenda, objectId do mapa e estado persistente. Reservas efêmeras são reconstruídas, não restauradas cegamente após interrupção.
- `FarmRelationship`: observador, outro indivíduo, afinidade, familiaridade e último contato; chave composta dirigida.
- `FarmMemory`: indivíduo observador, escopo, evento de origem, resumo, relevância e data.
- `FarmMessage`: UUID, farmId, sequence, tipo (`speech`, `user`, `event`), autor, snapshot de nome/espécie, corpo, audiência, replyTo, sessionId e timestamp. Eventos de sistema são distintos de falas.
- `FarmMessageRecipient`: messageId + individualId, permitindo destinatários múltiplos sem parsing de nomes; `all` resolve também o conjunto presente na publicação para preservar história.
- `FarmReadState`: farmId e última sequência lida; uma identidade de Tamer local por enquanto.
- `WildRelationship`: individualId, cardCharacterId opcional, snapshot de espécie/apelido, trust, contactUnlockedAt, recruitmentState, createdAt e updatedAt.
- `FarmGeneration`: requestId único, farmId, sessão, autor, revisão de contexto, status e eventual messageId, para idempotência e recuperação. Não persistir chave de API, prompt bruto ou raciocínio.

Preferências visuais/câmera e limites vão no armazenamento de configurações adequado; checkpoint de câmera também deve ser incluído no banco se for necessário restaurá-lo via `.vbhelper`. O dado indispensável à fazenda e seu histórico fica no Room.

Índices: moradores por farmId, unicidade por individualId, mensagens por `(farmId, sequence)`, relações por ambas as identidades e contatos por desbloqueio/atividade. Paginar históricos, inicialmente 50 mensagens por página. Consultas de lista trazem última mensagem e contagem de não lidas sem carregar toda a conversa.

Eventos sociais e fala/memória decorrente devem ser confirmados atomicamente ou derivados de IDs idempotentes. Relações/históricos sobrevivem à retirada de um morador; não usar cascade de FarmResident para apagar vida social. Exclusão explícita de identidade/dados pessoais precisa limpar suas dependências de forma definida. Remover card/espécie não pode apagar silenciosamente contatos: reter snapshot, marcar asset indisponível e desabilitar recrutamento até resolver referência.

## 13. Integração com Storage, relógio e ciclo de vida

Colocar na fazenda é uma alocação do indivíduo já presente no Storage. Não criar outro `UserCharacter`, não alterar `isActive`, não copiar sprites e não modificar campos de dispositivo. O indicador “Na fazenda X” aparece no Storage, com atalho de visita.

Transferir entre fazendas é operação atômica: verificar destino/capacidade, cancelar atividade/conversa, liberar recursos e escolher posição segura. Retirar mantém o Digimon no Storage e preserva histórico/relacionamentos. Excluir fazenda propõe arquivamento: libera moradores, encerra simulação e mantém grupo em histórico; exclusão definitiva exige ação explícita separada.

Saída confirmada do Storage para relógio/removeção bem-sucedida encerra a presença física na fazenda; tentativa de NFC com falha não remove morador prematuramente. Auditar os caminhos de WatchTransfer/receipts e os deletes de UserCharacter para fazer o cleanup na mesma transação efetiva ou por proteção de banco. Não impedir a continuidade da identidade permanente.

Evolução no app atualiza espécie/sprite do morador, preservando indivíduo e relações, e revalida footprint/posição. Retorno do relógio mantém identidade e histórico; readmissão na fazenda é explícita para não ocupar vaga inesperadamente.

Um `FarmSessionCoordinator` de escopo de aplicação gerencia a única sessão detalhada. UI adquire/libera sessão ao observar mapa ou grupo, evitando motores duplicados por recomposição. Outra fazenda recebe atualização resumida ao ser aberta. Ver listas Storage/Wild Ones da Digiline não liga sozinho uma fazenda em background.

## 14. Passagem do tempo e encerramento

Na sessão detalhada, simulação local inicialmente a 10–20 ticks/s; decisões de utilidade a cada 1–3 s; desenho interpolado pelo frame clock. Estado físico em memória, checkpoint a cada 5–10 s e nas mudanças importantes. Eventos/mensagens persistem imediatamente. Um processo morto pode perder poucos segundos de posição, nunca duplicar uma fala confirmada.

Ao sair de ambas as superfícies da fazenda ou mandar app para segundo plano: cancelar novas gerações, interromper/invalidar pendências conforme estado, salvar checkpoint e suspender loop. Nenhum foreground service contínuo.

Ao voltar: calcular elapsed não negativo, limitado inicialmente a 8 horas; avançar necessidades e ciclos locais por blocos, sem reproduzir milhões de ticks, sem chamadas LLM retroativas e sem fabricar transcrições de conversas que não ocorreram. Gerar no máximo um resumo local factual das atividades simuladas e marcar sua natureza resumida. As últimas posições válidas continuam como ponto de retorno; convites/reservas em andamento são encerrados ou revalidados.

Após longo afastamento, necessidades permanecem limitadas; não há morte ou perdas reais. Usar tempo monotônico durante sessão e timestamps persistidos para retomada, com clamp para mudança de relógio/fuso. O replay de um mesmo checkpoint deve ser idempotente.

WorkManager não é apropriado para conversa contínua: seu trabalho periódico tem mínimo de 15 minutos e execução sujeita a restrições do Android. A opção confirmada dispensa esse mecanismo para a Digifarm. Fonte: [Android — definir trabalho persistente](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## 15. Migração e backup sem perda de dados

Partindo da versão atualmente inspecionada, criar `MIGRATION_20_21`; ajustar número se outro trabalho alterar schema antes. Registrar migração no container e preservar o caminho de instalação por `items.db`.

Etapas transacionais: criar novas tabelas/índices; criar WildRelationship para spawns existentes com confiança/estado atuais; desbloquear contatos >75; desativar campos de seguir; preservar mensagens, leitura, apelidos e personalidades. Criar fazenda apenas no primeiro uso/ação do usuário, evitando 12 moradores inventados ou alocação automática.

Ativar exportação de schema para as novas versões e guardar uma fixture confiável de versão 20 para testes. Como o checkout atual não exporta schemas, não presumir que MigrationTestHelper terá automaticamente todos os schemas históricos; preparar baseline/fixture antes de escrever os testes.

Atualizar proteções de integridade para residência única e presença efetiva no Storage, sem enfraquecer a exclusividade World/Storage. Contatos são vínculos, não uma segunda presença física, e podem persistir após recrutamento.

Backup: suspender motor e escritores LLM, confirmar transações/checkpoint, então deixar exportação existente fechar/copyar banco. Importação também cancela sessões e não pode deixar callback atrasado gravar no banco substituído. Testar ida e volta de `.vbhelper` e abertura de backup antigo. Assets fixos do mapa são versionados no app; banco referencia versão compatível. Não introduzir novo formato de backup só para o mapa fixo.

Validar integridade, foreign keys e contagens antes/depois. Nenhum fallback destrutivo ou reset dos dados para “resolver” migração.

## 16. Organização de código proposta

Sob a raiz Kotlin existente:

- `digifarm/map/`: definição/importação de mapa, projeção, superfície, colisão e pathfinding.
- `digifarm/simulation/`: engine Kotlin puro, relógio/seed injetáveis, agentes, utilidade, atividades, reservas e recuperação offline.
- `digifarm/social/`: sessões, relações, memória, contexto, contrato/validador LLM e orquestrador.
- `digifarm/`: repository e coordenador de sessão.
- `domain/digifarm/`, `daos/`: entidades e DAOs; `domain/world/` recebe relação persistente selvagem.
- `digiline/`: consulta de listas, roteamento de conversa individual e grupo, leitura/paginação.
- `screens/digifarmScreen/`: renderer, câmera, balões, painel de morador e gestão.
- `screens/digilineScreen/`: listas em abas, grupo e compositor.
- `screens/worldScreen/`: contêiner World e Radar separado, adaptadores de chat/recrutamento sem seguir.
- `chat/`: cliente de geração e montagem reutilizável de contexto, preservando a API do chat atual onde possível.
- `navigation/`, `di/`, `database/`, recursos localizados e telas do Storage recebem integrações pontuais.

Assets derivados/manifesto em `app/src/main/assets/digifarm/bird/`; arquivos de autoria e scripts determinísticos em diretório de ferramentas do projeto. JSON runtime só suporta o subconjunto documentado. Nenhuma nova engine de jogo externa é necessária para o primeiro mapa e 12 sprites; reconsiderar renderer apenas se perfil no aparelho mostrar gargalo não resolvido.

## 17. Estados de interface e acessibilidade

Preservar fundo animado, paleta roxa/ciano, formas angulares e componentes atuais ao redor do mapa. A pixel art fornecida permanece com suas cores. O cenário é foco de experiência; a Digiline é interface de leitura/operação.

Cobrir: sem fazenda, vazia, cheia, carregando asset, asset ausente/inválido, sprite ausente, sem histórico, nenhum contato, sem LLM, offline, erro transitório, orçamento esgotado, personagem retirado durante conversa, contato recrutado, fazenda arquivada e banco sendo exportado.

Botões com alvo mínimo de 48dp, texto dimensionável, estados descritos além de cor e alternativa em lista ao toque nos sprites. TalkBack acessa moradores, atividade e conversa sem anunciar cada tick ou cada passo. Não expor o Canvas como centenas de células focáveis. Respeitar preferência de movimento reduzido: cortar efeitos decorativos e suavizações excessivas mantendo posição/atividade compreensíveis.

PT/EN/JA nos recursos, conforme estrutura atual, incluindo instruções LLM correspondentes. Testar teclado aberto, nomes longos, fontes ampliadas, retrato/paisagem, tablets e retorno por Voltar. A câmera acompanhando um morador se desliga ao arrastar manualmente.

## 18. Sequência de implementação e critérios de saída

1. **Fundação e mapa técnico.** Preparar asset, autoria/manifesto, projeção e depuração. Entrega verificável: um agente de teste atravessa ambas as pontes nos dois sentidos e respeita todas as bordas/obstáculos, com câmera correta. Sem LLM ainda.
2. **Modelo persistente e migração.** Criar entidades, DAOs, repositórios e fixtures; migração 20→nova versão. Saída: identidade, chats e contagens preservados; residência única/capacidade e backup validados.
3. **Digiline individual e Wild Ones.** Listas Storage/Wild, rotas por identidade, contato >75 e recrutamento independente de spawn; remover seguir de todos os caminhos. Saída: contato continua acessível após expiração e recrutamento não duplica indivíduo.
4. **World com abas e fazendas habitáveis.** Radar isolado, gestão de várias fazendas, alocação Storage, câmera/sprites/oclusão, 12 moradores. Saída: tudo funciona sem GPS e sem LLM, inclusive transferências de residência.
5. **Simulação e convivência local.** Necessidades, atividades, reservas, personalidade, relações e retorno offline. Saída: cenário vive sem clumping/teleporte, sem alterar atributos reais, e retoma sem duplicar eventos.
6. **Grupo e LLM social.** Grupo por fazenda, destinatários, orquestração individual, memória, registro único e balões. Saída: A fala, B responde com contexto próprio e ambos aparecem no mapa e no histórico; falhas do provedor não param o mundo.
7. **Integração e validação final.** Storage/NFC/evolução/backup, acessibilidade, idiomas, consumo, performance e aparelho real. Saída: todos os critérios abaixo satisfeitos e limitações materiais explicitadas.

Essas etapas são incrementos técnicos de uma entrega completa. Parar em personagens caminhando sem conversa autônoma, ou em um chat de grupo sem presença física, não satisfaz o pedido.

## 19. Testes necessários e aceitação

### Motor e domínio, com relógio/seed/provedor falsos

- Projeção/inversa em posições de referência; hit testing com zoom/pan/regiões; nenhuma dependência de densidade em coordenadas do mundo.
- A* acessível/inacessível, pontes nos dois sentidos, cercas, lago, prédio, obstáculos pequenos, ausência de corte diagonal e colisão contínua.
- Doze moradores disputando passagens e objetos sem sobreposição persistente, deadlock ou saída do chão.
- Transição e cancelamento de cada atividade, convite recusado, parceiro removido, prioridade justa e teto de alteração de necessidades/relações.
- Retorno após minutos/horas/dias, relógio regressivo, checkpoint repetido e versão de mapa alterada.
- Mensagens com destinatário único/múltiplos/todos, IDs inválidos, JSON inválido, texto puro, reasoning sem conteúdo, resposta atrasada e retry sem duplicação.
- Uma única sessão/worker de fazenda, teto de chamadas e prioridade de mensagem manual.

### Banco/instrumentação

- Migração de base v20 com chat lido/não lido, personalidade, seguidores com confiança 75 e 76, recrutamento pendente e identidades transferidas.
- Limiar 75 não libera, 76 libera; queda posterior e expiração não revogam; nenhum desbloqueio inferido sem evidência.
- Recrutamento remoto/em Radar e concorrente preserva todas as linhas auxiliares e respeita os triggers.
- Inserções concorrentes de 12º/13º morador e transferência entre fazendas; nenhuma lotação excedida ou duplicação.
- Exportação/importação `.vbhelper` conserva fazendas, contatos, chat, memórias e ocupação; callbacks antigos não escrevem após fechamento.
- Apagar card, retirar morador, evoluir e exportar para relógio mantêm histórico legível e não deixam agente fantasma.

### UI e aparelho real

- World/Radar/Digifarm e Mais/Digiline preservam retorno, abas e câmera; negar GPS não impede fazenda.
- Pinça, arraste e seleção convivem; sprites grandes/pequenos passam atrás/à frente corretamente; balões não cobrem controles nem ficam ilegíveis no zoom.
- Grupo aberto sozinho mantém conversa/simulação coerentes; alternar grupo/mapa não cria dois motores nem repete falas.
- Offline/timeout/429/sem chave têm estados claros e não bloqueiam movimento ou rolagem.
- Sessão de pelo menos 20 minutos com 12 moradores e histórico extenso, observando fluidez, memória, aquecimento e chamadas. Meta: desenho estável próximo de 60 fps onde viável, degradação explícita para 30 fps em modo econômico; não tratar a meta como medição já obtida.
- Demonstrar pelo menos uma conversa espontânea em dupla, uma mensagem a múltiplos destinatários, uma fala para todos e uma atividade em conjunto concluída. Verificar que cada NPC usa a personalidade atual e só controla suas próprias ações.
- Confirmar que vitais, evolução, inventário real e dados enviados ao relógio permanecem inalterados pelas atividades da fazenda.

Comandos previstos: `./gradlew.bat :app:compileDebugKotlin`, testes unitários apropriados (`:app:testDebugUnitTest`) e instrumentados (`:app:connectedDebugAndroidTest`) quando houver dispositivo disponível. Compilação sozinha não prova comportamento, colisão, câmera nem autonomia social.

## 20. Limites da primeira entrega e riscos tratados

Incluído: múltiplas fazendas no mapa fornecido, 12 moradores cada, câmera, tiles/colisão/oclusão, seis famílias de atividades, personalidade, relações/memória, falas autônomas entre indivíduos, balões, grupo e participação do usuário, Digiline completa e substituição de seguir.

Fora desta entrega: multiplayer, servidor 24h, geração de cenários, editor livre de construção, reprodução de Digimon, combate real na fazenda, recompensa econômica, vitais/evolução automáticos e novos mapas não fornecidos. O formato extensível não significa implementar esses recursos agora.

Riscos principais e contenção:

- **Asset achatado:** preparar recortes/oclusores e calibrar topologia antes da IA; a grade não pode ser deduzida só por cor.
- **LLM lento ou caro:** motor independente, fila limitada, contexto curto, teto de chamadas e atividade local durante falhas.
- **Conversa artificial/repetitiva:** turno individual, memória pertinente, personalidade reaproveitada, silêncio, cooldown e limite de sessão.
- **Perda de identidade/histórico:** vínculos por individualId, transações, snapshots de autoria e migração sem limpeza destrutiva.
- **GPS ainda afetando fazenda:** efeitos de Radar isolados e contato remoto separado de spawn.
- **Arquivos alterados por outra tarefa/modelo:** verificar git/schema/contratos antes de editar e ajustar este plano sem apagar alterações preexistentes.

## 21. Referências e passagem para implementação

Documentação consultada: [Compose — gestos de zoom e deslocamento](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch), [Tiled — mapas e orientação isométrica](https://docs.mapeditor.org/en/stable/manual/maps/), [Tiled — camadas e coordenadas de objetos](https://doc.mapeditor.org/en/latest/manual/layers/) e [Android — trabalho periódico](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). A combinação dessas técnicas com o motor proposto é uma decisão de arquitetura deste plano.

Ao receber autorização de implementação: ler este documento, conferir estado do repositório e diretrizes locais, preservar alterações existentes, começar pela etapa 1 e seguir os critérios de saída. Não reabrir decisões já confirmadas pelo usuário. Pendências de medição do tile, recortes e ajuste de desempenho são trabalho técnico previsto, não motivo para inventar dimensões ou encerrar a implementação parcialmente.

Ao concluir a implementação, entregar resumo de arquivos/funcionalidades, resultado dos testes, evidência visual em aparelho e qualquer lacuna real.
