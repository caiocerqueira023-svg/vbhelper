# VB Lab 1.4.6 — ícones e backgrounds originais

Assets originais extraídos do APK **VB Lab 1.4.6** (`net.digimon.digibb`), separados para uma
implementação futura no Tamers Lab. Nada aqui foi redesenhado: os arquivos são cópias verbatim das
texturas/sprites recuperadas do pacote (apenas renomeados para ficar legível).

- Fonte: `C:\Users\julye\Downloads\net.digimon.digibb_1.4.6.xapk`
- APK base: SHA-256 `6c6a7ff65e063cfdab1a4c6121bad7aa456d585231a4361279c17b4683bc3622`
- Extração: UnityPy sobre `assets/bin/Data/data.unity3d` + `res/` do APK (ícones do launcher).
- Uso: **preservação pessoal** de um app descontinuado; material de IP de terceiros — não redistribuir
  nem commitar sem decisão explícita. (O diretório `analysis/` está fora do git.)
- Índice legível por máquina: `index.json` (arquivo, largura, altura, asset de origem).
- Contact sheets: `contacts/icons_contact.png`, `contacts/backgrounds_contact.png`,
  `contacts/logos_launcher_contact.png`, `contacts/illustrations_contact.png`,
  `contacts/effects_contact.png`.

## Estrutura

| Pasta | Conteúdo | Arquivos |
|---|---|---:|
| `icons/ui/` | ícones de UI 11×11/8×8 (checkmark, foldout, elemento, máscara) e utilitários | 7 |
| `icons/buttons/` | atlas e sprites dos botões hexagonais (`button_b`, `button_st`, estados on/off) | 7 |
| `icons/popups/` | elementos de popup: atlas de textos, fundo octogonal, erro, linha e padrão | 5 |
| `backgrounds/title/` | arte de título: fundo, imagem, efeito, padrão, moldura de aviso, anéis/círculos e raio | 11 |
| `backgrounds/effects/` | `Ef_gr` — glow radial (vinheta/brilho) | 1 |
| `logos/` | logos do app: VITAL BRACELET Lab, BANDAI, CHARIOT | 3 |
| `launcher/` | ícones do launcher em 6 densidades (normal/round/foreground/background) + `xapk_icon.png` 512px + 2 XMLs adaptive | 27 |
| `illustrations/` | bônus: 5 ilustrações de Digimon encontradas localmente (`iC0002/3/4/11/9000`) | 5 |
| `effects/handoff/` | bônus: sprites originais `loadchara_0/1` e os dois atlas do fluxo de scan | 4 |
| `effects/attacks/` | bônus: sprites de efeitos de ataque (`atk_s_*`, `atk_l_*`) | 6 |

## Ícones de navegação (recriados)

Os ícones da barra inferior (Scan/Log/Digidex/Items/Battle) **não existem no APK** (são assets
remotos). Foram recriados como **PNG brancos com transparência** (24 a 1024 px) a partir do print
do app em [`icons/nav-final/`](icons/nav-final/README.md).

## Observações de origem

- Os ícones de UI são **pequenos demais** para uso direto em telas modernas (11×11 px): para a
  implementação, o caminho é escalar sem filtro (`FilterQuality.None`) ou redesenhar equivalentes.
- `title_img.png`, `title_bg.png`, `Ef_gr.png` e os anéis são os elementos de identidade visual do app
  (navy/ciano, scanlines, anéis e glow) — casam com o `DESIGN.md` do Tamers Lab.
- `popup_text_atlas.png` é um atlas de textos coloridos de estado (MAINTENANCE/ERROR/SP MISSION
  START!/YOU WIN! etc.), não um ícone único.
- Não foram incluídos: texturas de engine/terreno (`AreaTex`, `SearchTex`, `LDR_LLL1_*`, `base`) e os
  atlas de fonte `Thin/Medium/Large` (páginas vazias com alpha 0 no build).
- Se a intenção for os assets do **VB Arena 2.1.0** (outro app), é outra extração — este pacote é só
  do VB Lab.
