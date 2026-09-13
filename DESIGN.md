---
name: VBHelper
description: Um companheiro digital Android para guardar e viver experiências com Digimon.
colors:
  space-black: "#0A0812"
  deep-purple-bg: "#120F1F"
  deep-purple-bg-alt: "#171327"
  surface-deep-purple: "#1C1730"
  surface-elevated-purple: "#251E3D"
  surface-highlight-purple: "#2F2650"
  surface-stroke: "#3C3260"
  vital-purple: "#8B5CF6"
  vital-purple-bright: "#A78BFA"
  vital-purple-dim: "#4C3A82"
  vital-cyan: "#2DE1FC"
  vital-cyan-dim: "#1A8FA6"
  vital-yellow: "#FFD447"
  status-green: "#35D48B"
  status-blue: "#4C8DFF"
  status-red: "#FF4D6A"
  status-yellow: "#FFC93C"
  text-primary-on-dark: "#F4F1FF"
  text-secondary-on-dark: "#B7AFD6"
  text-muted-on-dark: "#8A82AC"
typography:
  display:
    fontFamily: "Oxanium, sans-serif"
    fontSize: "40sp"
    fontWeight: 900
    lineHeight: "44sp"
    letterSpacing: "0sp"
  headline:
    fontFamily: "Oxanium, sans-serif"
    fontSize: "28sp"
    fontWeight: 800
    lineHeight: "32sp"
  title:
    fontFamily: "Oxanium, sans-serif"
    fontSize: "22sp"
    fontWeight: 700
    lineHeight: "26sp"
  body:
    fontFamily: "Oxanium, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.25sp"
  label:
    fontFamily: "Oxanium, sans-serif"
    fontSize: "11sp"
    fontWeight: 600
    lineHeight: "16sp"
    letterSpacing: "0.4sp"
rounded:
  cut-xs: "4dp"
  cut-sm: "7dp"
  cut-md: "10dp"
  cut-lg: "14dp"
  cut-xl: "18dp"
  square: "0dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.vital-purple-bright}"
    textColor: "{colors.space-black}"
    rounded: "{rounded.cut-md}"
    height: "48dp"
  card-panel:
    backgroundColor: "{colors.surface-elevated-purple}"
    textColor: "{colors.text-primary-on-dark}"
    rounded: "{rounded.cut-md}"
    padding: "{spacing.lg}"
  cyber-frame:
    backgroundColor: "{colors.surface-elevated-purple}"
    textColor: "{colors.text-primary-on-dark}"
    rounded: "{rounded.square}"
    padding: "{spacing.sm}"
  navigation-active:
    backgroundColor: "{colors.vital-purple}"
    textColor: "{colors.vital-cyan}"
    rounded: "{rounded.cut-sm}"
---

# Design System: VBHelper

## Overview

**Creative North Star: "Companheiro Digital"**

VBHelper é um companheiro de bolso para o ecossistema Digimon: técnico o bastante para transmitir confiança em fluxos de NFC e coleção, mas acolhedor o suficiente para fazer o Digimon parecer presente entre uma interação e outra. A identidade evita uma interface genérica de ferramenta; o usuário deve perceber uma extensão viva do dispositivo, não um painel administrativo.

A base escura cria um espaço calmo para sprites e dados. Roxo constrói a atmosfera, enquanto ciano sinaliza energia, atividade e informação que merece atenção. Os detalhes tecnológicos — fundo de circuitos, anéis lentos e molduras de canto reforçado — são contidos para que dados, status e ações continuem fáceis de localizar.

**Key Characteristics:**

- Técnica, porém acolhedora.
- Densa em informação sem parecer um dashboard corporativo.
- Geométrica, legível e própria para toque em Android.
- Animada com intenção: estado e vitalidade, nunca ruído.

## Colors

A paleta usa profundidade violeta para o mundo do produto e energia ciano para tornar estados vivos e valores importantes imediatamente localizáveis.

### Primary

- **Pulso Violeta:** conduz ações primárias, seleção e a identidade energética do app.
- **Ciano Vital:** destaca progresso, valores ativos, foco e feedback de vitalidade.

### Secondary

- **Amarelo de Sinal:** reservado para reconhecimento, atributos especiais e destaques pontuais.
- **Estados de Missão:** verde, azul, vermelho e amarelo distinguem categorias e alertas sem substituir a semântica textual.

### Neutral

- **Espaço Profundo:** sustenta o fundo da aplicação e o contraste dos sprites.
- **Superfícies Violeta:** organizam informações em níveis de profundidade leves.
- **Texto Claro em Fundo Escuro:** mantém títulos, corpo e metadados em três níveis de contraste.

### Named Rules

**The Vital Signal Rule.** Ciano representa informação viva ou selecionada; não o use como decoração espalhada pela tela.

**The Dark Habitat Rule.** Fundos permanecem profundos e silenciosos para que sprites, dados e alertas tenham prioridade visual.

## Typography

**Display Font:** Oxanium (configurável pelo usuário, com alternativas Michroma, Eurostile Extended e Square 721 Extended)

**Body Font:** Oxanium (configurável pelo usuário)

**Character:** A tipografia é compacta, futurista e de alta legibilidade. O sistema usa peso e cor para hierarquia antes de aumentar demais o tamanho.

### Hierarchy

- **Display:** usado para números de grande impacto, nível e valores de vitais.
- **Headline:** identifica contextos e áreas principais.
- **Title:** nomeia painéis, personagens e ações importantes.
- **Body:** explica estados e orienta o usuário em fluxos de armazenamento, importação e chat.
- **Label:** sustenta metadados, rótulos de navegação e valores auxiliares; pode usar caixa alta quando reforça escaneabilidade.

### Named Rules

**The Signal-First Type Rule.** Em uma leitura rápida, o valor, o estado e o nome do Digimon precisam prevalecer sobre texto auxiliar.

## Layout

O app usa uma coluna de contexto no topo, conteúdo rolável e navegação inferior em telas compactas. Grades de Digimon e itens usam três colunas; a densidade é equilibrada com áreas de toque mínimas de 48dp e espaçamento de 8dp ou mais entre ações adjacentes.

O fundo tecnológico é global, atrás de superfícies transparentes de Scaffold. Painéis elevam a informação por camadas tonais e molduras, não por grandes blocos opacos. Em larguras ampliadas, navegação deve migrar para um padrão Android apropriado, como rail ou drawer, em vez de esticar a barra inferior de telefone.

## Elevation & Depth

O sistema é levemente elevado. Profundidade vem da progressão entre superfícies violeta, de molduras de baixa intensidade e de um brilho ciano apenas em estados ativos. Sombras pesadas não fazem parte do vocabulário; as camadas devem sugerir um instrumento digital preciso, não objetos flutuando.

### Named Rules

**The Quiet Elevation Rule.** Elevação separa grupos de informação, nunca compete com o sprite, o número principal ou a ação selecionada.

## Shapes

Controles Material usam cantos chanfrados em uma escala de 4dp a 18dp. Painéis que recebem a moldura `cyberFrame` são quadrados: a moldura já possui os quatro cantos grossos e define sua silhueta. Indicadores circulares continuam reservados para progresso e avatar, não para containers de conteúdo.

## Components

### Buttons

- **Shape:** cantos chanfrados médios e alvo mínimo de 48dp.
- **Primary:** violeta claro com texto escuro de alto contraste.
- **Secondary / Text:** sustentam ações de menor prioridade sem perder legibilidade no fundo escuro.

### Cards / Containers

- **Corner Style:** painéis comuns usam chanfrado; painéis com moldura técnica são quadrados.
- **Background:** superfícies violeta em níveis tonais.
- **Shadow Strategy:** elevação discreta por camadas e brilho de estado.
- **Border:** `Surface Stroke` para repouso; ciano para atividade ou seleção.
- **Internal Padding:** normalmente 8dp a 16dp.

### Inputs / Fields

- **Style:** componentes Material 3 com cantos chanfrados e contraste suficiente em fundo escuro.
- **Focus:** o estado de foco deve ser inequívoco por cor e nunca depender apenas de animação.

### Navigation

- **Style:** barra inferior compacta com ícone e rótulo; item ativo recebe fundo violeta translúcido e ícone/texto em ciano.
- **Motion:** cor do item ativo faz transição curta; mudanças de rota usam fade breve.

### Cyber Frame

Moldura técnica quadrada para painéis de vitais, itens e Digimon. Os quatro cantos são reforçados, e o estado ativo acrescenta um brilho ciano suave.

## Do's and Don'ts

### Do:

- **Do** use ciano para valores ativos, progresso e seleção.
- **Do** mantenha ações tocáveis com pelo menos 48dp.
- **Do** preserve o fundo técnico em baixa opacidade, atrás do conteúdo.
- **Do** use a moldura técnica apenas em painéis que precisam comunicar estado ou importância.

### Don't:

- **Don't** preencher a tela com brilho, ciano ou animações simultâneas.
- **Don't** arredondar painéis que já possuem os quatro cantos reforçados da moldura técnica.
- **Don't** depender somente de cor para comunicar erro, progresso ou seleção.
- **Don't** substituir padrões de navegação e interação nativos do Android por controles de aparência iOS.
