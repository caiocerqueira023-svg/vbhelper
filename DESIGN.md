---
name: Tamers Lab
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

# Design System: Tamers Lab

## Overview

**Creative North Star: "Companheiro Digital"**

Tamers Lab é um companheiro de bolso para o ecossistema Digimon: técnico o bastante para transmitir confiança em fluxos de NFC e coleção, mas acolhedor o suficiente para fazer o Digimon parecer presente entre uma interação e outra. A identidade evita uma interface genérica de ferramenta; o usuário deve perceber uma extensão viva do dispositivo, não um painel administrativo.

A base escura cria um espaço calmo para sprites e dados. Roxo constrói a atmosfera, enquanto ciano sinaliza energia, atividade e informação que merece atenção. Os detalhes tecnológicos — fundo de circuitos, anéis de dispositivo em rotação lenta e molduras de canto reforçado — são contidos para que dados, status e ações continuem fáceis de localizar.

**Key Characteristics:**

- Técnica, porém acolhedora.
- Densa em informação sem parecer um dashboard corporativo.
- Geométrica, legível e própria para toque em Android.
- Animada com intenção: estado e vitalidade, nunca ruído.

## Colors

### Selectable Color Themes

- **Tamers Lab:** preserves the incumbent dark purple/cyan appearance and is the default.
- **VB Lab:** charcoal and neutral gray surfaces, white lettering, and electric cyan signals, sampled from the shipped art (`#080808/#181818/#282828/#383838/#484848`, `#F8F8F8`, `#02C8FF/#89E3FF`). Three identity colours only; status green/red remain semantic roles.
- **VB Arena:** white/silver surfaces, charcoal lettering, and mint-green accents, based on the supplied Vital Bracelet Arena references. Small green text uses a deeper green to remain readable.
- **Digimon.net:** white/pale-blue surfaces, royal-blue controls and signals (`#1251D0`), and lemon-yellow filled highlights (`#F6FF00`), based on the official portal's `css/layout.css` and `css/top.css`. Imported logos and name sprites keep the shared light-theme silhouette shadow.
- Users select a saved palette in Settings → Appearance. Phone light/dark mode and wallpaper colors never select or alter it.
- Variants change colors only: layouts, typography, shapes, imported artwork, interactions, and animation remain shared. The dark-surface rules below describe Tamers Lab and VB Lab; VB Arena and Digimon.net use equivalent light tonal levels.

### 3D Environment Palettes

Digifarm, Colosseum, Radar battle and the isometric Radar world follow the selected
saved theme. Structural surfaces, digital panels, grids, boundaries and dome energy
use environment-specific roles: Helper purple/cyan, Lab charcoal/cyan, Arena
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

**Display Font:** Oxanium (configurável pelo usuário, com alternativas Michroma, Eurostile Extended, Square 721 Extended e VB Lab (Kafu Techno))

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

O fundo tecnológico é global, atrás de superfícies transparentes de Scaffold. Painéis elevam a informação por camadas tonais e molduras, não por grandes blocos opacos. Em larguras ampliadas, a navegação do app deve migrar para um padrão Android apropriado, como rail ou drawer, em vez de esticar a fileira de lugares do farm.

## Elevation & Depth

O sistema é levemente elevado. Profundidade vem da progressão entre superfícies violeta, de molduras de baixa intensidade e de um brilho ciano apenas em estados ativos. Sombras pesadas não fazem parte do vocabulário; as camadas devem sugerir um instrumento digital preciso, não objetos flutuando.

### Named Rules

**The Quiet Elevation Rule.** Elevação separa grupos de informação, nunca compete com o sprite, o número principal ou a ação selecionada.

## Shapes

Controles Material usam cantos chanfrados em uma escala de 4dp a 18dp. Painéis que recebem a moldura `cyberFrame` são quadrados: a moldura já possui os quatro cantos grossos e define sua silhueta. Indicadores circulares continuam reservados para progresso e avatar, não para containers de conteúdo.

## Components

### Cross-screen instrument vocabulary

Home, the Radar place and the battle surface are the binding top-chrome reference
compositions: a palette-resolved scrim instead of a shelf, a left-aligned title with a
single quiet context line, one junction line, and bare 48dp keys - icon and target only,
no fill, no hairline stroke and no cut-corner frame, as Home's HUD already had. The back
key always owns the left edge (in battle it is the exit key); every other action sits on
the right. Every other screen's header is that same type (`TopBanner`), and when a screen
does not pass its own back callback the header uses the activity back dispatcher, so it
matches the system back gesture. No screen keeps a separate pill, shelf or centered
toolbar. Storage, species-information cards and the rich Tamer Arena briefing remain
binding reference compositions for content: preserve their information, expressive cues
and emojis, their square technical panels, complete fact rows and consistent action
priorities rather than introducing separate card systems.

Full-screen pickers and editors that open as dialogs (`StorageCharacterPickerDialog`, the species
sprite picker and the species catalog editor) are pages, not see-through overlays: their window is
edge-to-edge and paints the palette's own opaque background, so the window dim can never darken a page
in the two light palettes. Insets are handled by the dialog wrapper, not by the page header.

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
- **Items is a grouped catalogue, not a wall of tiles.** Two tabs (My items / Items store). Owned
  items are sectioned by function - Training, Evolution, Vitals, Special missions - and the quest
  battle stock is the last section of My items rather than a third tab. Each entry is a compact
  instrument row: a 44dp framed glyph well, a two-line name that reserves both lines, the effect
  glyph, and the one number that matters for that list (owned count, stock, or store price, with an
  unaffordable price in the error colour). Special-mission entries take the full row because their
  names carry a goal and a window; the store keeps one wallet strip above the same sections. The item
  dialog repeats the row's grammar - framed glyph, name, family, owned count, description, cost - with
  one primary action (Use / Purchase) and a quiet Cancel. Labels grow and wrap instead of cropping
  the tile; equipment catalogs remain next to selected slots, and Blast/Jogress still have a labeled
  sibling tab without losing any options.

The Tamer chooser remains a single flat scrolling list. The search field and the section tabs collapse
while the roster is scrolled down and return on any upward scroll or at the top, the same way Storage's
search bar behaves; series/availability filters and reload expand from a labeled filter control.
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
as artwork priority only. VB first uses loaded/saved VB appearance, then the VB sprite
frames bundled with the app, then Dot fallback;
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

- **Style:** sem barra inferior global. O farm Home navega pela fileira de cinco lugares e pelo menu;
  as demais rotas abrem com o `TopBanner` de instrumento e uma chave de voltar explícita. A antiga
  barra compacta (quatro destinos + More) e o rail correspondente estão aposentados e não são compostos.
- **Motion:** mudanças de rota entre lugares usam slide horizontal curto, e Home tem a sua própria
  transição, porque é um mundo e não uma página (ver **Farm home shell** → Transitions).

A barra de status é transparente e o material do cabeçalho continua atrás dela; a barra de ação do
sistema mantém o fundo sólido da paleta selecionada. Gestos horizontais pertencem ao conteúdo, como
favoritos e gráficos; a camada global de navegação não os intercepta. Em larguras ampliadas a
navegação ainda não migra para rail ou
drawer: a fileira de lugares é limitada a 460dp e as chaves do HUD continuam nas bordas da janela.
A composição de largura ampliada permanece pendente.

O Scaffold externo reserva e consome os insets de sistema e recorte de tela; a raiz
do conteúdo reserva e consome o IME uma vez. Telas editáveis herdam esse consumo;
seu padding local atende apenas insets restantes quando usadas fora do shell.
A Activity usa `adjustResize` para manter cabeçalho e compositor na área disponível,
sem deslocar a janela por pan. Diálogos de edição usam limites da janela, conteúdo
rolável e ações que podem quebrar linha. O fechamento dos detalhes de Storage permanece acessível fora da lista de ações.

### Farm home shell

Home is the Digifarm: the 3D habitat fills the window edge-to-edge, and the app chrome floats over
it instead of framing it. The compact bottom navigation is retired; navigation happens on the world.

- **Top zone:** no shelf, no box. The HUD is two detached clusters over the world: status on the
  left (home glyph, resident count with a twelve-slot place gauge underneath, roster chevron inline
  with the count) and the tamer-rank emblem after it, then three keys floating at the right: Dex, the
  settings gear and the menu. Transfer lives in the place row, the currency lives in the Items/store
  surfaces instead of the header, and the menu sheet carries Quests, Transfer and a Tamer profile
  entry. A
  palette-resolved scrim and a single hairline ground line carry
  the contrast a panel used to carry; text and marks sit in HUD ink with a soft 1dp/3dp shadow that
  resolves per palette (dark ink shadow in the dark palettes, light in the light palettes), so
  each cluster holds up against sky, ground or chasm. Keys keep 48dp targets; past font scale 1.2 the
  count drops a type role and the chevron goes, and past 1.3 the rank emblem yields its slot, which
  keeps every key on a 360dp phone.
- **Place row:** five places as pure overlay, matching the top zone: Radar, Arena, Transfer, Items,
  Partners are icon plus caption floating on a bottom scrim, and the row owns the very bottom of the
  screen - the partner slab sits directly above it, like a mini player over a tab row. There is no
  deck, no tile and no container: contrast comes from a ground painted in the partner slab's own
  surface tone (`surfaceElevated` at the slab's opacity), so the top zone, the slab and the place row
  read as one material, plus HUD ink with a soft shadow. The row's ground starts at the sheet's own tone
  so the two fit together with no seam, then firms up toward the bottom edge so the places sit on their
  most solid ground; a single inset hairline - the same device the top zone uses against the farm -
  marks the junction. There is no blur or glass effect on chrome: translucency is tint only. The slab's frame keeps only its upper edge (two chamfers
  plus the top line): no side or bottom strokes, so it has no silhouette where it meets the row.
  The icon is the action and the caption its descriptor; the caption auto-sizes down rather than
  truncating (floor 11sp), so localized labels ("Parceiros") and large font scales stay complete.
  Pressing a place
  turns it accent; a current place would also keep a 2dp accent underline. The row is capped at 460dp
  so wide windows do not stretch it; centring on wide windows is not implemented yet.
  Badges appear only while something is actually
  waiting. The menu sheet carries Settings first, then Tamer profile, Quests, Dex, Items, Adventure,
  Transfer, Lorebook, Viewer and Credits. Earlier machined tile keys and the floating deck are
  superseded.
- **Partner deck:** a 96dp slab that doubles as the drag handle, sitting above the place row, over a
  three-detent sheet (peek 96dp, half 44%, expanded 96%). The peek height and the place band grow with
  the system font, capped at 1.5x, so the vitals and captions survive 2.0x text scale. The sheet owns
  one height value: a drag moves
  it 1:1 under the finger (no jump at grab or release, no detent snap while dragging) and the settle
  animation continues from wherever the finger left it; the chevron opens one detent upward and drops
  the sheet all the way to peek from expanded. A gesture that merely reaches the end of the overview's
  own scrolling stops there - the sheet only takes over on the next touch that starts at that end, and
  that pull closes it all the way to peek. When the sheet is fully open the top status zone fades
  and lifts away, because the overview owns the screen. While the sheet is pulled, its bottom slides
  into the place row's band and the row fades and drifts out in the same motion, so the two never
  overlap and never leave a gap; the row is fully gone by the time the sheet reaches Half. Half and
  expanded are non-modal and reuse the existing partner-overview modules unchanged; only expanded
  scrims. Back collapses Expanded -> Half -> Peek before the system leaves the app.
- **Special mission slots:** the four mission rows in the partner overview are actionable in
  place. An empty slot says "Tap to assign" and opens the assignment list, which offers only the
  owned items that fill that slot (steps, vitals, battles, wins) so a pick can never land in another
  slot; a running mission opens removal, and a completed one collects its reward. Assigning consumes
  one item through the same gameplay path as the Items tab, so Home and Items never diverge.
- **Gesture ownership:** the slab (and its loading/choose/unavailable state-card equivalents) claims
  only its own drag area above the navigation-bar strip with `systemGestureExclusion`, so pulling the
  sheet up is never read as the system Home gesture. The exclusion is attached to the bounded handle
  nodes, never to the whole route. Drags
  on the 3D surface belong to the camera; drags on chrome belong to the sheet; nothing else.
- **Camera:** the active partner is followed by default, a drag or pinch overrides for five seconds
  and then eases back over 600ms. A cinematic drift runs whenever nobody is touching the scene: the
  orbit turns slowly around the followed partner (~70s per revolution) while elevation and distance
  drift on incommensurate periods (~47s and ~71s), so the island is never seen from one fixed angle.
  Elevation stays under ~33 degrees because higher angles foreshorten the extruded sprite artwork.
  Every 14-26s the shot cuts abruptly to another side, height and distance instead of drifting there.
  Cuts draw from three heights (grazing, three-quarter, and the steepest the artwork allows) with the
  grazing angles weighted most common and the steep one occasional, and from three distances
  including a true close-up band; the previous tier is never repeated, and a close-up never pairs with
  the steep angle. The lens is floor-limited: its elevation is raised automatically whenever the orbit
  radius would put it at or below the island surface, so neither a cut, the drift nor a manual drag can
  sink the camera through the farm.
  After a touch the drift re-bases on the visitor's framing rather than easing back into the previous
  loop, so manual camera work is respected; reduced motion disables the drift and the cuts entirely. The followed resident stays between 30% and 52% of viewport height
  so the bottom chrome never covers it.
- **Lighting is baked, the shadow is real.** The island and its voxel blocks are authored
  emission-baked (`emissiveTexture` with a black base color) and the residents are unlit extruded
  pixel art, on purpose: the farm is a flat 2.5D diorama, not a lit scene. A full key/fill/rim +
  image-based-ambient rig was prototyped and rejected because it changed the art direction; the farm
  keeps its authored look. The one lighting-shaped thing that survived is the residents' ground drop
  shadow, which is what makes them sit on the grass. It is a blended ground quad under the feet
  (1.24 x 0.92 local units, black, 66% alpha, soft radial falloff) whose material is deliberately
  **lit** rather than unlit: with gltfio 1.76.1 an unlit blended primitive of the generated resident
  GLB never reaches the color buffer, which is why the old contact shadow was invisible. A pixel test
  in `SpriteExtrusionGlbTest` pins that combination.
- **The island is the original Links farm ground, patched with a digital grid.** Its top is built
  by `tools/digifarm/build_reference_textures.py` from the source atlas: the floor is cut into large
  16px pieces (a 2x2 block of the authored cells, so the source's own tile seams stay visible inside each
  piece) and the pieces are laid out in contiguous regions like the reference farm's hand-made floor -
  broad patches of one tile, a few warm path tiles, long runs of the same piece - then the mosaic is
  pulled onto the theme's accent hue at low saturation, keeping most of the tile variation. The exposed cells (`wireframe_tiles()`) are a near-black void carrying one continuous
  bright square grid. The farm is deliberately
  **monochromatic**: the turf is the theme's `energy` accent at low saturation and a dark value, and
  the grid uses the same `energy` accent at full strength - the very color the voxel blocks already
  use for their edges - so grass, patches and floating cubes are one hue per theme (violet, cyan,
  green, blue) and the scene shows only that hue plus the near-black substrate. The cliff keeps
  its authored texture and its transparent foot, and the hollow see-through on the flanks is gone:
  the upper 48% of the cliff is opaque in the authored art anyway, so those bands render alpha-masked
  in the opaque pass and write depth, which makes the near side occlude the inside of the far side
  that used to show through (about 70% of the artifact, measured offline with a ray/triangle check).
  An earlier inset "interior lining" was removed: it outlived the fading foot and read as a dark
  shadow hanging under the island. The cliff's authored strata highlights (the light lines near the
  rim) are lifted to the same `energy` accent, so the island's side lines read like the cubes.
- **The floating voxel cubes collide with each other.** The authored rest boxes do not overlap, but
  each cube bobs with its own amplitude, speed and phase on top of its group's bob, so a stacked pair
  drifts up to ~26mm into itself - the clipping the farm showed. A per-frame solver builds each
  cube's animated world centre from the known parent chain (group translation + cube translation; the
  cubes are origin-centred and unscaled) and separates every overlapping pair along the axis of least
  penetration, half to each plus a 2mm pad, in four passes so stacked chains settle. Corrections are
  recomputed from the authored bob every frame, so motion stays smooth and never drifts. The pure
  solver is `CubeCollision.kt`; `FloatingCubeLayoutTest` replays the real fourteen-cube layout and
  asserts no frame ever intersects (it also asserts the unsolved bob *does* overlap, so the case cannot
  silently go stale). Note: do not read `TransformManager.getWorldTransform` here - inside the frame's
  transform transaction it returns identity for these children, which is why an earlier version never
  saw the real cubes.
- **The farm's floor and cube accents bloom in every theme.** The authored emissive pulse reaches the
  dark-theme envelope (up to ~2.1x) and light palettes previously compressed it to 1.15, so only the
  dark themes glowed. The Digifarm's emissive materials are theme-tinted surfaces rather than white
  ones, so its controller is allowed a higher light-theme pulse ceiling (1.62) while the light-theme
  emissive gain is trimmed to 0.94: the floor's body stays saturated and only its bright lines climb
  into the bloom range, so the glow matches the dark themes without washing the colors out.
- **Frame budget:** rendering pauses when a place covers Home or the app leaves the foreground, runs
  at 30fps idle and 60fps during interaction, and honours reduced motion by stopping the island bob,
  glow pulse and pose cycling and freezing the residents to their staged tableau.
- **Transitions:** Home is a place, not the first tab of a pager, so it never slides sideways. The farm
  is the depth layer, and the arriving pages are transparent over the global device pattern, so while
  the change runs the farm *is* that page's background: leaving it, the outgoing chrome fades in 150 ms,
  the arriving place lifts ~2% of the window height as it dissolves (220 ms fade / 320 ms travel), and
  the world pushes in 1 → 1.05 while it crossfades into the device pattern over 240 ms - never a
  one-frame background cut at the end of the transition. Returning reverses it: the place sinks ~2% and
  dissolves (220/240 ms), the world settles 1.05 → 1 and fades back in over 240 ms, and Home's chrome
  lands 40 ms behind it so arrival reads as returning to the world. The farm's frame loop pauses the moment
  it is left, so a route change never renders two 3D scenes. Routes that touch neither Home nor the
  primary row keep a crossfade; reduced motion removes the scale and the lift and leaves ~90 ms fades.
  The shell and the destination are two independent layers: the farm keeps the window's full bounds
  for the shell's lifetime, so a route change never re-lays out or resizes the surface that owns the
  swapchain, and only the destination takes the system-bar and IME insets. Arrived collection grids do
  not replay their per-card entrance on top of the page transition; items that appear later (scrolling,
  later pages) still animate in.
- **Roster and additions:** the "who is here" sheet lists the members and their state next to the limit
  and artwork controls; adding someone opens the **same full-screen Storage partner picker the Arena
  uses** (search, filters, sort, sprite grid) instead of a bespoke list, so choosing a partner is one
  learned surface across the app.
- **Content rule — the diorama is a living tableau, not a simulation:** there is exactly one home farm.
  It shows the active partner plus the stored favourites, up to twelve, rotating at random when the
  favourites exceed that. Nothing on the island writes progress, consumes time or asks for care.
  One cosmetic cast (`FarmDioramaSimulation`) drives everybody on a single clock, using each VB
  Digimon's complete pose set: idle breathing, walking, jogging, working out, a happy beat and sleep.
  **Walking keeps the pre-liveliness rhythm the owner preferred:** the walk pose uses the artwork's
  walk frames and the jog pose its run frames, one stride covers 0.22 world units and is interpolated
  over 1.1 s, and the next stride starts after a 1.15-1.6 s beat — so each step lands, the sprite rests
  on the pause for a moment and then steps again, exactly like the old diorama. The walk pose cycles
  every 250 ms and the jog every 220 ms; there is no step bob and no speed-derived cadence. A jog uses
  the same rhythm family with a longer stride (0.32 units) and a shorter 0.8 s window, so it reads
  faster without strobing.
  **Pacing is probabilistic, not directed.** When a resident finishes what it was doing it rolls once
  for its next beat: 49% stands, 42% walks, and the remaining ~9% is split between a rare jog, a
  workout, a happy beat, a nap and going to greet a neighbour. There is no global cycle, no schedule
  and no cap on how many residents may do the same thing at once — the odds are the whole director, so
  the cast cannot synchronise into a show and most of the time the island is simply residents standing
  and walking about. Held beats last seconds (standing 2.5-8 s), while the deliberate ones are held
  longer: a workout lasts 10-20 s and a nap 30-60 s, so when one happens it is a visible state rather
  than a blink.
  **Greetings are the rarest roll (~0.8%) and are held longer:** one resident walks over to a standing
  neighbour, they face each other for 6-9 s and then drift apart, so a greeting reads as a small event
  instead of constant background behaviour.
  **Nothing replays.** The cast is seeded with fresh entropy every time it is created (app start or
  roster change) and every resident owns its own random stream; the partner of a greeting is picked
  uniformly at random among everyone in reach, not always the nearest. A fixed seed exists only as a
  constructor parameter for tests, never in the app.
  **Body collision is real:** each resident is a circle the width of its extruded artwork plus a
  margin; walkers are pushed around planted neighbours (a resident that has just arrived counts as a
  mover for that step) and the renderer adds a final movers-only visual-separation pass over the
  interpolated positions, so two sprites can never be drawn inside each other and a sleeping resident
  is never nudged. Tapping one offers only the camera or a chat. Under reduced motion the island
  freezes to its staged tableau: no walking, social beats, bobbing or pose cycling.
  Farm creation, switching, needs, activities, grouping and autonomous farm dialogue remain retired
  from the home surface; their stored data is left untouched rather than migrated or deleted. The rest
  of the app no longer exposes the old multi-farm UI either: DigiLine lists only Storage and Wild Ones
  (no farm-group section), Storage never captions a partner with a farm name, and the legacy `World`
  alias opens the Radar world without the Radar|Digifarm tabs or the retired farm-management screen.

Place destinations keep the instrument TopBanner and their own back behaviour; leaving a place
returns to the farm, which remains the single entry point for the collection and the menu. The
Radar place is the exception: it is a second full-bleed world and owns floating chrome instead of
the banner (see **Radar place**).

### Radar place

Radar is the world surface for the real-world ecosystem: it answers "what is near me, which way,
how far" at a glance and then gets out of the way. It inherits Home's shell (a live 3D world fills
the window; chrome floats over it) and Storage's instrument vocabulary (cut-corner 48dp keys,
Oxanium labels, cyan for live signals, no nested cards).

- **World:** the Radar plate is the authored radar grid with the battle props, sky dome and battle
  boundary stripped (`Arena/Radar/radar_plate.glb`, generated from `radar_grid.glb` by
  `tools/build_radar_plate.py`, so the Radar place never shows the false range ring or the floating
  voxel set pieces). It is theme-bound through the same environment-theme controller as battle, by
  mapping the derived asset onto the authored radar scene. Every nearby Digimon stands on it as its
  extruded sprite model at the bearing and distance the radar reports; the active partner stands at
  the plate centre as the player token, on a violet ring whose radius is the interaction proximity —
  it is placed at the same radial fraction a Digimon at `RadarWorldGeometry.INTERACTION_RANGE_METERS`
  gets, so anything inside the ring is inside the range the Radar uses for chat, battles and quests.
  The ring itself is a single clean violet line drawn as a **real 3D ground decal**, so the depth
  buffer makes Digimon occlude it exactly like the plate — no Compose overlay and no clipping tricks.
  Its thickness stays constant on screen through a small ladder of pre-baked decals (each calibrated
  for a different ring radius) whose alpha is cross-faded as the ring radius changes, so the line
  follows a pinch smoothly without rebuilding textures or waiting for the zoom to settle. There is no
  glow or halo, and the ring is always behind the Digimon standing on it.
  The plate turns with the compass heading so the screen stays north-up and the partner stays planted
  at the centre. Walking is visible on screen because the plate also **slides opposite to the player's
  real travel**, exactly like the legacy 2D radar's scrolling grid: the phase is anchored to the first
  location fix, converted at the plate's linear metre scale (one authored cell =
  `RADAR_ISO_GRID_CELL_METERS`), folded to half a cell so the finite authored floor never runs out, and
  rotated with the compass like every other plate element. The slide is filtered against GPS wander with
  a time constant that grows with the reported fix accuracy (0.35 s + 0.08 s per metre, so a 10 m fix
  follows in ~1.15 s) and fixes coarser than 30 m hold the grid entirely, so standing still does not
  shake the world. Because the authored world grid is a translucent mesh and now slides under the ground
  markings, the metre-ring decal and the interaction ring claim late Filament render priorities (5 and
  6, where 7 renders last) instead of relying on depth sorting.
- **Cardboard sprites:** wild Digimon use the farm/battle cardboard treatment: the extruded slab turns
  toward its real-world travel heading and is camera-assisted so it is never edge-on and mostly faces
  the camera (the radar clamps the assist to ±15°, tighter than the farm, because the radar camera is
  fixed while the plate turns). It mirrors only when travel crosses the screen's profile. Travel is
  measured from the Digimon's world position, never from its on-screen motion, so the compass turn and
  the zoom animation cannot read as a direction change (the plate deliberately rotates under a
  north-up view; sprites rotate with it instead of flipping). Walk/idle pose changes use a smoothed
  speed with start/stop hysteresis so the ecosystem's 1 s interpolation steps cannot flicker the
  animation. A Digimon that has not walked yet stands facing the camera like an idle island resident;
  idle residents keep their last heading instead of snapping back to the camera. Visibility is decided
  by the projected billboard rect, not by the visible-radius value, so zooming never hides a Digimon
  that is still on screen.
- **No distance rings:** the Radar place draws no range/metre rings (owner decision 2026-11-04); the
  ring capability stays in the renderer behind an empty ring list but is not used. Orientation comes
  from a **large, minimal instrument ring** in the same footprint as the earlier compass: 120 thin
  cyan ticks turn with the heading, the four cardinal directions get longer, thicker ticks (north the
  longest), with no letters, blocks, bezel face or inner circle — the plate itself is the dial. A
  single red needle (the reference digivice arrow's shape: a larger triangle pointing outward, a
  small gap, then a slightly smaller triangle pointing inward) rides the ring at the nearest wild
  Digimon's bearing and points outward at it. While the radar is live, the needle occasionally
  performs one short idle search gesture — a full sweep or a searching wiggle — and returns to the
  true bearing; the tick nearest that bearing is always drawn slightly longer, so the reading stays
  visible even while the needle sweeps. Reduced motion keeps the needle static. A soft vignette fades
  the plate edge into the page. The
  plate floor itself is enlarged so the ground continues past the grid at every zoom and camera
  angle; no horizon or floor edge is visible.
- **Chrome:** a detached top cluster (48dp back key, "Radar · NW" in HUD ink, one quiet context line
  with the resolved surroundings, options key, plus the spawn-settings key while Debug is on) over a
  single top scrim, and one bottom signal band holding the nearby count, the zoom keys and, only while
  the state is not ready, one status line with its recovery action. The surroundings line is the
  OpenStreetMap biome readout ("Surroundings: Park"); while the lookup is Null it is simply absent, so
  unavailable map data never invents surroundings. No panel wraps the world, and no permanent toggle
  rows exist.
- **Options sheet:** labels, AR battles, spawn/DIM settings (Debug) and the legacy renderers live in
  one `ModalBottomSheet`. The legacy 2D radar and the first-person radar remain in the codebase and
  are selectable there only while the master Debug switch is on; they are instruments, not a second
  interface.
- **Labels:** entity names and distances are off by default and never cover the top or bottom chrome;
  at most one speech bubble is shown, anchored to the nearest speaker. Tapping a Digimon opens the
  encounter sheet; long-pressing opens its species details. The tap target is the projected sprite
  billboard itself (minimum 48dp), not a box floating above its head, so hitting a Digimon works like
  hitting a farm resident; it draws no pressed ripple or overlay, so pressing never paints a grey box
  over the sprite. No marker sits over a Digimon's head: the former interaction dot was
  removed, and a live interaction is carried by the encounter sheet and the marker's accessibility
  description.
- **States:** locating, populating, empty region, approximate fix, frozen and failure states are one
  quiet line in the band with the recovery action beside it; READY is silent.
- **Motion:** the compass turn is continuous and follows the motion preference; pose cycling and
  interaction lunges reuse the farm's reduced-motion rules; the 3D surface pauses whenever the app is
  not resumed or a battle/AR stage covers it, and only one Filament engine exists at a time.
- **Camera:** the radar opens at its lowest readable angle (34°) and that is the only angle — the
  vertical tilt gesture was removed, so the plate keeps one predictable foreshortening while zoom and
  compass rotation remain free. (The Debug legacy first-person view keeps its own pitch drag.)
- **Social pause:** wild ↔ wild chat and battles are paused for the current versions, behind
  `WildSocialInteractionGate`. The planner, dialogue, NPC battle simulation and history all stay
  in the codebase and the Radar keeps rendering whatever history remains; player-directed and
  wild → player encounters are unchanged. Paused pairs are cancelled (releasing claims) and
  omitted from the public snapshot, so no wild-on-wild interaction appears on the radar while the
  gate is off.
- **Encounter data:** encounters that only carry the neutral 32×32 placeholder artwork are data
  markers, not Digimon: they are excluded from world population (and from Debug spawn choices), and
  any stale row referencing them is cleaned on the next population pass and filtered out of the radar.
- **Rendering budget:** the radar loads the plate, then extrudes each Digimon once. Extruded model
  bytes are cached process-wide, built on a small worker pool and uploaded nearest-first at up to two
  models per frame, so the closest Digimon are visible within a frame or two and re-entering the place
  does not rebuild the roster.
- **Numbers:** default visible radius 200 m; pinch zoom and the band keys range 0.5×–4×, which now maps
  to 400 m at the widest view and 50 m at the closest (the old 1000 m/125 m envelope left too much
  empty ground). Sprites share the radar's radial legibility curve so distant Digimon stay separated.

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
deck and menu, and the phone bottom navigation bar is retired (see **Farm home shell**). Home's
upper-left Transfer action opens the **Transfer library**, built from PKForge's library/editor/bank
references inside this document's visual system: a two-column shelf of game plates (DS launcher
art, roster census, save count), Storage and the Vital Bracelet as full-width plates, then the
opened game as an identity bar (save label, roster census, refresh), container scope tabs with
counts (All/Party/Farm/Bank) and a three-column box. A Digimon cell is a flat instrument tile:
framed sprite as hero, name and level underneath; tapping opens the full inspector sheet with the
native HP/MP/combat facts, a collapsed native-detail block and one primary Move action. Sends use
the same plates for destination games and the Party/Bank/Farm scope chips for placement. Sprites,
launcher art and the saved palette carry the identity; no extra covers, chrome or gamepad legends
are introduced. Earlier origin-row and cover-grid lists are superseded.

The only transfer directions are game → Storage and Storage → game. Any supported game
that supports an individual's species may receive it; origin is not a destination
restriction. The opened game sets the receiving destination. No filename hint or cover
art asserts reader/writer support, and current copies remain labelled as copies.
The normal supported move preserves nickname, identity and progress in one action,
without preview/confirmation/mapping/APP-creation screens. Internal backup, preflight,
journaling and persistence verification do not become extra routine user steps. Use
short in-flight/result feedback and never present a copy as an actual native move.
Keep management, historical reports and native review under secondary menu actions.
Bracelet preparation/receive/send belongs to its row in the same list, and the Home
transfer action opens that overall list - not the bracelet pane. Inside the destination the
hub owns the single TopBanner for every page, including the embedded NFC steps: a step that
runs in the hub renders its name as an in-flow `VitalSectionHeader` and offers its own
explicit exit instead of adding a second header (standalone `ScanScreen` keeps its own
banner). The hub keeps a small page back-stack, so Back returns to the page that opened the
current one instead of jumping home. The outgoing partner is one card (label, partner or
"Receive only", Change) and the chooser is a radio list, not a row of text buttons. Active
NFC work locks page/partner changes; prepared payloads follow the selected partner.
Storage shortcuts carry partner context. Native change review does not award app growth.

### Universal participation and cosmetic preferences

Every stored Digimon is usable regardless of origin, DIM association, catalog coverage
or artwork. Sprite priority never grays partners, clears active selection, filters
gameplay selectors, pauses Farm/Adventure, blocks chat or invalidates battle/quest
work. Ordinary ownership, actual activity and in-flight transfer constraints remain.
Retired source-mode pauses resume preserving activity clocks and records. A matching
loaded DIM/BEM is required only where the receiving Vital Bracelet needs it.

Ordinary Storage/Home/care/battle information is Tamers Lab-specific and uses the same
DIM-style presentation for every origin. Raw native levels, EXP, stats and product
fields live in the individual's information under the relevant product's Transfer
entry. The permanent individual retains all source and conversion data internally.

### Cyber Frame

Moldura técnica quadrada para painéis de vitais, itens e Digimon. Os quatro cantos são reforçados, e o estado ativo acrescenta um brilho ciano suave. Cantos e traços usam intensidade contida (cantos 8dp, traço 1.5dp ativo) para não competir com sprite e ação.

### Condition details

Home mood cards show only the short qualitative summary, without a numeric score, so they follow the neighboring stat cards' compact rhythm. Trust/rapport retains its summary and exact everyday reading independently of DigiLine message previews. Other farm-care values remain in normal Details. Combat stats, requirements, rewards, and recovery stay directly usable. Summaries derive from stored state and never alter simulation or social decisions.

### Settings

Settings is scanned in setup order rather than in the order features were built: Card archive
(import plus the card-origin prompt), Appearance, Sound, Tamer profile and preferences (Tamer
name and application language), Species (catalog plus the VB-first/Dot-first artwork priority),
Communication (research notes, and the DigiLine connection while Debug and legacy chat are on),
Backup and restore, Debug mode with its subordinate controls only while enabled, and finally
About and credits. Watch connection data ships with the build, so its import is a Debug-only
fallback for a future key rotation rather than an everyday row. Sections are the grouping
boundary; rows keep the cut-corner cyber-frame entry and the `VitalSectionBar` header unchanged.
Single-value editors (Tamer name, app font, application language) open a dialog from their row
instead of living inline in the list.

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

## Battle screen

- **Shell.** Arena battles (training and Arena exhibition/cups) and Radar encounters share one
  full-bleed battle surface: the authored Colosseum/Radar arena fills the window inside the app's
  safe-area shell, with floating chrome instead of the former 16dp page gutter, bordered 1:1 viewport
  and half-screen command slab. No panel wraps the arena and no permanent toggle row exists; the scene
  itself is the page.
- **Top cluster.** One top scrim carries the 48dp exit/back key, the matchup title (the active partner
  against the opponent; Arena sessions keep their title) with one quiet context line (format/round,
  pause reason in cyan, loading), a recenter-camera key, the Debug AI key while Debug is on, and the
  pause key.
- **Combatant HUD.** Opposing Digimon carry projected name+HP plates above their bodies (enemy red, a
  cyan hairline on the selected target, collision-aware so 2v2 and the top cluster never overlap). The
  selected partner has no floating vitals strip: HP/MP/ESP live in the command band's header, so the
  arena is never covered by partner chrome. The scene remains the selection surface (tap a fighter, or
  the existing accessibility actions). Values come from the battle snapshot only; the HUD never invents
  state.
- **Command band.** One bottom band over its own scrim is the trainer's single instrument, inheriting
  Re:Digitize Decode's fixed command grammar. Its header row holds the selected partner's name and
  compact HP/MP/ESP readings over hairline tracks — the space the retired CP meter used to waste — then
  one polite live event/feedback line and a fixed 3×3 deck of nine bare 48dp keys — no fill and no
angular frame, each key settled by a 1dp hairline separation — row one is the
  partner's three equipped techniques (name, EN cost and live availability), row two Items ›, Special
  and Strategy ›, row three Focus, Defend and Move. Unavailable keys stay visible so muscle memory
  survives every combat state; the deck is always three columns, capped at 520dp and centred on wide
  windows, and grows with the system font scale. **Move** arms a one-tap destination: the band asks for
  an arena point, the next touch on the scene is unprojected to the playable floor and issued as a
  move order (the core clamps it to the arena radius); pause, result, a menu or any other order disarms
  it. The former Support/cheer command and its CP grant no longer exist. Pause lives in the top cluster;
  the former standing "Tactics ON/OFF" toggle is now the tactical-pause switch inside the pause panel,
  default on, preserving the old pause-while-choosing behavior.
- **States.** The countdown floats over the live scene; loading/transforming is one compact banner with
  a hairline progress instead of a card that owns the window; pause opens a scrimmed technical panel
  with resume, the tactical-pause switch and exit; exit keeps its native confirmation dialog; the Blast
  timing overlay is a Decode-style command dial — a dark technical ring with tick marks, a yellow
  progress arc, a red target arc and a needle, with the ordered move's name and a small BLAST label set
  in the centre, plus one instruction line — and any touch on the screen answers it, with no Stop
  button; finisher titles sit below the top cluster while commands remain
  visible but inert and pause/exit stay reachable; once a battle settles the live chrome (top cluster,
  command band, projected HUD and effects) steps aside so the result and the Radar rewards own the
  surface, floating over the still-visible arena with 48dp actions. Error/retry is one line in the
  band's language with technical details behind Debug.
- **Radar continuity.** The Radar battle no longer falls back to a square viewport: the Radar world is
  full-bleed before the encounter, the arena replaces it in the same frame, and leaving returns to the
  Radar place. The AR stage keeps its established square scanning/battle layout — it is the explicit
  legacy exception rather than a second battle design — but shares the same command deck and partner
  instruments.
- **Motion and adaptation.** Band and scrims use the HUD ink and soft shadows; health changes are
  instant while the scene owns the motion; reduced motion removes the remaining fades (the timing
  challenge itself stays functional input); portrait, landscape and wide windows share one structure;
  every saved palette resolves through the theme roles, and the light palettes keep the scrims'
  contrast.

## Source-mode native acceptance boundary

The participation increment has source-review fixes recorded, with the final
narrow recruitment verdict and last guard/regression recompile pending at handoff.
Earlier native captures do not accept the new inactive/paused states. No connected
ADB device, new screenshots, TalkBack or large-font acceptance is recorded for
this increment; compilation is not native approval. See the latest `TESTING.md`
entry for the supplied checkpoint and remaining acceptance work.
