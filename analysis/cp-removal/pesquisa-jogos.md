# Pesquisa: recursos de combate na série Digimon World e a origem do "Command Points" (CP)

**Data:** 07/11/2026
**Autor:** pesquisador-jogos (pesquisa delegada ao time)
**Escopo:** Digimon World (PS1), World 2, World 3, World 4, World DS, Dawn/Dusk, Championship, Re:Digitize/Decode, Next Order e títulos online correlatos; leitura dos documentos locais do VBHelper sobre o DW1.
**Enquadramento (ajuste do usuário):** os arquivos `OFFLINE_BATTLE_DW1_*` são **base de informação**, não exigência de alinhamento. Este relatório é **descritivo**: não conclui que o projeto "deve" copiar o DW1 nem trata fidelidade ao DW1 como critério obrigatório. A pergunta central é quais jogos têm um recurso de comando/ordens parecido com o CP e quais não têm.

**Método:** fontes externas via `web_fetch` (07/11/2026), registradas na seção 7 com URL e status de acesso; fatos do DW1 conferidos também nos documentos locais do repo, citados como `arquivo:linha`. O que não pôde ser confirmado online está marcado como **NÃO VERIFICADO ONLINE**.

---

## 1. Resumo executivo

**Quais têm recurso do tipo "command points":**

- **Sim — Digimon World: Next Order (2016/2017/2023):** possui **OP (Order Points)**, um recurso de ordens do treinador. É o único caso claro da série World. Porém o OP é **individual por parceiro** (não compartilhado), começa igual ao **Bond** com aquele parceiro, é redefinido depois da batalha, pode ser aumentado com **Support** e é gasto em ordens especiais (Agitation 50 OP; Special, Digifusion e ExE 150 OP). Técnicas ordenadas pelo jogador **não consomem MP**.
- **Parcial — Digimon RPG / Digimon Battle Online (2002/2010/2022):** combate por turnos com barra de tempo por Digimon; a intervenção do treinador é limitada por **cartas** (3 por vez, número limitado de usos por batalha). Isso restringe ordens, mas é um limite de itens/cartas, não um pool numérico regenerável de pontos de comando.
- **Não — todos os demais analisados:** DW1, DW2, DW3, DW4, World DS, Dawn/Dusk, Championship, Re:Digitize/Decode e Digimon Masters Online. Nesses jogos as ordens são dadas por menu (turnos ou tempo real) ou, no caso de Championship, não existem comandos em batalha; os custos recaem sobre recursos da **criatura** (HP/MP/energia), sobre **gates de Brains** ou sobre **gauges de timing**, nunca sobre um saldo de pontos do treinador.

**Nenhum jogo da linha World usa um CP do treinador compartilhado entre aliados** como o do VBHelper (pool 0..100, Support concede +8, especial consome 25, compartilhado). O único antecedente próximo é o OP do Next Order, e mesmo ele é individual e ligado a Bond.

**DW1 não tem CP.** Os recursos reais do DW1 são HP, MP (golpes têm custo de MP — base `mpCost × 3`, reduzido por Brains), **Brains** (desbloqueia comandos e melhora decisões), **Speed/prontidão** (`speedBuffer` 100 a -155) e o **finisher** (progresso com meta `3000 - Speed` + carga por L1/R1). Não existe saldo de "pontos de comando" no original.

**Veredito informativo para o VBHelper:** o CP atual **não é mecânica do DW1 nem convenção da série**; ele nasceu como **proposta** do documento de design/plano 3D do próprio projeto, e só depois foi implementado. Se o CP permanecer, trata-se de uma regra original do app (legítima como design, mas que não pode ser apresentada como fidelidade ao DW1); se for removido, não há perda de fidelidade ao DW1. O único parente histórico é o OP individual do Next Order.

---

## 2. Critério de classificação "recurso tipo CP?"

Para evitar ambiguidade, "recurso tipo CP" aqui exige as três coisas:

1. um **pool numérico do treinador** (não da criatura);
2. **consumido ao emitir ordens/comandos**;
3. **recarregável por ações do treinador** (Support/incentivo) ou por regra de batalha.

- **Sim:** atende aos três.
- **Parcial:** limita ordens do treinador por um consumível/limite (cartas, usos), mas não é um pool de pontos regenerável.
- **Não:** as ordens são gratuitas para o treinador e limitadas apenas por menu/turno, por estado da criatura (MP, prontidão, cooldown), por gates de atributo (Brains) ou por gauges de timing.

---

## 3. Tabela comparativa por jogo

| Jogo (ano / plataforma) | Modelo de combate | Recursos e medidores existentes | Como o treinador emite ordens | Recurso tipo CP? | Justificativa (1–2 frases) |
|---|---|---|---|---|---|
| **Digimon World** (PS1, 1999; Windows KOR, 2002) | Tempo real com parceiro autônomo; batalhas de campo, 1 parceiro | HP; MP (consumido por golpes); Offense; Defense; Speed (cadência/prontidão e acerto); Brains; prontidão `speedBuffer` 100/-155; finisher com carga | Menu de comandos: "Your Call"/automático e "Retreat"; conforme Brains libera Attack, Moderate, Change, Distance, Defensive e técnicas específicas; finisher com L1/R1 (mash) | **Não** | Comandos são limitados pelo **atributo Brains** e pelos recursos/estado da criatura; não há saldo de pontos do treinador. [S1][S2][L3] |
| **Digimon World 2** (PS1, 2000/2001) | Turnos (JRPG de dungeon crawler); equipe de até 3 Digimon | HP/MP por Digimon; níveis e DP (Digivolving Points); DNA Digivolution (fontes consultadas não detalham o combate por medidor) | Seleção direta de ação por Digimon em menu de turno | **Não** | A ação do jogador é o próprio turno; não existe recurso de treinador gasto para ordenar. [S3][S4] |
| **Digimon World 3** / World 2003 (PS1, 2002) | Turnos; batalhas 1×1 com party de até 3 (troca de Digimon) | HP; MP (técnicas custam MP; técnica de outra forma custa +20%); Strength, Defense, Spirit, Wisdom, Speed, Charisma; tolerâncias elementais | Menu de comandos por turno para o Digimon ativo, com ataques, itens, troca, Blast e DNA | **Não** | MP é recurso da criatura; nada é debitado do treinador ao comandar. [S5][S6] |
| **Digimon World 4** / World X (PS2/GC/Xbox, 2005) | Ação (hack and slash ARPG), até 4 jogadores; armas e magias | HP/MP e barras de combate (o artigo consultado não detalha os medidores); Tech por tipo de arma; magia por ranks (Petty→Infinity) | Controle direto do avatar (sem camada de ordens de treinador) | **Não** | O jogador é o executor; não há recurso de ordens. [S7][S8] |
| **Digimon World DS** / Digimon Story (NDS, 2006) | Turnos; RPG de coleta, perspectiva isométrica | HP/MP por Digimon (menu de ataque); stats de espécie e equipamento | Menu de ataque/ordens por turno | **Não** | Menu de batalha clássico; nenhum pool de comandos do treinador. [S9][S10] |
| **Digimon World: Dawn / Dusk** (NDS, 2007; JP Sunburst & Moonlight) | Turnos; 3 de 6 Digimon em campo contra até 5 inimigos; campo de 5 zonas | HP/MP por Digimon; atributos/weaknesses; equipamento; scan/Digilab | Menu de ordens por turno; posicionamento/target por zonas | **Não** | O custo de agir é o turno; não há recurso de treinador. [S11][S12] |
| **Digimon World Championship** (NDS, 2008) | Simulação/auto-batalha (estilo virtual pet); foco em criação e torneios | Medidores de criação (fome/cuidado/condição) e atributos; **não** documentados online em detalhe | **Não há comandos em batalha**: os Digimon escolhem os ataques sozinhos | **Não** | Sem ordens, não há recurso de comando; o jogador influencia pela criação/treino. [S13][S14] |
| **Digimon World Re:Digitize** (PSP, 2012) / **Decode** (3DS, 2013) | Tempo real; parceiro pensa e age sozinho; jogador atua como "comandante" | MP; **Brains (賢さ)**: muda comandos disponíveis e comportamento; **Power/Finisher Power** (enche ao atacar/levar dano); **Attack Gauge** e **Defense Gauge** (timing muda dano/bloqueio) | Ícones de comando na tela; o leque de comandos depende de Brains e da forma de instruir; itens de suporte | **Não** | Brains e gauges **gateiam/temporizam** comandos, mas não existe pool de pontos do treinador a acumular e gastar. [S15][S16] |
| **Digimon World: Next Order** (Vita 2016; PS4 2017; Switch/PC 2023) | Tempo real; **dois parceiros** com IA; batalha em arena circular; combate pausa ao abrir o Order Ring | HP; MP; **OP (Order Points) individual por parceiro**; Bond; Support com timing/cooldown | **Order Ring**: seleciona parceiro (esquerdo/direito), pausa e confirma ordem/técnica; Support aumenta OP; técnicas ordenadas não gastam MP | **Sim (OP)** | Único pool de ordens da série: acumula e é gasto para comandar, mas é **individual**, ligado ao Bond e resetado após a batalha — não é compartilhado. [S17][S18] |
| **Digimon Masters Online** (Windows, KOR 2009 / WW 2011) | Tempo real; MMORPG; jogador controla o parceiro diretamente | HP e **Digi-soul** (energia para ataques especiais; sem ela, só ataques básicos); itens de cura com espera | Controle direto de skills/itens (sem "ordens" de treinador) | **Não** | Digi-soul é energia da criatura; não há recurso de comandos do treinador. [S19] |
| **Digimon RPG** (ex-**Digimon Battle Online**; Windows, KOR 2002; EN 2010–2013; relançado 2022) | Turnos com **barra de tempo por Digimon** (estilo ATB); o Tamer não luta | Barra de tempo; HP; **cartas** limitadas (até 3 por vez, número limitado de usos por batalha) para buffs/digivolução/troca | O Tamer age quando a barra enche: atacar, fugir, item; cartas alteram estado | **Parcial** | Existe um **limite de intervenção do treinador** (cartas/usos por batalha), mas é consumível de inventário, não pool de pontos regenerável. [S20] |
| **"Digimon World Online"** | — | — | — | **Não confirmado / provavelmente inexistente** | Não foi encontrado título oficial com esse nome; ver seção 4 para as prováveis confusões. [S21] |

---

## 4. Sobre "Digimon World Online"

Não localizei **título oficial** chamado "Digimon World Online" na lista de jogos da franquia nem na navegação de MMOs da Wikipedia (que lista *Digimon Battle Online* e *Digimon Masters*). [S21] As explicações mais prováveis para o nome:

1. **"Digimon Online"** é o **MMORPG fictício dentro do enredo de Digimon World 3**, no qual os personagens ficam presos — não é um jogo real. [S5]
2. **Digimon Masters Online** — MMO real, mas sem vínculo com a subsérie World. [S19]
3. **Digimon RPG** (relançamento do antigo *Digimon Battle Online*) — MMO real, turnos com barra de tempo. [S20]

**NÃO VERIFICADO ONLINE:** não fiz uma busca exaustiva em catálogos de jogos cancelados/regionais (ex.: MMOs asiáticos descontinuados); a afirmação acima vale para as fontes consultadas, não como prova de inexistência absoluta.

---

## 5. DW1 × VBHelper: recursos reais do DW1 e o que o repo documenta

### 5.1 Recursos e engrenagens reais do DW1

| Elemento | O que o DW1 faz | Evidência |
|---|---|---|
| **Seis atributos** | HP, MP, Offense, Defense, Speed e Brains são valores distintos. Speed afeta prontidão/acerto/cadência; Brains afeta decisões, comandos e eficiência de MP. | [S2]; `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:18-19` |
| **MP** | Golpes comuns consomem MP a partir de `mpCost × 3`; Brains alto reduz o custo do parceiro; doença/ferimento aumenta o custo. | `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:47`; `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:332-340` |
| **Brains e comandos** | Brains libera Attack, Moderate, Change, Distance, Defensive e seleção explícita de golpes em faixas; abaixo de 301 pode haver períodos `SENILE`/hesitação. | `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:113,120`; `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:210-218` |
| **Prontidão / Speed** | `speedBuffer` cresce com `Speed / 100 + 1` a cada dois frames, chega a 100 e cai conforme a potência usada, com piso -155; modos de carga alteram a espera. | `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:54-57`; `OFFLINE_BATTLE_DW1_IMPLEMENTATION.md:20-24`; `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:342-350` |
| **Finisher / mash** | O finisher acumula progresso com meta `3000 - Speed` e carga adicional por L1/R1; a fórmula de dano tem `finisherBase = offense + power` e multiplicador por `mashCharge > 40`. | `OFFLINE_BATTLE_DW1_IMPLEMENTATION.md:59-73`; `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:97-99`; `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:404-422` |
| **Ordens do treinador** | O jogador dá ordens ocasionais; a criatura continua autônoma. O repo modela foco, técnica, item, defesa, estratégia, aproximação/recuo e Support como `TrainerAction`. | `DigimonWorldBattleEspecifications.txt:2983-3011`; `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:120-123` |
| **CP** | **Não existe.** O termo "CP" não aparece como mecânica do DW1 em nenhuma fonte consultada; nos documentos locais ele aparece apenas descrevendo a infraestrutura do próprio VBHelper. | `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md:121`; `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:458`; `DigimonWorldBattleEspecifications.txt:3013-3036` |

Observações de honestidade documental:

- O próprio repo declara que a implementação atual é **adaptação autoral**, não emulação completa do PS1: "The active bracelet profile is an authored adaptation, not a claim of full PS1 or latest-Vice emulation" (`OFFLINE_BATTLE_DW1_IMPLEMENTATION.md:70-73`).
- A leitura de que Brains "desbloqueia comandos" no DW1 é sustentada pelo repo e pela Wiki/Fandom ("Brains: Allows for more commands in battle"); a **lista exata de limiares** de Brains não é dada por fonte oficial — o apêndice do repo registra limiares de **Decode** (200/300/400/500) com confiança média, e isso **não** deve ser extrapolado para o DW1 (`DigimonWorldBattleEspecifications.txt:1966-1984`).

### 5.2 O que o repo já documenta sobre o CP atual (para contraste)

O apêndice do repo descreve o CP implementado como: começa em `0`, máximo `100`, janela de Support após o aliado causar dano concede até `8` CP, a janela é removida após o uso, o especial consome `25` CP e o CP é **compartilhado** entre os aliados (`DigimonWorldBattleEspecifications.txt:3013-3023`). O mesmo apêndice compara isso ao Next Order e lista as diferenças: lá o OP é individual, deriva do Bond, é resetado por batalha, Support tem timing/cooldown e técnica ordenada não usa MP (`DigimonWorldBattleEspecifications.txt:3030-3036`).

---

## 6. Origem do CP no projeto: proposta do plano 3D, não mecânica do DW1

O CP **não vem do DW1**. Ele nasce na documentação de design do próprio VBHelper e é explicitamente registrado como proposta:

- **Plano 3D, seção "Ordens e recursos do treinador":** "Proposta de primeira versão: **um saldo de Command Points do treinador compartilhado entre os parceiros**, energia individual e filas individuais. [...] **Essa regra é recomendação do plano, não uma decisão já confirmada pelo usuário.**" — `OFFLINE_BATTLE_3D_PLAN.md:160`. O mesmo trecho já prevê reserva/revalidação de custos e UI de disponível × reservado (linhas 154–158), incentivo com janelas (linha 162) e consumo de CP pelo especial.
- **Plano 3D, Etapa 7:** "Incentivo, CP e especiais — implementada"; critério de aceite "observar → incentivar → ganhar CP → defender/ordenar especial" — `OFFLINE_BATTLE_3D_PLAN.md:222-226`.
- **Plano 3D, debug:** "energia, CP/reservas" entre os dados de depuração — `OFFLINE_BATTLE_3D_PLAN.md:259`.
- **Plano 3D, seção "Recomendações ainda ajustáveis":** "As seguintes escolhas são propostas técnicas/de produto do plano, não respostas atribuídas ao usuário: — **CP compartilhado pelo treinador**, energia e ordens por parceiro." — `OFFLINE_BATTLE_3D_PLAN.md:269-273`.
- **Documento de design (especificação):** a seção 18 propõe separar recurso da criatura × recurso de comando e criar `CommandPoints`/`OrderPoints`; a seção 19 descreve Support/incentivo concedendo pontos (`DigimonWorldBattleEspecifications.txt:765-796` e `800-824`). A seção 6.3 do apêndice reforça que `CommandPoints` deve continuar existindo "para o sandbox", enquanto perfis históricos deveriam usar MP (Decode) ou OP individual (Next Order) (`DigimonWorldBattleEspecifications.txt:3276-3295`).
- **O próprio apêndice classifica o CP como escolha do VBHelper, não fato histórico:** na lista de "pontos em que o documento deve ser explicitamente identificado como original" estão "CP compartilhado", "CP inicial zero" e "Support de até oito pontos" (`DigimonWorldBattleEspecifications.txt:3183-3209`). A conclusão da pesquisa separa as gramáticas: Decode = comandos por Brains, Finisher Power e gauges; Next Order = dois parceiros, OP, Bond, Order Ring, Support, Agitation, Digifusion/ExE; VBHelper atual = "gramática de regras própria" (`DigimonWorldBattleEspecifications.txt:3616-3633`).
- **Nas recomendações de aproximação do DW1**, o CP aparece **apenas como item de apresentação futura**, não como mecânica a portar: "Definir como CP, incentivo, Blast, FORM, DUO e Jogress são exibidos ou habilitados nesse perfil" (`OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md:454-462`, citação na linha 458).

**Conclusão da seção:** o CP do VBHelper é uma **proposta de design do projeto** (documento de design + plano 3D), inspirada conceitualmente em recursos de ordem do gênero — com o OP do Next Order como parente mais próximo —, e **não** uma mecânica do DW1. Isso é um fato documental, não um juízo de valor sobre manter ou remover o CP.

---

## 7. Fontes externas consultadas (acesso em 07/11/2026)

> Regra: para cada afirmação externa, a fonte está indicada pelo código. Onde o acesso falhou, isso está registrado.

| Código | Fonte | URL | Status / o que sustenta |
|---|---|---|---|
| S1 | Wikipedia — *Digimon World* | https://en.wikipedia.org/wiki/Digimon_World | HTTP 200. Data/plataforma; "player cannot control the actions of an unintelligent Digimon, but as it gets smarter more control over its actions is given to the player"; um parceiro. |
| S2 | DigimonWiki (Fandom) — *Digimon World*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World?action=raw | HTTP 200. Lista de atributos (HP, MP "how many attacks you can use", Offensive, Defense, Speed "decreases the time between attacks", Brains "Allows for more commands in battle"); comandos "Your Call" e "Retreat". |
| S3 | Wikipedia — *Digimon World 2* | https://en.wikipedia.org/wiki/Digimon_World_2 | HTTP 200. Dungeon crawler; batalha "similar to other traditional turn-based JRPG's"; três Digimon. |
| S4 | DigimonWiki (Fandom) — *Digimon World 2*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_2?action=raw | HTTP 200. Dungeon crawler, DP/DNA; **não** detalha medidores de batalha. |
| S5 | Wikipedia — *Digimon World 3* | https://en.wikipedia.org/wiki/Digimon_World_3 | HTTP 200. Turnos; até três monstros; MMORPG fictício "Digimon Online". |
| S6 | DigimonWiki (Fandom) — *Digimon World 3*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_3?action=raw | HTTP 200. HP/MP, Strength/Defense/Spirit/Wisdom/Speed/Charisma; turnos 1×1; custo de MP (+20% para técnica de outra forma). |
| S7 | Wikipedia — *Digimon World 4* | https://en.wikipedia.org/wiki/Digimon_World_4 | HTTP 200. Action RPG hack and slash; até 4 jogadores; armas. |
| S8 | DigimonWiki (Fandom) — *Digimon World 4*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_4?action=raw | HTTP 200. Magia por ranks e Tech por arma; **não** detalha barras de HP/MP. |
| S9 | Wikipedia — *Digimon World DS* | https://en.wikipedia.org/wiki/Digimon_World_DS | HTTP 200. RPG; jogador "selects an attack from the menu"; sem relação com a subsérie World. |
| S10 | DigimonWiki (Fandom) — *Digimon World DS*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_DS?action=raw | HTTP 200. RPG isométrico; **não** detalha combate. |
| S11 | Wikipedia — *Digimon World Dawn and Dusk* | https://en.wikipedia.org/wiki/Digimon_World_Dawn_and_Dusk | HTTP 200. Turnos; até 3 de 6 Digimon; até 5 inimigos; campo de cinco zonas. |
| S12 | DigimonWiki (Fandom) — *Dawn/Dusk*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_Dawn_and_Dusk?action=raw | HTTP 200. Contexto/série; **não** detalha medidores. |
| S13 | Wikipedia — *Digimon World Championship* | https://en.wikipedia.org/wiki/Digimon_World_Championship | HTTP 200. Simulação de criação; "the player does not give commands in a fight but the Digimon choose their attacks themselves". |
| S14 | DigimonWiki (Fandom) — *Championship*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_Championship?action=raw | HTTP 200. Criação/coleta; **não** detalha medidores. |
| S15 | Wikipedia — *Digimon World Re:Digitize* | https://en.wikipedia.org/wiki/Digimon_World_Re:Digitize | HTTP 200. PSP 2012 / Decode 3DS 2013; retorno às mecânicas do DW1. |
| S16 | Site oficial Bandai Namco — batalha do Decode 3DS (japonês) | https://digimon-gameportal.bn-ent.net/product/world_re_digitize/3ds/system03.html | HTTP 200. Batalha em tempo real; parceiro pensa/age sozinho; jogador comandante dá instruções/itens; "賢さ" (Brains) e a forma de instruir mudam comandos/ações; "Power" enche e libera o finisher; Attack Gauge/Defense Gauge alteram dano/bloqueio pelo timing. |
| S17 | Manual oficial PS4 de Next Order (citado pelo apêndice de pesquisa local) | https://d1vtv52f4vjbmu.cloudfront.net/manuals/dwno/PS4-DWNO_manual_GBQS_WEB.pdf | **NÃO VERIFICADO ONLINE nesta sessão** (PDF não baixado). Fatos de OP foram lidos do apêndice do repo, que cita este manual: `DigimonWorldBattleEspecifications.txt:2314-2388`. |
| S18 | Wikipedia — *Digimon World: Next Order* | https://en.wikipedia.org/wiki/Digimon_World:_Next_Order | HTTP 200. Batalha em tempo real em arena circular; dois parceiros com IA; jogador pausa para ordens/itens; troca de parceiro; fusão especial temporária. |
| S19 | Wikipedia — *Digimon Masters* | https://en.wikipedia.org/wiki/Digimon_Masters | HTTP 200. MMO real-time; barra de HP + **Digi-soul** (energia de especiais); itens com espera de alguns segundos. |
| S20 | Wikipedia — *Digimon Battle Online* (redireciona de *Digimon RPG*) | https://en.wikipedia.org/wiki/Digimon_Battle_Online | HTTP 200. Turnos com barra de tempo por Digimon; Tamer não luta; cartas (3 por vez, número limitado de usos por batalha). |
| S21 | Wikipedia — *List of Digimon video games* | https://en.wikipedia.org/wiki/List_of_Digimon_video_games | HTTP 200. Lista da subsérie World e MMOs (Battle Online, Masters); **nenhum** título "Digimon World Online". |
| S22 | StrategyWiki — *Digimon World* | https://strategywiki.org/wiki/Digimon_World | **HTTP 403** (Cloudflare "Just a moment...") também em `?action=raw`. Conteúdo não acessado; nenhuma afirmação desta tabela depende dele. |
| S23 | DigimonWiki (Fandom) — *Re:Digitize*, wikitexto | https://digimon.fandom.com/wiki/Digimon_World_Re:Digitize?action=raw | HTTP 200, porém **stub** sem detalhes de batalha; usado só para dados gerais de plataforma. |

Observações de fontes:

- A URL `https://en.wikipedia.org/wiki/Digimon_World_(video_game)` retornou **HTTP 404**; o artigo correto é `Digimon_World` (sem sufixo). A URL `https://digimon.fandom.com/wiki/Digimon_World_(game)` também tinha retornado 404 (informado pelo lead); a página correta na Fandom é `Digimon_World` (wikitexto acessado em S2).
- Páginas renderizadas da Fandom vieram majoritariamente com *chrome* de navegação; por isso usei `?action=raw` (wikitexto) para S2, S4, S6, S8, S10, S12, S14 e S23.
- O apêndice do repo registra limitações de acesso próprias (GameFAQs 403, manual PS4 acima do limite do leitor, GAME-CMR com codificação antiga): `DigimonWorldBattleEspecifications.txt:1905-1912`. Não re-verifiquei essas fontes nesta sessão; afirmações que dependem delas estão marcadas.

---

## 8. Limitações e o que não foi possível confirmar

1. **StrategyWiki indisponível (HTTP 403)** nas duas tentativas; não foi possível usar os guias de batalha como fonte independente.
2. **GameFAQs não foi consultado nesta sessão**; o apêndice local registra que o acesso direto retornou 403 anteriormente (`DigimonWorldBattleEspecifications.txt:1908`). Detalhes numéricos de OP do Next Order (ganhos de Support, timings) vêm do apêndice local e de fontes comunitárias, **NÃO VERIFICADOS ONLINE** por mim.
3. **Manual oficial PS4 do Next Order** não foi baixado/navegado (PDF grande); os fatos de OP (Bond inicial, reset, custos 50/150, técnicas ordenadas sem MP) são citação secundária via repo (`DigimonWorldBattleEspecifications.txt:2336-2388`).
4. **DW2, World DS e Championship**: as páginas consultadas descrevem o modelo de jogo, mas **não** detalham medidores de batalha (HP/MP/gauges). As células da tabela para esses jogos indicam explicitamente o que está e o que não está confirmado.
5. **DW4**: a fonte consultada detalha magia/Tech, não barras de HP/MP; a menção a HP/MP na tabela é a mais provável pelo gênero, mas **NÃO VERIFICADA ONLINE** em detalhe.
6. **Re:Digitize × Decode**: não separei completamente as diferenças de balanceamento/interface entre PSP e 3DS; as fontes oficiais usadas são da página do **Decode 3DS** (S16). O artigo da Fandom para PSP é um stub (S23).
7. **"Digimon World Online"**: não confirmado como título oficial; a seção 4 registra as prováveis confusões e não afirma inexistência absoluta.
8. **Sem teste prático**: não executei os jogos nem emuladores; a classificação vem de documentação e wikis, que podem divergir entre regiões/versões (ex.: DW3 × World 2003; Vita × PS4 × Switch/PC no Next Order).
9. **Fontes comunitárias**: Fandom/Wikipedia são fontes secundárias; nomes de mecânicas e detalhes de interface podem ter imprecisões. Onde a confiança é menor, o texto indica.

---

## 9. Mapa das referências locais

Arquivos usados deste repositório (linhas conferidas na leitura em 07/11/2026):

- `OFFLINE_BATTLE_3D_PLAN.md` — origem/proposta do CP: linhas 152–162 (saldo compartilhado; "recomendação do plano, não decisão confirmada"), 216–226 (Etapa 7), 259 (debug CP/reservas), 269–278 (recomendações ainda ajustáveis).
- `DigimonWorldBattleEspecifications.txt` — documento de design e apêndice de pesquisa: 765–824 (seções 18–19: proposta de CommandPoints e Support), 1920–2009 (Decode: comandos por Brains, Finisher Power, gauges), 2260–2388 (Next Order: Order Ring, OP/Bond, custos), 2983–3011 (ordens atuais), 3013–3036 (Support e CP atuais), 3154–3209 (o que é histórico × escolha do VBHelper), 3276–3295 (separação de recursos), 3616–3633 (conclusão da pesquisa).
- `OFFLINE_BATTLE_DW1_IMPLEMENTATION.md` — implementação atual: 20–28 (prontidão 100/-155), 41–47 (Counter/especial), 59–73 (fórmula de dano/finisher mash), 70–73 (adaptação autoral).
- `OFFLINE_BATTLE_DW1_VICE_DIFFERENCES.md` — DW1 × VBHelper: 16–21 (atributos), 45–50 (MP), 52–57 (prontidão/Speed), 95–100 (Counter/finisher), 104–116 (Brains/obediência), 118–123 (desbloqueio de comandos; CP como infraestrutura do app).
- `OFFLINE_BATTLE_DW1_RECOMMENDATIONS.md` — propostas de aproximação: 208–238 (comandos/Brains, Your Call, Attack/Moderate), 310–350 (dano, MP, prontidão), 402–462 (finishers, mash, papel do treinador; CP citado só como apresentação).
