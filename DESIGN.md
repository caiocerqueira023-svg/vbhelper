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

A base escura cria um espaço calmo para sprites e dados. Roxo constrói a atmosfera, enquanto ciano sinaliza energia, atividade e informação que merece atenção. Os detalhes tecnológicos — fundo de circuitos, anéis de dispositivo em rotação lenta e molduras de canto reforçado — são contidos para que dados, status e ações continuem fáceis de localizar.

**Key Characteristics:**

- Técnica, porém acolhedora.
- Densa em informação sem parecer um dashboard corporativo.
- Geométrica, legível e própria para toque em Android.
- Animada com intenção: estado e vitalidade, nunca ruído.

## Colors

### Selectable Color Themes

- **VB Helper:** preserves the incumbent dark purple/cyan appearance and is the default.
- **VB Lab:** charcoal and neutral gray surfaces, white lettering, and electric cyan signals, based on the supplied Vital Bracelet Lab references.
- **VB Arena:** white/silver surfaces, charcoal lettering, and mint-green accents, based on the supplied Vital Bracelet Arena references. Small green text uses a deeper green to remain readable.
- **Digimon.net:** white/pale-blue surfaces, royal-blue controls and signals (`#1251D0`), and lemon-yellow filled highlights (`#F6FF00`), based on the official portal's `css/layout.css` and `css/top.css`. Imported logos and name sprites keep the shared light-theme silhouette shadow.
- Users select a saved palette in Settings → Appearance. Phone light/dark mode and wallpaper colors never select or alter it.
- Variants change colors only: layouts, typography, shapes, imported artwork, interactions, and animation remain shared. The dark-surface rules below describe VB Helper and VB Lab; VB Arena and Digimon.net use equivalent light tonal levels.

### 3D Environment Palettes

Digifarm, Colosseum, Radar battle and first-person Radar follow the selected saved
theme. Structural surfaces, digital panels, grids, boundaries and dome energy use
environment-specific roles: Helper purple/cyan, Lab charcoal/cyan, Arena
silver/green, and Digimon.net pale blue/royal blue with selected yellow highlights.
Natural turf and texture detail remain recognizable, and Digimon/effect artwork
retains its own colors. Theme changes update existing materials/textures and
backdrops; they do not redefine the scene topology or interaction model.

The authored Helper texture pixels and emission factors are the baseline.
Light variants use a smoothly compressed glow envelope to retain energy without
washing out their pale surfaces. Asset provenance and regeneration instructions
are documented in `tools/ENVIRONMENT_THEMES.md`. The native follow-up corrected
texture mipmap usage, separated cosmetic theming from battle readiness, and
verified theme changes and light-environment appearance on the connected Samsung.

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

**Character:** A tipografia é compacta, futurista e de alta legibilidade. O sistema usa peso e cor para hierarquia antes de aumentar demais o tamanho. A hierarquia de instrumento usa `headlineSmall` seminegrito, `titleLarge` 20sp e `bodyMedium` normal; títulos de tela usam `titleLarge` seminegrito sem caixa alta.

### Hierarchy

- **Display:** usado para números de grande impacto, nível e valores de vitais.
- **Headline:** identifica contextos e áreas principais.
- **Title:** nomeia painéis, personagens e ações importantes.
- **Body:** explica estados e orienta o usuário em fluxos de armazenamento, importação e chat.
- **Label:** sustenta metadados, rótulos de navegação e valores auxiliares; pode usar caixa alta quando reforça escaneabilidade.

Todos os 15 papéis de tipografia Material usam a fonte selecionada. `bodySmall` usa
12sp/18sp, `labelMedium` 12sp/16sp e `labelSmall` 11sp/16sp. Texto explicativo usa
`onSurfaceVariant`, não `outline`. Painéis de dados crescem com o tamanho da fonte;
retratos continuam quadrados, mas os cartões de estatísticas não impõem essa proporção.
Na área do parceiro ativo, a coluna Level/Attribute/Days usa a mesma proporção
quadrada do cartão do Digimon; os três painéis dividem a altura disponível igualmente.

### Named Rules

**The Signal-First Type Rule.** Em uma leitura rápida, o valor, o estado e o nome do Digimon precisam prevalecer sobre texto auxiliar.

## Layout

O app usa uma coluna de contexto no topo, conteúdo rolável e navegação inferior em telas compactas. A grade de Digimon mantém três colunas; as grades de itens adaptam a quantidade de colunas à largura disponível para manter nomes completos e a área de arte separada. A densidade é equilibrada com áreas de toque mínimas de 48dp e espaçamento de 8dp ou mais entre ações adjacentes.

O fundo tecnológico é global, atrás de superfícies transparentes de Scaffold. Painéis elevam a informação por camadas tonais e molduras, não por grandes blocos opacos. Em larguras ampliadas, navegação deve migrar para um padrão Android apropriado, como rail ou drawer, em vez de esticar a barra inferior de telefone.

## Elevation & Depth

O sistema é levemente elevado. Profundidade vem da progressão entre superfícies violeta, de molduras de baixa intensidade e de um brilho ciano apenas em estados ativos. Sombras pesadas não fazem parte do vocabulário; as camadas devem sugerir um instrumento digital preciso, não objetos flutuando.

### Named Rules

**The Quiet Elevation Rule.** Elevação separa grupos de informação, nunca compete com o sprite, o número principal ou a ação selecionada.

## Shapes

Controles Material usam cantos chanfrados em uma escala de 4dp a 18dp. Painéis que recebem a moldura `cyberFrame` são quadrados: a moldura já possui os quatro cantos grossos e define sua silhueta. Indicadores circulares continuam reservados para progresso e avatar, não para containers de conteúdo.

## Components

### Cross-screen instrument vocabulary

Home, Storage, species-information cards and the rich Tamer Arena briefing are
binding reference compositions. Preserve their information, expressive cues and
emojis. Other surfaces adopt their centered `TopBanner`/48dp cut-corner action
chips, square technical information panels, complete fact rows and consistent
action priorities rather than introducing separate toolbar/card systems.

- `VitalTabs` owns native tab semantics, cyan/palette-resolved selected text and
  the shared full-width signal line across Items, DigiLine, World, Arena and loadout.
- `VitalSearchField` owns the Storage-style cut-corner search, clear affordance
  and Search IME; callers retain their own search terms, filters and eligibility.
- `VitalSectionBar` carries the species-details divided reading vocabulary into
  dense quest/settings/detail sections; `VitalSectionHeader` stays available for
  lightweight headings in richer flows.
- `InstrumentSpacing` distinguishes 16dp screen gutters, 12/16dp panel/detail
  padding, 8dp control gaps and 20dp section separation. The combat deck and
  artwork grids retain purposeful compact variants.
- Primary task actions use `VitalButtonStyle.PRIMARY`; navigation/management use
  secondary or text actions; destructive operations retain error styling and
  their actual confirmation/transaction behavior.
- Item proportions apply to the artwork well; labels grow and wrap instead of
  cropping the entire tile. Equipment catalogs remain next to selected slots;
  Blast/Jogress have a clearly labeled sibling tab without losing any options.

The Tamer chooser remains a single flat scrolling list. Search stays visible;
series/availability filters and reload expand from a labeled filter control.
Arena entry first opens the same team-setup screen available from the header,
so the player can confirm or change their Digimon, format and difficulty.
"Use this setup" proceeds to browsing; the confirmed state survives returning
from a fight. Team setup stays available in the header instead of a pinned
chosen-partner summary.
Search/filter changes return to the first result, while returning from a briefing
keeps the browsing position. First load and failure/retry reuse the same collection
state panel as Storage and Dex; cached refresh keeps the existing roster visible.

Arena list and briefing portraits alternate the existing idle frames with crisp
pixel rendering and staggered timing. The outer background partial ring rotates
every 24 seconds and the inner ring every 18 seconds, with their animation state read in the ring Canvas draw phase instead
of recomposing app content. Both follow the shared motion preference and stop in
the background. Partner-origin, combined-partner and stat-scaling annotations are
visible only while the live master Debug switch is enabled.

The entry team editor leads with animated partner portraits, names, stages and
actual HP/Attack/Defense readings. Format and difficulty remain native segmented
choices with concise factual context; the confirmation action stays outside the
scrolling content.

### User species catalog and sprite normalization

The species chooser places Add beside search. Add opens a blank information
template; long-pressing an existing species opens the same template prefilled.
Holding a catalog (JSON) species creates a **separate user-owned copy**: saving
under the same name is blocked, the original stays listed, and assigned profiles
keep the original. Only entries the user created are edited in place.
Settings → Species and sprites gives ordinary access to the catalog, independently
of Debug. User-authored profiles retain their own text and local catalog records.

Each species can have one saved sprite-set preference. It is reachable directly
from the Dex species details ("Species sprite" section beside the facts) as well
as from the template editor. The chooser starts with
sprites already assigned to that species and also offers a searchable, paged list
of all loaded sprites, including unassigned records. Rows preview original source
artwork so normalization never hides the alternatives from its own chooser.
"Use original sprites" clears the preference on Save.

The source tabs now distinguish Assigned, Loaded and bundled Dot sprites. Dot
alternatives are available offline with artist/device credits, matching/all
filters and canonical-name/alias search. Preserved canonical IDs keep matching
available after a user renames a species template. Portraits decode off-main
with a byte-sized cache; selecting a set installs only that artwork and applies
the species-wide preference on Save. Source scope has been reviewed; native
captures of this new tab remain pending a connected device.

There are no source gameplay modes. Settings offers **VB first** and **Dot first**
as artwork priority only. VB first uses loaded/saved VB appearance then Dot fallback;
Dot first uses the species' Dot appearance then loaded/saved VB fallback. Missing
artwork uses a neutral placeholder and never disables a partner. The shared display
projection and asset-backed renderers resolve preferences by canonical identity across
DiMs. Portraits, idle/walk/attack poses, Dex
routes, Farm/World, Arena, battle forms and the widget use the selected appearance.
Original imported sprite records, card identities and recorded gameplay values
remain the source data. Missing secondary poses fall back to the chosen idle pose.

### Dex Species Profiles

The species-details dialog follows the supplied Digimon.net mobile reference's
reading order inside the app's established visual system: imported name sprite and
species name, a centered 176dp pixel portrait, divided Level/Type/Attribute/Special
Moves rows, then a clearly marked Profile narrative. App-specific stage, base stats,
earned scan data, and evolution requirements remain available in the same scrolling
body. Portraits and section bars retain square technical frames, palette-resolved
colors, configurable Material typography, and ownership/privacy states.

Close, Jogress, and eligible scan conversion stay outside the scrolling body.
Rows wrap instead of truncating long facts. Unknown species hide their descriptive
metadata and private stats. Empty optional fields produce no empty sections.

### Buttons

- **Shape:** cantos chanfrados médios e alvo mínimo de 48dp.
- **Primary:** violeta claro com texto escuro de alto contraste.
- **Secondary / Text:** sustentam ações de menor prioridade sem perder legibilidade no fundo escuro.
- **Destructive:** contorno e texto usam a cor de erro; ações de exclusão preservam confirmação explícita.
- `VitalButtonStyle` diferencia ações primárias preenchidas, secundárias contornadas e destrutivas em todas as paletas.

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

A barra compacta preserva o desenho original: fundo técnico, seleção chanfrada
envolvendo ícone/rótulo e quatro destinos diários mais More. Os itens mantêm
semântica nativa de tabs selecionáveis. O rail usa componentes Material e permite rolagem em janelas baixas.
Gestos horizontais pertencem ao conteúdo, como favoritos e gráficos; a camada global
de navegação não os intercepta. A barra inferior se recolhe enquanto o teclado está aberto.

O Scaffold externo reserva e consome os insets de sistema e recorte de tela; a raiz
do conteúdo reserva e consome o IME uma vez. Telas editáveis herdam esse consumo;
seu padding local atende apenas insets restantes quando usadas fora do shell.
A Activity usa `adjustResize` para manter cabeçalho e compositor na área disponível,
sem deslocar a janela por pan. Diálogos de edição usam limites da janela, conteúdo
rolável e ações que podem quebrar linha. O fechamento dos detalhes de Storage permanece acessível fora da lista de ações.

### Farm home shell

Home is the Digifarm: the 3D habitat fills the window edge-to-edge, and the app chrome floats over
it instead of framing it. The compact bottom navigation is retired; navigation happens on the world.

- **Top zone:** one solid cut-corner shelf rather than floating pills. Left: farm identity (home
  glyph, resident count, roster chevron — it opens who-is-here, not a farm manager). Right: bits with a
  coin glyph, quests, settings gear and menu. Utility actions use Material vectors, never hand-drawn
  glyphs, and the shelf guarantees contrast over a moving scene.
- **Command deck:** five 56dp cut-corner place tiles — Radar, Arena, DigiLine, Shop, Partners — each
  with its visible label. Cyan marks the current or actionable place; badges appear only while
  something is actually waiting. The menu sheet carries Quests, Dex, Items, Adventure, Transfer,
  Credits, Viewer, Lorebook and Settings.
- **Partner deck:** a 96dp slab that doubles as the drag handle, over a three-detent sheet (peek
  96dp, half 44%, expanded 96%). Half and expanded are non-modal and reuse the existing
  partner-overview modules unchanged; only expanded scrims. Back collapses Expanded → Half → Peek
  before the system leaves the app.
- **Gesture ownership:** the slab claims its drag area above the navigation-bar strip with
  `systemGestureExclusion`, so pulling the sheet up is never read as the system Home gesture. Drags
  on the 3D surface belong to the camera; drags on chrome belong to the sheet; nothing else.
- **Camera:** the active partner is followed by default, a drag or pinch overrides for five seconds
  and then eases back over 600ms. The followed resident stays between 30% and 52% of viewport height
  so the bottom chrome never covers it.
- **Frame budget:** rendering pauses when a place covers Home or the app leaves the foreground, runs
  at 30fps idle and 60fps during interaction, and honours reduced motion by stopping the island bob,
  glow pulse and pose cycling.
- **Content rule — the diorama is not a simulation:** there is exactly one home farm. It shows the
  active partner plus the stored favourites, up to twelve, rotating at random when the favourites
  exceed that. Nothing on the island writes progress, consumes time or asks for care. Residents run
  their own rhythms rather than one shared clock — each wanders, rests, trains, plays or idles on its
  own timing — and pairs walk up to each other, face, share a celebration and drift apart so the
  island reads as a living group. Tapping one offers only the camera or a chat. Farm
  creation, switching, needs, activities, grouping and autonomous farm dialogue are retired from the
  home surface; their stored data is left untouched rather than migrated or deleted.

Place destinations keep the instrument TopBanner and their own back behaviour; leaving a place
returns to the farm, which remains the single entry point for the collection and the menu.

### Readiness and feedback

Home distingue carregamento, configuração incompleta, coleção vazia, escolha de
parceiro e detalhes indisponíveis. A configuração usa o estado real de cartões e
conexão, permite pular/retomar e mantém a próxima ação visível fora do conteúdo rolável.
Os estados vazios oferecem uma ação concreta de recuperação.

Feedback transitório usa um host compartilhado de Snackbar. Resultados de
transferência também permanecem visíveis em Scan; falhas não disparam nova transferência
automaticamente. Mensagens de chat preservam o rascunho e oferecem recuperação legível.
Home não oferece uma ação de envio ao VitalWear no rodapé. Ao voltar para Home,
o último parceiro carregado permanece visível enquanto os dados reativos se atualizam;
reparo de histórico e leitura de configuração não bloqueiam seus vitais.

Rematch de treino reutiliza arte, GLBs e recursos Filament já preparados. Apenas
simulador, resultado, inventário e contagem de abertura são reiniciados; troca de
participantes e recuperação de falhas continuam preparando recursos normalmente.

### Shared Home and universal stat identity

Card-independent APP partners use the same DIM Home composition: vitals header,
interactive animated portrait, level/attribute/days column, mood/trophies/timer,
battle records, history, missions and favorite navigation. Their persistent app
parameters supply training/rank/item status without manufacturing VB/BE device rows.
There is no separate reduced APP Home surface.

Stat provenance is independent of this presentation. A bounded, versioned origin
projection supplies balanced APP stats influenced by retained cross-product growth.
Explicit DIM/BEM assignment enforces that exact loaded card's normal stat rules and
suppresses extra app/origin bonuses while assigned. Native baselines and earned progression remain
separate, so display/configuration changes cannot create gains or rewrite native
originals. Normalization policy numbers are authored rules, not device measurements.

The farm home is the compact shell: the Digifarm fills Home, places are reached from its command
deck and menu, and the phone bottom navigation bar is retired (see **Farm home shell**).
Home's upper-left Transfer action is being redesigned from PKForge's library/editor/
bank references into a **vertical origin list**. Games, Vital Bracelet and every other
supported Digimon origin are peer rows. DS entries use the recognizable DS launcher
icons rather than covers. Opening a game reveals saved individuals with automatically
matched species and the app's normal sprites. Tap an individual for its information
and a direct Move to Storage action. Preserve the saved app palette, sprite preferences
and Material/Android behavior. Earlier game-cover grids are a superseded prototype.

The only transfer directions are game → Storage and Storage → game. Any supported game
that supports an individual's species may receive it; origin is not a destination
restriction. The opened game sets the receiving destination. No filename hint or cover
art asserts reader/writer support, and current copies remain labelled as copies.
The normal supported move preserves nickname, identity and progress in one action,
without preview/confirmation/mapping/APP-creation screens. Internal backup, preflight,
journaling and persistence verification do not become extra routine user steps. Use
short in-flight/result feedback and never present a copy as an actual native move.
Keep management, historical reports and native review under secondary menu actions.
Bracelet preparation/receive/send belongs to its row in the same list. Active NFC
work locks page/partner changes; prepared payloads follow the selected partner.
Storage shortcuts carry partner context. Native change review does not award app growth.

### Universal participation and cosmetic preferences

Every stored Digimon is usable regardless of origin, DIM association, catalog coverage
or artwork. Sprite priority never grays partners, clears active selection, filters
gameplay selectors, pauses Farm/Adventure, blocks chat or invalidates battle/quest
work. Ordinary ownership, actual activity and in-flight transfer constraints remain.
Retired source-mode pauses resume preserving activity clocks and records. A matching
loaded DIM/BEM is required only where the receiving Vital Bracelet needs it.

Ordinary Storage/Home/care/battle information is VBHelper-specific and uses the same
DIM-style presentation for every origin. Raw native levels, EXP, stats and product
fields live in the individual's information under the relevant product's Transfer
entry. The permanent individual retains all source and conversion data internally.

### Cyber Frame

Moldura técnica quadrada para painéis de vitais, itens e Digimon. Os quatro cantos são reforçados, e o estado ativo acrescenta um brilho ciano suave. Cantos e traços usam intensidade contida (cantos 8dp, traço 1.5dp ativo) para não competir com sprite e ação.

### Condition details

Home mood cards show only the short qualitative summary, without a numeric score, so they follow the neighboring stat cards' compact rhythm. Trust/rapport retains its summary and exact everyday reading independently of DigiLine message previews. Other farm-care values remain in normal Details. Combat stats, requirements, rewards, and recovery stay directly usable. Summaries derive from stored state and never alter simulation or social decisions.

### Debug

A default-off master Debug switch in Settings applies in every build. Diagnostic dialogs, technical editors (raw prompts, tuning, species overrides, retrieval priority), forced spawns, distance bypass, accelerated scans, sprite inspection, and technical failure details appear only while enabled. Disabling revokes overrides immediately, closes diagnostic surfaces, and rejects stale callbacks without deleting progress or personal data.

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

## Ordinary battle movement

- Observation and readiness recovery can use controlled lateral footwork. Mixed
  attack ranges preserve current spacing until an action is chosen.
- Preparing an autonomous attack may reassess a better target; relative hysteresis
  and a brief review cooldown prevent rapid switching. Trainer focus and explicit
  orders remain authoritative, and wind-up owns its committed target.
- Local steering finds space around occupied lanes and arena edges. Walking and
  repositioning face actual travel, retaining heading between fixed-step snapshots;
  attack/guard poses face the combat target.
- Movement rules are independently versioned so old NPC checkpoints retain their
  historical behavior. The latest movement change has regression/build evidence;
  its new on-device 2v2 review is pending a reconnected device.

## Battle finisher motion

- Ordinary attack wind-up reuses the cinematic energy vocabulary at a smaller scale:
  a gathering glow, converging data motes, and a faint contracting ground ripple.
  Progress follows the actual startup phase; reduced motion uses a quiet static cue.
  The wind-up glow, motes, and ripple take their color from the matching small/large
  attack sprite's dominant visible hue, with neutral/support fallbacks when art is absent.
- Hit planes sit beyond the camera-facing support plane of each rendered model's
  transformed bounds. Oversized artwork lifts within that plane to clear the floor;
  depth testing still allows nearer world objects to occlude effects correctly.

- Finisher movies use one snapshot-owned sequence: focus, transform, reveal, charge,
  release, impact, aftermath, and restore. The attack receives more time than the reveal.
- Blast Evolution shows the imported result; Jogress stages both sources, hides their
  bodies and shadows, presents one result, and then restores both originals.
- Preserve crisp imported pixel art. Original procedural effects stay local to actors,
  attack paths, and impacts; the arena remains visible without a darkening scrim.
- Shallow, aspect-fitted camera shots retain the established attack-screen direction
  across repeated finishers. Result recognition and charge have a settled camera;
  launch uses a travelling side shot that fits the attack and the victim's visible extent.
  Player composition returns after the sequence. No automatic orbit during a movie.
- Titles and committed damage use discreet theme-aware text clear of camera controls.
  Arena geometry and command positions remain fixed through finishers in training and
  Radar; gameplay commands remain visible but inactive while pause/exit stay available.
- Reduced motion retains correct participant visibility and phase feedback. Audio and
  haptics honor settings and lifecycle. Strong attacks accelerate into contact, with
  proportionate foreground hit sprites and short expansion/decay. Presentation was
  reviewed on Galaxy A34 in portrait/landscape; see `TESTING.md` for coverage boundaries.

## Source-mode native acceptance boundary

The participation increment has source-review fixes recorded, with the final
narrow recruitment verdict and last guard/regression recompile pending at handoff.
Earlier native captures do not accept the new inactive/paused states. No connected
ADB device, new screenshots, TalkBack or large-font acceptance is recorded for
this increment; compilation is not native approval. See the latest `TESTING.md`
entry for the supplied checkpoint and remaining acceptance work.
